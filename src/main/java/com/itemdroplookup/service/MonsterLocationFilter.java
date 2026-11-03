package com.itemdroplookup.service;

import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.model.DropSource;

/** The same location predicates are used in details and item results. */
public final class MonsterLocationFilter
{
    public enum WildernessFilter
    {
        ALL("All locations"), ONLY("Wilderness only"), EXCLUDE("Exclude Wilderness");

        private final String label;
        WildernessFilter(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public enum MembershipFilter
    {
        ALL("Any membership"), F2P("F2P locations"), MEMBERS("Members locations");

        private final String label;
        MembershipFilter(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private MonsterLocationFilter() { }

    public static boolean matchesSource(DropSource source, MonsterLocation location,
                                        WildernessFilter wilderness, MembershipFilter membership)
    {
        if (Boolean.TRUE.equals(source.getMembersOnly()))
        {
            if (membership == MembershipFilter.F2P)
            {
                return false;
            }
            if (membership == MembershipFilter.MEMBERS)
            {
                // An F2P-accessible area also exists on members worlds. Unknown
                // geography remains excluded by a specific membership filter.
                return location.getMembers() != null && matches(location, wilderness, MembershipFilter.ALL);
            }
        }
        return matches(location, wilderness, membership);
    }

    public static boolean matches(MonsterLocation location, WildernessFilter wilderness,
                                  MembershipFilter membership)
    {
        if (wilderness == WildernessFilter.ONLY && location.getWilderness() != MonsterLocation.Wilderness.YES)
        {
            return false;
        }
        if (wilderness == WildernessFilter.EXCLUDE && location.getWilderness() != MonsterLocation.Wilderness.NO)
        {
            return false;
        }
        if (membership == MembershipFilter.F2P && !Boolean.FALSE.equals(location.getMembers()))
        {
            return false;
        }
        if (membership == MembershipFilter.MEMBERS && !Boolean.TRUE.equals(location.getMembers()))
        {
            return false;
        }
        return true;
    }
}
