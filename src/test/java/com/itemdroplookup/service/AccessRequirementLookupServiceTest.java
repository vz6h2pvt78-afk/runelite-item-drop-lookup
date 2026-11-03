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
    private final AccessRequirementLookupService requirements = new AccessRequirementLookupService(new Gson());
    private final MonsterLocationLookupService locations = new MonsterLocationLookupService(new Gson());

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
        assertTrue(facts("Abyssal Sire", "Abyssal whip").isEmpty());
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
        assertTrue(facts("Bull shark", "Great white shark jaw").isEmpty());
    }

    @Test
    public void missingFactsDoNotRemoveExistingSources()
    {
        assertTrue(facts("Goblin", "Goblin mail").isEmpty());
        assertFalse(new DropLookupService(new Gson()).searchByItemName("Goblin mail").isEmpty());
    }
}
