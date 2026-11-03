package com.itemdroplookup.ui;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;

/** Follow B's explicit overview step in legacy behaviour regressions. */
final class PanelTestSupport
{
    static void openSources(Container root)
    {
        Component resume = find(root, "viewCurrentMethod");
        if (resume == null) resume = find(root, "viewAllSources");
        if (resume instanceof JButton) ((JButton) resume).doClick(0);
    }

    static Component find(Container root, String name)
    {
        for (Component c : root.getComponents())
        {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container)
            {
                Component result = find((Container)c, name);
                if (result != null) return result;
            }
        }
        return null;
    }
}
