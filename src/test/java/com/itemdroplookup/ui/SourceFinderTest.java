package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceFinderTest
{
    private ItemDropLookupPanel panel(List<String> reports)
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
        return new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson), new ItemPriceLookupService(gson),
                locations, new AccessRequirementLookupService(gson, drops, locations), reports::add);
    }

    private void findSource(ItemDropLookupPanel panel, String query)
    {
        JTextField field = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
        field.setText(query);
        field.postActionEvent();
    }

    private int count(Container parent, String name)
    {
        int result = 0;
        for (Component component : parent.getComponents())
        {
            if (!component.isVisible()) continue;
            if (name.equals(component.getName())) result++;
            if (component instanceof Container) result += count((Container) component, name);
        }
        return result;
    }

    @Test public void comparisonOnlyIgnoresCaseOrdinarySpacesAndApostrophes()
    {
        for (String[] pair : new String[][] {
                {"Vet'ion", " Vet’ion "}, {"Vet'ion", "Vetion"}, {"Kree'arra", "Kreearra"},
                {"K'ril Tsutsaroth", "Kril"}, {"Callisto's Den", "Callisto’s Den"},
                {"Ali's Discount Wares", "ALI’S"}, {"Lizardman shaman", "Lizardmanshaman"},
                {"Shilo Village", "Shilo   Village"}, {"Earth impling loot", "Earthimpling"},
                {"Air rune pack", "airrunepack"}})
            assertTrue(pair[1], SourceTextMatching.contains(pair[0], pair[1]));
        for (String query : List.of("Vetoin", "Vention", "ion Vet", "Vet-ion", "Vet_ion", "''", "’", "’ ’"))
            assertFalse(query, SourceTextMatching.contains("Vet'ion", query));
        assertFalse(SourceTextMatching.contains("Antidote++", "Antidote+++"));
        assertFalse(SourceTextMatching.contains("pack of 100", "pack of 10 1"));
        assertFalse(SourceTextMatching.contains(null, "Kril"));
        assertTrue(SourceTextMatching.contains(null, "  "));
    }

    @Test public void realNpcShopAndLairFindersHandlePunctuationWithoutChangingLocationEligibility() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = panel(reports);
            for (String[] example : new String[][] {
                    {"Dragon pickaxe", "NPC Drops", "Vetion", "1"}, {"Dragon pickaxe", "NPC Drops", "Vet’ion", "1"},
                    {"Big bones", "NPC Drops", "Callisto’s Den", "1"}, {"Armadyl chestplate", "NPC Drops", "Kreearra", "1"},
                    {"Blood rune", "NPC Drops", "Kril", "1"}, {"Air rune", "Shops", "Ali’s", "2"},
                    {"Dragon warhammer", "NPC Drops", "Lizardmanshaman", "1"}, {"Dragon warhammer", "NPC Drops", "Canyon", "0"}})
            {
                MorningFeedbackTest.search(panel, example[0]);
                PanelTestSupport.openSources(panel);
                ((JComboBox<?>) PanelTestSupport.find(panel, "sourceTypeFilter")).setSelectedItem(example[1]);
                findSource(panel, example[2]);
                int cards = count(panel, "dropSourceCard") + count(panel, "itemSourceCard");
                assertEquals(example[0] + " / " + example[2], Integer.parseInt(example[3]), cards);
                MorningFeedbackTest.click(panel, "copyReport");
                assertTrue(reports.get(reports.size() - 1).contains("Find source: " + example[2] + "\n"));
            }
            MorningFeedbackTest.search(panel, "Dragon pickaxe");
            PanelTestSupport.openSources(panel);
            findSource(panel, "Vet’ion");
            JComboBox<?> area = (JComboBox<?>) PanelTestSupport.find(panel, "npcWildernessFilter");
            area.setSelectedItem(WildernessFilter.EXCLUDE);
            assertEquals(0, count(panel, "dropSourceCard"));
            MorningFeedbackTest.click(panel, "recoverNpcAreas");
            assertEquals(1, count(panel, "dropSourceCard"));
            assertEquals("Vet’ion", ((JTextField) PanelTestSupport.find(panel, "sourceSearch")).getText());
            panel.dispose();
        });
    }

    @Test public void aliasReportsAndBackRetainCanonicalItemAndSubmittedFinder() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = panel(reports);
            MorningFeedbackTest.search(panel, "dwh");
            PanelTestSupport.openSources(panel);
            findSource(panel, "Lizardmanshaman");
            MorningFeedbackTest.click(panel, "copyReport");
            String before = reports.get(reports.size() - 1);
            assertTrue(before.contains("Item query: dwh\n"));
            assertTrue(before.contains("Item: Dragon warhammer\n"));
            assertEquals(1, count(panel, "dropSourceCard"));
            MorningFeedbackTest.click(panel, "openLocations");
            MorningFeedbackTest.click(panel, "copyReport");
            String detail = reports.get(reports.size() - 1);
            assertTrue(detail.contains("Lizardman shaman"));
            assertTrue(detail.contains("Drop table: Pre-roll\n"));
            assertTrue(MorningFeedbackTest.labels(panel).contains("1 in 3000"));
            MorningFeedbackTest.click(panel, "backToResults");
            MorningFeedbackTest.click(panel, "copyReport");
            assertEquals(before, reports.get(reports.size() - 1));
            for (String[] pair : new String[][] {{"tbow", "Twisted bow"}, {"ags", "Armadyl godsword"},
                    {"bgs", "Bandos godsword"}, {"sgs", "Saradomin godsword"}, {"zgs", "Zamorak godsword"}})
            {
                MorningFeedbackTest.search(panel, pair[0]);
                assertTrue(MorningFeedbackTest.labels(panel).contains("No acquisition sources recorded"));
                MorningFeedbackTest.click(panel, "copyReport");
                String report = reports.get(reports.size() - 1);
                assertTrue(report.contains("Item query: " + pair[0] + "\n"));
                assertTrue(report.contains("Item: " + pair[1] + "\n"));
                assertTrue(report.contains("Recorded sources: 0\n"));
                MorningFeedbackTest.click(panel, "backToPreviousItem");
                MorningFeedbackTest.click(panel, "copyReport");
                assertEquals(before, reports.get(reports.size() - 1));
            }
            panel.dispose();
        });
    }
}
