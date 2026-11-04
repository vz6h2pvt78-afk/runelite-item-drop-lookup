package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Offline rendering of the real event cards; no game client, credentials or input. */
public final class AcquisitionPreview
{
    public static void main(String[] args) throws Exception
    {
        File output = new File(args[0]);
        if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("Cannot create preview directory");
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
            for (String[] scenario : new String[][]{{"Magic logs", "Gathering", ""}, {"Yew logs", "Gathering", ""},
                    {"Logs", "Gathering", ""}, {"Amulet of glory", "Loot & containers", ""},
                    {"Mystic robe bottom", "Loot & containers", ""}, {"Snapdragon seed", "Loot & containers", ""},
                    {"Dragon arrow", "Loot & containers", ""}, {"Dragon dagger(p++)", "Loot & containers", ""},
                    {"Adamant axe", "NPC Drops", "Dust devil"}, {"Adamant boots", "NPC Drops", "Gargoyle"},
                    {"Adamant sq shield", "NPC Drops", "Night beast"}, {"Dragonstone", "NPC Drops", "Zulrah"},
                    {"Air rune", "Shops", "pack"}, {"Death talisman", "Reward Shops", "Temple Supplies"},
                    {"Spade", "Ground pickups", ""}, {"Iron ore", "Gathering", ""}, {"Oak logs", "Gathering", ""}})
            {
                JTextField search = (JTextField) PanelTestSupport.find(panel, "itemSearch");
                search.setText(scenario[0]);
                search.postActionEvent();
                for (String size : new String[]{"Standard text", "Larger text"})
                {
                    ((JComboBox<?>) PanelTestSupport.find(panel, "textSize")).setSelectedItem(size);
                    PanelTestSupport.openSources(panel);
                    ((JComboBox<?>) PanelTestSupport.find(panel, "sourceTypeFilter")).setSelectedItem(scenario[1]);
                    JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
                    finder.setText(scenario[2]);
                    finder.postActionEvent();
                    JComponent wrapped = panel.getWrappedPanel();
                    wrapped.setSize(242, 650);
                    for (int i = 0; i < 10; i++) layout(wrapped);
                    String name = scenario[0].replace(' ', '-') + "-" + size.replace(' ', '-');
                    save(wrapped, new File(output, name + "-sidebar.png"));
                    JComponent card = (JComponent) PanelTestSupport.find(panel, "itemSourceCard");
                    if (card == null) card = (JComponent) PanelTestSupport.find(panel, "dropSourceCard");
                    checkBounds(card);
                    save(card, new File(output, name + "-card.png"));
                    System.out.println(name + ": card " + card.getWidth() + "x" + card.getHeight() + "; bounds OK");
                }
            }
        });
    }

    static void checkBounds(Container parent)
    {
        for (Component child : parent.getComponents())
        {
            if (child.getX() < 0 || child.getX() + child.getWidth() > parent.getWidth()
                    || child.getY() < 0 || child.getY() + child.getHeight() > parent.getHeight())
                throw new IllegalStateException("Child escapes card: " + child.getName());
            if (child instanceof JLabel && child.getHeight() < child.getPreferredSize().height)
                throw new IllegalStateException("Clipped text: " + ((JLabel) child).getText());
            if (child instanceof Container) checkBounds((Container) child);
        }
    }

    static void layout(Container parent)
    {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    static void save(JComponent component, File path)
    {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        component.printAll(graphics);
        graphics.dispose();
        try { ImageIO.write(image, "png", path); }
        catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
}
