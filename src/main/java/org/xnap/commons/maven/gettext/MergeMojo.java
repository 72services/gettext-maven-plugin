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
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;

/**
 * Invokes the gettext:gettext goal and invokes msgmerge to update po files.
 *
 * @author Tammo van Lessen
 * @goal merge
 * @phase generate-resources
 */
public class MergeMojo
        extends AbstractGettextMojo {

    /**
     * The msgmerge command.
     *
     * @parameter expression="${msgmergeCmd}" default-value="msgmerge"
     * @required
     */
    protected String msgmergeCmd;

    /**
     * The msgmerge backup mode: none, numbered, existing, simple
     *
     * @parameter expression="${backup}" default-value="none"
     * @required
     */
    protected String backup;

    /**
     * Sort extracted messages, can be "output" or "by-file"
     *
     * @parameter expression="${sort}" default-value="by-file"
     * @required
     */
    protected String sort;

    public void execute()
            throws MojoExecutionException {
        getLog().info("Invoking msgmerge for po files in '"
                + poDirectory.getAbsolutePath() + "'.");

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
        for (int i = 0; i < files.length; i++) {
            getLog().info("Processing " + files[i]);
            Commandline cl = new Commandline();
            cl.setExecutable(msgmergeCmd);
            for (String arg : extraArgs) {
                cl.createArgument().setValue(arg);
            }
            cl.createArgument().setValue("-q");
            cl.createArgument().setValue("--backup=" + backup);
            cl.createArgument().setValue("-U");
            cl.createArgument().setFile(new File(poDirectory, files[i]));
            cl.createArgument().setValue(new File(poDirectory, keysFile).getAbsolutePath());
            cl.createArgument().setValue("by-file".equalsIgnoreCase(sort) ? "-F" : "-s");

            getLog().debug("Executing: " + cl.toString());
            StreamConsumer out = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.INFO);
            StreamConsumer err = new LoggerStreamConsumer(getLog(), LoggerStreamConsumer.WARN);
            try {
                CommandLineUtils.executeCommandLine(cl, out, err);
            } catch (CommandLineException e) {
                getLog().error("Could not execute " + msgmergeCmd + ".", e);
            }
        }
    }
}
