package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemSearchSelectionTest
{
    private ItemSearchSelection search(String query)
    {
        Gson gson = new Gson();
        return new ItemSearchSelection(query, new DropLookupService(gson), new ItemSourceLookupService(gson), new ItemPriceLookupService(gson));
    }

    @Test public void exactIdentityWinsBeforeFilteringWhileVerifiedPacksSurvive()
    {
        ItemSearchSelection stone = search("dragonstone");
        assertEquals(java.util.Collections.singletonList("Dragonstone"), stone.choices());
        assertTrue(stone.sources.isEmpty());
        assertEquals(4, stone.drops.size()); // Three existing sources plus reviewed Zulrah.
        assertEquals(1, stone.drops.stream().filter(d -> d.getMonsterName().equals("Zulrah")).count());
        assertTrue(search("air rune").sources.stream().anyMatch(s -> s.getPackQuantity() == 100));
        assertTrue(search("air rune pack").sources.stream().allMatch(s -> s.getItemName().equalsIgnoreCase("Air rune pack")));
        assertFalse(search("air rune pack").sources.isEmpty());
    }

    @Test public void potionIdentityPreservesBaseFamiliesAndExplicitDoses()
    {
        ItemSearchSelection family = search("prayer potion");
        assertEquals(1, family.choices().size());
        assertEquals(4, family.drops.stream().map(d -> d.getItemName()).distinct().count());
        ItemSearchSelection dose = search("super restore(4)");
        assertTrue(dose.drops.stream().allMatch(d -> d.getItemName().equalsIgnoreCase("Super restore(4)")));
        assertTrue(dose.sources.isEmpty());
        assertFalse(search("Antidote++").drops.stream().anyMatch(d -> !d.getItemName().startsWith("Antidote++")));
    }

    @Test public void broadItemsAliasesAndCompactNamesRemainDiscoverable()
    {
        assertEquals(9, search("uncut").choices().size());
        assertTrue(search("pouch").choices().size() > 1);
        assertEquals(java.util.Collections.singletonList("rune pouch"), search("runepouch").choices());
        assertEquals(java.util.Collections.singletonList("Dragon boots"), search("dboots").choices());
    }
}
