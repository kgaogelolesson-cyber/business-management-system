package com.bms.database;

import com.bms.util.PasswordHasher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepares the database every time the application starts:
 * 1. creates the data folder (and the database file) if missing,
 * 2. creates any missing tables, indexes, views and default settings,
 * 3. creates the first administrator account if there are no users yet.
 * It is safe to run on every start-up: existing data is never touched.
 */
public final class DatabaseInitializer {

    private DatabaseInitializer() {
    }

    public static void initialise() throws SQLException {
        createDatabaseFolderIfMissing();

        try (Connection connection = DatabaseManager.getConnection()) {
            // One transaction: either everything is created, or nothing is.
            connection.setAutoCommit(false);
            try {
                createSchema(connection);
                createDefaultAdminIfMissing(connection);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : DatabaseSchema.STATEMENTS) {
                statement.execute(sql);
            }
        }
    }

    /** Creates the admin and the employee record the admin needs to process orders. */
    private static void createDefaultAdminIfMissing(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // users already exist: do nothing
            }
        }

        String employeeSql = "INSERT INTO employees "
                + "(first_name, last_name, email, position, department, hire_date) "
                + "VALUES (?, ?, ?, ?, ?, date('now', 'localtime'))";
        try (PreparedStatement ps = connection.prepareStatement(employeeSql)) {
            ps.setString(1, "System");
            ps.setString(2, "Administrator");
            ps.setString(3, "admin@example.com");
            ps.setString(4, "Owner / Manager");
            ps.setString(5, "Management");
            ps.executeUpdate();
        }

        long employeeId;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            employeeId = rs.getLong(1);
        }

        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(DatabaseConfig.DEFAULT_ADMIN_PASSWORD, salt);

        String userSql = "INSERT INTO users (username, password_hash, salt, role, employee_id) "
                + "VALUES (?, ?, ?, 'ADMIN', ?)";
        try (PreparedStatement ps = connection.prepareStatement(userSql)) {
            ps.setString(1, DatabaseConfig.DEFAULT_ADMIN_USERNAME);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setLong(4, employeeId);
            ps.executeUpdate();
        }
    }

    /** Returns a short report (used to prove the database is ready). */
    public static String getDiagnostics() throws SQLException {
        String newLine = System.lineSeparator();
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            String version;
            try (ResultSet rs = statement.executeQuery("SELECT sqlite_version()")) {
                version = rs.next() ? rs.getString(1) : "unknown";
            }

            boolean foreignKeysOn;
            try (ResultSet rs = statement.executeQuery("PRAGMA foreign_keys")) {
                foreignKeysOn = rs.next() && rs.getInt(1) == 1;
            }

            // Table names come from SQLite itself (not from users), so this is safe.
            List<String> tableNames = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' "
                    + "AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                while (rs.next()) {
                    tableNames.add(rs.getString(1));
                }
            }

            StringBuilder report = new StringBuilder();
            Path dbFile = Paths.get(DatabaseConfig.DATABASE_FOLDER, DatabaseConfig.DATABASE_FILE);
            report.append("Database file  : ").append(dbFile.toAbsolutePath()).append(newLine);
            report.append("SQLite version : ").append(version).append(newLine);
            report.append("Foreign keys   : ").append(foreignKeysOn ? "ON" : "OFF").append(newLine);
            report.append("Tables (").append(tableNames.size()).append(") and row counts:").append(newLine);
            for (String table : tableNames) {
                try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    report.append("  ").append(table).append(" = ").append(rs.getInt(1)).append(newLine);
                }
            }
            return report.toString();
        }
    }

    private static void createDatabaseFolderIfMissing() throws SQLException {
        Path folder = Paths.get(DatabaseConfig.DATABASE_FOLDER);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new SQLException("Could not create the database folder: "
                    + folder.toAbsolutePath(), e);
        }
    }
}
