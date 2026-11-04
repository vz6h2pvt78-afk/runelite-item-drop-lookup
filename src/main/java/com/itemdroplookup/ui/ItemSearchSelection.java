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
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Coordinates existing matching tiers before any acquisition-method filter. */
final class ItemSearchSelection
{
    final String query;
    final List<DropSource> drops;
    final List<ItemSource> sources;
    private final ItemPriceLookupService prices;
    private final boolean explicitDose;

    ItemSearchSelection(String query, DropLookupService dropsService, ItemSourceLookupService sourceService, ItemPriceLookupService prices)
    {
        this.query = query;
        this.prices = prices;
        query = ItemNameMatching.resolveAlias(query);
        Optional<String> family = prices.findPotionFamily(query);
        // Numeric compact queries also retain their selected dose (prayerpotion4).
        explicitDose = query.matches(".*[0-9].*");
        String lookup = family.isPresent() && !explicitDose ? family.get() : query;
        List<DropSource> foundDrops = dropsService.searchByItemName(lookup);
        List<ItemSource> foundSources = sourceService.searchByItemName(lookup);
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
        // Known kill-then-harvest pairs have a Gathering projection, including an
        // honest fallback when reviewed event details cannot be loaded.
        foundDrops = foundDrops.stream().filter(d -> !sourceService.isHarvestSource(d)).collect(Collectors.toList());
        drops = Collections.unmodifiableList(new ArrayList<>(foundDrops));
        sources = Collections.unmodifiableList(new ArrayList<>(foundSources));
    }

    List<String> choices()
    {
        TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        drops.forEach(d -> result.add(choice(d.getItemName())));
        sources.forEach(s -> result.add(choice(s.getItemName())));
        return new ArrayList<>(result);
    }

    List<DropSource> dropsFor(String selected)
    {
        return drops.stream().filter(d -> choice(d.getItemName()).equalsIgnoreCase(selected)).collect(Collectors.toList());
    }

    List<ItemSource> sourcesFor(String selected)
    {
        return sources.stream().filter(s -> choice(s.getItemName()).equalsIgnoreCase(selected)).collect(Collectors.toList());
    }

    private String choice(String name)
    {
        return explicitDose ? name : prices.findPotionFamily(name).orElse(name);
    }

}
