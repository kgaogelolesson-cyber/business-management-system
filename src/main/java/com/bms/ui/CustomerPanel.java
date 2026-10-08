package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.model.Customer;
import com.bms.service.CustomerService;
import com.bms.util.Session;
import java.awt.BorderLayout;
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
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** The Customers tab: search box, table, and Add / Edit / Delete buttons. */
public class CustomerPanel extends JPanel implements Refreshable {

    private static final String[] COLUMNS = {"ID", "First name", "Last name", "Email", "Phone", "Address"};

    private final CustomerService customerService = new CustomerService();

    private final JTextField searchField = new JTextField(24);
    private final JLabel countLabel = new JLabel(" ");
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;   // the table is read-only; editing happens in the form
        }
    };
    private final StripedTable table = new StripedTable(tableModel);
    private final JButton editButton = new JButton("Edit");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton historyButton = new JButton("Order history");

    /** The customers currently shown, in the same order as the table rows. */
    private List<Customer> shown = List.of();

    public CustomerPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        add(buildTopBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        setColumnWidths();

        // Only the Administrator may delete customers.
        deleteButton.setVisible(Session.isAdmin());

        table.setDoubleClickAction(() -> {
            if (editButton.isShowing() && editButton.isEnabled()) {
                editButton.doClick();
            }
        });

        updateButtons();
        loadCustomers();
    }

    /** Called by the main window when this tab is opened, so the list is never out of date. */
    @Override
    public void refresh() {
        loadCustomers();
    }

    private JPanel buildTopBar() {
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        searchButton.addActionListener(event -> loadCustomers());
        searchField.addActionListener(event -> loadCustomers());   // Enter key
        clearButton.addActionListener(event -> {
            searchField.setText("");
            loadCustomers();
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search (name, email or phone):"));
        bar.add(searchField);
        bar.add(searchButton);
        bar.add(clearButton);
        return bar;
    }

    private JPanel buildBottomBar() {
        JButton addButton = new JButton("Add customer");
        addButton.addActionListener(event -> addCustomer());
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        historyButton.addActionListener(event -> showHistory());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(historyButton);
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(countLabel, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private void setColumnWidths() {
        int[] widths = {50, 130, 140, 240, 130, 330};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // ---------- loading ----------

    private void loadCustomers() {
        try {
            shown = customerService.search(searchField.getText());
        } catch (BmsException e) {
            showError(e.getMessage());
            return;
        }
        tableModel.setRowCount(0);
        for (Customer c : shown) {
            tableModel.addRow(new Object[]{
                c.getCustomerId(), c.getFirstName(), c.getLastName(),
                nullToEmpty(c.getEmail()), nullToEmpty(c.getPhone()), nullToEmpty(c.getAddress())});
        }
        boolean searching = !searchField.getText().trim().isEmpty();
        countLabel.setText(shown.size() + (searching ? " customer(s) found" : " customer(s)"));
        updateButtons();
    }

    // ---------- actions ----------

    private void addCustomer() {
        Customer entered = askUser(null);
        if (entered == null) {
            return;
        }
        try {
            customerService.add(entered);
            loadCustomers();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void editSelected() {
        Customer selected = getSelected();
        if (selected == null) {
            return;
        }
        Customer entered = askUser(selected);
        if (entered == null) {
            return;
        }
        try {
            customerService.update(entered);
            loadCustomers();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void deleteSelected() {
        Customer selected = getSelected();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete " + selected.getFullName() + "?\nThey will no longer appear in the customer list.",
                "Delete customer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            customerService.delete(selected.getCustomerId());
            loadCustomers();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    /**
     * Shows the form until the data is accepted, or the user cancels (returns null).
     * The form is re-opened with the reason when the service refuses the data,
     * and the user is warned when another customer has the same email or phone.
     */
    private Customer askUser(Customer existing) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        CustomerDialog dialog = new CustomerDialog(owner, existing);
        Customer entered = dialog.showDialog();

        while (entered != null) {
            try {
                customerService.validateCustomer(entered);   // same rules add()/update() will apply
                List<Customer> duplicates = customerService.findPossibleDuplicates(entered);
                if (!duplicates.isEmpty() && !confirmDuplicate(duplicates)) {
                    entered = dialog.showAgainWithMessage("Change the email or phone, or cancel.");
                    continue;
                }
                return entered;
            } catch (BmsException e) {
                entered = dialog.showAgainWithMessage(e.getMessage());
            }
        }
        return null;
    }

    private boolean confirmDuplicate(List<Customer> duplicates) {
        StringBuilder text = new StringBuilder("Another customer already has this email or phone:\n\n");
        for (Customer d : duplicates) {
            text.append("  - ").append(d.getFullName());
            if (d.getEmail() != null) {
                text.append("  (").append(d.getEmail()).append(")");
            }
            if (d.getPhone() != null) {
                text.append("  ").append(d.getPhone());
            }
            text.append("\n");
        }
        text.append("\nSave anyway?");
        return JOptionPane.showConfirmDialog(this, text.toString(), "Possible duplicate",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    // ---------- small helpers ----------

    private Customer getSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        long id = (Long) tableModel.getValueAt(table.convertRowIndexToModel(viewRow), 0);
        for (Customer c : shown) {
            if (c.getCustomerId() == id) {
                return c;
            }
        }
        return null;
    }

    private void showHistory() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        Customer customer = shown.get(table.convertRowIndexToModel(viewRow));
        new OrderHistoryDialog(SwingUtilities.getWindowAncestor(this), customer).setVisible(true);
    }

    private void updateButtons() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        historyButton.setEnabled(hasSelection);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Customers", JOptionPane.ERROR_MESSAGE);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
