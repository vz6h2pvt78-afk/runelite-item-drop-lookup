package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.ItemSource;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Lightweight checks for the tiered (most-specific non-empty tier) matching in
 * {@link ItemSourceLookupService}. Uses a plain Gson and the bundled resource JSON
 * (curated + generated), so no RuneLite/DI wiring is needed.
 */
public class ItemSourceLookupServiceTest
{
    private final ItemSourceLookupService service = new ItemSourceLookupService(new Gson());

    private static String norm(String value)
    {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean containsItem(List<ItemSource> results, String normalizedItemName)
    {
        return results.stream().anyMatch(r -> norm(r.getItemName()).equals(normalizedItemName));
    }

    @Test
    public void runePouchExactDoesNotReturnRunePouchNote()
    {
        List<ItemSource> results = service.searchByItemName("rune pouch");

        assertFalse("expected at least one rune pouch source", results.isEmpty());
        // Most specific tier (exact) wins, so every result is exactly "rune pouch".
        assertTrue("every result should be itemName 'rune pouch'",
                results.stream().allMatch(r -> norm(r.getItemName()).equals("rune pouch")));
        assertFalse("'rune pouch note' must not appear for an exact 'rune pouch' query",
                containsItem(results, "rune pouch note"));
    }

    @Test
    public void compactRunePouchMatchesSpacedItem()
    {
        List<ItemSource> results = service.searchByItemName("runepouch");

        assertFalse("expected compact match for 'runepouch'", results.isEmpty());
        assertTrue("compact query should resolve to 'rune pouch' only",
                results.stream().allMatch(r -> norm(r.getItemName()).equals("rune pouch")));
        assertFalse(containsItem(results, "rune pouch note"));
    }

    @Test
    public void runePouchDirectRewardsHaveVerifiedCostsWithoutDuplicatingGeneratedRows()
    {
        for (String query : new String[] {"rune pouch", "runepouch"})
        {
            List<ItemSource> results = service.searchByItemName(query);
            org.junit.Assert.assertEquals(3, results.size());
            assertTrue(results.stream().anyMatch(r -> "Slayer Rewards".equals(r.getSourceName())
                    && "750 Slayer reward points".equals(r.getCost())));
            assertTrue(results.stream().anyMatch(r -> "Justine's stuff for the Last Shopper Standing".equals(r.getSourceName())
                    && "75 LMS points".equals(r.getCost())));
            assertTrue(results.stream().anyMatch(r -> "Mage Training Arena Rewards".equals(r.getSourceName())
                    && "150 telekinetic + 150 graveyard + 1,500 enchantment + 200 alchemist pizazz points".equals(r.getCost())));
        }
        List<ItemSource> notes = service.searchByItemName("rune pouch note");
        org.junit.Assert.assertEquals(1, notes.size());
        assertTrue(notes.stream().allMatch(r -> "rune pouch note".equals(norm(r.getItemName()))
                && "75 LMS points".equals(r.getCost())));
    }

    @Test
    public void broadPouchQueryMayReturnRelatedItems()
    {
        List<ItemSource> pouch = service.searchByItemName("pouch");
        List<ItemSource> runePouch = service.searchByItemName("rune pouch");

        // The broad "contains" tier should be at least as wide as the exact query.
        assertTrue("'pouch' should be no narrower than 'rune pouch'",
                pouch.size() >= runePouch.size());
        // And it should surface items beyond the exact "rune pouch" (e.g. the note).
        assertTrue("'pouch' should include a non-'rune pouch' related item",
                pouch.stream().anyMatch(r -> !norm(r.getItemName()).equals("rune pouch")));
    }

    @Test
    public void deathTalismanRetainsOneConditionalPearlOfferWithoutLeakingItsUnlock()
    {
        List<ItemSource> offers = service.searchByItemName("Death talisman");
        org.junit.Assert.assertEquals(1, offers.stream()
                .filter(s -> "Temple Supplies".equals(s.getSourceName())).count());
        ItemSource offer = offers.stream().filter(s -> "Temple Supplies".equals(s.getSourceName()))
                .findFirst().get();
        org.junit.Assert.assertEquals("Reward Shop", offer.getSourceType());
        org.junit.Assert.assertEquals("80 Abyssal pearls", offer.getCost());
        assertTrue(offer.getNotes().contains("Requires Mourning's End Part II completion"));
        assertFalse(offer.getNotes().contains("stock depletes"));
        assertFalse(offer.getCost().contains("coins"));
        assertTrue(service.searchByItemName("Fire talisman").stream()
                .anyMatch(s -> "Temple Supplies".equals(s.getSourceName())));
        assertFalse(service.searchByItemName("Fire talisman").stream()
                .anyMatch(s -> s.getNotes().contains("Mourning's End Part II")));
    }

    @Test
    public void templeSuppliesUsesPearlsAndKeepsEachTalismanUnlockLocal()
    {
        String[][] cases = {{"Fire talisman", "10 Abyssal pearls", ""},
                {"Law talisman", "50 Abyssal pearls", "Troll Stronghold"},
                {"Blood talisman", "100 Abyssal pearls", "Sins of the Father"},
                {"Guardian's eye", "3,000 Abyssal pearls", ""}};
        for (String[] expected : cases)
        {
            ItemSource offer = service.searchByItemName(expected[0]).stream()
                    .filter(s -> "Temple Supplies".equals(s.getSourceName())).findFirst().get();
            org.junit.Assert.assertEquals(expected[1], offer.getCost());
            assertFalse(offer.getNotes().contains("stock depletes"));
            assertFalse(offer.getNotes().contains("Mourning's End Part II"));
            if (expected[2].isEmpty())
            {
                assertFalse(offer.getNotes().contains("completion"));
            }
            else
            {
                assertTrue(offer.getNotes().contains(expected[2] + " completion"));
            }
        }
    }

    @Test
    public void gracefulHoodStillReturnsSources()
    {
        List<ItemSource> results = service.searchByItemName("graceful hood");

        assertFalse("graceful hood should still return sources", results.isEmpty());
        assertTrue(containsItem(results, "graceful hood"));
    }

    @Test
    public void spadeStillReturnsShopSources()
    {
        List<ItemSource> results = service.searchByItemName("spade");

        assertFalse("spade should still return shop sources", results.isEmpty());
        assertTrue(containsItem(results, "spade"));
    }

    @Test
    public void raidResupplyChestIsNotOfferedAsAnOrdinaryShop()
    {
        for (String item : new String[] {"Prayer potion(4)", "Saradomin brew(4)", "Super restore(4)",
                "Stamina potion(4)", "Shark", "Manta ray", "Sea turtle", "Mushroom potato"})
        {
            assertFalse(service.searchByItemName(item).stream()
                    .anyMatch(source -> "Chest (Theatre of Blood)".equals(source.getSourceName())));
        }
        assertTrue(service.searchByItemName("raw shark").stream()
                .anyMatch(source -> "Shop".equals(source.getSourceType())));
    }

    @Test
    public void zeroStockShopsAreHiddenWhileStockedShopsRemain()
    {
        List<ItemSource> sharks = service.searchByItemName("raw shark");
        assertTrue(sharks.stream().anyMatch(source -> "Frankie's Fishing Emporium".equals(source.getSourceName())));
        assertTrue(sharks.stream().anyMatch(source -> "Rufus' Meat Emporium".equals(source.getSourceName())));
        assertFalse(sharks.stream().anyMatch(source -> "Etceteria Fish".equals(source.getSourceName())));
        assertFalse(sharks.stream().anyMatch(source -> source.getNotes().contains("Default stock: 0.")));
        List<ItemSource> coal = service.searchByItemName("coal");
        assertTrue(coal.stream().anyMatch(source -> "Ore seller.".equals(source.getSourceName())));
        assertFalse(coal.stream().anyMatch(source -> "Drogo's Mining Emporium.".equals(source.getSourceName())));
        assertTrue(service.searchByItemName("air rune").stream().anyMatch(source -> source.getPackQuantity() == 100));
    }

    @Test
    public void looseItemSearchesIncludeVerifiedPackContentsWithoutChangingItemIdentity()
    {
        for (String item : new String[] {"Air rune", "Water rune", "Earth rune", "Fire rune", "Mind rune", "Chaos rune",
                "Eye of newt", "Vial of water", "Vial", "Feather", "Amylase crystal"})
        {
            List<ItemSource> results = service.searchByItemName(item);
            assertTrue(item, results.stream().allMatch(source -> item.equalsIgnoreCase(source.getItemName())));
            assertTrue(item, results.stream().anyMatch(source -> source.getPackQuantity() == 100));
        }
        List<ItemSource> packs = service.searchByItemName("airrunepack");
        assertTrue(packs.stream().allMatch(source -> "Air rune pack".equals(source.getItemName())));
        assertTrue(service.searchByItemName("air rune").stream().anyMatch(source -> source.getPackQuantity() == 0));
        assertTrue(service.searchByItemName("eye of newt").stream().anyMatch(ItemSource::isPackNoted));
    }

    @Test
    public void packPricesKeepTheVerifiedCurrencyAndRandomPacksAreNotGuaranteedSources()
    {
        assertTrue(service.searchByItemName("air rune").stream().anyMatch(source ->
                "Tzhaar air rune pack".equals(source.getPackName()) && "645 Tokkul".equals(source.getCost())));
        assertTrue(service.searchByItemName("amylase crystal").stream().anyMatch(source ->
                "Grace's Graceful Clothing".equals(source.getSourceName()) && "10 marks of grace".equals(source.getCost())));
        assertTrue(service.searchByItemName("soft clay").stream().anyMatch(source ->
                "Dusuri's Star Shop".equals(source.getSourceName()) && "150 stardust".equals(source.getCost())));
        assertFalse(service.searchByItemName("ranarr seed").stream().anyMatch(source -> source.getPackQuantity() > 0));
    }

    @Test
    public void verifiedRewardCurrenciesAndLegacyRequirementsReplaceMisleadingCoins()
    {
        List<ItemSource> sources = service.searchByItemName("toadflax");
        assertTrue(sources.stream().anyMatch(source -> "3 Brimhaven vouchers".equals(source.getCost())));
        assertTrue(sources.stream().anyMatch(source -> "3 historical Agility arena tickets".equals(source.getCost())
                && source.getNotes().contains("can no longer be earned")));
        assertFalse(sources.stream().anyMatch(source -> source.getCost().contains("coins")));
        // This generated row records a four-item reward bundle as its own name.
        // Currency validation must not require collapsing that distinct identity.
        assertTrue(service.searchByItemName("Blighted super restore(4) (x4)").stream()
                .anyMatch(source -> "LMS points".equals(source.getCost().replaceAll("^[0-9,]+ ", ""))));
    }

    @Test
    public void correctedShopCurrenciesReachPlayersWithoutDiscountOrStockConfusion()
    {
        assertTrue(service.searchByItemName("Herb sack").stream().anyMatch(source ->
                "Slayer Rewards".equals(source.getSourceName()) && "750 Slayer points".equals(source.getCost())));
        List<ItemSource> onyx = service.searchByItemName("Uncut onyx");
        org.junit.Assert.assertEquals(1, onyx.stream().filter(source ->
                "TzHaar-Hur-Lek's Ore and Gem Store".equals(source.getSourceName())).count());
        assertTrue(onyx.stream().anyMatch(source -> "TzHaar-Hur-Lek's Ore and Gem Store".equals(source.getSourceName())
                && "300,000 Tokkul".equals(source.getCost())));
        assertFalse(service.searchByItemName("Coal").stream().anyMatch(source ->
                "TzHaar-Hur-Lek's Ore and Gem Store".equals(source.getSourceName())));
    }

    @Test
    public void modernTicketsAndFreeOrCompoundForestryPricesRemainDistinct()
    {
        assertTrue(service.searchByItemName("Agility XP").stream().anyMatch(source ->
                "Agility Arena Store".equals(source.getSourceName()) && "1 Agility arena ticket".equals(source.getCost())
                        && !source.getNotes().contains("can no longer be earned")));
        assertTrue(service.searchByItemName("Forestry kit").stream().anyMatch(source ->
                "Forestry Shop".equals(source.getSourceName()) && "Free".equals(source.getCost())));
        assertTrue(service.searchByItemName("Log basket").stream().anyMatch(source ->
                "Forestry Shop".equals(source.getSourceName()) && source.getCost().isEmpty()
                        && source.getNotes().contains("Additional purchase costs")));
    }

    @Test
    public void bothOnyxShopsUseNormalTokkulPricesWithScopedConditions()
    {
        List<ItemSource> onyx = service.searchByItemName("Uncut onyx");
        org.junit.Assert.assertEquals(2, onyx.size());
        for (ItemSource source : onyx)
        {
            org.junit.Assert.assertEquals("300,000 Tokkul", source.getCost());
            assertTrue(source.getNotes().contains("Normal price; 260,000 Tokkul with Karamja gloves 1+ equipped."));
            org.junit.Assert.assertEquals(source.getSourceName().contains("Hur-Rin"),
                    source.getNotes().contains("Fire cape required to access the inner-city shop."));
        }
        assertTrue(service.searchByItemName("Coal").stream().anyMatch(source ->
                source.getSourceName().contains("Hur-Rin") && "67 Tokkul".equals(source.getCost())));
        assertFalse(service.searchByItemName("Uncut dragonstone").stream().anyMatch(source ->
                source.getSourceName().contains("Hur-Rin") || source.getSourceName().contains("Hur-Lek")));
    }

    @Test
    public void currencyPatchKeepsOneOptionPerVerifiedBettyPack()
    {
        for (String item : new String[] {"Air rune", "Eye of newt"})
        {
            List<ItemSource> sources = service.searchByItemName(item);
            org.junit.Assert.assertEquals(1, sources.stream().filter(source ->
                    "Betty's Magic Emporium.".equals(source.getSourceName()) && source.getPackQuantity() == 100).count());
            String cost = "Air rune".equals(item) ? "430 coins" : "300 coins";
            assertTrue(sources.stream().anyMatch(source -> "Betty's Magic Emporium.".equals(source.getSourceName())
                    && source.getPackQuantity() == 100 && cost.equals(source.getCost())));
        }
    }
}
