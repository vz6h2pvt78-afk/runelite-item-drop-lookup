package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChooserPanelTest
{
    private static final class Pending
    {
        final String name; final BooleanSupplier current; final Consumer<BufferedImage> ready;
        Pending(String name, BooleanSupplier current, Consumer<BufferedImage> ready)
        { this.name=name; this.current=current; this.ready=ready; }
        void complete() { ready.accept(new BufferedImage(36,32,BufferedImage.TYPE_INT_ARGB)); }
    }
    private final List<Pending> pending=new ArrayList<>();
    private final List<String> reports=new ArrayList<>();
    private ItemDropLookupPanel panel;

    private void start()
    {
        Gson gson=new Gson();
        panel=new ItemDropLookupPanel(new DropLookupService(gson),new ItemSourceLookupService(gson),
                new ItemPriceLookupService(gson),new MonsterLocationLookupService(gson),new AccessRequirementLookupService(gson),
                reports::add,(name,current,ready)->pending.add(new Pending(name,current,ready)));
        panel.getWrappedPanel().addNotify();
        layout(650);
    }
    private void layout(int height)
    {
        panel.getWrappedPanel().setSize(242,height);
        for(int i=0;i<10;i++) AcquisitionPreview.layout(panel.getWrappedPanel());
    }
    private void search(String query)
    {
        // No native window in this test: hide the caret while editing its field.
        JTextField field=(JTextField)PanelTestSupport.find(panel,"itemSearch");
        field.setVisible(false);
        MorningFeedbackTest.search(panel,query);
        field.setVisible(true);
    }
    private List<JButton> choices(Container root)
    {
        List<JButton> result=new ArrayList<>();
        for(Component c:root.getComponents())
        {
            if(c instanceof JButton && c.getName()!=null && c.getName().startsWith("selectItem:")) result.add((JButton)c);
            if(c instanceof Container) result.addAll(choices((Container)c));
        }
        return result;
    }

    @Test public void ChooserNavigationRejectsOldCallbacksAndRestoresExactNames() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { start(); search("uncut"); layout(1000); });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(9,choices(panel).size()); assertEquals(9,pending.size());
            for(int i=0;i<9;i++) assertEquals(choices(panel).get(i).getName(),"selectItem:"+pending.get(i).name);
            MorningFeedbackTest.click(panel,"copyReport");
            MorningFeedbackTest.click(panel,"selectItem:Uncut emerald");
            for(Pending p:pending) { assertFalse(p.current.getAsBoolean()); p.complete(); }
            MorningFeedbackTest.click(panel,"backToItems"); layout(1000);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(18,pending.size());
            for(JButton button:choices(panel)) assertFalse(button.getIcon() instanceof ImageIcon);
            for(int i=9;i<18;i++) pending.get(i).complete();
        });
        SwingUtilities.invokeAndWait(() -> {
            for(JButton button:choices(panel)) assertTrue(button.getIcon() instanceof ImageIcon);
            MorningFeedbackTest.click(panel,"copyReport"); assertEquals(reports.get(0),reports.get(1));
            search("dragon dagger"); layout(650);
            assertTrue(MorningFeedbackTest.labels(panel).contains("Dragon dagger"));
            assertFalse(MorningFeedbackTest.labels(panel).contains("Uncut emerald"));
            panel.dispose(); panel.getWrappedPanel().removeNotify();
        });
    }

    @Test public void potionChooserRequestsExactPicturesWhileKeepingFamilyButtons() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { start(); search("prayer"); layout(1000); });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(3, choices(panel).size());
            assertEquals(3, pending.size());
            assertTrue(pending.stream().anyMatch(p -> p.name.equals("Prayer potion(4)")));
            assertTrue(pending.stream().anyMatch(p -> p.name.equals("Prayer mix(2)")));
            assertTrue(pending.stream().anyMatch(p -> p.name.equals("Prayer enhance (+)(4)")));
            JButton choice = (JButton) PanelTestSupport.find(panel, "selectItem:Prayer mix");
            assertTrue(choice.getToolTipText().contains("Prayer mix(2)"));
            choice.doClick(0); layout(1000);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(pending.stream().anyMatch(p -> p.current.getAsBoolean() && p.name.equals("Prayer mix(2)")));
            MorningFeedbackTest.click(panel, "copyReport");
            assertTrue(reports.get(0).contains("Item: Prayer mix\n"));
            assertFalse(reports.get(0).contains("Item: Prayer mix(2)"));
            panel.dispose(); panel.getWrappedPanel().removeNotify();
        });
    }

    @Test public void BroadChooserLoadsOnlyViewportAndFallbackRetainsKeyboardButtons() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { start(); search("rune"); layout(650); });
        SwingUtilities.invokeAndWait(() -> {
            List<JButton> choices=choices(panel);
            assertTrue(choices.size()>20);
            assertTrue(pending.size()>0); assertTrue(pending.size()<choices.size());
            assertTrue(pending.size()<15);
            for(JButton button:choices)
            {
                assertEquals(36,button.getIcon().getIconWidth());
                assertTrue(button.isFocusable()); assertTrue(button.isEnabled());
                assertTrue(button.getWidth()<=242); assertTrue(button.getHeight()>=32);
            }
            int oldRequests=pending.size();
            panel.getScrollPane().getVerticalScrollBar().setValue(900);
            assertTrue(pending.size()>oldRequests);
            search("zzzz-no-match");
            for(Pending p:pending) assertFalse(p.current.getAsBoolean());
            panel.dispose(); panel.getWrappedPanel().removeNotify();
        });
    }
}
