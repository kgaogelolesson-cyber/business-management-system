package com.bms.ui;

import com.bms.model.AppModule;
import com.bms.model.Employee;
import com.bms.model.User;
import com.bms.service.AccessControl;
import com.bms.util.Session;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * The main window: a header (who is signed in + Log out), one tab per module the user's role may open.
 * A tab only appears when (1) the role may use the module AND (2) the module has been built.
 * Opening a tab reloads its data, so what you see is never older than your last click.
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        super("Business Management System");

        if (!Session.isLoggedIn()) {
            throw new IllegalStateException("MainFrame opened without a logged-in user.");
        }

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(AppIcon.images());
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);

        setMinimumSize(new Dimension(1000, 600));
        setPreferredSize(new Dimension(1200, 720));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        User user = Session.getCurrentUser();
        Employee employee = Session.getCurrentEmployee();
        String displayName = (employee != null) ? employee.getFullName() : user.getUsername();

        JLabel welcome = new JLabel("Welcome, " + displayName);
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 20f));

        JLabel signedInAs = new JLabel("Signed in as: " + user.getUsername()
                + "  |  Role: " + user.getRole().getDisplayName());

        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(welcome, BorderLayout.NORTH);
        text.add(signedInAs, BorderLayout.SOUTH);

        JButton logoutButton = new JButton("Log out");
        logoutButton.setToolTipText("Sign out and return to the login screen");
        logoutButton.addActionListener(event -> logout());
        JPanel logoutHolder = new JPanel(new BorderLayout());
        logoutHolder.setOpaque(false);
        logoutHolder.add(logoutButton, BorderLayout.NORTH);

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 8, 20));
        header.add(text, BorderLayout.CENTER);
        header.add(logoutHolder, BorderLayout.EAST);
        return header;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        for (AppModule module : AccessControl.getModulesFor(Session.getCurrentUser().getRole())) {
            JComponent panel = createPanelFor(module);
            if (panel != null) {
                tabs.addTab(module.getDisplayName(), panel);
            }
        }
        // Reload the data of a tab each time it is opened (every panel loaded itself once when it was created).
        tabs.addChangeListener(event -> {
            Component selected = tabs.getSelectedComponent();
            if (selected instanceof Refreshable refreshable) {
                refreshable.refresh();
            }
        });
        return tabs;
    }

    /** Returns the screen for a module, or null if that module is not built yet. */
    private JComponent createPanelFor(AppModule module) {
        return switch (module) {
            case DASHBOARD -> new DashboardPanel();
            case CUSTOMERS -> new CustomerPanel();
            case PRODUCTS -> new ProductPanel();
            case SUPPLIERS -> new SupplierPanel();
            case EMPLOYEES -> new EmployeePanel();
            case INVENTORY -> new InventoryPanel();
            case ORDERS -> new OrderPanel();
            case REPORTS -> new ReportPanel();
            default -> null;   // not built yet
        };
    }

    private void logout() {
        Session.end();                       // forget the user
        dispose();                           // close this window
        new LoginFrame().setVisible(true);   // back to the login screen
    }
}
