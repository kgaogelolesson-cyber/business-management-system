package com.bms.exception;

import java.sql.SQLException;

/**
 * Turns a technical SQLException into a sentence a normal user can act on.
 * The original exception is still logged and kept as the "cause"; only this sentence is shown.
 */
public final class DatabaseErrors {

    private DatabaseErrors() {
    }

    /** The action-specific message ("The orders could not be loaded.") plus a hint when the cause is recognised. */
    public static String friendly(String baseMessage, SQLException e) {
        String hint = hintFor(e);
        return hint == null ? baseMessage : baseMessage + " " + hint;
    }

    /** Looks at the exception and everything that caused it. Returns null when the cause is not recognised. */
    static String hintFor(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = String.valueOf(t.getMessage()).toLowerCase();
            if (m.contains("sqlite_busy") || m.contains("sqlite_locked") || m.contains("database is locked")
                    || m.contains("database table is locked")) {
                return "The database is being used by another program (for example DB Browser). "
                        + "Close it and try again.";
            }
            if (m.contains("sqlite_readonly") || m.contains("readonly database") || m.contains("read-only")) {
                return "The database file is read-only. Check that the data folder is not read-only "
                        + "and that you are allowed to save files there.";
            }
            if (m.contains("sqlite_full") || m.contains("disk is full")) {
                return "The disk is full. Free some space and try again.";
            }
            if (m.contains("sqlite_corrupt") || m.contains("malformed") || m.contains("sqlite_notadb")
                    || m.contains("not a database")) {
                return "The database file looks damaged. Close the program and restore a copy of data/bms.db if you have one.";
            }
            if (m.contains("could not create the database folder")) {
                return "The data folder could not be created. Check that you are allowed to save files "
                        + "where the program is installed (not a read-only or protected folder).";
            }
            if (m.contains("sqlite_cantopen") || m.contains("unable to open") || m.contains("out of memory")) {
                return "The database file could not be opened. Check that the data folder exists and that you are allowed to use it.";
            }
        }
        return null;
    }
}
