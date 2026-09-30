package com.lonebot.example.clue;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.widgets.WidgetGroup;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Static;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.widgets.Dialog;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Open / read beginner clue scroll widget text.
 * Read-pad = CombatBot {@code BeginnerClueHandler#readClueTextFromWidgets}:
 * group-scan + BFS children, één client-thread dump (geen nested getAll/isHidden).
 */
public final class BeginnerClueContainerHelper {

    /** CombatBot primary — niet alleen WidgetGroup-constante. */
    private static final int CLUE_TEXT_GROUP_PRIMARY = 203;
    private static final int[] CLUE_TEXT_GROUP_FALLBACKS = {78, 141, 259};
    private static final int[] CLUE_TEXT_EXTRA_GROUPS = {193, 219, 229, 248, 345, 712};
    private static final int[] CLUE_TEXT_GROUPS = {
            CLUE_TEXT_GROUP_PRIMARY,
            WidgetGroup.CLUE_SCROLL_GROUP_ID,
            78, 141, 259, 193, 219, 229, 248, 345, 712
    };
    private static final int[] DIALOG_BODY_GROUPS = {
            WidgetGroup.DIALOG_NPC_GROUP_ID,
            WidgetGroup.DIALOG_PLAYER_GROUP_ID,
            WidgetGroup.DIALOG_SPRITE_GROUP_ID,
            WidgetGroup.DIALOG_OPTION_GROUP_ID
    };
    /** Non-blocking: poll clue UI over meerdere ticks (geen 5s Sleep in de loop). */
    private static final long WIDGET_WAIT_MS = 5_000L;
    private static final long RECLICK_COOLDOWN_MS = 1_600L;

    private String cachedText = "";
    private long lastOpenMs;
    private long readDeadlineMs;
    private boolean readPending;

    public void reset() {
        cachedText = "";
        lastOpenMs = 0L;
        readDeadlineMs = 0L;
        readPending = false;
    }

    public String cachedText() {
        return cachedText;
    }

    public boolean hasCachedText() {
        return cachedText != null && !cachedText.isBlank();
    }

    public String readOpenOrCached() {
        String live = readVisibleClueText();
        if (live != null && !live.isBlank()) {
            cachedText = live;
            return live;
        }
        return cachedText;
    }

    public int openAndRead() {
        String live = readVisibleClueText();
        if (live != null && !live.isBlank()) {
            cachedText = live;
            readPending = false;
            BotRuntime.logConsole("[Clue/Read] " + preview(live));
            return 200;
        }
        if (openMap() != null) {
            readPending = false;
            return 200;
        }

        long now = System.currentTimeMillis();
        boolean uiOpen = isClueInterfaceOpen();

        if (readPending) {
            live = readVisibleClueText();
            if (live != null && !live.isBlank()) {
                cachedText = live;
                readPending = false;
                BotRuntime.logConsole("[Clue/Read] " + preview(live));
                return 250;
            }
            if (now < readDeadlineMs) {
                // UI al open → niet opnieuw Read klikken (CombatBot awaitingClueWidgetAfterRead)
                return 300;
            }
            readPending = false;
            if (uiOpen) {
                // Interface open maar tekst niet gelezen — één dump-log, opnieuw wachten, niet spam-klikken
                BotRuntime.logConsole("[Clue/Read] UI open, tekst nog leeg — retry wait");
                readPending = true;
                readDeadlineMs = now + WIDGET_WAIT_MS;
                return 400;
            }
            BotRuntime.logConsole("[Clue/Read] widget timeout");
            // doorvallen naar re-Read alleen als UI echt dicht is
        }

        // CombatBot: cooldown tussen Read-klikken
        if (now - lastOpenMs < RECLICK_COOLDOWN_MS) {
            return 350;
        }

        // Al open → niet opnieuw klikken
        if (uiOpen) {
            readPending = true;
            readDeadlineMs = now + WIDGET_WAIT_MS;
            BotRuntime.logConsole("[Clue/Read] UI al open — wacht op tekst");
            return 300;
        }

        IInventoryItem clue = findBeginnerClue();
        if (clue == null) {
            return 400;
        }
        String action = clue.hasAction("Read") ? "Read"
                : (clue.hasAction("Read-clue") ? "Read-clue"
                : (clue.hasAction("Open") ? "Open" : null));
        if (action == null) {
            BotRuntime.logConsole("[Clue/Read] geen Read-actie");
            return 600;
        }
        if (!clue.interact(action)) {
            BotRuntime.logConsole("[Clue/Read] Read fail");
            return 600;
        }
        lastOpenMs = now;
        readPending = true;
        readDeadlineMs = now + WIDGET_WAIT_MS;
        BotRuntime.logConsole("[Clue/Read] open scroll…");
        return 350;
    }

    /**
     * CombatBot-parity: scan primary/fallback/extra groups + range 196–220;
     * BFS children; body-heuristiek. Alles in één client-thread call.
     */
    public String readVisibleClueText() {
        String best = Static.callOnClientThread(BeginnerClueContainerHelper::dumpClueTextOnClientThread, "");
        if (best == null || best.length() < 12) {
            return null;
        }
        return best;
    }

    private static String dumpClueTextOnClientThread() {
        Client c = Static.getClient();
        if (c == null) {
            return "";
        }
        String best = "";
        best = longer(best, scanGroups(c, CLUE_TEXT_GROUP_PRIMARY));
        best = longer(best, scanGroups(c, CLUE_TEXT_GROUP_FALLBACKS));
        if (WidgetGroup.CLUE_SCROLL_GROUP_ID != CLUE_TEXT_GROUP_PRIMARY) {
            best = longer(best, scanGroups(c, WidgetGroup.CLUE_SCROLL_GROUP_ID));
        }
        if (best.length() < 12) {
            best = longer(best, scanGroups(c, CLUE_TEXT_EXTRA_GROUPS));
        }
        if (best.length() < 12) {
            best = longer(best, scanGroupRange(c, 196, 220));
        }
        return best.length() >= 12 ? best : "";
    }

    private static String scanGroups(Client c, int... groups) {
        String best = "";
        for (int group : groups) {
            for (int child = 0; child <= 20; child++) {
                Widget w = c.getWidget(group, child);
                String t = extractLongClueText(w);
                if (t.length() > best.length()) {
                    best = t;
                }
            }
        }
        return best;
    }

    private static String scanGroupRange(Client c, int groupMin, int groupMax) {
        String best = "";
        for (int group = groupMin; group <= groupMax; group++) {
            for (int child = 0; child <= 6; child++) {
                Widget w = c.getWidget(group, child);
                String t = extractLongClueText(w);
                if (t.length() > best.length()) {
                    best = t;
                }
            }
        }
        return best;
    }

    private static String extractLongClueText(Widget root) {
        if (root == null) {
            return "";
        }
        String best = "";
        Deque<Widget> q = new ArrayDeque<>();
        q.add(root);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 400) {
            Widget w = q.poll();
            if (w == null) {
                continue;
            }
            try {
                if (w.isHidden()) {
                    continue;
                }
            } catch (Throwable ignored) {
                continue;
            }
            String t = widgetText(w);
            if (t.length() >= 12 && t.length() > best.length()
                    && (looksLikeClueBody(t) || BeginnerClueReference.looksLikeCharlie(t)
                    || BeginnerClueReference.anagramNpc(t) != null
                    || BeginnerClueReference.crypticNpc(t) != null
                    || BeginnerClueReference.emoteStep(t) != null
                    || BeginnerClueReference.searchStep(t) != null
                    || BeginnerClueReference.looksLikeHotCold(t)
                    || BeginnerClueReference.looksLikeAnagram(t))) {
                best = t;
            }
            enqueueChildren(q, w.getChildren());
            enqueueChildren(q, w.getStaticChildren());
            try {
                enqueueChildren(q, w.getDynamicChildren());
            } catch (Throwable ignored) {
            }
        }
        return best;
    }

    private static void enqueueChildren(Deque<Widget> q, Widget[] kids) {
        if (kids == null) {
            return;
        }
        for (Widget c : kids) {
            if (c != null) {
                q.add(c);
            }
        }
    }

    private static String widgetText(Widget w) {
        try {
            String t = w.getText();
            if (t == null) {
                return "";
            }
            return ClueScrollHelper.strip(t).replace('\n', ' ').trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** CombatBot looksLikeClueBody. */
    private static boolean looksLikeClueBody(String t) {
        String low = t.toLowerCase(Locale.ROOT);
        return low.contains("talk") || low.contains("emote") || low.contains("dig")
                || low.contains("anagram") || low.contains("challenge")
                || low.contains("speak") || low.contains("cheer") || low.contains("bow")
                || low.contains("clap") || low.contains("spin") || low.contains("panic")
                || low.contains("raspberry") || low.contains("walks") || low.contains("walking")
                || low.contains("near") || low.contains("buried") || low.contains("charlie")
                || low.contains("tramp") || low.contains("give") || low.contains("need to give")
                || low.contains("device") || low.contains("map")
                || low.contains("search") || low.contains("castle") || low.contains("duke")
                || low.contains("barbarian") || low.contains("desert") || low.contains("varrock")
                || low.contains("lumbridge") || low.contains("falador") || low.contains("draynor")
                || low.contains("standing") || low.contains("stones") || low.contains("destination")
                || low.contains("casket") || low.contains("champions") || low.contains("wizards");
    }

    private static String longer(String a, String b) {
        if (b == null) {
            return a != null ? a : "";
        }
        if (a == null || b.length() > a.length()) {
            return b;
        }
        return a;
    }

    public String readDialogBody() {
        if (Dialog.isOpen()) {
            String d = Dialog.getText();
            if (d != null && !d.isBlank()) {
                return ClueScrollHelper.strip(d);
            }
        }
        String best = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int group : DIALOG_BODY_GROUPS) {
                for (int child = 0; child <= 12; child++) {
                    Widget w = c.getWidget(group, child);
                    String t = widgetText(w);
                    if (t.isEmpty()) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(t);
                }
            }
            return sb.toString();
        }, "");
        String t = ClueScrollHelper.strip(best != null ? best : "");
        return t.isEmpty() ? null : t;
    }

    private static final int MAP_INTERFACE_SCAN_MIN = 195;
    private static final int MAP_INTERFACE_SCAN_MAX = 225;

    public BeginnerClueReference.MapDig openMap() {
        for (BeginnerClueReference.MapDig map : BeginnerClueReference.maps()) {
            if (isMapInterfaceGroupVisible(map.groupId)) {
                return map;
            }
        }
        // CombatBot: scan 195–225 + widget-text fallback (iface-id drift)
        for (int group = MAP_INTERFACE_SCAN_MIN; group <= MAP_INTERFACE_SCAN_MAX; group++) {
            if (!isMapInterfaceGroupVisible(group)) {
                continue;
            }
            BeginnerClueReference.MapDig known = BeginnerClueReference.matchByMapInterfaceGroup(group);
            if (known != null) {
                return known;
            }
            String text = scanGroupText(group);
            BeginnerClueReference.MapDig fromText = BeginnerClueReference.matchMapClueText(text);
            if (fromText != null) {
                BotRuntime.logConsole("[Clue/Map] scan group=" + group + " → " + fromText.label);
                return fromText;
            }
        }
        return null;
    }

    /** Map-clue UI: groot zichtbaar widget of bekende iface-groep (CombatBot). */
    private static boolean isMapInterfaceGroupVisible(int group) {
        Boolean open = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            for (int child = 0; child <= 25; child++) {
                Widget w = c.getWidget(group, child);
                if (w == null) {
                    continue;
                }
                try {
                    if (w.isHidden()) {
                        continue;
                    }
                    java.awt.Rectangle b = w.getBounds();
                    if (b != null && b.width >= 100 && b.height >= 100) {
                        return true;
                    }
                    if (child <= 6) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            return false;
        }, false);
        return Boolean.TRUE.equals(open);
    }

    private static String scanGroupText(int group) {
        String raw = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int child = 0; child <= 12; child++) {
                Widget w = c.getWidget(group, child);
                appendWidgetText(sb, w, 0);
            }
            return sb.toString();
        }, "");
        return ClueScrollHelper.strip(raw != null ? raw : "");
    }

    private static void appendWidgetText(StringBuilder sb, Widget w, int depth) {
        if (w == null || depth > 4) {
            return;
        }
        try {
            if (w.isHidden()) {
                return;
            }
            String t = w.getText();
            if (t != null && !t.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t);
            }
            Widget[] kids = w.getChildren();
            if (kids == null) {
                kids = w.getDynamicChildren();
            }
            if (kids != null) {
                for (Widget k : kids) {
                    appendWidgetText(sb, k, depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isClueInterfaceOpen() {
        if (openMap() != null) {
            return true;
        }
        Boolean open = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            for (int group : CLUE_TEXT_GROUPS) {
                for (int child = 0; child < 6; child++) {
                    Widget w = c.getWidget(group, child);
                    if (w == null) {
                        continue;
                    }
                    try {
                        if (!w.isHidden()) {
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return false;
        }, false);
        return Boolean.TRUE.equals(open);
    }

    public static boolean isRewardOpen() {
        return isGroupOpen(WidgetGroup.CLUE_SCROLL_REWARD_GROUP_ID);
    }

    static boolean isGroupOpen(int group) {
        Boolean open = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            for (int child = 0; child < 6; child++) {
                Widget w = c.getWidget(group, child);
                if (w == null) {
                    continue;
                }
                try {
                    if (!w.isHidden()) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            return false;
        }, false);
        return Boolean.TRUE.equals(open);
    }

    public static IInventoryItem findBeginnerClue() {
        for (IInventoryItem item : Inventory.getAll()) {
            if (item == null) {
                continue;
            }
            // Alleen echte scroll — geen geode/bottle/nest (die heten ook "clue … beginner").
            if (ClueScrollHelper.isBeginnerClueName(item.getName())) {
                return item;
            }
            if (item.getId() == 23182) {
                return item;
            }
        }
        return null;
    }

    public static IInventoryItem findBeginnerCasket() {
        for (IInventoryItem item : Inventory.getAll()) {
            if (item != null && ClueScrollHelper.isBeginnerCasketName(item.getName())) {
                return item;
            }
        }
        return Inventory.getFirst(BeginnerClueReference.REWARD_CASKET_BEGINNER);
    }

    public static boolean hasBeginnerClue() {
        return findBeginnerClue() != null;
    }

    public static boolean hasBeginnerCasket() {
        return findBeginnerCasket() != null;
    }

    private static String preview(String text) {
        String t = text.replace('\n', ' ');
        if (t.length() > 80) {
            return t.substring(0, 80).toLowerCase(Locale.ROOT) + "…";
        }
        return t;
    }
}
