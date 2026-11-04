package com.itemdroplookup.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.GlyphView;
import javax.swing.text.View;
import org.junit.Test;
import static org.junit.Assert.*;

public class StylingComparisonTest
{
    @Test public void oneAlternativeMatchesLowercaseHeightAndRestoresOriginalTypography() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            for (int size : new int[] {13, 15, 16, 18})
            {
                JPanel root = LookupStyle.column(); root.setSize(208, 600);
                JButton button = new LookupStyle(size).button("Locations & requirements", () -> { }, false);
                root.add(button);
                Font original = button.getFont();
                String text = button.getText(); Border border = button.getBorder();
                for (int attempt = 0; attempt < 3; attempt++)
                {
                    ReviewFontComparison.apply(root, true);
                    ReviewFontComparison.apply(root, true);
                    assertEquals("Atkinson Hyperlegible", button.getFont().getFamily());
                    assertEquals(ReviewFontComparison.xHeight(original),
                            ReviewFontComparison.xHeight(button.getFont()), 0.05);
                    assertEquals(text, button.getText()); assertSame(border, button.getBorder());
                    assertEquals(LookupStyle.CARD, button.getBackground());
                    assertEquals(original.getTransform(), button.getFont().getTransform());
                    View view = (View) button.getClientProperty(BasicHTML.propertyKey);
                    assertGlyphFamily(view, "Atkinson Hyperlegible");
                    ReviewFontComparison.apply(root, false);
                    assertEquals(original, button.getFont());
                    assertGlyphFamily((View) button.getClientProperty(BasicHTML.propertyKey), "OpenDyslexic");
                }
            }
        });
    }

    @Test public void comparisonReflowsLongRequirementsAtNarrowWidthsWithoutTruncatingText() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = LookupStyle.column();
            JLabel label = new LookupStyle(13).text("Bring a rope unless you have completed both Waterfall Quest and the medium Kandarin Diary.", false);
            root.add(label); String originalText = label.getText();
            for (boolean alternative : new boolean[] {false, true})
            {
                ReviewFontComparison.apply(root, alternative);
                root.setSize(242, 800); int wide = label.getPreferredSize().height;
                root.setSize(170, 800);
                assertTrue(label.getPreferredSize().height > wide);
                assertEquals(170, label.getPreferredSize().width);
                assertEquals(originalText, label.getText());
            }
        });
    }

    @Test public void newlyOpenedViewsUseTheChosenFontAndRestoreTheirOwnOriginal() throws Exception
    {
        JPanel root = new JPanel();
        JComboBox<String> choice = new JComboBox<>(new String[] {"OpenDyslexic", "Atkinson"});
        JButton button = new LookupStyle(13).button("View details", () -> { }, false);
        Font original = button.getFont();
        SwingUtilities.invokeAndWait(() -> {
            ImplementedDesignReview.installComparison(root, choice);
            choice.setSelectedIndex(1);
            JPanel nextView = LookupStyle.column(); nextView.add(button); root.add(nextView);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("Atkinson Hyperlegible", button.getFont().getFamily());
            choice.setSelectedIndex(0);
            assertEquals(original, button.getFont());
            assertSame(button, ((JPanel) root.getComponent(0)).getComponent(0));
        });
    }

    private static void assertGlyphFamily(View view, String family)
    {
        if (view instanceof GlyphView) assertEquals(family, ((GlyphView) view).getFont().getFamily());
        for (int i = 0; i < view.getViewCount(); i++) assertGlyphFamily(view.getView(i), family);
    }
}
