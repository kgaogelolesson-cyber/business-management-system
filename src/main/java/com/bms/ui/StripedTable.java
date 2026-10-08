package com.bms.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 * The one table style used everywhere: read-only look, light grey lines, bold header, every second row
 * slightly shaded so a long row is easy to follow across the screen, and an optional double-click action.
 * Why a subclass: the shading is done in prepareRenderer, so it works with every column renderer we already have.
 */
public class StripedTable extends JTable {

    private Runnable doubleClickAction;

    public StripedTable(TableModel model) {
        super(model);
        setGridColor(new Color(215, 215, 215));
        setFillsViewportHeight(true);                 // the shading/grid also fills the empty area
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setFont(getTableHeader().getFont().deriveFont(Font.BOLD));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (doubleClickAction != null && e.getClickCount() == 2 && !e.isConsumed()
                        && rowAtPoint(e.getPoint()) >= 0) {
                    doubleClickAction.run();
                }
            }
        });
    }

    /** What happens when a row is double-clicked. The row is already selected by the first click. */
    public void setDoubleClickAction(Runnable action) {
        this.doubleClickAction = action;
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (!isRowSelected(row) && c instanceof JComponent) {
            c.setBackground(row % 2 == 0 ? getBackground() : stripeColour());
        }
        return c;
    }

    private Color stripeColour() {
        Color alternate = UIManager.getColor("Table.alternateRowColor");
        if (alternate != null) {
            return alternate;
        }
        Color base = getBackground();
        int brightness = (base.getRed() + base.getGreen() + base.getBlue()) / 3;
        int shift = brightness > 128 ? -10 : 14;       // a little darker on light themes, lighter on dark ones
        return new Color(clamp(base.getRed() + shift), clamp(base.getGreen() + shift), clamp(base.getBlue() + shift));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
