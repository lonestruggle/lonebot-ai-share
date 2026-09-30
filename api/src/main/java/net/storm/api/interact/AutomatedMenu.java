package net.storm.api.interact;

import net.runelite.api.Point;

/**
 * Encapsulates a game menu action for queued / deferred execution.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/interact/AutomatedMenu.html">Storm AutomatedMenu</a>
 */
public class AutomatedMenu implements Automation {

    private String option;
    private String target;
    private int identifier;
    private int opcode;
    private int param0;
    private int param1;
    private int itemId;
    /** Optional canvas click point for {@link InteractMethod#MOUSE_EVENTS}. */
    private Point clickPoint;
    /** Per-action override; null → use {@link net.storm.sdk.interact.InteractManager} global. */
    private InteractMethod interactMethod;

    public AutomatedMenu() {
    }

    public AutomatedMenu(String option, String target, int identifier, int opcode, int param0, int param1) {
        this.option = option;
        this.target = target;
        this.identifier = identifier;
        this.opcode = opcode;
        this.param0 = param0;
        this.param1 = param1;
    }

    public static net.storm.api.interact.builder.MenuBuilder builder() {
        return new net.storm.api.interact.builder.MenuBuilder();
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public int getIdentifier() {
        return identifier;
    }

    public void setIdentifier(int identifier) {
        this.identifier = identifier;
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int getParam0() {
        return param0;
    }

    public void setParam0(int param0) {
        this.param0 = param0;
    }

    public int getParam1() {
        return param1;
    }

    public void setParam1(int param1) {
        this.param1 = param1;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public Point getClickPoint() {
        return clickPoint;
    }

    public void setClickPoint(Point clickPoint) {
        this.clickPoint = clickPoint;
    }

    public InteractMethod getInteractMethod() {
        return interactMethod;
    }

    public void setInteractMethod(InteractMethod interactMethod) {
        this.interactMethod = interactMethod;
    }

    @Override
    public String toString() {
        return "AutomatedMenu{option='" + option + "', target='" + target
                + "', id=" + identifier + ", opcode=" + opcode
                + ", p0=" + param0 + ", p1=" + param1
                + ", itemId=" + itemId
                + ", click=" + clickPoint + '}';
    }
}
