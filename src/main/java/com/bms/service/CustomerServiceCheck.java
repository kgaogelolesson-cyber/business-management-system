package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import com.bms.util.Session;

/**
 * A console self-test for customer management. It adds a few TEST customers
 * (names starting "Check", which it creates and removes itself), tests the rules, then removes its own test rows again.
 * Run it on its own: right-click -> Run File.
 */
public final class CustomerServiceCheck {

    private static int passed = 0;
    private static int total = 0;
    /** Ids of the test customers this program created, so it removes only those. */
    private static final List<Long> createdIds = new java.util.ArrayList<>();

    private CustomerServiceCheck() {
    }

    public static void main(String[] args) {
        long idA;
        long idB;
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            CustomerService customers = new CustomerService();

            // --- Not logged in ---
            Session.end();
            check("Customers are refused when nobody is logged in", fails(() -> customers.search(""), AccessDeniedException.class));

            // --- Employee: may add / edit / search, may NOT delete ---
            auth.login("thandi", "Employee@123");
            Customer a = make("CheckAnna", "Tester", "check.anna@example.com", "082 555 0101", "1 Test Street");
            idA = customers.add(a);
            createdIds.add(idA);
            check("Employee can add a customer", idA > 0);

            check("Search finds the new customer by first name", containsId(customers.search("checkanna"), idA));
            check("Search finds the new customer by email", containsId(customers.search("check.anna@"), idA));
            check("Search finds the new customer by phone", containsId(customers.search("555 0101"), idA));
            check("Search finds the new customer by full name", containsId(customers.search("CheckAnna Tester"), idA));
            check("Search for something that does not exist finds nothing", customers.search("zzzqqq").isEmpty());
            check("Searching for % is treated as a normal character, not a wildcard", customers.search("%").isEmpty());

            Customer edit = make("CheckAnna", "Updated", "check.anna@example.com", "082 555 0101", "2 New Road");
            edit.setCustomerId(idA);
            customers.update(edit);
            check("Edit changes the surname", customers.search("CheckAnna").get(0).getLastName().equals("Updated"));

            check("Employee cannot delete a customer", fails(() -> customers.delete(0), AccessDeniedException.class));

            // --- Validation ---
            check("First name is required", fails(() -> customers.add(make("  ", "X", null, null, null)), ValidationException.class));
            check("Last name is required", fails(() -> customers.add(make("CheckX", "", null, null, null)), ValidationException.class));
            check("A bad email is refused", fails(() -> customers.add(make("CheckX", "Y", "not-an-email", null, null)), ValidationException.class));
            check("A bad phone (letters) is refused", fails(() -> customers.add(make("CheckX", "Y", null, "abc123", null)), ValidationException.class));
            check("A too-short phone is refused", fails(() -> customers.add(make("CheckX", "Y", null, "123", null)), ValidationException.class));
            check("A too-long name is refused", fails(() -> customers.add(make("C".repeat(51), "Y", null, null, null)), ValidationException.class));
            check("Email and phone may be left empty", tryAdd(customers, make("CheckBen", "Tester", "  ", "", "")) > 0);

            // --- Duplicate warning ---
            Customer dup = make("CheckAnna2", "Other", "CHECK.ANNA@example.com", null, null);
            check("Same email (any capitals) is flagged as a possible duplicate", containsId(customers.findPossibleDuplicates(dup), idA));
            Customer dupPhone = make("CheckAnna3", "Other", null, "082 555 0101", null);
            check("Same phone is flagged as a possible duplicate", containsId(customers.findPossibleDuplicates(dupPhone), idA));
            Customer noDup = make("CheckNew", "Person", "different@example.com", "011 000 9999", null);
            check("A different email and phone is not flagged", customers.findPossibleDuplicates(noDup).isEmpty());
            Customer self = make("CheckAnna", "Updated", "check.anna@example.com", "082 555 0101", null);
            self.setCustomerId(idA);
            check("A customer is not flagged as a duplicate of themselves", customers.findPossibleDuplicates(self).isEmpty());
            check("Two customers with no email/phone are not flagged as duplicates",
                    customers.findPossibleDuplicates(make("CheckBen", "Tester", null, null, null)).isEmpty());

            // --- Administrator: delete rules ---
            auth.login("admin", "Admin@123");
            idB = customers.search("CheckBen").get(0).getCustomerId();
            customers.delete(idB);
            check("Administrator can delete a customer with no orders", !containsId(customers.search("CheckBen"), idB));
            check("The deleted customer is only hidden, not erased (row still in the database)", rowExists(idB));

            // A customer with an order cannot be deleted
            long customerWithOrder = firstCustomerWithOrder();
            if (customerWithOrder > 0) {
                check("A customer who has orders cannot be deleted", fails(() -> customers.delete(customerWithOrder), ValidationException.class));
                check("...and is still listed afterwards", customers.search("").stream().anyMatch(c -> c.getCustomerId() == customerWithOrder));
            } else {
                System.out.println("SKIPPED: no customer with orders found (run DemoDataLoader first to test this rule).");
            }

            check("Deleting a customer that does not exist gives a clear error", fails(() -> customers.delete(999999), ValidationException.class));

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

    private static long tryAdd(CustomerService service, Customer c) throws BmsException {
        long id = service.add(c);
        createdIds.add(id);
        return id;
    }

    private static Customer make(String first, String last, String email, String phone, String address) {
        Customer c = new Customer();
        c.setFirstName(first);
        c.setLastName(last);
        c.setEmail(email);
        c.setPhone(phone);
        c.setAddress(address);
        return c;
    }

    private static boolean containsId(List<Customer> list, long id) {
        return list.stream().anyMatch(c -> c.getCustomerId() == id);
    }

    private static boolean rowExists(long id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             var st = c.prepareStatement("SELECT COUNT(*) FROM customers WHERE customer_id = ?")) {
            st.setLong(1, id);
            try (var rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static long firstCustomerWithOrder() throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             var st = c.prepareStatement("SELECT customer_id FROM orders o JOIN customers c USING (customer_id) "
                     + "WHERE c.is_active = 1 LIMIT 1");
             var rs = st.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Removes ONLY the test customers this program created (they have no orders, so this is safe). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("DELETE FROM customers WHERE customer_id = ?")) {
            for (long id : createdIds) {
                st.setLong(1, id);
                st.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Could not remove the test customers: " + e.getMessage());
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
