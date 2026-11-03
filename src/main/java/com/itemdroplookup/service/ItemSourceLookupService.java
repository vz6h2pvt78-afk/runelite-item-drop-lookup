package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itemdroplookup.model.ItemSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class ItemSourceLookupService
{
    // Curated, hand-maintained non-NPC sources.
    private static final String CURATED_RESOURCE = "/item_sources.json";
    // Generated shop sources (tools/build_shop_sources.py). Optional: absent until generated.
    private static final String SHOP_RESOURCE = "/wiki_shop_sources.json";
    // Separate, reviewed ground-only slice; never apply shop suppression to it.
    private static final String GROUND_RESOURCE = "/wiki_ground_sources.json";

    // Generated (wiki_shop_sources.json) records hidden at lookup time because a
    // higher-quality curated record supersedes them, or the generated cost/label is
    // misleading (e.g. a coin price that ignores a reward-point unlock).
    //
    // This is applied ONLY to generated records, never to curated ones, so a curated
    // record that happens to share an (itemName, sourceName) pair is unaffected.
    // Matching is exact on (itemName, sourceName), case-insensitive, and deliberately
    // narrow: it hides only the listed pairs, not every generated record for an item.
    // The generated JSON is left untouched.
    private static final Set<String> SUPPRESSED_GENERATED_SOURCES = buildSuppressedGeneratedSources();

    // This is a mid-raid resupply interaction, not an ordinary acquisition shop.
    // Its generated rows also misidentify Theatre points as coins. Keep it out
    // until raid-specific supplies have their own explicit product treatment.
    private static final String RAID_SUPPLY_CHEST = "Chest (Theatre of Blood)";
    private static final Pattern ZERO_DEFAULT_STOCK = Pattern.compile(
            "\\bDefault stock:\\s*0(?:\\.|\\s|$)", Pattern.CASE_INSENSITIVE);
    // Verified StoreTableHead currencies (Wiki revisions captured in the QA report).
    // These corrections are exact shop names, not guesses from shop categories.
    private static final Map<String, String> VERIFIED_CURRENCIES = Map.of(
            "TzHaar-Mej-Roh's Rune Store", "Tokkul",
            "Agility Arena Store", "Brimhaven vouchers",
            "Brimhaven Agility Arena Ticket Exchange", "discontinued Agility arena tickets",
            "Bounty Hunter Store", "Bounty Hunter points",
            "Justine's stuff for the Last Shopper Standing", "LMS points");

    private static Set<String> buildSuppressedGeneratedSources()
    {
        Set<String> suppressed = new HashSet<>();
        suppressed.add(suppressionKey("Graceful hood", "Grace's Graceful Clothing"));
        suppressed.add(suppressionKey("Rune pouch", "Slayer Rewards"));
        suppressed.add(suppressionKey("Rune pouch", "Justine's stuff for the Last Shopper Standing"));
        return suppressed;
    }

    private static String suppressionKey(String itemName, String sourceName)
    {
        return itemName.trim().toLowerCase(Locale.ROOT)
                + "|"
                + sourceName.trim().toLowerCase(Locale.ROOT);
    }

    private final Gson gson;
    private List<ItemSourceRecord> itemSourceRecords;

    @Inject
    public ItemSourceLookupService(Gson gson)
    {
        this.gson = gson;
    }

    public List<ItemSource> searchByItemName(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String query = normalize(itemName);
        String compactQuery = compactNormalize(itemName);

        return selectMostSpecificMatches(query, compactQuery).stream()
                .map(record -> new ItemSource(
                        record.itemName,
                        record.sourceType,
                        record.sourceName,
                        record.location,
                        record.cost,
                        record.notes,
                        record.packName, record.packItemName, record.packQuantity, record.packNoted
                ))
                .collect(Collectors.toList());
    }

    /**
     * Tiered matching that returns only the most specific non-empty tier, so a precise
     * query is not diluted by broad substring hits (e.g. "rune pouch" should not also
     * return "Rune pouch note"). Tiers, in order:
     *   1. exact normalized item-name match
     *   2. compact exact match (ignores spaces/punctuation: "runepouch" -> "rune pouch")
     *   3. starts-with match
     *   4. contains match
     * Records are scanned in load order (curated first, then generated and suppression-
     * filtered), so result order is stable within whichever tier is selected.
     */
    private List<ItemSourceRecord> selectMostSpecificMatches(String query, String compactQuery)
    {
        List<ItemSourceRecord> exact = new ArrayList<>();
        List<ItemSourceRecord> compactExact = new ArrayList<>();
        List<ItemSourceRecord> startsWith = new ArrayList<>();
        List<ItemSourceRecord> contains = new ArrayList<>();

        for (ItemSourceRecord record : getItemSourceRecords())
        {
            if (record.itemName == null)
            {
                continue;
            }

            String normalizedItem = normalize(record.itemName);
            String compactItem = compactNormalize(record.itemName);

            if (normalizedItem.equals(query))
            {
                exact.add(record);
            }
            else if (!compactQuery.isEmpty() && compactItem.equals(compactQuery))
            {
                compactExact.add(record);
            }
            else if (normalizedItem.startsWith(query))
            {
                startsWith.add(record);
            }
            else if (normalizedItem.contains(query))
            {
                contains.add(record);
            }
        }

        if (!exact.isEmpty())
        {
            return exact;
        }
        if (!compactExact.isEmpty())
        {
            return compactExact;
        }
        if (!startsWith.isEmpty())
        {
            return startsWith;
        }
        return contains;
    }

    private String normalize(String value)
    {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", " ")
                .replace("_", " ")
                .replaceAll("\\s+", " ");
    }

    private String compactNormalize(String value)
    {
        return normalize(value)
                .replaceAll("[^a-z0-9]", "");
    }

    private List<ItemSourceRecord> getItemSourceRecords()
    {
        if (itemSourceRecords != null)
        {
            return itemSourceRecords;
        }

        // Curated sources first, then generated shop sources (if present). Both feed
        // the same searchable list; a missing generated file is treated as empty.
        // Suppression is applied only to the generated set so curated records — even
        // ones sharing an (itemName, sourceName) pair — are always kept.
        List<ItemSourceRecord> merged = new ArrayList<>();
        merged.addAll(loadRecords(CURATED_RESOURCE));
        merged.addAll(filterSuppressedGenerated(loadRecords(SHOP_RESOURCE)));
        merged.addAll(loadRecords(GROUND_RESOURCE));
        // A zero-stock shop relies on player sales; it is not a reliable purchase
        // option. Filter before expanding packs so derived offers follow this rule.
        merged.removeIf(record -> record.sourceType != null
                && record.sourceType.toLowerCase(Locale.ROOT).endsWith("shop")
                && record.notes != null && ZERO_DEFAULT_STOCK.matcher(record.notes).find());

        itemSourceRecords = addPackOptions(merged);
        return itemSourceRecords;
    }

    private List<ItemSourceRecord> addPackOptions(List<ItemSourceRecord> records)
    {
        Map<String, PackRecord> packs = new HashMap<>();
        try (InputStream stream = getClass().getResourceAsStream("/item_packs.json"))
        {
            if (stream != null)
            {
                Type type = new TypeToken<List<PackRecord>>() {}.getType();
                List<PackRecord> definitions = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), type);
                if (definitions != null)
                {
                    definitions.forEach(pack -> packs.put(normalize(pack.packName), pack));
                }
            }
        }
        catch (Exception ex)
        {
            // The original source records remain usable if optional pack data is absent.
            return records;
        }
        List<ItemSourceRecord> expanded = new ArrayList<>();
        for (ItemSourceRecord record : records)
        {
            String currency = VERIFIED_CURRENCIES.get(record.sourceName);
            if (currency != null && record.cost != null)
            {
                record.cost = record.cost.replaceAll(" coins?$", " " + currency);
                if (!"Tokkul".equals(currency))
                {
                    record.sourceType = "Reward Shop";
                    record.notes = record.notes == null ? "" : record.notes.replace("Cost increases as stock depletes.", "").trim();
                }
                if (currency.startsWith("discontinued"))
                {
                    record.notes = "Requires old tickets that can no longer be earned. " + record.notes;
                }
                if ("Tokkul".equals(currency))
                {
                    record.notes = (record.notes == null ? "" : record.notes + " ") + "Karamja gloves reduce the price.";
                }
            }
            PackRecord pack = record.itemName == null ? null : packs.get(normalize(record.itemName));
            if (pack != null)
            {
                record.packName = pack.packName;
                record.packItemName = pack.itemName;
                record.packQuantity = pack.quantity;
                record.packNoted = pack.noted;
                if (pack.costByShop != null && pack.costByShop.containsKey(record.sourceName))
                {
                    record.cost = pack.costByShop.get(record.sourceName);
                    record.sourceType = "Reward Shop";
                    record.notes = pack.notes;
                }
            }
            expanded.add(record);
            if (pack != null)
            {
                ItemSourceRecord contents = new ItemSourceRecord();
                contents.itemName = pack.itemName;
                contents.sourceName = record.sourceName;
                contents.sourceType = record.sourceType;
                contents.location = record.location;
                contents.cost = record.cost;
                contents.notes = record.notes;
                contents.packName = record.packName;
                contents.packItemName = record.packItemName;
                contents.packQuantity = record.packQuantity;
                contents.packNoted = record.packNoted;
                expanded.add(contents);
            }
        }
        return expanded;
    }

    private List<ItemSourceRecord> filterSuppressedGenerated(List<ItemSourceRecord> records)
    {
        List<ItemSourceRecord> kept = new ArrayList<>();
        for (ItemSourceRecord record : records)
        {
            if (RAID_SUPPLY_CHEST.equalsIgnoreCase(record.sourceName))
            {
                continue;
            }
            if (record.itemName == null || record.sourceName == null
                    || !SUPPRESSED_GENERATED_SOURCES.contains(suppressionKey(record.itemName, record.sourceName)))
            {
                kept.add(record);
            }
        }
        return kept;
    }

    private List<ItemSourceRecord> loadRecords(String resource)
    {
        try (InputStream inputStream = ItemSourceLookupService.class.getResourceAsStream(resource))
        {
            if (inputStream == null)
            {
                return Collections.emptyList();
            }

            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ItemSourceRecord>>() {}.getType();

            List<ItemSourceRecord> records = gson.fromJson(reader, listType);

            return records != null ? records : Collections.emptyList();
        }
        catch (Exception ex)
        {
            return Collections.emptyList();
        }
    }

    private static class ItemSourceRecord
    {
        private String itemName;
        private String sourceType;
        private String sourceName;
        private String location;
        private String cost;
        private String notes;
        private String packName;
        private String packItemName;
        private int packQuantity;
        private boolean packNoted;
    }

    private static class PackRecord
    {
        private String packName;
        private String itemName;
        private int quantity;
        private boolean noted;
        private Map<String, String> costByShop;
        private String notes;
    }
}
