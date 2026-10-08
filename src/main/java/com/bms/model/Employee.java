package com.bms.model;

import java.time.LocalDate;

/** A staff member (one row of the employees table). */
public class Employee {

    private final long employeeId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String position;
    private final String department;
    private final LocalDate hireDate;   // may be null
    private final boolean active;

    public Employee(long employeeId, String firstName, String lastName, String email,
                    String phone, String position, String department,
                    LocalDate hireDate, boolean active) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.position = position;
        this.department = department;
        this.hireDate = hireDate;
        this.active = active;
    }

    public long getEmployeeId() { return employeeId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPosition() { return position; }
    public String getDepartment() { return department; }
    public LocalDate getHireDate() { return hireDate; }
    public boolean isActive() { return active; }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
