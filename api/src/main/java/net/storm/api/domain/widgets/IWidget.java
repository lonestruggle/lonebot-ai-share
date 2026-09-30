package net.storm.api.domain.widgets;

import java.awt.Rectangle;

public interface IWidget {
    int getId();

    int getItemId();

    String getName();

    String getText();

    boolean isHidden();

    Rectangle getBounds();

    boolean hasAction(String action);

    boolean interact(String action);

    IWidget[] getChildren();

    /** Nested dynamic children (emote panel e.d.); default leeg. */
    default IWidget[] getDynamicChildren() {
        return null;
    }

    /** Nested static children; default leeg. */
    default IWidget[] getStaticChildren() {
        return null;
    }

    /** Child/slot index used as menuAction param0, or -1. */
    default int getIndex() {
        return -1;
    }
}
