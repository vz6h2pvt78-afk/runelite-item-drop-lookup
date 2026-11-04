package com.itemdroplookup.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StylingComparisonTest
{
    @Test public void colourComparisonChangesSurfacesWithoutChangingTypography() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = new JPanel();
            JPanel card = LookupStyle.column();
            card.setBackground(LookupStyle.CARD);
            JLabel text = new LookupStyle(13).text("Spade", true);
            Font original = text.getFont();
            card.add(text); root.add(card);
            ImplementedDesignReview.applyTreatment(root, true);
            assertEquals(new Color(67,55,39),card.getBackground());
            assertEquals(new Color(244,228,188),text.getForeground());
            assertEquals(original,text.getFont());
            ImplementedDesignReview.applyTreatment(root, false);
            assertEquals(LookupStyle.CARD,card.getBackground());
            assertEquals(LookupStyle.TEXT,text.getForeground());
            assertEquals(original,text.getFont());
        });
    }
}
