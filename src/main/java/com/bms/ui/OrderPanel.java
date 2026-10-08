package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import com.bms.model.CustomerOrder;
import com.bms.model.OrderLine;
import com.bms.model.Product;
import com.bms.service.CustomerService;
import com.bms.service.OrderService;
import com.bms.service.OrderService.StatusFilter;
import com.bms.service.ProductService;
import com.bms.util.MoneyFormatter;
import com.bms.util.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The Orders tab. Top table = orders (an administrator sees all, an employee only their own);
 * bottom table = the products on the selected order. "New order" opens the order form.
 */
public class OrderPanel extends JPanel implements Refreshable {

    private static final String[] ORDER_COLUMNS = {"Order #", "Date", "Customer", "Employee", "Total", "Status"};
    private static final String[] LINE_COLUMNS = {"Product", "Qty", "Unit price", "Subtotal"};
    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);

    private final OrderService orderService = new OrderService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private final JTextField searchField = new JTextField(20);
    private final JTextField fromField = new JTextField(8);
    private final JTextField toField = new JTextField(8);
    private final JComboBox<StatusFilter> filterBox = new JComboBox<>(StatusFilter.values());
    private final JLabel countLabel = new JLabel(" ");
    private final JLabel totalLabel = new JLabel(" ");
    private final DefaultTableModel orderModel = new DefaultTableModel(ORDER_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }
    };
    private final DefaultTableModel lineModel = new DefaultTableModel(LINE_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable orderTable = new StripedTable(orderModel);
    private final StripedTable lineTable = new StripedTable(lineModel);
    private final JPanel detailsPanel = new JPanel(new BorderLayout());
    private final JButton cancelButton = new JButton("Cancel order");

    /** The orders currently shown, in the same order as the table rows. */
    private List<CustomerOrder> shown = List.of();
    private String currency = "R";

    public OrderPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        try {
            currency = orderService.getCurrencySymbol();
        } catch (BmsException e) {
            showError(e.getMessage());
        }

        detailsPanel.setBorder(BorderFactory.createTitledBorder("Order details"));
        detailsPanel.add(new JScrollPane(lineTable), BorderLayout.CENTER);
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 14f));
        totalLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        totalLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 12));
        detailsPanel.add(totalLabel, BorderLayout.SOUTH);

        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.add(buildTopBar(), BorderLayout.NORTH);
        listPanel.add(new JScrollPane(orderTable), BorderLayout.CENTER);
        listPanel.add(buildBottomBar(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, detailsPanel);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        orderTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        orderTable.setRowHeight(24);
        orderTable.setAutoCreateRowSorter(true);
        orderTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadDetails();
                updateButtons();
            }
        });
        lineTable.setRowHeight(22);
        lineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setUpColumns();

        loadOrders();
    }

    /** Called by the main window when this tab is opened, so new and cancelled orders show up. */
    @Override
    public void refresh() {
        loadOrders();
    }

    private JPanel buildTopBar() {
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        searchButton.addActionListener(event -> loadOrders());
        searchField.addActionListener(event -> loadOrders());   // Enter key
        fromField.addActionListener(event -> loadOrders());
        toField.addActionListener(event -> loadOrders());
        searchField.setToolTipText("Order number, date, customer name or employee name");
        fromField.setToolTipText("Earliest order date, written yyyy-MM-dd (leave empty for no limit)");
        toField.setToolTipText("Latest order date, written yyyy-MM-dd (leave empty for no limit)");
        filterBox.addActionListener(event -> loadOrders());
        clearButton.addActionListener(event -> {
            searchField.setText("");
            fromField.setText("");
            toField.setText("");
            filterBox.setSelectedItem(StatusFilter.ALL);
            loadOrders();
        });
        filterBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? "" : ((StatusFilter) value).getLabel());
                return this;
            }
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search:"));
        bar.add(searchField);
        bar.add(new JLabel("From:"));
        bar.add(fromField);
        bar.add(new JLabel("To:"));
        bar.add(toField);
        bar.add(new JLabel("Show:"));
        bar.add(filterBox);
        bar.add(searchButton);
        bar.add(clearButton);
        return bar;
    }

    private JPanel buildBottomBar() {
        JButton newButton = new JButton("New order");
        newButton.addActionListener(event -> createOrder());
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> loadOrders());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cancelButton.addActionListener(event -> cancelSelected());
        cancelButton.setEnabled(false);

        buttons.add(refreshButton);
        buttons.add(newButton);
        if (Session.isAdmin()) {          // only an administrator may cancel an order
            buttons.add(cancelButton);
        }

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(countLabel, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private void setUpColumns() {
        int[] widths = {70, 140, 200, 170, 110, 100};
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                boolean right = (column == 0 || column == 4);
                setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setBorder(right ? NUMBER_PADDING : BorderFactory.createEmptyBorder(0, 4, 0, 4));
                if (!selected) {
                    Object status = t.getModel().getValueAt(t.convertRowIndexToModel(row), 5);
                    c.setForeground(CustomerOrder.CANCELLED.equals(status) ? Color.GRAY : t.getForeground());
                }
                return c;
            }
        };
        for (int i = 0; i < widths.length; i++) {
            orderTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            orderTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        int[] lineWidths = {320, 60, 120, 120};
        DefaultTableCellRenderer lineRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                setBorder(column == 0 ? BorderFactory.createEmptyBorder(0, 4, 0, 4) : NUMBER_PADDING);
                return c;
            }
        };
        for (int i = 0; i < lineWidths.length; i++) {
            lineTable.getColumnModel().getColumn(i).setPreferredWidth(lineWidths[i]);
            lineTable.getColumnModel().getColumn(i).setCellRenderer(lineRenderer);
        }
    }

    // ---------- loading ----------

    private void loadOrders() {
        long selectedId = selectedOrderId();
        try {
            shown = orderService.search(searchField.getText(), (StatusFilter) filterBox.getSelectedItem(),
                    fromField.getText(), toField.getText(), null);
        } catch (BmsException e) {
            showError(e.getMessage());
            return;
        }
        orderModel.setRowCount(0);
        for (CustomerOrder o : shown) {
            orderModel.addRow(new Object[]{
                (int) o.getOrderId(), o.getOrderDate(), o.getCustomerName(), o.getEmployeeName(),
                MoneyFormatter.format(o.getTotalCents(), currency), o.getStatus()});
        }
        boolean filtering = !searchField.getText().trim().isEmpty() || filterBox.getSelectedItem() != StatusFilter.ALL
                || !fromField.getText().trim().isEmpty() || !toField.getText().trim().isEmpty();
        if (shown.size() >= OrderService.MAX_LIST_ROWS) {
            // Never pretend the list is complete: older orders exist but are not shown.
            countLabel.setText("Showing the newest " + OrderService.MAX_LIST_ROWS
                    + " orders - use the search or the dates to narrow the list");
        } else {
            countLabel.setText(shown.size() + (filtering ? " order(s) found" : " order(s)")
                    + (Session.isAdmin() ? "" : " (your own orders)"));
        }

        reselect(selectedId);
        loadDetails();
        updateButtons();
    }

    /** Fills the bottom table with the products on the selected order (or clears it). */
    private void loadDetails() {
        CustomerOrder selected = getSelected();
        lineModel.setRowCount(0);
        if (selected == null) {
            detailsPanel.setBorder(BorderFactory.createTitledBorder("Order details - select an order above"));
            totalLabel.setText(" ");
            return;
        }
        detailsPanel.setBorder(BorderFactory.createTitledBorder(
                "Order #" + selected.getOrderId() + " - " + selected.getCustomerName()));
        try {
            for (OrderLine line : orderService.getLines(selected.getOrderId())) {
                lineModel.addRow(new Object[]{line.getProductName(), line.getQuantity(),
                    MoneyFormatter.format(line.getUnitPriceCents(), currency),
                    MoneyFormatter.format(line.getSubtotalCents(), currency)});
            }
        } catch (BmsException e) {
            showError(e.getMessage());
        }
        String total = "Total: " + MoneyFormatter.format(selected.getTotalCents(), currency);
        try {
            String cancelled = orderService.getCancellationInfo(selected.getOrderId());
            totalLabel.setText(cancelled == null ? total : cancelled + "      " + total);
        } catch (BmsException e) {
            totalLabel.setText(total);
        }
    }

    // ---------- actions ----------

    private void createOrder() {
        List<Customer> customers;
        List<Product> products;
        try {
            customers = customerService.search("");
            products = productService.search("", null);
        } catch (BmsException e) {
            showError(e.getMessage());
            return;
        }
        if (customers.isEmpty()) {
            showError("There are no customers yet. Add a customer on the Customers tab first.");
            return;
        }
        if (products.isEmpty()) {
            showError("There are no products yet.");
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        OrderDialog dialog = new OrderDialog(owner, customers, products, currency);
        OrderDialog.Result entered = dialog.showDialog();

        // Show the form again, with the reason, until the order is saved or the user cancels.
        while (entered != null) {
            try {
                long orderId = orderService.create(entered.customerId(), entered.lines());
                loadOrders();
                selectOrder(orderId);
                return;
            } catch (ValidationException e) {
                entered = dialog.showAgainWithMessage(e.getMessage());
            } catch (BmsException e) {
                showError(e.getMessage());
                return;
            }
        }
    }

    private void cancelSelected() {
        CustomerOrder selected = getSelected();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Cancel order #" + selected.getOrderId() + " for " + selected.getCustomerName() + "?\n"
                        + "The products on it will be put back into stock. This cannot be undone.",
                "Cancel order", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            orderService.cancel(selected.getOrderId());
            loadOrders();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    // ---------- small helpers ----------

    private void updateButtons() {
        CustomerOrder selected = getSelected();
        cancelButton.setEnabled(selected != null && CustomerOrder.COMPLETED.equals(selected.getStatus()));
    }

    private CustomerOrder getSelected() {
        long id = selectedOrderId();
        for (CustomerOrder o : shown) {
            if (o.getOrderId() == id) {
                return o;
            }
        }
        return null;
    }

    private long selectedOrderId() {
        int viewRow = orderTable.getSelectedRow();
        if (viewRow < 0) {
            return -1;
        }
        return (Integer) orderModel.getValueAt(orderTable.convertRowIndexToModel(viewRow), 0);
    }

    private void reselect(long orderId) {
        if (orderId < 0) {
            return;
        }
        selectOrder(orderId);
    }

    private void selectOrder(long orderId) {
        for (int modelRow = 0; modelRow < orderModel.getRowCount(); modelRow++) {
            if ((Integer) orderModel.getValueAt(modelRow, 0) == orderId) {
                int viewRow = orderTable.convertRowIndexToView(modelRow);
                orderTable.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Orders", JOptionPane.ERROR_MESSAGE);
    }
}
