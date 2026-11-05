package com.itemdroplookup.model;

public class DropSource
{
    private final String itemName;
    private final transient AnimatorReward animatorReward;
    private final transient ConditionalDropRate conditionalRate;
    private final String monsterName;
    private final String quantity;
    private final String dropRate;
    private final String notes;
    private final Boolean membersOnly;

    public DropSource(String itemName, String monsterName, String quantity, String dropRate, String notes)
    {
        this(itemName, monsterName, quantity, dropRate, notes, null);
    }

    public DropSource(String itemName, String monsterName, String quantity, String dropRate, String notes, Boolean membersOnly)
    {
        this(itemName, monsterName, quantity, dropRate, notes, membersOnly, null);
    }

    public DropSource(String itemName, String monsterName, String quantity, String dropRate, String notes,
            Boolean membersOnly, AnimatorReward animatorReward)
    {
        this(itemName, monsterName, quantity, dropRate, notes, membersOnly, animatorReward, null);
    }

    public DropSource(String itemName, String monsterName, String quantity, String dropRate, String notes,
            Boolean membersOnly, AnimatorReward animatorReward, ConditionalDropRate conditionalRate)
    {
        this.conditionalRate = conditionalRate;
        this.animatorReward = animatorReward;
        this.itemName = itemName;
        this.monsterName = monsterName;
        this.quantity = quantity;
        this.dropRate = dropRate;
        this.notes = notes;
        this.membersOnly = membersOnly;
    }

    public ConditionalDropRate getConditionalRate() { return conditionalRate; }
    public AnimatorReward getAnimatorReward() { return animatorReward; }

    public String getItemName()
    {
        return itemName;
    }

    public String getMonsterName()
    {
        return monsterName;
    }

    public String getQuantity()
    {
        return quantity;
    }

    public String getDropRate()
    {
        return dropRate;
    }

    public String getNotes()
    {
        return notes;
    }

    /** True: verified members-only; false: verified F2P; null: not recorded. */
    public Boolean getMembersOnly()
    {
        return membersOnly;
    }
}
