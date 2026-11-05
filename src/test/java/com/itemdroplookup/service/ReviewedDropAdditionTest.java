package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedDropAdditionTest
{
    @Test
    public void dragonWarhammerHasOneOrdinarySourceAndSecondaryTableMetadata()
    {
        DropLookupService drops = new DropLookupService(new Gson());
        List<DropSource> found = drops.searchByItemName("Dragon warhammer");
        assertEquals(1, found.size());
        DropSource source = found.get(0);
        assertEquals("Lizardman shaman", source.getMonsterName());
        assertEquals("1", source.getQuantity());
        assertEquals("1/3000", source.getDropRate());
        assertEquals("", source.getNotes());
        assertEquals(Boolean.TRUE, source.getMembersOnly());
        assertEquals("Pre-roll", source.getReviewedDropTable());
        assertNull(source.getConditionalRate());
        assertTrue(drops.hasUniqueRecordedDrop(source));
        assertNull(new DropSource("item", "monster", "1", "1/2", "").getReviewedDropTable());
        for (DropSource old : drops.searchByItemName("Air rune"))
            assertNull(old.getReviewedDropTable());
    }

    @Test
    public void sourceExistenceDoesNotFabricateLocationBindingOrTaskEligibility()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        DropSource source = drops.searchByItemName("Dragon warhammer").get(0);
        MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
        MonsterLocationLookupService.LocationMatch match = locations.lookup(source);
        assertFalse(match.isVerified());
        assertFalse(match.isReviewedEncounter());
        assertEquals(3, match.getLocations().size());
        assertTrue(locations.tableVersions(source).isEmpty());
        assertFalse(locations.isCommonDrop(source));
        assertTrue(locations.hasQualifyingLocation(source, MonsterLocationFilter.WildernessFilter.ALL));
        assertFalse(locations.hasQualifyingLocation(source, MonsterLocationFilter.WildernessFilter.ONLY));
        assertFalse(locations.hasQualifyingLocation(source, MonsterLocationFilter.WildernessFilter.EXCLUDE));
        AccessRequirementLookupService facts = new AccessRequirementLookupService(gson, drops, locations);
        assertTrue(facts.forSource(source).isEmpty());
        assertTrue(facts.itemAccessFor(source).isEmpty());
    }
}
