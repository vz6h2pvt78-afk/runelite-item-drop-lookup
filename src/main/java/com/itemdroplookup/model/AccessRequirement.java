package com.itemdroplookup.model;

import java.util.Collections;
import java.util.List;

/** A sourced fact about one exact acquisition context, not an eligibility verdict. */
public final class AccessRequirement
{
    public enum Scope { MONSTER, LOCATION, ITEM }
    public enum AppliesTo { PROTECTION, FINISHING, COMBAT, ACCESS, TASK_ELIGIBILITY, ITEM_DROP, ENCOUNTER_CONTEXT, ACTIVITY_CONTEXT, DROP_CONTEXT }
    public enum Type { SKILL, ACTIVE_TASK, QUEST, EQUIPMENT, CONTEXT, UNLOCK }

    private String id;
    private String monsterName;
    private Scope scope;
    private String locationId;
    private String itemName;
    private RawSignature dropSignature;
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

    public DropSource boundDrop()
    {
        return dropSignature == null ? null : new DropSource(itemName, monsterName, dropSignature.quantity, dropSignature.dropRate, dropSignature.notes);
    }

    public boolean matchesSignature(DropSource source)
    {
        return dropSignature == null || (monsterName.equals(source.getMonsterName()) && itemName.equals(source.getItemName())
                && dropSignature.quantity.equals(source.getQuantity()) && dropSignature.dropRate.equals(source.getDropRate())
                && dropSignature.notes.equals(source.getNotes()));
    }

    public boolean isItemAccess() { return scope == Scope.ITEM && appliesTo == AppliesTo.ACCESS; }
    public String getBoundLocationId() { return dropSignature == null ? null : dropSignature.verifiedLocationId; }
    public String getBoundVersion() { return dropSignature == null ? null : dropSignature.dropVersion; }

    private static final class RawSignature
    {
        private String quantity;
        private String dropRate;
        private String notes;
        private String verifiedLocationId;
        private String dropVersion;
    }

    public String getLabel()
    {
        switch (appliesTo)
        {
            case PROTECTION: return "Protection";
            case FINISHING: return "Finishing";
            case COMBAT:
                if (scope == Scope.LOCATION) return "Location restriction";
                return type == Type.CONTEXT ? "Combat" : "Required";
            case ACCESS: return isItemAccess() ? "Access" : "Location access";
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
                || (scope == Scope.ITEM && appliesTo == AppliesTo.ITEM_DROP) || isItemAccess();
    }

    public boolean isSourceCardFact()
    {
        return isSourceCardRequirement()
                || (scope == Scope.MONSTER && (appliesTo == AppliesTo.PROTECTION || appliesTo == AppliesTo.FINISHING))
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
