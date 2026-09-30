package net.runelite.client.plugins.lonebot;

import com.lonebot.example.ImpKillerPlugin;
import com.lonebot.example.Imps2Plugin;
import com.lonebot.example.StarMinerPlugin;
import com.lonebot.example.GiantsPlugin;
import com.lonebot.example.CluePlugin;
import com.lonebot.example.QuesterPlugin;
import com.lonebot.example.fishing.FishingPlugin;
import com.lonebot.example.woodcutter.WoodcutterPlugin;
import net.storm.sdk.bot.LoneBotPaths;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Catalogus van LoneBot-scripts voor de launcher Scripts-tab.
 */
public final class LoneBotScriptCatalog {

    public enum Maturity {
        WERKEND("Werkend"),
        BETA("Beta"),
        TEST("Test");

        public final String label;

        Maturity(String label) {
            this.label = label;
        }
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final Maturity maturity;
        public final String version;
        public final String note;
        public final String gradleTask;
        public final String relativeJar;
        public final File stagedJar;

        Entry(String id, String name, Maturity maturity, String version, String note,
              String gradleTask, String relativeJar, File stagedJar) {
            this.id = id;
            this.name = name;
            this.maturity = maturity;
            this.version = version;
            this.note = note;
            this.gradleTask = gradleTask;
            this.relativeJar = relativeJar;
            this.stagedJar = stagedJar;
        }
    }

    private LoneBotScriptCatalog() {
    }

    public static List<Entry> all() {
        File example = LoneBotPaths.exampleScriptsJar();
        return Arrays.asList(
                new Entry("imp", "Imp Killer", Maturity.WERKEND, ImpKillerPlugin.VERSION,
                        "Karamja imps: hunt, boot, deposit",
                        ":script-imp-killer:jar",
                        "script-imp-killer\\build\\libs\\imp-killer.jar",
                        LoneBotPaths.impKillerJar()),
                new Entry("imps2", "Imps2", Maturity.BETA, Imps2Plugin.VERSION,
                        "Walk: overal → Port Sarim dock → Musa → imps → terug → Draynor",
                        ":script-imps2:jar",
                        "script-imps2\\build\\libs\\imps2.jar",
                        LoneBotPaths.imps2Jar()),
                new Entry("wc", "Woodcutting", Maturity.WERKEND, WoodcutterPlugin.VERSION,
                        "Bomen, bank, bonfire",
                        ":script-woodcutter:jar",
                        "script-woodcutter\\build\\libs\\woodcutter.jar",
                        LoneBotPaths.woodcutterJar()),
                new Entry("fish", "Fishing", Maturity.WERKEND, FishingPlugin.VERSION,
                        "Vissen, koken, bank",
                        ":script-fishing:jar",
                        "script-fishing\\build\\libs\\fishing.jar",
                        LoneBotPaths.fishingJar()),
                new Entry("star", "Star Miner", Maturity.BETA, StarMinerPlugin.VERSION,
                        "Shooting stars: Discord + OSRS Portal → hop → loop → Mine",
                        ":script-star-miner:jar",
                        "script-star-miner\\build\\libs\\star-miner.jar",
                        LoneBotPaths.starMinerJar()),
                new Entry("giants", "Giants", Maturity.BETA, GiantsPlugin.VERSION,
                        "Hill Giants: brass key, shed/trapdoor, hunt, loot, eigen food-bank",
                        ":script-giants:jar",
                        "script-giants\\build\\libs\\giants.jar",
                        LoneBotPaths.giantsJar()),
                new Entry("clue", "Beginner Clue", Maturity.BETA, CluePlugin.VERSION,
                        "Beginner Treasure Trails: anagram, cryptic, emote, map, hot/cold, Charlie",
                        ":script-clue:jar",
                        "script-clue\\build\\libs\\clue.jar",
                        LoneBotPaths.clueJar()),
                new Entry("quest", "Quest Bot", Maturity.BETA, QuesterPlugin.VERSION,
                        "F2P: Cook / Goblin / Romeo / Rune / Doric / Vampire / Tutorial",
                        ":script-quest:jar",
                        "script-quest\\build\\libs\\quest.jar",
                        LoneBotPaths.questJar()),
                new Entry("cow", "Cow combat", Maturity.BETA, "bundled",
                        "Koeien — nog niet af (example.jar)",
                        ":example-plugin:jar",
                        "example-plugin\\build\\libs\\example-plugin.jar",
                        example),
                new Entry("tests", "Tests (bank / city circle)", Maturity.TEST, "bundled",
                        "Dev-tests — example.jar",
                        ":example-plugin:jar",
                        "example-plugin\\build\\libs\\example-plugin.jar",
                        example)
        );
    }

    public static File buildAndStage(Entry e, Consumer<String> status) {
        if (e == null) {
            return null;
        }
        switch (e.id) {
            case "imp":
                return ScriptReloadHelper.buildAndStageImpJar(status);
            case "imps2":
                return ScriptReloadHelper.buildAndStageImps2Jar(status);
            case "wc":
                return ScriptReloadHelper.buildAndStageWoodcutterJar(status);
            case "fish":
                return ScriptReloadHelper.buildAndStageFishingJar(status);
            case "star":
                return ScriptReloadHelper.buildAndStageStarMinerJar(status);
            case "giants":
                return ScriptReloadHelper.buildAndStageGiantsJar(status);
            case "clue":
                return ScriptReloadHelper.buildAndStageClueJar(status);
            case "quest":
                return ScriptReloadHelper.buildAndStageQuesterJar(status);
            case "cow":
            case "tests":
                return ScriptReloadHelper.buildAndStageExampleJar(status);
            default:
                return ScriptReloadHelper.buildAndStage(status, e.gradleTask, e.relativeJar, e.stagedJar);
        }
    }
}
