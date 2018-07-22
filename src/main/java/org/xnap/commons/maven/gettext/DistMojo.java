package org.xnap.commons.maven.gettext;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.MojoExecutionException;
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
 *
 * @author Tammo van Lessen
 * @goal dist
 * @phase generate-resources
 */
public class DistMojo
        extends AbstractGettextMojo {

    /**
     * The msgcat command.
     *
     * @parameter property="msgcatCmd" default-value="msgcat"
     * @required
     */
    protected String msgcatCmd;

    /**
     * The msgfmt command.
     *
     * @parameter property="msgfmtCmd" default-value="msgfmt"
     * @required
     */
    protected String msgfmtCmd;

    /**
     * The package and file name of the generated class or properties files.
     *
     * @parameter property="targetBundle"
     * @required
     */
    protected String targetBundle;

    /**
     * Output format, can be "class", "properties", or "java".
     *
     * @parameter property="outputFormat" default-value="class"
     * @required
     */
    protected String outputFormat;

    /**
     * Java version. Can be "1" or "2".
     *
     * @parameter property="javaVersion" default-value="2"
     * @required
     */
    protected String javaVersion;

    /**
     * The locale of the messages in the source code.
     *
     * @parameter property="sourceLocale" default-value="en"
     * @required
     */
    protected String sourceLocale;

    public void execute()
            throws MojoExecutionException {

        // create output directory if it doesn't exists
        outputDirectory.mkdirs();

        CommandlineFactory cf = null;
        if ("class".equals(outputFormat)) {
            cf = new MsgFmtCommandlineFactory();
        } else if ("properties".equals(outputFormat)) {
            cf = new MsgCatCommandlineFactory();
        } else if ("java".equals(outputFormat)) {
            cf = new MsgFmtSourceCommandlineFactory();
        } else
            throw new MojoExecutionException("Unknown output format: "
                    + outputFormat + ". Should be 'class' or 'properties'.");

        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(poDirectory);
        ds.setIncludes(new String[]{"**/*.po"});
        ds.scan();

        String[] files = ds.getIncludedFiles();
        for (int i = 0; i < files.length; i++) {
            getLog().info("Processing " + files[i]);
            try {
                cf.init();
            } catch (IOException e) {
                getLog().error("Unable to prepare for processing " + files[i], e);
                throw new IllegalStateException("Unable to prepare for processing " + files[i], e);
            }

            File inputFile = new File(poDirectory, files[i]);
            File outputFile = cf.getOutputFile(inputFile);

            if (!isNewer(inputFile, outputFile)) {
                getLog().info("Not compiling, target is up-to-date: " + outputFile);
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
                getLog().error("Unable to process file " + files[i], e);
                throw new IllegalStateException("Unable to process file " + files[i], e);
            }
        }
    }

    private boolean isNewer(File inputFile, File outputFile) {
        return inputFile.lastModified() > outputFile.lastModified();
    }

    private interface CommandlineFactory {
        Commandline createCommandline(File file);

        /**
         * @return the output file of this command
         */
        File getOutputFile(File input);

        void init() throws IOException;

        void finish() throws IOException;
    }

    private class MsgFmtCommandlineFactory implements CommandlineFactory {

        public File getOutputFile(File input) {
            String locale = getLocale(input);
            return new File(outputDirectory, targetBundle.replace('.', File.separatorChar) + "_" + locale + ".class");
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
            File target = new File(outputDirectory, basepath + "_" + locale + ".properties");
            return target;
        }

        public Commandline createCommandline(File file) {
            Commandline cl = new Commandline();

            File outputFile = getOutputFile(file);
            File parent = outputFile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
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
        public void init() throws IOException {
        }

        @Override
        public void finish() {
        }
    }

}
