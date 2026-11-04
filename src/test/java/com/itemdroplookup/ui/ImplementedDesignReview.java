package com.itemdroplookup.ui;

import com.itemdroplookup.service.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/** C2b review only. Border comparison and identification never enter the production JAR. */
@PluginDescriptor(name = "Item Lookup C2 Review", enabledByDefault = true)
public class ImplementedDesignReview extends Plugin
{
    @Inject private ClientToolbar toolbar;
    @Inject private DropLookupService drops;
    @Inject private ItemSourceLookupService sources;
    @Inject private ItemPriceLookupService prices;
    @Inject private MonsterLocationLookupService locations;
    @Inject private AccessRequirementLookupService requirements;
    private NavigationButton navigation;

    @Override protected void startUp()
    {
        PluginPanel review = new PluginPanel(false) { };
        review.setLayout(new BorderLayout());
        JComboBox<String> treatment = new JComboBox<>(new String[] {"Current borders", "Clearer borders"});
        treatment.setName("reviewBorders");
        treatment.setFont(new LookupStyle(13).font());
        treatment.setToolTipText("C2b review: only borders change; text, layout and background colours stay the same");
        JPanel body = new JPanel(new BorderLayout());
        ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, sources, prices, locations, requirements);
        body.add(panel.getWrappedPanel(), BorderLayout.CENTER);
        installComparison(body, treatment);
        JPanel controls = LookupStyle.column();
        controls.add(new LookupStyle(13).text("Development preview\nC2b · Box contrast", false));
        controls.add(treatment);
        review.add(controls, BorderLayout.NORTH);
        review.add(body, BorderLayout.CENTER);
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).setText("Spade");
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).postActionEvent();
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        graphics.setColor(new Color(222, 193, 126)); graphics.fillRoundRect(1, 1, 14, 14, 4, 4);
        graphics.setColor(Color.BLACK); graphics.drawString("B", 4, 12); graphics.dispose();
        navigation = NavigationButton.builder().tooltip("C2b B review — box contrast")
                .icon(icon).priority(4).panel(review).build();
        toolbar.addNavigation(navigation);
    }

    static void installComparison(Container body, JComboBox<String> treatment)
    {
        Runnable update = () -> {
            applyBoundaries(body, treatment.getSelectedIndex() == 1);
            body.repaint();
        };
        treatment.addActionListener(e -> update.run());
        boolean[] queued = {false};
        watchContent(body, () -> {
            if (queued[0]) return;
            queued[0] = true;
            SwingUtilities.invokeLater(() -> {
                queued[0] = false;
                update.run();
            });
        });
        update.run();
    }

    static final Color CLEAR_BORDER = new Color(128, 144, 153);

    static void applyBoundaries(Container parent, boolean clearer)
    {
        for (Component component : parent.getComponents())
        {
            if (component instanceof JComponent)
            {
                JComponent surface = (JComponent) component;
                Border border = surface.getBorder();
                if (clearer && !(border instanceof ReviewBorder))
                {
                    Border tinted = tintBorder(border);
                    if (tinted != border) surface.setBorder(new ReviewBorder(border, tinted));
                }
                else if (!clearer && border instanceof ReviewBorder)
                    surface.setBorder(((ReviewBorder) border).original);
            }
            if (component instanceof Container) applyBoundaries((Container) component, clearer);
        }
    }

    private static boolean neutral(Color color)
    {
        return new Color(83, 96, 106).equals(color) || ColorScheme.MEDIUM_GRAY_COLOR.equals(color);
    }

    private static Border tintBorder(Border original)
    {
        if (original instanceof MatteBorder)
        {
            MatteBorder matte = (MatteBorder) original;
            if (neutral(matte.getMatteColor()))
                return new MatteBorder(matte.getBorderInsets(), CLEAR_BORDER);
        }
        else if (original instanceof LineBorder)
        {
            LineBorder line = (LineBorder) original;
            if (neutral(line.getLineColor()))
                return new LineBorder(CLEAR_BORDER, line.getThickness(), line.getRoundedCorners());
        }
        else if (original instanceof CompoundBorder)
        {
            CompoundBorder compound = (CompoundBorder) original;
            Border outside = tintBorder(compound.getOutsideBorder());
            Border inside = tintBorder(compound.getInsideBorder());
            if (outside != compound.getOutsideBorder() || inside != compound.getInsideBorder())
                return new CompoundBorder(outside, inside);
        }
        return original;
    }

    /** Only recolours existing neutral strokes; no extra nested boxes or geometry changes. */
    private static final class ReviewBorder extends AbstractBorder
    {
        private final Border original;
        private final Border tinted;
        ReviewBorder(Border original, Border tinted) { this.original = original; this.tinted = tinted; }

        @Override public Insets getBorderInsets(Component component)
        {
            return original.getBorderInsets(component);
        }

        @Override public Insets getBorderInsets(Component component, Insets result)
        {
            Insets value = getBorderInsets(component);
            result.set(value.top, value.left, value.bottom, value.right);
            return result;
        }

        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height)
        {
            tinted.paintBorder(component, graphics, x, y, width, height);
        }
    }

    private static void watchContent(Container parent, Runnable changed)
    {
        if (parent instanceof JComponent)
        {
            JComponent component = (JComponent) parent;
            if (Boolean.TRUE.equals(component.getClientProperty("reviewWatched"))) return;
            component.putClientProperty("reviewWatched", true);
        }
        parent.addContainerListener(new java.awt.event.ContainerAdapter()
        {
            @Override public void componentAdded(java.awt.event.ContainerEvent event)
            {
                if (event.getChild() instanceof Container) watchContent((Container) event.getChild(), changed);
                changed.run();
            }
        });
        for (Component child : parent.getComponents()) if (child instanceof Container) watchContent((Container) child, changed);
    }

    @Override protected void shutDown() { if (navigation != null) toolbar.removeNavigation(navigation); }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ImplementedDesignReview.class);
        RuneLite.main(args);
    }
}
