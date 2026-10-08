package com.bms.model;

/** A login account (one row of the users table). */
public class User {

    private final long userId;
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final Role role;
    private final Long employeeId;   // null if the account is not linked to an employee
    private final boolean active;

    public User(long userId, String username, String passwordHash, String salt,
                Role role, Long employeeId, boolean active) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.employeeId = employeeId;
        this.active = active;
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public Role getRole() { return role; }
    public Long getEmployeeId() { return employeeId; }
    public boolean isActive() { return active; }
    public boolean isAdmin() { return role == Role.ADMIN; }
}
