package com.lonebot.example.woodcutter;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Chat;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.interact.ClickOnSight;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.movement.pathfinder.Pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Forestry events: Rising Roots, Entling (overhead prune), Struggling Sapling (mulch).
 * Kit buy/shop blijft in {@link ForestryKitHandler}.
 */
final class ForestryEventHandler {

    /** Ruimer → event eerder herkend (was 22). */
    private static final int EVENT_RADIUS = 40;
    /** Entling eerder zien / ernaartoe (ruimer dan algemene event-straal). */
    private static final int ENT_SPOT_RADIUS = 52;
    /** Rising roots: bruin + groen; spawn rond boom (wiki 5×5) — ruim scannen. */
    private static final int ROOTS_RADIUS = 40;
    /** Invoke Chop / walk dichterbij. */
    private static final int ROOT_CHOP_RANGE = 2;
    /** Min tijd tussen forestry-kliks (Entling / roots). Max delays ongewijzigd. */
    private static final long INTERACT_COOLDOWN_MS = 140L;
    /** Tussen Collects: kort, zoals spam-klik — id in tas is de waarheid. */
    private static final long SAPLING_TAKE_COOLDOWN_MS = 70L;
    /** Invoke Take/Add-mulch zonder muis (Chebyshev) — walk alleen als verder. */
    private static final int SAPLING_INVOKE_RANGE = 15;
    /** Invoke Prune op Entling — eerder klikbaar. */
    private static final int ENT_INVOKE_RANGE = 20;
    /** Na prune: niet te lang wachten vóór volgende. */
    private static final long ENT_PRUNE_WAIT_MS = 1_000L;
    private static final long ENT_PRUNE_MIN_WAIT_MS = 550L;
    /** ~1 op 5 expres verkeerde prune (humanisatie). */
    private static final int ENT_MISCLICK_DENOM = 5;
    /** RuneLite WoodcuttingPlugin — award na forestry-actie. */
    private static final Pattern ANIMA_BARK_PATTERN = Pattern.compile(
            "You've been awarded <col=[0-9a-f]+>(\\d+) Anima-infused bark</col>\\.",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANIMA_BARK_PLAIN = Pattern.compile(
            "You've been awarded\\s+(\\d+)\\s+Anima-infused bark",
            Pattern.CASE_INSENSITIVE);
    /**
     * Chat: "The sapling seems to love wild mushrooms as the first ingredient."
     * Optioneel punt/uitroep; whitespace soepel.
     */
    private static final Pattern SAPLING_LOVE_SLOT = Pattern.compile(
            "sapling\\s+seems\\s+to\\s+love\\s+(.+?)\\s+as\\s+the\\s+(first|second|third)\\s+ingredient",
            Pattern.CASE_INSENSITIVE);

    private static long lastInteractMs;
    private static long lastRootChopClickMs;
    private static long lastEntPruneClickMs;
    private static int lastEntPruneTargetIndex = -1;
    private static final Integer[] saplingOrderSlots = new Integer[3];
    private static final String[] saplingOrderNames = new String[3];
    private static int mulchSlotIndex;
    private static int mulchCollectClicks;
    private static Integer mulchIngredientId;
    /** Huidige batch: 3 ingredient-ids (AAA trial of OOO/70% recipe). */
    private static final Integer[] mulchRecipeIds = new Integer[3];
    private static boolean mulchBatchPlanned;
    private static boolean mulchBatchIsSeventy;
    /** Volgende pile bij onbekende slot (trial). */
    private static int saplingTrialCursor;
    /** Straal rond sapling (stort): piles hierbinnen tellen voor 100%/70%-keuze. */
    private static final int SAPLING_DEPOSIT_RADIUS = 7;
    /** Opeenvolgende Take-fails op dezelfde pile → andere ingredient. */
    private static final int MULCH_TAKE_FAIL_ROTATE = 5;
    private static int mulchTakeFails;
    /** Laatst gelogde tas-stage (throttled collect-log). */
    private static int lastLoggedMulchStage = -1;
    private static final Map<Integer, String> entRequests = new HashMap<>();
    private static final Map<Integer, Integer> entCompoundPhase = new HashMap<>();
    private static final Set<Integer> entCompletedIndices = new HashSet<>();
    /** Anima-infused bark dit event (reset als event weg is). */
    private static int eventAnimaBark;
    /** Totaal deze WC-sessie (reset bij loop.reset). */
    private static int sessionAnimaBark;
    private static boolean lastEventActive;

    private ForestryEventHandler() {
    }

    static int eventAnimaBark() {
        return eventAnimaBark;
    }

    static int sessionAnimaBark() {
        return sessionAnimaBark;
    }

    /** Statusregel tijdens forestry-event (anima-teller). */
    static String statusLine() {
        return statusWithAnima("forestry event");
    }

    static String statusWithAnima(String base) {
        int kit = ForestryKitHandler.animaBarkStored();
        if (eventAnimaBark > 0) {
            return base + " · kit " + kit + " · +" + eventAnimaBark;
        }
        if (kit > 0) {
            return base + " · kit " + kit;
        }
        return base;
    }

    static void reset() {
        lastInteractMs = 0L;
        lastRootChopClickMs = 0L;
        lastEntPruneClickMs = 0L;
        lastEntPruneTargetIndex = -1;
        Arrays.fill(saplingOrderSlots, null);
        Arrays.fill(saplingOrderNames, null);
        resetSaplingProgress();
        entRequests.clear();
        entCompoundPhase.clear();
        entCompletedIndices.clear();
        eventAnimaBark = 0;
        sessionAnimaBark = 0;
        lastEventActive = false;
    }

    static void syncEventAnimaBoundary() {
        boolean active = hasActiveEventNearby();
        if (!lastEventActive && active) {
            WcDebug.once("event", "event herkend (r=" + EVENT_RADIUS + ")");
        }
        if (lastEventActive && !active) {
            eventAnimaBark = 0;
        }
        lastEventActive = active;
    }

    private static void resetSaplingProgress() {
        mulchSlotIndex = 0;
        mulchCollectClicks = 0;
        mulchIngredientId = null;
        saplingTrialCursor = 0;
        mulchTakeFails = 0;
        lastLoggedMulchStage = -1;
        clearMulchRecipe();
    }

    private static void clearMulchRecipe() {
        Arrays.fill(mulchRecipeIds, null);
        mulchBatchPlanned = false;
        mulchBatchIsSeventy = false;
        mulchTakeFails = 0;
    }

    private static int countKnownSaplingSlots() {
        int n = 0;
        for (int i = 0; i < 3; i++) {
            if (saplingOrderSlots[i] != null) {
                n++;
            } else if (saplingOrderNames[i] != null && !saplingOrderNames[i].isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static int parseAnimaBarkAward(String message) {
        if (message == null || message.isEmpty()) {
            return 0;
        }
        String m = message.trim();
        Matcher tagged = ANIMA_BARK_PATTERN.matcher(m);
        if (tagged.matches()) {
            return Integer.parseInt(tagged.group(1));
        }
        Matcher plain = ANIMA_BARK_PLAIN.matcher(m);
        if (plain.find()) {
            return Integer.parseInt(plain.group(1));
        }
        return 0;
    }

    static void onGameMessage(String message) {
        if (message == null) {
            return;
        }
        String m = message.trim();
        String plain = stripColorTags(m);
        String lower = plain.toLowerCase(Locale.ROOT);

        int bark = parseAnimaBarkAward(m);
        if (bark <= 0) {
            bark = parseAnimaBarkAward(plain);
        }
        if (bark > 0) {
            eventAnimaBark += bark;
            sessionAnimaBark += bark;
            ForestryKitHandler.onAnimaBarkAwarded(bark);
            WcDebug.log("event", "+anima " + bark + " (event=" + eventAnimaBark
                    + " sessie=" + sessionAnimaBark
                    + " kit=" + ForestryKitHandler.animaBarkStored()
                    + "/" + ForestryKitHandler.animaBarkSource() + ")");
            return;
        }

        if (lower.contains("sapling seems to love")
                || (lower.contains("seems to love") && lower.contains("as the")
                && lower.contains("ingredient"))) {
            rememberSaplingFromLoveMessage(plain);
            return;
        }

        if (lower.contains("short on top") || lower.contains("leafy mullet")
                || lower.contains("short back and sides") || lower.contains("breezy at the back")) {
            String req = classifyEntRequest(lower);
            if (req == null) {
                return;
            }
            INPC ent = findEntlingMatchingRequest(req);
            if (ent != null) {
                rememberEntRequest(ent.getIndex(), req, "chat");
            }
        }
    }

    static boolean hasActiveEventNearby() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me.present && me.worldLocation != null && hasAny(me);
    }

    static int tick() {
        syncEventAnimaBoundary();
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return 0;
        }
        if (!hasAny(me)) {
            // Geen slots wissen hier — korte gap (roots/scene) wiste known=0 en forceerde AAA-loop
            resetSaplingProgress();
            entRequests.clear();
            entCompoundPhase.clear();
            entCompletedIndices.clear();
            return tryDestroyLeftovers();
        }

        long now = System.currentTimeMillis();
        boolean sapBurst = saplingTakeBurstActive();
        long cool = sapBurst ? SAPLING_TAKE_COOLDOWN_MS : INTERACT_COOLDOWN_MS;
        if (cool > 0L && now - lastInteractMs < cool) {
            return sapBurst
                    ? Rand.nextInt(20, 60)
                    : Rand.nextInt(80, 220);
        }

        ITileObject sapling = TileObjects.getNearest(o -> o != null
                && ForestryIds.isSaplingObject(o.getId())
                && o.getWorldLocation() != null
                && me.worldLocation.distanceTo(o.getWorldLocation()) <= EVENT_RADIUS);

        // Sapling-collect/-mulch vóór roots/ents — anders ~4s gaps door root-wacht
        if (sapling != null && (sapBurst || hasMulchReady() || mulchBatchPlanned)) {
            return handleSapling(me, sapling, now);
        }

        ITileObject greenRoot = findRisingRoot(me, true, ROOTS_RADIUS);
        if (greenRoot != null) {
            if (shouldWaitWhileChoppingRoot(me, greenRoot.getWorldLocation(), now)) {
                return Rand.nextInt(400, 700);
            }
            return chopRoot(greenRoot, now, true);
        }
        // Bruine roots = event is al actief (groen komt pas na ~15–21s) — ernaartoe + hakken
        ITileObject brownRoot = findRisingRoot(me, false, ROOTS_RADIUS);
        if (brownRoot != null) {
            if (shouldWaitWhileChoppingRoot(me, brownRoot.getWorldLocation(), now)) {
                return Rand.nextInt(400, 700);
            }
            return chopRoot(brownRoot, now, false);
        }

        INPC ent = findEntNeedingWork(me);
        if (ent != null) {
            return pruneEnt(ent, me, now);
        }
        if (hasUnprunedEntlingNearby(me)) {
            return waitForEntOverhead(me, now);
        }

        if (sapling != null) {
            return handleSapling(me, sapling, now);
        }

        return 0;
    }

    static int tryDestroyLeftovers() {
        IInventoryItem leftover = Inventory.getFirst(i -> i != null
                && ForestryIds.isForestryLeftover(i.getId(), i.getName()));
        if (leftover == null) {
            return 0;
        }
        if (leftover.hasAction("Destroy")) {
            leftover.interact("Destroy");
        } else {
            leftover.interact("Drop");
        }
        WcDebug.log("event", "leftover destroy " + leftover.getName());
        return Rand.nextInt(400, 800);
    }

    private static boolean hasAny(Players.LocalSnap me) {
        return findRisingRoot(me, false, EVENT_RADIUS) != null
                || hasUnprunedEntlingNearby(me)
                || TileObjects.getNearest(o -> o != null && ForestryIds.isSaplingObject(o.getId())
                && o.getWorldLocation() != null
                && me.worldLocation.distanceTo(o.getWorldLocation()) <= EVENT_RADIUS) != null;
    }

    /**
     * Rising Roots: {@code greenOnly} → alleen anima (47483); anders bruin of groen (dichtstbij).
     * Fallback op objectnaam als id onbekend is.
     */
    private static ITileObject findRisingRoot(Players.LocalSnap me, boolean greenOnly, int radius) {
        if (me == null || !me.present || me.worldLocation == null) {
            return null;
        }
        WorldPoint pos = me.worldLocation;
        if (greenOnly) {
            ITileObject green = findById(me, ForestryIds.RISING_ROOTS_SPECIAL, radius);
            if (green != null) {
                return green;
            }
            return TileObjects.getNearest(o -> o != null
                    && o.getWorldLocation() != null
                    && pos.distanceTo(o.getWorldLocation()) <= radius
                    && isGreenRootByName(o)
                    && rootHasChop(o));
        }
        ITileObject byId = TileObjects.getNearest(o -> o != null
                && ForestryIds.isRisingRootId(o.getId())
                && o.getWorldLocation() != null
                && pos.distanceTo(o.getWorldLocation()) <= radius
                && rootHasChop(o));
        if (byId != null) {
            return byId;
        }
        return TileObjects.getNearest(o -> o != null
                && o.getWorldLocation() != null
                && pos.distanceTo(o.getWorldLocation()) <= radius
                && isAnyRootByName(o)
                && rootHasChop(o));
    }

    private static boolean rootHasChop(ITileObject o) {
        try {
            return o.hasAction("Chop down") || o.hasAction("Chop");
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isGreenRootByName(ITileObject o) {
        if (o == null || o.getName() == null) {
            return false;
        }
        String n = o.getName().toLowerCase(Locale.ROOT);
        return (n.contains("anima") || n.contains("infused")) && n.contains("root");
    }

    private static boolean isAnyRootByName(ITileObject o) {
        if (o == null || o.getName() == null) {
            return false;
        }
        String n = o.getName().toLowerCase(Locale.ROOT);
        if (n.contains("yew") || n.contains("oak") || n.contains("willow") || n.contains("maple")) {
            return false;
        }
        return n.contains("rising root") || n.equals("tree roots") || n.equals("roots")
                || (n.contains("root") && (n.contains("tree") || n.contains("anima") || n.contains("rising")));
    }

    private static ITileObject findById(Players.LocalSnap me, int id, int radius) {
        return TileObjects.getNearest(o -> o != null && o.getId() == id
                && o.getWorldLocation() != null
                && me.worldLocation.distanceTo(o.getWorldLocation()) <= radius);
    }

    private static int eventDelay() {
        // min lager, max blijft ~380
        return Rand.nextInt(120, 380);
    }

    /** Collect tussen takes / korte walk-poll bij sapling. */
    private static int saplingTakeDelay() {
        return Rand.nextInt(15, 45);
    }

    /** Wacht op Entling overhead — snelle poll. */
    private static int entPollDelay() {
        return Rand.nextInt(80, 200);
    }

    /** Sapling bezig met collect tot packed (28650). */
    private static boolean saplingTakeBurstActive() {
        return mulchBatchPlanned && mulchInvStage() < 3;
    }

    private static int chopRoot(ITileObject obj, long now, boolean green) {
        WorldPoint wp = obj.getWorldLocation();
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null && me.present ? me.worldLocation : null;
        if (wp != null && pos != null && pos.distanceTo(wp) > ROOT_CHOP_RANGE) {
            WorldPoint stand = Pathfinder.nearestWalkableStand(wp);
            MovementHelper.walkTo(stand != null ? stand : wp);
            WcDebug.log("event", "→ Rising Root"
                    + (green ? " (groen)" : " (bruin→event)")
                    + " @" + wp.getX() + "," + wp.getY()
                    + " d=" + pos.distanceTo(wp));
            return saplingTakeDelay();
        }
        net.runelite.api.TileObject raw = TileObjects.unwrap(obj);
        boolean ok = false;
        if (raw != null) {
            if (obj.hasAction("Chop down")) {
                ok = MenuInteract.interactObject(raw, "Chop down");
            }
            if (!ok && obj.hasAction("Chop")) {
                ok = MenuInteract.interactObject(raw, "Chop");
            }
        }
        if (!ok) {
            if (obj.hasAction("Chop down")) {
                obj.interact("Chop down");
            } else if (obj.hasAction("Chop")) {
                obj.interact("Chop");
            } else {
                return Rand.nextInt(300, 500);
            }
        }
        lastInteractMs = now;
        lastRootChopClickMs = now;
        WcDebug.once("event", "Chop Rising Root id=" + obj.getId()
                + (green ? " groen" : " bruin")
                + (wp != null ? " @" + wp.getX() + "," + wp.getY() : "")
                + (ok ? " (invoke)" : ""));
        return eventDelay();
    }

    private static boolean shouldWaitWhileChoppingRoot(Players.LocalSnap me, WorldPoint rootTile, long now) {
        if (me == null || rootTile == null) {
            return false;
        }
        if (WcTrees.isWcAnim(me.animation)) {
            return true;
        }
        if (lastRootChopClickMs == 0L) {
            return false;
        }
        if (me.worldLocation == null || me.worldLocation.distanceTo(rootTile) > 2) {
            return false;
        }
        if (now - lastRootChopClickMs > 12_000L) {
            return false;
        }
        if (me.animating) {
            return true;
        }
        return now - lastRootChopClickMs < 2_500L;
    }

    private static int pruneEnt(INPC ent, Players.LocalSnap me, long now) {
        if (ent == null) {
            return 0;
        }
        int idx = ent.getIndex();
        if (idx < 0) {
            WcDebug.log("event", "Entling index onbekend — wacht");
            return eventDelay();
        }
        if (entCompletedIndices.contains(idx)) {
            return 0;
        }
        if (!isUnprunedEntling(ent)) {
            markEntComplete(idx, entRequests.get(idx), "pruned_npc");
            return 0;
        }
        if (shouldWaitWhilePruningEnt(me, ent, now)) {
            return eventDelay();
        }
        String request = resolveEntRequest(ent);
        if (request == null) {
            WcDebug.log("event", "wacht Entling verzoek");
            return eventDelay();
        }
        if (!hasAnyPruneAction(ent)) {
            markEntComplete(idx, request, "geen_actie");
            return eventDelay();
        }
        int phase = resolveCompoundPhase(ent, request, idx);
        String action = entPruneActionForPhase(request, phase);
        if (!ent.hasAction(action)) {
            WcDebug.log("event", "Entling actie ontbreekt: " + action);
            return eventDelay();
        }

        int dist = distanceToEnt(me, ent);
        if (dist > ENT_INVOKE_RANGE) {
            if (ent.getWorldLocation() != null) {
                MovementHelper.walkTo(ent.getWorldLocation());
                // Geen lastInteractMs — volgende tick opnieuw prune als dichtbij
                WcDebug.log("event", "→ Entling walk d=" + dist + " (" + request + ")");
            }
            return entPollDelay();
        }
        // ≤ invoke-range: alleen menu-invoke — geen muis
        if (MenuInteract.interactNpcByIndex(idx, action)) {
            lastInteractMs = now;
            lastEntPruneClickMs = now;
            lastEntPruneTargetIndex = idx;
            WcDebug.log("event", "Entling " + action + " (" + request + ") phase=" + phase
                    + " idx=" + idx + " d=" + distanceToEnt(me, ent) + " (invoke)");
            return eventDelay();
        }
        if (tryEntPruneClick(ent, action, idx, request, phase, me, now, true)) {
            return eventDelay();
        }
        if (tryEntPruneClick(ent, action, idx, request, phase, me, now, false)) {
            return eventDelay();
        }
        WcDebug.log("event", "Entling prune fail " + action + " " + ClickOnSight.getLastDetail());
        return Rand.nextInt(120, 280);
    }

    /**
     * @param allowMisclick 1/{@link #ENT_MISCLICK_DENOM} verkeerde prune
     */
    private static boolean tryEntPruneClick(INPC ent, String action, int idx, String request,
                                            int phase, Players.LocalSnap me, long now,
                                            boolean allowMisclick) {
        if (allowMisclick && maybeMisclickEnt(ent, action, idx, me)) {
            lastInteractMs = now;
            lastEntPruneClickMs = now;
            lastEntPruneTargetIndex = idx;
            return true;
        }
        ClickOnSight.Result r = ClickOnSight.interact(ent, action);
        if (!r.clicked) {
            // COS faalde — directe NPC-invoke
            if (!MenuInteract.interactNpcByIndex(idx, action)) {
                return false;
            }
        }
        lastInteractMs = now;
        lastEntPruneClickMs = now;
        lastEntPruneTargetIndex = idx;
        WcDebug.log("event", "Entling " + action + " (" + request + ") phase=" + phase
                + " idx=" + idx + " d=" + distanceToEnt(me, ent));
        return true;
    }

    /** ~1 op 5: verkeerde prune-actie op juiste ent, of prune op andere ent nearby. */
    private static boolean maybeMisclickEnt(INPC ent, String correctAction, int idx, Players.LocalSnap me) {
        if (ThreadLocalRandom.current().nextInt(ENT_MISCLICK_DENOM) != 0) {
            return false;
        }
        String wrong = pickWrongPruneAction(ent, correctAction);
        if (wrong != null && MenuInteract.interactNpcByIndex(idx, wrong)) {
            WcDebug.log("event", "Entling misclick " + wrong + " (wilde " + correctAction + ")");
            return true;
        }
        INPC other = findMisclickEntTarget(ent, me);
        if (other != null && other.getIndex() >= 0 && other.getIndex() != idx) {
            String alt = pickWrongPruneAction(other, correctAction);
            if (alt == null) {
                alt = other.hasAction("Prune-top") ? "Prune-top" : correctAction;
            }
            if (MenuInteract.interactNpcByIndex(other.getIndex(), alt)) {
                WcDebug.log("event", "Entling misclick andere ent idx=" + other.getIndex() + " " + alt);
                return true;
            }
        }
        return false;
    }

    private static String pickWrongPruneAction(INPC ent, String correctAction) {
        if (ent == null || correctAction == null) {
            return null;
        }
        String[] opts = {"Prune-top", "Prune-back", "Prune-sides"};
        List<String> wrong = new ArrayList<>();
        for (String o : opts) {
            if (!o.equals(correctAction) && ent.hasAction(o)) {
                wrong.add(o);
            }
        }
        if (wrong.isEmpty()) {
            return null;
        }
        return wrong.get(ThreadLocalRandom.current().nextInt(wrong.size()));
    }

    private static INPC findMisclickEntTarget(INPC skip, Players.LocalSnap me) {
        if (me == null || me.worldLocation == null) {
            return null;
        }
        List<INPC> all = NPCs.getAll(npc -> isUnprunedEntling(npc)
                && npc.getWorldLocation() != null
                && me.worldLocation.distanceTo(npc.getWorldLocation()) <= EVENT_RADIUS
                && hasAnyPruneAction(npc));
        if (all == null || all.isEmpty()) {
            return null;
        }
        for (INPC e : all) {
            if (skip == null || e.getIndex() != skip.getIndex()) {
                return e;
            }
        }
        return null;
    }

    private static int distanceToEnt(Players.LocalSnap me, INPC ent) {
        if (me == null || !me.present || me.worldLocation == null
                || ent == null || ent.getWorldLocation() == null) {
            return Integer.MAX_VALUE;
        }
        return me.worldLocation.distanceTo(ent.getWorldLocation());
    }

    private static int waitForEntOverhead(Players.LocalSnap me, long now) {
        if (hasEntWithKnownRequest(me)) {
            INPC ent = findEntNeedingWork(me);
            if (ent != null) {
                return pruneEnt(ent, me, now);
            }
        }
        INPC nearest = findNearestUnprunedEntling(me);
        if (nearest != null) {
            // Overhead al leesbaar op afstand → meteen onthouden + prune
            String overhead = readEntOverhead(nearest);
            if (overhead != null) {
                rememberEntRequest(nearest.getIndex(), overhead, "overhead-spot");
                return pruneEnt(nearest, me, now);
            }
            if (nearest.getWorldLocation() != null
                    && distanceToEnt(me, nearest) > ENT_INVOKE_RANGE) {
                MovementHelper.walkTo(nearest.getWorldLocation());
                WcDebug.log("event", "→ Entling (spot/walk) d=" + distanceToEnt(me, nearest));
                return entPollDelay();
            }
        }
        WcDebug.log("event", "wacht Entling overhead");
        return entPollDelay();
    }

    private static boolean shouldWaitWhilePruningEnt(Players.LocalSnap me, INPC ent, long now) {
        if (me == null || ent == null || lastEntPruneTargetIndex != ent.getIndex()) {
            return false;
        }
        if (now - lastEntPruneClickMs > ENT_PRUNE_WAIT_MS) {
            return false;
        }
        if (me.animating) {
            return true;
        }
        return now - lastEntPruneClickMs < ENT_PRUNE_MIN_WAIT_MS;
    }

    private static void markEntComplete(int idx, String request, String reason) {
        if (idx < 0 || entCompletedIndices.contains(idx)) {
            return;
        }
        entCompletedIndices.add(idx);
        WcDebug.once("event", "Entling klaar idx=" + idx + " req=" + request + " (" + reason + ")");
    }

    private static void rememberEntRequest(int idx, String request, String source) {
        if (request == null) {
            return;
        }
        String prev = entRequests.get(idx);
        if (request.equals(prev)) {
            return;
        }
        entRequests.put(idx, request);
        entCompoundPhase.remove(idx);
        entCompletedIndices.remove(idx);
        WcDebug.once("event", "Entling req=" + request + " idx=" + idx + " via " + source);
    }

    private static String resolveEntRequest(INPC ent) {
        if (ent == null) {
            return null;
        }
        int idx = ent.getIndex();
        String overhead = readEntOverhead(ent);
        if (overhead != null) {
            rememberEntRequest(idx, overhead, "overhead");
        }
        return entRequests.get(idx);
    }

    private static INPC findEntNeedingWork(Players.LocalSnap me) {
        if (me == null || me.worldLocation == null) {
            return null;
        }
        INPC sticky = findUnprunedEntByIndex(me, lastEntPruneTargetIndex);
        if (sticky != null && entHasWorkRemaining(sticky)) {
            return sticky;
        }
        List<INPC> all = NPCs.getAll(npc -> isUnprunedEntling(npc)
                && npc.getWorldLocation() != null
                && me.worldLocation.distanceTo(npc.getWorldLocation()) <= ENT_SPOT_RADIUS);
        if (all == null || all.isEmpty()) {
            return null;
        }
        INPC best = null;
        int bestDist = Integer.MAX_VALUE;
        for (INPC e : all) {
            if (!entHasWorkRemaining(e)) {
                continue;
            }
            int d = me.worldLocation.distanceTo(e.getWorldLocation());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean entHasWorkRemaining(INPC ent) {
        if (ent == null || !isUnprunedEntling(ent)) {
            return false;
        }
        int idx = ent.getIndex();
        if (entCompletedIndices.contains(idx)) {
            return false;
        }
        return entRequests.containsKey(idx) || readEntOverhead(ent) != null;
    }

    private static boolean hasEntWithKnownRequest(Players.LocalSnap me) {
        if (me == null || me.worldLocation == null) {
            return false;
        }
        List<INPC> all = NPCs.getAll(npc -> isUnprunedEntling(npc)
                && npc.getWorldLocation() != null
                && me.worldLocation.distanceTo(npc.getWorldLocation()) <= ENT_SPOT_RADIUS);
        if (all == null) {
            return false;
        }
        for (INPC ent : all) {
            if (entRequests.containsKey(ent.getIndex()) && !entCompletedIndices.contains(ent.getIndex())) {
                return true;
            }
        }
        return false;
    }

    private static INPC findUnprunedEntByIndex(Players.LocalSnap me, int npcIndex) {
        if (me == null || me.worldLocation == null || npcIndex < 0) {
            return null;
        }
        List<INPC> all = NPCs.getAll(npc -> isUnprunedEntling(npc)
                && npc.getIndex() == npcIndex
                && npc.getWorldLocation() != null
                && me.worldLocation.distanceTo(npc.getWorldLocation()) <= ENT_SPOT_RADIUS);
        return all != null && !all.isEmpty() ? all.get(0) : null;
    }

    private static INPC findEntlingMatchingRequest(String request) {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null || request == null) {
            return null;
        }
        List<INPC> all = NPCs.getAll(npc -> isUnprunedEntling(npc)
                && npc.getWorldLocation() != null
                && me.worldLocation.distanceTo(npc.getWorldLocation()) <= ENT_SPOT_RADIUS);
        if (all == null) {
            return null;
        }
        for (INPC ent : all) {
            if (request.equals(readEntOverhead(ent))) {
                return ent;
            }
        }
        return null;
    }

    private static boolean isCompoundRequest(String request) {
        return "top_sides".equals(request) || "back_sides".equals(request);
    }

    private static int resolveCompoundPhase(INPC ent, String request, int idx) {
        if (!isCompoundRequest(request)) {
            return 0;
        }
        int phase = entCompoundPhase.getOrDefault(idx, 0);
        if (phase == 0) {
            String primary = entPruneActionForPhase(request, 0);
            if (!ent.hasAction(primary) && ent.hasAction("Prune-sides")) {
                phase = 1;
                entCompoundPhase.put(idx, 1);
            }
        }
        return phase;
    }

    private static String entPruneActionForPhase(String request, int phase) {
        if (request == null) {
            return "Prune-top";
        }
        switch (request) {
            case "back":
                return "Prune-back";
            case "top_sides":
                return phase == 0 ? "Prune-top" : "Prune-sides";
            case "back_sides":
                return phase == 0 ? "Prune-back" : "Prune-sides";
            case "top":
            default:
                return "Prune-top";
        }
    }

    private static boolean hasAnyPruneAction(INPC ent) {
        return ent != null && (ent.hasAction("Prune-top") || ent.hasAction("Prune-back")
                || ent.hasAction("Prune-sides"));
    }

    private static boolean isUnprunedEntling(INPC npc) {
        return npc != null && npc.getId() == ForestryIds.ENTLING_NPC;
    }

    private static boolean hasUnprunedEntlingNearby(Players.LocalSnap me) {
        return findNearestUnprunedEntling(me) != null;
    }

    private static INPC findNearestUnprunedEntling() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me.present ? findNearestUnprunedEntling(me) : null;
    }

    private static INPC findNearestUnprunedEntling(Players.LocalSnap me) {
        if (me == null || me.worldLocation == null) {
            return null;
        }
        return NPCs.getNearest(npc -> isUnprunedEntling(npc)
                && npc.getWorldLocation() != null
                && me.worldLocation.distanceTo(npc.getWorldLocation()) <= ENT_SPOT_RADIUS);
    }

    private static int handleSapling(Players.LocalSnap me, ITileObject sapling, long now) {
        resolveSaplingSlotIdsFromNames(me);
        int known = countKnownSaplingSlots();
        // Trial-cursor wrap: slots niet wissen (70%/100% mag blijven)
        if (known < 2 && mulchSlotIndex >= 3) {
            mulchSlotIndex = 0;
            saplingTrialCursor++;
            clearMulchRecipe();
        }
        WorldPoint sapTile = sapling != null ? sapling.getWorldLocation() : null;
        int stage = syncMulchClicksFromInv();
        if (stage == 0 && !mulchBatchPlanned) {
            if (!planMulchBatch(me, sapTile)) {
                List<ITileObject> scan = listSaplingIngredients(me);
                if (scan.isEmpty()) {
                    WcDebug.log("event", "sapling — geen piles in scene");
                } else {
                    WcDebug.once("event", "sapling — " + scan.size() + " piles, known="
                            + known + " trial=" + saplingTrialCursor);
                }
                return Rand.nextInt(500, 900);
            }
        }
        int takeIndex = Math.min(stage, 2);
        Integer wantId = mulchRecipeIds[takeIndex];
        ITileObject pile = wantId != null ? findIngredientObject(me, wantId) : null;
        if (pile == null && known < 3) {
            pile = resolveSaplingPile(me, Math.min(mulchSlotIndex, 2));
        }
        if (pile == null) {
            WcDebug.log("event", "sapling — pile id=" + wantId + " weg (take "
                    + (takeIndex + 1) + "/3) → herplan");
            clearMulchRecipe();
            return Rand.nextInt(200, 400);
        }
        int targetId = pile.getId();
        String pileName = pile.getName() != null ? pile.getName() : ("id=" + targetId);
        WorldPoint pileTile = pile.getWorldLocation();
        WorldPoint stand = pileStandTile(pileTile);
        boolean knownSlot = known >= 3
                || (saplingOrderSlots[takeIndex] != null
                && saplingOrderSlots[takeIndex].equals(targetId));

        // Packed (28650) = inleveren; anders 1 Collect per tick tot id omhoog gaat
        if (stage < 3) {
            mulchIngredientId = targetId;
            if (pileTile == null || stand == null) {
                return Rand.nextInt(400, 700);
            }
            try {
                MovementHelper.clearPath();
            } catch (Throwable ignored) {
            }
            int distPile = me.worldLocation.distanceTo(pileTile);
            int distStand = me.worldLocation.distanceTo(stand);
            if (distPile > SAPLING_INVOKE_RANGE) {
                approachSaplingTile(stand, distStand, "pile " + pileName
                        + " (" + (stage + 1) + "/3)");
                return saplingTakeDelay();
            }
            if (!tryInvokePileTake(pile)) {
                mulchTakeFails++;
                WcDebug.log("event", "collect FAIL " + pileName + " ×" + mulchTakeFails
                        + "/" + MULCH_TAKE_FAIL_ROTATE + " d=" + distStand
                        + " actions=" + pileActionsHint(pile) + " inv=" + stage + "/3");
                if (mulchTakeFails >= MULCH_TAKE_FAIL_ROTATE) {
                    mulchTakeFails = 0;
                    clearMulchRecipe();
                    saplingTrialCursor++;
                    WcDebug.log("event", "collect opgeven → volgende trial pile (cursor="
                            + saplingTrialCursor + ")");
                }
                return Rand.nextInt(80, 160);
            }
            mulchTakeFails = 0;
            lastInteractMs = now;
            if (lastLoggedMulchStage != stage) {
                String nextId = stage == 0 ? "28648" : (stage == 1 ? "28649" : "28650");
                WcDebug.log("event", "collect " + pileName + " spam tot " + nextId
                        + " (nu stage " + stage + " id=" + mulchInvIdHint() + ")"
                        + (knownSlot ? " ✓" : " filler/trial")
                        + (mulchBatchIsSeventy ? " [70%]" : ""));
                lastLoggedMulchStage = stage;
            }
            return Rand.nextInt(50, 110);
        }

        if (sapTile == null) {
            return Rand.nextInt(180, 320);
        }
        clearStaleWalkIfNotToward(sapTile);
        int distSap = me.worldLocation.distanceTo(sapTile);
        if (distSap > SAPLING_INVOKE_RANGE) {
            approachSaplingTile(sapTile, distSap, "sapling (packed 28650)");
            return saplingTakeDelay();
        }
        if (tryInvokeSaplingFeed(sapling)) {
            return finishSaplingFeed(sapling, pileName, known, now);
        }
        WcDebug.log("event", "sapling feed FAIL d=" + distSap + " packed=28650");
        return Rand.nextInt(180, 320);
    }

    private static int finishSaplingFeed(ITileObject sapling, String pileName, int known, long now) {
        boolean wasTrialBatch = known < 3;
        int trialSlot = Math.min(mulchSlotIndex, 2);
        boolean slotWasUnknown = wasTrialBatch && saplingOrderSlots[trialSlot] == null
                && (saplingOrderNames[trialSlot] == null || saplingOrderNames[trialSlot].isEmpty());
        boolean wasSeventy = mulchBatchIsSeventy;
        boolean wasHundred = !wasSeventy && known >= 3;
        mulchCollectClicks = 0;
        mulchIngredientId = null;
        clearMulchRecipe();
        if (!wasSeventy && !wasHundred && known < 2) {
            mulchSlotIndex++;
            if (slotWasUnknown) {
                saplingTrialCursor++;
            } else {
                saplingTrialCursor = 0;
            }
        } else {
            mulchSlotIndex = 0;
        }
        lastInteractMs = now;
        WcDebug.log("event", "mulch → sapling "
                + (wasSeventy ? "70%" : wasTrialBatch ? ("trial " + mulchSlotIndex + "/3") : "100%")
                + " " + pileName
                + " known=" + countKnownSaplingSlots()
                + (mulchInvStage() >= 3 ? " (packed✓)" : " (inv=" + mulchInvStage() + ")"));
        pollSaplingLoveChat();
        return Rand.nextInt(220, 480);
    }

    /** Stand-tegel naast pile (booth/object-midden is vaak niet walkable). */
    private static WorldPoint pileStandTile(WorldPoint pileTile) {
        if (pileTile == null) {
            return null;
        }
        WorldPoint stand = Pathfinder.nearestWalkableStand(pileTile);
        return stand != null ? stand : pileTile;
    }

    /** Kort: Movement.walk; lang: pad. Geen lastInteractMs. Drempel = Movement.SHORT_WALK_TILES. */
    private static void approachSaplingTile(WorldPoint tile, int dist, String label) {
        if (tile == null) {
            return;
        }
        if (Movement.walkNearOrPath(tile)) {
            boolean kort = dist <= Movement.SHORT_WALK_TILES;
            WcDebug.log("event", "→ " + label + " d=" + dist
                    + (kort ? " (walk kort≤" + Movement.SHORT_WALK_TILES + ")" : " (pad)"));
        }
    }

    /** Yew-walk dest mag pile-Take niet blokkeren. */
    private static void clearStaleWalkIfNotToward(WorldPoint want) {
        if (want == null) {
            return;
        }
        try {
            WorldPoint active = MovementHelper.getActiveDestination();
            if (active == null) {
                return;
            }
            if (active.getPlane() != want.getPlane() || active.distanceTo(want) > 3) {
                MovementHelper.clearPath();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Menu-invoke Collect — geen muis / ClickOnSight.
     * Piles gebruiken Collect (niet Take); fallbacks voor oude/andere objecten.
     */
    private static boolean tryInvokePileTake(ITileObject pile) {
        if (pile == null) {
            return false;
        }
        final String[] actions = {"Collect", "Take", "Search", "Check", "Pick"};
        try {
            net.runelite.api.TileObject raw = TileObjects.unwrap(pile);
            if (raw != null) {
                for (String a : actions) {
                    if (MenuInteract.interactObject(raw, a)) {
                        return true;
                    }
                }
                // CombatBot interact(0): eerste non-null composition-actie
                if (MenuInteract.interactObjectFirst(raw)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (String a : actions) {
                if (pile.hasAction(a)) {
                    pile.interact(a);
                    return true;
                }
            }
            pile.interact("Collect");
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean tryInvokeSaplingFeed(ITileObject sapling) {
        if (sapling == null) {
            return false;
        }
        try {
            net.runelite.api.TileObject raw = TileObjects.unwrap(sapling);
            if (raw != null) {
                for (String a : new String[]{"Add-mulch", "Nurture", "Feed", "Water", "Add-to"}) {
                    if (MenuInteract.interactObject(raw, a)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String pileActionsHint(ITileObject pile) {
        if (pile == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (String a : new String[]{"Collect", "Take", "Search", "Check", "Pick"}) {
            try {
                if (pile.hasAction(a)) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(a);
                }
            } catch (Throwable ignored) {
            }
        }
        return sb.length() == 0 ? "geen?" : sb.toString();
    }

    /**
     * known=3 → 100% OOO. known=2 → 70% (filler op het gat). Anders AAA-trial.
     */
    private static boolean planMulchBatch(Players.LocalSnap me, WorldPoint sapTile) {
        clearMulchRecipe();
        resolveSaplingSlotIdsFromNames(me);
        int known = countKnownSaplingSlots();
        if (known >= 3) {
            return planHundredPercent(me);
        }
        if (known == 2) {
            return planSeventyPercent(me);
        }
        int slot = firstUnknownSlot();
        if (slot < 0) {
            slot = Math.min(mulchSlotIndex, 2);
        }
        Integer id = pickTrialIngredientId(me, slot);
        if (id == null) {
            return false;
        }
        mulchRecipeIds[0] = id;
        mulchRecipeIds[1] = id;
        mulchRecipeIds[2] = id;
        mulchBatchPlanned = true;
        mulchBatchIsSeventy = false;
        WcDebug.log("event", "sapling plan AAA trial slot=" + (slot + 1)
                + " id=" + id + " known=" + known
                + " names=" + slotDebugNames());
        return true;
    }

    private static boolean planHundredPercent(Players.LocalSnap me) {
        Integer[] full = new Integer[3];
        for (int i = 0; i < 3; i++) {
            full[i] = saplingOrderSlots[i];
            if (full[i] == null) {
                resolveSaplingSlotIdsFromNames(me);
                full[i] = saplingOrderSlots[i];
            }
            if (full[i] == null || findIngredientObject(me, full[i]) == null) {
                WcDebug.log("event", "sapling 100% mist pile slot=" + (i + 1)
                        + " → 70% filler");
                return planSeventyPercent(me);
            }
        }
        System.arraycopy(full, 0, mulchRecipeIds, 0, 3);
        mulchBatchIsSeventy = false;
        mulchBatchPlanned = true;
        WcDebug.log("event", "sapling plan 100% OOO ids="
                + full[0] + "/" + full[1] + "/" + full[2]
                + " names=" + slotDebugNames());
        return true;
    }

    /** Twee bekende slots op hun plek; gat = andere pile (of dubbel). */
    private static boolean planSeventyPercent(Players.LocalSnap me) {
        Integer[] rec = new Integer[3];
        int gap = -1;
        for (int i = 0; i < 3; i++) {
            rec[i] = saplingOrderSlots[i];
            if (rec[i] == null) {
                gap = i;
            } else if (findIngredientObject(me, rec[i]) == null) {
                gap = i;
                rec[i] = null;
            }
        }
        if (gap < 0) {
            return planHundredPercent(me);
        }
        Integer filler = pickFillerIngredientId(me, rec);
        if (filler == null) {
            return false;
        }
        rec[gap] = filler;
        for (int i = 0; i < 3; i++) {
            if (rec[i] == null) {
                rec[i] = filler;
            }
        }
        System.arraycopy(rec, 0, mulchRecipeIds, 0, 3);
        mulchBatchIsSeventy = true;
        mulchBatchPlanned = true;
        WcDebug.log("event", "sapling plan 70% gap-slot=" + (gap + 1)
                + " filler=" + filler
                + " ids=" + rec[0] + "/" + rec[1] + "/" + rec[2]
                + " names=" + slotDebugNames());
        return true;
    }

    private static int firstUnknownSlot() {
        for (int i = 0; i < 3; i++) {
            if (saplingOrderSlots[i] == null
                    && (saplingOrderNames[i] == null || saplingOrderNames[i].isEmpty())) {
                return i;
            }
        }
        return -1;
    }

    private static Integer pickFillerIngredientId(Players.LocalSnap me, Integer[] rec) {
        Set<Integer> used = new HashSet<>();
        if (rec != null) {
            for (Integer id : rec) {
                if (id != null) {
                    used.add(id);
                }
            }
        }
        List<ITileObject> piles = listSaplingIngredients(me);
        for (ITileObject p : piles) {
            if (p != null && !used.contains(p.getId())) {
                return p.getId();
            }
        }
        for (Integer id : used) {
            if (id != null && findIngredientObject(me, id) != null) {
                return id;
            }
        }
        return pickTrialIngredientId(me, Math.max(0, firstUnknownSlot()));
    }

    private static String slotDebugNames() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (saplingOrderNames[i] != null && !saplingOrderNames[i].isEmpty()) {
                sb.append(saplingOrderNames[i]);
            } else if (saplingOrderSlots[i] != null) {
                sb.append("id=").append(saplingOrderSlots[i]);
            } else {
                sb.append('?');
            }
        }
        return sb.append(']').toString();
    }

    /**
     * Hartje = sapling. Piles binnen r={@link #SAPLING_DEPOSIT_RADIUS}: streef 3, anders 2.
     * Verste pile buiten straal → slot vullen met dichtste van de twee dichte (BBD/BDD).
     */
    private static boolean planRecipeByDepositRadius(Players.LocalSnap me, WorldPoint sapTile,
                                                    Integer[] full) {
        if (full == null || full.length < 3 || sapTile == null) {
            return false;
        }
        int[] dist = new int[3];
        int inRadius = 0;
        int farSlot = -1;
        int farDist = -1;
        for (int i = 0; i < 3; i++) {
            ITileObject pile = findIngredientObject(me, full[i]);
            if (pile == null || pile.getWorldLocation() == null) {
                dist[i] = 999;
            } else {
                dist[i] = sapTile.distanceTo(pile.getWorldLocation());
            }
            if (dist[i] <= SAPLING_DEPOSIT_RADIUS) {
                inRadius++;
            } else if (dist[i] > farDist) {
                farDist = dist[i];
                farSlot = i;
            }
        }

        if (inRadius >= 3) {
            System.arraycopy(full, 0, mulchRecipeIds, 0, 3);
            mulchBatchIsSeventy = false;
            mulchBatchPlanned = true;
            WcDebug.log("event", "sapling plan 100% r=" + SAPLING_DEPOSIT_RADIUS
                    + " d=[" + dist[0] + "," + dist[1] + "," + dist[2] + "] ids="
                    + full[0] + "/" + full[1] + "/" + full[2]);
            return true;
        }

        // <3 in straal: skip de verste, dubbel de dichtste van de overige twee
        if (farSlot < 0) {
            // Alle "binnen" maar <3 objecten gevonden — skip hoogste dist
            farSlot = 0;
            for (int i = 1; i < 3; i++) {
                if (dist[i] > dist[farSlot]) {
                    farSlot = i;
                }
            }
        }
        int a = (farSlot + 1) % 3;
        int b = (farSlot + 2) % 3;
        int doubleSlot = dist[a] <= dist[b] ? a : b;
        Integer[] r70 = Arrays.copyOf(full, 3);
        r70[farSlot] = full[doubleSlot];
        System.arraycopy(r70, 0, mulchRecipeIds, 0, 3);
        mulchBatchIsSeventy = true;
        mulchBatchPlanned = true;
        WcDebug.log("event", "sapling plan 70% r=" + SAPLING_DEPOSIT_RADIUS
                + " in=" + inRadius + " skip-slot=" + (farSlot + 1)
                + " d=[" + dist[0] + "," + dist[1] + "," + dist[2] + "] → "
                + r70[0] + "/" + r70[1] + "/" + r70[2]
                + " (dubbel slot " + (doubleSlot + 1) + ")");
        return true;
    }

    /** Slot-namen uit chat → item-ids zodra pile in scene is. */
    private static void resolveSaplingSlotIdsFromNames(Players.LocalSnap me) {
        for (int i = 0; i < 3; i++) {
            if (saplingOrderSlots[i] != null) {
                continue;
            }
            String name = saplingOrderNames[i];
            if (name == null || name.isEmpty()) {
                continue;
            }
            ITileObject pile = findSaplingIngredientByName(name);
            if (pile != null) {
                saplingOrderSlots[i] = pile.getId();
                WcDebug.log("event", "Sapling slot " + (i + 1) + " id=" + pile.getId()
                        + " van naam " + name);
            }
        }
    }

    /** 0–3 uit tas-id (28648/28649/28650). */
    private static int mulchInvStage() {
        try {
            if (Inventory.contains(ForestryIds.MULCH_INV_PACKED)) {
                return 3;
            }
            if (Inventory.contains(ForestryIds.MULCH_INV_2)) {
                return 2;
            }
            if (Inventory.contains(ForestryIds.MULCH_INV_1)
                    || Inventory.contains(ForestryIds.MULCH_INV_1_LEGACY)) {
                return 1;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static int syncMulchClicksFromInv() {
        int stage = mulchInvStage();
        if (stage != lastLoggedMulchStage && stage > 0 && lastLoggedMulchStage >= 0
                && stage > lastLoggedMulchStage) {
            WcDebug.log("event", "mulch inv " + stageIdHint(lastLoggedMulchStage)
                    + " → " + stageIdHint(stage) + " (" + stage + "/3)");
        }
        mulchCollectClicks = stage;
        return stage;
    }

    private static String stageIdHint(int stage) {
        if (stage >= 3) {
            return "28650";
        }
        if (stage == 2) {
            return "28649";
        }
        if (stage == 1) {
            return "28648";
        }
        return "0";
    }

    private static String mulchInvIdHint() {
        try {
            IInventoryItem it = Inventory.getFirst(
                    ForestryIds.MULCH_INV_PACKED,
                    ForestryIds.MULCH_INV_2,
                    ForestryIds.MULCH_INV_1,
                    ForestryIds.MULCH_INV_1_LEGACY);
            return it != null ? String.valueOf(it.getId()) : "-";
        } catch (Throwable t) {
            return "?";
        }
    }

    /** Enige mulch in tas (1/2/3) — sapling-flow aanhouden. */
    private static boolean hasMulchReady() {
        return mulchInvStage() > 0;
    }

    /** Love-chat meteen na feed (niet wachten op WC-tick poll). */
    private static void pollSaplingLoveChat() {
        try {
            List<String> recent = Chat.getRecentMessages(16);
            if (recent == null) {
                return;
            }
            for (String line : recent) {
                if (line == null || line.isEmpty()) {
                    continue;
                }
                String lower = stripColorTags(line).toLowerCase(Locale.ROOT);
                if (lower.contains("sapling") && lower.contains("love")) {
                    onGameMessage(line);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Chat-bevestiging → slot vastleggen (RuneLite-parity + soepele parse). */
    private static void rememberSaplingFromLoveMessage(String message) {
        String plain = stripColorTags(message);
        String lower = plain.toLowerCase(Locale.ROOT).trim()
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ");
        if (!(lower.contains("love") && lower.contains("ingredient"))) {
            return;
        }
        // Altijd zichtbaar in console — bewijst of chat aankomt
        WcDebug.log("event", "sapling love-chat: " + (plain.length() > 140
                ? plain.substring(0, 140) + "…" : plain));

        int slot = -1;
        String loved = null;
        Matcher m = SAPLING_LOVE_SLOT.matcher(lower);
        if (m.find()) {
            loved = m.group(1).trim();
            slot = slotFromOrdinal(m.group(2));
        } else {
            // Fallback: ordinal ergens voor "ingredient" (ook als strip ooit mist)
            if (lower.contains("first ingredient") || lower.matches(".*\\bfirst\\b.*ingredient.*")) {
                slot = 0;
            } else if (lower.contains("second ingredient") || lower.matches(".*\\bsecond\\b.*ingredient.*")) {
                slot = 1;
            } else if (lower.contains("third ingredient") || lower.matches(".*\\bthird\\b.*ingredient.*")) {
                slot = 2;
            }
            loved = parseSaplingLovedName(lower);
        }
        if (slot < 0) {
            if (lower.contains("combination")) {
                WcDebug.log("event", "sapling love combo-ok (geen slot)");
            } else {
                WcDebug.log("event", "sapling love zonder first/second/third");
            }
            return;
        }
        if (loved != null) {
            loved = loved.replaceAll("[.!,]+$", "").trim();
        }
        ITileObject ingredient = matchIngredientInMessage(lower, loved);
        if (ingredient != null) {
            saplingOrderSlots[slot] = ingredient.getId();
            if (ingredient.getName() != null && !ingredient.getName().isEmpty()) {
                saplingOrderNames[slot] = ingredient.getName().toLowerCase(Locale.ROOT);
            } else if (loved != null && !loved.isEmpty()) {
                saplingOrderNames[slot] = loved;
            }
            WcDebug.log("event", "Sapling slot " + (slot + 1) + " = "
                    + ingredient.getName() + " (" + ingredient.getId() + ")"
                    + " known=" + countKnownSaplingSlots());
        } else if (loved != null && !loved.isEmpty()) {
            saplingOrderNames[slot] = loved;
            WcDebug.log("event", "Sapling slot " + (slot + 1) + " = " + loved
                    + " (naam) known=" + countKnownSaplingSlots());
        } else {
            WcDebug.log("event", "sapling love slot=" + (slot + 1) + " maar geen ingredient-naam");
            return;
        }
        // Mid-trial geleerd → volgende batch herplannen (OOO zodra known=3)
        clearMulchRecipe();
    }

    private static String stripColorTags(String text) {
        if (text == null) {
            return "";
        }
        // HTML + RuneLite/Jagex chat (@mes_hl_blu@ …) — zonder dit faalt "as the first"
        return text
                .replaceAll("<[^>]+>", "")
                .replaceAll("@[^@\\s]{1,40}@", " ");
    }

    private static int slotFromOrdinal(String ord) {
        if (ord == null) {
            return -1;
        }
        switch (ord.toLowerCase(Locale.ROOT)) {
            case "first":
                return 0;
            case "second":
                return 1;
            case "third":
                return 2;
            default:
                return -1;
        }
    }

    private static ITileObject matchIngredientInMessage(String lowerMsg, String loved) {
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return null;
        }
        ITileObject best = null;
        int bestDist = Integer.MAX_VALUE;
        for (ITileObject obj : listSaplingIngredients(me)) {
            String name = obj.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String n = name.toLowerCase(Locale.ROOT);
            boolean hit = lowerMsg.contains(n)
                    || (loved != null && (n.equals(loved) || n.contains(loved) || loved.contains(n)));
            if (!hit) {
                continue;
            }
            int d = me.worldLocation.distanceTo(obj.getWorldLocation());
            if (d < bestDist) {
                bestDist = d;
                best = obj;
            }
        }
        if (best != null) {
            return best;
        }
        return loved != null ? findSaplingIngredientByName(loved) : null;
    }

    /** Kies pile: chat-slot → trial-rotatie → dichtstbijzijnde (geen chat-wacht). */
    private static ITileObject resolveSaplingPile(Players.LocalSnap me, int slot) {
        Integer knownId = saplingOrderSlots[slot];
        if (knownId != null) {
            ITileObject pile = findIngredientObject(me, knownId);
            if (pile != null) {
                return pile;
            }
        }
        String knownName = saplingOrderNames[slot];
        if (knownName != null) {
            ITileObject pile = findSaplingIngredientByName(knownName);
            if (pile != null) {
                saplingOrderSlots[slot] = pile.getId();
                return pile;
            }
        }
        if (mulchIngredientId != null && mulchCollectClicks > 0) {
            ITileObject mid = findIngredientObject(me, mulchIngredientId);
            if (mid != null) {
                return mid;
            }
        }
        Integer trialId = pickTrialIngredientId(me, slot);
        if (trialId == null) {
            return null;
        }
        return findIngredientObject(me, trialId);
    }

    private static Integer pickTrialIngredientId(Players.LocalSnap me, int slot) {
        List<ITileObject> piles = listSaplingIngredients(me);
        if (piles.isEmpty()) {
            return null;
        }
        Set<Integer> usedElsewhere = usedSaplingIngredientIds(slot);
        List<ITileObject> candidates = new ArrayList<>();
        for (ITileObject p : piles) {
            if (!usedElsewhere.contains(p.getId())) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            candidates = piles;
        }
        if (saplingTrialCursor >= candidates.size()) {
            saplingTrialCursor = 0;
        }
        ITileObject pick = candidates.get(saplingTrialCursor % candidates.size());
        return pick != null ? pick.getId() : null;
    }

    private static Set<Integer> usedSaplingIngredientIds(int exceptSlot) {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            if (i != exceptSlot && saplingOrderSlots[i] != null) {
                used.add(saplingOrderSlots[i]);
            }
        }
        return used;
    }

    private static List<ITileObject> listSaplingIngredients(Players.LocalSnap me) {
        List<ITileObject> out = new ArrayList<>();
        if (me == null || !me.present || me.worldLocation == null) {
            return out;
        }
        WorldPoint pos = me.worldLocation;
        for (ITileObject obj : TileObjects.getAll(ForestryIds.SAPLING_INGREDIENT_IDS)) {
            if (obj == null || obj.getWorldLocation() == null) {
                continue;
            }
            if (pos.distanceTo(obj.getWorldLocation()) <= EVENT_RADIUS) {
                out.add(obj);
            }
        }
        out.sort((a, b) -> Integer.compare(
                pos.distanceTo(a.getWorldLocation()),
                pos.distanceTo(b.getWorldLocation())));
        return out;
    }

    private static String parseSaplingLovedName(String lower) {
        if (lower == null) {
            return null;
        }
        int love = lower.indexOf("love ");
        if (love < 0) {
            return null;
        }
        int start = love + 5;
        int asThe = lower.indexOf(" as the", start);
        String mid = asThe > start ? lower.substring(start, asThe) : lower.substring(start);
        mid = mid.replace('.', ' ').trim();
        return mid.isEmpty() ? null : mid;
    }

    private static ITileObject findSaplingIngredientByName(String loved) {
        if (loved == null || loved.isEmpty()) {
            return null;
        }
        String want = loved.toLowerCase(Locale.ROOT).trim();
        Players.LocalSnap me = Players.snapshotLocal();
        if (!me.present || me.worldLocation == null) {
            return null;
        }
        ITileObject best = null;
        int bestDist = Integer.MAX_VALUE;
        for (ITileObject obj : TileObjects.getAll(ForestryIds.SAPLING_INGREDIENT_IDS)) {
            if (obj == null || obj.getWorldLocation() == null) {
                continue;
            }
            String name = obj.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String n = name.toLowerCase(Locale.ROOT);
            if (!n.equals(want) && !n.contains(want) && !want.contains(n)) {
                continue;
            }
            int d = me.worldLocation.distanceTo(obj.getWorldLocation());
            if (d <= EVENT_RADIUS && d < bestDist) {
                bestDist = d;
                best = obj;
            }
        }
        return best;
    }
    private static ITileObject findIngredientObject(Players.LocalSnap me, int ingredientId) {
        return TileObjects.getNearest(obj -> obj != null
                && obj.getId() == ingredientId
                && obj.getWorldLocation() != null
                && me.worldLocation.distanceTo(obj.getWorldLocation()) <= EVENT_RADIUS);
    }

    private static String readEntOverhead(INPC npc) {
        if (npc == null) {
            return null;
        }
        try {
            String text = npc.getOverheadText();
            if (text != null && !text.isEmpty()) {
                return classifyEntRequest(text.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String classifyEntRequest(String lower) {
        if (lower == null || lower.isEmpty()) {
            return null;
        }
        String t = lower.replaceAll("<[^>]+>", " ").replace('\u2019', '\'').toLowerCase(Locale.ROOT);
        // Langste zinnen eerst — "short back and sides" bevat ook "short"
        if (t.contains("short back and sides")) {
            return "back_sides";
        }
        if (t.contains("leafy mullet") || t.contains("mullet")) {
            return "top_sides";
        }
        if (t.contains("breezy at the back") || t.contains("breezy")) {
            return "back";
        }
        if (t.contains("short on top")) {
            return "top";
        }
        return null;
    }
}
