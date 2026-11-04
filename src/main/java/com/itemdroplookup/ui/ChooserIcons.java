package com.itemdroplookup.ui;

import com.itemdroplookup.service.ItemIconProvider;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/** One chooser's visible rows. Never performs lookup work while painting. */
final class ChooserIcons
{
    private static final Icon EMPTY = new Icon()
    {
        public int getIconWidth() { return 36; }
        public int getIconHeight() { return 32; }
        public void paintIcon(Component c, Graphics g, int x, int y) { }
    };
    private final JScrollPane scroll;
    private final ItemIconProvider provider;
    private final List<Row> rows = new ArrayList<>();
    private final ChangeListener viewportChanged = e -> requestVisible();
    private final HierarchyListener visibilityChanged = e -> {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) requestVisible();
    };
    private volatile long generation;
    private volatile boolean disposed;

    ChooserIcons(JScrollPane scroll, ItemIconProvider provider)
    {
        this.scroll = scroll;
        this.provider = provider;
        scroll.getViewport().addChangeListener(viewportChanged);
        scroll.addHierarchyListener(visibilityChanged);
    }

    void clear()
    {
        generation++;
        rows.clear();
    }

    void add(String canonicalName, JButton button)
    {
        button.setIcon(EMPTY);
        button.setIconTextGap(8);
        rows.add(new Row(canonicalName, button));
    }

    void requestVisible()
    {
        if (disposed || !scroll.isShowing() || scroll.getViewport().getView() == null) return;
        Rectangle visible = scroll.getViewport().getViewRect();
        long token = generation;
        BooleanSupplier current = () -> !disposed && generation == token;
        for (Row row : rows)
        {
            if (row.requested || row.button.getParent() == null) continue;
            Rectangle bounds = SwingUtilities.convertRectangle(row.button.getParent(), row.button.getBounds(),
                    scroll.getViewport().getView());
            if (!visible.intersects(bounds)) continue;
            row.requested = true;
            provider.request(row.name, current, image -> SwingUtilities.invokeLater(() -> {
                if (!current.getAsBoolean() || image == null || image.getWidth() != 36 || image.getHeight() != 32) return;
                row.button.setIcon(new ImageIcon(image));
                row.button.repaint();
            }));
        }
    }

    void dispose()
    {
        disposed = true;
        Runnable cleanup = () -> {
            clear();
            scroll.getViewport().removeChangeListener(viewportChanged);
            scroll.removeHierarchyListener(visibilityChanged);
        };
        if (SwingUtilities.isEventDispatchThread()) cleanup.run();
        else SwingUtilities.invokeLater(cleanup);
    }

    private static final class Row
    {
        private final String name;
        private final JButton button;
        private boolean requested;
        private Row(String name, JButton button) { this.name = name; this.button = button; }
    }
}
