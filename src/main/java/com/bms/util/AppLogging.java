package com.bms.util;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Sends everything the program logs (java.util.logging) to a file as well as the console.
 * The user sees a short friendly message; the technical detail goes to logs/bms.log
 * so that it can be read later (for example when something fails on a computer without NetBeans).
 */
public final class AppLogging {

    public static final String LOG_FOLDER = "logs";
    public static final String LOG_FILE = LOG_FOLDER + "/bms.log";

    private static final int MAX_BYTES = 1_000_000;   // about 1 MB per file
    private static final int FILE_COUNT = 3;          // bms.log, bms.log.1, bms.log.2 are kept
    private static boolean started = false;

    private AppLogging() {
    }

    /**
     * Starts logging to the file. Safe to call more than once. If the log file cannot be created the
     * program still works (it logs to the console only) and this returns false.
     */
    public static synchronized boolean start() {
        if (started) {
            return true;
        }
        try {
            File folder = new File(LOG_FOLDER);
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new java.io.IOException("could not create the folder " + folder.getAbsolutePath());
            }
            FileHandler handler = new FileHandler(LOG_FOLDER + "/bms.%g.log", MAX_BYTES, FILE_COUNT, true);
            handler.setEncoding(StandardCharsets.UTF_8.name());
            handler.setFormatter(new LineFormatter());
            handler.setLevel(Level.INFO);
            Logger root = Logger.getLogger("");
            root.addHandler(handler);
            root.setLevel(Level.INFO);
            started = true;
            Logger.getLogger(AppLogging.class.getName()).info("Logging started. File: " + logFile().getAbsolutePath());
            return true;
        } catch (Exception e) {
            System.err.println("Warning: the log file could not be opened (" + e.getMessage() + "). Logging to the console only.");
            return false;
        }
    }

    /** The file the newest log lines are written to. */
    public static File logFile() {
        return new File(LOG_FOLDER, "bms.0.log");
    }

    /** One readable line per message, followed by the stack trace when there is one. */
    private static final class LineFormatter extends Formatter {
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(LocalDateTime.now().format(TIME)).append(' ')
              .append(String.format("%-7s", record.getLevel().getName())).append(' ')
              .append(shortName(record.getLoggerName())).append(" - ")
              .append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter text = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(text));
                sb.append(text);
            }
            return sb.toString();
        }

        private static String shortName(String name) {
            if (name == null) {
                return "";
            }
            int dot = name.lastIndexOf('.');
            return dot < 0 ? name : name.substring(dot + 1);
        }
    }
}
