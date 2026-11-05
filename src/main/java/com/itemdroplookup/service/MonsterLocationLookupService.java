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
    private Map<String, WildernessBossLair> componentLairs = Collections.emptyMap();
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
        if (verified != null) return new LocationMatch(verified, true, 0);
        List<MonsterLocation> places = lookupByMonsterName(source.getMonsterName());
        int level = places.size() == 1 ? places.get(0).getEncounterWildernessLevel() : 0;
        WildernessBossLair component = componentLairs.get(source.getMonsterName());
        if (component != null && component.matchesDrop(source.getMonsterName(), source.getItemName(),
                source.getQuantity(), source.getDropRate(), source.getNotes())) level = component.level;
        return new LocationMatch(places, false, level);
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
            componentLairs = ReviewedRows.read(gson, "/voidwaker_lairs.json", "encounters", "monsterName", this::readComponentLair);
            Set<String> accessItems = accessItemNames();
            for (Binding binding : dataset.dropBindings)
            {
                WildernessBossLair component = componentLairs.get(binding.monsterName);
                if (component != null && component.matchesDrop(binding.monsterName, binding.itemName,
                        binding.quantity, binding.dropRate, binding.notes)) componentLairs.remove(binding.monsterName);
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
            componentLairs = Collections.emptyMap();
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

    public static final class LocationMatch
    {
        private final List<MonsterLocation> locations;
        private final boolean verified;

        private final int encounterLevel;

        private LocationMatch(List<MonsterLocation> locations, boolean verified, int encounterLevel)
        {
            this.locations = locations;
            this.verified = verified;
            this.encounterLevel = encounterLevel;
        }

        public List<MonsterLocation> getLocations() { return locations; }
        public boolean isVerified() { return verified; }

        /** Normal encounter evidence is separate from verification of an individual drop. */
        public boolean isReviewedEncounter() { return encounterLevel > 0; }
        public int getEncounterWildernessLevel() { return encounterLevel; }
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
