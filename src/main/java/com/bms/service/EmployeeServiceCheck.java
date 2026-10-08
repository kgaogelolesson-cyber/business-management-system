package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.AuthenticationException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.EmployeeAccount;
import com.bms.model.Role;
import com.bms.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for employee management. It adds TEST employees (usernames starting "check"),
 * tests the rules, then removes its own test rows again.
 * It needs the demo data (run DemoDataLoader once first). Run it on its own: right-click -> Run File.
 */
public final class EmployeeServiceCheck {

    private static int passed = 0;
    private static int total = 0;
    /** Ids of the test employees this program created, so it removes only those. */
    private static final List<Long> createdEmployeeIds = new ArrayList<>();

    private EmployeeServiceCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            EmployeeService employees = new EmployeeService();

            // --- Not logged in ---
            Session.end();
            check("Employees are refused when nobody is logged in",
                    fails(() -> employees.search("", false), AccessDeniedException.class));

            // --- Employee role: no access at all ---
            auth.login("thandi", "Employee@123");
            check("Employee cannot view the employee list", fails(() -> employees.search("", false), AccessDeniedException.class));
            check("Employee cannot add an employee", fails(() -> employees.add(make("Check", "Nope", "checknope", "Passw0rdOK")), AccessDeniedException.class));
            check("Employee cannot edit an employee", fails(() -> employees.update(make("Check", "Nope", "checknope", "Passw0rdOK")), AccessDeniedException.class));
            check("Employee cannot deactivate anyone", fails(() -> employees.deactivate(1), AccessDeniedException.class));
            check("Employee cannot reactivate anyone", fails(() -> employees.reactivate(1), AccessDeniedException.class));
            check("Employee cannot reset a password", fails(() -> employees.resetPassword(1, "Passw0rdOK", "Passw0rdOK"), AccessDeniedException.class));
            check("Employee cannot validate an employee form", fails(() -> employees.validateEmployee(make("Check", "Nope", "checknope", "Passw0rdOK")), AccessDeniedException.class));

            // --- Administrator: the demo employees ---
            auth.login("admin", "Admin@123");
            List<EmployeeAccount> all = employees.search("", false);
            EmployeeAccount admin = findByUsername(all, "admin");
            EmployeeAccount thandi = findByUsername(all, "thandi");
            check("Administrator can view the employees", admin != null && thandi != null);
            check("The admin account shows the Administrator role", admin != null && admin.getRole() == Role.ADMIN);
            check("Thandi shows the Employee role", thandi != null && thandi.getRole() == Role.EMPLOYEE);
            check("You cannot deactivate your own account",
                    admin != null && fails(() -> employees.deactivate(admin.getEmployeeId()), ValidationException.class));
            check("...and you are still active afterwards", findByUsername(employees.search("", false), "admin") != null);
            check("You cannot change your own role", admin != null && fails(() -> {
                admin.setRole(Role.EMPLOYEE);
                try {
                    employees.update(admin);
                } finally {
                    admin.setRole(Role.ADMIN);
                }
            }, ValidationException.class));
            check("...and your role is unchanged",
                    Role.ADMIN == findByUsername(employees.search("", false), "admin").getRole());

            // --- Add and search ---
            EmployeeAccount a = make("Checka", "Tester", "checka.tester", "Passw0rdOK");
            a.setEmail("checka@example.com");
            a.setPhone("082 555 0123");
            a.setPosition("Sales Assistant");
            a.setDepartment("Retail Floor");
            a.setHireDate("2026-03-15");
            long idA = add(employees, a);
            check("Administrator can add an employee", idA > 0);
            check("Search finds it by name", containsId(employees.search("checka tester", false), idA));
            check("Search finds it by username", containsId(employees.search("checka.tester", false), idA));
            check("Search finds it by email", containsId(employees.search("checka@example", false), idA));
            check("Search finds it by phone", containsId(employees.search("555 0123", false), idA));
            check("Search finds it by position", containsId(employees.search("sales assist", false), idA));
            check("Search finds it by department", containsId(employees.search("retail floor", false), idA));
            check("Search for something that does not exist finds nothing", employees.search("zzzqqq", false).isEmpty());
            check("Searching for % is treated as a normal character", employees.search("%", false).isEmpty());
            EmployeeAccount savedA = find(employees.search("checka", false), idA);
            check("All details were saved",
                    savedA != null && "Checka".equals(savedA.getFirstName()) && "Tester".equals(savedA.getLastName())
                    && "checka@example.com".equals(savedA.getEmail()) && "082 555 0123".equals(savedA.getPhone())
                    && "Sales Assistant".equals(savedA.getPosition()) && "Retail Floor".equals(savedA.getDepartment())
                    && "2026-03-15".equals(savedA.getHireDate()) && "checka.tester".equals(savedA.getUsername())
                    && savedA.getRole() == Role.EMPLOYEE && savedA.isActive());
            check("Only name, username, role and password are required",
                    ok(() -> add(employees, make("Checkb", "Bare", "checkb.bare", "Passw0rdOK"))));

            // --- The new login account really works, and the password is not stored as text ---
            auth.login("checka.tester", "Passw0rdOK");
            check("The new employee can log in with the password that was set",
                    Session.isLoggedIn() && "checka.tester".equals(Session.getCurrentUser().getUsername()));
            check("...and sees the employee role (not admin)", !Session.isAdmin());
            check("...and that new employee cannot open the employee list",
                    fails(() -> employees.search("", false), AccessDeniedException.class));
            auth.login("admin", "Admin@123");
            check("The password is stored hashed, not as plain text", !storedPasswordHash("checka.tester").contains("Passw0rdOK"));

            // --- Validation ---
            check("Empty first name is refused", fails(() -> employees.add(make("", "Tester", "checkv1", "Passw0rdOK")), ValidationException.class));
            check("Empty last name is refused", fails(() -> employees.add(make("Checkv", " ", "checkv2", "Passw0rdOK")), ValidationException.class));
            check("Digits in a name are refused", fails(() -> employees.add(make("Check123", "Tester", "checkv3", "Passw0rdOK")), ValidationException.class));
            check("A name longer than 50 characters is refused", fails(() -> employees.add(make("C".repeat(51), "Tester", "checkv4", "Passw0rdOK")), ValidationException.class));
            check("Names with an apostrophe or hyphen are allowed",
                    ok(() -> add(employees, make("Checko", "O'Neil-Smith", "checko.neil", "Passw0rdOK"))));
            check("Bad email is refused", fails(() -> employees.add(withEmail(make("Checkv", "Mail", "checkv5", "Passw0rdOK"), "not-an-email")), ValidationException.class));
            check("Email already used by another active employee is refused",
                    fails(() -> employees.add(withEmail(make("Checkv", "Dup", "checkv6", "Passw0rdOK"), "CHECKA@example.com")), ValidationException.class));
            check("Bad phone is refused", fails(() -> {
                EmployeeAccount p = make("Checkv", "Phone", "checkv7", "Passw0rdOK");
                p.setPhone("abc");
                employees.add(p);
            }, ValidationException.class));
            check("Hire date in the wrong format is refused", fails(() -> employees.add(withHireDate(make("Checkv", "Date", "checkv8", "Passw0rdOK"), "15/03/2026")), ValidationException.class));
            check("A date that does not exist (2026-02-30) is refused", fails(() -> employees.add(withHireDate(make("Checkv", "Date", "checkv9", "Passw0rdOK"), "2026-02-30")), ValidationException.class));
            check("A hire date in the future is refused", fails(() -> employees.add(withHireDate(make("Checkv", "Date", "checkv10", "Passw0rdOK"), "2999-01-01")), ValidationException.class));
            check("A hire date before 1950 is refused", fails(() -> employees.add(withHireDate(make("Checkv", "Date", "checkv11", "Passw0rdOK"), "1949-12-31")), ValidationException.class));
            check("A role is required", fails(() -> {
                EmployeeAccount r = make("Checkv", "Role", "checkv12", "Passw0rdOK");
                r.setRole(null);
                employees.add(r);
            }, ValidationException.class));
            check("Username is required", fails(() -> employees.add(make("Checkv", "User", "", "Passw0rdOK")), ValidationException.class));
            check("A username shorter than 3 characters is refused", fails(() -> employees.add(make("Checkv", "User", "ab", "Passw0rdOK")), ValidationException.class));
            check("A username with a space is refused", fails(() -> employees.add(make("Checkv", "User", "check v", "Passw0rdOK")), ValidationException.class));
            check("A username already taken is refused (capital letters ignored)",
                    fails(() -> employees.add(make("Checkv", "User", "CHECKA.TESTER", "Passw0rdOK")), ValidationException.class));
            check("The username 'admin' is taken", fails(() -> employees.add(make("Checkv", "User", "Admin", "Passw0rdOK")), ValidationException.class));
            check("A password shorter than 8 characters is refused", fails(() -> employees.add(make("Checkv", "Pw", "checkv13", "Ab1")), ValidationException.class));
            check("A password with no number is refused", fails(() -> employees.add(make("Checkv", "Pw", "checkv14", "OnlyLetters")), ValidationException.class));
            check("A password with no letter is refused", fails(() -> employees.add(make("Checkv", "Pw", "checkv15", "12345678")), ValidationException.class));
            check("A password equal to the username is refused", fails(() -> employees.add(make("Checkv", "Pw", "checkv16a1", "checkv16a1")), ValidationException.class));
            check("Passwords that do not match are refused", fails(() -> {
                EmployeeAccount m = make("Checkv", "Pw", "checkv17", "Passw0rdOK");
                m.setConfirmPassword("Different123");
                employees.add(m);
            }, ValidationException.class));
            check("validateEmployee gives the same refusal without saving",
                    fails(() -> employees.validateEmployee(make("Checkv", "Pw", "checkv18", "short")), ValidationException.class)
                    && employees.search("checkv18", true).isEmpty());
            check("A refused employee leaves nothing behind (no employee and no account)",
                    employees.search("checkv", true).isEmpty());

            // --- Edit ---
            EmployeeAccount edit = find(employees.search("checka", false), idA);
            edit.setFirstName("Checkaa");
            edit.setPosition("Senior Sales Assistant");
            edit.setEmail("");
            edit.setRole(Role.ADMIN);
            employees.update(edit);
            EmployeeAccount afterEdit = find(employees.search("checkaa", false), idA);
            check("Editing saves the new details", afterEdit != null && "Senior Sales Assistant".equals(afterEdit.getPosition()));
            check("An empty email is saved as no email", afterEdit != null && afterEdit.getEmail() == null);
            check("The role can be changed by another administrator", afterEdit != null && afterEdit.getRole() == Role.ADMIN);
            auth.login("checka.tester", "Passw0rdOK");
            check("...and the changed role takes effect at the next login", Session.isAdmin());
            auth.login("admin", "Admin@123");
            check("Editing never changes the username or password",
                    afterEdit != null && "checka.tester".equals(afterEdit.getUsername())
                    && ok(() -> auth.login("checka.tester", "Passw0rdOK")));
            auth.login("admin", "Admin@123");
            check("Editing with a bad value is refused", fails(() -> {
                EmployeeAccount bad = find(employees.search("checkaa", false), idA);
                bad.setPhone("x");
                employees.update(bad);
            }, ValidationException.class));
            check("Editing keeps your own email without a false 'already used' error", ok(() -> {
                EmployeeAccount self = find(employees.search("checkaa", false), idA);
                self.setEmail("checka@example.com");
                employees.update(self);
                employees.update(self);
            }));
            check("Editing an employee that does not exist gives a clear error", fails(() -> {
                EmployeeAccount ghost = make("Check", "Ghost", "checkghost", "Passw0rdOK");
                ghost.setEmployeeId(999999);
                employees.update(ghost);
            }, ValidationException.class));

            // --- Reset password ---
            employees.resetPassword(idA, "NewPassw0rd", "NewPassw0rd");
            check("After a reset the old password no longer works", fails(() -> auth.login("checka.tester", "Passw0rdOK"), AuthenticationException.class));
            check("After a reset the new password works", ok(() -> auth.login("checka.tester", "NewPassw0rd")));
            auth.login("admin", "Admin@123");
            check("A weak new password is refused", fails(() -> employees.resetPassword(idA, "abc", "abc"), ValidationException.class));
            check("A new password that does not match its confirmation is refused",
                    fails(() -> employees.resetPassword(idA, "NewPassw0rd2", "NewPassw0rd3"), ValidationException.class));
            check("...and the password is unchanged after a refusal", ok(() -> auth.login("checka.tester", "NewPassw0rd")));
            auth.login("admin", "Admin@123");
            check("Resetting the password of an employee that does not exist gives a clear error",
                    fails(() -> employees.resetPassword(999999, "NewPassw0rd", "NewPassw0rd"), ValidationException.class));

            // --- Deactivate / reactivate ---
            employees.deactivate(idA);
            check("A deactivated employee is hidden from the normal list", find(employees.search("checkaa", false), idA) == null);
            check("...but shown when deactivated employees are included",
                    find(employees.search("checkaa", true), idA) != null && !find(employees.search("checkaa", true), idA).isActive());
            check("A deactivated employee cannot log in", fails(() -> auth.login("checka.tester", "NewPassw0rd"), AuthenticationException.class));
            check("The row is only hidden, not deleted", employeeRowExists(idA));
            check("A deactivated employee cannot be edited", fails(() -> {
                EmployeeAccount gone = find(employees.search("checkaa", true), idA);
                gone.setPosition("Changed");
                employees.update(gone);
            }, ValidationException.class));
            check("A deactivated employee's password cannot be reset", fails(() -> employees.resetPassword(idA, "NewPassw0rd", "NewPassw0rd"), ValidationException.class));
            check("Deactivating twice gives a clear error", fails(() -> employees.deactivate(idA), ValidationException.class));
            check("A deactivated employee's email can be used by a new employee",
                    ok(() -> add(employees, withEmail(make("Checkr", "Reuse", "checkr.reuse", "Passw0rdOK"), "checka@example.com"))));
            check("Reactivating while another active employee uses the same email is refused",
                    fails(() -> employees.reactivate(idA), ValidationException.class));
            EmployeeAccount reuse = findByUsername(employees.search("checkr", false), "checkr.reuse");
            reuse.setEmail("checkr@example.com");
            employees.update(reuse);
            employees.reactivate(idA);
            check("A reactivated employee is listed again", find(employees.search("checkaa", false), idA) != null);
            check("A reactivated employee can log in again", ok(() -> auth.login("checka.tester", "NewPassw0rd")));
            auth.login("admin", "Admin@123");
            check("Reactivating an employee who is already active gives a clear error", fails(() -> employees.reactivate(idA), ValidationException.class));
            check("Deactivating an employee that does not exist gives a clear error", fails(() -> employees.deactivate(999999), ValidationException.class));

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

    /** A new (unsaved) employee with the given names, username and password (typed twice). */
    private static EmployeeAccount make(String first, String last, String username, String password) {
        EmployeeAccount e = new EmployeeAccount();
        e.setFirstName(first);
        e.setLastName(last);
        e.setUsername(username);
        e.setRole(Role.EMPLOYEE);
        e.setPassword(password);
        e.setConfirmPassword(password);
        return e;
    }

    private static EmployeeAccount withEmail(EmployeeAccount e, String email) {
        e.setEmail(email);
        return e;
    }

    private static EmployeeAccount withHireDate(EmployeeAccount e, String date) {
        e.setHireDate(date);
        return e;
    }

    private static long add(EmployeeService service, EmployeeAccount e) throws BmsException {
        long id = service.add(e);
        createdEmployeeIds.add(id);
        return id;
    }

    private static EmployeeAccount find(List<EmployeeAccount> list, long id) {
        return list.stream().filter(e -> e.getEmployeeId() == id).findFirst().orElse(null);
    }

    private static EmployeeAccount findByUsername(List<EmployeeAccount> list, String username) {
        return list.stream().filter(e -> username.equals(e.getUsername())).findFirst().orElse(null);
    }

    private static boolean containsId(List<EmployeeAccount> list, long id) {
        return find(list, id) != null;
    }

    private static boolean employeeRowExists(long id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM employees WHERE employee_id = ?")) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private static String storedPasswordHash(String username) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT password_hash FROM users WHERE username = ?")) {
            st.setString(1, username);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    /** Removes ONLY the test rows this program created (the login account first, because it points at the employee). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement users = c.prepareStatement("DELETE FROM users WHERE employee_id = ?");
             PreparedStatement employees = c.prepareStatement("DELETE FROM employees WHERE employee_id = ?")) {
            for (long id : createdEmployeeIds) {
                users.setLong(1, id);
                users.executeUpdate();
                employees.setLong(1, id);
                employees.executeUpdate();
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
