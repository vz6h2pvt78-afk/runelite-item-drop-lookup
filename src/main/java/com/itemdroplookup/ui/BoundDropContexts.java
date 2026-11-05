package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.model.RoamingEncounter;
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
    private final Map<DropSource, RoamingEncounter> roaming = new IdentityHashMap<>();
    private final Map<DropSource, MonsterLocationLookupService.LocationMatch> reviewedEncounters = new IdentityHashMap<>();
    private final Set<DropSource> split = Collections.newSetFromMap(new IdentityHashMap<>());

    BoundDropContexts(MonsterLocationLookupService service, List<DropSource> drops)
    {
        this.service = service;
        Map<List<String>, List<DropSource>> pairs = new HashMap<>();
        for (DropSource drop : drops)
        {
            MonsterLocationLookupService.LocationMatch match = service.lookup(drop);
            if (match.getContextKind() == MonsterLocationLookupService.LocationMatch.ContextKind.ROAMING_ENCOUNTER)
            {
                roaming.put(drop, match.getRoamingEncounter());
                continue;
            }
            if (!match.hasItemLocationAssociation() && !match.isReviewedEncounter()) continue;
            List<MonsterLocation> available = match.getLocations().stream().filter(MonsterLocation::isAvailable)
                    .collect(Collectors.toList());
            if (available.isEmpty()) continue;
            locations.put(drop, available);
            if (match.isReviewedEncounter())
            {
                reviewedEncounters.put(drop, match);
                continue;
            }
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

    RoamingEncounter roamingContext(DropSource source) { return roaming.get(source); }

    boolean find(DropSource drop, String query, WildernessFilter filter)
    {
        if (SourceTextMatching.contains(drop.getMonsterName(), query)) return true;
        if (drop.getAnimatorReward() != null && (SourceTextMatching.contains(drop.getAnimatorReward().getSetupText(), query)
                || drop.getAnimatorReward().getEquipment().stream().anyMatch(item -> SourceTextMatching.contains(item, query)))) return true;
        RoamingEncounter roamingContext = roaming.get(drop);
        if (roamingContext != null && filter != WildernessFilter.EXCLUDE
                && (SourceTextMatching.contains(roamingContext.getArea(), query)
                    || SourceTextMatching.contains(roamingContext.getEncounter(), query)
                    || SourceTextMatching.contains(roamingContext.getTracking(), query))) return true;
        MonsterLocationLookupService.LocationMatch encounter = reviewedEncounters.get(drop);
        if (encounter != null && filter != WildernessFilter.EXCLUDE
                && SourceTextMatching.contains(encounter.encounterShortLocation(), query)) return true;
        return locations.getOrDefault(drop, Collections.emptyList()).stream()
                .filter(l -> MonsterLocationFilter.matches(l, filter, MonsterLocationFilter.MembershipFilter.ALL))
                .anyMatch(l -> SourceTextMatching.contains(l.getLocation(), query));
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
        RoamingEncounter roamingContext = roaming.get(drop);
        if (roamingContext != null) return LookupStyle.roamingDanger(roamingContext.getArea());
        List<MonsterLocation> places = locations.getOrDefault(drop, Collections.emptyList());
        MonsterLocationLookupService.LocationMatch encounter = reviewedEncounters.get(drop);
        if (encounter != null)
            return LookupStyle.encounterDanger(encounter.encounterLevelText()) + "\n" + encounter.encounterShortLocation();
        if (places.stream().noneMatch(l -> l.getWilderness() == Wilderness.YES)) return null;
        if (all(drop, Wilderness.YES) || filter == WildernessFilter.ONLY) return "WILDERNESS\nPvP danger";
        return "Some locations in the WILDERNESS\nPvP danger there";
    }
}
