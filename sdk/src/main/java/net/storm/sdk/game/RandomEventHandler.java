package net.storm.sdk.game;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.widgets.IWidget;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Port van CombatBot random-event handling: Dismiss (alleen als NPC met ons interacteert),
 * Genie Talk-to + lamp-skill, lamp-koeriers (Giles/Niles/Miles).
 */
public final class RandomEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RandomEventHandler.class);

    private static final long ACTION_COOLDOWN_MS = 1500;
    private static final long LAMP_ACTION_COOLDOWN_MS = 250;
    private static final long LAMP_INV_OPEN_GAP_MS = 2200;
    private static final int GENIE_LAMP_ITEM_ID = 2528;
    private static final Set<Integer> GENIE_NPC_IDS = new HashSet<>(Arrays.asList(326, 327));
    private static final Set<String> RANDOM_EVENT_NAMES = new HashSet<>(Arrays.asList(
            "genie",
            "drill demon",
            "freaky forester",
            "frog",
            "mysterious old man",
            "evil bob",
            "capt' arnav",
            "phileas rimor",
            "leo",
            "postie pete",
            "quiz master",
            "sandwich lady",
            "strange plant",
            "dunce",
            "pillory guard",
            "beekeeper",
            "security guard",
            "certer",
            "giles",
            "niles",
            "miles"
    ));
    private static final Set<String> LAMP_COURIER_NAMES = new HashSet<>(Arrays.asList(
            "giles", "niles", "miles"
    ));

    private static volatile long lastActionMs;
    private static volatile long lastLampActionMs;
    private static volatile long lastLampInvMs;
    private static volatile long lastLampSkillSelectMs;
    private static volatile long lampFlowStartedMs;
    private static volatile long lastLampDebugMs;
    private static volatile long lastLampLogMs;
    private static volatile String lastLampLogMsg = "";
    private static volatile int lampInterfaceGroupHint = -1;
    private static volatile String lastStatus = "-";

    private static final long LAMP_FAILSAFE_AFTER_MS = 12_000L;
    private static final long LAMP_DEBUG_INTERVAL_MS = 1500L;
    /** CombatBot: eerst deze groepen, daarna 0–800. */
    private static final int[] LAMP_INTERFACE_GROUPS_PRIORITY = {
            240, 219, 229, 233, 134, 260, 261, 311, 312, 162, 163
    };
    private static final String[] LAMP_SKILL_LABEL_FRAGMENTS = {
            "hitpoints", "hit points", "attack", "strength", "defence", "defense", "magic", "ranged", "prayer",
            "runecraft", "construction", "woodcutting", "fishing", "cooking", "mining", "smithing", "crafting",
            "herblore", "agility", "thieving", "slayer", "farming", "hunter", "fletching", "firemaking",
            "sailing", "choose"
    };

    private RandomEventHandler() {
    }

    public static String lastStatus() {
        return lastStatus;
    }

    public static boolean hasPendingLamp() {
        if (!RandomEventSettings.enabled) {
            return false;
        }
        String preferred = RandomEventSettings.genieLampSkill;
        if (preferred == null || preferred.isEmpty() || "NONE".equalsIgnoreCase(preferred)) {
            return false;
        }
        if (lampFlowStartedMs > 0L || lampInterfaceGroupHint >= 0) {
            return true;
        }
        try {
            return Inventory.getFirst(item -> item != null && item.getId() == GENIE_LAMP_ITEM_ID) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Bij de bank: lamp eerst (UI of lamp in inv). Niet tijdens verre travel — dan doorlopen.
     */
    public static boolean shouldYieldForLampAtBank() {
        if (isBusy()) {
            return true;
        }
        if (!hasPendingLamp()) {
            return false;
        }
        try {
            return !isFarTravel();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Lamp-flow of random-NPC die met ons interacteert — niet uitloggen. */
    public static boolean isBusy() {
        if (!Game.isLoggedIn()) {
            return false;
        }
        if (lampFlowStartedMs > 0L || lampInterfaceGroupHint >= 0) {
            return true;
        }
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            if (!me.present || me.worldLocation == null) {
                return false;
            }
            WorldPoint here = me.worldLocation;
            INPC randomNpc = NPCs.getNearest(npc -> {
                if (npc == null || npc.getName() == null || npc.getWorldLocation() == null) {
                    return false;
                }
                if (npc.getWorldLocation().distanceTo(here) > 6) {
                    return false;
                }
                String n = npc.getName().toLowerCase(Locale.ROOT).trim();
                boolean like = npc.hasAction("Dismiss")
                        || RANDOM_EVENT_NAMES.contains(n)
                        || GENIE_NPC_IDS.contains(npc.getId());
                if (!like) {
                    return false;
                }
                return isInteractingWithLocal(npc.getIndex());
            });
            return randomNpc != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * @return delay ms (&gt;0) als er iets gedaan is, anders 0
     */
    public static int tick() {
        if (!RandomEventSettings.enabled || !Game.isLoggedIn()) {
            return 0;
        }
        if (!activityAllows()) {
            return 0;
        }
        if (isFarTravel()) {
            return tickFarTravel();
        }

        long now = System.currentTimeMillis();
        int lamp = handleGenieLamp(now);
        if (lamp > 0) {
            return lamp;
        }

        if (now - lastActionMs < ACTION_COOLDOWN_MS) {
            return 0;
        }

        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 0;
        }
        WorldPoint here = me.worldLocation;

        INPC randomNpc = NPCs.getNearest(npc -> {
            if (npc == null || npc.getName() == null || npc.getWorldLocation() == null) {
                return false;
            }
            if (npc.getWorldLocation().distanceTo(here) > 6) {
                return false;
            }
            String n = npc.getName().toLowerCase(Locale.ROOT).trim();
            boolean like = npc.hasAction("Dismiss")
                    || RANDOM_EVENT_NAMES.contains(n)
                    || GENIE_NPC_IDS.contains(npc.getId());
            if (!like) {
                return false;
            }
            return isInteractingWithLocal(npc.getIndex());
        });
        if (randomNpc == null || randomNpc.getName() == null) {
            return 0;
        }
        return actOnRandomNpc(randomNpc, now);
    }

    /**
     * WorldWalker-tick: Dismiss alleen als een random al praat. Geen plugin, geen scene-scan.
     */
    public static int tickTalkingRandomOnly() {
        try {
            return tickFarTravel();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * WorldWalker / verre dest: geen getName op alle NPCs (1.5s × N = 30s ticks).
     * Alleen als een NPC ons al aanspreekt — Dismiss op díe index.
     */
    private static int tickFarTravel() {
        long now = System.currentTimeMillis();
        if (RandomEventSettings.enabled) {
            int lamp = handleGenieLamp(now);
            if (lamp > 0) {
                return lamp;
            }
        }
        Players.LocalSnap me = Players.snapshotLocal();
        if (me == null || !me.present || !me.interacting || me.interactingNpcIndex < 0) {
            return 0;
        }
        if (now - lastActionMs < ACTION_COOLDOWN_MS) {
            return 0;
        }
        String snapName = me.interactingName != null
                ? me.interactingName.toLowerCase(Locale.ROOT).trim() : "";
        boolean nameHint = RANDOM_EVENT_NAMES.contains(snapName) || snapName.contains("genie");
        INPC npc = NPCs.get(me.interactingNpcIndex);
        if (npc == null) {
            return 0;
        }
        int id = npc.getId();
        if (!nameHint && !GENIE_NPC_IDS.contains(id) && !npc.hasAction("Dismiss")) {
            return 0;
        }
        return actOnRandomNpc(npc, now);
    }

    private static boolean isFarTravel() {
        try {
            if (net.storm.sdk.movement.WorldWalker.isActive()) {
                return true;
            }
            if (net.storm.sdk.bot.BotRuntime.cityCircleTestEnabled
                    || net.storm.sdk.bot.BotRuntime.varrockEastBankTestEnabled) {
                return true;
            }
            Players.LocalSnap me = Players.snapshotLocal();
            WorldPoint dest = net.storm.sdk.movement.MovementHelper.getActiveDestination();
            WorldPoint from = me != null && me.present ? me.worldLocation : null;
            if (dest != null && from != null && from.getPlane() == dest.getPlane()
                    && from.distanceTo(dest) > 12) {
                return true;
            }
            return net.storm.sdk.movement.MovementHelper.isTravelContext();
        } catch (Throwable t) {
            return false;
        }
    }

    private static int actOnRandomNpc(INPC randomNpc, long now) {
        if (randomNpc == null) {
            return 0;
        }
        String name = randomNpc.getName();
        if (name == null) {
            return 0;
        }
        name = name.toLowerCase(Locale.ROOT).trim();
        int npcId = randomNpc.getId();
        boolean hasDismiss = randomNpc.hasAction("Dismiss");
        boolean known = RANDOM_EVENT_NAMES.contains(name) || GENIE_NPC_IDS.contains(npcId);
        if (!known && !hasDismiss) {
            return 0;
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean isGenie = GENIE_NPC_IDS.contains(npcId) || name.contains("genie");
        String preferred = RandomEventSettings.genieLampSkill;
        boolean keepGenie = preferred != null && !preferred.isEmpty()
                && !"NONE".equalsIgnoreCase(preferred);

        if (isGenie && keepGenie) {
            if (randomNpc.hasAction("Talk-to")) {
                if (!invokeNpcAction(randomNpc, "Talk-to")) {
                    return 400 + r.nextInt(300);
                }
                lastActionMs = now;
                setStatus("Genie Talk-to");
                return 900 + r.nextInt(600);
            }
            return 0;
        }

        // Primair: Dismiss via menu-invoke (niet left-click → Talk-to)
        if (hasDismiss) {
            if (!invokeNpcAction(randomNpc, "Dismiss")) {
                setStatus("Dismiss FAIL " + name);
                return 500 + r.nextInt(400);
            }
            lastActionMs = now;
            setStatus("Dismiss " + name);
            return 800 + r.nextInt(500);
        }

        // Lamp-koeriers (Giles/Niles/Miles): als Dismiss ontbreekt, Talk-to → XP-lamp.
        if (LAMP_COURIER_NAMES.contains(name)) {
            if (Dialog.isOpen()) {
                Dialog.continueSpace();
                lastActionMs = now;
                setStatus(name + " dialog");
                return 600 + r.nextInt(400);
            }
            if (randomNpc.hasAction("Talk-to")) {
                if (!invokeNpcAction(randomNpc, "Talk-to")) {
                    return 400 + r.nextInt(300);
                }
                lastActionMs = now;
                setStatus(name + " Talk-to");
                return 900 + r.nextInt(600);
            }
            return 0;
        }

        // Fallback: bekende random event zonder Dismiss → Talk-to
        if (known && randomNpc.hasAction("Talk-to")) {
            if (Dialog.isOpen()) {
                Dialog.continueSpace();
                lastActionMs = now;
                setStatus(name + " dialog");
                return 600 + r.nextInt(400);
            }
            if (!invokeNpcAction(randomNpc, "Talk-to")) {
                return 400 + r.nextInt(300);
            }
            lastActionMs = now;
            setStatus(name + " Talk-to (geen Dismiss)");
            return 900 + r.nextInt(600);
        }
        return 0;
    }

    /** Menu-invoke op NPC-index — vermijdt left-click Talk-to bij Dismiss. */
    private static boolean invokeNpcAction(INPC npc, String action) {
        if (npc == null || action == null) {
            return false;
        }
        boolean ok = MenuInteract.interactNpcByIndex(npc.getIndex(), action);
        if (!ok) {
            // Laatste poging via AimInteract (Dismiss = invoke-only na fix)
            ok = npc.interact(action);
        }
        log.info("[RandomEvent] {} → {} ok={} ({})",
                action, npc.getName(), ok, MenuInteract.getLastProbeDetail());
        return ok;
    }

    private static boolean activityAllows() {
        return BotRuntime.botEnabled
                || BotRuntime.fishingEnabled
                || BotRuntime.woodcuttingEnabled
                || BotRuntime.cowCombatEnabled
                || BotRuntime.impKillerEnabled
                || BotRuntime.starMinerEnabled
                || BotRuntime.cityCircleTestEnabled
                || BotRuntime.imps2Enabled
                || BotRuntime.varrockEastBankTestEnabled;
    }

    private static boolean isInteractingWithLocal(int npcIndex) {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Player local = c.getLocalPlayer();
            if (local == null) {
                return false;
            }
            for (NPC n : c.getNpcs()) {
                if (n == null || n.getIndex() != npcIndex) {
                    continue;
                }
                Actor a = n.getInteracting();
                return a == local;
            }
            return false;
        }, false));
    }

    /**
     * CombatBot lamp-flow: Rub → skill-widget (naam/tekst/actions + vaste ids) → Confirm.
     * Alleen {@link Widgets#getAll(String)} (getText) ziet het scherm niet — daarom group-scan.
     */
    private static int handleGenieLamp(long now) {
        String preferred = RandomEventSettings.genieLampSkill;
        if (preferred == null || preferred.isEmpty() || "NONE".equalsIgnoreCase(preferred)) {
            return 0;
        }
        if (now - lastLampActionMs < LAMP_ACTION_COOLDOWN_MS) {
            return 0;
        }

        ThreadLocalRandom r = ThreadLocalRandom.current();
        IInventoryItem lamp = Inventory.getFirst(item ->
                item != null && item.getId() == GENIE_LAMP_ITEM_ID);
        if (lamp == null && lampFlowStartedMs <= 0) {
            return 0;
        }

        int lampGroup = findLampInterfaceGroup();
        boolean interfaceOpen = lampGroup >= 0;
        if (interfaceOpen) {
            lampInterfaceGroupHint = lampGroup;
            if (lampFlowStartedMs <= 0) {
                lampFlowStartedMs = now;
            }
            if (now - lastLampDebugMs >= LAMP_DEBUG_INTERVAL_MS) {
                lastLampDebugMs = now;
                lampLog("interface open group=" + lampGroup + " skill=" + preferred);
            }
        }

        if (lamp != null && !interfaceOpen) {
            if (now - lastLampInvMs < LAMP_INV_OPEN_GAP_MS) {
                return 400 + r.nextInt(250);
            }
            if (lamp.hasAction("Rub")) {
                lamp.interact("Rub");
            } else if (lamp.hasAction("Use")) {
                lamp.interact("Use");
            } else {
                return 0;
            }
            lastLampActionMs = now;
            lastActionMs = now;
            lastLampInvMs = now;
            if (lampFlowStartedMs <= 0) {
                lampFlowStartedMs = now;
            }
            setStatus("Lamp Rub");
            return 250 + r.nextInt(250);
        }

        if (!interfaceOpen) {
            if (lamp == null) {
                lampFlowStartedMs = 0;
                lastLampSkillSelectMs = 0;
                lampInterfaceGroupHint = -1;
            }
            return 0;
        }

        boolean skillPicked = lastLampSkillSelectMs > 0L && (now - lastLampSkillSelectMs) < 12_000L;
        if (skillPicked) {
            if (clickLampConfirm()) {
                lastLampActionMs = now;
                lastActionMs = now;
                lastLampSkillSelectMs = 0;
                lampFlowStartedMs = 0;
                lampInterfaceGroupHint = -1;
                setStatus("Lamp Confirm");
                return 650 + r.nextInt(500);
            }
            lampLog("Confirm nog niet — skill blijft " + preferred + " (geen herklik)");
            return 280 + r.nextInt(180);
        }

        for (String keyword : keywordsForSkill(preferred)) {
            if (clickLampKeyword(keyword)) {
                lastLampSkillSelectMs = now;
                lastLampActionMs = now;
                lastActionMs = now;
                setStatus("Lamp skill " + preferred);
                return 500 + r.nextInt(450);
            }
        }

        if (clickLampConfirm()) {
            lastLampActionMs = now;
            lastActionMs = now;
            lastLampSkillSelectMs = 0;
            lampFlowStartedMs = 0;
            lampInterfaceGroupHint = -1;
            setStatus("Lamp Confirm");
            return 650 + r.nextInt(500);
        }

        if (lampFlowStartedMs > 0 && (now - lampFlowStartedMs) >= LAMP_FAILSAFE_AFTER_MS) {
            lampLog("FAILSAFE brute-force skill labels group=" + lampGroup);
            for (String frag : LAMP_SKILL_LABEL_FRAGMENTS) {
                if ("confirm".equals(frag) || "choose".equals(frag)) {
                    continue;
                }
                if (clickLampKeyword(frag)) {
                    lastLampSkillSelectMs = now;
                    lastLampActionMs = now;
                    lastActionMs = now;
                    setStatus("Lamp skill failsafe " + frag);
                    return 500 + r.nextInt(450);
                }
            }
            lampLog("FAILSAFE geen skill/confirm — reset");
            lampFlowStartedMs = 0;
            lampInterfaceGroupHint = -1;
            return 400;
        }

        lampLog("interface open, geen klik dit tick skill=" + preferred);
        return 350 + r.nextInt(250);
    }

    private static int findLampInterfaceGroup() {
        long t = System.currentTimeMillis();
        if (lampInterfaceGroupHint >= 0) {
            if (isLampLikeWidgetGroup(lampInterfaceGroupHint)) {
                return lampInterfaceGroupHint;
            }
            if (lampFlowStartedMs > 0 && (t - lampFlowStartedMs) < 15_000L
                    && groupHasNeedle(lampInterfaceGroupHint, "confirm")) {
                return lampInterfaceGroupHint;
            }
            lampInterfaceGroupHint = -1;
        }
        for (int group : LAMP_INTERFACE_GROUPS_PRIORITY) {
            if (isLampLikeWidgetGroup(group)) {
                return group;
            }
        }
        if (lampFlowStartedMs > 0 && (t - lampFlowStartedMs) < 15_000L) {
            for (int group : LAMP_INTERFACE_GROUPS_PRIORITY) {
                if (groupHasNeedle(group, "confirm")) {
                    return group;
                }
            }
        }
        if (lampFlowStartedMs > 0) {
            for (int group = 0; group <= 800; group++) {
                if (isLampLikeWidgetGroup(group)) {
                    return group;
                }
            }
        }
        return -1;
    }

    private static boolean isLampLikeWidgetGroup(int group) {
        if (group < 0 || !groupHasNeedle(group, "confirm")) {
            return false;
        }
        for (String frag : LAMP_SKILL_LABEL_FRAGMENTS) {
            if (groupHasNeedle(group, frag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean groupHasNeedle(int group, String needle) {
        if (needle == null || needle.isEmpty() || group < 0) {
            return false;
        }
        String low = needle.toLowerCase(Locale.ROOT);
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            for (int child = 0; child <= 80; child++) {
                if (widgetTreeHasNeedle(c.getWidget(group, child), low, 0)) {
                    return true;
                }
            }
            return false;
        }, false));
    }

    private static boolean widgetTreeHasNeedle(Widget w, String needleLower, int depth) {
        if (w == null || depth > 10) {
            return false;
        }
        try {
            if (w.isHidden()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        if (widgetHaystackContains(w, needleLower)) {
            Rectangle b = w.getBounds();
            if (b != null && b.width > 0 && b.height > 0) {
                return true;
            }
        }
        Widget[][] packs = {safeChildren(w), safeStatic(w), safeDynamic(w)};
        for (Widget[] pack : packs) {
            if (pack == null) {
                continue;
            }
            for (Widget ch : pack) {
                if (widgetTreeHasNeedle(ch, needleLower, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean clickLampKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        if ("confirm".equalsIgnoreCase(keyword)) {
            return clickLampConfirm();
        }
        String low = keyword.toLowerCase(Locale.ROOT);
        int group = lampInterfaceGroupHint >= 0 ? lampInterfaceGroupHint : findLampInterfaceGroup();
        if (group < 0) {
            group = 240;
        }
        Rectangle b = resolveLampBounds(group, low);
        if (b != null && b.width > 0 && b.height > 0) {
            return clickLampBounds(b, keyword);
        }
        Integer packed = findLampPackedId(group, low);
        if (packed == null && group != 240) {
            packed = findLampPackedId(240, low);
        }
        if (packed == null) {
            lampLog("geen widget '" + keyword + "' group=" + group);
            return false;
        }
        IWidget w = Widgets.get(packed);
        if (w == null || w.isHidden()) {
            return false;
        }
        boolean ok = w.interact("Select")
                || w.interact("Confirm")
                || w.interact(keyword)
                || w.interact(w.getName());
        if (ok) {
            lampLog("interact '" + keyword + "' packed=" + packed);
        }
        return ok;
    }

    /**
     * Confirm is een eigen knop (iface 240,27). De kleinste widget met "confirm" wint: een
     * parent-paneel bevat de tekst ook en zou de klik op een skill laten landen.
     */
    private static boolean clickLampConfirm() {
        int group = lampInterfaceGroupHint >= 0 ? lampInterfaceGroupHint : findLampInterfaceGroup();
        if (group < 0) {
            group = 240;
        }
        Rectangle best = smallestNeedleBounds(group, "confirm");
        if (!validBounds(best) && group != 240) {
            best = smallestNeedleBounds(240, "confirm");
        }
        if (validBounds(best) && clickLampBounds(best, "confirm")) {
            return true;
        }
        for (int child : new int[]{27, 26, 28, 29}) {
            try {
                IWidget w = Widgets.get(group, child);
                if (w != null && !w.isHidden() && (w.interact("Confirm") || w.interact("Select"))) {
                    lampLog("Confirm interact group=" + group + " child=" + child);
                    return true;
                }
            } catch (Throwable ignored) {
            }
            Rectangle b = readBounds(group, child);
            if (isClickableButton(b) && clickLampBounds(b, "confirm-" + child)) {
                return true;
            }
        }
        lampLog("Confirm mis — geen knop group=" + group);
        return false;
    }

    /** Knop, geen paneel — een groot vlak raakt de skill-lijst eronder. */
    private static boolean isClickableButton(Rectangle b) {
        if (!validBounds(b)) {
            return false;
        }
        long area = (long) b.width * b.height;
        return area >= 80L && area <= 80_000L;
    }

    private static Rectangle smallestNeedleBounds(int group, String needle) {
        if (needle == null || needle.isEmpty() || group < 0) {
            return null;
        }
        String low = needle.toLowerCase(Locale.ROOT);
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Rectangle best = null;
            long bestArea = Long.MAX_VALUE;
            for (int child = 0; child <= 80; child++) {
                java.util.List<Widget> hits = new java.util.ArrayList<>();
                collectNeedleWidgets(c.getWidget(group, child), low, 0, hits);
                for (Widget w : hits) {
                    Rectangle b = w.getBounds();
                    if (!isClickableButton(b)) {
                        continue;
                    }
                    long area = (long) b.width * b.height;
                    if (area < bestArea) {
                        bestArea = area;
                        best = b;
                    }
                }
            }
            return best;
        }, null);
    }

    private static void collectNeedleWidgets(Widget w, String needleLower, int depth,
                                             java.util.List<Widget> out) {
        if (w == null || depth > 10) {
            return;
        }
        try {
            if (w.isHidden()) {
                return;
            }
        } catch (Throwable ignored) {
        }
        if (widgetHaystackContains(w, needleLower) && validBounds(w.getBounds())) {
            out.add(w);
        }
        Widget[][] packs = {safeChildren(w), safeStatic(w), safeDynamic(w)};
        for (Widget[] pack : packs) {
            if (pack == null) {
                continue;
            }
            for (Widget ch : pack) {
                collectNeedleWidgets(ch, needleLower, depth + 1, out);
            }
        }
    }

    /** CombatBot hover-test ids: confirm=27, attack=2, magic=5, plus keyword in naam/tekst/actions. */
    private static Rectangle resolveLampBounds(int group, String lowKeyword) {
        Rectangle b = findBoundsByKeyword(group, lowKeyword);
        if (validBounds(b)) {
            return b;
        }
        if ("confirm".equals(lowKeyword)) {
            b = readBounds(group, 27);
            if (validBounds(b)) {
                return b;
            }
            b = readBounds(240, 27);
            if (validBounds(b)) {
                return b;
            }
            b = readBounds(24, 27);
            if (validBounds(b)) {
                return b;
            }
            return findBoundsByKeyword(240, lowKeyword);
        }
        if ("attack".equals(lowKeyword)) {
            b = readBounds(group, 2);
            if (validBounds(b)) {
                return b;
            }
            return readBounds(240, 2);
        }
        if ("magic".equals(lowKeyword)) {
            b = readBounds(group, 5);
            if (validBounds(b)) {
                return b;
            }
            return readBounds(240, 5);
        }
        if (group != 240) {
            return findBoundsByKeyword(240, lowKeyword);
        }
        return null;
    }

    private static boolean clickLampBounds(Rectangle b, String keyword) {
        Canvas canvas = net.storm.sdk.game.Client.getCanvas();
        if (canvas == null) {
            return Mouse.clickOnly(
                    b.x + Math.max(2, b.width / 2),
                    b.y + Math.max(2, b.height / 2),
                    true);
        }
        int x = b.x + Math.max(2, b.width / 2) + ThreadLocalRandom.current().nextInt(-2, 3);
        int y = b.y + Math.max(2, b.height / 2) + ThreadLocalRandom.current().nextInt(-2, 3);
        int maxX = Math.max(20, canvas.getWidth() - 3);
        int maxY = Math.max(20, canvas.getHeight() - 3);
        x = Math.max(3, Math.min(maxX, x));
        y = Math.max(3, Math.min(maxY, y));
        try {
            Mouse.moved(x, y, canvas, System.currentTimeMillis());
            sleepQuiet(40 + ThreadLocalRandom.current().nextInt(50));
            long t = System.currentTimeMillis();
            Mouse.pressed(x, y, canvas, t, MouseEvent.BUTTON1);
            sleepQuiet(45 + ThreadLocalRandom.current().nextInt(50));
            long t2 = System.currentTimeMillis();
            Mouse.released(x, y, canvas, t2, MouseEvent.BUTTON1);
            lampLog("click '" + keyword + "' @ " + x + "," + y
                    + " bounds=" + b.x + "," + b.y + " " + b.width + "x" + b.height);
            return true;
        } catch (Throwable t) {
            lampLog("click '" + keyword + "' fout: " + t.getMessage());
            return Mouse.clickOnly(x, y, true);
        }
    }

    private static Rectangle findBoundsByKeyword(int group, String lowKeyword) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            for (int child = 0; child <= 80; child++) {
                Widget found = findWidgetByNeedle(c.getWidget(group, child), lowKeyword, 0);
                if (found != null) {
                    Rectangle b = found.getBounds();
                    if (validBounds(b)) {
                        return b;
                    }
                }
            }
            return null;
        }, null);
    }

    private static Integer findLampPackedId(int group, String lowKeyword) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            for (int child = 0; child <= 80; child++) {
                Widget found = findWidgetByNeedle(c.getWidget(group, child), lowKeyword, 0);
                if (found != null) {
                    return found.getId();
                }
            }
            return null;
        }, null);
    }

    private static Widget findWidgetByNeedle(Widget w, String needleLower, int depth) {
        if (w == null || depth > 10) {
            return null;
        }
        try {
            if (w.isHidden()) {
                return null;
            }
        } catch (Throwable ignored) {
        }
        if (widgetHaystackContains(w, needleLower)) {
            Rectangle b = w.getBounds();
            if (validBounds(b)) {
                return w;
            }
        }
        Widget[][] packs = {safeChildren(w), safeStatic(w), safeDynamic(w)};
        for (Widget[] pack : packs) {
            if (pack == null) {
                continue;
            }
            for (Widget ch : pack) {
                Widget hit = findWidgetByNeedle(ch, needleLower, depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static Rectangle readBounds(int group, int child) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget w = c.getWidget(group, child);
            if (w == null) {
                return null;
            }
            try {
                if (w.isHidden()) {
                    return null;
                }
            } catch (Throwable ignored) {
            }
            return w.getBounds();
        }, null);
    }

    /** Tekst, naam én menu-actions — lamp-skills zitten vaak niet in getText(). */
    private static boolean widgetHaystackContains(Widget w, String needleLower) {
        if (w == null || needleLower == null || needleLower.isEmpty()) {
            return false;
        }
        if (cleanWidgetStr(w.getText()).contains(needleLower)) {
            return true;
        }
        if (cleanWidgetStr(w.getName()).contains(needleLower)) {
            return true;
        }
        try {
            String[] actions = w.getActions();
            if (actions != null) {
                for (String a : actions) {
                    if (cleanWidgetStr(a).contains(needleLower)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String cleanWidgetStr(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
    }

    private static Widget[] safeChildren(Widget w) {
        try {
            return w.getChildren();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Widget[] safeStatic(Widget w) {
        try {
            return w.getStaticChildren();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Widget[] safeDynamic(Widget w) {
        try {
            return w.getDynamicChildren();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean validBounds(Rectangle b) {
        return b != null && b.width > 0 && b.height > 0;
    }

    private static void sleepQuiet(int ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String[] keywordsForSkill(String skill) {
        if (skill == null) {
            return new String[0];
        }
        String s = skill.trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "HITPOINTS":
                return new String[]{"hitpoints", "hit points"};
            case "DEFENCE":
                return new String[]{"defence", "defense"};
            case "RUNECRAFT":
                return new String[]{"runecraft", "runecrafting"};
            default:
                return new String[]{skill.toLowerCase(Locale.ROOT)};
        }
    }

    private static void lampLog(String msg) {
        log.info("[RandomEvent] lamp {}", msg);
        long now = System.currentTimeMillis();
        if (msg != null && msg.equals(lastLampLogMsg) && now - lastLampLogMs < 1500L) {
            return;
        }
        lastLampLogMs = now;
        lastLampLogMsg = msg != null ? msg : "";
        BotRuntime.logConsole("[RE/lamp] " + msg);
    }

    private static void setStatus(String s) {
        lastStatus = s;
        BotRuntime.randomEventStatus = s;
        BotRuntime.logConsole("[RandomEvent] " + s);
        try {
            if (s != null && !s.isBlank() && !s.equals("uit") && !s.equals("idle")) {
                net.storm.sdk.bot.ActivityLog.step("Random", s);
            }
        } catch (Throwable ignored) {
        }
    }
}
