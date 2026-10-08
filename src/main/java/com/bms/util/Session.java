package com.bms.util;

import com.bms.exception.AccessDeniedException;
import com.bms.model.Employee;
import com.bms.model.User;

/**
 * Remembers who is logged in while the application is running.
 * There is only ever one logged-in user at a time, so everything here is static.
 */
public final class Session {

    private static User currentUser;
    private static Employee currentEmployee;   // may be null if the account has no employee link

    private Session() {
    }

    public static void start(User user, Employee employee) {
        currentUser = user;
        currentEmployee = employee;
    }

    /** Called on logout: forgets everything about the user. */
    public static void end() {
        currentUser = null;
        currentEmployee = null;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static Employee getCurrentEmployee() {
        return currentEmployee;
    }

    /** Services call this first. It stops anyone who is not logged in. */
    public static void requireLoggedIn() throws AccessDeniedException {
        if (!isLoggedIn()) {
            throw new AccessDeniedException("You must log in first.");
        }
    }

    /** Services call this before any administrator-only action. */
    public static void requireAdmin() throws AccessDeniedException {
        requireLoggedIn();
        if (!isAdmin()) {
            throw new AccessDeniedException("You do not have permission to do this. Please ask an administrator.");
        }
    }
}
