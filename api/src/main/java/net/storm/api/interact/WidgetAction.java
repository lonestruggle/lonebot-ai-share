package net.storm.api.interact;

/**
 * Queued widget interaction — Storm {@code WidgetAction}.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/interact/package-summary.html">Storm interact</a>
 */
public final class WidgetAction implements Automation {

    private final int packedWidgetId;
    private final String option;
    private final int identifier;
    private final int param0;
    private InteractMethod method;

    public WidgetAction(int packedWidgetId, String option) {
        this(packedWidgetId, option, 1, -1);
    }

    public WidgetAction(int packedWidgetId, String option, int identifier, int param0) {
        this.packedWidgetId = packedWidgetId;
        this.option = option != null ? option : "";
        this.identifier = Math.max(1, identifier);
        this.param0 = param0;
    }

    public int getPackedWidgetId() {
        return packedWidgetId;
    }

    public String getOption() {
        return option;
    }

    public int getIdentifier() {
        return identifier;
    }

    public int getParam0() {
        return param0;
    }

    public InteractMethod getInteractMethod() {
        return method;
    }

    public WidgetAction setInteractMethod(InteractMethod method) {
        this.method = method;
        return this;
    }
}
