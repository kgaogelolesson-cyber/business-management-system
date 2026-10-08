package com.bms.model;

/** One product row. productId is 0 for a product that has not been saved yet. */
public class Product {

    private long productId;
    private String productName;
    private String category;
    private String description;
    private long priceCents;          // 12999 means R129.99
    private int quantityInStock;
    private int reorderLevel;
    private Long supplierId;          // null = no supplier chosen
    private String supplierName;      // filled when loaded from the database
    private String stockStatus;       // IN STOCK / LOW STOCK / OUT OF STOCK (filled when loaded)

    public Product() {
    }

    public long getProductId() { return productId; }
    public void setProductId(long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }

    public int getQuantityInStock() { return quantityInStock; }
    public void setQuantityInStock(int quantityInStock) { this.quantityInStock = quantityInStock; }

    public int getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
}
