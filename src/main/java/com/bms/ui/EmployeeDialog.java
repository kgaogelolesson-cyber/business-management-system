package com.bms.ui;

import com.bms.model.EmployeeAccount;
import com.bms.model.Role;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * The Add / Edit employee form. It only collects what the user typed.
 * Checking the data is the service's job, not this form's.
 * When editing, the username cannot be changed and the password fields are hidden
 * (use the "Reset password" button on the Employees tab instead).
 */
public class EmployeeDialog extends JDialog {

    private final JTextField firstNameField = new JTextField(24);
    private final JTextField lastNameField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JTextField phoneField = new JTextField(24);
    private final JTextField positionField = new JTextField(24);
    private final JTextField departmentField = new JTextField(24);
    private final JTextField hireDateField = new JTextField(24);
    private final JTextField usernameField = new JTextField(24);
    private final JComboBox<Role> roleBox = new JComboBox<>(Role.values());
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JPasswordField confirmField = new JPasswordField(24);
    private final JLabel messageLabel = new JLabel(" ");

    private final EmployeeAccount employee;   // the employee being edited (employeeId 0 = new)
    private final boolean isNew;
    private boolean saveClicked = false;

    public EmployeeDialog(Window owner, EmployeeAccount existing) {
        super(owner, existing == null ? "Add employee" : "Edit employee", ModalityType.APPLICATION_MODAL);
        this.employee = (existing == null) ? new EmployeeAccount() : existing;
        this.isNew = (existing == null);

        firstNameField.setText(nullToEmpty(employee.getFirstName()));
        lastNameField.setText(nullToEmpty(employee.getLastName()));
        emailField.setText(nullToEmpty(employee.getEmail()));
        phoneField.setText(nullToEmpty(employee.getPhone()));
        positionField.setText(nullToEmpty(employee.getPosition()));
        departmentField.setText(nullToEmpty(employee.getDepartment()));
        hireDateField.setText(nullToEmpty(employee.getHireDate()));
        hireDateField.setToolTipText("Write the date as yyyy-MM-dd, for example 2026-03-15");
        usernameField.setText(nullToEmpty(employee.getUsername()));
        usernameField.setEditable(isNew);   // a username never changes after the account is created
        roleBox.setSelectedItem(employee.getRole() == null ? Role.EMPLOYEE : employee.getRole());
        roleBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? "" : ((Role) value).getDisplayName());
                return this;
            }
        });

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 4, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "First name *", firstNameField);
        addRow(form, c, row++, "Last name *", lastNameField);
        addRow(form, c, row++, "Email", emailField);
        addRow(form, c, row++, "Phone", phoneField);
        addRow(form, c, row++, "Position", positionField);
        addRow(form, c, row++, "Department", departmentField);
        addRow(form, c, row++, "Hire date (yyyy-MM-dd)", hireDateField);
        addRow(form, c, row++, isNew ? "Username *" : "Username", usernameField);
        addRow(form, c, row++, "Role *", roleBox);
        if (isNew) {
            addRow(form, c, row++, "Password *", passwordField);
            addRow(form, c, row++, "Confirm password *", confirmField);
        } else {
            JLabel note = new JLabel("To change the password, use \"Reset password\" on the Employees tab.");
            note.setForeground(Color.GRAY);
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            form.add(note, c);
        }

        messageLabel.setForeground(new Color(180, 0, 0));
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        form.add(messageLabel, c);
        return form;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        form.add(field, c);
    }

    private JPanel buildButtons() {
        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(event -> {
            saveClicked = true;
            setVisible(false);
        });
        cancel.addActionListener(event -> setVisible(false));
        DialogKeys.escapeCancels(this, cancel);
        getRootPane().setDefaultButton(save);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 20, 10, 20));
        buttons.add(cancel);
        buttons.add(save);
        return buttons;
    }

    /** Shows the form and waits. Returns the filled-in employee, or null if the user cancelled. */
    public EmployeeAccount showDialog() {
        saveClicked = false;
        setVisible(true);        // blocks until the dialog is hidden
        return saveClicked ? readForm() : null;
    }

    /** Shows the form again after the service refused the data, with the reason on screen. */
    public EmployeeAccount showAgainWithMessage(String message) {
        messageLabel.setText("<html><div style='width:360px'>" + escape(message) + "</div></html>");
        pack();
        return showDialog();
    }

    private EmployeeAccount readForm() {
        EmployeeAccount result = new EmployeeAccount();
        result.setEmployeeId(employee.getEmployeeId());
        result.setActive(employee.isActive());
        result.setFirstName(firstNameField.getText());
        result.setLastName(lastNameField.getText());
        result.setEmail(emailField.getText());
        result.setPhone(phoneField.getText());
        result.setPosition(positionField.getText());
        result.setDepartment(departmentField.getText());
        result.setHireDate(hireDateField.getText());
        result.setUsername(usernameField.getText());
        result.setRole((Role) roleBox.getSelectedItem());
        if (isNew) {
            result.setPassword(new String(passwordField.getPassword()));
            result.setConfirmPassword(new String(confirmField.getPassword()));
        }
        return result;
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
