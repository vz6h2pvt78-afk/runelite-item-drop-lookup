package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class AcquisitionSourceRulesTest
{
    @Test
    public void exclusionsCoverTheVerifiedHistoricalFamilyAndReturnedEquipment() throws Exception
    {
        int excluded = 0;
        Set<String> historicalNames = new HashSet<>();
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/wiki_monster_drops.json"), StandardCharsets.UTF_8))
        {
            for (JsonElement element : new Gson().fromJson(reader, JsonArray.class))
            {
                JsonObject row = element.getAsJsonObject();
                String item = row.get("itemName").getAsString();
                String monster = row.get("monsterName").getAsString();
                if (!AcquisitionSourceRules.isOrdinaryDrop(item, monster))
                {
                    excluded++;
                    if (monster.toLowerCase(java.util.Locale.ROOT).startsWith("bloodthirsty "))
                    {
                        historicalNames.add(monster);
                    }
                }
            }
        }
        assertEquals(28, historicalNames.size());
        assertEquals(949 + 21 + 2, excluded);
        assertFalse(AcquisitionSourceRules.isOrdinaryDrop("Super restore(4)", "Kamil"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Super restore(4)", "Kalphite Queen"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Coins", "Bloodthirsty future monster"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Warrior guild token", "Animated Adamant Armour"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Rune arrow", "Drake"));
    }
}
