package com.bms.service;

import com.bms.dao.EmployeeDao;
import com.bms.dao.UserDao;
import com.bms.exception.AuthenticationException;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.Employee;
import com.bms.model.User;
import com.bms.util.PasswordHasher;
import com.bms.util.Session;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The login rules. The screen calls this class; this class calls the DAOs. */
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    // One message for every kind of failure, so an attacker cannot tell
    // whether the username or the password was the wrong part.
    private static final String INVALID_LOGIN_MESSAGE = "Invalid username or password.";

    // Used when the username does not exist, so a failed login takes the same
    // time whether or not the username is real.
    private static final String DUMMY_SALT = PasswordHasher.generateSalt();
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password", DUMMY_SALT);

    private final UserDao userDao = new UserDao();
    private final EmployeeDao employeeDao = new EmployeeDao();

    /**
     * Logs a user in. If everything is correct, the user is stored in the Session.
     * If not, an exception explains why (the message is safe to show to the user).
     */
    public void login(String username, String password) throws BmsException {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new ValidationException("Please enter both your username and password.");
        }

        try {
            User user = userDao.findByUsername(username.trim());

            boolean passwordCorrect;
            if (user == null) {
                PasswordHasher.verify(password, DUMMY_SALT, DUMMY_HASH);
                passwordCorrect = false;
            } else {
                passwordCorrect = PasswordHasher.verify(password, user.getSalt(), user.getPasswordHash());
            }

            if (user == null || !passwordCorrect || !user.isActive()) {
                throw new AuthenticationException(INVALID_LOGIN_MESSAGE);
            }

            Employee employee = null;
            if (user.getEmployeeId() != null) {
                employee = employeeDao.findById(user.getEmployeeId());
            }

            Session.start(user, employee);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during login", e);
            throw new DatabaseException(DatabaseErrors.friendly("The system could not read the database. Please try again.", e), e);
        }
    }

    /** Ends the session. */
    public void logout() {
        Session.end();
    }
}
