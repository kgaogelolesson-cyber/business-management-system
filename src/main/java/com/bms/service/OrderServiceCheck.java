package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import com.bms.model.CustomerOrder;
import com.bms.model.OrderLine;
import com.bms.model.Product;
import com.bms.model.StockAdjustment;
import com.bms.service.InventoryService.StockFilter;
import com.bms.service.OrderService.StatusFilter;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for orders. It adds TEST customers and TEST products (names starting "Check"),
 * creates orders, tests the rules, then removes ALL of its own test rows again.
 * It needs the demo data (run DemoDataLoader once first). Run it on its own: right-click -> Run File.
 */
public final class OrderServiceCheck {

    private static int passed = 0;
    private static int total = 0;
    private static final List<Long> createdOrderIds = new ArrayList<>();
    private static final List<Long> createdProductIds = new ArrayList<>();
    private static final List<Long> createdCustomerIds = new ArrayList<>();

    private OrderServiceCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            OrderService orders = new OrderService();
            ProductService products = new ProductService();
            CustomerService customers = new CustomerService();
            InventoryService inventory = new InventoryService();

            // --- Not logged in ---
            Session.end();
            check("Orders are refused when nobody is logged in", fails(() -> orders.search("", StatusFilter.ALL), AccessDeniedException.class));
            check("Order lines are refused when nobody is logged in", fails(() -> orders.getLines(1), AccessDeniedException.class));
            check("Creating an order is refused when nobody is logged in", fails(() -> orders.create(1, List.of(line(1, 1))), AccessDeniedException.class));

            // --- Test data (added by the administrator) ---
            auth.login("admin", "Admin@123");
            long productA = addProduct(products, "CheckOrder Widget", 10000, 10);   // R100.00
            long productB = addProduct(products, "CheckOrder Gadget", 2550, 3);     // R25.50
            long productC = addProduct(products, "CheckOrder Gizmo", 500, 50);
            long customer1 = addCustomer(customers, "Checkorder", "Alpha");
            long customer2 = addCustomer(customers, "Checkorder", "Beta");
            long customerGone = addCustomer(customers, "Checkorder", "Gone");
            customers.delete(customerGone);
            long ordersBefore = countRows("orders");

            // --- An employee creates an order ---
            auth.login("thandi", "Employee@123");
            check("Employee can see the currency symbol", "R".equals(orders.getCurrencySymbol()));
            long order1 = orders.create(customer1, List.of(line(productA, 2), line(productB, 1)));
            createdOrderIds.add(order1);
            check("Employee can create an order", order1 > 0);
            check("The order is recorded against the logged-in employee",
                    "Thandi Mokoena".equals(find(orders.search("", StatusFilter.ALL), order1).getEmployeeName()));
            CustomerOrder saved = find(orders.search("", StatusFilter.ALL), order1);
            check("The order is COMPLETED and has the customer's name",
                    saved != null && CustomerOrder.COMPLETED.equals(saved.getStatus()) && "Checkorder Alpha".equals(saved.getCustomerName()));
            check("The total is 2 x R100.00 + 1 x R25.50 = R225.50 (22550 cents)", saved != null && saved.getTotalCents() == 22550);
            List<OrderLine> lines1 = orders.getLines(order1);
            check("The order has 2 lines with the right quantities", lines1.size() == 2 && lines1.get(0).getQuantity() == 2 && lines1.get(1).getQuantity() == 1);
            check("Each line stores the price at the time of sale",
                    lines1.get(0).getUnitPriceCents() == 10000 && lines1.get(1).getUnitPriceCents() == 2550);
            check("The subtotals add up to the total",
                    lines1.stream().mapToLong(OrderLine::getSubtotalCents).sum() == 22550);
            check("Stock went down: widget 10 -> 8", stockOf(inventory, productA) == 8);
            check("Stock went down: gadget 3 -> 2", stockOf(inventory, productB) == 2);
            check("Stock history says 'Sold on order #...'",
                    historyOf(inventory, productA).get(0).getQuantityChange() == -2
                    && historyOf(inventory, productA).get(0).getReason().equals("Sold on order #" + order1));

            // --- Price changes later do not change the old order ---
            auth.login("admin", "Admin@123");
            Product widget = findProduct(products.search("CheckOrder Widget", null), productA);
            widget.setPriceCents(99999);
            products.update(widget);
            check("A later price change does not change an old order",
                    orders.getLines(order1).get(0).getUnitPriceCents() == 10000
                    && find(orders.search("", StatusFilter.ALL), order1).getTotalCents() == 22550);
            widget.setPriceCents(10000);
            products.update(widget);

            // --- Who sees which orders ---
            long order2 = orders.create(customer2, List.of(line(productC, 4)));
            createdOrderIds.add(order2);
            check("The administrator can create an order too", order2 > 0);
            check("...and it is recorded against the administrator",
                    "System Administrator".equals(find(orders.search("", StatusFilter.ALL), order2).getEmployeeName()));
            check("The administrator sees every order", find(orders.search("", StatusFilter.ALL), order1) != null
                    && find(orders.search("", StatusFilter.ALL), order2) != null);
            check("The administrator can open any order's lines", orders.getLines(order1).size() == 2);
            auth.login("thandi", "Employee@123");
            check("An employee sees their own order", find(orders.search("", StatusFilter.ALL), order1) != null);
            check("An employee does NOT see the administrator's order", find(orders.search("", StatusFilter.ALL), order2) == null);
            check("An employee cannot open the administrator's order lines", fails(() -> orders.getLines(order2), AccessDeniedException.class));
            check("Opening an order that does not exist gives a clear error", fails(() -> orders.getLines(999999), ValidationException.class));

            // --- Search and filter ---
            auth.login("admin", "Admin@123");
            check("Search finds an order by its number", find(orders.search(String.valueOf(order1), StatusFilter.ALL), order1) != null);
            check("Search finds an order by customer name", find(orders.search("checkorder alpha", StatusFilter.ALL), order1) != null
                    && find(orders.search("checkorder alpha", StatusFilter.ALL), order2) == null);
            check("Search finds an order by employee name", find(orders.search("thandi", StatusFilter.ALL), order1) != null
                    && find(orders.search("thandi", StatusFilter.ALL), order2) == null);
            check("Search for something that does not exist finds nothing", orders.search("zzzqqq", StatusFilter.ALL).isEmpty());
            check("Searching for % is treated as a normal character", orders.search("%", StatusFilter.ALL).isEmpty());
            check("The 'Completed' filter lists the new orders", find(orders.search("", StatusFilter.COMPLETED), order1) != null);
            check("The 'Cancelled' filter does not list them", find(orders.search("", StatusFilter.CANCELLED), order1) == null);
            check("Newest orders come first",
                    orders.search("", StatusFilter.ALL).get(0).getOrderId() == order2);

            // --- Refusals leave everything unchanged ---
            int widgetStock = stockOf(inventory, productA);
            int gadgetStock = stockOf(inventory, productB);
            long orderCount = countRows("orders");
            long historyCount = countRows("stock_adjustments");
            check("An order with no lines is refused", fails(() -> orders.create(customer1, List.of()), ValidationException.class));
            check("A quantity of 0 is refused", fails(() -> orders.create(customer1, List.of(line(productA, 0))), ValidationException.class));
            check("A negative quantity is refused", fails(() -> orders.create(customer1, List.of(line(productA, -1))), ValidationException.class));
            check("More than 10000 of one product is refused", fails(() -> orders.create(customer1, List.of(line(productC, 10001))), ValidationException.class));
            check("The same product twice is refused", fails(() -> orders.create(customer1, List.of(line(productA, 1), line(productA, 1))), ValidationException.class));
            check("More than the stock is refused (gadget has 2, asking for 3)", fails(() -> orders.create(customer1, List.of(line(productB, 3))), ValidationException.class));
            check("The message names the product and the stock", messageOf(() -> orders.create(customer1, List.of(line(productB, 3))))
                    .contains("Only 2 in stock for \"CheckOrder Gadget\""));
            check("A customer that does not exist is refused", fails(() -> orders.create(999999, List.of(line(productA, 1))), ValidationException.class));
            check("A deleted customer is refused", fails(() -> orders.create(customerGone, List.of(line(productA, 1))), ValidationException.class));
            check("A product that does not exist is refused", fails(() -> orders.create(customer1, List.of(line(999999, 1))), ValidationException.class));
            check("A order with one good line and one bad line saves NOTHING", fails(() ->
                    orders.create(customer1, List.of(line(productA, 1), line(productB, 99))), ValidationException.class));
            check("Refused orders changed no stock", stockOf(inventory, productA) == widgetStock && stockOf(inventory, productB) == gadgetStock);
            check("Refused orders created no order rows and no history rows",
                    countRows("orders") == orderCount && countRows("stock_adjustments") == historyCount);

            // --- Selling the last items ---
            long order3 = orders.create(customer1, List.of(line(productB, 2)));
            createdOrderIds.add(order3);
            check("Selling all remaining stock works", stockOf(inventory, productB) == 0);
            check("...and the product is now OUT OF STOCK", "OUT OF STOCK".equals(statusOf(inventory, productB)));
            check("...so one more cannot be sold", fails(() -> orders.create(customer1, List.of(line(productB, 1))), ValidationException.class));
            check("The stock history of the gadget adds up to its stock",
                    historyOf(inventory, productB).stream().mapToInt(StockAdjustment::getQuantityChange).sum() == 0);

            // --- A deleted product cannot be sold ---
            long productD = addProduct(products, "CheckOrder Doohickey", 100, 5);
            products.delete(productD);
            check("A deleted product cannot be sold", fails(() -> orders.create(customer1, List.of(line(productD, 1))), ValidationException.class));
            check("A product on an order cannot be deleted", fails(() -> products.delete(productA), ValidationException.class));
            check("A customer with orders cannot be deleted", fails(() -> customers.delete(customer1), ValidationException.class));

            // --- Cancelling an order ---
            long completedBefore = countWhere("orders", "status = 'COMPLETED'");
            int widgetBefore = stockOf(inventory, productA);   // 8: order 1 took 2
            int gadgetBefore = stockOf(inventory, productB);   // 0: order 1 took 1, order 3 took 2 (gadget started with 3)
            check("Before cancelling: widget 8, gadget 0", widgetBefore == 8 && gadgetBefore == 0);
            check("A completed order has no cancellation info", orders.getCancellationInfo(order1) == null);

            auth.login("thandi", "Employee@123");
            check("Employee cannot cancel an order", fails(() -> orders.cancel(order1), AccessDeniedException.class));
            auth.login("admin", "Admin@123");
            check("...and the order is still COMPLETED", CustomerOrder.COMPLETED.equals(find(orders.search("", StatusFilter.ALL), order1).getStatus()));

            orders.cancel(order1);
            CustomerOrder cancelled = find(orders.search("", StatusFilter.ALL), order1);
            check("Administrator can cancel an order: status is now CANCELLED", cancelled != null && CustomerOrder.CANCELLED.equals(cancelled.getStatus()));
            check("The cancelled order keeps its total and lines (records are kept)",
                    cancelled.getTotalCents() == 22550 && orders.getLines(order1).size() == 2);
            check("Stock was put back: widget 8 -> 10", stockOf(inventory, productA) == 10);
            check("Stock was put back: gadget 0 -> 1", stockOf(inventory, productB) == 1);
            check("The stock history says 'Order #... cancelled' with +2",
                    historyOf(inventory, productA).get(0).getQuantityChange() == 2
                    && historyOf(inventory, productA).get(0).getReason().equals("Order #" + order1 + " cancelled"));
            String info = orders.getCancellationInfo(order1);
            check("The order records who cancelled it and when",
                    info != null && info.startsWith("Cancelled 20") && info.endsWith("by System Administrator"));
            check("The 'Cancelled' filter lists it", find(orders.search("", StatusFilter.CANCELLED), order1) != null);
            check("The 'Completed' filter no longer lists it", find(orders.search("", StatusFilter.COMPLETED), order1) == null);
            check("Cancelled orders are no longer counted as completed",
                    countWhere("orders", "status = 'COMPLETED'") == completedBefore - 1);
            check("Cancelling the same order twice is refused", fails(() -> orders.cancel(order1), ValidationException.class));
            check("...and the stock did not change a second time", stockOf(inventory, productA) == 10 && stockOf(inventory, productB) == 1);
            check("Cancelling an order that does not exist gives a clear error", fails(() -> orders.cancel(999999), ValidationException.class));

            auth.login("thandi", "Employee@123");
            check("The employee still sees her own cancelled order", CustomerOrder.CANCELLED.equals(find(orders.search("", StatusFilter.ALL), order1).getStatus()));
            check("...and the cancellation info", orders.getCancellationInfo(order1) != null);
            check("An employee cannot read the cancellation info of someone else's order",
                    fails(() -> orders.getCancellationInfo(order2), AccessDeniedException.class));
            auth.login("admin", "Admin@123");

            long order4 = orders.create(customer1, List.of(line(productB, 1)));
            createdOrderIds.add(order4);
            check("The restored stock can be sold again", order4 > 0 && stockOf(inventory, productB) == 0);
            orders.cancel(order3);
            check("Cancelling another order restores its stock (gadget 0 -> 2)", stockOf(inventory, productB) == 2);
            check("The gadget's history still adds up to its stock",
                    historyOf(inventory, productB).stream().mapToInt(StockAdjustment::getQuantityChange).sum() == stockOf(inventory, productB));
            orders.cancel(order2);
            check("Cancelling the administrator's order puts the gizmos back (46 -> 50)", stockOf(inventory, productC) == 50);
            check("A customer who only has cancelled orders still cannot be deleted (history is kept)",
                    fails(() -> customers.delete(customer2), ValidationException.class));

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

    private static String messageOf(Action action) {
        try {
            action.run();
            return "";
        } catch (Exception e) {
            return String.valueOf(e.getMessage());
        }
    }

    private static OrderLine line(long productId, int quantity) {
        return new OrderLine(productId, "CheckOrder Gadget", quantity, 0);
    }

    private static long addProduct(ProductService products, String name, long priceCents, int stock) throws BmsException {
        Product p = new Product();
        p.setProductName(name);
        p.setPriceCents(priceCents);
        p.setQuantityInStock(stock);
        p.setReorderLevel(1);
        long id = products.add(p);
        createdProductIds.add(id);
        return id;
    }

    private static long addCustomer(CustomerService customers, String first, String last) throws BmsException {
        Customer c = new Customer();
        c.setFirstName(first);
        c.setLastName(last);
        long id = customers.add(c);
        createdCustomerIds.add(id);
        return id;
    }

    private static CustomerOrder find(List<CustomerOrder> list, long id) {
        return list.stream().filter(o -> o.getOrderId() == id).findFirst().orElse(null);
    }

    private static Product findProduct(List<Product> list, long id) {
        return list.stream().filter(p -> p.getProductId() == id).findFirst().orElse(null);
    }

    private static int stockOf(InventoryService inventory, long id) throws BmsException {
        Product p = findProduct(inventory.search("", StockFilter.ALL), id);
        return p == null ? -1 : p.getQuantityInStock();
    }

    private static String statusOf(InventoryService inventory, long id) throws BmsException {
        Product p = findProduct(inventory.search("", StockFilter.ALL), id);
        return p == null ? "" : p.getStockStatus();
    }

    private static List<StockAdjustment> historyOf(InventoryService inventory, long id) throws BmsException {
        return inventory.getHistory(id);
    }

    private static long countWhere(String table, String condition) throws SQLException {
        // Both values are constants written in this file, never user input.
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + condition);
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private static long countRows(String table) throws SQLException {
        // The table name is one of our own constants above, never user input.
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    /** Removes ONLY the test rows this program created (orders first: they point at customers and products). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement orders = c.prepareStatement("DELETE FROM orders WHERE order_id = ?");
             PreparedStatement adjustments = c.prepareStatement("DELETE FROM stock_adjustments WHERE product_id = ?");
             PreparedStatement products = c.prepareStatement("DELETE FROM products WHERE product_id = ?");
             PreparedStatement customers = c.prepareStatement("DELETE FROM customers WHERE customer_id = ?")) {
            for (long id : createdOrderIds) {
                orders.setLong(1, id);
                orders.executeUpdate();     // its order_items are removed automatically (ON DELETE CASCADE)
            }
            for (long id : createdProductIds) {
                adjustments.setLong(1, id);
                adjustments.executeUpdate();
                products.setLong(1, id);
                products.executeUpdate();
            }
            for (long id : createdCustomerIds) {
                customers.setLong(1, id);
                customers.executeUpdate();
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
