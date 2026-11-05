package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.AccessRequirement;
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
    public void exactBossLairsParticipateInSafetyWithoutCertifyingIndividualDrops()
    {
        DropLookupService drops = new DropLookupService(new Gson());
        for (String[] test : new String[][] {
                {"Voidwaker gem", "Spindel", "Web Chasm", "29"},
                {"Voidwaker blade", "Calvar'ion", "Skeletal Tomb", "21"}})
        {
            DropSource source = drops.searchByItemName(test[0]).stream()
                    .filter(d -> test[1].equals(d.getMonsterName())).findFirst().get();
            MonsterLocationLookupService.LocationMatch match = service.lookup(source);
            assertFalse(match.isVerified());
            assertTrue(match.isReviewedEncounter());
            MonsterLocation place = match.getLocations().get(0);
            assertEquals(test[2], place.getLocation());
            assertEquals(Integer.parseInt(test[3]), place.getEncounterWildernessLevel());
            assertNull(place.getSpawns());
            assertTrue(service.tableVersions(source).isEmpty());
            assertFalse(service.isCommonDrop(source));
            assertTrue(service.hasQualifyingLocation(source, WildernessFilter.ALL));
            assertTrue(service.hasQualifyingLocation(source, WildernessFilter.ONLY));
            assertFalse(service.hasQualifyingLocation(source, WildernessFilter.EXCLUDE));
            assertTrue(service.lookupByMonsterName(test[1] + " (quest)").isEmpty());
        }
        assertFalse(service.lookup(drop("Venenatis", "Voidwaker gem", "1/1")).isReviewedEncounter());
    }

    @Test
    public void onlyFourUnboundVoidwakerRowsUseTheExistingLairsForAreaFiltering()
    {
        for (String[] s : new String[][] {
                {"Artio","Voidwaker hilt","1/912","21"}, {"Callisto","Voidwaker hilt","1/360","40"},
                {"Vet'ion","Voidwaker blade","1/360","35"}, {"Venenatis","Voidwaker gem","1/360","35"}})
        {
            DropSource target = drop(s[0], s[1], s[2]);
            MonsterLocationLookupService.LocationMatch match = service.lookup(target);
            assertFalse(match.isVerified());
            assertTrue(match.isReviewedEncounter());
            assertEquals(Integer.parseInt(s[3]), match.getEncounterWildernessLevel());
            assertEquals(1, match.getLocations().size());
            assertSame(service.lookupByMonsterName(s[0]).get(0), match.getLocations().get(0));
            assertTrue(service.hasQualifyingLocation(target, WildernessFilter.ONLY));
            assertFalse(service.hasQualifyingLocation(target, WildernessFilter.EXCLUDE));
            for (DropSource other : Arrays.asList(drop(s[0], s[1], "1/999"), drop(s[0],"Dragon pickaxe",s[2]),
                    new DropSource(s[1],s[0],"2",s[2],""), new DropSource(s[1],s[0],"1",s[2],"Quest")))
                assertFalse(service.lookup(other).isReviewedEncounter());
        }
        DropSource bound = serviceDrop("Blood rune", "Artio");
        assertTrue(service.lookup(bound).isVerified());
        assertFalse(service.lookup(bound).isReviewedEncounter());
        assertTrue(service.hasQualifyingLocation(bound, WildernessFilter.ONLY));
    }

    private DropSource serviceDrop(String item, String monster)
    {
        return new DropLookupService(new Gson()).searchByItemName(item).stream()
                .filter(row -> row.getMonsterName().equals(monster)).findFirst().get();
    }

    @Test
    public void scorpiaWarningsCoverBothExactShardsAndPreserveTheRange()
    {
        for (String item : Arrays.asList("Malediction shard 3", "Odium shard 3"))
        {
            DropSource shard = serviceDrop(item, "Scorpia");
            assertEquals("1/256", shard.getDropRate());
            assertEquals("1", shard.getQuantity());
            assertEquals("", shard.getNotes());
            MonsterLocationLookupService.LocationMatch match = service.lookup(shard);
            assertFalse(match.isVerified());
            assertTrue(match.isReviewedEncounter());
            assertEquals("levels 53–55", match.encounterLevelText());
            assertEquals(0, match.getEncounterWildernessLevel());
            assertEquals(1, match.getLocations().size());
            assertSame(service.lookupByMonsterName("Scorpia").get(0), match.getLocations().get(0));
            assertTrue(service.hasQualifyingLocation(shard, WildernessFilter.ONLY));
            assertFalse(service.hasQualifyingLocation(shard, WildernessFilter.EXCLUDE));
            assertFalse(service.lookup(drop("Scorpia", item, "1/128")).isReviewedEncounter());
        }
        for (String item : Arrays.asList("Brimstone key", "Scorpia's offspring", "Dragon 2h sword"))
            assertFalse(service.lookup(serviceDrop(item, "Scorpia")).isReviewedEncounter());
        assertTrue(service.lookup(serviceDrop("Dragon 2h sword", "Scorpia")).isVerified());
    }

    @Test
    public void fourPickaxeEncountersHaveIndependentSafetyAndThreeGroupRates()
    {
        AccessRequirementLookupService access = new AccessRequirementLookupService(new Gson());
        for (String[] pair : new String[][] {{"Artio","1/358"},{"Callisto","1/256"},{"Vet'ion","1/256"},{"Venenatis","1/256"}})
        {
            DropSource pickaxe = serviceDrop("Dragon pickaxe",pair[0]);
            assertEquals(pair[1],pickaxe.getDropRate());
            assertEquals("1",pickaxe.getQuantity());
            assertEquals("",pickaxe.getNotes());
            MonsterLocationLookupService.LocationMatch match = service.lookup(pickaxe);
            assertTrue(match.isReviewedEncounter());
            assertFalse(match.isVerified());
            assertEquals(1,match.getLocations().size());
            assertTrue(service.hasQualifyingLocation(pickaxe,WildernessFilter.ONLY));
            assertFalse(service.hasQualifyingLocation(pickaxe,WildernessFilter.EXCLUDE));
            AccessRequirement group = AccessRequirement.groupRewardIn(access.forSource(pickaxe));
            if (pair[0].equals("Artio")) assertNull(group);
            else assertEquals(AccessRequirement.GroupRewardRole.PER_KILL_UNIQUE,group.getGroupRewardRole());
            assertFalse(service.lookup(drop(pair[0],"Dragon pickaxe","1/999")).isReviewedEncounter());
            assertFalse(service.lookup(drop(pair[0],"Bones","Always")).isReviewedEncounter());
        }
        assertTrue(service.hasQualifyingLocation(serviceDrop("Dragon pickaxe","King Black Dragon"),WildernessFilter.EXCLUDE));
    }

    @Test
    public void fourWildernessEntrancesKeepTheirIndependentDiaryOrBossAssignment()
    {
        AccessRequirementLookupService access = new AccessRequirementLookupService(new Gson());
        for (String[] test : new String[][] {
                {"Artio", "hard", "Callisto", "bear"}, {"Callisto", "medium", "Callisto", "bear"},
                {"Venenatis", "medium", "Venenatis", "spider"}, {"Vet'ion", "medium", "Vet'ion", "skeleton"}})
        {
            MonsterLocation place = service.lookupByMonsterName(test[0]).get(0);
            List<AccessRequirement> facts = access.forLocation(place);
            assertEquals(1, facts.size());
            assertEquals("Complete the " + test[1] + " Wilderness Diary or have a " + test[2]
                    + " boss Slayer task. Ordinary " + test[3] + " tasks do not qualify.", facts.get(0).getText());
            assertEquals(place.getRevisionId(), facts.get(0).getEvidence().get(0).getRevisionId());
            assertEquals(MonsterLocation.Wilderness.YES, place.getWilderness());
            assertTrue(access.forSource(drop(test[0], "Dragon pickaxe", "1/1")).isEmpty());
        }
        for (String other : Arrays.asList("Spindel", "Calvar'ion", "King Black Dragon"))
            assertTrue(access.forLocation(service.lookupByMonsterName(other).get(0)).isEmpty());
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
