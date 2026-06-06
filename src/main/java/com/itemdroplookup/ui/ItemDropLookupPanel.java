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

    private final DropLookupService dropLookupService;
    private final ItemSourceLookupService itemSourceLookupService;
    private final ItemPriceLookupService itemPriceLookupService;

    private final JTextField searchField = new JTextField();
    private final JButton searchButton = new JButton("Search");
    private final JComboBox<String> filterDropdown = new JComboBox<>(new String[] {
            FILTER_ALL,
            FILTER_DROPS,
            FILTER_SOURCES
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
            boolean showSources = FILTER_ALL.equals(selectedFilter) || FILTER_SOURCES.equals(selectedFilter);

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

            if (showSources && !itemSources.isEmpty())
            {
                resultsPanel.add(makeSpacer());
                resultsPanel.add(makeSectionHeader("Non-NPC Sources"));

                for (ItemSource source : itemSources)
                {
                    resultsPanel.add(makeItemSourceCard(source));
                }
            }

            if ((showDrops && dropResults.isEmpty()) && (showSources && itemSources.isEmpty()))
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
            else if (FILTER_SOURCES.equals(selectedFilter) && itemSources.isEmpty())
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

        card.add(makeCardTitle(source.getMonsterName()));

        Optional<String> variantLabel = extractVariantLabel(source.getMonsterName());
        variantLabel.ifPresent(label -> card.add(makeCardLine("Variant: " + label)));

        card.add(makeCardLine("Rate: " + source.getDropRate()));
        card.add(makeCardLine("Quantity: " + source.getQuantity()));

        if (source.getNotes() != null && !source.getNotes().isEmpty())
        {
            card.add(makeCardLine("Notes: " + source.getNotes()));
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

        card.add(makeCardTitle(source.getSourceName()));

        if (hasText(source.getSourceType()))
        {
            card.add(makeCardLine("Type: " + source.getSourceType()));
        }

        if (hasText(source.getLocation()))
        {
            card.add(makeCardLine("Location: " + source.getLocation()));
        }

        if (hasText(source.getCost()))
        {
            card.add(makeCardLine("Cost: " + source.getCost()));
        }

        if (hasText(source.getNotes()))
        {
            card.add(makeCardLine("Notes: " + source.getNotes()));
        }

        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));

        return card;
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private JLabel makeSearchPriceSummary(ItemPrice itemPrice)
    {
        String displayName = formatItemName(itemPrice.getItemName());
        String priceLine = formatPriceLine(itemPrice);

        JLabel label = new JLabel(
                "<html><div style='width:180px;'><b>"
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
        JLabel label = new JLabel("<html><div style='width:180px;'><b>" + escapeHtml(text) + "</b></div></html>");
        label.setBorder(new EmptyBorder(8, 0, 6, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel makeLabel(String text)
    {
        JLabel label = new JLabel("<html><div style='width:180px;'>" + escapeHtml(text) + "</div></html>");
        label.setBorder(new EmptyBorder(4, 0, 4, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel makeSummaryLabel(String text)
    {
        JLabel label = new JLabel("<html><div style='width:180px;'>" + escapeHtml(text) + "</div></html>");
        label.setBorder(new EmptyBorder(2, 0, 2, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel makeSectionHeader(String text)
    {
        JLabel label = new JLabel("<html><b>" + escapeHtml(text) + "</b></html>");
        label.setBorder(new EmptyBorder(10, 0, 6, 0));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    private JLabel makeItemHeader(String text)
    {
        JLabel label = new JLabel("<html><b>" + escapeHtml(text) + "</b></html>");
        label.setBorder(new EmptyBorder(10, 0, 6, 0));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
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

    private JLabel makeCardTitle(String text)
    {
        JLabel label = new JLabel("<html><b>" + escapeHtml(text) + "</b></html>");
        label.setBorder(new EmptyBorder(0, 0, 5, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel makeCardLine(String text)
    {
        JLabel label = new JLabel("<html><div style='width:180px;'>" + escapeHtml(text) + "</div></html>");
        label.setBorder(new EmptyBorder(2, 0, 2, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
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