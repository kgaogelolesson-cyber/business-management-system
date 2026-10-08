package com.bms.model;

/** The two user roles. The names match the values stored in users.role. */
public enum Role {

    ADMIN("Administrator"),
    EMPLOYEE("Employee");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Converts the text stored in the database (for example "ADMIN") into a Role. */
    public static Role fromDatabase(String value) {
        return Role.valueOf(value.trim().toUpperCase());
    }
}
