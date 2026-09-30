package net.storm.api.movement.pathfinder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Pre-computed world collision map (Storm {@code /regions} format).
 */
public final class GlobalCollisionMap implements CollisionMap {

    public final BitSet4D[] regions = new BitSet4D[256 * 256];

    public GlobalCollisionMap() {
    }

    public GlobalCollisionMap(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            int region = buffer.getShort() & 0xffff;
            regions[region] = new BitSet4D(buffer, 64, 64, 4, 2);
        }
    }

    /** Load gzipped {@code /regions} from the classpath (Storm-compatible). */
    public static GlobalCollisionMap loadFromClasspath() {
        try (InputStream is = GlobalCollisionMap.class.getResourceAsStream("/regions")) {
            if (is == null) {
                return new GlobalCollisionMap();
            }
            byte[] gzipped = readAll(is);
            byte[] raw = gunzip(gzipped);
            return new GlobalCollisionMap(raw);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load /regions collision map", e);
        }
    }

    public BitSet4D getRegion(int x, int y) {
        int regionId = x / 64 * 256 + y / 64;
        return regions[regionId];
    }

    public boolean get(int x, int y, int z, int w) {
        BitSet4D region = getRegion(x, y);
        if (region == null) {
            return false;
        }
        return region.get(x % 64, y % 64, z, w);
    }

    public void set(int x, int y, int z, int w, boolean value) {
        BitSet4D region = getRegion(x, y);
        if (region == null) {
            return;
        }
        region.set(x % 64, y % 64, z, w, value);
    }

    public int loadedRegionCount() {
        return (int) Arrays.stream(regions).filter(Objects::nonNull).count();
    }

    @Override
    public boolean n(int x, int y, int z) {
        return get(x, y, z, 0);
    }

    @Override
    public boolean e(int x, int y, int z) {
        return get(x, y, z, 1);
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static byte[] gunzip(byte[] gzipped) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = gis.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }
}
