package com.bms.service;

import com.bms.dao.ProductDao;
import com.bms.dao.SettingDao;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.Product;
import com.bms.model.SupplierOption;
import com.bms.util.Session;
import com.bms.util.TextRules;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The rules for products. Everyone logged in may VIEW products.
 * Only the Administrator may add, edit or delete them.
 */
public class ProductService {

    private static final Logger LOGGER = Logger.getLogger(ProductService.class.getName());

    private static final int MAX_NAME = 100;
    private static final int MAX_CATEGORY = 50;
    private static final int MAX_DESCRIPTION = 500;
    private static final long MAX_PRICE_CENTS = 1_000_000_000L;   // R10,000,000.00
    private static final int MAX_QUANTITY = 1_000_000;

    private final ProductDao productDao = new ProductDao();
    private final SettingDao settingDao = new SettingDao();

    // ---------- reading (any logged-in user) ----------

    /** category = null (or blank) means all categories. */
    public List<Product> search(String text, String category) throws BmsException {
        return search(text, category, InventoryService.StockFilter.ALL);
    }

    /** As above, plus a stock-status filter (the same filter choices the Inventory tab uses). */
    public List<Product> search(String text, String category, InventoryService.StockFilter stock) throws BmsException {
        Session.requireLoggedIn();
        String cat = (category == null || category.isBlank()) ? null : category.trim();
        String status = (stock == null) ? null : stock.getDbValue();
        try {
            return productDao.search(text == null ? "" : text.trim(), cat, status);
        } catch (SQLException e) {
            throw databaseProblem("searching products", "The products could not be loaded. Please try again.", e);
        }
    }

    public List<String> getCategories() throws BmsException {
        Session.requireLoggedIn();
        try {
            return productDao.findCategories();
        } catch (SQLException e) {
            throw databaseProblem("loading product categories", "The categories could not be loaded. Please try again.", e);
        }
    }

    public List<SupplierOption> getSupplierOptions() throws BmsException {
        Session.requireLoggedIn();
        try {
            return productDao.findSupplierOptions();
        } catch (SQLException e) {
            throw databaseProblem("loading suppliers", "The suppliers could not be loaded. Please try again.", e);
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

    /** The "default low-stock level" setting, used to pre-fill the form for a new product. */
    public int getDefaultReorderLevel() throws BmsException {
        Session.requireLoggedIn();
        try {
            String value = settingDao.getValue("default_low_stock_level", "5");
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 5;
        } catch (SQLException e) {
            throw databaseProblem("loading settings", "The settings could not be loaded. Please try again.", e);
        }
    }

    // ---------- changing (Administrator only) ----------

    /** Checks everything add()/update() will check, without saving. Throws ValidationException with the reason. */
    public void validateProduct(Product product) throws BmsException {
        Session.requireAdmin();
        Product cleaned = clean(product);
        try {
            check(cleaned);
        } catch (SQLException e) {
            throw databaseProblem("checking a product", "The product could not be checked. Please try again.", e);
        }
    }

    /** Validates and saves a NEW product. Returns the new id. */
    public long add(Product product) throws BmsException {
        Session.requireAdmin();
        Product cleaned = clean(product);
        try {
            check(cleaned);
            return productDao.insert(cleaned, Session.getCurrentUser().getUserId());
        } catch (SQLException e) {
            throw databaseProblem("adding a product", "The product could not be saved. Please try again.", e);
        }
    }

    /** Validates and saves changes to an existing product (the stock quantity is not changed here). */
    public void update(Product product) throws BmsException {
        Session.requireAdmin();
        Product cleaned = clean(product);
        try {
            check(cleaned);
            if (!productDao.update(cleaned)) {
                throw new ValidationException("This product no longer exists. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("updating a product", "The product could not be saved. Please try again.", e);
        }
    }

    /** Hides a product (soft delete). Refused when the product appears on any order. */
    public void delete(long productId) throws BmsException {
        Session.requireAdmin();
        try {
            int lines = productDao.countOrderLines(productId);
            if (lines > 0) {
                throw new ValidationException("This product appears on " + lines + " order line"
                        + (lines == 1 ? "" : "s") + " and cannot be deleted. "
                        + "Order history must be kept.");
            }
            if (!productDao.deactivate(productId)) {
                throw new ValidationException("This product no longer exists. Please refresh the list.");
            }
        } catch (SQLException e) {
            throw databaseProblem("deleting a product", "The product could not be deleted. Please try again.", e);
        }
    }

    // ---------- helpers ----------

    /** Trims text fields; empty optional fields become null. */
    private Product clean(Product p) {
        Product copy = new Product();
        copy.setProductId(p.getProductId());
        copy.setProductName(p.getProductName() == null ? "" : p.getProductName().trim());
        copy.setCategory(blankToNull(p.getCategory()));
        copy.setDescription(blankToNull(p.getDescription()));
        copy.setPriceCents(p.getPriceCents());
        copy.setQuantityInStock(p.getQuantityInStock());
        copy.setReorderLevel(p.getReorderLevel());
        copy.setSupplierId(p.getSupplierId());
        return copy;
    }

    /** All the rules. Throws ValidationException for bad data, SQLException if the database fails. */
    private void check(Product p) throws ValidationException, SQLException {
        if (p.getProductName().isEmpty()) {
            throw new ValidationException("The product name is required.");
        }
        TextRules.requireSingleLine(p.getProductName(), "product name");
        TextRules.requireSingleLine(p.getCategory(), "category");
        TextRules.requireText(p.getDescription(), "description");
        if (p.getProductName().length() > MAX_NAME) {
            throw new ValidationException("The product name may be at most " + MAX_NAME + " characters.");
        }
        if (p.getCategory() != null && p.getCategory().length() > MAX_CATEGORY) {
            throw new ValidationException("The category may be at most " + MAX_CATEGORY + " characters.");
        }
        if (p.getDescription() != null && p.getDescription().length() > MAX_DESCRIPTION) {
            throw new ValidationException("The description may be at most " + MAX_DESCRIPTION + " characters.");
        }
        if (p.getPriceCents() < 0 || p.getPriceCents() > MAX_PRICE_CENTS) {
            throw new ValidationException("The price must be between 0 and 10,000,000.");
        }
        if (p.getQuantityInStock() < 0 || p.getQuantityInStock() > MAX_QUANTITY) {
            throw new ValidationException("The stock quantity must be between 0 and " + MAX_QUANTITY + ".");
        }
        if (p.getReorderLevel() < 0 || p.getReorderLevel() > MAX_QUANTITY) {
            throw new ValidationException("The reorder level must be between 0 and " + MAX_QUANTITY + ".");
        }
        if (p.getSupplierId() != null && !productDao.supplierExists(p.getSupplierId())) {
            throw new ValidationException("The chosen supplier does not exist. Please choose another.");
        }
        if (productDao.nameInUse(p.getProductName(), p.getProductId())) {
            throw new ValidationException("A product called \"" + p.getProductName() + "\" already exists.");
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
