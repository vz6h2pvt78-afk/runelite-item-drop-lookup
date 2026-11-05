package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.*;
import com.itemdroplookup.service.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MorningFeedbackTest
{
    private final Gson gson = new Gson();
    private ItemDropLookupPanel panel(List<String> reports)
    {
        return new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson),
                new AccessRequirementLookupService(gson), reports::add);
    }
    static void search(Container p, String query)
    {
        JTextField field = (JTextField) PanelTestSupport.find(p, "itemSearch");
        field.setText(query); field.postActionEvent();
    }
    static void click(Container p, String name) { ((JButton) PanelTestSupport.find(p, name)).doClick(0); }
    static String labels(Container p)
    {
        StringBuilder result = new StringBuilder();
        for (Component c : p.getComponents())
        {
            if (c instanceof JLabel) result.append(((JLabel)c).getText()).append('\n');
            if (c instanceof Container) result.append(labels((Container)c));
        }
        return result.toString();
    }

    @Test public void invalidAndBroadQueriesRestoreLastValidSourceStateAndReport() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = panel(reports);
            search(p, "Goblin mail"); PanelTestSupport.openSources(p);
            ((JComboBox<?>)PanelTestSupport.find(p,"sourceTypeFilter")).setSelectedItem("NPC Drops");
            ((JComboBox<?>)PanelTestSupport.find(p,"npcWildernessFilter")).setSelectedItem(MonsterLocationFilter.WildernessFilter.EXCLUDE);
            JTextField finder = (JTextField)PanelTestSupport.find(p,"sourceSearch");
            finder.setText("Goblin"); finder.postActionEvent();
            click(p,"copyReport");
            String before = reports.get(0);
            for (String query : new String[]{"not-a-real-item-zzzz", "uncut", "Prayer potion(5)", "another-invalid-item-zzzz"})
            {
                search(p,query);
                JButton back = (JButton)PanelTestSupport.find(p,"backToPreviousItem");
                assertNotNull(back.getIcon());
                assertTrue(back.getText().contains("Back to results"));
                click(p,"copyReport");
                String choiceReport = reports.get(reports.size()-1);
                assertTrue(choiceReport.contains("Item query: " + query));
                assertFalse(choiceReport.contains("\nItem:"));
                assertFalse(choiceReport.contains("Find source:"));
                back.doClick(0);
                assertEquals("Goblin mail", ((JTextField)PanelTestSupport.find(p,"itemSearch")).getText());
                assertEquals("Goblin", finder.getText());
                assertNotNull(PanelTestSupport.find(p,"dropSourceCard"));
                click(p,"copyReport");
                assertEquals(before,reports.get(reports.size()-1));
            }
            search(p,"uncut"); search(p,"bad-again-zzzz");
            click(p,"backToPreviousItem"); click(p,"copyReport");
            assertEquals(before,reports.get(reports.size()-1));
        });
    }

    @Test public void freshInvalidHasNoFakeHistoryAndChooserBackKeepsSelectedIdentity() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel p=panel(new ArrayList<>());
            search(p,"zzzz-no-item");
            assertNull(PanelTestSupport.find(p,"backToPreviousItem"));
            search(p,"uncut"); click(p,"selectItem:Uncut emerald");
            JButton back=(JButton)PanelTestSupport.find(p,"backToItems");
            assertNotNull(back.getIcon()); back.doClick(0);
            assertEquals(9,new ItemSearchSelection("uncut",new DropLookupService(gson),new ItemSourceLookupService(gson),new ItemPriceLookupService(gson)).choices().size());
            click(p,"backToPreviousItem");
            assertTrue(labels(p).contains("Uncut emerald"));
            assertNotNull(PanelTestSupport.find(p,"viewAllSources"));
            search(p,"uncut");
            assertNotNull(PanelTestSupport.find(p,"backToPreviousItem"));
        });
    }

    @Test public void knownZeroSourceStateKeepsCanonicalReportAndLastValidBack() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = panel(reports);
            search(p, "Iron ore"); PanelTestSupport.openSources(p);
            ((JComboBox<?>)PanelTestSupport.find(p,"sourceTypeFilter")).setSelectedItem("Gathering");
            JTextField finder = (JTextField)PanelTestSupport.find(p,"sourceSearch");
            finder.setText("Varrock"); finder.postActionEvent();
            click(p, "copyReport"); String original = reports.get(0);
            search(p, "Cooked chicken");
            assertTrue(labels(p).contains("No acquisition sources recorded"));
            assertFalse(labels(p).contains("No matching item"));
            assertFalse(labels(p).contains("Raw chicken"));
            assertNull(PanelTestSupport.find(p,"viewAllSources"));
            assertNotNull(((JButton)PanelTestSupport.find(p,"backToPreviousItem")).getIcon());
            click(p, "copyReport"); String empty = reports.get(1);
            assertTrue(empty.contains("Item: Cooked chicken\n"));
            assertTrue(empty.contains("Item query: Cooked chicken\n"));
            assertTrue(empty.contains("View: No sources\n"));
            assertTrue(empty.contains("Recorded sources: 0\n"));
            assertFalse(empty.contains("Find source:"));
            assertFalse(empty.contains("Method:"));
            assertFalse(empty.contains("NPC area:"));
            search(p, "zzzz-no-item");
            assertTrue(labels(p).contains("No matching item"));
            click(p, "copyReport"); assertFalse(reports.get(2).contains("\nItem:"));
            click(p, "backToPreviousItem"); click(p, "copyReport");
            assertEquals(original, reports.get(3));
            assertEquals("Varrock", finder.getText());
            assertEquals("Gathering", ((JComboBox<?>)PanelTestSupport.find(p,"sourceTypeFilter")).getSelectedItem());
        });
    }

    @Test public void freshKnownZeroSourceHasNoFakeHistoryAndCanonicalWikiAction() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel p = panel(new ArrayList<>());
            search(p, "  coOKed chICken  ");
            assertTrue(labels(p).contains("Cooked chicken"));
            assertNull(PanelTestSupport.find(p,"backToPreviousItem"));
            JButton wiki = (JButton)PanelTestSupport.find(p,"itemWiki");
            assertNotNull(wiki);
            assertTrue(wiki.getText().contains("Wiki"));
        });
    }

    @Test public void historyRestoresScrollAndSavedDetailFiltersAfterEventQueueSettles() throws Exception
    {
        ItemDropLookupPanel[] current=new ItemDropLookupPanel[1];
        SwingUtilities.invokeAndWait(()->{
            ItemDropLookupPanel p=current[0]=panel(new ArrayList<>());
            search(p,"Rune scimitar"); PanelTestSupport.openSources(p);
            JComponent wrapped=p.getWrappedPanel(); wrapped.setSize(242,500);
            for(int i=0;i<10;i++)AcquisitionPreview.layout(wrapped);
            click(p,"openLocations");
            ((JComboBox<?>)PanelTestSupport.find(p,"detailMembership")).setSelectedItem(MonsterLocationFilter.MembershipFilter.MEMBERS);
            click(p,"backToResults");
        });
        SwingUtilities.invokeAndWait(()->{
            ItemDropLookupPanel p=current[0];
            p.getScrollPane().getVerticalScrollBar().setValue(250);
            assertEquals(250,p.getScrollPane().getVerticalScrollBar().getValue());
            search(p,"uncut");
        });
        SwingUtilities.invokeAndWait(()->click(current[0],"backToPreviousItem"));
        SwingUtilities.invokeAndWait(()->{
            ItemDropLookupPanel p=current[0];
            assertEquals(250,p.getScrollPane().getVerticalScrollBar().getValue());
            click(p,"openLocations");
            assertEquals(MonsterLocationFilter.MembershipFilter.MEMBERS,((JComboBox<?>)PanelTestSupport.find(p,"detailMembership")).getSelectedItem());
        });
    }

    @Test public void lyreCardAndDetailAgreeWithoutChangingRawDataOrQuestScope() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            DropLookupService drops = new DropLookupService(gson);
            AccessRequirementLookupService requirements = new AccessRequirementLookupService(gson);
            for (String monster : new String[]{"Borrokar","Freidir","Lanzig","Lensa","Jennella"})
            {
                DropSource source=drops.searchByItemName("Lyre").stream().filter(d->d.getMonsterName().equals(monster)).findFirst().get();
                assertEquals(DropRatePresentation.sourceRateLine(source),DropRatePresentation.detailRateLine(source));
                assertTrue(DropRatePresentation.detailRateLine(source).contains("1 in 17.1"));
                assertEquals("30/512",source.getDropRate());
                if(monster.equals("Jennella")) assertTrue(requirements.forSource(source).isEmpty());
                else assertTrue(requirements.forSource(source).stream().anyMatch(f->f.getText().contains("Olaf the Bard")));
                assertFalse(requirements.forSource(source).stream().anyMatch(f->f.getText().contains("Heroes")));
            }
        });
    }

    @Test public void gloryAliasKeepsCanonicalIdentityAndCannotLeakToCharges()
    {
        ItemSearchSelection alias = selection("Amulet of glory (uncharged)");
        assertEquals(Collections.singletonList("Amulet of glory"),alias.choices());
        assertEquals("Amulet of glory (uncharged)",alias.query);
        assertTrue(alias.sources.stream().anyMatch(s->"Loot & containers".equals(s.getSourceType())));
        for(String query:new String[]{"Amulet of glory(1)","Amulet of glory (2)","Amulet of glory(3)","Amulet of glory(4)","Amulet of glory(6)","Amulet of glory (noted)","Amulet of glory (uncharged) extra"})
            assertFalse(query,selection(query).sources.stream().anyMatch(s->s.getAcquisitionMethod()!=null && s.getItemName().equals("Amulet of glory")));
    }
    private ItemSearchSelection selection(String query)
    { return new ItemSearchSelection(query,new DropLookupService(gson),new ItemSourceLookupService(gson),new ItemPriceLookupService(gson)); }

    @Test public void detailHasSourceItemHierarchyAndOnlyVerifiedWildernessWarns() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            for(String monster:new String[]{"Dust devil","Gargoyle","Zulrah"})
            {
                String item=monster.equals("Zulrah")?"Dragonstone":monster.equals("Gargoyle")?"Granite maul":"Adamant axe";
                DropSource source=new DropLookupService(gson).searchByItemName(item).stream().filter(d->d.getMonsterName().equals(monster)).findFirst().get();
                MonsterLocationPanel p=new MonsterLocationPanel(source,item,new MonsterLocationLookupService(gson),new AccessRequirementLookupService(gson),MonsterLocationFilter.WildernessFilter.ALL,()->{});
                assertTrue(((JLabel)PanelTestSupport.find(p,"sourceItemHeading")).getText().contains(monster+" → "+item));
                assertNotNull(((JButton)PanelTestSupport.find(p,"backToResults")).getIcon());
                assertFalse(labels(p).contains("Outside Wilderness"));
                assertFalse(labels(p).contains("Wilderness status unknown"));
                JLabel warning=(JLabel)PanelTestSupport.find(p,"wildernessWarning");
                if(monster.equals("Dust devil")) { assertNotNull(warning); assertNotNull(warning.getIcon()); assertTrue(warning.getText().contains("PvP danger")); }
                else assertNull(warning);
            }
        });
    }

    @Test public void wildernessLairCanBeFoundAndItsWarningSurvivesTheTripToDetails() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel p = panel(new ArrayList<>());
            search(p, "Voidwaker gem");
            click(p, "showDropSources");
            JTextField finder = (JTextField)PanelTestSupport.find(p, "sourceSearch");
            finder.setText("Web Chasm");
            finder.postActionEvent();
            JComboBox<?> areas = (JComboBox<?>)PanelTestSupport.find(p, "npcWildernessFilter");
            areas.setSelectedItem(MonsterLocationFilter.WildernessFilter.ONLY);
            JLabel danger = (JLabel)PanelTestSupport.find(p, "wildernessWarning");
            assertNotNull(danger.getIcon());
            assertEquals("WILDERNESS level 29 PvP danger Web Chasm", danger.getAccessibleContext().getAccessibleName());
            click(p, "openLocations");
            Container location = (Container)PanelTestSupport.find(p, "location:encounter:Spindel");
            assertNotNull(location);
            assertEquals("WILDERNESS level 29 PvP danger", ((JLabel)PanelTestSupport.find(location,
                    "wildernessWarning")).getAccessibleContext().getAccessibleName());
            click(p, "backToResults");
            areas.setSelectedItem(MonsterLocationFilter.WildernessFilter.EXCLUDE);
            assertNull(PanelTestSupport.find(p, "openLocations"));
            assertNotNull(PanelTestSupport.find(p, "recoverNpcAreas"));
            click(p, "recoverNpcAreas");
            assertNotNull(PanelTestSupport.find(p, "openLocations"));
        });
    }

    @Test public void groupedUniqueRateAndRecipientStayWithTheSelectedDetailAndClipboard() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = panel(reports);
            search(p, "Voidwaker hilt");
            click(p, "showDropSources");
            JTextField finder = (JTextField)PanelTestSupport.find(p, "sourceSearch");
            finder.setText("Callisto");
            finder.postActionEvent();
            assertTrue(labels(p).contains("1 in 360 per kill"));
            click(p, "copyReport");
            assertFalse(reports.get(0).contains("Group drop:"));
            click(p, "openLocations");
            assertTrue(labels(p).contains("1 in 360 per kill"));
            click(p, "copyReport");
            String details = reports.get(1);
            assertTrue(details.contains("Rate: 1 in 360 per kill\nGroup drop: At most one unique per kill."));
            assertTrue(details.contains("Fighting lair: Callisto's Den (Wilderness level 40, PvP danger)"));
            ((JComboBox<?>)PanelTestSupport.find(p,"detailMembership")).setSelectedItem(MonsterLocationFilter.MembershipFilter.F2P);
            click(p, "copyReport");
            assertFalse(reports.get(2).contains("Fighting lair:"));
            assertTrue(reports.get(2).contains("Group drop:"));
            click(p, "backToResults");
            click(p, "copyReport");
            assertEquals(reports.get(0), reports.get(3));
        });
    }

    @Test public void shardWarningIsSearchableAndReportsTheAreaRangeOnlyForVisibleCave() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = panel(reports);
            search(p, "Odium shard 3"); click(p, "showDropSources");
            JTextField finder = (JTextField)PanelTestSupport.find(p, "sourceSearch");
            finder.setText("Scorpion Pit cave"); finder.postActionEvent();
            ((JComboBox<?>)PanelTestSupport.find(p,"npcWildernessFilter")).setSelectedItem(MonsterLocationFilter.WildernessFilter.ONLY);
            JLabel danger = (JLabel)PanelTestSupport.find(p,"wildernessWarning");
            assertEquals("WILDERNESS levels 53–55 PvP danger Scorpion Pit cave",danger.getAccessibleContext().getAccessibleName());
            assertNotNull(danger.getIcon());
            assertTrue(labels(p).contains("1 in 256"));
            click(p, "openLocations"); click(p, "copyReport");
            assertTrue(reports.get(0).contains("(Wilderness levels 53–55, PvP danger)"));
            assertFalse(reports.get(0).contains("Group drop:"));
            click(p, "backToResults");
            ((JComboBox<?>)PanelTestSupport.find(p,"npcWildernessFilter")).setSelectedItem(MonsterLocationFilter.WildernessFilter.EXCLUDE);
            assertNull(PanelTestSupport.find(p,"openLocations"));
        });
    }

    @Test public void implingSummaryRetainsRateCaveatAndOpeningExceptionWithoutRouteLecture()
    {
        AcquisitionMethod method=selection("Amulet of glory").sources.stream().filter(s->s.getAcquisitionMethod()!=null).findFirst().get().getAcquisitionMethod();
        String summary=method.getSummarySections().stream().map(s->String.join(" ",s.getLines())).collect(java.util.stream.Collectors.joining(" "));
        assertTrue(summary.contains("1/19")); assertTrue(summary.contains("Monkey Madness II"));
        assertTrue(summary.contains("83 Hunter")); assertTrue(summary.contains("93 bare-handed"));
        assertTrue(summary.contains("no Hunter level")); assertTrue(summary.contains("empty impling jar in Puro-Puro"));
        assertTrue(summary.length()<method.getSearchText().length());
        assertTrue(method.getSearchText().contains("Outside Puro-Puro"));
        assertFalse(summary.contains("Each route"));
    }
}
