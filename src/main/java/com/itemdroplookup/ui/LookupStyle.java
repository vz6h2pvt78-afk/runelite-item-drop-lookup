package com.itemdroplookup.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.RenderingHints;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.io.InputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

/** Typography and wrapping local to Item Drop Lookup; never changes UIManager defaults. */
final class LookupStyle
{
    static final Color BACK = new Color(29, 33, 38);
    static final Color CARD = new Color(41, 47, 54);
    static final Color TEXT = new Color(239, 240, 232);
    static final Color MUTED = new Color(184, 196, 203);
    static final Color ACCENT = new Color(222, 193, 126);
    static final Color BORDER = new Color(128, 144, 153);
    static final int STANDARD_SIZE = 15;
    static final int LARGER_SIZE = 18;
    private static final Font REGULAR = loadFont();
    private static final Icon BACK_ARROW = new Icon()
    {
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.getForeground());
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x + 3, y + 8, x + 14, y + 8);
            g.drawLine(x + 3, y + 8, x + 8, y + 3);
            g.drawLine(x + 3, y + 8, x + 8, y + 13);
            g.dispose();
        }
    };
    private final int size;

    LookupStyle(int size) { this.size = size; }
    Font font() { return REGULAR.deriveFont((float) size); }
    Font heading() { return REGULAR.deriveFont(Font.BOLD, (float) size + 2); }
    static String fontName() { return REGULAR.getFontName(); }

    JLabel text(String value, boolean heading)
    {
        JLabel label = new WrappedLabel("<html>" + escape(value).replace("\n", "<br>") + "</html>");
        label.setFont(heading ? heading() : font());
        label.putClientProperty("lookupHeading", heading);
        label.setForeground(heading ? TEXT : MUTED);
        label.setBorder(new EmptyBorder(3, 0, 4, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    JButton button(String title, Runnable action, boolean primary)
    {
        JButton button = new WrappedButton("<html>" + escape(title).replace("\n", "<br>") + "</html>");
        button.getAccessibleContext().setAccessibleName(title);
        button.setFont(font());
        button.setForeground(primary ? BACK : TEXT);
        button.setBackground(primary ? ACCENT : CARD);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? ACCENT : BORDER), new EmptyBorder(7, 8, 7, 8)));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    JButton backButton(String title, Runnable action)
    {
        JButton button = button(title, action, false);
        button.setIcon(BACK_ARROW);
        button.setIconTextGap(8);
        return button;
    }

    static String encounterDanger(String levelText)
    {
        return "WILDERNESS " + levelText + "\nPvP danger";
    }

    JLabel wildernessWarning()
    {
        return wildernessWarning("WILDERNESS\nPvP danger");
    }

    JLabel wildernessWarning(String message)
    {
        JLabel label = text(message, true);
        label.setName("wildernessWarning");
        label.setForeground(new Color(255, 192, 155));
        label.setIcon(new Icon()
        {
            public int getIconWidth() { return 20; }
            public int getIconHeight() { return 22; }
            public void paintIcon(Component c, Graphics graphics, int x, int y)
            {
                // Original vector skull: no external asset or font-glyph dependency.
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c.getForeground());
                g.fillOval(x + 1, y + 2, 18, 15);
                g.fillRect(x + 5, y + 13, 10, 7);
                g.setColor(CARD);
                g.fillOval(x + 4, y + 7, 4, 5);
                g.fillOval(x + 12, y + 7, 4, 5);
                g.drawLine(x + 8, y + 16, x + 8, y + 20);
                g.drawLine(x + 12, y + 16, x + 12, y + 20);
                g.dispose();
            }
        });
        label.setIconTextGap(8);
        label.getAccessibleContext().setAccessibleName(message.replace('\n', ' '));
        return label;
    }

    static javax.swing.border.Border cardBorder()
    {
        return BorderFactory.createCompoundBorder(new EmptyBorder(4, 0, 8, 0),
                BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(8, 8, 8, 8)));
    }

    void control(JComponent component)
    {
        component.setFont(font());
        component.setForeground(TEXT);
        component.setBackground(CARD);
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (component instanceof JTextField)
        {
            ((JTextField) component).setCaretColor(TEXT);
            component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(6, 6, 6, 6)));
        }
        controlFont(component);
    }

    void controlFont(JComponent component)
    {
        component.setFont(font());
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(32, component.getPreferredSize().height)));
    }

    void refreshTree(Container root)
    {
        for (Component child : root.getComponents())
        {
            if (child instanceof JLabel && ((JLabel)child).getClientProperty("lookupHeading") != null)
                child.setFont(Boolean.TRUE.equals(((JLabel)child).getClientProperty("lookupHeading")) ? heading() : font());
            else if (child instanceof AbstractButton || child instanceof JComboBox || child instanceof JTextField)
                controlFont((JComponent) child);
            if (child instanceof Container) refreshTree((Container)child);
        }
    }

    static JPanel column()
    {
        JPanel panel = new JPanel()
        {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACK);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    static JPanel scrollBody(JPanel content)
    {
        JPanel body = new ViewportBody();
        body.setBackground(BACK);
        body.add(content, java.awt.BorderLayout.NORTH);
        return body;
    }

    private static final class ViewportBody extends JPanel implements Scrollable
    {
        ViewportBody() { super(new java.awt.BorderLayout()); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 28; }
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return Math.max(28, visible.height - 28); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static String escape(String value)
    {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Font loadFont()
    {
        try (InputStream in = LookupStyle.class.getResourceAsStream("/fonts/AtkinsonHyperlegible-Regular.ttf"))
        {
            if (in == null) throw new IllegalStateException("Missing bundled Atkinson Hyperlegible font");
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            // Makes this bundled family resolvable by Swing's HTML renderer; does
            // not install a system font or change any RuneLite component defaults.
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        }
        catch (Exception ex) { throw new IllegalStateException("Cannot load bundled Atkinson Hyperlegible", ex); }
    }

    private static int availableWidth(JComponent component)
    {
        Container parent = component.getParent();
        int width = component.getWidth();
        if (parent != null && parent.getWidth() > 0)
        {
            Insets insets = parent.getInsets();
            width = parent.getWidth() - insets.left - insets.right;
        }
        return Math.max(30, width > 0 ? width : 190);
    }

    private static Dimension wrappedSize(JComponent component, Dimension fallback)
    {
        View view = (View) component.getClientProperty(BasicHTML.propertyKey);
        if (view == null) return fallback;
        Insets insets = component.getInsets();
        int width = availableWidth(component);
        Icon icon = component instanceof AbstractButton ? ((AbstractButton) component).getIcon()
                : component instanceof JLabel ? ((JLabel) component).getIcon() : null;
        int iconSpace = icon == null ? 0 : icon.getIconWidth() + (component instanceof AbstractButton
                ? ((AbstractButton) component).getIconTextGap() : ((JLabel) component).getIconTextGap());
        view.setSize(Math.max(1, width - insets.left - insets.right - iconSpace), 0);
        int height = Math.max((int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)), icon == null ? 0 : icon.getIconHeight());
        return new Dimension(width, height + insets.top + insets.bottom);
    }

    private static final class WrappedLabel extends JLabel
    {
        WrappedLabel(String text) { super(text); }
        @Override public Dimension getPreferredSize() { return wrappedSize(this, super.getPreferredSize()); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public void setBounds(int x, int y, int w, int h)
        {
            boolean resized = getWidth() != w;
            super.setBounds(x, y, w, h);
            if (resized) revalidate();
        }
    }

    private static final class WrappedButton extends JButton
    {
        WrappedButton(String text) { super(text); }
        @Override public Dimension getPreferredSize() { return wrappedSize(this, super.getPreferredSize()); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, 32); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, Math.max(32, getPreferredSize().height)); }
        @Override public void setBounds(int x, int y, int w, int h)
        {
            boolean resized = getWidth() != w;
            super.setBounds(x, y, w, h);
            if (resized) revalidate();
        }
    }
}
