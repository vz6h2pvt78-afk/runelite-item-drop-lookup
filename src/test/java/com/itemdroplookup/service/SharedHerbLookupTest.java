package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedHerbLookupTest
{
    private final Gson gson = new Gson();
    private final DropLookupService drops = new DropLookupService(gson);
    private final MonsterLocationLookupService locations = new MonsterLocationLookupService(gson);

    @Test
    public void allFourHerbOccurrencesKeepTheirOwnRatesAndConditions()
    {
        List<DropSource> sources = drops.searchByItemName("Grimy harralander").stream()
                .filter(s -> Boolean.TRUE.equals(s.getMembersOnly())).collect(Collectors.toList());
        assertEquals(4, sources.size());
        assertEquals("1/50.9", source("Man", "1/50.9").getDropRate());
        assertEquals("1/585.1", source("Goblin", "1/585.1").getDropRate());
        DropSource standard = source("Abyssal demon", "1/61.6");
        DropSource wild = source("Abyssal demon", "1/32.7");
        assertTrue(standard.getNotes().contains("Standard only"));
        assertTrue(wild.getNotes().contains("Wilderness Slayer Cave only"));
        assertFalse(locations.hasQualifyingLocation(standard, WildernessFilter.ONLY));
        assertTrue(locations.hasQualifyingLocation(standard, WildernessFilter.EXCLUDE));
        assertTrue(locations.hasQualifyingLocation(wild, WildernessFilter.ONLY));
        assertFalse(locations.hasQualifyingLocation(wild, WildernessFilter.EXCLUDE));
        assertEquals(1, locations.lookup(wild).getLocations().size());
    }

    @Test
    public void membersWorldCanUseLumbridgeButF2pCannotObtainTheHerb()
    {
        DropSource man = source("Man", "1/50.9");
        assertTrue(locations.lookup(man).isVerified());
        MonsterLocation lumbridge = locations.lookup(man).getLocations().stream()
                .filter(l -> l.getLocation().equals("Lumbridge")).findFirst().get();
        assertEquals(Boolean.FALSE, lumbridge.getMembers());
        assertFalse(MonsterLocationFilter.matchesSource(man, lumbridge, WildernessFilter.ALL, MembershipFilter.F2P));
        assertTrue(MonsterLocationFilter.matchesSource(man, lumbridge, WildernessFilter.ALL, MembershipFilter.MEMBERS));
        assertTrue(MonsterLocationFilter.matchesSource(man, lumbridge, WildernessFilter.ALL, MembershipFilter.ALL));
        DropSource goblin = source("Goblin", "1/585.1");
        assertTrue(locations.lookup(goblin).isVerified());
        assertTrue(locations.lookup(goblin).getLocations().stream().allMatch(l -> l.getDropVersions().contains("Drop table 2")));
        assertFalse(locations.lookup(goblin).getLocations().stream().anyMatch(l -> l.getLocation().equals("Lumbridge")));
        assertFalse(locations.lookup(goblin).getLocations().stream().anyMatch(l ->
                MonsterLocationFilter.matchesSource(goblin, l, WildernessFilter.ALL, MembershipFilter.F2P)));
    }

    @Test
    public void legacyRecordsKeepUnknownWorldEligibilityAndTheirBindings()
    {
        DropSource mail = drops.searchByItemName("Goblin mail").stream()
                .filter(s -> s.getMonsterName().equals("Goblin") && s.getDropRate().equals("5/128")).findFirst().get();
        assertNull(mail.getMembersOnly());
        MonsterLocation lumbridge = locations.lookup(mail).getLocations().stream()
                .filter(l -> l.getLocation().equals("Lumbridge")).findFirst().get();
        assertTrue(MonsterLocationFilter.matchesSource(mail, lumbridge, WildernessFilter.ALL, MembershipFilter.F2P));
        assertNull(new DropSource("Item", "Man", "1", "1/2", "").getMembersOnly());
        assertEquals(Boolean.FALSE, new DropSource("Item", "Man", "1", "1/2", "", false).getMembersOnly());
    }

    @Test
    public void newHerbsCoexistWithExistingSourcesWithoutDuplicateSignatures()
    {
        List<DropSource> ranarr = drops.searchByItemName("Grimy ranarr weed");
        assertEquals(4, ranarr.stream().filter(s -> Boolean.TRUE.equals(s.getMembersOnly())).count());
        assertTrue(ranarr.stream().anyMatch(s -> s.getMembersOnly() == null));
        List<DropSource> broad = drops.searchByItemName("grimy");
        List<DropSource> added = broad.stream().filter(s -> Boolean.TRUE.equals(s.getMembersOnly())).collect(Collectors.toList());
        assertEquals(44, added.size());
        assertEquals(44, added.stream().map(s -> s.getMonsterName() + "|" + s.getItemName() + "|" + s.getDropRate() + "|" + s.getNotes()).distinct().count());
    }

    private DropSource source(String monster, String rate)
    {
        return drops.searchByItemName("Grimy harralander").stream()
                .filter(s -> s.getMonsterName().equals(monster) && s.getDropRate().equals(rate)).findFirst().get();
    }
}
