package com.lonebot.example.fishing;

import net.runelite.api.coords.WorldPoint;

/**
 * CombatBot {@code FishingConfig} — methodes, visdata, locatie-profielen.
 */
final class FishingConfig {

    // {Spot naam, actie, level, tool, bait}
    static final String[][] FISHING_METHODS = {
            {"Fishing spot", "Net", "1", "Small fishing net", ""},
            {"Fishing spot", "Bait", "5", "Fishing rod", "Fishing bait"},
            {"Fishing spot", "Lure", "20", "Fly fishing rod", "Feather"},
            {"Cage/Harpoon fishing spot", "Cage", "40", "Lobster pot", ""},
            {"Cage/Harpoon fishing spot", "Harpoon", "35", "Harpoon", ""},
            {"Net/Harpoon fishing spot", "Net", "62", "Small fishing net", ""},
            {"Net/Harpoon fishing spot", "Harpoon", "76", "Harpoon", ""},
            {"Rod Fishing spot", "Use-rod", "48", "Barbarian rod", "Fishing bait"},
            {"Fishing spot", "Use-rod", "65", "Fishing rod", "Fishing bait"}
    };

    // {Raw vis, cooked, cooking level, fishing level}
    static final String[][] FISH_DATA = {
            {"Raw shrimps", "Shrimps", "1", "1"},
            {"Raw sardine", "Sardine", "1", "5"},
            {"Raw herring", "Herring", "5", "10"},
            {"Raw anchovies", "Anchovies", "1", "15"},
            {"Raw trout", "Trout", "15", "20"},
            {"Raw pike", "Pike", "20", "25"},
            {"Raw salmon", "Salmon", "25", "30"},
            {"Raw tuna", "Tuna", "30", "35"},
            {"Raw lobster", "Lobster", "40", "40"},
            {"Raw swordfish", "Swordfish", "45", "50"},
            {"Raw monkfish", "Monkfish", "62", "62"},
            {"Raw shark", "Shark", "80", "76"},
            {"Raw anglerfish", "Anglerfish", "84", "82"},
            {"Raw dark crab", "Dark crab", "90", "85"}
    };

    static final int SMALL_NET_ID = 303;
    static final int FISHING_ROD_ID = 307;
    static final int FLY_ROD_ID = 309;
    static final int BARBARIAN_ROD_ID = 11323;
    static final int HARPOON_ID = 311;
    static final int LOBSTER_POT_ID = 301;
    static final int BAIT_ID = 313;
    static final int FEATHER_ID = 314;
    static final int COINS_ID = 995;

    private static final int BARBARIAN_X = 3109;
    private static final int BARBARIAN_Y = 3434;
    private static final int BARBARIAN_R = 18;
    private static final int DRAYNOR_X = 3090;
    private static final int DRAYNOR_Y = 3230;
    private static final int DRAYNOR_R = 28;

    static final int MIN_FISHING_LEVEL_PREFER_BARBARIAN_OVER_DRAYNOR = 20;

    private FishingConfig() {
    }

    static boolean isBarbarianFishingLocation(int x, int y) {
        return Math.abs(x - BARBARIAN_X) <= BARBARIAN_R && Math.abs(y - BARBARIAN_Y) <= BARBARIAN_R;
    }

    static WorldPoint getBarbarianRiverAnchor() {
        return new WorldPoint(BARBARIAN_X, BARBARIAN_Y, 0);
    }

    /** Land, noordkant rivier — vanaf Edgeville, niet het dorpsmidden tussen huizen. */
    static WorldPoint getBarbarianNorthApproach() {
        return new WorldPoint(3102, 3442, 0);
    }

    /** Zuid van Draynor-bank, bij het water — niet de banktegel zelf. */
    static WorldPoint getDraynorShoreAnchor() {
        return new WorldPoint(3087, 3228, 0);
    }

    static boolean isDraynorFishingLocation(int x, int y, String centerName) {
        if (centerName != null) {
            String n = centerName.toLowerCase();
            if (n.contains("draynor")) {
                return true;
            }
            if (n.contains("barbarian")) {
                return false;
            }
        }
        return Math.abs(x - DRAYNOR_X) <= DRAYNOR_R && Math.abs(y - DRAYNOR_Y) <= DRAYNOR_R;
    }

    static String[] getFlyLureMethod() {
        return new String[]{
                FISHING_METHODS[2][0], FISHING_METHODS[2][1],
                FISHING_METHODS[2][3], FISHING_METHODS[2][4]
        };
    }

    static String[] getBestMethodForLevelBarbarian(int fishingLevel) {
        return pickBest(fishingLevel, m -> {
            String spot = m[0];
            return !"Cage/Harpoon fishing spot".equals(spot) && !"Net/Harpoon fishing spot".equals(spot);
        });
    }

    static String[] getBestMethodForLevelNonBarbarian(int fishingLevel) {
        return pickBest(fishingLevel, m -> !"Lure".equals(m[1]) && !"Barbarian rod".equals(m[3]));
    }

    static String[] getBestMethodForLevelDraynor(int fishingLevel) {
        return pickBest(fishingLevel, m ->
                "Fishing spot".equals(m[0]) && ("Net".equals(m[1]) || "Bait".equals(m[1])));
    }

    static String[] getBestMethodForLevelEdgeFeatherOnly(int fishingLevel) {
        String[] best = null;
        int bestLevel = -1;
        for (String[] method : FISHING_METHODS) {
            if (!"Lure".equals(method[1]) || !"Feather".equalsIgnoreCase(method[4])) {
                continue;
            }
            int req = Integer.parseInt(method[2]);
            if (fishingLevel >= req && req >= bestLevel) {
                best = new String[]{method[0], method[1], method[3], method[4]};
                bestLevel = req;
            }
        }
        return best != null ? best : getBestMethodForLevelNonBarbarian(fishingLevel);
    }

    static String getToolForMethod(String spotName, String action) {
        for (String[] method : FISHING_METHODS) {
            if (method[0].equals(spotName) && method[1].equals(action)) {
                return method[3];
            }
        }
        return "Small fishing net";
    }

    static String getBaitForMethod(String spotName, String action) {
        for (String[] method : FISHING_METHODS) {
            if (method[0].equals(spotName) && method[1].equals(action)) {
                return method[4];
            }
        }
        return "";
    }

    static int getCookingLevelForFish(String rawFishName) {
        for (String[] data : FISH_DATA) {
            if (data[0].equalsIgnoreCase(rawFishName)) {
                return Integer.parseInt(data[2]);
            }
        }
        return -1;
    }

    static String getCookedName(String rawFishName) {
        for (String[] data : FISH_DATA) {
            if (data[0].equalsIgnoreCase(rawFishName)) {
                return data[1];
            }
        }
        return null;
    }

    static boolean canCook(String rawFishName, int cookingLevel) {
        int req = getCookingLevelForFish(rawFishName);
        return req >= 0 && cookingLevel >= req;
    }

    static int toolId(String toolName) {
        if (toolName == null) {
            return 0;
        }
        switch (toolName.toLowerCase()) {
            case "small fishing net":
                return SMALL_NET_ID;
            case "fishing rod":
                return FISHING_ROD_ID;
            case "fly fishing rod":
                return FLY_ROD_ID;
            case "barbarian rod":
                return BARBARIAN_ROD_ID;
            case "harpoon":
                return HARPOON_ID;
            case "lobster pot":
                return LOBSTER_POT_ID;
            default:
                return 0;
        }
    }

    static int baitId(String baitName) {
        if (baitName == null) {
            return 0;
        }
        if ("fishing bait".equalsIgnoreCase(baitName)) {
            return BAIT_ID;
        }
        if ("feather".equalsIgnoreCase(baitName)) {
            return FEATHER_ID;
        }
        return 0;
    }

    private interface MethodFilter {
        boolean allow(String[] method);
    }

    private static String[] pickBest(int fishingLevel, MethodFilter filter) {
        String[] best = {FISHING_METHODS[0][0], FISHING_METHODS[0][1], FISHING_METHODS[0][3], FISHING_METHODS[0][4]};
        int bestLevel = 1;
        for (String[] method : FISHING_METHODS) {
            if (!filter.allow(method)) {
                continue;
            }
            int req = Integer.parseInt(method[2]);
            if (fishingLevel >= req && req >= bestLevel) {
                best = new String[]{method[0], method[1], method[3], method[4]};
                bestLevel = req;
            }
        }
        return best;
    }
}
