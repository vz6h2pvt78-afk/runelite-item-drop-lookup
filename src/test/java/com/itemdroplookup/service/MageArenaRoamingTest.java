package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.model.RoamingEncounter;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import com.itemdroplookup.service.MonsterLocationLookupService.LocationMatch;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MageArenaRoamingTest
{
    private final Gson gson = new Gson();
    private final DropLookupService drops = new DropLookupService(gson);
    private final MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);

    @Test public void sixExactRowsGainRoamingContextWhileRemainingUnbound()
    {
        for (String[] pair : new String[][] {{"Derwen", "Ent's roots"}, {"Porazdir", "Demon's heart"},
                {"Justiciar Zachariah", "Justiciar's hand"}})
        {
            for (String item : List.of(pair[1], "Looting bag"))
            {
                DropSource source = drops.searchByItemName(item).stream().filter(row -> row.getMonsterName().equals(pair[0])).findFirst().get();
                LocationMatch match = locations.lookup(source);
                assertEquals(LocationMatch.ContextKind.ROAMING_ENCOUNTER, match.getContextKind());
                assertFalse(match.isVerified());
                assertFalse(match.isReviewedEncounter());
                assertTrue(match.getLocations().isEmpty());
                assertTrue(locations.lookupByMonsterName(pair[0]).isEmpty());
                assertNull(source.getMembersOnly());
                assertEquals("1", source.getQuantity());
                assertEquals(item.equals("Looting bag") ? "1/3" : "Always", source.getDropRate());
                assertEquals("", source.getNotes());
                assertEquals(0, match.getEncounterWildernessLevel());
                assertEquals("", match.encounterLevelText());
                RoamingEncounter context = match.getRoamingEncounter();
                assertEquals("Mage Arena II", context.getEncounter());
                assertEquals("Deep Wilderness", context.getArea());
                assertEquals(MonsterLocation.Wilderness.YES, context.getWilderness());
                assertTrue(context.isMembersOnly());
                assertTrue(locations.hasQualifyingLocation(source, WildernessFilter.ONLY));
                assertFalse(locations.hasQualifyingLocation(source, WildernessFilter.EXCLUDE));
                assertTrue(locations.hasQualifyingLocation(source, WildernessFilter.ALL));
                assertTrue(MonsterLocationFilter.matches(context, WildernessFilter.ALL, MembershipFilter.MEMBERS));
                assertTrue(MonsterLocationFilter.matches(context, WildernessFilter.ONLY, MembershipFilter.ALL));
                assertFalse(MonsterLocationFilter.matches(context, WildernessFilter.ALL, MembershipFilter.F2P));
                assertFalse(MonsterLocationFilter.matches(context, WildernessFilter.EXCLUDE, MembershipFilter.MEMBERS));
            }
        }
    }

    @Test public void contextRequiresExactReviewedDropAndCannotLeakToOtherNpcOrSignature()
    {
        for (DropSource source : List.of(
                new DropSource("Ent's roots", "Derwen", "2", "Always", ""),
                new DropSource("Looting bag", "Porazdir", "1", "1/30", ""),
                new DropSource("Justiciar's hand", "Justiciar Zachariah", "1", "Always", "Different note"),
                new DropSource("Looting bag", "Demon of Balance", "1", "1/3", ""),
                new DropSource("Coins", "Porazdir", "1", "Always", ""),
                new DropSource("Ent's roots", "Kolodion", "1", "Always", "")))
            assertNull(source.getMonsterName(), locations.lookup(source).getRoamingEncounter());
        DropSource fixed = drops.searchByItemName("Voidwaker hilt").stream().filter(row -> row.getMonsterName().equals("Callisto")).findFirst().get();
        assertEquals(LocationMatch.ContextKind.FIXED_ENCOUNTER, locations.lookup(fixed).getContextKind());
        assertTrue(locations.lookup(fixed).isReviewedEncounter());
        assertNull(locations.lookup(fixed).getRoamingEncounter());
    }
}
