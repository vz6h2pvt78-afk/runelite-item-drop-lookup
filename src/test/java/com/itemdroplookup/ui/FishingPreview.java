package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.io.File;
import javax.swing.*;

/** Offline rendering of the real sidebar; no game interaction. */
public final class FishingPreview
{
    public static void main(String[] args) throws Exception
    {
        File output = new File(args[0]);
        output.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            ItemDropLookupPanel panel = new ItemDropLookupPanel(new DropLookupService(gson),
                    new ItemSourceLookupService(gson), new ItemPriceLookupService(gson),
                    new MonsterLocationLookupService(gson), new AccessRequirementLookupService(gson));
            MorningFeedbackTest.search(panel, "Raw shrimps");
            PanelTestSupport.openSources(panel);
            ((JComboBox<?>) PanelTestSupport.find(panel, "sourceTypeFilter")).setSelectedItem("Gathering");
            JComponent wrap = panel.getWrappedPanel();
            wrap.setSize(242, 900);
            for (int i = 0; i < 10; i++) AcquisitionPreview.layout(wrap);
            AcquisitionPreview.save(wrap, new File(output, "raw-shrimps-gathering.png"));
            MorningFeedbackTest.search(panel, "Shrimps");
            for (int i = 0; i < 10; i++) AcquisitionPreview.layout(wrap);
            AcquisitionPreview.save(wrap, new File(output, "shrimps-cooking-overview.png"));
        });
    }
}
