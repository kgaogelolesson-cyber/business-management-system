package com.bms.service;

import com.bms.dao.CustomerDao;
import com.bms.dao.InventoryDao;
import com.bms.dao.OrderDao;
import com.bms.dao.SettingDao;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.CustomerOrder;
import com.bms.model.Employee;
import com.bms.model.OrderLine;
import com.bms.util.Session;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The rules for orders.
 * - Any logged-in user with an employee record may create an order. The order is recorded
 *   against THAT employee (never typed in by the user).
 * - An administrator sees every order. An employee sees only their own.
 * - Creating an order reduces the stock and writes stock history lines, all in one transaction.
 * - Only an administrator may cancel an order. Cancelling puts the stock back (also in one transaction).
 */
public class OrderService {

    private static final Logger LOGGER = Logger.getLogger(OrderService.class.getName());

    public static final int MAX_LINES = 50;
    public static final int MAX_QUANTITY = 10_000;

    /** The choices of the status filter. dbValue is what the orders table stores. */
    public enum StatusFilter {
        ALL("All orders", null),
        COMPLETED("Completed", CustomerOrder.COMPLETED),
        CANCELLED("Cancelled", CustomerOrder.CANCELLED);

        private final String label;
        private final String dbValue;

        StatusFilter(String label, String dbValue) {
            this.label = label;
            this.dbValue = dbValue;
        }

        public String getLabel() {
            return label;
        }

        String getDbValue() {
            return dbValue;
        }
    }

    /** The most orders one search returns. When a list is exactly this long, older orders may be hidden. */
    public static final int MAX_LIST_ROWS = OrderDao.LIST_LIMIT;

    private final OrderDao orderDao = new OrderDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final InventoryDao inventoryDao = new InventoryDao();
    private final SettingDao settingDao = new SettingDao();

    // ---------- reading ----------

    /** Orders matching the text and the status filter (no date or customer limit). */
    public List<CustomerOrder> search(String text, StatusFilter filter) throws BmsException {
        return search(text, filter, null, null, null);
    }

    /**
     * Orders matching the text, the status filter, an optional date range and an optional customer.
     * fromText / toText are typed as yyyy-MM-dd (blank = no limit, both ends included).
     * An employee only gets their own orders, whatever filters are used.
     */
    public List<CustomerOrder> search(String text, StatusFilter filter, String fromText, String toText,
                                      Long customerId) throws BmsException {
        Session.requireLoggedIn();
        LocalDate from = parseOptionalDate(fromText, "From");
        LocalDate to = parseOptionalDate(toText, "To");
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("The From date cannot be after the To date.");
        }
        Long employeeId = null;
        if (!Session.isAdmin()) {
            Employee me = Session.getCurrentEmployee();
            if (me == null) {
                return List.of();   // an account without an employee record has no orders
            }
            employeeId = me.getEmployeeId();
        }
        try {
            return orderDao.search(text == null ? "" : text.trim(),
                    filter == null ? null : filter.getDbValue(), employeeId, customerId,
                    from == null ? null : from.toString(), to == null ? null : to.toString());
        } catch (SQLException e) {
            throw databaseProblem("loading orders", "The orders could not be loaded. Please try again.", e);
        }
    }

    /** Every order of one customer, newest first (an employee only sees the ones they processed). */
    public List<CustomerOrder> getCustomerHistory(long customerId) throws BmsException {
        return search("", StatusFilter.ALL, null, null, customerId);
    }

    /** A blank text means "no limit" (null); anything else must be a real yyyy-MM-dd date. */
    private static LocalDate parseOptionalDate(String text, String fieldName) throws ValidationException {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("The " + fieldName
                    + " date must be a real date written as yyyy-MM-dd (example: 2026-10-01).");
        }
    }

    /** The lines of one order. An employee may only open their own orders. */
    public List<OrderLine> getLines(long orderId) throws BmsException {
        Session.requireLoggedIn();
        try {
            CustomerOrder order = orderDao.findById(orderId);
            if (order == null) {
                throw new ValidationException("This order no longer exists. Please refresh the list.");
            }
            requireMayView(order);
            return orderDao.findLines(orderId);
        } catch (SQLException e) {
            throw databaseProblem("loading an order", "The order could not be loaded. Please try again.", e);
        }
    }

    /** The currency symbol from the settings table ("R" unless changed). */
    public String getCurrencySymbol() throws BmsException {
        Session.requireLoggedIn();
        try {
            return settingDao.getValue("currency_symbol", "R");
        } catch (SQLException e) {
            throw databaseProblem("loading settings", "The settings could not be loaded. Please try again.", e);
        }
    }

    // ---------- creating ----------

    /**
     * Validates and saves a NEW order for the given customer. Only productId and quantity of each
     * line are used. Returns the new order number.
     */
    public long create(long customerId, List<OrderLine> lines) throws BmsException {
        Session.requireLoggedIn();
        Employee me = Session.getCurrentEmployee();
        if (me == null) {
            throw new ValidationException("Your account is not linked to an employee record, so you cannot create orders.");
        }
        try {
            if (customerDao.findById(customerId) == null) {
                throw new ValidationException("Please choose a customer. (The customer may have been deleted.)");
            }
            if (lines == null || lines.isEmpty()) {
                throw new ValidationException("Add at least one product to the order.");
            }
            if (lines.size() > MAX_LINES) {
                throw new ValidationException("An order may have at most " + MAX_LINES + " different products.");
            }

            Set<Long> seen = new HashSet<>();
            for (OrderLine line : lines) {
                if (!seen.add(line.getProductId())) {
                    throw new ValidationException("The same product is on the order twice. Combine them into one line.");
                }
                if (line.getQuantity() < 1) {
                    throw new ValidationException("Every quantity must be at least 1.");
                }
                if (line.getQuantity() > MAX_QUANTITY) {
                    throw new ValidationException("A quantity may be at most " + MAX_QUANTITY + ".");
                }
                int stock = inventoryDao.findStock(line.getProductId());
                if (stock < 0) {
                    throw new ValidationException("A product on the order no longer exists. Please remove it and try again.");
                }
                if (stock < line.getQuantity()) {
                    throw new ValidationException("Only " + stock + " in stock for \"" + line.getProductName()
                            + "\" (you asked for " + line.getQuantity() + ").");
                }
            }

            long orderId = orderDao.create(customerId, me.getEmployeeId(), Session.getCurrentUser().getUserId(), lines);
            if (orderId < 0) {
                // The stock changed (or a product was deleted) between the check above and the save.
                throw new ValidationException("The stock changed while you were working. Nothing was saved. Please try again.");
            }
            return orderId;
        } catch (SQLException e) {
            throw databaseProblem("creating an order", "The order could not be saved. Please try again.", e);
        }
    }

    // ---------- cancelling (Administrator only) ----------

    /** Who cancelled the order and when, or null if it is not cancelled. Same visibility rule as getLines. */
    public String getCancellationInfo(long orderId) throws BmsException {
        Session.requireLoggedIn();
        try {
            CustomerOrder order = orderDao.findById(orderId);
            if (order == null) {
                throw new ValidationException("This order no longer exists. Please refresh the list.");
            }
            requireMayView(order);
            return orderDao.findCancellation(orderId);
        } catch (SQLException e) {
            throw databaseProblem("loading an order", "The order could not be loaded. Please try again.", e);
        }
    }

    /**
     * Cancels a COMPLETED order and puts its products back into stock.
     * Administrator only. A cancelled order stays in the list (greyed out) for the records.
     */
    public void cancel(long orderId) throws BmsException {
        Session.requireAdmin();
        try {
            CustomerOrder order = orderDao.findById(orderId);
            if (order == null) {
                throw new ValidationException("This order no longer exists. Please refresh the list.");
            }
            if (CustomerOrder.CANCELLED.equals(order.getStatus())) {
                throw new ValidationException("Order #" + orderId + " is already cancelled.");
            }
            if (!orderDao.cancel(orderId, Session.getCurrentUser().getUserId())) {
                throw new ValidationException("Order #" + orderId + " could not be cancelled. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("cancelling an order", "The order could not be cancelled. Please try again.", e);
        }
    }

    // ---------- helpers ----------

    private void requireMayView(CustomerOrder order) throws AccessDeniedException {
        if (Session.isAdmin()) {
            return;
        }
        Employee me = Session.getCurrentEmployee();
        if (me == null || me.getEmployeeId() != order.getEmployeeId()) {
            throw new AccessDeniedException("You can only view your own orders.");
        }
    }

    private DatabaseException databaseProblem(String action, String friendlyMessage, SQLException e) {
        LOGGER.log(Level.SEVERE, "Database error while " + action, e);
        return new DatabaseException(DatabaseErrors.friendly(friendlyMessage, e), e);
    }
}
