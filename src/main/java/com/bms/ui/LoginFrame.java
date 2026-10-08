package com.bms.ui;

import com.bms.exception.BmsException;
import com.bms.service.AuthService;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/** The login window. It only collects input and shows messages; the rules live in AuthService. */
public class LoginFrame extends JFrame {

    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JCheckBox showPasswordBox = new JCheckBox("Show password");
    private final JButton loginButton = new JButton("Log in");
    private final JLabel messageLabel = new JLabel(" ", SwingConstants.CENTER);

    private final AuthService authService = new AuthService();

    public LoginFrame() {
        super("Business Management System - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(AppIcon.images());

        buildLayout();

        loginButton.addActionListener(event -> attemptLogin());
        getRootPane().setDefaultButton(loginButton);   // pressing Enter clicks "Log in"

        pack();
        setResizable(false);
        setLocationRelativeTo(null);                   // centre on the screen
    }

    private void buildLayout() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Business Management System", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);

        JLabel subtitle = new JLabel("Please log in to continue", SwingConstants.CENTER);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 6, 18, 6);
        panel.add(subtitle, gbc);

        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridwidth = 1;

        gbc.gridy = 2; gbc.gridx = 0;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        panel.add(usernameField, gbc);

        gbc.gridy = 3; gbc.gridx = 0;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        char defaultEcho = passwordField.getEchoChar();
        showPasswordBox.addActionListener(event ->
                passwordField.setEchoChar(showPasswordBox.isSelected() ? (char) 0 : defaultEcho));
        gbc.gridy = 4; gbc.gridx = 1; gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 6, 6, 6);
        panel.add(showPasswordBox, gbc);

        messageLabel.setForeground(new Color(180, 0, 0));
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(messageLabel, gbc);

        gbc.gridy = 6;
        gbc.insets = new Insets(12, 6, 6, 6);
        panel.add(loginButton, gbc);

        setContentPane(panel);
    }

    private void attemptLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        try {
            authService.login(username, password);

            // Success: open the main window and close this one.
            passwordField.setText("");
            new MainFrame().setVisible(true);
            dispose();

        } catch (BmsException e) {
            // Wrong details, empty fields or a database problem: show the message.
            messageLabel.setText(e.getMessage());
            passwordField.setText("");
            showPasswordBox.setSelected(false);
            passwordField.setEchoChar(new JPasswordField().getEchoChar());
            passwordField.requestFocusInWindow();
        }
    }
}
