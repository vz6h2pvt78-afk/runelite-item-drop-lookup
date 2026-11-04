package com.itemdroplookup.model;

import java.util.Collections;
import java.util.List;

/** A sourced fact about one exact acquisition context, not an eligibility verdict. */
public final class AccessRequirement
{
    public enum Scope { MONSTER, LOCATION, ITEM }
    public enum AppliesTo { COMBAT, ACCESS, TASK_ELIGIBILITY, ITEM_DROP, ENCOUNTER_CONTEXT, ACTIVITY_CONTEXT, DROP_CONTEXT }
    public enum Type { SKILL, ACTIVE_TASK, QUEST, EQUIPMENT, CONTEXT }

    private String id;
    private String monsterName;
    private Scope scope;
    private String locationId;
    private String itemName;
    private AppliesTo appliesTo;
    private Type type;
    private String text;
    private String skill;
    private Integer level;
    private List<Evidence> evidence;

    public String getId() { return id; }
    public String getMonsterName() { return monsterName; }
    public Scope getScope() { return scope; }
    public String getLocationId() { return locationId; }
    public String getItemName() { return itemName; }
    public AppliesTo getAppliesTo() { return appliesTo; }
    public Type getType() { return type; }
    public String getText() { return text; }
    public String getSkill() { return skill; }
    public Integer getLevel() { return level; }
    public List<Evidence> getEvidence()
    {
        return evidence == null ? Collections.emptyList() : Collections.unmodifiableList(evidence);
    }

    public String getLabel()
    {
        switch (appliesTo)
        {
            case COMBAT: return scope == Scope.LOCATION ? "Location restriction" : "Required";
            case ACCESS: return "Location access";
            case TASK_ELIGIBILITY: return "Task eligibility";
            case ITEM_DROP: return "For this drop";
            case ENCOUNTER_CONTEXT: return "Encounter context";
            case ACTIVITY_CONTEXT: return "Activity context";
            case DROP_CONTEXT: return "Drop context";
            default: return "Context";
        }
    }

    public boolean isSourceCardRequirement()
    {
        return (scope == Scope.MONSTER && appliesTo == AppliesTo.COMBAT)
                || (scope == Scope.ITEM && appliesTo == AppliesTo.ITEM_DROP);
    }

    public boolean isSourceCardFact()
    {
        return isSourceCardRequirement()
                || (scope == Scope.MONSTER && appliesTo == AppliesTo.DROP_CONTEXT && type == Type.CONTEXT);
    }

    public static final class Evidence
    {
        private String page;
        private long revisionId;
        private String sourceUrl;

        public String getPage() { return page; }
        public long getRevisionId() { return revisionId; }
        public String getSourceUrl() { return sourceUrl; }
    }
}
