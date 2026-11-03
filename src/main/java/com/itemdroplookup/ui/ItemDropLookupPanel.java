package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.DropSourceGroup;
import com.itemdroplookup.model.ItemPrice;
import com.itemdroplookup.model.ItemSource;
import com.itemdroplookup.model.AccessRequirement;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterFamilyGrouper;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
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

    // Exact sourceType values matched by the Shops / Reward Shops filters. These
    // must match the data verbatim — no fuzzy matching.
    private static final String SOURCE_TYPE_SHOP = "Shop";
    private static final String SOURCE_TYPE_REWARD_SHOP = "Reward Shop";

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
    private final JPanel searchView = new JPanel(new BorderLayout());
    private final JComboBox<WildernessFilter> wildernessDropdown = new JComboBox<>(WildernessFilter.values());
    private int searchScrollPosition;
    private String displayedQuery = "";
    private final JScrollPane resultsScroll = new JScrollPane();
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
    private final JComboBox<String> filterDropdown = new JComboBox<>(new String[] {
            FILTER_ALL,
            FILTER_DROPS,
            FILTER_SOURCES,
            FILTER_SHOPS,
            FILTER_REWARD_SHOPS
    });
    private final JCheckBox showPriceCheckbox = new JCheckBox("Show GE price", true);
    private final JPanel resultsPanel = new JPanel();

    public ItemDropLookupPanel(
            DropLookupService dropLookupService,
            ItemSourceLookupService itemSourceLookupService,
            ItemPriceLookupService itemPriceLookupService,
            MonsterLocationLookupService monsterLocationLookupService,
            AccessRequirementLookupService accessRequirementLookupService
    )
    {
        super(false);
        this.dropLookupService = dropLookupService;
        this.itemSourceLookupService = itemSourceLookupService;
        this.itemPriceLookupService = itemPriceLookupService;
        this.monsterLocationLookupService = monsterLocationLookupService;
        this.accessRequirementLookupService = accessRequirementLookupService;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField.setToolTipText("Enter an item name and press Enter");
        searchField.getAccessibleContext().setAccessibleName("Item name");
        searchField.setName("itemSearch");
        filterDropdown.setName("sourceTypeFilter");
        searchButton.setName("searchButton");
        searchField.addActionListener(e -> performSearch());
        searchButton.addActionListener(e -> performSearch());

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        filterDropdown.setToolTipText("Choose which source types to show");
        filterDropdown.addActionListener(e -> performSearch());
        filterDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, filterDropdown.getPreferredSize().height));

        showPriceCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showPriceCheckbox.setName("showPrice");
        showPriceCheckbox.setToolTipText("Show the bundled GE price snapshot; prices are not live");
        showPriceCheckbox.addActionListener(e -> {
            int position = resultsScroll.getVerticalScrollBar().getValue();
            priceLabels.forEach(label -> label.setVisible(showPriceCheckbox.isSelected()));
            resultsPanel.revalidate();
            resultsPanel.repaint();
            SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(position));
        });
        showPriceCheckbox.setMaximumSize(new Dimension(Integer.MAX_VALUE, showPriceCheckbox.getPreferredSize().height));

        topPanel.add(searchPanel);
        topPanel.add(makeSmallSpacer());
        topPanel.add(filterDropdown);
        topPanel.add(makeSmallSpacer());
        wildernessDropdown.setName("npcWildernessFilter");
        wildernessDropdown.setToolTipText("Filter NPC drops by verified usable locations. Shops and other sources are unchanged.");
        wildernessDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, wildernessDropdown.getPreferredSize().height));
        wildernessDropdown.addActionListener(e -> performSearch());
        npcFilters.setBackground(ColorScheme.DARK_GRAY_COLOR);
        npcFilters.add(new JLabel("NPCs"), BorderLayout.WEST);
        npcFilters.add(wildernessDropdown, BorderLayout.CENTER);
        topPanel.add(npcFilters);
        topPanel.add(makeSmallSpacer());
        topPanel.add(showPriceCheckbox);
        sourceSearchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sourceSearchRow.add(new JLabel("Find source"), BorderLayout.WEST);
        sourceSearchRow.add(sourceSearch, BorderLayout.CENTER);
        sourceSearch.setName("sourceSearch");
        sourceSearch.setToolTipText("Narrow these results by monster, shop or shop location; clear to show all");
        sourceSearch.getAccessibleContext().setAccessibleName("Find a monster, shop or shop location in results");
        sourceSearchTimer.setRepeats(false);
        sourceSearch.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { sourceSearchTimer.restart(); }
            public void removeUpdate(DocumentEvent e) { sourceSearchTimer.restart(); }
            public void changedUpdate(DocumentEvent e) { sourceSearchTimer.restart(); }
        });
        sourceSearch.addActionListener(e -> { sourceSearchTimer.stop(); renderResults(); });
        sourceSearchRow.setVisible(false);
        topPanel.add(sourceSearchRow);

        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchView.setBackground(ColorScheme.DARK_GRAY_COLOR);
        searchView.add(topPanel, BorderLayout.NORTH);
        JPanel resultBody = new JPanel(new BorderLayout());
        resultBody.setBackground(ColorScheme.DARK_GRAY_COLOR);
        resultBody.add(resultsPanel, BorderLayout.NORTH);
        resultsScroll.setViewportView(resultBody);
        resultsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        resultsScroll.setBorder(null);
        resultsScroll.getVerticalScrollBar().setUnitIncrement(24);
        searchView.add(resultsScroll, BorderLayout.CENTER);
        add(searchView, BorderLayout.CENTER);
        resultsPanel.add(makeLabel("Search an item to find monster drops, shops and rewards."));
    }

    @Override
    protected JScrollPane getScrollPane()
    {
        return detail == null ? resultsScroll : detail.getLocationScrollPane();
    }

    private boolean adjustingControls;

    private void performSearch()
    {
        if (adjustingControls)
        {
            return;
        }
        String query = searchField.getText().trim();
        if (!query.equals(displayedQuery))
        {
            displayedQuery = query;
            java.util.Optional<String> potionFamily = itemPriceLookupService.findPotionFamily(query);
            boolean allDoses = potionFamily.isPresent() && !query.matches(".*\\([1-4]\\)$");
            String lookupQuery = allDoses ? potionFamily.get() : query;
            matchingDrops = dropLookupService.searchByItemName(lookupQuery);
            matchingSources = itemSourceLookupService.searchByItemName(lookupQuery);
            // Services rank independently. If either finds the exact item, do not
            // mix in another service's broader fallback (e.g. blighted potions).
            String exactName = compactItemName(query);
            boolean exactMatch = matchingDrops.stream().anyMatch(source -> compactItemName(source.getItemName()).equals(exactName))
                    || matchingSources.stream().anyMatch(source -> compactItemName(source.getItemName()).equals(exactName));
            if (potionFamily.isPresent())
            {
                matchingDrops = matchingDrops.stream().filter(source -> itemPriceLookupService.findPotionFamily(source.getItemName()).equals(potionFamily)
                        && (allDoses || compactItemName(source.getItemName()).equals(exactName))).collect(Collectors.toList());
                matchingSources = matchingSources.stream().filter(source -> itemPriceLookupService.findPotionFamily(source.getItemName()).equals(potionFamily)
                        && (allDoses || compactItemName(source.getItemName()).equals(exactName))).collect(Collectors.toList());
            }
            else if (exactMatch)
            {
                matchingDrops = matchingDrops.stream().filter(source -> compactItemName(source.getItemName()).equals(exactName)).collect(Collectors.toList());
                matchingSources = matchingSources.stream().filter(source -> compactItemName(source.getItemName()).equals(exactName)).collect(Collectors.toList());
            }
            sourceSearch.setText("");
            sourceSearchTimer.stop();
        }
        renderResults();
    }

    private String compactItemName(String name)
    {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void renderResults()
    {
        if (detail != null)
        {
            return;
        }
        resultsPanel.removeAll();
        priceLabels.clear();
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
            List<DropSource> dropResults = showDrops ? matchingDrops.stream()
                    .filter(source -> monsterLocationLookupService.hasQualifyingLocation(source, locationFilter))
                    .filter(source -> contains(source.getMonsterName(), sourceQuery))
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
            List<ItemSource> displaySources = showSources ? sources.stream()
                    .filter(source -> contains(source.getSourceName(), sourceQuery) || contains(source.getLocation(), sourceQuery)
                            || contains(source.getPackName(), sourceQuery))
                    .collect(Collectors.toList()) : Collections.emptyList();
            boolean hasPacks = displaySources.stream().anyMatch(source -> source.getPackQuantity() > 0);
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
            java.util.Optional<String> potionFamily = itemPriceLookupService.findPotionFamily(displayedQuery);
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
                if (!displayedQuery.matches(".*\\([1-4]\\)$"))
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
                    resultsPanel.add(makeNoResultHeader("No sources match these filters."));
                    resultsPanel.add(makeLabel("This item has other sources in the local data."));
                    JButton reset = new JButton("Show all sources");
                    reset.setName("resetSourceFilters");
                    reset.addActionListener(e -> {
                        adjustingControls = true;
                        filterDropdown.setSelectedItem(FILTER_ALL);
                        wildernessDropdown.setSelectedItem(WildernessFilter.ALL);
                        sourceSearch.setText("");
                        adjustingControls = false;
                        sourceSearchTimer.stop();
                        renderResults();
                    });
                    resultsPanel.add(reset);
                }
                else
                {
                    resultsPanel.add(makeNoResultHeader("No source found for: " + displayedQuery));
                    resultsPanel.add(makeLabel("No match in the local data. Check the spelling or try a shorter item name."));
                }
                resultsPanel.add(wikiButton(displayedQuery));
            }
        }
        resultsPanel.revalidate();
        resultsPanel.repaint();
        revalidate();
        SwingUtilities.invokeLater(() -> resultsScroll.getVerticalScrollBar().setValue(0));
    }

    private boolean contains(String value, String query)
    {
        return query.isEmpty() || value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private JButton wikiButton(String item)
    {
        JButton wiki = new JButton("Search item on Wiki");
        wiki.setName("itemWiki");
        wiki.addActionListener(e -> LinkBrowser.browse("https://oldschool.runescape.wiki/w/Special:Search?search="
                + URLEncoder.encode(item, StandardCharsets.UTF_8)));
        return wiki;
    }

    private void displayDropResults(List<DropSource> results)
    {
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
            resultsPanel.add(makeItemHeader(formatItemName(itemEntry.getKey())));

            List<DropSource> sortedSources = itemEntry.getValue().stream()
                    .sorted(Comparator.comparingDouble(this::getRateSortValue))
                    .collect(java.util.stream.Collectors.toList());

            List<DropSourceGroup> groups = MonsterFamilyGrouper.group(sortedSources);
            groups.sort(Comparator.comparingDouble((DropSourceGroup g) -> getRateSortValue(g.getMembers().get(0))));

            for (DropSourceGroup group : groups)
            {
                List<DropSource> members = group.getMembers();
                if (shouldRenderAsGroup(group))
                {
                    resultsPanel.add(makeGroupContainer(group));
                }
                else
                {
                    for (DropSource member : members)
                    {
                        resultsPanel.add(makeDropCard(member, false));
                    }
                }
            }

            resultsPanel.add(makeSpacer());
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
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);
        container.setAlignmentX(LEFT_ALIGNMENT);
        container.setBorder(new EmptyBorder(0, 0, 8, 0));

        int memberCount = group.getMembers().size();
        String collapsedText = "▸ " + group.getParentName() + " (" + memberCount + ")";
        String expandedText  = "▾ " + group.getParentName() + " (" + memberCount + ")";

        JButton header = new JButton("<html><b>" + escapeHtml(collapsedText) + "</b></html>");
        header.setName("sourceGroup:" + group.getParentName());
        header.setHorizontalAlignment(JButton.LEFT);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setOpaque(true);
        header.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(8, 8, 6, 8)
        ));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));

        JPanel membersPanel = new JPanel();
        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        membersPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        membersPanel.setAlignmentX(LEFT_ALIGNMENT);
        membersPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(0, 8, 6, 0)
        ));
        membersPanel.setVisible(false);

        header.addActionListener(e -> {
            boolean expand = !membersPanel.isVisible();
            if (expand && membersPanel.getComponentCount() == 0)
            {
                group.getMembers().forEach(member -> membersPanel.add(makeDropCard(member, true)));
            }
            membersPanel.setVisible(expand);
            header.setText("<html><b>" + escapeHtml(expand ? expandedText : collapsedText) + "</b></html>");
            resultsPanel.revalidate();
            resultsPanel.repaint();
        });

        container.add(header);
        container.add(membersPanel);

        return container;
    }

    private JPanel makeDropCard(DropSource source, boolean nested)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);

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
                            BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
                            new EmptyBorder(8, 8, 8, 8)
                    )
            ));
        }

        int wrapWidth = nested ? NESTED_CARD_TEXT_WIDTH : CARD_TEXT_WIDTH;

        card.add(makeCardTitle(source.getMonsterName(), wrapWidth));

        card.add(makeCardLine("Rate: " + (hasText(source.getDropRate()) ? source.getDropRate() : "Not recorded"), wrapWidth));
        card.add(makeCardLine("Quantity: " + source.getQuantity(), wrapWidth));

        if (source.getNotes() != null && !source.getNotes().isEmpty())
        {
            card.add(makeCardLine("Notes: " + source.getNotes(), wrapWidth));
        }

        for (AccessRequirement fact : accessRequirementLookupService.forSource(source))
        {
            if (fact.isSourceCardRequirement())
            {
                JLabel requirement = makeCardLine(fact.getLabel() + ": " + fact.getText(), wrapWidth);
                requirement.setName("requirement:" + fact.getId());
                card.add(requirement);
            }
        }

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
        card.addMouseListener(openDetail);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        for (Component component : card.getComponents())
        {
            component.addMouseListener(openDetail);
        }
        JButton locations = new JButton("Locations & requirements");
        locations.setMargin(new java.awt.Insets(3, 4, 3, 4));
        locations.setName("openLocations");
        locations.getAccessibleContext().setAccessibleName("Locations and requirements for " + source.getMonsterName());
        locations.addActionListener(e -> showMonsterDetails(source));
        card.add(locations);

        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));

        return card;
    }

    private void showMonsterDetails(DropSource source)
    {
        sourceSearchTimer.stop();
        searchScrollPosition = resultsScroll.getVerticalScrollBar().getValue();
        remove(searchView);
        detail = new MonsterLocationPanel(source, displayedQuery, monsterLocationLookupService, accessRequirementLookupService,
                (WildernessFilter) wildernessDropdown.getSelectedItem(), this::showSearchResults);
        add(detail, BorderLayout.CENTER);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> getScrollPane().getVerticalScrollBar().setValue(0));
    }

    private void showSearchResults()
    {
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
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(4, 0, 6, 0),
                new EmptyBorder(8, 8, 8, 8)
        ));

        card.setName("itemSourceCard");
        card.add(makeCardTitle(hasText(source.getSourceName()) ? source.getSourceName() : source.getSourceType(), CARD_TEXT_WIDTH));
        boolean pack = source.getPackQuantity() > 0;
        card.add(makeCardLine(formatItemName(pack ? source.getPackName() : source.getItemName()), CARD_TEXT_WIDTH));
        if (pack)
        {
            card.add(makeCardLine("Contains: " + source.getPackQuantity() + " × " + source.getPackItemName()
                    + (source.isPackNoted() ? " (noted)" : ""), CARD_TEXT_WIDTH));
        }

        if (hasText(source.getSourceType()))
        {
            card.add(makeCardLine("Type: " + source.getSourceType(), CARD_TEXT_WIDTH));
        }

        if (hasText(source.getLocation()))
        {
            card.add(makeCardLine("Location: " + source.getLocation(), CARD_TEXT_WIDTH));
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

        if (hasText(source.getNotes()))
        {
            String notes = unverifiedCurrency ? source.getNotes().replace("Cost increases as stock depletes.", "").trim() : source.getNotes();
            if (hasText(notes))
            {
                card.add(makeCardLine("Notes: " + notes, CARD_TEXT_WIDTH));
            }
        }

        card.add(wikiButton(pack ? source.getPackName() : source.getItemName()));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));

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
        String body = escapeHtml(text);
        if (bold)
        {
            body = "<b>" + body + "</b>";
        }

        JLabel label = new JLabel("<html><div style='width:" + wrapWidth + "px;'>" + body + "</div></html>");
        label.setBorder(border);
        label.setAlignmentX(LEFT_ALIGNMENT);
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

        JLabel label = new JLabel(
                "<html><div style='width:" + PANEL_TEXT_WIDTH + "px;'><b>"
                        + escapeHtml(displayName)
                        + "</b><br>"
                        + escapeHtml(priceLine)
                        + "</div></html>"
        );

        label.setBorder(new EmptyBorder(8, 0, 6, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
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
        return makeWrappedLabel(text, PANEL_TEXT_WIDTH, true, new EmptyBorder(10, 0, 6, 0));
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
