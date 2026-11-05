package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.itemdroplookup.model.AccessRequirement;
import com.itemdroplookup.model.DropSource;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerScopeTest
{
    private final AccessRequirementLookupService access = new AccessRequirementLookupService(new Gson());
    private List<AccessRequirement> facts(String monster)
    {
        return access.forSource(new DropSource("Coins",monster,"1","1/2",""));
    }
    @Test public void sailingBountyLevelsApplyToAssignmentAndSixExactDrops()
    {
        for (String[] reviewed : List.of(new String[]{"Bull shark","30"}, new String[]{"Hammerhead shark","40"},
                new String[]{"Tiger shark","55"}))
        {
            String shark = reviewed[0];
            List<AccessRequirement> ordinary = facts(shark);
            assertEquals(1, ordinary.size());
            AccessRequirement eligibility = ordinary.get(0);
            assertEquals(AccessRequirement.AppliesTo.TASK_ELIGIBILITY, eligibility.getAppliesTo());
            assertEquals("Sailing", eligibility.getSkill());
            assertEquals(Integer.valueOf(reviewed[1]), eligibility.getLevel());
            assertFalse(eligibility.isSourceCardRequirement());
            for (String suffix : List.of(" jaw", " liver"))
            {
                List<AccessRequirement> item = access.forSource(new DropSource(shark + suffix,shark,"1","1/10",""));
                assertEquals(2, item.size());
                assertTrue(item.stream().anyMatch(f -> f.getType() == AccessRequirement.Type.ACTIVE_TASK && f.isSourceCardFact()
                        && f.getText().contains(reviewed[1] + " Sailing to receive one")));
            }
            assertTrue(access.forSource(new DropSource(shark + " jaw","Karamjan tiger shark","1","1/2","")).isEmpty());
        }
        assertTrue(facts("Orca").isEmpty());
    }

    @Test public void threeNamedMonstersKeepCombatVerbsAndQuestAssignmentSeparate()
    {
        for (String[] row : new String[][]{{"Bloodveld","50 Slayer to kill.",""},{"Nechryael","80 Slayer to harm.","Priest in Peril"},
                {"Spiritual mage","83 Slayer to kill.","Death Plateau"}})
        {
            List<AccessRequirement> entries = facts(row[0]);
            assertEquals(row[2].isEmpty() ? 1 : 2, entries.size());
            assertTrue(entries.stream().anyMatch(f -> f.isSourceCardRequirement() && f.getText().equals(row[1])));
            if (!row[2].isEmpty()) assertTrue(entries.stream().anyMatch(f -> f.getLabel().equals("Task eligibility")
                    && !f.isSourceCardFact() && f.getText().contains(row[2])));
        }
        for (String name : List.of("Mutated Bloodveld","Greater Nechryael","Spiritual warrior","Spiritual ranger"))
            assertFalse(facts(name).stream().anyMatch(f -> f.getId().equals("bloodveld-slayer") || f.getId().equals("nechryael-slayer")
                    || f.getId().equals("spiritual-mage-slayer")));
    }

    @Test public void laterMonstersKeepAttackRulesAndProtectionWithTheirExactCreature()
    {
        assertMonster("Basilisk", 40, "Protection", "mirror shield or V's shield");
        assertMonster("Aberrant spectre", 60, "Protection", "nose peg or Slayer helmet");
        assertMonster("Turoth", 55, "Combat", "For ordinary attacks");
        assertMonster("Kurask", 70, "Combat", "Burn damage can also apply");
        for (String relative : List.of("Basilisk Knight", "King kurask", "Deviant spectre", "Spiked turoth"))
        {
            assertTrue(relative, facts(relative).isEmpty());
        }
    }

    private void assertMonster(String name, int level, String label, String text)
    {
        List<AccessRequirement> entries = facts(name);
        assertEquals(name, 2, entries.size());
        assertTrue(entries.stream().anyMatch(f -> "Slayer".equals(f.getSkill()) && Integer.valueOf(level).equals(f.getLevel())));
        assertTrue(entries.stream().anyMatch(f -> label.equals(f.getLabel()) && f.isSourceCardFact() && f.getText().contains(text)));
    }

    @Test public void earlyMonstersKeepSkillSeparateFromProtectionOrFinishing()
    {
        assertTrue(facts("Banshee").stream().anyMatch(f -> f.getLabel().equals("Protection") && f.isSourceCardFact() && f.getText().contains("earmuffs")));
        assertTrue(facts("Twisted banshee").isEmpty());
        assertTrue(facts("Rockslug").stream().anyMatch(f -> f.getLabel().equals("Finishing") && f.getText().contains("or use a brine sabre")));
        for(String name : List.of("Cave slime","Cave crawler","Crawling Hand"))
        {
            assertEquals(1,facts(name).size());
            assertEquals("Slayer",facts(name).get(0).getSkill());
        }
        assertTrue(facts("Giant rockslug").isEmpty());
    }
}
