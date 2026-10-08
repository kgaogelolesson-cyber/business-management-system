package com.bms;

import com.bms.database.DatabaseInitializer;
import com.bms.exception.DatabaseErrors;
import com.bms.ui.LoginFrame;
import com.bms.util.AppErrors;
import com.bms.util.AppLogging;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point of the Business Management System.
 * Start logging and the safety net, prepare the database, then show the login window.
 */
public class Main {

    public static void main(String[] args) {
        AppLogging.start();          // technical details go to logs/bms.0.log
        AppErrors.install();         // unexpected bugs: logged + one friendly message
        useSystemLookAndFeel();

        try {
            DatabaseInitializer.initialise();
        } catch (SQLException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "The database could not be prepared", e);
            JOptionPane.showMessageDialog(null,
                    DatabaseErrors.friendly("The database could not be prepared, so the program cannot start.", e)
                            + "\n\nTechnical details were saved in: " + AppLogging.logFile().getAbsolutePath(),
                    "Business Management System", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Swing windows must be created on the Swing thread.
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    private static void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // If it fails we simply keep Java's default look. Not important.
        }
    }
}
