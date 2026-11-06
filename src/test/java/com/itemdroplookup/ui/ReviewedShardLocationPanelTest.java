package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.*;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedShardLocationPanelTest
{
    private static final List<String> MONSTERS = List.of("Dark beast", "Greater Nechryael", "Kurask", "Mutated Bloodveld", "Waterfiend");

    private static long locationCards(Container root)
    {
        long count = 0;
        for (Component child : root.getComponents())
        {
            if (child.getName() != null && child.getName().startsWith("location:")) count++;
            if (child instanceof Container) count += locationCards((Container) child);
        }
        return count;
    }

    @Test public void applicableSiteOtherLocationsAndReportFollowVisibleFilters() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
            AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops, locations);
            for (String name : MONSTERS)
            {
                DropSource source = drops.searchByItemName("Crystal shard").stream()
                        .filter(row -> row.getMonsterName().equals(name)).findFirst().get();
                MonsterLocationPanel detail = new MonsterLocationPanel(source, "Crystal shard", locations, access, WildernessFilter.ALL, () -> {});
                assertEquals(1, locationCards(detail));
                assertTrue(MorningFeedbackTest.labels(detail).contains("Applicable locations"));
                assertTrue(detail.reviewedRewardReport().contains("Reviewed applicable location: Iorwerth Dungeon\n"));
                if (name.equals("Dark beast"))
                    assertTrue(MorningFeedbackTest.labels(detail).contains("Prifddinas or Iorwerth Dungeon"));
                JButton other = (JButton) PanelTestSupport.find(detail, "allMonsterLocations");
                assertTrue(other.getAccessibleContext().getAccessibleName().startsWith("Show other monster locations"));
                Component explanation = PanelTestSupport.find(detail, "otherLocationsExplanation");
                assertNotNull(explanation);
                for (Component ancestor = other; ancestor != null; ancestor = ancestor.getParent())
                    assertTrue("Other locations must be discoverable with filters collapsed", ancestor.isVisible());
                assertTrue(MorningFeedbackTest.labels(detail).contains("This item and drop rate are not confirmed there."));
                other.doClick(0);
                assertTrue(other.getAccessibleContext().getAccessibleName().startsWith("Hide other monster locations"));
                assertEquals(locations.lookupByMonsterName(name).size(), locationCards(detail));
                assertTrue(MorningFeedbackTest.labels(detail).contains("Monster locations only"));
                assertTrue(detail.reviewedRewardReport().contains("Reviewed applicable location: Iorwerth Dungeon\n"));
                ((JComboBox<?>) PanelTestSupport.find(detail, "detailMembership")).setSelectedItem(MembershipFilter.F2P);
                assertFalse(detail.reviewedRewardReport().contains("Reviewed applicable location:"));
                assertTrue(detail.reviewedRewardReport().contains("For this drop:"));
                other.doClick(0);
                assertEquals(0, locationCards(detail));
                ((JComboBox<?>) PanelTestSupport.find(detail, "detailMembership")).setSelectedItem(MembershipFilter.ALL);
                ((JComboBox<?>) PanelTestSupport.find(detail, "detailWilderness")).setSelectedItem(WildernessFilter.ONLY);
                assertEquals(0, locationCards(detail));
                assertFalse(detail.reviewedRewardReport().contains("Reviewed applicable location:"));
                ((JComboBox<?>) PanelTestSupport.find(detail, "detailWilderness")).setSelectedItem(WildernessFilter.EXCLUDE);
                assertEquals(1, locationCards(detail));
                assertTrue(detail.reviewedRewardReport().contains("Reviewed applicable location: Iorwerth Dungeon\n"));
            }
        });
    }

    @Test public void itemSourceFinderAreaAndBackPreserveContext() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), locations, new AccessRequirementLookupService(gson, drops, locations), reports::add);
            for (String size : List.of("Standard text", "Larger text"))
            {
                ((JComboBox<?>) PanelTestSupport.find(panel, "textSize")).setSelectedItem(size);
                for (String name : MONSTERS)
                {
                    MorningFeedbackTest.search(panel, "Crystal shard");
                    MorningFeedbackTest.click(panel, "showDropSources");
                    ((JComboBox<?>) PanelTestSupport.find(panel, "npcWildernessFilter")).setSelectedItem(WildernessFilter.EXCLUDE);
                    JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
                    finder.setText(name);
                    finder.postActionEvent();
                    MorningFeedbackTest.click(panel, "copyReport");
                    String before = reports.get(reports.size() - 1);
                    assertFalse(before.contains("Reviewed applicable location:"));
                    MorningFeedbackTest.click(panel, "openLocations");
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertTrue(reports.get(reports.size() - 1).contains("Reviewed applicable location: Iorwerth Dungeon\n"));
                    MorningFeedbackTest.click(panel, "backToResults");
                    assertEquals(name, finder.getText());
                    assertEquals(WildernessFilter.EXCLUDE, ((JComboBox<?>) PanelTestSupport.find(panel, "npcWildernessFilter")).getSelectedItem());
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertEquals(before, reports.get(reports.size() - 1));
                }
            }
            panel.dispose();
        });
    }
}
