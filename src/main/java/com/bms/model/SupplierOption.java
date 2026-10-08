package com.bms.model;

/**
 * A supplier as shown in a drop-down list (just its id and name).
 * toString() is what a JComboBox displays.
 */
public record SupplierOption(long supplierId, String supplierName) {

    @Override
    public String toString() {
        return supplierName;
    }
}
