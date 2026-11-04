package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.ItemSource;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
        Optional<String> family = prices.findPotionFamily(query);
        explicitDose = family.isPresent() && query.matches(".*\\([1-4]\\)$");
        String lookup = family.isPresent() && !explicitDose ? family.get() : query;
        List<DropSource> foundDrops = dropsService.searchByItemName(lookup);
        List<ItemSource> foundSources = sourceService.searchByItemName(lookup);
        String exactName = compact(query);
        boolean exact = foundDrops.stream().anyMatch(d -> compact(d.getItemName()).equals(exactName))
                || foundSources.stream().anyMatch(s -> compact(s.getItemName()).equals(exactName));
        if (family.isPresent())
        {
            foundDrops = foundDrops.stream().filter(d -> prices.findPotionFamily(d.getItemName()).equals(family)
                    && (!explicitDose || compact(d.getItemName()).equals(exactName))).collect(Collectors.toList());
            foundSources = foundSources.stream().filter(s -> prices.findPotionFamily(s.getItemName()).equals(family)
                    && (!explicitDose || compact(s.getItemName()).equals(exactName))).collect(Collectors.toList());
        }
        else if (exact)
        {
            // Verified pack expansions expose the requested contents as itemName;
            // packName remains the separate purchased item. Do not compare packName.
            foundDrops = foundDrops.stream().filter(d -> compact(d.getItemName()).equals(exactName)).collect(Collectors.toList());
            foundSources = foundSources.stream().filter(s -> compact(s.getItemName()).equals(exactName)).collect(Collectors.toList());
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

    private static String compact(String text)
    {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
