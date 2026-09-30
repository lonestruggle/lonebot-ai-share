package net.runelite.client.plugins.lonebot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Statische OSRS-wereldcatalogus voor de launcher (geen live client nodig).
 * Regio’s: UK / DE / US / AU — vlaggen via {@link RegionFlagIcons}.
 */
public final class OsrsWorldCatalog {

    public enum Kind {
        F2P,
        MEMBERS
    }

    public static final class Entry {
        public final int id;
        public final Kind kind;
        public final String activity;
        public final boolean pvp;
        public final boolean highRisk;
        public final boolean skillTotal;
        public final boolean deadman;
        /** UK | DE | US | AU */
        public final String region;

        public Entry(int id, Kind kind, String activity, String region,
                     boolean pvp, boolean highRisk, boolean skillTotal, boolean deadman) {
            this.id = id;
            this.kind = kind;
            this.activity = activity != null ? activity : "";
            this.region = region != null ? region : "UK";
            this.pvp = pvp;
            this.highRisk = highRisk;
            this.skillTotal = skillTotal;
            this.deadman = deadman;
        }

        public String flagEmoji() {
            // Alleen tekst-fallback; UI gebruikt {@link RegionFlagIcons}.
            return "[" + region.toUpperCase(Locale.ROOT) + "]";
        }

        public String regionLabel() {
            switch (region.toUpperCase(Locale.ROOT)) {
                case "DE":
                    return "Germany";
                case "US":
                    return "United States";
                case "AU":
                    return "Australia";
                case "UK":
                default:
                    return "United Kingdom";
            }
        }

        public String typeLabel() {
            StringBuilder sb = new StringBuilder(kind == Kind.F2P ? "F2P" : "Members");
            if (pvp) {
                sb.append(" · PVP");
            }
            if (highRisk) {
                sb.append(" · High Risk");
            }
            if (skillTotal) {
                sb.append(" · Skill Total");
            }
            if (deadman) {
                sb.append(" · Deadman");
            }
            sb.append(" · ").append(region);
            return sb.toString();
        }

        /** Rij-tekst zonder emoji (vlag = Icon in de UI). */
        public String listLabel() {
            return id + " — " + typeLabel()
                    + (activity.isEmpty() ? "" : " — " + activity);
        }

        public boolean matchesFilters(boolean showF2p, boolean showMembers,
                                      boolean hidePvp, boolean hideHighRisk,
                                      boolean hideSkillTotal, boolean hideDeadman,
                                      boolean showUk, boolean showDe, boolean showUs, boolean showAu) {
            if (kind == Kind.F2P && !showF2p) {
                return false;
            }
            if (kind == Kind.MEMBERS && !showMembers) {
                return false;
            }
            if (hidePvp && pvp) {
                return false;
            }
            if (hideHighRisk && highRisk) {
                return false;
            }
            if (hideSkillTotal && skillTotal) {
                return false;
            }
            if (hideDeadman && deadman) {
                return false;
            }
            String r = region.toUpperCase(Locale.ROOT);
            if ("UK".equals(r) && !showUk) {
                return false;
            }
            if ("DE".equals(r) && !showDe) {
                return false;
            }
            if ("US".equals(r) && !showUs) {
                return false;
            }
            if ("AU".equals(r) && !showAu) {
                return false;
            }
            return true;
        }
    }

    private static final List<Entry> ALL = build();

    private OsrsWorldCatalog() {
    }

    public static List<Entry> all() {
        return ALL;
    }

    public static Entry find(int id) {
        for (Entry e : ALL) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    public static List<Entry> filter(boolean showF2p, boolean showMembers,
                                     boolean hidePvp, boolean hideHighRisk,
                                     boolean hideSkillTotal, boolean hideDeadman,
                                     boolean showUk, boolean showDe, boolean showUs, boolean showAu,
                                     String search) {
        String q = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (!e.matchesFilters(showF2p, showMembers, hidePvp, hideHighRisk, hideSkillTotal, hideDeadman,
                    showUk, showDe, showUs, showAu)) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = (e.id + " " + e.activity + " " + e.typeLabel() + " " + e.regionLabel()
                        + " " + e.region).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) {
                    continue;
                }
            }
            out.add(e);
        }
        return out;
    }

    /** @deprecated gebruik filter met regio-flags */
    @Deprecated
    public static List<Entry> filter(boolean showF2p, boolean showMembers,
                                     boolean hidePvp, boolean hideHighRisk,
                                     boolean hideSkillTotal, boolean hideDeadman,
                                     String search) {
        return filter(showF2p, showMembers, hidePvp, hideHighRisk, hideSkillTotal, hideDeadman,
                true, true, true, true, search);
    }

    private static List<Entry> build() {
        List<Entry> list = new ArrayList<>();

        // ——— F2P UK (slu / wiki) ———
        addF(list, 308, "Wilderness PK - Free", "UK", true, false, false);
        addF(list, 316, "Wilderness PK - Free", "UK", true, false, false);
        addF(list, 326, "LMS Casual", "UK", false, false, false);
        addF(list, 372, "750 skill total", "UK", false, false, true);
        addF(list, 379, "PvP Arena (Legacy Duels)", "UK", true, false, false);
        addF(list, 380, "Trade / general", "UK", false, false, false);
        addF(list, 381, "500 skill total", "UK", false, false, true);
        addF(list, 382, "Trade / general", "UK", false, false, false);
        addF(list, 497, "Clan Recruitment", "UK", false, false, false);
        addF(list, 498, "Trade / general", "UK", false, false, false);
        addF(list, 499, "Trade / general", "UK", false, false, false);

        // ——— F2P Germany ———
        addF(list, 335, "Trade / general", "DE", false, false, false);
        addF(list, 383, "Castle Wars - Free", "DE", false, false, false);
        addF(list, 384, "Trade / general", "DE", false, false, false);
        addF(list, 397, "Trade / general", "DE", false, false, false);
        addF(list, 398, "Forestry", "DE", false, false, false);
        addF(list, 399, "Trade / general", "DE", false, false, false);
        addF(list, 413, "500 skill total", "DE", false, false, true);
        addF(list, 414, "750 skill total", "DE", false, false, true);
        addF(list, 451, "Trade / general", "DE", false, false, false);
        addF(list, 452, "Trade / general", "DE", false, false, false);
        addF(list, 453, "Trade / general", "DE", false, false, false);
        addF(list, 454, "Trade / general", "DE", false, false, false);
        addF(list, 455, "Trade / general", "DE", false, false, false);
        addF(list, 456, "Trade / general", "DE", false, false, false);
        addF(list, 552, "Trade / general", "DE", false, false, false);
        addF(list, 553, "Trade / general", "DE", false, false, false);
        addF(list, 554, "Trade / general", "DE", false, false, false);
        addF(list, 555, "Trade / general", "DE", false, false, false);

        // ——— F2P US ———
        addF(list, 301, "Trade - Free", "US", false, false, false);
        addF(list, 393, "750 skill total", "US", false, false, true);
        addF(list, 417, "Trade / general", "US", false, false, false);
        addF(list, 418, "Trade / general", "US", false, false, false);
        addF(list, 419, "500 skill total", "US", false, false, true);
        addF(list, 430, "Trade / general", "US", false, false, false);
        addF(list, 431, "Trade / general", "US", false, false, false);
        addF(list, 432, "750 skill total", "US", false, false, true);
        addF(list, 433, "Trade / general", "US", false, false, false);
        addF(list, 434, "Forestry", "US", false, false, false);
        addF(list, 435, "Trade / general", "US", false, false, false);
        addF(list, 436, "Trade / general", "US", false, false, false);
        addF(list, 437, "Trade / general", "US", false, false, false);
        addF(list, 468, "500 skill total", "US", false, false, true);
        addF(list, 469, "LMS Casual", "US", false, false, false);
        addF(list, 483, "PvP Arena (Legacy Duels)", "US", true, false, false);

        // ——— Members sample (UK / DE / US / AU) ———
        addM(list, 302, "Trade - Varrock", "UK");
        addM(list, 303, "Trade", "DE");
        addM(list, 304, "Trouble Brewing", "DE");
        addM(list, 305, "Falador Party Room", "US");
        addM(list, 306, "Barbarian Assault", "US");
        addM(list, 307, "Wintertodt", "US");
        addM(list, 309, "Wintertodt", "UK");
        addM(list, 310, "Barbarian Assault", "UK");
        addM(list, 311, "Wintertodt", "DE");
        addM(list, 312, "Group Skilling", "DE");
        addM(list, 327, "Ourania Altar", "DE");
        addM(list, 328, "Royal Titans", "DE");
        addM(list, 331, "Trade", "US");
        addM(list, 332, "Trade", "US");
        addM(list, 333, "Trade", "AU");
        addM(list, 334, "Castle Wars", "AU");
        addM(list, 336, "ToA FFA", "DE");
        list.add(new Entry(318, Kind.MEMBERS, "PvP World", "UK", true, false, false, false));
        list.add(new Entry(319, Kind.MEMBERS, "High Risk PvP", "UK", true, true, false, false));
        list.add(new Entry(345, Kind.MEMBERS, "1250 skill total", "UK", false, false, true, false));
        list.add(new Entry(346, Kind.MEMBERS, "1500 skill total", "UK", false, false, true, false));
        list.add(new Entry(353, Kind.MEMBERS, "1750 skill total", "UK", false, false, true, false));
        list.add(new Entry(366, Kind.MEMBERS, "2000 skill total", "UK", false, false, true, false));

        for (int id = 370; id <= 378; id++) {
            if (findIn(list, id) == null) {
                addF(list, id, "Trade / general", "UK", false, false, false);
            }
        }
        for (int id = 385; id <= 390; id++) {
            if (findIn(list, id) == null) {
                addF(list, id, "Trade / general", "UK", false, false, false);
            }
        }

        list.sort((a, b) -> Integer.compare(a.id, b.id));
        return Collections.unmodifiableList(list);
    }

    private static void addF(List<Entry> list, int id, String activity, String region,
                             boolean pvp, boolean highRisk, boolean skillTotal) {
        if (findIn(list, id) != null) {
            return;
        }
        list.add(new Entry(id, Kind.F2P, activity, region, pvp, highRisk, skillTotal, false));
    }

    private static void addM(List<Entry> list, int id, String activity, String region) {
        if (findIn(list, id) != null) {
            return;
        }
        list.add(new Entry(id, Kind.MEMBERS, activity, region, false, false, false, false));
    }

    private static Entry findIn(List<Entry> list, int id) {
        for (Entry e : list) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }
}
