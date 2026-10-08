package com.bms.model;

/** One line of stock history (one row of the stock_adjustments table), for display. */
public class StockAdjustment {

    private final String adjustedAt;      // for example 2026-10-04 15:30:12
    private final int quantityChange;     // +10 = stock added, -3 = stock removed
    private final String reason;
    private final String adjustedBy;      // the employee's name (or the username if there is none)

    public StockAdjustment(String adjustedAt, int quantityChange, String reason, String adjustedBy) {
        this.adjustedAt = adjustedAt;
        this.quantityChange = quantityChange;
        this.reason = reason;
        this.adjustedBy = adjustedBy;
    }

    public String getAdjustedAt() { return adjustedAt; }
    public int getQuantityChange() { return quantityChange; }
    public String getReason() { return reason; }
    public String getAdjustedBy() { return adjustedBy; }
}
