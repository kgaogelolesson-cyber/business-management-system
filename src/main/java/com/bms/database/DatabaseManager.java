package com.bms.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens connections to the SQLite database.
 * Every other class that needs the database asks this class for a connection.
 */
public final class DatabaseManager {

    private DatabaseManager() {
    }

    /**
     * Opens a new connection with foreign keys switched ON.
     * SQLite turns foreign keys off by default, and the setting only lasts
     * for one connection, so we must switch it on every time.
     * The caller must close the connection (use try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DatabaseConfig.JDBC_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
