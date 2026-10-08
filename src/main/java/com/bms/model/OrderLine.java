package com.bms.model;

/**
 * One product on an order (one row of order_items), for display and for creating an order.
 * When an order is created, only productId and quantity are used: the unit price is read
 * from the database at that moment and then stored with the order line.
 */
public class OrderLine {

    private final long productId;
    private final String productName;
    private final int quantity;
    private final long unitPriceCents;

    public OrderLine(long productId, String productName, int quantity, long unitPriceCents) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
    }

    public long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public long getSubtotalCents() { return quantity * unitPriceCents; }
}
