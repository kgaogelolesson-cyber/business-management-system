package com.bms.model;

/** One supplier row. supplierId is 0 for a supplier that has not been saved yet. */
public class Supplier {

    private long supplierId;
    private String supplierName;
    private String contactPerson;
    private String email;
    private String phone;
    private String address;
    private int productCount;     // active products from this supplier (filled when loaded, read-only)

    public Supplier() {
    }

    public long getSupplierId() { return supplierId; }
    public void setSupplierId(long supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getProductCount() { return productCount; }
    public void setProductCount(int productCount) { this.productCount = productCount; }
}
