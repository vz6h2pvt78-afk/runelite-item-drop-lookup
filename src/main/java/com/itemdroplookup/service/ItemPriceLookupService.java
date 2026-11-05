package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itemdroplookup.model.ItemPrice;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@Singleton
public class ItemPriceLookupService
{
    private static final String PRICES_RESOURCE = "/ge_prices.json";

    private final Gson gson;
    private List<ItemPriceRecord> itemPriceRecords;
    private Map<String, String> potionFamilies;

    /** Verified potion families only: charged jewellery and waterskins are not doses. */
    public Optional<String> findPotionFamily(String itemName)
    {
        if (potionFamilies == null)
        {
            potionFamilies = new HashMap<>();
            try (InputStream stream = getClass().getResourceAsStream("/potion_families.json"))
            {
                if (stream != null)
                {
                    PotionFamilies data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PotionFamilies.class);
                    if (data != null && data.families != null)
                    {
                        data.families.forEach(name -> potionFamilies.put(potionKey(name), name));
                    }
                }
            }
            catch (Exception ex)
            {
                // Ordinary exact prices remain available if optional family data is absent.
            }
        }
        return itemName == null ? Optional.empty() : Optional.ofNullable(potionFamilies.get(potionKey(itemName)));
    }

    private String potionKey(String name)
    {
        // Preserve plus signs: Antidote+ and Antidote++ are different potions.
        return ItemNameMatching.compact(name.trim().replaceAll("\\([1-4]\\)$", ""));
    }

    private static class PotionFamilies
    {
        private List<String> families;
    }

    @Inject
    public ItemPriceLookupService(Gson gson)
    {
        this.gson = gson;
    }

    public Optional<ItemPrice> searchByItemName(String itemName)
    {
        return canonicalRecord(itemName).map(record ->
                new ItemPrice(record.itemName, record.price, record.highAlch, record.tradeable, record.notes));
    }

    /** Canonical names in the saved catalog establish identity independently of recorded prices. */
    private Optional<ItemPriceRecord> canonicalRecord(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty()) return Optional.empty();
        Optional<ItemPriceRecord> literal = getItemPriceRecords().stream()
                .filter(record -> record.itemName != null && record.itemName.equalsIgnoreCase(itemName.trim())).findFirst();
        if (literal.isPresent()) return literal;
        String query = ItemNameMatching.resolveAlias(itemName);
        List<ItemPriceRecord> matches = ItemNameMatching.bestMatches(query, getItemPriceRecords(), record -> record.itemName);
        if (matches.isEmpty() || ItemNameMatching.rank(query, matches.get(0).itemName) > 1
                || matches.stream().map(record -> ItemNameMatching.normalize(record.itemName)).distinct().count() != 1)
        {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    /** Literal or uniquely compatible compact identity, before source-backed discovery. */
    public Optional<String> findExactItemName(String query)
    {
        return canonicalRecord(query).map(record -> record.itemName);
    }

    private List<ItemPriceRecord> getItemPriceRecords()
    {
        if (itemPriceRecords != null)
        {
            return itemPriceRecords;
        }

        try (InputStream inputStream = ItemPriceLookupService.class.getResourceAsStream(PRICES_RESOURCE))
        {
            if (inputStream == null)
            {
                itemPriceRecords = Collections.emptyList();
                return itemPriceRecords;
            }

            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ItemPriceRecord>>() {}.getType();

            itemPriceRecords = gson.fromJson(reader, listType);

            if (itemPriceRecords == null)
            {
                itemPriceRecords = Collections.emptyList();
            }

            return itemPriceRecords;
        }
        catch (Exception ex)
        {
            itemPriceRecords = Collections.emptyList();
            return itemPriceRecords;
        }
    }

    private static class ItemPriceRecord
    {
        private String itemName;
        private String price;
        private String highAlch;
        private boolean tradeable;
        private String notes;
    }
}
