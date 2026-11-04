package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.awt.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverviewFlowTest
{
    private ItemDropLookupPanel panel()
    {
        Gson gson = new Gson();
        return new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
    }
    private void search(Container panel, String query)
    {
        ((JTextField)PanelTestSupport.find(panel, "itemSearch")).setText(query);
        ((JButton)PanelTestSupport.find(panel, "searchButton")).doClick(0);
    }

    @Test public void exactItemsOpenOverviewAndBroadItemsRequireAnExplicitChoice() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel p=panel();
            search(p,"Spade");
            assertNotNull(PanelTestSupport.find(p,"viewAllSources"));
            assertNotNull(PanelTestSupport.find(p,"showGroundSources"));
            assertNull(PanelTestSupport.find(p,"dropSourceCard"));
            ((JButton)PanelTestSupport.find(p,"showGroundSources")).doClick(0);
            assertNotNull(PanelTestSupport.find(p,"itemSourceCard"));
            ((JButton)PanelTestSupport.find(p,"backToOverview")).doClick(0);
            assertNotNull(PanelTestSupport.find(p,"activeFilters"));
            search(p,"uncut");
            assertNotNull(PanelTestSupport.find(p,"selectItem:Uncut emerald"));
            assertNull(PanelTestSupport.find(p,"viewAllSources"));
            ((JButton)PanelTestSupport.find(p,"selectItem:Uncut emerald")).doClick(0);
            assertNotNull(PanelTestSupport.find(p,"viewAllSources"));
            ((JButton)PanelTestSupport.find(p,"backToItems")).doClick(0);
            assertNotNull(PanelTestSupport.find(p,"selectItem:Uncut ruby"));
        });
    }

    @Test public void retainedShopsFilterCannotSilentlyChangeBlackBeadIdentity() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel p=panel();
            search(p,"Spade");
            ((JButton)PanelTestSupport.find(p,"showOtherSources")).doClick(0);
            search(p,"Black bead");
            assertNotNull(PanelTestSupport.find(p,"activeFilters"));
            ((JButton)PanelTestSupport.find(p,"viewCurrentMethod")).doClick(0);
            assertNull(PanelTestSupport.find(p,"dropSourceCard"));
            ((JButton)PanelTestSupport.find(p,"resetSourceFilters")).doClick(0);
            assertNotNull(PanelTestSupport.find(p,"dropSourceCard"));
            assertEquals("Black bead",((JTextField)PanelTestSupport.find(p,"itemSearch")).getText());
        });
    }

    @Test public void wrappedContentGrowsForLargerTextAndNarrowerSpace() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Atkinson Hyperlegible",new LookupStyle(LookupStyle.STANDARD_SIZE).font().getFamily());
            JPanel parent=new JPanel();
            // A short sample can occupy three lines at both widths in Atkinson.
            // This longer requirements/cost example exercises an actual line-count change.
            String text="Mourning's End Part II required. Costs 80 abyssal pearls. "
                    + "Speak to the Rewards Guardian in the Temple of the Eye.";
            JLabel label=new LookupStyle(LookupStyle.STANDARD_SIZE).text(text,false);
            parent.add(label);
            parent.setSize(242,500);
            int wide=label.getPreferredSize().height;
            parent.setSize(170,500);
            assertTrue("Narrow requirements should take more lines",label.getPreferredSize().height>wide);
            JLabel larger=new LookupStyle(LookupStyle.LARGER_SIZE).text(text,false);
            parent.add(larger);
            assertTrue(larger.getPreferredSize().height>=label.getPreferredSize().height);
            assertEquals("Atkinson Hyperlegible",larger.getFont().getFamily());
        });
    }

    @Test public void detailFiltersReturnToTheirOwnSourceAndDoNotLeakToAnotherItem() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel p = panel();
            search(p, "Goblin mail");
            PanelTestSupport.openSources(p);
            ((JButton)PanelTestSupport.find(p,"openLocations")).doClick(0);
            JComboBox<?> member = (JComboBox<?>)PanelTestSupport.find(p,"detailMembership");
            member.setSelectedItem(MonsterLocationFilter.MembershipFilter.F2P);
            ((JButton)PanelTestSupport.find(p,"backToResults")).doClick(0);
            ((JButton)PanelTestSupport.find(p,"openLocations")).doClick(0);
            assertEquals(MonsterLocationFilter.MembershipFilter.F2P, ((JComboBox<?>)PanelTestSupport.find(p,"detailMembership")).getSelectedItem());
            ((JButton)PanelTestSupport.find(p,"backToResults")).doClick(0);
            search(p, "Black bead");
            PanelTestSupport.openSources(p);
            ((JButton)PanelTestSupport.find(p,"openLocations")).doClick(0);
            assertEquals(MonsterLocationFilter.MembershipFilter.ALL, ((JComboBox<?>)PanelTestSupport.find(p,"detailMembership")).getSelectedItem());
        });
    }

    @Test public void wheelScrollsInsteadOfChangingClosedFiltersAndKeyboardSelectionStillWorks() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> combo = new IntentionalComboBox<>(new String[] {"All", "Shops", "Drops"});
            JPanel column = LookupStyle.column();
            column.add(combo);
            for (int i = 0; i < 20; i++) column.add(new LookupStyle(LookupStyle.STANDARD_SIZE).text("Long source " + i, false));
            JScrollPane scroll = new JScrollPane(LookupStyle.scrollBody(column));
            scroll.setSize(242, 200);
            layout(scroll);
            combo.dispatchEvent(new java.awt.event.MouseWheelEvent(combo, java.awt.event.MouseEvent.MOUSE_WHEEL,
                    1, 0, 5, 5, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
            assertEquals("All", combo.getSelectedItem());
            assertTrue("Wheel should move the surrounding list", scroll.getVerticalScrollBar().getValue() > 0);
            assertTrue(combo.isFocusable());
            // Arrow actions require isShowing(); exercise Swing's End binding
            // here, with focused arrow/popup behaviour reserved for live review.
            Action next = combo.getActionMap().get("endPassThrough");
            assertNotNull(next);
            next.actionPerformed(new java.awt.event.ActionEvent(combo, 1, "endPassThrough"));
            assertEquals("Drops", combo.getSelectedItem());
        });
    }

    @Test public void scrollBodyTracksViewportAfterScrollbarAppears() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JPanel column = LookupStyle.column();
            for (int i = 0; i < 25; i++) column.add(new LookupStyle(LookupStyle.LARGER_SIZE).text("A very long source title and its conditions", true));
            JPanel body = LookupStyle.scrollBody(column);
            JScrollPane scroll = new JScrollPane(body);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            for (int width : new int[] {242, 210, 300})
            {
                scroll.setSize(width, 400);
                for (int i = 0; i < 8; i++) layout(scroll);
                assertEquals(scroll.getViewport().getExtentSize().width, body.getWidth());
                for (Component child : column.getComponents()) assertTrue(child.getX() + child.getWidth() <= body.getWidth());
            }
        });
    }

    @Test public void largerTextResizesPersistentControlsAndSurvivesSourceNavigation() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemDropLookupPanel p = panel();
            search(p, "Lyre");
            JComboBox<?> sizes = (JComboBox<?>) PanelTestSupport.find(p, "textSize");
            sizes.setSelectedIndex(1);
            for (String name : new String[] {"itemSearch", "showPrice", "textSize"})
            {
                JComponent control = (JComponent) PanelTestSupport.find(p, name);
                assertEquals("Atkinson Hyperlegible", control.getFont().getFamily());
                assertEquals(LookupStyle.LARGER_SIZE, control.getFont().getSize());
                assertTrue(control.getMaximumSize().height >= control.getPreferredSize().height);
            }
            PanelTestSupport.openSources(p);
            JButton details = (JButton) PanelTestSupport.find(p, "openLocations");
            assertEquals(LookupStyle.LARGER_SIZE, details.getFont().getSize());
            details.doClick(0);
            JButton back = (JButton) PanelTestSupport.find(p, "backToResults");
            assertEquals(LookupStyle.LARGER_SIZE, back.getFont().getSize());
            back.doClick(0);
            assertSame(details, PanelTestSupport.find(p, "openLocations"));
            sizes.setSelectedIndex(0);
            assertEquals(LookupStyle.STANDARD_SIZE, PanelTestSupport.find(p, "openLocations").getFont().getSize());
        });
    }

    private static void layout(Container root)
    {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container)child);
    }

    @Test public void pendingSourceFinderCannotReplaceANewerItemOverview() throws Exception
    {
        ItemDropLookupPanel[] current = new ItemDropLookupPanel[1];
        java.util.concurrent.CompletableFuture<Void> settled = new java.util.concurrent.CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> {
            current[0] = panel();
            search(current[0], "Coins");
            PanelTestSupport.openSources(current[0]);
            JTextField finder = (JTextField) PanelTestSupport.find(current[0], "sourceSearch");
            finder.setText("z"); finder.setText("zo"); finder.setText("zombie");
            search(current[0], "Black bead");
            Timer settle = new Timer(300, e -> settled.complete(null));
            settle.setRepeats(false); settle.start();
        });
        settled.get(5, java.util.concurrent.TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {
            assertNotNull(PanelTestSupport.find(current[0], "viewAllSources"));
            assertNull(PanelTestSupport.find(current[0], "dropSourceCard"));
            assertEquals("Black bead", ((JTextField)PanelTestSupport.find(current[0], "itemSearch")).getText());
            PanelTestSupport.openSources(current[0]);
            assertEquals("", ((JTextField)PanelTestSupport.find(current[0], "sourceSearch")).getText());
            assertNotNull(PanelTestSupport.find(current[0], "dropSourceCard"));
        });
    }
}
