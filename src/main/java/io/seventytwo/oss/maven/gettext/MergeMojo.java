package io.seventytwo.oss.maven.gettext;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;

/**
 * Executes msmerge
 */
@Mojo(name = "merge", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class MergeMojo extends AbstractGettextMojo {

    /**
     * The msgmerge command.
     */
    @Parameter(required = true, defaultValue = "msgmerge")
    protected String msgmergeCmd;

    /**
     * The msgmerge backup mode: none, numbered, existing, simple
     */
    @Parameter(required = true, defaultValue = "none")
    protected String backup;

    /**
     * Sort extracted messages, can be "output" or "by-file"
     */
    @Parameter(required = true, defaultValue = "by-file")
    protected String sort;

    public void execute() throws MojoExecutionException {
        getLog().info("Invoking msgmerge for po files in '%s'.".formatted(poDirectory.getAbsolutePath()));

        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir(poDirectory);
        if (includes != null && includes.length > 0) {
            ds.setIncludes(includes);
        } else {
            ds.setIncludes(new String[]{"**/*.po"});
        }
        if (excludes != null && excludes.length > 0) {
            ds.setExcludes(excludes);
        }
        ds.scan();

        getLog().info("Processing files in %s".formatted(poDirectory));

        String[] files = ds.getIncludedFiles();
        for (String fileName : files) {
            getLog().info("Processing " + fileName);
            Commandline cl = new Commandline();
            cl.setExecutable(msgmergeCmd);
            for (String arg : extraArgs) {
                cl.createArg().setValue(arg);
            }
            cl.createArg().setValue("-q");
            cl.createArg().setValue("--backup=" + backup);
            cl.createArg().setValue("-U");
            File file = new File(poDirectory, fileName);
            cl.createArg().setFile(file);
            cl.createArg().setValue(new File(poDirectory, keysFile).getAbsolutePath());
            cl.createArg().setValue("by-file".equalsIgnoreCase(sort) ? "-F" : "-s");

            getLog().debug("Executing: " + cl.toString());
            StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
            StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
            try {
                CommandLineUtils.executeCommandLine(cl, out, err);
            } catch (CommandLineException e) {
                getLog().error("Could not execute " + msgmergeCmd + ".", e);
            }

            if (!printPOTCreationDate) {
                GettextUtils.removePotCreationDate(file, getLog());
            }
        }
    }

}
