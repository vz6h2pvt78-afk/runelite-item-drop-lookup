package com.itemdroplookup.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Local authoring tool: renders the exact Swing components used by the preview sidebar. */
public final class DesignConceptPreview
{
    public static void main(String[] args) throws Exception
    {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        File out = new File(args.length == 0 ? "build/design-concepts" : args[0]);
        if (!out.isDirectory() && !out.mkdirs()) throw new IllegalStateException("Cannot create output folder");
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                for (int size : new int[] {13, 16})
                {
                    DesignConceptPanel a = new DesignConceptPanel(false, size);
                    DesignConceptPanel b = new DesignConceptPanel(true, size);
                    for (String item : new String[] {"Spade", "Iron ore", "Raw bear meat", "uncut", "Lensa"})
                    {
                        if (item.equals("Lensa")) { a.showLensa(); b.showLensa(); }
                        else { a.search(item); b.search(item); }
                        BufferedImage combined = new BufferedImage(520, 840, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = combined.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(DesignConceptPanel.BACK); g.fillRect(0, 0, 520, 840);
                        g.setFont(DesignConceptPanel.BODY.deriveFont(14f)); g.setColor(DesignConceptPanel.ACCENT);
                        g.drawString("A · Search first", 10, 25); g.drawString("B · Item overview", 278, 25);
                        paint(a, g, 0, 40); paint(b, g, 278, 40); g.dispose();
                        ImageIO.write(combined, "png", new File(out, item.replace(' ', '-') + "-" + size + ".png"));
                    }
                }
                System.out.println("Font loaded: " + DesignConceptPanel.BODY.getFontName());
                System.out.println("Rendered both concepts at 242px width, 13px and 16px body sizes: " + out.getAbsolutePath());
            }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    private static void paint(JComponent component, Graphics2D output, int x, int y)
    {
        component.setSize(242, 800);
        for (int i = 0; i < 5; i++) layout(component);
        Graphics2D g = (Graphics2D) output.create(x, y, 242, 800);
        component.printAll(g); g.dispose();
    }

    private static void layout(Container root)
    {
        root.doLayout();
        for (Component c : root.getComponents()) if (c instanceof Container) layout((Container)c);
    }
}
