package com.itemdroplookup.ui;

/** Small navigation state; live controls and retained source tree own their input/scroll state. */
final class LookupViewState
{
    enum Page { WELCOME, ITEMS, OVERVIEW, SOURCES, NO_SOURCES }
    Page page = Page.WELCOME;
    String selectedItem = "";
    String query = "";
    int overviewScroll;
    ItemSearchSelection selection;
    java.util.Map<com.itemdroplookup.model.DropSource, MonsterLocationPanel.State> detailStates = new java.util.IdentityHashMap<>();

    void beginSelection(String query, ItemSearchSelection selection)
    {
        this.query = query;
        this.selection = selection;
        // DropSource identity belongs to this particular result set.
        detailStates = new java.util.IdentityHashMap<>();
    }

    LookupViewState copy()
    {
        LookupViewState saved = new LookupViewState();
        saved.page = page;
        saved.selectedItem = selectedItem;
        saved.query = query;
        saved.overviewScroll = overviewScroll;
        saved.selection = selection;
        saved.detailStates = detailStates;
        return saved;
    }
}
