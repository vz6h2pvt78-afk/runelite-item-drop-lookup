package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.ItemSource;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.ItemNameMatching;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Coordinates existing matching tiers before any acquisition-method filter. */
final class ItemSearchSelection
{
    final String query;
    final List<DropSource> drops;
    final List<ItemSource> sources;
    private final ItemPriceLookupService prices;
    private final DropLookupService dropsService;
    private final ItemSourceLookupService sourceService;
    private final List<String> browseMembers;
    private final Map<String, ItemSearchSelection> selectedItems = new HashMap<>();
    private final boolean explicitDose;
    private final boolean broadDiscovery;
    private final Optional<String> exactCatalogItem;
    private final Optional<String> unqualifiedPotionFamily;

    ItemSearchSelection(String query, DropLookupService dropsService, ItemSourceLookupService sourceService, ItemPriceLookupService prices)
    {
        this(query, dropsService, sourceService, prices, false);
    }

    private ItemSearchSelection(String query, DropLookupService dropsService, ItemSourceLookupService sourceService,
                                ItemPriceLookupService prices, boolean canonicalSelection)
    {
        this.query = query;
        this.prices = prices;
        this.dropsService = dropsService;
        this.sourceService = sourceService;
        browseMembers = canonicalSelection ? Collections.emptyList()
                : prices.findBrowseGroup(query).map(group -> group.getMembers()).orElse(Collections.emptyList());
        if (isBrowse())
        {
            explicitDose = false;
            broadDiscovery = false;
            unqualifiedPotionFamily = Optional.empty();
            exactCatalogItem = Optional.empty();
            drops = Collections.emptyList();
            sources = Collections.emptyList();
            return;
        }
        ItemSource exactOffer = sourceService.findExactBulkOffer(query);
        if (exactOffer != null)
        {
            explicitDose = false;
            broadDiscovery = false;
            unqualifiedPotionFamily = Optional.empty();
            exactCatalogItem = prices.findExactItemName(exactOffer.getItemName());
            drops = Collections.emptyList();
            sources = Collections.singletonList(exactOffer);
            return;
        }
        String canonicalQuery = ItemNameMatching.reviewedAliases().getOrDefault(ItemNameMatching.normalize(query), query.trim());
        query = ItemNameMatching.resolveAlias(query);
        Optional<String> family = prices.findPotionFamily(query);
        String compactQuery = ItemNameMatching.compact(query);
        unqualifiedPotionFamily = family.filter(name -> ItemNameMatching.compact(name).equals(compactQuery));
        // A numeral within a reviewed family name (Serum 207) is part of its identity.
        // Explicit suffixes and existing compact dose queries still retain the exact dose.
        explicitDose = !unqualifiedPotionFamily.isPresent() && query.matches(".*[0-9].*");
        // Establish an exact known identity before gathering source candidates.
        // Unqualified potion families deliberately retain their existing dose view.
        exactCatalogItem = family.isPresent() && !explicitDose ? Optional.empty() : prices.findExactItemName(canonicalQuery)
                .or(() -> dropsService.findReviewedSourceItemName(canonicalQuery))
                .or(() -> canonicalSelection ? Optional.of(canonicalQuery) : Optional.empty());
        String lookup = family.isPresent() && !explicitDose ? family.get() : exactCatalogItem.orElse(query);
        List<DropSource> foundDrops = dropsService.searchByItemName(lookup);
        List<ItemSource> foundSources = sourceService.searchByItemName(lookup);
        if (family.isPresent() && !explicitDose)
        {
            // A reviewed family includes its doses even when its own name has a qualifier, e.g. (+).
            // Do not relax qualifier matching for any ordinary item or explicit dose query.
            Set<String> members = new LinkedHashSet<>();
            members.add(family.get());
            for (int dose = 1; dose <= 4; dose++) members.add(family.get() + "(" + dose + ")");
            foundDrops = dropsService.findByExactNames(members);
            foundSources = sourceService.findByExactNames(members);
        }
        String exactName = ItemNameMatching.compact(query);
        if (family.isPresent())
        {
            foundDrops = foundDrops.stream().filter(d -> prices.findPotionFamily(d.getItemName()).equals(family)
                    && (!explicitDose || ItemNameMatching.compact(d.getItemName()).equals(exactName))).collect(Collectors.toList());
            foundSources = foundSources.stream().filter(s -> prices.findPotionFamily(s.getItemName()).equals(family)
                    && (!explicitDose || ItemNameMatching.compact(s.getItemName()).equals(exactName))).collect(Collectors.toList());
        }
        else
        {
            // Choose the same most-specific tier across every source type. Tied
            // canonical names remain separate choices; packs use contents identity.
            List<String> names = new ArrayList<>();
            foundDrops.forEach(d -> names.add(d.getItemName()));
            foundSources.forEach(s -> names.add(s.getItemName()));
            List<String> best = ItemNameMatching.bestMatches(query, names, name -> name);
            if (!best.isEmpty())
            {
                foundDrops = foundDrops.stream().filter(d -> best.contains(d.getItemName())).collect(Collectors.toList());
                foundSources = foundSources.stream().filter(s -> best.contains(s.getItemName())).collect(Collectors.toList());
            }
        }
        if (exactCatalogItem.isPresent())
        {
            // Lack of coverage for a known item must not substitute another item.
            String exact = exactCatalogItem.get();
            foundDrops = foundDrops.stream().filter(d -> d.getItemName().equalsIgnoreCase(exact)).collect(Collectors.toList());
            foundSources = foundSources.stream().filter(s -> s.getItemName().equalsIgnoreCase(exact)).collect(Collectors.toList());
        }
        // Known kill-then-harvest pairs have a Gathering projection, including an
        // honest fallback when reviewed event details cannot be loaded.
        foundDrops = foundDrops.stream().filter(d -> !sourceService.isHarvestSource(d)).collect(Collectors.toList());
        // Exact identities still win. A non-exact prefix may look unique only
        // because the strict tier omitted other compatible contains matches.
        // Gather those before the UI decides whether there is one item or a chooser.
        TreeSet<String> strictChoices = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        foundDrops.forEach(d -> strictChoices.add(choice(d.getItemName())));
        foundSources.forEach(s -> strictChoices.add(choice(s.getItemName())));
        final String resolvedQuery = query;
        boolean hasExact = strictChoices.stream().anyMatch(name -> ItemNameMatching.rank(resolvedQuery, name) <= 1);
        boolean hasCompatible = strictChoices.stream().anyMatch(name -> ItemNameMatching.rank(resolvedQuery, name) != Integer.MAX_VALUE);
        broadDiscovery = !exactCatalogItem.isPresent() && !family.isPresent()
                && !hasExact && hasCompatible;
        if (broadDiscovery)
        {
            foundDrops = dropsService.discoverByItemName(query).stream()
                    .filter(d -> !sourceService.isHarvestSource(d)).collect(Collectors.toList());
            foundSources = sourceService.discoverByItemName(query);
        }
        drops = Collections.unmodifiableList(new ArrayList<>(foundDrops));
        sources = Collections.unmodifiableList(new ArrayList<>(foundSources));
    }

    boolean isBrowse() { return !browseMembers.isEmpty(); }

    boolean hasChooser() { return isBrowse() || choices().size() > 1; }

    /** Cache exact selected objects so Back/detail state keeps the same source identities. */
    private ItemSearchSelection selected(String item)
    {
        return selectedItems.computeIfAbsent(item,
                name -> new ItemSearchSelection(name, dropsService, sourceService, prices, true));
    }

    List<String> choices()
    {
        if (isBrowse()) return browseMembers;
        TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        drops.forEach(d -> result.add(choice(d.getItemName())));
        sources.forEach(s -> result.add(choice(s.getItemName())));
        if (result.isEmpty()) unqualifiedPotionFamily.or(() -> exactCatalogItem).ifPresent(result::add);
        List<String> ordered = new ArrayList<>(result);
        if (broadDiscovery) ordered.sort(ItemChoiceRanking.forQuery(query));
        return ordered;
    }

    List<DropSource> dropsFor(String selected)
    {
        if (isBrowse() || broadDiscovery) return selected(selected).dropsFor(selected);
        return drops.stream().filter(d -> choice(d.getItemName()).equalsIgnoreCase(selected)).collect(Collectors.toList());
    }

    List<ItemSource> sourcesFor(String selected)
    {
        if (isBrowse() || broadDiscovery) return selected(selected).sourcesFor(selected);
        return sources.stream().filter(s -> choice(s.getItemName()).equalsIgnoreCase(selected)).collect(Collectors.toList());
    }

    /** Artwork only: a representative dose never changes selection or source identity. */
    String iconItem(String selected)
    {
        if ("Herb pack".equals(selected)) return "Guam leaf"; // Representative reward artwork approved by Apollo.
        Optional<String> family = prices.findPotionFamily(selected).filter(selected::equalsIgnoreCase);
        if (!family.isPresent()) return selected;
        List<String> exactNames = new ArrayList<>();
        drops.forEach(d -> exactNames.add(d.getItemName()));
        sources.forEach(s -> exactNames.add(s.getItemName()));
        for (int dose = 4; dose >= 1; dose--)
        {
            String candidate = family.get() + "(" + dose + ")";
            String key = ItemNameMatching.compact(candidate);
            Optional<String> catalog = prices.findExactItemName(candidate)
                    .filter(name -> ItemNameMatching.compact(name).equals(key));
            if (catalog.isPresent()) return catalog.get();
            Optional<String> source = exactNames.stream()
                    .filter(name -> ItemNameMatching.compact(name).equals(key)).findFirst();
            if (source.isPresent()) return source.get();
        }
        return isBrowse() ? selected(selected).iconItem(selected) : selected;
    }

    private String choice(String name)
    {
        return explicitDose ? name : prices.findPotionFamily(name).orElse(name);
    }

}
