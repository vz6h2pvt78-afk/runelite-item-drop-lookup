package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccessRequirementPanelTest
{
    @Test
    public void nightBeastContextIsVisibleOnceOnTheSourceAndDetails() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
            JTextField query = (JTextField) named(panel, "itemSearch").get(0);
            query.setText("Adamant sq shield");
            query.postActionEvent();
            PanelTestSupport.openSources(panel);
            JTextField finder = (JTextField) named(panel, "sourceSearch").get(0);
            finder.setText("Night beast");
            finder.postActionEvent();
            assertEquals(1, named(panel, "requirement:night-beast-drop-roll-context").size());
            assertLabel(panel, "night-beast-drop-roll-context", "Drop context");
            ((JButton) named(panel, "openLocations").get(0)).doClick();
            assertEquals(1, named(panel, "requirement:night-beast-drop-roll-context").size());
            assertLabel(panel, "night-beast-drop-roll-context", "per roll");
        });
    }

    private MonsterLocationPanel detail(String monster, String item)
    {
        Gson gson = new Gson();
        return new MonsterLocationPanel(new DropSource(item, monster, "1", "", ""), item,
                new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson), WildernessFilter.ALL, () -> { });
    }

    @Test
    public void monsterFactsAppearOnceAndLocalFactsStayInTheirCard() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            MonsterLocationPanel panel = detail("Abyssal demon", "Abyssal whip");
            assertEquals(1, named(panel, "requirement:abyssal-demon-slayer").size());
            assertEquals(1, named(panel, "requirementsCoverage").size());
            Container cave = (Container) named(panel, "location:ec346dbbf5098d24ef88").get(0);
            assertEquals(1, named(cave, "requirement:abyssal-demon-wilderness-task").size());
            assertEquals(0, named(cave, "requirement:abyssal-demon-slayer").size());
            JLabel fact = (JLabel) named(cave, "requirement:abyssal-demon-wilderness-task").get(0);
            assertTrue(fact.getText().contains("Location restriction"));
            assertTrue(fact.getToolTipText().contains("oldid=15351480"));
        });
    }

    @Test
    public void eligibilityAndEncounterFactsHaveDistinctLabels() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            assertLabel(detail("Kraken", "Kraken tentacle"), "kraken-task-magic", "Task eligibility");
            assertLabel(detail("Vorkath", "Superior dragon bones"), "vorkath-repeatable-encounter", "Encounter context");
            MonsterLocationPanel shark = detail("Great white shark", "Great white shark jaw");
            assertLabel(shark, "great-white-shark-boat-context", "Activity context");
            assertLabel(shark, "great-white-shark-bounty-eligibility", "Task eligibility");
            assertLabel(shark, "great-white-shark-jaw-task", "For this drop");
        });
    }

    @Test
    public void narrowSourceCardShowsSlayerAndBackRetainsTheSearch() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
            JTextField query = (JTextField) named(panel, "itemSearch").get(0);
            query.setText("Kraken tentacle");
            ((JButton) named(panel, "searchButton").get(0)).doClick();
            PanelTestSupport.openSources(panel);
            assertEquals(1, named(panel, "requirement:kraken-slayer").size());
            assertTrue(named(panel, "requirement:kraken-task-magic").isEmpty());
            Component fact = named(panel, "requirement:kraken-slayer").get(0);
            JButton open = (JButton) named(fact.getParent(), "openLocations").get(0);
            assertTrue("Button must fit a narrow card", open.getPreferredSize().width <= 195);
            open.doClick();
            assertLabel(panel, "kraken-task-magic", "Task eligibility");
            ((JButton) named(panel, "backToResults").get(0)).doClick();
            assertSame(query, named(panel, "itemSearch").get(0));
            assertEquals("Kraken tentacle", query.getText());
            assertSame(open, named(fact.getParent(), "openLocations").get(0));
        });
    }

    private static void assertLabel(Container panel, String id, String label)
    {
        JLabel fact = (JLabel) named(panel, "requirement:" + id).get(0);
        assertTrue(fact.getText(), fact.getText().contains(label));
    }

    private static List<Component> named(Container parent, String name)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            if (name.equals(child.getName()))
            {
                result.add(child);
            }
            if (child instanceof Container)
            {
                result.addAll(named((Container) child, name));
            }
        }
        return result;
    }
}
