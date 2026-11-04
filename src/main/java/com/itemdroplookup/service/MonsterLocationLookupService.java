package com.itemdroplookup.service;

import com.google.gson.Gson;
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
    private final Gson gson;
    private static final Comparator<MonsterLocation> LOCATION_ORDER = Comparator
            .comparing(MonsterLocation::isAvailable).reversed()
            .thenComparing(MonsterLocation::getLocation, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(location -> String.valueOf(location.getLevelsText()));
    private final Map<String, List<MonsterLocation>> byMonster = new HashMap<>();
    private final Map<List<String>, List<MonsterLocation>> byDrop = new HashMap<>();
    private final Map<List<String>, Set<String>> versions = new HashMap<>();
    private final Set<List<String>> commonDrops = new LinkedHashSet<>();
    private boolean loaded;

    @Inject
    public MonsterLocationLookupService(Gson gson)
    {
        this.gson = gson;
    }

    public List<MonsterLocation> lookupByMonsterName(String monsterName)
    {
        loadOnce();
        return byMonster.getOrDefault(normalize(monsterName), Collections.emptyList());
    }

    public LocationMatch lookup(DropSource source)
    {
        loadOnce();
        List<MonsterLocation> verified = byDrop.get(key(source.getMonsterName(), source.getItemName(),
                source.getQuantity(), source.getDropRate(), source.getNotes()));
        return verified == null
                ? new LocationMatch(lookupByMonsterName(source.getMonsterName()), false)
                : new LocationMatch(verified, true);
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
        return match.isVerified() && match.getLocations().stream().anyMatch(location ->
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
            for (Binding binding : dataset.dropBindings)
            {
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
            byMonster.replaceAll((name, locations) -> {
                locations.sort(LOCATION_ORDER);
                return Collections.unmodifiableList(locations);
            });
        }
        catch (Exception ex)
        {
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

        private LocationMatch(List<MonsterLocation> locations, boolean verified)
        {
            this.locations = locations;
            this.verified = verified;
        }

        public List<MonsterLocation> getLocations() { return locations; }
        public boolean isVerified() { return verified; }
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
