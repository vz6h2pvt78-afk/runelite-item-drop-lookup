package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EliteClueConditionTest
{
    @Test
    public void exactClueSelectorsRemainVisibleAndCopyOnlyWithTheirSelectedDetail() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), locations,
                    new AccessRequirementLookupService(gson, drops, locations), reports::add);
            for (String size : List.of("Standard text", "Larger text"))
            {
                ((JComboBox<?>) PanelTestSupport.find(panel, "textSize")).setSelectedItem(size);
                for (String[] example : new String[][]{
                        {"Clue scroll (elite)", "Aberrant spectre", "non-final elite cryptic"},
                        {"Reward casket (elite)", "Aberrant spectre", "the final elite cryptic"},
                        {"Key (elite)", "King Black Dragon", "no Key (elite) in your bank or inventory"}})
                {
                    MorningFeedbackTest.search(panel, example[0]);
                    MorningFeedbackTest.click(panel, "showDropSources");
                    JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
                    finder.setText(example[1]);
                    finder.postActionEvent();
                    String card = MorningFeedbackTest.labels(panel);
                    assertTrue(card.contains("Rate: Always"));
                    assertTrue(card.contains("For this drop:"));
                    assertTrue(card.contains(example[2]));
                    MorningFeedbackTest.click(panel, "copyReport");
                    String sourceReport = reports.get(reports.size() - 1);
                    assertFalse(sourceReport.contains("For this drop:"));
                    MorningFeedbackTest.click(panel, "openLocations");
                    String visible = MorningFeedbackTest.labels(panel);
                    assertTrue(visible.contains(example[1] + " → " + example[0]));
                    assertTrue(visible.contains("Rate: Always"));
                    assertTrue(visible.contains(example[2]));
                    MorningFeedbackTest.click(panel, "copyReport");
                    String detail = reports.get(reports.size() - 1);
                    assertTrue(detail.contains("Item: " + example[0] + "\n"));
                    assertTrue(detail.contains("Detail source: " + example[1] + "\n"));
                    assertTrue(detail.contains("For this drop: "));
                    assertTrue(detail.contains(example[2]));
                    assertFalse(detail.contains("per roll"));
                    if (example[0].equals("Reward casket (elite)")) assertFalse(detail.contains("non-final"));
                    if (example[0].equals("Key (elite)"))
                    {
                        assertFalse(detail.contains("cryptic"));
                        assertFalse(detail.contains("Wilderness:"));
                    }
                    MorningFeedbackTest.click(panel, "backToResults");
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertEquals(sourceReport, reports.get(reports.size() - 1));
                }
            }
            panel.dispose();
        });
    }
}
