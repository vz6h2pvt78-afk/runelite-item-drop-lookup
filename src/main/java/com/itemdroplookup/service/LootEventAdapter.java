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
@lombok.extern.slf4j.Slf4j
final class LootEventAdapter
{
    private static final String EVENT = "dragon-impling-ordinary-loot";
    private enum Tier
    {
        DRAGON("Dragon", 83, 93, 19), BABY("Baby", 17, 27, 19), YOUNG("Young", 22, 32, 19),
        GOURMET("Gourmet", 28, 38, 18), EARTH("Earth", 36, 46, 19),
        ESSENCE("Essence", 42, 52, 19), ECLECTIC("Eclectic", 50, 60, 19),
        NATURE("Nature", 58, 68, 19), MAGPIE("Magpie", 65, 75, 20), NINJA("Ninja", 74, 84, 19);

        final String creature, jar, eventId;
        final int net, bare, ordinaryRows;
        Tier(String name, int net, int bare, int ordinaryRows)
        {
            creature = name + " impling";
            jar = creature + " jar";
            eventId = name.toLowerCase(java.util.Locale.ROOT) + "-impling-ordinary-loot";
            this.net = net;
            this.bare = bare;
            this.ordinaryRows = ordinaryRows;
        }

        static Tier from(JsonObject event)
        {
            for (Tier tier : values())
                if (tier.eventId.equals(string(event, "id")) && tier.jar.equals(string(event, "containerName"))
                        && (tier.creature + " loot").equals(string(event, "sourceName"))) return tier;
            throw new IllegalArgumentException("Unreviewed impling");
        }
    }

    static List<AcquisitionMethod> readFamily(com.google.gson.Gson gson)
    {
        List<AcquisitionMethod> result = new ArrayList<>();
        try (java.io.InputStream in = LootEventAdapter.class.getResourceAsStream("/impling_loot.json"))
        {
            if (in == null) return result;
            JsonObject root = gson.fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            fields(root, "schemaVersion,tables", "");
            integer(root, "schemaVersion", 1, 1);
            Set<String> seen = new HashSet<>();
            for (JsonElement row : array(root, "tables"))
            {
                JsonObject table = object(row);
                fields(table, "event,coverage", "");
                JsonObject event = object(table.get("event"));
                String id = string(event, "id");
                require(!EVENT.equals(id) && seen.add(id), "Duplicate impling event");
                JsonArray events = new JsonArray(), coverage = new JsonArray();
                events.add(event);
                coverage.add(table.get("coverage"));
                result.addAll(read(events, coverage));
            }
        }
        catch (Exception ex)
        {
            log.debug("Additional impling tables unavailable", ex);
            result.clear();
        }
        return result;
    }

    static List<AcquisitionMethod> read(JsonArray events, JsonArray coverage)
    {
        Map<String, JsonObject> tables = new LinkedHashMap<>();
        for (JsonElement value : events)
        {
            JsonObject event = object(value);
            fields(event, "id,sourceName,containerName,probabilityBasis,probabilityContext,quantityBasis,"
                    + "otherRewardsNotice,outcomes,routes,evidence", "");
            String id = string(event, "id");
            Tier.from(event);
            require(tables.put(id, event) == null, "Duplicate loot event");
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
            Tier tier = Tier.from(event);
            List<Section> routeSections = routes(event);
            Map<String, List<JsonObject>> byItem = new LinkedHashMap<>();
            for (JsonElement selection : array(row, "includedOutcomeIds"))
            {
                require(selection.isJsonPrimitive() && selection.getAsJsonPrimitive().isString(), "Outcome ID must be text");
                String outcomeId = selection.getAsString();
                require(selected.add(outcomeId) && outcomes.containsKey(outcomeId), "Unknown or duplicate covered outcome");
                JsonObject outcome = outcomes.get(outcomeId);
                String item = string(outcome, "itemName");
                require(!outcome.has("eligibility") && !"Nothing".equals(item)
                        && (tier != Tier.DRAGON || !"Dragonstone bolt tips".equals(item)), "Unsupported lookup coverage");
                byItem.computeIfAbsent(item, unused -> new ArrayList<>()).add(outcome);
            }
            for (Map.Entry<String, List<JsonObject>> entry : byItem.entrySet())
            {
                String item = entry.getKey();
                List<JsonObject> rolls = entry.getValue();
                List<String> lootLines = new ArrayList<>();
                for (JsonObject outcome : rolls)
                {
                    int minimum = integer(outcome, "quantityMin", 1, 1000000);
                    int maximum = integer(outcome, "quantityMax", minimum, 1000000);
                    String quantity = minimum == maximum ? String.valueOf(minimum) : minimum + "–" + maximum;
                    lootLines.add("Quantity: " + quantity + (bool(outcome, "noted") ? " (noted)" : "") + " on this outcome.");
                    lootLines.add("Wiki-listed rate: " + string(outcome, "listedChance") + ".");
                }
                lootLines.add(string(event, "probabilityContext"));
                List<Section> sections = new ArrayList<>();
                sections.add(new Section("Loot", lootLines));
                sections.addAll(routeSections);
                sections.add(new Section("One loot event", Arrays.asList(
                        "Each route gives one loot event. Catching into a jar adds no extra chance.",
                        string(event, "otherRewardsNotice"))));
                List<Section> summary = Arrays.asList(sections.get(0),
                        new Section("Catch and loot", Arrays.asList(
                                "Puro-Puro or rare encounters around Gielinor.",
                                tier.net + " Hunter with a butterfly or magic butterfly net; " + tier.bare + " bare-handed.",
                                "Take an empty impling jar in Puro-Puro.")),
                        new Section("Already have a filled jar?", Collections.singletonList(
                                "Open the " + tier.jar + " directly; no Hunter level is required.")));
                methods.add(new AcquisitionMethod(id + ":" + string(rolls.get(0), "id"), item, string(event, "sourceName"),
                        "Loot & containers", tier == Tier.DRAGON ? tier.jar : tier.creature, "Gielinor; Puro-Puro", sections, summary, true));
            }
        }
        return methods;
    }

    private static Map<String, JsonObject> outcomes(JsonObject event)
    {
        JsonArray values = array(event, "outcomes");
        Tier tier = Tier.from(event);
        require(values.size() == tier.ordinaryRows, "Incomplete impling ordinary table");
        Map<String, JsonObject> result = new LinkedHashMap<>();
        int gated = 0;
        for (JsonElement value : values)
        {
            JsonObject outcome = object(value);
            fields(outcome, "id,itemName,quantityMin,quantityMax,noted,listedChance", "eligibility,whenIneligible");
            require(result.put(string(outcome, "id"), outcome) == null, "Duplicate outcome ID");
            String item = string(outcome, "itemName");
            int minimum = integer(outcome, "quantityMin", "Nothing".equals(item) ? 0 : 1, 1000000);
            integer(outcome, "quantityMax", minimum, 1000000);
            bool(outcome, "noted");
            String chance = string(outcome, "listedChance");
            require(tier == Tier.DRAGON ? chance.equals("1/19") : chance.matches("[1-9][0-9]{0,3}/[1-9][0-9]{0,3}"), "Invalid listed chance");
            String[] fraction = chance.split("/");
            require(Integer.parseInt(fraction[0]) <= Integer.parseInt(fraction[1]), "Invalid probability");
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
        require(gated == (tier == Tier.DRAGON ? 1 : 0), "Missing quest-dependent outcome");
        return result;
    }

    private static List<Section> routes(JsonObject event)
    {
        Tier tier = Tier.from(event);
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
                require(tier.jar.equals(string(route, "inputItem")) && array(route, "captureOptions").size() == 0,
                        "Opening must not inherit capture requirements");
                title = "Already have a jar?";
                lines.add("Open an already obtained " + tier.jar + ".");
                lines.add("No Hunter level is needed.");
            }
            else
            {
                boolean intoJar = "CATCH_INTO_CONTAINER".equals(kind);
                require(intoJar || "CATCH_AND_LOOT".equals(kind), "Unknown route");
                require((intoJar ? "EMPTY_JAR_PRESENT" : "EMPTY_JAR_ABSENT").equals(string(route, "jarState")), "Wrong jar state");
                require((intoJar ? "Gielinor or Puro-Puro" : "Outside Puro-Puro").equals(string(route, "area")), "Wrong route area");
                title = intoJar ? "Catch into a jar" : "Catch for immediate loot";
                String requirements = captureOptions(array(route, "captureOptions"), tier);
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

    private static String captureOptions(JsonArray options, Tier tier)
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
            require(integer(option, "hunterLevel", 1, 99) == ("NET".equals(kind) ? tier.net : tier.bare), "Unreviewed Hunter level");
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
        return tier.net + " Hunter with a butterfly net or magic butterfly net; or " + tier.bare + " Hunter bare-handed.";
    }
}
