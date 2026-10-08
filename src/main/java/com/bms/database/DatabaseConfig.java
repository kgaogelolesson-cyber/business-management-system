package com.bms.database;

/**
 * Central place for database settings, so no other class hard-codes them.
 */
public final class DatabaseConfig {

    public static final String DATABASE_FOLDER = "data";
    public static final String DATABASE_FILE = "bms.db";

    // JDBC URL format for SQLite: jdbc:sqlite:<path to file>
    public static final String JDBC_URL =
            "jdbc:sqlite:" + DATABASE_FOLDER + "/" + DATABASE_FILE;

    // The account created on the very first run, so you can log in (Phase 6).
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";

    private DatabaseConfig() {
        // Not meant to be instantiated: it only holds constants.
    }
}
