package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MonsterLocationLookupServiceTest
{
    private final MonsterLocationLookupService service = new MonsterLocationLookupService(new Gson());

    private DropSource drop(String monster, String item, String rate)
    {
        return new DropSource(item, monster, "1", rate, "");
    }

    @Test
    public void fireGiantContainsLevelsAndBothWildernessChoices()
    {
        List<MonsterLocation> locations = service.lookupByMonsterName("Fire giant");
        assertEquals(12, locations.size());
        assertTrue(locations.stream().anyMatch(r -> r.getLocation().equals("Catacombs of Kourend")
                && r.getLevels().equals(Arrays.asList(104, 109))));
        DropSource scimitar = drop("Fire giant", "Rune scimitar", "1/128");
        assertTrue(service.lookup(scimitar).isVerified());
        assertTrue(service.hasQualifyingLocation(scimitar, WildernessFilter.ONLY));
        assertTrue(service.hasQualifyingLocation(scimitar, WildernessFilter.EXCLUDE));
        assertTrue(locations.stream().anyMatch(r -> !r.isAvailable() && r.getLocation().contains("Unreachable")));
    }

    @Test
    public void goblinMailUsesTheCorrectDropTableForLumbridge()
    {
        MonsterLocationLookupService.LocationMatch five = service.lookup(drop("Goblin", "Goblin mail", "5/128"));
        MonsterLocationLookupService.LocationMatch ten = service.lookup(drop("Goblin", "Goblin mail", "10/128"));
        assertTrue(five.isVerified());
        assertTrue(ten.isVerified());
        MonsterLocation lumbridge = five.getLocations().stream().filter(r -> r.getLocation().equals("Lumbridge")).findFirst().get();
        assertEquals(Boolean.FALSE, lumbridge.getMembers());
        assertEquals(MonsterLocation.Wilderness.NO, lumbridge.getWilderness());
        assertFalse(ten.getLocations().stream().anyMatch(r -> r.getLocation().equals("Lumbridge")));
    }

    @Test
    public void exactCaseInsensitiveNamesPreserveParentheticalVariants()
    {
        assertEquals(service.lookupByMonsterName("Fire giant"), service.lookupByMonsterName("  FIRE_GIANT  "));
        assertFalse(service.lookupByMonsterName("Skeleton (Barrows)").isEmpty());
        assertTrue(service.lookupByMonsterName("Skeleton (Barrows)").stream().allMatch(r -> r.getMonsterName().equals("Skeleton (Barrows)")));
        assertTrue(service.lookupByMonsterName("Skeleton (invented variant)").isEmpty());
        assertTrue(service.lookupByMonsterName("Not a monster").isEmpty());
        assertTrue(service.lookupByMonsterName(null).isEmpty());
    }

    @Test
    public void wildernessDropRateDoesNotBorrowStandardLocations()
    {
        DropSource standard = drop("Abyssal demon", "Black sword", "4/128");
        DropSource wild = drop("Abyssal demon", "Black sword", "4/68");
        assertTrue(service.lookup(standard).isVerified());
        assertFalse(service.hasQualifyingLocation(standard, WildernessFilter.ONLY));
        assertTrue(service.hasQualifyingLocation(standard, WildernessFilter.EXCLUDE));
        assertTrue(service.hasQualifyingLocation(wild, WildernessFilter.ONLY));
        assertFalse(service.hasQualifyingLocation(wild, WildernessFilter.EXCLUDE));
        // Identical signatures can apply to multiple variants; neither is discarded.
        DropSource whip = drop("Abyssal demon", "Abyssal whip", "1/512");
        assertTrue(service.hasQualifyingLocation(whip, WildernessFilter.ONLY));
        assertTrue(service.hasQualifyingLocation(whip, WildernessFilter.EXCLUDE));
    }

    @Test
    public void unverifiedDropsStayVisibleWithFiltersCleared()
    {
        DropSource unknownRate = drop("Fire giant", "Rune scimitar", "1/999");
        assertFalse(service.lookup(unknownRate).isVerified());
        assertEquals(12, service.lookup(unknownRate).getLocations().size());
        assertFalse(service.hasQualifyingLocation(unknownRate, WildernessFilter.ONLY));
        assertFalse(service.hasQualifyingLocation(unknownRate, WildernessFilter.EXCLUDE));
        assertTrue(service.hasQualifyingLocation(unknownRate, WildernessFilter.ALL));
        DropSource missing = drop("Missing monster", "Item", "1/2");
        assertTrue(service.lookup(missing).getLocations().isEmpty());
        assertTrue(service.hasQualifyingLocation(missing, WildernessFilter.ALL));
    }

    @Test
    public void safeLairsAreNotClassifiedFromLeagueRegion()
    {
        for (String monster : Arrays.asList("King Black Dragon", "Corporeal Beast"))
        {
            assertFalse(service.lookupByMonsterName(monster).isEmpty());
            for (MonsterLocation location : service.lookupByMonsterName(monster))
            {
                assertEquals(MonsterLocation.Wilderness.NO, location.getWilderness());
                assertTrue(location.getLeagueRegions().contains("Wilderness"));
                assertFalse(location.getNotes().isEmpty());
            }
        }
    }

    @Test
    public void unknownMembershipAndWildernessFailSpecificFilters()
    {
        MonsterLocation unknown = new Gson().fromJson("{\"location\":\"Unknown\",\"wilderness\":\"UNKNOWN\",\"members\":null}", MonsterLocation.class);
        assertTrue(MonsterLocationFilter.matches(unknown, WildernessFilter.ALL, MembershipFilter.ALL));
        assertFalse(MonsterLocationFilter.matches(unknown, WildernessFilter.ONLY, MembershipFilter.ALL));
        assertFalse(MonsterLocationFilter.matches(unknown, WildernessFilter.EXCLUDE, MembershipFilter.ALL));
        assertFalse(MonsterLocationFilter.matches(unknown, WildernessFilter.ALL, MembershipFilter.F2P));
        assertFalse(MonsterLocationFilter.matches(unknown, WildernessFilter.ALL, MembershipFilter.MEMBERS));
    }

    @Test
    public void membershipAndWildernessFiltersCombineOnOneLocation()
    {
        List<MonsterLocation> goblins = service.lookupByMonsterName("Goblin");
        assertTrue(goblins.stream().anyMatch(r -> MonsterLocationFilter.matches(r, WildernessFilter.EXCLUDE, MembershipFilter.F2P)));
        assertTrue(goblins.stream().anyMatch(r -> MonsterLocationFilter.matches(r, WildernessFilter.EXCLUDE, MembershipFilter.MEMBERS)));
        assertFalse(goblins.stream().anyMatch(r -> MonsterLocationFilter.matches(r, WildernessFilter.ONLY, MembershipFilter.F2P)));
    }
}
