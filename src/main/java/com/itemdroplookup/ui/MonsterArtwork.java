package com.itemdroplookup.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import lombok.extern.slf4j.Slf4j;

/** Reviewed, decorative portraits. No network, game state, matching, or work during painting. */
@Slf4j
final class MonsterArtwork
{
    private static final int CACHE_LIMIT = 96;
    private static final Icon EMPTY = new Icon()
    {
        public int getIconWidth() { return 36; }
        public int getIconHeight() { return 40; }
        public void paintIcon(Component c, Graphics g, int x, int y) { }
    };

    interface Resources { InputStream open(String path) throws IOException; }

    private final JScrollPane scroll;
    private final Resources resources;
    private final ThreadPoolExecutor worker;
    private final List<Row> rows = new ArrayList<>();
    // Accessed only by the single worker. Failed assets share the same bounded cache.
    private final Map<String, Optional<Icon>> cache = new LinkedHashMap<String, Optional<Icon>>(128, .75f, true)
    {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Optional<Icon>> entry)
        {
            return size() > CACHE_LIMIT;
        }
    };
    private final ChangeListener viewportChanged = e -> requestVisible();
    private final HierarchyListener visibilityChanged = e -> {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) requestVisible();
    };
    // The catalogue and rows belong to the EDT. Tokens are also read by the worker.
    private Map<String, String> catalogue;
    private volatile long generation;
    private volatile boolean disposed;

    MonsterArtwork(JScrollPane scroll)
    {
        this(scroll, MonsterArtwork.class::getResourceAsStream);
    }

    MonsterArtwork(JScrollPane scroll, Resources resources)
    {
        this.scroll = scroll;
        this.resources = resources;
        worker = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread thread = new Thread(runnable, "item-drop-lookup-artwork");
            thread.setDaemon(true);
            return thread;
        });
        scroll.getViewport().addChangeListener(viewportChanged);
        scroll.addHierarchyListener(visibilityChanged);
        worker.execute(() -> {
            Map<String, String> loaded = loadCatalogue(resources);
            SwingUtilities.invokeLater(() -> {
                if (disposed) return;
                catalogue = loaded;
                rows.forEach(this::reserve);
                requestVisible();
            });
        });
    }

    void addSource(String exactSource, JLabel label)
    {
        label.setIconTextGap(8);
        add("S\t" + exactSource, label, label::setIcon);
    }

    void addGroup(String group, JButton button)
    {
        button.setIconTextGap(8);
        add("G\t" + group, button, button::setIcon);
    }

    private void add(String key, JComponent component, Consumer<Icon> setter)
    {
        if (disposed) return;
        Row row = new Row(key, component, setter);
        rows.add(row);
        reserve(row);
    }

    private void reserve(Row row)
    {
        if (catalogue != null && catalogue.containsKey(row.key)) row.setter.accept(EMPTY);
    }

    void clear()
    {
        generation++;
        rows.clear();
    }

    void requestVisible()
    {
        if (disposed || catalogue == null || !scroll.isShowing() || scroll.getViewport().getView() == null) return;
        Rectangle visible = scroll.getViewport().getViewRect();
        long token = generation;
        for (Row row : rows)
        {
            String asset = catalogue.get(row.key);
            if (asset == null || row.requested || row.component.getParent() == null
                    || !row.component.isVisible()
                    || !SwingUtilities.isDescendingFrom(row.component, scroll.getViewport().getView())) continue;
            Rectangle bounds = SwingUtilities.convertRectangle(row.component.getParent(), row.component.getBounds(),
                    scroll.getViewport().getView());
            if (!visible.intersects(bounds)) continue;
            row.requested = true;
            try
            {
                worker.execute(() -> {
                    Icon icon = !disposed && generation == token ? loadAsset(asset) : null;
                    SwingUtilities.invokeLater(() -> {
                        if (!disposed && generation == token) row.setter.accept(icon);
                        // Also retries a visible row that previously met a full queue.
                        requestVisible();
                    });
                });
            }
            catch (RejectedExecutionException ex)
            {
                row.requested = false;
                break;
            }
        }
    }

    private Icon loadAsset(String asset)
    {
        Optional<Icon> hit = cache.get(asset);
        if (hit != null) return hit.orElse(null);
        Icon icon = null;
        try (InputStream stream = resources.open("/artwork/monsters/" + asset + ".png"))
        {
            if (stream != null)
            {
                BufferedImage image = ImageIO.read(stream);
                if (image != null && image.getWidth() == 36 && image.getHeight() == 40)
                    icon = new ImageIcon(image, "Decorative monster portrait");
            }
        }
        catch (IOException | RuntimeException ex)
        {
            log.debug("Optional monster artwork unavailable: {}", asset, ex);
        }
        cache.put(asset, Optional.ofNullable(icon));
        return icon;
    }

    static Map<String, String> loadCatalogue(Resources resources)
    {
        Map<String, String> result = new HashMap<>();
        try (InputStream stream = resources.open("/artwork/monsters.tsv"))
        {
            if (stream == null) return Collections.emptyMap();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null)
                {
                    if (++count > 2048 || line.length() > 512) return Collections.emptyMap();
                    if (line.startsWith("#")) continue;
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 3 || !fields[0].matches("[SG]") || fields[1].isEmpty()
                            || fields[1].chars().anyMatch(Character::isISOControl)
                            || !fields[2].matches("[a-f0-9]{24}")
                            || result.putIfAbsent(fields[0] + "\t" + fields[1], fields[2]) != null)
                        return Collections.emptyMap();
                }
            }
        }
        catch (IOException | RuntimeException ex)
        {
            log.debug("Optional monster artwork catalogue unavailable", ex);
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(result);
    }

    void dispose()
    {
        disposed = true;
        generation++;
        worker.shutdownNow();
        Runnable cleanup = () -> {
            rows.clear();
            scroll.getViewport().removeChangeListener(viewportChanged);
            scroll.removeHierarchyListener(visibilityChanged);
        };
        if (SwingUtilities.isEventDispatchThread()) cleanup.run();
        else SwingUtilities.invokeLater(cleanup);
    }

    private static final class Row
    {
        private final String key;
        private final JComponent component;
        private final Consumer<Icon> setter;
        private boolean requested;
        private Row(String key, JComponent component, Consumer<Icon> setter)
        {
            this.key = key;
            this.component = component;
            this.setter = setter;
        }
    }
}
