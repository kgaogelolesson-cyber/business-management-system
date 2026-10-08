package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.Product;
import com.bms.model.StockAdjustment;
import com.bms.service.InventoryService;
import com.bms.service.InventoryService.StockFilter;
import com.bms.util.InputParser;
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
 * The Inventory tab. Everyone can see the stock levels and the history of a product.
 * Only an administrator sees the "Adjust stock" button.
 * Top table = stock levels; bottom table = history of the selected product.
 */
public class InventoryPanel extends JPanel implements Refreshable {

    private static final String[] STOCK_COLUMNS = {"ID", "Product", "Category", "In stock", "Reorder at", "Status", "Supplier"};
    private static final String[] HISTORY_COLUMNS = {"Date", "Change", "Reason", "By"};
    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);
    private static final Color RED = new Color(180, 0, 0);
    private static final Color ORANGE = new Color(200, 110, 0);

    private final InventoryService inventoryService = new InventoryService();

    private final JTextField searchField = new JTextField(20);
    private final JComboBox<StockFilter> filterBox = new JComboBox<>(StockFilter.values());
    private final JLabel countLabel = new JLabel(" ");
    private final DefaultTableModel stockModel = new DefaultTableModel(STOCK_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return (column == 0 || column == 3 || column == 4) ? Integer.class : String.class;
        }
    };
    private final DefaultTableModel historyModel = new DefaultTableModel(HISTORY_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 ? Integer.class : String.class;
        }
    };
    private final StripedTable stockTable = new StripedTable(stockModel);
    private final StripedTable historyTable = new StripedTable(historyModel);
    private final JPanel historyPanel = new JPanel(new BorderLayout());
    private final JButton adjustButton = new JButton("Adjust stock");

    /** The products currently shown, in the same order as the table rows. */
    private List<Product> shown = List.of();

    public InventoryPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        historyPanel.setBorder(BorderFactory.createTitledBorder("Stock history"));
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JPanel stockPanel = new JPanel(new BorderLayout(0, 8));
        stockPanel.add(buildTopBar(), BorderLayout.NORTH);
        stockPanel.add(new JScrollPane(stockTable), BorderLayout.CENTER);
        stockPanel.add(buildBottomBar(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stockPanel, historyPanel);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        stockTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stockTable.setRowHeight(24);
        stockTable.setAutoCreateRowSorter(true);
        stockTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateButtons();
                loadHistory();
            }
        });
        historyTable.setRowHeight(22);
        historyTable.setAutoCreateRowSorter(true);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setUpColumns();

        stockTable.setDoubleClickAction(() -> {
            if (adjustButton.isShowing() && adjustButton.isEnabled()) {
                adjustButton.doClick();
            }
        });

        updateButtons();
        loadStock();
    }

    /** Called by the main window when this tab is opened, so stock levels are never out of date. */
    @Override
    public void refresh() {
        loadStock();
    }

    private JPanel buildTopBar() {
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        searchButton.addActionListener(event -> loadStock());
        searchField.addActionListener(event -> loadStock());   // Enter key
        filterBox.addActionListener(event -> loadStock());
        clearButton.addActionListener(event -> {
            searchField.setText("");
            filterBox.setSelectedItem(StockFilter.ALL);
            loadStock();
        });
        filterBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? "" : ((StockFilter) value).getLabel());
                return this;
            }
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search (product, category or supplier):"));
        bar.add(searchField);
        bar.add(new JLabel("Show:"));
        bar.add(filterBox);
        bar.add(searchButton);
        bar.add(clearButton);
        return bar;
    }

    private JPanel buildBottomBar() {
        adjustButton.addActionListener(event -> adjustSelected());
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> loadStock());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(refreshButton);
        if (Session.isAdmin()) {          // employees can look, but not change stock by hand
            buttons.add(adjustButton);
        }

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(countLabel, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private void setUpColumns() {
        int[] widths = {45, 220, 130, 80, 90, 110, 200};
        // Swing resets the border on every cell, so the padding is set inside the renderer.
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                boolean numeric = (column == 0 || column == 3 || column == 4);
                setHorizontalAlignment(numeric ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setBorder(numeric ? NUMBER_PADDING : BorderFactory.createEmptyBorder(0, 4, 0, 4));
                if (!selected) {
                    Object status = t.getModel().getValueAt(t.convertRowIndexToModel(row), 5);
                    Color colour = t.getForeground();
                    if ("OUT OF STOCK".equals(status) && (column == 3 || column == 5)) {
                        colour = RED;
                    } else if ("LOW STOCK".equals(status) && (column == 3 || column == 5)) {
                        colour = ORANGE;
                    }
                    c.setForeground(colour);
                }
                return c;
            }
        };
        for (int i = 0; i < widths.length; i++) {
            stockTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            stockTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        int[] historyWidths = {150, 70, 420, 180};
        DefaultTableCellRenderer historyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                boolean change = (column == 1);
                setHorizontalAlignment(change ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setBorder(change ? NUMBER_PADDING : BorderFactory.createEmptyBorder(0, 4, 0, 4));
                if (change && value instanceof Integer) {
                    int amount = (Integer) value;
                    setText(amount > 0 ? "+" + amount : String.valueOf(amount));   // +10 / -3
                    if (!selected) {
                        c.setForeground(amount < 0 ? RED : t.getForeground());
                    }
                } else if (!selected) {
                    c.setForeground(t.getForeground());
                }
                return c;
            }
        };
        for (int i = 0; i < historyWidths.length; i++) {
            historyTable.getColumnModel().getColumn(i).setPreferredWidth(historyWidths[i]);
            historyTable.getColumnModel().getColumn(i).setCellRenderer(historyRenderer);
        }
    }

    // ---------- loading ----------

    private void loadStock() {
        long selectedId = selectedProductId();
        try {
            shown = inventoryService.search(searchField.getText(), (StockFilter) filterBox.getSelectedItem());
        } catch (BmsException e) {
            showError(e.getMessage());
            return;
        }
        stockModel.setRowCount(0);
        for (Product p : shown) {
            stockModel.addRow(new Object[]{
                (int) p.getProductId(), p.getProductName(), nullToEmpty(p.getCategory()),
                p.getQuantityInStock(), p.getReorderLevel(), p.getStockStatus(),
                nullToEmpty(p.getSupplierName())});
        }
        boolean filtering = !searchField.getText().trim().isEmpty() || filterBox.getSelectedItem() != StockFilter.ALL;
        countLabel.setText(shown.size() + (filtering ? " product(s) found" : " product(s)"));

        reselect(selectedId);
        updateButtons();
        loadHistory();
    }

    /** Fills the bottom table with the history of the selected product (or clears it). */
    private void loadHistory() {
        Product selected = getSelected();
        historyModel.setRowCount(0);
        if (selected == null) {
            historyPanel.setBorder(BorderFactory.createTitledBorder("Stock history - select a product above"));
            return;
        }
        historyPanel.setBorder(BorderFactory.createTitledBorder("Stock history: " + selected.getProductName()));
        try {
            for (StockAdjustment a : inventoryService.getHistory(selected.getProductId())) {
                historyModel.addRow(new Object[]{a.getAdjustedAt(), a.getQuantityChange(), a.getReason(), a.getAdjustedBy()});
            }
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    // ---------- actions ----------

    private void adjustSelected() {
        Product selected = getSelected();
        if (selected == null) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        StockAdjustDialog dialog = new StockAdjustDialog(owner, selected.getProductName(), selected.getQuantityInStock());
        StockAdjustDialog.Entry entry = dialog.showDialog();

        // Show the form again, with the reason, until the change is accepted or the user cancels.
        while (entry != null) {
            try {
                int amount = InputParser.parseWholeNumber(entry.quantityText(), "The quantity");
                inventoryService.adjustStock(selected.getProductId(), entry.add() ? amount : -amount, entry.reason());
                loadStock();
                return;
            } catch (ValidationException e) {
                entry = dialog.showAgainWithMessage(e.getMessage());
            } catch (BmsException e) {
                showError(e.getMessage());
                return;
            }
        }
    }

    // ---------- small helpers ----------

    private Product getSelected() {
        long id = selectedProductId();
        for (Product p : shown) {
            if (p.getProductId() == id) {
                return p;
            }
        }
        return null;
    }

    private long selectedProductId() {
        int viewRow = stockTable.getSelectedRow();
        if (viewRow < 0) {
            return -1;
        }
        return (Integer) stockModel.getValueAt(stockTable.convertRowIndexToModel(viewRow), 0);
    }

    /** After a reload, selects the same product again (if it is still listed). */
    private void reselect(long productId) {
        if (productId < 0) {
            return;
        }
        for (int modelRow = 0; modelRow < stockModel.getRowCount(); modelRow++) {
            if ((Integer) stockModel.getValueAt(modelRow, 0) == productId) {
                int viewRow = stockTable.convertRowIndexToView(modelRow);
                stockTable.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
    }

    private void updateButtons() {
        adjustButton.setEnabled(stockTable.getSelectedRow() >= 0);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Inventory", JOptionPane.ERROR_MESSAGE);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
