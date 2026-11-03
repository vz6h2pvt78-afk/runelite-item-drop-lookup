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

public class GroundSourcePanelTest
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
    public void oneClickShortcutCountsRowsAndShopsRemainSeparate() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("spade");
            int allCards = named(panel, "itemSourceCard").size();
            assertTrue(allCards > 3);
            assertFalse(named(panel, "openLocations").isEmpty());
            JButton shortcut = (JButton) named(panel, "showGroundSources").get(0);
            assertEquals("View ground pickups (3)", shortcut.getText());
            shortcut.doClick();
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter").get(0);
            assertEquals("Ground pickups", filter.getSelectedItem());
            assertEquals(3, named(panel, "itemSourceCard").size());
            assertTrue(named(panel, "openLocations").isEmpty());
            assertEquals(1, named(panel, "groundCoverage").size());
            filter.setSelectedItem("Shops");
            assertEquals(allCards - 3, named(panel, "itemSourceCard").size());
            assertFalse(labels(panel).contains("Type: Ground pickup"));
            assertTrue(named(panel, "showGroundSources").isEmpty());
            filter.setSelectedItem("Non-NPC Sources");
            assertEquals(allCards, named(panel, "itemSourceCard").size());
            assertTrue(labels(panel).contains("Type: Ground pickup"));
        });
    }

    @Test
    public void pointCountsDoNotBecomePickupQuantitiesOrRespawnClaims() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("bucket");
            ((JButton) named(panel, "showGroundSources").get(0)).doClick();
            assertEquals(3, named(panel, "itemSourceCard").size());
            String text = labels(panel);
            assertTrue(text.contains("F2P · 2 spawn points"));
            assertTrue(text.contains("Falador farm"));
            assertTrue(text.contains("Access requirements not fully checked"));
            assertFalse(text.contains("Quantity:"));
            assertFalse(text.toLowerCase().contains("respawn"));
            assertFalse(text.contains("League"));
            assertEquals(1, named(panel, "groundCoverage").size());
        });
    }

    @Test
    public void missingCoverageDoesNotClaimThatNetsHaveNoGroundSpawns() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = search("small fishing net");
            ((JComboBox<?>) named(panel, "sourceTypeFilter").get(0)).setSelectedItem("Ground pickups");
            assertEquals(0, named(panel, "itemSourceCard").size());
            assertTrue(labels(panel).contains("No verified ground pickups in the current data."));
            assertTrue(labels(panel).contains("limited coverage"));
            ((JButton) named(panel, "resetSourceFilters").get(0)).doClick();
            assertFalse(named(panel, "itemSourceCard").isEmpty());
            assertFalse(labels(panel).contains("Type: Ground pickup"));
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
