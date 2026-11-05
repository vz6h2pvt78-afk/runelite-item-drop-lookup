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

    @Test public void reviewedFamilyIdentitySurvivesMissingSourcesAndIntrinsicNumerals()
    {
        for (String name : new String[]{"Stamina potion", "Super energy", "Serum 207"})
        {
            ItemSearchSelection selected = search(name);
            assertEquals(java.util.Collections.singletonList(name), selected.choices());
            assertTrue(selected.drops.isEmpty());
            assertTrue(selected.sources.isEmpty());
        }
        assertTrue(search("Prayer potion(5)").choices().isEmpty());
        assertTrue(search("Serum 207(5)").choices().isEmpty());
        assertEquals(java.util.Collections.singletonList("Prayer potion(3)"), search("Prayer potion(3)").choices());
        assertEquals(java.util.Collections.singletonList("Super restore"), search("super restore potion").choices());
        assertEquals(java.util.Collections.singletonList("Stamina potion"), search("stam").choices());
        assertEquals(java.util.Collections.singletonList("Prayer potion"), search("ppot").choices());
        assertEquals(java.util.Collections.singletonList("Ranging potion"), search("range pot").choices());
        assertEquals(java.util.Collections.singletonList("Super energy"), search("super energy potion").choices());
    }

    @Test public void exactIdentityWinsBeforeFilteringWhileVerifiedPacksSurvive()
    {
        ItemSearchSelection stone = search("dragonstone");
        assertEquals(java.util.Collections.singletonList("Dragonstone"), stone.choices());
        assertEquals(2, stone.sources.size());
        assertTrue(stone.sources.stream().allMatch(s -> "Loot & containers".equals(s.getSourceType()) && "Dragonstone".equals(s.getItemName())));
        assertTrue(stone.sources.stream().anyMatch(s -> "Crystal impling loot".equals(s.getSourceName())));
        assertTrue(stone.sources.stream().anyMatch(s -> "Dragon impling loot".equals(s.getSourceName())));
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

    @Test public void exactCatalogIdentityCannotBorrowAnotherItemsCoverage()
    {
        ItemSearchSelection cooked = search("  SHRIMPS  ");
        assertEquals(java.util.Collections.singletonList("Shrimps"), cooked.choices());
        assertTrue(cooked.drops.isEmpty());
        assertEquals(1, cooked.sources.size());
        assertEquals("Production", cooked.sources.get(0).getSourceType());
        assertEquals("Shrimps", cooked.sources.get(0).getItemName());
        assertTrue(search("Cooked chicken").sources.isEmpty());
        assertEquals(java.util.Collections.singletonList("Raw shrimps"), search("Raw shrimps").choices());
        ItemPriceLookupService prices = new ItemPriceLookupService(new Gson());
        assertFalse(prices.findExactItemName("rawshrimps").isPresent());
        assertFalse(prices.findExactItemName("shrimp").isPresent());
        assertFalse(prices.findExactItemName("Raw-shrimps").isPresent());
        assertTrue(search("Prayer potion(5)").choices().isEmpty());
        assertTrue(search("Super restore(9)").choices().isEmpty());
    }
}
