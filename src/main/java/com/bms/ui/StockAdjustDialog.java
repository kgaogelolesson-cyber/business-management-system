package com.bms.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The "Adjust stock" form. It only collects what the user typed:
 * add or remove, how many, and why. Checking is the service's job.
 */
public class StockAdjustDialog extends JDialog {

    /** What the user entered. quantityText is still text; the panel turns it into a number. */
    public record Entry(boolean add, String quantityText, String reason) {
    }

    private static final String ADD = "Add stock";
    private static final String REMOVE = "Remove stock";

    private final JComboBox<String> actionBox = new JComboBox<>(new String[]{ADD, REMOVE});
    private final JTextField quantityField = new JTextField(10);
    private final JTextField reasonField = new JTextField(28);
    private final JLabel messageLabel = new JLabel(" ");
    private boolean saveClicked = false;

    public StockAdjustDialog(Window owner, String productName, int currentStock) {
        super(owner, "Adjust stock", ModalityType.APPLICATION_MODAL);

        JLabel name = new JLabel(productName);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
        JLabel stock = new JLabel("Currently in stock: " + currentStock);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 4, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        form.add(name, c);
        c.gridy = 1;
        form.add(stock, c);
        addRow(form, c, 2, "Action", actionBox);
        addRow(form, c, 3, "Quantity *", quantityField);
        addRow(form, c, 4, "Reason *", reasonField);

        messageLabel.setForeground(new Color(180, 0, 0));
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        form.add(messageLabel, c);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
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

    /** Shows the form and waits. Returns what was entered, or null if the user cancelled. */
    public Entry showDialog() {
        saveClicked = false;
        setVisible(true);        // blocks until the dialog is hidden
        return saveClicked
                ? new Entry(ADD.equals(actionBox.getSelectedItem()), quantityField.getText(), reasonField.getText())
                : null;
    }

    /** Shows the form again after the data was refused, with the reason on screen. */
    public Entry showAgainWithMessage(String message) {
        messageLabel.setText("<html><div style='width:300px'>" + escape(message) + "</div></html>");
        pack();
        return showDialog();
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
