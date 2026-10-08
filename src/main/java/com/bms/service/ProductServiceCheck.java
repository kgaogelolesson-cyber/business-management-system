package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Product;
import com.bms.model.SupplierOption;
import com.bms.util.InputParser;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for product management. It adds a few TEST products
 * (names starting "Check"), tests the rules, then removes its own test rows again.
 * It needs the demo data (run DemoDataLoader once first). Run it on its own: right-click -> Run File.
 */
public final class ProductServiceCheck {

    private static int passed = 0;
    private static int total = 0;
    /** Ids of the test products this program created, so it removes only those. */
    private static final List<Long> createdIds = new ArrayList<>();

    private ProductServiceCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            ProductService products = new ProductService();

            // --- Typed text -> numbers (no database needed) ---
            check("\"129.99\" becomes 12999 cents", parsesTo("129.99", 12999));
            check("\"R129.99\" becomes 12999 cents", parsesTo("R129.99", 12999));
            check("\"R 1 200.5\" becomes 120050 cents", parsesTo("R 1 200.5", 120050));
            check("\"120\" becomes 12000 cents", parsesTo("120", 12000));
            check("\"0\" is allowed (a free item)", parsesTo("0", 0));
            check("An empty price is refused", priceRefused(""));
            check("Letters in the price are refused", priceRefused("abc"));
            check("A negative price is refused", priceRefused("-5"));
            check("A comma in the price is refused", priceRefused("1,200.50"));
            check("Three decimals are refused", priceRefused("1.999"));
            check("A whole number such as 25 is accepted", wholeNumber("25", 25));
            check("A decimal stock quantity is refused", wholeNumberRefused("2.5"));
            check("A negative stock quantity is refused", wholeNumberRefused("-1"));
            check("An empty quantity is refused", wholeNumberRefused("  "));

            // --- Not logged in ---
            Session.end();
            check("Products are refused when nobody is logged in", fails(() -> products.search("", null), AccessDeniedException.class));

            // --- Employee: may view, may NOT change ---
            auth.login("thandi", "Employee@123");
            List<Product> all = products.search("", null);
            check("Employee can view the products", !all.isEmpty());
            check("Search by name finds the USB drive", containsName(products.search("usb", null), "USB Flash Drive 32GB"));
            check("Search by category text finds Stationery items", containsName(products.search("stationery", null), "A4 Paper Ream"));
            check("Search by supplier name finds that supplier's products",
                    containsName(products.search("TechSource", null), "USB Flash Drive 32GB")
                    && !containsName(products.search("TechSource", null), "A4 Paper Ream"));
            check("Category filter returns only that category", onlyCategory(products.search("", "Stationery"), "Stationery")
                    && !products.search("", "stationery").isEmpty());
            check("Search for something that does not exist finds nothing", products.search("zzzqqq", null).isEmpty());
            check("Searching for % is treated as a normal character", products.search("%", null).isEmpty());
            check("The category list has no repeats", noRepeats(products.getCategories()));
            check("Employee cannot add a product", fails(() -> products.add(make("CheckNope", 100, 1, 1)), AccessDeniedException.class));
            check("Employee cannot edit a product", fails(() -> products.update(make("CheckNope", 100, 1, 1)), AccessDeniedException.class));
            check("Employee cannot delete a product", fails(() -> products.delete(1), AccessDeniedException.class));
            check("Employee cannot even validate a product form", fails(() -> products.validateProduct(make("CheckNope", 100, 1, 1)), AccessDeniedException.class));

            // --- Administrator: add ---
            auth.login("admin", "Admin@123");
            List<SupplierOption> suppliers = products.getSupplierOptions();
            check("There are suppliers to choose from", !suppliers.isEmpty());
            Product a = make("CheckWidget", 15050, 20, 5);
            a.setCategory("CheckCategory");
            a.setDescription("A test product");
            a.setSupplierId(suppliers.get(0).supplierId());
            long idA = products.add(a);
            createdIds.add(idA);
            check("Administrator can add a product", idA > 0);

            Product savedA = find(products.search("CheckWidget", null), idA);
            check("The saved product has the right price", savedA != null && savedA.getPriceCents() == 15050);
            check("The saved product shows its supplier name", savedA != null && suppliers.get(0).supplierName().equals(savedA.getSupplierName()));
            check("20 in stock with reorder level 5 is IN STOCK", savedA != null && "IN STOCK".equals(savedA.getStockStatus()));
            check("Starting stock was recorded as an 'Initial stock' adjustment", adjustmentCount(idA, 20) == 1);

            Product lowP = make("CheckLowOne", 1000, 3, 5);
            long idLow = products.add(lowP);
            createdIds.add(idLow);
            check("3 in stock with reorder level 5 is LOW STOCK", "LOW STOCK".equals(find(products.search("CheckLowOne", null), idLow).getStockStatus()));

            Product zero = make("CheckZero", 1000, 0, 5);
            long idZero = products.add(zero);
            createdIds.add(idZero);
            check("0 in stock is OUT OF STOCK", "OUT OF STOCK".equals(find(products.search("CheckZero", null), idZero).getStockStatus()));
            check("A product that starts with 0 stock gets no stock adjustment line", adjustmentCount(idZero, 0) == 0);

            check("The new category appears once in the category list", countIgnoreCase(products.getCategories(), "CheckCategory") == 1);
            check("Product can be saved without category, description or supplier",
                    products.search("CheckZero", null).get(0).getSupplierId() == null);
            check("A price of 0 can be saved", tryAdd(products, make("CheckFree", 0, 1, 1)) > 0);

            // --- Validation ---
            check("A name is required", fails(() -> products.add(make("   ", 100, 1, 1)), ValidationException.class));
            check("A too-long name is refused", fails(() -> products.add(make("C".repeat(101), 100, 1, 1)), ValidationException.class));
            check("A negative price is refused by the service", fails(() -> products.add(make("CheckNeg", -1, 1, 1)), ValidationException.class));
            check("A price above R10,000,000 is refused", fails(() -> products.add(make("CheckHuge", 1_000_000_001L, 1, 1)), ValidationException.class));
            check("Negative stock is refused", fails(() -> products.add(make("CheckNegStock", 100, -1, 1)), ValidationException.class));
            check("A negative reorder level is refused", fails(() -> products.add(make("CheckNegReorder", 100, 1, -1)), ValidationException.class));
            Product badSupplier = make("CheckBadSupplier", 100, 1, 1);
            badSupplier.setSupplierId(999999L);
            check("A supplier that does not exist is refused", fails(() -> products.add(badSupplier), ValidationException.class));
            check("A too-long description is refused", fails(() -> {
                Product d = make("CheckLongDesc", 100, 1, 1);
                d.setDescription("D".repeat(501));
                products.add(d);
            }, ValidationException.class));

            // --- Duplicate names ---
            check("The same name (different capitals) is refused", fails(() -> products.add(make("CHECKWIDGET", 100, 1, 1)), ValidationException.class));
            Product keep = make("CheckWidget", 15050, 20, 5);
            keep.setProductId(idA);
            check("Saving a product under its own name is allowed", ok(() -> products.update(keep)));
            Product clash = make("CheckLowOne", 100, 1, 1);
            clash.setProductId(idA);
            check("Renaming to another product's name is refused", fails(() -> products.update(clash), ValidationException.class));

            // --- Edit ---
            Product edit = make("CheckWidget Pro", 19999, 9999, 8);   // 9999 here must be IGNORED
            edit.setProductId(idA);
            edit.setCategory("CheckCategory");
            products.update(edit);
            Product afterEdit = find(products.search("CheckWidget Pro", null), idA);
            check("Edit changes the name, price and reorder level",
                    afterEdit != null && afterEdit.getPriceCents() == 19999 && afterEdit.getReorderLevel() == 8);
            check("Edit does NOT change the stock quantity (still 20)", afterEdit != null && afterEdit.getQuantityInStock() == 20);
            check("Edit can remove the supplier", afterEdit != null && afterEdit.getSupplierId() == null);
            Product ghost = make("CheckGhost", 100, 1, 1);
            ghost.setProductId(999999);
            check("Editing a product that does not exist gives a clear error", fails(() -> products.update(ghost), ValidationException.class));

            // --- Delete ---
            long usbId = productIdByName("USB Flash Drive 32GB");
            check("A product that is on an order cannot be deleted", fails(() -> products.delete(usbId), ValidationException.class));
            check("...and is still listed afterwards", containsName(products.search("USB", null), "USB Flash Drive 32GB"));
            products.delete(idZero);
            check("Administrator can delete a product that was never ordered", find(products.search("CheckZero", null), idZero) == null);
            check("The deleted product is only hidden (row still in the database)", rowExists(idZero));
            check("A deleted product's name can be used again", tryAdd(products, make("CheckZero", 100, 1, 1)) > 0);
            check("Deleting a product that does not exist gives a clear error", fails(() -> products.delete(999999), ValidationException.class));

        } catch (Exception e) {
            total++;
            System.out.println("FAIL: unexpected error: " + e);
            e.printStackTrace(System.out);
        } finally {
            cleanUp();
            Session.end();
        }
        System.out.println();
        System.out.println(passed + " of " + total + " checks passed.");
        System.out.println(passed == total ? "ALL CHECKS PASSED." : "SOME CHECKS FAILED - read the FAIL lines above.");
    }

    // ---------- small helpers ----------

    private interface Action {
        void run() throws Exception;
    }

    private static boolean fails(Action action, Class<? extends BmsException> expected) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return expected.isInstance(e);
        }
    }

    private static boolean ok(Action action) {
        try {
            action.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean parsesTo(String text, long cents) {
        try {
            return InputParser.parseMoneyToCents(text, "R") == cents;
        } catch (ValidationException e) {
            return false;
        }
    }

    private static boolean priceRefused(String text) {
        return fails(() -> InputParser.parseMoneyToCents(text, "R"), ValidationException.class);
    }

    private static boolean wholeNumber(String text, int expected) {
        try {
            return InputParser.parseWholeNumber(text, "The quantity") == expected;
        } catch (ValidationException e) {
            return false;
        }
    }

    private static boolean wholeNumberRefused(String text) {
        return fails(() -> InputParser.parseWholeNumber(text, "The quantity"), ValidationException.class);
    }

    private static Product make(String name, long priceCents, int stock, int reorder) {
        Product p = new Product();
        p.setProductName(name);
        p.setPriceCents(priceCents);
        p.setQuantityInStock(stock);
        p.setReorderLevel(reorder);
        return p;
    }

    private static long tryAdd(ProductService service, Product p) throws BmsException {
        long id = service.add(p);
        createdIds.add(id);
        return id;
    }

    private static Product find(List<Product> list, long id) {
        return list.stream().filter(p -> p.getProductId() == id).findFirst().orElse(null);
    }

    private static boolean containsName(List<Product> list, String name) {
        return list.stream().anyMatch(p -> name.equals(p.getProductName()));
    }

    private static boolean onlyCategory(List<Product> list, String category) {
        return !list.isEmpty() && list.stream().allMatch(p -> category.equalsIgnoreCase(p.getCategory()));
    }

    private static boolean noRepeats(List<String> categories) {
        return categories.stream().map(String::toLowerCase).distinct().count() == categories.size();
    }

    private static long countIgnoreCase(List<String> list, String text) {
        return list.stream().filter(s -> s.equalsIgnoreCase(text)).count();
    }

    private static int adjustmentCount(long productId, int quantity) throws SQLException {
        String sql = "SELECT COUNT(*) FROM stock_adjustments WHERE product_id = ? AND quantity_change = ? "
                + "AND reason LIKE 'Initial stock%'";
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setLong(1, productId);
            st.setInt(2, quantity);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static boolean rowExists(long id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM products WHERE product_id = ?")) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static long productIdByName(String name) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT product_id FROM products WHERE product_name = ?")) {
            st.setString(1, name);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Removes ONLY the test products this program created (they were never ordered, so this is safe). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement adjustments = c.prepareStatement("DELETE FROM stock_adjustments WHERE product_id = ?");
             PreparedStatement products = c.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            for (long id : createdIds) {
                adjustments.setLong(1, id);
                adjustments.executeUpdate();
                products.setLong(1, id);
                products.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Could not remove the test products: " + e.getMessage());
        }
    }

    private static void check(String description, boolean ok) {
        total++;
        if (ok) {
            passed++;
            System.out.println("PASS: " + description);
        } else {
            System.out.println("FAIL: " + description);
        }
    }
}
