package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.model.MonsterLocation.Wilderness;
import com.itemdroplookup.service.MonsterLocationFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import com.itemdroplookup.service.MonsterLocationLookupService;
import java.util.*;
import java.util.stream.Collectors;

/** A presentation index over exact drop tuples. Each entry retains its own details and restrictions. */
final class BoundDropContexts
{
    private final MonsterLocationLookupService service;
    private final Map<DropSource, List<MonsterLocation>> locations = new IdentityHashMap<>();
    private final Set<DropSource> split = Collections.newSetFromMap(new IdentityHashMap<>());

    BoundDropContexts(MonsterLocationLookupService service, List<DropSource> drops)
    {
        this.service = service;
        Map<List<String>, List<DropSource>> pairs = new HashMap<>();
        for (DropSource drop : drops)
        {
            MonsterLocationLookupService.LocationMatch match = service.lookup(drop);
            if (!match.isVerified()) continue;
            List<MonsterLocation> available = match.getLocations().stream().filter(MonsterLocation::isAvailable)
                    .collect(Collectors.toList());
            if (available.isEmpty()) continue;
            locations.put(drop, available);
            pairs.computeIfAbsent(Arrays.asList(drop.getItemName(), drop.getMonsterName()), k -> new ArrayList<>()).add(drop);
        }
        for (List<DropSource> pair : pairs.values())
        {
            if (pair.size() < 2) continue;
            boolean wild = pair.stream().anyMatch(d -> all(d, Wilderness.YES));
            boolean regular = pair.stream().anyMatch(d -> all(d, Wilderness.NO));
            if (wild && regular)
                pair.stream().filter(d -> all(d, Wilderness.YES) || all(d, Wilderness.NO)).forEach(split::add);
        }
    }

    private boolean all(DropSource drop, Wilderness area)
    {
        return locations.containsKey(drop) && locations.get(drop).stream().allMatch(l -> l.getWilderness() == area);
    }

    boolean isVariant(DropSource source) { return split.contains(source); }

    boolean find(DropSource drop, String query, WildernessFilter filter)
    {
        if (query.isEmpty() || drop.getMonsterName().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (drop.getAnimatorReward() != null && (drop.getAnimatorReward().getSetupText().toLowerCase(Locale.ROOT).contains(query)
                || drop.getAnimatorReward().getEquipment().stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).contains(query)))) return true;
        return locations.getOrDefault(drop, Collections.emptyList()).stream()
                .filter(l -> MonsterLocationFilter.matches(l, filter, MonsterLocationFilter.MembershipFilter.ALL))
                .anyMatch(l -> l.getLocation() != null && l.getLocation().toLowerCase(Locale.ROOT).contains(query));
    }

    String variantTitle(DropSource drop)
    {
        Set<String> versions = service.tableVersions(drop);
        if (!versions.isEmpty()) return String.join(" / ", versions);
        return all(drop, Wilderness.YES) ? "Wilderness locations" : "Standard locations";
    }

    String danger(DropSource drop, WildernessFilter filter)
    {
        if (filter == WildernessFilter.EXCLUDE) return null;
        List<MonsterLocation> places = locations.getOrDefault(drop, Collections.emptyList());
        if (places.stream().noneMatch(l -> l.getWilderness() == Wilderness.YES)) return null;
        if (all(drop, Wilderness.YES) || filter == WildernessFilter.ONLY) return "WILDERNESS\nPvP danger";
        return "Some locations in the WILDERNESS\nPvP danger there";
    }
}
