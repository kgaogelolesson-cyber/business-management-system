package com.bms.service;

import com.bms.dao.EmployeeDao;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.EmployeeAccount;
import com.bms.model.Role;
import com.bms.util.PasswordHasher;
import com.bms.util.Session;
import com.bms.util.TextRules;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The rules for employees and their login accounts. This is an ADMINISTRATOR-only area:
 * every method (even viewing) refuses an employee.
 *
 * An employee is never deleted. "Deactivate" hides the employee AND blocks their login, but
 * keeps the row, so old orders that mention them stay correct.
 *
 * Because only an active administrator can use this screen, and an administrator cannot
 * deactivate or change the role of their OWN account, at least one active administrator
 * always remains.
 */
public class EmployeeService {

    private static final Logger LOGGER = Logger.getLogger(EmployeeService.class.getName());

    private static final int MAX_NAME = 50;
    private static final int MAX_EMAIL = 100;
    private static final int MAX_TEXT = 100;
    private static final int MIN_USERNAME = 3;
    private static final int MAX_USERNAME = 30;
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 64;

    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L} .'-]*$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_CHARACTERS = Pattern.compile("^[0-9 +()-]+$");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final LocalDate EARLIEST_HIRE_DATE = LocalDate.of(1950, 1, 1);

    private final EmployeeDao employeeDao = new EmployeeDao();

    // ---------- reading ----------

    public List<EmployeeAccount> search(String text, boolean includeInactive) throws BmsException {
        return search(text, includeInactive, null);
    }

    /** department = null (or blank) means every department. */
    public List<EmployeeAccount> search(String text, boolean includeInactive, String department) throws BmsException {
        Session.requireAdmin();
        String dept = (department == null || department.isBlank()) ? null : department.trim();
        try {
            return employeeDao.search(text == null ? "" : text.trim(), includeInactive, dept);
        } catch (SQLException e) {
            throw databaseProblem("searching employees", "The employees could not be loaded. Please try again.", e);
        }
    }

    public List<String> getDepartments() throws BmsException {
        Session.requireAdmin();
        try {
            return employeeDao.findDepartments();
        } catch (SQLException e) {
            throw databaseProblem("loading departments", "The departments could not be loaded. Please try again.", e);
        }
    }

    // ---------- adding and editing ----------

    /** Checks everything add()/update() will check, without saving. Throws ValidationException with the reason. */
    public void validateEmployee(EmployeeAccount employee) throws BmsException {
        Session.requireAdmin();
        EmployeeAccount cleaned = clean(employee);
        try {
            check(cleaned, employee.getPassword(), employee.getConfirmPassword());
        } catch (SQLException e) {
            throw databaseProblem("checking an employee", "The employee could not be checked. Please try again.", e);
        }
    }

    /** Validates and saves a NEW employee together with their login account. Returns the new employee id. */
    public long add(EmployeeAccount employee) throws BmsException {
        Session.requireAdmin();
        if (employee.getEmployeeId() != 0) {
            throw new ValidationException("This employee already exists. Use Edit to change them.");
        }
        EmployeeAccount cleaned = clean(employee);
        try {
            check(cleaned, employee.getPassword(), employee.getConfirmPassword());
            String salt = PasswordHasher.generateSalt();
            String hash = PasswordHasher.hash(employee.getPassword(), salt);
            return employeeDao.insert(cleaned, hash, salt);
        } catch (SQLException e) {
            throw databaseProblem("adding an employee", "The employee could not be saved. Please try again.", e);
        }
    }

    /** Validates and saves changes to an existing (active) employee. The username and password are not changed here. */
    public void update(EmployeeAccount employee) throws BmsException {
        Session.requireAdmin();
        if (employee.getEmployeeId() == 0) {
            throw new ValidationException("This employee has not been saved yet. Use Add instead.");
        }
        EmployeeAccount cleaned = clean(employee);
        try {
            check(cleaned, null, null);
            if (!employeeDao.update(cleaned)) {
                throw new ValidationException("This employee no longer exists or is deactivated. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("updating an employee", "The employee could not be saved. Please try again.", e);
        }
    }

    // ---------- deactivate / reactivate / password ----------

    /** Deactivates an employee and blocks their login. You cannot deactivate your own account. */
    public void deactivate(long employeeId) throws BmsException {
        Session.requireAdmin();
        if (isCurrentUser(employeeId)) {
            throw new ValidationException("You cannot deactivate your own account.");
        }
        try {
            if (!employeeDao.setActive(employeeId, false)) {
                throw new ValidationException("This employee no longer exists or is already deactivated. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("deactivating an employee", "The employee could not be deactivated. Please try again.", e);
        }
    }

    /** Brings a deactivated employee (and their login) back. */
    public void reactivate(long employeeId) throws BmsException {
        Session.requireAdmin();
        try {
            EmployeeAccount existing = employeeDao.findAccountById(employeeId);
            if (existing == null || existing.isActive()) {
                throw new ValidationException("This employee no longer exists or is already active. Please refresh the list.");
            }
            if (existing.getEmail() != null && employeeDao.emailInUse(existing.getEmail(), employeeId)) {
                throw new ValidationException("Another active employee now uses the email " + existing.getEmail()
                        + ". Change that employee's email first.");
            }
            if (!employeeDao.setActive(employeeId, true)) {
                throw new ValidationException("This employee could not be reactivated. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("reactivating an employee", "The employee could not be reactivated. Please try again.", e);
        }
    }

    /** Sets a new password for an active employee's login account. */
    public void resetPassword(long employeeId, String newPassword, String confirmPassword) throws BmsException {
        Session.requireAdmin();
        try {
            EmployeeAccount existing = employeeDao.findAccountById(employeeId);
            if (existing == null || !existing.isActive()) {
                throw new ValidationException("This employee no longer exists or is deactivated. Please refresh the list.");
            }
            if (existing.getUsername() == null) {
                throw new ValidationException("This employee has no login account.");
            }
            checkPassword(newPassword, confirmPassword, existing.getUsername());

            String salt = PasswordHasher.generateSalt();
            String hash = PasswordHasher.hash(newPassword, salt);
            if (!employeeDao.updatePassword(employeeId, hash, salt)) {
                throw new ValidationException("The password could not be changed. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("resetting a password", "The password could not be changed. Please try again.", e);
        }
    }

    // ---------- helpers ----------

    private boolean isCurrentUser(long employeeId) {
        Long mine = Session.getCurrentUser().getEmployeeId();
        return mine != null && mine == employeeId;
    }

    /** Trims every field; empty optional fields become null. Passwords are not touched here. */
    private EmployeeAccount clean(EmployeeAccount e) {
        EmployeeAccount copy = new EmployeeAccount();
        copy.setEmployeeId(e.getEmployeeId());
        copy.setFirstName(e.getFirstName() == null ? "" : e.getFirstName().trim());
        copy.setLastName(e.getLastName() == null ? "" : e.getLastName().trim());
        copy.setEmail(blankToNull(e.getEmail()));
        copy.setPhone(blankToNull(e.getPhone()));
        copy.setPosition(blankToNull(e.getPosition()));
        copy.setDepartment(blankToNull(e.getDepartment()));
        copy.setHireDate(blankToNull(e.getHireDate()));
        copy.setUsername(e.getUsername() == null ? "" : e.getUsername().trim());
        copy.setRole(e.getRole());
        return copy;
    }

    /**
     * All the rules. A new employee (id 0) also needs a username and a password;
     * an existing one may only change role under the rules below.
     */
    private void check(EmployeeAccount e, String password, String confirmPassword)
            throws ValidationException, SQLException {
        boolean isNew = e.getEmployeeId() == 0;

        checkName(e.getFirstName(), "first name");
        checkName(e.getLastName(), "last name");

        if (e.getEmail() != null) {
            if (e.getEmail().length() > MAX_EMAIL || !EMAIL.matcher(e.getEmail()).matches()) {
                throw new ValidationException("The email address is not valid (example: thandi@company.co.za).");
            }
            if (employeeDao.emailInUse(e.getEmail(), e.getEmployeeId())) {
                throw new ValidationException("Another active employee already uses the email " + e.getEmail() + ".");
            }
        }
        if (e.getPhone() != null) {
            long digits = e.getPhone().chars().filter(Character::isDigit).count();
            if (!PHONE_CHARACTERS.matcher(e.getPhone()).matches() || digits < 7 || digits > 15) {
                throw new ValidationException(
                        "The phone number is not valid. Use 7 to 15 digits (spaces, +, - and brackets are allowed).");
            }
        }
        TextRules.requireSingleLine(e.getPosition(), "position");
        TextRules.requireSingleLine(e.getDepartment(), "department");
        if (e.getPosition() != null && e.getPosition().length() > MAX_TEXT) {
            throw new ValidationException("The position may be at most " + MAX_TEXT + " characters.");
        }
        if (e.getDepartment() != null && e.getDepartment().length() > MAX_TEXT) {
            throw new ValidationException("The department may be at most " + MAX_TEXT + " characters.");
        }
        checkHireDate(e.getHireDate());

        if (e.getRole() == null) {
            throw new ValidationException("Please choose a role.");
        }

        if (isNew) {
            checkUsername(e.getUsername());
            if (employeeDao.usernameInUse(e.getUsername())) {
                throw new ValidationException("The username \"" + e.getUsername() + "\" is already taken.");
            }
            checkPassword(password, confirmPassword, e.getUsername());
        } else {
            EmployeeAccount existing = employeeDao.findAccountById(e.getEmployeeId());
            if (existing == null || !existing.isActive()) {
                throw new ValidationException("This employee no longer exists or is deactivated. Please refresh the list.");
            }
            if (existing.getRole() != null && existing.getRole() != e.getRole() && isCurrentUser(e.getEmployeeId())) {
                throw new ValidationException("You cannot change your own role. Ask another administrator.");
            }
            if (existing.getUsername() == null) {
                throw new ValidationException("This employee has no login account, so a role cannot be set.");
            }
        }
    }

    private void checkName(String value, String label) throws ValidationException {
        if (value.isEmpty()) {
            throw new ValidationException("The " + label + " is required.");
        }
        if (value.length() > MAX_NAME) {
            throw new ValidationException("The " + label + " may be at most " + MAX_NAME + " characters.");
        }
        if (!NAME.matcher(value).matches()) {
            throw new ValidationException("The " + label + " may only contain letters, spaces, apostrophes, dots and hyphens.");
        }
    }

    private void checkHireDate(String text) throws ValidationException {
        if (text == null) {
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(text, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("The hire date must be a real date written as yyyy-MM-dd (example: 2026-03-15).");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("The hire date cannot be in the future.");
        }
        if (date.isBefore(EARLIEST_HIRE_DATE)) {
            throw new ValidationException("The hire date cannot be before 1950-01-01.");
        }
    }

    private void checkUsername(String username) throws ValidationException {
        if (username.isEmpty()) {
            throw new ValidationException("The username is required.");
        }
        if (username.length() < MIN_USERNAME || username.length() > MAX_USERNAME) {
            throw new ValidationException("The username must be " + MIN_USERNAME + " to " + MAX_USERNAME + " characters long.");
        }
        if (!USERNAME.matcher(username).matches()) {
            throw new ValidationException("The username may only contain letters, numbers, dots, underscores and hyphens (no spaces).");
        }
    }

    private void checkPassword(String password, String confirmPassword, String username) throws ValidationException {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("The password is required.");
        }
        if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new ValidationException("The password must be " + MIN_PASSWORD + " to " + MAX_PASSWORD + " characters long.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("The password must contain at least one letter and one number.");
        }
        if (password.equalsIgnoreCase(username)) {
            throw new ValidationException("The password may not be the same as the username.");
        }
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("The two passwords do not match.");
        }
    }

    private String blankToNull(String text) {
        String t = (text == null) ? "" : text.trim();
        return t.isEmpty() ? null : t;
    }

    private DatabaseException databaseProblem(String action, String friendlyMessage, SQLException e) {
        LOGGER.log(Level.SEVERE, "Database error while " + action, e);
        return new DatabaseException(DatabaseErrors.friendly(friendlyMessage, e), e);
    }
}
