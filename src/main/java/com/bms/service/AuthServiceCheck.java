package com.bms.service;

import com.bms.dao.UserDao;
import com.bms.database.DatabaseInitializer;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.AuthenticationException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.AppModule;
import com.bms.model.Role;
import com.bms.util.Session;
import java.sql.SQLException;

/**
 * A console self-test for the login rules (no windows involved).
 * Run it on its own: right-click -> Run File. It changes nothing in the database.
 */
public final class AuthServiceCheck {

    private static int passed = 0;
    private static int total = 0;

    private AuthServiceCheck() {
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();

            expectFailure("Empty username is rejected", auth, "", "Admin@123", ValidationException.class);
            expectFailure("Empty password is rejected", auth, "admin", "", ValidationException.class);
            expectFailure("Unknown username is rejected", auth, "nobody", "Admin@123", AuthenticationException.class);
            expectFailure("Wrong password is rejected", auth, "admin", "wrong-password", AuthenticationException.class);

            expectSuccess("Admin logs in with the correct password", auth, "admin", "Admin@123", Role.ADMIN);
            check("Session knows the employee behind the admin account",
                    Session.getCurrentEmployee() != null
                            && "System Administrator".equals(Session.getCurrentEmployee().getFullName()),
                    "");
            check("Admin passes the administrator check", passesAdminCheck(), "");
            check("Admin may open Reports", AccessControl.canAccess(Role.ADMIN, AppModule.REPORTS), "");

            auth.logout();
            check("After logout nobody is logged in", !Session.isLoggedIn(), "");

            expectSuccess("Username is not case-sensitive", auth, "ADMIN", "Admin@123", Role.ADMIN);
            auth.logout();

            if (new UserDao().findByUsername("thandi") != null) {
                expectSuccess("Employee logs in", auth, "thandi", "Employee@123", Role.EMPLOYEE);
                check("Employee is stopped by the administrator check", !passesAdminCheck(), "");
                check("Employee may open Customers", AccessControl.canAccess(Role.EMPLOYEE, AppModule.CUSTOMERS), "");
                check("Employee may NOT open Reports", !AccessControl.canAccess(Role.EMPLOYEE, AppModule.REPORTS), "");
                check("Employee may NOT open Suppliers", !AccessControl.canAccess(Role.EMPLOYEE, AppModule.SUPPLIERS), "");
                auth.logout();
            } else {
                System.out.println("SKIPPED: employee tests (run DemoDataLoader first to create the user 'thandi').");
            }

            System.out.println();
            System.out.println(passed + " of " + total + " checks passed.");
        } catch (SQLException e) {
            System.err.println("Could not run the checks: " + e.getMessage());
        }
    }

    private static boolean passesAdminCheck() {
        try {
            Session.requireAdmin();
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }

    private static void expectFailure(String description, AuthService auth, String username,
                                      String password, Class<? extends BmsException> expectedType) {
        try {
            auth.login(username, password);
            check(description, false, "login unexpectedly succeeded");
            auth.logout();
        } catch (BmsException e) {
            check(description, expectedType.isInstance(e) && !Session.isLoggedIn(), "message: " + e.getMessage());
        }
    }

    private static void expectSuccess(String description, AuthService auth, String username,
                                      String password, Role expectedRole) {
        try {
            auth.login(username, password);
            check(description, Session.isLoggedIn() && Session.getCurrentUser().getRole() == expectedRole,
                    "role: " + Session.getCurrentUser().getRole());
        } catch (BmsException e) {
            check(description, false, "login failed: " + e.getMessage());
        }
    }

    private static void check(String description, boolean ok, String detail) {
        total++;
        if (ok) {
            passed++;
        }
        System.out.println((ok ? "PASS: " : "FAIL: ") + description
                + (detail.isEmpty() ? "" : "   (" + detail + ")"));
    }
}
