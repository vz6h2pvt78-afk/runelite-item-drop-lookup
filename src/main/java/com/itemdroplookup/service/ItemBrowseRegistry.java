package com.itemdroplookup.service;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reviewed browse terms and exact members, separate from identity aliases and source facts. */
public final class ItemBrowseRegistry
{
    private final Map<String, Group> byTerm;

    ItemBrowseRegistry(Gson gson)
    {
        Map<String, Group> terms = new HashMap<>();
        try (InputStream stream = getClass().getResourceAsStream("/item_browse_groups.json"))
        {
            if (stream != null)
            {
                Data data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
                if (data == null || data.schemaVersion != 1 || data.groups == null) throw new IllegalArgumentException();
                Set<String> ids = new HashSet<>();
                for (Group group : data.groups)
                {
                    if (group == null || group.id == null || !ids.add(group.id)
                            || group.terms == null || group.terms.isEmpty()
                            || group.members == null || group.members.isEmpty()) throw new IllegalArgumentException();
                    Set<String> members = new HashSet<>();
                    for (String member : group.members)
                        if (member == null || member.trim().isEmpty()
                                || !members.add(ItemNameMatching.normalize(member))) throw new IllegalArgumentException();
                    group.members = List.copyOf(group.members);
                    for (String term : group.terms)
                    {
                        if (term == null || term.trim().isEmpty()) throw new IllegalArgumentException();
                        String key = ItemNameMatching.normalize(term);
                        if (ItemNameMatching.reviewedAliases().containsKey(key) || terms.putIfAbsent(key, group) != null)
                            throw new IllegalArgumentException();
                    }
                }
            }
        }
        catch (Exception ex)
        {
            // Optional browse metadata must never break ordinary exact-item lookup.
            terms.clear();
        }
        byTerm = Collections.unmodifiableMap(terms);
    }

    Optional<Group> find(String query)
    {
        return query == null ? Optional.empty() : Optional.ofNullable(byTerm.get(ItemNameMatching.normalize(query)));
    }

    public static final class Group
    {
        private String id;
        private List<String> terms;
        private List<String> members;
        private boolean overridesCanonical;

        public List<String> getMembers() { return members; }
        public boolean overridesCanonical() { return overridesCanonical; }
    }

    private static final class Data
    {
        private int schemaVersion;
        private List<Group> groups;
    }
}
