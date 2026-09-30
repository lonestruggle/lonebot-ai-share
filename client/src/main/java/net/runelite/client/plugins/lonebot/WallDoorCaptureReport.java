package net.runelite.client.plugins.lonebot;

import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.walls.RoomScan;
import net.storm.sdk.walls.RoomScanner;

/**
 * Volledige Get walls-report (CombatBot-stijl) voor Capture-console / scripts.
 */
public final class WallDoorCaptureReport {

    private static final int OUTSIDE_TALK_RADIUS = 15;

    public static final class Result {
        public final String kind;
        public final String line;
        public final String detail;
        public final String preview;

        public Result(String kind, String line, String detail, String preview) {
            this.kind = kind;
            this.line = line;
            this.detail = detail;
            this.preview = preview;
        }
    }

    private WallDoorCaptureReport() {
    }

    public static Result format(RoomScan scan) {
        return format(scan, null);
    }

    public static Result format(RoomScan scan, net.runelite.api.Client client) {
        if (scan == null || scan.center == null) {
            return null;
        }
        RoomScan.RoomKind room = RoomScanner.classify(scan);
        String coords = scan.center.getX() + "," + scan.center.getY() + "," + scan.center.getPlane();
        String roomLabel = room == RoomScan.RoomKind.OPEN ? "open" : "afgesloten";

        String line;
        if (scan.isNpcScan()) {
            line = "Walls/doors NPC " + scan.npcName + " (id=" + scan.npcId + ") @ " + coords
                    + " — " + roomLabel
                    + (scan.doors.isEmpty() ? "" : ", " + scan.doors.size() + " deur(en)");
        } else {
            line = "Walls/doors @ " + coords + " (r=" + scan.radius + ", " + roomLabel + ")";
        }

        StringBuilder detail = new StringBuilder(220);
        detail.append("kind: walls\n");
        if (scan.isNpcScan()) {
            detail.append("npc: ").append(scan.npcName).append(" (id=").append(scan.npcId).append(")\n");
            detail.append("modus: flood-fill kamer rond NPC tot r=").append(scan.radius)
                    .append(" (stopt bij deuren)\n");
        } else {
            detail.append("modus: flood-fill kamer vanaf geklikte tegel tot r=").append(scan.radius)
                    .append(" (stopt bij deuren)\n");
        }
        if (!scan.roomTiles.isEmpty()) {
            detail.append("kamer-tegels: ").append(scan.roomTiles.size()).append('\n');
        }
        detail.append("ruimte: ").append(roomLabel);
        if (room == RoomScan.RoomKind.OPEN) {
            detail.append(" (geen deuren — talk ≤15 tiles/LOS, geen talk-zone)\n");
        } else {
            detail.append(" (talk-zone; in zone = klaar om te praten)\n");
        }
        detail.append("center: ").append(coords.replace(",", ", ")).append('\n');
        detail.append("radius: ").append(scan.radius).append('\n');
        if (scan.bboxMin != null && scan.bboxMax != null) {
            int w = scan.bboxMax.getX() - scan.bboxMin.getX() + 1;
            int h = scan.bboxMax.getY() - scan.bboxMin.getY() + 1;
            detail.append("walkable bbox: ")
                    .append(scan.bboxMin.getX()).append(',').append(scan.bboxMin.getY())
                    .append(" → ")
                    .append(scan.bboxMax.getX()).append(',').append(scan.bboxMax.getY())
                    .append(" (").append(w).append('×').append(h).append(")\n");
        }
        if (room == RoomScan.RoomKind.ENCLOSED && !scan.cornerHints.isEmpty()) {
            detail.append("talk-zone (4 hoeken — geen waypoints):\n");
            for (WorldPoint c : scan.cornerHints) {
                detail.append("  WorldPoint(").append(c.getX()).append(", ")
                        .append(c.getY()).append(", ").append(c.getPlane()).append(")\n");
            }
        }
        if (!scan.doors.isEmpty()) {
            detail.append("deur (fallback Open — geen waypoint):\n");
            for (RoomScan.DoorMark d : scan.doors) {
                detail.append("  WorldPoint(").append(d.tile.getX()).append(", ")
                        .append(d.tile.getY()).append(", ").append(d.tile.getPlane()).append(")");
                detail.append(" — ").append(d.name);
                if (!d.state.isEmpty()) {
                    detail.append(" (").append(d.state).append(')');
                }
                detail.append('\n');
            }
        }
        if (scan.isNpcScan() && !scan.talkFromOutsideTiles.isEmpty()) {
            detail.append("talk-buiten (≤").append(OUTSIDE_TALK_RADIUS)
                    .append(" tiles, buiten kamer):\n");
            int logged = 0;
            for (WorldPoint t : scan.talkFromOutsideTiles) {
                if (logged >= 24) {
                    detail.append("  … +").append(scan.talkFromOutsideTiles.size() - logged).append(" meer\n");
                    break;
                }
                detail.append("  WorldPoint(").append(t.getX()).append(", ")
                        .append(t.getY()).append(", ").append(t.getPlane()).append(")\n");
                logged++;
            }
        }
        if (scan.wallObjectTiles != null && !scan.wallObjectTiles.isEmpty()) {
            detail.append("wall-objects (Highlight): ").append(scan.wallObjectTiles.size()).append('\n');
        }
        if (!scan.wallEdges.isEmpty()) {
            detail.append("muur-randen overlay: ").append(scan.wallEdges.size());
            if (room == RoomScan.RoomKind.OPEN) {
                detail.append(" (scan-rand, niet gelogd — te veel ruis)\n");
            } else if (scan.wallEdges.size() <= 12) {
                detail.append(" (niet voor bot):\n");
                for (RoomScan.WallEdgeMark w : scan.wallEdges) {
                    detail.append("  ").append(w.edge).append(" @ ")
                            .append(w.tile.getX()).append(',').append(w.tile.getY()).append('\n');
                }
            } else {
                detail.append(" (niet voor bot, lijst weggelaten)\n");
            }
        }

        if (client != null && scan.center != null) {
            String col = net.runelite.client.plugins.lonebot.dev.DevInspectCapture.collisionBlock(client, scan.center);
            if (col != null && !col.isBlank()) {
                detail.append('\n').append(col);
            }
        }

        StringBuilder preview = new StringBuilder(160);
        if (scan.isNpcScan()) {
            preview.append("NPC ").append(scan.npcName).append(" — ").append(roomLabel).append('\n');
            preview.append("   @ ").append(coords.replace(",", ", "));
        } else {
            preview.append("Walls/doors — ").append(roomLabel).append('\n');
            preview.append("   @ ").append(coords.replace(",", ", "));
        }
        if (room == RoomScan.RoomKind.ENCLOSED && !scan.cornerHints.isEmpty()) {
            preview.append('\n').append("   talk-zone: ");
            for (int i = 0; i < scan.cornerHints.size(); i++) {
                WorldPoint c = scan.cornerHints.get(i);
                if (i > 0) {
                    preview.append(" | ");
                }
                preview.append(c.getX()).append(',').append(c.getY());
            }
        }
        if (!scan.doors.isEmpty()) {
            preview.append('\n').append("   deur: ")
                    .append(scan.doors.get(0).tile.getX()).append(',')
                    .append(scan.doors.get(0).tile.getY());
        }
        if (scan.isNpcScan() && !scan.talkFromOutsideTiles.isEmpty()) {
            preview.append('\n').append("   talk-buiten: ").append(scan.talkFromOutsideTiles.size()).append(" tegel(s)");
        }

        return new Result("walls", line, detail.toString().trim(), preview.toString());
    }

    public static Result formatCoords(WorldPoint tile, boolean player) {
        if (tile == null) {
            return null;
        }
        String who = player ? "Player" : "Tile";
        String line = who + " @ " + tile.getX() + "," + tile.getY() + "," + tile.getPlane();
        String detail = "kind: coords\n"
                + who + "\n"
                + tile.getX() + ", " + tile.getY() + ", " + tile.getPlane() + "\n"
                + "WorldPoint(" + tile.getX() + ", " + tile.getY() + ", " + tile.getPlane() + ")";
        return new Result("coords", line, detail, line);
    }
}
