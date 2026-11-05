package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.itemdroplookup.model.ConditionalDropRate;
import java.util.Map;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** Reviewed alternate rates with an explicit task condition in the primary drop row. */
final class DropTaskRates
{
    static Map<String, ConditionalDropRate> read(Gson gson)
    {
        Map<String, ConditionalDropRate> rates = ReviewedRows.read(gson, "/conditional_drop_rates.json", "rates",
                "itemName", (row, item) -> parse(row, item, false));
        // Each reader accepts disjoint item identities, so one optional file cannot replace the other.
        rates.putAll(ReviewedRows.read(gson, "/basilisk_jaw_task_rate.json", "rates",
                "itemName", (row, item) -> parse(row, item, true)));
        return rates;
    }

    private static ConditionalDropRate parse(JsonObject row, String item, boolean jaw)
    {
        fields(row, "monsterName,itemName,quantity,dropRate,notes,taskName,onTaskRate,evidence", "");
        String monster = string(row, "monsterName");
        String quantity = string(row, "quantity");
        String baseRate = string(row, "dropRate");
        String notes = text(row, "notes");
        String task = string(row, "taskName");
        String taskRate = string(row, "onTaskRate");
        boolean reviewed;
        if (jaw)
            reviewed = monster.equals("Basilisk Knight") && item.equals("Basilisk jaw") && baseRate.equals("1/5000")
                    && task.equals("Basilisk") && taskRate.equals("1/1000");
        else
            reviewed = monster.equals("Drake") && (item.equals("Drake's tooth") || item.equals("Drake's claw"))
                    && baseRate.equals("1/2560") && task.equals("Drake") && taskRate.equals("1/512");
        require(reviewed && quantity.equals("1") && notes.isEmpty(), "Unreviewed on-task drop rate");
        JsonArray sources = array(row, "evidence");
        evidence(sources);
        require(sources.size() == 1, "One exact primary revision required");
        JsonObject proof = object(sources.get(0));
        int revision = jaw ? 15340714 : 15371749;
        integer(proof, "revisionId", revision, revision);
        require(string(proof, "page").equals(monster)
                && string(proof, "sourceUrl").equals("https://oldschool.runescape.wiki/w/" + monster.replace(' ', '_') + "?oldid=" + revision),
                "Wrong conditional-rate primary");
        return new ConditionalDropRate(monster, item, quantity, baseRate, notes, task, taskRate);
    }
}
