package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.model.Customer;
import com.bms.model.CustomerOrder;
import com.bms.service.OrderService;
import com.bms.util.MoneyFormatter;
import com.bms.util.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Read-only list of every order one customer has placed (business rule: customers have an order history).
 * An administrator sees all of the customer's orders; an employee only sees the ones they processed.
 */
public class OrderHistoryDialog extends JDialog {

    private static final String[] COLUMNS = {"Order #", "Date", "Employee", "Total", "Status"};
    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);

    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }
    };
    private final StripedTable table = new StripedTable(model);

    public OrderHistoryDialog(Window owner, Customer customer) {
        super(owner, "Order history - " + customer.getFullName(), ModalityType.APPLICATION_MODAL);

        OrderService orderService = new OrderService();
        List<CustomerOrder> orders = List.of();
        String currency = "R";
        try {
            currency = orderService.getCurrencySymbol();
            orders = orderService.getCustomerHistory(customer.getCustomerId());
        } catch (BmsException e) {
            JOptionPane.showMessageDialog(owner, e.getMessage(), "Order history", JOptionPane.ERROR_MESSAGE);
        }

        long completedCents = 0;
        int completed = 0;
        int cancelled = 0;
        for (CustomerOrder o : orders) {
            model.addRow(new Object[]{(int) o.getOrderId(), o.getOrderDate(), o.getEmployeeName(),
                MoneyFormatter.format(o.getTotalCents(), currency), o.getStatus()});
            if (CustomerOrder.CANCELLED.equals(o.getStatus())) {
                cancelled++;
            } else {
                completed++;
                completedCents += o.getTotalCents();
            }
        }

        String summaryText;
        if (orders.isEmpty()) {
            summaryText = "This customer has no orders" + (Session.isAdmin() ? " yet." : " that you processed.");
        } else {
            summaryText = completed + " completed order(s) worth " + MoneyFormatter.format(completedCents, currency)
                    + (cancelled > 0 ? ", " + cancelled + " cancelled (not counted)" : "")
                    + (Session.isAdmin() ? "" : "  -  showing only orders you processed");
        }
        if (orders.size() >= OrderService.MAX_LIST_ROWS) {
            summaryText = "Only the newest " + OrderService.MAX_LIST_ROWS
                    + " orders are listed, so the totals below are incomplete.  " + summaryText;
        }
        JLabel summary = new JLabel(summaryText);
        summary.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        int[] widths = {70, 150, 190, 110, 100};
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                boolean right = (column == 0 || column == 3);
                setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setBorder(right ? NUMBER_PADDING : BorderFactory.createEmptyBorder(0, 4, 0, 4));
                if (!selected) {
                    Object status = t.getModel().getValueAt(t.convertRowIndexToModel(row), 4);
                    c.setForeground(CustomerOrder.CANCELLED.equals(status) ? Color.GRAY : t.getForeground());
                }
                return c;
            }
        };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JButton close = new JButton("Close");
        close.addActionListener(event -> dispose());
        DialogKeys.escapeCancels(this, close);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        content.add(summary, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);

        setPreferredSize(new Dimension(680, 420));
        pack();
        setLocationRelativeTo(owner);
    }
}
