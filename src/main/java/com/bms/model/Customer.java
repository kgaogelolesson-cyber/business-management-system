package com.bms.model;

/** One customer row. customerId is 0 for a customer that has not been saved yet. */
public class Customer {

    private long customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String createdAt;

    public Customer() {
    }

    public Customer(long customerId, String firstName, String lastName, String email,
                    String phone, String address, String createdAt) {
        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.createdAt = createdAt;
    }

    public long getCustomerId() { return customerId; }
    public void setCustomerId(long customerId) { this.customerId = customerId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCreatedAt() { return createdAt; }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
