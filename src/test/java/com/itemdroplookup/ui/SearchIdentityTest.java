package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchIdentityTest
{
    private final Gson gson=new Gson();
    private final DropLookupService drops=new DropLookupService(gson);
    private final ItemSourceLookupService sources=new ItemSourceLookupService(gson);
    private final ItemPriceLookupService prices=new ItemPriceLookupService(gson);
    private ItemSearchSelection search(String q) { return new ItemSearchSelection(q,drops,sources,prices); }

    @Test public void uniqueCanonicalFormattingCannotSubstituteAvailableChargedOrPoisonedVariants()
    {
        for (String[] example : new String[][] {
                {"blackmask","Black mask"},{"amulet of glory (4)","Amulet of glory(4)"},
                {"amulet of glory (6)","Amulet of glory(6)"},{"rune dagger p++","Rune dagger(p++)"},
                {"cookedchicken","Cooked chicken"}})
        {
            ItemSearchSelection selected = search(example[0]);
            assertEquals(Collections.singletonList(example[1]), selected.choices());
            assertTrue(selected.drops.isEmpty());
            assertTrue(selected.sources.isEmpty());
        }
        assertEquals(Collections.singletonList("Black mask (10)"),search("Black mask (10)").choices());
        assertFalse(search("Black mask (10)").drops.isEmpty());
        for (String alias : List.of("d pick","d pickaxe","dpick"))
        {
            assertEquals(Collections.singletonList("Dragon pickaxe"),search(alias).choices());
            assertEquals(sourceSet(search("Dragon pickaxe")), sourceSet(search(alias)));
        }
    }

    @Test public void unavailableQualifiedItemsNeverBorrowAnotherVariantsSources()
    {
        for(String q:Arrays.asList("Dragon dagger (p+)","dragon dagger p+"))
        {
            ItemSearchSelection exact = search(q);
            assertEquals(Collections.singletonList("Dragon dagger(p+)"), exact.choices());
            assertEquals(1, exact.sources.size());
            assertEquals("Ninja impling loot", exact.sources.get(0).getSourceName());
        }
        for(String q:Arrays.asList("Prayer potion(5)","Super restore(9)","Amulet of glory(5)","Rune platebody (noted)"))
        {
            assertTrue(q,search(q).choices().isEmpty());
            assertTrue(q,drops.searchByItemName(q).isEmpty());
            assertTrue(q,sources.searchByItemName(q).isEmpty());
        }
        for(String q:Arrays.asList("Dragon dagger(p)","Antidote+(4)"))
        {
            ItemSearchSelection known = search(q);
            assertEquals(Collections.singletonList(q), known.choices());
            assertTrue(known.drops.isEmpty());
            assertTrue(known.sources.isEmpty());
            assertTrue(drops.searchByItemName(q).isEmpty());
            assertTrue(sources.searchByItemName(q).isEmpty());
        }
        assertEquals(Collections.singletonList("Dragon dagger(p++)"),search("dragon dagger p++").choices());
        assertEquals(Collections.singletonList("Dragon dagger"),search("Dragon dagger").choices());
        assertFalse(search("Prayer potion(4)").drops.isEmpty());
        assertTrue(search("Prayer potion(4)").drops.stream().allMatch(d->d.getItemName().equals("Prayer potion(4)")));
        assertEquals(Collections.singletonList("Prayer potion(4)"),search("prayerpotion4").choices());
        assertEquals(Collections.singletonList("Prayer potion(4)"),search("Prayer potion ( 4 )").choices());
        assertFalse(search("Blighted super restore(4)").sources.stream().anyMatch(s->s.getItemName().contains("(x4)")));
        assertFalse(sources.searchByItemName("Blighted super restore(4)").stream().anyMatch(s->s.getItemName().contains("(x4)")));
    }

    private List<String> sourceSet(ItemSearchSelection selection)
    {
        List<String> result=new ArrayList<>();
        selection.drops.forEach(d->result.add("drop|"+d.getItemName()+"|"+d.getMonsterName()+"|"+d.getQuantity()+"|"+d.getDropRate()));
        selection.sources.forEach(s->result.add("source|"+s.getItemName()+"|"+s.getSourceType()+"|"+s.getSourceName()+"|"+s.getLocation()+"|"+s.getCost()+"|"+s.getPackName()));
        Collections.sort(result); return result;
    }

    @Test public void everyReviewedAliasHasTheCanonicalSourceSetAndKeepsSubmittedQuery()
    {
        ItemNameMatching.reviewedAliases().forEach((alias,canonical)->{
            ItemSearchSelection selected=search(alias);
            assertEquals(alias,selected.query);
            assertEquals(alias,sourceSet(search(canonical)),sourceSet(selected));
        });
        assertEquals(Collections.singletonList("Dragon boots"),search("d boots").choices());
        assertFalse(search("r scim").sources.isEmpty());
    }

    @Test public void reviewedWeaponShorthandKeepsExactIdentityIncludingZeroCoverage()
    {
        for (String[] pair : new String[][] {
                {"dwh", "Dragon warhammer"}, {"tbow", "Twisted bow"}, {"d scim", "Dragon scimitar"},
                {"d defender", "Dragon defender"}, {"d axe", "Dragon axe"}, {"d spear", "Dragon spear"},
                {"acb", "Armadyl crossbow"}, {"ags", "Armadyl godsword"}, {"bgs", "Bandos godsword"},
                {"sgs", "Saradomin godsword"}, {"zgs", "Zamorak godsword"}})
        {
            ItemSearchSelection canonical = search(pair[1]);
            for (String query : List.of(pair[0], "  " + pair[0].toUpperCase(Locale.ROOT) + "  ",
                    pair[0].replace(" ", "   ")))
            {
                ItemSearchSelection selected = search(query);
                assertEquals(query, Collections.singletonList(pair[1]), selected.choices());
                assertEquals(query, gson.toJson(Arrays.asList(canonical.drops, canonical.sources)),
                        gson.toJson(Arrays.asList(selected.drops, selected.sources)));
                assertEquals(query, selected.query);
            }
        }
        for (String query : List.of("tbow", "ags", "bgs", "sgs", "zgs"))
        {
            assertTrue(query, search(query).drops.isEmpty());
            assertTrue(query, search(query).sources.isEmpty());
        }
        assertEquals(1, search("dwh").drops.size());
        assertEquals("Lizardman shaman", search("dwh").drops.get(0).getMonsterName());
    }

    @Test public void shorthandDoesNotExpandInsideQualifiedOrAmbiguousQueries()
    {
        for (String query : List.of("dwh(or)", "tbow (or)", "ags ornament kit", "d scim(p)",
                "d spear p++", "acb(1)", "dwh noted", "dwh pack", "dwh extra",
                "dfs", "dds", "bowfa", "shadow", "trident", "whip", "fang"))
            assertEquals(query, ItemNameMatching.normalize(query), ItemNameMatching.resolveAlias(query));
        for (String query : List.of("dwh(or)", "tbow (or)", "ags ornament kit", "d scim(p)",
                "d spear p++", "acb(1)", "dwh noted", "dwh pack"))
            assertTrue(query, search(query).choices().isEmpty());
        assertEquals(Collections.singletonList("Dragon dagger(p++)"), search("Dragon dagger(p++)").choices());
    }

    @Test public void replacedDemonHeartsRemainExactKnownZeroIdentities()
    {
        for (String kind : List.of("balance", "darkness", "light"))
        {
            String canonical = "Demon's heart (" + kind + ")";
            for (String query : List.of(canonical, ItemNameMatching.compact(canonical)))
            {
                ItemSearchSelection selected = search(query);
                assertEquals(query, Collections.singletonList(canonical), selected.choices());
                assertTrue(query, selected.drops.isEmpty());
                assertTrue(query, selected.sources.isEmpty());
            }
        }
        for (String counterpartItem : List.of("Ent's roots", "Demon's heart", "Justiciar's hand"))
            assertFalse(counterpartItem, search(counterpartItem).drops.isEmpty());
    }

    @Test public void historicalHolidayItemRemainsRecognizedWithoutChangingOrdinaryChicken()
    {
        for (String query : List.of("Thanksgiving dinner", "thanksgivingdinner"))
        {
            ItemSearchSelection selected = search(query);
            assertEquals(Collections.singletonList("Thanksgiving dinner"), selected.choices());
            assertTrue(selected.drops.isEmpty());
            assertTrue(selected.sources.isEmpty());
        }
        assertTrue(search("Feather").drops.stream().anyMatch(row -> row.getMonsterName().equals("Chicken")));
        assertFalse(search("Feather").drops.stream().anyMatch(row -> row.getMonsterName().equals("Unusual Chicken")));
    }

    @Test public void compactPossessiveBroadAndTypoSearchRemainUseful()
    {
        assertEquals(9,search("uncut").choices().size());
        assertEquals(Collections.singletonList("rune pouch"),search("runepouch").choices());
        assertEquals(Collections.singletonList("Rune pouch note"),search("Rune pouch note").choices());
        assertEquals(sourceSet(search("Zulrah's scales")),sourceSet(search("zulrahscales")));
        assertEquals(sourceSet(search("Hydra's claw")),sourceSet(search("hydrasclaw")));
        assertFalse(search("rune scimitar").choices().isEmpty());
        assertEquals(search("rune scimitar").choices(),search("rune scimitarx").choices());
        assertTrue(search("Eye of newt").sources.stream().anyMatch(s->s.getPackQuantity()>0));
        assertTrue(search("Air rune pack").sources.stream().allMatch(s->s.getItemName().equalsIgnoreCase("Air rune pack")));
    }

    @Test public void ambiguousCompactNamesUseChooserAndCanonicalSelectionStaysExact()
    {
        DropLookupService fixtures=new DropLookupService(gson) {
            @Override public java.util.List<com.itemdroplookup.model.DropSource> searchByItemName(String q) {
                return Arrays.asList(new com.itemdroplookup.model.DropSource("A bc","First","1","Always",""),
                        new com.itemdroplookup.model.DropSource("Ab c","Second","1","Always",""));
            }
        };
        ItemSourceLookupService noSources=new ItemSourceLookupService(gson) {
            @Override public java.util.List<com.itemdroplookup.model.ItemSource> searchByItemName(String q) { return Collections.emptyList(); }
        };
        ItemSearchSelection ambiguous=new ItemSearchSelection("abc",fixtures,noSources,prices);
        assertEquals(Arrays.asList("A bc","Ab c"),ambiguous.choices());
        assertEquals("First",ambiguous.dropsFor("A bc").get(0).getMonsterName());
        assertEquals("Second",ambiguous.dropsFor("Ab c").get(0).getMonsterName());
        assertEquals(Collections.singletonList("Ab c"),new ItemSearchSelection("Ab c",fixtures,noSources,prices).choices());
    }
}
