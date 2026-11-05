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
