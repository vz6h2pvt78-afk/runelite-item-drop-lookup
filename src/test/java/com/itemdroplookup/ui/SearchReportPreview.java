package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** Actual empty-state/utility layout; injected clipboard sink, no OS clipboard or game. */
public final class SearchReportPreview
{
    public static void main(String[] args) throws Exception
    {
        File output = new File(args[0]);
        if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("Cannot create output");
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            List<String> copies = new ArrayList<>();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson), new ItemSourceLookupService(gson),
                    new ItemPriceLookupService(gson), new MonsterLocationLookupService(gson),
                    new AccessRequirementLookupService(gson), copies::add);
            JTextField item = (JTextField) PanelTestSupport.find(panel, "itemSearch");
            item.setText("Black bead"); item.postActionEvent();
            PanelTestSupport.openSources(panel);
            ((JComboBox<?>) PanelTestSupport.find(panel, "sourceTypeFilter")).setSelectedItem("Shops");
            for (String size : new String[]{"Standard text", "Larger text"})
            {
                ((JComboBox<?>) PanelTestSupport.find(panel, "textSize")).setSelectedItem(size);
                JTextField finder = (JTextField) PanelTestSupport.find(panel, "sourceSearch");
                finder.setText("unmatched"); finder.postActionEvent();
                for (boolean clear : new boolean[]{false, true})
                {
                    if (clear) ((JButton) PanelTestSupport.find(panel, "clearSourceSearch")).doClick(0);
                    ((JButton) PanelTestSupport.find(panel, "copyReport")).doClick(0);
                    JComponent root = panel.getWrappedPanel(); root.setSize(242, 760);
                    for (int n = 0; n < 10; n++) AcquisitionPreview.layout(root);
                    String name = size.replace(' ', '-') + (clear ? "-cleared" : "-query");
                    AcquisitionPreview.save(root, new File(output, name + "-sidebar.png"));
                    JComponent controls = (JComponent) PanelTestSupport.find(panel, "clearSourceSearch").getParent();
                    AcquisitionPreview.save(controls, new File(output, name + "-controls.png"));
                    JComponent report = (JComponent) PanelTestSupport.find(panel, "copyReport").getParent();
                    AcquisitionPreview.checkBounds(report);
                    AcquisitionPreview.save(report, new File(output, name + "-report.png"));
                    System.out.println(name + ": utility bounds OK; menu="
                            + ((JComboBox<?>) PanelTestSupport.find(panel, "sourceTypeFilter")).getSelectedItem());
                }
            }
            if (copies.size() != 4) throw new IllegalStateException("Unexpected clipboard-write count");
        });
    }
}
