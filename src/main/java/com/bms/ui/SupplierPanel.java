package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.model.Supplier;
import com.bms.service.SupplierService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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

/** The Suppliers tab (administrator only): search box, table, and Add / Edit / Delete buttons. */
public class SupplierPanel extends JPanel implements Refreshable {

    private static final String[] COLUMNS = {"ID", "Supplier", "Contact person", "Email", "Phone", "Address", "Products"};
    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);

    private final SupplierService supplierService = new SupplierService();

    private final JTextField searchField = new JTextField(24);
    private final JLabel countLabel = new JLabel(" ");
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;   // the table is read-only; editing happens in the form
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return (column == 0) ? Long.class : (column == 6) ? Integer.class : String.class;
        }
    };
    private final StripedTable table = new StripedTable(tableModel);
    private final JButton editButton = new JButton("Edit");
    private final JButton deleteButton = new JButton("Delete");

    /** The suppliers currently shown, in the same order as the table rows. */
    private List<Supplier> shown = List.of();

    public SupplierPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

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
        loadSuppliers();
    }

    /** Called by the main window when this tab is opened, so the list is never out of date. */
    @Override
    public void refresh() {
        loadSuppliers();
    }

    private JPanel buildTopBar() {
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        searchButton.addActionListener(event -> loadSuppliers());
        searchField.addActionListener(event -> loadSuppliers());   // Enter key
        clearButton.addActionListener(event -> {
            searchField.setText("");
            loadSuppliers();
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search (name, contact, email or phone):"));
        bar.add(searchField);
        bar.add(searchButton);
        bar.add(clearButton);
        return bar;
    }

    private JPanel buildBottomBar() {
        JButton addButton = new JButton("Add supplier");
        addButton.addActionListener(event -> addSupplier());
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(countLabel, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private void setUpColumns() {
        int[] widths = {45, 200, 140, 220, 110, 230, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        // ID and product count: right-aligned numbers with a little space to the right
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
        table.getColumnModel().getColumn(6).setCellRenderer(rightAlign);
    }

    // ---------- loading ----------

    private void loadSuppliers() {
        try {
            shown = supplierService.search(searchField.getText());
        } catch (BmsException e) {
            showError(e.getMessage());
            return;
        }
        tableModel.setRowCount(0);
        for (Supplier s : shown) {
            tableModel.addRow(new Object[]{
                s.getSupplierId(), s.getSupplierName(), nullToEmpty(s.getContactPerson()),
                nullToEmpty(s.getEmail()), nullToEmpty(s.getPhone()), nullToEmpty(s.getAddress()),
                s.getProductCount()});
        }
        boolean searching = !searchField.getText().trim().isEmpty();
        countLabel.setText(shown.size() + (searching ? " supplier(s) found" : " supplier(s)"));
        updateButtons();
    }

    // ---------- actions ----------

    private void addSupplier() {
        Supplier entered = askUser(null);
        if (entered == null) {
            return;
        }
        try {
            supplierService.add(entered);
            loadSuppliers();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void editSelected() {
        Supplier selected = getSelected();
        if (selected == null) {
            return;
        }
        Supplier entered = askUser(selected);
        if (entered == null) {
            return;
        }
        try {
            supplierService.update(entered);
            loadSuppliers();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void deleteSelected() {
        Supplier selected = getSelected();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete \"" + selected.getSupplierName() + "\"?\nThey will no longer appear in the supplier list.",
                "Delete supplier", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            supplierService.delete(selected.getSupplierId());
            loadSuppliers();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    /**
     * Shows the form until the data is accepted, or the user cancels (returns null).
     * When the service refuses the data, the form is shown again with the reason.
     */
    private Supplier askUser(Supplier existing) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        SupplierDialog dialog = new SupplierDialog(owner, existing);
        Supplier entered = dialog.showDialog();

        while (entered != null) {
            try {
                supplierService.validateSupplier(entered);   // same rules add()/update() will apply
                return entered;
            } catch (BmsException e) {
                entered = dialog.showAgainWithMessage(e.getMessage());
            }
        }
        return null;
    }

    // ---------- small helpers ----------

    private Supplier getSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        long id = (Long) tableModel.getValueAt(table.convertRowIndexToModel(viewRow), 0);
        for (Supplier s : shown) {
            if (s.getSupplierId() == id) {
                return s;
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
        JOptionPane.showMessageDialog(this, message, "Suppliers", JOptionPane.ERROR_MESSAGE);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
