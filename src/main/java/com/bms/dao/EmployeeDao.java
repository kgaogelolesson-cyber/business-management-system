package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.Employee;
import com.bms.model.EmployeeAccount;
import com.bms.model.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the employees table (and the login account that belongs to each employee)
 * lives here. Anything that changes BOTH tables runs as one transaction: either both
 * changes are saved or neither is.
 */
public class EmployeeDao {

    private static final String ACCOUNT_COLUMNS =
            "SELECT e.employee_id, e.first_name, e.last_name, e.email, e.phone, e.position, "
            + "       e.department, e.hire_date, e.is_active, u.username, u.role "
            + "FROM employees e LEFT JOIN users u ON u.employee_id = e.employee_id ";

    /** Returns the employee, or null if there is no such employee. (Used at login.) */
    public Employee findById(long employeeId) throws SQLException {
        String sql = "SELECT employee_id, first_name, last_name, email, phone, "
                + "position, department, hire_date, is_active "
                + "FROM employees WHERE employee_id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, employeeId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String hireDateText = rs.getString("hire_date");
                LocalDate hireDate = (hireDateText == null || hireDateText.isBlank())
                        ? null : LocalDate.parse(hireDateText);

                return new Employee(
                        rs.getLong("employee_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("position"),
                        rs.getString("department"),
                        hireDate,
                        rs.getInt("is_active") == 1);
            }
        }
    }

    /** One employee with login details (active or not), or null if there is no such employee. */
    public EmployeeAccount findAccountById(long employeeId) throws SQLException {
        String sql = ACCOUNT_COLUMNS + "WHERE e.employee_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, employeeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readAccount(rs) : null;
            }
        }
    }

    /**
     * Employees whose name, username, email, phone, position or department contains the search text.
     * An empty text returns everyone. Deactivated employees are only included if includeInactive is true.
     */
    public List<EmployeeAccount> search(String text, boolean includeInactive) throws SQLException {
        return search(text, includeInactive, null);
    }

    /** As above, plus department = only that department (capital letters ignored), or null for all. */
    public List<EmployeeAccount> search(String text, boolean includeInactive, String department) throws SQLException {
        String sql = ACCOUNT_COLUMNS
                + "WHERE (? = 1 OR e.is_active = 1) "
                + "AND (? IS NULL OR LOWER(IFNULL(e.department, '')) = LOWER(?)) AND ("
                + "   (e.first_name || ' ' || e.last_name) LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(u.username, '') LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(e.email, '') LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(e.phone, '') LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(e.position, '') LIKE ? ESCAPE '\\' "
                + "   OR IFNULL(e.department, '') LIKE ? ESCAPE '\\') "
                + "ORDER BY e.is_active DESC, e.first_name COLLATE NOCASE, e.last_name COLLATE NOCASE";

        String pattern = "%" + escapeLike(text) + "%";
        List<EmployeeAccount> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, includeInactive ? 1 : 0);
            statement.setString(2, department);
            statement.setString(3, department);
            for (int i = 4; i <= 9; i++) {
                statement.setString(i, pattern);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readAccount(rs));
                }
            }
        }
        return result;
    }

    /** Saves a new employee AND their login account in one transaction. Returns the new employee id. */
    public long insert(EmployeeAccount a, String passwordHash, String salt) throws SQLException {
        String employeeSql = "INSERT INTO employees (first_name, last_name, email, phone, position, department, hire_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String userSql = "INSERT INTO users (username, password_hash, salt, role, employee_id) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long newId;
                try (PreparedStatement statement = connection.prepareStatement(employeeSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, a.getFirstName());
                    statement.setString(2, a.getLastName());
                    statement.setString(3, a.getEmail());
                    statement.setString(4, a.getPhone());
                    statement.setString(5, a.getPosition());
                    statement.setString(6, a.getDepartment());
                    statement.setString(7, a.getHireDate());
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        newId = keys.next() ? keys.getLong(1) : 0;
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(userSql)) {
                    statement.setString(1, a.getUsername());
                    statement.setString(2, passwordHash);
                    statement.setString(3, salt);
                    statement.setString(4, a.getRole().name());
                    statement.setLong(5, newId);
                    statement.executeUpdate();
                }
                connection.commit();
                return newId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Updates an ACTIVE employee's details and role. Returns true if a row was changed. */
    public boolean update(EmployeeAccount a) throws SQLException {
        String employeeSql = "UPDATE employees SET first_name = ?, last_name = ?, email = ?, phone = ?, "
                + "position = ?, department = ?, hire_date = ? WHERE employee_id = ? AND is_active = 1";
        String userSql = "UPDATE users SET role = ? WHERE employee_id = ?";

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(employeeSql)) {
                    statement.setString(1, a.getFirstName());
                    statement.setString(2, a.getLastName());
                    statement.setString(3, a.getEmail());
                    statement.setString(4, a.getPhone());
                    statement.setString(5, a.getPosition());
                    statement.setString(6, a.getDepartment());
                    statement.setString(7, a.getHireDate());
                    statement.setLong(8, a.getEmployeeId());
                    changed = statement.executeUpdate();
                }
                if (changed == 1) {
                    try (PreparedStatement statement = connection.prepareStatement(userSql)) {
                        statement.setString(1, a.getRole().name());
                        statement.setLong(2, a.getEmployeeId());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return changed == 1;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Deactivates (active = false) or reactivates (active = true) an employee AND their login account.
     * Returns false if the employee was not in the opposite state (so nothing changed).
     */
    public boolean setActive(long employeeId, boolean active) throws SQLException {
        String employeeSql = "UPDATE employees SET is_active = ? WHERE employee_id = ? AND is_active = ?";
        String userSql = "UPDATE users SET is_active = ? WHERE employee_id = ?";
        int newValue = active ? 1 : 0;

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(employeeSql)) {
                    statement.setInt(1, newValue);
                    statement.setLong(2, employeeId);
                    statement.setInt(3, 1 - newValue);
                    changed = statement.executeUpdate();
                }
                if (changed == 1) {
                    try (PreparedStatement statement = connection.prepareStatement(userSql)) {
                        statement.setInt(1, newValue);
                        statement.setLong(2, employeeId);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return changed == 1;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Stores a new password hash for the employee's login account. Returns true if an account was changed. */
    public boolean updatePassword(long employeeId, String passwordHash, String salt) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, salt = ? WHERE employee_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setString(2, salt);
            statement.setLong(3, employeeId);
            return statement.executeUpdate() == 1;
        }
    }

    /** Is this username taken (by any account, active or not)? Capital letters are ignored. */
    public boolean usernameInUse(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Is another ACTIVE employee already using this email? excludeId = the employee being edited, or 0. */
    public boolean emailInUse(String email, long excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM employees WHERE is_active = 1 "
                + "AND LOWER(email) = LOWER(?) AND employee_id <> ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setLong(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ---------- helpers ----------

    private EmployeeAccount readAccount(ResultSet rs) throws SQLException {
        EmployeeAccount a = new EmployeeAccount();
        a.setEmployeeId(rs.getLong("employee_id"));
        a.setFirstName(rs.getString("first_name"));
        a.setLastName(rs.getString("last_name"));
        a.setEmail(rs.getString("email"));
        a.setPhone(rs.getString("phone"));
        a.setPosition(rs.getString("position"));
        a.setDepartment(rs.getString("department"));
        a.setHireDate(rs.getString("hire_date"));
        a.setActive(rs.getInt("is_active") == 1);
        a.setUsername(rs.getString("username"));
        String role = rs.getString("role");
        a.setRole(role == null ? null : Role.fromDatabase(role));
        return a;
    }

    /** Makes % and _ typed by the user match literally instead of acting as wildcards. */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Each department used by an employee, once (capital letters ignored), in alphabetical order. */
    public List<String> findDepartments() throws SQLException {
        String sql = "SELECT MIN(department) FROM employees "
                + "WHERE department IS NOT NULL AND TRIM(department) <> '' "
                + "GROUP BY LOWER(department) ORDER BY LOWER(MIN(department))";
        List<String> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }
}
