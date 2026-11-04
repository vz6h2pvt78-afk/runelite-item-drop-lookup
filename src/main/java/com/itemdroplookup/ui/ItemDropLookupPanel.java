package com.itemdroplookup.ui;

import com.itemdroplookup.model.AcquisitionMethod;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.DropSourceGroup;
import com.itemdroplookup.model.ItemPrice;
import com.itemdroplookup.model.ItemSource;
import com.itemdroplookup.model.AccessRequirement;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.ItemIconProvider;
import com.itemdroplookup.service.MonsterFamilyGrouper;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.runelite.client.util.LinkBrowser;

public class ItemDropLookupPanel extends PluginPanel
{
    private static final String FILTER_ALL = "All";
    private static final String FILTER_DROPS = "NPC Drops";
    private static final String FILTER_SOURCES = "Non-NPC Sources";
    private static final String FILTER_SHOPS = "Shops";
    private static final String FILTER_REWARD_SHOPS = "Reward Shops";
    private static final String FILTER_GROUND = "Ground pickups";
    private static final String FILTER_GATHERING = "Gathering";
    private static final String FILTER_LOOT = "Loot & containers";

    // Exact sourceType values matched by the Shops / Reward Shops filters. These
    // must match the data verbatim — no fuzzy matching.
    private static final String SOURCE_TYPE_SHOP = "Shop";
    private static final String SOURCE_TYPE_REWARD_SHOP = "Reward Shop";
    private static final String SOURCE_TYPE_GROUND = "Ground pickup";
    private static final String SOURCE_TYPE_GATHERING = "Gathering";
    private static final String SOURCE_TYPE_LOOT = "Loot & containers";

    // Swing HTML labels need an explicit pixel width to wrap, and that width also gives
    // a deterministic preferred height inside BoxLayout (unlike a JTextArea, whose wrap
    // height fights the layout). We derive the wrap width from the real panel width so
    // long Source/Cost/Notes lines wrap instead of clipping. The scrollbar allowance
    // matters: results usually scroll, and the vertical scrollbar shrinks the usable
    // width — a fixed 180px div clipped once the scrollbar appeared. Without a scrollbar
    // there is simply a little extra right margin.
    private static final int SCROLLBAR_ALLOWANCE = 45;
    private static final int PANEL_INSETS = 20;   // panel EmptyBorder(10,10,10,10), left+right
    private static final int CARD_INSETS = 16;    // card EmptyBorder(.,8,.,8), left+right
    private static final int NESTED_INDENT = 10;  // grouped members' left rail + indent

    // Text added directly to the results panel.
    private static final int PANEL_TEXT_WIDTH = PluginPanel.PANEL_WIDTH - SCROLLBAR_ALLOWANCE - PANEL_INSETS;
    // Text inside a top-level card.
    private static final int CARD_TEXT_WIDTH = PANEL_TEXT_WIDTH - CARD_INSETS;
    // Text inside a nested (grouped) drop card, which is indented further.
    private static final int NESTED_CARD_TEXT_WIDTH = CARD_TEXT_WIDTH - NESTED_INDENT - CARD_INSETS;

    private final DropLookupService dropLookupService;
    private final ItemSourceLookupService itemSourceLookupService;
    private final ItemPriceLookupService itemPriceLookupService;
    private final MonsterLocationLookupService monsterLocationLookupService;
    private final AccessRequirementLookupService accessRequirementLookupService;
    private final java.util.function.Consumer<String> clipboardWriter;
    private final JPanel searchView = new JPanel(new BorderLayout());
    private final JComboBox<WildernessFilter> wildernessDropdown = new IntentionalComboBox<>(WildernessFilter.values());
    private int searchScrollPosition;
    private String displayedQuery = "";
    private final JScrollPane resultsScroll = new JScrollPane();
    private final ChooserIcons chooserIcons;
    private MonsterLocationPanel detail;
    private final JTextField sourceSearch = new JTextField();
    private final JPanel sourceSearchRow = new JPanel(new BorderLayout(5, 0));
    private final JPanel npcFilters = new JPanel(new BorderLayout(5, 0));
    private final Timer sourceSearchTimer = new Timer(180, e -> renderResults());
    private List<DropSource> matchingDrops = Collections.emptyList();
    private List<ItemSource> matchingSources = Collections.emptyList();
    private final List<Component> priceLabels = new ArrayList<>();

    private final JTextField searchField = new JTextField();
    private final JButton searchButton = new JButton("Search");
    private final JComboBox<String> filterDropdown = new IntentionalComboBox<>(new String[] {
            FILTER_ALL,
            FILTER_DROPS,
            FILTER_SOURCES,
            FILTER_SHOPS,
            FILTER_REWARD_SHOPS,
            FILTER_GROUND,
            FILTER_GATHERING,
            FILTER_LOOT
    });
    private final JCheckBox showPriceCheckbox = new JCheckBox("Show GE price", true);
    private final JPanel resultsPanel = LookupStyle.column();
    private LookupViewState viewState = new LookupViewState();
    private ItemHistory previousItem;
    private LookupStyle style = new LookupStyle(LookupStyle.STANDARD_SIZE);
    private final JComboBox<String> textSize = new IntentionalComboBox<>(new String[] {"Standard text", "Larger text"});
    private final JPanel controlsPanel = LookupStyle.column();
    private final JButton overviewBack = style.backButton("Back to item", this::showOverview);
    private final JButton clearSearch = style.button("Clear search", this::clearSourceSearch, false);
    private final java.util.Set<String> expandedGroups = new java.util.HashSet<>();
    private final Map<String, Integer> savedSourceScroll = new java.util.HashMap<>();
    private final Map<DropSource, MonsterLocationPanel.State> detailStates = new java.util.IdentityHashMap<>();
    private DropSource detailSource;
    private BoundDropContexts boundContexts;
    private List<DropSource> indexedDrops;
    private final Map<String, Integer> loadedSourceRows = new java.util.HashMap<>();

    public ItemDropLookupPanel(
            DropLookupService dropLookupService,
            ItemSourceLookupService itemSourceLookupService,
            ItemPriceLookupService itemPriceLookupService,
            MonsterLocationLookupService monsterLocationLookupService,
            AccessRequirementLookupService accessRequirementLookupService
    )
    {
        this(dropLookupService, itemSourceLookupService, itemPriceLookupService, monsterLocationLookupService,
                accessRequirementLookupService, text -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(text), null));
    }

    ItemDropLookupPanel(DropLookupService dropLookupService, ItemSourceLookupService itemSourceLookupService,
                        ItemPriceLookupService itemPriceLookupService, MonsterLocationLookupService monsterLocationLookupService,
                        AccessRequirementLookupService accessRequirementLookupService,
                        java.util.function.Consumer<String> clipboardWriter)
    {
        this(dropLookupService, itemSourceLookupService, itemPriceLookupService, monsterLocationLookupService,
                accessRequirementLookupService, clipboardWriter, ItemIconProvider.NONE);
    }

    public ItemDropLookupPanel(DropLookupService dropLookupService, ItemSourceLookupService itemSourceLookupService,
                              ItemPriceLookupService itemPriceLookupService, MonsterLocationLookupService monsterLocationLookupService,
                              AccessRequirementLookupService accessRequirementLookupService, ItemIconProvider iconProvider)
    {
        this(dropLookupService, itemSourceLookupService, itemPriceLookupService, monsterLocationLookupService,
                accessRequirementLookupService, text -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(text), null), iconProvider);
    }

    ItemDropLookupPanel(DropLookupService dropLookupService, ItemSourceLookupService itemSourceLookupService,
                        ItemPriceLookupService itemPriceLookupService, MonsterLocationLookupService monsterLocationLookupService,
                        AccessRequirementLookupService accessRequirementLookupService,
                        java.util.function.Consumer<String> clipboardWriter, ItemIconProvider iconProvider)
    {
        super(false);
        this.dropLookupService = dropLookupService;
        this.itemSourceLookupService = itemSourceLookupService;
        this.itemPriceLookupService = itemPriceLookupService;
        this.monsterLocationLookupService = monsterLocationLookupService;
        this.accessRequirementLookupService = accessRequirementLookupService;
        this.clipboardWriter = clipboardWriter;
        chooserIcons = new ChooserIcons(resultsScroll, iconProvider);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(LookupStyle.BACK);
        JPanel topPanel = LookupStyle.column();
        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.setAlignmentX(LEFT_ALIGNMENT);
        searchPanel.setBackground(LookupStyle.BACK);
        searchField.setToolTipText("Enter an item name and press Enter");
        searchField.getAccessibleContext().setAccessibleName("Item name");
        searchField.setName("itemSearch");
        searchButton.setName("searchButton");
        searchButton.setText("Find");
        style.control(searchField);
        style.control(searchButton);
        searchButton.setBackground(LookupStyle.ACCENT);
        searchButton.setForeground(LookupStyle.BACK);
        searchField.addActionListener(e -> performSearch());
        searchButton.addActionListener(e -> performSearch());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        topPanel.add(searchPanel);
        overviewBack.setName("backToOverview");
        style.control(overviewBack);
        overviewBack.setVisible(false);
        topPanel.add(overviewBack);

        filterDropdown.setName("sourceTypeFilter");
        style.control(filterDropdown);
        filterDropdown.getAccessibleContext().setAccessibleName("Acquisition method");
        filterDropdown.addActionListener(e -> { if (!adjustingControls) { viewState.page = LookupViewState.Page.SOURCES; renderResults(); } });
        wildernessDropdown.setName("npcWildernessFilter");
        wildernessDropdown.setToolTipText("Filter NPC drops by verified usable locations. Other methods are unchanged.");
        style.control(wildernessDropdown);
        wildernessDropdown.addActionListener(e -> { if (!adjustingControls) renderResults(); });
        npcFilters.setBackground(LookupStyle.BACK);
        npcFilters.setAlignmentX(LEFT_ALIGNMENT);
        npcFilters.add(style.text("NPC area", false), BorderLayout.NORTH);
        npcFilters.add(wildernessDropdown, BorderLayout.CENTER);
        sourceSearch.setName("sourceSearch");
        sourceSearch.getAccessibleContext().setAccessibleName("Find a source within the selected item");
        sourceSearch.setToolTipText("Monster, shop, location or pack name");
        style.control(sourceSearch);
        sourceSearchRow.setBackground(LookupStyle.BACK);
        sourceSearchRow.setAlignmentX(LEFT_ALIGNMENT);
        sourceSearchRow.add(style.text("Find source", false), BorderLayout.NORTH);
        sourceSearchRow.add(sourceSearch, BorderLayout.CENTER);
        sourceSearchTimer.setRepeats(false);
        sourceSearch.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { sourceSearchChanged(); }
            public void removeUpdate(DocumentEvent e) { sourceSearchChanged(); }
            public void changedUpdate(DocumentEvent e) { sourceSearchChanged(); }
        });
        sourceSearch.addActionListener(e -> { sourceSearchTimer.stop(); renderResults(); });
        controlsPanel.add(style.text("Method", false));
        controlsPanel.add(filterDropdown);
        controlsPanel.add(npcFilters);
        controlsPanel.add(sourceSearchRow);
        clearSearch.setName("clearSourceSearch");
        clearSearch.setToolTipText("Clear Find source text; keep the selected item, method and NPC area");
        clearSearch.setVisible(false);
        controlsPanel.add(clearSearch);

        showPriceCheckbox.setName("showPrice");
        style.control(showPriceCheckbox);
        showPriceCheckbox.setToolTipText("Bundled price snapshot, not a live GE price");
        showPriceCheckbox.addActionListener(e -> {
            int position = resultsScroll.getVerticalScrollBar().getValue();
            priceLabels.forEach(label -> label.setVisible(showPriceCheckbox.isSelected()));
            resultsPanel.revalidate();
            SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(position));
        });
        textSize.setName("textSize");
        style.control(textSize);
        textSize.addActionListener(e -> changeTextSize());
        searchView.setBackground(LookupStyle.BACK);
        searchView.add(topPanel, BorderLayout.NORTH);
        JPanel body = LookupStyle.scrollBody(resultsPanel);
        resultsScroll.setViewportView(body);
        resultsScroll.getViewport().setBackground(LookupStyle.BACK);
        resultsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        resultsScroll.setBorder(null);
        resultsScroll.getVerticalScrollBar().setUnitIncrement(28);
        searchView.add(resultsScroll, BorderLayout.CENTER);
        add(searchView, BorderLayout.CENTER);
        resultsPanel.add(makeLabel("Find an item, then choose how to get it."));
        addDisplayOptions();
    }

    @Override
    protected JScrollPane getScrollPane()
    {
        return detail == null ? resultsScroll : detail.getLocationScrollPane();
    }

    public void dispose()
    {
        chooserIcons.dispose();
        if (SwingUtilities.isEventDispatchThread()) sourceSearchTimer.stop();
        else SwingUtilities.invokeLater(sourceSearchTimer::stop);
    }

    private boolean adjustingControls;

    /** A real last-valid navigation entry; never inferred from a failed search. */
    private static final class ItemHistory
    {
        LookupViewState view;
        String method;
        WildernessFilter area;
        String finder;
        int scroll;
    }

    private void rememberItem()
    {
        if (viewState.page != LookupViewState.Page.OVERVIEW && viewState.page != LookupViewState.Page.SOURCES) return;
        ItemHistory saved = new ItemHistory();
        saved.view = viewState.copy();
        saved.method = (String) filterDropdown.getSelectedItem();
        saved.area = (WildernessFilter) wildernessDropdown.getSelectedItem();
        saved.finder = sourceSearch.getText();
        saved.scroll = resultsScroll.getVerticalScrollBar().getValue();
        previousItem = saved;
    }

    private void restorePreviousItem()
    {
        if (previousItem == null) return;
        sourceSearchTimer.stop();
        ItemHistory saved = previousItem;
        previousItem = null;
        viewState = saved.view;
        displayedQuery = viewState.query;
        adjustingControls = true;
        searchField.setText(viewState.query);
        filterDropdown.setSelectedItem(saved.method);
        wildernessDropdown.setSelectedItem(saved.area);
        sourceSearch.setText(saved.finder);
        adjustingControls = false;
        matchingDrops = viewState.selection.dropsFor(viewState.selectedItem);
        matchingSources = viewState.selection.sourcesFor(viewState.selectedItem);
        if (viewState.page == LookupViewState.Page.SOURCES) renderResults();
        else showOverview();
        SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(saved.scroll));
    }

    private void performSearch()
    {
        if (adjustingControls) return;
        sourceSearchTimer.stop();
        if (viewState.page == LookupViewState.Page.SOURCES)
            savedSourceScroll.put(sourceViewKey(), resultsScroll.getVerticalScrollBar().getValue());
        String query = searchField.getText().trim();
        rememberItem();
        if (viewState.selection == null || !query.equals(viewState.query))
        {
            viewState.query = query;
            displayedQuery = query;
            viewState.selection = new ItemSearchSelection(query, dropLookupService, itemSourceLookupService, itemPriceLookupService);
        }
        List<String> choices = viewState.selection.choices();
        if (choices.size() == 1) selectItem(choices.get(0));
        else showItemChoices();
    }

    private void selectItem(String item)
    {
        matchingDrops = viewState.selection.dropsFor(item);
        matchingSources = viewState.selection.sourcesFor(item);
        if (matchingDrops.isEmpty() && matchingSources.isEmpty())
        {
            viewState.selectedItem = item;
            showNoSources();
            return;
        }
        previousItem = null;
        if (!item.equals(viewState.selectedItem))
        {
            viewState.overviewScroll = 0;
            adjustingControls = true;
            sourceSearch.setText("");
            adjustingControls = false;
        }
        viewState.selectedItem = item;
        viewState.page = LookupViewState.Page.OVERVIEW;
        showOverview();
    }

    private void showNoSources()
    {
        chooserIcons.clear();
        viewState.page = LookupViewState.Page.NO_SOURCES;
        overviewBack.setVisible(false);
        resultsPanel.removeAll();
        priceLabels.clear();
        if (previousItem != null)
        {
            JButton back = style.backButton(previousItem.view.page == LookupViewState.Page.SOURCES
                    ? "Back to results" : "Back to item", this::restorePreviousItem);
            back.setName("backToPreviousItem");
            resultsPanel.add(back);
        }
        resultsPanel.add(makeItemHeader(viewState.selectedItem));
        resultsPanel.add(makeSectionHeader("No acquisition sources recorded"));
        resultsPanel.add(makeLabel("This item is recognized, but no acquisition sources are recorded in the current data."));
        resultsPanel.add(wikiButton(viewState.selectedItem));
        resultsPanel.add(reportControls());
        finishPage(0);
    }

    private void showItemChoices()
    {
        chooserIcons.clear();
        viewState.page = LookupViewState.Page.ITEMS;
        overviewBack.setVisible(false);
        resultsPanel.removeAll();
        priceLabels.clear();
        List<String> choices = viewState.selection.choices();
        if (previousItem != null)
        {
            JButton back = style.backButton(previousItem.view.page == LookupViewState.Page.SOURCES
                    ? "Back to results" : "Back to item", this::restorePreviousItem);
            back.setName("backToPreviousItem");
            resultsPanel.add(back);
        }
        resultsPanel.add(makeSectionHeader(choices.isEmpty() ? "No matching item" : "Choose an item"));
        resultsPanel.add(makeLabel(choices.isEmpty() ? "Try a shorter name or check the spelling." : choices.size() + " matching items"));
        for (String item : choices)
        {
            JButton choose = style.button(item, () -> selectItem(item), false);
            choose.setName("selectItem:" + item);
            chooserIcons.add(item, choose);
            resultsPanel.add(choose);
            resultsPanel.add(makeSmallSpacer());
        }
        if (choices.isEmpty()) resultsPanel.add(wikiButton(viewState.query));
        resultsPanel.add(reportControls());
        finishPage(0);
    }

    private void showOverview()
    {
        chooserIcons.clear();
        if (viewState.page == LookupViewState.Page.SOURCES)
            savedSourceScroll.put(sourceViewKey(), resultsScroll.getVerticalScrollBar().getValue());
        viewState.page = LookupViewState.Page.OVERVIEW;
        overviewBack.setVisible(false);
        resultsPanel.removeAll();
        priceLabels.clear();
        if (viewState.selection != null && viewState.selection.choices().size() > 1)
        {
            JButton back = style.backButton("Matching items", () -> { rememberItem(); showItemChoices(); });
            back.setName("backToItems");
            resultsPanel.add(back);
        }
        resultsPanel.add(makeItemHeader(viewState.selectedItem));
        addSelectedPrice();
        resultsPanel.add(makeSectionHeader("Ways to get it"));
        if (!FILTER_ALL.equals(filterDropdown.getSelectedItem()) || wildernessDropdown.getSelectedItem() != WildernessFilter.ALL || !sourceSearch.getText().isEmpty())
        {
            JLabel saved = makeLabel("Saved filters: " + filterDropdown.getSelectedItem()
                    + (wildernessDropdown.getSelectedItem() != WildernessFilter.ALL ? "; NPCs " + wildernessDropdown.getSelectedItem() : "")
                    + (!sourceSearch.getText().isEmpty() ? "; Find: " + sourceSearch.getText() : ""));
            saved.setName("activeFilters");
            resultsPanel.add(saved);
            JButton resume = style.button("Use saved filters", () -> openMethod((String) filterDropdown.getSelectedItem()), false);
            resume.setName("viewCurrentMethod");
            resultsPanel.add(resume);
        }
        addMethod("Shops", FILTER_SHOPS, "buying options", matchingSources.stream().filter(s -> SOURCE_TYPE_SHOP.equals(s.getSourceType())).count(), "showOtherSources");
        addMethod("Rewards", FILTER_REWARD_SHOPS, "reward offers", matchingSources.stream().filter(s -> SOURCE_TYPE_REWARD_SHOP.equals(s.getSourceType())).count(), "showRewardSources");
        addMethod("Pickups", FILTER_GROUND, "pickup locations", matchingSources.stream().filter(s -> SOURCE_TYPE_GROUND.equals(s.getSourceType())).count(), "showGroundSources");
        addMethod("Gathering", FILTER_GATHERING, "gathering methods", matchingSources.stream().filter(s -> SOURCE_TYPE_GATHERING.equals(s.getSourceType())).count(), "showGatheringSources");
        addMethod("Loot & containers", FILTER_LOOT, "loot methods", matchingSources.stream().filter(s -> SOURCE_TYPE_LOOT.equals(s.getSourceType())).count(), "showLootSources");
        addMethod("Monsters", FILTER_DROPS, "drop entries", matchingDrops.size(), "showDropSources");
        JButton all = style.button("All sources", () -> openMethod(FILTER_ALL), true);
        all.setName("viewAllSources");
        resultsPanel.add(all);
        resultsPanel.add(makeSummaryLabel("Counts before filters."));
        addDisplayOptions();
        finishPage(viewState.overviewScroll);
    }

    private void addMethod(String title, String filter, String unit, long count, String name)
    {
        if (count == 0) return;
        if (count == 1) unit = unit.equals("drop entries") ? "drop entry" : unit.substring(0, unit.length() - 1);
        JButton open = style.button(title + "\n" + count + " " + unit, () -> openMethod(filter), false);
        open.setText("<html><b>" + LookupStyle.escape(title) + "</b><br>" + count + " " + LookupStyle.escape(unit) + "</html>");
        open.setName(name);
        resultsPanel.add(open);
        resultsPanel.add(makeSpacer());
    }

    private void openMethod(String filter)
    {
        if (viewState.page == LookupViewState.Page.OVERVIEW)
            viewState.overviewScroll = resultsScroll.getVerticalScrollBar().getValue();
        viewState.page = LookupViewState.Page.SOURCES;
        adjustingControls = true;
        filterDropdown.setSelectedItem(filter);
        adjustingControls = false;
        renderResults();
        int scroll = savedSourceScroll.getOrDefault(sourceViewKey(), 0);
        SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(scroll));
    }

    private String sourceViewKey() { return viewState.selectedItem + "|" + filterDropdown.getSelectedItem() + "|" + wildernessDropdown.getSelectedItem() + "|" + sourceSearch.getText(); }

    private void addSelectedPrice()
    {
        java.util.Optional<String> family = itemPriceLookupService.findPotionFamily(viewState.selectedItem);
        java.util.Optional<ItemPrice> price = itemPriceLookupService.searchByItemName(family.map(f -> f + "(4)").orElse(viewState.selectedItem));
        JLabel label = price.map(p -> makeSearchPriceSummary(p, family.isPresent())).orElseGet(() -> makeSummaryLabel(family.isPresent() ? "GE snapshot (4-dose): unavailable" : "GE price not recorded"));
        label.setVisible(showPriceCheckbox.isSelected());
        priceLabels.add(label);
        resultsPanel.add(label);
        if (family.isPresent() && !viewState.selectedItem.matches(".*\\([1-4]\\)$")) resultsPanel.add(makeSummaryLabel("Showing all available doses"));
    }

    private void addDisplayOptions()
    {
        resultsPanel.add(makeSectionHeader("Display"));
        resultsPanel.add(textSize);
        resultsPanel.add(showPriceCheckbox);
        if (viewState.selection != null) resultsPanel.add(reportControls());
    }

    private JPanel reportControls()
    {
        JPanel controls = LookupStyle.column();
        JLabel status = style.text("", false);
        status.setName("copyReportStatus");
        status.setVisible(false);
        JButton copy = style.button("Copy report", () -> {
            boolean hasSelectedItem = viewState.page != LookupViewState.Page.ITEMS;
            boolean hasSourceContext = hasSelectedItem && viewState.page != LookupViewState.Page.NO_SOURCES;
            String method = hasSourceContext ? (String) filterDropdown.getSelectedItem() : null;
            String area = FILTER_ALL.equals(method) || FILTER_DROPS.equals(method)
                    ? wildernessDropdown.getSelectedItem().toString() : null;
            String view = detail != null ? "Source details" : viewState.page == LookupViewState.Page.ITEMS
                    ? "Matching items" : viewState.page == LookupViewState.Page.NO_SOURCES ? "No sources"
                    : viewState.page == LookupViewState.Page.OVERVIEW ? "Item overview" : "Sources";
            try
            {
                String report = LookupReport.create(hasSelectedItem ? viewState.selectedItem : null,
                        viewState.query, hasSourceContext ? sourceSearch.getText() : null, method, area, view,
                        detail == null ? null : detailSource.getMonsterName(), detail == null ? null : detail.saveState());
                if (viewState.page == LookupViewState.Page.NO_SOURCES) report += "Recorded sources: 0\n";
                if (detail != null && showingPotionFamily()) report += "Dose item: " + detailSource.getItemName() + "\n";
                clipboardWriter.accept(report);
                status.setText("<html>Report copied</html>");
            }
            catch (RuntimeException ex)
            {
                status.setText("<html>Could not copy; try again</html>");
            }
            status.setVisible(true);
            controls.revalidate();
            controls.repaint();
        }, false);
        copy.setName("copyReport");
        copy.setToolTipText("Copies this lookup and build details. Paste it where you choose.");
        controls.add(copy);
        controls.add(status);
        return controls;
    }

    private void changeTextSize()
    {
        int position = resultsScroll.getVerticalScrollBar().getValue();
        style = new LookupStyle(textSize.getSelectedIndex() == 0 ? LookupStyle.STANDARD_SIZE : LookupStyle.LARGER_SIZE);
        style.refreshTree(searchView);
        for (javax.swing.JComponent control : new javax.swing.JComponent[] {searchField, searchButton, filterDropdown, wildernessDropdown, sourceSearch, showPriceCheckbox, textSize, overviewBack}) style.controlFont(control);
        if (viewState.page == LookupViewState.Page.SOURCES) renderResults();
        else if (viewState.page == LookupViewState.Page.OVERVIEW) showOverview();
        else if (viewState.page == LookupViewState.Page.NO_SOURCES) showNoSources();
        else if (viewState.selection != null) showItemChoices();
        revalidate();
        SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(position));
    }

    private void finishPage(int position)
    {
        style.refreshTree(searchView);
        resultsPanel.revalidate(); resultsPanel.repaint(); revalidate();
        SwingUtilities.invokeLater(() -> {
            resultsScroll.getVerticalScrollBar().setValue(position);
            chooserIcons.requestVisible();
        });
    }

    private void renderResults()
    {
        if (detail != null || viewState.page != LookupViewState.Page.SOURCES)
        {
            return;
        }
        chooserIcons.clear();
        // Keep the live dropdowns and source field attached while refreshing
        // results, so changing a selection does not dismiss their popup/focus.
        for (Component child : resultsPanel.getComponents())
            if (child != controlsPanel) resultsPanel.remove(child);
        priceLabels.clear();
        overviewBack.setVisible(true);
        resultsPanel.add(makeItemHeader(viewState.selectedItem), 0);
        if (controlsPanel.getParent() != resultsPanel) resultsPanel.add(controlsPanel);
        clearSearch.setVisible(!sourceSearch.getText().isEmpty());
        String selectedFilter = (String) filterDropdown.getSelectedItem();
        boolean showDrops = FILTER_ALL.equals(selectedFilter) || FILTER_DROPS.equals(selectedFilter);
        boolean showSources = !FILTER_DROPS.equals(selectedFilter);
        npcFilters.setVisible(showDrops);
        sourceSearchRow.setVisible(!matchingDrops.isEmpty() || !matchingSources.isEmpty());

        if (displayedQuery.isEmpty())
        {
            resultsPanel.add(makeLabel("Enter an item name, then press Enter or Search."));
        }
        else
        {
            WildernessFilter locationFilter = (WildernessFilter) wildernessDropdown.getSelectedItem();
            String sourceQuery = sourceSearch.getText().trim().toLowerCase(Locale.ROOT);
            if (showDrops && indexedDrops != matchingDrops)
            {
                boundContexts = new BoundDropContexts(monsterLocationLookupService, matchingDrops);
                indexedDrops = matchingDrops;
            }
            List<DropSource> dropResults = showDrops ? matchingDrops.stream()
                    .filter(source -> monsterLocationLookupService.hasQualifyingLocation(source, locationFilter))
                    .filter(source -> boundContexts.find(source, sourceQuery, locationFilter))
                    .collect(Collectors.toList()) : Collections.emptyList();
            List<ItemSource> sources = matchingSources;
            if (FILTER_SHOPS.equals(selectedFilter))
            {
                sources = filterBySourceType(sources, SOURCE_TYPE_SHOP);
            }
            else if (FILTER_REWARD_SHOPS.equals(selectedFilter))
            {
                sources = filterBySourceType(sources, SOURCE_TYPE_REWARD_SHOP);
            }
            else if (FILTER_GROUND.equals(selectedFilter))
            {
                sources = filterBySourceType(sources, SOURCE_TYPE_GROUND);
            }
            else if (FILTER_GATHERING.equals(selectedFilter))
            {
                sources = filterBySourceType(sources, SOURCE_TYPE_GATHERING);
            }
            else if (FILTER_LOOT.equals(selectedFilter))
            {
                sources = filterBySourceType(sources, SOURCE_TYPE_LOOT);
            }
            List<ItemSource> displaySources = showSources ? sources.stream()
                    .filter(source -> contains(source.getSourceName(), sourceQuery) || contains(source.getLocation(), sourceQuery)
                            || contains(source.getPackName(), sourceQuery)
                            || (source.getAcquisitionMethod() != null
                                && contains(source.getAcquisitionMethod().getSearchText(), sourceQuery)))
                    .collect(Collectors.toList()) : Collections.emptyList();
            boolean hasPacks = displaySources.stream().anyMatch(source -> source.getPackQuantity() > 0);
            long groundCount = displaySources.stream().filter(source -> SOURCE_TYPE_GROUND.equals(source.getSourceType())).count();
            long gatheringCount = displaySources.stream().filter(source -> SOURCE_TYPE_GATHERING.equals(source.getSourceType())).count();
            long lootCount = displaySources.stream().filter(source -> SOURCE_TYPE_LOOT.equals(source.getSourceType())).count();
            if (hasPacks)
            {
                // Keep a shop's loose and packaged purchase options next to each other.
                displaySources.sort(java.util.Comparator.comparing(ItemSource::getSourceName,
                        java.util.Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)).thenComparingInt(ItemSource::getPackQuantity));
            }

            // A broad item query must not present one arbitrary item's price as its answer.
            java.util.Set<String> itemNames = new java.util.LinkedHashSet<>();
            matchingDrops.forEach(source -> itemNames.add(source.getItemName().toLowerCase(Locale.ROOT)));
            matchingSources.forEach(source -> itemNames.add(source.getItemName().toLowerCase(Locale.ROOT)));
            java.util.Optional<String> potionFamily = itemPriceLookupService.findPotionFamily(viewState.selectedItem);
            if (potionFamily.isPresent())
            {
                // All dose variants use the clearly labelled four-dose reference.
                // Two-dose mixes/untradeable potions may have no four-dose GE item.
                java.util.Optional<ItemPrice> reference = itemPriceLookupService.searchByItemName(potionFamily.get() + "(4)");
                JLabel label = reference.map(price -> makeSearchPriceSummary(price, true))
                        .orElseGet(() -> makeSummaryLabel("GE snapshot (4-dose): unavailable"));
                label.setVisible(showPriceCheckbox.isSelected());
                priceLabels.add(label);
                resultsPanel.add(label);
                if (!viewState.selectedItem.matches(".*\\([1-4]\\)$"))
                {
                    resultsPanel.add(makeSummaryLabel("Showing all available doses"));
                }
            }
            else if (itemNames.size() == 1)
            {
                itemPriceLookupService.searchByItemName(itemNames.iterator().next()).ifPresent(price -> {
                    JLabel label = makeSearchPriceSummary(price);
                    label.setVisible(showPriceCheckbox.isSelected());
                    priceLabels.add(label);
                    resultsPanel.add(label);
                });
            }
            if (showDrops && locationFilter != WildernessFilter.ALL && !matchingDrops.isEmpty())
            {
                JLabel notice = makeSummaryLabel("NPCs: " + locationFilter + ". Unknown or unverified locations are hidden.");
                notice.setToolTipText("Requires a usable location associated with this exact drop. Shops and other sources are unchanged.");
                resultsPanel.add(notice);
            }
            if (groundCount > 0 || FILTER_GROUND.equals(selectedFilter))
            {
                JLabel coverage = makeSummaryLabel("Ground pickups: limited coverage. Access requirements not fully checked.");
                coverage.setName("groundCoverage");
                resultsPanel.add(coverage);
            }
            if (groundCount > 0 && !FILTER_GROUND.equals(selectedFilter))
            {
                JButton groundSources = new JButton("View ground pickups (" + groundCount + ")");
                groundSources.setName("showGroundSources");
                groundSources.setAlignmentX(Component.LEFT_ALIGNMENT);
                groundSources.addActionListener(e -> filterDropdown.setSelectedItem(FILTER_GROUND));
                resultsPanel.add(groundSources);
            }
            if (gatheringCount > 0 || FILTER_GATHERING.equals(selectedFilter))
            {
                JLabel coverage = makeSummaryLabel("Gathering: limited location coverage. Access requirements not fully checked.");
                coverage.setName("gatheringCoverage");
                resultsPanel.add(coverage);
            }
            if (gatheringCount > 0 && !FILTER_GATHERING.equals(selectedFilter))
            {
                JButton gatheringSources = new JButton("View gathering (" + gatheringCount + ")");
                gatheringSources.setName("showGatheringSources");
                gatheringSources.setAlignmentX(Component.LEFT_ALIGNMENT);
                gatheringSources.addActionListener(e -> filterDropdown.setSelectedItem(FILTER_GATHERING));
                resultsPanel.add(gatheringSources);
            }
            if (lootCount > 0 || FILTER_LOOT.equals(selectedFilter))
            {
                JLabel coverage = makeSummaryLabel("Loot & containers: selected methods only. Other loot sources may exist.");
                coverage.setName("lootCoverage");
                resultsPanel.add(coverage);
            }
            if (!dropResults.isEmpty() && !displaySources.isEmpty())
            {
                boolean shopsOnly = displaySources.stream().allMatch(source -> SOURCE_TYPE_SHOP.equals(source.getSourceType()));
                JButton otherSources = new JButton((hasPacks ? "View buying options" : shopsOnly ? "View shops" : "View other sources")
                        + " (" + displaySources.size() + ")");
                otherSources.setName("showOtherSources");
                otherSources.setAlignmentX(Component.LEFT_ALIGNMENT);
                otherSources.addActionListener(e -> filterDropdown.setSelectedItem(shopsOnly ? FILTER_SHOPS : FILTER_SOURCES));
                resultsPanel.add(otherSources);
            }
            if (!dropResults.isEmpty())
            {
                displayDropResults(dropResults);
            }
            if (!displaySources.isEmpty())
            {
                resultsPanel.add(makeSectionHeader((hasPacks ? "Buying options" : sourceSectionHeading(selectedFilter)) + " (" + displaySources.size() + ")"));
                for (ItemSource source : displaySources)
                {
                    resultsPanel.add(makeItemSourceCard(source));
                }
            }
            if (dropResults.isEmpty() && displaySources.isEmpty())
            {
                boolean filtersActive = !FILTER_ALL.equals(selectedFilter)
                        || locationFilter != WildernessFilter.ALL || !sourceQuery.isEmpty();
                if (filtersActive && (!matchingDrops.isEmpty() || !matchingSources.isEmpty()))
                {
                    String emptyMessage = "No sources match these filters.";
                    if (FILTER_GROUND.equals(selectedFilter))
                    {
                        boolean hasGroundData = matchingSources.stream()
                                .anyMatch(source -> SOURCE_TYPE_GROUND.equals(source.getSourceType()));
                        emptyMessage = hasGroundData ? "No verified ground pickups match these filters."
                                : "No verified ground pickups in the current data.";
                    }
                    else if (FILTER_GATHERING.equals(selectedFilter))
                    {
                        boolean hasGatheringData = matchingSources.stream()
                                .anyMatch(source -> SOURCE_TYPE_GATHERING.equals(source.getSourceType()));
                        emptyMessage = hasGatheringData ? "No verified gathering methods match these filters."
                                : "No verified gathering methods in the current data.";
                    }
                    else if (FILTER_LOOT.equals(selectedFilter))
                    {
                        boolean hasLootData = matchingSources.stream().anyMatch(source -> SOURCE_TYPE_LOOT.equals(source.getSourceType()));
                        emptyMessage = hasLootData ? "No reviewed loot methods match these filters."
                                : "No reviewed loot methods in the current data.";
                    }
                    resultsPanel.add(makeNoResultHeader(emptyMessage));
                    resultsPanel.add(makeLabel("This item has other sources in the local data."));
                    resultsPanel.add(makeLabel((showDrops ? "Use the Method and NPC area menus above to change filters."
                            : "Use the Method menu above to choose another source type.")
                            + (!sourceSearch.getText().isEmpty() ? " Clear search removes only Find source text." : "")));
                }
                else
                {
                    resultsPanel.add(makeNoResultHeader(FILTER_GROUND.equals(selectedFilter)
                            ? "No verified ground pickups in the current data." : FILTER_GATHERING.equals(selectedFilter)
                            ? "No verified gathering methods in the current data." : FILTER_LOOT.equals(selectedFilter)
                            ? "No reviewed loot methods in the current data." : "No source found for: " + displayedQuery));
                    resultsPanel.add(makeLabel(FILTER_GROUND.equals(selectedFilter)
                            ? "Check the Wiki for other pickup locations."
                            : FILTER_GATHERING.equals(selectedFilter) ? "Check the Wiki for other gathering methods."
                            : FILTER_LOOT.equals(selectedFilter) ? "Check the Wiki for other loot methods."
                            : "No match in the local data. Check the spelling or try a shorter item name."));
                }
                resultsPanel.add(wikiButton(displayedQuery));
            }
        }
        addDisplayOptions();
        style.refreshTree(searchView);
        resultsPanel.revalidate();
        resultsPanel.repaint();
        revalidate();
        SwingUtilities.invokeLater(() -> {
            resultsScroll.getVerticalScrollBar().setValue(0);
            chooserIcons.requestVisible();
        });
    }

    private boolean contains(String value, String query)
    {
        return query.isEmpty() || value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void sourceSearchChanged()
    {
        if (adjustingControls) return;
        clearSearch.setVisible(!sourceSearch.getText().isEmpty());
        controlsPanel.revalidate();
        sourceSearchTimer.restart();
    }

    private void clearSourceSearch()
    {
        sourceSearchTimer.stop();
        adjustingControls = true;
        sourceSearch.setText("");
        adjustingControls = false;
        renderResults();
        sourceSearch.requestFocusInWindow();
    }

    private JButton wikiButton(String item)
    {
        JButton wiki = style.button("Search item on Wiki", () -> LinkBrowser.browse("https://oldschool.runescape.wiki/w/Special:Search?search="
                + URLEncoder.encode(item, StandardCharsets.UTF_8)), false);
        wiki.setName("itemWiki");
        return wiki;
    }

    private boolean showingPotionFamily()
    {
        return itemPriceLookupService.findPotionFamily(viewState.selectedItem)
                .filter(viewState.selectedItem::equalsIgnoreCase).isPresent();
    }

    private static String doseLabel(String item)
    {
        java.util.regex.Matcher dose = DOSE_SUFFIX.matcher(item);
        return dose.find() ? dose.group(1) + "-dose" : item;
    }

    private static final java.util.regex.Pattern DOSE_SUFFIX = java.util.regex.Pattern.compile("\\(([1-4])\\)$");

    private void displayPotionSources(List<DropSource> results)
    {
        Map<Object, List<DropSource>> groups = new LinkedHashMap<>();
        results.stream().sorted(Comparator.comparingDouble(this::getRateSortValue)
                .thenComparing(DropSource::getMonsterName)).forEach(source -> {
            Object key = source;
            if (monsterLocationLookupService.isCommonDrop(source))
                key = java.util.Arrays.asList(source.getMonsterName(), source.getMembersOnly(), source.getNotes(),
                        monsterLocationLookupService.lookup(source).getLocations(), accessRequirementLookupService.forSource(source));
            groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(source);
        });
        resultsPanel.add(makeSectionHeader("NPC drops (" + results.size() + ")"));
        for (List<DropSource> offers : groups.values())
        {
            DropSource first = offers.get(0);
            JPanel card = LookupStyle.column();
            card.setName("dropSourceCard");
            card.setBackground(LookupStyle.CARD);
            card.setBorder(LookupStyle.cardBorder());
            card.add(makeCardTitle(first.getMonsterName(), CARD_TEXT_WIDTH));
            if (hasText(first.getNotes())) card.add(makeCardLine(first.getNotes(), CARD_TEXT_WIDTH));
            addBoundDanger(card, first);
            addSourceRequirements(card, first, CARD_TEXT_WIDTH);
            for (DropSource offer : offers)
            {
                String title = doseLabel(offer.getItemName()) + " × " + DropQuantity.display(offer)
                        + "\n" + DropRatePresentation.sourceRateLine(offer);
                JButton row = style.button(title, () -> showMonsterDetails(offer), false);
                row.setName("openLocations");
                row.getAccessibleContext().setAccessibleName(first.getMonsterName() + " → " + offer.getItemName() + ", " + title + ", view details");
                row.setToolTipText("View " + offer.getItemName() + " drop details");
                chooserIcons.add(offer.getItemName(), row);
                card.add(row);
            }
            resultsPanel.add(card);
        }
    }

    private void displayDropResults(List<DropSource> results)
    {
        if (showingPotionFamily())
        {
            displayPotionSources(results);
            return;
        }
        Map<String, List<DropSource>> resultsByItem = new LinkedHashMap<>();

        for (DropSource source : results)
        {
            resultsByItem
                    .computeIfAbsent(source.getItemName(), key -> new java.util.ArrayList<>())
                    .add(source);
        }

        resultsPanel.add(makeSummaryLabel("NPC drops: " + results.size()
                + (resultsByItem.size() > 1 ? " across " + resultsByItem.size() + " items" : "")));

        for (Map.Entry<String, List<DropSource>> itemEntry : resultsByItem.entrySet())
        {
            if (resultsByItem.size() > 1) resultsPanel.add(makeItemHeader(formatItemName(itemEntry.getKey())));

            List<DropSource> sortedSources = itemEntry.getValue().stream()
                    .sorted(Comparator.comparingDouble(this::getRateSortValue))
                    .collect(java.util.stream.Collectors.toList());

            List<DropSourceGroup> groups = MonsterFamilyGrouper.group(sortedSources);
            groups.sort(Comparator.comparingDouble((DropSourceGroup g) -> getRateSortValue(g.getMembers().get(0))));

            List<java.util.function.Supplier<JPanel>> rows = new ArrayList<>();
            for (DropSourceGroup group : groups)
            {
                List<DropSource> members = group.getMembers();
                if (members.size() > 1 && members.stream().allMatch(boundContexts::isVariant)
                        && members.stream().map(DropSource::getMonsterName).distinct().count() == 1)
                {
                    rows.add(() -> makeVariantCard(members));
                }
                else if (shouldRenderAsGroup(group))
                {
                    rows.add(() -> makeGroupContainer(group));
                }
                else
                {
                    for (DropSource member : members)
                    {
                        rows.add(() -> makeDropCard(member, false));
                    }
                }
            }
            if (rows.size() <= 250)
            {
                rows.forEach(row -> resultsPanel.add(row.get()));
            }
            else
            {
                JPanel batch = LookupStyle.column();
                String key = sourceViewKey() + "|" + itemEntry.getKey();
                int count = Math.min(rows.size(), loadedSourceRows.getOrDefault(key, 50));
                appendSourceRows(batch, rows, key, 0, count);
                resultsPanel.add(batch);
            }

            resultsPanel.add(makeSpacer());
        }
    }

    private void appendSourceRows(JPanel batch, List<java.util.function.Supplier<JPanel>> rows, String key, int from, int to)
    {
        for (int index = from; index < to; index++) batch.add(rows.get(index).get());
        loadedSourceRows.put(key, to);
        if (to < rows.size())
        {
            JButton more = style.button("Show more sources\n" + to + " of " + rows.size() + " source cards shown", () -> { }, false);
            more.setName("moreSourceRows");
            more.addActionListener(event -> {
                int position = resultsScroll.getVerticalScrollBar().getValue();
                batch.remove(more);
                appendSourceRows(batch, rows, key, to, Math.min(rows.size(), to + 50));
                batch.revalidate(); batch.repaint();
                SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(position));
            });
            batch.add(more);
        }
    }

    private boolean shouldRenderAsGroup(DropSourceGroup group)
    {
        List<DropSource> members = group.getMembers();

        if (members.size() <= 1)
        {
            return false;
        }

        String firstName = members.get(0).getMonsterName();

        for (int i = 1; i < members.size(); i++)
        {
            String name = members.get(i).getMonsterName();

            if (firstName == null ? name != null : !firstName.equals(name))
            {
                return true;
            }
        }

        return false;
    }

    private JPanel makeGroupContainer(DropSourceGroup group)
    {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(LookupStyle.BACK);
        container.setAlignmentX(LEFT_ALIGNMENT);
        container.setBorder(new EmptyBorder(0, 0, 8, 0));

        int memberCount = group.getMembers().size();
        String collapsedText = "▸ " + group.getParentName() + " (" + memberCount + ")";
        String expandedText  = "▾ " + group.getParentName() + " (" + memberCount + ")";

        String groupKey = sourceViewKey() + "|" + group.getParentName();
        JButton header = style.button(expandedGroups.contains(groupKey) ? expandedText : collapsedText, () -> { }, false);
        header.setName("sourceGroup:" + group.getParentName());
        header.setHorizontalAlignment(JButton.LEFT);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setOpaque(true);
        header.setBackground(LookupStyle.CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LookupStyle.BORDER),
                new EmptyBorder(8, 8, 6, 8)
        ));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel membersPanel = new JPanel();
        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        membersPanel.setBackground(LookupStyle.BACK);
        membersPanel.setAlignmentX(LEFT_ALIGNMENT);
        membersPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, LookupStyle.BORDER),
                new EmptyBorder(0, 8, 6, 0)
        ));
        membersPanel.setVisible(expandedGroups.contains(groupKey));
        if (membersPanel.isVisible()) group.getMembers().forEach(member -> membersPanel.add(makeDropCard(member, true)));

        header.addActionListener(e -> {
            boolean expand = !membersPanel.isVisible();
            if (expand && membersPanel.getComponentCount() == 0)
            {
                group.getMembers().forEach(member -> membersPanel.add(makeDropCard(member, true)));
            }
            membersPanel.setVisible(expand);
            if (expand) expandedGroups.add(groupKey); else expandedGroups.remove(groupKey);
            header.setText("<html><b>" + escapeHtml(expand ? expandedText : collapsedText) + "</b></html>");
            resultsPanel.revalidate();
            resultsPanel.repaint();
        });

        container.add(header);
        container.add(membersPanel);

        return container;
    }

    private JPanel makeVariantCard(List<DropSource> members)
    {
        DropSource first = members.get(0);
        JPanel card = LookupStyle.column();
        card.setBorder(LookupStyle.cardBorder());
        card.setName("dropSourceCard");
        card.setBackground(LookupStyle.CARD);
        JLabel title = itemTitle(first.getItemName(), first.getMonsterName() + " → " + first.getItemName(), CARD_TEXT_WIDTH);
        title.setName("dropContextGroup");
        card.add(title);
        for (DropSource member : members)
        {
            card.add(makeSmallSpacer());
            appendDropContext(card, member, CARD_TEXT_WIDTH, true);
        }
        return card;
    }

    private JPanel makeDropCard(DropSource source, boolean nested)
    {
        JPanel card = LookupStyle.column();
        card.setBackground(LookupStyle.CARD);

        if (nested)
        {
            // Group children are tighter/compact; the group header bar and left
            // rail already separate them, so no per-card outline is needed.
            card.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(2, 0, 2, 0),
                    new EmptyBorder(6, 8, 6, 8)
            ));
        }
        else
        {
            // Unrelated top-level cards get a thin outline plus extra bottom
            // spacing so they read as separate entities, not one continuous list.
            card.setBorder(BorderFactory.createCompoundBorder(
                    new EmptyBorder(4, 0, 10, 0),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(1, 1, 1, 1, LookupStyle.BORDER),
                            new EmptyBorder(8, 8, 8, 8)
                    )
            ));
        }

        appendDropContext(card, source, nested ? NESTED_CARD_TEXT_WIDTH : CARD_TEXT_WIDTH, false);
        card.setAlignmentX(LEFT_ALIGNMENT);
        return card;
    }

    private void appendDropContext(JPanel card, DropSource source, int wrapWidth, boolean contextRow)
    {
        int firstComponent = card.getComponentCount();
        if (!contextRow) card.add(itemTitle(source.getItemName(), source.getMonsterName() + " → " + source.getItemName(), wrapWidth));
        if (boundContexts.isVariant(source)) card.add(makeCardTitle(boundContexts.variantTitle(source), wrapWidth));
        addBoundDanger(card, source);

        card.add(makeCardLine(DropRatePresentation.sourceRateLine(source), wrapWidth));
        card.add(makeCardLine("Quantity: " + DropQuantity.display(source), wrapWidth));

        if (source.getNotes() != null && !source.getNotes().isEmpty())
        {
            card.add(makeCardLine("Notes: " + source.getNotes(), wrapWidth));
        }

        addSourceRequirements(card, source, wrapWidth);

        card.setName("dropSourceCard");
        MouseAdapter openDetail = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e))
                {
                    showMonsterDetails(source);
                }
            }
        };
        if (!contextRow) card.addMouseListener(openDetail);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        for (int i = firstComponent; i < card.getComponentCount(); i++)
            card.getComponent(i).addMouseListener(openDetail);
        JButton locations = style.button("View details", () -> showMonsterDetails(source), false);
        locations.setToolTipText("Locations and requirements for " + source.getMonsterName());
        locations.setMargin(new java.awt.Insets(3, 4, 3, 4));
        locations.setName("openLocations");
        locations.getAccessibleContext().setAccessibleName("Locations and requirements for " + source.getMonsterName());
        card.add(locations);
    }

    private void addBoundDanger(JPanel card, DropSource source)
    {
        String danger = boundContexts.danger(source, (WildernessFilter) wildernessDropdown.getSelectedItem());
        if (danger != null) card.add(style.wildernessWarning(danger));
    }

    private void addSourceRequirements(JPanel card, DropSource source, int wrapWidth)
    {
        for (AccessRequirement fact : accessRequirementLookupService.forSource(source))
        {
            if (fact.isSourceCardFact())
            {
                JLabel requirement = makeCardLine(fact.getLabel() + ": " + fact.getText(), wrapWidth);
                requirement.setName("requirement:" + fact.getId());
                card.add(requirement);
            }
        }
    }

    private void showMonsterDetails(DropSource source)
    {
        sourceSearchTimer.stop();
        searchScrollPosition = resultsScroll.getVerticalScrollBar().getValue();
        remove(searchView);
        detail = new MonsterLocationPanel(source, displayedQuery, monsterLocationLookupService, accessRequirementLookupService,
                (WildernessFilter) wildernessDropdown.getSelectedItem(), this::showSearchResults, style);
        if (boundContexts.isVariant(source)) detail.setContextTitle(boundContexts.variantTitle(source));
        detailSource = source;
        chooserIcons.showPinned(source.getItemName(), detail.getIdentityHeading());
        detail.setReportControls(reportControls());
        add(detail, BorderLayout.CENTER);
        revalidate();
        repaint();
        detail.restoreState(detailStates.get(source));
    }

    private void showSearchResults()
    {
        chooserIcons.closePinned();
        if (detail != null) detailStates.put(detailSource, detail.saveState());
        removeAll();
        detail = null;
        add(searchView, BorderLayout.CENTER);
        revalidate();
        repaint();
        // Retain the existing component tree: query, filters, prices, expanded
        // monster groups and all results survive Back without rerunning search.
        SwingUtilities.invokeLater(() -> getScrollPane().getVerticalScrollBar().setValue(searchScrollPosition));
    }

    private JPanel makeItemSourceCard(ItemSource source)
    {
        JPanel card = LookupStyle.column();
        card.setBackground(LookupStyle.CARD);
        card.setBorder(LookupStyle.cardBorder());

        card.setName("itemSourceCard");
        if (source.getAcquisitionMethod() != null)
        {
            AcquisitionMethod method = source.getAcquisitionMethod();
            card.putClientProperty("acquisitionMethodId", method.getId());
            card.add(itemTitle(source.getItemName(), method.getSourceName() + " → " + source.getItemName(), CARD_TEXT_WIDTH));
            for (AcquisitionMethod.Section section : method.getSummarySections())
            {
                card.add(Box.createVerticalStrut(6));
                card.add(makeCardTitle(section.getHeading(), CARD_TEXT_WIDTH));
                if (section.isWilderness()) card.add(style.wildernessWarning());
                for (String line : section.getLines()) card.add(makeCardLine(line, CARD_TEXT_WIDTH));
            }
            JButton wiki = wikiButton(method.getWikiPage());
            wiki.setText("Method Wiki");
            wiki.setName("methodWiki");
            wiki.setToolTipText("https://oldschool.runescape.wiki/w/" + method.getWikiPage().replace(' ', '_'));
            card.add(wiki);
            card.setAlignmentX(LEFT_ALIGNMENT);
            return card;
        }
        boolean ground = SOURCE_TYPE_GROUND.equals(source.getSourceType());
        boolean gathering = SOURCE_TYPE_GATHERING.equals(source.getSourceType());
        String shop = hasText(source.getSourceName()) ? source.getSourceName() : source.getSourceType();
        if (showingPotionFamily())
        {
            card.add(itemTitle(source.getItemName(), shop, CARD_TEXT_WIDTH));
            card.add(makeCardLine(doseLabel(source.getItemName()), CARD_TEXT_WIDTH));
        }
        else card.add(itemTitle(source.getItemName(), shop + " → " + source.getItemName(), CARD_TEXT_WIDTH));
        boolean pack = source.getPackQuantity() > 0;
        if (pack)
        {
            card.add(makeCardTitle("Buy pack", CARD_TEXT_WIDTH));
            card.add(itemTitle(source.getPackName(), source.getPackName(), CARD_TEXT_WIDTH));
            card.add(makeCardLine("Contains: " + source.getPackQuantity() + " × " + source.getPackItemName()
                    + (source.isPackNoted() ? " (noted)" : ""), CARD_TEXT_WIDTH));
        }

        if (!pack && hasText(source.getSourceType()))
        {
            card.add(makeCardLine(SOURCE_TYPE_SHOP.equals(source.getSourceType()) ? "Direct purchase" : source.getSourceType(), CARD_TEXT_WIDTH));
        }

        boolean unverifiedCurrency = SOURCE_TYPE_REWARD_SHOP.equals(source.getSourceType())
                && source.getCost() != null && source.getCost().matches("[0-9,]+ coins?");
        if (unverifiedCurrency)
        {
            card.add(makeCardLine("Cost not verified; check the Wiki.", CARD_TEXT_WIDTH));
        }
        else if (hasText(source.getCost()))
        {
            boolean missingAmount = source.getCost().equals("Marks of grace")
                    || source.getCost().equals("Slayer reward points") || source.getCost().equals("LMS points");
            card.add(makeCardLine((missingAmount ? "Currency: " : "Cost: ") + source.getCost()
                    + (pack ? " per pack" : ""), CARD_TEXT_WIDTH));
            if (missingAmount)
            {
                card.add(makeCardLine("Amount not recorded; check the Wiki.", CARD_TEXT_WIDTH));
            }
        }

        if (hasText(source.getLocation()) && !ground)
        {
            card.add(makeCardLine("Location: " + source.getLocation(), CARD_TEXT_WIDTH));
        }

        if (hasText(source.getNotes()))
        {
            String notes = unverifiedCurrency ? source.getNotes().replace("Cost increases as stock depletes.", "").trim() : source.getNotes();
            if (hasText(notes))
            {
                if (gathering)
                {
                    for (String line : notes.split("\n"))
                    {
                        card.add(makeCardLine(line, CARD_TEXT_WIDTH));
                    }
                }
                else
                {
                    card.add(makeCardLine((ground ? "" : "Notes: ") + notes, CARD_TEXT_WIDTH));
                }
            }
        }

        card.add(wikiButton(pack ? source.getPackName() : source.getItemName()));
        card.setAlignmentX(LEFT_ALIGNMENT);


        return card;
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Returns only the sources whose sourceType matches {@code sourceType} exactly.
     * Used by the Shops / Reward Shops filters — no fuzzy matching is applied.
     */
    private List<ItemSource> filterBySourceType(List<ItemSource> sources, String sourceType)
    {
        List<ItemSource> filtered = new java.util.ArrayList<>();

        for (ItemSource source : sources)
        {
            if (sourceType.equals(source.getSourceType()))
            {
                filtered.add(source);
            }
        }

        return filtered;
    }

    /**
     * Section heading for the non-NPC source block, reflecting the active filter so
     * the heading matches what is actually being shown.
     */
    private String sourceSectionHeading(String selectedFilter)
    {
        if (FILTER_LOOT.equals(selectedFilter)) return "Loot & containers";
        if (FILTER_GATHERING.equals(selectedFilter))
        {
            return "Gathering";
        }
        if (FILTER_GROUND.equals(selectedFilter))
        {
            return "Ground pickups";
        }
        if (FILTER_SHOPS.equals(selectedFilter))
        {
            return "Shops";
        }

        if (FILTER_REWARD_SHOPS.equals(selectedFilter))
        {
            return "Reward Shops";
        }

        return "Non-NPC Sources";
    }

    /**
     * Builds a left-aligned label whose text wraps at {@code wrapWidth} pixels. A pixel
     * width is required because Swing only wraps HTML labels when the enclosing block has
     * an explicit width; this keeps long lines inside the panel instead of clipping, and
     * the wrapped preferred height is reported correctly to BoxLayout.
     */
    private JLabel makeWrappedLabel(String text, int wrapWidth, boolean bold, EmptyBorder border)
    {
        JLabel label = style.text(text, bold);
        label.setBorder(border);
        return label;
    }

    private JLabel makeSearchPriceSummary(ItemPrice itemPrice)
    {
        return makeSearchPriceSummary(itemPrice, false);
    }

    private JLabel makeSearchPriceSummary(ItemPrice itemPrice, boolean fourDoseReference)
    {
        String displayName = formatItemName(itemPrice.getItemName());
        String priceLine = formatPriceLine(itemPrice);
        if (fourDoseReference)
        {
            priceLine = priceLine.replace("GE snapshot:", "GE snapshot (4-dose):");
        }

        JLabel label = style.text((fourDoseReference ? displayName + "\n" : "") + priceLine, false);
        label.setBorder(new EmptyBorder(4, 0, 6, 0));
        return label;
    }

    private JLabel makeNoResultHeader(String text)
    {
        return makeWrappedLabel(text, PANEL_TEXT_WIDTH, true, new EmptyBorder(8, 0, 6, 0));
    }

    private JLabel makeLabel(String text)
    {
        return makeWrappedLabel(text, PANEL_TEXT_WIDTH, false, new EmptyBorder(4, 0, 4, 0));
    }

    private JLabel makeSummaryLabel(String text)
    {
        return makeWrappedLabel(text, PANEL_TEXT_WIDTH, false, new EmptyBorder(2, 0, 2, 0));
    }

    private JLabel makeSectionHeader(String text)
    {
        return makeWrappedLabel(text, PANEL_TEXT_WIDTH, true, new EmptyBorder(10, 0, 6, 0));
    }

    private JLabel makeItemHeader(String text)
    {
        JLabel label = makeWrappedLabel(text, PANEL_TEXT_WIDTH, true, new EmptyBorder(10, 0, 6, 0));
        if (!itemPriceLookupService.findPotionFamily(text).filter(text::equalsIgnoreCase).isPresent())
            chooserIcons.add(text, label);
        return label;
    }

    private JLabel itemTitle(String item, String title, int width)
    {
        JLabel label = makeCardTitle(title, width);
        chooserIcons.add(item, label);
        return label;
    }

    private String formatPriceLine(ItemPrice itemPrice)
    {
        if (!itemPrice.isTradeable())
        {
            return "GE: Untradeable";
        }

        if (itemPrice.getPrice() == null || itemPrice.getPrice().trim().isEmpty())
        {
            return "GE: unavailable";
        }

        return "GE snapshot: " + formatNumber(itemPrice.getPrice()) + " gp";
    }

    private String formatNumber(String value)
    {
        try
        {
            long number = Long.parseLong(value.trim());
            return String.format("%,d", number);
        }
        catch (NumberFormatException ex)
        {
            return value;
        }
    }

    private JLabel makeCardTitle(String text, int wrapWidth)
    {
        return makeWrappedLabel(text, wrapWidth, true, new EmptyBorder(0, 0, 5, 0));
    }

    private JLabel makeCardLine(String text, int wrapWidth)
    {
        return makeWrappedLabel(text, wrapWidth, false, new EmptyBorder(2, 0, 2, 0));
    }

    private JLabel makeSpacer()
    {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(1, 6));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private JLabel makeSmallSpacer()
    {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(1, 5));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private String formatItemName(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return "";
        }

        String[] words = itemName.trim().split("\\s+");
        StringBuilder formatted = new StringBuilder();

        for (String word : words)
        {
            if (word.isEmpty())
            {
                continue;
            }

            if (formatted.length() > 0)
            {
                formatted.append(" ");
            }

            if (word.length() == 1)
            {
                formatted.append(word.toUpperCase());
            }
            else
            {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                formatted.append(word.substring(1));
            }
        }

        return formatted.toString();
    }

    private String escapeHtml(String text)
    {
        if (text == null)
        {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private double getRateSortValue(DropSource source)
    {
        String rate = source.getDropRate();

        if (rate == null || rate.trim().isEmpty())
        {
            return Double.MAX_VALUE;
        }

        String cleanedRate = rate.trim().toLowerCase();

        if (cleanedRate.equals("always"))
        {
            return 1.0;
        }

        if (cleanedRate.equals("varies") || cleanedRate.equals("unknown"))
        {
            return Double.MAX_VALUE;
        }

        if (cleanedRate.contains("/"))
        {
            String[] parts = cleanedRate.split("/");

            if (parts.length == 2)
            {
                try
                {
                    double numerator = Double.parseDouble(parts[0].trim());
                    double denominator = Double.parseDouble(parts[1].trim());

                    if (denominator > 0 && numerator > 0)
                    {
                        return denominator / numerator;
                    }
                }
                catch (NumberFormatException ignored)
                {
                    return Double.MAX_VALUE;
                }
            }
        }

        try
        {
            return Double.parseDouble(cleanedRate);
        }
        catch (NumberFormatException ignored)
        {
            return Double.MAX_VALUE;
        }
    }
}
