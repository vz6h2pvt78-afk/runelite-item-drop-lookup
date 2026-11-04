package com.itemdroplookup.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AcquisitionMethod;
import com.itemdroplookup.model.AcquisitionMethod.Section;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** One bounded ordinary-loot table. Capture routes never become extra rolls. */
final class LootEventAdapter
{
    private static final String EVENT = "dragon-impling-ordinary-loot";
    private static final String JAR = "Dragon impling jar";

    static List<AcquisitionMethod> read(JsonArray events, JsonArray coverage)
    {
        Map<String, JsonObject> tables = new LinkedHashMap<>();
        for (JsonElement value : events)
        {
            JsonObject event = object(value);
            fields(event, "id,sourceName,containerName,probabilityBasis,probabilityContext,quantityBasis,"
                    + "otherRewardsNotice,outcomes,routes,evidence", "");
            String id = string(event, "id");
            require(EVENT.equals(id) && tables.put(id, event) == null, "Unsupported or duplicate loot event");
            require(JAR.equals(string(event, "containerName")), "Wrong container identity");
            require("WIKI_LISTED_TABLE".equals(string(event, "probabilityBasis"))
                    && "SUCCESSFUL_OUTCOME".equals(string(event, "quantityBasis")), "Unsupported chance or quantity basis");
            string(event, "sourceName");
            string(event, "probabilityContext");
            string(event, "otherRewardsNotice");
            evidence(array(event, "evidence"));
            outcomes(event); // Validate the full table, including outcomes not in lookup coverage.
            routes(event);
        }
        List<AcquisitionMethod> methods = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (JsonElement value : coverage)
        {
            JsonObject row = object(value);
            fields(row, "eventId,includedOutcomeIds", "");
            String id = string(row, "eventId");
            require(covered.add(id) && tables.containsKey(id), "Unknown or duplicate covered event");
            JsonObject event = tables.get(id);
            Map<String, JsonObject> outcomes = outcomes(event);
            Set<String> selected = new HashSet<>();
            Set<String> itemNames = new HashSet<>();
            for (JsonElement selection : array(row, "includedOutcomeIds"))
            {
                require(selection.isJsonPrimitive() && selection.getAsJsonPrimitive().isString(), "Outcome ID must be text");
                String outcomeId = selection.getAsString();
                require(selected.add(outcomeId) && outcomes.containsKey(outcomeId), "Unknown or duplicate covered outcome");
                JsonObject outcome = outcomes.get(outcomeId);
                String item = string(outcome, "itemName");
                // Ambiguous multi-row or gated outputs need a separately reviewed presentation.
                require(!"Dragonstone bolt tips".equals(item) && !outcome.has("eligibility") && itemNames.add(item),
                        "Unsupported lookup coverage");
                int minimum = integer(outcome, "quantityMin", 1, 1000000);
                int maximum = integer(outcome, "quantityMax", minimum, 1000000);
                String quantity = minimum == maximum ? String.valueOf(minimum) : minimum + "–" + maximum;
                List<Section> sections = new ArrayList<>();
                sections.add(new Section("Loot", Arrays.asList("Quantity: " + quantity
                        + (bool(outcome, "noted") ? " (noted)" : "") + " on this outcome.",
                        "Wiki-listed rate: " + string(outcome, "listedChance") + ".", string(event, "probabilityContext"))));
                sections.addAll(routes(event));
                sections.add(new Section("One loot event", Arrays.asList(
                        "Each route gives one loot event. Catching into a jar adds no extra chance.",
                        string(event, "otherRewardsNotice"))));
                List<Section> summary = Arrays.asList(sections.get(0),
                        new Section("Catch and loot", Arrays.asList(
                                "Puro-Puro or rare encounters around Gielinor.",
                                "83 Hunter with a butterfly or magic butterfly net; 93 bare-handed.",
                                "Take an empty impling jar in Puro-Puro.")),
                        new Section("Already have a filled jar?", Collections.singletonList(
                                "Open the Dragon impling jar directly; no Hunter level is required.")));
                methods.add(new AcquisitionMethod(id + ":" + outcomeId, item, string(event, "sourceName"),
                        "Loot & containers", JAR, "Gielinor; Puro-Puro", sections, summary, true));
            }
        }
        return methods;
    }

    private static Map<String, JsonObject> outcomes(JsonObject event)
    {
        JsonArray values = array(event, "outcomes");
        require(values.size() == 19, "Incomplete Dragon impling ordinary table");
        Map<String, JsonObject> result = new LinkedHashMap<>();
        int gated = 0;
        for (JsonElement value : values)
        {
            JsonObject outcome = object(value);
            fields(outcome, "id,itemName,quantityMin,quantityMax,noted,listedChance", "eligibility,whenIneligible");
            require(result.put(string(outcome, "id"), outcome) == null, "Duplicate outcome ID");
            String item = string(outcome, "itemName");
            int minimum = integer(outcome, "quantityMin", 1, 1000000);
            integer(outcome, "quantityMax", minimum, 1000000);
            bool(outcome, "noted");
            require("1/19".equals(string(outcome, "listedChance")), "Unsupported listed table chance");
            if (outcome.has("eligibility"))
            {
                gated++;
                JsonObject condition = object(outcome.get("eligibility"));
                fields(condition, "kind,quest,state,scope", "");
                require("Dragon javelin tips".equals(item) && "QUEST".equals(string(condition, "kind"))
                        && "Monkey Madness II".equals(string(condition, "quest"))
                        && "COMPLETED".equals(string(condition, "state")) && "OUTCOME".equals(string(condition, "scope"))
                        && "REROLL_WITHIN_ORDINARY_TABLE".equals(string(outcome, "whenIneligible")), "Unsupported outcome eligibility");
            }
            else require(!outcome.has("whenIneligible") && !"Dragon javelin tips".equals(item), "Missing outcome eligibility");
        }
        require(gated == 1, "Missing quest-dependent outcome");
        return result;
    }

    private static List<Section> routes(JsonObject event)
    {
        JsonArray values = array(event, "routes");
        require(values.size() == 3, "Incomplete loot routes");
        Map<String, Section> result = new LinkedHashMap<>();
        String catchRequirements = null;
        Set<String> ids = new HashSet<>();
        for (JsonElement value : values)
        {
            JsonObject route = object(value);
            String kind = string(route, "kind");
            boolean opening = "OPEN_EXISTING".equals(kind);
            fields(route, "id,kind,steps,captureOptions,evidence," + (opening ? "inputItem" : "area,jarState"), "");
            require(ids.add(string(route, "id")), "Duplicate route ID");
            evidence(array(route, "evidence"));
            List<String> lines = new ArrayList<>();
            for (JsonElement step : array(route, "steps"))
            {
                require(step.isJsonPrimitive() && step.getAsJsonPrimitive().isString()
                        && !step.getAsString().trim().isEmpty(), "Invalid route step");
                lines.add(step.getAsString() + ".");
            }
            require(lines.size() == 2, "Unexpected route action count");
            // Render concise actions from validated route kinds, never by parsing prose.
            lines.clear();
            String title;
            if (opening)
            {
                require(JAR.equals(string(route, "inputItem")) && array(route, "captureOptions").size() == 0,
                        "Opening must not inherit capture requirements");
                title = "Already have a jar?";
                lines.add("Open an already obtained Dragon impling jar.");
                lines.add("No Hunter level is needed.");
            }
            else
            {
                boolean intoJar = "CATCH_INTO_CONTAINER".equals(kind);
                require(intoJar || "CATCH_AND_LOOT".equals(kind), "Unknown route");
                require((intoJar ? "EMPTY_JAR_PRESENT" : "EMPTY_JAR_ABSENT").equals(string(route, "jarState")), "Wrong jar state");
                require((intoJar ? "Gielinor or Puro-Puro" : "Outside Puro-Puro").equals(string(route, "area")), "Wrong route area");
                title = intoJar ? "Catch into a jar" : "Catch for immediate loot";
                String requirements = captureOptions(array(route, "captureOptions"));
                require(catchRequirements == null || catchRequirements.equals(requirements), "Capture alternatives differ between routes");
                catchRequirements = requirements;
                lines.add(intoJar ? "Catch with an empty impling jar, then open it."
                        : "Outside Puro-Puro: catch without an empty jar for immediate loot.");
                lines.add(intoJar ? "Gielinor or Puro-Puro. Bare-handed catches also fill the jar."
                        : "Puro-Puro requires a jar.");
            }
            require(result.put(kind, new Section(title, lines)) == null, "Duplicate route kind");
        }
        require(result.keySet().containsAll(Arrays.asList("OPEN_EXISTING", "CATCH_INTO_CONTAINER", "CATCH_AND_LOOT")), "Missing route kind");
        return Arrays.asList(result.get("OPEN_EXISTING"), new Section("To catch an impling", Collections.singletonList(catchRequirements)),
                result.get("CATCH_INTO_CONTAINER"), result.get("CATCH_AND_LOOT"));
    }

    private static String captureOptions(JsonArray options)
    {
        require(options.size() == 2, "Incomplete capture alternatives");
        Set<String> kinds = new HashSet<>();
        for (JsonElement value : options)
        {
            JsonObject option = object(value);
            fields(option, "hunterLevel,equipment,evidence,kind,skill,equipmentAlternatives,scope", "");
            String kind = string(option, "kind");
            require(kinds.add(kind) && ("NET".equals(kind) || "BAREHANDED".equals(kind)), "Unsupported capture type");
            require("Hunter".equals(string(option, "skill")) && "CAPTURE".equals(string(option, "scope")), "Wrong capture requirement scope");
            require(integer(option, "hunterLevel", 1, 99) == ("NET".equals(kind) ? 83 : 93), "Unreviewed Hunter level");
            string(option, "equipment");
            evidence(array(option, "evidence"));
            List<String> equipment = new ArrayList<>();
            for (JsonElement item : array(option, "equipmentAlternatives"))
            {
                require(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString(), "Invalid equipment ID");
                equipment.add(item.getAsString());
            }
            require(equipment.equals("NET".equals(kind) ? Arrays.asList("BUTTERFLY_NET", "MAGIC_BUTTERFLY_NET")
                    : Collections.emptyList()), "Unreviewed capture equipment");
        }
        return "83 Hunter with a butterfly net or magic butterfly net; or 93 Hunter bare-handed.";
    }
}
