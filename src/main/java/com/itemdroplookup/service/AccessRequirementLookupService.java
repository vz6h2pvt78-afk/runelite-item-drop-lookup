package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;
import com.itemdroplookup.model.AccessRequirement;
import com.itemdroplookup.model.AccessRequirement.Scope;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Offline facts with exact page/item/location bindings. Does not filter sources. */
@Slf4j
@Singleton
public class AccessRequirementLookupService
{
    private static final Map<String, List<String>> SENTINEL_REGULAR_ROWS = Map.ofEntries(
            Map.entry("Adamant kiteshield", List.of("1", "1/52")),
            Map.entry("Adamant platelegs", List.of("1", "1/52")),
            Map.entry("Adamantite ore", List.of("1-2 (noted)", "1/52")),
            Map.entry("Astral rune", List.of("15-35", "6/52")),
            Map.entry("Blood rune", List.of("8-20", "3/52")),
            Map.entry("Coins", List.of("500-2498", "7/52")),
            Map.entry("Death rune", List.of("10-25", "3/52")),
            Map.entry("Law rune", List.of("20-30", "6/52")),
            Map.entry("Nature rune", List.of("15-30", "6/52")),
            Map.entry("Rune axe", List.of("1", "2/52")),
            Map.entry("Rune battleaxe", List.of("1", "1/52")),
            Map.entry("Rune dagger", List.of("1", "1/52")),
            Map.entry("Rune med helm", List.of("1", "1/52")),
            Map.entry("Rune scimitar", List.of("1", "1/52")),
            Map.entry("Rune spear", List.of("1", "1/52")));

    private static final Map<String, Map<String, List<String>>> SUPERIOR_REGULAR_ROWS = Map.ofEntries(
            Map.entry("Cave abomination", Map.ofEntries(
                    Map.entry("superior-regular-cave-abomination-adamant-full-helm-1",
                            List.of("Adamant full helm", "1", "1/128")),
                    Map.entry("superior-regular-cave-abomination-black-mask-10-1",
                            List.of("Black mask (10)", "1", "1/512")),
                    Map.entry("superior-regular-cave-abomination-coins-440",
                            List.of("Coins", "440", "1/128")),
                    Map.entry("superior-regular-cave-abomination-coins-132",
                            List.of("Coins", "132", "12/128")),
                    Map.entry("superior-regular-cave-abomination-coins-44",
                            List.of("Coins", "44", "28/128")),
                    Map.entry("superior-regular-cave-abomination-limpwurt-root-1",
                            List.of("Limpwurt root", "1", "7/128")),
                    Map.entry("superior-regular-cave-abomination-mahogany-logs-2",
                            List.of("Mahogany logs", "2", "3/128")),
                    Map.entry("superior-regular-cave-abomination-mithril-axe-1",
                            List.of("Mithril axe", "1", "3/128")),
                    Map.entry("superior-regular-cave-abomination-mithril-kiteshield-1",
                            List.of("Mithril kiteshield", "1", "1/128")),
                    Map.entry("superior-regular-cave-abomination-nature-rune-3",
                            List.of("Nature rune", "3", "1/128")),
                    Map.entry("superior-regular-cave-abomination-nature-rune-4",
                            List.of("Nature rune", "4", "5/128")),
                    Map.entry("superior-regular-cave-abomination-nature-rune-6",
                            List.of("Nature rune", "6", "6/128")),
                    Map.entry("superior-regular-cave-abomination-rune-dagger-1",
                            List.of("Rune dagger", "1", "1/128")),
                    Map.entry("superior-regular-cave-abomination-teak-logs-4-noted",
                            List.of("Teak logs", "4 (noted)", "7/128")))),
            Map.entry("Marble gargoyle", Map.ofEntries(
                    Map.entry("superior-regular-marble-gargoyle-adamant-boots-1",
                            List.of("Adamant boots", "1", "1/128")),
                    Map.entry("superior-regular-marble-gargoyle-adamant-platelegs-1",
                            List.of("Adamant platelegs", "1", "4/128")),
                    Map.entry("superior-regular-marble-gargoyle-chaos-rune-30",
                            List.of("Chaos rune", "30", "8/128")),
                    Map.entry("superior-regular-marble-gargoyle-coins-500-1000",
                            List.of("Coins", "500-1000", "20/128")),
                    Map.entry("superior-regular-marble-gargoyle-coins-400-800",
                            List.of("Coins", "400-800", "28/128")),
                    Map.entry("superior-regular-marble-gargoyle-coins-10000",
                            List.of("Coins", "10000", "5/128")),
                    Map.entry("superior-regular-marble-gargoyle-death-rune-15",
                            List.of("Death rune", "15", "5/128")),
                    Map.entry("superior-regular-marble-gargoyle-fire-rune-75",
                            List.of("Fire rune", "75", "10/128")),
                    Map.entry("superior-regular-marble-gargoyle-fire-rune-150",
                            List.of("Fire rune", "150", "6/128")),
                    Map.entry("superior-regular-marble-gargoyle-gold-bar-10-15-noted",
                            List.of("Gold bar", "10-15 (noted)", "3/128")),
                    Map.entry("superior-regular-marble-gargoyle-gold-ore-10-20-noted",
                            List.of("Gold ore", "10-20 (noted)", "10/128")),
                    Map.entry("superior-regular-marble-gargoyle-mithril-bar-15-noted",
                            List.of("Mithril bar", "15 (noted)", "2/128")),
                    Map.entry("superior-regular-marble-gargoyle-pure-essence-150-noted",
                            List.of("Pure essence", "150 (noted)", "6/128")),
                    Map.entry("superior-regular-marble-gargoyle-rune-2h-sword-1",
                            List.of("Rune 2h sword", "1", "2/128")),
                    Map.entry("superior-regular-marble-gargoyle-rune-battleaxe-1",
                            List.of("Rune battleaxe", "1", "1/128")),
                    Map.entry("superior-regular-marble-gargoyle-rune-full-helm-1",
                            List.of("Rune full helm", "1", "3/128")),
                    Map.entry("superior-regular-marble-gargoyle-rune-platelegs-1",
                            List.of("Rune platelegs", "1", "1/128")),
                    Map.entry("superior-regular-marble-gargoyle-runite-ore-1",
                            List.of("Runite ore", "1", "2/128")),
                    Map.entry("superior-regular-marble-gargoyle-steel-bar-15-noted",
                            List.of("Steel bar", "15 (noted)", "6/128")))),
            Map.entry("Choke devil", Map.ofEntries(
                    Map.entry("superior-regular-choke-devil-adamant-axe-1",
                            List.of("Adamant axe", "1", "3/128")),
                    Map.entry("superior-regular-choke-devil-adamantite-bar-4-noted",
                            List.of("Adamantite bar", "4 (noted)", "1/128")),
                    Map.entry("superior-regular-choke-devil-air-battlestaff-1",
                            List.of("Air battlestaff", "1", "2/128")),
                    Map.entry("superior-regular-choke-devil-black-d-hide-vambraces-1",
                            List.of("Black d'hide vambraces", "1", "1/128")),
                    Map.entry("superior-regular-choke-devil-chaos-rune-80",
                            List.of("Chaos rune", "80", "7/128")),
                    Map.entry("superior-regular-choke-devil-coins-2000-4000",
                            List.of("Coins", "2000-4000", "32/128")),
                    Map.entry("superior-regular-choke-devil-dragon-chainbody-1",
                            List.of("Dragon chainbody", "1", "1/32768")),
                    Map.entry("superior-regular-choke-devil-dragon-dagger-1",
                            List.of("Dragon dagger", "1", "1/128")),
                    Map.entry("superior-regular-choke-devil-dust-battlestaff-1",
                            List.of("Dust battlestaff", "1", "1/4000")),
                    Map.entry("superior-regular-choke-devil-dust-rune-200",
                            List.of("Dust rune", "200", "10/128")),
                    Map.entry("superior-regular-choke-devil-earth-battlestaff-1",
                            List.of("Earth battlestaff", "1", "2/128")),
                    Map.entry("superior-regular-choke-devil-earth-rune-300",
                            List.of("Earth rune", "300", "10/128")),
                    Map.entry("superior-regular-choke-devil-fire-rune-50",
                            List.of("Fire rune", "50", "1/128")),
                    Map.entry("superior-regular-choke-devil-fire-rune-300",
                            List.of("Fire rune", "300", "10/128")),
                    Map.entry("superior-regular-choke-devil-mithril-bar-10-noted",
                            List.of("Mithril bar", "10 (noted)", "3/128")),
                    Map.entry("superior-regular-choke-devil-mystic-air-staff-1",
                            List.of("Mystic air staff", "1", "1/128")),
                    Map.entry("superior-regular-choke-devil-mystic-earth-staff-1",
                            List.of("Mystic earth staff", "1", "1/128")),
                    Map.entry("superior-regular-choke-devil-red-d-hide-vambraces-1",
                            List.of("Red d'hide vambraces", "1", "2/128")),
                    Map.entry("superior-regular-choke-devil-rune-arrow-12",
                            List.of("Rune arrow", "12", "5/128")),
                    Map.entry("superior-regular-choke-devil-rune-dagger-1",
                            List.of("Rune dagger", "1", "2/128")),
                    Map.entry("superior-regular-choke-devil-soul-rune-50",
                            List.of("Soul rune", "50", "1/128")),
                    Map.entry("superior-regular-choke-devil-soul-rune-20",
                            List.of("Soul rune", "20", "4/128")),
                    Map.entry("superior-regular-choke-devil-ugthanki-kebab-4",
                            List.of("Ugthanki kebab", "4", "2/128")))));

    private static final Map<String, Long> SUPERIOR_ROLL_REVISIONS = Map.of(
            "Cave abomination", 15373805L, "Marble gargoyle", 15383620L, "Choke devil", 15373841L);

    private static final Map<String, Long> BRITTLE_KEY_REVISIONS = Map.of(
            "Gargoyle", 15385687L, "Marble gargoyle", 15383620L);

    private static final Map<String, Long> CRYSTAL_SHARD_REVISIONS = Map.of(
            "Dark beast", 15350345L, "Greater Nechryael", 15341801L,
            "Kurask", 15386817L, "Mutated Bloodveld", 15338397L, "Waterfiend", 15385712L,
            "Insatiable mutated Bloodveld", 15357549L, "King kurask", 15373844L,
            "Nechryarch", 15373905L, "Night beast", 15373912L);
    private static final Set<String> GUARANTEED_CRYSTAL_SHARD_SOURCES = Set.of(
            "Insatiable mutated Bloodveld", "King kurask", "Nechryarch", "Night beast");

    private static final Map<String, List<String>> ELITE_CLUE_CONDITIONS = Map.of(
            "elite-clue-aberrant-spectre-next-step", List.of("Aberrant spectre", "Clue scroll (elite)",
                    "Only for a non-final elite cryptic clue step that asks you to kill an aberrant spectre."),
            "elite-clue-aberrant-spectre-casket", List.of("Aberrant spectre", "Reward casket (elite)",
                    "Only for the final elite cryptic clue step that asks you to kill an aberrant spectre."),
            "elite-clue-king-black-dragon-key", List.of("King Black Dragon", "Key (elite)",
                    "Only for an elite clue step asking for a King Black Dragon kill, with no Key (elite) in your bank or inventory."));
    private static final Map<String, Long> ELITE_CLUE_REVISIONS = Map.of(
            "Aberrant spectre", 15371564L, "King Black Dragon", 15385987L);

    private static final Comparator<AccessRequirement> ITEM_DROP_CONTEXT_FIRST =
            Comparator.comparing(AccessRequirement::isBoundItemDropContext).reversed();

    private final Gson gson;
    private final DropLookupService drops;
    private final MonsterLocationLookupService locations;
    private final Map<String, List<AccessRequirement>> byMonster = new HashMap<>();
    private final Map<String, List<AccessRequirement>> accessByItem = new HashMap<>();
    private boolean loaded;

    public AccessRequirementLookupService(Gson gson)
    {
        this(gson, new DropLookupService(gson));
    }

    public AccessRequirementLookupService(Gson gson, DropLookupService drops)
    {
        this(gson, drops, new MonsterLocationLookupService(gson, drops));
    }

    @Inject
    public AccessRequirementLookupService(Gson gson, DropLookupService drops, MonsterLocationLookupService locations)
    {
        this.gson = gson;
        this.drops = drops;
        this.locations = locations;
    }

    public List<AccessRequirement> forSource(DropSource source)
    {
        return facts(source.getMonsterName()).stream().filter(fact -> fact.getScope() == Scope.MONSTER
                || (fact.getScope() == Scope.ITEM && normalize(fact.getItemName()).equals(normalize(source.getItemName()))
                        && fact.matchesSignature(source)))
                .sorted(ITEM_DROP_CONTEXT_FIRST)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public List<AccessRequirement> itemAccessFor(DropSource source)
    {
        loadOnce();
        List<AccessRequirement> candidates = accessByItem.get(source.getItemName());
        return candidates == null ? Collections.emptyList() : candidates.stream().filter(fact -> fact.matchesSignature(source))
                .collect(Collectors.toList());
    }

    public List<AccessRequirement> forLocation(MonsterLocation location)
    {
        return facts(location.getMonsterName()).stream().filter(fact -> fact.getScope() == Scope.LOCATION
                && fact.getLocationId().equals(location.getId()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    private List<AccessRequirement> facts(String monsterName)
    {
        loadOnce();
        return byMonster.getOrDefault(normalize(monsterName), Collections.emptyList());
    }

    private synchronized void loadOnce()
    {
        if (loaded)
        {
            return;
        }
        // First use is on the Swing sidebar, never on the game client thread.
        Set<String> ids = new HashSet<>();
        loadResource("/access_requirements.json",ids);
        loadResource("/reviewed_slayer_access.json",ids);
        loadResource("/shark_bounty_access.json",ids);
        loadResource("/ocean_bounty_access.json",ids,true);
        loadResource(MonsterLocationLookupService.ITEM_ACCESS_RESOURCE,ids,true);
        Map<String,AccessRequirement> gates = ReviewedRows.readDatedFacts(gson, "/lair_access.json",
                "locationId", this::readLairGate);
        addReviewedFacts(gates, ids);
        addReviewedFacts(ReviewedRows.readDatedFacts(gson, "/group_reward_access.json", "id", this::readGroupReward), ids);
        addReviewedFacts(ReviewedRows.readDatedFacts(gson, "/sentinel_spawn_context.json", "id", this::readSentinelSpawn), ids);
        addReviewedFacts(ReviewedRows.readDatedFacts(gson, "/sentinel_regular_rolls.json", "id", this::readSentinelRoll), ids);
        addReviewedFacts(ReviewedRows.readDatedFacts(gson, "/superior_regular_rolls.json", "id", this::readSuperiorRoll), ids);
        addReviewedFacts(ReviewedRows.readDatedFacts(gson, "/brittle_key_conditions.json", "id", this::readBrittleKeyCondition), ids);
        addReviewedFacts(ReviewedRows.readDatedFacts(gson, "/crystal_shard_conditions.json", "id", this::readCrystalShardCondition), ids);
        addReviewedFacts(ReviewedRows.readDatedFacts(gson, "/elite_clue_conditions.json", "id", this::readEliteClueCondition), ids);
        loaded = true;
    }

    private void addReviewedFacts(Map<String,AccessRequirement> facts, Set<String> ids)
    {
        for (AccessRequirement fact : facts.values())
            if (ids.add(fact.getId()))
                byMonster.computeIfAbsent(normalize(fact.getMonsterName()), unused -> new ArrayList<>()).add(fact);
    }

    private AccessRequirement readBoundItemDropContext(JsonObject row)
    {
        fields(row, "id,monsterName,itemName,scope,appliesTo,type,dropSignature,text,evidence", "");
        fields(object(row.get("dropSignature")), "quantity,dropRate,notes", "");
        AccessRequirement fact = gson.fromJson(row, AccessRequirement.class);
        require(valid(fact) && fact.isBoundItemDropContext() && fact.getEvidence().size() == 1,
                "Unreviewed item-drop context");
        return fact;
    }

    private AccessRequirement readEliteClueCondition(JsonObject row, String id)
    {
        AccessRequirement fact = readBoundItemDropContext(row);
        List<String> expected = ELITE_CLUE_CONDITIONS.get(id);
        require(expected != null && expected.equals(List.of(fact.getMonsterName(), fact.getItemName(), fact.getText())),
                "Unreviewed elite clue reward");
        DropSource drop = fact.boundDrop();
        require("1".equals(drop.getQuantity()) && "Always".equals(drop.getDropRate())
                && "".equals(drop.getNotes()) && drops.hasUniqueRecordedDrop(drop),
                "Unmatched elite clue reward signature");
        long revision = ELITE_CLUE_REVISIONS.get(fact.getMonsterName());
        AccessRequirement.Evidence proof = fact.getEvidence().get(0);
        require(proof.getPage().equals(fact.getMonsterName()) && proof.getRevisionId() == revision
                && proof.getSourceUrl().equals("https://oldschool.runescape.wiki/w/"
                        + fact.getMonsterName().replace(' ', '_') + "?oldid=" + revision),
                "Changed elite clue primary");
        return fact;
    }

    private AccessRequirement readCrystalShardCondition(JsonObject row, String id)
    {
        AccessRequirement fact = readBoundItemDropContext(row);
        require(CRYSTAL_SHARD_REVISIONS.containsKey(fact.getMonsterName()), "Unreviewed Crystal shard source");
        boolean guaranteed = GUARANTEED_CRYSTAL_SHARD_SOURCES.contains(fact.getMonsterName());
        DropSource drop = fact.boundDrop();
        require(id.equals("crystal-shard-" + fact.getMonsterName().toLowerCase(Locale.ROOT).replace(' ', '-'))
                && "Crystal shard".equals(drop.getItemName())
                && (guaranteed ? "6-10" : "3-5").equals(drop.getQuantity())
                && (guaranteed ? "Always" : "1/24").equals(drop.getDropRate())
                && "".equals(drop.getNotes()) && drops.hasUniqueRecordedDrop(drop),
                "Unmatched Crystal shard signature");
        long revision = CRYSTAL_SHARD_REVISIONS.get(fact.getMonsterName());
        AccessRequirement.Evidence proof = fact.getEvidence().get(0);
        require(proof.getPage().equals(fact.getMonsterName()) && proof.getRevisionId() == revision
                && proof.getSourceUrl().equals("https://oldschool.runescape.wiki/w/"
                        + fact.getMonsterName().replace(' ', '_') + "?oldid=" + revision),
                "Changed Crystal shard primary");
        String expectedText = fact.getMonsterName().equals("Dark beast")
                ? "Only drops while in Prifddinas or Iorwerth Dungeon." : "Only drops in Iorwerth Dungeon.";
        require(expectedText.equals(fact.getText()), "Changed Crystal shard condition");
        return fact;
    }

    private AccessRequirement readBrittleKeyCondition(JsonObject row, String id)
    {
        AccessRequirement fact = readBoundItemDropContext(row);
        require(BRITTLE_KEY_REVISIONS.containsKey(fact.getMonsterName()), "Unreviewed Brittle key condition");
        DropSource drop = fact.boundDrop();
        require(id.equals("brittle-key-" + fact.getMonsterName().toLowerCase(Locale.ROOT).replace(' ', '-'))
                && "Brittle key".equals(drop.getItemName()) && "1".equals(drop.getQuantity())
                && "1/150".equals(drop.getDropRate()) && "".equals(drop.getNotes())
                && drops.hasUniqueRecordedDrop(drop), "Unmatched Brittle key signature");
        long revision = BRITTLE_KEY_REVISIONS.get(fact.getMonsterName());
        AccessRequirement.Evidence proof = fact.getEvidence().get(0);
        require(proof.getPage().equals(fact.getMonsterName()) && proof.getRevisionId() == revision
                && proof.getSourceUrl().equals("https://oldschool.runescape.wiki/w/"
                        + fact.getMonsterName().replace(' ', '_') + "?oldid=" + revision),
                "Changed Brittle key primary");
        require(fact.getText().equals("Requires a Gargoyles Slayer task, Slayer Tower roof access still locked, "
                + "and no Brittle key in your bank or inventory."), "Changed Brittle key conditions");
        return fact;
    }

    private AccessRequirement readRegularRoll(JsonObject row)
    {
        fields(row, "id,monsterName,itemName,scope,appliesTo,type,perRoll,dropSignature,text,evidence", "");
        fields(object(row.get("dropSignature")), "quantity,dropRate,notes", "");
        require(row.get("perRoll").isJsonPrimitive() && row.getAsJsonPrimitive("perRoll").isBoolean()
                && row.get("perRoll").getAsBoolean(), "Missing per-roll basis");
        AccessRequirement fact = gson.fromJson(row, AccessRequirement.class);
        require(valid(fact) && fact.isPerRollContext() && fact.getEvidence().size() == 1,
                "Unreviewed regular-roll context");
        return fact;
    }

    private AccessRequirement readSuperiorRoll(JsonObject row, String id)
    {
        AccessRequirement fact = readRegularRoll(row);
        Map<String, List<String>> reviewed = SUPERIOR_REGULAR_ROWS.get(fact.getMonsterName());
        require(reviewed != null && reviewed.containsKey(id), "Unreviewed superior regular row");
        List<String> expected = reviewed.get(id);
        DropSource drop = fact.boundDrop();
        require(expected.equals(List.of(drop.getItemName(), drop.getQuantity(), drop.getDropRate()))
                && "".equals(drop.getNotes()) && drops.hasUniqueRecordedDrop(drop),
                "Unmatched superior regular-roll signature");
        long revision = SUPERIOR_ROLL_REVISIONS.get(fact.getMonsterName());
        AccessRequirement.Evidence proof = fact.getEvidence().get(0);
        require(proof.getPage().equals(fact.getMonsterName()) && proof.getRevisionId() == revision
                && proof.getSourceUrl().equals("https://oldschool.runescape.wiki/w/"
                        + fact.getMonsterName().replace(' ', '_') + "?oldid=" + revision),
                "Changed superior roll primary");
        require(fact.getText().equals("Three additional item rolls per kill, with at most one from the superior unique table. "
                + "Guaranteed, tertiary and pre-roll drops are separate."),
                "Changed superior roll explanation");
        return fact;
    }

    private AccessRequirement readSentinelRoll(JsonObject row, String id)
    {
        AccessRequirement fact = readRegularRoll(row);
        require(fact.getMonsterName().equals("Basilisk Sentinel"), "Unreviewed Sentinel regular row");
        DropSource drop = fact.boundDrop();
        List<String> expected = SENTINEL_REGULAR_ROWS.get(fact.getItemName());
        require(expected != null && id.equals("sentinel-regular-" + fact.getItemName().toLowerCase(Locale.ROOT).replace(' ', '-'))
                && expected.get(0).equals(drop.getQuantity()) && expected.get(1).equals(drop.getDropRate())
                && "".equals(drop.getNotes()) && drops.hasUniqueRecordedDrop(drop), "Unmatched regular-roll signature");
        AccessRequirement.Evidence proof = fact.getEvidence().get(0);
        require(proof.getPage().equals("Basilisk Sentinel") && proof.getRevisionId() == 15373808
                && proof.getSourceUrl().equals("https://oldschool.runescape.wiki/w/Basilisk_Sentinel?oldid=15373808"),
                "Changed Sentinel roll primary");
        return fact;
    }

    private AccessRequirement readSentinelSpawn(JsonObject row, String id)
    {
        fields(row, "id,monsterName,scope,appliesTo,type,text,evidence", "");
        AccessRequirement fact = gson.fromJson(row, AccessRequirement.class);
        require(valid(fact) && fact.isSpawnContext() && id.equals("basilisk-sentinel-spawn")
                && fact.getMonsterName().equals("Basilisk Sentinel") && fact.getEvidence().size() == 2,
                "Unreviewed Sentinel encounter");
        String[] pages = {"Basilisk Sentinel", "Basilisk Knight"};
        long[] revisions = {15373808, 15340714};
        for (int i = 0; i < pages.length; i++)
        {
            AccessRequirement.Evidence proof = fact.getEvidence().get(i);
            require(proof.getPage().equals(pages[i]) && proof.getRevisionId() == revisions[i]
                    && proof.getSourceUrl().equals("https://oldschool.runescape.wiki/w/"
                            + pages[i].replace(' ', '_') + "?oldid=" + revisions[i]), "Changed Sentinel primary");
        }
        return fact;
    }

    private AccessRequirement readGroupReward(JsonObject row, String id)
    {
        fields(row, "id,monsterName,itemName,scope,appliesTo,type,groupRewardRole,dropSignature,text,evidence", "");
        JsonObject signature = object(row.get("dropSignature"));
        fields(signature, "quantity,dropRate,notes", "");
        string(signature, "quantity");
        string(signature, "dropRate");
        text(signature, "notes");
        AccessRequirement fact = gson.fromJson(row, AccessRequirement.class);
        require(valid(fact) && fact.getScope() == Scope.ITEM && fact.getAppliesTo() == AccessRequirement.AppliesTo.ITEM_DROP
                && fact.getType() == AccessRequirement.Type.CONTEXT && fact.getGroupRewardRole() != null
                && fact.getEvidence().size() == 1, "Invalid group reward");
        WildernessBossLair boss = WildernessBossLair.forMonster(fact.getMonsterName());
        AccessRequirement.Evidence proof = fact.getEvidence().get(0);
        require(boss != WildernessBossLair.ARTIO && proof.getPage().equals(boss.monster) && proof.getRevisionId() == boss.revision
                && proof.getSourceUrl().equals("https://oldschool.runescape.wiki/w/" + boss.monster + "?oldid=" + boss.revision),
                "Unreviewed group boss primary");
        AccessRequirement.GroupRewardRole role = fact.getGroupRewardRole();
        boolean unique = role == AccessRequirement.GroupRewardRole.PER_KILL_UNIQUE;
        String item = role == AccessRequirement.GroupRewardRole.TOP_DAMAGE ? "Big bones" : "Bones";
        String rate = "Always";
        String infix = "";
        if (unique)
        {
            item = boss.item;
            rate = "1/360";
            if ("Dragon pickaxe".equals(fact.getItemName()))
            {
                item = "Dragon pickaxe";
                rate = "1/256";
                infix = "pickaxe-";
            }
        }
        DropSource drop = fact.boundDrop();
        require(id.equals("group-" + boss.name().toLowerCase(Locale.ROOT) + "-" + infix + role.name().toLowerCase(Locale.ROOT))
                && item.equals(fact.getItemName()) && "1".equals(drop.getQuantity()) && "".equals(drop.getNotes())
                && rate.equals(drop.getDropRate()) && drops.hasUniqueRecordedDrop(drop),
                "Unmatched group reward signature");
        return fact;
    }

    private AccessRequirement readLairGate(JsonObject row, String id)
    {
        fields(row, "id,monsterName,scope,locationId,appliesTo,type,text,evidence", "");
        AccessRequirement gate = gson.fromJson(row, AccessRequirement.class);
        require(valid(gate) && gate.getScope() == Scope.LOCATION && gate.getAppliesTo() == AccessRequirement.AppliesTo.ACCESS
                && gate.getType() == AccessRequirement.Type.CONTEXT && gate.getId().equals("lair-entry-" + id)
                && gate.getEvidence().size() == 1, "Unreviewed entrance");
        AccessRequirement.Evidence proof = gate.getEvidence().get(0);
        List<MonsterLocation> places = locations.lookupByMonsterName(gate.getMonsterName());
        WildernessBossLair lair = WildernessBossLair.forMonster(gate.getMonsterName());
        require(lair.matchesLocation(places) && id.equals(lair.id) && proof.getPage().equals(lair.monster)
                && proof.getRevisionId() == lair.revision
                && proof.getSourceUrl().equals(places.get(0).getSourceUrl() + "?oldid=" + lair.revision),
                "Lair or primary revision changed");
        return gate;
    }

    private void loadResource(String path, Set<String> allIds)
    {
        loadResource(path, allIds, false);
    }

    private void loadResource(String path, Set<String> allIds, boolean requireItemSignatures)
    {
        try (InputStream input = getClass().getResourceAsStream(path))
        {
            if (input == null)
            {
                return;
            }
            JsonObject raw = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
            if (requireItemSignatures)
            {
                for (JsonElement element : array(raw, "facts"))
                {
                    JsonObject row = object(element);
                    if ("ITEM".equals(string(row, "scope")))
                    {
                        JsonObject signature = object(row.get("dropSignature"));
                        boolean access = "ACCESS".equals(string(row, "appliesTo"));
                        fields(signature, access ? "quantity,dropRate,notes,verifiedLocationId,dropVersion" : "quantity,dropRate,notes", "");
                        if (access)
                        {
                            string(signature, "verifiedLocationId");
                            string(signature, "dropVersion");
                        }
                        string(signature, "quantity");
                        string(signature, "dropRate");
                        text(signature, "notes");
                    }
                }
            }
            Dataset data = gson.fromJson(raw, Dataset.class);
            if (data == null || data.schemaVersion != 1 || data.facts == null || !hasText(data.checkedAt))
            {
                return;
            }
            Set<String> ids = new HashSet<>();
            for (AccessRequirement fact : data.facts)
            {
                if (!valid(fact) || fact.getGroupRewardRole() != null || allIds.contains(fact.getId()) || !ids.add(fact.getId()))
                {
                    throw new IllegalArgumentException("Invalid bundled access fact");
                }
            }
            allIds.addAll(ids);
            for (AccessRequirement fact : data.facts)
            {
                DropSource bound = fact.boundDrop();
                if (bound != null && (!drops.hasUniqueRecordedDrop(bound) || (fact.isItemAccess()
                        && !locations.hasUniqueVerifiedBinding(bound, fact.getBoundLocationId(), fact.getBoundVersion()))))
                {
                    log.debug("Withholding unmatched access fact {}", fact.getId());
                    continue;
                }
                byMonster.computeIfAbsent(normalize(fact.getMonsterName()), key -> new ArrayList<>()).add(fact);
                if (fact.isItemAccess()) accessByItem.computeIfAbsent(fact.getItemName(), key -> new ArrayList<>()).add(fact);
            }
        }
        catch (Exception ex)
        {
            log.debug("Unable to load bundled access requirements {}", path, ex);
        }
    }

    private static boolean valid(AccessRequirement fact)
    {
        if (fact == null || !hasText(fact.getId()) || !hasText(fact.getMonsterName()) || !hasText(fact.getText())
                || fact.getScope() == null || fact.getAppliesTo() == null || fact.getType() == null)
        {
            return false;
        }
        if (hasText(fact.getLocationId()) != (fact.getScope() == Scope.LOCATION)
                || hasText(fact.getItemName()) != (fact.getScope() == Scope.ITEM))
        {
            return false;
        }
        DropSource bound = fact.boundDrop();
        if (bound != null && (fact.getScope() != Scope.ITEM || !hasText(bound.getQuantity())
                || !hasText(bound.getDropRate()) || bound.getNotes() == null)) return false;
        if (fact.isItemAccess() != (fact.getType() == AccessRequirement.Type.UNLOCK)) return false;
        if (fact.isItemAccess() && (bound == null || !hasText(fact.getBoundLocationId()) || !hasText(fact.getBoundVersion()))) return false;
        if (!fact.isItemAccess() && (fact.getBoundLocationId() != null || fact.getBoundVersion() != null)) return false;
        if (fact.getType() == AccessRequirement.Type.SKILL
                && (!hasText(fact.getSkill()) || fact.getLevel() == null || fact.getLevel() < 1 || fact.getLevel() > 99))
        {
            return false;
        }
        if (fact.getAppliesTo() == AccessRequirement.AppliesTo.PROTECTION && (fact.getScope() != Scope.MONSTER
                || (fact.getType() != AccessRequirement.Type.EQUIPMENT && fact.getType() != AccessRequirement.Type.CONTEXT))) return false;
        if (fact.getAppliesTo() == AccessRequirement.AppliesTo.FINISHING
                && (fact.getScope() != Scope.MONSTER || fact.getType() != AccessRequirement.Type.EQUIPMENT)) return false;
        boolean itemRoll = fact.getScope() == Scope.ITEM && fact.getAppliesTo() == AccessRequirement.AppliesTo.DROP_CONTEXT
                && fact.getType() == AccessRequirement.Type.CONTEXT && bound != null && fact.isPerRollContext();
        if (fact.hasPerRollMarker() && !itemRoll) return false;
        if (fact.getAppliesTo() == AccessRequirement.AppliesTo.DROP_CONTEXT
                && !(itemRoll || (fact.getScope() == Scope.MONSTER && fact.getType() == AccessRequirement.Type.CONTEXT))) return false;
        return !fact.getEvidence().isEmpty() && fact.getEvidence().stream().allMatch(evidence -> evidence != null
                && hasText(evidence.getPage()) && evidence.getRevisionId() > 0
                && evidence.getSourceUrl() != null
                && evidence.getSourceUrl().startsWith("https://oldschool.runescape.wiki/w/")
                && evidence.getSourceUrl().endsWith("?oldid=" + evidence.getRevisionId()));
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.replace('_', ' ').replace('\u2019', '\'')
                .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static class Dataset
    {
        private int schemaVersion;
        private String checkedAt;
        private List<AccessRequirement> facts;
    }
}
