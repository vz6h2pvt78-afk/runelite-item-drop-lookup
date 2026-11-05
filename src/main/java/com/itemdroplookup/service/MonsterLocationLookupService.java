package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Offline, exact-page location lookup. Never falls back to a parent monster name. */
@Slf4j
@Singleton
public class MonsterLocationLookupService
{
    static final String ITEM_ACCESS_RESOURCE = "/item_access.json";
    private final Gson gson;
    private final DropLookupService drops;
    // Only reviewed rewards enter this small index. Raw quantity, rate and notes remain part of the key.
    private final Map<String, Map<List<String>, EncounterWarning>> rewardWarnings = new HashMap<>();
    // Retain raw binding multiplicity only for the few reviewed item-access targets.
    private final Map<List<String>, Binding> accessBindings = new HashMap<>();
    private static final Comparator<MonsterLocation> LOCATION_ORDER = Comparator
            .comparing(MonsterLocation::isAvailable).reversed()
            .thenComparing(MonsterLocation::getLocation, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(location -> String.valueOf(location.getLevelsText()));
    private final Map<String, List<MonsterLocation>> byMonster = new HashMap<>();
    private final Map<List<String>, List<MonsterLocation>> byDrop = new HashMap<>();
    private final Map<List<String>, Set<String>> versions = new HashMap<>();
    private final Set<List<String>> commonDrops = new LinkedHashSet<>();
    private Map<String, MonsterLocation> encounterLairs = Collections.emptyMap();
    private boolean loaded;

    public MonsterLocationLookupService(Gson gson)
    {
        this(gson, new DropLookupService(gson));
    }

    @Inject
    public MonsterLocationLookupService(Gson gson, DropLookupService drops)
    {
        this.gson = gson;
        this.drops = drops;
    }

    public List<MonsterLocation> lookupByMonsterName(String monsterName)
    {
        loadOnce();
        List<MonsterLocation> existing = byMonster.get(normalize(monsterName));
        if (existing != null) return existing;
        MonsterLocation lair = encounterLairs.get(monsterName);
        return lair == null ? Collections.emptyList() : Collections.singletonList(lair);
    }

    boolean hasUniqueVerifiedBinding(DropSource drop, String locationId, String version)
    {
        LocationMatch match = lookup(drop);
        Binding binding = accessBindings.get(Arrays.asList(drop.getMonsterName(),drop.getItemName(),drop.getQuantity(),drop.getDropRate(),drop.getNotes()));
        return binding != null && Collections.singletonList(locationId).equals(binding.locationIds)
                && Collections.singletonList(version).equals(binding.dropVersions)
                && match.isVerified() && match.getLocations().size() == 1 && locationId.equals(match.getLocations().get(0).getId());
    }

    private Set<String> accessItemNames()
    {
        Set<String> names = new LinkedHashSet<>();
        try (InputStream input = getClass().getResourceAsStream(ITEM_ACCESS_RESOURCE))
        {
            if (input == null) return names;
            JsonObject root = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            for (JsonElement entry : AcquisitionEventLookupService.array(root,"facts"))
                names.add(AcquisitionEventLookupService.string(AcquisitionEventLookupService.object(entry),"itemName"));
        }
        catch (Exception ex)
        {
            log.debug("Item access binding targets unavailable",ex);
            names.clear();
        }
        return names;
    }

    public LocationMatch lookup(DropSource source)
    {
        loadOnce();
        List<MonsterLocation> verified = byDrop.get(key(source.getMonsterName(), source.getItemName(),
                source.getQuantity(), source.getDropRate(), source.getNotes()));
        if (verified != null) return new LocationMatch(verified, true, null);
        List<MonsterLocation> places = lookupByMonsterName(source.getMonsterName());
        Map<List<String>, EncounterWarning> rewards = rewardWarnings.get(source.getMonsterName());
        EncounterWarning warning = rewards == null ? null : rewards.get(Arrays.asList(
                source.getItemName(), source.getQuantity(), source.getDropRate(), source.getNotes()));
        if (places.size() == 1 && places.get(0).getEncounterWildernessLevel() > 0)
        {
            MonsterLocation place = places.get(0);
            int level = place.getEncounterWildernessLevel();
            warning = new EncounterWarning(level, level, place.getLocation());
        }
        return new LocationMatch(places, false, warning);
    }

    public Set<String> tableVersions(DropSource source)
    {
        loadOnce();
        return Collections.unmodifiableSet(versions.getOrDefault(key(source.getMonsterName(), source.getItemName(),
                source.getQuantity(), source.getDropRate(), source.getNotes()), Collections.emptySet()));
    }

    /** Dose outputs may share a source card only where their exact unversioned tables are known. */
    public boolean isCommonDrop(DropSource source)
    {
        loadOnce();
        return commonDrops.contains(key(source.getMonsterName(), source.getItemName(),
                source.getQuantity(), source.getDropRate(), source.getNotes()));
    }

    public boolean hasQualifyingLocation(DropSource source, MonsterLocationFilter.WildernessFilter filter)
    {
        // Clearing the filter restores every source, including missing location data.
        if (filter == MonsterLocationFilter.WildernessFilter.ALL)
        {
            return true;
        }
        LocationMatch match = lookup(source);
        return (match.isVerified() || match.isReviewedEncounter()) && match.getLocations().stream().anyMatch(location ->
                location.isAvailable() && MonsterLocationFilter.matches(location, filter,
                        MonsterLocationFilter.MembershipFilter.ALL));
    }

    private synchronized void loadOnce()
    {
        if (loaded)
        {
            return;
        }
        // First use is from the Swing sidebar, never from the RuneLite client thread.
        try (InputStream input = getClass().getResourceAsStream("/monster_locations.json"))
        {
            if (input == null)
            {
                log.debug("Bundled monster locations are unavailable");
                return;
            }
            Dataset dataset = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Dataset.class);
            if (dataset == null || dataset.schemaVersion != 1 || dataset.locations == null || dataset.dropBindings == null)
            {
                log.debug("Unsupported monster location dataset");
                return;
            }
            Map<String, MonsterLocation> byId = new HashMap<>();
            for (MonsterLocation location : dataset.locations)
            {
                if (location == null || location.getId() == null || location.getMonsterName() == null)
                {
                    continue;
                }
                byId.put(location.getId(), location);
                byMonster.computeIfAbsent(normalize(location.getMonsterName()), k -> new ArrayList<>()).add(location);
            }
            // Signatures can represent multiple table variants. Union all verified
            // associations, never arbitrarily choose the first one.
            Map<List<String>, Set<MonsterLocation>> associations = new HashMap<>();
            Set<List<String>> invalid = new LinkedHashSet<>();
            Set<List<String>> variantTables = new LinkedHashSet<>();
            Map<String, WildernessBossLair> components = ReviewedRows.read(gson, "/voidwaker_lairs.json", "encounters",
                    "monsterName", this::readComponentLair);
            for (WildernessBossLair lair : components.values())
                addRewardWarning(lair.component(), new EncounterWarning(lair.level, lair.level, lair.location));
            Map<String, EncounterWarning> shards = ReviewedRows.read(gson, "/scorpia_shard_lairs.json", "shards",
                    "itemName", this::readScorpiaWarning);
            shards.forEach((item, warning) -> addRewardWarning(new DropSource(item, "Scorpia", "1", "1/256", ""), warning));
            Set<String> accessItems = accessItemNames();
            for (Binding binding : dataset.dropBindings)
            {
                Map<List<String>, EncounterWarning> rewards = rewardWarnings.get(binding.monsterName);
                if (rewards != null) rewards.remove(Arrays.asList(binding.itemName, binding.quantity, binding.dropRate, binding.notes));
                if (accessItems.contains(binding.itemName))
                {
                    List<String> rawKey = Arrays.asList(binding.monsterName,binding.itemName,binding.quantity,binding.dropRate,binding.notes);
                    accessBindings.put(rawKey, accessBindings.containsKey(rawKey) ? null : binding);
                }
                List<String> signature = key(binding.monsterName, binding.itemName, binding.quantity, binding.dropRate, binding.notes);
                if (binding.dropVersions != null && !binding.dropVersions.isEmpty())
                    versions.computeIfAbsent(signature, unused -> new LinkedHashSet<>()).addAll(binding.dropVersions);
                if (binding.dropVersions == null || !binding.dropVersions.isEmpty()
                        || !Collections.singletonList("common-unversioned-table").equals(binding.matchMethods))
                    variantTables.add(signature);
                if (binding.locationIds == null || binding.locationIds.isEmpty())
                {
                    invalid.add(signature);
                    continue;
                }
                Set<MonsterLocation> locations = associations.computeIfAbsent(signature, k -> new LinkedHashSet<>());
                for (String id : binding.locationIds)
                {
                    MonsterLocation location = byId.get(id);
                    if (location == null || !normalize(location.getMonsterName()).equals(normalize(binding.monsterName)))
                    {
                        invalid.add(signature);
                    }
                    else
                    {
                        locations.add(location);
                    }
                }
            }
            associations.forEach((signature, locations) -> {
                if (!invalid.contains(signature) && !locations.isEmpty())
                {
                    List<MonsterLocation> ordered = new ArrayList<>(locations);
                    ordered.sort(LOCATION_ORDER);
                    byDrop.put(signature, Collections.unmodifiableList(ordered));
                    if (!variantTables.contains(signature)) commonDrops.add(signature);
                }
            });
            encounterLairs = ReviewedRows.read(gson, "/reviewed_encounter_lairs.json", "lairs", "monsterName",
                    MonsterLocationLookupService::readEncounterLair);
            byMonster.replaceAll((name, locations) -> {
                locations.sort(LOCATION_ORDER);
                return Collections.unmodifiableList(locations);
            });
        }
        catch (Exception ex)
        {
            rewardWarnings.clear();
            byMonster.clear();
            byDrop.clear();
            commonDrops.clear();
            versions.clear();
            log.debug("Unable to load bundled monster locations", ex);
        }
        finally
        {
            loaded = true;
        }
    }

    private void addRewardWarning(DropSource source, EncounterWarning warning)
    {
        rewardWarnings.computeIfAbsent(source.getMonsterName(), unused -> new HashMap<>()).put(
                Arrays.asList(source.getItemName(), source.getQuantity(), source.getDropRate(), source.getNotes()), warning);
    }

    private EncounterWarning readScorpiaWarning(JsonObject row, String item)
    {
        AcquisitionEventLookupService.fields(row,
                "itemName,monsterName,quantity,dropRate,notes,locationId,lair,revisionId,wildernessLevelMin,wildernessLevelMax", "");
        if (!item.equals("Malediction shard 3") && !item.equals("Odium shard 3")) throw new IllegalArgumentException("Unknown shard");
        DropSource source = new DropSource(item, AcquisitionEventLookupService.string(row, "monsterName"),
                AcquisitionEventLookupService.string(row, "quantity"), AcquisitionEventLookupService.string(row, "dropRate"),
                AcquisitionEventLookupService.text(row, "notes"));
        if (!"Scorpia".equals(source.getMonsterName()) || !"1".equals(source.getQuantity()) || !"1/256".equals(source.getDropRate())
                || !"".equals(source.getNotes()) || !drops.hasUniqueRecordedDrop(source)) throw new IllegalArgumentException("Invalid shard row");
        List<MonsterLocation> caves = byMonster.get(normalize("Scorpia"));
        if (caves == null || caves.size() != 1) throw new IllegalArgumentException("Ambiguous Scorpia cave");
        MonsterLocation cave = caves.get(0);
        if (!"f3fb4ec480822cf6cbb2".equals(cave.getId()) || !"Cave in the Scorpion Pit (north-east Wilderness)".equals(cave.getLocation())
                || !"Scorpia".equals(cave.getMonsterName()) || !"Scorpia".equals(cave.getSourcePage()) || cave.getRevisionId() != 15362166
                || !cave.isAvailable() || cave.getWilderness() != MonsterLocation.Wilderness.YES
                || !cave.getId().equals(AcquisitionEventLookupService.string(row, "locationId"))
                || !cave.getLocation().equals(AcquisitionEventLookupService.string(row, "lair")))
            throw new IllegalArgumentException("Unreviewed Scorpia cave");
        AcquisitionEventLookupService.integer(row, "revisionId", 15362166, 15362166);
        int low = AcquisitionEventLookupService.integer(row, "wildernessLevelMin", 53, 53);
        int high = AcquisitionEventLookupService.integer(row, "wildernessLevelMax", 55, 55);
        return new EncounterWarning(low, high, "Scorpion Pit cave");
    }

    private WildernessBossLair readComponentLair(JsonObject row, String name)
    {
        AcquisitionEventLookupService.fields(row,
                "monsterName,itemName,quantity,dropRate,notes,locationId,lair,revisionId,wildernessLevel", "");
        WildernessBossLair lair = WildernessBossLair.forMonster(name);
        if (!lair.matchesLocation(byMonster.get(normalize(name)))
                || !lair.id.equals(AcquisitionEventLookupService.string(row, "locationId"))
                || !lair.location.equals(AcquisitionEventLookupService.string(row, "lair"))
                || !lair.matchesDrop(name, AcquisitionEventLookupService.string(row, "itemName"),
                        AcquisitionEventLookupService.string(row, "quantity"), AcquisitionEventLookupService.string(row, "dropRate"),
                        AcquisitionEventLookupService.text(row, "notes"))
                || !drops.hasUniqueRecordedDrop(lair.component()))
            throw new IllegalArgumentException("Unverified Voidwaker encounter");
        AcquisitionEventLookupService.integer(row, "revisionId", lair.revision, lair.revision);
        AcquisitionEventLookupService.integer(row, "wildernessLevel", lair.level, lair.level);
        return lair;
    }

    private static MonsterLocation readEncounterLair(JsonObject row, String name)
    {
        AcquisitionEventLookupService.fields(row, "monsterName,revisionId,lair,wildernessLevel,combatLevel", "");
        String lair = AcquisitionEventLookupService.string(row, "lair");
        int revision = AcquisitionEventLookupService.integer(row, "revisionId", 1, Integer.MAX_VALUE);
        int wilderness = AcquisitionEventLookupService.integer(row, "wildernessLevel", 1, 60);
        int combat = AcquisitionEventLookupService.integer(row, "combatLevel", 1, 1000);
        // These two reviewed page revisions establish where the encounter occurs.
        // A different place, level or revision must be reviewed before affecting safety filters.
        boolean spindel = name.equals("Spindel") && lair.equals("Web Chasm") && revision == 15358779
                && wilderness == 29 && combat == 302;
        boolean calvarion = name.equals("Calvar'ion") && lair.equals("Skeletal Tomb") && revision == 15375801
                && wilderness == 21 && combat == 264;
        if (!spindel && !calvarion) throw new IllegalArgumentException("Unreviewed encounter location");
        return MonsterLocation.reviewedLair(name, lair, wilderness, combat, revision);
    }

    private static List<String> key(String monster, String item, String quantity, String rate, String notes)
    {
        return Arrays.asList(normalize(monster), normalize(item), normalize(quantity), normalize(rate), normalize(notes));
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.replace('_', ' ').replace('\u2019', '\'')
                .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static final class EncounterWarning
    {
        private final int minimum, maximum;
        private final String shortLocation;

        private EncounterWarning(int minimum, int maximum, String shortLocation)
        {
            this.minimum = minimum;
            this.maximum = maximum;
            this.shortLocation = shortLocation;
        }
    }

    public static final class LocationMatch
    {
        private final List<MonsterLocation> locations;
        private final boolean verified;

        private final EncounterWarning warning;

        private LocationMatch(List<MonsterLocation> locations, boolean verified, EncounterWarning warning)
        {
            this.locations = locations;
            this.verified = verified;
            this.warning = warning;
        }

        public List<MonsterLocation> getLocations() { return locations; }
        public boolean isVerified() { return verified; }

        /** Normal encounter evidence is separate from verification of an individual drop. */
        public boolean isReviewedEncounter() { return warning != null; }

        public int getEncounterWildernessLevel()
        {
            return warning != null && warning.minimum == warning.maximum ? warning.minimum : 0;
        }

        public String encounterLevelText()
        {
            if (warning == null) return "";
            return warning.minimum == warning.maximum ? "level " + warning.minimum : "levels " + warning.minimum + "–" + warning.maximum;
        }

        public String encounterShortLocation() { return warning == null ? "" : warning.shortLocation; }
    }

    private static class Dataset
    {
        private int schemaVersion;
        private List<MonsterLocation> locations;
        private List<Binding> dropBindings;
    }

    private static class Binding
    {
        private String monsterName;
        private String itemName;
        private String quantity;
        private String dropRate;
        private String notes;
        private List<String> locationIds;
        private List<String> dropVersions;
        private List<String> matchMethods;
    }
}
