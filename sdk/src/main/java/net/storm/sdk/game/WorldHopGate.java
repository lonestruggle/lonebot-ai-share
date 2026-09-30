package net.storm.sdk.game;

import net.runelite.api.World;
import net.runelite.api.WorldType;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mag deze account naar wereld X? Skill-total, PVP, DMM, enz. — reden of {@code null} = ok.
 */
public final class WorldHopGate {

    private static final Pattern SKILL_TOTAL_ACT =
            Pattern.compile("(\\d+)\\s*skill\\s*total", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAT_F2P_TOTAL =
            Pattern.compile("total of (\\d+) in non-member skills", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAT_TOTAL_LEVEL =
            Pattern.compile("total level of (\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAT_TOTAL_OF =
            Pattern.compile("need(?:ed)?'?d? a total of (\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * Dedicated PvP Arena-werelden (restricted, geen skilling).
     * Niet Legacy Duels (348/379/479/483/506/571) — daar mag je wel minen.
     */
    private static final Set<Integer> PVP_ARENA_RESTRICTED = Set.of(558, 570, 578);

    private static volatile int cacheTotal;
    private static volatile int cacheF2p;
    private static volatile long cacheMs;

    private WorldHopGate() {
    }

    /**
     * @param f2pOnly members-werelden blokkeren
     * @return korte reden, of {@code null} als hop mag
     */
    public static String blockReason(int worldId, boolean f2pOnly) {
        if (worldId <= 0) {
            return "ongeldige wereld";
        }
        if (f2pOnly) {
            // Cache blijft na hop actief — geen hopper opnieuw openen
            Worlds.refreshWorldTypeCacheFromLive();
            if (Worlds.isFreeToPlayWorld(worldId)) {
                // ok F2P
            } else if (Worlds.hasWorldTypeCache()) {
                return Worlds.isMembersWorld(worldId) ? "members" : "niet in hopper (members?)";
            } else {
                return "hopper-lijst leeg";
            }
        }
        World w = Worlds.get(worldId);
        if (w == null) {
            return null;
        }
        return blockReason(w);
    }

    public static String blockReason(World w) {
        if (w == null) {
            return null;
        }
        Set<WorldType> types;
        try {
            types = w.getTypes();
        } catch (Throwable t) {
            types = null;
        }
        String act = "";
        try {
            act = w.getActivity() != null ? w.getActivity() : "";
        } catch (Throwable ignored) {
        }
        int worldId = 0;
        try {
            worldId = w.getId();
        } catch (Throwable ignored) {
        }
        String typed = typeBlock(worldId, types, act);
        if (typed != null) {
            return typed;
        }
        int need = skillTotalNeed(types, act);
        if (need < 0) {
            return "skill-total (drempel onbekend)";
        }
        if (need > 0) {
            refreshTotals();
            boolean members = has(types, WorldType.MEMBERS);
            int have = members ? cacheTotal : cacheF2p;
            String kind = members ? "totaal" : "F2P-skills";
            if (have < need) {
                return need + " skill-total (" + kind + " " + have + " < " + need + ")";
            }
        }
        return null;
    }

    /** Game-chat na mislukte hop, of {@code null}. */
    public static String hopDeniedFromChat() {
        List<String> lines = Chat.getRecentMessages(4);
        if (lines == null) {
            return null;
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            String s = lines.get(i);
            if (s == null || s.isBlank()) {
                continue;
            }
            String low = s.toLowerCase(Locale.ROOT);
            if (!low.contains("log into") && !low.contains("log in to")) {
                continue;
            }
            Matcher m = CHAT_F2P_TOTAL.matcher(s);
            if (m.find()) {
                return m.group(1) + " skill-total (F2P-skills te laag — chat)";
            }
            m = CHAT_TOTAL_LEVEL.matcher(s);
            if (m.find()) {
                return m.group(1) + " skill-total (totaal te laag — chat)";
            }
            m = CHAT_TOTAL_OF.matcher(s);
            if (m.find()) {
                return m.group(1) + " skill-total (te laag — chat)";
            }
            if (low.contains("members")) {
                return "members (chat)";
            }
            return "wereld geweigerd (chat)";
        }
        return null;
    }

    public static int cachedTotalLevel() {
        refreshTotals();
        return cacheTotal;
    }

    public static int cachedF2pTotal() {
        refreshTotals();
        return cacheF2p;
    }

    private static String typeBlock(int worldId, Set<WorldType> types, String act) {
        String a = act != null ? act.toLowerCase(Locale.ROOT) : "";
        if (has(types, WorldType.DEADMAN) || a.contains("deadman")) {
            return "Deadman";
        }
        if (hasName(types, "FRESH_START_WORLD") || a.contains("fresh start")) {
            return "Fresh Start";
        }
        if (hasName(types, "SEASONAL") || hasName(types, "TOURNAMENT")
                || a.contains("seasonal") || a.contains("tournament")) {
            return "seasonal/tournament";
        }
        if (hasName(types, "QUEST_SPEEDRUNNING") || a.contains("speedrunning")) {
            return "Quest Speedrunning";
        }
        if (hasName(types, "BETA_WORLD") || hasName(types, "NOSAVE_MODE")) {
            return "beta/nosave";
        }
        if (hasName(types, "LAST_MAN_STANDING") || a.contains("lms")) {
            return "LMS";
        }
        if (dedicatedPvpArena(worldId, a)) {
            return "PvP Arena";
        }
        // Hopper: "PvP World - Free" ≠ "Wilderness PK - Free" (gewone wereld, PK alleen in wildy).
        if (isPvpWorld(types, a)) {
            return "PVP";
        }
        if (hasName(types, "BOUNTY") || a.contains("bounty hunter")) {
            return "Bounty";
        }
        if (hasName(types, "HIGH_RISK") || a.contains("high risk")) {
            return "High Risk";
        }
        return null;
    }

    /** Echte PvP-wereld (hopper: PvP World). Wilderness PK telt niet. */
    public static boolean isPvpBlockReason(String reason) {
        return reason != null && "PVP".equalsIgnoreCase(reason.trim());
    }

    private static boolean isPvpWorld(Set<WorldType> types, String activityLower) {
        String a = activityLower != null ? activityLower : "";
        // Hopper: "Wilderness PK - Free" ≠ "PvP World - Free".
        if (a.contains("wilderness pk")) {
            return false;
        }
        if (a.contains("pvp world")) {
            return true;
        }
        if (a.contains("pvp") && !a.contains("arena")) {
            return true;
        }
        return has(types, WorldType.PVP);
    }

    /**
     * Alleen restricted Arena-werelden. Niet {@code WorldType.PVP_ARENA} alleen:
     * die vlag staat ook op gewone F2P (W430 Trade) → valse ✕ in de Star-tab.
     * Legacy Duels (o.a. W571) blijven toegestaan — ster minen werkt daar.
     */
    private static boolean dedicatedPvpArena(int worldId, String activityLower) {
        if (PVP_ARENA_RESTRICTED.contains(worldId)) {
            return true;
        }
        if (activityLower == null || !activityLower.contains("pvp arena")) {
            return false;
        }
        return !activityLower.contains("legacy");
    }

    /** &gt;0 drempel, 0 geen skill-total, -1 skill-total zonder getal. */
    private static int skillTotalNeed(Set<WorldType> types, String act) {
        if (act != null) {
            Matcher m = SKILL_TOTAL_ACT.matcher(act);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (hasName(types, "SKILL_TOTAL")) {
            return -1;
        }
        return 0;
    }

    private static void refreshTotals() {
        long now = System.currentTimeMillis();
        if (now - cacheMs < 2_000L && cacheMs > 0L) {
            return;
        }
        cacheTotal = Skills.getTotalLevel();
        cacheF2p = Skills.getF2pTotalLevel();
        cacheMs = now;
    }

    private static boolean has(Set<WorldType> types, WorldType t) {
        return types != null && t != null && types.contains(t);
    }

    private static boolean hasName(Set<WorldType> types, String name) {
        if (types == null || name == null) {
            return false;
        }
        try {
            return types.contains(WorldType.valueOf(name));
        } catch (Throwable t) {
            return false;
        }
    }
}
