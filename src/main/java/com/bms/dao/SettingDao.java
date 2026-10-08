package com.bms.dao;

import com.bms.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** All SQL for the settings table (business name, currency symbol, ...). */
public class SettingDao {

    /** Returns the setting's value, or defaultValue if the setting does not exist. */
    public String getValue(String key, String defaultValue) throws SQLException {
        String sql = "SELECT setting_value FROM settings WHERE setting_key = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : defaultValue;
            }
        }
    }
}
