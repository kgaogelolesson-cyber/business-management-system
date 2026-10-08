package com.bms.model;

import java.util.List;

/**
 * Everything the dashboard shows, collected in one object.
 * A "record" is a short way to write a class that only holds data.
 */
public record DashboardSummary(
        boolean fullView,                 // true = administrator view, false = limited employee view
        int totalCustomers,
        int totalProducts,
        int totalEmployees,
        int totalOrders,
        long totalRevenueCents,
        int lowStockCount,
        List<LowStockItem> lowStockItems,
        List<RecentOrder> recentOrders,
        List<RecentCustomer> recentCustomers,
        String currencySymbol) {

    /** One product that is low or out of stock. */
    public record LowStockItem(String productName, int quantityInStock, int reorderLevel, String stockStatus) {
    }

    /** One line of the "recent orders" table. */
    public record RecentOrder(long orderId, String orderDate, String customerName,
                              String employeeName, long totalCents, String status) {
    }

    /** One line of the "recent customers" table. */
    public record RecentCustomer(String customerName, String email, String createdAt) {
    }
}
