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

    @Test public void productionFilterAndIngredientFinderKeepTheOutputInReport() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = new ItemDropLookupPanel(new DropLookupService(gson),new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson),new MonsterLocationLookupService(gson),new AccessRequirementLookupService(gson),reports::add);
            MorningFeedbackTest.search(p,"Bronze bar");
            MorningFeedbackTest.click(p,"showProductionSources");
            assertEquals(1,named(p,"itemSourceCard").size());
            JTextField finder = (JTextField)PanelTestSupport.find(p,"sourceSearch");
            finder.setText("Copper ore");
            finder.postActionEvent();
            assertTrue(MorningFeedbackTest.labels(p).contains("Normal furnace → Bronze bar"));
            MorningFeedbackTest.click(p,"copyReport");
            assertTrue(reports.get(0).contains("Item: Bronze bar") && reports.get(0).contains("Method: Production"));
            assertFalse(reports.get(0).contains("NPC area:"));
            JComboBox<?> filter = (JComboBox<?>)PanelTestSupport.find(p,"sourceTypeFilter");
            filter.setSelectedItem("Non-NPC Sources");
            assertEquals(1,named(p,"itemSourceCard").size());
            filter.setSelectedItem("All");
            assertEquals(1,named(p,"itemSourceCard").size());
            filter.setSelectedItem("Gathering");
            assertEquals(0,named(p,"itemSourceCard").size());
            p.dispose();
        });
    }

    @Test public void sharedEquipmentRequirementAppearsOnceAboveBothDustDevilRates() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson),new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson),new MonsterLocationLookupService(gson),new AccessRequirementLookupService(gson));
            MorningFeedbackTest.search(panel, "Adamant axe");
            MorningFeedbackTest.click(panel, "showDropSources");
            JTextField finder = (JTextField)PanelTestSupport.find(panel,"sourceSearch");
            finder.setText("Dust devil"); finder.postActionEvent();
            Container card = named(panel,"dropContextGroup").get(0).getParent();
            String labels = MorningFeedbackTest.labels(card);
            assertTrue(labels.contains("facemask"));
            assertEquals(labels.indexOf("facemask"),labels.lastIndexOf("facemask"));
            assertEquals(2,named(card,"openLocations").size());
            panel.dispose();
        });
    }

    @Test public void detailHistoryFollowsItsResultSetAndPreservesExactVariants()
    {
        LookupViewState view = new LookupViewState();
        DropSource standard = new DropSource("Air rune","Abyssal demon","50","8/128","");
        DropSource wild = new DropSource("Air rune","Abyssal demon","50","8/68","");
        MonsterLocationPanel.State first = new MonsterLocationPanel.State();
        first.scroll = 17;
        MonsterLocationPanel.State second = new MonsterLocationPanel.State();
        second.scroll = 305;
        view.detailStates.put(standard, first);
        view.detailStates.put(wild, second);
        LookupViewState back = view.copy();
        view.beginSelection("Bronze bar",null);
        assertTrue(view.detailStates.isEmpty());
        assertEquals(17,back.detailStates.get(standard).scroll);
        assertEquals(305,back.detailStates.get(wild).scroll);
    }

    @Test public void findingAnyReviewedShopSiteKeepsOneOfferAndItsExactCost() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson g = new Gson();
            ItemSourceLookupService service = new ItemSourceLookupService(g);
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = new ItemDropLookupPanel(new DropLookupService(g),service,new ItemPriceLookupService(g),
                    new MonsterLocationLookupService(g),new AccessRequirementLookupService(g),reports::add);
            for (String[] query : new String[][] {{"Cape pouch","Forestry Shop","4","2,500 Anima-infused bark"},
                    {"Broad bolts (x250)","Slayer Rewards","10","35 Slayer points"}})
            {
                com.itemdroplookup.model.ItemSource offer = service.searchByItemName(query[0]).stream()
                        .filter(s -> query[1].equals(s.getSourceName())).findFirst().get();
                assertEquals(query[3],offer.getCost());
                List<String> sites = offer.getShopLocations().getSites();
                assertEquals(Integer.parseInt(query[2]),sites.size());
                MorningFeedbackTest.search(p,query[0]);
                MorningFeedbackTest.click(p,"showRewardSources");
                JTextField find = (JTextField)PanelTestSupport.find(p,"sourceSearch");
                for (String place : sites)
                {
                    find.setText(place); find.postActionEvent();
                    assertEquals(1,named(p,"itemSourceCard").size());
                    assertTrue(MorningFeedbackTest.labels(p).contains(place));
                }
                MorningFeedbackTest.click(p,"copyReport");
                assertTrue(reports.get(reports.size()-1).contains("Find source: "+sites.get(sites.size()-1)));
                MorningFeedbackTest.click(p,"allShopSites");
                for (String place : sites) assertTrue(MorningFeedbackTest.labels(p).contains(place));
                MorningFeedbackTest.click(p,"backToOverview");
            }
            p.dispose();
        });
    }

    @Test public void canonicalAndLegacyBulkSearchShareOneReviewedOffer()
    {
        Gson gson = new Gson();
        ItemSourceLookupService service = new ItemSourceLookupService(gson);
        DropLookupService drops = new DropLookupService(gson);
        ItemPriceLookupService prices = new ItemPriceLookupService(gson);
        for (String item : List.of("Broad bolts", "Broad arrows"))
        {
            ItemSearchSelection canonical = new ItemSearchSelection(item, drops, service, prices);
            ItemSearchSelection legacy = new ItemSearchSelection(item + " (x250)", drops, service, prices);
            assertEquals(List.of(item), canonical.choices());
            assertEquals(List.of(item), legacy.choices());
            assertEquals(1, legacy.sources.size());
            assertTrue(legacy.drops.isEmpty());
            com.itemdroplookup.model.ItemSource offer = legacy.sources.get(0);
            assertSame(offer, canonical.sources.stream().filter(s -> s.getSourceName().equals("Slayer Rewards")).findFirst().get());
            assertEquals(1, canonical.sources.stream().filter(s -> s.getBulkPurchase() != null).count());
            assertEquals(250, offer.getBulkPurchase().getQuantity());
            assertEquals(0, offer.getPackQuantity());
            assertEquals(10, offer.getShopLocations().getSites().size());
            assertEquals("35 Slayer points", offer.getCost());
            assertEquals(item.equals("Broad arrows") ? 3 : 0, canonical.drops.size());
            assertEquals(item.equals("Broad arrows") ? 2 : 1, canonical.sources.size());
        }
        ItemSearchSelection broad = new ItemSearchSelection("broad", drops, service, prices);
        assertTrue(broad.choices().containsAll(List.of("Broad bolts","Broad arrows")));
        assertFalse(broad.choices().stream().anyMatch(n -> n.endsWith("(x250)")));
        assertNull(service.findExactBulkOffer("Broad-bolts (x250)"));
        assertNull(service.findExactBulkOffer("Broad bolts (x25)"));
        assertNull(service.findExactBulkOffer("Sawmill voucher (x10)"));
    }

    @Test public void defenderPrerequisiteBelongsOnlyToTheVisibleBasementSource() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson g = new Gson(); List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = new ItemDropLookupPanel(new DropLookupService(g),new ItemSourceLookupService(g),
                    new ItemPriceLookupService(g),new MonsterLocationLookupService(g),new AccessRequirementLookupService(g),reports::add);
            MorningFeedbackTest.search(p,"Dragon defender"); MorningFeedbackTest.click(p,"showDropSources");
            JTextField find = (JTextField)PanelTestSupport.find(p,"sourceSearch");
            find.setText("Rune defender"); find.postActionEvent();
            for (int page=0; page<2; page++)
            {
                assertTrue(MorningFeedbackTest.labels(p).contains("Access: Earn a Rune defender"));
                MorningFeedbackTest.click(p,"copyReport");
                assertTrue(reports.get(page).contains("Access: Earn a Rune defender"));
                if (page==0) MorningFeedbackTest.click(p,"openLocations");
            }
            MorningFeedbackTest.click(p,"backToResults");
            find.setText("unrelated"); find.postActionEvent();
            MorningFeedbackTest.click(p,"copyReport"); assertFalse(reports.get(2).contains("Access: Earn"));
            MorningFeedbackTest.search(p,"Rune defender"); MorningFeedbackTest.click(p,"showDropSources");
            assertFalse(MorningFeedbackTest.labels(p).contains("Access: Earn"));
            MorningFeedbackTest.click(p,"copyReport"); assertFalse(reports.get(3).contains("Access: Earn"));
            p.dispose();
        });
    }

    @Test public void areaRecoveryRequiresAMatchingHiddenDropAndOnlyRelaxesTheArea() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson g = new Gson();
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel p = new ItemDropLookupPanel(new DropLookupService(g),new ItemSourceLookupService(g),
                    new ItemPriceLookupService(g),new MonsterLocationLookupService(g),new AccessRequirementLookupService(g),reports::add);
            MorningFeedbackTest.search(p,"Adamant axe"); MorningFeedbackTest.click(p,"showDropSources");
            JComboBox<?> area = (JComboBox<?>)PanelTestSupport.find(p,"npcWildernessFilter");
            area.setSelectedItem(MonsterLocationFilter.WildernessFilter.EXCLUDE);
            MorningFeedbackTest.search(p,"Shrimps"); MorningFeedbackTest.click(p,"showProductionSources");
            MorningFeedbackTest.search(p,"Raw shrimps"); MorningFeedbackTest.click(p,"showGatheringSources");
            MorningFeedbackTest.search(p,"Chocolate cake"); MorningFeedbackTest.click(p,"showDropSources");
            JTextField finder = (JTextField)PanelTestSupport.find(p,"sourceSearch");
            finder.setText("Kamil"); finder.postActionEvent();
            assertEquals(MonsterLocationFilter.WildernessFilter.EXCLUDE,area.getSelectedItem());
            assertNull(PanelTestSupport.find(p,"openLocations"));
            assertTrue(MorningFeedbackTest.labels(p).contains("Includes Wilderness"));
            assertNotNull(PanelTestSupport.find(p,"recoverNpcAreas"));
            JComboBox<?> methods = (JComboBox<?>)PanelTestSupport.find(p,"sourceTypeFilter");
            methods.setSelectedItem("Ground pickups"); assertNull(PanelTestSupport.find(p,"recoverNpcAreas"));
            methods.setSelectedItem("NPC Drops");
            finder.setText("not an npc"); finder.postActionEvent(); assertNull(PanelTestSupport.find(p,"recoverNpcAreas"));
            finder.setText("Kamil"); finder.postActionEvent();
            area.setSelectedItem(MonsterLocationFilter.WildernessFilter.ONLY);
            MorningFeedbackTest.click(p,"recoverNpcAreas");
            assertEquals(MonsterLocationFilter.WildernessFilter.ALL,area.getSelectedItem());
            assertEquals("NPC Drops",methods.getSelectedItem());
            assertEquals("Kamil",finder.getText());
            assertNotNull(PanelTestSupport.find(p,"openLocations"));
            assertNull(PanelTestSupport.find(p,"recoverNpcAreas"));
            MorningFeedbackTest.click(p,"copyReport"); String recovered = reports.get(0);
            assertTrue(recovered.contains("Item query: Chocolate cake") && recovered.contains("Find source: Kamil"));
            MorningFeedbackTest.click(p,"openLocations"); MorningFeedbackTest.click(p,"backToResults");
            MorningFeedbackTest.click(p,"copyReport"); assertEquals(recovered,reports.get(1));
            p.dispose();
        });
    }

    @Test public void dreamCakeIsQualifiedInTheListAndDetailsWithoutChangingOrdinaryCake() throws Exception
    {
        Gson g = new Gson();
        DropLookupService drops = new DropLookupService(g);
        List<DropSource> cake = drops.searchByItemName("Chocolate cake");
        assertEquals(1,cake.stream().filter(DropRatePresentation::isDreamCake).count());
        assertFalse(cake.stream().anyMatch(d -> d.getMonsterName().equals("Kamil")));
        for (String monster : List.of("Kamil", "Karamel (Nightmare Zone)", "Black dragon"))
            assertFalse(DropRatePresentation.isDreamCake(new DropSource("Chocolate cake",monster,"1","Always","")));
        assertFalse(DropRatePresentation.isDreamCake(new DropSource("Chocolate cake","Kamil (Nightmare Zone)","2","Always","")));
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops,new ItemSourceLookupService(g),new ItemPriceLookupService(g),
                    new MonsterLocationLookupService(g),new AccessRequirementLookupService(g));
            MorningFeedbackTest.search(panel,"Chocolate cake"); PanelTestSupport.openSources(panel);
            JTextField finder = (JTextField)PanelTestSupport.find(panel,"sourceSearch");
            finder.setText("Kamil"); finder.postActionEvent();
            for (int page = 0; page < 2; page++)
            {
                String labels = MorningFeedbackTest.labels(panel);
                assertTrue(labels.contains("Always inside the Nightmare Zone dream"));
                assertTrue(labels.contains(DropRatePresentation.DREAM_CAKE_NOTE));
                if (page == 0) MorningFeedbackTest.click(panel,"openLocations");
            }
            panel.dispose();
        });
    }

    @Test public void sevenTokenRewardsKeepOneSourceAndTheMatchingPreparation() throws Exception
    {
        Gson g = new Gson();
        DropLookupService drops = new DropLookupService(g);
        List<DropSource> tokens = drops.searchByItemName("Warrior guild token");
        assertEquals(7, tokens.stream().filter(s -> s.getAnimatorReward() != null).count());
        String[] metals = {"Bronze","Iron","Steel","Black","Mithril","Adamant","Rune"};
        int[][] amounts = {{5,10,15},{10,20,30},{15,30,45},{20,40,60},{25,50,75},{30,60,90},{40,80,120}};
        for (int i = 0; i < metals.length; i++)
        {
            String metal = metals[i];
            String name = "Animated " + metal + " Armour";
            DropSource token = tokens.stream().filter(s -> s.getMonsterName().equals(name)).findFirst().get();
            com.itemdroplookup.model.AnimatorReward reward = token.getAnimatorReward();
            assertEquals(Integer.toString(amounts[i][0]), token.getQuantity());
            assertEquals("Always", token.getDropRate());
            assertEquals(amounts[i][1], reward.getEasy());
            assertEquals(amounts[i][2], reward.getMedium());
            for (String piece : reward.getEquipment())
                assertFalse(drops.searchByItemName(piece).stream().anyMatch(s -> s.getMonsterName().equals(name)));
        }
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops,new ItemSourceLookupService(g),new ItemPriceLookupService(g),
                    new MonsterLocationLookupService(g),new AccessRequirementLookupService(g));
            MorningFeedbackTest.search(panel,"Warrior guild token"); PanelTestSupport.openSources(panel);
            JTextField find = (JTextField)PanelTestSupport.find(panel,"sourceSearch");
            find.setText("Adamant full helm"); find.postActionEvent();
            assertEquals(1,named(panel,"dropSourceCard").size());
            for (int page = 0; page < 2; page++)
            {
                String labels = MorningFeedbackTest.labels(panel);
                assertTrue(labels.contains("Create opponent") && labels.contains("magical animator"));
                assertTrue(labels.indexOf("magical animator") < labels.indexOf("30 tokens (base)"));
                assertTrue(labels.contains("30 tokens (base)") && labels.contains("60 tokens after Easy, 90 after Medium"));
                if (page == 0) MorningFeedbackTest.click(panel,"openLocations");
            }
            panel.dispose();
        });
    }

    @Test public void alternateJawRatesAreFlaggedForOnlyTheReviewedDropSignatures() throws Exception
    {
        for (String[] row : new String[][]{{"Bull shark","1/3"},{"Hammerhead shark","1/2"}})
        {
            DropSource jaw = new DropSource(row[0] + " jaw", row[0], "1", row[1], "");
            assertTrue(DropRatePresentation.hasUnresolvedAlternate(jaw));
            assertTrue(DropRatePresentation.sourceRateLine(jaw).contains(DropRatePresentation.format(row[1])));
            assertEquals(DropRatePresentation.sourceRateLine(jaw),DropRatePresentation.detailRateLine(jaw));
            assertFalse(DropRatePresentation.hasUnresolvedAlternate(new DropSource(row[0] + " liver", row[0], "1", "1/10", "")));
            assertFalse(DropRatePresentation.hasUnresolvedAlternate(new DropSource(row[0] + " jaw", row[0], "2", row[1], "")));
        }
        assertFalse(DropRatePresentation.hasUnresolvedAlternate(new DropSource("Tiger shark jaw","Tiger shark","1","1/2","")));
        SwingUtilities.invokeAndWait(() -> {
            Gson g = new Gson();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(g),new ItemSourceLookupService(g),
                    new ItemPriceLookupService(g),new MonsterLocationLookupService(g),new AccessRequirementLookupService(g));
            MorningFeedbackTest.search(panel,"Bull shark jaw");
            PanelTestSupport.openSources(panel);
            assertTrue(MorningFeedbackTest.labels(panel).contains("alternate listed"));
            assertTrue(MorningFeedbackTest.labels(panel).contains("30 Sailing to receive one"));
            MorningFeedbackTest.click(panel,"openLocations");
            assertTrue(MorningFeedbackTest.labels(panel).contains("Which rate applies here is unverified"));
            panel.dispose();
        });
    }

    @Test public void cookingCountsOnlySuccessfulOutputAndReportsItsActualContext() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson g = new Gson();
            List<String> reports = new ArrayList<>();
            DropLookupService drops = new DropLookupService(g);
            ItemSourceLookupService sources = new ItemSourceLookupService(g);
            ItemPriceLookupService prices = new ItemPriceLookupService(g);
            for (String other : List.of("Raw shrimps", "Burnt shrimp"))
                assertFalse(new ItemSearchSelection(other, drops, sources, prices).sources.stream()
                        .anyMatch(s -> s.getAcquisitionMethod() != null && s.getAcquisitionMethod().getId().equals("production:shrimps:cooking-range")));
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops,sources,prices,
                    new MonsterLocationLookupService(g),new AccessRequirementLookupService(g),reports::add);
            MorningFeedbackTest.search(panel,"Shrimps");
            PanelTestSupport.openSources(panel);
            JComboBox<?> filters = (JComboBox<?>)PanelTestSupport.find(panel,"sourceTypeFilter");
            for (String filter : List.of("All", "Non-NPC Sources", "Production"))
            {
                filters.setSelectedItem(filter);
                assertEquals(1,named(panel,"itemSourceCard").size());
                assertTrue(MorningFeedbackTest.labels(panel).contains("Makes on success"));
                assertTrue(MorningFeedbackTest.labels(panel).contains("Can burn while cooking"));
            }
            MorningFeedbackTest.click(panel,"copyReport");
            assertTrue(reports.get(0).contains("Production: Cooking range | 1 × Raw shrimps → 1 × Shrimps on success | Can burn"));
            filters.setSelectedItem("Gathering");
            assertEquals(0,named(panel,"itemSourceCard").size());
            MorningFeedbackTest.click(panel,"copyReport");
            assertFalse(reports.get(1).contains("Production: Cooking range"));
            panel.dispose();
        });
    }

    @Test public void bulkPurchaseReportTracksOnlyMatchingVisibleOffers() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson g = new Gson();
            List<String> reports = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(g),new ItemSourceLookupService(g),
                    new ItemPriceLookupService(g),new MonsterLocationLookupService(g),new AccessRequirementLookupService(g),reports::add);
            MorningFeedbackTest.search(panel,"Broad bolts (x250)");
            MorningFeedbackTest.click(panel,"showRewardSources");
            JTextField find = (JTextField)PanelTestSupport.find(panel,"sourceSearch");
            find.setText("250"); find.postActionEvent();
            String labels = MorningFeedbackTest.labels(panel);
            assertTrue(labels.contains("Bulk purchase") && labels.contains("Quantity: 250"));
            assertTrue(labels.contains("35 Slayer points per purchase"));
            MorningFeedbackTest.click(panel,"copyReport");
            String report = reports.get(reports.size()-1);
            assertTrue(report.contains("Item: Broad bolts\n") && report.contains("Item query: Broad bolts (x250)"));
            assertTrue(report.contains("Bulk purchase: Slayer Rewards | 250 Broad bolts for 35 Slayer points per purchase"));
            find.setText("nowhere"); find.postActionEvent();
            MorningFeedbackTest.click(panel,"copyReport");
            assertFalse(reports.get(reports.size()-1).contains("Bulk purchase:"));
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
