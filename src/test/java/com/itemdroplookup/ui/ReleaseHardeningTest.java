package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Bounded, repeatable release sequences; no game inputs or real clipboard. */
public class ReleaseHardeningTest
{
    private final Gson gson=new Gson();
    private ItemDropLookupPanel panel(Consumer<String> writer, ItemIconProvider provider)
    {
        return new ItemDropLookupPanel(new DropLookupService(gson),new ItemSourceLookupService(gson),
                new ItemPriceLookupService(gson),new MonsterLocationLookupService(gson),new AccessRequirementLookupService(gson),writer,provider);
    }
    private static void search(ItemDropLookupPanel panel,String query)
    {
        JTextField field=(JTextField)PanelTestSupport.find(panel,"itemSearch");
        field.setVisible(false); MorningFeedbackTest.search(panel,query); field.setVisible(true);
    }
    private static void layout(ItemDropLookupPanel panel)
    {
        panel.getWrappedPanel().setSize(242,650);
        for(int i=0;i<10;i++)AcquisitionPreview.layout(panel.getWrappedPanel());
    }
    private static void backToPrevious(ItemDropLookupPanel panel)
    {
        JTextField field=(JTextField)PanelTestSupport.find(panel,"itemSearch");
        field.setVisible(false); MorningFeedbackTest.click(panel,"backToPreviousItem"); field.setVisible(true);
    }
    private static List<JButton> choices(Container root)
    {
        List<JButton> rows=new ArrayList<>();
        for(Component c:root.getComponents())
        {
            if(c instanceof JButton && c.getName()!=null && c.getName().startsWith("selectItem:"))rows.add((JButton)c);
            if(c instanceof Container)rows.addAll(choices((Container)c));
        }
        return rows;
    }
    private static final class Request
    {
        final BooleanSupplier current; final Consumer<BufferedImage> ready;
        Request(BooleanSupplier current,Consumer<BufferedImage> ready) {this.current=current;this.ready=ready;}
    }

    @Test public void repeatedQueryBackZeroSourceAndRecreationKeepCleanState() throws Exception
    {
        for(int cycle=0;cycle<12;cycle++)
        {
            List<Request> pending=new ArrayList<>(); List<String> reports=new ArrayList<>();
            ItemDropLookupPanel[] p=new ItemDropLookupPanel[1];
            SwingUtilities.invokeAndWait(()->{
                p[0]=panel(reports::add,(name,current,ready)->pending.add(new Request(current,ready)));
                assertEquals("",((JTextField)PanelTestSupport.find(p[0],"itemSearch")).getText());
                assertNull(PanelTestSupport.find(p[0],"backToPreviousItem"));
                p[0].getWrappedPanel().addNotify(); search(p[0],"uncut"); layout(p[0]);
            });
            SwingUtilities.invokeAndWait(()->{
                assertEquals(9,choices(p[0]).size()); assertTrue(pending.size()>0);
                p[0].getScrollPane().getVerticalScrollBar().setValue(800);
                MorningFeedbackTest.click(p[0],"selectItem:Uncut emerald"); PanelTestSupport.openSources(p[0]);
                ((JComboBox<?>)PanelTestSupport.find(p[0],"sourceTypeFilter")).setSelectedItem("NPC Drops");
                MorningFeedbackTest.click(p[0],"openLocations");
                MorningFeedbackTest.click(p[0],"backToResults");
                search(p[0],"pouch"); MorningFeedbackTest.click(p[0],"selectItem:rune pouch");
                search(p[0],"zzzz-no-item"); backToPrevious(p[0]);
                assertTrue(MorningFeedbackTest.labels(p[0]).toLowerCase().contains("rune pouch"));
                search(p[0],"Cooked chicken"); MorningFeedbackTest.click(p[0],"copyReport");
                assertTrue(reports.get(0).contains("Item: Cooked chicken\n"));
                assertTrue(reports.get(0).contains("View: No sources\n"));
                assertFalse(reports.get(0).contains("Find source:"));
                assertFalse(reports.get(0).contains("Method:"));
                assertFalse(MorningFeedbackTest.labels(p[0]).contains("No matching item"));
                backToPrevious(p[0]);
                search(p[0],"rune"); layout(p[0]); search(p[0],"Cooked chicken");
                assertTrue(MorningFeedbackTest.labels(p[0]).contains("No acquisition sources recorded"));
                p[0].dispose(); p[0].getWrappedPanel().removeNotify();
                for(Request r:pending)assertFalse(r.current.getAsBoolean());
            });
            // Deliver after disposal from a non-EDT worker, as the native service does.
            for(Request r:pending)r.ready.accept(new BufferedImage(36,32,BufferedImage.TYPE_INT_ARGB));
            SwingUtilities.invokeAndWait(()->{
                assertTrue(MorningFeedbackTest.labels(p[0]).contains("No acquisition sources recorded"));
                assertEquals(0,choices(p[0]).size());
            });
        }
    }

    @Test public void disposedSourceFinderDoesNotFireItsPendingDebounce() throws Exception
    {
        CompletableFuture<Void> settled=new CompletableFuture<>();
        ItemDropLookupPanel[] p=new ItemDropLookupPanel[1]; Component[] oldCard=new Component[1];
        SwingUtilities.invokeAndWait(()->{
            p[0]=panel(text->{},ItemIconProvider.NONE);search(p[0],"Goblin mail");PanelTestSupport.openSources(p[0]);
            oldCard[0]=PanelTestSupport.find(p[0],"dropSourceCard");assertNotNull(oldCard[0]);
            ((JTextField)PanelTestSupport.find(p[0],"sourceSearch")).setText("definitely-no-source");
            p[0].dispose();
            Timer wait=new Timer(350,e->settled.complete(null));wait.setRepeats(false);wait.start();
        });
        settled.get(2,TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(()->assertSame(oldCard[0],PanelTestSupport.find(p[0],"dropSourceCard")));
    }

    @Test public void lockedClipboardFailsOncePerClickAcrossViewsAndDisposal() throws Exception
    {
        AtomicInteger attempts=new AtomicInteger();CompletableFuture<Void> settled=new CompletableFuture<>();
        SwingUtilities.invokeAndWait(()->{
            ItemDropLookupPanel p=panel(text->{attempts.incrementAndGet();throw new IllegalStateException("clipboard busy");},ItemIconProvider.NONE);
            for(String query:new String[]{"Cooked chicken","uncut","Goblin mail"})
            {
                search(p,query);
                if(query.equals("Goblin mail")){PanelTestSupport.openSources(p);MorningFeedbackTest.click(p,"openLocations");}
                MorningFeedbackTest.click(p,"copyReport");
                JLabel status=(JLabel)PanelTestSupport.find(p,"copyReportStatus");
                assertTrue(status.isVisible());assertEquals("<html>Could not copy; try again</html>",status.getText());
            }
            p.dispose();Timer wait=new Timer(350,e->settled.complete(null));wait.setRepeats(false);wait.start();
        });
        settled.get(2,TimeUnit.SECONDS);assertEquals(3,attempts.get());
    }

    @Test public void repeatedLargeChooserClearsListenersAndRejectsLateCallbacks() throws Exception
    {
        for(int cycle=0;cycle<20;cycle++)
        {
            SwingUtilities.invokeAndWait(()->{
                JScrollPane scroll=new JScrollPane(){@Override public boolean isShowing(){return true;}};
                JPanel body=new JPanel(null);scroll.setViewportView(body);
                int listeners=scroll.getViewport().getChangeListeners().length;
                int hierarchy=scroll.getHierarchyListeners().length;
                List<Request> pending=new ArrayList<>();
                ChooserIcons icons=new ChooserIcons(scroll,(name,current,ready)->pending.add(new Request(current,ready)));
                body.setSize(200,25000);body.setPreferredSize(new Dimension(200,25000));
                scroll.getViewport().setExtentSize(new Dimension(200,400));
                for(int n=0;n<500;n++) {JButton b=new JButton("item "+n);b.setBounds(0,n*50,200,50);body.add(b);icons.add("item "+n,b);}
                icons.requestVisible();assertEquals(8,pending.size());
                scroll.getViewport().setViewPosition(new Point(0,24000));assertEquals(16,pending.size());
                icons.clear();body.removeAll();icons.dispose();icons.dispose();
                assertEquals(listeners,scroll.getViewport().getChangeListeners().length);
                assertEquals(hierarchy,scroll.getHierarchyListeners().length);
                assertEquals(0,body.getComponentCount());
                for(Request r:pending)assertFalse(r.current.getAsBoolean());
            });
        }
    }
}
