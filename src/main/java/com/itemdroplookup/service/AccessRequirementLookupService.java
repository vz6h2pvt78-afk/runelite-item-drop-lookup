package com.itemdroplookup.service;

import com.google.gson.Gson;
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
    private final Map<String, List<AccessRequirement>> byMonster = new HashMap<>();
    private boolean loaded;

    @Inject
    public AccessRequirementLookupService(Gson gson)
    {
        this.gson = gson;
    }

    public List<AccessRequirement> forSource(DropSource source)
    {
        return facts(source.getMonsterName()).stream().filter(fact -> fact.getScope() == Scope.MONSTER
                || (fact.getScope() == Scope.ITEM && normalize(fact.getItemName()).equals(normalize(source.getItemName()))))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
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
        loaded = true;
    }

    private void loadResource(String path, Set<String> allIds)
    {
        try (InputStream input = getClass().getResourceAsStream(path))
        {
            if (input == null)
            {
                return;
            }
            Dataset data = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Dataset.class);
            if (data == null || data.schemaVersion != 1 || data.facts == null || !hasText(data.checkedAt))
            {
                return;
            }
            Set<String> ids = new HashSet<>();
            for (AccessRequirement fact : data.facts)
            {
                if (!valid(fact) || allIds.contains(fact.getId()) || !ids.add(fact.getId()))
                {
                    throw new IllegalArgumentException("Invalid bundled access fact");
                }
            }
            allIds.addAll(ids);
            for (AccessRequirement fact : data.facts)
            {
                byMonster.computeIfAbsent(normalize(fact.getMonsterName()), key -> new ArrayList<>()).add(fact);
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
