package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.model.Product;
import com.bms.model.SupplierOption;
import com.bms.service.InventoryService;
import com.bms.service.ProductService;
import com.bms.util.MoneyFormatter;
import com.bms.util.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The Products tab: search, category filter, table, and (for the administrator) Add / Edit / Delete.
 * An employee can look at products but not change them.
 */
public class ProductPanel extends JPanel implements Refreshable {

    private static final String ALL_CATEGORIES = "All categories";
    private static final String[] COLUMNS =
            {"ID", "Product", "Category", "Price", "In stock", "Reorder at", "Status", "Supplier"};
    private static final int PRICE_COLUMN = 3;
    private static final int STATUS_COLUMN = 6;
    private static final Color WARNING_COLOUR = new Color(200, 110, 0);
    private static final Color DANGER_COLOUR = new Color(180, 0, 0);
    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);

    private final ProductService productService = new ProductService();

    private final JTextField searchField = new JTextField(20);
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JComboBox<InventoryService.StockFilter> stockBox =
            new JComboBox<>(InventoryService.StockFilter.values());
    private final JLabel countLabel = new JLabel(" ");
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;   // read-only table; editing happens in the form
        }

        @Override
        public Class<?> getColumnClass(int column) {
            // Real numbers (not text) so that clicking a column header sorts 9 before 10
            return switch (column) {
                case 0, PRICE_COLUMN -> Long.class;
                case 4, 5 -> Integer.class;
                default -> String.class;
            };
        }
    };
    private final StripedTable table = new StripedTable(tableModel);
    private final JButton editButton = new JButton("Edit");
    private final JButton deleteButton = new JButton("Delete");

    private String currencySymbol = "R";
    private boolean loadingCategories = false;   // stops the category box from reloading the table while it is being refilled
    private List<Product> shown = List.of();

    public ProductPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        try {
            currencySymbol = productService.getCurrencySymbol();
        } catch (BmsException e) {
            // keep "R"; the product list below will report any real database problem
        }

        add(buildTopBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        setUpColumns();

        table.setDoubleClickAction(() -> {
            if (editButton.isShowing() && editButton.isEnabled()) {
                editButton.doClick();
            }
        });

        updateButtons();
        reloadCategories();
        loadProducts();
    }

    /** Called by the main window when this tab is opened, so the list is never out of date. */
    @Override
    public void refresh() {
        reloadCategories();
        loadProducts();
    }

    private JPanel buildTopBar() {
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        searchButton.addActionListener(event -> loadProducts());
        searchField.addActionListener(event -> loadProducts());   // Enter key
        categoryBox.addActionListener(event -> {
            if (!loadingCategories) {
                loadProducts();
            }
        });
        stockBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? "" : ((InventoryService.StockFilter) value).getLabel());
                return this;
            }
        });
        stockBox.addActionListener(event -> loadProducts());
        clearButton.addActionListener(event -> {
            searchField.setText("");
            stockBox.setSelectedItem(InventoryService.StockFilter.ALL);
            categoryBox.setSelectedIndex(0);   // "All categories" (this also reloads the table)
            loadProducts();
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search (name, category, supplier):"));
        bar.add(searchField);
        bar.add(new JLabel("Category:"));
        bar.add(categoryBox);
        bar.add(new JLabel("Stock:"));
        bar.add(stockBox);
        bar.add(searchButton);
        bar.add(clearButton);
        return bar;
    }

    private JPanel buildBottomBar() {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(countLabel);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(left, BorderLayout.WEST);

        if (Session.isAdmin()) {
            JButton addButton = new JButton("Add product");
            addButton.addActionListener(event -> addProduct());
            editButton.addActionListener(event -> editSelected());
            deleteButton.addActionListener(event -> deleteSelected());

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.add(addButton);
            buttons.add(editButton);
            buttons.add(deleteButton);
            bar.add(buttons, BorderLayout.EAST);
        } else {
            JLabel note = new JLabel("You can view products. Only an administrator can change them.");
            note.setForeground(Color.GRAY);
            bar.add(note, BorderLayout.EAST);
        }
        return bar;
    }

    private void setUpColumns() {
        int[] widths = {45, 220, 110, 90, 70, 80, 110, 170};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Price: show cents as money, right-aligned
        table.getColumnModel().getColumn(PRICE_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                String text = (value instanceof Long cents) ? MoneyFormatter.format(cents, currencySymbol) : "";
                Component c = super.getTableCellRendererComponent(t, text, selected, focus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setBorder(NUMBER_PADDING);
                return c;
            }
        });

        // ID, stock and reorder level: right-aligned numbers with a little space to the right
        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setBorder(NUMBER_PADDING);   // Swing resets the border on every cell, so it is set here
                return c;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(rightAlign);
        table.getColumnModel().getColumn(4).setCellRenderer(rightAlign);
        table.getColumnModel().getColumn(5).setCellRenderer(rightAlign);

        // Status: colour it
        table.getColumnModel().getColumn(STATUS_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                if (!selected) {
                    if ("OUT OF STOCK".equals(value)) {
                        c.setForeground(DANGER_COLOUR);
                    } else if ("LOW STOCK".equals(value)) {
                        c.setForeground(WARNING_COLOUR);
                    } else {
                        c.setForeground(Color.DARK_GRAY);
                    }
                }
                return c;
            }
        });
    }

    // ---------- loading ----------

    /** Refills the category drop-down, keeping the current choice when it still exists. */
    private void reloadCategories() {
        Object previous = categoryBox.getSelectedItem();
        loadingCategories = true;
        try {
            categoryBox.removeAllItems();
            categoryBox.addItem(ALL_CATEGORIES);
            for (String category : productService.getCategories()) {
                categoryBox.addItem(category);
            }
            if (previous != null) {
                categoryBox.setSelectedItem(previous);   // does nothing if it no longer exists
            }
            if (categoryBox.getSelectedItem() == null) {
                categoryBox.setSelectedIndex(0);
            }
        } catch (BmsException e) {
            showError(e.getMessage());
        } finally {
            loadingCategories = false;
        }
    }

    private void loadProducts() {
        String category = (String) categoryBox.getSelectedItem();
        if (category == null || ALL_CATEGORIES.equals(category)) {
            category = null;
        }
        try {
            shown = productService.search(searchField.getText(), category,
                    (InventoryService.StockFilter) stockBox.getSelectedItem());
        } catch (BmsException e) {
            showError(e.getMessage());
            return;
        }
        tableModel.setRowCount(0);
        for (Product p : shown) {
            tableModel.addRow(new Object[]{
                p.getProductId(), p.getProductName(), nullToEmpty(p.getCategory()), p.getPriceCents(),
                p.getQuantityInStock(), p.getReorderLevel(), p.getStockStatus(), nullToEmpty(p.getSupplierName())});
        }
        boolean filtering = !searchField.getText().trim().isEmpty() || category != null
                || stockBox.getSelectedItem() != InventoryService.StockFilter.ALL;
        countLabel.setText(shown.size() + (filtering ? " product(s) found" : " product(s)"));
        updateButtons();
    }

    // ---------- actions (administrator only: the buttons are not even shown to employees) ----------

    private void addProduct() {
        Product entered = askUser(null);
        if (entered == null) {
            return;
        }
        try {
            productService.add(entered);
            reloadCategories();
            loadProducts();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void editSelected() {
        Product selected = getSelected();
        if (selected == null) {
            return;
        }
        Product entered = askUser(selected);
        if (entered == null) {
            return;
        }
        try {
            productService.update(entered);
            reloadCategories();
            loadProducts();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void deleteSelected() {
        Product selected = getSelected();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete \"" + selected.getProductName() + "\"?\nIt will no longer appear in the product list.",
                "Delete product", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            productService.delete(selected.getProductId());
            reloadCategories();
            loadProducts();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    /**
     * Shows the form until the data is accepted, or the user cancels (returns null).
     * When the service refuses the data, the form is shown again with the reason.
     */
    private Product askUser(Product existing) {
        List<String> categories;
        List<SupplierOption> suppliers;
        int defaultReorderLevel;
        try {
            categories = productService.getCategories();
            suppliers = productService.getSupplierOptions();
            defaultReorderLevel = productService.getDefaultReorderLevel();
        } catch (BmsException e) {
            showError(e.getMessage());
            return null;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        ProductDialog dialog = new ProductDialog(owner, existing, categories, suppliers,
                currencySymbol, defaultReorderLevel);
        Product entered = dialog.showDialog();

        while (entered != null) {
            try {
                productService.validateProduct(entered);   // same rules add()/update() will apply
                return entered;
            } catch (BmsException e) {
                entered = dialog.showAgainWithMessage(e.getMessage());
            }
        }
        return null;
    }

    // ---------- small helpers ----------

    private Product getSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        long id = (Long) tableModel.getValueAt(table.convertRowIndexToModel(viewRow), 0);
        for (Product p : shown) {
            if (p.getProductId() == id) {
                return p;
            }
        }
        return null;
    }

    private void updateButtons() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Products", JOptionPane.ERROR_MESSAGE);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
