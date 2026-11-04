package com.itemdroplookup.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.Border;
import org.junit.Test;
import static org.junit.Assert.*;

public class StylingComparisonTest
{
    @Test public void bordersChangePixelsButPreserveTextGeometryAndRestoreExactly() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = LookupStyle.column(); root.setSize(208, 600);
            JButton button = new LookupStyle(16).button("Buy from shops\n38 buying options", () -> { }, false);
            root.add(button);
            Font font = button.getFont();
            String text = button.getText();
            Border border = button.getBorder();
            Insets insets = button.getInsets();
            Dimension size = button.getPreferredSize();
            button.setSize(size);
            int originalPixel = pixel(button, 0, 0);
            for (int attempt = 0; attempt < 3; attempt++)
            {
                ImplementedDesignReview.applyBoundaries(root, true);
                ImplementedDesignReview.applyBoundaries(root, true);
                assertEquals(ImplementedDesignReview.CLEAR_BORDER.getRGB(), pixel(button, 0, 0));
                assertNotEquals(originalPixel, pixel(button, 0, 0));
                assertEquals(insets, button.getInsets());
                assertEquals(size, button.getPreferredSize());
                assertEquals(font, button.getFont());
                assertEquals(text, button.getText());
                assertEquals(LookupStyle.CARD, button.getBackground());
                ImplementedDesignReview.applyBoundaries(root, false);
                assertSame(border, button.getBorder());
                assertEquals(originalPixel, pixel(button, 0, 0));
            }
        });
    }

    @Test public void existingCardOutlineKeepsItsGeometryWithoutAddingNestedBoxesOrAlteringFocus() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = LookupStyle.column(); root.setSize(208, 600);
            JPanel card = LookupStyle.column(); card.setBackground(LookupStyle.CARD);
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0),
                    BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 1, 1,
                            net.runelite.client.ui.ColorScheme.MEDIUM_GRAY_COLOR),
                            BorderFactory.createEmptyBorder(8, 8, 8, 8))));
            card.add(new LookupStyle(13).text("Source and requirement", false));
            root.add(card); card.setSize(208, 80);
            Border original = card.getBorder(); Insets insets = card.getInsets();
            JButton primary = new LookupStyle(13).button("See all sources", () -> { }, true);
            Border primaryBorder = primary.getBorder(); root.add(primary);
            JLabel link = new LookupStyle(13).text("Read requirement", false);
            Border linkBorder = BorderFactory.createLineBorder(LookupStyle.ACCENT);
            link.setBorder(linkBorder); root.add(link);
            JPanel nested = LookupStyle.column(); nested.setBackground(LookupStyle.CARD);
            Border nestedBorder = BorderFactory.createEmptyBorder(6, 8, 6, 8);
            nested.setBorder(nestedBorder); root.add(nested);
            ImplementedDesignReview.applyBoundaries(root, true);
            assertEquals(LookupStyle.CARD.getRGB(), pixel(card, 0, 0));
            assertEquals(ImplementedDesignReview.CLEAR_BORDER.getRGB(), pixel(card, 0, 4));
            assertEquals(insets, card.getInsets());
            assertSame(primaryBorder, primary.getBorder()); assertSame(linkBorder, link.getBorder());
            assertSame(nestedBorder, nested.getBorder());
            ImplementedDesignReview.applyBoundaries(root, false);
            assertSame(original, card.getBorder());
        });
    }

    @Test public void newViewsFollowTheSelectedComparisonWithoutLosingCurrentContent() throws Exception
    {
        JPanel root = new JPanel();
        JComboBox<String> choice = new JComboBox<>(new String[] {"Current", "Clearer"});
        JButton button = new LookupStyle(13).button("Locations & requirements", () -> { }, false);
        Border original = button.getBorder();
        SwingUtilities.invokeAndWait(() -> {
            ImplementedDesignReview.installComparison(root, choice);
            choice.setSelectedIndex(1);
            JPanel nextView = LookupStyle.column(); nextView.add(button); root.add(nextView);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertNotSame(original, button.getBorder());
            choice.setSelectedIndex(0);
            assertSame(original, button.getBorder());
            assertSame(button, ((JPanel) root.getComponent(0)).getComponent(0));
        });
    }

    private static int pixel(JComponent component, int x, int y)
    {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { component.paint(graphics); }
        finally { graphics.dispose(); }
        return image.getRGB(x, y);
    }
}
