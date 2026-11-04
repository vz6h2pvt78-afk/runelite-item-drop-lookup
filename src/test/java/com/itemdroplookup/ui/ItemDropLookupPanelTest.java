package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import com.itemdroplookup.service.MonsterLocationLookupService;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemDropLookupPanelTest
{
    private static class ScrollTestPanel extends ItemDropLookupPanel
    {
        ScrollTestPanel()
        {
            super(new DropLookupService(new Gson()), new ItemSourceLookupService(new Gson()),
                    new ItemPriceLookupService(new Gson()), new MonsterLocationLookupService(new Gson()),
                    new AccessRequirementLookupService(new Gson()));
        }

        JScrollBar scrollBar() { return getScrollPane().getVerticalScrollBar(); }
    }

    private static void layout(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents())
        {
            if (child instanceof Container)
            {
                layout((Container) child);
            }
        }
    }

    private ItemDropLookupPanel panel(DropLookupService drops)
    {
        Gson gson = new Gson();
        return new ItemDropLookupPanel(drops, new ItemSourceLookupService(gson),
                new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
    }

    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents())
        {
            result.add(child);
            if (child instanceof Container)
            {
                result.addAll(descendants((Container) child));
            }
        }
        return result;
    }

    private static Component named(Container root, String name)
    {
        return descendants(root).stream().filter(c -> name.equals(c.getName())).findFirst().get();
    }

    @Test
    public void backRetainsTheOriginalSearchControlsAndResults() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "goblin mail");
            JTextField query = (JTextField) named(panel, "itemSearch");
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter");
            JComboBox<?> wilderness = (JComboBox<?>) named(panel, "npcWildernessFilter");
            query.setText("goblin mail");
            filter.setSelectedItem("NPC Drops");
            wilderness.setSelectedItem(WildernessFilter.EXCLUDE);
            JButton cardButton = (JButton) named(panel, "openLocations");
            long cards = descendants(panel).stream().filter(c -> "dropSourceCard".equals(c.getName())).count();
            cardButton.doClick();
            assertNotNull(named(panel, "monsterDetail"));
            assertTrue(descendants(panel).stream().anyMatch(c -> c instanceof JLabel && ((JLabel) c).getText().toLowerCase(java.util.Locale.ROOT).contains("goblin mail")));
            ((JButton) named(panel, "backToResults")).doClick();
            assertSame(query, named(panel, "itemSearch"));
            assertSame(cardButton, named(panel, "openLocations"));
            assertEquals("goblin mail", query.getText());
            assertEquals("NPC Drops", filter.getSelectedItem());
            assertEquals(WildernessFilter.EXCLUDE, wilderness.getSelectedItem());
            assertEquals(cards, descendants(panel).stream().filter(c -> "dropSourceCard".equals(c.getName())).count());
        });
    }

    @Test
    public void everySourceRemainsAccessibleBeyondTheOldCap() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            DropLookupService drops = new DropLookupService(new Gson())
            {
                @Override
                public List<DropSource> searchByItemName(String query)
                {
                    List<DropSource> result = new ArrayList<>();
                    for (int i = 0; i < 125; i++)
                    {
                        result.add(new DropSource("Example item", "Monster " + i, "1", "1/10", ""));
                    }
                    return result;
                }
            };
            ItemDropLookupPanel panel = panel(drops);
            ((JTextField) named(panel, "itemSearch")).setText("Example item");
            ((JButton) named(panel, "searchButton")).doClick();
            PanelTestSupport.openSources(panel);
            assertEquals(125, descendants(panel).stream().filter(c -> "openLocations".equals(c.getName())).count());
            JComboBox<?> filter = (JComboBox<?>) named(panel, "npcWildernessFilter");
            filter.setSelectedItem(WildernessFilter.ONLY);
            assertEquals(0, descendants(panel).stream().filter(c -> "openLocations".equals(c.getName())).count());
            filter.setSelectedItem(WildernessFilter.ALL);
            assertEquals(125, descendants(panel).stream().filter(c -> "openLocations".equals(c.getName())).count());
        });
    }

    @Test
    public void unknownMonsterHasAnHonestEmptyStateAndWorkingBackButton() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            boolean[] returned = {false};
            MonsterLocationPanel detail = new MonsterLocationPanel(new DropSource("Toadflax", "Zulrah", "25", "2/249", "noted"),
                    "item", new MonsterLocationLookupService(new Gson()), new AccessRequirementLookupService(new Gson()), WildernessFilter.ALL, () -> returned[0] = true);
            assertTrue(descendants(detail).stream().anyMatch(c -> c instanceof JLabel && ((JLabel) c).getText().contains("No location data available")));
            assertEquals("https://oldschool.runescape.wiki/w/Zulrah", ((JButton) named(detail, "monsterWiki")).getToolTipText());
            assertFalse(named(detail, "detailWilderness").isVisible());
            assertFalse(named(detail, "detailMembership").isVisible());
            assertNull(PanelTestSupport.find(detail, "clearLocationFilters"));
            ((JButton) named(detail, "backToResults")).doClick();
            assertTrue(returned[0]);
        });
    }

    @Test
    public void largeListsExposeEveryBatchAndKeepLoadedRowsAfterOverviewBack() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            DropLookupService drops = new DropLookupService(new Gson())
            {
                @Override public List<DropSource> searchByItemName(String query)
                {
                    List<DropSource> rows = new ArrayList<>();
                    for (int i = 0; i < 301; i++) rows.add(new DropSource("Example item", "Monster " + i, "1", "1/10", ""));
                    return rows;
                }
            };
            ItemDropLookupPanel panel = panel(drops);
            search(panel, "Example item");
            assertEquals(50, descendants(panel).stream().filter(c -> "dropSourceCard".equals(c.getName())).count());
            for (int batch = 0; batch < 6; batch++) ((JButton)named(panel,"moreSourceRows")).doClick(0);
            assertEquals(301, descendants(panel).stream().filter(c -> "dropSourceCard".equals(c.getName())).count());
            assertNull(PanelTestSupport.find(panel,"moreSourceRows"));
            ((JButton)named(panel,"backToOverview")).doClick(0);
            PanelTestSupport.openSources(panel);
            assertEquals(301, descendants(panel).stream().filter(c -> "dropSourceCard".equals(c.getName())).count());
        });
    }

    @Test
    public void backRestoresScrollPositionAfterDetailLayout() throws Exception
    {
        ScrollTestPanel[] holder = {null};
        SwingUtilities.invokeAndWait(() -> {
            ScrollTestPanel panel = new ScrollTestPanel();
            holder[0] = panel;
            ((JTextField) named(panel, "itemSearch")).setText("rune scimitar");
            ((JButton) named(panel, "searchButton")).doClick();
            PanelTestSupport.openSources(panel);
            panel.getWrappedPanel().setSize(242, 650);
            layout(panel.getWrappedPanel());
            layout(panel.getWrappedPanel());
            panel.scrollBar().setValue(350);
            assertEquals(350, panel.scrollBar().getValue());
            ((JButton) named(panel, "openLocations")).doClick();
            layout(panel.getWrappedPanel());
        });
        SwingUtilities.invokeAndWait(() -> {
            ScrollTestPanel panel = holder[0];
            assertEquals(0, panel.scrollBar().getValue());
            ((JButton) named(panel, "backToResults")).doClick();
            layout(panel.getWrappedPanel());
            layout(panel.getWrappedPanel());
        });
        SwingUtilities.invokeAndWait(() -> assertEquals(350, holder[0].scrollBar().getValue()));
    }

    @Test
    public void detailFilterControlsActuallyNarrowLocations() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            MonsterLocationPanel detail = new MonsterLocationPanel(new DropSource("Rune scimitar", "Fire giant", "1", "1/128", ""),
                    "rune scimitar", new MonsterLocationLookupService(new Gson()), new AccessRequirementLookupService(new Gson()), WildernessFilter.ALL, () -> { });
            assertEquals(12, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.ONLY);
            assertEquals(1, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailMembership")).setSelectedItem(com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter.F2P);
            assertEquals(0, locationCardCount(detail));
            assertTrue(named(detail, "detailWilderness").isVisible());
            assertTrue(named(detail, "detailMembership").isVisible());
            assertNull(PanelTestSupport.find(detail, "clearLocationFilters"));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.ALL);
            ((JComboBox<?>) named(detail, "detailMembership")).setSelectedItem(com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter.ALL);
            assertEquals(12, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.ONLY);
            ((JComboBox<?>) named(detail, "detailMembership")).setSelectedItem(com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter.ALL);
            assertEquals(1, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.ALL);
            assertEquals(12, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.EXCLUDE);
            assertEquals(11, locationCardCount(detail));
        });
    }

    private static boolean textContains(Container panel, String value)
    {
        return descendants(panel).stream().anyMatch(c -> c instanceof JLabel && ((JLabel) c).getText().contains(value));
    }

    @Test
    public void missingRateIsExplicitInResultsAndDetailsWithoutChangingTheSource() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            DropSource source = new DropSource("Coins", "Shark (Sailing)", "1095-2296", "", "");
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson())
            {
                @Override
                public List<DropSource> searchByItemName(String query)
                {
                    return java.util.Collections.singletonList(source);
                }
            });
            search(panel, "Coins");
            assertTrue(textContains(panel, "Rate: Not recorded"));
            assertFalse(textContains(panel, "Rate: Always"));
            ((JButton) named(panel, "openLocations")).doClick();
            assertTrue(textContains(panel, "Rate: Not recorded"));
            assertEquals("", source.getDropRate());
        });
    }

    private static long sourceCardCount(Container panel)
    {
        return descendants(panel).stream().filter(c -> "itemSourceCard".equals(c.getName())).count();
    }

    private static void search(Container panel, String query)
    {
        ((JTextField) named(panel, "itemSearch")).setText(query);
        ((JButton) named(panel, "searchButton")).doClick();
        PanelTestSupport.openSources(panel);
    }

    @Test
    public void findSourceSurvivesBackAndResetsForANewItem() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "rune scimitar");
            JTextField find = (JTextField) named(panel, "sourceSearch");
            find.setText("fire giant");
            find.postActionEvent();
            assertEquals(1, descendants(panel).stream().filter(c -> "openLocations".equals(c.getName())).count());
            ((JButton) named(panel, "openLocations")).doClick();
            ((JButton) named(panel, "backToResults")).doClick();
            assertSame(find, named(panel, "sourceSearch"));
            assertEquals("fire giant", find.getText());
            search(panel, "prospector boots");
            assertEquals("", find.getText());
            assertEquals(2, sourceCardCount(panel));
            find.setText("motherlode");
            find.postActionEvent();
            assertEquals(1, sourceCardCount(panel));
            assertTrue(textContains(panel, "30 golden nuggets"));
        });
    }

    @Test
    public void clearSearchLeavesMenusInControlOfCombinedFilters() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "graceful hood");
            ((JComboBox<?>) named(panel, "npcWildernessFilter")).setSelectedItem(WildernessFilter.ONLY);
            assertFalse(textContains(panel, "0 of 0"));
            ((JComboBox<?>) named(panel, "sourceTypeFilter")).setSelectedItem("NPC Drops");
            JTextField find = (JTextField) named(panel, "sourceSearch");
            find.setText("missing");
            find.postActionEvent();
            assertTrue(textContains(panel, "No sources match these filters"));
            ((JButton) named(panel, "clearSourceSearch")).doClick();
            assertEquals("NPC Drops", ((JComboBox<?>) named(panel, "sourceTypeFilter")).getSelectedItem());
            assertEquals(WildernessFilter.ONLY, ((JComboBox<?>) named(panel, "npcWildernessFilter")).getSelectedItem());
            assertEquals("", find.getText());
            ((JComboBox<?>) named(panel, "sourceTypeFilter")).setSelectedItem("Reward Shops");
            assertEquals(1, sourceCardCount(panel));
        });
    }

    @Test
    public void togglingPriceKeepsExpandedMonsterCards() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "goblin mail");
            JButton group = (JButton) named(panel, "sourceGroup:Goblin");
            group.doClick();
            JButton location = (JButton) named(group.getParent(), "openLocations");
            ((javax.swing.JCheckBox) named(panel, "showPrice")).doClick();
            assertSame(group, named(panel, "sourceGroup:Goblin"));
            assertSame(location, named(group.getParent(), "openLocations"));
            assertTrue(location.getParent().getParent().isVisible());
            ((JButton) named(panel, "backToOverview")).doClick(0);
            PanelTestSupport.openSources(panel);
            JButton restored = (JButton) named(panel, "sourceGroup:Goblin");
            JButton restoredLocation = (JButton) named(restored.getParent(), "openLocations");
            assertTrue(restoredLocation.getParent().getParent().isVisible());
        });
    }

    @Test
    public void broadQueryDoesNotBorrowOneItemsPrice() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            DropLookupService drops = new DropLookupService(new Gson())
            {
                @Override public List<DropSource> searchByItemName(String query)
                {
                    return java.util.Arrays.asList(
                            new DropSource("Rune scimitar", "One", "1", "1/2", ""),
                            new DropSource("Dragon dagger", "Two", "1", "1/3", ""));
                }
            };
            ItemDropLookupPanel panel = panel(drops);
            search(panel, "example weapons");
            assertFalse(textContains(panel, "GE snapshot:"));
            assertTrue(textContains(panel, "2 matching items"));
            assertNotNull(named(panel, "selectItem:Rune scimitar"));
            assertNotNull(named(panel, "selectItem:Dragon dagger"));
        });
    }

    @Test
    public void searchAndBackStayVisibleWhenListsScroll() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ScrollTestPanel panel = new ScrollTestPanel();
            search(panel, "rune scimitar");
            panel.setSize(242, 535);
            layout(panel);
            layout(panel);
            Component query = named(panel, "itemSearch");
            int queryY = SwingUtilities.convertPoint(query, 0, 0, panel).y;
            panel.scrollBar().setValue(400);
            assertEquals(400, panel.scrollBar().getValue());
            assertEquals(queryY, SwingUtilities.convertPoint(query, 0, 0, panel).y);
            assertTrue(queryY < 50);

            MonsterLocationPanel detail = new MonsterLocationPanel(new DropSource("Rune scimitar", "Fire giant", "1", "1/128", ""),
                    "rune scimitar", new MonsterLocationLookupService(new Gson()), new AccessRequirementLookupService(new Gson()), WildernessFilter.ALL, () -> { });
            detail.setSize(222, 500);
            layout(detail);
            layout(detail);
            Component back = named(detail, "backToResults");
            int backY = SwingUtilities.convertPoint(back, 0, 0, detail).y;
            detail.getLocationScrollPane().getVerticalScrollBar().setValue(400);
            assertEquals(400, detail.getLocationScrollPane().getVerticalScrollBar().getValue());
            assertEquals(backY, SwingUtilities.convertPoint(back, 0, 0, detail).y);
            assertTrue(backY < 30);
        });
    }

    @Test
    public void rewardShopCostsAndCompactItemMatchingRemainIntact() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "prospector boots");
            ((JComboBox<?>) named(panel, "sourceTypeFilter")).setSelectedItem("Reward Shops");
            assertEquals(2, sourceCardCount(panel));
            assertTrue(textContains(panel, "21,000 Volcanic Mine reward points"));
            assertTrue(textContains(panel, "30 golden nuggets"));
            assertFalse(named(panel, "npcWildernessFilter").getParent().isVisible());
            search(panel, "runepouch");
            assertEquals(3, sourceCardCount(panel));
            assertFalse(textContains(panel, "Rune Pouch Note"));
            assertFalse(textContains(panel, "Amount not recorded"));
            assertTrue(textContains(panel, "750 Slayer reward points"));
            assertTrue(textContains(panel, "75 LMS points"));
            assertTrue(textContains(panel, "150 telekinetic + 150 graveyard + 1,500 enchantment + 200 alchemist pizazz points"));
            search(panel, "graceful hood");
            assertEquals(1, sourceCardCount(panel));
            assertTrue(textContains(panel, "Currency: Marks of grace"));
            search(panel, "spade");
            ((JComboBox<?>) named(panel, "sourceTypeFilter")).setSelectedItem("Shops");
            assertEquals(38, sourceCardCount(panel));
            assertFalse(descendants(panel).stream().anyMatch(c -> "openLocations".equals(c.getName())));
            ((JComboBox<?>) named(panel, "sourceTypeFilter")).setSelectedItem("Non-NPC Sources");
            assertEquals(41, sourceCardCount(panel)); // Same 38 shops plus three reviewed ground pickups.
        });
    }

    @Test
    public void shopsAreDiscoverableBeforeScrollingThroughMonsterDrops() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "coal");
            JButton shortcut = (JButton) named(panel, "showOtherSources");
            assertEquals("View shops (6)", shortcut.getText());
            shortcut.doClick();
            assertEquals("Shops", ((JComboBox<?>) named(panel, "sourceTypeFilter")).getSelectedItem());
            assertEquals("coal", ((JTextField) named(panel, "itemSearch")).getText());
            assertEquals(6, sourceCardCount(panel));
            assertFalse(descendants(panel).stream().anyMatch(c -> "openLocations".equals(c.getName())));
        });
    }

    @Test
    public void exactPotionDoesNotIncludeAnotherServicesBlightedFallback() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "super restore(4)");
            assertTrue(textContains(panel, "GE snapshot (4-dose): 11,406 gp"));
            assertFalse(textContains(panel, "Blighted"));
            assertEquals(0, sourceCardCount(panel));
            ((JComboBox<?>) named(panel, "sourceTypeFilter")).setSelectedItem("Shops");
            assertTrue(textContains(panel, "No sources match these filters"));
            assertEquals(0, sourceCardCount(panel));
        });
    }

    @Test
    public void potionNamesShowAllAvailableDosesAndFourDoseReference() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "Prayer potion");
            for (int dose = 1; dose <= 4; dose++)
            {
                assertTrue(textContains(panel, "Prayer Potion(" + dose + ")"));
            }
            assertTrue(textContains(panel, "GE snapshot (4-dose): 8,650 gp"));
            assertTrue(textContains(panel, "Showing all available doses"));
            search(panel, "Super restore");
            assertTrue(textContains(panel, "Super Restore(1)"));
            assertTrue(textContains(panel, "Super Restore(3)"));
            assertTrue(textContains(panel, "Super Restore(4)"));
            assertFalse(textContains(panel, "Blighted"));
            assertFalse(textContains(panel, "Super Restore Mix"));
            assertTrue(textContains(panel, "GE snapshot (4-dose): 11,406 gp"));
            search(panel, "Prayer potion(1)");
            assertTrue(textContains(panel, "GE snapshot (4-dose): 8,650 gp"));
            assertFalse(textContains(panel, "2,388 gp"));
            search(panel, "Antidote++");
            assertTrue(textContains(panel, "GE snapshot (4-dose): 7,072 gp"));
            search(panel, "Prayer mix");
            assertTrue(textContains(panel, "GE snapshot (4-dose): unavailable"));
            search(panel, "Waterskin");
            assertFalse(textContains(panel, "4-dose"));
        });
    }

    @Test
    public void packOptionsShowContentsAndPerPackCostsWithoutChangingTheRunePrice() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "Air rune");
            assertTrue(textContains(panel, "GE snapshot: 4 gp"));
            assertTrue(textContains(panel, "Contains: 100 × Air rune"));
            assertTrue(textContains(panel, "430 coins per pack"));
            assertTrue(textContains(panel, "645 Tokkul per pack"));
            JTextField finder = (JTextField) named(panel, "sourceSearch");
            finder.setText("pack");
            finder.postActionEvent();
            assertEquals(15, sourceCardCount(panel));
            assertFalse(descendants(panel).stream().anyMatch(c -> "openLocations".equals(c.getName())));
            search(panel, "Eye of newt");
            assertTrue(textContains(panel, "Contains: 100 × Eye of newt (noted)"));
        });
    }

    @Test
    public void unverifiedRewardPricesAreNotPresentedAsCoinPrices() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            search(panel, "Seed pack");
            assertTrue(textContains(panel, "Cost not verified; check the Wiki"));
            assertFalse(textContains(panel, "Cost: 30 coins"));
        });
    }

    @Test
    public void entHarvestMovesOnceToGatheringWithCombatAndYieldVisible() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            ItemSourceLookupService sources = new ItemSourceLookupService(gson);
            ItemSearchSelection selection = new ItemSearchSelection("Magic logs", drops, sources, new ItemPriceLookupService(gson));
            assertEquals(drops.searchByItemName("Magic logs").size() - 1, selection.drops.size());
            assertFalse(selection.drops.stream().anyMatch(d -> d.getMonsterName().equals("Ent")));
            assertEquals(1, selection.sources.stream().filter(s -> s.getAcquisitionMethod() != null).count());
            ItemDropLookupPanel panel = panel(drops);
            search(panel, "Magic logs");
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter");
            filter.setSelectedItem("Gathering");
            assertEquals(1, sourceCardCount(panel));
            assertTrue(textContains(panel, "75 base Woodcutting"));
            assertTrue(textContains(panel, "level 101 Ent. If this log type is rolled: 2 noted logs"));
            assertTrue(textContains(panel, "level 86 Ent. If this log type is rolled: 1 noted log"));
            assertFalse(descendants(panel).stream().anyMatch(c -> "openLocations".equals(c.getName())));
            assertEquals("https://oldschool.runescape.wiki/w/Ent", ((JButton) named(panel, "methodWiki")).getToolTipText());
            filter.setSelectedItem("NPC Drops");
            assertEquals(0, sourceCardCount(panel));
            search(panel, "Slayer's enchantment");
            assertTrue(textContains(panel, "Ent"));
            assertEquals(0, descendants(panel).stream().filter(c -> "methodWiki".equals(c.getName())).count());
            search(panel, "Oak logs");
            filter.setSelectedItem("Gathering");
            assertTrue(textContains(panel, "harvesting details unavailable"));
        });
    }

    private static long locationCardCount(Container detail)
    {
        return descendants(detail).stream().filter(c -> c.getName() != null && c.getName().startsWith("location:")).count();
    }

    @Test
    public void zulrahDragonstoneKeepsRateBasisAndHonestMissingLocations() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            DropSource source = drops.searchByItemName("Dragonstone").stream().filter(d -> d.getMonsterName().equals("Zulrah")).findFirst().get();
            assertEquals("Dragonstone", source.getItemName());
            assertEquals("5/7968", source.getDropRate());
            assertEquals("1", source.getQuantity());
            assertEquals(1, drops.searchByItemName("Dragonstone").stream().filter(d -> d.getMonsterName().equals("Zulrah")).count());
            for (String other : new String[]{"Uncut dragonstone", "Dragonstone bolt tips", "Dragonstone amulet"})
                assertFalse(drops.searchByItemName(other).stream().anyMatch(d -> d.getMonsterName().equals("Zulrah") && d.getDropRate().equals("5/7968")));
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson);
            assertTrue(locations.lookupByMonsterName("Zulrah").isEmpty());
            assertFalse(locations.lookup(source).isVerified());
            ItemDropLookupPanel panel = panel(drops);
            search(panel, "Dragonstone");
            JTextField finder = (JTextField) named(panel, "sourceSearch");
            finder.setText("Zulrah");
            finder.postActionEvent();
            assertTrue(textContains(panel, "1 in 1593.6 per roll; 2 rolls per kill"));
            ((JButton) named(panel, "openLocations")).doClick();
            assertTrue(textContains(panel, "1 in 1593.6 per roll; 2 rolls per kill"));
            assertTrue(textContains(panel, "No location data available"));
            assertEquals("https://oldschool.runescape.wiki/w/Zulrah", ((JButton) named(panel, "monsterWiki")).getToolTipText());
            ((JButton) named(panel, "backToResults")).doClick();
            ((JComboBox<?>) named(panel, "npcWildernessFilter")).setSelectedItem(WildernessFilter.EXCLUDE);
            assertFalse(descendants(panel).stream().anyMatch(c -> "openLocations".equals(c.getName())));
            ((JComboBox<?>) named(panel, "npcWildernessFilter")).setSelectedItem(WildernessFilter.ALL);
            assertNotNull(named(panel, "openLocations"));
        });
    }

    @Test
    public void gloryLootIsDiscoverableAndKeepsJarAndChargedItemSearchesSeparate() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel panel = panel(new DropLookupService(new Gson()));
            JTextField query = (JTextField) named(panel, "itemSearch");
            query.setText("Amulet of glory");
            query.postActionEvent();
            JButton loot = (JButton) named(panel, "showLootSources");
            assertTrue(loot.getText().contains("1 loot method"));
            loot.doClick();
            assertEquals(1, sourceCardCount(panel));
            assertTrue(textContains(panel, "3 (noted)"));
            assertTrue(textContains(panel, "Wiki-listed rate: 1/19"));
            assertTrue(textContains(panel, "no Hunter level is required"));
            assertTrue(textContains(panel, "83 Hunter"));
            assertTrue(textContains(panel, "93 bare-handed"));
            assertFalse(descendants(panel).stream().anyMatch(c -> "openLocations".equals(c.getName())));
            JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter");
            filter.setSelectedItem("Non-NPC Sources");
            assertEquals(1, descendants(panel).stream().filter(c -> "methodWiki".equals(c.getName())).count());
            search(panel, "Dragon impling jar");
            assertFalse(textContains(panel, "Dragon impling loot"));
            assertFalse(textContains(panel, "Amulet Of Glory"));
            search(panel, "Amulet of glory(4)");
            assertFalse(textContains(panel, "Dragon impling loot"));
        });
    }
}
