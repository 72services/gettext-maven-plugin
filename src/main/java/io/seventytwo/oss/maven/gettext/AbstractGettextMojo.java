package io.seventytwo.oss.maven.gettext;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Base class with generally used parameters
 */
public abstract class AbstractGettextMojo extends AbstractMojo {

    /**
     * The output directory for generated class or properties files.
     */
    @Parameter(required = true, defaultValue = "${project.build.outputDirectory}")
    protected File outputDirectory;

    /**
     * Source directory. This directory is searched recursively for .java files.
     */
    @Parameter(required = true, defaultValue = "${project.build.sourceDirectory}")
    protected File sourceDirectory;

    /**
     * The output directory for the keys.pot directory for merging .po files.
     */
    @Parameter(required = true, defaultValue = "${project.build.sourceDirectory}/main/po")
    protected File poDirectory;

    /**
     * Filename of the .pot file.
     */
    @Parameter(required = true, defaultValue = "keys.pot")
    protected String keysFile;

    /**
     * Extra arguments for the cmd-line
     */
    @Parameter
    protected String[] extraArgs = new String[0];

    /**
     * Includes
     */
    @Parameter
    protected String[] includes = new String[0];

    /**
     * Excludes
     */
    @Parameter
    protected String[] excludes = new String[0];

    /**
     * Print POT-Creation-Date header to po files
     */
    @Parameter(required = true, defaultValue = "false")
    protected boolean printPOTCreationDate;
}
