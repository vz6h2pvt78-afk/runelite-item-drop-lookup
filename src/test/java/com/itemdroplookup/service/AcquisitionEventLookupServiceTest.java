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
        assertEquals(2, lines.stream().filter(l -> l.contains("level 101 Ent. If this log type is rolled: 2 noted logs")).count());
        assertEquals(1, lines.stream().filter(l -> l.contains("level 86 Ent. If this log type is rolled: 1 noted log")).count());
        assertTrue(text.contains("not guaranteed"));
        assertEquals(6, service.getMethods().stream().filter(m -> m.getSourceType().equals("Gathering")).count());
        assertEquals(3, service.getMethods().stream().filter(m -> m.getSourceType().equals("Gathering") && m.isVerified()).count());
        assertEquals("1 (noted)", drops.searchByItemName("Magic logs").stream()
                .filter(d -> d.getMonsterName().equals("Ent")).findFirst().get().getQuantity());
    }

    @Test
    public void yewUsesItsEntOutputConditionAndExactEncounters()
    {
        AcquisitionMethod yew = service(data()).getMethods().stream()
                .filter(m -> m.getItemName().equals("Yew logs")).findFirst().get();
        assertTrue(yew.isVerified());
        assertTrue(yew.getSearchText().contains("61 base Woodcutting"));
        assertFalse(yew.getSearchText().contains("60 base Woodcutting"));
        assertTrue(yew.getSearchText().contains("Kill an Ent, then chop its trunk"));
        assertTrue(yew.getSearchText().contains("level 101 Ent. If this log type is rolled: 2 noted logs"));
        assertTrue(yew.getSearchText().contains("level 86 Ent. If this log type is rolled: 1 noted log"));
        assertTrue(yew.getSearchText().contains("not guaranteed"));
        assertFalse(yew.getSearchText().contains("Slayer"));
        for (String item : new String[]{"Oak logs", "Willow logs", "Maple logs"})
            assertFalse(service(data()).getMethods().stream().filter(m -> m.getItemName().equals(item)).findFirst().get().isVerified());
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
        while (input.getAsJsonArray("harvests").size() > 1) input.getAsJsonArray("harvests").remove(1);
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
    public void ordinaryLogsKeepTheConflictingBoundaryAndOnlyWildernessLocations()
    {
        AcquisitionMethod logs = service(data()).getMethods().stream()
                .filter(m -> m.getItemName().equals("Logs")).findFirst().get();
        assertTrue(logs.isVerified());
        assertTrue(logs.getSearchText().contains("Below 45: available"));
        assertTrue(logs.getSearchText().contains("Level 45: uncertain"));
        assertTrue(logs.getSearchText().contains("46+: unavailable"));
        assertFalse(logs.getSearchText().contains("Requires 1"));
        assertFalse(logs.getLocationText().contains("Guild"));
        assertEquals(2, logs.getSections().stream().filter(s -> s.getHeading().equals("Where and yield"))
                .findFirst().get().getLines().size());
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

    @Test public void entSummaryGroupsOnlyTheTwoReviewedEquivalentWildernessLocations()
    {
        AcquisitionMethod method=magic(service(data()));
        assertEquals(1,method.getSummarySections().stream().filter(s->s.isWilderness()).count());
        assertTrue(method.getSummarySections().stream().anyMatch(s->s.getHeading().equals("East and north of Chaos Temple")));
        assertTrue(method.getSearchText().contains("East of Chaos Temple"));
        assertTrue(method.getSearchText().contains("North of Chaos Temple"));
        assertEquals(3,method.getSections().stream().filter(s->s.getHeading().equals("Where and yield")).findFirst().get().getLines().size());
        for (String field : new String[]{"members","wilderness","requirements","notes","sourceUrl"})
        {
            MonsterLocationLookupService altered=new MonsterLocationLookupService(gson)
            {
                @Override public synchronized List<MonsterLocation> lookupByMonsterName(String monster)
                {
                    List<MonsterLocation> result=new ArrayList<>();
                    for(MonsterLocation location:super.lookupByMonsterName(monster))
                    {
                        JsonObject row=gson.toJsonTree(location).getAsJsonObject();
                        if(location.getId().equals("a68fdb7c45298260aeff"))
                        {
                            if(field.equals("members"))row.addProperty(field,false);
                            else row.addProperty(field,field.equals("wilderness")?"UNKNOWN":"Distinct condition or destination");
                        }
                        result.add(gson.fromJson(row,MonsterLocation.class));
                    }
                    return result;
                }
            };
            AcquisitionMethod separate=magic(new AcquisitionEventLookupService(gson,altered,drops,()->new StringReader(data().toString())));
            assertFalse(field,separate.getSummarySections().stream().anyMatch(s->s.getHeading().equals("East and north of Chaos Temple")));
            if(field.equals("requirements")||field.equals("notes"))
                assertTrue(separate.getSummarySections().stream().flatMap(s->s.getLines().stream()).anyMatch(l->l.contains("Distinct condition")));
        }
    }

    @Test
    public void gloryIsOneLootEventWithThreeAlternativeRoutesAndAFullTable()
    {
        JsonObject input = data();
        assertEquals(19, input.getAsJsonArray("lootEvents").get(0).getAsJsonObject().getAsJsonArray("outcomes").size());
        List<AcquisitionMethod> methods = loot(input);
        assertEquals(16, methods.size());
        AcquisitionMethod glory = methods.stream().filter(m -> m.getItemName().equals("Amulet of glory")).findFirst().get();
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
        com.google.gson.JsonArray initial = new com.google.gson.JsonArray();
        initial.add("dragon-amulet-of-glory");
        input.getAsJsonArray("lootCoverage").get(0).getAsJsonObject().add("includedOutcomeIds", initial);
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

    @Test
    public void finiteLootCoveragePreservesQuantitiesNotingAndExactIdentity()
    {
        String[][] expected = {{"Mystic robe bottom", "1"}, {"Amulet of glory", "3 (noted)"},
                {"Dragonstone amulet", "2 (noted)"}, {"Dragon arrow", "100–250"}, {"Dragonstone bolts", "10–40"},
                {"Dragon longsword", "1"}, {"Dragon dagger(p++)", "3 (noted)"}, {"Dragon dart", "100–250"},
                {"Dragonstone", "3 (noted)"}, {"Dragon dart tip", "100–350"}, {"Dragon arrowtips", "100–350"},
                {"Babydragon bones", "100–300 (noted)"}, {"Dragon bones", "50–100 (noted)"},
                {"Magic seed", "1"}, {"Snapdragon seed", "6"}, {"Summer pie", "15 (noted)"}};
        ItemSourceLookupService sources = new ItemSourceLookupService(gson, service(data()));
        assertEquals(expected.length, loot(data()).size());
        for (String[] row : expected)
        {
            List<com.itemdroplookup.model.ItemSource> matches = new ArrayList<>();
            sources.searchByItemName(row[0]).stream()
                    .filter(s -> s.getItemName().equals(row[0]) && "Dragon impling loot".equals(s.getSourceName())).forEach(matches::add);
            assertEquals(row[0], 1, matches.size());
            AcquisitionMethod method = matches.get(0).getAcquisitionMethod();
            assertTrue(row[0], method.getSearchText().contains("Quantity: " + row[1] + " on this outcome."));
            assertTrue(method.getSearchText().contains("Wiki-listed rate: 1/19"));
            assertFalse(method.getSearchText().contains("1/16"));
            SectionAssertion.assertOpeningHasNoCaptureGate(method);
        }
        for (String excluded : new String[]{"Dragonstone bolt tips", "Dragon javelin tips", "Dragon impling jar",
                "Uncut dragonstone", "Dragon dagger", "Amulet of glory(4)"})
            assertTrue(excluded, sources.searchByItemName(excluded).stream()
                    .filter(s -> s.getItemName().equals(excluded)).noneMatch(s -> "Dragon impling loot".equals(s.getSourceName())));
    }
}
