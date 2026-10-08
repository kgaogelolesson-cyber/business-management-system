package com.bms.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * The safety net for bugs we did not expect (a NullPointerException, for example).
 * Expected problems are handled where they happen and shown as friendly messages. Anything that
 * still escapes ends up here: it is written to the log with its stack trace, and the user sees one short
 * message instead of nothing happening (or a Java error in a console they never open).
 */
public final class AppErrors {

    private static final Logger LOGGER = Logger.getLogger(AppErrors.class.getName());
    private static final AtomicBoolean DIALOG_OPEN = new AtomicBoolean(false);

    private AppErrors() {
    }

    /** Call once, as early as possible in main(). */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> report(error, true));
    }

    /**
     * Logs the error and (when showDialog is true) shows one friendly dialog - never a pile of them
     * if the same error repeats. showDialog = false is for the self-check programs.
     */
    public static void report(Throwable error, boolean showDialog) {
        LOGGER.log(Level.SEVERE, "Unexpected error", error);
        if (!showDialog || !DIALOG_OPEN.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                JOptionPane.showMessageDialog(null, message(), "Business Management System", JOptionPane.ERROR_MESSAGE);
            } catch (RuntimeException ignored) {
                // no screen available: the log already has the details
            } finally {
                DIALOG_OPEN.set(false);
            }
        });
    }

    static String message() {
        return "Something unexpected went wrong, and the last action may not have finished.\n"
                + "Please check your data and try again. If it keeps happening, restart the program.\n\n"
                + "Technical details were saved in: " + AppLogging.logFile().getAbsolutePath();
    }
}
