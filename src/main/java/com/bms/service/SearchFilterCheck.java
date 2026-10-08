package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import com.bms.model.CustomerOrder;
import com.bms.model.EmployeeAccount;
import com.bms.model.OrderLine;
import com.bms.model.Product;
import com.bms.model.Role;
import com.bms.service.InventoryService.StockFilter;
import com.bms.service.OrderService.StatusFilter;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for Phase 15 (search and filtering): the order date range, the customer's order
 * history, the product stock-status filter and the employee department filter.
 * It adds TEST rows (names starting "Check"), tests them, then removes ALL of its own rows again.
 * It needs the demo data (run DemoDataLoader once first). Run it on its own: right-click -> Run File.
 */
public final class SearchFilterCheck {

    private static int passed = 0;
    private static int total = 0;
    private static final List<Long> orderIds = new ArrayList<>();
    private static final List<Long> productIds = new ArrayList<>();
    private static final List<Long> customerIds = new ArrayList<>();
    private static final List<Long> employeeIds = new ArrayList<>();

    private SearchFilterCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            OrderService orders = new OrderService();
            ProductService products = new ProductService();
            CustomerService customers = new CustomerService();
            EmployeeService employees = new EmployeeService();

            auth.login("admin", "Admin@123");
            long outId = addProduct(products, "CheckFilter Out", 1000, 0, 5);
            long lowId = addProduct(products, "CheckFilter Low", 1000, 3, 5);
            long inId = addProduct(products, "CheckFilter Plenty", 1000, 50, 5);
            long cust1 = addCustomer(customers, "Checkfilter", "Alpha");
            long cust2 = addCustomer(customers, "Checkfilter", "Beta");
            long cust3 = addCustomer(customers, "Checkfilter", "NoOrders");

            // ---------- orders: date range ----------
            long orderA = order(orders, cust1, inId);
            long orderB = order(orders, cust2, inId);
            long orderC = order(orders, cust1, inId);
            setDate(orderA, "2026-01-10 09:00:00");
            setDate(orderB, "2026-01-20 23:30:00");
            setDate(orderC, "2026-02-05 08:00:00");

            check("No dates: all three orders are found", has(orders.search("", StatusFilter.ALL, "", "", null), orderA, orderB, orderC));
            check("From 2026-01-15 hides the older order", !has(orders.search("", StatusFilter.ALL, "2026-01-15", "", null), orderA)
                    && has(orders.search("", StatusFilter.ALL, "2026-01-15", "", null), orderB, orderC));
            check("To 2026-01-20 includes an order made late on 20 January",
                    has(orders.search("", StatusFilter.ALL, "", "2026-01-20", null), orderA, orderB)
                    && !has(orders.search("", StatusFilter.ALL, "", "2026-01-20", null), orderC));
            List<CustomerOrder> oneDay = orders.search("", StatusFilter.ALL, "2026-01-20", "2026-01-20", null);
            check("From = To = 2026-01-20 finds exactly that day's order",
                    has(oneDay, orderB) && !has(oneDay, orderA) && !has(oneDay, orderC));
            List<CustomerOrder> march = orders.search("", StatusFilter.ALL, "2026-03-01", "2026-03-31", null);
            check("A range with no orders finds none of our orders", !has(march, orderA) && !has(march, orderB) && !has(march, orderC));
            check("Dates and text combine (January + 'Beta' = only order B)",
                    has(orders.search("checkfilter beta", StatusFilter.ALL, "2026-01-01", "2026-01-31", null), orderB)
                    && !has(orders.search("checkfilter beta", StatusFilter.ALL, "2026-01-01", "2026-01-31", null), orderA));
            check("Spaces around a date are accepted", has(orders.search("", StatusFilter.ALL, " 2026-01-20 ", " 2026-01-20 ", null), orderB));
            check("A date that is not a date is refused", fails(() -> orders.search("", StatusFilter.ALL, "tomorrow", "", null), ValidationException.class));
            check("A day that does not exist is refused (2026-02-30)", fails(() -> orders.search("", StatusFilter.ALL, "", "2026-02-30", null), ValidationException.class));
            check("The wrong date format is refused (20/01/2026)", fails(() -> orders.search("", StatusFilter.ALL, "20/01/2026", "", null), ValidationException.class));
            check("From after To is refused", fails(() -> orders.search("", StatusFilter.ALL, "2026-02-01", "2026-01-01", null), ValidationException.class));
            check("The From message names the field", message(() -> orders.search("", StatusFilter.ALL, "x", "", null)).contains("From"));
            check("The To message names the field", message(() -> orders.search("", StatusFilter.ALL, "", "x", null)).contains("To"));

            // ---------- customer order history ----------
            List<CustomerOrder> history1 = orders.getCustomerHistory(cust1);
            check("History of customer 1 holds exactly their two orders", history1.size() == 2 && has(history1, orderA, orderC));
            check("History of customer 2 holds only their order", orders.getCustomerHistory(cust2).size() == 1 && has(orders.getCustomerHistory(cust2), orderB));
            check("A customer with no orders has an empty history", orders.getCustomerHistory(cust3).isEmpty());
            check("History is newest first", history1.get(0).getOrderId() == orderC);
            check("History can be narrowed by date as well",
                    orders.search("", StatusFilter.ALL, "2026-02-01", "", cust1).size() == 1);
            check("A customer id that does not exist has an empty history", orders.getCustomerHistory(999999).isEmpty());

            // ---------- cancelled orders in history ----------
            orders.cancel(orderC);
            check("A cancelled order still shows in the history, marked CANCELLED",
                    CustomerOrder.CANCELLED.equals(find(orders.getCustomerHistory(cust1), orderC).getStatus()));
            check("The Cancelled filter narrows the history", orders.search("", StatusFilter.CANCELLED, "", "", cust1).size() == 1);

            // ---------- employee sees only their own orders, also in history ----------
            auth.login("thandi", "Employee@123");
            long thandiOrder = order(orders, cust2, inId);
            check("An employee's history of a customer shows only the employee's own orders",
                    has(orders.getCustomerHistory(cust2), thandiOrder) && !has(orders.getCustomerHistory(cust2), orderB));
            check("Date filters do not let an employee see other people's orders",
                    !has(orders.search("", StatusFilter.ALL, "2026-01-01", "2026-12-31", cust2), orderB));
            check("An employee's own order is found without any filter on its date",
                    has(orders.search("", StatusFilter.ALL, "", "", cust2), thandiOrder));
            check("Dates are still checked for an employee", fails(() -> orders.search("", StatusFilter.ALL, "bad", "", null), ValidationException.class));
            Session.end();
            check("History is refused when nobody is logged in", fails(() -> orders.getCustomerHistory(cust1), AccessDeniedException.class));
            check("Date search is refused when nobody is logged in", fails(() -> orders.search("", StatusFilter.ALL, "", "", null), AccessDeniedException.class));

            // ---------- products: stock status ----------
            auth.login("thandi", "Employee@123");
            check("Products: 'All' lists all three test products",
                    hasProducts(products.search("CheckFilter", null, StockFilter.ALL), outId, lowId, inId));
            List<Product> out = products.search("CheckFilter", null, StockFilter.OUT_OF_STOCK);
            check("Products: 'Out of stock' finds only the empty one", hasProducts(out, outId) && !hasProducts(out, lowId) && !hasProducts(out, inId));
            List<Product> low = products.search("CheckFilter", null, StockFilter.LOW_STOCK);
            check("Products: 'Low stock' finds only the one at or below its reorder level", hasProducts(low, lowId) && !hasProducts(low, outId) && !hasProducts(low, inId));
            List<Product> in = products.search("CheckFilter", null, StockFilter.IN_STOCK);
            check("Products: 'In stock' finds only the well-stocked one", hasProducts(in, inId) && !hasProducts(in, outId) && !hasProducts(in, lowId));
            check("Products: a null stock filter means all", hasProducts(products.search("CheckFilter", null, null), outId, lowId, inId));
            check("Products: the old two-argument search still works", hasProducts(products.search("CheckFilter", null), outId, lowId, inId));
            check("Products: the stock label on each row matches the filter",
                    out.stream().allMatch(p -> "OUT OF STOCK".equals(p.getStockStatus())) && low.stream().allMatch(p -> "LOW STOCK".equals(p.getStockStatus())));
            check("Products: stock filter and text combine", products.search("zzzqqq", null, StockFilter.LOW_STOCK).isEmpty());
            check("Products: stock filter agrees with the Inventory tab",
                    new InventoryService().search("CheckFilter", StockFilter.LOW_STOCK).size() == low.size());
            Session.end();
            check("Products: filtered search is refused when nobody is logged in", fails(() -> products.search("", null, StockFilter.LOW_STOCK), AccessDeniedException.class));

            // ---------- employees: department ----------
            auth.login("admin", "Admin@123");
            long e1 = addEmployee(employees, "Checkfilter", "One", "checkfilter_one", "CheckDept");
            long e2 = addEmployee(employees, "Checkfilter", "Two", "checkfilter_two", "checkdept");   // same department, other capitals
            long e3 = addEmployee(employees, "Checkfilter", "Three", "checkfilter_three", "CheckOther");
            List<String> departments = employees.getDepartments();
            check("Departments: each department appears once, capital letters ignored",
                    departments.stream().filter(d -> d.equalsIgnoreCase("CheckDept")).count() == 1);
            check("Departments: the other test department is listed", departments.stream().anyMatch(d -> d.equals("CheckOther")));
            check("Departments: the list is alphabetical",
                    departments.equals(departments.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));
            List<EmployeeAccount> dept = employees.search("", false, "CheckDept");
            check("Department filter finds both people of that department (capitals ignored)",
                    hasEmployees(dept, e1, e2) && !hasEmployees(dept, e3));
            check("Department filter excludes other departments", dept.stream().allMatch(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase("CheckDept")));
            check("Department and text combine", hasEmployees(employees.search("one", false, "CheckDept"), e1)
                    && !hasEmployees(employees.search("one", false, "CheckDept"), e2));
            check("A null or blank department means everyone", hasEmployees(employees.search("checkfilter", false, null), e1, e2, e3)
                    && hasEmployees(employees.search("checkfilter", false, "  "), e1, e2, e3));
            check("A department nobody is in finds nothing", employees.search("", true, "NoSuchDepartment").isEmpty());
            check("The old two-argument search still works", hasEmployees(employees.search("checkfilter", false), e1, e2, e3));
            check("Searching for % in a department is not a wildcard", employees.search("", true, "%").isEmpty());
            employees.deactivate(e2);
            check("A deactivated employee is hidden from the department filter by default", !hasEmployees(employees.search("", false, "CheckDept"), e2));
            check("...and shown when 'show deactivated' is on", hasEmployees(employees.search("", true, "CheckDept"), e2));
            auth.login("thandi", "Employee@123");
            check("An employee cannot list departments", fails(() -> employees.getDepartments(), AccessDeniedException.class));
            check("An employee cannot search employees by department", fails(() -> employees.search("", false, "CheckDept"), AccessDeniedException.class));

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

    private static String message(Action action) {
        try {
            action.run();
            return "";
        } catch (Exception e) {
            return String.valueOf(e.getMessage());
        }
    }

    private static long order(OrderService orders, long customerId, long productId) throws BmsException {
        long id = orders.create(customerId, List.of(new OrderLine(productId, "x", 1, 0)));
        orderIds.add(id);
        return id;
    }

    private static void setDate(long orderId, String date) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("UPDATE orders SET order_date = ? WHERE order_id = ?")) {
            st.setString(1, date);
            st.setLong(2, orderId);
            st.executeUpdate();
        }
    }

    private static boolean has(List<CustomerOrder> list, long... ids) {
        for (long id : ids) {
            if (find(list, id) == null) {
                return false;
            }
        }
        return true;
    }

    private static CustomerOrder find(List<CustomerOrder> list, long id) {
        return list.stream().filter(o -> o.getOrderId() == id).findFirst().orElse(null);
    }

    private static boolean hasProducts(List<Product> list, long... ids) {
        for (long id : ids) {
            if (list.stream().noneMatch(p -> p.getProductId() == id)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasEmployees(List<EmployeeAccount> list, long... ids) {
        for (long id : ids) {
            if (list.stream().noneMatch(a -> a.getEmployeeId() == id)) {
                return false;
            }
        }
        return true;
    }

    private static long addProduct(ProductService products, String name, long priceCents, int stock, int reorder) throws BmsException {
        Product p = new Product();
        p.setProductName(name);
        p.setPriceCents(priceCents);
        p.setQuantityInStock(stock);
        p.setReorderLevel(reorder);
        long id = products.add(p);
        productIds.add(id);
        return id;
    }

    private static long addCustomer(CustomerService customers, String first, String last) throws BmsException {
        Customer c = new Customer();
        c.setFirstName(first);
        c.setLastName(last);
        long id = customers.add(c);
        customerIds.add(id);
        return id;
    }

    private static long addEmployee(EmployeeService employees, String first, String last, String username, String department) throws BmsException {
        EmployeeAccount e = new EmployeeAccount();
        e.setFirstName(first);
        e.setLastName(last);
        e.setUsername(username);
        e.setRole(Role.EMPLOYEE);
        e.setDepartment(department);
        e.setPassword("Check1234");
        e.setConfirmPassword("Check1234");
        long id = employees.add(e);
        employeeIds.add(id);
        return id;
    }

    /** Removes ONLY the test rows this program created (orders first: they point at customers and products). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement orders = c.prepareStatement("DELETE FROM orders WHERE order_id = ?");
             PreparedStatement adjustments = c.prepareStatement("DELETE FROM stock_adjustments WHERE product_id = ?");
             PreparedStatement products = c.prepareStatement("DELETE FROM products WHERE product_id = ?");
             PreparedStatement customers = c.prepareStatement("DELETE FROM customers WHERE customer_id = ?");
             PreparedStatement users = c.prepareStatement("DELETE FROM users WHERE employee_id = ?");
             PreparedStatement employees = c.prepareStatement("DELETE FROM employees WHERE employee_id = ?")) {
            for (long id : orderIds) {
                orders.setLong(1, id);
                orders.executeUpdate();     // its order_items are removed automatically (ON DELETE CASCADE)
            }
            for (long id : productIds) {
                adjustments.setLong(1, id);
                adjustments.executeUpdate();
                products.setLong(1, id);
                products.executeUpdate();
            }
            for (long id : customerIds) {
                customers.setLong(1, id);
                customers.executeUpdate();
            }
            for (long id : employeeIds) {
                users.setLong(1, id);
                users.executeUpdate();
                employees.setLong(1, id);
                employees.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("WARNING: could not remove all test rows: " + e.getMessage());
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
