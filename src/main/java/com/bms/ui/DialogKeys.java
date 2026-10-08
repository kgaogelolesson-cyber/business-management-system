package com.bms.ui;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;

/** Small keyboard helpers shared by the dialogs. */
final class DialogKeys {

    private DialogKeys() {
    }

    /** Pressing Esc does exactly what clicking the Cancel button does. */
    static void escapeCancels(JDialog dialog, JButton cancelButton) {
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "cancel-dialog");
        dialog.getRootPane().getActionMap().put("cancel-dialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelButton.doClick();
            }
        });
    }
}
