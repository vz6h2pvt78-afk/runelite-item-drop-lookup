package com.itemdroplookup.ui;

/** Small navigation state; live controls and retained source tree own their input/scroll state. */
final class LookupViewState
{
    enum Page { WELCOME, ITEMS, OVERVIEW, SOURCES }
    Page page = Page.WELCOME;
    String selectedItem = "";
    String query = "";
    int overviewScroll;
    ItemSearchSelection selection;
}
