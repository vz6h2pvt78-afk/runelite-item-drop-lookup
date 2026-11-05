package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itemdroplookup.model.DropSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedHistoricalSourceTest
{
    @Test public void exactUnusedSourceIsHiddenAndAllOtherRecordedSourcesRemain() throws Exception
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        Set<String> excluded = AcquisitionSourceRules.reviewedHistoricalSources(gson);
        assertEquals(Collections.singleton("Bloodthirst rockslug"), excluded);
        List<DropSource> raw;
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/wiki_monster_drops.json"), StandardCharsets.UTF_8))
        {
            raw = gson.fromJson(reader, new TypeToken<List<DropSource>>(){}.getType());
        }
        List<DropSource> reviewed = raw.stream().filter(row -> excluded.contains(row.getMonsterName())).collect(Collectors.toList());
        assertEquals(13, reviewed.size());
        Set<String> items = reviewed.stream().map(DropSource::getItemName).collect(Collectors.toSet());
        assertEquals(12, items.size());
        Set<String> allExcluded = new HashSet<>(AcquisitionSourceRules.reviewedEchoSources(gson));
        allExcluded.addAll(excluded);
        for (String item : items)
        {
            List<String> expected = raw.stream().filter(row -> row.getItemName().equals(item)
                    && AcquisitionSourceRules.isOrdinaryDrop(item, row.getMonsterName())
                    && !allExcluded.contains(row.getMonsterName())).map(gson::toJson).sorted().collect(Collectors.toList());
            List<DropSource> actual = drops.searchByItemName(item);
            assertEquals(item, expected, actual.stream().map(gson::toJson).sorted().collect(Collectors.toList()));
            assertFalse(item, actual.isEmpty());
            assertEquals(Optional.of(item), drops.findReviewedSourceItemName(item));
        }
        for (String counterpart : List.of("Rockslug", "Giant rockslug"))
            for (DropSource row : raw)
                if (row.getMonsterName().equals(counterpart))
                    assertTrue(counterpart, drops.searchByItemName(row.getItemName()).stream()
                            .anyMatch(visible -> gson.toJson(visible).equals(gson.toJson(row))));
        assertFalse(excluded.contains("Bloodthirsty rockslug"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Coal", "Bloodthirst rockslug"));
    }

    @Test public void concurrentInitializationPublishesBothReviewedResourcesAndDwhTogether()
    {
        DropLookupService drops = new DropLookupService(new Gson());
        CompletableFuture<Void> start = new CompletableFuture<>();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            tasks.add(start.thenRunAsync(() -> {
                assertFalse(drops.searchByItemName("Earth rune").stream()
                        .anyMatch(row -> row.getMonsterName().equals("Bloodthirst rockslug")));
                assertEquals(Optional.of("Earth rune"), drops.findReviewedSourceItemName("earthrune"));
                assertTrue(drops.searchByItemName("Drygore blowpipe (empty)").isEmpty());
                assertEquals(Optional.of("Drygore blowpipe (empty)"), drops.findReviewedSourceItemName("drygoreblowpipeempty"));
                assertEquals(1, drops.searchByItemName("dwh").size());
                assertTrue(drops.searchByItemName("Dragonn warhammer").stream().anyMatch(row -> row.getItemName().equals("Dragon warhammer")));
            }));
        start.complete(null);
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture<?>[0])).join();
    }
}
