package net.storm.sdk.interact;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.tiles.ITileItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.input.Mouse;
import net.storm.sdk.interact.mouse.MouseManager;
import net.storm.sdk.movement.MovementHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ground loot Take — meerdere strategieën (Storm {@code ITileItem#pickup} + fallbacks).
 * <p>
 * Zie: <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/api/domain/tiles/ITileItem.html#pickup()">
 * Storm ITileItem.pickup()</a>
 * <p>
 * Volgorde (laatst-werkende eerst):
 * <ol>
 *   <li>{@code STORM_PICKUP} — {@link ITileItem#pickup()} (Take)</li>
 *   <li>{@code INTERACT_TAKE} — {@link ITileItem#interact(String) interact("Take")}</li>
 *   <li>{@code MENU_INVOKE} — {@code Client.menuAction} GROUND_ITEM_* + itemId</li>
 *   <li>{@code MOUSE_LEFT} — alleen als left-click default Take is (nooit Chop)</li>
 *   <li>{@code RIGHT_CLICK_TAKE} — rechtsklik → Take-regel met itemnaam</li>
 *   <li>{@code WALK_THEN_RETRY} — loop dichterbij + opnieuw (alleen als ver)</li>
 * </ol>
 * AUTO gebruikt geen canvas-LMB: LMB op loot achter een boom = Chop down.
 */
public final class GroundLootPickupHelper {

    private static final Logger log = LoggerFactory.getLogger(GroundLootPickupHelper.class);

    public enum Method {
        STORM_PICKUP,
        INTERACT_TAKE,
        MENU_INVOKE,
        MOUSE_LEFT,
        RIGHT_CLICK_TAKE,
        WALK_THEN_RETRY
    }

    public static final class Result {
        public final boolean ok;
        public final Method method;
        public final String detail;

        public Result(boolean ok, Method method, String detail) {
            this.ok = ok;
            this.method = method;
            this.detail = detail != null ? detail : "";
        }

        @Override
        public String toString() {
            return (ok ? "OK" : "FAIL") + " " + (method != null ? method.name() : "?") + " " + detail;
        }
    }

    private static volatile Method lastWorking;
    private static volatile String lastDetail = "";
    /** null / AUTO = roteren; anders preferred eerst (of alleen bij strict). */
    private static volatile Method preferredMode;
    private static volatile boolean strictMode;
    private static volatile int rotateIndex;
    private static volatile WorldPoint lastAttemptTile;
    private static volatile long lastAttemptMs;
    private static volatile WorldPoint chopSkipTile;
    private static volatile long chopSkipUntilMs;
    private static final long CHOP_SKIP_MS = 12_000L;
    private static final long CHOP_CHAT_WINDOW_MS = 4_000L;

    private static final int MENU_HEADER_H = 19;
    private static final int MENU_ROW_H = 15;

    private GroundLootPickupHelper() {
    }

    public static Method getLastWorking() {
        return lastWorking;
    }

    public static String getLastDetail() {
        return lastDetail != null ? lastDetail : "";
    }

    public static Method getPreferredMode() {
        return preferredMode;
    }

    public static boolean isStrictMode() {
        return strictMode;
    }

    /**
     * Paneel/config: preferred methode. {@code null} = AUTO.
     * {@code strict} = alleen die methode, geen fallbacks.
     */
    public static void setPickupMode(Method preferred, boolean strict) {
        preferredMode = preferred;
        strictMode = strict;
        if (preferred != null) {
            lastWorking = null; // forceer nieuwe keuze
        }
        log.info("[GroundLoot] mode → {} strict={}", preferred != null ? preferred : "AUTO", strict);
    }

    /** String-naam uit ImpsTypes.LootPickupMode / config. */
    public static void setPickupModeFromName(String modeName, boolean strict) {
        if (modeName == null || modeName.isBlank() || "AUTO".equalsIgnoreCase(modeName.trim())) {
            setPickupMode(null, false);
            return;
        }
        try {
            Method m = Method.valueOf(modeName.trim().toUpperCase(Locale.ROOT));
            if (m == Method.WALK_THEN_RETRY) {
                setPickupMode(null, false);
                return;
            }
            setPickupMode(m, strict);
        } catch (IllegalArgumentException e) {
            setPickupMode(null, false);
        }
    }

    /**
     * Game-chat: Chop down i.p.v. Take. Skip die loot-tegel — zelfde doel (loot), geen LMB-spam.
     */
    public static void onGameMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String plain = strip(message).toLowerCase(Locale.ROOT);
        if (!plain.contains("need an axe to chop")
                && !plain.contains("do not have an axe which you have the woodcutting")) {
            return;
        }
        long now = System.currentTimeMillis();
        WorldPoint tile = lastAttemptTile;
        if (tile == null || now - lastAttemptMs > CHOP_CHAT_WINDOW_MS) {
            log.warn("[GroundLoot] chop-chat zonder recente loot-klik — ignore");
            return;
        }
        chopSkipTile = tile;
        chopSkipUntilMs = now + CHOP_SKIP_MS;
        if (lastWorking == Method.MOUSE_LEFT) {
            lastWorking = null;
        }
        lastDetail = "chop-misclick skip @" + tile.getX() + "," + tile.getY();
        log.warn("[GroundLoot] {}", lastDetail);
    }

    public static boolean isChopSkipTile(WorldPoint wp) {
        if (wp == null || chopSkipTile == null || chopSkipUntilMs <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now > chopSkipUntilMs) {
            chopSkipTile = null;
            chopSkipUntilMs = 0L;
            return false;
        }
        return wp.getX() == chopSkipTile.getX()
                && wp.getY() == chopSkipTile.getY()
                && wp.getPlane() == chopSkipTile.getPlane();
    }

    public static WorldPoint peekChopMisclickTile() {
        if (chopSkipTile == null || System.currentTimeMillis() > chopSkipUntilMs) {
            return null;
        }
        return chopSkipTile;
    }

    /**
     * Probeer Take op dit ground-item.
     * CombatBot-stijl: named Take (interact/invoke of RMB-menu), geen blinde LMB op gestapelde tiles.
     */
    public static Result pickup(ITileItem item) {
        if (item == null || item.getWorldLocation() == null) {
            return fail(null, "item null");
        }
        String name = safeName(item);
        int id = item.getId();
        WorldPoint wp = item.getWorldLocation();
        lastAttemptTile = wp;
        lastAttemptMs = System.currentTimeMillis();
        if (isChopSkipTile(wp)) {
            lastDetail = "chop-misclick skip tile";
            return fail(Method.MENU_INVOKE, lastDetail);
        }
        int sameTile = countItemsOnTile(wp);

        // Achter boom/object: nooit LMB (Chop down) — alleen GROUND_ITEM invoke.
        if (isOccludedByObject(item)) {
            log.info("[GroundLoot] occluded \"{}\" @{} — invoke Take, no LMB", name, wp);
            Result inv = tryMethod(item, Method.MENU_INVOKE, name, id, wp);
            if (inv.ok) {
                lastWorking = Method.MENU_INVOKE;
                lastDetail = "occluded invoke " + inv.detail;
                return inv;
            }
            Result take = tryMethod(item, Method.INTERACT_TAKE, name, id, wp);
            if (take.ok) {
                lastWorking = Method.INTERACT_TAKE;
                lastDetail = "occluded interact " + take.detail;
                return take;
            }
            lastDetail = "occluded — invoke fail (skip LMB, would hit tree)";
            log.warn("[GroundLoot] {}", lastDetail);
            return fail(Method.MENU_INVOKE, lastDetail);
        }

        // Off-screen: eerst camera draaien, daarna opnieuw proberen (anders invoke/walk)
        if (!isItemOnScreen(item) && AimInteractHelper.turnToward(wp)) {
            sleep(200 + ThreadLocalRandom.current().nextInt(180));
            log.info("[GroundLoot] camera naar loot \"{}\" @{}", name, wp);
        }

        // CombatBot: 50/50 interact("Take") vs RMB named Take — daarna fallbacks
        if (preferredMode == null && !strictMode) {
            Result combat = pickupCombatBotStyle(item, name, id, wp, sameTile > 1);
            if (combat.ok) {
                return combat;
            }
        }

        List<Method> order = buildOrder(sameTile > 1);
        for (Method m : order) {
            Result r = tryMethod(item, m, name, id, wp);
            if (r.ok) {
                // Nooit MOUSE_LEFT onthouden — dat was vaak Chop down achter een boom
                if (m == Method.RIGHT_CLICK_TAKE || m == Method.MENU_INVOKE
                        || m == Method.INTERACT_TAKE || m == Method.STORM_PICKUP) {
                    lastWorking = m;
                }
                lastDetail = m.name() + ": " + r.detail
                        + (sameTile > 1 ? " stacked=" + sameTile : "");
                log.info("[GroundLoot] {} id={} \"{}\" @{} stacked={} — {}",
                        m, id, name, wp, sameTile, r.detail);
                return r;
            }
            log.info("[GroundLoot] {} FAIL: {}", m, r.detail);
        }
        rotateIndex++;
        lastDetail = "all methods failed for " + name;
        log.warn("[GroundLoot] ALL FAIL id={} \"{}\" @{}", id, name, wp);
        return fail(null, lastDetail);
    }

    /**
     * CombatBot {@code GroundLootPickupHelper.pickup}: random interact/Take of RMB+Take-naam.
     */
    private static Result pickupCombatBotStyle(
            ITileItem item, String name, int id, WorldPoint wp, boolean stacked
    ) {
        boolean interactFirst = ThreadLocalRandom.current().nextBoolean();
        Method primary = interactFirst ? Method.INTERACT_TAKE : Method.RIGHT_CLICK_TAKE;
        Method secondary = interactFirst ? Method.RIGHT_CLICK_TAKE : Method.INTERACT_TAKE;
        // Gestapeld: altijd eerst RMB met itemnaam (LMB/interact kan verkeerde Take pakken)
        if (stacked) {
            primary = Method.RIGHT_CLICK_TAKE;
            secondary = Method.INTERACT_TAKE;
        }
        Result a = tryMethod(item, primary, name, id, wp);
        if (a.ok) {
            lastWorking = primary;
            lastDetail = primary.name() + ": " + a.detail + (stacked ? " stacked" : "");
            log.info("[GroundLoot] CombatBot-style {} OK \"{}\" — {}", primary, name, a.detail);
            return a;
        }
        Result b = tryMethod(item, secondary, name, id, wp);
        if (b.ok) {
            lastWorking = secondary;
            lastDetail = secondary.name() + ": " + b.detail + (stacked ? " stacked" : "");
            log.info("[GroundLoot] CombatBot-style {} OK \"{}\" — {}", secondary, name, b.detail);
            return b;
        }
        return fail(null, "combatbot-style fail");
    }

    /** Meer dan 1 ground-item op dezelfde tile → RMB nodig om specifieke Take te kiezen. */
    private static int countItemsOnTile(WorldPoint wp) {
        if (wp == null) {
            return 0;
        }
        try {
            return net.storm.sdk.entities.TileItems.getAt(wp).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static List<Method> buildOrder() {
        return buildOrder(false);
    }

    private static List<Method> buildOrder(boolean stackedTile) {
        // CombatBot: named Take eerst. Gestapeld: RMB verplicht vóór LMB.
        List<Method> base = new ArrayList<>();
        if (stackedTile) {
            base.add(Method.RIGHT_CLICK_TAKE);
            base.add(Method.INTERACT_TAKE);
            base.add(Method.MENU_INVOKE);
            base.add(Method.STORM_PICKUP);
            // Geen MOUSE_LEFT op gestapelde tile — pakt vaak verkeerde item
        } else {
            base.add(Method.INTERACT_TAKE);
            base.add(Method.RIGHT_CLICK_TAKE);
            base.add(Method.MENU_INVOKE);
            base.add(Method.STORM_PICKUP);
            // Geen canvas-LMB in AUTO: default op boom = Chop down
        }

        if (preferredMode != null && preferredMode != Method.WALK_THEN_RETRY) {
            List<Method> order = new ArrayList<>();
            // Bij stack: RIGHT_CLICK vóór preferred LMB/invoke (anders pak je verkeerde item)
            if (stackedTile && preferredMode != Method.RIGHT_CLICK_TAKE
                    && preferredMode != Method.INTERACT_TAKE) {
                order.add(Method.RIGHT_CLICK_TAKE);
            }
            order.add(preferredMode);
            if (!strictMode) {
                for (Method m : base) {
                    if (!order.contains(m)) {
                        order.add(m);
                    }
                }
            }
            return order;
        }

        // AUTO
        List<Method> order = new ArrayList<>();
        if (stackedTile) {
            order.add(Method.RIGHT_CLICK_TAKE);
        } else if (lastWorking == Method.RIGHT_CLICK_TAKE || lastWorking == Method.MENU_INVOKE) {
            order.add(lastWorking);
        }
        int start = Math.floorMod(rotateIndex, base.size());
        for (int i = 0; i < base.size(); i++) {
            Method m = base.get((start + i) % base.size());
            if (!order.contains(m)) {
                order.add(m);
            }
        }
        return order;
    }

    private static Result tryMethod(ITileItem item, Method m, String name, int id, WorldPoint wp) {
        switch (m) {
            case STORM_PICKUP:
                return tryStormPickup(item, name);
            case INTERACT_TAKE:
                return tryInteractTake(item, name);
            case MENU_INVOKE:
                return tryMenuInvoke(item, name, id, wp);
            case MOUSE_LEFT:
                return tryMouseLeft(item, name);
            case RIGHT_CLICK_TAKE:
                return tryRightClickTake(item, name);
            default:
                return fail(m, "skip");
        }
    }

    /**
     * A — Storm {@code canPick()+pickup()}: named Take via itemId-invoke (geen blinde LMB).
     */
    private static Result tryStormPickup(ITileItem item, String name) {
        try {
            if (!item.canPick()) {
                return fail(Method.STORM_PICKUP, "canPick=false");
            }
            if (item.pickup()) {
                return ok(Method.STORM_PICKUP, "item.pickup Take \"" + name + "\"");
            }
            return fail(Method.STORM_PICKUP, "pickup=false");
        } catch (Throwable t) {
            return fail(Method.STORM_PICKUP, t.toString());
        }
    }

    /**
     * B — {@code interact("Take")} met itemId (CombatBot legacy) — juiste item, ook gestapeld.
     */
    private static Result tryInteractTake(ITileItem item, String name) {
        try {
            if (item.interact("Take") || item.interact("Pick-up")) {
                return ok(Method.INTERACT_TAKE, "interact Take \"" + name + "\"");
            }
            return fail(Method.INTERACT_TAKE, "interact=false");
        } catch (Throwable t) {
            return fail(Method.INTERACT_TAKE, t.toString());
        }
    }

    /**
     * C — menuAction Take met echte menu-entry params.
     * Open kort RMB-menu → invoke die entry → menu dicht (Escape).
     * Plus blind GROUND_ITEM_THIRD (Take) als fallback.
     */
    private static Result tryMenuInvoke(ITileItem item, String name, int itemId, WorldPoint wp) {
        // Nooit LMB vóór invoke — dat is Chop down als een boom het canvas-punt dekt.
        Boolean blind = Static.callOnClientThread(() -> invokeGroundTakeBlind(name, itemId, wp), false);
        if (Boolean.TRUE.equals(blind)) {
            return ok(Method.MENU_INVOKE, "blind THIRD/ops itemId=" + itemId);
        }
        if (isOccludedByObject(item)) {
            return fail(Method.MENU_INVOKE, "occluded + blind fail (no RMB on tree)");
        }
        // 2) RMB → echte MenuEntry → invoke → sluit menu
        Point canvas = resolveCanvas(item);
        if (canvas == null) {
            return fail(Method.MENU_INVOKE, "off-screen + blind fail");
        }
        if (UiClickGuard.isBlocked(canvas)) {
            return fail(Method.MENU_INVOKE, "UI blocked");
        }
        closeMenuQuietly();
        if (!MouseManager.moveTo(canvas) || !Mouse.clickOnly(canvas.getX(), canvas.getY(), false)) {
            return fail(Method.MENU_INVOKE, "RMB fail");
        }
        if (!waitMenu(650)) {
            closeMenuQuietly();
            return fail(Method.MENU_INVOKE, "menu niet open");
        }
        MenuEntry[] entries = readMenu();
        int idx = findTakeIndex(entries, name);
        if (idx < 0) {
            closeMenuQuietly();
            return fail(Method.MENU_INVOKE, "geen Take entry");
        }
        boolean invoked = invokeMenuEntry(entries[idx]);
        closeMenuQuietly();
        return invoked
                ? ok(Method.MENU_INVOKE, "entry invoke \"" + name + "\" idx=" + idx)
                : fail(Method.MENU_INVOKE, "entry invoke fail");
    }

    /**
     * D — left-click canvas alleen als hover-default Take is.
     * Chop/Mine/Walk here → geen klik (zou boom hakken).
     */
    private static Result tryMouseLeft(ITileItem item, String name) {
        if (isOccludedByObject(item)) {
            return fail(Method.MOUSE_LEFT, "occluded (tree)");
        }
        Point canvas = resolveCanvas(item);
        if (canvas == null) {
            return fail(Method.MOUSE_LEFT, "off-screen");
        }
        if (UiClickGuard.isBlocked(canvas)) {
            return fail(Method.MOUSE_LEFT, "UI blocked");
        }
        if (!MouseManager.moveTo(canvas)) {
            return fail(Method.MOUSE_LEFT, "move fail");
        }
        sleep(40 + ThreadLocalRandom.current().nextInt(50));
        if (hoverLooksLikeChop()) {
            return fail(Method.MOUSE_LEFT, "hover Chop — no LMB");
        }
        if (!hoverLooksLikeTake(name)) {
            return fail(Method.MOUSE_LEFT, "hover not Take");
        }
        if (Mouse.clickOnly(canvas.getX(), canvas.getY(), true)) {
            return ok(Method.MOUSE_LEFT, "LMB Take " + canvas.getX() + "," + canvas.getY() + " " + name);
        }
        return fail(Method.MOUSE_LEFT, "click fail");
    }

    /**
     * E — rechtsklik → Take-rij aanklikken (alleen muis, geen invoke — voorkomt hangend menu).
     */
    private static Result tryRightClickTake(ITileItem item, String name) {
        return rightClickSelectTake(item, name, true);
    }

    /**
     * RMB → Choose Option → Take.
     * <p>
     * Belangrijk: canvas-mouse (CANVAS_EDT) beweegt vaak <b>niet</b> de Windows-cursor —
     * alleen game-events. Take zelf gaat via {@code menuAction} op de echte MenuEntry
     * (muis-klik op open menu is onbetrouwbaar). Menu altijd dicht (Cancel/Escape).
     */
    private static Result rightClickSelectTake(ITileItem item, String name, boolean tagAsRightClick) {
        Method label = tagAsRightClick ? Method.RIGHT_CLICK_TAKE : Method.INTERACT_TAKE;
        Point canvas = resolveCanvas(item);
        if (canvas == null) {
            return fail(label, "off-screen");
        }
        if (UiClickGuard.isBlocked(canvas)) {
            return fail(label, "UI blocked");
        }
        if (isOccludedByObject(item)) {
            return fail(label, "occluded — no RMB on tree");
        }
        closeMenuQuietly();
        // Korte move naar item (menu opent bij cursor)
        if (!Mouse.moveRaw(canvas.getX(), canvas.getY())) {
            if (!MouseManager.moveTo(canvas)) {
                return fail(label, "move fail");
            }
        }
        sleep(40 + ThreadLocalRandom.current().nextInt(50));
        if (hoverLooksLikeChop()) {
            return fail(label, "hover Chop — no RMB on tree");
        }
        if (!Mouse.clickOnly(canvas.getX(), canvas.getY(), false)) {
            closeMenuQuietly();
            return fail(label, "RMB fail");
        }
        if (!waitMenu(800)) {
            closeMenuQuietly();
            return fail(label, "menu niet open");
        }
        sleep(60 + ThreadLocalRandom.current().nextInt(60));

        MenuEntry[] openMenu = readMenu();
        boolean chopMenu = menuContainsChop(openMenu);
        MenuHit hit = resolveTakeMenuHit(name);
        if (hit == null) {
            closeMenuQuietly();
            return fail(label, chopMenu ? "Chop-menu, geen Take" : "geen Take voor " + name);
        }

        log.info("[GroundLoot] Choose Option Take \"{}\" idx={} hover={},{} type={}",
                name, hit.entryIdx, hit.x, hit.y,
                hit.entry != null && hit.entry.getType() != null ? hit.entry.getType().name() : "?");

        // Boom in het menu: nooit LMB op een rij (mis-klik = Chop down). Alleen invoke Take.
        if (!chopMenu) {
            boolean hovered = Mouse.moveRaw(hit.x, hit.y);
            if (!hovered) {
                hovered = MouseManager.moveTo(hit.x, hit.y);
            }
            if (hovered) {
                sleep(45 + ThreadLocalRandom.current().nextInt(70));
                Mouse.clickOnly(hit.x, hit.y, true);
                sleep(100);
                if (!isMenuOpen()) {
                    return ok(label, "LMB Take \"" + name + "\" @" + hit.x + "," + hit.y);
                }
            } else {
                log.warn("[GroundLoot] hover Take-rij mislukt @{},{}", hit.x, hit.y);
            }
        }

        // Betrouwbaar: invoke de open MenuEntry (sluit menu + Take)
        if (hit.entry != null && invokeMenuEntry(hit.entry)) {
            sleep(90);
            closeMenuQuietly();
            return ok(label, "menuAction Take \"" + name + "\" idx=" + hit.entryIdx);
        }

        closeMenuQuietly();
        return fail(label, "Take menuAction fail");
    }

    /** Snapshot van Take-rij positie — alles op client-thread. */
    private static final class MenuHit {
        final int entryIdx;
        final int x;
        final int y;
        final MenuEntry entry;

        MenuHit(int entryIdx, int x, int y, MenuEntry entry) {
            this.entryIdx = entryIdx;
            this.x = x;
            this.y = y;
            this.entry = entry;
        }
    }

    private static MenuHit resolveTakeMenuHit(String itemName) {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || !c.isMenuOpen()) {
                return null;
            }
            MenuEntry[] entries = c.getMenuEntries();
            if (entries == null || entries.length == 0) {
                return null;
            }
            int idx = findTakeIndex(entries, itemName);
            if (idx < 0) {
                return null;
            }
            int menuX = c.getMenuX();
            int menuY = c.getMenuY();
            int menuW = c.getMenuWidth();
            int menuH = c.getMenuHeight();
            if (menuW <= 0 || menuH <= 0) {
                log.warn("[GroundLoot] menu size 0: {}x{} @{},{}", menuW, menuH, menuX, menuY);
                return null;
            }
            // Array is omgekeerd: laatste entry = bovenste rij (MenuOpened javadoc)
            int rowFromTop = Math.max(0, (entries.length - 1) - idx);
            int header = estimateMenuHeader(menuH, entries.length);
            int rowH = estimateMenuRowHeight(menuH, entries.length, header);
            int hoverX = menuX + Math.max(4, menuW / 2);
            int hoverY = menuY + header + (rowFromTop * rowH) + (rowH / 2);
            hoverX = Math.max(menuX + 4, Math.min(menuX + menuW - 4, hoverX));
            hoverY = Math.max(menuY + header + 2, Math.min(menuY + menuH - 4, hoverY));
            if (hoverX < 8 || hoverY < 8) {
                log.warn("[GroundLoot] bad hover {},{} menu@{},{} {}x{}", hoverX, hoverY, menuX, menuY, menuW, menuH);
                return null;
            }
            return new MenuHit(idx, hoverX, hoverY, entries[idx]);
        }, null);
    }

    /** Kies header/rij-hoogte passend bij gemeten menu-hoogte. */
    private static int estimateMenuHeader(int menuH, int entryCount) {
        if (entryCount <= 0 || menuH <= 0) {
            return MENU_HEADER_H;
        }
        // header + n*row ≈ menuH → probeer bekende paren
        int[] headers = {19, 22, 15};
        int[] rows = {15, 15, 16};
        int bestH = MENU_HEADER_H;
        int bestErr = Integer.MAX_VALUE;
        for (int i = 0; i < headers.length; i++) {
            int predict = headers[i] + entryCount * rows[i];
            int err = Math.abs(predict - menuH);
            if (err < bestErr) {
                bestErr = err;
                bestH = headers[i];
            }
        }
        return bestH;
    }

    private static int estimateMenuRowHeight(int menuH, int entryCount, int header) {
        if (entryCount <= 0) {
            return MENU_ROW_H;
        }
        int rem = menuH - header;
        if (rem <= 0) {
            return MENU_ROW_H;
        }
        return Math.max(12, rem / entryCount);
    }

    private static boolean invokeGroundTakeBlind(String name, int itemId, WorldPoint wp) {
        Client c = Static.getClient();
        if (c == null || wp == null || itemId <= 0) {
            return false;
        }
        LocalPoint lp = null;
        try {
            net.runelite.api.WorldView wv = null;
            try {
                wv = c.findWorldViewFromWorldPoint(wp);
            } catch (Throwable ignored) {
            }
            if (wv == null) {
                try {
                    wv = c.getTopLevelWorldView();
                } catch (Throwable ignored) {
                }
            }
            if (wv != null) {
                lp = LocalPoint.fromWorld(wv, wp);
            }
            if (lp == null) {
                lp = LocalPoint.fromWorld(c, wp);
            }
        } catch (Throwable ignored) {
            lp = LocalPoint.fromWorld(c, wp);
        }
        if (lp == null) {
            return false;
        }
        int sx = lp.getSceneX();
        int sy = lp.getSceneY();
        String target = name != null ? name : "";
        // Storm/CombatBot: param0/param1 = scene, id+itemId = item, opcode = GROUND_ITEM_*
        MenuAction[] ops = {
                MenuAction.GROUND_ITEM_THIRD_OPTION,
                MenuAction.GROUND_ITEM_FIRST_OPTION,
                MenuAction.GROUND_ITEM_SECOND_OPTION,
                MenuAction.GROUND_ITEM_FOURTH_OPTION,
                MenuAction.GROUND_ITEM_FIFTH_OPTION
        };
        for (MenuAction op : ops) {
            if (op == null || op == MenuAction.UNKNOWN) {
                continue;
            }
            // Storm-volgorde: (p0,p1,opcode,id,itemId,wv,option,target)
            if (MenuInteract.invokeMenuAction(sx, sy, op.getId(), itemId, itemId, -1, "Take", target)) {
                return true;
            }
            if (MenuInteract.invokeMenuAction(sx, sy, op.getId(), itemId, -1, -1, "Take", target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * F — loop dichterbij als ver weg / off-screen.
     */
    public static Result walkCloserThenPickup(ITileItem item, WorldPoint playerPos) {
        if (item == null || item.getWorldLocation() == null) {
            return fail(Method.WALK_THEN_RETRY, "item null");
        }
        WorldPoint wp = item.getWorldLocation();
        int dist = playerPos != null ? playerPos.distanceTo(wp) : 99;
        if (dist > 1 || !isItemOnScreen(item)) {
            MovementHelper.walkTo(wp);
            lastDetail = "walk → " + wp + " d=" + dist + " onScreen=" + isItemOnScreen(item);
            log.info("[GroundLoot] WALK_THEN_RETRY {}", lastDetail);
            return new Result(true, Method.WALK_THEN_RETRY, lastDetail);
        }
        return pickup(item);
    }

    /** Item zichtbaar + geldig clickpunt (niet 0,0 / linkerhoek). */
    public static boolean isItemOnScreen(ITileItem item) {
        return resolveCanvas(item) != null;
    }

    /**
     * True als een scene-object (boom, etc.) de loot-clickbox dekt — LMB zou Chop/Mine doen.
     * Timeout / onleesbaar → true (liever invoke-only dan Chop-spam).
     */
    public static boolean isOccludedByObject(ITileItem item) {
        if (item == null || item.getWorldLocation() == null) {
            return false;
        }
        Boolean occ = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return true;
            }
            Point p = item.getCanvasPoint();
            WorldPoint loot = item.getWorldLocation();
            if (p == null && loot != null) {
                LocalPoint lp = LocalPoint.fromWorld(c, loot);
                if (lp != null) {
                    p = Perspective.localToCanvas(c, lp, c.getPlane());
                }
            }
            if (p == null || p.getX() < 8 || p.getY() < 8) {
                return false;
            }
            int px = p.getX();
            int py = p.getY();
            for (ITileObject obj : TileObjects.getAll()) {
                if (obj == null || obj.getWorldLocation() == null) {
                    continue;
                }
                WorldPoint ot = obj.getWorldLocation();
                if (ot.getPlane() != loot.getPlane()) {
                    continue;
                }
                int cheb = Math.max(Math.abs(ot.getX() - loot.getX()), Math.abs(ot.getY() - loot.getY()));
                if (cheb > 4) {
                    continue;
                }
                if (!looksLikeBlockingObject(obj)) {
                    continue;
                }
                // Zelfde tegel of ernaast: altijd occluded (palm/jungle dekt loot zonder clickbox-hit)
                if (cheb <= 1) {
                    return true;
                }
                TileObject raw = TileObjects.unwrap(obj);
                if (raw == null) {
                    if (cheb <= 2) {
                        return true;
                    }
                    continue;
                }
                java.awt.Shape box = null;
                try {
                    box = raw.getClickbox();
                } catch (Throwable ignored) {
                }
                if (box != null && box.contains(px, py)) {
                    return true;
                }
                if (box == null && cheb <= 2) {
                    return true;
                }
            }
            return false;
        }, true);
        return Boolean.TRUE.equals(occ);
    }

    private static boolean looksLikeBlockingObject(ITileObject obj) {
        try {
            if (obj.hasAction("Chop down") || obj.hasAction("Chop") || obj.hasAction("Mine")
                    || obj.hasAction("Cut") || obj.hasAction("Search")) {
                return true;
            }
            String n = obj.getName();
            if (n == null || n.isBlank() || "null".equalsIgnoreCase(n)) {
                return false;
            }
            String lower = n.toLowerCase(Locale.ROOT);
            return lower.contains("tree") || lower.contains("palm") || lower.contains("plant")
                    || lower.contains("bush") || lower.contains("rock");
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean menuContainsChop(MenuEntry[] entries) {
        if (entries == null) {
            return false;
        }
        for (MenuEntry e : entries) {
            if (e == null || e.getOption() == null) {
                continue;
            }
            String opt = strip(e.getOption()).toLowerCase(Locale.ROOT);
            if (opt.contains("chop") || opt.equals("mine") || opt.equals("cut")) {
                return true;
            }
        }
        return false;
    }

    /** Left-click default is Chop/Mine — canvas-klik zou de boom raken. */
    private static boolean hoverLooksLikeChop() {
        String opt = firstHoverOption();
        if (opt == null) {
            return false;
        }
        return opt.contains("chop") || opt.equals("mine") || opt.equals("cut");
    }

    /** Left-click default is Take (optioneel: target bevat itemnaam). */
    private static boolean hoverLooksLikeTake(String itemName) {
        Boolean ok = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            try {
                MenuEntry[] entries = c.getMenuEntries();
                if (entries == null || entries.length == 0) {
                    return false;
                }
                for (MenuEntry e : entries) {
                    if (e == null || e.getOption() == null) {
                        continue;
                    }
                    String opt = strip(e.getOption()).toLowerCase(Locale.ROOT);
                    if (opt.isEmpty() || opt.equals("cancel")) {
                        continue;
                    }
                    if (!opt.equals("take") && !opt.equals("pick-up") && !opt.equals("take-all")) {
                        return false;
                    }
                    if (itemName != null && !itemName.isBlank()) {
                        String target = strip(e.getTarget()).toLowerCase(Locale.ROOT);
                        String needle = itemName.toLowerCase(Locale.ROOT).trim();
                        if (!target.isEmpty() && !target.contains(needle)) {
                            return false;
                        }
                    }
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    private static String firstHoverOption() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                MenuEntry[] entries = c.getMenuEntries();
                if (entries == null) {
                    return null;
                }
                for (MenuEntry e : entries) {
                    if (e == null || e.getOption() == null) {
                        continue;
                    }
                    String opt = strip(e.getOption()).toLowerCase(Locale.ROOT);
                    if (opt.isEmpty() || opt.equals("cancel")) {
                        continue;
                    }
                    return opt;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }, null);
    }

    // ---- internals ----

    private static Point resolveCanvas(ITileItem item) {
        try {
            return Static.callOnClientThread(() -> {
                if (item == null || item.getWorldLocation() == null) {
                    return null;
                }
                Client c = Static.getClient();
                if (c == null) {
                    return null;
                }
                WorldPoint wp = item.getWorldLocation();
                LocalPoint lp = LocalPoint.fromWorld(c, wp);
                if (lp == null) {
                    return null;
                }
                // Geen tile-poly = niet in viewport → geen klik
                if (Perspective.getCanvasTilePoly(c, lp) == null) {
                    return null;
                }
                Point p = item.getCanvasPoint();
                if (p == null) {
                    p = Perspective.localToCanvas(c, lp, c.getPlane());
                }
                if (!isValidGameClick(c, p)) {
                    return null;
                }
                return p;
            }, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isValidGameClick(Client c, Point p) {
        if (p == null) {
            return false;
        }
        int x = p.getX();
        int y = p.getY();
        // Linkerhoek / off-screen junk (Perspective geeft soms 0,0)
        if (x < 8 || y < 8) {
            return false;
        }
        int maxW = 765;
        int maxH = 503;
        try {
            java.awt.Canvas canvas = c.getCanvas();
            if (canvas != null) {
                maxW = Math.max(40, canvas.getWidth());
                maxH = Math.max(40, canvas.getHeight());
            }
        } catch (Throwable ignored) {
        }
        return x < maxW - 2 && y < maxH - 2;
    }

    private static String safeName(ITileItem item) {
        try {
            String n = item.getName();
            return n != null ? n : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean waitMenu(long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isMenuOpen()) {
                return true;
            }
            sleep(20);
        }
        return isMenuOpen();
    }

    private static boolean isMenuOpen() {
        try {
            Client c = Static.getClient();
            return c != null && c.isMenuOpen();
        } catch (Throwable t) {
            return false;
        }
    }

    private static MenuEntry[] readMenu() {
        try {
            Client c = Static.getClient();
            return c != null ? c.getMenuEntries() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int findTakeIndex(MenuEntry[] entries, String itemName) {
        if (entries == null || entries.length == 0) {
            return -1;
        }
        String needle = itemName != null ? itemName.toLowerCase(Locale.ROOT).trim() : "";
        int fallback = -1;
        // Van boven (laatste array-index) naar beneden — eerste zichtbare Take matcht needle
        for (int i = entries.length - 1; i >= 0; i--) {
            MenuEntry e = entries[i];
            if (e == null) {
                continue;
            }
            String opt = strip(e.getOption()).toLowerCase(Locale.ROOT);
            if (!opt.equals("take") && !opt.equals("pick-up")) {
                continue;
            }
            if (fallback < 0) {
                fallback = i; // bovenste Take
            }
            if (!needle.isEmpty()) {
                String target = strip(e.getTarget()).toLowerCase(Locale.ROOT);
                if (target.contains(needle)) {
                    return i;
                }
            }
        }
        // Bekende itemnaam maar geen match → liever falen dan verkeerde loot pakken
        if (!needle.isEmpty()) {
            return -1;
        }
        return fallback;
    }

    private static String strip(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("<[^>]+>", "").trim();
    }

    private static boolean invokeMenuEntry(MenuEntry e) {
        return MenuInteract.invokeMenuEntry(e);
    }

    private static boolean clickMenuRow(MenuEntry[] entries, int entryIdx) {
        MenuHit hit = Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null || !c.isMenuOpen() || entries == null) {
                return null;
            }
            if (entryIdx < 0 || entryIdx >= entries.length) {
                return null;
            }
            int menuX = c.getMenuX();
            int menuY = c.getMenuY();
            int menuW = c.getMenuWidth();
            int menuH = c.getMenuHeight();
            if (menuW <= 0 || menuH <= 0) {
                return null;
            }
            int rowFromTop = Math.max(0, (entries.length - 1) - entryIdx);
            int header = estimateMenuHeader(menuH, entries.length);
            int rowH = estimateMenuRowHeight(menuH, entries.length, header);
            int hx = menuX + Math.max(4, menuW / 2);
            int hy = menuY + header + (rowFromTop * rowH) + (rowH / 2);
            hx = Math.max(menuX + 4, Math.min(menuX + menuW - 4, hx));
            hy = Math.max(menuY + header + 2, Math.min(menuY + menuH - 4, hy));
            return new MenuHit(entryIdx, hx, hy, entries[entryIdx]);
        }, null);
        if (hit == null) {
            return false;
        }
        if (!MouseManager.moveTo(hit.x, hit.y)) {
            return false;
        }
        sleep(50 + ThreadLocalRandom.current().nextInt(80));
        return Mouse.clickOnly(hit.x, hit.y, true);
    }

    private static void closeMenuQuietly() {
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!isMenuOpen()) {
                return;
            }
            // 1) Cancel-entry (sluit Choose Option zonder Escape)
            if (invokeCancelEntry()) {
                sleep(50 + ThreadLocalRandom.current().nextInt(40));
                if (!isMenuOpen()) {
                    return;
                }
            }
            // 2) Escape
            try {
                Keyboard.pressKey(java.awt.event.KeyEvent.VK_ESCAPE);
                sleep(55 + ThreadLocalRandom.current().nextInt(45));
            } catch (Throwable ignored) {
            }
            if (!isMenuOpen()) {
                return;
            }
        }
    }

    private static boolean invokeCancelEntry() {
        try {
            return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
                Client c = Static.getClient();
                if (c == null || !c.isMenuOpen()) {
                    return false;
                }
                MenuEntry[] entries = c.getMenuEntries();
                if (entries == null) {
                    return false;
                }
                for (MenuEntry e : entries) {
                    if (e == null) {
                        continue;
                    }
                    if ("cancel".equalsIgnoreCase(strip(e.getOption()))) {
                        return invokeMenuEntry(e);
                    }
                }
                return false;
            }, false));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Result ok(Method m, String detail) {
        return new Result(true, m, detail);
    }

    private static Result fail(Method m, String detail) {
        return new Result(false, m, detail);
    }
}
