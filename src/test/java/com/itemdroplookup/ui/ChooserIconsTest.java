package com.itemdroplookup.ui;

import com.itemdroplookup.service.ItemIconProvider;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChooserIconsTest
{
    private static final class Request
    {
        String name; BooleanSupplier current; Consumer<BufferedImage> ready;
        Request(String name, BooleanSupplier current, Consumer<BufferedImage> ready)
        { this.name = name; this.current = current; this.ready = ready; }
        void complete() { ready.accept(new BufferedImage(36,32,BufferedImage.TYPE_INT_ARGB)); }
    }
    private static final class Fixture
    {
        boolean showing = true;
        JPanel body = new JPanel(null);
        JScrollPane scroll = new JScrollPane() { @Override public boolean isShowing() { return showing; } };
        List<Request> requests = new ArrayList<>();
        List<JButton> buttons = new ArrayList<>();
        ChooserIcons icons = new ChooserIcons(scroll, (name, current, ready) -> requests.add(new Request(name,current,ready)));
        Fixture(int count)
        {
            scroll.setViewportView(body);
            body.setPreferredSize(new Dimension(200,count*50));
            body.setSize(200,count*50);
            scroll.getViewport().setExtentSize(new Dimension(200,100));
            for (int i=0;i<count;i++)
            {
                JButton button = new LookupStyle(15).button("Item "+i, () -> { }, false);
                button.setBounds(0,i*50,200,50);
                body.add(button); buttons.add(button); icons.add("Item "+i,button);
            }
        }
    }

    @Test public void OnlyVisibleRowsRequestOnceAndPaintingDoesNoWork() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Fixture f = new Fixture(500);
            f.showing=false; f.icons.requestVisible(); assertEquals(0,f.requests.size());
            f.showing=true; f.icons.requestVisible(); assertEquals(2,f.requests.size());
            f.icons.requestVisible(); assertEquals(2,f.requests.size());
            BufferedImage screen=new BufferedImage(200,100,BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g=screen.createGraphics(); f.body.paint(g); g.dispose();
            assertEquals(2,f.requests.size());
            f.scroll.getViewport().setViewPosition(new Point(0,200));
            assertEquals(4,f.requests.size());
            assertEquals("Item 4",f.requests.get(2).name);
            for(JButton button:f.buttons) { assertEquals(36,button.getIcon().getIconWidth()); assertEquals(32,button.getIcon().getIconHeight()); }
            f.icons.dispose();
        });
    }

    @Test public void LateResultsCannotPopulateNewQueryOrDisposedView() throws Exception
    {
        Fixture[] f = new Fixture[1]; Icon[] empty = new Icon[1];
        SwingUtilities.invokeAndWait(() -> {
            f[0]=new Fixture(3); f[0].icons.requestVisible(); empty[0]=f[0].buttons.get(0).getIcon();
            f[0].icons.clear();
            assertFalse(f[0].requests.get(0).current.getAsBoolean());
            f[0].requests.get(0).complete();
            f[0].icons.add("new exact identity", f[0].buttons.get(0)); f[0].icons.requestVisible();
        });
        SwingUtilities.invokeAndWait(() -> {
            assertSame(empty[0], f[0].buttons.get(0).getIcon());
            assertEquals("new exact identity", f[0].requests.get(2).name);
            f[0].requests.get(2).complete(); f[0].icons.dispose();
            assertFalse(f[0].requests.get(2).current.getAsBoolean());
        });
        SwingUtilities.invokeAndWait(() -> assertSame(empty[0],f[0].buttons.get(0).getIcon()));
    }

    @Test public void LoadedAndTextFallbackRowsKeepSizeAndSelection() throws Exception
    {
        Fixture[] f=new Fixture[1]; Dimension[] before=new Dimension[1];
        SwingUtilities.invokeAndWait(() -> {
            f[0]=new Fixture(3); before[0]=f[0].buttons.get(0).getPreferredSize();
            f[0].icons.requestVisible(); f[0].requests.get(0).complete();
        });
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(f[0].buttons.get(0).getIcon() instanceof ImageIcon);
            assertFalse(f[0].buttons.get(1).getIcon() instanceof ImageIcon);
            assertEquals(before[0], f[0].buttons.get(0).getPreferredSize());
            assertEquals(f[0].buttons.get(0).getPreferredSize(), f[0].buttons.get(1).getPreferredSize());
            assertTrue(f[0].buttons.get(1).isEnabled());
            assertEquals("<html>Item 0</html>",f[0].buttons.get(0).getText());
            f[0].icons.dispose();
        });
    }
}
