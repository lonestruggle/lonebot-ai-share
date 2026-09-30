package net.storm.api.interact.builder;

import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.storm.api.interact.AutomatedMenu;
import net.storm.api.interact.InteractMethod;

/**
 * Fluent builder for {@link AutomatedMenu}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/interact/builder/MenuBuilder.html">Storm MenuBuilder</a>
 */
public class MenuBuilder {

    private String option;
    private String target;
    private int identifier;
    private int opcode;
    private int param0;
    private int param1;
    private int itemId;
    private Point clickPoint;
    private InteractMethod interactMethod;

    public MenuBuilder setOption(String option) {
        this.option = option;
        return this;
    }

    public MenuBuilder setTarget(String target) {
        this.target = target;
        return this;
    }

    public MenuBuilder setIdentifier(int identifier) {
        this.identifier = identifier;
        return this;
    }

    public MenuBuilder setOpcode(int opcode) {
        this.opcode = opcode;
        return this;
    }

    public MenuBuilder setOpcode(MenuAction action) {
        if (action != null) {
            this.opcode = action.getId();
        }
        return this;
    }

    public MenuBuilder setParam0(int param0) {
        this.param0 = param0;
        return this;
    }

    public MenuBuilder setParam1(int param1) {
        this.param1 = param1;
        return this;
    }

    public MenuBuilder setClickPoint(Point clickPoint) {
        this.clickPoint = clickPoint;
        return this;
    }

    public MenuBuilder setItemId(int itemId) {
        this.itemId = itemId;
        return this;
    }

    public MenuBuilder setInteractMethod(InteractMethod method) {
        this.interactMethod = method;
        return this;
    }

    public AutomatedMenu build() {
        AutomatedMenu menu = new AutomatedMenu(option, target, identifier, opcode, param0, param1);
        menu.setClickPoint(clickPoint);
        menu.setItemId(itemId);
        menu.setInteractMethod(interactMethod);
        return menu;
    }
}
