package io.seventytwo.oss.maven.gettext;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.WriterStreamConsumer;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Goal that generates a report.
 *
 * @author Steffen Pingel
 * @goal report
 * @phase process-sources
 */
public class ReportMojo extends AbstractMojo {
    private static final Pattern TRANSLATOR_PATTERN = Pattern.compile("\"Last-Translator: (?<name>.*)\"", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Specifies the directory where the report will be generated.
     */
    @Parameter(required = true, defaultValue = "${project.reporting.outputDirectory}")
    private File outputDirectory;

    @Parameter(required = true, readonly = true, defaultValue = "${project}")
    private MavenProject project;

    /**
     * PO directory.
     */
    @Parameter(required = true, defaultValue = "${project.build.sourceDirectory}/main/po")
    protected File poDirectory;

    /**
     * msgfmt command.
     */
    @Parameter(required = true, defaultValue = "msgfmt")
    protected String msgfmtCmd;

    @Parameter
    protected String[] includes;

    @Parameter
    protected String[] excludes;

    protected PrintStream out;

    public void execute() throws MojoExecutionException {
        try {
            File file = new File(outputDirectory, getOutputName() + ".txt");
            file.delete();
            file.createNewFile();
            out = new PrintStream(new FileOutputStream(file, false));
            executeReport();
            out.close();
        } catch (Exception e) {
            throw new MojoExecutionException("An error has occurred in " + getName() + " report generation: " + e, e);
        }
    }

    protected void executeReport() {
        Stats stats = gatherStats();
        createReport(stats);
    }

    private void createReport(Stats stats) {
        // Layout
        // Locale (text) | i18n | % | Trans | Untr | Fuzzy | Author
        out.println("Gettext Statistics");

        // I know, this is not Sink-aware at all!
        String hdrString = "%-20s | %-10s | %4s | %8s | %8s | %8s | %s";
        String rowString = "%-20s | %-10s | %3.0f%% | %8d | %8d | %8d | %s";
        out.println(String.format(hdrString, "Locale", "code", "%", "Trans", "Untr", "Fuzzy", "Translator"));
        out.println(String.format(hdrString, "--------------------", "----------", "----", "--------", "--------", "--------", "----------------"));
        List<StatsEntry> items = stats.getItems();
        Collections.sort(items);
        for (StatsEntry item : items) {
            out.println(String.format(rowString, item.getLocale().getDisplayName(), item.getLocale(),
                    item.getTranslated() * 100f / item.getTotal(), item.getTranslated(), item.getUntranslated(),
                    item.getFuzzy(), item.getTranslator()));
        }
    }

    public String getDescription() {
        return "Statistics about po files.";
    }

    public String getName() {
        return "Gettext";
    }

    public String getOutputName() {
        return "gettext-report";
    }

    protected MavenProject getProject() {
        return project;
    }

    public Stats gatherStats() {
        getLog().info("Gathering statistics for po files in '%s'.".formatted(poDirectory.getAbsolutePath()));

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

        Stats stats = new Stats();

        String[] files = ds.getIncludedFiles();
        for (String s : files) {
            File file = new File(poDirectory, s);
            getLog().info("Processing " + file.getAbsolutePath());

            Commandline cl = new Commandline();
            // make sure the output is in english
            cl.addEnvironment("LC_ALL", "C");
            cl.setExecutable(msgfmtCmd);
            cl.createArg().setValue("--statistics");
            cl.createArg().setValue(file.getAbsolutePath());

            Writer out = new StringWriter();
            Writer err = new StringWriter();
            try {
                int ret = CommandLineUtils.executeCommandLine(cl,
                        new WriterStreamConsumer(out),
                        new WriterStreamConsumer(err));
                if (ret == 0) {
                    // for whatever reason the output is written to stderr
                    StatsEntry entry = stats.parseOutput(file, err.toString());
                    entry.setTranslator(getTranslator(file));
                } else {
                    getLog().info(err.toString());
                }
            } catch (CommandLineException e) {
                getLog().error("Could not execute msgfmt: %s".formatted(err), e);
            }
        }

        return stats;
    }

    private String getTranslator(File file) {
        try (BufferedReader rdr = new BufferedReader(new FileReader(file))) {
            String line = null;
            boolean headerFound = false;
            while ((line = rdr.readLine()) != null) {
                if ((line.isEmpty() || line.startsWith("msgid")) && headerFound) { // only search the header
                    return null;
                } else if (line.startsWith("msgid") && !headerFound) {
                    headerFound = true;
                }
                Matcher m = TRANSLATOR_PATTERN.matcher(line);
                if (m.matches()) {
                    return m.group("name").trim().replaceAll("\\\\n", "");
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static Locale getLocale(File file) {
        String basename = file.getName().substring(0, file.getName().lastIndexOf('.'));
        if (basename.contains("_")) {
            StringTokenizer t = new StringTokenizer(basename, "_");
            return Locale.of(t.nextToken(), t.nextToken());
        } else {
            return Locale.of(basename);
        }
    }

    private class Stats {

        private final List<StatsEntry> items = new ArrayList<>();

        /**
         * <code>
         * 117 translated messages.
         * 0 translated messages, 117 untranslated messages.
         * 92 translated messages, 5 fuzzy translations, 20 untranslated messages.
         * </code>
         *
         * @param line output of msgfmt command
         */
        public StatsEntry parseOutput(File file, String line) {
            StatsEntry entry = new StatsEntry(file);
            items.add(entry);

            StringTokenizer t = new StringTokenizer(line, ",");
            while (t.hasMoreTokens()) {
                String token = t.nextToken().trim();

                // remove trailing "."
                if (token.endsWith(".")) {
                    token = token.substring(0, token.length() - 1);
                }

                if (token.endsWith("untranslated messages")) {
                    entry.setUntranslated(extractNumber(token));
                } else if (token.endsWith("translated messages")) {
                    entry.setTranslated(extractNumber(token));
                } else if (token.endsWith("fuzzy translations")) {
                    entry.setFuzzy(extractNumber(token));
                }
            }
            return entry;
        }

        private int extractNumber(String token) {
            StringTokenizer t = new StringTokenizer(token, " ");
            if (t.hasMoreTokens()) {
                try {
                    return Integer.parseInt(t.nextToken());
                } catch (NumberFormatException ignored) {
                }
            }
            getLog().warn("Could not parse token: " + token);
            return 0;
        }

        public List<StatsEntry> getItems() {
            return items;
        }

    }

    private static class StatsEntry implements Comparable<StatsEntry> {

        private final File file;
        private final Locale locale;
        private int untranslated;
        private int fuzzy;
        private int translated;
        private String translator;

        public StatsEntry(File file) {
            this.file = file;
            this.locale = ReportMojo.getLocale(file);
        }

        public int compareTo(StatsEntry o) {
            return getLocale().getDisplayName().compareTo(o.getLocale().getDisplayName());
        }

        public Locale getLocale() {
            return locale;
        }

        public File getFile() {
            return file;
        }

        public int getTotal() {
            return getUntranslated() + getFuzzy() + getTranslated();
        }

        public int getUntranslated() {
            return untranslated;
        }

        public int getFuzzy() {
            return fuzzy;
        }

        public int getTranslated() {
            return translated;
        }

        public void setTranslated(int translated) {
            this.translated = translated;
        }

        public void setFuzzy(int fuzzy) {
            this.fuzzy = fuzzy;
        }

        public void setUntranslated(int untranslated) {
            this.untranslated = untranslated;
        }

        public String getTranslator() {
            return translator;
        }

        public void setTranslator(String translator) {
            this.translator = translator;
        }
    }
}
