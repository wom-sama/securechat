package com.securechat.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

public class ImageUtils {
    // Tạo Avatar hình tròn
    public static Icon createCircularAvatar(byte[] imageBytes, int size) {
        if (imageBytes == null) return createDefaultAvatar(size);
        try {
            ImageIcon icon = new ImageIcon(imageBytes);
            Image img = icon.getImage();
            
            BufferedImage avatar = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = avatar.createGraphics();
            
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setClip(new Ellipse2D.Float(0, 0, size, size));
            g2.drawImage(img, 0, 0, size, size, null);
            g2.dispose();
            
            return new ImageIcon(avatar);
        } catch (Exception e) {
            return createDefaultAvatar(size);
        }
    }

    private static Icon createDefaultAvatar(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(200, 200, 200));
        g2.fillOval(0, 0, size, size);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, size / 2));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("?", (size - fm.stringWidth("?")) / 2, (size + fm.getAscent()) / 2 - 2);
        g2.dispose();
        return new ImageIcon(img);
    }
}