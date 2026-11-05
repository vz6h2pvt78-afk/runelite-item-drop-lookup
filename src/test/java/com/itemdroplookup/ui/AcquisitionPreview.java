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
                    {"Occult necklace", "NPC Drops", "Smoke devil", "93 Slayer to harm"}, {"Granite legs", "NPC Drops", "Skeletal Wyvern", "icy breath"},
                    {"Mystic boots (light)", "NPC Drops", "Cockatrice", "piercing gaze"}, {"Drake\u0027s tooth", "NPC Drops", "Drake", "84 Slayer to kill"},
                    {"Drake\u0027s claw", "NPC Drops", "Drake", "On a Drake Slayer task"},
                    {"Brine sabre", "NPC Drops", "Brine rat", "47 Slayer to fight"},
                    {"Dragon defender", "NPC Drops", "Rune defender"},
                    {"Butterfly ray fin", "NPC Drops", "Butterfly ray", "40 Sailing"}, {"Narwhal tusk", "NPC Drops", "Narwhal", "62 Sailing"},
                    {"Chocolate cake", "NPC Drops", "Kamil (Nightmare Zone)", "Keeping the cake"},
                    {"Warrior guild token", "NPC Drops", "Animated Adamant Armour", "magical animator"},
                    {"Black mask (10)", "NPC Drops", "Cave horror", "witchwood icon", "70e74913efa330ff38bc"},
                    {"Rune boots", "NPC Drops", "Nechryael", "80 Slayer to harm"}, {"Dragon boots", "NPC Drops", "Spiritual mage", "83 Slayer to kill"},
                    {"Bull shark jaw", "NPC Drops", "Bull shark", "applicable bounty task"},
                    {"Hammerhead shark liver", "NPC Drops", "Hammerhead shark", "40 Sailing"},
                    {"Kurask head", "NPC Drops", "Kurask", "Burn damage"}, {"Adamant platelegs", "NPC Drops", "Aberrant spectre", "nose peg"},
                    {"Basilisk head", "NPC Drops", "Basilisk", "mirror shield"}, {"Mystic robe bottom (light)", "NPC Drops", "Turoth", "ordinary attacks"},
                    {"Mystic gloves (light)", "NPC Drops", "Rockslug", "Finishing"}, {"Mystic gloves (dark)", "NPC Drops", "Banshee", "Protection"},
                    {"Adamant axe", "NPC Drops", "Dust devil"}, {"Adamant boots", "NPC Drops", "Gargoyle"},
                    {"Adamant sq shield", "NPC Drops", "Night beast"}, {"Dragonstone", "NPC Drops", "Zulrah"},
                    {"Cape pouch", "Reward Shops", "Forestry Shop"}, {"Broad bolts (x250)", "Reward Shops", "Shilo Village"},
                    {"Broad arrows", "Reward Shops", "250"},
                    {"Shrimps", "Production", "Cooking range"}, {"Bronze bar", "Production", ""}, {"Crystal shard", "Loot & containers", "Crystal impling"}, {"Lucky impling jar", "Gathering", ""},
                    {"Mithril ore", "Loot & containers", "Earth impling"}, {"Black dragonhide", "Loot & containers", "Magpie impling"},
                    {"Prayer potion", "NPC Drops", "Tormented Demon"}, {"Earth rune", "Shops", "Ali\u0027s Discount Wares"},
                    {"Air rune", "Shops", "pack"}, {"Death talisman", "Reward Shops", "Temple Supplies"},
                    {"Spade", "Ground pickups", ""}, {"Iron ore", "Gathering", ""}, {"Oak logs", "Gathering", ""},
                    {"Blighted super restore(4) (x4)", "Reward Shops", "Soul Wars"}, {"Soul cape", "Reward Shops", "Soul Wars"}})
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
                    if (scenario.length >= 4)
                    {
                        Component family = PanelTestSupport.find(panel, "sourceGroup:" + scenario[2]);
                        if (family instanceof JButton && ((JButton) family).getText().contains("▸")) ((JButton) family).doClick(0);
                    }
                    JComponent wrapped = panel.getWrappedPanel();
                    wrapped.setSize(242, 650);
                    for (int i = 0; i < 10; i++) layout(wrapped);
                    String name = scenario[0].replace(' ', '-') + "-" + size.replace(' ', '-');
                    save(wrapped, new File(output, name + "-sidebar.png"));
                    JComponent card = (JComponent) PanelTestSupport.find(panel, "itemSourceCard");
                    if (card == null) card = (JComponent) PanelTestSupport.find(panel, "dropSourceCard");
                    if (scenario.length >= 4)
                    {
                        card = exactMonsterCard(wrapped, scenario[2] + " → " + scenario[0], scenario[3]);
                        if (card == null) throw new IllegalStateException("Missing reviewed monster card: " + scenario[2]);
                    }
                    checkBounds(card);
                    save(card, new File(output, name + "-card.png"));
                    if (scenario.length == 5)
                    {
                        ((JButton)PanelTestSupport.find(card, "openLocations")).doClick(0);
                        for (int i = 0; i < 10; i++) layout(wrapped);
                        JComponent location = (JComponent)PanelTestSupport.find(panel, "location:" + scenario[4]);
                        if (location == null) throw new IllegalStateException("Missing reviewed location: " + scenario[4]);
                        checkBounds(location);
                        save(location, new File(output, name + "-location.png"));
                        MorningFeedbackTest.click(panel, "backToResults");
                    }
                    System.out.println(name + ": card " + card.getWidth() + "x" + card.getHeight() + "; bounds OK");
                }
            }
            MorningFeedbackTest.search(panel,"Chocolate cake");
            MorningFeedbackTest.click(panel,"showDropSources");
            ((JComboBox<?>)PanelTestSupport.find(panel,"npcWildernessFilter")).setSelectedItem(MonsterLocationFilter.WildernessFilter.EXCLUDE);
            JTextField finder=(JTextField)PanelTestSupport.find(panel,"sourceSearch");
            finder.setText("Kamil"); finder.postActionEvent();
            for (String size : new String[]{"Standard text","Larger text"})
            {
                ((JComboBox<?>)PanelTestSupport.find(panel,"textSize")).setSelectedItem(size);
                JComponent wrapped=panel.getWrappedPanel();
                wrapped.setSize(242,650);
                for (int i=0; i<10; i++) layout(wrapped);
                JButton recover=(JButton)PanelTestSupport.find(panel,"recoverNpcAreas");
                if (recover==null || recover.getWidth()==0) throw new IllegalStateException("Area recovery button unavailable");
                save(wrapped,new File(output,"NPC-area-recovery-"+size.replace(' ','-')+".png"));
            }
        });
    }

    private static JComponent exactMonsterCard(Container parent, String heading, String fact)
    {
        if ("dropSourceCard".equals(parent.getName()))
        {
            boolean hasHeading = false;
            boolean hasFact = false;
            for (Component child : parent.getComponents())
            {
                if (!(child instanceof JLabel)) continue;
                String text = ((JLabel) child).getText().replaceAll("<[^>]+>", "").trim();
                hasHeading |= text.equals(heading);
                hasFact |= text.contains(fact);
            }
            if (hasHeading && hasFact) return (JComponent) parent;
        }
        for (Component child : parent.getComponents())
        {
            if (child instanceof Container && child.isVisible())
            {
                JComponent match = exactMonsterCard((Container) child, heading, fact);
                if (match != null) return match;
            }
        }
        return null;
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
