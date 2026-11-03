package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.AccessRequirement;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.MonsterLocationFilter;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService.LocationMatch;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** Sidebar details only: no gameplay, client state, or live data requests. */
public class MonsterLocationPanel extends JPanel
{
    private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 65;
    private final JPanel locationsPanel = column();
    private final JScrollPane locationScroll = new JScrollPane();
    private final JComboBox<WildernessFilter> wilderness = new IntentionalComboBox<>(WildernessFilter.values());
    private final JComboBox<MembershipFilter> membership = new IntentionalComboBox<>(MembershipFilter.values());
    private final JCheckBox showAll = new JCheckBox("Other locations");
    private final JButton clear = new JButton("Clear location filters");
    private final LocationMatch match;
    private final List<MonsterLocation> allLocations;
    private final String dropNotes;
    private final String monsterWikiUrl;
    private final AccessRequirementLookupService requirements;
    private final List<AccessRequirement> sourceFacts;
    private final DropSource source;
    private final LookupStyle style;
    private boolean filtersExpanded;
    private boolean adjusting;

    static final class State
    {
        WildernessFilter wilderness;
        MembershipFilter membership;
        boolean allLocations;
        boolean filtersExpanded;
        int scroll;
    }

    State saveState()
    {
        State state = new State();
        state.wilderness = (WildernessFilter) wilderness.getSelectedItem();
        state.membership = (MembershipFilter) membership.getSelectedItem();
        state.allLocations = showAll.isSelected();
        state.filtersExpanded = filtersExpanded;
        state.scroll = locationScroll.getVerticalScrollBar().getValue();
        return state;
    }

    void restoreState(State state)
    {
        if (state == null) return;
        adjusting = true;
        wilderness.setSelectedItem(state.wilderness);
        membership.setSelectedItem(state.membership);
        showAll.setSelected(state.allLocations);
        filtersExpanded = state.filtersExpanded;
        adjusting = false;
        renderLocations();
        javax.swing.SwingUtilities.invokeLater(() -> locationScroll.getVerticalScrollBar().setValue(state.scroll));
    }

    public MonsterLocationPanel(DropSource source, String searchQuery,
                                MonsterLocationLookupService service, AccessRequirementLookupService requirements,
                                WildernessFilter initialFilter, Runnable back)
    {
        this(source, searchQuery, service, requirements, initialFilter, back, new LookupStyle(13));
    }

    MonsterLocationPanel(DropSource source, String searchQuery,
                         MonsterLocationLookupService service, AccessRequirementLookupService requirements,
                         WildernessFilter initialFilter, Runnable back, LookupStyle style)
    {
        super(new BorderLayout());
        this.style = style;
        setBackground(LookupStyle.BACK);
        setName("monsterDetail");
        this.source = source;
        match = service.lookup(source);
        this.requirements = requirements;
        sourceFacts = requirements.forSource(source);
        dropNotes = source.getNotes();
        monsterWikiUrl = "https://oldschool.runescape.wiki/w/"
                + URLEncoder.encode(source.getMonsterName().replace(' ', '_'), StandardCharsets.UTF_8);
        allLocations = service.lookupByMonsterName(source.getMonsterName());
        JPanel header = column();
        JButton backButton = style.button("Back to results", back, false);
        backButton.setName("backToResults");
        header.add(backButton);
        header.add(text(source.getMonsterName(), true, TEXT_WIDTH));
        header.add(text(source.getItemName(), true, TEXT_WIDTH));
        if (!source.getItemName().equalsIgnoreCase(searchQuery.trim()))
        {
            header.add(text("Searched: " + searchQuery, false, TEXT_WIDTH));
        }

        wilderness.setSelectedItem(initialFilter);
        wilderness.setName("detailWilderness");
        membership.setName("detailMembership");
        wilderness.setToolTipText("Wilderness describes the kill location, not the journey. Unknown locations are excluded by specific filters.");
        membership.setToolTipText("Drop world restrictions also apply. Unknown location membership is excluded by specific filters.");
        showAll.setBackground(LookupStyle.BACK);
        showAll.setName("allMonsterLocations");
        showAll.setVisible(match.isVerified() && allLocations.stream().anyMatch(location ->
                match.getLocations().stream().noneMatch(matched -> matched.getId().equals(location.getId()))));
        showAll.setToolTipText("Include other drop tables; the item and rate above may not apply there");
        clear.setName("clearLocationFilters");
        clear.addActionListener(e -> {
            wilderness.setSelectedItem(WildernessFilter.ALL);
            membership.setSelectedItem(MembershipFilter.ALL);
        });

        for (JComboBox<?> combo : new JComboBox<?>[] {wilderness, membership})
        {
            style.control(combo);
            combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, combo.getPreferredSize().height));
            combo.setAlignmentX(LEFT_ALIGNMENT);
            combo.addActionListener(e -> renderLocations());
        }
        style.control(showAll);
        style.control(clear);
        showAll.addActionListener(e -> renderLocations());
        add(header, BorderLayout.NORTH);
        JPanel body = LookupStyle.scrollBody(locationsPanel);
        locationScroll.setViewportView(body);
        locationScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        locationScroll.setBorder(null);
        locationScroll.getVerticalScrollBar().setUnitIncrement(24);
        add(locationScroll, BorderLayout.CENTER);
        renderLocations();
    }

    JScrollPane getLocationScrollPane()
    {
        return locationScroll;
    }

    private void renderLocations()
    {
        if (adjusting) return;
        locationsPanel.removeAll();
        if (!sourceFacts.isEmpty())
        {
            locationsPanel.add(text("Requirements & context", true, TEXT_WIDTH));
            sourceFacts.forEach(fact -> locationsPanel.add(requirementText(fact, TEXT_WIDTH)));
        }
        locationsPanel.add(text("Rate: " + (hasText(source.getDropRate()) ? source.getDropRate() : "Not recorded")
                + " | Quantity: " + source.getQuantity(), false, TEXT_WIDTH));
        JButton toggle = style.button((filtersExpanded ? "Hide location filters" : "Location filters")
                + (wilderness.getSelectedItem() != WildernessFilter.ALL ? "\n" + wilderness.getSelectedItem() : "")
                + (membership.getSelectedItem() != MembershipFilter.ALL ? "\n" + membership.getSelectedItem() : ""), () -> {
            filtersExpanded = !filtersExpanded;
            renderLocations();
        }, false);
        toggle.setName("toggleLocationFilters");
        locationsPanel.add(toggle);
        JPanel filters = column();
        filters.add(wilderness);
        filters.add(membership);
        filters.add(showAll);
        filters.add(clear);
        filters.setVisible(filtersExpanded);
        locationsPanel.add(filters);
        if (hasText(dropNotes))
        {
            locationsPanel.add(text(dropNotes, false, TEXT_WIDTH));
        }
        boolean membersDrop = Boolean.TRUE.equals(source.getMembersOnly());
        if (membersDrop)
        {
            JLabel world = text("This drop requires a members world, even at locations accessible in F2P.", false, TEXT_WIDTH);
            world.setName("dropWorldRestriction");
            locationsPanel.add(world);
        }
        else if (source.getMembersOnly() == null && membership.getSelectedItem() == MembershipFilter.F2P)
        {
            locationsPanel.add(text("F2P-accessible areas; this drop's world availability has not been verified.", false, TEXT_WIDTH));
        }
        boolean filtering = wilderness.getSelectedItem() != WildernessFilter.ALL
                || membership.getSelectedItem() != MembershipFilter.ALL;
        boolean verifiedView = match.isVerified() && !showAll.isSelected();
        List<MonsterLocation> locations = verifiedView ? match.getLocations() : allLocations;
        wilderness.setVisible(!locations.isEmpty());
        membership.setVisible(!locations.isEmpty());
        clear.setVisible(filtering && !locations.isEmpty());
        if (locations.isEmpty())
        {
            locationsPanel.add(text("No location data available for this monster yet.", true, TEXT_WIDTH));
            locationsPanel.add(text("The item source remains in your search results. Missing location data does not mean the monster is unavailable.", false, TEXT_WIDTH));
            JButton wiki = style.button("View on OSRS Wiki", () -> LinkBrowser.browse(monsterWikiUrl), false);
            wiki.setName("monsterWiki");
            wiki.setToolTipText(monsterWikiUrl);
            locationsPanel.add(wiki);
        }
        else
        {
            if (!verifiedView)
            {
                locationsPanel.add(text("Monster locations only: this item and rate may not apply at every location.", false, TEXT_WIDTH));
            }
            List<MonsterLocation> filtered = locations.stream().filter(location -> MonsterLocationFilter.matchesSource(
                    source, location, (WildernessFilter) wilderness.getSelectedItem(), (MembershipFilter) membership.getSelectedItem()))
                    .collect(Collectors.toList());
            locationsPanel.add(text("Locations: " + filtered.size() + " of " + locations.size(), true, TEXT_WIDTH));
            if (filtering)
            {
                locationsPanel.add(text("Unknown locations excluded.", false, TEXT_WIDTH));
            }
            if (filtered.isEmpty())
            {
                locationsPanel.add(text(membersDrop && membership.getSelectedItem() == MembershipFilter.F2P
                        ? "No F2P acquisition locations for this drop. Choose Any membership to see its locations."
                        : "No known locations match these filters. Clear the filters to include unknown classifications.", false, TEXT_WIDTH));
            }
            filtered.forEach(location -> locationsPanel.add(locationCard(location)));
        }
        JLabel coverage = text("Requirements coverage is incomplete; other conditions may apply.", false, TEXT_WIDTH);
        coverage.setName("requirementsCoverage");
        locationsPanel.add(coverage);
        revalidate();
        repaint();
        javax.swing.SwingUtilities.invokeLater(() -> locationScroll.getVerticalScrollBar().setValue(0));
    }

    private JPanel locationCard(MonsterLocation location)
    {
        JPanel card = column();
        card.setName("location:" + location.getId());
        card.setBackground(LookupStyle.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(4, 0, 6, 0), new EmptyBorder(8, 8, 8, 8)));
        int width = TEXT_WIDTH - 16;
        card.add(text(location.getLocation(), true, width));
        if (!location.isAvailable())
        {
            card.add(text("Unavailable / unreachable", true, width));
        }
        card.add(text("Combat level " + (hasText(location.getLevelsText()) ? location.getLevelsText() : "unknown")
                + " · " + (location.getMembers() == null ? "Membership unknown" : location.getMembers() ? "Members area"
                        : Boolean.TRUE.equals(source.getMembersOnly()) ? "F2P-accessible area; use a members world" : "F2P"), false, width));
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
        requirements.forLocation(location).forEach(fact -> card.add(requirementText(fact, width)));
        if (hasText(location.getNotes()))
        {
            card.add(text(location.getNotes(), false, width));
        }
        String url = location.getSourceUrl();
        if (url != null && url.startsWith("https://oldschool.runescape.wiki/w/"))
        {
            JButton wiki = style.button("Wiki page & maps", () -> LinkBrowser.browse(url), false);
            card.add(wiki);
        }

        return card;
    }

    private JLabel requirementText(AccessRequirement fact, int width)
    {
        JLabel label = text(fact.getLabel() + ": " + fact.getText(), false, width);
        label.setName("requirement:" + fact.getId());
        String sources = fact.getEvidence().stream().map(evidence -> evidence.getPage()
                + " (revision " + evidence.getRevisionId() + "): " + evidence.getSourceUrl())
                .collect(Collectors.joining(" | "));
        label.setToolTipText("Click to view Wiki evidence. " + sources);
        label.getAccessibleContext().setAccessibleDescription(label.getToolTipText());
        label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        label.setFocusable(true);
        javax.swing.Action openEvidence = new javax.swing.AbstractAction()
        {
            @Override public void actionPerformed(java.awt.event.ActionEvent event)
            {
                LinkBrowser.browse(fact.getEvidence().get(0).getSourceUrl());
            }
        };
        label.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "openEvidence");
        label.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("SPACE"), "openEvidence");
        label.getActionMap().put("openEvidence", openEvidence);
        label.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override public void focusGained(java.awt.event.FocusEvent event) { label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LookupStyle.ACCENT), new EmptyBorder(2, 0, 2, 0))); }
            @Override public void focusLost(java.awt.event.FocusEvent event) { label.setBorder(new EmptyBorder(3, 0, 3, 0)); }
        });
        label.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event)
            {
                if (javax.swing.SwingUtilities.isLeftMouseButton(event))
                {
                    LinkBrowser.browse(fact.getEvidence().get(0).getSourceUrl());
                }
            }
        });
        return label;
    }

    private static JPanel column()
    {
        return LookupStyle.column();
    }

    private static boolean hasText(String text)
    {
        return text != null && !text.trim().isEmpty();
    }

    private JLabel text(String value, boolean bold, int width)
    {
        JLabel label = style.text(value, bold);
        label.setBorder(new EmptyBorder(3, 0, 3, 0));
        return label;
    }
}
