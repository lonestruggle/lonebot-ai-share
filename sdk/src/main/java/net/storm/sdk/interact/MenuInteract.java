package net.storm.sdk.interact;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.game.Static;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Menu interact via {@code Client.invokeMenuAction} (geen muis).
 * Alle NPC/composition-reads gebeuren op de client-thread.
 */
public final class MenuInteract {

    private static final Logger log = LoggerFactory.getLogger(MenuInteract.class);

    public enum ProbeResult {
        NOT_RUN,
        NO_CLIENT,
        NO_ACTION,
        METHOD_MISSING,
        EXCEPTION,
        INVOKED_OK,
        TIMEOUT
    }

    private static volatile ProbeResult lastProbe = ProbeResult.NOT_RUN;
    private static volatile String lastProbeDetail = "";

    private MenuInteract() {
    }

    public static ProbeResult getLastProbe() {
        return lastProbe;
    }

    public static String getLastProbeDetail() {
        return lastProbeDetail != null ? lastProbeDetail : "";
    }

    public static boolean interactNpc(NPC npc, String action) {
        return probeNpc(npc, action) == ProbeResult.INVOKED_OK;
    }

    public static boolean interactPlayer(Player player, String action) {
        return probePlayer(player, action) == ProbeResult.INVOKED_OK;
    }

    /** Via NPC-index (INPC / off-thread veilig). */
    public static boolean interactNpcByIndex(int npcIndex, String action) {
        if (npcIndex < 0 || action == null) {
            return false;
        }
        ProbeResult r = Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return ProbeResult.NO_CLIENT;
            }
            NPC live = null;
            for (NPC n : client.getNpcs()) {
                if (n != null && n.getIndex() == npcIndex) {
                    live = n;
                    break;
                }
            }
            if (live == null) {
                lastProbeDetail = "npc index " + npcIndex + " weg";
                return ProbeResult.NO_ACTION;
            }
            return probeNpcOnClient(live, action);
        }, ProbeResult.TIMEOUT);
        lastProbe = r != null ? r : ProbeResult.TIMEOUT;
        return lastProbe == ProbeResult.INVOKED_OK;
    }

    public static ProbeResult probeNpcAttack(NPC npc) {
        return probeNpc(npc, "Attack");
    }

    public static ProbeResult probeNpc(NPC npc, String action) {
        if (npc == null || action == null) {
            lastProbe = ProbeResult.NO_CLIENT;
            lastProbeDetail = "npc/action null";
            return lastProbe;
        }
        // Alles op client-thread — composition + invoke
        ProbeResult r = Static.callOnClientThread(() -> probeNpcOnClient(npc, action), ProbeResult.TIMEOUT);
        lastProbe = r != null ? r : ProbeResult.TIMEOUT;
        return lastProbe;
    }

    private static ProbeResult probePlayer(Player player, String action) {
        if (player == null || action == null) {
            lastProbe = ProbeResult.NO_CLIENT;
            lastProbeDetail = "player/action null";
            return lastProbe;
        }
        ProbeResult r = Static.callOnClientThread(() -> probePlayerOnClient(player, action), ProbeResult.TIMEOUT);
        lastProbe = r != null ? r : ProbeResult.TIMEOUT;
        return lastProbe;
    }

    private static ProbeResult probePlayerOnClient(Player player, String action) {
        Client client = Static.getClient();
        if (client == null) {
            lastProbeDetail = "geen client";
            return ProbeResult.NO_CLIENT;
        }
        Player live = player;
        try {
            int want = player.getId();
            for (Player p : client.getPlayers()) {
                if (p != null && p.getId() == want) {
                    live = p;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        String[] options;
        try {
            options = client.getPlayerOptions();
        } catch (Throwable t) {
            lastProbeDetail = "geen playerOptions: " + t;
            return ProbeResult.EXCEPTION;
        }
        if (options == null) {
            lastProbeDetail = "geen playerOptions";
            return ProbeResult.NO_ACTION;
        }
        int idx = -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i] != null && options[i].equalsIgnoreCase(action)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            lastProbeDetail = "geen '" + action + "' op player " + live.getName();
            return ProbeResult.NO_ACTION;
        }
        String option = options[idx];
        String target = live.getName() != null ? live.getName() : "";
        int id = live.getId();
        MenuAction menuAction = menuActionForPlayerIndex(idx);
        int opcode = menuAction.getId();
        try {
            if (invokeMenuOnClient(option, target, id, opcode, 0, 0, 0)) {
                lastProbeDetail = "menuAction " + option + " → " + target + " pid=" + id;
                log.info("[MenuProbe] INVOKED_OK — {}", lastProbeDetail);
                return ProbeResult.INVOKED_OK;
            }
            lastProbeDetail = "menuAction fail " + option + " → " + target;
            return ProbeResult.NO_ACTION;
        } catch (Throwable t1) {
            lastProbeDetail = String.valueOf(t1);
            return ProbeResult.EXCEPTION;
        }
    }

    private static ProbeResult probeNpcOnClient(NPC npc, String action) {
        Client client = Static.getClient();
        if (client == null) {
            lastProbeDetail = "geen client";
            return ProbeResult.NO_CLIENT;
        }
        // Re-find by index — meegegeven NPC-ref kan stale zijn off-thread
        NPC live = null;
        int want = npc.getIndex();
        for (NPC n : client.getNpcs()) {
            if (n != null && n.getIndex() == want) {
                live = n;
                break;
            }
        }
        if (live == null) {
            live = npc;
        }

        NPCComposition comp;
        try {
            comp = live.getTransformedComposition();
            if (comp == null) {
                comp = live.getComposition();
            }
        } catch (Throwable t) {
            lastProbeDetail = "composition: " + t;
            return ProbeResult.EXCEPTION;
        }
        if (comp == null || comp.getActions() == null) {
            lastProbeDetail = "geen composition";
            return ProbeResult.NO_ACTION;
        }
        String[] actions = comp.getActions();
        int idx = -1;
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && actions[i].equalsIgnoreCase(action)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            lastProbeDetail = "geen '" + action + "' op " + live.getName();
            return ProbeResult.NO_ACTION;
        }

        String option = actions[idx];
        String target = live.getName() != null ? live.getName() : "";
        int id = live.getIndex();
        MenuAction menuAction = menuActionForNpcIndex(idx);
        int opcode = menuAction.getId();

        try {
            // Prefer public Client.menuAction — via resolve (geen UNKNOWN vals-OK)
            if (invokeMenuOnClient(option, target, id, opcode, 0, 0, 0)) {
                lastProbeDetail = "menuAction " + option + " → " + target + " idx=" + id;
                log.info("[MenuProbe] INVOKED_OK — {}", lastProbeDetail);
                return ProbeResult.INVOKED_OK;
            }
            lastProbeDetail = "menuAction fail " + option + " → " + target;
            return ProbeResult.NO_ACTION;
        } catch (Throwable t1) {
            try {
                Method m = findInvokeMenuAction(client);
                if (m == null) {
                    lastProbeDetail = "menuAction+invokeMenuAction fail: " + t1;
                    log.warn("[MenuProbe] A=METHOD_MISSING — {}", lastProbeDetail);
                    return ProbeResult.METHOD_MISSING;
                }
                m.invoke(client, option, target, id, opcode, 0, 0);
                lastProbeDetail = option + " → " + target + " idx=" + id + " op=" + opcode;
                log.info("[MenuProbe] INVOKED_OK (reflect) — {}", lastProbeDetail);
                return ProbeResult.INVOKED_OK;
            } catch (Throwable t) {
                lastProbeDetail = String.valueOf(t);
                log.warn("[MenuProbe] A=EXCEPTION — {}", t.toString());
                return ProbeResult.EXCEPTION;
            }
        }
    }

    public static boolean interactObject(TileObject obj, String action) {
        Client client = Static.getClient();
        if (client == null || obj == null || action == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            ObjectComposition comp = resolveObjectComposition(client, obj.getId());
            if (comp == null || comp.getActions() == null) {
                return false;
            }
            String[] actions = comp.getActions();
            int[] scene = objectSceneXY(obj);
            if (scene == null) {
                return false;
            }
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] != null && actions[i].equalsIgnoreCase(action)) {
                    return invokeMenuOnClient(
                            actions[i],
                            comp.getName() != null ? comp.getName() : "",
                            obj.getId(),
                            menuActionForObjectIndex(i).getId(),
                            scene[0],
                            scene[1]
                    );
                }
            }
            return false;
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    /** CombatBot {@code interact(0)} — eerste non-null object-actie. */
    public static boolean interactObjectFirst(TileObject obj) {
        Client client = Static.getClient();
        if (client == null || obj == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            ObjectComposition comp = resolveObjectComposition(client, obj.getId());
            if (comp == null || comp.getActions() == null) {
                return false;
            }
            String[] actions = comp.getActions();
            int[] scene = objectSceneXY(obj);
            if (scene == null) {
                return false;
            }
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] != null && !actions[i].isBlank()) {
                    return invokeMenuOnClient(
                            actions[i],
                            comp.getName() != null ? comp.getName() : "",
                            obj.getId(),
                            menuActionForObjectIndex(i).getId(),
                            scene[0],
                            scene[1]
                    );
                }
            }
            return false;
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Inventory item → NPC ({@link MenuAction#WIDGET_TARGET_ON_NPC}).
     */
    public static boolean useInventoryOnNpc(int invSlot, int itemId, String itemName, NPC npc) {
        if (invSlot < 0 || itemId <= 0 || npc == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return false;
            }
            String fromName = itemName != null ? itemName : "";
            String npcName = npc.getName() != null ? npc.getName() : "";
            int packed = net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER;
            try {
                client.menuAction(invSlot, packed, MenuAction.WIDGET_TARGET, 0, itemId, "Use", fromName);
            } catch (Throwable t) {
                if (!invokeMenuOnClient("Use", fromName, 0, MenuAction.WIDGET_TARGET.getId(),
                        invSlot, packed, itemId)) {
                    return false;
                }
            }
            String target = fromName + " -> " + npcName;
            try {
                client.menuAction(0, 0, MenuAction.WIDGET_TARGET_ON_NPC, npc.getIndex(), 0, "Use", target);
                lastProbe = ProbeResult.INVOKED_OK;
                lastProbeDetail = "useOn npc " + target;
                return true;
            } catch (Throwable t) {
                return invokeMenuOnClient("Use", target, npc.getIndex(),
                        MenuAction.WIDGET_TARGET_ON_NPC.getId(), 0, 0, 0);
            }
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Inventory item → ground item ({@link MenuAction#WIDGET_TARGET_ON_GROUND_ITEM}).
     */
    public static boolean useInventoryOnGroundItem(int invSlot, int itemId, String itemName,
                                                   int groundItemId, WorldPoint tile) {
        if (invSlot < 0 || itemId <= 0 || groundItemId <= 0 || tile == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return false;
            }
            net.runelite.api.coords.LocalPoint lp = net.runelite.api.coords.LocalPoint.fromWorld(client, tile);
            if (lp == null) {
                return false;
            }
            String fromName = itemName != null ? itemName : "";
            int packed = net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER;
            try {
                client.menuAction(invSlot, packed, MenuAction.WIDGET_TARGET, 0, itemId, "Use", fromName);
            } catch (Throwable t) {
                if (!invokeMenuOnClient("Use", fromName, 0, MenuAction.WIDGET_TARGET.getId(),
                        invSlot, packed, itemId)) {
                    return false;
                }
            }
            String target = fromName + " -> ground";
            try {
                client.menuAction(lp.getSceneX(), lp.getSceneY(), MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
                        groundItemId, 0, "Use", target);
                lastProbe = ProbeResult.INVOKED_OK;
                lastProbeDetail = "useOn ground " + target;
                return true;
            } catch (Throwable t) {
                return invokeMenuOnClient("Use", target, groundItemId,
                        MenuAction.WIDGET_TARGET_ON_GROUND_ITEM.getId(),
                        lp.getSceneX(), lp.getSceneY(), 0);
            }
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Inventory item → widget ({@link MenuAction#WIDGET_TARGET_ON_WIDGET}).
     */
    public static boolean useInventoryOnWidget(int invSlot, int itemId, String itemName,
                                               int otherIndex, int otherPacked, int otherItemId,
                                               String otherName) {
        if (invSlot < 0 || itemId <= 0 || otherPacked == 0) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return false;
            }
            String fromName = itemName != null ? itemName : "";
            int packed = net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER;
            try {
                client.menuAction(invSlot, packed, MenuAction.WIDGET_TARGET, 0, itemId, "Use", fromName);
            } catch (Throwable t) {
                if (!invokeMenuOnClient("Use", fromName, 0, MenuAction.WIDGET_TARGET.getId(),
                        invSlot, packed, itemId)) {
                    return false;
                }
            }
            String other = otherName != null ? otherName : "";
            String target = fromName + " -> " + other;
            int p0 = otherIndex < 0 ? -1 : otherIndex;
            try {
                client.menuAction(p0, otherPacked, MenuAction.WIDGET_TARGET_ON_WIDGET,
                        0, otherItemId, "Use", target);
                lastProbe = ProbeResult.INVOKED_OK;
                lastProbeDetail = "useOn widget " + target;
                return true;
            } catch (Throwable t) {
                return invokeMenuOnClient("Use", target, 0, MenuAction.WIDGET_TARGET_ON_WIDGET.getId(),
                        p0, otherPacked, otherItemId);
            }
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Tweede helft van inventory Use: item is al geselecteerd (vorige tick WIDGET_TARGET).
     */
    public static boolean useSelectedOnInventory(int otherSlot, int otherItemId, String targetName) {
        if (otherSlot < 0 || otherItemId <= 0) {
            return false;
        }
        String target = targetName != null ? targetName : "";
        int packed = net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER;
        return invokeMenu("Use", target, 0, MenuAction.WIDGET_TARGET_ON_WIDGET.getId(),
                otherSlot, packed, otherItemId);
    }

    /**
     * Inventory item → tile object (CombatBot {@code log.useOn(fire)}).
     * {@link MenuAction#WIDGET_TARGET} then {@link MenuAction#WIDGET_TARGET_ON_GAME_OBJECT}.
     */
    public static boolean useInventoryOnObject(int invSlot, int itemId, String itemName, TileObject obj) {
        if (invSlot < 0 || itemId <= 0 || obj == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> {
            Client client = Static.getClient();
            if (client == null) {
                return false;
            }
            int[] scene = objectSceneXY(obj);
            if (scene == null) {
                return false;
            }
            ObjectComposition comp = resolveObjectComposition(client, obj.getId());
            String objName = comp != null && comp.getName() != null ? comp.getName() : "";
            String fromName = itemName != null ? itemName : "";
            int packed = net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER;
            try {
                client.menuAction(invSlot, packed, MenuAction.WIDGET_TARGET, 0, itemId, "Use", fromName);
            } catch (Throwable t) {
                if (!invokeMenuOnClient("Use", fromName, 0, MenuAction.WIDGET_TARGET.getId(),
                        invSlot, packed, itemId)) {
                    return false;
                }
            }
            String target = fromName + " -> " + objName;
            try {
                client.menuAction(scene[0], scene[1], MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
                        obj.getId(), 0, "Use", target);
                lastProbe = ProbeResult.INVOKED_OK;
                lastProbeDetail = "useOn object " + target;
                return true;
            } catch (Throwable t) {
                return invokeMenuOnClient("Use", target, obj.getId(),
                        MenuAction.WIDGET_TARGET_ON_GAME_OBJECT.getId(), scene[0], scene[1], 0);
            }
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Scene-coords voor GAME_OBJECT_* — bij GameObject bij voorkeur {@code getSceneMinLocation}
     * (multi-tile bomen), anders LocalPoint.
     */
    private static int[] objectSceneXY(TileObject obj) {
        if (obj == null) {
            return null;
        }
        try {
            if (obj instanceof net.runelite.api.GameObject) {
                Point sm = ((net.runelite.api.GameObject) obj).getSceneMinLocation();
                if (sm != null) {
                    return new int[]{sm.getX(), sm.getY()};
                }
            }
        } catch (Throwable ignored) {
        }
        var lp = obj.getLocalLocation();
        if (lp == null) {
            return null;
        }
        return new int[]{lp.getSceneX(), lp.getSceneY()};
    }

    /** Prefer transformed (impostor) composition when present — deposit boxes etc. */
    public static ObjectComposition resolveObjectComposition(Client client, int objectId) {
        if (client == null || objectId < 0) {
            return null;
        }
        ObjectComposition comp = client.getObjectDefinition(objectId);
        if (comp == null) {
            return null;
        }
        try {
            int[] impostorIds = comp.getImpostorIds();
            if (impostorIds != null && impostorIds.length > 0) {
                ObjectComposition imp = comp.getImpostor();
                if (imp != null) {
                    return imp;
                }
            }
        } catch (Throwable ignored) {
        }
        return comp;
    }

    public static boolean invokeMenu(String option, String target, int id, int opcode, int p0, int p1) {
        // Back-compat: id vaak itemId; itemId-veld 0 (widgets). Voor ground items: zie overload.
        return invokeMenu(option, target, id, opcode, p0, p1, 0);
    }

    /**
     * MenuAction met expliciete {@code itemId} (nodig voor ground-item Take).
     */
    public static boolean invokeMenu(String option, String target, int id, int opcode, int p0, int p1, int itemId) {
        Boolean ok = Static.callOnClientThread(
                () -> invokeMenuOnClient(option, target, id, opcode, p0, p1, itemId), false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Storm-compat: {@code invokeMenuAction(param0, param1, opcode, id, itemId, worldViewId, option, target)}.
     * Op vanilla RL → {@link Client#menuAction}; {@code worldViewId} wordt gebruikt voor scene-resolutie
     * waar nodig (blind ground-Take), niet als extra menuAction-arg (RL heeft die niet).
     */
    public static boolean invokeMenuAction(int param0, int param1, int opcode, int id, int itemId,
                                           int worldViewId, String option, String target) {
        Boolean ok = Static.callOnClientThread(() -> {
            // worldViewId: -1 = top-level; geen apart veld in Client.menuAction
            return invokeMenuOnClient(option, target, id, opcode, param0, param1, itemId)
                    || invokeMenuOnClient(option, target, itemId, opcode, param0, param1, id);
        }, false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Zoals CombatBot/Storm: fire de open {@link MenuEntry} 1:1 (tags behouden, deprioritize strippen).
     */
    public static boolean invokeMenuEntry(net.runelite.api.MenuEntry e) {
        if (e == null) {
            return false;
        }
        Boolean ok = Static.callOnClientThread(() -> invokeMenuEntryOnClient(e), false);
        return Boolean.TRUE.equals(ok);
    }

    private static boolean invokeMenuEntryOnClient(net.runelite.api.MenuEntry e) {
        int opcode = -1;
        try {
            if (e.getType() != null) {
                opcode = e.getType().getId();
            }
        } catch (Throwable ignored) {
        }
        int itemId = -1;
        try {
            itemId = e.getItemId();
        } catch (Throwable ignored) {
        }
        int worldViewId = -1;
        try {
            worldViewId = e.getWorldViewId();
        } catch (Throwable ignored) {
        }
        String option = e.getOption() != null ? e.getOption() : "";
        String target = e.getTarget() != null ? e.getTarget() : ""; // tags behouden (Storm)
        int id = e.getIdentifier();
        int p0 = e.getParam0();
        int p1 = e.getParam1();

        // 1) Storm-volgorde
        if (invokeMenuActionOnClient(p0, p1, opcode, id, itemId, worldViewId, option, target)) {
            return true;
        }
        // 2) identifier ↔ itemId swap (sommige entries)
        if (itemId > 0 && itemId != id
                && invokeMenuActionOnClient(p0, p1, opcode, itemId, id, worldViewId, option, target)) {
            return true;
        }
        // 3) itemId fallback = identifier (ground Take)
        if (itemId <= 0 && id > 0
                && invokeMenuActionOnClient(p0, p1, opcode, id, id, worldViewId, option, target)) {
            return true;
        }
        return false;
    }

    private static boolean invokeMenuActionOnClient(int param0, int param1, int opcode, int id, int itemId,
                                                    int worldViewId, String option, String target) {
        return invokeMenuOnClient(option, target, id, opcode, param0, param1, itemId);
    }

    /**
     * Inventory / bank-inventory CC_OP: {@code itemOp} is 1–5, {@code itemId} is the item definition id.
     */
    public static boolean invokeInventory(String option, String target, int itemId, int itemOp,
                                          int opcode, int slot, int widgetId) {
        Boolean ok = Static.callOnClientThread(
                () -> invokeMenuOnClient(option, target, itemOp, opcode, slot, widgetId, itemId), false);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Walk-here via public {@link Client#menuAction} (vanilla RL) — preferred over reflection invokeMenuAction.
     */
    public static boolean invokeWalk(WorldPoint worldPoint) {
        return net.storm.sdk.movement.WalkClickHelper.invokeWalk(worldPoint);
    }

    private static boolean invokeMenuOnClient(String option, String target, int id, int opcode,
                                              int p0, int p1) {
        return invokeMenuOnClient(option, target, id, opcode, p0, p1, 0);
    }

    private static boolean invokeMenuOnClient(String option, String target, int id, int opcode,
                                              int p0, int p1, int itemId) {
        Client client = Static.getClient();
        if (client == null) {
            lastProbe = ProbeResult.NO_CLIENT;
            return false;
        }
        MenuAction action = resolveMenuAction(opcode);
        if (action == null || action == MenuAction.UNKNOWN) {
            // Vals-OK fix: MenuAction.of() geeft UNKNOWN i.p.v. null — dat is GEEN succes
            lastProbe = ProbeResult.NO_ACTION;
            lastProbeDetail = "UNKNOWN/invalid opcode=" + opcode;
            log.debug("[MenuInteract] skip UNKNOWN opcode={}", opcode);
            return false;
        }
        try {
            client.menuAction(p0, p1, action, id, itemId,
                    option != null ? option : "", target != null ? target : "");
            lastProbe = ProbeResult.INVOKED_OK;
            lastProbeDetail = "menuAction " + action.name()
                    + " p0=" + p0 + " p1=" + p1
                    + " id=" + id + " itemId=" + itemId
                    + " " + option + " → " + target;
            log.info("[MenuInteract] {}", lastProbeDetail);
            return true;
        } catch (Throwable t1) {
            try {
                Method m = findInvokeMenuAction(client);
                if (m == null) {
                    lastProbe = ProbeResult.METHOD_MISSING;
                    lastProbeDetail = "menuAction fail + geen invokeMenuAction: " + t1;
                    return false;
                }
                m.invoke(client, option, target, id, action.getId(), p0, p1);
                lastProbe = ProbeResult.INVOKED_OK;
                lastProbeDetail = "invokeMenuAction reflect " + action.name();
                return true;
            } catch (Throwable t) {
                lastProbe = ProbeResult.EXCEPTION;
                lastProbeDetail = t.toString();
                log.warn("[MenuInteract] exception: {}", t.toString());
                return false;
            }
        }
    }

    /** Strip deprioritize-offset (2000) — anders of() → UNKNOWN → vals-OK. */
    private static MenuAction resolveMenuAction(int opcode) {
        if (opcode < 0) {
            return MenuAction.UNKNOWN;
        }
        int id = opcode;
        if (id >= MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET) {
            id -= MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET;
        }
        MenuAction a = MenuAction.of(id);
        return a != null ? a : MenuAction.UNKNOWN;
    }

    private static Method findInvokeMenuAction(Client client) {
        for (Method m : client.getClass().getMethods()) {
            if (!"invokeMenuAction".equals(m.getName())) {
                continue;
            }
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 6 && pts[0] == String.class && pts[1] == String.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static MenuAction menuActionForPlayerIndex(int i) {
        switch (i) {
            case 1:
                return MenuAction.PLAYER_SECOND_OPTION;
            case 2:
                return MenuAction.PLAYER_THIRD_OPTION;
            case 3:
                return MenuAction.PLAYER_FOURTH_OPTION;
            case 4:
                return MenuAction.PLAYER_FIFTH_OPTION;
            case 5:
                return MenuAction.PLAYER_SIXTH_OPTION;
            case 6:
                return MenuAction.PLAYER_SEVENTH_OPTION;
            case 7:
                return MenuAction.PLAYER_EIGHTH_OPTION;
            default:
                return MenuAction.PLAYER_FIRST_OPTION;
        }
    }

    private static MenuAction menuActionForNpcIndex(int i) {
        switch (i) {
            case 1:
                return MenuAction.NPC_SECOND_OPTION;
            case 2:
                return MenuAction.NPC_THIRD_OPTION;
            case 3:
                return MenuAction.NPC_FOURTH_OPTION;
            case 4:
                return MenuAction.NPC_FIFTH_OPTION;
            default:
                return MenuAction.NPC_FIRST_OPTION;
        }
    }

    private static MenuAction menuActionForObjectIndex(int i) {
        switch (i) {
            case 1:
                return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 2:
                return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 3:
                return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 4:
                return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default:
                return MenuAction.GAME_OBJECT_FIRST_OPTION;
        }
    }
}
