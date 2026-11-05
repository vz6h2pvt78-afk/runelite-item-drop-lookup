package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AnimatorReward;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

@Slf4j
final class AnimatorRewards
{
    private static final Set<String> METALS = Set.of("Bronze", "Iron", "Steel", "Black", "Mithril", "Adamant", "Rune");

    static Map<String, AnimatorReward> read(Gson gson)
    {
        Map<String, AnimatorReward> result = new HashMap<>();
        try (InputStream input = AnimatorRewards.class.getResourceAsStream("/animator_rewards.json"))
        {
            if (input == null) return result;
            JsonObject root = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            fields(root, "schemaVersion,rewards", "");
            integer(root, "schemaVersion", 1, 1);
            Set<String> seen = new HashSet<>();
            for (JsonElement element : array(root, "rewards"))
            {
                try
                {
                    JsonObject row = object(element);
                    String monster = string(row, "monsterName");
                    if (!seen.add(monster))
                    {
                        result.remove(monster);
                        continue;
                    }
                    result.put(monster, parse(row, monster));
                }
                catch (RuntimeException ex)
                {
                    log.debug("One animator reward could not be verified", ex);
                }
            }
        }
        catch (Exception ex)
        {
            log.debug("Reviewed animator rewards unavailable", ex);
        }
        return result;
    }

    private static AnimatorReward parse(JsonObject row, String monster)
    {
        fields(row, "monsterName,itemName,quantity,dropRate,notes,metal,items,facility,easyTokens,mediumTokens,evidence", "");
        String metal = string(row, "metal");
        require(METALS.contains(metal) && monster.equals("Animated " + metal + " Armour") && string(row, "itemName").equals("Warrior guild token")
                && string(row, "dropRate").equals("Always") && text(row, "notes").isEmpty()
                && string(row, "facility").equals("Magical animator"), "Unreviewed animator identity");
        List<String> items = new ArrayList<>();
        for (JsonElement item : array(row, "items")) items.add(item.getAsString());
        require(items.equals(List.of(metal + " platebody", metal + " platelegs", metal + " full helm")), "Mismatched armour pieces");
        String quantity = string(row, "quantity");
        require(quantity.matches("[1-9][0-9]?"), "Invalid base token amount");
        int base = Integer.parseInt(quantity);
        int easy = integer(row, "easyTokens", base + 1, 200);
        int medium = integer(row, "mediumTokens", easy + 1, 200);
        evidence(array(row, "evidence"));
        return new AnimatorReward(metal, items, base, easy, medium);
    }
}
