package com.bms.service;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DatabaseManager;
import com.bms.exception.AccessDeniedException;
import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import com.bms.model.OrderLine;
import com.bms.model.Product;
import com.bms.model.ReportResult;
import com.bms.model.ReportResult.ColumnType;
import com.bms.service.ReportService.ReportType;
import com.bms.util.ReportExporter;
import com.bms.util.Session;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A console self-test for reports. It creates TEST orders (customers, products and orders whose names
 * start "Check"), moves their dates back to March 2020 so they are the only orders in that date range,
 * and then checks every report against numbers worked out by hand. It removes all its own test rows again.
 * It needs the demo data (run DemoDataLoader once first). Run it on its own: right-click -> Run File.
 */
public final class ReportServiceCheck {

    private static int passed = 0;
    private static int total = 0;
    private static final List<Long> createdOrderIds = new ArrayList<>();
    private static final List<Long> createdProductIds = new ArrayList<>();
    private static final List<Long> createdCustomerIds = new ArrayList<>();

    private ReportServiceCheck() {
    }

    public static void main(String[] args) {
        Path csvFile = null;
        try {
            DatabaseInitializer.initialise();
            AuthService auth = new AuthService();
            ReportService reports = new ReportService();
            OrderService orders = new OrderService();
            ProductService products = new ProductService();
            CustomerService customers = new CustomerService();

            LocalDate from = LocalDate.of(2020, 3, 1);
            LocalDate to = LocalDate.of(2020, 3, 31);

            // --- Who may run reports ---
            Session.end();
            check("Reports are refused when nobody is logged in", fails(() -> reports.run(ReportType.SALES_BY_DAY, from, to), AccessDeniedException.class));
            auth.login("thandi", "Employee@123");
            for (ReportType type : ReportType.values()) {
                check("Employee cannot run the report '" + type.getLabel() + "'", fails(() -> reports.run(type, from, to), AccessDeniedException.class));
            }
            check("Employee cannot read the currency symbol for reports", fails(() -> reports.getCurrencySymbol(), AccessDeniedException.class));

            // --- Test data ---
            auth.login("admin", "Admin@123");
            long widget = addProduct(products, "CheckReport Widget", 10000, 100, 5);   // R100.00
            long gadget = addProduct(products, "CheckReport Gadget", 2550, 100, 5);    // R25.50
            long customer1 = addCustomer(customers, "Checkreport", "Alpha");
            long customer2 = addCustomer(customers, "Checkreport", "Beta");

            auth.login("thandi", "Employee@123");
            long order1 = order(orders, customer1, line(widget, 2), line(gadget, 1));   // 22550, thandi
            long order3 = order(orders, customer1, line(widget, 1));                    // 10000, thandi
            long order4 = order(orders, customer1, line(widget, 5));                    // will be cancelled
            long order5 = order(orders, customer1, line(widget, 1));                    // outside the date range
            auth.login("admin", "Admin@123");
            long order2 = order(orders, customer2, line(gadget, 3));                    // 7650, admin
            orders.cancel(order4);

            setDate(order1, "2020-03-10 09:00:00");
            setDate(order2, "2020-03-10 15:30:00");
            setDate(order3, "2020-03-20 11:00:00");
            setDate(order4, "2020-03-12 10:00:00");
            setDate(order5, "2020-04-05 10:00:00");

            // --- Date handling ---
            check("A valid date is accepted", ok(() -> ReportService.parseDate("2020-03-01", "from")));
            check("An empty date is refused", fails(() -> ReportService.parseDate("", "from"), ValidationException.class));
            check("A date in the wrong format is refused", fails(() -> ReportService.parseDate("01/03/2020", "from"), ValidationException.class));
            check("A date that does not exist (2020-02-30) is refused", fails(() -> ReportService.parseDate("2020-02-30", "from"), ValidationException.class));
            check("A 'from' date after the 'to' date is refused", fails(() -> reports.run(ReportType.SALES_BY_DAY, to, from), ValidationException.class));
            check("A sales report without dates is refused", fails(() -> reports.run(ReportType.SALES_BY_DAY, null, null), ValidationException.class));

            // --- Sales by product (March 2020) ---
            ReportResult byProduct = reports.run(ReportType.SALES_BY_PRODUCT, from, to);
            check("Sales by product has 2 rows", byProduct.getRows().size() == 2);
            check("The widget is first (higher revenue)", "CheckReport Widget".equals(byProduct.getRows().get(0)[0]));
            check("Widget: 3 units, R300.00 (the cancelled order is NOT counted)",
                    (Long) byProduct.getRows().get(0)[1] == 3 && (Long) byProduct.getRows().get(0)[2] == 30000);
            check("Gadget: 4 units, R102.00",
                    (Long) byProduct.getRows().get(1)[1] == 4 && (Long) byProduct.getRows().get(1)[2] == 10200);
            check("The summary shows 3 orders, R402.00 revenue, R134.00 average, 1 cancelled",
                    byProduct.getSummary().contains("3 completed order(s)") && byProduct.getSummary().contains("R402.00")
                    && byProduct.getSummary().contains("R134.00") && byProduct.getSummary().contains("1 cancelled"));
            check("The title names the report and the dates", byProduct.getTitle().equals("Sales by product, 2020-03-01 to 2020-03-31"));

            // --- Sales by employee ---
            ReportResult byEmployee = reports.run(ReportType.SALES_BY_EMPLOYEE, from, to);
            check("Sales by employee has 2 rows", byEmployee.getRows().size() == 2);
            check("Thandi: 2 orders, R325.50", "Thandi Mokoena".equals(byEmployee.getRows().get(0)[0])
                    && (Long) byEmployee.getRows().get(0)[1] == 2 && (Long) byEmployee.getRows().get(0)[2] == 32550);
            check("The administrator: 1 order, R76.50", "System Administrator".equals(byEmployee.getRows().get(1)[0])
                    && (Long) byEmployee.getRows().get(1)[1] == 1 && (Long) byEmployee.getRows().get(1)[2] == 7650);

            // --- Sales by customer ---
            ReportResult byCustomer = reports.run(ReportType.SALES_BY_CUSTOMER, from, to);
            check("Sales by customer: Alpha 2 orders R325.50, Beta 1 order R76.50",
                    byCustomer.getRows().size() == 2
                    && "Checkreport Alpha".equals(byCustomer.getRows().get(0)[0]) && (Long) byCustomer.getRows().get(0)[1] == 2 && (Long) byCustomer.getRows().get(0)[2] == 32550
                    && "Checkreport Beta".equals(byCustomer.getRows().get(1)[0]) && (Long) byCustomer.getRows().get(1)[1] == 1 && (Long) byCustomer.getRows().get(1)[2] == 7650);

            // --- Sales by day ---
            ReportResult byDay = reports.run(ReportType.SALES_BY_DAY, from, to);
            check("Sales by day has 2 days (the cancelled day 2020-03-12 is not listed)", byDay.getRows().size() == 2);
            check("2020-03-10: 2 orders, R302.00", "2020-03-10".equals(byDay.getRows().get(0)[0])
                    && (Long) byDay.getRows().get(0)[1] == 2 && (Long) byDay.getRows().get(0)[2] == 30200);
            check("2020-03-20: 1 order, R100.00", "2020-03-20".equals(byDay.getRows().get(1)[0])
                    && (Long) byDay.getRows().get(1)[1] == 1 && (Long) byDay.getRows().get(1)[2] == 10000);

            // --- Date range edges ---
            ReportResult oneDay = reports.run(ReportType.SALES_BY_DAY, LocalDate.of(2020, 3, 10), LocalDate.of(2020, 3, 10));
            check("A one-day range includes both orders of that day (morning and afternoon)",
                    oneDay.getRows().size() == 1 && (Long) oneDay.getRows().get(0)[1] == 2);
            ReportResult wide = reports.run(ReportType.SALES_BY_DAY, from, LocalDate.of(2020, 4, 30));
            check("A wider range includes the April order too", wide.getRows().size() == 3 && wide.getSummary().contains("4 completed order(s)"));
            ReportResult empty = reports.run(ReportType.SALES_BY_DAY, LocalDate.of(2019, 1, 1), LocalDate.of(2019, 1, 31));
            check("A range with no orders gives an empty report", empty.getRows().isEmpty());
            check("...with a summary of 0 orders and R0.00", empty.getSummary().contains("0 completed order(s)") && empty.getSummary().contains("R0.00"));

            // --- Stock reports ---
            long lowId = addProduct(products, "CheckReport Low", 1000, 2, 5);
            long outId = addProduct(products, "CheckReport Out", 1000, 0, 5);
            ReportResult stock = reports.run(ReportType.STOCK_LEVELS, null, null);
            Object[] widgetRow = findRow(stock, "CheckReport Widget");
            check("Stock levels shows the widget: 96 in stock (100 - 2 - 1 - 1 sold; the cancelled 5 went back), R100.00, value R9,600.00",
                    widgetRow != null && (Long) widgetRow[2] == 96 && (Long) widgetRow[5] == 10000 && (Long) widgetRow[6] == 960000);
            check("Stock value = quantity x price for every row",
                    stock.getRows().stream().allMatch(r -> (Long) r[6] == (Long) r[2] * (Long) r[5]));
            check("The summary stock value equals the sum of the rows",
                    stock.getSummary().contains(com.bms.util.MoneyFormatter.format(
                            stock.getRows().stream().mapToLong(r -> (Long) r[6]).sum(), "R")));
            check("Stock levels lists the out-of-stock test product with value R0.00",
                    findRow(stock, "CheckReport Out") != null && (Long) findRow(stock, "CheckReport Out")[6] == 0);

            ReportResult low = reports.run(ReportType.LOW_STOCK, null, null);
            check("Low stock lists the product with 2 in stock (reorder at 5)", findRow(low, "CheckReport Low") != null);
            check("Low stock lists the product with 0 in stock", findRow(low, "CheckReport Out") != null);
            check("Low stock does not list the well-stocked widget", findRow(low, "CheckReport Widget") == null);
            check("Lowest stock comes first", (Long) low.getRows().get(0)[2] <= (Long) low.getRows().get(low.getRows().size() - 1)[2]);
            check("Every row in the low-stock report is at or below its reorder level",
                    low.getRows().stream().allMatch(r -> (Long) r[2] <= (Long) r[3]));
            check("The low-stock summary counts the out-of-stock products",
                    low.getSummary().contains(low.getRows().size() + " product(s) need reordering"));

            // --- CSV export ---
            String csv = ReportExporter.toCsv(byProduct);
            String[] lines = csv.split("\r\n");
            check("CSV: first line is the title", lines[0].equals("Sales by product, 2020-03-01 to 2020-03-31") || lines[0].equals("\"Sales by product, 2020-03-01 to 2020-03-31\""));
            check("CSV: second line is the column headings", lines[1].equals("Product,Units sold,Revenue"));
            check("CSV: money is a plain number with two decimals", lines[2].equals("CheckReport Widget,3,300.00"));
            check("CSV: one line per row plus title and headings", lines.length == 2 + byProduct.getRows().size());

            ReportResult tricky = new ReportResult("Tricky", new String[]{"Name", "Count", "Amount"},
                    new ColumnType[]{ColumnType.TEXT, ColumnType.NUMBER, ColumnType.MONEY});
            tricky.getRows().add(new Object[]{"Smith, John \"JJ\"", 5L, 68498L});
            tricky.getRows().add(new Object[]{"=1+1", 1L, 5L});
            tricky.getRows().add(new Object[]{"Line1\nLine2", 2L, 100L});
            tricky.getRows().add(new Object[]{"Negative money", -3L, -150L});
            String trickyCsv = ReportExporter.toCsv(tricky);
            check("CSV: commas and quotes are escaped", trickyCsv.contains("\"Smith, John \"\"JJ\"\"\",5,684.98"));
            check("CSV: text starting with = is neutralised", trickyCsv.contains("'=1+1,1,0.05"));
            check("CSV: a text cell with a line break is quoted", trickyCsv.contains("\"Line1\nLine2\",2,1.00"));
            check("CSV: negative numbers stay numbers", trickyCsv.contains("Negative money,-3,-1.50"));

            csvFile = Files.createTempFile("bms-report-check", ".csv");
            ReportExporter.write(byProduct, csvFile);
            byte[] bytes = Files.readAllBytes(csvFile);
            check("The file starts with the UTF-8 marker for Excel", bytes.length > 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF);
            check("The file contains the same text as toCsv", new String(bytes, StandardCharsets.UTF_8).equals("﻿" + csv));

        } catch (Exception e) {
            total++;
            System.out.println("FAIL: unexpected error: " + e);
            e.printStackTrace(System.out);
        } finally {
            cleanUp();
            try {
                if (csvFile != null) {
                    Files.deleteIfExists(csvFile);
                }
            } catch (Exception ignored) {
                // a leftover temp file is harmless
            }
            Session.end();
        }
        System.out.println();
        System.out.println(passed + " of " + total + " checks passed.");
        System.out.println(passed == total ? "ALL CHECKS PASSED." : "SOME CHECKS FAILED - read the FAIL lines above.");
    }

    // ---------- small helpers ----------

    private interface Action {
        void run() throws Exception;
    }

    private static boolean fails(Action action, Class<? extends BmsException> expected) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return expected.isInstance(e);
        }
    }

    private static boolean ok(Action action) {
        try {
            action.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static OrderLine line(long productId, int quantity) {
        return new OrderLine(productId, "x", quantity, 0);
    }

    private static long order(OrderService orders, long customerId, OrderLine... lines) throws BmsException {
        long id = orders.create(customerId, List.of(lines));
        createdOrderIds.add(id);
        return id;
    }

    private static long addProduct(ProductService products, String name, long priceCents, int stock, int reorder) throws BmsException {
        Product p = new Product();
        p.setProductName(name);
        p.setPriceCents(priceCents);
        p.setQuantityInStock(stock);
        p.setReorderLevel(reorder);
        long id = products.add(p);
        createdProductIds.add(id);
        return id;
    }

    private static long addCustomer(CustomerService customers, String first, String last) throws BmsException {
        Customer c = new Customer();
        c.setFirstName(first);
        c.setLastName(last);
        long id = customers.add(c);
        createdCustomerIds.add(id);
        return id;
    }

    private static Object[] findRow(ReportResult report, String name) {
        return report.getRows().stream().filter(r -> name.equals(r[0])).findFirst().orElse(null);
    }

    /** Moves a test order to a past date, so the report tests do not depend on today's date. */
    private static void setDate(long orderId, String dateTime) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement st = c.prepareStatement("UPDATE orders SET order_date = ? WHERE order_id = ?")) {
            st.setString(1, dateTime);
            st.setLong(2, orderId);
            st.executeUpdate();
        }
    }

    /** Removes ONLY the test rows this program created (orders first: they point at customers and products). */
    private static void cleanUp() {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement orders = c.prepareStatement("DELETE FROM orders WHERE order_id = ?");
             PreparedStatement adjustments = c.prepareStatement("DELETE FROM stock_adjustments WHERE product_id = ?");
             PreparedStatement products = c.prepareStatement("DELETE FROM products WHERE product_id = ?");
             PreparedStatement customers = c.prepareStatement("DELETE FROM customers WHERE customer_id = ?")) {
            for (long id : createdOrderIds) {
                orders.setLong(1, id);
                orders.executeUpdate();     // its order_items are removed automatically (ON DELETE CASCADE)
            }
            for (long id : createdProductIds) {
                adjustments.setLong(1, id);
                adjustments.executeUpdate();
                products.setLong(1, id);
                products.executeUpdate();
            }
            for (long id : createdCustomerIds) {
                customers.setLong(1, id);
                customers.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Could not remove the test rows: " + e.getMessage());
        }
    }

    private static void check(String description, boolean ok) {
        total++;
        if (ok) {
            passed++;
            System.out.println("PASS: " + description);
        } else {
            System.out.println("FAIL: " + description);
        }
    }
}
