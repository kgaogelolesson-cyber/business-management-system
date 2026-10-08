package com.bms.dao;

import com.bms.database.DatabaseManager;
import com.bms.model.CustomerOrder;
import com.bms.model.OrderLine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** All SQL for orders and order lines lives here (and nowhere else). */
public class OrderDao {

    /** The most orders one search returns (newest first). The screens tell the user when the list was cut. */
    public static final int LIST_LIMIT = 1000;

    /** Same as the full search, without a customer or date limit. */
    public List<CustomerOrder> search(String text, String statusFilter, Long employeeId) throws SQLException {
        return search(text, statusFilter, employeeId, null, null, null);
    }

    /**
     * Orders whose number, date, customer name or employee name contains the text.
     * statusFilter = COMPLETED / CANCELLED, or null for both.
     * employeeId = only that employee's orders, or null for everyone's.
     * customerId = only that customer's orders, or null for every customer.
     * fromDate / toDate = "yyyy-MM-dd" (both ends included), or null for no limit.
     * Newest first.
     */
    public List<CustomerOrder> search(String text, String statusFilter, Long employeeId,
                                      Long customerId, String fromDate, String toDate) throws SQLException {
        String sql = "SELECT order_id, order_date, status, total_amount_cents, customer_id, customer_name, "
                + "       employee_id, employee_name "
                + "FROM v_order_summary "
                + "WHERE (? IS NULL OR status = ?) "
                + "  AND (? IS NULL OR employee_id = ?) "
                + "  AND (? IS NULL OR customer_id = ?) "
                + "  AND (? IS NULL OR date(order_date) >= ?) "
                + "  AND (? IS NULL OR date(order_date) <= ?) "
                + "  AND (CAST(order_id AS TEXT) LIKE ? ESCAPE '\\' "
                + "       OR order_date LIKE ? ESCAPE '\\' "
                + "       OR customer_name LIKE ? ESCAPE '\\' "
                + "       OR employee_name LIKE ? ESCAPE '\\') "
                + "ORDER BY order_date DESC, order_id DESC LIMIT " + LIST_LIMIT;

        String pattern = "%" + escapeLike(text) + "%";
        List<CustomerOrder> result = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, statusFilter);
            statement.setString(2, statusFilter);
            setNullableLong(statement, 3, employeeId);
            setNullableLong(statement, 4, employeeId);
            setNullableLong(statement, 5, customerId);
            setNullableLong(statement, 6, customerId);
            statement.setString(7, fromDate);
            statement.setString(8, fromDate);
            statement.setString(9, toDate);
            statement.setString(10, toDate);
            for (int i = 11; i <= 14; i++) {
                statement.setString(i, pattern);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readOrder(rs));
                }
            }
        }
        return result;
    }

    /** One order (without its lines), or null if there is no such order. */
    public CustomerOrder findById(long orderId) throws SQLException {
        String sql = "SELECT order_id, order_date, status, total_amount_cents, customer_id, customer_name, "
                + "       employee_id, employee_name FROM v_order_summary WHERE order_id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readOrder(rs) : null;
            }
        }
    }

    /** The lines of one order, in the order they were added. */
    public List<OrderLine> findLines(long orderId) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection()) {
            return findLines(connection, orderId);
        }
    }

    private List<OrderLine> findLines(Connection connection, long orderId) throws SQLException {
        String sql = "SELECT product_id, product_name, quantity, unit_price_cents "
                + "FROM v_order_item_details WHERE order_id = ? ORDER BY order_item_id";
        List<OrderLine> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new OrderLine(rs.getLong("product_id"), rs.getString("product_name"),
                            rs.getInt("quantity"), rs.getLong("unit_price_cents")));
                }
            }
        }
        return result;
    }

    /**
     * Saves a new order as ONE transaction: the order, every line, the stock reduction of every
     * product and the stock history lines. If anything fails, nothing at all is saved.
     * The price of each product is read from the database here, so it cannot be faked by the screen.
     * The stock UPDATE only succeeds while enough is left, so two people selling the last item
     * at the same moment can never push the stock below zero.
     *
     * Returns the new order id, or -1 if a product was gone or did not have enough stock any more
     * (nothing was saved in that case).
     */
    public long create(long customerId, long employeeId, long userId, List<OrderLine> lines) throws SQLException {
        String orderSql = "INSERT INTO orders (customer_id, employee_id) VALUES (?, ?)";
        String priceSql = "SELECT price_cents FROM products WHERE product_id = ? AND is_active = 1";
        String stockSql = "UPDATE products SET quantity_in_stock = quantity_in_stock - ? "
                + "WHERE product_id = ? AND is_active = 1 AND quantity_in_stock >= ?";
        String lineSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents, subtotal_cents) "
                + "VALUES (?, ?, ?, ?, ?)";
        String historySql = "INSERT INTO stock_adjustments (product_id, user_id, quantity_change, reason) "
                + "VALUES (?, ?, ?, ?)";
        String totalSql = "UPDATE orders SET total_amount_cents = ? WHERE order_id = ?";

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long orderId;
                try (PreparedStatement statement = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, customerId);
                    statement.setLong(2, employeeId);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        orderId = keys.next() ? keys.getLong(1) : 0;
                    }
                }

                long total = 0;
                for (OrderLine line : lines) {
                    long price;
                    try (PreparedStatement statement = connection.prepareStatement(priceSql)) {
                        statement.setLong(1, line.getProductId());
                        try (ResultSet rs = statement.executeQuery()) {
                            if (!rs.next()) {
                                connection.rollback();
                                return -1;
                            }
                            price = rs.getLong(1);
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(stockSql)) {
                        statement.setInt(1, line.getQuantity());
                        statement.setLong(2, line.getProductId());
                        statement.setInt(3, line.getQuantity());
                        if (statement.executeUpdate() != 1) {
                            connection.rollback();
                            return -1;
                        }
                    }
                    long subtotal = line.getQuantity() * price;
                    try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
                        statement.setLong(1, orderId);
                        statement.setLong(2, line.getProductId());
                        statement.setInt(3, line.getQuantity());
                        statement.setLong(4, price);
                        statement.setLong(5, subtotal);
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(historySql)) {
                        statement.setLong(1, line.getProductId());
                        statement.setLong(2, userId);
                        statement.setInt(3, -line.getQuantity());
                        statement.setString(4, "Sold on order #" + orderId);
                        statement.executeUpdate();
                    }
                    total += subtotal;
                }

                try (PreparedStatement statement = connection.prepareStatement(totalSql)) {
                    statement.setLong(1, total);
                    statement.setLong(2, orderId);
                    statement.executeUpdate();
                }
                connection.commit();
                return orderId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Cancels an order as ONE transaction: marks it CANCELLED (who and when), puts every product's
     * quantity back into stock and writes a history line for each. If anything fails, nothing changes.
     * Returns false if the order does not exist or is not COMPLETED (so nothing was changed).
     */
    public boolean cancel(long orderId, long userId) throws SQLException {
        String orderSql = "UPDATE orders SET status = 'CANCELLED', cancelled_at = datetime('now', 'localtime'), "
                + "cancelled_by = ? WHERE order_id = ? AND status = 'COMPLETED'";
        String stockSql = "UPDATE products SET quantity_in_stock = quantity_in_stock + ? WHERE product_id = ?";
        String historySql = "INSERT INTO stock_adjustments (product_id, user_id, quantity_change, reason) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection connection = DatabaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
                    statement.setLong(1, userId);
                    statement.setLong(2, orderId);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                for (OrderLine line : findLines(connection, orderId)) {
                    try (PreparedStatement statement = connection.prepareStatement(stockSql)) {
                        statement.setInt(1, line.getQuantity());
                        statement.setLong(2, line.getProductId());
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(historySql)) {
                        statement.setLong(1, line.getProductId());
                        statement.setLong(2, userId);
                        statement.setInt(3, line.getQuantity());
                        statement.setString(4, "Order #" + orderId + " cancelled");
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Who cancelled the order and when, as a sentence. Returns null if the order is not cancelled. */
    public String findCancellation(long orderId) throws SQLException {
        String sql = "SELECT o.cancelled_at, IFNULL(e.first_name || ' ' || e.last_name, u.username) AS who "
                + "FROM orders o "
                + "JOIN users u ON u.user_id = o.cancelled_by "
                + "LEFT JOIN employees e ON e.employee_id = u.employee_id "
                + "WHERE o.order_id = ? AND o.status = 'CANCELLED'";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? "Cancelled " + rs.getString("cancelled_at") + " by " + rs.getString("who") : null;
            }
        }
    }

    // ---------- helpers ----------

    private CustomerOrder readOrder(ResultSet rs) throws SQLException {
        CustomerOrder o = new CustomerOrder();
        o.setOrderId(rs.getLong("order_id"));
        o.setOrderDate(rs.getString("order_date"));
        o.setStatus(rs.getString("status"));
        o.setTotalCents(rs.getLong("total_amount_cents"));
        o.setCustomerId(rs.getLong("customer_id"));
        o.setCustomerName(rs.getString("customer_name"));
        o.setEmployeeId(rs.getLong("employee_id"));
        o.setEmployeeName(rs.getString("employee_name"));
        return o;
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setLong(index, value);
        }
    }

    /** Makes % and _ typed by the user match literally instead of acting as wildcards. */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
