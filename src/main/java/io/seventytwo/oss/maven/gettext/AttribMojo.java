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
 * Invokes the gettext:gettext goal and invokes msgattrib to update po files.
 */
@Mojo(name = "attrib", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class AttribMojo extends AbstractGettextMojo {

    /**
     * The msgattrib command.
     */
    @Parameter(required = true, defaultValue = "msgattrib")
    protected String msgattribCmd;

    public void execute() throws MojoExecutionException {
        getLog().info("Invoking msgattrib for po files in '%s'.".formatted(poDirectory.getAbsolutePath()));

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
        String[] files = ds.getIncludedFiles();
        for (String file : files) {
            getLog().info("Processing %s".formatted(file));
            Commandline cl = new Commandline();
            cl.setExecutable(msgattribCmd);
            for (String arg : extraArgs) {
                cl.createArg().setValue(arg);
            }
            cl.createArg().setValue("-o");
            cl.createArg().setFile(new File(poDirectory, file));
            cl.createArg().setFile(new File(poDirectory, file));

            getLog().debug("Executing: %s".formatted(cl.toString()));
            StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
            StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
            try {
                CommandLineUtils.executeCommandLine(cl, out, err);
            } catch (CommandLineException e) {
                getLog().error("Could not execute " + msgattribCmd + ".", e);
            }
            if (!printPOTCreationDate) {
                GettextUtils.removePotCreationDate(new File(poDirectory, file), getLog());
            }
        }
    }
}
