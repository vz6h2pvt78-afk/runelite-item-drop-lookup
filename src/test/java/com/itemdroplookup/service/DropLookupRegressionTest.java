package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Baseline counts, adjusted only for documented invalid acquisition exclusions. */
public class DropLookupRegressionTest
{
    private final DropLookupService service = new DropLookupService(new Gson());

    @Test
    public void existingSearchResultsAndCompactMatchingArePreserved()
    {
        assertEquals(34, service.searchByItemName("rune scimitar").size());
        assertEquals(34, service.searchByItemName("runescimitar").size());
        assertEquals(34, service.searchByItemName("rune scim").size());
        assertEquals(6, service.searchByItemName("goblin mail").size());
        assertEquals(2, service.searchByItemName("abyssalwhip").size());
        // 91 historical Bloodthirsty coin rows are intentionally excluded.
        assertEquals(1689, service.searchByItemName("coins").size());
    }

    @Test
    public void exactItemSearchDoesNotExpandToOtherItems()
    {
        List<DropSource> results = service.searchByItemName("black sword");
        assertEquals(7, results.size());
        assertTrue(results.stream().allMatch(source -> source.getItemName().equalsIgnoreCase("Black sword")));
    }

    @Test
    public void animatedArmourDoesNotOfferTheEquipmentUsedToSummonIt()
    {
        for (String metal : new String[] {"Bronze", "Iron", "Steel", "Black", "Mithril", "Adamant", "Rune"})
        {
            for (String piece : new String[] {"platebody", "platelegs", "full helm"})
            {
                List<DropSource> results = service.searchByItemName(metal + " " + piece);
                assertFalse(results.stream().anyMatch(source -> source.getMonsterName().equals("Animated " + metal + " Armour")));
            }
        }
        assertTrue(service.searchByItemName("warrior guild token").stream()
                .anyMatch(source -> source.getMonsterName().equals("Animated Adamant Armour")));
        assertTrue(service.searchByItemName("adamant platebody").stream()
                .anyMatch(source -> source.getMonsterName().equals("Black dragon")));
    }

    @Test
    public void removedLeagueDrakeIsNotAnOrdinaryAcquisitionSource()
    {
        assertFalse(service.searchByItemName("rune arrow").stream()
                .anyMatch(source -> "Bloodthirsty Drake".equals(source.getMonsterName())));
        assertTrue(service.searchByItemName("rune arrow").stream()
                .anyMatch(source -> "Drake".equals(source.getMonsterName())));
    }
}
