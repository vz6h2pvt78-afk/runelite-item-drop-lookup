package com.itemdroplookup.ui;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;

/** One authorized alternative, local to the development review; never shipped in the plugin. */
final class ReviewFontComparison
{
    private static final String ORIGINAL = "reviewOriginalFont";
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final Font ALTERNATIVE = loadAlternative();
    private static final Map<Font, Font> MATCHED = new HashMap<>();

    static double xHeight(Font font)
    {
        return font.createGlyphVector(METRICS, "x").getVisualBounds().getHeight();
    }

    static Font matchHeight(Font original)
    {
        return MATCHED.computeIfAbsent(original, reference -> {
            Font candidate = ALTERNATIVE.deriveFont(reference.getStyle(), reference.getSize2D());
            float size = (float) (candidate.getSize2D() * xHeight(reference) / xHeight(candidate));
            return candidate.deriveFont(size);
        });
    }

    static void apply(Container parent, boolean alternative)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JComponent)
            {
                JComponent component = (JComponent) child;
                Font original = (Font) component.getClientProperty(ORIGINAL);
                Font current = component.getFont();
                if (current != null && current.getFamily(Locale.ROOT).equals("OpenDyslexic"))
                {
                    original = current;
                    component.putClientProperty(ORIGINAL, original);
                }
                if (original != null) component.setFont(alternative ? matchHeight(original) : original);
            }
            if (child instanceof Container) apply((Container) child, alternative);
        }
    }

    private static Font loadAlternative()
    {
        try (InputStream resource = ReviewFontComparison.class.getResourceAsStream("/fonts/AtkinsonHyperlegible-Regular.ttf"))
        {
            if (resource == null) throw new IllegalStateException("Missing review font");
            Font font = Font.createFont(Font.TRUETYPE_FONT, resource);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        }
        catch (Exception exception) { throw new IllegalStateException("Cannot load review font", exception); }
    }
}
