package net.runelite.client.plugins.lonebot;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Game;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.input.MouseSettings;
import net.storm.sdk.movement.WalkCameraSettings;
import net.storm.sdk.movement.WalkClickSettings;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.utils.AntiBanSettings;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Blauw canvas-debug paneel: walk/camera/muis techniek — compact gegroepeerd.
 */
public class LoneBotStatusOverlay extends MinimizableStatusOverlay {

    private static final Color TITLE = new Color(120, 200, 255);
    private static final Color SECTION = new Color(140, 160, 180);
    private static final Color ON = new Color(140, 220, 160);
    private static final Color OFF = new Color(130, 130, 140);

    private final Client client;
    private final LoneBotConfig config;
    private final PanelComponent panel = new PanelComponent();
    private volatile String message = "Starting…";

    @Inject
    public LoneBotStatusOverlay(Client client, LoneBotConfig config) {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGH);
        panel.setPreferredSize(new Dimension(280, 0));
    }

    @Override
    public String overlayId() {
        return "canvasStatus";
    }

    @Override
    protected Color chipColor() {
        return new Color(80, 140, 200);
    }

    @Override
    protected boolean isEnabled() {
        return config != null && config.statusOverlay() && BotRuntime.canvasDebugEnabled;
    }

    @Override
    protected boolean hideWhenBankOrGe() {
        return false;
    }

    public void setMessage(String message) {
        this.message = message != null ? message : "";
        LoneBotControlOverlay.pushStatus(this.message);
    }

    @Override
    protected Dimension renderExpanded(Graphics2D graphics) {
        panel.getChildren().clear();
        panel.getChildren().add(TitleComponent.builder()
                .text("LoneBot v" + LoneBotBootstrapPlugin.VERSION)
                .color(TITLE)
                .build());

        add("Status", trim(message, 32), Color.WHITE);

        if (BotRuntime.relogFlowActive || BotRuntime.relogInfoEnabled || BotRuntime.breakPending) {
            String relogRight;
            if (BotRuntime.relogLauncherLabel != null && !BotRuntime.relogLauncherLabel.isBlank()) {
                relogRight = trim(BotRuntime.relogLauncherLabel, 28);
            } else if (BotRuntime.relogInPause) {
                relogRight = "pauze " + BotRuntime.relogPauseSeconds + "s";
            } else if (BotRuntime.relogFlowActive) {
                relogRight = trim(BotRuntime.lastRelogStatus, 24);
            } else {
                relogRight = "uit over " + BotRuntime.relogSecondsUntilLogout + "s";
            }
            add("Break", relogRight, Color.WHITE);
        }

        // ── scripts (één compacte regel per actieve, of korte lijst)
        addSection("scripts");
        add("Actief", activeScriptsSummary(), ON);
        if (BotRuntime.randomEventStatus != null
                && !BotRuntime.randomEventStatus.isBlank()
                && !"uit".equals(BotRuntime.randomEventStatus)
                && !"-".equals(BotRuntime.randomEventStatus)) {
            add("RandomEv", trim(BotRuntime.randomEventStatus, 26), Color.WHITE);
        }

        // ── walk / camera
        addSection("walk / camera");
        add("Walk clk", trim(WalkClickSettings.lastMethod + " " + WalkClickSettings.lastDetail, 30), Color.WHITE);
        add("Walk hum", trim(WalkClickSettings.lastHumanSummary, 30), Color.WHITE);
        add("Cam", trim(camLine(), 32), Color.WHITE);
        add("Face", faceLine(), Color.WHITE);

        // ── mouse
        addSection("mouse");
        int mx = Mouse.getX();
        int my = Mouse.getY();
        int tx = Mouse.getTargetX();
        int ty = Mouse.getTargetY();
        add("Pos", mx + "," + my + " → " + (tx >= 0 ? tx + "," + ty : "-"), Color.WHITE);
        add("Backend", Mouse.getBackend().name()
                + " · " + MouseSettings.getSpeedPercent() + "%"
                + (MouseSettings.isRandomMoveEnabled() ? " rnd" : ""), Color.WHITE);
        if (BotRuntime.mouseSummary != null && !BotRuntime.mouseSummary.isBlank()) {
            add("Click", trim(BotRuntime.mouseSummary, 28), Color.WHITE);
        }

        // ── misc
        addSection("misc");
        add("AntiBan", AntiBanSettings.enabled
                ? trim(AntiBan.get().getLastActionLabel(), 26)
                : "uit", AntiBanSettings.enabled ? ON : OFF);
        Player local = client.getLocalPlayer();
        String tile = (local != null && local.getWorldLocation() != null)
                ? local.getWorldLocation().getX() + "," + local.getWorldLocation().getY()
                : "-";
        add("Tile", tile + " · " + (Game.isLoggedIn() ? "in" : "out"), Color.WHITE);

        graphics.translate(6, 6);
        return panel.render(graphics);
    }

    private void addSection(String title) {
        panel.getChildren().add(LineComponent.builder()
                .left("─ " + title)
                .right("")
                .leftColor(SECTION)
                .build());
    }

    private void add(String left, String right, Color rightColor) {
        panel.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right != null ? right : "-")
                .leftColor(new Color(170, 190, 210))
                .rightColor(rightColor != null ? rightColor : Color.WHITE)
                .build());
    }

    private String activeScriptsSummary() {
        StringBuilder sb = new StringBuilder();
        appendOn(sb, "WC", BotRuntime.woodcuttingEnabled, BotRuntime.wcPluginVersion);
        appendOn(sb, "Fish", BotRuntime.fishingEnabled, BotRuntime.fishPluginVersion);
        appendOn(sb, "Imp", BotRuntime.impKillerEnabled, BotRuntime.impPluginVersion);
        appendOn(sb, "Star", BotRuntime.starMinerEnabled, BotRuntime.starPluginVersion);
        appendOn(sb, "Giants", BotRuntime.giantsKillerEnabled, BotRuntime.giantsPluginVersion);
        appendOn(sb, "Cow", BotRuntime.cowCombatEnabled, null);
        appendOn(sb, "Imps2", BotRuntime.imps2Enabled, BotRuntime.imps2PluginVersion);
        if (BotRuntime.cityCircleTestEnabled) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("Circle");
        }
        if (BotRuntime.varrockEastBankTestEnabled) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("BankTest");
        }
        return sb.length() == 0 ? "geen" : trim(sb.toString(), 34);
    }

    private static void appendOn(StringBuilder sb, String name, boolean on, String ver) {
        if (!on) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(name);
        if (ver != null && !ver.isBlank() && !"?".equals(ver)) {
            sb.append(" v").append(ver);
        }
    }

    private String camLine() {
        try {
            int yaw = client.getCameraYaw() & 2047;
            int pitch = client.getCameraPitch();
            int scale = client.getScale();
            WalkCameraSettings.lastYaw = yaw;
            WalkCameraSettings.lastPitch = pitch;
            WalkCameraSettings.lastScale = scale;
            String mmb = WalkClickSettings.lastCameraDebug;
            if (mmb != null && !mmb.isBlank() && !"-".equals(mmb)) {
                return "y" + yaw + " p" + pitch + " · " + trim(mmb, 14);
            }
            return "y" + yaw + " p" + pitch + " s" + scale;
        } catch (Throwable t) {
            return WalkCameraSettings.overlayAnglesLine();
        }
    }

    private String faceLine() {
        if (WalkCameraSettings.lastFacePercent < 0) {
            return "-";
        }
        return "ahead " + WalkCameraSettings.lastFacePercent + "%≥"
                + WalkCameraSettings.visibilitySkipPercent + "%"
                + (WalkCameraSettings.lastSkipReason != null
                && !WalkCameraSettings.lastSkipReason.isBlank()
                ? " " + trim(WalkCameraSettings.lastSkipReason, 12)
                : "");
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
