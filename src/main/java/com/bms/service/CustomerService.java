package com.bms.service;

import com.bms.dao.CustomerDao;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import com.bms.util.Session;
import com.bms.util.TextRules;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The rules for customers: who may do what, and what counts as valid data.
 * Administrator and Employee may view, add and edit customers.
 * Only the Administrator may delete one, and never one that has orders.
 */
public class CustomerService {

    private static final Logger LOGGER = Logger.getLogger(CustomerService.class.getName());

    private static final int MAX_NAME = 50;
    private static final int MAX_EMAIL = 100;
    private static final int MAX_ADDRESS = 200;
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_CHARACTERS = Pattern.compile("^[0-9 +()-]+$");

    private final CustomerDao customerDao = new CustomerDao();

    public List<Customer> search(String text) throws BmsException {
        Session.requireLoggedIn();
        try {
            return customerDao.search(text == null ? "" : text.trim());
        } catch (SQLException e) {
            throw databaseProblem("searching customers", "The customers could not be loaded. Please try again.", e);
        }
    }

    /** Checks the data against the rules without saving. Throws ValidationException with the reason. */
    public void validateCustomer(Customer customer) throws BmsException {
        Session.requireLoggedIn();
        validate(clean(customer));
    }

    /** Other customers who already use this email or phone. The screen shows them as a warning. */
    public List<Customer> findPossibleDuplicates(Customer customer) throws BmsException {
        Session.requireLoggedIn();
        Customer cleaned = clean(customer);
        try {
            return customerDao.findDuplicates(cleaned.getEmail(), cleaned.getPhone(), cleaned.getCustomerId());
        } catch (SQLException e) {
            throw databaseProblem("checking duplicate customers", "The customer could not be checked. Please try again.", e);
        }
    }

    /** Validates and saves a NEW customer. Returns the new id. */
    public long add(Customer customer) throws BmsException {
        Session.requireLoggedIn();
        Customer cleaned = clean(customer);
        validate(cleaned);
        try {
            return customerDao.insert(cleaned);
        } catch (SQLException e) {
            throw databaseProblem("adding a customer", "The customer could not be saved. Please try again.", e);
        }
    }

    /** Validates and saves changes to an existing customer. */
    public void update(Customer customer) throws BmsException {
        Session.requireLoggedIn();
        Customer cleaned = clean(customer);
        validate(cleaned);
        try {
            if (!customerDao.update(cleaned)) {
                throw new ValidationException("This customer no longer exists. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("updating a customer", "The customer could not be saved. Please try again.", e);
        }
    }

    /** Hides a customer (soft delete). Administrator only; refused when the customer has orders. */
    public void delete(long customerId) throws BmsException {
        Session.requireAdmin();
        try {
            int orders = customerDao.countOrders(customerId);
            if (orders > 0) {
                throw new ValidationException("This customer has " + orders + " order"
                        + (orders == 1 ? "" : "s") + " and cannot be deleted. "
                        + "Their order history must be kept.");
            }
            if (!customerDao.deactivate(customerId)) {
                throw new ValidationException("This customer no longer exists. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("deleting a customer", "The customer could not be deleted. Please try again.", e);
        }
    }

    // ---------- helpers ----------

    /** Trims every field; empty optional fields become null (stored as "no value"). */
    private Customer clean(Customer c) {
        Customer copy = new Customer();
        copy.setCustomerId(c.getCustomerId());
        copy.setFirstName(trim(c.getFirstName()));
        copy.setLastName(trim(c.getLastName()));
        copy.setEmail(blankToNull(c.getEmail()));
        copy.setPhone(blankToNull(c.getPhone()));
        copy.setAddress(blankToNull(c.getAddress()));
        return copy;
    }

    private void validate(Customer c) throws ValidationException {
        if (c.getFirstName().isEmpty()) {
            throw new ValidationException("First name is required.");
        }
        if (c.getLastName().isEmpty()) {
            throw new ValidationException("Last name is required.");
        }
        TextRules.requireSingleLine(c.getFirstName(), "first name");
        TextRules.requireSingleLine(c.getLastName(), "last name");
        TextRules.requireSingleLine(c.getAddress(), "address");
        if (c.getFirstName().length() > MAX_NAME || c.getLastName().length() > MAX_NAME) {
            throw new ValidationException("Names may be at most " + MAX_NAME + " characters.");
        }
        if (c.getEmail() != null) {
            if (c.getEmail().length() > MAX_EMAIL || !EMAIL.matcher(c.getEmail()).matches()) {
                throw new ValidationException("The email address is not valid (example: name@example.com).");
            }
        }
        if (c.getPhone() != null) {
            long digits = c.getPhone().chars().filter(Character::isDigit).count();
            if (!PHONE_CHARACTERS.matcher(c.getPhone()).matches() || digits < 7 || digits > 15) {
                throw new ValidationException(
                        "The phone number is not valid. Use 7 to 15 digits (spaces, +, - and brackets are allowed).");
            }
        }
        if (c.getAddress() != null && c.getAddress().length() > MAX_ADDRESS) {
            throw new ValidationException("The address may be at most " + MAX_ADDRESS + " characters.");
        }
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private String blankToNull(String text) {
        String t = trim(text);
        return t.isEmpty() ? null : t;
    }

    private DatabaseException databaseProblem(String action, String friendlyMessage, SQLException e) {
        LOGGER.log(Level.SEVERE, "Database error while " + action, e);
        return new DatabaseException(DatabaseErrors.friendly(friendlyMessage, e), e);
    }
}
