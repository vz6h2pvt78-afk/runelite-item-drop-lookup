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
}
