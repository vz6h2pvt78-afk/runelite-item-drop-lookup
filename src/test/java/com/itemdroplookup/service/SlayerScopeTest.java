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
