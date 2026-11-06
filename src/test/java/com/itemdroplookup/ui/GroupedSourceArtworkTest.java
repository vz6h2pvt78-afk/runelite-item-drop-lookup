package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GroupedSourceArtworkTest
{
    private static List<JLabel> titles(Container root)
    {
        List<JLabel> found = new ArrayList<>();
        for (Component child : root.getComponents())
        {
            if (child instanceof JLabel && "dropSourceHeading".equals(child.getName())) found.add((JLabel) child);
            if (child instanceof Container) found.addAll(titles((Container) child));
        }
        return found;
    }

    @Test public void groupedSourcesKeepIdentityAndNavigationWithoutRepeatedItemArtwork() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), locations, new AccessRequirementLookupService(gson, drops, locations), reports::add);
            for (String[] pair : new String[][] {{"Red cape", "Goblin"}, {"Rune scimitar", "Skeleton"}})
            {
                MorningFeedbackTest.search(panel, pair[0]);
                MorningFeedbackTest.click(panel, "showDropSources");
                JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
                finder.setText(pair[1]); finder.postActionEvent();
                JButton group = (JButton) PanelTestSupport.find(panel, "sourceGroup:" + pair[1]);
                assertNotNull(group);
                if (pair[1].equals("Goblin"))
                {
                    assertNotNull(group.getIcon());
                    assertEquals(27, group.getIcon().getIconWidth());
                    assertEquals(39, group.getIcon().getIconHeight());
                }
                else assertNull("Unreviewed groups keep a text-only header", group.getIcon());
                group.doClick(0);
                List<JLabel> rows = titles(group.getParent());
                assertEquals(4, rows.size());
                for (JLabel title : rows)
                {
                    assertNull("No item icon or blank icon gutter on a grouped source row", title.getIcon());
                    assertFalse(title.getText().contains(pair[0]));
                    assertFalse(title.getText().contains("→"));
                    assertTrue(title.getText().contains(pair[1]));
                }
                if (pair[1].equals("Goblin"))
                    assertTrue(MorningFeedbackTest.labels(group.getParent()).contains("Some locations in the WILDERNESS"));
                MorningFeedbackTest.click(panel, "copyReport");
                String before = reports.get(reports.size() - 1);
                MorningFeedbackTest.click(panel, "openLocations");
                MorningFeedbackTest.click(panel, "copyReport");
                assertTrue(reports.get(reports.size() - 1).contains("Detail source: " + pair[1]));
                MorningFeedbackTest.click(panel, "backToResults");
                MorningFeedbackTest.click(panel, "copyReport");
                assertEquals(before, reports.get(reports.size() - 1));
                assertSame(group, PanelTestSupport.find(panel, "sourceGroup:" + pair[1]));
            }
            panel.dispose();
        });
    }
}
