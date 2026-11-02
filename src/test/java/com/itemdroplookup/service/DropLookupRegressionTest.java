package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Counts captured from the verified pre-feature dataset at 9acc143. */
public class DropLookupRegressionTest
{
    private final DropLookupService service = new DropLookupService(new Gson());

    @Test
    public void existingSearchResultsAndCompactMatchingArePreserved()
    {
        assertEquals(35, service.searchByItemName("rune scimitar").size());
        assertEquals(35, service.searchByItemName("runescimitar").size());
        assertEquals(35, service.searchByItemName("rune scim").size());
        assertEquals(6, service.searchByItemName("goblin mail").size());
        assertEquals(3, service.searchByItemName("abyssalwhip").size());
        assertEquals(1780, service.searchByItemName("coins").size());
    }

    @Test
    public void exactItemSearchDoesNotExpandToOtherItems()
    {
        List<DropSource> results = service.searchByItemName("black sword");
        assertEquals(8, results.size());
        assertTrue(results.stream().allMatch(source -> source.getItemName().equalsIgnoreCase("Black sword")));
    }
}
