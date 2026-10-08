package com.bms.service;

import com.bms.dao.SupplierDao;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.Supplier;
import com.bms.util.Session;
import com.bms.util.TextRules;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The rules for suppliers. Suppliers are an ADMINISTRATOR-only area:
 * every method here (even viewing) refuses an employee.
 * A supplier cannot be deleted while active products still come from it.
 */
public class SupplierService {

    private static final Logger LOGGER = Logger.getLogger(SupplierService.class.getName());

    private static final int MAX_NAME = 100;
    private static final int MAX_CONTACT = 100;
    private static final int MAX_EMAIL = 100;
    private static final int MAX_ADDRESS = 200;
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_CHARACTERS = Pattern.compile("^[0-9 +()-]+$");

    private final SupplierDao supplierDao = new SupplierDao();

    public List<Supplier> search(String text) throws BmsException {
        Session.requireAdmin();
        try {
            return supplierDao.search(text == null ? "" : text.trim());
        } catch (SQLException e) {
            throw databaseProblem("searching suppliers", "The suppliers could not be loaded. Please try again.", e);
        }
    }

    /** Checks everything add()/update() will check, without saving. Throws ValidationException with the reason. */
    public void validateSupplier(Supplier supplier) throws BmsException {
        Session.requireAdmin();
        Supplier cleaned = clean(supplier);
        try {
            check(cleaned);
        } catch (SQLException e) {
            throw databaseProblem("checking a supplier", "The supplier could not be checked. Please try again.", e);
        }
    }

    /** Validates and saves a NEW supplier. Returns the new id. */
    public long add(Supplier supplier) throws BmsException {
        Session.requireAdmin();
        Supplier cleaned = clean(supplier);
        try {
            check(cleaned);
            return supplierDao.insert(cleaned);
        } catch (SQLException e) {
            throw databaseProblem("adding a supplier", "The supplier could not be saved. Please try again.", e);
        }
    }

    /** Validates and saves changes to an existing supplier. */
    public void update(Supplier supplier) throws BmsException {
        Session.requireAdmin();
        Supplier cleaned = clean(supplier);
        try {
            check(cleaned);
            if (!supplierDao.update(cleaned)) {
                throw new ValidationException("This supplier no longer exists. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("updating a supplier", "The supplier could not be saved. Please try again.", e);
        }
    }

    /** Hides a supplier (soft delete). Refused while active products still come from it. */
    public void delete(long supplierId) throws BmsException {
        Session.requireAdmin();
        try {
            int products = supplierDao.countActiveProducts(supplierId);
            if (products > 0) {
                throw new ValidationException("This supplier provides " + products + " product"
                        + (products == 1 ? "" : "s") + " and cannot be deleted. "
                        + "Change or delete those products first.");
            }
            if (!supplierDao.deactivate(supplierId)) {
                throw new ValidationException("This supplier no longer exists. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("deleting a supplier", "The supplier could not be deleted. Please try again.", e);
        }
    }

    // ---------- helpers ----------

    /** Trims every field; empty optional fields become null. */
    private Supplier clean(Supplier s) {
        Supplier copy = new Supplier();
        copy.setSupplierId(s.getSupplierId());
        copy.setSupplierName(s.getSupplierName() == null ? "" : s.getSupplierName().trim());
        copy.setContactPerson(blankToNull(s.getContactPerson()));
        copy.setEmail(blankToNull(s.getEmail()));
        copy.setPhone(blankToNull(s.getPhone()));
        copy.setAddress(blankToNull(s.getAddress()));
        return copy;
    }

    /** All the rules. Throws ValidationException for bad data, SQLException if the database fails. */
    private void check(Supplier s) throws ValidationException, SQLException {
        if (s.getSupplierName().isEmpty()) {
            throw new ValidationException("The supplier name is required.");
        }
        TextRules.requireSingleLine(s.getSupplierName(), "supplier name");
        TextRules.requireSingleLine(s.getContactPerson(), "contact person");
        TextRules.requireSingleLine(s.getAddress(), "address");
        if (s.getSupplierName().length() > MAX_NAME) {
            throw new ValidationException("The supplier name may be at most " + MAX_NAME + " characters.");
        }
        if (s.getContactPerson() != null && s.getContactPerson().length() > MAX_CONTACT) {
            throw new ValidationException("The contact person may be at most " + MAX_CONTACT + " characters.");
        }
        if (s.getEmail() != null && (s.getEmail().length() > MAX_EMAIL || !EMAIL.matcher(s.getEmail()).matches())) {
            throw new ValidationException("The email address is not valid (example: orders@supplier.co.za).");
        }
        if (s.getPhone() != null) {
            long digits = s.getPhone().chars().filter(Character::isDigit).count();
            if (!PHONE_CHARACTERS.matcher(s.getPhone()).matches() || digits < 7 || digits > 15) {
                throw new ValidationException(
                        "The phone number is not valid. Use 7 to 15 digits (spaces, +, - and brackets are allowed).");
            }
        }
        if (s.getAddress() != null && s.getAddress().length() > MAX_ADDRESS) {
            throw new ValidationException("The address may be at most " + MAX_ADDRESS + " characters.");
        }
        if (supplierDao.nameInUse(s.getSupplierName(), s.getSupplierId())) {
            throw new ValidationException("A supplier called \"" + s.getSupplierName() + "\" already exists.");
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
