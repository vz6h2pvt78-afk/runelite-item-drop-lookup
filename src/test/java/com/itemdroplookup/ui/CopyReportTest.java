package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CopyReportTest
{
    private ItemDropLookupPanel panel(Consumer<String> writer)
    {
        Gson gson = new Gson();
        return new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson),
                new AccessRequirementLookupService(gson), writer);
    }

    private void search(ItemDropLookupPanel panel, String query)
    {
        JTextField field = (JTextField) PanelTestSupport.find(panel, "itemSearch");
        field.setText(query);
        field.postActionEvent();
    }

    @Test public void copyIsManualAndUsesSubmittedQueryWithoutResettingMenus() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            ItemDropLookupPanel panel = panel(copied::add);
            assertNull(PanelTestSupport.find(panel, "copyReport"));
            search(panel, "Spade");
            PanelTestSupport.openSources(panel);
            JComboBox<?> method = (JComboBox<?>) PanelTestSupport.find(panel, "sourceTypeFilter");
            method.setSelectedItem("Shops");
            ((JTextField) PanelTestSupport.find(panel, "sourceSearch")).setText("no matching source");
            ((JTextField) PanelTestSupport.find(panel, "sourceSearch")).postActionEvent();
            ((JTextField) PanelTestSupport.find(panel, "itemSearch")).setText("UNSUBMITTED DRAFT");
            assertTrue(copied.isEmpty());
            JButton copy = (JButton) PanelTestSupport.find(panel, "copyReport");
            copy.doClick(0);
            assertEquals(1, copied.size());
            String report = copied.get(0);
            assertTrue(report.startsWith("Item Drop Lookup report\nItem: Spade\nItem query: Spade\n"));
            assertTrue(report.contains("Find source: no matching source\nMethod: Shops\nView: Sources\n"));
            assertFalse(report.contains("UNSUBMITTED"));
            assertFalse(report.contains("NPC area:"));
            assertFalse(report.contains("Detail source:"));
            assertTrue(report.contains("Build: development-"));
            assertTrue(report.contains("Data schemas: access=1; acquisition=1; locations=1"));
            assertTrue(report.matches("(?s).*Dataset: [a-f0-9]{16}\\n"));
            assertEquals("Shops", method.getSelectedItem());
            assertTrue(((JLabel) PanelTestSupport.find(panel, "copyReportStatus")).getText().contains("Report copied"));
            copy.doClick(0);
            assertEquals(2, copied.size());
            assertEquals(report, copied.get(1));
        });
    }

    @Test public void detailReportUsesExactSourceAndMenuStateWithoutExportingLocations() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            ItemDropLookupPanel panel = panel(copied::add);
            search(panel, "Rune scimitar");
            PanelTestSupport.openSources(panel);
            JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
            finder.setText("Fire giant"); finder.postActionEvent();
            ((JButton) PanelTestSupport.find(panel, "openLocations")).doClick(0);
            ((JComboBox<?>) PanelTestSupport.find(panel, "detailMembership")).setSelectedItem(MonsterLocationFilter.MembershipFilter.F2P);
            ((JComboBox<?>) PanelTestSupport.find(panel, "detailWilderness")).setSelectedItem(MonsterLocationFilter.WildernessFilter.ONLY);
            ((JButton) PanelTestSupport.find(panel, "copyReport")).doClick(0);
            assertEquals(1, copied.size());
            String report = copied.get(0);
            assertTrue(report.contains("View: Source details\nDetail source: Fire giant\nDetail method: NPC Drop\n"));
            assertTrue(report.contains("Detail area: Wilderness only\nDetail membership: F2P locations\n"));
            assertFalse(report.contains("Waterfall"));
            assertFalse(report.contains("x:"));
            ((JButton) PanelTestSupport.find(panel, "backToResults")).doClick(0);
            ((JButton) PanelTestSupport.find(panel, "copyReport")).doClick(0);
            assertFalse(copied.get(1).contains("Detail source:"));
            assertEquals("Fire giant", finder.getText());
        });
    }

    @Test public void itemChoicesDoNotReportThePreviouslySelectedItem() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            ItemDropLookupPanel panel = panel(copied::add);
            search(panel, "Spade");
            search(panel, "uncut");
            ((JButton) PanelTestSupport.find(panel, "copyReport")).doClick(0);
            assertTrue(copied.get(0).contains("Item query: uncut\nView: Matching items\n"));
            assertFalse(copied.get(0).contains("Item:"));
            assertFalse(copied.get(0).contains("Spade"));
            assertFalse(copied.get(0).contains("Method:"));
        });
    }

    @Test public void clipboardFailureIsVisibleAndDoesNotRetryOrChangeTheSearch() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            int[] writes = {0};
            ItemDropLookupPanel panel = panel(text -> { writes[0]++; throw new IllegalStateException("busy"); });
            search(panel, "Spade");
            ((JButton) PanelTestSupport.find(panel, "copyReport")).doClick(0);
            assertEquals(1, writes[0]);
            assertTrue(((JLabel) PanelTestSupport.find(panel, "copyReportStatus")).getText().contains("Could not copy; try again"));
            assertEquals("Spade", ((JTextField) PanelTestSupport.find(panel, "itemSearch")).getText());
        });
    }

    @Test public void missingOrMalformedMetadataNeverInventsBuildIdentity()
    {
        assertEquals("Build identity: unavailable", LookupReport.readIdentity(null));
        for (String value : new String[]{"", "build=unknown\ndatasetSha256=abc", "\\uinvalid"})
            assertEquals("Build identity: unavailable", LookupReport.readIdentity(
                    new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII))));
    }
}
