package net.storm.sdk.walls;

/**
 * Laatste muren/deuren-scan voor in-game overlay (Get walls).
 */
public final class WallDoorCaptureState {

    private static volatile RoomScan latest;

    private WallDoorCaptureState() {
    }

    public static void setLatest(RoomScan result) {
        latest = result;
    }

    public static RoomScan getLatest() {
        return latest;
    }

    public static void clear() {
        latest = null;
    }
}
