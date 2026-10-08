package com.bms.ui;

import com.bms.exception.ValidationException;
import com.bms.model.Customer;
import com.bms.model.OrderLine;
import com.bms.model.Product;
import com.bms.util.InputParser;
import com.bms.util.MoneyFormatter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The "New order" form: choose a customer, add products with quantities, see the total.
 * It only collects the order. The service does the real checking and saving.
 * (The checks on the "Add to order" button are only there to give quick feedback.)
 */
public class OrderDialog extends JDialog {

    /** What the user built: the customer and the lines (productId + quantity are what matter). */
    public record Result(long customerId, List<OrderLine> lines) {
    }

    private static final String[] COLUMNS = {"Product", "Qty", "Unit price", "Subtotal"};
    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);

    private final List<Product> products;
    private final String currency;
    private final List<OrderLine> cart = new ArrayList<>();

    private final JComboBox<Customer> customerBox;
    private final JComboBox<Product> productBox;
    private final JTextField quantityField = new JTextField("1", 6);
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(tableModel);
    private final JLabel totalLabel = new JLabel();
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton removeButton = new JButton("Remove selected");
    private boolean saveClicked = false;

    public OrderDialog(Window owner, List<Customer> customers, List<Product> products, String currency) {
        super(owner, "New order", ModalityType.APPLICATION_MODAL);
        this.products = products;
        this.currency = currency;

        customerBox = new JComboBox<>(customers.toArray(new Customer[0]));
        customerBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Customer c) {
                    setText(c.getFirstName() + " " + c.getLastName());
                }
                return this;
            }
        });
        customerBox.setSelectedIndex(-1);   // the user must choose on purpose

        productBox = new JComboBox<>(products.toArray(new Product[0]));
        productBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Product p) {
                    setText(p.getProductName() + "  -  " + MoneyFormatter.format(p.getPriceCents(), currency)
                            + "  (" + p.getQuantityInStock() + " in stock)");
                }
                return this;
            }
        });

        setLayout(new BorderLayout(0, 8));
        add(buildTop(), BorderLayout.NORTH);
        add(buildMiddle(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);
        setPreferredSize(new Dimension(680, 520));
        pack();
        setLocationRelativeTo(owner);
        refreshTable();
    }

    private JPanel buildTop() {
        JButton addButton = new JButton("Add to order");
        addButton.addActionListener(event -> addToCart());
        quantityField.addActionListener(event -> addToCart());

        JPanel customerRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        customerRow.add(new JLabel("Customer *"));
        customerBox.setPreferredSize(new Dimension(320, customerBox.getPreferredSize().height));
        customerRow.add(customerBox);

        JPanel productRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        productRow.add(new JLabel("Product"));
        productBox.setPreferredSize(new Dimension(360, productBox.getPreferredSize().height));
        productRow.add(productBox);
        productRow.add(new JLabel("Qty"));
        productRow.add(quantityField);
        productRow.add(addButton);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        top.add(customerRow, BorderLayout.NORTH);
        top.add(productRow, BorderLayout.SOUTH);
        return top;
    }

    private JPanel buildMiddle() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getSelectionModel().addListSelectionListener(event -> removeButton.setEnabled(table.getSelectedRow() >= 0));
        removeButton.setEnabled(false);
        removeButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                cart.remove(row);
                messageLabel.setText(" ");
                refreshTable();
            }
        });

        int[] widths = {300, 50, 110, 110};
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                setBorder(column == 0 ? BorderFactory.createEmptyBorder(0, 4, 0, 4) : NUMBER_PADDING);
                return c;
            }
        };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JPanel middle = new JPanel(new BorderLayout(0, 4));
        middle.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        middle.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel under = new JPanel(new BorderLayout());
        under.add(removeButton, BorderLayout.WEST);
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 15f));
        under.add(totalLabel, BorderLayout.EAST);
        middle.add(under, BorderLayout.SOUTH);
        return middle;
    }

    private JPanel buildBottom() {
        JButton save = new JButton("Save order");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(event -> {
            saveClicked = true;
            setVisible(false);
        });
        cancel.addActionListener(event -> setVisible(false));
        DialogKeys.escapeCancels(this, cancel);

        messageLabel.setForeground(new Color(180, 0, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));
        bottom.add(messageLabel, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        return bottom;
    }

    // ---------- the shopping list ----------

    /** Adds the chosen product to the list (or adds to its quantity if it is already there). */
    private void addToCart() {
        Product product = (Product) productBox.getSelectedItem();
        if (product == null) {
            say("Choose a product first.");
            return;
        }
        int quantity;
        try {
            quantity = InputParser.parseWholeNumber(quantityField.getText(), "The quantity");
        } catch (ValidationException e) {
            say(e.getMessage());
            return;
        }
        if (quantity < 1) {
            say("The quantity must be at least 1.");
            return;
        }

        int existingIndex = -1;
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).getProductId() == product.getProductId()) {
                existingIndex = i;
            }
        }
        int alreadyOnOrder = (existingIndex >= 0) ? cart.get(existingIndex).getQuantity() : 0;
        if (alreadyOnOrder + quantity > product.getQuantityInStock()) {
            say("Only " + product.getQuantityInStock() + " in stock for \"" + product.getProductName() + "\""
                    + (alreadyOnOrder > 0 ? " (" + alreadyOnOrder + " already on this order)." : "."));
            return;
        }

        OrderLine line = new OrderLine(product.getProductId(), product.getProductName(),
                alreadyOnOrder + quantity, product.getPriceCents());
        if (existingIndex >= 0) {
            cart.set(existingIndex, line);
        } else {
            cart.add(line);
        }
        quantityField.setText("1");
        messageLabel.setText(" ");
        refreshTable();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        long total = 0;
        for (OrderLine line : cart) {
            tableModel.addRow(new Object[]{line.getProductName(), line.getQuantity(),
                MoneyFormatter.format(line.getUnitPriceCents(), currency),
                MoneyFormatter.format(line.getSubtotalCents(), currency)});
            total += line.getSubtotalCents();
        }
        totalLabel.setText("Total: " + MoneyFormatter.format(total, currency));
    }

    private void say(String message) {
        messageLabel.setText("<html><div style='width:600px'>" + escape(message) + "</div></html>");
    }

    // ---------- showing the form ----------

    /** Shows the form and waits. Returns the order the user built, or null if they cancelled. */
    public Result showDialog() {
        saveClicked = false;
        setVisible(true);        // blocks until the dialog is hidden
        if (!saveClicked) {
            return null;
        }
        Customer customer = (Customer) customerBox.getSelectedItem();
        return new Result(customer == null ? 0 : customer.getCustomerId(), new ArrayList<>(cart));
    }

    /** Shows the form again after the service refused the order, with the reason on screen. */
    public Result showAgainWithMessage(String message) {
        say(message);
        return showDialog();
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
