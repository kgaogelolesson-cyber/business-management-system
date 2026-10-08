package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.model.ReportResult;
import com.bms.model.ReportResult.ColumnType;
import com.bms.service.ReportService;
import com.bms.service.ReportService.ReportType;
import com.bms.util.MoneyFormatter;
import com.bms.util.ReportExporter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * The Reports tab (administrator only): choose a report, choose the dates (for sales reports),
 * click "Run report", read the table, and optionally save it as a CSV file.
 */
public class ReportPanel extends JPanel {

    private static final Border NUMBER_PADDING = BorderFactory.createEmptyBorder(0, 4, 0, 12);

    private final ReportService reportService = new ReportService();

    private final JComboBox<ReportType> reportBox = new JComboBox<>(ReportType.values());
    private final JTextField fromField = new JTextField(9);
    private final JTextField toField = new JTextField(9);
    private final JLabel summaryLabel = new JLabel("Choose a report and click \"Run report\".");
    private final JLabel countLabel = new JLabel(" ");
    private final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return (column < columnTypes.length && columnTypes[column] != ColumnType.TEXT) ? Long.class : String.class;
        }
    };
    private final StripedTable table = new StripedTable(tableModel);
    private final JButton exportButton = new JButton("Export to CSV");

    private ColumnType[] columnTypes = new ColumnType[0];
    private ReportResult current;          // the report currently on screen (null until one has been run)
    private String currency = "R";

    public ReportPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        try {
            currency = reportService.getCurrencySymbol();
        } catch (BmsException e) {
            showError(e.getMessage());
        }

        LocalDate today = LocalDate.now();
        fromField.setText(today.minusDays(29).toString());   // the last 30 days, today included
        toField.setText(today.toString());

        add(buildTopBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        exportButton.setEnabled(false);
        updateDateFields();
    }

    private JPanel buildTopBar() {
        reportBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value == null ? "" : ((ReportType) value).getLabel());
                return this;
            }
        });
        reportBox.addActionListener(event -> updateDateFields());

        JButton runButton = new JButton("Run report");
        runButton.addActionListener(event -> runReport());
        fromField.addActionListener(event -> runReport());   // Enter key
        toField.addActionListener(event -> runReport());

        JButton thisMonth = new JButton("This month");
        thisMonth.addActionListener(event -> {
            LocalDate today = LocalDate.now();
            fromField.setText(today.withDayOfMonth(1).toString());
            toField.setText(today.toString());
        });
        JButton last30 = new JButton("Last 30 days");
        last30.addActionListener(event -> {
            LocalDate today = LocalDate.now();
            fromField.setText(today.minusDays(29).toString());
            toField.setText(today.toString());
        });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Report:"));
        row1.add(reportBox);
        row1.add(runButton);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("From (yyyy-MM-dd):"));
        row2.add(fromField);
        row2.add(new JLabel("To:"));
        row2.add(toField);
        row2.add(thisMonth);
        row2.add(last30);

        JPanel summary = new JPanel(new BorderLayout());
        summary.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 6));
        summary.add(summaryLabel, BorderLayout.WEST);

        JPanel bar = new JPanel(new BorderLayout());
        JPanel controls = new JPanel(new BorderLayout());
        controls.add(row1, BorderLayout.NORTH);
        controls.add(row2, BorderLayout.CENTER);
        bar.add(controls, BorderLayout.NORTH);
        bar.add(summary, BorderLayout.SOUTH);
        return bar;
    }

    private JPanel buildBottomBar() {
        exportButton.addActionListener(event -> exportCsv());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(exportButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(countLabel, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    /** The date boxes only matter for sales reports. */
    private void updateDateFields() {
        boolean needsDates = ((ReportType) reportBox.getSelectedItem()).needsDates();
        fromField.setEnabled(needsDates);
        toField.setEnabled(needsDates);
    }

    // ---------- running a report ----------

    private void runReport() {
        ReportType type = (ReportType) reportBox.getSelectedItem();
        try {
            LocalDate from = null;
            LocalDate to = null;
            if (type.needsDates()) {
                from = ReportService.parseDate(fromField.getText(), "from");
                to = ReportService.parseDate(toField.getText(), "to");
            }
            show(reportService.run(type, from, to));
        } catch (BmsException e) {
            showError(e.getMessage());
        }
    }

    private void show(ReportResult report) {
        current = report;
        columnTypes = report.getTypes();

        tableModel.setColumnIdentifiers(report.getHeaders());
        tableModel.setRowCount(0);
        for (Object[] row : report.getRows()) {
            tableModel.addRow(row);
        }
        setUpColumns(report);

        summaryLabel.setText(report.getSummary());
        countLabel.setText(report.getRows().size() + " row(s)");
        exportButton.setEnabled(true);
    }

    /** Right-aligns numbers, shows money with the currency symbol (the table still sorts by the real number). */
    private void setUpColumns(ReportResult report) {
        table.setAutoCreateColumnsFromModel(true);
        for (int c = 0; c < table.getColumnCount(); c++) {
            ColumnType type = report.getTypes()[c];
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    if (type == ColumnType.MONEY && value instanceof Long cents) {
                        setText(MoneyFormatter.format(cents, currency));
                    } else {
                        super.setValue(value);
                    }
                }

                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                               boolean focus, int row, int column) {
                    Component comp = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                    setHorizontalAlignment(type == ColumnType.TEXT ? SwingConstants.LEFT : SwingConstants.RIGHT);
                    setBorder(type == ColumnType.TEXT ? BorderFactory.createEmptyBorder(0, 4, 0, 4) : NUMBER_PADDING);
                    return comp;
                }
            };
            table.getColumnModel().getColumn(c).setCellRenderer(renderer);
        }
    }

    // ---------- exporting ----------

    private void exportCsv() {
        if (current == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save report as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV file (*.csv)", "csv"));
        chooser.setSelectedFile(new File(current.getTitle().replaceAll("[^A-Za-z0-9._-]+", "_") + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        if (file.exists()) {
            int answer = JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Replace it?",
                    "Save report", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            ReportExporter.write(current, file.toPath());
            JOptionPane.showMessageDialog(this, "The report was saved to:\n" + file.getAbsolutePath(),
                    "Save report", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            showError("The file could not be saved: " + e.getMessage()
                    + "\nIf it is open in Excel, close it and try again.");
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Reports", JOptionPane.ERROR_MESSAGE);
    }
}
