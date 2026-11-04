package com.itemdroplookup.ui;

import org.junit.Test;
import com.itemdroplookup.model.DropSource;
import static org.junit.Assert.*;

public class DropRatePresentationTest
{
    @Test public void reviewedZulrahRateAlwaysRetainsItsTwoRollBasis()
    {
        String notes = "Rare drop table. Rate and quantity are per roll; two rolls per kill.";
        DropSource source = new DropSource("Dragonstone", "Zulrah", "1", "5/7968", notes);
        String expected = "Rate: 1 in 1593.6 per roll; 2 rolls per kill";
        assertEquals(expected, DropRatePresentation.sourceRateLine(source));
        assertEquals(expected, DropRatePresentation.detailRateLine(source));
        for (DropSource other : new DropSource[] {
                new DropSource("Uncut dragonstone", "Zulrah", "1", "5/7968", notes),
                new DropSource("Dragonstone bolt tips", "Zulrah", "12", "8/249", ""),
                new DropSource("Dragonstone", "Other", "1", "5/7968", notes),
                new DropSource("Dragonstone", "Zulrah", "1", "1/100", "")})
        {
            assertFalse(DropRatePresentation.sourceRateLine(other).contains("2 rolls per kill"));
        }
        assertEquals("5/7968", source.getDropRate());
        assertEquals("Listed rate: 1/128", DropRatePresentation.sourceRateLine(new DropSource("Adamant sq shield", "Night beast", "1", "1/128", "")));
    }

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
