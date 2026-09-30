package net.storm.sdk.walls;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultaat van een muren/deuren-scan (CombatBot Get walls).
 */
public final class RoomScan {

    public static final class DoorMark {
        public final WorldPoint tile;
        public final String name;
        public final String state;

        public DoorMark(WorldPoint tile, String name, String state) {
            this.tile = tile;
            this.name = name != null ? name : "Door";
            this.state = state != null ? state : "";
        }
    }

    public static final class WallEdgeMark {
        public final WorldPoint tile;
        /** north | east | south | west */
        public final String edge;

        public WallEdgeMark(WorldPoint tile, String edge) {
            this.tile = tile;
            this.edge = edge;
        }
    }

    public enum RoomKind {
        OPEN,
        ENCLOSED
    }

    public final long scannedAtMs;
    public final WorldPoint center;
    public final int radius;
    public final List<DoorMark> doors;
    public final List<WallEdgeMark> wallEdges;
    /** Tegels met een scene-WallObject (zelfde bron als Highlight Wall Objects). */
    public final List<WorldPoint> wallObjectTiles;
    public final List<WorldPoint> cornerHints;
    public final List<WorldPoint> roomTiles;
    public final List<WorldPoint> talkFromOutsideTiles;
    public final WorldPoint bboxMin;
    public final WorldPoint bboxMax;
    public final String npcName;
    public final int npcId;

    public RoomScan(long scannedAtMs, WorldPoint center, int radius,
                    List<DoorMark> doors, List<WallEdgeMark> wallEdges,
                    List<WorldPoint> wallObjectTiles,
                    List<WorldPoint> cornerHints, List<WorldPoint> roomTiles,
                    List<WorldPoint> talkFromOutsideTiles,
                    WorldPoint bboxMin, WorldPoint bboxMax,
                    String npcName, int npcId) {
        this.scannedAtMs = scannedAtMs;
        this.center = center;
        this.radius = radius;
        this.doors = immutableCopy(doors);
        this.wallEdges = immutableCopy(wallEdges);
        this.wallObjectTiles = immutableCopyPoints(wallObjectTiles);
        this.cornerHints = immutableCopyPoints(cornerHints);
        this.roomTiles = immutableCopyPoints(roomTiles);
        this.talkFromOutsideTiles = immutableCopyPoints(talkFromOutsideTiles);
        this.bboxMin = bboxMin;
        this.bboxMax = bboxMax;
        this.npcName = npcName;
        this.npcId = npcId;
    }

    public boolean isNpcScan() {
        return npcName != null && !npcName.isEmpty();
    }

    private static <T> List<T> immutableCopy(List<T> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    private static List<WorldPoint> immutableCopyPoints(List<WorldPoint> src) {
        return immutableCopy(src);
    }
}
