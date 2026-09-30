package net.runelite.client.plugins.lonebot;

import com.lonebot.example.fishing.FishingPlugin;
import com.lonebot.example.woodcutter.WoodcutterPlugin;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.storm.sdk.bot.ActivityLog;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.Static;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Sessie-kop voor {@link ActivityLog} (pc, versies, cfg). */
final class ActivitySessionHeaders {

    private ActivitySessionHeaders() {
    }

    static List<String> build() {
        List<String> lines = new ArrayList<>();
        lines.add("pc=" + hostName() + "  os=" + System.getProperty("os.name", "?"));
        lines.add("LoneBot=" + LoneBotBootstrapPlugin.VERSION
                + "  skill=" + BotRuntime.activeSkill
                + "  wereld=" + worldId()
                + skillLevelBit());
        lines.add("account=" + (LoneBotBotControl.launchedAccountName() != null
                ? LoneBotBotControl.launchedAccountName() : "?")
                + "  Fish=" + nz(BotRuntime.fishPluginVersion)
                + "  WC=" + nz(BotRuntime.wcPluginVersion)
                + "  Star=" + nz(BotRuntime.starPluginVersion));
        lines.add("cfg Fish loc=" + fishLoc()
                + " drop=" + onOff(FishingPlugin.dropFish)
                + " cook=" + onOff(FishingPlugin.cookEnabled)
                + " restock=" + onOff(FishingPlugin.restockEnabled)
                + " clues=" + onOff(BotRuntime.fishClueSolver)
                + " baitMin=" + FishingPlugin.baitMin);
        lines.add("cfg WC tree=" + wcTree()
                + " loc=" + (WoodcutterPlugin.preferredLocation != null
                ? WoodcutterPlugin.preferredLocation : "AUTO")
                + " drop=" + onOff(WoodcutterPlugin.dropLogs)
                + " fm=" + onOff(WoodcutterPlugin.firemaking)
                + " clues=" + onOff(BotRuntime.wcClueSolver));
        return lines;
    }

    private static String skillLevelBit() {
        try {
            switch (BotRuntime.activeSkill) {
                case FISHING:
                    return "  Fishing=" + Skills.getLevel(Skill.FISHING);
                case WOODCUTTING:
                    return "  WC=" + Skills.getLevel(Skill.WOODCUTTING);
                case STAR:
                    return "  Mining=" + Skills.getLevel(Skill.MINING);
                default:
                    return "";
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static String fishLoc() {
        String pref = FishingPlugin.preferredLocation != null ? FishingPlugin.preferredLocation : "AUTO";
        if ("AUTO".equalsIgnoreCase(pref)) {
            try {
                int lvl = Skills.getLevel(Skill.FISHING);
                String pick = lvl >= 20 ? "Barbarian" : "Draynor";
                return "AUTO(" + pick + ")";
            } catch (Throwable t) {
                return "AUTO";
            }
        }
        return pref;
    }

    private static String wcTree() {
        if (WoodcutterPlugin.useSpecificTree && WoodcutterPlugin.treeName != null
                && !WoodcutterPlugin.treeName.isBlank()) {
            return WoodcutterPlugin.treeName;
        }
        return "AUTO";
    }

    private static int worldId() {
        try {
            Client c = Static.getClient();
            return c != null ? c.getWorld() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String onOff(boolean v) {
        return v ? "aan" : "uit";
    }

    private static String nz(String v) {
        return v != null && !v.isBlank() ? v : "?";
    }
}
