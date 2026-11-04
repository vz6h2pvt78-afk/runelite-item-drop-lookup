package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceClarityTest
{
    private static List<Component> named(Container parent, String name)
    {
        List<Component> matches = new ArrayList<>();
        for (Component c : parent.getComponents())
        {
            if (name.equals(c.getName())) matches.add(c);
            if (c instanceof Container) matches.addAll(named((Container)c, name));
        }
        return matches;
    }

    @Test public void sameDemonTwoDoseDetailsRemainIndividuallyReachable() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson), reports::add);
            MorningFeedbackTest.search(panel, "Prayer potion");
            MorningFeedbackTest.click(panel, "showDropSources");
            JTextField finder = (JTextField)PanelTestSupport.find(panel, "sourceSearch");
            finder.setText("Tormented Demon");
            finder.postActionEvent();
            assertEquals(1, named(panel, "dropSourceCard").size());
            List<Component> details = named(panel, "openLocations");
            assertEquals(2, details.size());
            for (Component c : details)
            {
                JButton row = (JButton)c;
                String item = row.getAccessibleContext().getAccessibleName().contains("potion(2)") ? "Prayer potion(2)" : "Prayer potion(4)";
                assertTrue(row.getText().contains(item.endsWith("(2)") ? "2-dose × 2" : "4-dose × 1"));
                row.doClick(0);
                assertTrue(MorningFeedbackTest.labels(panel).contains("Tormented Demon → " + item));
                MorningFeedbackTest.click(panel, "copyReport");
                assertTrue(reports.get(reports.size()-1).contains("Dose item: " + item));
                MorningFeedbackTest.click(panel, "backToResults");
                assertEquals("Tormented Demon", finder.getText());
            }
            panel.dispose();
        });
    }

    @Test public void sameNameWithUnverifiedOrDifferentBindingStaysSeparate() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson) {
                @Override public List<DropSource> searchByItemName(String name)
                {
                    return List.of(new DropSource("Prayer potion(2)", "Tormented Demon", "2", "1/51", ""),
                            new DropSource("Prayer potion(4)", "Tormented Demon", "1", "1/99", "Other form"));
                }
            };
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
            MorningFeedbackTest.search(panel, "Prayer potion");
            MorningFeedbackTest.click(panel, "showDropSources");
            assertEquals(2, named(panel, "dropSourceCard").size());
            panel.dispose();
        });
    }

    @Test public void discreteQuantitiesKeepTheirMeaningAndUnreviewedCommasArePreserved()
    {
        for (String item : List.of("Iron ore", "Bronze bar"))
            assertEquals("63 or 95 (noted)", DropQuantity.display(new DropSource(item, "Duke Sucellus", "63,95 (noted)", "1/100", "")));
        assertEquals("63,95 (noted)", DropQuantity.display(new DropSource("Other", "Another", "63,95 (noted)", "", "")));
        assertEquals("10–20", DropQuantity.display(new DropSource("Coins", "Example", "10-20", "", "")));
        assertEquals("60–150 (noted)", DropQuantity.display(new DropSource("Example", "Example", "60-150 (noted)", "", "")));
        assertEquals("1,000", DropQuantity.display(new DropSource("Coins", "Example", "1,000", "", "")));
    }
}
