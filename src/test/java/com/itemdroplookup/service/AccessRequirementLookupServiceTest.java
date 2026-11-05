package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.AccessRequirement;
import com.itemdroplookup.model.AccessRequirement.AppliesTo;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccessRequirementLookupServiceTest
{
    @Test
    public void nightBeastDropContextIsNotAnAccessGateOrAFamilyRule()
    {
        AccessRequirement fact = facts("Night beast", "Adamant sq shield").stream()
                .filter(f -> f.getAppliesTo() == AppliesTo.DROP_CONTEXT).findFirst().get();
        assertEquals("Drop context", fact.getLabel());
        assertTrue(fact.isSourceCardFact());
        assertFalse(fact.isSourceCardRequirement());
        assertTrue(fact.getText().contains("three additional items"));
        assertTrue(fact.getText().contains("At most one"));
        assertTrue(fact.getText().contains("Regular-table rates and quantities shown here are per roll"));
        assertTrue(fact.getText().contains("Guaranteed and tertiary drops are separate"));
        assertFalse(facts("Dark beast", "Adamant sq shield").stream().anyMatch(f -> f.getAppliesTo() == AppliesTo.DROP_CONTEXT));
        assertFalse(facts("Nechryarch", "Adamant sq shield").stream().anyMatch(f -> f.getAppliesTo() == AppliesTo.DROP_CONTEXT));
        DropLookupService drops = new DropLookupService(new Gson());
        assertEquals("Always", drops.searchByItemName("Big bones").stream().filter(d -> d.getMonsterName().equals("Night beast")).findFirst().get().getDropRate());
        assertEquals("1/400", drops.searchByItemName("Long bone").stream().filter(d -> d.getMonsterName().equals("Night beast")).findFirst().get().getDropRate());
        assertEquals("1", drops.searchByItemName("Adamant sq shield").stream().filter(d -> d.getMonsterName().equals("Night beast")).findFirst().get().getQuantity());
    }

    @Test
    public void sentinelSpawnContextIsExactAndDistinctFromCombatAndDropEligibility()
    {
        List<AccessRequirement> sentinel = facts("Basilisk Sentinel", "Basilisk jaw");
        assertEquals(1, sentinel.size());
        AccessRequirement spawn = sentinel.get(0);
        assertTrue(spawn.isSpawnContext());
        assertTrue(spawn.isSourceCardFact());
        assertFalse(spawn.isSourceCardRequirement());
        assertEquals("Spawn condition", spawn.getLabel());
        assertEquals("May spawn after killing a Basilisk Knight while on a Basilisk Slayer task with Bigger and Badder unlocked.", spawn.getText());
        assertEquals(2, spawn.getEvidence().size());
        assertEquals(15373808L, spawn.getEvidence().get(0).getRevisionId());
        assertEquals(15340714L, spawn.getEvidence().get(1).getRevisionId());
        for (String other : new String[]{"Basilisk Knight", "Basilisk", "Night beast", "Vorkath"})
            assertFalse(facts(other, "Coins").stream().anyMatch(AccessRequirement::isSpawnContext));
        DropLookupService drops = new DropLookupService(new Gson());
        List<DropSource> jaw = drops.searchByItemName("Basilisk jaw");
        assertEquals(2, jaw.size());
        DropSource source = jaw.stream().filter(d -> d.getMonsterName().equals("Basilisk Sentinel")).findFirst().get();
        assertEquals("1/1000", source.getDropRate());
        assertEquals("1", source.getQuantity());
        assertNull(source.getConditionalRate());
    }

    @Test
    public void sentinelRegularRollContextIsBoundToExactDirectRows()
    {
        AccessRequirement regular = AccessRequirement.perRollContextIn(facts("Basilisk Sentinel", "Adamant kiteshield"));
        // Use the real signature for item-scoped facts.
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops);
        DropSource shield = drops.searchByItemName("Adamant kiteshield").stream()
                .filter(d -> d.getMonsterName().equals("Basilisk Sentinel")).findFirst().get();
        assertNull(regular);
        regular = AccessRequirement.perRollContextIn(access.forSource(shield));
        assertNotNull(regular);
        assertTrue(regular.isSourceCardFact());
        assertFalse(regular.isSourceCardRequirement());
        assertEquals(AccessRequirement.Scope.ITEM, regular.getScope());
        assertEquals(AppliesTo.DROP_CONTEXT, regular.getAppliesTo());
        assertEquals("Drop context", regular.getLabel());
        assertEquals("1/52", shield.getDropRate());
        assertNull(AccessRequirement.perRollContextIn(access.forSource(
                new DropSource(shield.getItemName(), shield.getMonsterName(), "2", shield.getDropRate(), ""))));
        for (String item : new String[]{"Basilisk jaw", "Basilisk head", "Basilisk bone", "Big bones", "Brimstone key",
                "Mystic hat (light)", "Long bone", "Curved bone"})
            for (DropSource source : drops.searchByItemName(item))
                assertNull(AccessRequirement.perRollContextIn(access.forSource(source)));
        for (String monster : new String[]{"Basilisk Knight", "Basilisk", "Night beast"})
            assertNull(AccessRequirement.perRollContextIn(access.forSource(new DropSource("Adamant kiteshield", monster, "1", "1/52", ""))));
    }

    @Test
    public void superiorOrdinaryRollsBindOnlyTheReviewedExactRows() throws Exception
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops);
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, String> parents = java.util.Map.of(
                "Cave abomination", "Cave horror", "Marble gargoyle", "Gargoyle", "Choke devil", "Dust devil");
        int excluded = 0;
        try (java.io.Reader input = new java.io.InputStreamReader(
                getClass().getResourceAsStream("/wiki_monster_drops.json"), java.nio.charset.StandardCharsets.UTF_8))
        {
            for (DropSource source : gson.fromJson(input, DropSource[].class))
            {
                if (!parents.containsKey(source.getMonsterName())) continue;
                List<AccessRequirement> facts = access.forSource(source);
                assertFalse(facts.stream().anyMatch(AccessRequirement::isSpawnContext));
                AccessRequirement context = AccessRequirement.perRollContextIn(facts);
                if (context == null)
                {
                    excluded++;
                    continue;
                }
                counts.merge(source.getMonsterName(), 1, Integer::sum);
                assertEquals(1, facts.stream().filter(AccessRequirement::isPerRollContext).count());
                assertEquals(AccessRequirement.Scope.ITEM, context.getScope());
                assertEquals(AppliesTo.DROP_CONTEXT, context.getAppliesTo());
                assertFalse(context.isSourceCardRequirement());
                assertTrue(context.getText().contains("pre-roll drops are separate"));
                assertNull(AccessRequirement.perRollContextIn(access.forSource(new DropSource(
                        source.getItemName(), source.getMonsterName(), "999", source.getDropRate(), source.getNotes()))));
                assertNull(AccessRequirement.perRollContextIn(access.forSource(new DropSource(
                        source.getItemName(), source.getMonsterName(), source.getQuantity(), "1/999", source.getNotes()))));
                assertNull(AccessRequirement.perRollContextIn(access.forSource(new DropSource(
                        source.getItemName(), source.getMonsterName(), source.getQuantity(), source.getDropRate(), "Changed notes"))));
                assertNull(AccessRequirement.perRollContextIn(access.forSource(new DropSource(
                        source.getItemName(), parents.get(source.getMonsterName()),
                        source.getQuantity(), source.getDropRate(), source.getNotes()))));
            }
        }
        assertEquals(java.util.Map.of("Cave abomination", 14, "Marble gargoyle", 19, "Choke devil", 23), counts);
        assertEquals(10, excluded);
    }

    @Test
    public void superiorRepeatedItemsKeepDistinctSignaturesAndPreRollsStaySeparate()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops);
        List<DropSource> nature = drops.searchByItemName("Nature rune").stream()
                .filter(source -> source.getMonsterName().equals("Cave abomination"))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(3, nature.size());
        assertEquals(3, nature.stream().map(DropSource::getQuantity).distinct().count());
        assertEquals(3, nature.stream().map(DropSource::getDropRate).distinct().count());
        assertEquals(3, nature.stream().map(source -> AccessRequirement.perRollContextIn(access.forSource(source)).getId())
                .distinct().count());
        for (String item : new String[]{"Granite maul", "Mystic robe top (dark)", "Brittle key", "Brimstone key"})
            for (DropSource source : drops.searchByItemName(item))
                if (source.getMonsterName().equals("Marble gargoyle"))
                    assertNull(AccessRequirement.perRollContextIn(access.forSource(source)));
    }

    @Test
    public void brittleKeyConditionsStayOnTwoExactItemDrops()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops);
        List<DropSource> keys = drops.searchByItemName("Brittle key");
        assertEquals(2, keys.size());
        for (DropSource source : keys)
        {
            List<AccessRequirement> conditions = access.forSource(source).stream()
                    .filter(AccessRequirement::isBoundItemDropContext).collect(java.util.stream.Collectors.toList());
            assertEquals(1, conditions.size());
            AccessRequirement fact = conditions.get(0);
            assertEquals("For this drop", fact.getLabel());
            assertFalse(fact.isSpawnContext());
            assertFalse(fact.isPerRollContext());
            assertEquals("1/150", source.getDropRate());
            assertEquals("1", source.getQuantity());
            assertEquals(source.getMonsterName(), fact.getEvidence().get(0).getPage());
            assertTrue(fact.getText().contains("Gargoyles Slayer task"));
            assertTrue(fact.getText().contains("roof access still locked"));
            assertTrue(fact.getText().contains("no Brittle key in your bank or inventory"));
            for (DropSource wrong : List.of(
                    new DropSource("Coins", source.getMonsterName(), "1", "1/150", ""),
                    new DropSource("Brittle key", source.getMonsterName(), "2", "1/150", ""),
                    new DropSource("Brittle key", source.getMonsterName(), "1", "1/151", ""),
                    new DropSource("Brittle key", source.getMonsterName(), "1", "1/150", "Changed"),
                    new DropSource("Brittle key", "Bloodthirsty gargoyle", "1", "1/150", "")))
                assertFalse(access.forSource(wrong).stream().anyMatch(AccessRequirement::isBoundItemDropContext));
        }
        DropSource groupBone = drops.searchByItemName("Bones").stream()
                .filter(source -> source.getMonsterName().equals("Callisto")).findFirst().get();
        assertFalse(access.forSource(groupBone).stream().anyMatch(AccessRequirement::isBoundItemDropContext));
    }

    @Test
    public void crystalShardConditionsPreserveGuaranteedRowsAndIndependentNightBeastContext()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops);
        java.util.Set<String> expected = java.util.Set.of("Dark beast", "Greater Nechryael", "Kurask",
                "Mutated Bloodveld", "Waterfiend", "Insatiable mutated Bloodveld", "King kurask", "Nechryarch", "Night beast");
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<DropSource> shards = drops.searchByItemName("Crystal shard");
        assertEquals(13, shards.size());
        for (DropSource source : shards)
        {
            List<AccessRequirement> conditions = access.forSource(source).stream()
                    .filter(AccessRequirement::isBoundItemDropContext).collect(java.util.stream.Collectors.toList());
            assertEquals(expected.contains(source.getMonsterName()) ? 1 : 0, conditions.size());
            if (conditions.isEmpty()) continue;
            AccessRequirement fact = conditions.get(0);
            seen.add(source.getMonsterName());
            assertTrue(fact.getText().contains("Iorwerth Dungeon"));
            assertFalse(fact.isPerRollContext());
            assertFalse(fact.isSpawnContext());
            assertEquals(source.getMonsterName(), fact.getEvidence().get(0).getPage());
            assertNull(AccessRequirement.perRollContextIn(access.forSource(source)));
            assertFalse(access.forSource(new DropSource(source.getItemName(), source.getMonsterName(), "999",
                    source.getDropRate(), source.getNotes())).stream().anyMatch(AccessRequirement::isBoundItemDropContext));
            assertFalse(access.forSource(new DropSource("Coins", source.getMonsterName(), source.getQuantity(),
                    source.getDropRate(), source.getNotes())).stream().anyMatch(AccessRequirement::isBoundItemDropContext));
        }
        assertEquals(expected, seen);
        DropSource night = shards.stream().filter(source -> source.getMonsterName().equals("Night beast")).findFirst().get();
        assertEquals("Always", night.getDropRate());
        assertEquals("6-10", night.getQuantity());
        assertTrue(access.forSource(night).stream().anyMatch(fact -> fact.getId().equals("night-beast-drop-roll-context")
                && fact.getText().contains("Guaranteed and tertiary drops are separate")));
    }

    @Test
    public void exactItemConditionsPrecedeGeneralFactsWithoutReorderingThoseFacts()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops);
        for (String[] pair : new String[][]{{"Gargoyle", "Brittle key"}, {"Night beast", "Crystal shard"}})
        {
            DropSource source = drops.searchByItemName(pair[1]).stream()
                    .filter(drop -> drop.getMonsterName().equals(pair[0])).findFirst().get();
            List<AccessRequirement> facts = access.forSource(source);
            assertTrue(facts.get(0).isBoundItemDropContext());
            List<String> general = access.forSource(new DropSource("Different item", pair[0], "1", "", "")).stream()
                    .map(AccessRequirement::getId).collect(java.util.stream.Collectors.toList());
            List<String> remaining = facts.stream().filter(fact -> !fact.isBoundItemDropContext())
                    .map(AccessRequirement::getId).collect(java.util.stream.Collectors.toList());
            assertEquals(general, remaining);
        }
    }

    @Test
    public void eliteClueConditionsStayOnThreeExactRewardsAndKeepTheirSelectorsDistinct()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops);
        for (String[] example : new String[][]{
                {"Aberrant spectre", "Clue scroll (elite)", "non-final elite cryptic"},
                {"Aberrant spectre", "Reward casket (elite)", "the final elite cryptic"},
                {"King Black Dragon", "Key (elite)", "no Key (elite) in your bank or inventory"}})
        {
            for (DropSource source : drops.searchByItemName(example[1]))
            {
                List<AccessRequirement> conditions = access.forSource(source).stream()
                        .filter(AccessRequirement::isBoundItemDropContext).collect(java.util.stream.Collectors.toList());
                if (!source.getMonsterName().equals(example[0]))
                {
                    assertTrue(source.getMonsterName(), conditions.isEmpty());
                    continue;
                }
                assertEquals(1, conditions.size());
                AccessRequirement fact = conditions.get(0);
                assertTrue(fact.getText().contains(example[2]));
                assertEquals("Always", source.getDropRate());
                assertEquals("1", source.getQuantity());
                assertFalse(fact.isPerRollContext());
                assertFalse(fact.isSpawnContext());
                assertEquals(source.getMonsterName(), fact.getEvidence().get(0).getPage());
                if (example[1].equals("Reward casket (elite)")) assertFalse(fact.getText().contains("non-final"));
                if (example[1].equals("Key (elite)")) assertFalse(fact.getText().contains("cryptic"));
                for (DropSource changed : List.of(
                        new DropSource(source.getItemName(), source.getMonsterName(), "2", "Always", ""),
                        new DropSource(source.getItemName(), source.getMonsterName(), "1", "1/2", ""),
                        new DropSource(source.getItemName(), source.getMonsterName(), "1", "Always", "Changed")))
                    assertFalse(access.forSource(changed).stream().anyMatch(AccessRequirement::isBoundItemDropContext));
            }
        }
    }

    private final AccessRequirementLookupService requirements = new AccessRequirementLookupService(new Gson());
    private final MonsterLocationLookupService locations = new MonsterLocationLookupService(new Gson());

    @Test
    public void twoAdditionalSlayerCasesSeparateProtectionFromFinishing()
    {
        List<AccessRequirement> dust = facts("Dust devil", "Adamant axe");
        List<AccessRequirement> gargoyle = facts("Gargoyle", "Adamant boots");
        assertEquals(2, dust.size());
        assertEquals(2, gargoyle.size());
        assertEquals(Integer.valueOf(65), dust.stream().filter(f -> f.getType() == AccessRequirement.Type.SKILL).findFirst().get().getLevel());
        assertEquals(Integer.valueOf(75), gargoyle.stream().filter(f -> f.getType() == AccessRequirement.Type.SKILL).findFirst().get().getLevel());
        assertTrue(dust.stream().anyMatch(f -> f.getText().contains("Equip a facemask, Slayer helmet or Slayer helmet (i) to prevent")));
        assertTrue(gargoyle.stream().anyMatch(f -> f.getText().contains("use a rock hammer or rock thrownhammer at 8 HP or lower")
                && f.getText().contains("wield a granite hammer for automatic finishing")));
        for (String related : new String[]{"Choke devil", "Smoke devil", "Marble gargoyle", "Grotesque Guardians"})
            assertFalse(related, facts(related, "Coins").stream()
                    .anyMatch(f -> f.getId().startsWith("dust-devil-") || f.getId().startsWith("gargoyle-")));
        assertFalse(dust.stream().anyMatch(f -> f.getType() == AccessRequirement.Type.ACTIVE_TASK));
        assertFalse(gargoyle.stream().anyMatch(f -> f.getType() == AccessRequirement.Type.ACTIVE_TASK));
    }

    private List<AccessRequirement> facts(String monster, String item)
    {
        return requirements.forSource(new DropSource(item, monster, "1", "", ""));
    }

    private MonsterLocation location(String monster, String name)
    {
        return locations.lookupByMonsterName(monster).stream().filter(row -> row.getLocation().equals(name)).findFirst().get();
    }

    @Test
    public void slayerRequirementDoesNotLeakIntoMonsterFamily()
    {
        assertEquals(Integer.valueOf(85), facts("Abyssal demon", "Abyssal whip").get(0).getLevel());
        assertEquals(1, facts(" abyssal_demon ", "Abyssal whip").size());
        assertFalse(facts("Abyssal Sire", "Abyssal whip").stream().anyMatch(f -> f.getId().startsWith("abyssal-demon-")));
        assertTrue(facts("Greater abyssal demon", "Abyssal whip").isEmpty());
    }

    @Test
    public void wildernessTaskAppliesOnlyToTheExactMonsterLocation()
    {
        assertEquals("abyssal-demon-wilderness-task", requirements.forLocation(
                location("Abyssal demon", "Wilderness Slayer Cave")).get(0).getId());
        assertTrue(requirements.forLocation(location("Abyssal demon", "Catacombs of Kourend")).isEmpty());
        assertTrue(requirements.forLocation(location("Ankou", "Wilderness Slayer Cave")).isEmpty());
        MonsterLocation mismatched = new Gson().fromJson("{\"id\":\"ec346dbbf5098d24ef88\",\"monsterName\":\"Goblin\"}", MonsterLocation.class);
        assertTrue(requirements.forLocation(mismatched).isEmpty());
    }

    @Test
    public void waterfallAccessRetainsBothAlternativesAndDoesNotSpread()
    {
        List<AccessRequirement> waterfall = requirements.forLocation(location("Fire giant", "Waterfall Dungeon"));
        assertEquals(2, waterfall.size());
        assertTrue(waterfall.get(0).getText().contains("not needed after completing"));
        assertTrue(waterfall.get(1).getText().contains("both Waterfall Quest and the medium Kandarin Diary"));
        assertTrue(requirements.forLocation(location("Fire giant", "Catacombs of Kourend")).isEmpty());
        assertTrue(facts("Fire giant", "Rune scimitar").isEmpty());
    }

    @Test
    public void krakenSeparatesCombatFromAssignmentEligibility()
    {
        List<AccessRequirement> kraken = facts("Kraken", "Kraken tentacle");
        assertEquals(2, kraken.stream().filter(AccessRequirement::isSourceCardRequirement).count());
        AccessRequirement magic = kraken.stream().filter(fact -> "Magic".equals(fact.getSkill())).findFirst().get();
        assertEquals(AppliesTo.TASK_ELIGIBILITY, magic.getAppliesTo());
        assertFalse(magic.isSourceCardRequirement());
        assertTrue(kraken.stream().anyMatch(fact -> fact.getText().contains("cannot be boosted")));
        assertTrue(kraken.stream().anyMatch(fact -> fact.getText().contains("Kraken or cave kraken")));
    }

    @Test
    public void vorkathContextDoesNotInventVariantBinding()
    {
        DropSource source = new DropSource("Superior dragon bones", "Vorkath", "2", "Always", "");
        AccessRequirement fact = requirements.forSource(source).get(0);
        assertEquals(AppliesTo.ENCOUNTER_CONTEXT, fact.getAppliesTo());
        assertFalse(fact.isSourceCardRequirement());
        assertFalse(locations.lookup(source).isVerified());
        assertTrue(fact.getText().contains("post-quest"));
    }

    @Test
    public void sailingBountyEligibilityIsNeverAUniversalCombatGate()
    {
        List<AccessRequirement> shark = facts("Great white shark", "Adamant platebody");
        assertEquals(2, shark.size());
        assertFalse(shark.stream().anyMatch(AccessRequirement::isSourceCardRequirement));
        AccessRequirement sailing = shark.stream().filter(fact -> "Sailing".equals(fact.getSkill())).findFirst().get();
        assertEquals(Integer.valueOf(75), sailing.getLevel());
        assertEquals(AppliesTo.TASK_ELIGIBILITY, sailing.getAppliesTo());
    }

    @Test
    public void bountyDropGateMatchesOnlyTheExactItem()
    {
        assertEquals(1, facts("Great white shark", "Great white shark jaw").stream()
                .filter(fact -> fact.getAppliesTo() == AppliesTo.ITEM_DROP).count());
        assertEquals(1, facts("Great white shark", "Great white shark liver").stream()
                .filter(fact -> fact.getAppliesTo() == AppliesTo.ITEM_DROP).count());
        assertFalse(facts("Great white shark", "Raw shark").stream().anyMatch(AccessRequirement::isSourceCardRequirement));
        assertFalse(facts("Bull shark", "Great white shark jaw").stream().anyMatch(AccessRequirement::isSourceCardFact));
    }

    @Test
    public void lyreNeedsTheTrialStartedOnlyForTheFourVerifiedSources()
    {
        for (String monster : new String[] {"Borrokar", "Freidir", "Lanzig", "Lensa"})
        {
            List<AccessRequirement> lyre = facts(monster, "Lyre");
            assertEquals(1, lyre.size());
            assertTrue(lyre.get(0).isSourceCardRequirement());
            assertEquals(AppliesTo.ITEM_DROP, lyre.get(0).getAppliesTo());
            assertTrue(lyre.get(0).getText().contains("Speak to Olaf the Bard"));
            assertTrue(lyre.get(0).getText().contains("start his trial"));
            assertFalse(lyre.get(0).getText().contains("completion"));
            assertTrue(facts(monster, "Fremennik blade").isEmpty());
            assertTrue(facts(monster, "Enchanted lyre").isEmpty());
        }
        // The fifth raw source has not been verified by the scoped quest evidence.
        assertTrue(facts("Jennella", "Lyre").isEmpty());
        List<DropSource> drops = new DropLookupService(new Gson()).searchByItemName("Lyre");
        assertEquals(5, drops.size());
        assertTrue(drops.stream().allMatch(d -> "30/512".equals(d.getDropRate())
                && "1".equals(d.getQuantity()) && d.getNotes().isEmpty()));
    }

    @Test
    public void bearMeatFormContextKeepsIdentityQuantityAndEquipmentScope()
    {
        AccessRequirement alexis = facts("Alexis", "Raw bear meat").get(0);
        assertTrue(alexis.isSourceCardRequirement());
        assertTrue(alexis.getText().contains("Canifis citizen, human form"));
        assertTrue(alexis.getText().contains("Wield Wolfbane"));
        AccessRequirement werewolf = facts("Werewolf", "Raw bear meat").get(0);
        assertTrue(werewolf.getText().startsWith("Werewolf form"));
        assertFalse(werewolf.getText().contains("Wield Wolfbane"));
        assertTrue(facts("Roavar", "Raw bear meat").isEmpty());
        assertTrue(facts("Alexis", "Coins").isEmpty());
        List<DropSource> meat = new DropLookupService(new Gson()).searchByItemName("Raw bear meat");
        assertEquals(25, meat.size());
        assertTrue(meat.stream().anyMatch(d -> "Alexis".equals(d.getMonsterName())
                && "2".equals(d.getQuantity()) && "10/512".equals(d.getDropRate())));
        assertTrue(meat.stream().anyMatch(d -> "Werewolf".equals(d.getMonsterName())
                && "5".equals(d.getQuantity()) && "10/512".equals(d.getDropRate())));
    }

    @Test
    public void entDirectRewardTaskDoesNotBecomeAHarvestRequirement()
    {
        AccessRequirement task = facts("Ent", "Slayer's enchantment").get(0);
        assertTrue(task.isSourceCardRequirement());
        assertTrue(task.getText().contains("Wilderness"));
        assertTrue(task.getText().contains("Krystilia"));
        assertTrue(task.getText().contains("drops on death"));
        assertTrue(facts("Ent", "Magic logs").isEmpty());
        assertTrue(facts("Ent", "Logs").isEmpty());
        assertTrue(facts("Goblin", "Slayer's enchantment").isEmpty());
    }

    @Test
    public void missingFactsDoNotRemoveExistingSources()
    {
        assertTrue(facts("Goblin", "Goblin mail").isEmpty());
        assertFalse(new DropLookupService(new Gson()).searchByItemName("Goblin mail").isEmpty());
    }
}
