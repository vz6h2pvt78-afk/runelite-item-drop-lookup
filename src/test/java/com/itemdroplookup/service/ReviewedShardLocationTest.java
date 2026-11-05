package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import com.itemdroplookup.service.MonsterLocationLookupService.LocationMatch;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedShardLocationTest
{
    @Test public void fiveExistingSitesHaveSeparateProvenanceAndPreserveOriginalDropIdentity()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
        for (String[] pair : new String[][] {
                {"Dark beast", "5f9a03c81abe69928796"}, {"Greater Nechryael", "51b7c39b8d085595856b"},
                {"Kurask", "7d37e4898b37137e6b86"}, {"Mutated Bloodveld", "5d02c785be1ea0f0d404"},
                {"Waterfiend", "6c6ac675635c5903fbfe"}})
        {
            DropSource source = drops.searchByItemName("Crystal shard").stream()
                    .filter(row -> row.getMonsterName().equals(pair[0])).findFirst().get();
            LocationMatch match = locations.lookup(source);
            assertEquals(LocationMatch.AssociationOrigin.REVIEWED_ITEM_LOCATION, match.getAssociationOrigin());
            assertFalse(match.isVerified());
            assertTrue(match.hasItemLocationAssociation());
            assertFalse(match.isReviewedEncounter());
            assertEquals(LocationMatch.ContextKind.NONE, match.getContextKind());
            assertEquals(1, match.getLocations().size());
            assertEquals(pair[1], match.getLocations().get(0).getId());
            assertSame(locations.lookupByMonsterName(pair[0]).stream().filter(place -> place.getId().equals(pair[1])).findFirst().get(),
                    match.getLocations().get(0));
            assertEquals("Iorwerth Dungeon", match.getLocations().get(0).getLocation());
            assertEquals("3-5", source.getQuantity());
            assertEquals("1/24", source.getDropRate());
            assertEquals("", source.getNotes());
            assertTrue(locations.hasQualifyingLocation(source, WildernessFilter.ALL));
            assertTrue(locations.hasQualifyingLocation(source, WildernessFilter.EXCLUDE));
            assertFalse(locations.hasQualifyingLocation(source, WildernessFilter.ONLY));
            assertFalse(locations.isCommonDrop(source));
            assertTrue(locations.tableVersions(source).isEmpty());
        }
    }

    @Test public void changedSignaturesAndSuperiorRowsDoNotInheritAssociations()
    {
        MonsterLocationLookupService locations = new MonsterLocationLookupService(new Gson());
        for (DropSource source : List.of(
                new DropSource("Crystal shard", "Dark beast", "1", "1/24", ""),
                new DropSource("Crystal shard", "Kurask", "3-5", "1/25", ""),
                new DropSource("Crystal shard", "Waterfiend", "3-5", "1/24", "Other"),
                new DropSource("Crystal shard", "Night beast", "6-10", "Always", ""),
                new DropSource("Crystal shard", "Nechryarch", "6-10", "Always", ""),
                new DropSource("Crystal shard", "King kurask", "6-10", "Always", ""),
                new DropSource("Crystal shard", "Insatiable mutated Bloodveld", "6-10", "Always", "")))
            assertEquals(LocationMatch.AssociationOrigin.NONE, locations.lookup(source).getAssociationOrigin());
    }

    @Test public void legacyBindingStillUsesTheOriginalVerificationContract()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
        DropSource source = drops.searchByItemName("Rune scimitar").stream()
                .filter(row -> row.getMonsterName().equals("Fire giant")).findFirst().get();
        assertTrue(locations.lookup(source).isVerified());
        assertTrue(locations.lookup(source).hasItemLocationAssociation());
        assertEquals(LocationMatch.AssociationOrigin.LEGACY_DROP_BINDING, locations.lookup(source).getAssociationOrigin());
    }
}
