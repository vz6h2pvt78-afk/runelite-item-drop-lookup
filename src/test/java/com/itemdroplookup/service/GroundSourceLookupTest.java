package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.ItemSource;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class GroundSourceLookupTest
{
    private final ItemSourceLookupService service = new ItemSourceLookupService(new Gson());

    private List<ItemSource> ground(String query)
    {
        return service.searchByItemName(query).stream()
                .filter(s -> "Ground pickup".equals(s.getSourceType())).collect(Collectors.toList());
    }

    @Test
    public void allFiveItemsReturnOnlyTheReviewedLocations()
    {
        assertEquals(3, ground("Spade").size());
        assertEquals(3, ground("Knife").size());
        assertEquals(3, ground("Bucket").size());
        assertEquals(1, ground("Snape grass").size());
        assertEquals(2, ground("Red spiders' eggs").size());
        assertTrue(ground("Knife").stream().allMatch(s -> s.getLocation().startsWith("Lumbridge")));
        assertEquals("Members · 3 spawn points", ground("Snape grass").get(0).getNotes());
        assertEquals("Hobgoblin Peninsula west of the Crafting Guild", ground("Snape grass").get(0).getLocation());
        assertTrue(ground("Bucket").stream().anyMatch(s -> s.getLocation().equals("Falador farm")
                && s.getNotes().equals("F2P · 2 spawn points")));
        assertTrue(ground("Red spiders' eggs").stream().anyMatch(s -> s.getLocation().equals("Varrock Sewers")));
        assertTrue(ground("Red spiders' eggs").stream().anyMatch(s -> s.getLocation().equals("Crandor and Karamja Dungeon")));
    }

    @Test
    public void exactCompactAndBroadSearchRemainSpecificAndNetsAreExcluded()
    {
        assertEquals(2, ground("redspiderseggs").size());
        assertEquals(3, ground("spa").size());
        assertTrue(service.searchByItemName("bucket").stream().allMatch(s -> "Bucket".equals(s.getItemName())));
        assertTrue(ground("Small fishing net").isEmpty());
        assertFalse(service.searchByItemName("Small fishing net").isEmpty());
        assertTrue(service.searchByItemName("spade").stream().anyMatch(s -> "Shop".equals(s.getSourceType())));
        assertTrue(ground("Spade").stream().noneMatch(s -> s.getLocation().contains("Slayer Tower")
                || s.getLocation().contains("Rimmington") || s.getLocation().contains("Swamp")));
    }
}
