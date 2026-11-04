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

    LookupViewState copy()
    {
        LookupViewState saved = new LookupViewState();
        saved.page = page;
        saved.selectedItem = selectedItem;
        saved.query = query;
        saved.overviewScroll = overviewScroll;
        saved.selection = selection;
        return saved;
    }
}
