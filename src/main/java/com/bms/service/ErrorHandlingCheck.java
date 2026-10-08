package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AuthenticationException;
import com.bms.exception.BmsException;
import com.bms.exception.DatabaseErrors;
import com.bms.exception.DatabaseException;
import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import com.bms.model.EmployeeAccount;
import com.bms.model.Product;
import com.bms.model.Role;
import com.bms.model.Supplier;
import com.bms.service.ReportService.ReportType;
import com.bms.service.OrderService.StatusFilter;
import com.bms.util.AppErrors;
import com.bms.util.AppLogging;
import com.bms.util.Session;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for Phase 16 (validation and error handling): refusing control characters,
 * friendly messages for database problems (including a REAL locked database), and the log file.
 * It adds TEST rows (names starting "Check"), tests them, then removes ALL of its own rows again.
 * It needs the demo data (run DemoDataLoader once first). It takes about 20 seconds, because a locked
 * database makes the program wait 3 seconds before giving up. Run it on its own: right-click -> Run File.
 */
public final class ErrorHandlingCheck {

    private static int passed = 0;
    private static int total = 0;
    private static final List<Long> customerIds = new ArrayList<>();
    private static final List<Long> productIds = new ArrayList<>();
    private static final List<Long> supplierIds = new ArrayList<>();
    private static final List<Long> employeeIds = new ArrayList<>();

    private ErrorHandlingCheck() {
    }

    public static void main(String[] args) {
        try {
            check("The log file can be started", AppLogging.start());
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            CustomerService customers = new CustomerService();
            SupplierService suppliers = new SupplierService();
            ProductService products = new ProductService();
            EmployeeService employees = new EmployeeService();
            InventoryService inventory = new InventoryService();
            OrderService orders = new OrderService();
            DashboardService dashboard = new DashboardService();
            ReportService reports = new ReportService();
            auth.login("admin", "Admin@123");

            // ---------- 1. control characters are refused ----------
            String[] bad = {"Bob\nSmith", "Tab\tName", "Nul\u0000l", "Evil\u202Ename", "Zero\u200Bwidth", "Be\u0007ll"};
            for (String name : bad) {
                String shown = name.replace("\n", "\\n").replace("\t", "\\t").replace("\u0000", "\\0")
                        .replace("\u202E", "\\u202E").replace("\u200B", "\\u200B").replace("\u0007", "\\a");
                check("Customer first name [" + shown + "] is refused", fails(() -> addCustomer(customers, name, "Check"), ValidationException.class));
            }
            check("A control character at the very end is simply trimmed away (like spaces)", addCustomer(customers, "Check", "Trimmed\u0007") > 0);
            check("Customer last name with a line break is refused", fails(() -> addCustomer(customers, "Check", "Line\nBreak"), ValidationException.class));
            check("The message names the field", message(() -> addCustomer(customers, "Bob\nSmith", "Check")).contains("first name"));
            check("Customer address with a line break is refused", fails(() -> {
                Customer c = new Customer();
                c.setFirstName("Check");
                c.setLastName("Address");
                c.setAddress("1 Main Road\n2nd line");
                customerIds.add(customers.add(c));
            }, ValidationException.class));
            long before = countWhere("customers", "first_name LIKE 'Check%' OR last_name LIKE 'Check%'");
            check("Nothing was saved by the refused customers (only the trimmed one exists)", before == 1);

            long normal = addCustomer(customers, "Zo\u00EB", "Check \u00D1and\u00FA");
            check("Accented letters are accepted", normal > 0);
            String injection = "Check'); DROP TABLE customers;--";
            long inj = addCustomer(customers, injection, "Injection");
            check("A name that looks like SQL is accepted as plain text", inj > 0);
            check("...it is stored exactly as typed", customers.search("Injection").stream()
                    .anyMatch(c -> c.getCustomerId() == inj && injection.equals(c.getFirstName())));
            check("...and the customers table is still there", countWhere("customers", "1=1") > 0);

            check("Supplier name with a line break is refused", fails(() -> addSupplier(suppliers, "Check\nSupplier", null, null), ValidationException.class));
            check("Supplier contact person with a tab is refused", fails(() -> addSupplier(suppliers, "Check Supplier A", "Tab\tPerson", null), ValidationException.class));
            check("Supplier address with a NUL is refused", fails(() -> addSupplier(suppliers, "Check Supplier B", null, "Ro\u0000ad"), ValidationException.class));
            check("A normal supplier is accepted", addSupplier(suppliers, "Check Supplier OK", "Sipho Dlamini", "5 Long Street") > 0);

            check("Product name with a line break is refused", fails(() -> addProduct(products, "Check\nProduct", null, null), ValidationException.class));
            check("Product category with a tab is refused", fails(() -> addProduct(products, "Check Product A", "Tab\tCat", null), ValidationException.class));
            check("Product description with a NUL is refused", fails(() -> addProduct(products, "Check Product B", null, "bad\u0000text"), ValidationException.class));
            long withDescription = addProduct(products, "Check Product C", "CheckCat", "Line one\nLine two");
            check("A product description MAY have line breaks", withDescription > 0);
            check("...and the line break is kept", products.search("Check Product C", null).get(0).getDescription().contains("\n"));

            check("Employee position with a line break is refused", fails(() -> addEmployee(employees, "check_pos", "Boss\nMan", "Sales"), ValidationException.class));
            check("Employee department with a tab is refused", fails(() -> addEmployee(employees, "check_dep", "Clerk", "Sa\tles"), ValidationException.class));
            check("A normal employee is accepted", addEmployee(employees, "check_ok", "Clerk", "Sales") > 0);

            check("Stock reason with a line break is refused", fails(() -> inventory.adjustStock(withDescription, 5, "Delivery\nreceived"), ValidationException.class));
            check("A normal stock reason is accepted", inventory.adjustStock(withDescription, 5, "Delivery received") == 6);

            // ---------- 2. friendly messages for each kind of database problem ----------
            String base = "The orders could not be loaded. Please try again.";
            check("Unrecognised problem: just the base message", DatabaseErrors.friendly(base, new SQLException("something odd")).equals(base));
            check("Locked: tells the user to close the other program", DatabaseErrors.friendly(base, new SQLException("[SQLITE_BUSY] The database file is locked (database is locked)")).contains("another program"));
            check("Locked (other wording)", DatabaseErrors.friendly(base, new SQLException("database table is locked")).contains("another program"));
            check("Read-only file", DatabaseErrors.friendly(base, new SQLException("[SQLITE_READONLY] attempt to write a readonly database")).contains("read-only"));
            check("Disk full", DatabaseErrors.friendly(base, new SQLException("[SQLITE_FULL] database or disk is full")).contains("disk is full"));
            check("Damaged file", DatabaseErrors.friendly(base, new SQLException("[SQLITE_CORRUPT] database disk image is malformed")).contains("damaged"));
            check("Not a database", DatabaseErrors.friendly(base, new SQLException("[SQLITE_NOTADB] file is not a database")).contains("damaged"));
            check("Cannot open the file", DatabaseErrors.friendly(base, new SQLException("[SQLITE_CANTOPEN] unable to open database file")).contains("could not be opened"));
            check("The cause inside another exception is found too", DatabaseErrors.friendly(base, new SQLException("wrapper", new SQLException("database is locked"))).contains("another program"));
            check("Data folder cannot be created", DatabaseErrors.friendly(base, new SQLException("Could not create the database folder: C:\\x\\data")).contains("data folder could not be created"));
            check("Every hint keeps the base message first", DatabaseErrors.friendly(base, new SQLException("database is locked")).startsWith(base));

            // ---------- 3. a REAL locked database ----------
            long lockedCustomerCount = countWhere("customers", "1=1");
            Connection locker = DatabaseManager.getConnection();
            try (Statement st = locker.createStatement()) {
                st.execute("BEGIN EXCLUSIVE");   // another program (like DB Browser) holding the file
            }
            try {
                Session.end();
                BmsException loginError = errorOf(() -> auth.login("admin", "Admin@123"));
                check("Login while the database is locked gives a DatabaseException", loginError instanceof DatabaseException);
                check("...with a friendly message", isFriendly(loginError));
                check("...that says what to do", loginError != null && loginError.getMessage().contains("another program"));
                check("...and nobody is logged in", !Session.isLoggedIn());
            } finally {
                try (Statement st = locker.createStatement()) {
                    st.execute("ROLLBACK");
                }
            }
            auth.login("admin", "Admin@123");
            try (Statement st = locker.createStatement()) {
                st.execute("BEGIN EXCLUSIVE");
            }
            try {
                BmsException e1 = errorOf(() -> customers.search(""));
                check("Customer search while locked: friendly DatabaseException", e1 instanceof DatabaseException && isFriendly(e1) && e1.getMessage().contains("another program"));
                BmsException e2 = errorOf(() -> orders.search("", StatusFilter.ALL));
                check("Order search while locked: friendly DatabaseException", e2 instanceof DatabaseException && isFriendly(e2));
                BmsException e3 = errorOf(() -> dashboard.getSummary());
                check("Dashboard while locked: friendly DatabaseException", e3 instanceof DatabaseException && isFriendly(e3));
                BmsException e4 = errorOf(() -> reports.run(ReportType.SALES_BY_PRODUCT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
                check("Report while locked: friendly DatabaseException", e4 instanceof DatabaseException && isFriendly(e4));
                BmsException e5 = errorOf(() -> addCustomer(customers, "Check", "WhileLocked"));
                check("Saving a customer while locked: friendly DatabaseException", e5 instanceof DatabaseException && isFriendly(e5));
            } finally {
                try (Statement st = locker.createStatement()) {
                    st.execute("ROLLBACK");
                }
                locker.close();
            }
            check("The refused save left nothing behind", countWhere("customers", "1=1") == lockedCustomerCount
                    && countWhere("customers", "last_name = 'WhileLocked'") == 0);
            check("After the lock is released the same search works again", errorOf(() -> customers.search("")) == null);
            check("...and saving works again", addCustomer(customers, "Check", "AfterLock") > 0);

            // ---------- 4. the log file ----------
            String secret = "Secret-PW-Check-9";
            try {
                auth.login("admin", secret);
            } catch (AuthenticationException expected) {
                // fine: wrong password
            }
            AppErrors.report(new IllegalStateException("boom-check"), false);
            File logFile = AppLogging.logFile();
            check("The log file exists", logFile.isFile() && logFile.length() > 0);
            String log = logFile.isFile() ? Files.readString(logFile.toPath(), StandardCharsets.UTF_8) : "";
            check("The log recorded the locked-database problems", log.contains("SEVERE") && log.contains("Database error while"));
            check("The log keeps the technical detail (stack trace)", log.contains("org.sqlite") || log.contains("SQLException"));
            check("The log recorded the unexpected error", log.contains("Unexpected error") && log.contains("boom-check"));
            check("The log never contains the password that was typed", !log.contains(secret));
            check("The safety net is installed after AppErrors.install()", installAndTest());

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
        System.exit(passed == total ? 0 : 1);
    }

    // ---------- small helpers ----------

    private interface Action {
        void run() throws Exception;
    }

    private static boolean installAndTest() {
        AppErrors.install();
        return Thread.getDefaultUncaughtExceptionHandler() != null;
    }

    private static boolean fails(Action action, Class<? extends BmsException> expected) {
        return expected.isInstance(errorOf(action));
    }

    /** The BmsException the action throws, or null if it does not throw one. Other exceptions are reported as failures. */
    private static BmsException errorOf(Action action) {
        try {
            action.run();
            return null;
        } catch (BmsException e) {
            return e;
        } catch (Exception e) {
            System.out.println("  (unexpected " + e.getClass().getName() + ": " + e.getMessage() + ")");
            return new BmsException("UNEXPECTED " + e);
        }
    }

    private static String message(Action action) {
        BmsException e = errorOf(action);
        return e == null ? "" : String.valueOf(e.getMessage());
    }

    /** A friendly message has no technical words in it. */
    private static boolean isFriendly(BmsException e) {
        if (e == null) {
            return false;
        }
        String m = e.getMessage();
        return m != null && !m.contains("SQLITE") && !m.contains("org.sqlite") && !m.contains("Exception")
                && !m.contains("java.") && !m.contains("SQL");
    }

    private static long addCustomer(CustomerService customers, String first, String last) throws BmsException {
        Customer c = new Customer();
        c.setFirstName(first);
        c.setLastName(last);
        long id = customers.add(c);
        customerIds.add(id);
        return id;
    }

    private static long addSupplier(SupplierService suppliers, String name, String contact, String address) throws BmsException {
        Supplier s = new Supplier();
        s.setSupplierName(name);
        s.setContactPerson(contact);
        s.setAddress(address);
        long id = suppliers.add(s);
        supplierIds.add(id);
        return id;
    }

    private static long addProduct(ProductService products, String name, String category, String description) throws BmsException {
        Product p = new Product();
        p.setProductName(name);
        p.setCategory(category);
        p.setDescription(description);
        p.setPriceCents(1000);
        p.setQuantityInStock(1);
        p.setReorderLevel(1);
        long id = products.add(p);
        productIds.add(id);
        return id;
    }

    private static long addEmployee(EmployeeService employees, String username, String position, String department) throws BmsException {
        EmployeeAccount e = new EmployeeAccount();
        e.setFirstName("Check");
        e.setLastName("Person");
        e.setUsername(username);
        e.setRole(Role.EMPLOYEE);
        e.setPosition(position);
        e.setDepartment(department);
        e.setPassword("Check1234");
        e.setConfirmPassword("Check1234");
        long id = employees.add(e);
        employeeIds.add(id);
        return id;
    }

    /** Counts rows. The table and condition are our own constants above, never user input. */
    private static long countWhere(String table, String condition) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + condition);
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    /** Removes ONLY the test rows this program created. */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement adjustments = c.prepareStatement("DELETE FROM stock_adjustments WHERE product_id = ?");
             PreparedStatement products = c.prepareStatement("DELETE FROM products WHERE product_id = ?");
             PreparedStatement customers = c.prepareStatement("DELETE FROM customers WHERE customer_id = ?");
             PreparedStatement suppliers = c.prepareStatement("DELETE FROM suppliers WHERE supplier_id = ?");
             PreparedStatement users = c.prepareStatement("DELETE FROM users WHERE employee_id = ?");
             PreparedStatement employees = c.prepareStatement("DELETE FROM employees WHERE employee_id = ?")) {
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
            for (long id : supplierIds) {
                suppliers.setLong(1, id);
                suppliers.executeUpdate();
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
