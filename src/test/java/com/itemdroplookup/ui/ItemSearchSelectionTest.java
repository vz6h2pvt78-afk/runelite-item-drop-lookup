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

    @Test public void broadDiscoveryFindsWholeWordsWithoutChangingSelectedSources()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        ItemSourceLookupService sources = new ItemSourceLookupService(gson);
        ItemPriceLookupService prices = new ItemPriceLookupService(gson);
        for (String query : new String[]{"sword", "cape", "boots", "ring", "staff", "shield", "axe", "bow", "helm", "key", "ore", "seed"})
        {
            ItemSearchSelection broad = new ItemSearchSelection(query, drops, sources, prices);
            assertFalse(query, broad.choices().isEmpty());
            for (String item : broad.choices())
            {
                ItemSearchSelection exact = new ItemSearchSelection(item, drops, sources, prices);
                assertEquals(query + " / drops / " + item, gson.toJson(exact.dropsFor(item)), gson.toJson(broad.dropsFor(item)));
                assertEquals(query + " / sources / " + item, gson.toJson(exact.sourcesFor(item)), gson.toJson(broad.sourcesFor(item)));
            }
        }
        java.util.List<String> swords = new ItemSearchSelection("sword", drops, sources, prices).choices();
        assertTrue(swords.contains("Bronze sword"));
        assertTrue(swords.contains("Swordfish"));
        assertTrue(swords.indexOf("Bronze sword") < swords.indexOf("Swordfish"));
    }

    @Test public void chooserRankingUsesWordsWithoutInferringItemCategories()
    {
        java.util.List<String> names = new java.util.ArrayList<>(java.util.Arrays.asList(
                "Swordfish", "Godsword shard 1", "Rune sword", "Swords and emblem", "Bronze sword"));
        names.sort(ItemChoiceRanking.forQuery("sword"));
        assertEquals(java.util.Arrays.asList("Bronze sword", "Rune sword", "Swordfish", "Swords and emblem", "Godsword shard 1"), names);
        assertEquals(java.util.Collections.singletonList("Shark"), search("shark").choices());
        assertEquals(java.util.Collections.singletonList("Dragon sword"), search("dragon sword").choices());
        assertEquals(java.util.Collections.singletonList("Black mask"), search("black mask").choices());
        assertEquals(java.util.Collections.singletonList("Amulet of glory(4)"), search("amulet of glory (4)").choices());
        assertEquals(java.util.Collections.singletonList("Rune dagger(p++)"), search("rune dagger p++").choices());
        assertEquals(java.util.Collections.singletonList("Dragon warhammer"), search("dwh").choices());
        assertEquals(java.util.Collections.singletonList("Twisted bow"), search("tbow").choices());
    }

    @Test public void genericWordsGatherAllCompatibleItemsBeforeAutoSelection()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        ItemSourceLookupService sources = new ItemSourceLookupService(gson);
        ItemPriceLookupService prices = new ItemPriceLookupService(gson);
        for (String[] scenario : new String[][]{
                {"potion", "Prayer potion", "Attack potion"},
                {"log", "Logs", "Magic logs"},
                {"axe", "Bronze axe", "Dragon axe"}})
        {
            ItemSearchSelection broad = new ItemSearchSelection(scenario[0], drops, sources, prices);
            assertTrue(scenario[0], broad.choices().size() > 1);
            assertTrue(scenario[0], broad.choices().contains(scenario[1]));
            assertTrue(scenario[0], broad.choices().contains(scenario[2]));
            for (String item : broad.choices())
            {
                ItemSearchSelection exact = new ItemSearchSelection(item, drops, sources, prices);
                assertEquals(scenario[0] + " / drops / " + item, gson.toJson(exact.dropsFor(item)), gson.toJson(broad.dropsFor(item)));
                assertEquals(scenario[0] + " / sources / " + item, gson.toJson(exact.sourcesFor(item)), gson.toJson(broad.sourcesFor(item)));
            }
        }
        assertEquals(java.util.Collections.singletonList("Potion (Apothecary)"), search("Potion (Apothecary)").choices());
        assertEquals(search("log").choices(), search("Logs").choices());
        assertEquals(java.util.Collections.singletonList("Staff"), search("staff").choices());
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

    @Test public void representativePotionArtworkDoesNotNarrowTheSelectedFamily()
    {
        ItemSearchSelection prayer = search("prayer");
        java.util.List<String> choices = prayer.choices();
        assertEquals("Prayer potion(4)", prayer.iconItem("Prayer potion"));
        assertEquals("Prayer mix(2)", prayer.iconItem("Prayer mix"));
        assertEquals("Prayer enhance (+)(4)", prayer.iconItem("Prayer enhance (+)"));
        assertEquals(choices, prayer.choices());
        assertEquals(4, prayer.dropsFor("Prayer potion").stream().map(d -> d.getItemName()).distinct().count());
        ItemSearchSelection exact = search("Prayer potion(1)");
        assertEquals("Prayer potion(1)", exact.iconItem("Prayer potion(1)"));
        assertTrue(exact.drops.stream().allMatch(d -> d.getItemName().equals("Prayer potion(1)")));
        assertEquals("Stamina potion(4)", search("stam").iconItem("Stamina potion"));
        assertEquals("Antidote++(4)", search("Antidote++").iconItem("Antidote++"));
        assertEquals("Red goblin mail", search("red").iconItem("Red goblin mail"));
        assertEquals("Guam leaf", search("herb").iconItem("Herb pack"));
        assertTrue(search("herb").choices().contains("Herb pack"));
        assertEquals("Amulet of glory", search("glory").iconItem("Amulet of glory"));
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
        assertEquals(10, search("uncut").choices().size());
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
        assertEquals("Raw shrimps", prices.findExactItemName("rawshrimps").get());
        assertFalse(prices.findExactItemName("shrimp").isPresent());
        assertEquals("Raw shrimps", prices.findExactItemName("Raw-shrimps").get());
        assertTrue(search("Prayer potion(5)").choices().isEmpty());
        assertTrue(search("Super restore(9)").choices().isEmpty());
    }
}
