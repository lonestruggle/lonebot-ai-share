package com.lonebot.example;

import com.lonebot.example.giants.GiantsDebugPaint;
import com.lonebot.example.giants.GiantsHandler;
import com.lonebot.example.giants.GiantsLocations;
import com.lonebot.example.giants.GiantsTypes;
import net.storm.api.plugins.LoopedPlugin;
import net.storm.api.plugins.PluginDescriptor;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.loop.LoopHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LoneBot Giants — Hill Giants in Edgeville Dungeon (F2P).
 */
@PluginDescriptor(
        name = "LoneBot Giants",
        description = "Hill Giants: brass key, shed/trapdoor, hunt, loot, eigen food-bank"
)
public class GiantsPlugin extends LoopedPlugin {

    public static final String VERSION = "0.1.1";

    private static final Logger log = LoggerFactory.getLogger(GiantsPlugin.class);

    private final GiantsHandler handler = new GiantsHandler();
    private GiantsDebugPaint debugPaint;
    private BotRuntime.DebugPaint paintHook;
    private long seenStartGen;
    private boolean wasRunning;

    public GiantsHandler getHandler() {
        return handler;
    }

    @Override
    public void startUp() {
        super.startUp();
        debugPaint = new GiantsDebugPaint(handler);
        paintHook = debugPaint::render;
        BotRuntime.giantsPluginVersion = VERSION;
        BotRuntime.giantsDebugPaint = paintHook;
        log.info("[Giants] startUp v{}", VERSION);
    }

    @Override
    public void shutDown() {
        if (paintHook != null && BotRuntime.giantsDebugPaint == paintHook) {
            BotRuntime.giantsDebugPaint = null;
        }
        paintHook = null;
        debugPaint = null;
        super.shutDown();
    }

    @Override
    public int loop() {
        if (LoopHost.isReloading()) {
            return 50;
        }
        handler.enabled = BotRuntime.giantsKillerEnabled;
        BotRuntime.giantsPluginVersion = VERSION;
        if (paintHook != null) {
            BotRuntime.giantsDebugPaint = paintHook;
        }
        boolean on = BotRuntime.botEnabled && BotRuntime.giantsKillerEnabled;
        if (on && BotRuntime.botStartGeneration != seenStartGen) {
            seenStartGen = BotRuntime.botStartGeneration;
            handler.reset();
            BotRuntime.logConsole("[Giants] loop start v" + VERSION
                    + " resume=" + BotRuntime.lastStartWasResume
                    + " allowed=" + BotRuntime.isSkillPluginAllowed("GiantsPlugin"));
        }
        if (!on) {
            if (wasRunning) {
                wasRunning = false;
                handler.reset();
            }
            BotRuntime.giantsStatus = BotRuntime.botEnabled ? "uit" : "bot uit";
            return 800;
        }
        wasRunning = true;
        BotRuntime.enforceExclusiveSkills();
        if (!BotRuntime.giantsKillerEnabled) {
            BotRuntime.giantsStatus = "uit (andere skill)";
            return 800;
        }
        if (net.storm.sdk.bot.ClueSkillHandoff.tryHandoffFromActiveSkill()) {
            BotRuntime.giantsStatus = "clue handoff";
            return 400;
        }

        try {
            int delay = handler.loop();
            BotRuntime.giantsStatus = handler.getStatus();
            return delay;
        } catch (Throwable t) {
            log.warn("[Giants] loop error: {}", t.toString(), t);
            BotRuntime.giantsStatus = "err: " + t.getClass().getSimpleName();
            return 1000;
        }
    }

    public static void applySettings(
            GiantsTypes.GiantsCombatStyle style,
            GiantsTypes.GiantsMageSpell mageSpell,
            String lootCsv,
            int eatPercent,
            int foodAmount,
            int foodBankThreshold,
            boolean magicAutoUpdate,
            String lootPickupMode,
            boolean lootPickupStrict
    ) {
        Object styleVal = style != null ? style : GiantsTypes.GiantsCombatStyle.MELEE;
        Object spellVal = mageSpell != null ? mageSpell : GiantsTypes.GiantsMageSpell.WIND_STRIKE;
        String loot = lootCsv != null && !lootCsv.isBlank() ? lootCsv : GiantsLocations.DEFAULT_LOOT_CSV;
        boolean any = false;
        for (LoopedPlugin p : LoopHost.plugins()) {
            if (p == null || !"com.lonebot.example.GiantsPlugin".equals(p.getClass().getName())) {
                continue;
            }
            try {
                Object h = p.getClass().getMethod("getHandler").invoke(p);
                if (h == null) {
                    continue;
                }
                setField(h, "style", coerceEnum(h, "style", styleVal));
                setField(h, "mageSpell", coerceEnum(h, "mageSpell", spellVal));
                setField(h, "lootCsv", loot);
                setField(h, "eatPercent", eatPercent);
                setField(h, "foodAmount", foodAmount);
                setField(h, "foodBankThreshold", foodBankThreshold);
                setField(h, "magicAutoUpdate", magicAutoUpdate);
                setField(h, "lootPickupMode", lootPickupMode != null ? lootPickupMode : "AUTO");
                setField(h, "lootPickupStrict", lootPickupStrict);
                any = true;
                try {
                    Object ver = p.getClass().getField("VERSION").get(null);
                    if (ver != null) {
                        BotRuntime.giantsPluginVersion = String.valueOf(ver);
                    }
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) {
                log.warn("[Giants] applySettings: {}", t.toString());
            }
        }
        if (!any) {
            log.warn("[Giants] applySettings: geen live GiantsPlugin geregistreerd");
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
