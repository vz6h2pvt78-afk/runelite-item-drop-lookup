package com.itemdroplookup.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only presentation of one reviewed method, not an account eligibility verdict. */
public final class AcquisitionMethod
{
    private final String id;
    private final String itemName;
    private final String sourceName;
    private final String sourceType;
    private final String wikiPage;
    private final String locationText;
    private final List<Section> sections;
    private final List<Section> summarySections;
    private final boolean verified;
    private final String reportSummary;

    public AcquisitionMethod(String id, String itemName, String sourceName, String sourceType,
            String wikiPage, String locationText, List<Section> sections, boolean verified)
    {
        this(id, itemName, sourceName, sourceType, wikiPage, locationText, sections, sections, verified);
    }

    public AcquisitionMethod(String id, String itemName, String sourceName, String sourceType,
            String wikiPage, String locationText, List<Section> sections, List<Section> summarySections, boolean verified)
    {
        this(id, itemName, sourceName, sourceType, wikiPage, locationText, sections, summarySections, verified, null);
    }

    public AcquisitionMethod(String id, String itemName, String sourceName, String sourceType, String wikiPage,
            String locationText, List<Section> sections, List<Section> summarySections, boolean verified, String reportSummary)
    {
        this.reportSummary = reportSummary;
        this.id = id;
        this.itemName = itemName;
        this.sourceName = sourceName;
        this.sourceType = sourceType;
        this.wikiPage = wikiPage;
        this.locationText = locationText;
        this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
        this.summarySections = Collections.unmodifiableList(new ArrayList<>(summarySections));
        this.verified = verified;
    }

    public String getId() { return id; }
    public String getItemName() { return itemName; }
    public String getSourceName() { return sourceName; }
    public String getSourceType() { return sourceType; }
    public String getWikiPage() { return wikiPage; }
    public String getLocationText() { return locationText; }
    public List<Section> getSections() { return sections; }
    public List<Section> getSummarySections() { return summarySections; }
    public String getReportSummary() { return reportSummary; }
    public boolean isVerified() { return verified; }

    public String getSearchText()
    {
        List<String> text = new ArrayList<>();
        for (Section section : sections)
        {
            text.add(section.getHeading());
            text.addAll(section.getLines());
        }
        return String.join("\n", text);
    }

    public static final class Section
    {
        private final String heading;
        private final List<String> lines;
        private final boolean wilderness;
        private final List<String> lineItems;

        public Section(String heading, List<String> lines)
        {
            this(heading, lines, false);
        }

        public Section(String heading, List<String> lines, boolean wilderness)
        {
            this(heading, lines, wilderness, null);
        }

        public static Section itemLines(String heading, List<String> lines, List<String> items)
        {
            if (items.size() != lines.size()) throw new IllegalArgumentException("Each item line needs an identity");
            return new Section(heading, lines, false, Collections.unmodifiableList(new ArrayList<>(items)));
        }

        private Section(String heading, List<String> lines, boolean wilderness, List<String> lineItems)
        {
            this.lineItems = lineItems;
            this.heading = heading;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            this.wilderness = wilderness;
        }

        public String itemForLine(int index) { return lineItems == null ? null : lineItems.get(index); }
        public String getHeading() { return heading; }
        public List<String> getLines() { return lines; }
        public boolean isWilderness() { return wilderness; }
    }
}
