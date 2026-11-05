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
    public void caveHorrorKeepsProtectionOnTheMonsterAndAccessInsideTheExactCave() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            MonsterLocationPanel panel = detail("Cave horror","Black mask (10)");
            assertLabel(panel,"cave-horror-slayer","58 Slayer to kill");
            assertLabel(panel,"cave-horror-protection","Protection: A witchwood icon reduces");
            assertLabel(panel,"cave-horror-protection","Protect from Melee prevents its damage");
            Container cave = (Container)named(panel,"location:70e74913efa330ff38bc").get(0);
            assertEquals(1, named(panel,"requirement:cave-horror-cabin-fever").size());
            assertLabel(cave,"cave-horror-cabin-fever","Complete Cabin Fever");
            assertLabel(cave,"cave-horror-light","unless the fire of eternal light is lit");
            assertTrue(named(cave,"requirement:cave-horror-protection").isEmpty());
            assertFalse(MorningFeedbackTest.labels(panel).contains("Trouble Brewing"));
            assertTrue(named(detail("Cave abomination","Black mask (10)"),"requirement:cave-horror-protection").isEmpty());
        });
    }

    @Test
    public void assignmentQuestsAppearOnceInMonsterDetails() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            for (String[] entry : new String[][]{{"Nechryael", "Rune boots", "nechryael", "80 Slayer to harm", "Priest in Peril"},
                    {"Spiritual mage", "Dragon boots", "spiritual-mage", "83 Slayer to kill", "Death Plateau"}})
            {
                MonsterLocationPanel panel = detail(entry[0], entry[1]);
                assertEquals(1, named(panel, "requirement:" + entry[2] + "-slayer").size());
                assertLabel(panel, entry[2] + "-slayer", entry[3]);
                assertEquals(1, named(panel, "requirement:" + entry[2] + "-task-eligibility").size());
                assertLabel(panel, entry[2] + "-task-eligibility", "Task eligibility");
                assertLabel(panel, entry[2] + "-task-eligibility", entry[4]);
            }
        });
    }

    @Test
    public void equipmentActionsStayVisibleOnTheSourceAndInDetails() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            for (String[] row : new String[][]{{"Dust devil", "Adamant axe", "dust-devil-face-protection", "Equip a facemask"},
                    {"Gargoyle", "Adamant boots", "gargoyle-finishing-equipment", "To finish: use"}})
            {
                Gson gson = new Gson();
                ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                        new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
                JTextField query = (JTextField) named(panel, "itemSearch").get(0);
                query.setText(row[1]);
                query.postActionEvent();
                PanelTestSupport.openSources(panel);
                JTextField finder = (JTextField) named(panel, "sourceSearch").get(0);
                finder.setText(row[0]);
                finder.postActionEvent();
                assertLabel(panel, row[2], row[3]);
                ((JButton) named(panel, "openLocations").get(0)).doClick();
                assertEquals(1, named(panel, "requirement:" + row[2]).size());
                assertLabel(panel, row[2], row[3]);
            }
        });
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
