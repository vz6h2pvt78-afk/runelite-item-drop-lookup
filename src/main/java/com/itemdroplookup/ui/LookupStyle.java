package com.itemdroplookup.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
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
    private static final Font REGULAR = loadFont();
    private final int size;

    LookupStyle(int size) { this.size = size; }
    Font font() { return REGULAR.deriveFont((float) size); }
    Font heading() { return REGULAR.deriveFont((float) size + 2); }
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
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(32, component.getPreferredSize().height)));
    }

    void refreshTree(Container root)
    {
        for (Component child : root.getComponents())
        {
            if (child instanceof JLabel && ((JLabel)child).getClientProperty("lookupHeading") != null)
                child.setFont(Boolean.TRUE.equals(((JLabel)child).getClientProperty("lookupHeading")) ? heading() : font());
            else if (child instanceof AbstractButton || child instanceof JComboBox || child instanceof JTextField)
                child.setFont(font());
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
        try (InputStream in = LookupStyle.class.getResourceAsStream("/fonts/OpenDyslexic-Regular.otf"))
        {
            if (in == null) throw new IllegalStateException("Missing bundled OpenDyslexic font");
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            // Makes this bundled family resolvable by Swing's HTML renderer; does
            // not install a system font or change any RuneLite component defaults.
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        }
        catch (Exception ex) { throw new IllegalStateException("Cannot load bundled OpenDyslexic", ex); }
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
        view.setSize(Math.max(1, width - insets.left - insets.right), 0);
        return new Dimension(width, (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom);
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
