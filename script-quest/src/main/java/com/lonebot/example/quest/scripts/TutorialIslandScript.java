package com.lonebot.example.quest.scripts;

import com.lonebot.example.quest.QuestBotScript;
import com.lonebot.example.quest.QuestBotTarget;
import com.lonebot.example.quest.QuestLog;
import com.lonebot.example.quest.helpers.QuestActions;
import com.lonebot.example.quest.helpers.QuestDialogHelper;
import com.lonebot.example.quest.helpers.QuestHelperDialogSteps;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.magic.SpellBook;
import net.storm.api.widgets.Tab;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Static;
import net.storm.sdk.game.Vars;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Tabs;
import net.storm.sdk.widgets.Widgets;

import java.awt.event.KeyEvent;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tutorial Island — VarPlayer 281 → 1000 done.
 * <p>
 * Stage map is ported from CombatBot {@code TutorialModeHandler#runStep} (sqiderwoman
 * capture): creator/name → Gielinor Guide → survival → chef → quest guide → mining →
 * combat → bank/poll → account → prayer → magic → Lumbridge.
 * <p>
 * Each stage returns {@code null} when it has nothing to do, so the hint-arrow follower
 * stays available as a failsafe for captures we do not model.
 */
public final class TutorialIslandScript implements QuestBotScript {

    public static final int VARP = 281;
    public static final int DONE = 1000;
    public static final int IFACE_CREATOR = 679;
    public static final int IFACE_NAME = 558;

    /** Smith-dagger keuze in de tutorial anvil-interface (CombatBot capture). */
    private static final int SMITH_GROUP = 312;
    private static final int SMITH_DAGGER_CHILD = 9;
    /** Equipment-stats paneel (387,2) + sluitknop (84,3). */
    private static final int EQUIP_STATS_GROUP = 387;
    private static final int EQUIP_STATS_CHILD = 2;
    private static final int EQUIP_CLOSE_GROUP = 84;
    private static final int EQUIP_CLOSE_CHILD = 3;
    /** Bank-interface sluiten (12,2) en gedeelde tutorial-overlay/poll-X (708,2). */
    private static final int BANK_CLOSE_GROUP = 12;
    private static final int BANK_CLOSE_CHILD = 2;
    private static final int OVERLAY_GROUP = 708;
    private static final int OVERLAY_CHILD = 2;

    private static final WorldPoint GUIDE_TILE = new WorldPoint(3094, 3107, 0);
    private static final WorldPoint GUIDE_DOOR = new WorldPoint(3098, 3107, 0);
    private static final WorldPoint SURVIVAL_EXPERT_TILE = new WorldPoint(3101, 3095, 0);
    private static final WorldPoint FISHING_SPOT_TILE = new WorldPoint(3103, 3092, 0);
    private static final WorldPoint FIRE_LIGHT_TILE = new WorldPoint(3104, 3094, 0);
    private static final WorldPoint SURVIVAL_EXIT_GATE = new WorldPoint(3090, 3092, 0);
    private static final WorldPoint CHEF_DOOR_NORTH = new WorldPoint(3079, 3084, 0);
    private static final WorldPoint CHEF_DOOR_SOUTH = new WorldPoint(3072, 3090, 0);
    private static final WorldPoint MASTER_CHEF_TILE = new WorldPoint(3075, 3086, 0);
    private static final WorldPoint CHEF_RANGE_STAND = new WorldPoint(3074, 3085, 0);
    private static final WorldPoint QUEST_GUIDE_DOOR = new WorldPoint(3086, 3126, 0);
    private static final WorldPoint QUEST_GUIDE_TILE = new WorldPoint(3086, 3123, 0);
    private static final WorldPoint QUEST_GUIDE_LADDER = new WorldPoint(3088, 3124, 0);
    private static final WorldPoint MINING_INSTRUCTOR_TILE = new WorldPoint(3083, 9504, 0);
    private static final WorldPoint FURNACE_TILE = new WorldPoint(3079, 9495, 0);
    private static final WorldPoint ANVIL_TILE = new WorldPoint(3083, 9499, 0);
    private static final WorldPoint MINING_EXIT_GATE = new WorldPoint(3094, 9502, 0);
    private static final WorldPoint COMBAT_INSTRUCTOR_TILE = new WorldPoint(3106, 9508, 0);
    private static final WorldPoint RAT_PEN_GATE = new WorldPoint(3110, 9518, 0);
    private static final WorldPoint RAT_PEN_INSIDE = new WorldPoint(3112, 9520, 0);
    private static final WorldPoint RAT_RANGE_STAND = new WorldPoint(3108, 9517, 0);
    private static final WorldPoint COMBAT_EXIT_LADDER = new WorldPoint(3111, 9526, 0);
    private static final WorldPoint BANK_BOOTH_TILE = new WorldPoint(3122, 3124, 0);
    private static final WorldPoint POLL_BOOTH_TILE = new WorldPoint(3119, 3121, 0);
    private static final WorldPoint ACCOUNT_POLL_DOOR = new WorldPoint(3125, 3124, 0);
    private static final WorldPoint ACCOUNT_GUIDE_TILE = new WorldPoint(3126, 3123, 0);
    private static final WorldPoint ACCOUNT_TO_PRAYER_DOOR = new WorldPoint(3130, 3125, 0);
    private static final WorldPoint BROTHER_BRACE_TILE = new WorldPoint(3125, 3107, 0);
    private static final WorldPoint PRAYER_TO_MAGIC_DOOR = new WorldPoint(3122, 3103, 0);
    private static final WorldPoint MAGIC_INSTRUCTOR_TILE = new WorldPoint(3142, 3090, 0);

    private static final String NET = "Small fishing net";
    private static final String RAW_SHRIMP = "Raw shrimps";
    private static final String SHRIMP = "Shrimps";
    private static final String TINDERBOX = "Tinderbox";
    private static final String LOGS = "Logs";
    private static final String AXE = "Bronze axe";
    private static final String FLOUR = "Pot of flour";
    private static final String WATER = "Bucket of water";
    private static final String DOUGH = "Bread dough";
    private static final String BREAD = "Bread";
    private static final String PICKAXE = "Bronze pickaxe";
    private static final String TIN_ORE = "Tin ore";
    private static final String COPPER_ORE = "Copper ore";
    private static final String BRONZE_BAR = "Bronze bar";
    private static final String HAMMER = "Hammer";
    private static final String DAGGER = "Bronze dagger";
    private static final String SWORD = "Bronze sword";
    private static final String SHIELD = "Wooden shield";
    private static final String SHORTBOW = "Shortbow";
    private static final String ARROW = "Bronze arrow";

    /** Ondergronds mining/combat gedeelte (y ≈ 9480–9545). */
    private static final int DUNGEON_MIN_Y = 9480;
    private static final int DUNGEON_MAX_Y = 9545;

    private static final int SHRIMP_TARGET = 3;
    private static final long SKILL_CLICK_GAP_MS = 1_400L;

    private static final QuestHelperDialogSteps GENERIC = QuestHelperDialogSteps.of(
            "I'm fine, thanks.",
            "Yes.",
            "No, I'm not planning to do that.",
            "I've been here before."
    );

    private final PathWalk survivalPath = new PathWalk(
            new WorldPoint(3098, 3104, 0),
            new WorldPoint(3098, 3102, 0),
            new WorldPoint(3100, 3102, 0),
            new WorldPoint(3102, 3096, 0));

    private final PathWalk questGuidePath = new PathWalk(
            new WorldPoint(3071, 3094, 0),
            new WorldPoint(3070, 3111, 0),
            new WorldPoint(3076, 3126, 0),
            QUEST_GUIDE_DOOR);

    private final PathWalk magicPath = new PathWalk(
            new WorldPoint(3124, 3107, 0),
            new WorldPoint(3123, 3105, 0),
            new WorldPoint(3131, 3094, 0),
            new WorldPoint(3141, 3085, 0),
            MAGIC_INSTRUCTOR_TILE);

    private int lastVarp = -1;
    private long lastSkillClickMs;

    @Override
    public String displayName() {
        return "Tutorial Island";
    }

    @Override
    public QuestBotTarget target() {
        return QuestBotTarget.TUTORIAL;
    }

    @Override
    public Quest runeliteQuest() {
        return null;
    }

    @Override
    public void reset() {
        survivalPath.reset();
        questGuidePath.reset();
        magicPath.reset();
        lastVarp = -1;
        lastSkillClickMs = 0L;
    }

    @Override
    public boolean isFinished() {
        return varp() >= DONE;
    }

    @Override
    public int loop() {
        int p = varp();
        trackVarp(p);

        if (p >= DONE) {
            QuestLog.step("Tutorial", "klaar (varp 281=" + p + ")");
            return rand(1400, 1800);
        }
        if (handleCreator()) {
            return rand(500, 800);
        }
        if (handleDisplayName()) {
            return rand(600, 900);
        }
        if (QuestDialogHelper.handle(GENERIC) || Dialog.isOpen()) {
            if (Dialog.canContinue()) {
                Dialog.continueTutorial();
            }
            return rand(300, 500);
        }
        if (closeTutorialPanels(p)) {
            return rand(400, 700);
        }

        Integer staged = stage(p);
        if (staged != null) {
            return staged;
        }
        if (followHint()) {
            return rand(500, 800);
        }
        QuestLog.step("Tutorial", "varp 281=" + p + " — geen stap, wacht op hint/dialoog");
        return rand(600, 900);
    }

    // ---------------------------------------------------------------- stages

    /**
     * @return delay when this varp range had work, {@code null} to fall through to the hint arrow
     */
    private Integer stage(int p) {
        if (p < 10) {
            return guideStage(p);
        }
        if (p < 20) {
            return toSurvivalStage(p);
        }
        if (p < 120) {
            return survivalStage(p);
        }
        if (p < 170) {
            return chefStage(p);
        }
        if (p < 250) {
            return questGuideStage(p);
        }
        if (p < 370) {
            return miningStage(p);
        }
        if (p < 510) {
            return combatStage(p);
        }
        if (p < 540) {
            return bankPollStage(p);
        }
        if (p < 550) {
            return accountToPrayerStage();
        }
        if (p < 610) {
            return prayerStage(p);
        }
        return magicStage(p);
    }

    /** Varp 2–9: Gielinor Guide; 3–6 vraagt eerst de Settings-tab. */
    private Integer guideStage(int p) {
        if (p < 2) {
            return null;
        }
        if (p >= 3 && p <= 6 && openTab(Tab.OPTIONS, "Settings")) {
            return rand(900, 1400);
        }
        return talkAt("Gielinor Guide", GUIDE_TILE);
    }

    /** Varp 10–19: deur uit het guide-gebouw → pad zuidoost → Survival Expert. */
    private Integer toSurvivalStage(int p) {
        if (p <= 12 && QuestActions.dist(GUIDE_DOOR) <= 6 && openDoorAt(GUIDE_DOOR)) {
            return rand(500, 800);
        }
        if (p < 15 && survivalPath.step("pad Survival")) {
            return rand(500, 800);
        }
        return talkAt("Survival Expert", SURVIVAL_EXPERT_TILE);
    }

    /** Varp 20–119: net → vissen → vuur → koken. */
    private Integer survivalStage(int p) {
        if (!Inventory.contains(NET)) {
            return talkAt("Survival Expert", SURVIVAL_EXPERT_TILE);
        }
        if (p >= 30 && p < 40 && openTab(Tab.INVENTORY, "Inventory")) {
            return rand(900, 1300);
        }
        if (p < 40) {
            return talkAt("Survival Expert", SURVIVAL_EXPERT_TILE);
        }

        int raw = Inventory.getCount(RAW_SHRIMP);
        int cooked = Inventory.getCount(SHRIMP);

        if (raw >= SHRIMP_TARGET || (raw > 0 && cooked == 0 && hasFire())) {
            Integer cook = cookShrimp();
            if (cook != null) {
                return cook;
            }
        }
        if (raw > 0 && !hasFire()) {
            return makeFire();
        }
        if (raw == 0 && cooked == 0) {
            return fishShrimp();
        }
        if (cooked > 0) {
            if (openTab(Tab.SKILLS, "Skills")) {
                return rand(900, 1300);
            }
            return talkAt("Survival Expert", SURVIVAL_EXPERT_TILE);
        }
        return fishShrimp();
    }

    /** Varp 120–169: gate → keuken → deeg → brood bakken. */
    private Integer chefStage(int p) {
        if (Inventory.contains(DOUGH)) {
            if (!QuestActions.walkTo(CHEF_RANGE_STAND, 2)) {
                QuestLog.step("Tutorial", "→ range (brood bakken)");
                return rand(500, 800);
            }
            if (busySkilling()) {
                return rand(500, 800);
            }
            if (useOnObject(DOUGH, "Range", CHEF_RANGE_STAND)) {
                return rand(1200, 1700);
            }
            return rand(500, 800);
        }
        if (Inventory.contains(FLOUR) && Inventory.contains(WATER)) {
            if (QuestActions.useItemOnItem(FLOUR, WATER)) {
                QuestLog.step("Tutorial", "flour + water → bread dough");
                return rand(600, 900);
            }
        }
        if (Inventory.contains(BREAD)) {
            return talkAt("Master Chef", MASTER_CHEF_TILE);
        }
        if (QuestActions.dist(SURVIVAL_EXIT_GATE) <= 4 && openDoorAt(SURVIVAL_EXIT_GATE)) {
            return rand(500, 800);
        }
        if (QuestActions.dist(CHEF_DOOR_NORTH) <= 3 && openDoorAt(CHEF_DOOR_NORTH)) {
            return rand(500, 800);
        }
        if (p < 150 && !QuestActions.walkTo(MASTER_CHEF_TILE, 4)) {
            QuestLog.step("Tutorial", "→ Master Chef keuken");
            return rand(500, 800);
        }
        return talkAt("Master Chef", MASTER_CHEF_TILE);
    }

    /** Varp 170–249: zuiddeur uit keuken → westpad → Quest Guide + Quest-tab. */
    private Integer questGuideStage(int p) {
        if (isInsideChefKitchen() && openDoorAt(CHEF_DOOR_SOUTH)) {
            return rand(500, 800);
        }
        if (p >= 170 && p < 250 && QuestActions.dist(QUEST_GUIDE_TILE) <= 6
                && openTab(Tab.QUESTS, "Quest List")) {
            return rand(900, 1300);
        }
        if (QuestActions.dist(QUEST_GUIDE_DOOR) > 4 && questGuidePath.step("pad Quest Guide")) {
            return rand(500, 800);
        }
        if (QuestActions.dist(QUEST_GUIDE_DOOR) <= 2 && !isInQuestGuideRoom()
                && openDoorAt(QUEST_GUIDE_DOOR)) {
            return rand(500, 800);
        }
        return talkAt("Quest Guide", QUEST_GUIDE_TILE);
    }

    /** Varp 250–369: ladder → instructeur → tin/copper → furnace → anvil → gate oost. */
    private Integer miningStage(int p) {
        if (!isInDungeon()) {
            if (!QuestActions.walkTo(QUEST_GUIDE_LADDER, 1)) {
                QuestLog.step("Tutorial", "→ ladder mining");
                return rand(500, 800);
            }
            if (QuestActions.interactObject("Ladder", "Climb-down", QUEST_GUIDE_LADDER)) {
                return rand(1200, 1700);
            }
            return rand(500, 800);
        }
        if (p >= 360 || (Inventory.contains(DAGGER) && p >= 350)) {
            if (openDoorAt(MINING_EXIT_GATE)) {
                return rand(600, 900);
            }
            if (!QuestActions.walkTo(MINING_EXIT_GATE, 1)) {
                QuestLog.step("Tutorial", "→ gate combat");
                return rand(500, 800);
            }
            return rand(500, 800);
        }
        if (p >= 340) {
            return smithDagger();
        }
        if (p >= 320) {
            return smeltBar();
        }
        if (p >= 300) {
            return mineOres();
        }
        if (!Inventory.contains(PICKAXE)) {
            return talkAt("Mining Instructor", MINING_INSTRUCTOR_TILE);
        }
        return talkAt("Mining Instructor", MINING_INSTRUCTOR_TILE);
    }

    /** Varp 370–509: gear-tabs → dagger/sword+shield → rat melee → rat ranged → ladder. */
    private Integer combatStage(int p) {
        if (p >= 500) {
            if (!QuestActions.walkTo(COMBAT_EXIT_LADDER, 1)) {
                QuestLog.step("Tutorial", "→ ladder uit combat");
                return rand(500, 800);
            }
            if (QuestActions.interactObject("Ladder", "Climb-up", COMBAT_EXIT_LADDER)) {
                return rand(1200, 1700);
            }
            return rand(500, 800);
        }
        if (p >= 480) {
            if (Inventory.contains(SHORTBOW) && equip(SHORTBOW)) {
                return rand(600, 900);
            }
            if (Inventory.contains(ARROW) && equip(ARROW)) {
                return rand(600, 900);
            }
            if (isInRatPen()) {
                return leaveRatPen();
            }
            if (!QuestActions.walkTo(RAT_RANGE_STAND, 2)) {
                QuestLog.step("Tutorial", "→ ranging stand (buiten hok)");
                return rand(500, 800);
            }
            return attackRat("ranged");
        }
        if (p >= 470) {
            if (isInRatPen()) {
                return leaveRatPen();
            }
            return talkAt("Combat Instructor", COMBAT_INSTRUCTOR_TILE);
        }
        if (p >= 460) {
            if (isInRatPen()) {
                return leaveRatPen();
            }
            return null;
        }
        if (p >= 450) {
            if (!isInRatPen() && openDoorAt(RAT_PEN_GATE)) {
                return rand(600, 900);
            }
            return attackRat("melee");
        }
        if (p >= 440) {
            if (isInRatPen()) {
                return attackRat("melee");
            }
            if (openDoorAt(RAT_PEN_GATE)) {
                return rand(600, 900);
            }
            if (!QuestActions.walkTo(RAT_PEN_GATE, 1)) {
                QuestLog.step("Tutorial", "→ gate rattenhok");
                return rand(500, 800);
            }
            return rand(500, 800);
        }
        if (p >= 430) {
            if (openTab(Tab.COMBAT, "Combat Options")) {
                return rand(900, 1300);
            }
            return talkAt("Combat Instructor", COMBAT_INSTRUCTOR_TILE);
        }
        if (p >= 420) {
            if (Inventory.contains(SHIELD) && equip(SHIELD)) {
                return rand(600, 900);
            }
            if (Inventory.contains(SWORD) && equip(SWORD)) {
                return rand(600, 900);
            }
            return talkAt("Combat Instructor", COMBAT_INSTRUCTOR_TILE);
        }
        if (p >= 405) {
            if (Inventory.contains(DAGGER) && equip(DAGGER)) {
                return rand(600, 900);
            }
            if (clickWidget(EQUIP_CLOSE_GROUP, EQUIP_CLOSE_CHILD, "Close")) {
                QuestLog.step("Tutorial", "equipment-paneel sluiten");
                return rand(600, 900);
            }
            if (p >= 410) {
                return talkAt("Combat Instructor", COMBAT_INSTRUCTOR_TILE);
            }
            return rand(600, 900);
        }
        if (p >= 400) {
            if (openTab(Tab.EQUIPMENT, "Worn Equipment")) {
                return rand(900, 1300);
            }
            if (clickWidget(EQUIP_STATS_GROUP, EQUIP_STATS_CHILD, "View equipment stats")) {
                QuestLog.step("Tutorial", "View equipment stats");
                return rand(900, 1300);
            }
            return rand(600, 900);
        }
        if (p >= 390) {
            if (openTab(Tab.EQUIPMENT, "Worn Equipment")) {
                return rand(900, 1300);
            }
            return rand(600, 900);
        }
        return talkAt("Combat Instructor", COMBAT_INSTRUCTOR_TILE);
    }

    /** Varp 510–539: bank booth → poll booth → deur → Account Management + Account Guide. */
    private Integer bankPollStage(int p) {
        if (p >= 530) {
            if (openTab(Tab.ACCOUNT, "Account Management")) {
                return rand(900, 1300);
            }
            return talkAt("Account Guide", ACCOUNT_GUIDE_TILE);
        }
        if (p >= 525) {
            if (openDoorAt(ACCOUNT_POLL_DOOR)) {
                return rand(600, 900);
            }
            if (!QuestActions.walkTo(ACCOUNT_POLL_DOOR, 1)) {
                QuestLog.step("Tutorial", "→ deur Account Guide");
                return rand(500, 800);
            }
            return rand(500, 800);
        }
        if (p >= 520) {
            if (!QuestActions.walkTo(POLL_BOOTH_TILE, 3)) {
                QuestLog.step("Tutorial", "→ poll booth");
                return rand(500, 800);
            }
            if (QuestActions.interactObject("Poll booth", "Use", POLL_BOOTH_TILE)) {
                return rand(1200, 1700);
            }
            return rand(600, 900);
        }
        if (!QuestActions.walkTo(BANK_BOOTH_TILE, 3)) {
            QuestLog.step("Tutorial", "→ bank booth");
            return rand(500, 800);
        }
        if (QuestActions.interactObject("Bank booth", "Use", BANK_BOOTH_TILE)) {
            return rand(1200, 1700);
        }
        return rand(600, 900);
    }

    /** Varp 540–549: deur account-gebouw → Brother Brace. */
    private Integer accountToPrayerStage() {
        if (openDoorAt(ACCOUNT_TO_PRAYER_DOOR)) {
            return rand(600, 900);
        }
        if (!QuestActions.walkTo(ACCOUNT_TO_PRAYER_DOOR, 1)) {
            QuestLog.step("Tutorial", "→ deur Prayer");
            return rand(500, 800);
        }
        return rand(500, 800);
    }

    /** Varp 550–609: Brother Brace + Prayer-tab. */
    private Integer prayerStage(int p) {
        if (p >= 560 && p < 570 && openTab(Tab.PRAYER, "Prayer")) {
            return rand(900, 1300);
        }
        return talkAt("Brother Brace", BROTHER_BRACE_TILE);
    }

    /** Varp 610–999: deur → Magic Instructor → Magic-tab → Wind Strike → Home Teleport. */
    private Integer magicStage(int p) {
        if (p >= 680) {
            if (Magic.canCast(SpellBook.Standard.HOME_TELEPORT)
                    && Magic.cast(SpellBook.Standard.HOME_TELEPORT)) {
                QuestLog.step("Tutorial", "Home Teleport → Lumbridge");
                return rand(2000, 2600);
            }
            QuestLog.step("Tutorial", "wacht op Home Teleport (cooldown/anim)");
            return rand(1200, 1700);
        }
        if (p >= 650 && p < 670) {
            INPC chicken = NPCs.getNearest("Chicken");
            if (chicken != null && Magic.cast(SpellBook.Standard.WIND_STRIKE, chicken)) {
                QuestLog.step("Tutorial", "Wind Strike op Chicken");
                return rand(1400, 2000);
            }
            if (chicken == null) {
                return talkAt("Magic Instructor", MAGIC_INSTRUCTOR_TILE);
            }
            return rand(700, 1100);
        }
        if (p >= 630 && p < 640 && openTab(Tab.MAGIC, "Magic spellbook")) {
            return rand(900, 1300);
        }
        if (p < 620) {
            WorldPoint me = QuestActions.local();
            if (me != null && me.getY() > 3110) {
                if (openDoorAt(PRAYER_TO_MAGIC_DOOR)) {
                    return rand(600, 900);
                }
                if (!QuestActions.walkTo(PRAYER_TO_MAGIC_DOOR, 1)) {
                    QuestLog.step("Tutorial", "→ deur Magic");
                    return rand(500, 800);
                }
                return rand(500, 800);
            }
            if (QuestActions.dist(MAGIC_INSTRUCTOR_TILE) > 6 && magicPath.step("pad Magic")) {
                return rand(500, 800);
            }
        }
        return talkAt("Magic Instructor", MAGIC_INSTRUCTOR_TILE);
    }

    // ------------------------------------------------------------- survival

    private Integer fishShrimp() {
        if (busySkilling()) {
            return rand(600, 900);
        }
        INPC spot = NPCs.getNearest("Fishing spot");
        if (spot == null) {
            if (!QuestActions.walkTo(FISHING_SPOT_TILE, 2)) {
                QuestLog.step("Tutorial", "→ fishing spot");
                return rand(500, 800);
            }
            return rand(600, 900);
        }
        WorldPoint tile = spot.getWorldLocation();
        if (tile != null && QuestActions.dist(tile) > 5) {
            QuestActions.walkTo(tile, 2);
            QuestLog.step("Tutorial", "→ fishing spot (net)");
            return rand(500, 800);
        }
        if (throttleSkillClick() && spot.interact("Net")) {
            QuestLog.step("Tutorial", "Net fishing spot");
            return rand(1200, 1700);
        }
        return rand(500, 800);
    }

    private Integer makeFire() {
        if (busySkilling()) {
            return rand(600, 900);
        }
        if (!Inventory.contains(LOGS)) {
            if (!Inventory.contains(AXE)) {
                return talkAt("Survival Expert", SURVIVAL_EXPERT_TILE);
            }
            if (QuestActions.interactObject("Tree", "Chop down", null)) {
                QuestLog.step("Tutorial", "Chop down Tree (logs voor vuur)");
                return rand(1200, 1700);
            }
            return rand(600, 900);
        }
        if (!Inventory.contains(TINDERBOX)) {
            return talkAt("Survival Expert", SURVIVAL_EXPERT_TILE);
        }
        if (!QuestActions.walkTo(FIRE_LIGHT_TILE, 3)) {
            QuestLog.step("Tutorial", "→ vuur-tegel");
            return rand(500, 800);
        }
        if (QuestActions.useItemOnItem(TINDERBOX, LOGS)) {
            QuestLog.step("Tutorial", "Tinderbox → Logs");
            return rand(1400, 2000);
        }
        return rand(600, 900);
    }

    private Integer cookShrimp() {
        if (busySkilling()) {
            return rand(600, 900);
        }
        ITileObject fire = TileObjects.getNearest("Fire");
        if (fire == null) {
            return null;
        }
        WorldPoint tile = fire.getWorldLocation();
        if (tile != null && QuestActions.dist(tile) > 4) {
            QuestActions.walkTo(tile, 2);
            QuestLog.step("Tutorial", "→ vuur (koken)");
            return rand(500, 800);
        }
        IInventoryItem raw = Inventory.getFirst(RAW_SHRIMP);
        if (raw != null && throttleSkillClick() && raw.useOn(fire)) {
            QuestLog.step("Tutorial", "Raw shrimps → Fire");
            return rand(1400, 2000);
        }
        return rand(600, 900);
    }

    private boolean hasFire() {
        return TileObjects.getNearest("Fire") != null;
    }

    // --------------------------------------------------------------- mining

    private Integer mineOres() {
        if (busySkilling()) {
            return rand(600, 900);
        }
        if (!Inventory.contains(PICKAXE)) {
            return talkAt("Mining Instructor", MINING_INSTRUCTOR_TILE);
        }
        String ore = !Inventory.contains(TIN_ORE) ? "Tin" : !Inventory.contains(COPPER_ORE) ? "Copper" : null;
        if (ore == null) {
            return null;
        }
        ITileObject rock = TileObjects.getNearest(o -> o != null && o.getName() != null
                && o.getName().toLowerCase().contains(ore.toLowerCase())
                && (o.hasAction("Mine") || o.hasAction("Prospect")));
        if (rock == null) {
            QuestLog.step("Tutorial", ore + " rock niet in scene");
            return null;
        }
        WorldPoint tile = rock.getWorldLocation();
        if (tile != null && QuestActions.dist(tile) > 6) {
            QuestActions.walkTo(tile, 1);
            QuestLog.step("Tutorial", "→ " + ore + " rock");
            return rand(500, 800);
        }
        if (throttleSkillClick() && rock.interact("Mine")) {
            QuestLog.step("Tutorial", "Mine " + ore + " rock");
            return rand(1400, 2000);
        }
        return rand(600, 900);
    }

    private Integer smeltBar() {
        if (busySkilling()) {
            return rand(600, 900);
        }
        if (Inventory.contains(BRONZE_BAR)) {
            return null;
        }
        if (!Inventory.contains(TIN_ORE) && !Inventory.contains(COPPER_ORE)) {
            return mineOres();
        }
        if (!QuestActions.walkTo(FURNACE_TILE, 3)) {
            QuestLog.step("Tutorial", "→ furnace");
            return rand(500, 800);
        }
        if (useOnObject(TIN_ORE, "Furnace", FURNACE_TILE)
                || useOnObject(COPPER_ORE, "Furnace", FURNACE_TILE)) {
            QuestLog.step("Tutorial", "ore → Furnace (bronze bar)");
            return rand(1400, 2000);
        }
        return rand(600, 900);
    }

    private Integer smithDagger() {
        if (Inventory.contains(DAGGER)) {
            return null;
        }
        if (clickWidget(SMITH_GROUP, SMITH_DAGGER_CHILD, "Smith 1", "Smith", "Dagger")) {
            QuestLog.step("Tutorial", "smith Bronze dagger (312,9)");
            return rand(1400, 2000);
        }
        if (busySkilling()) {
            return rand(600, 900);
        }
        if (!Inventory.contains(BRONZE_BAR)) {
            return smeltBar();
        }
        if (!Inventory.contains(HAMMER)) {
            return talkAt("Mining Instructor", MINING_INSTRUCTOR_TILE);
        }
        if (!QuestActions.walkTo(ANVIL_TILE, 2)) {
            QuestLog.step("Tutorial", "→ anvil");
            return rand(500, 800);
        }
        if (useOnObject(BRONZE_BAR, "Anvil", ANVIL_TILE)) {
            QuestLog.step("Tutorial", "Bronze bar → Anvil");
            return rand(1200, 1700);
        }
        return rand(600, 900);
    }

    // --------------------------------------------------------------- combat

    private Integer attackRat(String style) {
        Players.LocalSnap me = Players.snapshotLocal();
        if (me.present && me.interacting) {
            QuestLog.step("Tutorial", "rat in combat (" + style + ")");
            return rand(1200, 1700);
        }
        INPC rat = NPCs.getNearest(n -> n != null && n.getName() != null
                && n.getName().toLowerCase().contains("rat") && n.hasAction("Attack"));
        if (rat == null) {
            QuestLog.step("Tutorial", "geen Giant rat in scene (" + style + ")");
            return null;
        }
        if (rat.interact("Attack")) {
            QuestLog.step("Tutorial", "Attack Giant rat (" + style + ")");
            return rand(1200, 1700);
        }
        return rand(600, 900);
    }

    private Integer leaveRatPen() {
        if (openDoorAt(RAT_PEN_GATE)) {
            return rand(600, 900);
        }
        if (!QuestActions.walkTo(RAT_RANGE_STAND, 1)) {
            QuestLog.step("Tutorial", "uit rattenhok → instructeur");
            return rand(500, 800);
        }
        return rand(500, 800);
    }

    private static boolean isInRatPen() {
        WorldPoint me = QuestActions.local();
        return me != null && me.getX() >= RAT_PEN_INSIDE.getX() - 2 && me.getX() <= RAT_PEN_INSIDE.getX() + 4
                && me.getY() >= RAT_PEN_INSIDE.getY() - 3 && me.getY() <= RAT_PEN_INSIDE.getY() + 4;
    }

    // --------------------------------------------------------------- shared

    private Integer talkAt(String name, WorldPoint stand) {
        INPC npc = NPCs.getNearest(name);
        if (npc == null) {
            if (stand == null) {
                return null;
            }
            if (!QuestActions.walkTo(stand, 2)) {
                QuestLog.step("Tutorial", "→ " + name + " " + stand.getX() + "," + stand.getY());
                return rand(500, 800);
            }
            QuestLog.step("Tutorial", name + " niet in scene @ stand-tegel");
            return rand(600, 900);
        }
        WorldPoint tile = npc.getWorldLocation();
        if (tile != null && QuestActions.dist(tile) > 4) {
            QuestActions.walkTo(tile, 2);
            QuestLog.step("Tutorial", "→ " + name);
            return rand(500, 800);
        }
        if (npc.interact("Talk-to")) {
            QuestLog.step("Tutorial", "Talk-to " + name);
            return rand(1000, 1500);
        }
        return rand(600, 900);
    }

    private boolean openTab(Tab tab, String label) {
        try {
            if (Tabs.isOpen(tab)) {
                return false;
            }
            Tabs.open(tab);
            QuestLog.step("Tutorial", "tab " + label);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean equip(String name) {
        IInventoryItem item = Inventory.getFirst(name);
        if (item == null || Equipment.contains(name)) {
            return false;
        }
        if (item.interact("Wield") || item.interact("Equip")) {
            QuestLog.step("Tutorial", "Wield " + name);
            return true;
        }
        return false;
    }

    private static boolean useOnObject(String item, String object, WorldPoint near) {
        return QuestActions.useItemOnObject(item, object, near);
    }

    /** Deur/gate/barrier op een vaste tegel — alleen {@code Open} binnen 2 tegels. */
    private static boolean openDoorAt(WorldPoint tile) {
        if (tile == null || QuestActions.dist(tile) > 3) {
            return false;
        }
        ITileObject door = TileObjects.getNearest(tile, o -> {
            if (o == null || o.getWorldLocation() == null) {
                return false;
            }
            return o.getWorldLocation().distanceTo(tile) <= 2 && o.hasAction("Open");
        });
        if (door == null) {
            return false;
        }
        if (door.interact("Open")) {
            QuestLog.step("Tutorial", "Open " + door.getName() + " @ " + tile.getX() + "," + tile.getY());
            return true;
        }
        return false;
    }

    /** Bank/poll/tutorial-overlay wegklikken zodat de varp kan doorlopen. */
    private boolean closeTutorialPanels(int p) {
        if (p >= 510 && p < 525 && clickWidget(BANK_CLOSE_GROUP, BANK_CLOSE_CHILD, "Close")) {
            QuestLog.step("Tutorial", "bank-interface sluiten");
            return true;
        }
        if (p >= 510 && p < 535 && clickWidget(OVERLAY_GROUP, OVERLAY_CHILD, "Close")) {
            QuestLog.step("Tutorial", "tutorial-overlay sluiten");
            return true;
        }
        return false;
    }

    private static boolean clickWidget(int group, int child, String... actions) {
        IWidget w = Widgets.get(group, child);
        if (w == null || w.isHidden()) {
            return false;
        }
        for (String a : actions) {
            if (w.interact(a)) {
                return true;
            }
        }
        return w.interact("Continue") || w.interact("Ok");
    }

    private static boolean isInDungeon() {
        WorldPoint me = QuestActions.local();
        return me != null && me.getY() >= DUNGEON_MIN_Y && me.getY() <= DUNGEON_MAX_Y;
    }

    private static boolean isInQuestGuideRoom() {
        WorldPoint me = QuestActions.local();
        return me != null && me.getX() >= 3082 && me.getX() <= 3090
                && me.getY() >= 3118 && me.getY() <= 3125;
    }

    private static boolean isInsideChefKitchen() {
        WorldPoint me = QuestActions.local();
        return me != null && me.getX() >= 3073 && me.getX() <= 3078
                && me.getY() >= 3081 && me.getY() <= 3086;
    }

    /** Animatie loopt (vissen/koken/minen/smelten) — niet opnieuw klikken. */
    private static boolean busySkilling() {
        Players.LocalSnap me = Players.snapshotLocal();
        return me.present && me.animating;
    }

    private boolean throttleSkillClick() {
        long now = System.currentTimeMillis();
        if (now - lastSkillClickMs < SKILL_CLICK_GAP_MS) {
            return false;
        }
        lastSkillClickMs = now;
        return true;
    }

    private void trackVarp(int p) {
        if (p == lastVarp) {
            return;
        }
        if (lastVarp >= 0) {
            QuestLog.force("Tutorial", "varp 281 " + lastVarp + " → " + p);
        }
        lastVarp = p;
    }

    private static int varp() {
        try {
            return Vars.getVarp(VARP);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int rand(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min);
    }

    // ----------------------------------------------------------- onboarding

    private boolean handleCreator() {
        IWidget root = Widgets.get(IFACE_CREATOR, 0);
        if (root == null || root.isHidden()) {
            return false;
        }
        QuestLog.step("Tutorial", "character creator (679)");
        if (clickText(IFACE_CREATOR, "Accept") || clickText(IFACE_CREATOR, "Confirm")
                || clickText(IFACE_CREATOR, "Continue")) {
            return true;
        }
        IWidget accept = Widgets.get(IFACE_CREATOR, 74);
        if (accept != null && !accept.isHidden() && accept.interact("Accept")) {
            return true;
        }
        return true;
    }

    private boolean handleDisplayName() {
        IWidget root = Widgets.get(IFACE_NAME, 0);
        if (root == null || root.isHidden()) {
            return false;
        }
        QuestLog.step("Tutorial", "display name (558)");
        if (clickText(IFACE_NAME, "Look up name") || clickText(IFACE_NAME, "Set name")
                || clickText(IFACE_NAME, "Confirm") || clickText(IFACE_NAME, "Continue")) {
            return true;
        }
        Keyboard.pressKey(KeyEvent.VK_ENTER);
        return true;
    }

    // ----------------------------------------------------------- hint arrow

    /** Failsafe voor niet-gemodelleerde captures: volg de hint-pijl (NPC of object). */
    private boolean followHint() {
        INPC hintNpc = NPCs.getHintArrowed();
        if (hintNpc != null) {
            WorldPoint t = hintNpc.getWorldLocation();
            if (t != null && QuestActions.dist(t) > 2) {
                QuestLog.step("Tutorial", "hint-NPC " + hintNpc.getName());
                QuestActions.walkTo(t, 2);
                return true;
            }
            if (hintNpc.interact("Talk-to") || hintNpc.interact("Attack")) {
                QuestLog.step("Tutorial", "hint interact " + hintNpc.getName());
                return true;
            }
        }
        ITileObject wrap = hintObject();
        if (wrap == null) {
            return false;
        }
        String[] acts = {"Chop down", "Use", "Open", "Climb-up", "Climb-down",
                "Prospect", "Mine", "Smelt", "Smith", "Bank", "Pray-at",
                "Climb-over", "Open-door", "Light", "Net", "Cast"};
        for (String a : acts) {
            if (wrap.hasAction(a) && wrap.interact(a)) {
                QuestLog.step("Tutorial", a + " hint-object");
                return true;
            }
        }
        WorldPoint t = wrap.getWorldLocation();
        if (t != null && QuestActions.dist(t) > 2) {
            QuestActions.walkTo(t, 2);
            return true;
        }
        return wrap.interact("Use");
    }

    private static ITileObject hintObject() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                Object raw = c.getClass().getMethod("getHintArrowGameObject").invoke(c);
                if (raw instanceof net.runelite.api.TileObject) {
                    net.runelite.api.TileObject to = (net.runelite.api.TileObject) raw;
                    WorldPoint wp = to.getWorldLocation();
                    return wp != null ? TileObjects.getNearest(wp, to.getId()) : null;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }, null);
    }

    private static boolean clickText(int group, String text) {
        IWidget w = Widgets.get(group, t -> {
            String s = t.getText();
            return s != null && s.toLowerCase().contains(text.toLowerCase());
        });
        if (w != null && !w.isHidden()) {
            return w.interact(text) || w.interact("Continue") || w.interact("OK");
        }
        return false;
    }

    /** Vaste waypoint-route; index loopt mee met de positie (CombatBot {@code sync*PathIdx}). */
    private static final class PathWalk {

        private final WorldPoint[] path;
        private int idx;

        private PathWalk(WorldPoint... path) {
            this.path = path;
        }

        private void reset() {
            idx = 0;
        }

        /**
         * @return {@code true} while still walking the route
         */
        private boolean step(String label) {
            syncFromPosition();
            if (idx >= path.length) {
                return false;
            }
            WorldPoint wp = path[idx];
            if (QuestActions.walkTo(wp, 3)) {
                idx++;
                return idx < path.length;
            }
            QuestLog.step("Tutorial", label + " " + (idx + 1) + "/" + path.length
                    + " → " + wp.getX() + "," + wp.getY());
            return true;
        }

        private void syncFromPosition() {
            WorldPoint me = QuestActions.local();
            if (me == null) {
                return;
            }
            for (int i = path.length - 1; i >= 0; i--) {
                if (me.distanceTo(path[i]) <= 4) {
                    idx = Math.max(idx, i + 1);
                    return;
                }
            }
        }
    }
}
