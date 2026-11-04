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

    @Test public void searchableItemGetsOnlyItsOwnReviewedLoot()
    {
        ItemSourceLookupService service = new ItemSourceLookupService(new Gson());
        assertEquals(1, service.searchByItemName("Chisel").stream().filter(s -> "Baby impling loot".equals(s.getSourceName())).count());
        assertTrue(service.searchByItemName("Chisel").stream().noneMatch(s -> "Gourmet impling loot".equals(s.getSourceName())));
    }
}
