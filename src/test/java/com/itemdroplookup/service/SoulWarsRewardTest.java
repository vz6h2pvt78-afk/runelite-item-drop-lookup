package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.ItemSource;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Prices and exact displayed quantities from Soul Wars Reward Shop revision 15337354. */
public class SoulWarsRewardTest
{
    private final ItemSourceLookupService service=new ItemSourceLookupService(new Gson());

    @Test public void verifiedStockTableUsesZealWithNoStockDepletionClaim()
    {
        for (String[] row : new String[][]{
                {"Blighted entangle sack (x70)","10 Zeal Tokens per bundle"},
                {"Blighted teleport spell sack (x50)","10 Zeal Tokens per bundle"},
                {"Blighted vengeance sack (x50)","10 Zeal Tokens per bundle"},
                {"Blighted ancient ice sack (x30)","10 Zeal Tokens per bundle"},
                {"Blighted manta ray (x15)","10 Zeal Tokens per bundle"},
                {"Blighted anglerfish (x15)","10 Zeal Tokens per bundle"},
                {"Blighted karambwan (x12)","10 Zeal Tokens per bundle"},
                {"Blighted super restore(4) (x4)","10 Zeal Tokens per bundle"},
                {"Ectoplasmator","250 Zeal Tokens"}, {"Soul cape","2,500 Zeal Tokens"},
                {"Spoils of war","30 Zeal Tokens"}})
        {
            ItemSource source=service.searchByItemName(row[0]).stream()
                    .filter(s->s.getSourceName().equals("Soul Wars Reward Shop")).findFirst().get();
            assertEquals(row[0],source.getItemName());
            assertEquals(row[1],source.getCost());
            assertEquals("Reward Shop",source.getSourceType());
            assertEquals("Isle of Souls",source.getLocation());
            assertFalse(source.getNotes().contains("stock depletes"));
            assertEquals(0,source.getPackQuantity()); // No new bundle-to-item projection.
        }
    }

    @Test public void correctionCannotLeakToOtherShopsOrCanonicalPotion()
    {
        java.util.List<ItemSource> rows=service.searchByItemName("Blighted super restore(4) (x4)");
        assertEquals(3,rows.size());
        assertTrue(rows.stream().anyMatch(s->s.getSourceName().equals("Bounty Hunter Store")&&s.getCost().equals("2 Bounty Hunter points")));
        assertTrue(rows.stream().anyMatch(s->s.getSourceName().equals("Justine's stuff for the Last Shopper Standing")&&s.getCost().equals("1 LMS points")));
        assertTrue(service.searchByItemName("Blighted super restore(4)").isEmpty());
        for(String q:Arrays.asList("Air rune","Death talisman","Rune pouch"))
            assertFalse(service.searchByItemName(q).stream().anyMatch(s->s.getCost().contains("Zeal")));
    }
}
