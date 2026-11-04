package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CardHierarchyTest
{
    private ItemDropLookupPanel panel()
    {
        Gson gson=new Gson();
        return new ItemDropLookupPanel(new DropLookupService(gson),new ItemSourceLookupService(gson),new ItemPriceLookupService(gson),new MonsterLocationLookupService(gson),new AccessRequirementLookupService(gson));
    }
    private List<Container> cards(Container root)
    {
        List<Container> result=new ArrayList<>();
        for(Component c:root.getComponents())
            if(c instanceof Container) { if("itemSourceCard".equals(c.getName()))result.add((Container)c); else result.addAll(cards((Container)c)); }
        return result;
    }
    @Test public void packHeadingUsesContentsIdentityAndCostPrecedesLocation() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            ItemDropLookupPanel p=panel(); MorningFeedbackTest.search(p,"Air rune");
            MorningFeedbackTest.click(p,"showOtherSources");
            Container pack=cards(p).stream().filter(c->MorningFeedbackTest.labels(c).contains("Buy pack") && MorningFeedbackTest.labels(c).contains("Air rune pack")).findFirst().get();
            String text=MorningFeedbackTest.labels(pack);
            assertTrue(((JLabel)pack.getComponent(0)).getText().contains("→ Air rune</html>"));
            assertTrue(text.contains("100 × Air rune"));
            assertTrue(text.contains("per pack"));
            assertTrue(text.indexOf("Cost:")<text.indexOf("Location:"));
        });
    }
    @Test public void entSummaryWarnsOnceAndUncertainOutputsExplainWhy() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            ItemDropLookupPanel p=panel(); MorningFeedbackTest.search(p,"Magic logs"); MorningFeedbackTest.click(p,"showGatheringSources");
            assertEquals(1,cards(p).size()); Container card=cards(p).get(0);
            assertTrue(MorningFeedbackTest.labels(card).contains("East and north of Chaos Temple"));
            assertTrue(MorningFeedbackTest.labels(card).contains("Woodcutting Guild Ent cavern"));
            assertNotNull(PanelTestSupport.find(card,"wildernessWarning"));
            assertFalse(MorningFeedbackTest.labels(card).contains("Outside Wilderness"));
            for(String item:new String[]{"Oak logs","Willow logs","Maple logs"})
            {
                MorningFeedbackTest.search(p,item); MorningFeedbackTest.click(p,"showGatheringSources");
                assertEquals(1,cards(p).size());
                String text=MorningFeedbackTest.labels(cards(p).get(0));
                assertTrue(text.contains("base Woodcutting and the axe used"));
                assertTrue(text.contains("not verified"));
                assertFalse(text.contains("level 15"));
                assertFalse(text.contains("level 30"));
            }
        });
    }
}
