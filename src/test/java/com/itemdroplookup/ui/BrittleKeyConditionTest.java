package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrittleKeyConditionTest
{
    private JButton detailButton(Container parent, String monster)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JButton && ("Locations and requirements for " + monster).equals(
                    ((JButton) child).getAccessibleContext().getAccessibleName())) return (JButton) child;
            if (child instanceof Container)
            {
                JButton found = detailButton((Container) child, monster);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void copiedConditionFollowsOnlyTheVisibleSelectedBrittleKeyDetail() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), locations,
                    new AccessRequirementLookupService(gson, drops, locations), reports::add);
            for (String size : new String[]{"Standard text", "Larger text"})
            {
                ((JComboBox<?>) PanelTestSupport.find(panel, "textSize")).setSelectedItem(size);
                MorningFeedbackTest.search(panel, "Brittle key");
                MorningFeedbackTest.click(panel, "copyReport");
                assertFalse(reports.get(reports.size() - 1).contains("For this drop:"));
                MorningFeedbackTest.click(panel, "showDropSources");
                for (String monster : new String[]{"Gargoyle", "Marble gargoyle"})
                {
                    JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
                    finder.setText(monster);
                    finder.postActionEvent();
                    JButton open = detailButton(panel, monster);
                    assertNotNull(open);
                    assertTrue(MorningFeedbackTest.labels(open.getParent()).contains("For this drop:"));
                    MorningFeedbackTest.click(panel, "copyReport");
                    String sourceReport = reports.get(reports.size() - 1);
                    assertFalse(sourceReport.contains("For this drop:"));
                    open.doClick(0);
                    String visible = MorningFeedbackTest.labels(panel);
                    assertTrue(visible.contains("Rate: 1 in 150"));
                    assertTrue(visible.contains("For this drop:"));
                    MorningFeedbackTest.click(panel, "copyReport");
                    String detail = reports.get(reports.size() - 1);
                    assertTrue(detail.contains("Detail source: " + monster + "\n"));
                    assertTrue(detail.contains("For this drop: Requires a Gargoyles Slayer task, Slayer Tower roof access still locked, and no Brittle key in your bank or inventory.\n"));
                    assertFalse(detail.contains("per roll"));
                    assertEquals(detail.indexOf("For this drop:"), detail.lastIndexOf("For this drop:"));
                    ((JComboBox<?>) PanelTestSupport.find(panel, "detailMembership")).setSelectedItem(MembershipFilter.F2P);
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertTrue(reports.get(reports.size() - 1).contains("For this drop:"));
                    MorningFeedbackTest.click(panel, "backToResults");
                    MorningFeedbackTest.click(panel, "copyReport");
                    assertEquals(sourceReport, reports.get(reports.size() - 1));
                }
                MorningFeedbackTest.search(panel, "Granite maul");
                MorningFeedbackTest.click(panel, "showDropSources");
                detailButton(panel, "Marble gargoyle").doClick(0);
                MorningFeedbackTest.click(panel, "copyReport");
                assertFalse(reports.get(reports.size() - 1).contains("For this drop:"));
                MorningFeedbackTest.click(panel, "backToResults");
            }
            panel.dispose();
        });
    }
}
