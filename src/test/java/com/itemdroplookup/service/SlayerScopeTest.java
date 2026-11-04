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
