package com.bms.service;

import com.bms.dao.InventoryDao;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.Product;
import com.bms.model.StockAdjustment;
import com.bms.util.Session;
import com.bms.util.TextRules;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The rules for inventory.
 * Viewing stock and its history: any logged-in user (administrator or employee).
 * Changing stock by hand: administrator only. (Orders will change stock too, in a later phase.)
 * Stock can never go below 0, and every change needs a reason and is recorded in the history.
 */
public class InventoryService {

    private static final Logger LOGGER = Logger.getLogger(InventoryService.class.getName());

    public static final int MAX_CHANGE = 100_000;     // largest single adjustment
    public static final int MAX_REASON = 200;

    /** The choices of the status filter. dbValue is what the v_inventory view calls the status. */
    public enum StockFilter {
        ALL("All products", null),
        IN_STOCK("In stock", "IN STOCK"),
        LOW_STOCK("Low stock", "LOW STOCK"),
        OUT_OF_STOCK("Out of stock", "OUT OF STOCK");

        private final String label;
        private final String dbValue;

        StockFilter(String label, String dbValue) {
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

    private final InventoryDao inventoryDao = new InventoryDao();

    public List<Product> search(String text, StockFilter filter) throws BmsException {
        Session.requireLoggedIn();
        try {
            return inventoryDao.search(text == null ? "" : text.trim(),
                    filter == null ? null : filter.getDbValue());
        } catch (SQLException e) {
            throw databaseProblem("loading the inventory", "The inventory could not be loaded. Please try again.", e);
        }
    }

    public List<StockAdjustment> getHistory(long productId) throws BmsException {
        Session.requireLoggedIn();
        try {
            return inventoryDao.findHistory(productId);
        } catch (SQLException e) {
            throw databaseProblem("loading stock history", "The stock history could not be loaded. Please try again.", e);
        }
    }

    /**
     * Adds stock (change is positive) or removes stock (change is negative) and records why.
     * Returns the new stock level.
     */
    public int adjustStock(long productId, int change, String reason) throws BmsException {
        Session.requireAdmin();

        String cleanReason = (reason == null) ? "" : reason.trim();
        if (change == 0) {
            throw new ValidationException("The quantity must be at least 1.");
        }
        if (Math.abs((long) change) > MAX_CHANGE) {
            throw new ValidationException("You can change the stock by at most " + MAX_CHANGE + " in one go.");
        }
        if (cleanReason.isEmpty()) {
            throw new ValidationException("Please give a reason (for example: Delivery received, Damaged goods, Stock count).");
        }
        TextRules.requireSingleLine(cleanReason, "reason");
        if (cleanReason.length() > MAX_REASON) {
            throw new ValidationException("The reason may be at most " + MAX_REASON + " characters.");
        }

        try {
            int current = inventoryDao.findStock(productId);
            if (current < 0) {
                throw new ValidationException("This product no longer exists. Please refresh the list.");
            }
            if (current + (long) change < 0) {
                throw new ValidationException("Only " + current + " in stock, so you cannot remove " + (-change) + ".");
            }
            int newStock = inventoryDao.adjust(productId, change, cleanReason, Session.getCurrentUser().getUserId());
            if (newStock < 0) {
                // The stock changed (or the product was deleted) between the check above and the save.
                throw new ValidationException("The stock changed in the meantime. Please refresh the list and try again.");
            }
            return newStock;
        } catch (SQLException e) {
            throw databaseProblem("adjusting stock", "The stock could not be changed. Please try again.", e);
        }
    }

    private DatabaseException databaseProblem(String action, String friendlyMessage, SQLException e) {
        LOGGER.log(Level.SEVERE, "Database error while " + action, e);
        return new DatabaseException(DatabaseErrors.friendly(friendlyMessage, e), e);
    }
}
