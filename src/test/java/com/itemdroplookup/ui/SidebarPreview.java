package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.AccessRequirementLookupService;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Offline Swing layout preview. Does not launch or interact with RuneScape. */
public class SidebarPreview
{
    public static void main(String[] args) throws Exception
    {
        File directory = new File(args.length == 0 ? "build/previews" : args[0]);
        if (!directory.exists() && !directory.mkdirs())
        {
            throw new IllegalStateException("Cannot create preview directory");
        }
        SwingUtilities.invokeAndWait(() -> {
            UIManager.put("Label.foreground", Color.WHITE);
            UIManager.put("CheckBox.foreground", Color.WHITE);
            UIManager.put("Panel.background", ColorScheme.DARK_GRAY_COLOR);
            Gson gson = new Gson();
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson);
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson),
                    new ItemSourceLookupService(gson), new ItemPriceLookupService(gson), locations, new AccessRequirementLookupService(gson));
            ((JTextField) find(panel, "itemSearch")).setText("rune scimitar");
            ((JButton) find(panel, "searchButton")).doClick();
            PanelTestSupport.openSources(panel);
            ((JComboBox<?>) find(panel, "npcWildernessFilter")).setSelectedItem(WildernessFilter.ONLY);
            render(panel.getWrappedPanel(), directory, "search-wilderness.png");
            renderDetail(new DropSource("Rune scimitar", "Fire giant", "1", "1/128", ""), locations, directory, "fire-giant.png");
            renderDetail(new DropSource("Goblin mail", "Goblin", "1", "5/128", ""), locations, directory, "goblin.png");
            renderDetail(new DropSource("Dragon pickaxe", "King Black Dragon", "1", "1/1000", ""), locations, directory, "safe-lair.png");
            renderDetail(new DropSource("Item", "Unknown monster", "1", "1/2", ""), locations, directory, "missing-locations.png");
            ((JComboBox<?>) find(panel, "npcWildernessFilter")).setSelectedItem(WildernessFilter.ALL);
            for (String query : new String[] {"Lyre", "Raw bear meat", "Spade", "Iron ore", "uncut", "runepouch", "prayer potion", "coins", "notanitemxyz"})
            {
                ((JTextField) find(panel, "itemSearch")).setText(query);
                long started = System.nanoTime();
                ((JButton) find(panel, "searchButton")).doClick();
                long searchMillis = (System.nanoTime() - started) / 1_000_000;
                render(panel.getWrappedPanel(), directory, query.replace(' ', '-') + ".png");
                if (find(panel, "viewAllSources") != null)
                {
                    PanelTestSupport.openSources(panel);
                    render(panel.getWrappedPanel(), directory, query.replace(' ', '-') + "-sources.png");
                    ((JComboBox<?>) find(panel, "textSize")).setSelectedItem("Larger text");
                    render(panel.getWrappedPanel(), directory, query.replace(' ', '-') + "-large.png");
                    ((JComboBox<?>) find(panel, "textSize")).setSelectedItem("Standard text");
                }
                System.out.println("Search/render scenario: " + query + "; search=" + searchMillis + "ms");
            }

        });
        System.out.println("Offline previews written to " + directory.getAbsolutePath());
    }

    private static void renderDetail(DropSource source, MonsterLocationLookupService service, File directory, String name)
    {
        JPanel detail = new MonsterLocationPanel(source, source.getItemName(), service, new AccessRequirementLookupService(new Gson()), WildernessFilter.ALL, () -> { });
        PluginPanel container = new PluginPanel(false) { };
        container.setLayout(new BorderLayout());
        container.setBorder(new EmptyBorder(10, 10, 10, 10));
        container.add(detail, BorderLayout.CENTER);
        render(container.getWrappedPanel(), directory, name);
    }

    private static Component find(Container root, String name)
    {
        for (Component child : root.getComponents())
        {
            if (name.equals(child.getName()))
            {
                return child;
            }
            if (child instanceof Container)
            {
                Component found = find((Container) child, name);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private static void layout(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents())
        {
            if (child instanceof Container)
            {
                layout((Container) child);
            }
        }
    }

    private static void render(JComponent component, File directory, String name)
    {
        component.setSize(new Dimension(242, 535));
        for (int i = 0; i < 8; i++)
        {
            layout(component);
        }
        BufferedImage image = new BufferedImage(242, 535, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        component.printAll(graphics);
        graphics.dispose();
        try
        {
            ImageIO.write(image, "png", new File(directory, name));
        }
        catch (java.io.IOException ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
