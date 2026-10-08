package com.bms.model;

import java.util.ArrayList;
import java.util.List;

/**
 * An order (one row of orders) with the customer and employee names, plus its lines.
 * orderId is 0 for an order that has not been saved yet.
 * The class is called CustomerOrder because "Order" is also an SQL word.
 */
public class CustomerOrder {

    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private long orderId;
    private String orderDate;
    private String status = COMPLETED;
    private long totalCents;
    private long customerId;
    private String customerName;
    private long employeeId;
    private String employeeName;
    private final List<OrderLine> lines = new ArrayList<>();

    public CustomerOrder() {
    }

    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long totalCents) { this.totalCents = totalCents; }

    public long getCustomerId() { return customerId; }
    public void setCustomerId(long customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public long getEmployeeId() { return employeeId; }
    public void setEmployeeId(long employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public List<OrderLine> getLines() { return lines; }
}
