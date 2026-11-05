package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.AccessRequirement;
import com.itemdroplookup.model.DropSource;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedCombatSlayerTest
{
    private final Gson gson = new Gson();
    private final DropLookupService drops = new DropLookupService(gson);
    private final MonsterLocationLookupService locations = new MonsterLocationLookupService(gson, drops);
    private final AccessRequirementLookupService access = new AccessRequirementLookupService(gson, drops, locations);

    @Test public void threeExactMonstersGainOnlyTheirOwnCombatRequirement()
    {
        for (String[] expected : new String[][] {
                {"Dark beast", "dark-beast-slayer", "90 Slayer to kill."},
                {"Greater Nechryael", "greater-nechryael-slayer", "80 Slayer to harm."},
                {"Mutated Bloodveld", "mutated-bloodveld-slayer", "50 Slayer to kill."}})
        {
            DropSource shard = drops.searchByItemName("Crystal shard").stream()
                    .filter(source -> source.getMonsterName().equals(expected[0])).findFirst().get();
            List<AccessRequirement> facts = access.forSource(shard);
            AccessRequirement skill = facts.stream().filter(fact -> fact.getId().equals(expected[1])).findFirst().get();
            assertEquals(expected[2], skill.getText());
            assertEquals(AccessRequirement.Scope.MONSTER, skill.getScope());
            assertEquals(AccessRequirement.AppliesTo.COMBAT, skill.getAppliesTo());
            assertEquals(AccessRequirement.Type.SKILL, skill.getType());
            assertEquals("Slayer", skill.getSkill());
            assertTrue(skill.isSourceCardRequirement());
            assertFalse(skill.isBoundItemDropContext());
            assertFalse(skill.isPerRollContext());
            assertTrue(facts.get(0).isBoundItemDropContext());
            assertFalse(facts.stream().anyMatch(fact -> fact.getAppliesTo() == AccessRequirement.AppliesTo.TASK_ELIGIBILITY
                    || fact.getAppliesTo() == AccessRequirement.AppliesTo.ACCESS || fact.getType() == AccessRequirement.Type.QUEST));
            assertEquals(MonsterLocationLookupService.LocationMatch.AssociationOrigin.REVIEWED_ITEM_LOCATION,
                    locations.lookup(shard).getAssociationOrigin());
            DropSource ordinary = drops.searchByItemName("Coins").stream()
                    .filter(source -> source.getMonsterName().equals(expected[0])).findFirst().get();
            assertTrue(access.forSource(ordinary).stream().anyMatch(fact -> fact.getId().equals(expected[1])));
        }
    }

    @Test public void parentSuperiorAndOtherIorwerthMonstersKeepTheirOwnFacts()
    {
        for (String name : List.of("Nechryael", "Bloodveld", "Night beast", "Nechryarch", "Insatiable mutated Bloodveld", "Waterfiend", "Kurask"))
            assertFalse(access.forSource(new DropSource("Crystal shard", name, "3-5", "1/24", "")).stream()
                    .anyMatch(fact -> List.of("dark-beast-slayer", "greater-nechryael-slayer", "mutated-bloodveld-slayer").contains(fact.getId())));
        List<AccessRequirement> kurask = access.forSource(new DropSource("Crystal shard", "Kurask", "3-5", "1/24", ""));
        assertTrue(kurask.stream().anyMatch(fact -> "Slayer".equals(fact.getSkill()) && Integer.valueOf(70).equals(fact.getLevel())));
        assertFalse(access.forSource(new DropSource("Crystal shard", "Waterfiend", "3-5", "1/24", "")).stream()
                .anyMatch(fact -> "Slayer".equals(fact.getSkill())));
    }
}
