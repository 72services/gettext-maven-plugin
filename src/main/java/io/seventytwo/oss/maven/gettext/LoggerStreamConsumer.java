package io.seventytwo.oss.maven.gettext;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Logger stream consumer
 */
public class LoggerStreamConsumer implements StreamConsumer {

    /**
     * Debug level
     */
    public static final int DEBUG = 0;
    /**
     * Info level
     */
    public static final int INFO = 1;
    /**
     * Warn level
     */
    public static final int WARN = 2;

    /**
     * Error level
     */
    public static final int ERROR = 3;

    private final Log logger;
    private final int loglevel;

    /**
     * Ctor
     *
     * @param logger   logger
     * @param loglevel level
     */
    public LoggerStreamConsumer(Log logger, int loglevel) {
        this.logger = logger;
        this.loglevel = loglevel;
    }

    /**
     * Consumes a line
     *
     * @param line line
     */
    public void consumeLine(String line) {
        if (loglevel == DEBUG) {
            logger.debug(line);
        } else if (loglevel == INFO) {
            logger.info(line);
        } else if (loglevel == WARN) {
            logger.warn(line);
        } else if (loglevel == ERROR) {
            logger.error(line);
        }
    }

}
