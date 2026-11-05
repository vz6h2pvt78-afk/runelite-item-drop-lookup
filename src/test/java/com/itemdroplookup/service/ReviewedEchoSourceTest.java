package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itemdroplookup.model.DropSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewedEchoSourceTest
{
    @Test
    public void exactLeagueSourcesAreExcludedWhileRawRowsAndOrdinaryTanglerootRemain() throws Exception
    {
        Gson gson = new Gson();
        DropLookupService drops = new DropLookupService(gson);
        Set<String> excluded = AcquisitionSourceRules.reviewedEchoSources(gson);
        assertEquals(14, excluded.size());
        List<DropSource> raw;
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/wiki_monster_drops.json"), StandardCharsets.UTF_8))
        {
            raw = gson.fromJson(reader, new TypeToken<List<DropSource>>(){}.getType());
        }
        List<DropSource> reviewed = new ArrayList<>();
        raw.stream().filter(row -> excluded.contains(row.getMonsterName())).forEach(reviewed::add);
        assertEquals(20, reviewed.size());
        assertEquals(15, reviewed.stream().map(DropSource::getItemName).distinct().count());
        for (DropSource row : reviewed)
        {
            assertFalse(drops.searchByItemName(row.getItemName()).stream().anyMatch(d -> excluded.contains(d.getMonsterName())));
            assertEquals(Optional.of(row.getItemName()), drops.findReviewedSourceItemName(row.getItemName()));
        }
        List<DropSource> pet = drops.searchByItemName("Tangleroot");
        assertEquals(1, pet.size());
        assertEquals("Hespori", pet.get(0).getMonsterName());
        assertEquals("1/5375", pet.get(0).getDropRate());
        assertEquals("",pet.get(0).getNotes());
        assertFalse(excluded.contains("Kalphite Queen"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Example item","Unreviewed (Echo)"));
    }

    @Test
    public void identityRecognitionIsLiteralOrUniqueCompatibleCompactOnly()
    {
        DropLookupService drops = new DropLookupService(new Gson());
        assertEquals(Optional.of("Drygore blowpipe (empty)"),drops.findReviewedSourceItemName("Drygore blowpipe (empty)"));
        assertEquals(Optional.of("Drygore blowpipe (empty)"),drops.findReviewedSourceItemName("drygoreblowpipeempty"));
        for (String query : new String[]{null,"","blowpipe","Drygore blowpipe","Drygore blowpipe (charged)","Emperor ring (i)","fang"})
            assertFalse(drops.findReviewedSourceItemName(query).isPresent());
    }

    @Test
    public void priorSourcePolicyStaysIndependent()
    {
        assertFalse(AcquisitionSourceRules.isOrdinaryDrop("Air rune","Bloodthirsty abyssal demon"));
        assertFalse(AcquisitionSourceRules.isOrdinaryDrop("Chocolate cake","Kamil"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Chocolate cake","Kamil (Nightmare Zone)"));
        assertFalse(AcquisitionSourceRules.isOrdinaryDrop("Adamant platebody","Animated Adamant Armour"));
        assertTrue(AcquisitionSourceRules.isOrdinaryDrop("Warrior guild token","Animated Adamant Armour"));
    }

    @Test
    public void concurrentFirstLookupsPublishCompleteSourceAndIdentityState()
    {
        DropLookupService drops = new DropLookupService(new Gson());
        CompletableFuture<Void> start = new CompletableFuture<>();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int i=0;i<8;i++)
            tasks.add(start.thenRunAsync(() -> {
                assertTrue(drops.searchByItemName("Drygore blowpipe (empty)").isEmpty());
                assertEquals(Optional.of("Drygore blowpipe (empty)"),drops.findReviewedSourceItemName("drygoreblowpipeempty"));
                assertEquals(1,drops.searchByItemName("Dragon warhammer").size());
                assertTrue(drops.searchByItemName("Dragonn warhammer").stream().anyMatch(d -> d.getItemName().equals("Dragon warhammer")));
            }));
        start.complete(null);
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture<?>[0])).join();
    }
}
