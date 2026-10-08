package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.exception.ValidationException;
import com.bms.model.EmployeeAccount;
import com.bms.service.EmployeeService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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
 * The Employees tab (administrator only): search box, table, and
 * Add / Edit / Reset password / Deactivate / Reactivate buttons.
 */
public class EmployeePanel extends JPanel implements Refreshable {

    private static final String[] COLUMNS =
            {"ID", "Name", "Username", "Role", "Position", "Department", "Email", "Phone", "Hired", "Status"};
    private static final int STATUS_COLUMN = 9;
    private static final String ACTIVE_TEXT = "Active";
    private static final String INACTIVE_TEXT = "Deactivated";
    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);

    private final EmployeeService employeeService = new EmployeeService();

    private final JTextField searchField = new JTextField(22);
    private static final String ALL_DEPARTMENTS = "All departments";
    private final JComboBox<String> departmentBox = new JComboBox<>();
    private boolean loadingDepartments = false;
    private final JCheckBox showInactiveBox = new JCheckBox("Show deactivated employees");
    private final JLabel countLabel = new JLabel(" ");
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;   // the table is read-only; editing happens in the form
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return (column == 0) ? Long.class : String.class;
        }
    };
    private final StripedTable table = new StripedTable(tableModel);
    private final JButton editButton = new JButton("Edit");
    private final JButton resetPasswordButton = new JButton("Reset password");
    private final JButton deactivateButton = new JButton("Deactivate");
    private final JButton reactivateButton = new JButton("Reactivate");

    /** The employees currently shown, in the same order as the table rows. */
    private List<EmployeeAccount> shown = List.of();

    public EmployeePanel() {
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
        loadEmployees();
    }

    /** Called by the main window when this tab is opened, so the list is never out of date. */
    @Override
    public void refresh() {
        loadEmployees();
    }

    private JPanel buildTopBar() {
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        searchButton.addActionListener(event -> loadEmployees());
        searchField.addActionListener(event -> loadEmployees());   // Enter key
        showInactiveBox.addActionListener(event -> loadEmployees());
        departmentBox.addActionListener(event -> {
            if (!loadingDepartments) {
                loadEmployees();
            }
        });
        clearButton.addActionListener(event -> {
            searchField.setText("");
            departmentBox.setSelectedIndex(0);   // "All departments"
            loadEmployees();
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search (name, username, email, position...):"));
        bar.add(searchField);
        bar.add(new JLabel("Department:"));
        bar.add(departmentBox);
        bar.add(searchButton);
        bar.add(clearButton);
        bar.add(showInactiveBox);
        return bar;
    }

    private JPanel buildBottomBar() {
        JButton addButton = new JButton("Add employee");
        addButton.addActionListener(event -> addEmployee());
        editButton.addActionListener(event -> editSelected());
        resetPasswordButton.addActionListener(event -> resetPasswordOfSelected());
        deactivateButton.addActionListener(event -> deactivateSelected());
        reactivateButton.addActionListener(event -> reactivateSelected());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(resetPasswordButton);
        buttons.add(deactivateButton);
        buttons.add(reactivateButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(countLabel, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private void setUpColumns() {
        int[] widths = {40, 150, 90, 100, 120, 110, 190, 100, 85, 85};
        // Swing resets the border on every cell, so the number padding is set inside the renderer.
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                boolean numeric = (column == 0);
                setHorizontalAlignment(numeric ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setBorder(numeric ? NUMBER_PADDING : BorderFactory.createEmptyBorder(0, 4, 0, 4));

                // Deactivated employees are shown in grey (unless the row is selected)
                Object status = t.getModel().getValueAt(t.convertRowIndexToModel(row), STATUS_COLUMN);
                boolean inactive = INACTIVE_TEXT.equals(status);
                if (!selected) {
                    c.setForeground(inactive ? Color.GRAY : t.getForeground());
                }
                return c;
            }
        };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    // ---------- loading ----------

    /** Refills the department drop-down, keeping the current choice when it still exists. */
    private void reloadDepartments() {
        Object previous = departmentBox.getSelectedItem();
        loadingDepartments = true;
        try {
            departmentBox.removeAllItems();
            departmentBox.addItem(ALL_DEPARTMENTS);
            for (String department : employeeService.getDepartments()) {
                departmentBox.addItem(department);
            }
            if (previous != null) {
                departmentBox.setSelectedItem(previous);   // does nothing if it no longer exists
            }
            if (departmentBox.getSelectedItem() == null) {
                departmentBox.setSelectedIndex(0);
            }
        } catch (BmsException e) {
            showError(e.getMessage());
        } finally {
            loadingDepartments = false;
        }
    }

    private void loadEmployees() {
        reloadDepartments();
        String department = (String) departmentBox.getSelectedItem();
        if (department == null || ALL_DEPARTMENTS.equals(department)) {
            department = null;
        }
        try {
            shown = employeeService.search(searchField.getText(), showInactiveBox.isSelected(), department);
        } catch (BmsException e) {
            showError(e.getMessage());
            return;
        }
        tableModel.setRowCount(0);
        for (EmployeeAccount a : shown) {
            tableModel.addRow(new Object[]{
                a.getEmployeeId(), a.getFullName(), nullToEmpty(a.getUsername()),
                a.getRole() == null ? "" : a.getRole().getDisplayName(),
                nullToEmpty(a.getPosition()), nullToEmpty(a.getDepartment()),
                nullToEmpty(a.getEmail()), nullToEmpty(a.getPhone()), nullToEmpty(a.getHireDate()),
                a.isActive() ? ACTIVE_TEXT : INACTIVE_TEXT});
        }
        boolean searching = !searchField.getText().trim().isEmpty() || department != null;
        countLabel.setText(shown.size() + (searching ? " employee(s) found" : " employee(s)"));
        updateButtons();
    }

    // ---------- actions ----------

    private void addEmployee() {
        EmployeeAccount entered = askUser(null);
        if (entered == null) {
            return;
        }
        try {
            employeeService.add(entered);
            loadEmployees();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void editSelected() {
        EmployeeAccount selected = getSelected();
        if (selected == null) {
            return;
        }
        EmployeeAccount entered = askUser(selected);
        if (entered == null) {
            return;
        }
        try {
            employeeService.update(entered);
            loadEmployees();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void deactivateSelected() {
        EmployeeAccount selected = getSelected();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Deactivate " + selected.getFullName() + "?\n"
                        + "They will no longer be able to log in. Their past orders are kept.",
                "Deactivate employee", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            employeeService.deactivate(selected.getEmployeeId());
            loadEmployees();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void reactivateSelected() {
        EmployeeAccount selected = getSelected();
        if (selected == null) {
            return;
        }
        try {
            employeeService.reactivate(selected.getEmployeeId());
            loadEmployees();
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    /** Asks for a new password (twice) until it is accepted, or the user cancels. */
    private void resetPasswordOfSelected() {
        EmployeeAccount selected = getSelected();
        if (selected == null) {
            return;
        }
        String message = " ";
        while (true) {
            JPasswordField newPassword = new JPasswordField(20);
            JPasswordField confirm = new JPasswordField(20);
            JLabel problem = new JLabel("<html><div style='width:260px'>" + escape(message) + "</div></html>");
            problem.setForeground(new Color(180, 0, 0));

            JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
            form.add(new JLabel("New password for " + selected.getFullName() + ":"));
            form.add(newPassword);
            form.add(new JLabel("Type it again:"));
            form.add(confirm);
            form.add(problem);

            int answer = JOptionPane.showConfirmDialog(this, form, "Reset password",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                employeeService.resetPassword(selected.getEmployeeId(),
                        new String(newPassword.getPassword()), new String(confirm.getPassword()));
                JOptionPane.showMessageDialog(this,
                        "The password for " + selected.getFullName() + " was changed.",
                        "Reset password", JOptionPane.INFORMATION_MESSAGE);
                return;
            } catch (ValidationException e) {
                message = e.getMessage();          // show the form again with the reason
            } catch (BmsException e) {
                showError(e.getMessage());
                return;
            }
        }
    }

    /**
     * Shows the form until the data is accepted, or the user cancels (returns null).
     * When the service refuses the data, the form is shown again with the reason.
     */
    private EmployeeAccount askUser(EmployeeAccount existing) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        EmployeeDialog dialog = new EmployeeDialog(owner, existing);
        EmployeeAccount entered = dialog.showDialog();

        while (entered != null) {
            try {
                employeeService.validateEmployee(entered);   // same rules add()/update() will apply
                return entered;
            } catch (BmsException e) {
                entered = dialog.showAgainWithMessage(e.getMessage());
            }
        }
        return null;
    }

    // ---------- small helpers ----------

    private EmployeeAccount getSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        long id = (Long) tableModel.getValueAt(table.convertRowIndexToModel(viewRow), 0);
        for (EmployeeAccount a : shown) {
            if (a.getEmployeeId() == id) {
                return a;
            }
        }
        return null;
    }

    private void updateButtons() {
        EmployeeAccount selected = getSelected();
        boolean has = selected != null;
        boolean active = has && selected.isActive();
        editButton.setEnabled(active);
        resetPasswordButton.setEnabled(active && selected.getUsername() != null);
        deactivateButton.setEnabled(active);
        reactivateButton.setEnabled(has && !selected.isActive());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Employees", JOptionPane.ERROR_MESSAGE);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
