package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.DropSourceGroup;
import com.itemdroplookup.model.ItemPrice;
import com.itemdroplookup.model.ItemSource;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterFamilyGrouper;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemDropLookupPanel extends PluginPanel
{
    private static final int MAX_DISPLAYED_DROP_SOURCES = 100;

    // Captures the inner text of a trailing parenthetical for the display-only
    // "Variant" line, e.g. "Skeleton (Tarn's Lair)" -> "Tarn's Lair". Mirrors the
    // trailing-parenthetical convention used by MonsterFamilyGrouper.
    private static final Pattern TRAILING_PAREN = Pattern.compile("^.+\\s+\\(([^)]+)\\)$");

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
            ItemPriceLookupService itemPriceLookupService
    )
    {
        this.dropLookupService = dropLookupService;
        this.itemSourceLookupService = itemSourceLookupService;
        this.itemPriceLookupService = itemPriceLookupService;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField.setToolTipText("Enter an item name");
        searchField.addActionListener(e -> performSearch());
        searchButton.addActionListener(e -> performSearch());

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        filterDropdown.setToolTipText("Choose which source types to show");
        filterDropdown.addActionListener(e -> performSearch());
        filterDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, filterDropdown.getPreferredSize().height));

        showPriceCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showPriceCheckbox.setToolTipText("Show Grand Exchange price data when available");
        showPriceCheckbox.addActionListener(e -> performSearch());
        showPriceCheckbox.setMaximumSize(new Dimension(Integer.MAX_VALUE, showPriceCheckbox.getPreferredSize().height));

        topPanel.add(searchPanel);
        topPanel.add(makeSmallSpacer());
        topPanel.add(filterDropdown);
        topPanel.add(makeSmallSpacer());
        topPanel.add(showPriceCheckbox);

        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(topPanel, BorderLayout.NORTH);
        add(resultsPanel, BorderLayout.CENTER);
    }

    private void performSearch()
    {
        resultsPanel.removeAll();

        String itemName = searchField.getText().trim();
        String selectedFilter = (String) filterDropdown.getSelectedItem();

        if (itemName.isEmpty())
        {
            resultsPanel.add(makeLabel("Enter an item name."));
        }
        else
        {
            List<DropSource> dropResults = dropLookupService.searchByItemName(itemName);
            List<ItemSource> itemSources = itemSourceLookupService.searchByItemName(itemName);

            boolean showDrops = FILTER_ALL.equals(selectedFilter) || FILTER_DROPS.equals(selectedFilter);
            boolean showSources = FILTER_ALL.equals(selectedFilter)
                    || FILTER_SOURCES.equals(selectedFilter)
                    || FILTER_SHOPS.equals(selectedFilter)
                    || FILTER_REWARD_SHOPS.equals(selectedFilter);

            // Narrow the non-NPC sources to the selected source type. For All and
            // Non-NPC Sources this is the full list; the Shops / Reward Shops
            // filters keep only rows whose sourceType matches exactly.
            List<ItemSource> displaySources;
            if (FILTER_SHOPS.equals(selectedFilter))
            {
                displaySources = filterBySourceType(itemSources, SOURCE_TYPE_SHOP);
            }
            else if (FILTER_REWARD_SHOPS.equals(selectedFilter))
            {
                displaySources = filterBySourceType(itemSources, SOURCE_TYPE_REWARD_SHOP);
            }
            else
            {
                displaySources = itemSources;
            }

            if (showPriceCheckbox.isSelected())
            {
                Optional<ItemPrice> itemPrice = itemPriceLookupService.searchByItemName(itemName);

                if (!itemPrice.isPresent() && !dropResults.isEmpty())
                {
                    itemPrice = itemPriceLookupService.searchByItemName(dropResults.get(0).getItemName());
                }

                if (itemPrice.isPresent())
                {
                    resultsPanel.add(makeSearchPriceSummary(itemPrice.get()));
                    resultsPanel.add(makeSpacer());
                }
            }

            if (showDrops && !dropResults.isEmpty())
            {
                displayDropResults(dropResults);
            }

            if (showSources && !displaySources.isEmpty())
            {
                resultsPanel.add(makeSpacer());
                resultsPanel.add(makeSectionHeader(sourceSectionHeading(selectedFilter)));
                resultsPanel.add(makeSummaryLabel(sourceCountSummary(selectedFilter, displaySources.size())));

                for (ItemSource source : displaySources)
                {
                    resultsPanel.add(makeItemSourceCard(source));
                }
            }

            if ((showDrops && dropResults.isEmpty()) && (showSources && displaySources.isEmpty()))
            {
                resultsPanel.add(makeNoResultHeader("No source found for: " + itemName));
                resultsPanel.add(makeLabel("No NPC drop or known non-NPC source is currently in the local data."));
                resultsPanel.add(makeLabel("Possible sources: clues, shops, skilling, minigames, quests, spawns, or reward chests."));
                resultsPanel.add(makeLabel("Try a more specific item name, or check the OSRS Wiki."));
            }
            else if (FILTER_DROPS.equals(selectedFilter) && dropResults.isEmpty())
            {
                resultsPanel.add(makeNoResultHeader("No NPC drop found for: " + itemName));
                resultsPanel.add(makeLabel("This item may not come from a normal monster drop."));
            }
            else if (FILTER_SHOPS.equals(selectedFilter) && displaySources.isEmpty())
            {
                resultsPanel.add(makeNoResultHeader("No shop source found for: " + itemName));
                resultsPanel.add(makeLabel("This item may still exist, but no shop entry is currently in the local data."));
            }
            else if (FILTER_REWARD_SHOPS.equals(selectedFilter) && displaySources.isEmpty())
            {
                resultsPanel.add(makeNoResultHeader("No reward shop source found for: " + itemName));
                resultsPanel.add(makeLabel("This item may still exist, but no reward shop entry is currently in the local data."));
            }
            else if (FILTER_SOURCES.equals(selectedFilter) && displaySources.isEmpty())
            {
                resultsPanel.add(makeNoResultHeader("No non-NPC source found for: " + itemName));
                resultsPanel.add(makeLabel("This item may still exist, but no shop/reward/source entry is currently in the local data."));
            }
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();
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

        resultsPanel.add(makeSummaryLabel("Matched items: " + resultsByItem.size()));
        resultsPanel.add(makeSummaryLabel("Drop sources: " + results.size()));

        if (results.size() > MAX_DISPLAYED_DROP_SOURCES)
        {
            resultsPanel.add(makeSummaryLabel("Showing first " + MAX_DISPLAYED_DROP_SOURCES + " drop sources."));
            resultsPanel.add(makeSummaryLabel("Refine your search for more specific results."));
        }

        resultsPanel.add(makeSpacer());

        int displayedDropSources = 0;

        for (Map.Entry<String, List<DropSource>> itemEntry : resultsByItem.entrySet())
        {
            if (displayedDropSources >= MAX_DISPLAYED_DROP_SOURCES)
            {
                break;
            }

            resultsPanel.add(makeItemHeader(formatItemName(itemEntry.getKey())));

            List<DropSource> sortedSources = itemEntry.getValue().stream()
                    .sorted(Comparator.comparingDouble(this::getRateSortValue))
                    .collect(java.util.stream.Collectors.toList());

            List<DropSourceGroup> groups = MonsterFamilyGrouper.group(sortedSources);
            groups.sort(Comparator.comparingDouble((DropSourceGroup g) -> getRateSortValue(g.getMembers().get(0))));

            for (DropSourceGroup group : groups)
            {
                List<DropSource> members = group.getMembers();
                int memberCount = members.size();

                if (displayedDropSources + memberCount > MAX_DISPLAYED_DROP_SOURCES)
                {
                    break;
                }

                if (shouldRenderAsGroup(group))
                {
                    resultsPanel.add(makeGroupContainer(group));
                    displayedDropSources += memberCount;
                }
                else
                {
                    for (DropSource member : members)
                    {
                        resultsPanel.add(makeDropCard(member, false));
                    }
                    displayedDropSources += memberCount;
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

        JLabel header = new JLabel("<html><b>" + escapeHtml(collapsedText) + "</b></html>");
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

        for (DropSource member : group.getMembers())
        {
            membersPanel.add(makeDropCard(member, true));
        }

        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                boolean expand = !membersPanel.isVisible();
                membersPanel.setVisible(expand);
                header.setText("<html><b>" + escapeHtml(expand ? expandedText : collapsedText) + "</b></html>");
                resultsPanel.revalidate();
                resultsPanel.repaint();
            }
        });

        container.add(header);
        container.add(membersPanel);

        return container;
    }

    /**
     * Extracts the trailing-parenthetical context from a raw monster name for display
     * as a "Variant" line, e.g. "Skeleton (Tarn's Lair)" -> "Tarn's Lair".
     * Returns empty when there is no trailing parenthetical. The raw monster name is
     * never modified or hidden; this is a display-only derived value.
     */
    private Optional<String> extractVariantLabel(String monsterName)
    {
        if (monsterName == null)
        {
            return Optional.empty();
        }

        Matcher matcher = TRAILING_PAREN.matcher(monsterName.trim());

        if (matcher.matches())
        {
            String variant = matcher.group(1).trim();

            if (!variant.isEmpty())
            {
                return Optional.of(variant);
            }
        }

        return Optional.empty();
    }

    private JPanel makeDropCard(DropSource source)
    {
        return makeDropCard(source, false);
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

        Optional<String> variantLabel = extractVariantLabel(source.getMonsterName());
        variantLabel.ifPresent(label -> card.add(makeCardLine("Variant: " + label, wrapWidth)));

        card.add(makeCardLine("Rate: " + source.getDropRate(), wrapWidth));
        card.add(makeCardLine("Quantity: " + source.getQuantity(), wrapWidth));

        if (source.getNotes() != null && !source.getNotes().isEmpty())
        {
            card.add(makeCardLine("Notes: " + source.getNotes(), wrapWidth));
        }

        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));

        return card;
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

        card.add(makeCardTitle(formatItemName(source.getItemName()), CARD_TEXT_WIDTH));

        if (hasText(source.getSourceName()))
        {
            card.add(makeCardLine("Source: " + source.getSourceName(), CARD_TEXT_WIDTH));
        }

        if (hasText(source.getSourceType()))
        {
            card.add(makeCardLine("Type: " + source.getSourceType(), CARD_TEXT_WIDTH));
        }

        if (hasText(source.getLocation()))
        {
            card.add(makeCardLine("Location: " + source.getLocation(), CARD_TEXT_WIDTH));
        }

        if (hasText(source.getCost()))
        {
            card.add(makeCardLine("Cost: " + source.getCost(), CARD_TEXT_WIDTH));
        }

        if (hasText(source.getNotes()))
        {
            card.add(makeCardLine("Notes: " + source.getNotes(), CARD_TEXT_WIDTH));
        }

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
     * Summary count line for the non-NPC source block, labelled to match the active
     * filter so the count is not mistaken for a drop-source count.
     */
    private String sourceCountSummary(String selectedFilter, int count)
    {
        if (FILTER_SHOPS.equals(selectedFilter))
        {
            return "Shop sources: " + count;
        }

        if (FILTER_REWARD_SHOPS.equals(selectedFilter))
        {
            return "Reward shop sources: " + count;
        }

        return "Non-NPC sources: " + count;
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
        String displayName = formatItemName(itemPrice.getItemName());
        String priceLine = formatPriceLine(itemPrice);

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

        return "GE: " + formatNumber(itemPrice.getPrice()) + " gp";
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
        JLabel label = new JLabel(" ");
        label.setBorder(new EmptyBorder(3, 0, 3, 0));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private JLabel makeSmallSpacer()
    {
        JLabel label = new JLabel(" ");
        label.setBorder(new EmptyBorder(1, 0, 1, 0));
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
