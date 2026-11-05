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
    @Test public void groupRewardRolesMatchOnlyTheirExactBossItemQuantityRateAndNotes()
    {
        for (String[] example : new String[][] {{"Callisto","Voidwaker hilt"},{"Vet'ion","Voidwaker blade"},{"Venenatis","Voidwaker gem"}})
        {
            for (String[] kind : new String[][] {{"Bones","Always","OTHER_ELIGIBLE"},
                    {"Big bones","Always","TOP_DAMAGE"},{example[1],"1/360","PER_KILL_UNIQUE"}})
            {
                DropSource source = new DropSource(kind[0],example[0],"1",kind[1],"");
                AccessRequirement fact = AccessRequirement.groupRewardIn(access.forSource(source));
                assertEquals(kind[2],fact.getGroupRewardRole().name());
                for (DropSource other : List.of(new DropSource(kind[0],example[0],"2",kind[1],""),
                        new DropSource(kind[0],example[0],"1","1/912",""),
                        new DropSource(kind[0],example[0],"1",kind[1],"Another variant"),
                        new DropSource(kind[0],"Artio","1",kind[1],"")))
                    assertNull(AccessRequirement.groupRewardIn(access.forSource(other)));
            }
        }
    }

    @Test public void fiveNamedSlayerBossesKeepSkillsTasksQuestsAndProtectionSeparate()
    {
        for (Object[] row : new Object[][] {{"Cerberus",91,1},{"Alchemical Hydra",95,2},{"Abyssal Sire",85,2},
                {"Basilisk Knight",60,3},{"Thermonuclear smoke devil",93,2}})
        {
            List<AccessRequirement> reviewed = facts((String)row[0]);
            assertEquals(row[0].toString(),((Integer)row[2]).intValue(),reviewed.size());
            AccessRequirement skill = reviewed.stream().filter(f->f.getType()==AccessRequirement.Type.SKILL).findFirst().get();
            assertEquals(row[1],skill.getLevel());
            assertEquals(AccessRequirement.AppliesTo.COMBAT,skill.getAppliesTo());
        }
        assertTrue(facts("Alchemical Hydra").stream().anyMatch(f -> "Active task".equals(f.getLabel()) && f.getText().contains("boss task from Konar")));
        assertTrue(facts("Abyssal Sire").stream().anyMatch(f -> "Active task".equals(f.getLabel()) && f.getText().contains("other than Krystilia")));
        assertTrue(facts("Basilisk Knight").stream().anyMatch(f -> "Quest".equals(f.getLabel()) && f.getText().contains("The Fremennik Exiles")));
        assertTrue(facts("Basilisk Knight").stream().anyMatch(f -> "Protection".equals(f.getLabel()) && f.getText().contains("mirror shield or V's shield")));
        assertTrue(facts("Thermonuclear smoke devil").stream().anyMatch(f -> f.getText().contains("one off-task kill with 93 unboosted Slayer")));
        assertFalse(facts("Smoke devil").stream().anyMatch(f -> f.getText().contains("Diary")));
        assertFalse(facts("Hydra").stream().anyMatch(f -> f.getId().startsWith("alchemical-hydra")));
        assertFalse(facts("Abyssal demon").stream().anyMatch(f -> f.getId().startsWith("abyssal-sire")));
        assertTrue(facts("Basilisk").stream().anyMatch(f -> Integer.valueOf(40).equals(f.getLevel())));
        assertFalse(facts("Basilisk Knight").stream().anyMatch(f -> f.getAppliesTo()==AccessRequirement.AppliesTo.TASK_ELIGIBILITY));
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
        assertFalse(facts("Orca").stream().anyMatch(AccessRequirement::isSourceCardRequirement));
    }

    @Test public void oceanBountyFactsFollowExactRawSignaturesAndPreserveOlderMatching()
    {
        DropSource tusk = new DropSource("Narwhal tusk","Narwhal","1","1/10","");
        assertEquals(2, access.forSource(tusk).size());
        for (DropSource other : List.of(new DropSource("Narwhal tusk","Narwhal","2","1/10",""),
                new DropSource("Narwhal tusk","Narwhal","1","2/20",""),
                new DropSource("Narwhal tusk","Narwhal","1","1/10","Other context")))
        {
            List<AccessRequirement> facts = access.forSource(other);
            assertEquals(1, facts.size());
            assertEquals(Integer.valueOf(62), facts.get(0).getLevel());
        }
        for (String item : List.of("Echo pearl","Orca blubber","Orca teeth"))
        {
            List<AccessRequirement> orca = access.forSource(new DropSource(item,"Orca","1","1/70",""));
            assertEquals(1, orca.size());
            assertEquals(AccessRequirement.AppliesTo.TASK_ELIGIBILITY, orca.get(0).getAppliesTo());
            assertEquals(Integer.valueOf(80), orca.get(0).getLevel());
        }
        assertTrue(access.forSource(new DropSource("Bull shark jaw","Bull shark","2","1/99",""))
                .stream().anyMatch(f -> f.getType() == AccessRequirement.Type.ACTIVE_TASK));
    }

    @Test public void bootSourcesPreserveEveryExistingDropTupleAndTheirOwnKillLevel()
    {
        DropLookupService drops=new DropLookupService(new Gson());
        for (String[] row : new String[][]{{"Mystic boots (dark)","Infernal Mage","45","1"},
                {"Mithril boots","Jelly","52","2"},{"Steel boots","Pyrefiend","30","1"}})
        {
            List<DropSource> exact = drops.searchByItemName(row[0]).stream().filter(d -> row[1].equals(d.getMonsterName()))
                    .collect(java.util.stream.Collectors.toList());
            assertEquals(Integer.parseInt(row[3]),exact.size());
            for (DropSource source : exact)
            {
                List<AccessRequirement> facts = access.forSource(source);
                assertEquals(1,facts.size());
                assertEquals(row[2]+" Slayer to kill.",facts.get(0).getText());
                assertTrue(facts.get(0).isSourceCardRequirement());
            }
        }
        for (String related : List.of("Warped Jelly","Malevolent mage","Flaming pyrelord"))
            assertFalse(facts(related).stream().anyMatch(f -> List.of("jelly-slayer","infernal-mage-slayer","pyrefiend-slayer").contains(f.getId())));
    }

    @Test public void gearSourcesDistinguishCombatTaskAndProtection()
    {
        List<AccessRequirement> smoke = facts("Smoke devil");
        assertEquals(3, smoke.size());
        assertTrue(smoke.stream().anyMatch(f -> f.getText().equals("93 Slayer to harm.") && f.isSourceCardRequirement()));
        assertTrue(smoke.stream().anyMatch(f -> f.getType()==AccessRequirement.Type.ACTIVE_TASK && f.isSourceCardRequirement()));
        assertTrue(smoke.stream().anyMatch(f -> f.getAppliesTo()==AccessRequirement.AppliesTo.PROTECTION
                && !f.isSourceCardRequirement() && f.getText().contains("facemask, Slayer helmet or Slayer helmet (i)")));
        List<AccessRequirement> wyvern = facts("Skeletal Wyvern");
        assertEquals(2, wyvern.size());
        assertTrue(wyvern.stream().anyMatch(f -> f.getText().equals("72 Slayer to harm.")));
        assertTrue(wyvern.stream().anyMatch(f -> f.getLabel().equals("Protection")
                && f.getText().contains("elemental shield, mind shield, dragonfire shield, dragonfire ward or ancient wyvern shield reduces")));
        assertMonster("Cockatrice",25,"Protection","mirror shield or V's shield");
        assertEquals(1, facts("Drake").size());
        assertEquals("84 Slayer to kill.", facts("Drake").get(0).getText());
        assertFalse(facts("Thermonuclear smoke devil").stream().anyMatch(f -> f.getId().startsWith("smoke-devil-")));
        assertFalse(facts("Thermonuclear smoke devil").stream().anyMatch(f -> f.getAppliesTo()==AccessRequirement.AppliesTo.PROTECTION));
        for (String other : List.of("Guardian Drake","Choke devil","Cockathrice","Ancient Wyvern"))
            assertTrue(other, facts(other).isEmpty());
    }

    @Test public void basementUnlockRequiresOneVerifiedLocationAndExactVersion()
    {
        Gson g = new Gson();
        DropSource defender = new DropSource("Dragon defender","Cyclops","1","1/100","");
        MonsterLocationLookupService locations = new MonsterLocationLookupService(g);
        assertTrue(locations.hasUniqueVerifiedBinding(defender,"d5a1fab73fd067a98909","Warriors' Guild Basement"));
        assertFalse(locations.hasUniqueVerifiedBinding(defender,"ec2299b243b871a0ab97","Warriors' Guild Basement"));
        assertFalse(locations.hasUniqueVerifiedBinding(defender,"d5a1fab73fd067a98909","Upstairs"));
        assertEquals("Access", access.itemAccessFor(defender).get(0).getLabel());
        assertTrue(access.itemAccessFor(new DropSource("Rune defender","Cyclops","1","2/100","")).isEmpty());
        assertTrue(access.itemAccessFor(new DropSource("Dragon defender","Cyclops","1","1/99","")).isEmpty());
        MonsterLocationLookupService missing = new MonsterLocationLookupService(g) {
            @Override boolean hasUniqueVerifiedBinding(DropSource drop, String id, String version) { return false; }
        };
        assertTrue(new AccessRequirementLookupService(g,new DropLookupService(g),missing).forSource(defender).isEmpty());
    }

    @Test public void anOrphanedOceanFactIsWithheldIndependently()
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson) {
            @Override boolean hasUniqueRecordedDrop(DropSource row)
            {
                return !row.getItemName().equals("Stingray fin") && super.hasUniqueRecordedDrop(row);
            }
        };
        AccessRequirementLookupService isolated = new AccessRequirementLookupService(gson, drops);
        assertEquals(1, isolated.forSource(new DropSource("Stingray fin","Stingray","1","1/2","")).size());
        assertEquals(2, isolated.forSource(new DropSource("Stingray skin","Stingray","1","1/10","")).size());
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
        assertFalse(facts("Basilisk Knight").stream().anyMatch(f -> f.getId().equals("basilisk-slayer") || f.getId().equals("basilisk-protection")));
        for (String relative : List.of("King kurask", "Deviant spectre", "Spiked turoth"))
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
