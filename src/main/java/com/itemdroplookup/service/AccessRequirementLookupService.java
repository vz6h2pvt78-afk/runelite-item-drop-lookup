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
        loaded = true;
    }

    private void addReviewedFacts(Map<String,AccessRequirement> facts, Set<String> ids)
    {
        for (AccessRequirement fact : facts.values())
            if (ids.add(fact.getId()))
                byMonster.computeIfAbsent(normalize(fact.getMonsterName()), unused -> new ArrayList<>()).add(fact);
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
        if (fact.getAppliesTo() == AccessRequirement.AppliesTo.DROP_CONTEXT
                && (fact.getScope() != Scope.MONSTER || fact.getType() != AccessRequirement.Type.CONTEXT)) return false;
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
