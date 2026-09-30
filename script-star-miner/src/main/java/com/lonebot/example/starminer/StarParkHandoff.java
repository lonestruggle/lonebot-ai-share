package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.Skill;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Skills;

/**
 * Star is baas: geen minebare tier → andere skill; feed minebaar → terug + gear prep.
 * Resume alleen als chooseTarget/pickNext die ster écht zou pakken (skip-werelden niet).
 */
public final class StarParkHandoff {

    private static final long PARK_AFTER_MS = 4_000L;
    private static final long RESUME_GRACE_MS = 8_000L;
    private static final long PARK_MIN_MS = 2_000L;
    private static final long RESUME_CHECK_MS = 2_000L;

    private static volatile StarMinerLoop loopRef;
    private static volatile long noMineableSinceMs;
    private static volatile long lastParkMs;
    private static volatile long lastResumeMs;
    private static volatile long lastResumeCheckMs;
    private static volatile long lastLogMs;
    private static volatile String lastLog = "";

    private StarParkHandoff() {
    }

    static void bind(StarMinerLoop loop) {
        loopRef = loop;
    }

    public static boolean parkSkillEnabled() {
        return parkWaitSkill() != BotRuntime.ActiveSkill.NONE;
    }

    public static BotRuntime.ActiveSkill parkWaitSkill() {
        return parse(StarMinerPlugin.waitParkSkill);
    }

    public static boolean tryPark(StarMinerLoop loop, String why) {
        BotRuntime.ActiveSkill wait = parkWaitSkill();
        if (wait == BotRuntime.ActiveSkill.NONE) {
            noMineableSinceMs = 0L;
            return false;
        }
        if (!BotRuntime.botEnabled || BotRuntime.starParkActive) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastResumeMs > 0L && now - lastResumeMs < RESUME_GRACE_MS) {
            return false;
        }
        if (noMineableSinceMs <= 0L) {
            noMineableSinceMs = now;
            log("geen minebare ster — park over " + (PARK_AFTER_MS / 1000L) + "s (" + why + ")");
            return false;
        }
        if (now - noMineableSinceMs < PARK_AFTER_MS) {
            return false;
        }
        if (loop != null) {
            loop.onParkLeave();
        }
        if (!BotRuntime.parkStarForWaitSkill(wait)) {
            return false;
        }
        lastParkMs = now;
        noMineableSinceMs = 0L;
        log("park " + wait + " — " + why);
        return true;
    }

    /**
     * Valse hervatting: skip-wereld stond nog in de feed. Direct terug naar park
     * (geen 8s grace, geen 4s wacht, geen bank sluiten).
     */
    public static void noteFalseResume() {
        lastResumeMs = 0L;
        noMineableSinceMs = System.currentTimeMillis() - PARK_AFTER_MS;
        log("false resume — terug naar park (skip/onbereikbaar)");
    }

    /** Park meteen (na false resume). */
    public static boolean forcePark(StarMinerLoop loop, String why) {
        lastResumeMs = 0L;
        noMineableSinceMs = System.currentTimeMillis() - PARK_AFTER_MS;
        return tryPark(loop, why);
    }

    public static boolean tryResume() {
        if (!BotRuntime.starParkActive || !BotRuntime.botEnabled) {
            return false;
        }
        if (BotRuntime.activeSkill == BotRuntime.ActiveSkill.CLUE
                || BotRuntime.activeSkill == BotRuntime.ActiveSkill.QUEST) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastParkMs > 0L && now - lastParkMs < PARK_MIN_MS) {
            return false;
        }
        if (lastResumeCheckMs > 0L && now - lastResumeCheckMs < RESUME_CHECK_MS) {
            return false;
        }
        lastResumeCheckMs = now;
        int mining = 0;
        try {
            mining = Skills.getLevel(Skill.MINING);
        } catch (Throwable ignored) {
            return false;
        }
        StarMinerLoop loop = loopRef;
        StarCall next = null;
        if (loop != null) {
            try {
                next = loop.peekTravelableMineable(mining);
            } catch (Throwable ignored) {
            }
        }
        if (next == null) {
            int raw = 0;
            try {
                raw = StarFeed.get().countMineableNow(mining, StarMinerPlugin.avoidWilderness,
                        StarMinerPlugin.f2pOnly, null);
            } catch (Throwable ignored) {
            }
            if (raw > 0) {
                log("blijf park — feed nu=" + raw + " maar skip/onbereikbaar");
            }
            return false;
        }
        if (!BotRuntime.resumeStarFromPark()) {
            return false;
        }
        lastResumeMs = now;
        noMineableSinceMs = 0L;
        log("feed minebaar → " + next.label() + " — Star hervat, gear prep");
        return true;
    }

    public static void onFeedUpdated() {
        try {
            tryResume();
        } catch (Throwable ignored) {
        }
    }

    public static void noteMineablePresent() {
        noMineableSinceMs = 0L;
    }

    private static BotRuntime.ActiveSkill parse(String raw) {
        if (raw == null || raw.isBlank() || "NONE".equalsIgnoreCase(raw)) {
            return BotRuntime.ActiveSkill.NONE;
        }
        try {
            BotRuntime.ActiveSkill s = BotRuntime.ActiveSkill.valueOf(raw.trim().toUpperCase());
            if (s == BotRuntime.ActiveSkill.STAR || s == BotRuntime.ActiveSkill.CLUE
                    || s == BotRuntime.ActiveSkill.QUEST || s == BotRuntime.ActiveSkill.NONE) {
                return BotRuntime.ActiveSkill.NONE;
            }
            return s;
        } catch (IllegalArgumentException e) {
            return BotRuntime.ActiveSkill.NONE;
        }
    }

    private static void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/park] " + msg);
    }
}
