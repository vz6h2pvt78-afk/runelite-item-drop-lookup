package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AnimatorReward;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

final class AnimatorRewards
{
    private static final Set<String> METALS = Set.of("Bronze", "Iron", "Steel", "Black", "Mithril", "Adamant", "Rune");

    static Map<String, AnimatorReward> read(Gson gson)
    {
        return ReviewedRows.read(gson, "/animator_rewards.json", "rewards", "monsterName", AnimatorRewards::parse);
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
