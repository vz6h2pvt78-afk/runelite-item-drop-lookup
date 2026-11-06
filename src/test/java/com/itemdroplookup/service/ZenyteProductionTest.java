package com.itemdroplookup.service;

import com.google.gson.*;
import com.itemdroplookup.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZenyteProductionTest
{
    private final Gson gson = new Gson();
    private JsonObject fixture()
    {
        return gson.fromJson(new InputStreamReader(getClass().getResourceAsStream("/zenyte_production.json"),
                StandardCharsets.UTF_8), JsonObject.class);
    }
    private JsonObject fusion(JsonObject root) { return root.getAsJsonArray("recipes").get(0).getAsJsonObject(); }
    private List<AcquisitionMethod> read(JsonObject root)
    {
        return ZenyteProductionAdapter.read(gson,
                new ByteArrayInputStream(gson.toJson(root).getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void outputsOwnOnlyTheirReviewedRecipeAndShardsKeepTheirDrops()
    {
        ItemSourceLookupService sources = new ItemSourceLookupService(gson);
        DropLookupService drops = new DropLookupService(gson);
        for (String name : List.of("Zenyte", "Uncut zenyte"))
        {
            List<ItemSource> rows = sources.findByExactNames(Set.of(name));
            assertEquals(1, rows.size());
            assertEquals("Production", rows.get(0).getSourceType());
            assertTrue(drops.findByExactNames(Set.of(name)).isEmpty());
        }
        List<DropSource> shard = drops.findByExactNames(Set.of("Zenyte shard"));
        assertEquals(2, shard.size());
        assertEquals(Set.of("Demonic gorilla", "Tortured gorilla"),
                shard.stream().map(DropSource::getMonsterName).collect(Collectors.toSet()));
        List<AcquisitionMethod> methods = read(fixture());
        assertEquals(2, methods.size());
        String report = methods.get(0).getReportSummary();
        for (String fact : List.of("1 × Zenyte shard", "1 × Onyx (cut)", "70 (cannot be boosted)",
                "Monkey Madness II", "small or big", "Temple of Marimbo"))
            assertTrue(fact, report.contains(fact));
        assertTrue(methods.get(1).getReportSummary().contains("89 (boosts allowed)"));
        assertTrue(methods.get(1).getReportSummary().contains("Chisel"));
        assertFalse(methods.get(0).getReportSummary().contains("Find Zenyte shard"));
        assertFalse(methods.get(0).getReportSummary().contains("1/300"));
    }

    @Test public void optionalBadOrMissingActionsNeverRemoveTheRecipe()
    {
        for (String target : List.of("dwh", "Uncut zenyte", "Zenyte", "zenyte shard", "missing"))
        {
            JsonObject root = fixture();
            fusion(root).getAsJsonArray("relatedItemActions").get(0).getAsJsonObject().addProperty("itemName", target);
            List<AcquisitionMethod> methods = read(root);
            assertEquals(2, methods.size());
            assertTrue(methods.get(0).getRelatedItemActions().isEmpty());
            assertEquals(1, methods.get(1).getRelatedItemActions().size());
        }
        JsonObject root = fixture();
        fusion(root).remove("relatedItemActions");
        assertEquals(2, read(root).size());
        assertTrue(read(root).get(0).getRelatedItemActions().isEmpty());
        fusion(root).addProperty("relatedItemActions", "bad");
        assertEquals(2, read(root).size());
        assertTrue(read(root).get(0).getRelatedItemActions().isEmpty());
    }

    @Test public void incompleteOrChangedRecipeCannotCreateAFalseRoute()
    {
        for (String field : List.of("inputs", "requirements", "output", "evidence"))
        {
            JsonObject root = fixture();
            fusion(root).remove(field);
            List<AcquisitionMethod> remaining = read(root);
            assertEquals(1, remaining.size());
            assertEquals("Zenyte", remaining.get(0).getItemName());
        }
        JsonObject root = fixture();
        fusion(root).getAsJsonArray("inputs").get(1).getAsJsonObject().addProperty("itemName", "Uncut onyx");
        assertEquals(1, read(root).size());
        root = fixture();
        fusion(root).getAsJsonObject("output").addProperty("quantity", 2);
        assertEquals(1, read(root).size());
        assertTrue(ZenyteProductionAdapter.read(gson, null).isEmpty());
    }
}
