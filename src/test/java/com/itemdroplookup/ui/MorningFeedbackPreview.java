package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.io.File;
import javax.swing.*;

/** Actual Swing review render; never sends input to a game client. */
public final class MorningFeedbackPreview
{
    public static void main(String[] args) throws Exception
    {
        File output=new File(args[0]); output.mkdirs();
        SwingUtilities.invokeAndWait(()->{
            Gson gson=new Gson();
            for(String[] example:new String[][]{{"Lyre","Lensa"},{"Dragonstone","Zulrah"},{"Adamant axe","Dust devil"},{"Granite maul","Gargoyle"}})
            {
                ItemDropLookupPanel panel=new ItemDropLookupPanel(new DropLookupService(gson),new ItemSourceLookupService(gson),new ItemPriceLookupService(gson),new MonsterLocationLookupService(gson),new AccessRequirementLookupService(gson));
                MorningFeedbackTest.search(panel,example[0]); PanelTestSupport.openSources(panel);
                JTextField finder=(JTextField)PanelTestSupport.find(panel,"sourceSearch");
                finder.setText(example[1]);finder.postActionEvent();
                MorningFeedbackTest.click(panel,"openLocations");
                JComponent wrap=panel.getWrappedPanel();wrap.setSize(242,900);
                for(int i=0;i<10;i++)AcquisitionPreview.layout(wrap);
                AcquisitionPreview.save(wrap,new File(output,example[1]+"-detail.png"));
            }
        });
    }
}
