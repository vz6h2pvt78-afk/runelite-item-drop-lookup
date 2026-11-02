package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.service.MonsterLocationFilter;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService.LocationMatch;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** Sidebar details only: no gameplay, client state, or live data requests. */
public class MonsterLocationPanel extends JPanel
{
    private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 65;
    private final JPanel locationsPanel = column();
    private final JComboBox<WildernessFilter> wilderness = new JComboBox<>(WildernessFilter.values());
    private final JComboBox<MembershipFilter> membership = new JComboBox<>(MembershipFilter.values());
    private final JCheckBox showAll = new JCheckBox("All monster locations");
    private final JButton clear = new JButton("Clear location filters");
    private final LocationMatch match;
    private final List<MonsterLocation> allLocations;

    public MonsterLocationPanel(DropSource source, String searchQuery,
                                MonsterLocationLookupService service, WildernessFilter initialFilter, Runnable back)
    {
        super(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setName("monsterDetail");
        match = service.lookup(source);
        allLocations = service.lookupByMonsterName(source.getMonsterName());
        JPanel header = column();
        JButton backButton = new JButton("Back to results");
        backButton.setName("backToResults");
        backButton.addActionListener(e -> back.run());
        header.add(backButton);
        header.add(text(source.getMonsterName(), true, TEXT_WIDTH));
        header.add(text(source.getItemName(), true, TEXT_WIDTH));
        if (!source.getItemName().equalsIgnoreCase(searchQuery.trim()))
        {
            header.add(text("Searched: " + searchQuery, false, TEXT_WIDTH));
        }
        header.add(text("Rate: " + source.getDropRate() + " | Quantity: " + source.getQuantity(), false, TEXT_WIDTH));
        if (hasText(source.getNotes()))
        {
            header.add(text(source.getNotes(), false, TEXT_WIDTH));
        }

        wilderness.setSelectedItem(initialFilter);
        wilderness.setName("detailWilderness");
        membership.setName("detailMembership");
        wilderness.setToolTipText("Wilderness describes the kill location, not the journey. Unknown locations are excluded by specific filters.");
        membership.setToolTipText("Locations with unknown membership are excluded by specific filters.");
        header.add(wilderness);
        header.add(membership);
        showAll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showAll.setName("allMonsterLocations");
        showAll.setVisible(match.isVerified() && allLocations.stream().anyMatch(location ->
                match.getLocations().stream().noneMatch(matched -> matched.getId().equals(location.getId()))));
        showAll.setToolTipText("Include other drop tables; the item and rate above may not apply there");
        header.add(showAll);
        clear.addActionListener(e -> {
            wilderness.setSelectedItem(WildernessFilter.ALL);
            membership.setSelectedItem(MembershipFilter.ALL);
        });
        header.add(clear);

        for (JComboBox<?> combo : new JComboBox<?>[] {wilderness, membership})
        {
            combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, combo.getPreferredSize().height));
            combo.setAlignmentX(LEFT_ALIGNMENT);
            combo.addActionListener(e -> renderLocations());
        }
        showAll.addActionListener(e -> renderLocations());
        add(header, BorderLayout.NORTH);
        add(locationsPanel, BorderLayout.CENTER);
        renderLocations();
    }

    private void renderLocations()
    {
        locationsPanel.removeAll();
        boolean filtering = wilderness.getSelectedItem() != WildernessFilter.ALL
                || membership.getSelectedItem() != MembershipFilter.ALL;
        clear.setVisible(filtering);
        boolean verifiedView = match.isVerified() && !showAll.isSelected();
        List<MonsterLocation> locations = verifiedView ? match.getLocations() : allLocations;
        if (locations.isEmpty())
        {
            locationsPanel.add(text("No location data available for this monster yet.", true, TEXT_WIDTH));
            locationsPanel.add(text("The item source remains in your search results. Missing location data does not mean the monster is unavailable.", false, TEXT_WIDTH));
        }
        else
        {
            if (!verifiedView)
            {
                locationsPanel.add(text("Monster locations only: this item and rate may not apply at every location.", false, TEXT_WIDTH));
            }
            List<MonsterLocation> filtered = locations.stream().filter(location -> MonsterLocationFilter.matches(
                    location, (WildernessFilter) wilderness.getSelectedItem(), (MembershipFilter) membership.getSelectedItem()))
                    .collect(Collectors.toList());
            locationsPanel.add(text("Locations: " + filtered.size() + " of " + locations.size(), true, TEXT_WIDTH));
            locationsPanel.add(text(filtering ? "Unknown locations excluded. Access requirements may apply."
                    : "Access requirements may apply.", false, TEXT_WIDTH));
            if (filtered.isEmpty())
            {
                locationsPanel.add(text("No known locations match these filters. Clear the filters to include unknown classifications.", false, TEXT_WIDTH));
            }
            filtered.forEach(location -> locationsPanel.add(locationCard(location)));
        }
        revalidate();
        repaint();
    }

    private JPanel locationCard(MonsterLocation location)
    {
        JPanel card = column();
        card.setName("location:" + location.getId());
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(4, 0, 6, 0), new EmptyBorder(8, 8, 8, 8)));
        int width = TEXT_WIDTH - 16;
        card.add(text(location.getLocation(), true, width));
        if (!location.isAvailable())
        {
            card.add(text("Unavailable / unreachable", true, width));
        }
        card.add(text("Level " + (hasText(location.getLevelsText()) ? location.getLevelsText() : "unknown")
                + " · " + (location.getMembers() == null ? "Membership unknown" : location.getMembers() ? "Members" : "F2P"), false, width));
        card.add(text(location.getWilderness() == MonsterLocation.Wilderness.YES ? "Wilderness"
                : location.getWilderness() == MonsterLocation.Wilderness.NO ? "Outside Wilderness" : "Wilderness status unknown", false, width));
        if (location.getSpawns() != null)
        {
            boolean approximate = location.getSpawnCountBasis() != null && location.getSpawnCountBasis().contains("approximate");
            JLabel spawns = text((approximate ? "About " : "") + location.getSpawns()
                    + (location.getSpawns() == 1 ? " spawn" : " spawns"), false, width);
            spawns.setToolTipText(location.getSpawnCountBasis());
            card.add(spawns);
        }
        if (hasText(location.getRequirements()))
        {
            card.add(text("Requirements: " + location.getRequirements(), false, width));
        }
        if (hasText(location.getNotes()))
        {
            card.add(text(location.getNotes(), false, width));
        }
        String url = location.getSourceUrl();
        if (url != null && url.startsWith("https://oldschool.runescape.wiki/w/"))
        {
            JButton wiki = new JButton("Wiki page & maps");
            wiki.addActionListener(e -> LinkBrowser.browse(url));
            card.add(wiki);
        }
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private static JPanel column()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static boolean hasText(String text)
    {
        return text != null && !text.trim().isEmpty();
    }

    private static JLabel text(String value, boolean bold, int width)
    {
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel label = new JLabel("<html><div style='width:" + width + "px;'>"
                + (bold ? "<b>" + escaped + "</b>" : escaped) + "</div></html>");
        label.setBorder(new EmptyBorder(3, 0, 3, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
}
