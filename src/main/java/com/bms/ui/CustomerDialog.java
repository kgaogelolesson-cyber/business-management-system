package com.bms.ui;

import com.bms.model.Customer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The Add / Edit customer form. It only collects what the user typed.
 * Checking the data is the service's job, not this form's.
 */
public class CustomerDialog extends JDialog {

    private final JTextField firstNameField = new JTextField(24);
    private final JTextField lastNameField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JTextField phoneField = new JTextField(24);
    private final JTextField addressField = new JTextField(24);
    private final JLabel messageLabel = new JLabel(" ");

    private final Customer customer;   // the customer being edited (customerId 0 = new)
    private boolean saveClicked = false;

    public CustomerDialog(Window owner, Customer existing) {
        super(owner, existing == null ? "Add customer" : "Edit customer", ModalityType.APPLICATION_MODAL);
        this.customer = (existing == null) ? new Customer() : existing;

        firstNameField.setText(nullToEmpty(customer.getFirstName()));
        lastNameField.setText(nullToEmpty(customer.getLastName()));
        emailField.setText(nullToEmpty(customer.getEmail()));
        phoneField.setText(nullToEmpty(customer.getPhone()));
        addressField.setText(nullToEmpty(customer.getAddress()));

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

        addRow(form, c, 0, "First name *", firstNameField);
        addRow(form, c, 1, "Last name *", lastNameField);
        addRow(form, c, 2, "Email", emailField);
        addRow(form, c, 3, "Phone", phoneField);
        addRow(form, c, 4, "Address", addressField);

        messageLabel.setForeground(new Color(180, 0, 0));
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        form.add(messageLabel, c);
        return form;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JTextField field) {
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

    /** Shows the form and waits. Returns the filled-in customer, or null if the user cancelled. */
    public Customer showDialog() {
        saveClicked = false;
        setVisible(true);        // blocks until the dialog is hidden
        return saveClicked ? readForm() : null;
    }

    /** Shows the form again after the service refused the data, with the reason on screen. */
    public Customer showAgainWithMessage(String message) {
        messageLabel.setText("<html>" + escape(message) + "</html>");
        pack();
        return showDialog();
    }

    private Customer readForm() {
        Customer result = new Customer();
        result.setCustomerId(customer.getCustomerId());
        result.setFirstName(firstNameField.getText());
        result.setLastName(lastNameField.getText());
        result.setEmail(emailField.getText());
        result.setPhone(phoneField.getText());
        result.setAddress(addressField.getText());
        return result;
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
