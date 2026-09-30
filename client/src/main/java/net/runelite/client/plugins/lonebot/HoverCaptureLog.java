package net.runelite.client.plugins.lonebot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sessie-log voor Capture-tab (Get walls, coords, …) — gescheiden van Console.
 */
public final class HoverCaptureLog {

    public static final class Entry {
        public final long capturedAtMs;
        public final String kind;
        public final String line;
        public final String detail;

        Entry(long capturedAtMs, String kind, String line, String detail) {
            this.capturedAtMs = capturedAtMs;
            this.kind = kind != null ? kind : "tile";
            this.line = line != null ? line : "";
            this.detail = detail != null ? detail : line;
        }
    }

    private static final List<Entry> ENTRIES = Collections.synchronizedList(new ArrayList<>());
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private HoverCaptureLog() {
    }

    public static void addListener(Runnable onChange) {
        if (onChange != null) {
            LISTENERS.add(onChange);
        }
    }

    public static void removeListener(Runnable onChange) {
        LISTENERS.remove(onChange);
    }

    public static List<Entry> snapshot() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES);
        }
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static boolean wouldSkipDuplicate(String kind, String line, String detail) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        synchronized (ENTRIES) {
            if (ENTRIES.isEmpty()) {
                return false;
            }
            Entry last = ENTRIES.get(ENTRIES.size() - 1);
            if (kind != null && detail != null && !detail.isEmpty()
                    && ("widget".equals(kind) || "coords".equals(kind) || "walls".equals(kind)
                    || "npc".equals(kind) || "object".equals(kind) || "ground".equals(kind)
                    || "tile".equals(kind) || "player".equals(kind) || "menu".equals(kind)
                    || "collision".equals(kind) || "iface".equals(kind) || "var".equals(kind))) {
                return detail.equals(last.detail);
            }
            return line.equals(last.line);
        }
    }

    public static Entry add(String kind, String line, String detail) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        Entry e = new Entry(System.currentTimeMillis(), kind, line.trim(), detail);
        ENTRIES.add(e);
        notifyListeners();
        return e;
    }

    public static void clear() {
        ENTRIES.clear();
        notifyListeners();
    }

    public static String formatForDisplay() {
        List<Entry> snap = snapshot();
        if (snap.isEmpty()) {
            return "(Nog geen entries — Get walls / rechtsklik Capture / middenklik.)";
        }
        StringBuilder sb = new StringBuilder(snap.size() * 64);
        int i = 1;
        for (Entry e : snap) {
            String time = TIME_FMT.format(Instant.ofEpochMilli(e.capturedAtMs));
            if (prettyDetail(e)) {
                sb.append(String.format("#%d [%s]  (%s)%n", i++, e.kind, time));
                for (String dl : e.detail.split("\n")) {
                    if (!dl.trim().isEmpty()) {
                        sb.append("   ").append(dl.trim()).append('\n');
                    }
                }
            } else {
                sb.append(String.format("#%d [%s] %s  (%s)%n", i++, e.kind, e.line, time));
            }
        }
        return sb.toString().trim();
    }

    public static String formatForCopyAll() {
        List<Entry> snap = snapshot();
        if (snap.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(128 + snap.size() * 80);
        sb.append("# LoneBot capture").append(System.lineSeparator());
        for (int i = 0; i < snap.size(); i++) {
            Entry e = snap.get(i);
            if (prettyDetail(e)) {
                sb.append(i + 1).append(". [").append(e.kind).append(']').append(System.lineSeparator());
                sb.append(e.detail).append(System.lineSeparator());
            } else {
                sb.append(i + 1).append(". [").append(e.kind).append("] ")
                        .append(e.line).append(System.lineSeparator());
                if (e.detail != null && !e.detail.equals(e.line)) {
                    for (String dl : e.detail.split("\n")) {
                        if (!dl.trim().isEmpty()) {
                            sb.append("   ").append(dl.trim()).append(System.lineSeparator());
                        }
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private static boolean prettyDetail(Entry e) {
        if (e == null || e.detail == null || !e.detail.contains("\n")) {
            return false;
        }
        switch (e.kind) {
            case "widget":
            case "coords":
            case "walls":
            case "npc":
            case "object":
            case "ground":
            case "tile":
            case "miss":
            case "player":
            case "menu":
            case "collision":
            case "iface":
            case "var":
                return true;
            default:
                return false;
        }
    }

    private static void notifyListeners() {
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
