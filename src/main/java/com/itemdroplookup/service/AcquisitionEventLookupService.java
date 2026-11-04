package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AcquisitionMethod;
import com.itemdroplookup.model.AcquisitionMethod.Section;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Small offline event adapter. Never evaluates the player's account or performs game actions. */
@Slf4j
@Singleton
public class AcquisitionEventLookupService
{
    private static final List<String> ENT_LOGS = Arrays.asList(
            "Logs", "Oak logs", "Willow logs", "Maple logs", "Yew logs", "Magic logs");
    private final Gson gson;
    private final MonsterLocationLookupService locations;
    private final DropLookupService drops;
    private final Supplier<Reader> resource;
    private List<AcquisitionMethod> methods;

    @Inject
    public AcquisitionEventLookupService(Gson gson, MonsterLocationLookupService locations, DropLookupService drops)
    {
        this(gson, locations, drops, () -> {
            InputStream stream = AcquisitionEventLookupService.class.getResourceAsStream("/acquisition_events.json");
            return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
        });
    }

    AcquisitionEventLookupService(Gson gson, MonsterLocationLookupService locations,
            DropLookupService drops, Supplier<Reader> resource)
    {
        this.gson = gson;
        this.locations = locations;
        this.drops = drops;
        this.resource = resource;
    }

    /** Independent of the optional manifest, so loading failure cannot invent ordinary kill loot. */
    public boolean isHarvestSource(DropSource source)
    {
        return "Ent".equals(source.getMonsterName()) && ENT_LOGS.contains(source.getItemName());
    }

    public synchronized List<AcquisitionMethod> getMethods()
    {
        if (methods != null) return methods;
        Map<String, AcquisitionMethod> harvests = new LinkedHashMap<>();
        List<AcquisitionMethod> loot = Collections.emptyList();
        try (Reader input = resource.get())
        {
            require(input != null, "Event resource missing");
            JsonObject root = gson.fromJson(input, JsonObject.class);
            fields(root, "schemaVersion,checkedAt,attribution,harvests,lootEvents,lootCoverage", "");
            require(integer(root, "schemaVersion", 1, 1) == 1, "Unsupported schema");
            Instant.parse(string(root, "checkedAt"));
            string(root, "attribution");
            // A broken container payload must not invalidate separately reviewed harvesting.
            try
            {
                loot = LootEventAdapter.read(array(root, "lootEvents"), array(root, "lootCoverage"));
            }
            catch (Exception ex)
            {
                log.debug("Reviewed loot event details unavailable", ex);
            }
            try
            {
                Set<String> ids = new HashSet<>();
                for (JsonElement element : array(root, "harvests"))
                {
                    AcquisitionMethod method = harvest(object(element));
                    require(ids.add(method.getId()) && !harvests.containsKey(method.getItemName()),
                            "Duplicate harvest association");
                    harvests.put(method.getItemName(), method);
                }
            }
            catch (Exception ex)
            {
                harvests.clear();
                log.debug("Reviewed harvest details unavailable", ex);
            }
        }
        catch (Exception ex)
        {
            harvests.clear();
            loot = Collections.emptyList();
            log.debug("Reviewed acquisition details unavailable", ex);
        }
        List<AcquisitionMethod> result = new ArrayList<>();
        for (String item : ENT_LOGS)
        {
            // Do not invent an item/source when the legacy dataset has no such pair.
            if (drops.searchByItemName(item).stream().noneMatch(d -> item.equals(d.getItemName()) && isHarvestSource(d))) continue;
            result.add(harvests.containsKey(item) ? harvests.get(item) : unavailable(item));
        }
        result.addAll(loot);
        methods = Collections.unmodifiableList(result);
        return methods;
    }

    private AcquisitionMethod harvest(JsonObject row)
    {
        fields(row, "id,itemName,sourceName,legacyDrop,prerequisiteAction,harvestAction,outputCondition,"
                + "chanceKind,chanceContext,locations,evidence,toolRequirement", "");
        String id = string(row, "id");
        String item = string(row, "itemName");
        require(ENT_LOGS.contains(item), "Unreviewed harvest output");
        require("DEFEAT_MONSTER".equals(string(row, "prerequisiteAction")), "Unsupported prerequisite");
        require("VARIABLE".equals(string(row, "chanceKind")), "Unsupported harvest probability basis");
        evidence(array(row, "evidence"));
        JsonObject old = object(row.get("legacyDrop"));
        fields(old, "itemName,monsterName,quantity,dropRate,notes", "");
        require(item.equals(string(old, "itemName")) && "Ent".equals(string(old, "monsterName")),
                "Wrong legacy item or monster");
        List<DropSource> matches = new ArrayList<>();
        for (DropSource source : drops.searchByItemName(item))
            if (sameLegacy(source, old)) matches.add(source);
        require(matches.size() == 1, "Exact legacy signature missing or ambiguous");
        // Harvest evidence supplies the association. Legacy drop bindings keep their
        // original meaning and cannot prove where a harvested outcome is obtained.
        List<MonsterLocation> entLocations = locations.lookupByMonsterName("Ent");
        JsonObject tool = object(row.get("toolRequirement"));
        fields(tool, "kind,tool,qualification,scope,text", "");
        require("TOOL_CATEGORY".equals(string(tool, "kind")) && "AXE".equals(string(tool, "tool"))
                && "USABLE_AT_CURRENT_SKILL".equals(string(tool, "qualification"))
                && "HARVEST".equals(string(tool, "scope")), "Unsupported tool scope");
        JsonObject condition = object(row.get("outputCondition"));
        fields(condition, "kind,scope,text", "skill,minBaseLevel,maxBaseLevelExclusive");
        require("OUTPUT".equals(string(condition, "scope")), "Wrong output condition scope");
        String kind = string(condition, "kind");
        if ("SKILL".equals(kind))
        {
            require("Woodcutting".equals(string(condition, "skill")), "Wrong harvest skill");
            require(condition.has("minBaseLevel") || condition.has("maxBaseLevelExclusive"), "No skill bound");
            int minimum = condition.has("minBaseLevel") ? integer(condition, "minBaseLevel", 1, 99) : 1;
            int maximum = condition.has("maxBaseLevelExclusive") ? integer(condition, "maxBaseLevelExclusive", 2, 100) : 100;
            require(minimum < maximum, "Empty skill interval");
        }
        else
        {
            require("SCOPED_STATEMENT".equals(kind) && !condition.has("skill")
                    && !condition.has("minBaseLevel") && !condition.has("maxBaseLevelExclusive"), "Unsupported condition");
        }
        List<Section> sections = new ArrayList<>();
        string(row, "harvestAction");
        sections.add(new Section("Actions", Collections.singletonList("Kill an Ent, then chop its trunk.")));
        sections.add(new Section("Required for this log", Arrays.asList(string(tool, "text"), string(condition, "text"))));
        List<String> locationLines = new ArrayList<>();
        List<String> locationNames = new ArrayList<>();
        Map<String, Section> locationSummaries = new LinkedHashMap<>();
        Map<String, MonsterLocation> summaryLocations = new LinkedHashMap<>();
        Set<String> locationIds = new HashSet<>();
        for (JsonElement element : array(row, "locations"))
        {
            JsonObject variant = object(element);
            fields(variant, "locationId,locationName,combatLevel,quantity,noted,quantityBasis,evidence", "");
            String locationId = string(variant, "locationId");
            require(locationIds.add(locationId), "Duplicate harvest location");
            MonsterLocation location = entLocations.stream().filter(l -> locationId.equals(l.getId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing harvest location"));
            int level = integer(variant, "combatLevel", 1, 10000);
            require("Ent".equals(location.getMonsterName()) && string(variant, "locationName").equals(location.getLocation())
                    && location.isAvailable() && location.getLevels().equals(Collections.singletonList(level)),
                    "Changed harvest encounter identity, level or availability");
            int quantity = integer(variant, "quantity", 1, 1000000);
            require(bool(variant, "noted") && "SUCCESSFUL_HARVEST".equals(string(variant, "quantityBasis")),
                    "Wrong harvest quantity basis");
            evidence(array(variant, "evidence"));
            locationNames.add(location.getLocation());
            locationLines.add(location.getLocation() + ": defeat level " + level + " Ent. If this log type is rolled: " + quantity
                    + " noted " + (quantity == 1 ? "log" : "logs") + ".");
            List<String> summaryLines = new ArrayList<>(Arrays.asList(
                    "Defeat level " + level + " Ent.",
                    "If this log type is rolled: " + quantity + " noted " + (quantity == 1 ? "log." : "logs.")));
            if (location.getRequirements() != null && !location.getRequirements().isEmpty()) summaryLines.add(location.getRequirements());
            if (location.getNotes() != null && !location.getNotes().isEmpty()) summaryLines.add(location.getNotes());
            locationSummaries.put(locationId, new Section(location.getLocation(), summaryLines,
                    location.getWilderness() == MonsterLocation.Wilderness.YES));
            summaryLocations.put(locationId, location);
        }
        require(!locationLines.isEmpty(), "No reviewed harvest locations");
        sections.add(new Section("Where and yield", locationLines));
        sections.add(new Section("Chance varies", Arrays.asList(string(row, "chanceContext"),
                "The selected log is not guaranteed.")));
        // Only these two reviewed variants share the same encounter and yield.
        // Keep the full individual associations in sections/search and the manifest.
        Section east = locationSummaries.get("96e8b6f8e85241dc9c51");
        Section north = locationSummaries.get("a68fdb7c45298260aeff");
        if (east != null && north != null && east.isWilderness() && north.isWilderness()
                && east.getLines().equals(north.getLines())
                && sameLocationContext(summaryLocations.get("96e8b6f8e85241dc9c51"), summaryLocations.get("a68fdb7c45298260aeff")))
        {
            locationSummaries.put("96e8b6f8e85241dc9c51", new Section("East and north of Chaos Temple", east.getLines(), true));
            locationSummaries.remove("a68fdb7c45298260aeff");
        }
        List<Section> summary = new ArrayList<>(sections.subList(0, 2));
        summary.addAll(locationSummaries.values());
        summary.add(sections.get(sections.size() - 1));
        return new AcquisitionMethod(id, item, string(row, "sourceName"), "Gathering", "Ent",
                String.join("; ", locationNames), sections, summary, true);
    }

    private static boolean sameLocationContext(MonsterLocation first, MonsterLocation second)
    {
        return java.util.Objects.equals(first.getMembers(), second.getMembers())
                && java.util.Objects.equals(first.getRequirements(), second.getRequirements())
                && java.util.Objects.equals(first.getNotes(), second.getNotes())
                && java.util.Objects.equals(first.getSourceUrl(), second.getSourceUrl());
    }

    private AcquisitionMethod unavailable(String item)
    {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Kill, then chop", Collections.singletonList("Logs come from chopping the defeated Ent's trunk.")));
        boolean axeDependent = Arrays.asList("Oak logs", "Willow logs", "Maple logs").contains(item);
        sections.add(new Section("Details unavailable", Collections.singletonList(axeDependent
                ? "Availability depends on base Woodcutting and the axe used. An exact level requirement for this log is not verified; check the Wiki calculator."
                : "Reviewed requirements, locations and yield are unavailable for this entry. Check the method Wiki.")));
        return new AcquisitionMethod("ent-unavailable-" + item, item, "Ent trunk", "Gathering", "Ent", "", sections, false);
    }

    private static boolean sameLegacy(DropSource source, JsonObject old)
    {
        return source.getItemName().equals(string(old, "itemName"))
                && source.getMonsterName().equals(string(old, "monsterName"))
                && source.getQuantity().equals(string(old, "quantity"))
                && source.getDropRate().equals(string(old, "dropRate"))
                && source.getNotes().equals(text(old, "notes"));
    }

    static void evidence(JsonArray sources)
    {
        require(sources.size() > 0, "Missing evidence");
        for (JsonElement element : sources)
        {
            JsonObject source = object(element);
            fields(source, "page,revisionId,sourceUrl", "");
            String page = string(source, "page");
            int revision = integer(source, "revisionId", 1, Integer.MAX_VALUE);
            URI url = URI.create(string(source, "sourceUrl"));
            require("https".equals(url.getScheme()) && "oldschool.runescape.wiki".equals(url.getHost())
                    && ("/w/" + page.replace(' ', '_')).equals(url.getPath())
                    && ("oldid=" + revision).equals(url.getRawQuery()) && url.getFragment() == null
                    && url.getUserInfo() == null && url.getPort() == -1, "Evidence URL mismatch");
        }
    }

    static void fields(JsonObject row, String required, String optional)
    {
        require(row != null, "Missing object");
        Set<String> allowed = new HashSet<>(Arrays.asList(required.split(",")));
        for (String key : allowed) require(row.has(key) && !row.get(key).isJsonNull(), "Missing " + key);
        if (!optional.isEmpty()) allowed.addAll(Arrays.asList(optional.split(",")));
        for (Map.Entry<String, JsonElement> entry : row.entrySet())
            require(allowed.contains(entry.getKey()), "Unsupported field " + entry.getKey());
    }

    static JsonObject object(JsonElement value)
    {
        require(value != null && value.isJsonObject(), "Expected object");
        return value.getAsJsonObject();
    }

    static JsonArray array(JsonObject row, String key)
    {
        require(row.has(key) && row.get(key).isJsonArray(), "Expected array " + key);
        return row.getAsJsonArray(key);
    }

    static String text(JsonObject row, String key)
    {
        require(row.has(key) && row.get(key).isJsonPrimitive() && row.getAsJsonPrimitive(key).isString(), "Expected text " + key);
        return row.get(key).getAsString();
    }

    static String string(JsonObject row, String key)
    {
        String value = text(row, key);
        require(!value.trim().isEmpty(), "Blank " + key);
        return value;
    }

    static int integer(JsonObject row, String key, int minimum, int maximum)
    {
        require(row.has(key) && row.get(key).isJsonPrimitive() && row.getAsJsonPrimitive(key).isNumber(), "Expected integer " + key);
        int value = row.get(key).getAsBigDecimal().intValueExact();
        require(value >= minimum && value <= maximum, "Out of range " + key);
        return value;
    }

    static boolean bool(JsonObject row, String key)
    {
        require(row.has(key) && row.get(key).isJsonPrimitive() && row.getAsJsonPrimitive(key).isBoolean(), "Expected boolean " + key);
        return row.get(key).getAsBoolean();
    }

    static void require(boolean condition, String reason)
    {
        if (!condition) throw new IllegalArgumentException(reason);
    }
}
