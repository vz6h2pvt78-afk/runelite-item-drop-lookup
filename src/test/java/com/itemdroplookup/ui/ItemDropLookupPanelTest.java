package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.DropLookupService;
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
                    new ItemPriceLookupService(new Gson()), new MonsterLocationLookupService(new Gson()));
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
                new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson));
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
            MonsterLocationPanel detail = new MonsterLocationPanel(new DropSource("Item", "Unknown monster", "1", "1/2", ""),
                    "item", new MonsterLocationLookupService(new Gson()), WildernessFilter.ALL, () -> returned[0] = true);
            assertTrue(descendants(detail).stream().anyMatch(c -> c instanceof JLabel && ((JLabel) c).getText().contains("No location data available")));
            ((JButton) named(detail, "backToResults")).doClick();
            assertTrue(returned[0]);
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
                    "rune scimitar", new MonsterLocationLookupService(new Gson()), WildernessFilter.ALL, () -> { });
            assertEquals(12, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.ONLY);
            assertEquals(1, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailMembership")).setSelectedItem(com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter.F2P);
            assertEquals(0, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailMembership")).setSelectedItem(com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter.ALL);
            assertEquals(1, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.ALL);
            assertEquals(12, locationCardCount(detail));
            ((JComboBox<?>) named(detail, "detailWilderness")).setSelectedItem(WildernessFilter.EXCLUDE);
            assertEquals(11, locationCardCount(detail));
        });
    }

    private static long locationCardCount(Container detail)
    {
        return descendants(detail).stream().filter(c -> c.getName() != null && c.getName().startsWith("location:")).count();
    }
}
