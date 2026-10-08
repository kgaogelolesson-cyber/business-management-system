package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.model.DashboardSummary;
import com.bms.service.DashboardService;
import com.bms.util.MoneyFormatter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The dashboard screen: summary cards on top, tables underneath.
 * It only displays what DashboardService returns. The administrator and the
 * employee see different things because the service returns different data.
 */
public class DashboardPanel extends JPanel implements Refreshable {

    private static final Color WARNING_COLOUR = new Color(200, 110, 0);
    private static final Color DANGER_COLOUR = new Color(180, 0, 0);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DashboardService dashboardService = new DashboardService();

    private final JPanel cardsPanel = new JPanel(new GridLayout(0, 3, 12, 12));
    private final JPanel tablesPanel = new JPanel(new GridLayout(1, 2, 12, 12));
    private final JLabel updatedLabel = new JLabel(" ");

    public DashboardPanel() {
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.add(buildTitleBar(), BorderLayout.NORTH);
        top.add(cardsPanel, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(tablesPanel, BorderLayout.CENTER);

        refresh();
    }

    private JPanel buildTitleBar() {
        JLabel title = new JLabel("Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> refresh());

        JPanel right = new JPanel();
        right.add(updatedLabel);
        right.add(refreshButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Reloads everything from the database. Called at start-up and by the Refresh button. */
    @Override
    public void refresh() {
        try {
            DashboardSummary summary = dashboardService.getSummary();
            showSummary(summary);
            updatedLabel.setText("Last updated " + LocalTime.now().format(TIME_FORMAT));
        } catch (BmsException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Dashboard", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showSummary(DashboardSummary s) {
        cardsPanel.removeAll();
        tablesPanel.removeAll();

        Color lowStockColour = s.lowStockCount() > 0 ? DANGER_COLOUR : Color.DARK_GRAY;

        cardsPanel.add(createCard("Total Customers", String.valueOf(s.totalCustomers()), Color.DARK_GRAY));
        cardsPanel.add(createCard("Total Products", String.valueOf(s.totalProducts()), Color.DARK_GRAY));
        if (s.fullView()) {
            cardsPanel.add(createCard("Total Employees", String.valueOf(s.totalEmployees()), Color.DARK_GRAY));
            cardsPanel.add(createCard("Total Orders", String.valueOf(s.totalOrders()), Color.DARK_GRAY));
            cardsPanel.add(createCard("Total Revenue",
                    MoneyFormatter.format(s.totalRevenueCents(), s.currencySymbol()), Color.DARK_GRAY));
        }
        cardsPanel.add(createCard("Low-Stock Products", String.valueOf(s.lowStockCount()), lowStockColour));

        // Left: low stock. Right: recent orders (and, for the administrator, recent customers).
        tablesPanel.add(createLowStockTable(s));

        JPanel right = new JPanel(new GridLayout(s.fullView() ? 2 : 1, 1, 0, 12));
        right.add(createRecentOrdersTable(s));
        if (s.fullView()) {
            right.add(createRecentCustomersTable(s));
        }
        tablesPanel.add(right);

        cardsPanel.revalidate();
        cardsPanel.repaint();
        tablesPanel.revalidate();
        tablesPanel.repaint();
    }

    private JPanel createCard(String title, String value, Color valueColour) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.GRAY);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 26f));
        valueLabel.setForeground(valueColour);

        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane createLowStockTable(DashboardSummary s) {
        List<Object[]> rows = new ArrayList<>();
        for (DashboardSummary.LowStockItem item : s.lowStockItems()) {
            rows.add(new Object[]{item.productName(), item.quantityInStock(), item.reorderLevel(), item.stockStatus()});
        }
        JTable table = createTable(new String[]{"Product", "In stock", "Reorder at", "Status"}, rows, 1, 2);
        setColumnWidths(table, 200, 70, 80, 110);

        // Colour the status column
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                if (!selected) {
                    c.setForeground("OUT OF STOCK".equals(value) ? DANGER_COLOUR : WARNING_COLOUR);
                }
                return c;
            }
        });
        return wrap("Low-stock products", table);
    }

    private JScrollPane createRecentOrdersTable(DashboardSummary s) {
        List<Object[]> rows = new ArrayList<>();
        for (DashboardSummary.RecentOrder order : s.recentOrders()) {
            rows.add(new Object[]{order.orderId(), shortDate(order.orderDate()), order.customerName(),
                    order.employeeName(), MoneyFormatter.format(order.totalCents(), s.currencySymbol()), order.status()});
        }
        JTable table = createTable(new String[]{"Order", "Date", "Customer", "Employee", "Total", "Status"}, rows, 0, 4);
        setColumnWidths(table, 45, 125, 120, 130, 85, 100);
        return wrap(s.fullView() ? "Recent orders" : "My recent orders", table);
    }

    private JScrollPane createRecentCustomersTable(DashboardSummary s) {
        List<Object[]> rows = new ArrayList<>();
        for (DashboardSummary.RecentCustomer customer : s.recentCustomers()) {
            rows.add(new Object[]{customer.customerName(), customer.email(), shortDate(customer.createdAt())});
        }
        JTable table = createTable(new String[]{"Customer", "Email", "Added"}, rows);
        setColumnWidths(table, 150, 220, 130);
        return wrap("Recent customers", table);
    }

    /** Builds a read-only table. The extra numbers are the columns to right-align. */
    private JTable createTable(String[] columns, List<Object[]> rows, int... rightAlignedColumns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;   // the dashboard is for reading only
            }
        };
        for (Object[] row : rows) {
            model.addRow(row);
        }
        JTable table = new StripedTable(model);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int column : rightAlignedColumns) {
            table.getColumnModel().getColumn(column).setCellRenderer(rightAlign);
        }
        return table;
    }

    /** Sets how wide each column prefers to be (the numbers only matter relative to each other). */
    private void setColumnWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private JScrollPane wrap(String title, JTable table) {
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder(title));
        return scrollPane;
    }

    /** "2026-10-03 19:50:12" becomes "2026-10-03 19:50". */
    private static String shortDate(String dateTime) {
        return (dateTime != null && dateTime.length() >= 16) ? dateTime.substring(0, 16) : dateTime;
    }
}
