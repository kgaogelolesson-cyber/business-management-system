package com.bms.service;

import com.bms.model.AppModule;
import com.bms.model.Role;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single place that says which role may open which module.
 * Screens use it to decide what to show. Finer rules (for example "only an
 * administrator may delete a customer") are enforced in the services with
 * Session.requireAdmin().
 */
public final class AccessControl {

    private static final Map<Role, Set<AppModule>> ACCESS = new EnumMap<>(Role.class);

    static {
        ACCESS.put(Role.ADMIN, EnumSet.allOf(AppModule.class));
        ACCESS.put(Role.EMPLOYEE, EnumSet.of(
                AppModule.DASHBOARD,
                AppModule.CUSTOMERS,
                AppModule.PRODUCTS,
                AppModule.ORDERS,
                AppModule.INVENTORY));
    }

    private AccessControl() {
    }

    public static boolean canAccess(Role role, AppModule module) {
        return ACCESS.get(role).contains(module);
    }

    /** The modules this role may use, in a fixed, sensible order. */
    public static List<AppModule> getModulesFor(Role role) {
        List<AppModule> modules = new ArrayList<>();
        for (AppModule module : AppModule.values()) {
            if (canAccess(role, module)) {
                modules.add(module);
            }
        }
        return modules;
    }
}
