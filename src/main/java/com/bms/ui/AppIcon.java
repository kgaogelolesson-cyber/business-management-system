package com.bms.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** The window icon, drawn in code (a blue rounded square with a white "B") so no image file is needed. */
public final class AppIcon {

    private AppIcon() {
    }

    /** Several sizes, so Windows can pick a sharp one for the title bar and the taskbar. */
    public static List<Image> images() {
        return List.of(draw(16), draw(32), draw(64));
    }

    private static Image draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(31, 91, 168));
        g.fillRoundRect(0, 0, size, size, size / 4, size / 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (size * 0.7)));
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth("B")) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString("B", x, y);
        g.dispose();
        return image;
    }
}
