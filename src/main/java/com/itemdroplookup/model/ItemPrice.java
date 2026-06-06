package com.itemdroplookup.model;

public class ItemPrice
{
    private final String itemName;
    private final String price;
    private final String highAlch;
    private final boolean tradeable;
    private final String notes;

    public ItemPrice(String itemName, String price, String highAlch, boolean tradeable, String notes)
    {
        this.itemName = itemName;
        this.price = price;
        this.highAlch = highAlch;
        this.tradeable = tradeable;
        this.notes = notes;
    }

    public String getItemName()
    {
        return itemName;
    }

    public String getPrice()
    {
        return price;
    }

    public String getHighAlch()
    {
        return highAlch;
    }

    public boolean isTradeable()
    {
        return tradeable;
    }

    public String getNotes()
    {
        return notes;
    }
}