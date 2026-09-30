package net.storm.sdk.movement.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.movement.pathfinder.model.Requirements;
import net.storm.api.movement.pathfinder.model.Transport;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Factory + registry for non-instant transports (ladders, boats, doors, shortcuts).
 * {@link net.storm.sdk.movement.MovementHelper} gebruikt {@link #getCustomTransports()}
 * in de globale BFS — zelfde graaf als {@link net.storm.sdk.movement.Walker}.
 * TSV-data: {@link TransportTsvLoader} laadt F2P gates + stairs/dungeons (of full SP via JVM-flag).
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/pathfinder/TransportLoader.html">Storm TransportLoader</a>
 */
public final class TransportLoader {

    private static final CopyOnWriteArrayList<Transport> CUSTOM = new CopyOnWriteArrayList<>();

    public TransportLoader() {
    }

    public static List<Transport> getCustomTransports() {
        TransportTsvLoader.ensureLoaded();
        return CUSTOM;
    }

    /**
     * Classpath-TSV object (ID, anders naam). {@code chatOptions} niet leeg → Open + dialog (Al Kharid-tol).
     */
    static Transport tsvObject(WorldPoint source, WorldPoint destination, int radius,
                               int objId, String objName, String action, String name, String... chatOptions) {
        return tsvObject(source, destination, radius, objId, objName, action, name, null, 1, chatOptions);
    }

    static Transport tsvObject(WorldPoint source, WorldPoint destination, int radius,
                               int objId, String objName, String action, String name,
                               Requirements extraReq, int weight, String... chatOptions) {
        String label = name != null && !name.isBlank() ? name : "object";
        boolean dialog = chatOptions != null && chatOptions.length > 0;
        Callable<Boolean> handler = dialog
                ? () -> TransportActions.dialogOrObjectIdOrName(source, radius, objId, objName, action, chatOptions)
                : () -> TransportActions.interactObjectIdOrName(source, radius, objId, objName, action);
        Requirements req = mergeReq(AlKharidGate.requirementsIfGate(objId, source), extraReq);
        return build(source, destination, radius, req, label, handler, Math.max(1, weight));
    }

    private static Requirements mergeReq(Requirements a, Requirements b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        for (var q : b.getQuests()) {
            a.quest(q);
        }
        for (var s : b.getSkills()) {
            a.skill(s.skill, s.level);
        }
        for (var e : b.getExtra()) {
            a.require(e);
        }
        return a;
    }

    public static void addCustomTransport(Transport transport) {
        if (transport != null && !CUSTOM.contains(transport)) {
            CUSTOM.add(transport);
        }
    }

    public static void removeCustomTransport(Transport transport) {
        if (transport != null) {
            CUSTOM.remove(transport);
        }
    }

    public static Transport trapDoorTransport(WorldPoint source, WorldPoint destination, int closedId, int openedId) {
        return build(source, destination, Transport.DEFAULT_RADIUS, null, "trapdoor",
                () -> TransportActions.trapDoor(source, Transport.DEFAULT_RADIUS, closedId, openedId));
    }

    public static Transport itemUseAndObjectTransport(WorldPoint source, WorldPoint destination,
                                                      int beforeItem, int afterItem, int itemId) {
        return build(source, destination, Transport.DEFAULT_RADIUS, null, "item-use-state",
                () -> TransportActions.itemUseStateChange(source, Transport.DEFAULT_RADIUS, beforeItem, afterItem, itemId));
    }

    public static Transport itemUseTransport(WorldPoint source, WorldPoint destination, int itemId, int objId, int radius) {
        return build(source, destination, radius, null, "item-use",
                () -> TransportActions.useItemOnObject(source, radius, itemId, objId));
    }

    public static Transport itemUseTransport(WorldPoint source, WorldPoint destination, int itemId, int objId) {
        return itemUseTransport(source, destination, itemId, objId, Transport.DEFAULT_RADIUS);
    }

    public static Transport itemWearTransport(int radius, WorldPoint source, WorldPoint destination,
                                              int objId, String actions, int... itemIds) {
        return build(source, destination, radius, null, "item-wear",
                () -> TransportActions.wearThenObject(source, radius, objId, actions, itemIds));
    }

    public static Transport itemWearTransport(WorldPoint source, WorldPoint destination,
                                              int objId, String actions, int... itemIds) {
        return itemWearTransport(Transport.DEFAULT_RADIUS, source, destination, objId, actions, itemIds);
    }

    public static Transport npcTransport(int radius, WorldPoint source, WorldPoint destination,
                                         int npcId, Requirements requirements, String... actions) {
        return build(source, destination, radius, requirements, "npc",
                () -> TransportActions.interactNpc(source, radius, npcId, null, actions));
    }

    public static Transport npcTransport(int radius, WorldPoint source, WorldPoint destination,
                                         String npcName, Requirements requirements, String... actions) {
        return build(source, destination, radius, requirements, "npc",
                () -> TransportActions.interactNpc(source, radius, -1, npcName, actions));
    }

    public static Transport npcTransport(WorldPoint source, WorldPoint destination, String npcName, String... actions) {
        return npcTransport(Transport.DEFAULT_RADIUS, source, destination, npcName, null, actions);
    }

    public static Transport npcTransport(WorldPoint source, WorldPoint destination, int npcId,
                                         Requirements requirements, String... actions) {
        return npcTransport(Transport.DEFAULT_RADIUS, source, destination, npcId, requirements, actions);
    }

    public static Transport npcTransport(WorldPoint source, WorldPoint destination, int npcId, String... actions) {
        return npcTransport(Transport.DEFAULT_RADIUS, source, destination, npcId, null, actions);
    }

    public static Transport npcTransport(int radius, WorldPoint source, WorldPoint destination,
                                         int npcId, String... actions) {
        return npcTransport(radius, source, destination, npcId, null, actions);
    }

    public static Transport npcDialogTransport(int radius, WorldPoint source, WorldPoint destination,
                                               int npcId, Requirements requirements, String... chatOptions) {
        return build(source, destination, radius, requirements, "npc-dialog",
                () -> TransportActions.dialogOrTalk(source, radius, npcId, null, "Talk-to", chatOptions));
    }

    public static Transport npcDialogTransport(int radius, WorldPoint source, WorldPoint destination,
                                               int npcId, String... chatOptions) {
        return npcDialogTransport(radius, source, destination, npcId, null, chatOptions);
    }

    public static Transport npcDialogTransport(WorldPoint source, WorldPoint destination, int npcId,
                                               Requirements requirements, String... chatOptions) {
        return npcDialogTransport(Transport.DEFAULT_RADIUS, source, destination, npcId, requirements, chatOptions);
    }

    public static Transport npcDialogTransport(WorldPoint source, WorldPoint destination, int npcId, String... chatOptions) {
        return npcDialogTransport(Transport.DEFAULT_RADIUS, source, destination, npcId, null, chatOptions);
    }

    public static Transport objectTransport(WorldPoint source, WorldPoint destination, int objId, String actions) {
        return objectTransport(Transport.DEFAULT_RADIUS, source, destination, objId, actions, null);
    }

    public static Transport objectTransport(int radius, WorldPoint source, WorldPoint destination,
                                            int objId, String actions, Requirements requirements) {
        return build(source, destination, radius, requirements, "object",
                () -> TransportActions.interactObject(source, radius, objId, actions));
    }

    public static Transport objectDialogTransport(WorldPoint source, WorldPoint destination, int objId,
                                                  String action, Requirements requirements, String... chatOptions) {
        return build(source, destination, Transport.DEFAULT_RADIUS, requirements, "object-dialog",
                () -> TransportActions.dialogOrObject(source, Transport.DEFAULT_RADIUS, objId, action, chatOptions));
    }

    public static Transport objectDialogTransport(WorldPoint source, WorldPoint destination, int objId,
                                                  String action, String... chatOptions) {
        return objectDialogTransport(source, destination, objId, action, null, chatOptions);
    }

    public static Transport objectTransport(WorldPoint source, WorldPoint destination, int objId,
                                            String actions, Requirements requirements) {
        return objectTransport(Transport.DEFAULT_RADIUS, source, destination, objId, actions, requirements);
    }

    public static Transport objectTransport(int radius, WorldPoint source, WorldPoint destination,
                                            int objId, String actions) {
        return objectTransport(radius, source, destination, objId, actions, null);
    }

    public static Transport slashWebTransport(WorldPoint source, WorldPoint destination) {
        return build(source, destination, Transport.DEFAULT_RADIUS, null, "web",
                () -> TransportActions.slashWeb(source, Transport.DEFAULT_RADIUS));
    }

    private static Transport build(WorldPoint source, WorldPoint destination, int radius,
                                   Requirements requirements, String name, Callable<Boolean> action) {
        return build(source, destination, radius, requirements, name, action, 1);
    }

    private static Transport build(WorldPoint source, WorldPoint destination, int radius,
                                   Requirements requirements, String name, Callable<Boolean> action, int weight) {
        Callable<Boolean> handler = () -> {
            if (!PathfinderRequirements.met(requirements)) {
                return false;
            }
            return Boolean.TRUE.equals(action.call());
        };
        return Transport.builder()
                .source(source)
                .destination(destination)
                .radius(radius)
                .requirements(requirements)
                .name(name)
                .weight(Math.max(1, weight))
                .handler(handler)
                .build();
    }
}
