package com.lonebot.example;

import com.lonebot.example.imps.CoreImpsHandler;
import com.lonebot.example.imps.ImpDebugPaint;
import com.lonebot.example.imps.ImpsTypes;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.loop.LoopHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LoneBot Imp Killer — wraps {@link CoreImpsHandler}.
 * Versie = {@link #VERSION} (plugin jar). Hot-reload via paneel.
 */
@PluginDescriptor(
        name = "LoneBot Imp Killer",
        description = "Karamja imps: hunt, loot, boat, deposit, restock, gear, quest loot"
)
public class ImpKillerPlugin extends LoopedPlugin {

    public static final String VERSION = "0.2.90";

    private static final Logger log = LoggerFactory.getLogger(ImpKillerPlugin.class);

    private final CoreImpsHandler handler = new CoreImpsHandler();
    private ImpDebugPaint debugPaint;
    private BotRuntime.DebugPaint paintHook;

    public CoreImpsHandler getHandler() {
        return handler;
    }

    public void onGameMessage(String message) {
        handler.onGameMessage(message);
    }

    @Override
    public void startUp() {
        super.startUp();
        debugPaint = new ImpDebugPaint(handler);
        paintHook = debugPaint::render;
        BotRuntime.impPluginVersion = VERSION;
        BotRuntime.impDebugPaint = paintHook;
        log.info("[ImpKiller] startUp v{} — debug paint registered", VERSION);
    }

    @Override
    public void shutDown() {
        if (paintHook != null && BotRuntime.impDebugPaint == paintHook) {
            BotRuntime.impDebugPaint = null;
        }
        paintHook = null;
        debugPaint = null;
        super.shutDown();
    }

    @Override
    public int loop() {
        handler.enabled = BotRuntime.impKillerEnabled;
        BotRuntime.impPluginVersion = VERSION;
        if (paintHook != null) {
            BotRuntime.impDebugPaint = paintHook;
        }
        if (!BotRuntime.botEnabled || !BotRuntime.impKillerEnabled) {
            BotRuntime.impStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            return 800;
        }
        if (net.storm.sdk.bot.ClueSkillHandoff.tryHandoffFromActiveSkill()) {
            BotRuntime.impStatus = "clue handoff";
            return 400;
        }

        try {
            int delay = handler.loop();
            BotRuntime.impStatus = handler.getStatus();
            return delay;
        } catch (Throwable t) {
            log.warn("[ImpKiller] loop error: {}", t.toString(), t);
            String detail = t.getMessage();
            if ((detail == null || detail.isEmpty()) && t.getCause() != null) {
                detail = t.getCause().getMessage();
            }
            if (detail != null && detail.length() > 48) {
                detail = detail.substring(detail.lastIndexOf('.') + 1);
                if (detail.length() > 40) {
                    detail = detail.substring(0, 40);
                }
            }
            BotRuntime.impStatus = detail != null && !detail.isEmpty()
                    ? "err: " + t.getClass().getSimpleName() + " " + detail
                    : "err: " + t.getClass().getSimpleName();
            return 1000;
        }
    }

    public static void applySettings(
            ImpsTypes.ImpsCombatStyle style,
            ImpsTypes.ImpsMageSpell mageSpell,
            int huntX, int huntY, int huntRadius,
            int npcId, String lootCsv,
            boolean scatterAshes,
            int bankThreshold, int minCoins,
            boolean avoidScorpions, boolean attackScorpions,
            int scorpionAvoidRadius, int scorpionNpcLevel,
            boolean geSellEnabled, int geSellAfterBanks, int geSellPrice,
            String geSellCsv,
            boolean gearPrepEnabled, boolean geRestockEnabled, boolean questLootEnabled,
            boolean ashHumanize, int ashHumanizeChancePercent,
            int idleRoamSeconds, boolean meleeOpeningAirStrike,
            String lootPickupMode, boolean lootPickupStrict,
            String accountKey,
            boolean magicAutoUpdate,
            boolean lootDelayEnabled, int lootDelayKills, String specialLootCsv
    ) {
        Object styleVal = style != null ? style : ImpsTypes.ImpsCombatStyle.MELEE;
        Object spellVal = mageSpell != null ? mageSpell : ImpsTypes.ImpsMageSpell.WIND_STRIKE;
        boolean any = false;
        for (LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"com.lonebot.example.ImpKillerPlugin".equals(p.getClass().getName())) {
                continue;
            }
            try {
                Object h = p.getClass().getMethod("getHandler").invoke(p);
                if (h == null) {
                    continue;
                }
                setField(h, "style", coerceEnum(h, "style", styleVal));
                setField(h, "mageSpell", coerceEnum(h, "mageSpell", spellVal));
                setField(h, "huntX", huntX);
                setField(h, "huntY", huntY);
                setField(h, "huntRadius", huntRadius);
                setField(h, "impNpcId", npcId);
                setField(h, "lootCsv", lootCsv);
                setField(h, "scatterAshes", scatterAshes);
                setField(h, "bankThreshold", bankThreshold);
                setField(h, "minCoins", minCoins);
                setField(h, "avoidScorpions", avoidScorpions);
                setField(h, "attackScorpions", attackScorpions);
                setField(h, "scorpionZoneRadius", scorpionAvoidRadius > 0 ? scorpionAvoidRadius : 5);
                setField(h, "scorpionNpcLevel", scorpionNpcLevel > 0 ? scorpionNpcLevel : 14);
                setField(h, "geSellEnabled", geSellEnabled);
                setField(h, "geSellAfterBanks", geSellAfterBanks);
                setField(h, "geSellPrice", geSellPrice);
                setField(h, "geSellCsv", geSellCsv);
                setField(h, "gearPrepEnabled", gearPrepEnabled);
                setField(h, "geRestockEnabled", geRestockEnabled);
                setField(h, "questLootEnabled", questLootEnabled);
                setField(h, "ashHumanize", ashHumanize);
                setField(h, "ashHumanizeChancePercent", ashHumanizeChancePercent);
                setField(h, "idleRoamSeconds", idleRoamSeconds);
                setField(h, "meleeOpeningAirStrike", meleeOpeningAirStrike);
                setField(h, "lootPickupMode", lootPickupMode != null ? lootPickupMode : "AUTO");
                setField(h, "lootPickupStrict", lootPickupStrict);
                setField(h, "accountKey", accountKey != null ? accountKey : "");
                setField(h, "magicAutoUpdate", magicAutoUpdate);
                setField(h, "lootDelayEnabled", lootDelayEnabled);
                setField(h, "lootDelayKills", lootDelayKills);
                setField(h, "specialLootCsv", specialLootCsv != null ? specialLootCsv : "");
                try {
                    net.storm.sdk.interact.GroundLootPickupHelper.setPickupModeFromName(
                            lootPickupMode, lootPickupStrict);
                } catch (Throwable ignored) {
                }
                any = true;
                try {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.impPluginVersion = String.valueOf(ver);
                    }
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) {
                log.warn("[ImpKiller] applySettings: {}", t.toString());
            }
        }
        if (!any) {
            log.warn("[ImpKiller] applySettings: geen live ImpKillerPlugin geregistreerd");
        }
    }

    private static Object coerceEnum(Object handler, String fieldName, Object value) {
        if (value == null || !(value instanceof Enum)) {
            return value;
        }
        try {
            java.lang.reflect.Field f = findField(handler.getClass(), fieldName);
            if (f == null) {
                return value;
            }
            Class<?> ft = f.getType();
            if (!ft.isEnum()) {
                return value;
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object mapped = Enum.valueOf((Class<? extends Enum>) ft, ((Enum<?>) value).name());
            return mapped;
        } catch (Throwable t) {
            return value;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = findField(target.getClass(), name);
        if (f == null) {
            return;
        }
        f.setAccessible(true);
        f.set(target, value);
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name) {
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
