package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.ConditionalDropRate;
import java.util.Map;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** Reviewed alternate rates with an explicit task condition in the primary drop row. */
final class DropTaskRates
{
    static Map<String, ConditionalDropRate> read(Gson gson)
    {
        return ReviewedRows.read(gson, "/conditional_drop_rates.json", "rates", "itemName", DropTaskRates::parse);
    }

    private static ConditionalDropRate parse(JsonObject row, String item)
    {
        fields(row, "monsterName,itemName,quantity,dropRate,notes,taskName,onTaskRate,evidence", "");
        String monster = string(row, "monsterName");
        String quantity = string(row, "quantity");
        String baseRate = string(row, "dropRate");
        String notes = text(row, "notes");
        String task = string(row, "taskName");
        String taskRate = string(row, "onTaskRate");
        require(monster.equals("Drake") && (item.equals("Drake's tooth") || item.equals("Drake's claw"))
                && quantity.equals("1") && notes.isEmpty() && baseRate.equals("1/2560")
                && task.equals("Drake") && taskRate.equals("1/512"), "Unreviewed on-task drop rate");
        evidence(array(row, "evidence"));
        return new ConditionalDropRate(monster, item, quantity, baseRate, notes, task, taskRate);
    }
}
