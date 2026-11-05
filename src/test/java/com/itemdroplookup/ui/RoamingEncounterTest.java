package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoamingEncounterTest
{
    @Test public void roamingCardsFiltersAndReportsUseEncounterContextOnly() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), locations, new AccessRequirementLookupService(gson, drops, locations), reports::add);
            for (String[] pair : new String[][] {{"Derwen", "Ent's roots"}, {"Porazdir", "Demon's heart"},
                    {"Justiciar Zachariah", "Justiciar's hand"}})
            {
                for (String item : List.of(pair[1], "Looting bag"))
                {
                    MorningFeedbackTest.search(panel, item);
                    MorningFeedbackTest.click(panel, "showDropSources");
                    JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
                    finder.setText(pair[0]);
                    finder.postActionEvent();
                    JComboBox<?> area = (JComboBox<?>) PanelTestSupport.find(panel, "npcWildernessFilter");
                    area.setSelectedItem(WildernessFilter.ONLY);
                    assertNotNull(PanelTestSupport.find(panel, "openLocations"));
                    JLabel warning = (JLabel) PanelTestSupport.find(panel, "wildernessWarning");
                    assertNotNull(warning.getIcon());
                    assertTrue(warning.getText().contains("Deep Wilderness"));
                    assertTrue(MorningFeedbackTest.labels(panel).contains("Members encounter"));
                    MorningFeedbackTest.click(panel, "copyReport");
                    String before = reports.get(reports.size() - 1);
                    assertFalse(before.contains("Encounter:"));
                    MorningFeedbackTest.click(panel, "openLocations");
                    String words = MorningFeedbackTest.labels(panel);
                    assertTrue(words.contains("Mage Arena II encounter"));
                    assertTrue(words.contains("enchanted symbol"));
                    assertFalse(words.contains("fighting lair"));
                    assertFalse(words.contains("Combat level"));
                    assertFalse(words.contains("Locations:"));
                    assertFalse(words.contains("level 0"));
                    assertNotNull(PanelTestSupport.find(panel, "roamingWiki"));
                    MorningFeedbackTest.click(panel, "copyReport");
                    String detail = reports.get(reports.size() - 1);
                    assertTrue(detail.contains("Encounter: Mage Arena II\nArea: Deep Wilderness — roaming\nWilderness: Yes\nMembership: Members\nTracking: Enchanted symbol\n"));
                    assertFalse(detail.contains("Fighting lair:"));
                    JComboBox<?> membership = (JComboBox<?>) PanelTestSupport.find(panel, "detailMembership");
                    membership.setSelectedItem(MembershipFilter.F2P);
                    assertNull(PanelTestSupport.find(panel, "roamingEncounter"));
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertFalse(reports.get(reports.size() - 1).contains("Encounter:"));
                    membership.setSelectedItem(MembershipFilter.MEMBERS);
                    assertNotNull(PanelTestSupport.find(panel, "roamingEncounter"));
                    ((JComboBox<?>) PanelTestSupport.find(panel, "detailWilderness")).setSelectedItem(WildernessFilter.EXCLUDE);
                    assertNull(PanelTestSupport.find(panel, "roamingEncounter"));
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertFalse(reports.get(reports.size() - 1).contains("Encounter:"));
                    MorningFeedbackTest.click(panel, "backToResults");
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertEquals(before, reports.get(reports.size() - 1));
                    area.setSelectedItem(WildernessFilter.EXCLUDE);
                    assertNull(PanelTestSupport.find(panel, "openLocations"));
                    MorningFeedbackTest.click(panel, "recoverNpcAreas");
                    assertNotNull(PanelTestSupport.find(panel, "openLocations"));
                }
            }
            MorningFeedbackTest.search(panel, "Ent's roots");
            MorningFeedbackTest.click(panel, "showDropSources");
            JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
            for (String query : List.of("Deep Wilderness", "Mage Arena II", "enchanted symbol"))
            {
                finder.setText(query);
                finder.postActionEvent();
                assertNotNull(query, PanelTestSupport.find(panel, "openLocations"));
            }
            panel.dispose();
        });
    }
}
