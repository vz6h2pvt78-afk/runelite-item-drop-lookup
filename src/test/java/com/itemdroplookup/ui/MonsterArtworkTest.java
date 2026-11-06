package com.itemdroplookup.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MonsterArtworkTest
{
    private static byte[] png(int width, int height) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private static String id(int index) { return String.format("%024x", index); }

    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    private static void await(BooleanSupplier condition) throws Exception
    {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean[] ready = {false};
        while (System.nanoTime() < end)
        {
            edt(() -> ready[0] = condition.getAsBoolean());
            if (ready[0]) return;
            CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(5, TimeUnit.MILLISECONDS)).get(1, TimeUnit.SECONDS);
        }
        fail("Artwork condition did not complete");
    }

    private static final class Fixture
    {
        volatile boolean showing;
        final JPanel body = new JPanel(null);
        final JScrollPane scroll = new JScrollPane() { @Override public boolean isShowing() { return showing; } };
        final List<JLabel> labels = new ArrayList<>();
        final AtomicInteger imageReads = new AtomicInteger();
        final AtomicInteger offEdtReads = new AtomicInteger();
        final List<String> paths = Collections.synchronizedList(new ArrayList<>());
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        volatile boolean block;
        byte[] image;
        final MonsterArtwork artwork;
        final int priorListeners;

        Fixture(int count, int height, byte[] image)
        {
            this.image = image;
            scroll.setViewportView(body);
            body.setPreferredSize(new Dimension(200, count * 50));
            body.setSize(200, count * 50);
            scroll.getViewport().setExtentSize(new Dimension(200, height));
            StringBuilder manifest = new StringBuilder();
            for (int i = 0; i < count; i++) manifest.append("S\tMonster ").append(i).append("\t").append(id(i)).append("\n");
            priorListeners = scroll.getViewport().getChangeListeners().length;
            artwork = new MonsterArtwork(scroll, path -> {
                if (!SwingUtilities.isEventDispatchThread()) offEdtReads.incrementAndGet();
                if (path.endsWith(".tsv")) return new ByteArrayInputStream(manifest.toString().getBytes(StandardCharsets.UTF_8));
                imageReads.incrementAndGet();
                paths.add(path);
                if (block)
                {
                    try { gate.get(5, TimeUnit.SECONDS); }
                    catch (Exception ex) { throw new IOException(ex); }
                }
                return this.image == null ? null : new ByteArrayInputStream(this.image);
            });
            for (int i = 0; i < count; i++)
            {
                JLabel label = new LookupStyle(15).text("Monster " + i, true);
                label.setBounds(0, i * 50, 200, 50);
                body.add(label); labels.add(label); artwork.addSource("Monster " + i, label);
            }
        }
        void show() { showing = true; artwork.requestVisible(); }
    }

    @Test public void exactAndRepresentativeMappingsRemainSeparate()
    {
        Map<String, String> map = MonsterArtwork.loadCatalogue(MonsterArtwork.class::getResourceAsStream);
        assertEquals(1072, map.size());
        assertEquals(map.get("S\tGoblin"), map.get("G\tGoblin"));
        assertNotEquals(map.get("S\tGoblin"), map.get("S\tGoblin (Goblin Village)"));
        assertNotNull(map.get("S\tGuard"));
        assertNull(map.get("G\tGuard"));
        assertNotNull(map.get("S\tMogre (Sailing)"));
        assertNull(map.get("G\tMogre"));
        assertNull(map.get("S\tGoblin raider"));
        assertNull(map.get("S\tShark (Sailing)"));
        assertNull(map.get("S\tWild dog (Last Man Standing)"));
        assertNull(map.get("S\tgoblin"));
    }

    @Test public void everyBundledPortraitDecodesAtItsPreparedSize() throws Exception
    {
        Set<String> assets = new HashSet<>(MonsterArtwork.loadCatalogue(MonsterArtwork.class::getResourceAsStream).values());
        assertEquals(979, assets.size());
        for (String asset : assets)
        {
            try (InputStream stream = MonsterArtwork.class.getResourceAsStream("/artwork/monsters/" + asset + ".png"))
            {
                assertNotNull(asset, stream);
                BufferedImage image = ImageIO.read(stream);
                assertNotNull(asset, image);
                assertEquals(asset, 36, image.getWidth());
                assertEquals(asset, 40, image.getHeight());
            }
        }
    }

    @Test public void groupHoldsDoNotSuppressExactSourcePictures() throws Exception
    {
        JScrollPane[] scroll = {null}; MonsterArtwork[] art = {null};
        JButton[] goblin = {null}, guard = {null}; JLabel[] exact = {null}, held = {null};
        edt(() -> {
            JPanel body = new JPanel(null); body.setSize(220, 250);
            scroll[0] = new JScrollPane() { @Override public boolean isShowing() { return true; } };
            scroll[0].setViewportView(body);
            scroll[0].getViewport().setExtentSize(new Dimension(220, 250));
            art[0] = new MonsterArtwork(scroll[0]);
            goblin[0] = new LookupStyle(15).button("Goblin (4)", () -> { }, false);
            guard[0] = new LookupStyle(15).button("Guard", () -> { }, false);
            exact[0] = new LookupStyle(15).text("Guard", true);
            held[0] = new LookupStyle(15).text("Goblin raider", true);
            JComponent[] rows = {goblin[0], guard[0], exact[0], held[0]};
            for (int i = 0; i < rows.length; i++) { rows[i].setBounds(0, i * 50, 220, 50); body.add(rows[i]); }
            art[0].addGroup("Goblin", goblin[0]); art[0].addGroup("Guard", guard[0]);
            art[0].addSource("Guard", exact[0]); art[0].addSource("Goblin raider", held[0]);
            art[0].requestVisible();
        });
        try
        {
            await(() -> goblin[0].getIcon() instanceof ImageIcon && exact[0].getIcon() instanceof ImageIcon);
            assertNull(guard[0].getIcon()); assertNull(held[0].getIcon());
        }
        finally { edt(art[0]::dispose); }
    }

    @Test public void malformedOrMissingCatalogueFailsClosed()
    {
        for (String raw : new String[] {"S\tGoblin\t../../oops\n",
                "S\tGoblin\t" + id(1) + "\nS\tGoblin\t" + id(2) + "\n",
                "X\tGoblin\t" + id(1), "S\t\t" + id(1), "S\tGoblin\t" + id(1) + "\textra"})
            assertTrue(MonsterArtwork.loadCatalogue(path -> new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))).isEmpty());
        assertTrue(MonsterArtwork.loadCatalogue(path -> null).isEmpty());
        assertTrue(MonsterArtwork.loadCatalogue(path -> { throw new IOException("broken"); }).isEmpty());
    }

    @Test public void onlyVisibleHeadersLoadOffEdtAndPaintingDoesNoWork() throws Exception
    {
        Fixture[] f = {null}; byte[] valid = png(36, 40);
        edt(() -> f[0] = new Fixture(500, 100, valid));
        try
        {
            await(() -> f[0].labels.get(0).getIcon() != null);
            assertEquals(0, f[0].imageReads.get());
            edt(f[0]::show);
            await(() -> f[0].labels.get(1).getIcon() instanceof ImageIcon);
            assertEquals(2, f[0].imageReads.get());
            edt(() -> {
                BufferedImage screen = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = screen.createGraphics(); f[0].body.paint(g); g.dispose();
                f[0].artwork.requestVisible();
            });
            assertEquals(2, f[0].imageReads.get());
            edt(() -> f[0].scroll.getViewport().setViewPosition(new Point(0, 500)));
            await(() -> f[0].labels.get(11).getIcon() instanceof ImageIcon);
            assertEquals(4, f[0].imageReads.get());
            assertEquals(5, f[0].offEdtReads.get());
        }
        finally { edt(f[0].artwork::dispose); }
    }

    @Test public void boundedCacheEvictsOldPortraitButRetainsRecentOne() throws Exception
    {
        Fixture[] f = {null}; byte[] valid = png(36, 40);
        edt(() -> f[0] = new Fixture(97, 50, valid));
        try
        {
            edt(f[0]::show);
            for (int i = 0; i < 97; i++)
            {
                final int row = i;
                edt(() -> f[0].scroll.getViewport().setViewPosition(new Point(0, row * 50)));
                await(() -> f[0].labels.get(row).getIcon() instanceof ImageIcon);
            }
            assertEquals(97, f[0].imageReads.get());
            edt(() -> {
                f[0].artwork.clear();
                f[0].labels.get(0).setIcon(null);
                f[0].artwork.addSource("Monster 0", f[0].labels.get(0));
                f[0].scroll.getViewport().setViewPosition(new Point(0, 0));
            });
            await(() -> f[0].labels.get(0).getIcon() instanceof ImageIcon);
            assertEquals(98, f[0].imageReads.get());
            edt(() -> {
                f[0].artwork.clear();
                f[0].labels.get(96).setIcon(null);
                f[0].artwork.addSource("Monster 96", f[0].labels.get(96));
                f[0].scroll.getViewport().setViewPosition(new Point(0, 96 * 50));
            });
            await(() -> f[0].labels.get(96).getIcon() instanceof ImageIcon);
            assertEquals(98, f[0].imageReads.get());
        }
        finally { edt(f[0].artwork::dispose); }
    }

    @Test public void missingCorruptAndWrongSizeResourcesRemoveTheGutter() throws Exception
    {
        for (byte[] bad : new byte[][] {null, new byte[] {1, 2, 3}, png(27, 39)})
        {
            Fixture[] f = {null};
            edt(() -> f[0] = new Fixture(1, 50, bad));
            try
            {
                await(() -> f[0].labels.get(0).getIcon() != null);
                edt(f[0]::show);
                await(() -> f[0].imageReads.get() == 1 && f[0].labels.get(0).getIcon() == null);
                edt(() -> {
                    f[0].artwork.clear();
                    f[0].artwork.addSource("Monster 0", f[0].labels.get(0));
                    f[0].artwork.requestVisible();
                });
                await(() -> f[0].labels.get(0).getIcon() == null);
                assertEquals("Actual failures are negatively cached", 1, f[0].imageReads.get());
                assertTrue(f[0].labels.get(0).getText().contains("Monster 0"));
            }
            finally { edt(f[0].artwork::dispose); }
        }
    }

    @Test public void navigationAndDisposalRejectLatePortraits() throws Exception
    {
        Fixture[] f = {null}; byte[] valid = png(36, 40);
        edt(() -> { f[0] = new Fixture(2, 50, valid); f[0].block = true; f[0].show(); });
        await(() -> f[0].imageReads.get() == 1);
        edt(() -> {
            f[0].artwork.clear();
            f[0].labels.get(0).setIcon(null);
            f[0].artwork.addSource("Monster 1", f[0].labels.get(1));
            f[0].scroll.getViewport().setViewPosition(new Point(0, 50));
            f[0].gate.complete(null);
        });
        await(() -> f[0].labels.get(1).getIcon() instanceof ImageIcon);
        assertNull(f[0].labels.get(0).getIcon());
        edt(f[0].artwork::dispose);
        assertEquals(f[0].priorListeners, f[0].scroll.getViewport().getChangeListeners().length);
        int reads = f[0].imageReads.get();
        edt(() -> { f[0].artwork.addSource("Monster 0", f[0].labels.get(0)); f[0].artwork.requestVisible(); });
        assertNull(f[0].labels.get(0).getIcon());
        assertEquals(reads, f[0].imageReads.get());
    }

    @Test public void rapidScrollingQueueRecoversToCurrentVisibleHeader() throws Exception
    {
        Fixture[] f = {null}; byte[] valid = png(36, 40);
        edt(() -> { f[0] = new Fixture(200, 50, valid); f[0].block = true; f[0].show(); });
        try
        {
            await(() -> f[0].imageReads.get() == 1);
            edt(() -> {
                for (int i = 1; i < 200; i++)
                    f[0].scroll.getViewport().setViewPosition(new Point(0, i * 50));
            });
            assertEquals(1, f[0].imageReads.get());
            f[0].gate.complete(null);
            await(() -> f[0].labels.get(199).getIcon() instanceof ImageIcon);
            assertTrue("One running, 32 queued, and the recovered visible row", f[0].imageReads.get() <= 34);
        }
        finally { f[0].gate.complete(null); edt(f[0].artwork::dispose); }
    }
}
