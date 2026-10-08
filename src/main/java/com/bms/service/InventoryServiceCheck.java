package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Product;
import com.bms.model.StockAdjustment;
import com.bms.service.InventoryService.StockFilter;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for inventory. It adds one TEST product (name starting "Check"),
 * changes its stock several times, tests the rules, then removes its own test rows again.
 * It needs the demo data (run DemoDataLoader once first). Run it on its own: right-click -> Run File.
 */
public final class InventoryServiceCheck {

    private static int passed = 0;
    private static int total = 0;
    /** Ids of the test products this program created, so it removes only those. */
    private static final List<Long> createdProductIds = new ArrayList<>();

    private InventoryServiceCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            InventoryService inventory = new InventoryService();
            ProductService products = new ProductService();

            // --- Not logged in ---
            Session.end();
            check("The inventory is refused when nobody is logged in",
                    fails(() -> inventory.search("", StockFilter.ALL), AccessDeniedException.class));
            check("Stock history is refused when nobody is logged in",
                    fails(() -> inventory.getHistory(1), AccessDeniedException.class));
            check("Adjusting stock is refused when nobody is logged in",
                    fails(() -> inventory.adjustStock(1, 1, "x"), AccessDeniedException.class));

            // --- Employee: may look, may not change ---
            auth.login("thandi", "Employee@123");
            List<Product> seenByEmployee = inventory.search("", StockFilter.ALL);
            check("Employee can view the inventory", !seenByEmployee.isEmpty());
            check("Employee can view stock history", ok(() -> inventory.getHistory(seenByEmployee.get(0).getProductId())));
            check("Employee cannot adjust stock",
                    fails(() -> inventory.adjustStock(seenByEmployee.get(0).getProductId(), 1, "Not allowed"), AccessDeniedException.class));

            // --- Administrator: a test product with 10 in stock, reorder level 5 ---
            auth.login("admin", "Admin@123");
            Product p = new Product();
            p.setProductName("CheckInventory Widget");
            p.setCategory("CheckCategory");
            p.setPriceCents(1999);
            p.setQuantityInStock(10);
            p.setReorderLevel(5);
            long id = products.add(p);
            createdProductIds.add(id);

            Product start = find(inventory.search("CheckInventory", StockFilter.ALL), id);
            check("The new product is listed with its starting stock",
                    start != null && start.getQuantityInStock() == 10 && start.getReorderLevel() == 5);
            check("10 in stock with reorder level 5 is IN STOCK", start != null && "IN STOCK".equals(start.getStockStatus()));
            check("Its history starts with the 'Initial stock' line",
                    historyOf(inventory, id).size() == 1 && historyOf(inventory, id).get(0).getQuantityChange() == 10);

            // --- Adjusting ---
            int after1 = inventory.adjustStock(id, -5, "Damaged goods");
            check("Removing 5 leaves 5 and returns the new stock", after1 == 5 && stockOf(inventory, id) == 5);
            check("5 in stock with reorder level 5 is LOW STOCK", "LOW STOCK".equals(statusOf(inventory, id)));
            int after2 = inventory.adjustStock(id, -5, "Stock count correction");
            check("Removing the rest leaves 0", after2 == 0 && stockOf(inventory, id) == 0);
            check("0 in stock is OUT OF STOCK", "OUT OF STOCK".equals(statusOf(inventory, id)));
            int after3 = inventory.adjustStock(id, 20, "  Delivery received  ");
            check("Adding 20 gives 20", after3 == 20 && stockOf(inventory, id) == 20);
            check("20 in stock is IN STOCK again", "IN STOCK".equals(statusOf(inventory, id)));

            // --- History ---
            List<StockAdjustment> history = historyOf(inventory, id);
            check("The history has the 3 adjustments plus the initial stock line", history.size() == 4);
            check("The newest change is listed first", history.get(0).getQuantityChange() == 20 && history.get(3).getQuantityChange() == 10);
            check("The reason is saved without extra spaces", "Delivery received".equals(history.get(0).getReason()));
            check("The history says who made the change", "System Administrator".equals(history.get(0).getAdjustedBy()));
            check("The history adds up to the current stock",
                    history.stream().mapToInt(StockAdjustment::getQuantityChange).sum() == stockOf(inventory, id));
            check("Every history line has a date", history.stream().allMatch(h -> h.getAdjustedAt() != null && !h.getAdjustedAt().isBlank()));

            // --- Refusals leave everything unchanged ---
            int stockBefore = stockOf(inventory, id);
            int linesBefore = historyOf(inventory, id).size();
            check("A change of 0 is refused", fails(() -> inventory.adjustStock(id, 0, "Nothing"), ValidationException.class));
            check("Removing more than is in stock is refused", fails(() -> inventory.adjustStock(id, -21, "Too many"), ValidationException.class));
            check("A missing reason is refused", fails(() -> inventory.adjustStock(id, 1, ""), ValidationException.class));
            check("A reason of only spaces is refused", fails(() -> inventory.adjustStock(id, 1, "    "), ValidationException.class));
            check("A reason longer than 200 characters is refused", fails(() -> inventory.adjustStock(id, 1, "x".repeat(201)), ValidationException.class));
            check("A reason of exactly 200 characters is accepted", ok(() -> inventory.adjustStock(id, 1, "x".repeat(200))));
            check("Adding more than 100000 at once is refused", fails(() -> inventory.adjustStock(id, 100_001, "Too big"), ValidationException.class));
            check("Removing more than 100000 at once is refused", fails(() -> inventory.adjustStock(id, -100_001, "Too big"), ValidationException.class));
            check("Adjusting a product that does not exist gives a clear error", fails(() -> inventory.adjustStock(999999, 1, "Ghost"), ValidationException.class));
            check("Refused changes did not change the stock", stockOf(inventory, id) == stockBefore + 1);
            check("Refused changes wrote no history lines", historyOf(inventory, id).size() == linesBefore + 1);

            // --- Searching and filtering ---
            check("Search finds the product by name", find(inventory.search("checkinventory", StockFilter.ALL), id) != null);
            check("Search finds the product by category", find(inventory.search("checkcategory", StockFilter.ALL), id) != null);
            check("Search for something that does not exist finds nothing", inventory.search("zzzqqq", StockFilter.ALL).isEmpty());
            check("Searching for % is treated as a normal character", inventory.search("%", StockFilter.ALL).isEmpty());
            check("The 'In stock' filter lists it now (21 in stock)", find(inventory.search("CheckInventory", StockFilter.IN_STOCK), id) != null);
            check("The 'Low stock' filter does not list it now", find(inventory.search("CheckInventory", StockFilter.LOW_STOCK), id) == null);
            inventory.adjustStock(id, -21, "Move everything out");
            check("After removing everything, the 'Out of stock' filter lists it", find(inventory.search("CheckInventory", StockFilter.OUT_OF_STOCK), id) != null);
            check("...and the 'In stock' filter does not", find(inventory.search("CheckInventory", StockFilter.IN_STOCK), id) == null);
            check("Every product in the 'Out of stock' filter really has 0 in stock",
                    inventory.search("", StockFilter.OUT_OF_STOCK).stream().allMatch(x -> x.getQuantityInStock() == 0));
            check("Every product in the 'Low stock' filter is at or below its reorder level, but not 0",
                    inventory.search("", StockFilter.LOW_STOCK).stream()
                            .allMatch(x -> x.getQuantityInStock() > 0 && x.getQuantityInStock() <= x.getReorderLevel()));

            // --- A deleted product cannot be adjusted or listed ---
            products.delete(id);
            check("A deleted product disappears from the inventory", find(inventory.search("CheckInventory", StockFilter.ALL), id) == null);
            check("A deleted product cannot be adjusted", fails(() -> inventory.adjustStock(id, 1, "Too late"), ValidationException.class));

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

    private static Product find(List<Product> list, long id) {
        return list.stream().filter(p -> p.getProductId() == id).findFirst().orElse(null);
    }

    private static int stockOf(InventoryService inventory, long id) throws BmsException {
        Product p = find(inventory.search("", StockFilter.ALL), id);
        return p == null ? -1 : p.getQuantityInStock();
    }

    private static String statusOf(InventoryService inventory, long id) throws BmsException {
        Product p = find(inventory.search("", StockFilter.ALL), id);
        return p == null ? "" : p.getStockStatus();
    }

    private static List<StockAdjustment> historyOf(InventoryService inventory, long id) throws BmsException {
        return inventory.getHistory(id);
    }

    /** Removes ONLY the test rows this program created (history first, because it points at the product). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement adjustments = c.prepareStatement("DELETE FROM stock_adjustments WHERE product_id = ?");
             PreparedStatement products = c.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            for (long id : createdProductIds) {
                adjustments.setLong(1, id);
                adjustments.executeUpdate();
                products.setLong(1, id);
                products.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Could not remove the test rows: " + e.getMessage());
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
