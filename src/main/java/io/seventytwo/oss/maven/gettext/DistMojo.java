package io.seventytwo.oss.maven.gettext;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Generates ressource bundles.
 */
@Mojo(name = "dist", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class DistMojo extends AbstractGettextMojo {

    /**
     * The msgcat command.
     */
    @Parameter(required = true, defaultValue = "msgcat")
    protected String msgcatCmd;

    /**
     * The msgfmt command.
     */
    @Parameter(required = true, defaultValue = "msgfmt")
    protected String msgfmtCmd;

    /**
     * The package and file name of the generated class or properties files.
     */
    @Parameter(required = true)
    protected String targetBundle;

    /**
     * Output format, can be "class", "properties", or "java".
     */
    @Parameter(required = true, defaultValue = "class")
    protected String outputFormat;

    /**
     * Java version. Can be "1" or "2".
     */
    @Parameter(required = true, defaultValue = "2")
    protected String javaVersion;

    /**
     * The locale of the messages in the source code.
     */
    @Parameter(required = true, defaultValue = "en")
    protected String sourceLocale;

    /**
     * Use unicode escape sequences when printing non-ASCII characters
     */
    @Parameter(required = true, defaultValue = "false")
    protected boolean escapeUnicode;

    /**
     * The encoding of the source Java files. utf-8 is a superset of ascii.
     */
    @Parameter(defaultValue = "utf-8")
    protected String encoding;

    public void execute() throws MojoExecutionException {
        if (!outputDirectory.exists()) {
            // create output directory if it doesn't exist
            boolean directoryCreated = outputDirectory.mkdirs();
            if (!directoryCreated) {
                getLog().error("Unable to create directory %s".formatted(outputDirectory.getAbsolutePath()));
            }
        }

        CommandlineFactory cf;
        if ("class".equals(outputFormat)) {
            cf = new MsgFmtCommandlineFactory();
        } else if ("properties".equals(outputFormat)) {
            cf = new MsgCatCommandlineFactory();
        } else if ("java".equals(outputFormat)) {
            cf = new MsgFmtSourceCommandlineFactory();
        } else {
            throw new MojoExecutionException("Unknown output format: %s. Should be 'class' or 'properties'.".formatted(outputFormat));
        }

        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(poDirectory);
        ds.setIncludes(new String[]{"**/*.po"});
        ds.scan();

        String[] files = ds.getIncludedFiles();
        for (String file : files) {
            getLog().info("Processing " + file);
            try {
                cf.init();
            } catch (IOException e) {
                getLog().error("Unable to prepare for processing %s".formatted(file), e);
                throw new IllegalStateException("Unable to prepare for processing %s".formatted(file), e);
            }

            File inputFile = new File(poDirectory, file);
            File outputFile = cf.getOutputFile(inputFile);

            if (!isNewer(inputFile, outputFile)) {
                getLog().info("Not compiling, target is up-to-date: %s".formatted(outputFile));
                continue;
            }

            Commandline cl = cf.createCommandline(inputFile);
            for (String arg : extraArgs) {
                cl.createArg().setValue(arg);
            }
            getLog().debug("Executing: " + cl.toString());
            StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
            StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
            try {
                CommandLineUtils.executeCommandLine(cl, out, err);
            } catch (CommandLineException e) {
                getLog().error("Could not execute " + cl.getExecutable() + ".", e);
            }
            try {
                cf.finish();
            } catch (IOException e) {
                getLog().error("Unable to process file " + file, e);
                throw new IllegalStateException("Unable to process file " + file, e);
            }
            if (!escapeUnicode && "java".equals(outputFormat)) {
                GettextUtils.unescapeUnicode(outputFile, encoding, getLog());
            }
        }
    }

    private boolean isNewer(File inputFile, File outputFile) {
        return inputFile.lastModified() > outputFile.lastModified();
    }

    private interface CommandlineFactory {
        Commandline createCommandline(File file);

        File getOutputFile(File input);

        void init() throws IOException;

        void finish() throws IOException;
    }

    private class MsgFmtCommandlineFactory implements CommandlineFactory {

        protected String outputExtension() {
            return ".class";
        }

        public File getOutputFile(File input) {
            String locale = getLocale(input);
            return new File(outputDirectory, "%s_%s%s".formatted(targetBundle.replace('.', File.separatorChar), locale, outputExtension()));
        }

        private String getLocale(File file) {
            String locale = file.getName().substring(0, file.getName().lastIndexOf('.'));
            return GettextUtils.getJavaLocale(locale);
        }

        protected File getOutputDirectory() {
            return outputDirectory;
        }

        public Commandline createCommandline(File file) {
            Commandline cl = new Commandline();
            cl.setExecutable(msgfmtCmd);

            if ("2".equals(javaVersion)) {
                cl.createArg().setValue("--java2");
            } else {
                cl.createArg().setValue("--java");
            }

            cl.createArg().setValue("-d");
            cl.createArg().setFile(getOutputDirectory());
            cl.createArg().setValue("-r");
            cl.createArg().setValue(targetBundle);
            cl.createArg().setValue("-l");
            cl.createArg().setValue(getLocale(file));
            cl.createArg().setFile(file);
            return cl;
        }

        @Override
        public void init() throws IOException {
        }

        @Override
        public void finish() throws IOException {
        }
    }

    private class MsgFmtSourceCommandlineFactory extends MsgFmtCommandlineFactory {
        private File tmpOutDir;

        @Override
        protected String outputExtension() {
            return ".java";
        }

        @Override
        public void init() throws IOException {
            super.init();
            try {
                tmpOutDir = Files.createTempDirectory(msgfmtCmd + sourceLocale).toFile();
            } catch (IOException e) {
                getLog().error("Unable to create temp directory", e);
                throw new IllegalStateException("Unable to create temp directory", e);
            }
        }

        @Override
        protected File getOutputDirectory() {
            return tmpOutDir;
        }

        @Override
        public Commandline createCommandline(File file) {
            Commandline cl = super.createCommandline(file);
            cl.createArg().setValue("--source");
            return cl;
        }

        @Override
        public void finish() throws IOException {
            getLog().debug("Copying java files from " + getOutputDirectory() + " to " + super.getOutputDirectory());
            FileUtils.copyDirectoryStructure(getOutputDirectory(), super.getOutputDirectory());
            FileUtils.deleteDirectory(getOutputDirectory());
        }
    }

    private class MsgCatCommandlineFactory implements CommandlineFactory {

        public File getOutputFile(File input) {
            String basepath = targetBundle.replace('.', File.separatorChar);
            String locale = input.getName().substring(0, input.getName().lastIndexOf('.'));
            locale = GettextUtils.getJavaLocale(locale);
            return new File(outputDirectory, "%s_%s.properties".formatted(basepath, locale));
        }

        public Commandline createCommandline(File file) {
            Commandline cl = new Commandline();

            File outputFile = getOutputFile(file);
            File parent = outputFile.getParentFile();
            if (parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new RuntimeException("Directory could not be created: %s".formatted(parent.getAbsolutePath()));
                }
            }

            cl.setExecutable(msgcatCmd);

            cl.createArg().setValue("--no-location");
            cl.createArg().setValue("-p");
            cl.createArg().setFile(file);
            cl.createArg().setValue("-o");
            cl.createArg().setFile(outputFile);

            return cl;
        }

        @Override
        public void init() {
        }

        @Override
        public void finish() {
        }
    }

}
