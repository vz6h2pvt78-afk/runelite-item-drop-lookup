package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AcquisitionMethod;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class AcquisitionEventLookupServiceTest
{
    private final Gson gson = new Gson();
    private final DropLookupService drops = new DropLookupService(gson);
    private final MonsterLocationLookupService locations = new MonsterLocationLookupService(gson);

    private JsonObject data()
    {
        return gson.fromJson(new InputStreamReader(getClass().getResourceAsStream("/acquisition_events.json"),
                StandardCharsets.UTF_8), JsonObject.class);
    }

    private AcquisitionEventLookupService service(JsonObject data)
    {
        return new AcquisitionEventLookupService(gson, locations, drops, () -> new StringReader(data.toString()));
    }

    private AcquisitionMethod magic(AcquisitionEventLookupService service)
    {
        return service.getMethods().stream().filter(m -> m.getItemName().equals("Magic logs")).findFirst().get();
    }

    @Test
    public void magicLogsDescribeTheEncounterAndEachSuccessfulHarvest()
    {
        AcquisitionEventLookupService service = service(data());
        AcquisitionMethod method = magic(service);
        assertTrue(method.isVerified());
        assertEquals("Gathering", method.getSourceType());
        assertEquals("Ent", method.getWikiPage());
        String text = method.getSearchText();
        assertTrue(text.contains("Kill an Ent, then chop its trunk"));
        assertTrue(text.contains("75 base Woodcutting"));
        assertTrue(text.contains("An axe usable at your Woodcutting level"));
        List<String> lines = method.getSections().stream().filter(s -> s.getHeading().equals("Where and yield"))
                .findFirst().get().getLines();
        assertEquals(3, lines.size());
        assertEquals(2, lines.stream().filter(l -> l.contains("level 101 Ent; 2 noted logs per successful chop")).count());
        assertEquals(1, lines.stream().filter(l -> l.contains("level 86 Ent; 1 noted log per successful chop")).count());
        assertTrue(text.contains("not guaranteed"));
        assertEquals(6, service.getMethods().stream().filter(m -> m.getSourceType().equals("Gathering")).count());
        assertEquals(1, service.getMethods().stream().filter(m -> m.getSourceType().equals("Gathering") && m.isVerified()).count());
        assertEquals("1 (noted)", drops.searchByItemName("Magic logs").stream()
                .filter(d -> d.getMonsterName().equals("Ent")).findFirst().get().getQuantity());
    }

    @Test
    public void brokenOrMissingDataKeepsHarvestingTruthAndUnrelatedSources()
    {
        for (String invalid : new String[]{null, "{broken", "{}"})
        {
            AcquisitionEventLookupService service = new AcquisitionEventLookupService(gson, locations, drops,
                    () -> invalid == null ? null : new StringReader(invalid));
            assertFalse(magic(service).isVerified());
            assertTrue(magic(service).getSearchText().contains("details")
                    || magic(service).getSearchText().contains("unavailable"));
            assertFalse(magic(service).getSearchText().contains("1 (noted)"));
            ItemSourceLookupService sources = new ItemSourceLookupService(gson, service);
            assertFalse(sources.searchByItemName("Air rune").isEmpty());
            assertTrue(service.isHarvestSource(new DropSource("Magic logs", "Ent", "changed", "changed", "")));
            assertFalse(service.isHarvestSource(new DropSource("Slayer's enchantment", "Ent", "1", "1/236", "")));
        }
    }

    private void rejects(Consumer<JsonObject> change)
    {
        JsonObject input = data();
        change.accept(input.getAsJsonArray("harvests").get(0).getAsJsonObject());
        assertFalse(magic(service(input)).isVerified());
    }

    @Test
    public void unsafeAssociationAndProbabilityChangesFailHonestly()
    {
        rejects(r -> r.getAsJsonObject("legacyDrop").addProperty("quantity", "2 (noted)"));
        rejects(r -> r.getAsJsonArray("locations").get(0).getAsJsonObject().addProperty("locationId", "unknown"));
        rejects(r -> r.getAsJsonArray("locations").get(0).getAsJsonObject().addProperty("combatLevel", 86));
        rejects(r -> r.getAsJsonArray("locations").get(0).getAsJsonObject().addProperty("locationName", "Somewhere nearby"));
        rejects(r -> r.getAsJsonArray("locations").get(0).getAsJsonObject().addProperty("quantityBasis", "PER_KILL"));
        rejects(r -> r.getAsJsonObject("outputCondition").addProperty("scope", "CAPTURE"));
        rejects(r -> r.getAsJsonObject("toolRequirement").addProperty("tool", "PICKAXE"));
        rejects(r -> r.addProperty("chanceKind", "GUARANTEED"));
        rejects(r -> r.addProperty("unknownRequirement", 1));
        rejects(r -> r.getAsJsonArray("evidence").get(0).getAsJsonObject().addProperty("revisionId", 1));
    }

    @Test
    public void absentLowerBoundIsNotInventedAndInvalidIntervalsAreRejected()
    {
        JsonObject input = data();
        JsonObject row = input.getAsJsonArray("harvests").get(0).getAsJsonObject();
        row.addProperty("id", "ent-logs");
        row.addProperty("itemName", "Logs");
        row.getAsJsonObject("legacyDrop").addProperty("itemName", "Logs");
        row.getAsJsonArray("locations").remove(2); // Guild is deliberately not asserted for ordinary logs.
        JsonObject condition = row.getAsJsonObject("outputCondition");
        condition.remove("minBaseLevel");
        condition.addProperty("maxBaseLevelExclusive", 45);
        condition.addProperty("text", "Ordinary logs are unavailable at 45 base Woodcutting or above.");
        AcquisitionMethod logs = service(input).getMethods().stream().filter(m -> m.getItemName().equals("Logs")).findFirst().get();
        assertTrue(logs.isVerified());
        assertTrue(logs.getSearchText().contains("45 base Woodcutting or above"));
        assertFalse(logs.getSearchText().contains("Requires 1"));
        assertFalse(logs.getLocationText().contains("Guild"));
        rejects(r -> r.getAsJsonObject("outputCondition").addProperty("maxBaseLevelExclusive", 75));
    }

    @Test
    public void changedLocationRecordsDoNotReceiveApproximateHarvestAssociations()
    {
        for (String changedField : new String[]{"monsterName", "id", "available"})
        {
            MonsterLocationLookupService changedLocations = new MonsterLocationLookupService(gson)
            {
                @Override public List<MonsterLocation> lookupByMonsterName(String monster)
                {
                    List<MonsterLocation> values = new ArrayList<>(super.lookupByMonsterName(monster));
                    JsonObject changed = gson.toJsonTree(values.get(0)).getAsJsonObject();
                    if (changedField.equals("available")) changed.addProperty(changedField, false);
                    else changed.addProperty(changedField, "Changed");
                    values.set(0, gson.fromJson(changed, MonsterLocation.class));
                    return values;
                }
            };
            AcquisitionEventLookupService service = new AcquisitionEventLookupService(gson, changedLocations, drops,
                    () -> new StringReader(data().toString()));
            assertFalse(magic(service).isVerified());
        }
    }

    private List<AcquisitionMethod> loot(JsonObject input)
    {
        List<AcquisitionMethod> result = new ArrayList<>();
        for (AcquisitionMethod method : service(input).getMethods())
            if (method.getSourceType().equals("Loot & containers")) result.add(method);
        return result;
    }

    @Test
    public void gloryIsOneLootEventWithThreeAlternativeRoutesAndAFullTable()
    {
        JsonObject input = data();
        assertEquals(19, input.getAsJsonArray("lootEvents").get(0).getAsJsonObject().getAsJsonArray("outcomes").size());
        List<AcquisitionMethod> methods = loot(input);
        assertEquals(1, methods.size());
        AcquisitionMethod glory = methods.get(0);
        assertEquals("Amulet of glory", glory.getItemName());
        assertEquals("Dragon impling jar", glory.getWikiPage());
        String text = glory.getSearchText();
        assertTrue(text.contains("Quantity: 3 (noted)"));
        assertTrue(text.contains("Wiki-listed rate: 1/19"));
        assertTrue(text.contains("Monkey Madness II"));
        assertFalse(text.contains("1/18"));
        assertFalse(text.contains("1/16"));
        assertTrue(text.contains("83 Hunter"));
        assertTrue(text.contains("93 Hunter"));
        assertTrue(text.contains("Outside Puro-Puro"));
        assertTrue(text.contains("Bare-handed catches also fill"));
        SectionAssertion.assertOpeningHasNoCaptureGate(glory);
        assertEquals(3, glory.getSections().stream().filter(s -> s.getHeading().startsWith("Catch")
                || s.getHeading().equals("Already have a jar?")).count());
    }

    private static class SectionAssertion
    {
        static void assertOpeningHasNoCaptureGate(AcquisitionMethod method)
        {
            String opening = String.join(" ", method.getSections().stream()
                    .filter(s -> s.getHeading().equals("Already have a jar?")).findFirst().get().getLines());
            assertTrue(opening.contains("No Hunter level"));
            assertFalse(opening.contains("83"));
            assertFalse(opening.contains("93"));
        }
    }

    @Test
    public void missingOutcomesOrCaptureGateOnOpeningWithholdOnlyLoot()
    {
        JsonObject input = data();
        input.getAsJsonArray("lootEvents").get(0).getAsJsonObject().getAsJsonArray("outcomes").remove(0);
        assertTrue(loot(input).isEmpty());
        assertTrue(magic(service(input)).isVerified());
        input = data();
        JsonObject event = input.getAsJsonArray("lootEvents").get(0).getAsJsonObject();
        event.getAsJsonArray("routes").get(0).getAsJsonObject().getAsJsonArray("captureOptions")
                .add(event.getAsJsonArray("routes").get(1).getAsJsonObject().getAsJsonArray("captureOptions").get(0));
        assertTrue(loot(input).isEmpty());
        assertTrue(magic(service(input)).isVerified());
        input = data();
        input.getAsJsonArray("lootEvents").get(0).getAsJsonObject().getAsJsonArray("routes").get(2)
                .getAsJsonObject().addProperty("area", "Puro-Puro");
        assertTrue(loot(input).isEmpty());
    }

    @Test
    public void lookupCoverageNeverChangesTheTableOrBecomesTheJarIdentity()
    {
        JsonObject input = data();
        input.getAsJsonArray("lootCoverage").get(0).getAsJsonObject().getAsJsonArray("includedOutcomeIds")
                .add("dragon-magic-seed");
        List<AcquisitionMethod> methods = loot(input);
        assertEquals(2, methods.size());
        for (AcquisitionMethod method : methods) assertTrue(method.getSearchText().contains("1/19"));
        ItemSourceLookupService sources = new ItemSourceLookupService(gson, service(data()));
        assertTrue(sources.searchByItemName("Dragon impling jar").stream().noneMatch(s -> s.getAcquisitionMethod() != null));
        assertTrue(sources.searchByItemName("Amulet of glory(4)").stream().noneMatch(s -> s.getAcquisitionMethod() != null));
        input.getAsJsonArray("lootCoverage").get(0).getAsJsonObject().getAsJsonArray("includedOutcomeIds")
                .add("dragon-dragon-javelin-tips");
        assertTrue(loot(input).isEmpty());
    }
}
