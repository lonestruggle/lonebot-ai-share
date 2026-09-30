package com.lonebot.example.starminer;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.game.Chat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live ster: object + NPC (healthbar, o.a. nid 10629) + Prospect-chat.
 */
final class StarInspect {

    /** HoverCapture: crashed star als NPC. */
    static final int STAR_NPC_ID = 10629;

    private static final Pattern SIZE = Pattern.compile("size[- ](\\d+) star", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED = Pattern.compile(
            "Mining level of (?:at least )?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINED_PCT = Pattern.compile(
            "mined (\\d+)\\s*% of the way", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_MINE = Pattern.compile(
            "need a Mining level of (\\d+) to mine this star", Pattern.CASE_INSENSITIVE);

    private static volatile int prospectTier;
    private static volatile int prospectNeed;
    private static volatile int minedPct = -1;
    private static volatile long lastProspectClickMs;
    private static volatile long nextProspectAtMs;
    private static volatile long lastLogMs;
    private static volatile String lastLog = "";
    private static volatile int lastObjId;
    private static volatile int lastHp = -1;
    private static volatile long lastHpLogMs;
    private static volatile long lastHpMs;
    /** Laagste geloofde tier — object-ID mag niet omhoog (ster zakt alleen). */
    private static volatile int trustedTier;
    private static volatile long lastProspectChatMs;
    private static volatile String lastAppliedChat = "";

    private StarInspect() {
    }

    static void reset() {
        prospectTier = 0;
        prospectNeed = 0;
        minedPct = -1;
        lastProspectClickMs = 0L;
        nextProspectAtMs = 0L;
        lastObjId = 0;
        lastHp = -1;
        lastHpLogMs = 0L;
        lastHpMs = 0L;
        trustedTier = 0;
        lastProspectChatMs = 0L;
        lastAppliedChat = "";
    }

    static int prospectTier() {
        return prospectTier;
    }

    static int prospectNeed() {
        return prospectNeed;
    }

    /** HP-balk recent gezien met resterende laag — ster is niet weg. */
    static boolean healthRecentlyAlive(long maxAgeMs) {
        if (lastHp <= 0 || lastHpMs <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - lastHpMs <= maxAgeMs;
    }

    static int trustedTier() {
        return trustedTier;
    }

    /** Prospect-chat van de laatste 45s — wint van object-ID bij conflict. */
    static boolean prospectFresh() {
        return lastProspectChatMs > 0L
                && System.currentTimeMillis() - lastProspectChatMs < 45_000L;
    }

    /** Laag gemined naar volgende laag (Prospect-chat), of -1. */
    static int minedPct() {
        return minedPct;
    }

    static INPC nearestNpc(WorldPoint near) {
        try {
            return near != null
                    ? NPCs.getNearest(near, STAR_NPC_ID)
                    : NPCs.getNearest(STAR_NPC_ID);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean looksLikeStarNpc(INPC n) {
        if (n == null) {
            return false;
        }
        if (n.getId() == STAR_NPC_ID) {
            return true;
        }
        String name = "";
        try {
            name = n.getName() != null ? n.getName() : "";
        } catch (Throwable ignored) {
        }
        String low = name.toLowerCase();
        if (low.contains("crashed star") || low.contains("shooting star")) {
            return true;
        }
        try {
            return n.hasAction("Prospect") && n.hasAction("Mine");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 0–100 remaining van huidige laag via NPC-healthbar, of -1. */
    static int npcHealthPct(INPC npc) {
        if (npc == null) {
            return -1;
        }
        try {
            int scale = npc.getHealthScale();
            int ratio = npc.getHealthRatio();
            if (scale <= 0 || ratio < 0) {
                return -1;
            }
            return Math.max(0, Math.min(100, (int) Math.round(100.0 * ratio / scale)));
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Object/Prospect lager dan cache → cache mee naar beneden (T6 mag T5 niet blokkeren). */
    static void syncLower(int liveTier) {
        if (liveTier <= 0) {
            return;
        }
        if (prospectTier > liveTier) {
            log("cache T" + prospectTier + " → T" + liveTier);
            prospectTier = liveTier;
            prospectNeed = StarObjects.miningLevelForTier(liveTier);
        }
        if (trustedTier > liveTier && liveTier > 0) {
            trustedTier = liveTier;
        }
    }

    /**
     * Laag-wissel: object-ID (T6=41224 → T5=41225). Healthbar is extra:
     * RuneLite geeft ratio alleen als de balk in beeld is, anders -1.
     * @return true als een nieuwe laag begint (opnieuw Mine)
     */
    static boolean watch(ITileObject star, INPC npc) {
        boolean layerDrop = false;
        int id = 0;
        try {
            id = star != null ? star.getId() : 0;
        } catch (Throwable ignored) {
        }
        if (id > 0 && lastObjId > 0 && id != lastObjId) {
            int from = StarObjects.tierFromId(lastObjId);
            int to = StarObjects.tierFromId(id);
            log("obj " + lastObjId + (from > 0 ? " T" + from : "")
                    + " → " + id + (to > 0 ? " T" + to : ""));
            if (to > 0 && from > 0 && to < from) {
                prospectTier = to;
                prospectNeed = StarObjects.miningLevelForTier(to);
                trustedTier = to;
                minedPct = -1;
                layerDrop = true;
                lastHp = -1;
                BotRuntime.logConsole("[Star] laag T" + from + " → T" + to
                        + " (obj " + lastObjId + " → " + id + ")");
            } else if (to > 0 && from > 0 && to > from) {
                prospectTier = to;
                prospectNeed = StarObjects.miningLevelForTier(to);
                trustedTier = to;
                minedPct = -1;
                layerDrop = true;
                lastHp = -1;
                log("obj T" + from + "→T" + to + " — nieuwe ster, niet oude T" + from);
            } else if (to > 0) {
                prospectTier = to;
                prospectNeed = StarObjects.miningLevelForTier(to);
                trustedTier = to;
                minedPct = -1;
                layerDrop = true;
            } else {
                nextProspectAtMs = 0L;
                layerDrop = true;
            }
        }
        if (id > 0) {
            lastObjId = id;
            int mapped = StarObjects.tierFromId(id);
            if (trustedTier <= 0 && mapped > 0) {
                trustedTier = mapped;
            }
        }
        int hp = npcHealthPct(npc);
        long now = System.currentTimeMillis();
        if (hp >= 0 && (lastHpLogMs <= 0L || now - lastHpLogMs > 8_000L || (lastHp >= 0 && Math.abs(hp - lastHp) >= 8))) {
            lastHpLogMs = now;
            log("hp " + hp + "%" + (npc != null ? " npc=" + npc.getId() : " geen-npc"));
        }
        if (lastHp >= 0 && hp >= 0 && lastHp <= 20 && hp >= 50) {
            log("hp " + lastHp + "% → " + hp + "% — balk reset (nieuwe laag)");
            minedPct = -1;
            layerDrop = true;
            lastHp = hp;
            lastHpMs = now;
            return layerDrop;
        }
        if (hp >= 0) {
            lastHp = hp;
            lastHpMs = now;
        }
        return layerDrop;
    }

    static String overlayLine(ITileObject star, INPC npc) {
        int objT = StarObjects.tierFromObject(star);
        int live = StarObjects.liveTier(star);
        int hp = npcHealthPct(npc);
        String t = live > 0 ? "T" + live : "T?";
        StringBuilder sb = new StringBuilder("Ster ");
        sb.append(t);
        if (star != null) {
            sb.append(" obj=").append(star.getId());
            if (objT > 0 && objT != live) {
                sb.append(" (id T").append(objT).append(")");
            }
        }
        if (hp >= 0) {
            sb.append(" hp ").append(hp).append("%");
        } else if (npc != null) {
            sb.append(" hp ?");
        } else {
            sb.append(" geen-npc");
        }
        if (minedPct >= 0) {
            sb.append(" mined ").append(minedPct).append("%");
        }
        if (npc != null) {
            sb.append(" npc ").append(npc.getId());
        }
        return sb.toString();
    }

    static void readChat() {
        List<String> lines = Chat.getRecentMessages(8);
        if (lines == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String s = lines.get(i);
            if (s == null || s.isBlank()) {
                continue;
            }
            Matcher fail = NEED_MINE.matcher(s);
            if (fail.find()) {
                prospectNeed = Integer.parseInt(fail.group(1));
                if (prospectTier <= 0 && prospectNeed > 0) {
                    prospectTier = Math.max(1, Math.min(9, prospectNeed / 10));
                }
            }
            Matcher sizeM = SIZE.matcher(s);
            if (!sizeM.find()) {
                continue;
            }
            if (!s.equals(lastAppliedChat)) {
                applyProspectChat(s, sizeM, now);
                lastAppliedChat = s;
            }
            Matcher mined = MINED_PCT.matcher(s);
            if (mined.find()) {
                minedPct = Integer.parseInt(mined.group(1));
            }
            break;
        }
    }

    private static void applyProspectChat(String s, Matcher sizeM, long now) {
        int size = Integer.parseInt(sizeM.group(1));
        if (size < 1 || size > 9) {
            return;
        }
        prospectTier = size;
        lastProspectChatMs = now;
        trustedTier = size;
        Matcher needM = NEED.matcher(s);
        if (needM.find()) {
            prospectNeed = Integer.parseInt(needM.group(1));
        } else {
            prospectNeed = StarObjects.miningLevelForTier(size);
        }
        log("chat size-" + size + " mining " + prospectNeed
                + " (obj-id T" + (lastObjId > 0 ? StarObjects.tierFromId(lastObjId) : 0) + ")");
    }

    static WorldPoint tile(ITileObject star, INPC npc) {
        if (star != null && star.getWorldLocation() != null) {
            return star.getWorldLocation();
        }
        if (npc != null && npc.getWorldLocation() != null) {
            return npc.getWorldLocation();
        }
        return null;
    }

    static boolean present(ITileObject star, INPC npc) {
        if (star != null) {
            return true;
        }
        if (npc == null) {
            return false;
        }
        try {
            return npc.hasAction("Mine") || npc.hasAction("Prospect");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Prospect alleen als de laag onbekend is, of één keer na een laag-wissel.
     * Geen timer tijdens wachten — dat is elke ~30s een klik op de ster.
     */
    static int maybeProspect(ITileObject star, INPC npc, boolean force) {
        return maybeProspect(star, npc, force, false);
    }

    static int maybeProspect(ITileObject star, INPC npc, boolean force, boolean waiting) {
        readChat();
        long now = System.currentTimeMillis();
        if (waiting && !force) {
            return 0;
        }
        if (lastProspectClickMs > 0L && now - lastProspectClickMs < 8_000L) {
            return 0;
        }
        if (!force && nextProspectAtMs > 0L && now < nextProspectAtMs) {
            return 0;
        }
        boolean ok = clickProspect(star, npc);
        if (!ok) {
            return 0;
        }
        lastProspectClickMs = now;
        nextProspectAtMs = now + ThreadLocalRandom.current().nextInt(45_000, 90_000);
        log("Prospect " + (star != null ? "object id=" + star.getId() : "npc"));
        return ThreadLocalRandom.current().nextInt(700, 1100);
    }

    static boolean clickProspect(ITileObject star, INPC npc) {
        try {
            if (star != null && star.hasAction("Prospect")) {
                return star.interact("Prospect");
            }
        } catch (Throwable ignored) {
        }
        try {
            if (npc != null && npc.hasAction("Prospect")) {
                return npc.interact("Prospect");
            }
        } catch (Throwable ignored) {
        }
        try {
            if (npc != null) {
                return npc.interact("Prospect");
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean clickMine(ITileObject star, INPC npc) {
        try {
            if (star != null && star.hasAction("Mine")) {
                return star.interact("Mine");
            }
        } catch (Throwable ignored) {
        }
        try {
            if (npc != null && npc.hasAction("Mine")) {
                return npc.interact("Mine");
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/prospect] " + msg);
    }
}
