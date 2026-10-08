package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.Role;
import com.bms.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** All SQL for the users table lives here (and nowhere else). */
public class UserDao {

    /** Finds a user by username (upper/lower case does not matter). Returns null if not found. */
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, salt, role, employee_id, is_active "
                + "FROM users WHERE username = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long employeeId = rs.getLong("employee_id");
                Long employeeIdOrNull = rs.wasNull() ? null : employeeId;

                return new User(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("salt"),
                        Role.fromDatabase(rs.getString("role")),
                        employeeIdOrNull,
                        rs.getInt("is_active") == 1);
            }
        }
    }
}
