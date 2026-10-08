package com.bms.model;

/** The parts (modules) of the application that a role may or may not use. */
public enum AppModule {

    DASHBOARD("Dashboard"),
    CUSTOMERS("Customers"),
    PRODUCTS("Products"),
    SUPPLIERS("Suppliers"),
    EMPLOYEES("Employees"),
    ORDERS("Orders"),
    INVENTORY("Inventory"),
    REPORTS("Reports"),
    USERS("User Management"),
    SETTINGS("Settings");

    private final String displayName;

    AppModule(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
