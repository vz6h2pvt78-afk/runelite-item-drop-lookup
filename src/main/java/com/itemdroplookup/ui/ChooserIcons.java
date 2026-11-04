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
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.util.function.Consumer;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/** Visible item artwork for the current results page. Never performs lookup work while painting. */
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
    private volatile long pinnedGeneration;
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
        rows.add(new Row(canonicalName, button, button::setIcon));
    }

    void add(String canonicalName, JLabel label)
    {
        label.setIcon(EMPTY);
        label.setIconTextGap(6);
        rows.add(new Row(canonicalName, label, label::setIcon));
    }

    void showPinned(String canonicalName, JLabel label)
    {
        long token = ++pinnedGeneration;
        label.setIcon(EMPTY);
        label.setIconTextGap(6);
        request(canonicalName, () -> !disposed && pinnedGeneration == token, label::setIcon);
    }

    void closePinned()
    {
        pinnedGeneration++;
    }

    void requestVisible()
    {
        if (disposed || !scroll.isShowing() || scroll.getViewport().getView() == null) return;
        Rectangle visible = scroll.getViewport().getViewRect();
        long token = generation;
        BooleanSupplier current = () -> !disposed && generation == token;
        for (Row row : rows)
        {
            if (row.requested || row.component.getParent() == null) continue;
            Rectangle bounds = SwingUtilities.convertRectangle(row.component.getParent(), row.component.getBounds(),
                    scroll.getViewport().getView());
            if (!visible.intersects(bounds) || !SwingUtilities.isDescendingFrom(row.component, scroll.getViewport().getView())) continue;
            row.requested = true;
            request(row.name, current, row.setIcon);
        }
    }

    private void request(String name, BooleanSupplier current, Consumer<Icon> setIcon)
    {
        provider.request(name, current, image -> SwingUtilities.invokeLater(() -> {
            if (current.getAsBoolean() && image != null && image.getWidth() == 36 && image.getHeight() == 32)
                setIcon.accept(new ImageIcon(image));
        }));
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
        private final JComponent component;
        private final Consumer<Icon> setIcon;
        private boolean requested;
        private Row(String name, JComponent component, Consumer<Icon> setIcon)
        {
            this.name = name;
            this.component = component;
            this.setIcon = setIcon;
        }
    }
}
