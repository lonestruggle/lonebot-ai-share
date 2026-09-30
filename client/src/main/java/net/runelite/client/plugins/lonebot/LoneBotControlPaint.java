package net.runelite.client.plugins.lonebot;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.Skills;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.WorldWalker;
import net.storm.sdk.utils.AntiBan;
import net.storm.sdk.utils.AntiBanSettings;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Gecombineerd status+script paneel (layout A): kop + secties voortgang/doel/actie (+ optioneel).
 * Breedte/hoogte groeien mee met aangezette Debug-tab opties.
 */
public final class LoneBotControlPaint {

    private static final Color TITLE_GOLD = new Color(255, 215, 0);
    private static final Color SECTION = new Color(110, 110, 110);
    private static final Color MUTED = new Color(170, 170, 170);
    private static final int CHIP_SIZE = 18;
    private static final int PAD = 10;
    private static final int MIN_W = 340;
    private static final int MAX_W = 420;
    private static final int LH = 15;

    private LoneBotConfig config;
    /** Null tot eerste Start; pauze/stop bevriest via {@link #frozenElapsedMs}. */
    private Instant sessionStart;
    private long frozenElapsedMs;
    private boolean wasBotEnabled;
    private long lastSessionTickMs;
    private long miningActiveMs;
    private long fishActiveMs;
    private long wcActiveMs;
    private long combatActiveMs;
    private long startWcXp = -1;
    private long startFmXp = -1;
    private long startFishXp = -1;
    private long startMiningXp = -1;
    private long startAttackXp = -1;
    private boolean xpInit;

    private volatile String currentStatus = "Plugin geladen — druk Start";
    private volatile String lastAction = "Geen";
    private volatile String activeSkill = "Idle";

    public void setConfig(LoneBotConfig config) {
        this.config = config;
    }

    public void setCurrentStatus(String status) {
        if (status != null && !status.isBlank()) {
            this.currentStatus = status.trim();
        }
    }

    public void setLastAction(String action) {
        if (action != null && !action.isBlank()) {
            this.lastAction = action.trim();
        }
    }

    public void setActiveSkill(String skill) {
        if (skill != null && !skill.isBlank()) {
            this.activeSkill = skill.trim();
        }
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    Color chipColor() {
        return borderColor();
    }

    public Dimension render(Graphics2D g, boolean minimized) {
        tickSessionClock();
        syncFromRuntime();

        if (minimized) {
            WorldWalkerStopHit.clear();
            return renderMinimizedChip(g);
        }

        List<String> progress = buildProgressLines();
        List<String> target = buildTargetLines();
        List<String> action = buildActionLines();
        List<String> kit = buildKitLines();
        List<String> bank = buildBankLines();
        List<String> antiban = buildAntiBanLines();
        List<String> extra = buildExtraDebugLines();
        List<String> loopWatch = buildLoopWatchLines();

        boolean showProgress = showProgress() && !progress.isEmpty();
        boolean showTarget = showTarget() && !target.isEmpty();
        boolean showAction = showAction() && !action.isEmpty();
        boolean showKit = showKit() && !kit.isEmpty();
        boolean showBank = showBank() && !bank.isEmpty();
        boolean showAntiBan = showAntiBan() && !antiban.isEmpty();
        boolean showExtra = showExtra() && !extra.isEmpty();
        boolean showLoop = !loopWatch.isEmpty();

        Font titleF = new Font("Arial", Font.BOLD, 13);
        Font bodyF = new Font("Arial", Font.PLAIN, 11);
        Font boldF = new Font("Arial", Font.BOLD, 11);
        FontMetrics bodyFm = g.getFontMetrics(bodyF);

        int contentW = measureWidth(bodyFm, titleF, g,
                progress, target, action, kit, bank, antiban, extra, loopWatch);
        int pw = Math.min(MAX_W, Math.max(MIN_W, contentW + PAD * 2 + 8));
        int maxChars = Math.max(28, (pw - PAD * 2) / 7);

        int x = 10;
        int y = 28;
        int top = y - 14;

        // Height must mirror header + drawSection (divider + title + lines)
        int bottomY = y;
        bottomY += LH + 2; // skill
        bottomY += LH; // status
        if (showProgress) {
            bottomY += sectionBlockHeight(progress.size());
        }
        if (showTarget) {
            bottomY += sectionBlockHeight(target.size());
        }
        if (showAction) {
            bottomY += sectionBlockHeight(action.size());
        }
        if (showKit) {
            bottomY += sectionBlockHeight(kit.size());
        }
        if (showBank) {
            bottomY += sectionBlockHeight(bank.size());
        }
        if (showAntiBan) {
            bottomY += sectionBlockHeight(antiban.size());
        }
        if (showExtra) {
            bottomY += sectionBlockHeight(extra.size());
        }
        if (showLoop) {
            bottomY += sectionBlockHeight(loopWatch.size());
        }
        if (WorldWalker.isActive()) {
            bottomY += 4 + LH;
        }
        int panelH = Math.max(40, bottomY - top + 10);

        g.setColor(new Color(0, 0, 0, 195));
        g.fillRoundRect(x, top, pw, panelH, 8, 8);
        g.setColor(borderColor());
        g.drawRoundRect(x, top, pw, panelH, 8, 8);

        drawTitleChip(g, x + 4, top + 4, borderColor());
        g.setFont(titleF);
        g.setColor(TITLE_GOLD);
        g.drawString("LoneBot v" + LoneBotBootstrapPlugin.VERSION, x + 20, y);
        g.setFont(bodyF);
        g.setColor(Color.WHITE);
        String rt = formatRuntime();
        g.drawString(rt, x + pw - PAD - g.getFontMetrics().stringWidth(rt), y);

        y += LH + 2;
        g.setColor(new Color(255, 215, 0));
        String skillLine = "▶ " + activeSkill;
        String verTag = scriptVersionTag();
        if (verTag != null) {
            skillLine += " · " + verTag;
        }
        g.drawString(trim(skillLine, maxChars), x + PAD, y);

        y += LH;
        g.setColor(statusColor());
        g.setFont(boldF);
        g.drawString("● " + trim(currentStatus, maxChars), x + PAD, y);
        g.setFont(bodyF);

        if (showProgress) {
            y = drawSection(g, x, y, pw, "voortgang", progress, maxChars, new Color(160, 210, 160));
        }
        if (showTarget) {
            y = drawSection(g, x, y, pw, "doel", target, maxChars, new Color(100, 200, 255));
        }
        if (showAction) {
            y = drawSection(g, x, y, pw, "actie", action, maxChars, new Color(220, 200, 140));
        }
        if (showKit) {
            y = drawSection(g, x, y, pw, "kit / event", kit, maxChars, new Color(140, 200, 160));
        }
        if (showBank) {
            y = drawSection(g, x, y, pw, "bank / supply", bank, maxChars, new Color(255, 200, 120));
        }
        if (showAntiBan) {
            y = drawSection(g, x, y, pw, "anti-ban", antiban, maxChars, MUTED);
        }
        if (showExtra) {
            y = drawSection(g, x, y, pw, "script debug", extra, maxChars, new Color(180, 210, 190));
        }
        if (showLoop) {
            y = drawSection(g, x, y, pw, "loopwatch", loopWatch, maxChars, new Color(255, 160, 80));
        }

        if (WorldWalker.isActive()) {
            y += 4;
            int bx = x + PAD;
            int by = y - 11;
            int bw = Math.min(200, pw - PAD * 2);
            int bh = 15;
            g.setColor(new Color(170, 40, 40, 230));
            g.fillRoundRect(bx, by, bw, bh, 5, 5);
            g.setColor(Color.WHITE);
            g.drawString("STOP WALKER  (ESC)", bx + 6, y);
            WorldWalkerStopHit.setLocal(bx, by, bw, bh);
            y += LH;
        } else {
            WorldWalkerStopHit.clear();
        }

        // Prefer measured draw end so bg never clips if a section adds padding
        int drawnH = Math.max(panelH, y - top + 10);
        return new Dimension(pw + 4, drawnH);
    }

    /** Matches {@link #drawSection}: +5 divider, +(LH-2) title, +LH per line. */
    private static int sectionBlockHeight(int lineCount) {
        return 5 + (LH - 2) + Math.max(0, lineCount) * LH;
    }

    private boolean showProgress() {
        return config == null || config.paintShowProgress();
    }

    private boolean showTarget() {
        return config == null || config.paintShowTarget();
    }

    private boolean showAction() {
        return config == null || config.paintShowAction();
    }

    private boolean showKit() {
        return config != null && config.paintShowKit();
    }

    private boolean showBank() {
        return config != null && config.paintShowBankSupply();
    }

    private boolean showAntiBan() {
        return config != null && config.paintShowAntiBan();
    }

    private boolean showExtra() {
        return config != null && config.paintShowScriptDebug();
    }

    private int drawSection(Graphics2D g, int x, int y, int pw, String title,
                            List<String> lines, int maxChars, Color lineColor) {
        y += 5;
        g.setColor(SECTION);
        g.drawLine(x + PAD, y, x + pw - PAD, y);
        y += LH - 2;
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(MUTED);
        g.drawString("─ " + title + " ─", x + PAD, y);
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        for (String line : lines) {
            y += LH;
            g.setColor(lineColor);
            g.drawString(trim(line, maxChars), x + PAD, y);
        }
        return y;
    }

    private int measureWidth(FontMetrics bodyFm, Font titleF, Graphics2D g,
                             List<String> a, List<String> b, List<String> c,
                             List<String> d, List<String> e, List<String> f,
                             List<String> h, List<String> i) {
        int max = g.getFontMetrics(titleF).stringWidth("LoneBot v" + LoneBotBootstrapPlugin.VERSION) + 90;
        max = Math.max(max, bodyFm.stringWidth("▶ " + activeSkill + " · WC v9.99.99"));
        max = Math.max(max, bodyFm.stringWidth("● " + currentStatus));
        for (List<String> list : java.util.Arrays.asList(a, b, c, d, e, f, h, i)) {
            if (list == null) {
                continue;
            }
            for (String s : list) {
                if (s != null) {
                    max = Math.max(max, bodyFm.stringWidth(s));
                }
            }
        }
        return max;
    }

    private List<String> buildProgressLines() {
        List<String> out = new ArrayList<>();
        long sessionSecs = Math.max(1L, sessionElapsedMs() / 1000L);

        long mineXp = xpGained(Skill.MINING, startMiningXp);
        long fishXp = xpGained(Skill.FISHING, startFishXp);
        long wcXp = xpGained(Skill.WOODCUTTING, startWcXp);
        long fmXp = xpGained(Skill.FIREMAKING, startFmXp);
        long atkXp = xpGained(Skill.ATTACK, startAttackXp);

        boolean showMine = BotRuntime.starMinerEnabled || mineXp > 0;
        boolean showFish = BotRuntime.fishingEnabled || fishXp > 0;
        boolean showWc = BotRuntime.woodcuttingEnabled || wcXp > 0 || fmXp > 0;
        boolean showCombat = BotRuntime.giantsKillerEnabled || BotRuntime.impKillerEnabled
                || BotRuntime.cowCombatEnabled || atkXp > 0;

        if (showMine) {
            out.add(progressLine("Mining", mineXp, miningActiveMs, sessionSecs));
        }
        if (showFish) {
            out.add(progressLine("Fish", fishXp, fishActiveMs, sessionSecs));
        }
        if (showWc) {
            long skillSecs = Math.max(0L, wcActiveMs / 1000L);
            long rateBase = skillSecs > 0 ? skillSecs : sessionSecs;
            out.add("XP " + formatXp(wcXp) + " WC · " + formatXp(fmXp) + " FM · "
                    + formatXp(wcXp * 3600L / Math.max(1L, rateBase)) + "/hr · "
                    + formatDurationMs(wcActiveMs));
        }
        if (showCombat && (atkXp > 0 || BotRuntime.giantsKillerEnabled)) {
            out.add(progressLine("Atk", atkXp, combatActiveMs, sessionSecs));
        }
        if (BotRuntime.starMinerEnabled || BotRuntime.starSuccessCount > 0) {
            out.add("Stars succes: " + BotRuntime.starSuccessCount);
        }
        if (out.isEmpty() && BotRuntime.botEnabled) {
            out.add("XP — · " + formatRuntime());
        }
        return out;
    }

    private static String progressLine(String label, long xp, long activeMs, long sessionSecs) {
        long skillSecs = Math.max(0L, activeMs / 1000L);
        long rateBase = skillSecs > 0 ? skillSecs : sessionSecs;
        String dur = formatDurationMs(activeMs);
        return "XP " + formatXp(xp) + " " + label + " · "
                + formatXp(xp * 3600L / Math.max(1L, rateBase)) + "/hr · " + dur;
    }

    private List<String> buildTargetLines() {
        List<String> out = new ArrayList<>();
        String heading = whereHeading();
        if (heading != null && !heading.isBlank()) {
            out.add(heading);
        }
        String hop = prefixed(BotRuntime.starDebugLines, "Hop:");
        if (hop != null && !hop.isBlank()) {
            out.add("Hop: " + hop);
        }
        WorldPoint dest = whereTile();
        WorldPoint me = localTile();
        if (dest != null) {
            int d = distTiles(me, dest);
            String dist = d >= 0 ? d + "t" : "p" + dest.getPlane();
            out.add(dest.getX() + "," + dest.getY() + " (" + dist + ")");
        }
        String tree = prefixed(activeDebugLines(), "Tree:");
        if (tree != null) {
            out.add("Tree: " + tree);
        }
        if (out.isEmpty()) {
            out.add("—");
        }
        return out;
    }

    private List<String> buildActionLines() {
        List<String> out = new ArrayList<>();
        String state = prefixed(activeDebugLines(), "State:");
        if (state != null) {
            out.add("State " + state);
        }
        String anim = prefixed(activeDebugLines(), "Anim:");
        String bot = BotRuntime.botEnabled ? "Bot AAN" : "Bot UIT";
        long age = BotRuntime.loopLastTickMs > 0L
                ? System.currentTimeMillis() - BotRuntime.loopLastTickMs : 0L;
        String stil = age > 1500L ? "stil " + (age / 1000) + "s" : "loop";
        StringBuilder sb = new StringBuilder(bot).append(" · ").append(stil);
        if (anim != null) {
            sb.append(" · ").append(anim);
        }
        out.add(sb.toString());
        if (lastAction != null && !lastAction.isBlank() && !"Geen".equals(lastAction)
                && !showAntiBan()) {
            // keep short action hint only if anti-ban section off
            if (!lastAction.equals(AntiBanSettings.enabled ? AntiBan.get().getLastActionLabel() : "")) {
                out.add(lastAction);
            }
        }
        return out;
    }

    private List<String> buildKitLines() {
        List<String> out = new ArrayList<>();
        List<String> lines = activeDebugLines();
        addIfPrefixed(out, lines, "Kit:");
        addIfPrefixed(out, lines, "Kit bark:");
        addIfPrefixed(out, lines, "FM:");
        addIfPrefixed(out, lines, "Bonfire:");
        return out;
    }

    private List<String> buildBankLines() {
        List<String> out = new ArrayList<>();
        List<String> lines = activeDebugLines();
        addIfPrefixed(out, lines, "BankSnap:");
        addIfPrefixed(out, lines, "Inv:");
        for (String s : lines) {
            if (s != null && (s.contains("inv=") || s.toLowerCase().contains("bank"))) {
                if (!out.contains(s)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private List<String> buildAntiBanLines() {
        List<String> out = new ArrayList<>();
        if (AntiBanSettings.enabled) {
            String ab = AntiBan.get().getLastActionLabel();
            out.add(ab != null && !ab.isBlank() ? ab : "idle");
        } else {
            out.add("uit");
        }
        return out;
    }

    private List<String> buildExtraDebugLines() {
        List<String> out = new ArrayList<>();
        List<String> lines = activeDebugLines();
        if (lines == null) {
            return out;
        }
        for (String s : lines) {
            if (s == null || s.isBlank()) {
                continue;
            }
            // Skip lines already shown in other sections
            if (s.startsWith("State:") || s.startsWith("Center:") || s.startsWith("Tree:")
                    || s.startsWith("Anim:") || s.startsWith("Kit:") || s.startsWith("Kit bark:")
                    || s.startsWith("FM:") || s.startsWith("Bonfire:") || s.startsWith("BankSnap:")
                    || s.startsWith("Hop:") || s.startsWith("Doel:")
                    || s.startsWith("Stars succes:")) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private List<String> buildLoopWatchLines() {
        List<String> out = new ArrayList<>();
        if (BotRuntime.loopWatchStatus != null
                && !"-".equals(BotRuntime.loopWatchStatus)
                && !"ok".equals(BotRuntime.loopWatchStatus)) {
            out.add(BotRuntime.loopWatchStatus);
        }
        return out;
    }

    private static void addIfPrefixed(List<String> out, List<String> lines, String prefix) {
        String v = prefixed(lines, prefix);
        if (v != null) {
            out.add(prefix + " " + v);
        }
    }

    private static List<String> activeDebugLines() {
        if (BotRuntime.woodcuttingEnabled && BotRuntime.wcDebugLines != null) {
            return BotRuntime.wcDebugLines;
        }
        if (BotRuntime.starMinerEnabled && BotRuntime.starDebugLines != null) {
            return BotRuntime.starDebugLines;
        }
        if (BotRuntime.fishingEnabled && BotRuntime.fishDebugLines != null) {
            return BotRuntime.fishDebugLines;
        }
        return List.of();
    }

    private String scriptVersionTag() {
        if (BotRuntime.woodcuttingEnabled) {
            return "WC v" + BotRuntime.wcPluginVersion;
        }
        if (BotRuntime.fishingEnabled) {
            return "Fish v" + BotRuntime.fishPluginVersion;
        }
        if (BotRuntime.impKillerEnabled) {
            return "Imp v" + BotRuntime.impPluginVersion;
        }
        if (BotRuntime.imps2Enabled) {
            return "Imps2 v" + BotRuntime.imps2PluginVersion;
        }
        if (BotRuntime.starMinerEnabled) {
            return "Star v" + BotRuntime.starPluginVersion;
        }
        if (BotRuntime.giantsKillerEnabled) {
            return "Giants v" + BotRuntime.giantsPluginVersion;
        }
        if (BotRuntime.questEnabled) {
            return "Quest v" + BotRuntime.questPluginVersion;
        }
        return null;
    }

    private void syncFromRuntime() {
        if (!BotRuntime.botEnabled) {
            if (WorldWalker.isActive()) {
                currentStatus = "WorldWalker — ESC of STOP om te stoppen";
                activeSkill = "WorldWalker";
                lastAction = "Pathfind";
                return;
            }
            if (BotRuntime.pausedForResume) {
                currentStatus = "⏸ Bot gepauzeerd — Start om verder te gaan";
                activeSkill = "Pauze";
            } else {
                currentStatus = "⏹ Bot gestopt — Start begint opnieuw";
                activeSkill = "Uit";
            }
            lastAction = "Besturing";
            return;
        }
        if (BotRuntime.woodcuttingEnabled) {
            activeSkill = "Woodcutting";
            String st = BotRuntime.wcStatus != null ? BotRuntime.wcStatus : "";
            if (st.isBlank() || st.contains("starten") || "bot uit".equals(st) || "uit".equals(st)) {
                currentStatus = "WC tickt…";
            } else {
                currentStatus = st;
            }
            long age = BotRuntime.loopLastTickMs > 0L
                    ? System.currentTimeMillis() - BotRuntime.loopLastTickMs : 99_999L;
            if (age > 4_000L) {
                currentStatus = "loop stil " + (age / 1000) + "s — herstart client";
            }
        } else if (BotRuntime.fishingEnabled) {
            activeSkill = "Fishing";
            String st = BotRuntime.fishStatus != null ? BotRuntime.fishStatus : "";
            currentStatus = st.isBlank() || "bot uit".equals(st) || "uit".equals(st) ? "Fish tickt…" : st;
        } else if (BotRuntime.impKillerEnabled) {
            activeSkill = "Imp Killer";
            if (BotRuntime.impStatus != null && !BotRuntime.impStatus.isBlank()) {
                currentStatus = BotRuntime.impStatus;
            }
        } else if (BotRuntime.imps2Enabled) {
            activeSkill = "Imps2 walk";
            if (BotRuntime.imps2Status != null && !BotRuntime.imps2Status.isBlank()) {
                currentStatus = BotRuntime.imps2Status;
            }
        } else if (BotRuntime.starMinerEnabled) {
            activeSkill = "Star Miner";
            String st = BotRuntime.starStatus != null ? BotRuntime.starStatus : "";
            currentStatus = st.isBlank() || "bot uit".equals(st) || "uit".equals(st) ? "Star tickt…" : st;
        } else if (BotRuntime.giantsKillerEnabled) {
            activeSkill = "Giants";
            String st = BotRuntime.giantsStatus != null ? BotRuntime.giantsStatus : "";
            currentStatus = st.isBlank() || "bot uit".equals(st) || "uit".equals(st) ? "Giants tickt…" : st;
        } else if (BotRuntime.questEnabled) {
            activeSkill = "Quest Bot";
            String st = BotRuntime.questStatus != null ? BotRuntime.questStatus : "";
            currentStatus = st.isBlank() || "bot uit".equals(st) || "uit".equals(st) ? "Quest tickt…" : st;
        } else if (BotRuntime.cowCombatEnabled) {
            activeSkill = "Cow Combat";
            if (BotRuntime.cowStatus != null && !BotRuntime.cowStatus.isBlank()) {
                currentStatus = BotRuntime.cowStatus;
            }
        } else if (BotRuntime.monkKillerEnabled) {
            activeSkill = "Monk Killer";
            if (BotRuntime.monkStatus != null && !BotRuntime.monkStatus.isBlank()) {
                currentStatus = BotRuntime.monkStatus;
            }
        } else if (BotRuntime.cityCircleTestEnabled) {
            activeSkill = "F2P banken-cirkel";
            String st = BotRuntime.cityCircleStatus != null ? BotRuntime.cityCircleStatus : "";
            currentStatus = st.isBlank() || "uit".equals(st) ? "F2P banken-cirkel tickt…" : st;
        } else if (BotRuntime.varrockEastBankTestEnabled) {
            activeSkill = "Bank-test";
            String st = BotRuntime.bankTestStatus != null ? BotRuntime.bankTestStatus : "";
            currentStatus = st.isBlank() || "uit".equals(st) ? "Bank-test tickt…" : st;
        } else {
            activeSkill = "Idle";
            currentStatus = "Bot aan — geen script aangevinkt";
        }

        if (BotRuntime.breakPending || BotRuntime.relogFlowActive) {
            String br = BotRuntime.relogLauncherLabel;
            if (br == null || br.isBlank()) {
                br = BotRuntime.lastRelogStatus;
            }
            if (br != null && !br.isBlank()) {
                currentStatus = br;
            }
        }

        String ab = AntiBanSettings.enabled ? AntiBan.get().getLastActionLabel() : "";
        if (ab != null && !ab.isBlank()) {
            lastAction = ab;
        } else if (BotRuntime.debugSummary != null && !BotRuntime.debugSummary.isBlank()) {
            lastAction = BotRuntime.debugSummary;
        }
    }

    private static String whereHeading() {
        if (WorldWalker.isActive()) {
            return "WorldWalker";
        }
        if (BotRuntime.starMinerEnabled) {
            String doel = prefixed(BotRuntime.starDebugLines, "Doel:");
            if (doel != null && !doel.isBlank()) {
                return doel;
            }
        }
        if (BotRuntime.woodcuttingEnabled) {
            String center = prefixed(BotRuntime.wcDebugLines, "Center:");
            if (center != null && !center.isBlank() && !"(geen)".equals(center)) {
                return center;
            }
        }
        if (BotRuntime.giantsKillerEnabled) {
            return "Hill Giants 3115,9837";
        }
        if (BotRuntime.questEnabled) {
            String tgt = BotRuntime.questTarget != null ? BotRuntime.questTarget : "ROTATION";
            return "Quest " + tgt;
        }
        return null;
    }

    private static WorldPoint whereTile() {
        if (WorldWalker.isActive()) {
            WorldPoint w = WorldWalker.destination();
            if (w != null) {
                return w;
            }
        }
        WorldPoint active = MovementHelper.getActiveDestination();
        if (active != null) {
            return active;
        }
        return BotRuntime.debugTarget;
    }

    private static WorldPoint localTile() {
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            return me != null && me.present ? me.worldLocation : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int distTiles(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return -1;
        }
        return from.distanceTo(to);
    }

    private static String prefixed(List<String> lines, String prefix) {
        if (lines == null || prefix == null) {
            return null;
        }
        for (String s : lines) {
            if (s != null && s.startsWith(prefix)) {
                String rest = s.substring(prefix.length()).trim();
                return rest.isEmpty() ? null : rest;
            }
        }
        return null;
    }

    private Color borderColor() {
        if (!BotRuntime.botEnabled) {
            return new Color(120, 120, 120);
        }
        if (BotRuntime.woodcuttingEnabled) {
            return new Color(80, 180, 100);
        }
        if (BotRuntime.fishingEnabled) {
            return new Color(80, 160, 255);
        }
        if (BotRuntime.impKillerEnabled) {
            return new Color(220, 140, 50);
        }
        if (BotRuntime.imps2Enabled) {
            return new Color(200, 110, 50);
        }
        if (BotRuntime.starMinerEnabled) {
            return new Color(160, 120, 220);
        }
        if (BotRuntime.giantsKillerEnabled) {
            return new Color(140, 190, 80);
        }
        if (BotRuntime.questEnabled) {
            return new Color(210, 160, 70);
        }
        if (BotRuntime.cowCombatEnabled) {
            return new Color(200, 160, 60);
        }
        return new Color(80, 140, 200);
    }

    private Color statusColor() {
        String s = currentStatus != null ? currentStatus : "";
        if (s.startsWith("⏸")) {
            return new Color(160, 160, 160);
        }
        if (s.startsWith("⏹")) {
            return new Color(180, 120, 120);
        }
        if (s.startsWith("⚠") || s.contains("err") || s.contains("fail")) {
            return new Color(255, 80, 80);
        }
        if (s.contains("→") || s.contains("naar") || s.contains("walk") || s.contains("lopen")) {
            return new Color(100, 200, 255);
        }
        if (s.contains("chop") || s.contains("Chop")) {
            return new Color(120, 220, 140);
        }
        if (s.contains("bank") || s.contains("Bank")) {
            return new Color(255, 200, 80);
        }
        if (s.contains("fm") || s.contains("fire") || s.contains("bonfire")) {
            return new Color(255, 140, 80);
        }
        if (s.contains("Mine") || s.contains("minen") || s.contains("hop")) {
            return new Color(200, 170, 255);
        }
        return Color.WHITE;
    }

    private Dimension renderMinimizedChip(Graphics2D g) {
        int x = 10;
        int y = 20;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(x, y, CHIP_SIZE + 8, CHIP_SIZE + 8, 6, 6);
        g.setColor(borderColor());
        g.fillRoundRect(x + 4, y + 4, CHIP_SIZE, CHIP_SIZE, 4, 4);
        g.setColor(TITLE_GOLD);
        g.drawRoundRect(x, y, CHIP_SIZE + 8, CHIP_SIZE + 8, 6, 6);
        return new Dimension(CHIP_SIZE + 16, CHIP_SIZE + 16);
    }

    private void drawTitleChip(Graphics2D g, int x, int y, Color fill) {
        g.setColor(fill);
        g.fillRoundRect(x, y, 12, 12, 3, 3);
        g.setColor(TITLE_GOLD);
        g.drawRoundRect(x, y, 12, 12, 3, 3);
    }

    private void tickSessionClock() {
        boolean on = BotRuntime.botEnabled;
        long now = System.currentTimeMillis();
        if (on && !wasBotEnabled) {
            if (BotRuntime.lastStartWasResume && sessionStart != null) {
                // Pauze → Start: timer door laten lopen vanaf bevroren stand
                sessionStart = Instant.now().minusMillis(Math.max(0L, frozenElapsedMs));
            } else {
                beginFreshSession();
            }
        } else if (!on && wasBotEnabled) {
            if (sessionStart != null) {
                frozenElapsedMs = Math.max(0L, Duration.between(sessionStart, Instant.now()).toMillis());
            }
            lastSessionTickMs = 0L;
        }

        if (on && sessionStart != null) {
            ensureXpBaselines();
            long dt = lastSessionTickMs > 0L ? now - lastSessionTickMs : 0L;
            if (dt > 0L && dt < 5_000L) {
                accumulateSkillTime(dt);
            }
            lastSessionTickMs = now;
        }
        wasBotEnabled = on;
    }

    private void beginFreshSession() {
        sessionStart = Instant.now();
        frozenElapsedMs = 0L;
        miningActiveMs = 0L;
        fishActiveMs = 0L;
        wcActiveMs = 0L;
        combatActiveMs = 0L;
        lastSessionTickMs = 0L;
        xpInit = false;
        ensureXpBaselines();
    }

    private void accumulateSkillTime(long dtMs) {
        BotRuntime.ActiveSkill skill = BotRuntime.activeSkill;
        if (skill == null) {
            return;
        }
        switch (skill) {
            case STAR:
                miningActiveMs += dtMs;
                break;
            case FISHING:
                fishActiveMs += dtMs;
                break;
            case WOODCUTTING:
                wcActiveMs += dtMs;
                break;
            case GIANTS:
            case IMP:
            case IMP2:
            case COW:
            case MONK:
                combatActiveMs += dtMs;
                break;
            default:
                break;
        }
    }

    private long sessionElapsedMs() {
        if (BotRuntime.botEnabled && sessionStart != null) {
            return Math.max(0L, Duration.between(sessionStart, Instant.now()).toMillis());
        }
        if (frozenElapsedMs > 0L) {
            return frozenElapsedMs;
        }
        return 0L;
    }

    private void ensureXpBaselines() {
        if (xpInit) {
            return;
        }
        try {
            startWcXp = Skills.getExperience(Skill.WOODCUTTING);
            startFmXp = Skills.getExperience(Skill.FIREMAKING);
            startFishXp = Skills.getExperience(Skill.FISHING);
            startMiningXp = Skills.getExperience(Skill.MINING);
            startAttackXp = Skills.getExperience(Skill.ATTACK);
            xpInit = true;
        } catch (Throwable ignored) {
        }
    }

    private static long xpGained(Skill skill, long start) {
        if (start < 0) {
            return 0;
        }
        try {
            return Math.max(0, Skills.getExperience(skill) - start);
        } catch (Throwable t) {
            return 0;
        }
    }

    private String formatRuntime() {
        return formatDurationMs(sessionElapsedMs());
    }

    private static String formatDurationMs(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }

    private static String formatXp(long xp) {
        if (xp >= 1_000_000) {
            return String.format("%.1fM", xp / 1_000_000.0);
        }
        if (xp >= 1_000) {
            return String.format("%.1fK", xp / 1_000.0);
        }
        return String.valueOf(xp);
    }

    private static String trim(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "-";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
