package com.lonebot.example.starminer;

import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.Players;
import net.storm.sdk.movement.MovementHelper;
import net.storm.sdk.quests.Quests;

import java.util.Map;

/**
 * Quest-/bereikbaarheids-locks per crash-site.
 * Crandor → Dragon Slayer I. Corsair → The Corsair Curse.
 * Crafting Guild → Crafting 40 + brown apron / cape (ster zit binnen).
 */
final class StarSpotGate {

    private static final Map<String, Quest> REQUIRED = Map.of(
            "CORSAIR_BANK", Quest.THE_CORSAIR_CURSE,
            "CORSAIR_RESOURCE", Quest.THE_CORSAIR_CURSE,
            "NORTH_CRANDOR", Quest.DRAGON_SLAYER_I,
            "SOUTH_CRANDOR", Quest.DRAGON_SLAYER_I
    );

    private static long lastLogMs;
    private static String lastLog = "";

    private StarSpotGate() {
    }

    static boolean accessible(StarLocations.Spot spot) {
        return blockReason(spot) == null;
    }

    static String blockReason(StarLocations.Spot spot) {
        if (spot == null || spot.key == null) {
            return null;
        }
        Quest quest = REQUIRED.get(spot.key);
        if (quest != null && !Quests.isFinished(quest)) {
            return quest.getName() != null ? quest.getName() : quest.name();
        }
        if (StarCraftingGuild.isSpot(spot)) {
            String why = StarCraftingGuild.blockReason();
            if (why != null) {
                return why;
            }
        }
        // Geen pad over water (Crandor/Karamja) vanaf huidige tegel
        try {
            Players.LocalSnap me = Players.snapshotLocal();
            WorldPoint pos = me != null ? me.worldLocation : null;
            WorldPoint tile = spot.tile;
            if (pos != null && tile != null && MovementHelper.isBoatRequiredGap(pos, tile)) {
                return "boot/eiland — geen pad";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static void logSkipOnce(StarLocations.Spot spot) {
        String why = blockReason(spot);
        if (why == null) {
            return;
        }
        String msg = (spot != null ? spot.shortName : "spot") + " — overslaan: " + why;
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 4_000L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/quest] " + msg);
    }
}
