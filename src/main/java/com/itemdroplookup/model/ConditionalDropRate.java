package com.itemdroplookup.model;

/** One reviewed alternative rate for an exact drop, conditional on a named Slayer task. */
public final class ConditionalDropRate
{
    private final String monster;
    private final String item;
    private final String quantity;
    private final String baseRate;
    private final String notes;
    private final String taskName;
    private final String taskRate;

    public ConditionalDropRate(String monster, String item, String quantity, String baseRate, String notes, String taskName, String taskRate)
    {
        this.monster=monster;
        this.item=item;
        this.quantity=quantity;
        this.baseRate=baseRate;
        this.notes=notes;
        this.taskName=taskName;
        this.taskRate=taskRate;
    }

    public boolean matches(String monster, String item, String quantity, String baseRate, String notes)
    {
        return this.monster.equals(monster) && this.item.equals(item) && this.quantity.equals(quantity)
                && this.baseRate.equals(baseRate) && this.notes.equals(notes);
    }

    public String getTaskName() { return taskName; }
    public String getTaskRate() { return taskRate; }
}
