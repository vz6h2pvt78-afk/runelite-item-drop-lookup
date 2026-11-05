package com.itemdroplookup.model;

/** One stock row selling several of the canonical item in a single transaction. */
public final class BulkPurchase
{
    private final String itemName;
    private final String stockLabel;
    private final int quantity;

    public BulkPurchase(String itemName, String stockLabel, int quantity)
    {
        this.itemName = itemName;
        this.stockLabel = stockLabel;
        this.quantity = quantity;
    }

    public String getItemName() { return itemName; }
    public String getStockLabel() { return stockLabel; }
    public int getQuantity() { return quantity; }
}
