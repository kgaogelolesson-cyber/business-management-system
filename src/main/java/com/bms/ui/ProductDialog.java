package com.bms.ui;

import com.bms.exception.ValidationException;
import com.bms.model.Product;
import com.bms.model.SupplierOption;
import com.bms.util.InputParser;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * The Add / Edit product form. It turns what was typed into a Product.
 * If a number is typed badly, it explains the problem and stays open.
 * The business rules (name taken, price too high, ...) are checked by ProductService.
 */
public class ProductDialog extends JDialog {

    /** Stands for "no supplier" in the supplier drop-down. */
    private static final SupplierOption NO_SUPPLIER = new SupplierOption(0, "(none)");

    private final JTextField nameField = new JTextField(26);
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JTextArea descriptionArea = new JTextArea(3, 26);
    private final JTextField priceField = new JTextField(12);
    private final JTextField stockField = new JTextField(8);
    private final JTextField reorderField = new JTextField(8);
    private final JComboBox<SupplierOption> supplierBox = new JComboBox<>();
    private final JLabel messageLabel = new JLabel(" ");

    private final Product existing;          // null when adding
    private final String currencySymbol;
    private boolean saveClicked = false;
    private Product result;

    public ProductDialog(Window owner, Product existing, List<String> categories,
                         List<SupplierOption> suppliers, String currencySymbol, int defaultReorderLevel) {
        super(owner, existing == null ? "Add product" : "Edit product", ModalityType.APPLICATION_MODAL);
        this.existing = existing;
        this.currencySymbol = currencySymbol;

        // Category: pick an existing one or type a new one
        categoryBox.setEditable(true);
        categoryBox.addItem("");
        for (String category : categories) {
            categoryBox.addItem(category);
        }

        // Supplier: "(none)" + the active suppliers (+ the product's current supplier if it was deactivated)
        supplierBox.addItem(NO_SUPPLIER);
        for (SupplierOption option : suppliers) {
            supplierBox.addItem(option);
        }
        if (existing != null && existing.getSupplierId() != null && !containsSupplier(existing.getSupplierId())) {
            supplierBox.addItem(new SupplierOption(existing.getSupplierId(),
                    existing.getSupplierName() + " (inactive)"));
        }

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        if (existing == null) {
            stockField.setText("0");
            reorderField.setText(String.valueOf(defaultReorderLevel));
        } else {
            nameField.setText(existing.getProductName());
            categoryBox.setSelectedItem(existing.getCategory() == null ? "" : existing.getCategory());
            descriptionArea.setText(existing.getDescription() == null ? "" : existing.getDescription());
            priceField.setText(BigDecimal.valueOf(existing.getPriceCents(), 2).toPlainString());
            reorderField.setText(String.valueOf(existing.getReorderLevel()));
            selectSupplier(existing.getSupplierId());
        }

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
        addRow(form, c, row++, "Product name *", nameField);
        addRow(form, c, row++, "Category", categoryBox);
        addRow(form, c, row++, "Description", new JScrollPane(descriptionArea));
        addRow(form, c, row++, "Price (" + currencySymbol + ") *", priceField);

        if (existing == null) {
            addRow(form, c, row++, "Starting stock *", stockField);
        } else {
            JLabel stockInfo = new JLabel(existing.getQuantityInStock()
                    + " in stock (changed by orders and stock adjustments)");
            stockInfo.setForeground(Color.GRAY);
            addRow(form, c, row++, "Stock", stockInfo);
        }
        addRow(form, c, row++, "Reorder level *", reorderField);
        addRow(form, c, row++, "Supplier", supplierBox);

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
            try {
                result = readForm();       // stays open if a number is typed badly
                saveClicked = true;
                setVisible(false);
            } catch (ValidationException e) {
                showMessage(e.getMessage());
            }
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

    /** Shows the form and waits. Returns the filled-in product, or null if the user cancelled. */
    public Product showDialog() {
        saveClicked = false;
        result = null;
        setVisible(true);        // blocks until the dialog is hidden
        return saveClicked ? result : null;
    }

    /** Shows the form again after the service refused the data, with the reason on screen. */
    public Product showAgainWithMessage(String message) {
        showMessage(message);
        return showDialog();
    }

    private void showMessage(String message) {
        messageLabel.setText("<html><div style='width:320px'>" + escape(message) + "</div></html>");
        pack();
    }

    private Product readForm() throws ValidationException {
        Product p = new Product();
        p.setProductId(existing == null ? 0 : existing.getProductId());
        p.setProductName(nameField.getText());
        Object category = categoryBox.getEditor().getItem();
        p.setCategory(category == null ? null : category.toString());
        p.setDescription(descriptionArea.getText());
        p.setPriceCents(InputParser.parseMoneyToCents(priceField.getText(), currencySymbol));
        p.setReorderLevel(InputParser.parseWholeNumber(reorderField.getText(), "The reorder level"));
        if (existing == null) {
            p.setQuantityInStock(InputParser.parseWholeNumber(stockField.getText(), "The starting stock"));
        } else {
            p.setQuantityInStock(existing.getQuantityInStock());   // not editable here
        }
        SupplierOption supplier = (SupplierOption) supplierBox.getSelectedItem();
        p.setSupplierId(supplier == null || supplier.supplierId() == 0 ? null : supplier.supplierId());
        return p;
    }

    private boolean containsSupplier(long supplierId) {
        for (int i = 0; i < supplierBox.getItemCount(); i++) {
            if (supplierBox.getItemAt(i).supplierId() == supplierId) {
                return true;
            }
        }
        return false;
    }

    private void selectSupplier(Long supplierId) {
        if (supplierId == null) {
            supplierBox.setSelectedItem(NO_SUPPLIER);
            return;
        }
        for (int i = 0; i < supplierBox.getItemCount(); i++) {
            if (supplierBox.getItemAt(i).supplierId() == supplierId) {
                supplierBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
