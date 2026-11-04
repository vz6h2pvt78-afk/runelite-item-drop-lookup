package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.MonsterLocationFilter.MembershipFilter;
import com.itemdroplookup.service.MonsterLocationFilter.WildernessFilter;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharedHerbPanelTest
{
    @Test
    public void f2pGuardMembersLumbridgeAndMenuRecoveryAreVisible() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            MonsterLocationPanel panel = detail("Man", "Grimy harralander", "1/50.9");
            JComboBox<?> membership = (JComboBox<?>) named(panel, "detailMembership").get(0);
            assertEquals(1, named(panel, "dropWorldRestriction").size());
            membership.setSelectedItem(MembershipFilter.F2P);
            assertEquals(0, locationCount(panel));
            assertTrue(labels(panel).contains("No F2P acquisition locations"));
            membership.setSelectedItem(MembershipFilter.MEMBERS);
            assertEquals(1, named(panel, "location:fdf802b6900421379085").size());
            assertTrue(labels(panel).contains("F2P-accessible area; use a members world"));
            ((JComboBox<?>) named(panel, "detailWilderness").get(0)).setSelectedItem(WildernessFilter.ONLY);
            membership.setSelectedItem(MembershipFilter.ALL);
            ((JComboBox<?>) named(panel, "detailWilderness").get(0)).setSelectedItem(WildernessFilter.ALL);
            assertEquals(MembershipFilter.ALL, membership.getSelectedItem());
            assertEquals(WildernessFilter.ALL, ((JComboBox<?>) named(panel, "detailWilderness").get(0)).getSelectedItem());
            assertEquals(28, locationCount(panel));
        });
    }

    @Test
    public void showAllCannotBypassWorldRestrictionAndMailRemainsContextual() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            MonsterLocationPanel herb = detail("Goblin", "Grimy harralander", "1/585.1");
            assertEquals(0, named(herb, "location:1b37fd40efb415e153fb").size());
            ((JComboBox<?>) named(herb, "detailMembership").get(0)).setSelectedItem(MembershipFilter.F2P);
            ((JCheckBox) named(herb, "allMonsterLocations").get(0)).doClick();
            assertEquals(0, locationCount(herb));
            MonsterLocationPanel mail = detail("Goblin", "Goblin mail", "5/128");
            ((JComboBox<?>) named(mail, "detailMembership").get(0)).setSelectedItem(MembershipFilter.F2P);
            assertEquals(1, named(mail, "location:1b37fd40efb415e153fb").size());
            assertTrue(labels(mail).contains("world availability has not been verified"));
            assertEquals(0, named(mail, "dropWorldRestriction").size());
        });
    }

    private static MonsterLocationPanel detail(String monster, String item, String rate)
    {
        Gson gson = new Gson();
        DropSource source = new DropLookupService(gson).searchByItemName(item).stream()
                .filter(s -> s.getMonsterName().equals(monster) && s.getDropRate().equals(rate)).findFirst().get();
        return new MonsterLocationPanel(source, item, new MonsterLocationLookupService(gson),
                new AccessRequirementLookupService(gson), WildernessFilter.ALL, () -> { });
    }

    private static int locationCount(Container parent)
    {
        int count = 0;
        for (Component child : parent.getComponents())
        {
            if (child.getName() != null && child.getName().startsWith("location:")) { count++; }
            if (child instanceof Container) { count += locationCount((Container) child); }
        }
        return count;
    }

    private static String labels(Container parent)
    {
        StringBuilder result = new StringBuilder();
        for (Component child : parent.getComponents())
        {
            if (child instanceof JLabel) { result.append(((JLabel) child).getText()); }
            if (child instanceof Container) { result.append(labels((Container) child)); }
        }
        return result.toString();
    }

    private static List<Component> named(Container parent, String name)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            if (name.equals(child.getName())) { result.add(child); }
            if (child instanceof Container) { result.addAll(named((Container) child, name)); }
        }
        return result;
    }
}
