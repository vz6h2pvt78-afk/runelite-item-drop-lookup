package com.itemdroplookup.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropRatePresentationTest
{
    @Test public void roundsExactDecimalInputsWithoutLosingTheirMeaning()
    {
        assertEquals("About 1 in 17.1", DropRatePresentation.format("30/512"));
        assertEquals("1 in 25.6", DropRatePresentation.format("5/128"));
        assertEquals("About 1 in 21.3", DropRatePresentation.format("6/128"));
        assertEquals("1 in 128", DropRatePresentation.format("1/128"));
        assertEquals("1 in 5012.5", DropRatePresentation.format("1/5012.5"));
        assertEquals("127/128", DropRatePresentation.format("127/128"));
    }

    @Test public void unsupportedOrMissingRatesDoNotBecomeProbabilities()
    {
        for (String raw : new String[] {"Always", "Varies", "Unknown", "Rare", "0/128", "1/0", "4/3", "1/128 (on task)", "NaN"})
            assertEquals(raw, DropRatePresentation.format(raw));
        assertEquals("Not recorded", DropRatePresentation.format(null));
        assertEquals("Not recorded", DropRatePresentation.format(""));
        assertTrue(DropRatePresentation.unresolvedEvent("Ent"));
        assertTrue(DropRatePresentation.unresolvedEvent("Night beast"));
    }
}
