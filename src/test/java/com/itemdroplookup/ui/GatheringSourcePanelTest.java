package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class GatheringSourcePanelTest
{
    private ItemDropLookupPanel search(String query)
    {
        Gson gson = new Gson();
        ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson),
                new ItemSourceLookupService(gson), new ItemPriceLookupService(gson),
                new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
        ((JTextField) named(panel, "itemSearch").get(0)).setText(query);
        ((JButton) named(panel, "searchButton").get(0)).doClick();
        if ("Logs".equals(query)) MorningFeedbackTest.click(panel, "selectItem:Logs");
        PanelTestSupport.openSources(panel);
        return panel;
    }

    @Test
    public void shortcutAndFiltersPreserveAllExistingSources() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("iron ore");
            int allCards = named(panel, "itemSourceCard").size();
            assertTrue(allCards > 1);
            assertFalse(named(panel, "openLocations").isEmpty());
            JButton shortcut = (JButton) named(panel, "showGatheringSources").get(0);
            assertEquals("View gathering (1)", shortcut.getText());
            shortcut.doClick();
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter").get(0);
            assertEquals("Gathering", filter.getSelectedItem());
            assertEquals(1, named(panel, "itemSourceCard").size());
            assertTrue(named(panel, "openLocations").isEmpty());
            assertEquals(1, named(panel, "gatheringCoverage").size());
            for (String excluded : new String[] {"Shops", "Reward Shops", "Ground pickups", "NPC Drops"})
            {
                filter.setSelectedItem(excluded);
                assertFalse(labels(panel).contains("Mine iron rocks"));
                assertTrue(named(panel, "showGatheringSources").isEmpty());
            }
            filter.setSelectedItem("Shops");
            assertEquals(allCards - 1, named(panel, "itemSourceCard").size());
            filter.setSelectedItem("Non-NPC Sources");
            assertEquals(allCards, named(panel, "itemSourceCard").size());
            assertTrue(labels(panel).contains("Mine iron rocks"));
        });
    }

    @Test
    public void cardDistinguishesActionRequirementsResourcesAndCaution() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("ironore");
            ((JButton) named(panel, "showGatheringSources").get(0)).doClick();
            String text = labels(panel);
            assertTrue(text.contains("Mine iron rocks"));
            assertTrue(text.contains("South-west Varrock mine"));
            assertTrue(text.contains("Mining level 15 · Pickaxe required"));
            assertTrue(text.contains("3 iron rocks · F2P"));
            assertTrue(text.contains("Caution: aggressive mugger nearby."));
            assertTrue(text.contains("Access requirements not fully checked"));
            assertFalse(text.contains("Quantity:"));
            assertFalse(text.contains("Combat"));
            assertFalse(text.contains("Respawn"));
            assertFalse(text.contains("League"));
            assertEquals(1, named(panel, "itemWiki").size());
        });
    }

    @Test
    public void unreviewedLogsKeepAnHonestHarvestFallbackAndOtherSources() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("oak logs");
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("Gathering");
            assertEquals(1, named(panel, "itemSourceCard").size());
            assertTrue(labels(panel).contains("Details unavailable"));
            assertEquals(1, named(panel, "gatheringCoverage").size());
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("All");
            assertFalse(named(panel, "openLocations").isEmpty());
        });
    }

    @Test
    public void sourceSearchUsesTheActionAndLocationWithoutChangingItemIdentity() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("iron ore");
            ((JTextField) named(panel, "sourceSearch").get(0)).setText("varrock");
            // Changing the filter renders immediately without waiting for the typing timer.
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("Gathering");
            assertEquals(1, named(panel, "itemSourceCard").size());
            ((JTextField) named(panel, "sourceSearch").get(0)).setText("lumbridge");
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("Non-NPC Sources");
            assertTrue(named(panel, "showGatheringSources").isEmpty());
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("Gathering");
            assertTrue(labels(panel).contains("No verified gathering methods match these filters."));
            ((JButton) named(panel, "clearSourceSearch").get(0)).doClick();
            assertEquals("Gathering", ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).getSelectedItem());
            assertEquals(1, named(panel, "itemSourceCard").size());
        });
    }

    @Test
    public void fishingAppearsOnceInEachApplicableViewAndNeverAsCombat() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("Raw shrimps");
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter").get(0);
            assertEquals("View gathering (1)", ((JButton) named(panel, "showGatheringSources").get(0)).getText());
            for (String included : new String[] {"All", "Non-NPC Sources", "Gathering"})
            {
                filter.setSelectedItem(included);
                assertEquals(1, named(panel, "itemSourceCard").stream()
                        .filter(c -> labels((Container) c).contains("Small-net fishing")).count());
                assertTrue(labels(panel).contains("Fishing level 1 · Small fishing net required"));
                assertTrue(labels(panel).contains("No bait needed · F2P"));
                assertTrue(labels(panel).contains("Lumbridge Swamp"));
                assertTrue(labels(panel).contains("Near the Fishing tutor."));
            }
            assertEquals(1, named(panel, "itemSourceCard").size());
            assertTrue(named(panel, "openLocations").isEmpty());
            for (String excluded : new String[] {"Shops", "Reward Shops", "Ground pickups", "NPC Drops"})
            {
                filter.setSelectedItem(excluded);
                assertFalse(labels(panel).contains("Small-net fishing"));
                assertFalse(labels(panel).contains("Small fishing net required"));
            }
        });
    }

    @Test
    public void fishingSourceFinderPreservesOneLocationAndRecoversFromNoMatch() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("rawshrimps");
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter").get(0);
            JTextField finder = (JTextField) named(panel, "sourceSearch").get(0);
            finder.setText("Lumbridge Swamp");
            filter.setSelectedItem("Gathering");
            assertEquals(1, named(panel, "itemSourceCard").size());
            finder.setText("Draynor");
            filter.setSelectedItem("Non-NPC Sources");
            filter.setSelectedItem("Gathering");
            assertEquals(0, named(panel, "itemSourceCard").size());
            assertTrue(labels(panel).contains("No verified gathering methods match these filters."));
            ((JButton) named(panel, "clearSourceSearch").get(0)).doClick();
            assertEquals("Gathering", filter.getSelectedItem());
            assertEquals(1, named(panel, "itemSourceCard").size());
            finder.setText("Small-net");
            filter.setSelectedItem("Non-NPC Sources");
            assertEquals(1, named(panel, "itemSourceCard").size());
        });
    }

    @Test
    public void fishingIdentityDoesNotLeakToCookedShrimpsOrOtherFish()
    {
        Gson gson = new Gson();
        ItemSourceLookupService sources = new ItemSourceLookupService(gson);
        assertEquals(1, sources.searchByItemName("Raw shrimps").stream()
                .filter(s -> "Gathering".equals(s.getSourceType())).count());
        for (String query : new String[] {"Shrimps", "Raw anchovies", "Raw sardine", "Raw herring"})
        {
            // Selection resolves the best identity across NPC and non-NPC data.
            // A single service may legitimately return a broader matching tier.
            ItemSearchSelection selection = new ItemSearchSelection(query, new DropLookupService(gson),
                    sources, new ItemPriceLookupService(gson));
            assertTrue(query, selection.sources.stream()
                    .noneMatch(s -> "Small-net fishing".equals(s.getSourceName())));
            assertEquals(query, 1, selection.choices().size());
            assertEquals(query, selection.choices().get(0));
        }
        assertTrue(new DropLookupService(gson).searchByItemName("Raw shrimps").stream()
                .noneMatch(d -> d.getMonsterName().toLowerCase().contains("fishing spot")));
    }

    @Test
    public void ordinaryTreesAndEntsRemainSeparateGatheringMethods() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("Logs");
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter").get(0);
            assertEquals("View gathering (2)", ((JButton) named(panel, "showGatheringSources").get(0)).getText());
            for (String included : new String[] {"All", "Non-NPC Sources", "Gathering"})
            {
                filter.setSelectedItem(included);
                assertEquals(1, named(panel, "itemSourceCard").stream()
                        .filter(c -> labels((Container)c).contains("Chop ordinary trees")).count());
                assertEquals(1, named(panel, "itemSourceCard").stream()
                        .filter(c -> labels((Container)c).contains("Ent trunk")).count());
            }
            assertEquals(2, named(panel, "itemSourceCard").size());
            for (Component component : named(panel, "itemSourceCard"))
            {
                String card = labels((Container) component);
                if (card.contains("Chop ordinary trees"))
                {
                    assertTrue(card.contains("Woodcutting level 1 · Axe required"));
                    assertTrue(card.contains("Draynor Village"));
                    for (String excluded : new String[] {"Ent", "WILDERNESS", "PvP", "45", "noted log", "Combat"})
                        assertFalse(card, card.contains(excluded));
                }
                else
                {
                    assertTrue(card.contains("Ent trunk"));
                    assertTrue(card.contains("WILDERNESS"));
                    assertTrue(card.contains("45"));
                    assertFalse(card.contains("Draynor"));
                    assertFalse(card.contains("Woodcutting level 1 · Axe required"));
                }
            }
            filter.setSelectedItem("NPC Drops");
            assertFalse(labels(panel).contains("Chop ordinary trees"));
            assertFalse(labels(panel).contains("Ent trunk"));
        });
    }

    @Test
    public void ordinaryTreeLocationFinderDoesNotBorrowEntLocations() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("Logs");
            JTextField finder = (JTextField) named(panel, "sourceSearch").get(0);
            finder.setText("Draynor");
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("Gathering");
            assertEquals(1, named(panel, "itemSourceCard").size());
            assertTrue(labels(panel).contains("Chop ordinary trees"));
            assertFalse(labels(panel).contains("Ent trunk"));
            assertTrue(named(panel, "openLocations").isEmpty());
            ((JButton) named(panel, "clearSourceSearch").get(0)).doClick();
            assertEquals(2, named(panel, "itemSourceCard").size());
        });
    }

    @Test
    public void ordinaryTreesOnlyAddTheReviewedCanonicalLogsOutput()
    {
        Gson gson = new Gson();
        ItemSourceLookupService sources = new ItemSourceLookupService(gson);
        for (String query : new String[] {"Oak logs", "Willow logs", "Maple logs", "Yew logs", "Magic logs", "Raw shrimps"})
            assertTrue(sources.searchByItemName(query).stream().noneMatch(s -> "Chop ordinary trees".equals(s.getSourceName())));
        ItemSearchSelection logs = new ItemSearchSelection("Logs", new DropLookupService(gson), sources, new ItemPriceLookupService(gson));
        assertTrue(logs.isBrowse());
        assertTrue(logs.choices().contains("Logs"));
        assertEquals(2, logs.sourcesFor("Logs").stream().filter(s -> "Gathering".equals(s.getSourceType())).count());
        assertEquals(1, logs.sourcesFor("Logs").stream().filter(s -> "Chop ordinary trees".equals(s.getSourceName()) && s.getAcquisitionMethod() == null).count());
    }

    private static List<Component> named(Container parent, String name)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            if (name.equals(child.getName())) { result.add(child); }
            if (child instanceof Container) { result.addAll(named((Container) child, name)); }
        }
        return result;
    }

    private static String labels(Container parent)
    {
        StringBuilder result = new StringBuilder();
        for (Component child : parent.getComponents())
        {
            if (child instanceof JLabel) { result.append(((JLabel) child).getText()); }
            if (child instanceof Container) { result.append(labels((Container) child)); }
        }
        return result.toString();
    }
}
