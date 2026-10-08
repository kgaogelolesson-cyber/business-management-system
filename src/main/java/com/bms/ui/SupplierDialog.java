package com.bms.ui;

import com.bms.model.Supplier;
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
 * The Add / Edit supplier form. It only collects what the user typed.
 * Checking the data is the service's job, not this form's.
 */
public class SupplierDialog extends JDialog {

    private final JTextField nameField = new JTextField(26);
    private final JTextField contactField = new JTextField(26);
    private final JTextField emailField = new JTextField(26);
    private final JTextField phoneField = new JTextField(26);
    private final JTextField addressField = new JTextField(26);
    private final JLabel messageLabel = new JLabel(" ");

    private final Supplier supplier;   // the supplier being edited (supplierId 0 = new)
    private boolean saveClicked = false;

    public SupplierDialog(Window owner, Supplier existing) {
        super(owner, existing == null ? "Add supplier" : "Edit supplier", ModalityType.APPLICATION_MODAL);
        this.supplier = (existing == null) ? new Supplier() : existing;

        nameField.setText(nullToEmpty(supplier.getSupplierName()));
        contactField.setText(nullToEmpty(supplier.getContactPerson()));
        emailField.setText(nullToEmpty(supplier.getEmail()));
        phoneField.setText(nullToEmpty(supplier.getPhone()));
        addressField.setText(nullToEmpty(supplier.getAddress()));

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

        addRow(form, c, 0, "Supplier name *", nameField);
        addRow(form, c, 1, "Contact person", contactField);
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

    /** Shows the form and waits. Returns the filled-in supplier, or null if the user cancelled. */
    public Supplier showDialog() {
        saveClicked = false;
        setVisible(true);        // blocks until the dialog is hidden
        return saveClicked ? readForm() : null;
    }

    /** Shows the form again after the service refused the data, with the reason on screen. */
    public Supplier showAgainWithMessage(String message) {
        messageLabel.setText("<html><div style='width:320px'>" + escape(message) + "</div></html>");
        pack();
        return showDialog();
    }

    private Supplier readForm() {
        Supplier result = new Supplier();
        result.setSupplierId(supplier.getSupplierId());
        result.setSupplierName(nameField.getText());
        result.setContactPerson(contactField.getText());
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
