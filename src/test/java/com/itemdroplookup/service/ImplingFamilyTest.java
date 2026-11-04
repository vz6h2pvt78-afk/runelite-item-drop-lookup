package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.AcquisitionMethod;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImplingFamilyTest
{
    private final List<AcquisitionMethod> methods = LootEventAdapter.readFamily(new Gson());

    private AcquisitionMethod find(String source, String item)
    {
        List<AcquisitionMethod> found = methods.stream().filter(m -> m.getSourceName().equals(source + " impling loot")
                && m.getItemName().equals(item)).collect(Collectors.toList());
        assertEquals(1, found.size());
        return found.get(0);
    }

    @Test public void twoMithrilOreResultsAreOneMethodWithTwoListingFacts()
    {
        String text = find("Earth", "Mithril ore").getSearchText();
        assertTrue(text.contains("Quantity: 1 on this outcome."));
        assertTrue(text.contains("Quantity: 3 (noted) on this outcome."));
        assertTrue(text.contains("1/10.") && text.contains("1/100."));
        assertTrue(text.contains("36 Hunter") && text.contains("46 Hunter"));
    }

    @Test public void standardTableSeparatesCaptureFromOpeningAndExtraRolls()
    {
        AcquisitionMethod method = find("Gourmet", "Lobster");
        assertTrue(method.getSearchText().contains("Grubby key pre-roll"));
        assertTrue(method.getSearchText().contains("28 Hunter"));
        assertTrue(method.getSearchText().contains("38 Hunter"));
        assertFalse(method.getSearchText().contains("83 Hunter"));
        assertTrue(method.getSections().stream().filter(s -> s.getHeading().equals("Already have a jar?"))
                .anyMatch(s -> s.getLines().contains("No Hunter level is needed.")));
        assertFalse(methods.stream().anyMatch(m -> m.getItemName().equals("Nothing") || m.getItemName().equals("Grubby key")));
    }

    @Test public void higherTiersPreserveUnequalWeightsAndExactItems()
    {
        String hide = find("Magpie", "Black dragonhide").getSearchText();
        assertTrue(hide.contains("6 (noted)") && hide.contains("2/21") && hide.contains("75 Hunter"));
        assertTrue(find("Magpie", "Runite bar").getSearchText().contains("1/21"));
        assertTrue(find("Ninja", "Dragon dagger(p+)").getSearchText().contains("84 Hunter"));
        assertTrue(find("Nature", "Torstol").getSearchText().contains("58 Hunter"));
        assertFalse(methods.stream().anyMatch(m -> m.getSourceName().equals("Ninja impling loot")
                && m.getItemName().equals("Dragon dagger(p++)")));
    }

    @Test public void crystalAccessIsScopedToCaptureAndLuckyProducesOnlyTheJar()
    {
        AcquisitionMethod crystal = find("Crystal", "Crystal shard");
        assertEquals("Prifddinas", crystal.getLocationText());
        assertTrue(crystal.getSearchText().contains("Song of the Elves"));
        assertFalse(crystal.getSearchText().contains("Puro-Puro"));
        assertTrue(crystal.getSearchText().contains("90 Hunter"));
        ItemSourceLookupService service = new ItemSourceLookupService(new Gson());
        List<AcquisitionMethod> lucky = methods.stream().filter(m -> m.getSourceName().equals("Lucky impling")).collect(Collectors.toList());
        assertEquals(1,lucky.size());
        assertEquals("Gathering",lucky.get(0).getSourceType());
        assertEquals("Lucky impling jar",lucky.get(0).getItemName());
        assertTrue(lucky.get(0).getSearchText().contains("89 Hunter"));
        assertEquals(1,service.searchByItemName("Lucky impling jar").stream().filter(s -> s.getSourceName().equals("Lucky impling")).count());
        assertFalse(service.searchByItemName("Reward casket (master)").stream().anyMatch(s -> s.getSourceName().equals("Lucky impling")));
    }

    @Test public void searchableItemGetsOnlyItsOwnReviewedLoot()
    {
        ItemSourceLookupService service = new ItemSourceLookupService(new Gson());
        assertEquals(1, service.searchByItemName("Chisel").stream().filter(s -> "Baby impling loot".equals(s.getSourceName())).count());
        assertTrue(service.searchByItemName("Chisel").stream().noneMatch(s -> "Gourmet impling loot".equals(s.getSourceName())));
    }
}
