package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZenytePanelTest
{
    private final Gson gson = new Gson();
    private final List<String> reports = new ArrayList<>();
    private ItemDropLookupPanel panel;
    private void start()
    {
        panel = new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson),
                new AccessRequirementLookupService(gson), reports::add);
    }
    private void click(String name) { MorningFeedbackTest.click(panel, name); }
    private void search(String query) { MorningFeedbackTest.search(panel, query); }
    private void chooseMethod(String method)
    { ((JComboBox<?>)PanelTestSupport.find(panel,"sourceTypeFilter")).setSelectedItem(method); }
    private String report() { click("copyReport"); return reports.get(reports.size()-1); }
    private void production(String query)
    {
        search(query); PanelTestSupport.openSources(panel); chooseMethod("Production");
    }

    @Test public void exactRelatedTargetsNeverUseAliasesBrowseOrPartialMatching()
    {
        DropLookupService drops = new DropLookupService(gson);
        ItemSourceLookupService sources = new ItemSourceLookupService(gson);
        ItemPriceLookupService prices = new ItemPriceLookupService(gson);
        for (String query : List.of("dwh", "logs", "Zenyte shar", "zenyte shard", "Prayer potion"))
        {
            ItemSearchSelection exact = ItemSearchSelection.canonicalItem(query, drops, sources, prices);
            assertFalse(exact.isBrowse());
            assertEquals(List.of(query), exact.choices());
            assertTrue(query, exact.drops.isEmpty());
            assertTrue(query, exact.sources.isEmpty());
        }
        ItemSearchSelection shard = ItemSearchSelection.canonicalItem("Zenyte shard", drops, sources, prices);
        assertEquals(List.of("Zenyte shard"), shard.choices());
        assertEquals(2, shard.drops.size());
        assertTrue(shard.drops.stream().allMatch(d -> d.getItemName().equals("Zenyte shard")));
        assertFalse(ItemSearchSelection.canonicalItem("Logs", drops, sources, prices).drops.isEmpty());
    }

    @Test public void ingredientJourneyRestoresBothRecipesAndTheirExactReports() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            start();
            for (String size : List.of("Standard text", "Larger text"))
            {
                production("Zenyte");
                ((JComboBox<?>)PanelTestSupport.find(panel,"textSize")).setSelectedItem(size);
                String cut = report();
                assertTrue(cut.contains("Item: Zenyte\n"));
                assertTrue(cut.contains("Production: Cut an uncut zenyte"));
                assertFalse(panel.canFollowRelatedItem("Zenyte"));
                click("findRelatedItem:Uncut zenyte");
                assertEquals("All", ((JComboBox<?>)PanelTestSupport.find(panel,"sourceTypeFilter")).getSelectedItem());
                chooseMethod("Production");
                ((JComboBox<?>)PanelTestSupport.find(panel,"npcWildernessFilter")).setSelectedItem(MonsterLocationFilter.WildernessFilter.ONLY);
                JTextField finder = (JTextField)PanelTestSupport.find(panel,"sourceSearch");
                finder.setText("Onyx"); finder.postActionEvent();
                String fuse = report();
                assertTrue(fuse.contains("Item: Uncut zenyte\n"));
                assertTrue(fuse.contains("Production: Fuse an uncut zenyte"));
                assertFalse(panel.canFollowRelatedItem("Zenyte"));
                assertFalse(panel.canFollowRelatedItem("Uncut zenyte"));
                click("findRelatedItem:Zenyte shard");
                assertEquals("", finder.getText());
                assertEquals(MonsterLocationFilter.WildernessFilter.ALL,
                        ((JComboBox<?>)PanelTestSupport.find(panel,"npcWildernessFilter")).getSelectedItem());
                String shard = report();
                assertTrue(shard.contains("Item: Zenyte shard\n"));
                assertFalse(shard.contains("Production:"));
                assertFalse(panel.canFollowRelatedItem("Zenyte"));
                assertFalse(panel.canFollowRelatedItem("Onyx")); // At most two reviewed hops.
                String labels = MorningFeedbackTest.labels(panel);
                assertTrue(labels.contains("Demonic gorilla")); assertTrue(labels.contains("Tortured gorilla"));
                click("openLocations");
                assertTrue(report().contains("View: Source details"));
                click("backToResults");
                assertEquals(shard, report());
                assertNotNull(((JButton)PanelTestSupport.find(panel,"backToRelatedItem")).getIcon());
                click("backToRelatedItem");
                assertEquals("Onyx", finder.getText());
                assertEquals(fuse, report());
                click("backToRelatedItem");
                assertEquals(cut, report());
                assertNull(PanelTestSupport.find(panel,"backToRelatedItem"));
            }
            panel.dispose();
        });
    }

    @Test public void browseEntryAndManualSearchRetainExistingHistoryBehavior() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            start(); search("uncut");
            String chooser = report();
            click("selectItem:Uncut zenyte");
            PanelTestSupport.openSources(panel); chooseMethod("Production");
            String recipe = report();
            click("findRelatedItem:Zenyte shard");
            click("backToRelatedItem");
            assertEquals(recipe, report());
            click("backToOverview"); click("backToItems");
            assertEquals(chooser, report());
            click("selectItem:Uncut zenyte"); PanelTestSupport.openSources(panel); chooseMethod("Production");
            click("findRelatedItem:Zenyte shard");
            search("Air rune");
            assertNull(PanelTestSupport.find(panel,"backToRelatedItem"));
            assertTrue(report().contains("Item: Air rune\n"));
            search("not-real-zzzz");
            click("backToPreviousItem");
            assertTrue(report().contains("Item: Air rune\n"));
            panel.dispose();
        });
    }

    @Test public void returningFromIngredientRestoresTheActualScrollAfterEventsSettle() throws Exception
    {
        int[] before = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            start(); production("Uncut zenyte");
            panel.getWrappedPanel().setSize(242,500);
            for (int i=0;i<10;i++) AcquisitionPreview.layout(panel.getWrappedPanel());
        });
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane scroll=panel.getScrollPane();
            scroll.getVerticalScrollBar().setValue(100);
            before[0]=scroll.getVerticalScrollBar().getValue();
            assertTrue(before[0]>0);
            click("findRelatedItem:Zenyte shard"); click("backToRelatedItem");
            for (int i=0;i<10;i++) AcquisitionPreview.layout(panel.getWrappedPanel());
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(before[0], (panel.getScrollPane()).getVerticalScrollBar().getValue());
            panel.dispose();
        });
    }
}
