package com.itemdroplookup.ui;

import java.awt.AWTEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Scrolling the sidebar must not silently change a closed filter or text-size control. */
final class IntentionalComboBox<E> extends JComboBox<E>
{
    IntentionalComboBox(E[] choices)
    {
        super(choices);
        enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    @Override protected void processMouseWheelEvent(MouseWheelEvent event)
    {
        if (isPopupVisible())
        {
            super.processMouseWheelEvent(event);
            return;
        }
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scroll != null) scroll.dispatchEvent(SwingUtilities.convertMouseEvent(this, event, scroll));
        event.consume();
    }
}
