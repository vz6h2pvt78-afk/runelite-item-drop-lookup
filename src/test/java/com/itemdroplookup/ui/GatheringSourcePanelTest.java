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
    public void missingCoverageDoesNotClaimThatLogsCannotBeGathered() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("logs");
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("Gathering");
            assertTrue(named(panel, "itemSourceCard").isEmpty());
            assertTrue(labels(panel).contains("No verified gathering methods in the current data."));
            assertEquals(1, named(panel, "gatheringCoverage").size());
            ((JButton) named(panel, "resetSourceFilters").get(0)).doClick();
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
            ((JButton) named(panel, "resetSourceFilters").get(0)).doClick();
            assertEquals(1, named(panel, "showGatheringSources").size());
        });
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
