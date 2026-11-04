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

    @Test public void areaVariantsExposeTheirOwnRateLocationAndFilteredDetail() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService exactMonster = new DropLookupService(gson) {
                @Override public List<DropSource> searchByItemName(String item)
                {
                    return super.searchByItemName(item).stream().filter(d -> d.getMonsterName().equals("Abyssal demon"))
                            .collect(java.util.stream.Collectors.toList());
                }
            };
            ItemDropLookupPanel p = new ItemDropLookupPanel(exactMonster, new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
            MorningFeedbackTest.search(p, "Air rune");
            MorningFeedbackTest.click(p, "showDropSources");
            JTextField finder = (JTextField)PanelTestSupport.find(p, "sourceSearch");
            finder.setText("Abyssal demon");
            finder.postActionEvent();
            assertEquals(1, named(p, "dropContextGroup").size());
            assertEquals(2, named(p, "openLocations").size());
            String both = MorningFeedbackTest.labels(p);
            assertTrue(both.contains("1 in 16") && both.contains("1 in 8.5"));
            JComboBox<?> area = (JComboBox<?>)PanelTestSupport.find(p, "npcWildernessFilter");
            area.setSelectedItem(MonsterLocationFilter.WildernessFilter.ONLY);
            assertEquals(1, named(p, "openLocations").size());
            MorningFeedbackTest.click(p, "openLocations");
            String detail = MorningFeedbackTest.labels(p);
            assertTrue(detail.contains("Wilderness Slayer Cave") && detail.contains("1 in 8.5"));
            assertFalse(detail.contains("1 in 16"));
            MorningFeedbackTest.click(p, "backToResults");
            area.setSelectedItem(MonsterLocationFilter.WildernessFilter.EXCLUDE);
            assertEquals(1, named(p, "openLocations").size());
            assertTrue(MorningFeedbackTest.labels(p).contains("1 in 16"));
            assertFalse(MorningFeedbackTest.labels(p).contains("1 in 8.5"));
            p.dispose();
        });
    }

    @Test public void mixedLocationsKeepASingleTupleAndFinderRespectsArea()
    {
        Gson gson = new Gson();
        DropSource source = new DropLookupService(gson).searchByItemName("Adamant full helm").stream()
                .filter(d -> d.getMonsterName().equals("Green dragon") && d.getDropRate().equals("1/128")).findFirst().get();
        BoundDropContexts ctx = new BoundDropContexts(new MonsterLocationLookupService(gson), List.of(source));
        assertFalse(ctx.isVariant(source));
        assertTrue(ctx.danger(source, MonsterLocationFilter.WildernessFilter.ALL).contains("Some locations"));
        assertTrue(ctx.find(source, "wilderness slayer cave", MonsterLocationFilter.WildernessFilter.ONLY));
        assertFalse(ctx.find(source, "wilderness slayer cave", MonsterLocationFilter.WildernessFilter.EXCLUDE));
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
