package com.lonebot.example.quest.scripts;

import com.lonebot.example.quest.QuestBotScript;
import com.lonebot.example.quest.QuestBotTarget;
import com.lonebot.example.quest.QuestLog;
import com.lonebot.example.quest.helpers.QuestBankGePrep;
import com.lonebot.example.quest.helpers.QuestGePrices;
import com.lonebot.example.quest.helpers.QuestImpLootHints;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.domain.tiles.ITileObject;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.magic.SpellBook;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.entities.TileObjects;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Combat;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.Vars;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.BankSnapshot;
import net.storm.sdk.items.BankWithdrawHelper;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.items.MeleeWeaponRequirements;
import net.storm.sdk.items.Shop;
import net.storm.sdk.magic.Magic;
import net.storm.sdk.movement.Movement;
import net.storm.sdk.quests.Quests;
import net.storm.sdk.widgets.Dialog;
import net.storm.sdk.widgets.Widgets;

import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Random;

/**
 * Vampire Slayer — full CombatBot {@code VampireSlayerQuestHandler} port (VarPlayer 178).
 * <p>LoneBot APIs: {@link QuestBotScript}, {@link QuestLog}, {@link QuestBankGePrep},
 * {@link QuestGePrices}, {@link QuestImpLootHints}, {@link SpellBook.Standard},
 * {@link MeleeWeaponRequirements}, {@link Movement}, {@link BankWithdrawHelper}.
 *
 * <ul>
 *   <li>0 — Morgan start</li>
 *   <li>1 — garlic / beer+Harlow stake / hammer (bank→Imp→GE→shop) / combat kit</li>
 *   <li>2 — manor → crypt → coffin once → Count (Fire→Wind→melee)</li>
 *   <li>3 — done</li>
 * </ul>
 */
public final class VampireSlayerScript implements QuestBotScript {

    /** VarPlayer ID voor Vampyre Slayer (vast in OSRS). */
    private static final int VARP_VAMPYRE_SLAYER = 178;

    private static final WorldPoint MORGAN_TILE = new WorldPoint(3098, 3268, 0);
    /** Veilige tegel binnen Morgan's huis; pathfinder opent benodigde deuren zelf. */
    private static final WorldPoint MORGAN_HOUSE_INSIDE_TILE = new WorldPoint(3098, 3267, 0);
    private static final WorldPoint UPSTAIRS_AREA = new WorldPoint(3100, 3267, 1);
    /** Cupboard-object op noordmuur (ID 2612/2613) — niet naar deze muurtegel lopen. */
    private static final WorldPoint GARLIC_CUPBOARD_TILE = new WorldPoint(3102, 3266, 1);
    /** Loopbare tegel vóór de cupboard (zuid van de muur). */
    private static final WorldPoint GARLIC_CUPBOARD_APPROACH = new WorldPoint(3101, 3266, 1);
    /**
     * Trap-top → langs bed → cupboard. Muur-object is niet bereikbaar; pad om het bed heen.
     * Hover-captures kunnen dit verfijnen.
     */
    private static final WorldPoint[] MORGAN_UPSTAIRS_GARLIC_PATH = {
            new WorldPoint(3100, 3268, 1),
            new WorldPoint(3100, 3267, 1),
            GARLIC_CUPBOARD_APPROACH,
    };
    private static final int GARLIC_PATH_NODE_REACHED = 2;
    /** Zoekradius rond speler op plane 1. */
    private static final int CUPBOARD_SEARCH_NEAR_PLAYER_DIST = 15;
    /** Alleen trappen/kast binnen deze afstand van Morgan (voorkomt verkeerde trap elders in Draynor). */
    private static final int MORGAN_HOUSE_OBJECT_MAX_DIST = 14;
    /** Pas garlic/trap-logica uit als we echt bij Morgan in Draynor zijn (niet in Varrock Blue Moon e.d.). */
    private static final int GARLIC_MORGAN_AREA_MAX_DIST = 28;
    private static final WorldPoint DRAYNOR_BANK_TILE = new WorldPoint(3093, 3243, 0);
    private static final WorldPoint HARLOW_TILE = new WorldPoint(3222, 3398, 0);
    private static final WorldPoint BARTENDER_TILE = new WorldPoint(3226, 3400, 0);
    private static final WorldPoint VARROCK_GEN_STORE = new WorldPoint(3217, 3415, 0);
    /** Tegel vóór de hoofdingang Draynor Manor (lopen naar deze punt). */
    private static final WorldPoint MANOR_DOOR_STAND_TILE = new WorldPoint(3108, 3351, 0);
    /** Hoofdingang — interactie "Open Large door". */
    private static final WorldPoint MANOR_LARGE_DOOR_TILE = new WorldPoint(3108, 3353, 0);
    /** Binnen begane grond: trap naar kelder/crypt — "Walk-down Stairs" (object op 3115,3357). */
    private static final WorldPoint MANOR_STAIRS_DOWN_TILE = new WorldPoint(3115, 3357, 0);
    /**
     * Grote voordeur staat op Y=3353; alles op begane grond met Y≥3354 is binnen het manor (niet meer via voordeur).
     */
    private static final int MANOR_INTERIOR_MIN_Y = 3354;
    /** Coffin in de Draynor Manor crypt. */
    private static final WorldPoint COFFIN_TILE = new WorldPoint(3078, 9772, 0);

    private static final int CUPBOARD_ID = 2612;
    /** Geopende variant van de garlic-cupboard. */
    private static final int CUPBOARD_OPEN_ID = 2613;
    /** Draynor Manor begane grond — trap naar beneden (Object ID, RuneLite overlay). */
    private static final int MANOR_STAIRS_DOWN_OBJECT_ID = 2616;
    /** Bier voor Dr Harlow in de Blue Moon Inn kost 2 gp; dit is de minimum inventory-check. */
    private static final int MIN_COINS_BEER = 2;

    /** Shop interface widget group. */
    private static final int SHOP_WIDGET_GROUP = 300;
    /** Per-fase failsafe — als we langer dan dit op één fase zitten, reset state. */
    private static final long PHASE_FAILSAFE_MS = 5 * 60 * 1000L;
    /** Minimaal aantal runes (inv + equipment) om mage vs Count te blijven gebruiken. */
    private static final int MIN_RUNES_COUNT_FIGHT = 5;
    /** Low-level safety: neem wat food mee als het in de bank ligt. */
    private static final int MIN_QUEST_FOOD = 5;
    /** Count-gevecht: voorkeur magic — Fire Strike → Wind Strike (Air) → melee. */
    private static final int MIN_QUEST_MAGE_MIND_RUNES = 40;
    private static final int MIN_QUEST_MAGE_AIR_RUNES = 80;
    private static final String[] QUEST_MAGE_STAFF_PRIORITY = {
            "Staff of fire", "Mystic fire staff", "Fire staff",
            "Staff of air", "Mystic air staff", "Air staff",
            "Staff"
    };
    private static final String[] QUEST_MELEE_BANK_PRIORITY = {
            "Rune scimitar", "Adamant scimitar", "Mithril scimitar", "Steel scimitar",
            "Rune sword", "Adamant sword", "Mithril sword", "Steel sword", "Iron scimitar", "Bronze scimitar"
    };
    private static final String[] QUEST_FOOD_PRIORITY = {
            "Swordfish", "Lobster", "Tuna", "Salmon", "Trout", "Herring", "Sardine",
            "Shrimps", "Anchovies", "Cooked meat", "Cooked chicken", "Bread"
    };

    private final Random random = new Random();
    private long lastChatPollMs;

    // ---- QuestBotScript -------------------------------------------------

    @Override
    public String displayName() {
        return "Vampire Slayer";
    }

    @Override
    public QuestBotTarget target() {
        return QuestBotTarget.VAMPYRE_SLAYER;
    }

    @Override
    public Quest runeliteQuest() {
        return Quest.VAMPYRE_SLAYER;
    }

    @Override
    public void reset() {
        lastWalkMs = 0L;
        lastNpcMs = 0L;
        lastObjMs = 0L;
        lastDialogMs = 0L;
        lastShopMs = 0L;
        lastFallbackDialogSig = "";
        sameFallbackHits = 0;
        harlowSaidGoFindStake = false;
        harlowSaidGoFindStakeMs = 0L;
        draynorBankStakeCheckCount = 0;
        questBlockedStakeMissing = false;
        questBlockedStakeMissingMs = 0L;
        notOurCountUntilMs.clear();
        lastAttackedCountIndex = -1;
        lastAttackedCountClickMs = 0L;
        lastBankActionMs = 0L;
        lastEatMs = 0L;
        coffinOpenMs = 0L;
        lastCoffinInteractMs = 0L;
        coffinOpenedOnce = false;
        attackedCount = false;
        inBankSession = false;
        visitedDraynorBank = false;
        outOfFoodForQuest = false;
        recentDoorMs.clear();
        morganDoorClickMs = 0L;
        lastPhase = -1;
        phaseStartMs = 0L;
        lastMeleeEquipMs = 0L;
        countCombatBankNoProgressPasses = 0;
        lastChatPollMs = 0L;
    }

    @Override
    public boolean isFinished() {
        return Quests.isFinished(Quest.VAMPYRE_SLAYER) || readQuestVar() >= 3;
    }


    private long lastWalkMs;
    private long lastNpcMs;
    private long lastObjMs;
    private long lastDialogMs;
    private long lastShopMs;
    /** Signature van de laatste fallback-dialog (option-tekst) + hoeveel keer dezelfde fallback achter elkaar.
     * Wordt gebruikt om bij ≥2 mislukte chooseOption(0)-pogingen over te schakelen op een keyboard-cijfer. */
    private String lastFallbackDialogSig = "";
    private int sameFallbackHits = 0;
    /**
     * Wordt ge-set zodra Dr Harlow zegt: "Yesh. You must've put it somewhere. Go find it."
     * Betekent: Harlow geeft GEEN nieuwe stake. De stake ligt elders (bank of Death's office).
     * Reset bij stake in inventory of bij phase-change.
     */
    private boolean harlowSaidGoFindStake = false;
    private long harlowSaidGoFindStakeMs = 0L;
    /** Aantal keer dat we de Draynor bank hebben gecheckt op stake na Harlow's "go find it". */
    private int draynorBankStakeCheckCount = 0;
    /**
     * Hard signaal naar de plugin: stake is nergens beschikbaar (niet in inv, niet in bank,
     * Harlow weigert). Plugin moet de quest tijdelijk uitschakelen en doorrouteren naar
     * een andere skill (of account-switch / logout als geen alternatief is).
     * Reset bij phase-change of zodra de plugin het signaal opgepakt heeft.
     */
    private boolean questBlockedStakeMissing = false;
    private long questBlockedStakeMissingMs = 0L;
    /**
     * Blacklist van Count-NPC-indices die NIET van ons zijn (Draynor manor crypt is geïnstantieerd
     * per coffin-open, dus elke speler heeft z'n eigen Count). Gevuld via chat-message
     * "The vampyre doesn't seem interested in fighting you." Sleutel = NPC index; waarde = expireMs.
     */
    private final java.util.Map<Integer, Long> notOurCountUntilMs = new java.util.HashMap<>();
    /** Korte tracker voor de NPC waar we laatst op klikten (om bij de chat-error te blacklisten). */
    private int lastAttackedCountIndex = -1;
    private long lastAttackedCountClickMs = 0L;
    /** Hoe lang we een Count blacklisten nadat hij "doesn't seem interested" zei. */
    private static final long NOT_OUR_COUNT_BLACKLIST_MS = 45_000L;
    private long lastBankActionMs;
    private long lastEatMs;
    private long coffinOpenMs;
    private long lastCoffinInteractMs;
    /** Eén keer coffin Open/Search; pas na failsafe opnieuw. */
    private boolean coffinOpenedOnce;
    private boolean attackedCount;
    private boolean inBankSession = false;
    private boolean visitedDraynorBank = false;
    private boolean outOfFoodForQuest = false;
    private final Deque<Long> recentDoorMs = new ArrayDeque<>();
    private long morganDoorClickMs;

    private int lastPhase = -1;
    private long phaseStartMs = 0L;
    private long lastMeleeEquipMs;
    /** Bank-pogingen zonder combat-kit progress (voorkomt eindeloos bank-loop). */
    private int countCombatBankNoProgressPasses;


    @Override
    public int loop() {
        IPlayer local = Players.getLocal();
        if (local == null || local.getWorldLocation() == null) {
            return 1000;
        }
        pollChatMessages();

        // 1. Eerst altijd dialoog/menu's afhandelen — ongeacht stap.
        Integer dialogDelay = handleDialog();
        if (dialogDelay != null) {
            return dialogDelay;
        }

        int eatDelay = tryEatAtOrBelowFiveHp();
        if (eatDelay > 0) {
            return eatDelay;
        }

        int questVar = readQuestVar();
        trackPhase(questVar);
        QuestLog.step("Vamp", "varp=" + questVar);

        // FIX #1: Bank wordt NIET meer blind gesloten — alleen als we niet midden in een
        // bank-sessie zitten (visitDraynorBank zet inBankSession).
        if (Bank.isOpen() && !inBankSession) {
            Bank.close();
            return randomDelay(400, 800);
        }

        switch (questVar) {
            case 0:
                return phaseStart(local);
            case 1:
                return phasePrepareAndHarlow(local);
            case 2:
                return phaseManorAndCount(local);
            case 3:
            default:
                return finishComplete();
        }
    }

    public boolean isOutOfFoodForQuest() {
        return outOfFoodForQuest;
    }

    public void resetOutOfFoodForQuest() {
        outOfFoodForQuest = false;
    }

    /** Reset per-fase failsafe state als de fase wijzigt. */
    private void trackPhase(int phase) {
        long now = System.currentTimeMillis();
        if (phase != lastPhase) {
            lastPhase = phase;
            phaseStartMs = now;
            visitedDraynorBank = false;
            inBankSession = false;
            countCombatBankNoProgressPasses = 0;
            lastMeleeEquipMs = 0L;
            if (phase == 2) {
                coffinOpenedOnce = false;
                attackedCount = false;
                coffinOpenMs = 0L;
            }
            // Bij volledige reset (varp 0 of 3): ook de Harlow-stake-flag resetten.
            if (phase == 0 || phase == 3) {
                harlowSaidGoFindStake = false;
                draynorBankStakeCheckCount = 0;
                questBlockedStakeMissing = false;
            }
            QuestLog.force("Vamp", "Fase gewijzigd → varp=" + phase);
        } else if (phaseStartMs > 0 && now - phaseStartMs > PHASE_FAILSAFE_MS) {
            QuestLog.force("Vamp", "Failsafe: fase " + phase + " > 5 min, reset state");
            phaseStartMs = now;
            visitedDraynorBank = false;
            inBankSession = false;
            countCombatBankNoProgressPasses = 0;
            attackedCount = false;
            coffinOpenedOnce = false;
            coffinOpenMs = 0L;
            // Failsafe ook de bank-check counter resetten zodat we opnieuw kunnen verifiëren.
            draynorBankStakeCheckCount = 0;
        }
    }

    // ===================================================================
    // Dialog handling — gebruikt Storm SDK Dialog API
    // ===================================================================

    /**
     * Handelt elk open dialoog of optie-menu af.
     * @return delay in ms, of null als geen dialoog open is.
     */
    private Integer handleDialog() {
        if (!Dialog.isOpen()) {
            return null;
        }
        // throttle dialog clicks
        long now = System.currentTimeMillis();
        if (now - lastDialogMs < 350) {
            return 200;
        }
        lastDialogMs = now;

        if (Dialog.isViewingOptions()) {
            // FIX #2: Korte woorden ("Yes") moeten exact matchen, lange substrings via contains.
            String[] exactPreferred = {
                    "Yes", "Yes.", "Yes please.", "Yes, I will.",
                    // Blue Moon Inn / barman (OSRS): bier wordt "ale" genoemd, niet "beer"
                    "A glass of your finest ale please.",
                    // Dr Harlow — 1e gesprek (wiki transcript)
                    "Morgan needs your help!",
                    "His village is being terrorised by a vampyre! He told me to ask you about how I can stop it.",
                    "His village is being terrorized by a vampyre! He told me to ask you about how I can stop it.",
                    "But this is your friend Morgan we're talking about!",
                    // Dr Harlow — 2e gesprek met bier op zak: bier afgeven, daarna stake
                    "Here you go.",
                    // Dr Harlow — gesprek voor de eerste stake (we waren hier elke keer in fallback aan
                    // het belanden, dus expliciet matchen i.p.v. chooseOption(0)).
                    "Did you say I'd need a stake?",
            };
            for (String opt : exactPreferred) {
                final String target = opt;
                if (Dialog.hasOption(s -> s != null && s.equalsIgnoreCase(target))) {
                    Dialog.chooseOption(s -> s != null && s.equalsIgnoreCase(target));
                    QuestLog.force("Vamp", "Dialog option (exact): " + opt);
                    sameFallbackHits = 0;
                    lastFallbackDialogSig = "";
                    return randomDelay(700, 1300);
                }
            }
            String[] containsPreferred = {
                    "Morgan needs your help",
                    "here you go",
                    "terrorised by a vampyre", "terrorized by a vampyre",
                    "friend morgan we're talking about",
                    "So tell me how to kill vampyres",
                    "I'm in search of a vampyre", "vampyre", "vampire",
                    // Eerste stake-prompt (Dr Harlow). Was niet gedekt → fallback ging steeds naar 0
                    // wat technisch gelijk is maar de keuze ging niet door (zie sameFallbackHits).
                    "did you say i'd need a stake", "i'd need a stake", "say i'd need a stake",
                    "need a stake",
                    // Tweede stake (na falen Count Draynor)
                    "I need another stake", "another stake",
                    "glass of your finest ale", "finest ale", "ale please",
                    "A beer please", "One beer please.", "beer",
                    "buy", "trade"
            };
            for (String opt : containsPreferred) {
                final String key = opt.toLowerCase(Locale.ROOT);
                if (Dialog.hasOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(key))) {
                    Dialog.chooseOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(key));
                    QuestLog.force("Vamp", "Dialog option (contains): " + opt);
                    sameFallbackHits = 0;
                    lastFallbackDialogSig = "";
                    return randomDelay(700, 1300);
                }
            }
            // Fallback: eerste optie. Log de echte beschikbare opties zodat we kunnen zien
            // WAAROM we hier vastlopen (bijv. Dr Harlow heeft een sub-prompt buiten onze lijst).
            String dialogSig = "";
            try {
                java.util.List<IWidget> shown = Dialog.getOptions();
                if (shown != null && !shown.isEmpty()) {
                    StringBuilder sigSb = new StringBuilder();
                    StringBuilder sb = new StringBuilder("Dialog: fallback optie 0 gekozen | beschikbaar=");
                    for (int i = 0; i < shown.size(); i++) {
                        if (i > 0) {
                            sb.append(" || ");
                            sigSb.append('|');
                        }
                        IWidget w = shown.get(i);
                        String txt = "";
                        try {
                            if (w != null && w.getText() != null) txt = stripTags(w.getText()).trim();
                        } catch (Throwable ignored2) {}
                        sb.append('[').append(i).append(']').append(' ').append(txt);
                        sigSb.append(txt);
                    }
                    dialogSig = sigSb.toString();
                    QuestLog.force("Vamp", sb.toString());
                } else {
                    QuestLog.force("Vamp", "Dialog: fallback optie 0 gekozen (geen opties zichtbaar)");
                }
            } catch (Throwable ignored) {
                QuestLog.force("Vamp", "Dialog: fallback optie 0 gekozen");
            }

            // Track of we DEZELFDE dialog steeds opnieuw zien — als chooseOption(0) blijkbaar niet
            // doorgaat (zie het stake-loop incident), escaleer naar keyboard "1".
            if (!dialogSig.isEmpty() && dialogSig.equals(lastFallbackDialogSig)) {
                sameFallbackHits++;
            } else {
                sameFallbackHits = 1;
                lastFallbackDialogSig = dialogSig;
            }

            if (sameFallbackHits >= 2) {
                // chooseOption(0) lijkt niet door te komen → keyboard "1" (OSRS dialog accepteert
                // cijfertoetsen voor opties; werkt ook als de widget-click in een race-window valt).
                try {
                    Keyboard.pressKey(KeyEvent.VK_1);
                    QuestLog.force("Vamp", "Dialog: chooseOption(0) hangt → keyboard '1' geforceerd"
                            + " (hits=" + sameFallbackHits + ")");
                } catch (Throwable t) {
                    QuestLog.force("Vamp", "Dialog: keyboard '1' fallback fout: " + t.getMessage());
                    Dialog.chooseOption(0);
                }
                // Reset zodat we niet eindeloos keys blijven sturen
                if (sameFallbackHits >= 4) {
                    sameFallbackHits = 0;
                    lastFallbackDialogSig = "";
                }
                return randomDelay(900, 1500);
            }
            Dialog.chooseOption(0);
            return randomDelay(700, 1300);
        }

        if (Dialog.canContinue()) {
            // Lees de NPC-dialog tekst vóór we continue klikken — sommige speech triggers
            // moeten we vastleggen omdat ze de quest-state beïnvloeden (bv. Dr Harlow die zegt
            // dat de stake al uitgereikt is en we hem moeten zoeken).
            detectStateChangingNpcSpeech();
            Dialog.continueSpace();
            QuestLog.force("Vamp", "Dialog continue (spatie)");
            return randomDelay(450, 800);
        }

        // Open maar niet-continueable, niet-options — even wachten
        return randomDelay(400, 700);
    }

    /**
     * Probeert de huidige NPC dialog-tekst te lezen via meerdere bekende widget-ID's.
     * OSRS gebruikt verschillende dialog-widgets (klassieke chatbox, modern overlay).
     * Geeft "" terug als geen tekst gevonden.
     */
    private String currentNpcDialogText() {
        // Bekende NPC-dialog widget locaties: (group, child) paren.
        // - 231,6   = klassieke NPC chathead text
        // - 217,5/6 = player chathead text (alternatief layout)
        // - 11,4/5  = single-line "object examine" / continue
        // - 193,2   = sprite-overlay dialog
        int[][] candidates = {
                {231, 6}, {231, 5}, {231, 4},
                {217, 5}, {217, 6}, {217, 4},
                {11, 4}, {11, 5},
                {193, 2}
        };
        for (int[] gc : candidates) {
            try {
                IWidget w = Widgets.get(gc[0], gc[1]);
                if (w == null || w.isHidden()) continue;
                String txt = w.getText();
                if (txt == null) continue;
                String clean = stripTags(txt).trim();
                if (!clean.isEmpty()) {
                    return clean;
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    /**
     * Vangt NPC-speech die invloed heeft op de quest-state. Wordt aangeroepen vóór
     * elke {@code Dialog.continueSpace()} zodat we niet door de tekst heen klikken
     * en de trigger missen.
     */
    private void detectStateChangingNpcSpeech() {
        String npc = currentNpcDialogText();
        if (npc.isEmpty()) return;
        String low = npc.toLowerCase(Locale.ROOT);
        // Dr Harlow's "ik heb je al een stake gegeven, ga 'm zoeken" speech.
        // Mogelijk varianten:
        //   "Yesh. You must've put it somewhere. Go find it."
        //   "You must have put it somewhere. Go find it."
        if (low.contains("must've put it somewhere")
                || low.contains("must have put it somewhere")
                || (low.contains("go find it") && low.contains("somewhere"))
                || (low.contains("put it somewhere") && low.contains("find"))) {
            if (!harlowSaidGoFindStake) {
                QuestLog.force("Vamp", "Dr Harlow: \"" + npc + "\" → stake ligt elders, niet meer Harlow lastigvallen");
            }
            harlowSaidGoFindStake = true;
            harlowSaidGoFindStakeMs = System.currentTimeMillis();
            // Forceer een nieuwe bank-bezoek zodat we daadwerkelijk gaan kijken.
            visitedDraynorBank = false;
        }
    }

    // ===================================================================
    // Quest fases
    // ===================================================================

    /** varp == 0 — praat met Morgan om de quest te starten. */
    private int phaseStart(IPlayer local) {
        if (!insideMorganHouse(local.getWorldLocation())) {
            // Alleen path naar binnen (Storm pathfinder opent deuren). Geen losse deur-zoekradius:
            // binnen ~10t van Morgan staan meerdere deuren in Draynor.
            issueWalkToMorganHouseInside();
            QuestLog.step("Vamp", "Quest: Morgan — path naar huis binnen");
            return travelDelay();
        }
        morganDoorClickMs = 0L;
        if (local.getWorldLocation().distanceTo(MORGAN_TILE) > 6) {
            issueWalk(MORGAN_TILE);
            if (nearMorganHouseForDoor(local)) {
                tryClickNearbyDoor(local);
            }
            return travelDelay();
        }
        return talkToNpcContaining(local, "Morgan");
    }

    /** varp == 1 — verzamel garlic + stake + hammer, praat met Harlow. */
    private int phasePrepareAndHarlow(IPlayer local) {
        boolean hasGarlic = inventoryHasGarlic();
        boolean hasStake = inventoryHasStake();
        boolean hasHammer = Inventory.contains("Hammer");
        boolean hasEnoughCoins = inventoryCoinCount() >= MIN_COINS_BEER;
        boolean hasEnoughFood = inventoryFoodCount() >= MIN_QUEST_FOOD;

        // Garlic uit Morgan's cupboard (boven)
        if (!hasGarlic) {
            return getGarlicFromCupboard(local);
        }

        // Hammer + coins + food + combat al op zak maar bank-vlag nog niet gezet
        if (hasHammer && hasEnoughCoins && hasEnoughFood && hasCountCombatReady()
                && !visitedDraynorBank) {
            visitedDraynorBank = true;
            inBankSession = false;
            if (Bank.isOpen()) {
                Bank.close();
            }
        }

        // Na garlic: eerst naar Draynor bank voor hammer + coins + minimaal 5 food.
        if ((!hasHammer || !hasEnoughCoins || !hasEnoughFood) && !visitedDraynorBank) {
            return visitDraynorBank(local, hasHammer, hasEnoughCoins, hasEnoughFood);
        }
        if (!hasEnoughFood) {
            QuestLog.step("Vamp", "Quest: wacht — minimaal " + MIN_QUEST_FOOD + " food nodig");
            QuestLog.force("Vamp", "Vampire Slayer pauze: onvoldoende food in inventory (" + inventoryFoodCount()
                    + "/" + MIN_QUEST_FOOD + ")");
            outOfFoodForQuest = true;
            return randomDelay(3500, 5500);
        }

        // Stake bij Dr Harlow (vereist bier — niet leeg!).
        // Maar: als Harlow eerder zei "go find it", weet hij dat we al een stake gehad hebben
        // en geeft hij geen nieuwe meer. Dan moeten we eerst de bank checken (en uiteindelijk
        // Death's Office als de stake daar terecht is gekomen na een dood).
        if (!hasStake) {
            if (harlowSaidGoFindStake) {
                return recoverMissingStake(local);
            }
            // FIX #4: Beer-safeguard. "Beer glass" (leeg) telt niet, alleen volle Beer.
            boolean hasFullBeer = inventoryHasFullBeer();
            if (!hasFullBeer) {
                return getBeerFromBartender(local);
            }
            return talkToHarlowForStake(local);
        } else if (harlowSaidGoFindStake) {
            // Stake terug op zak → flag opheffen.
            harlowSaidGoFindStake = false;
            draynorBankStakeCheckCount = 0;
            QuestLog.force("Vamp", "Stake terug in inventory — Dr Harlow flag gereset");
        }

        // Hammer kopen in Varrock General Store als we hem nog niet hebben
        if (!hasHammer) {
            return buyHammer(local);
        }

        int combatPrep = tickCountCombatReady(local);
        if (combatPrep > 0) {
            return combatPrep;
        }

        // Alles geregeld → naar Draynor Manor
        return walkToManor(local);
    }

    /**
     * Loopt naar de Draynor bank, opent hem en probeert hammer + coins te withdrawen.
     */
    private int visitDraynorBank(IPlayer local, boolean hasHammer, boolean hasEnoughCoins, boolean hasEnoughFood) {
        QuestLog.step("Vamp", "Quest: Draynor bank — hammer/coins/food ophalen");

        if (local.getWorldLocation().distanceTo(DRAYNOR_BANK_TILE) > 6) {
            issueWalk(DRAYNOR_BANK_TILE);
            return travelDelay();
        }

        // Altijd markeren zodat loop() de bank niet sluit vóór we klaar zijn (ook als bank al open stond).
        inBankSession = true;

        if (!Bank.isOpen()) {
            openBankNow();
            lastBankActionMs = System.currentTimeMillis();
            QuestLog.force("Vamp", "Draynor bank openen");
            return randomDelay(1200, 2000);
        }

        // throttle bank acties
        if (System.currentTimeMillis() - lastBankActionMs < 600) {
            return randomDelay(300, 500);
        }

        int bankInputFix = BankWithdrawHelper.resolveBlockingBankInput();
        if (bankInputFix > 0) {
            return bankInputFix;
        }

        // Hammer (Imp loot hint → wait for bank stock before GE)
        if (!hasHammer && !Bank.contains("Hammer") && QuestImpLootHints.likelyInBank("Hammer", 1)) {
            QuestLog.step("Vamp", "Hammer likely in bank (Imp) — wait/retry");
            return randomDelay(700, 1200);
        }
        if (!hasHammer && Bank.contains("Hammer")) {
            Bank.withdraw("Hammer", 1);
            lastBankActionMs = System.currentTimeMillis();
            QuestLog.force("Vamp", "Hammer gewithdrawn");
            return randomDelay(700, 1200);
        }

        // Coins — volledige bankstack (withdraw-via-X is hier onbetrouwbaar)
        if (!hasEnoughCoins && Bank.contains("Coins")) {
            Bank.withdrawAll("Coins");
            lastBankActionMs = System.currentTimeMillis();
            QuestLog.force("Vamp", "Alle coins uit bank gehaald (withdrawAll)");
            return randomDelay(700, 1200);
        }

        // Food — neem minimaal 5 mee als er iets eetbaars in de bank ligt.
        if (!hasEnoughFood) {
            String food = firstAvailableQuestFoodInBank();
            if (food != null) {
                int need = Math.max(1, MIN_QUEST_FOOD - inventoryFoodCount());
                Bank.withdraw(food, need);
                lastBankActionMs = System.currentTimeMillis();
                QuestLog.force("Vamp", "Food meegenomen voor Vampire Slayer: " + food + " x" + need);
                return randomDelay(700, 1200);
            }
            QuestLog.force("Vamp", "Geen bekende food in bank — quest wacht tot food beschikbaar is");
            QuestLog.step("Vamp", "Quest: geen food in bank — voeg food toe");
            outOfFoodForQuest = true;
            return randomDelay(3500, 5500);
        }

        Integer combatWithdraw = withdrawCountCombatFromBank();
        if (combatWithdraw != null) {
            return combatWithdraw;
        }

        if (needsCountCombatSupplies() && !hasCountCombatSuppliesReady()) {
            countCombatBankNoProgressPasses++;
            inBankSession = false;
            Bank.close();
            if (countCombatBankNoProgressPasses >= 3) {
                QuestLog.step("Vamp", "Quest: geen combat-kit in bank (staff/runes/wapen)");
                QuestLog.force("Vamp", "Count combat: bank leeg na " + countCombatBankNoProgressPasses + " pogingen");
                return randomDelay(3000, 5000);
            }
            QuestLog.force("Vamp", "Count combat: bank had nog geen volledige kit — opnieuw proberen");
            return randomDelay(800, 1400);
        }
        countCombatBankNoProgressPasses = 0;

        // Klaar — sluit en markeer
        visitedDraynorBank = true;
        inBankSession = false;
        Bank.close();
        QuestLog.force("Vamp", "Draynor bank klaar — door naar volgende stap");
        return randomDelay(500, 900);
    }

    /** varp == 2 — coffin openen en Count Draynor verslaan. */
    private int phaseManorAndCount(IPlayer local) {
        // Veiligheidscheck: ga nooit naar Count zonder stake + hammer.
        // In sommige runs springt varp al naar 2 terwijl je die items nog niet in inventory hebt.
        boolean hasStake = inventoryHasStake();
        boolean hasHammer = Inventory.contains("Hammer");
        if (local.getWorldLocation().getY() < 9000 && inventoryFoodCount() < MIN_QUEST_FOOD) {
            QuestLog.step("Vamp", "Quest: Count uitgesteld — minimaal " + MIN_QUEST_FOOD + " food nodig");
            QuestLog.force("Vamp", "varp2 guard: onvoldoende food voor Count (" + inventoryFoodCount()
                    + "/" + MIN_QUEST_FOOD + ")");
            if (!visitedDraynorBank) {
                return visitDraynorBank(local, hasHammer, inventoryCoinCount() >= MIN_COINS_BEER, false);
            }
            outOfFoodForQuest = true;
            return randomDelay(3500, 5500);
        }
        if (!hasStake || !hasHammer) {
            if (!hasStake) {
                // Harlow weigert nieuwe stake → bank/Death pad i.p.v. eindeloze Harlow-loop.
                if (harlowSaidGoFindStake) {
                    return recoverMissingStake(local);
                }
                if (!inventoryHasFullBeer()) {
                    QuestLog.step("Vamp", "Quest: varp2 maar geen stake — eerst bier halen");
                    QuestLog.force("Vamp", "varp2 guard: stake ontbreekt, bier halen");
                    return getBeerFromBartender(local);
                }
                QuestLog.step("Vamp", "Quest: varp2 maar geen stake — terug naar Dr Harlow");
                QuestLog.force("Vamp", "varp2 guard: stake ontbreekt, praat met Dr Harlow");
                return talkToHarlowForStake(local);
            }
            QuestLog.step("Vamp", "Quest: varp2 maar geen hammer — hammer kopen");
            QuestLog.force("Vamp", "varp2 guard: hammer ontbreekt, koop hammer");
            return buyHammer(local);
        }

        int combatPrep = tickCountCombatReady(local);
        if (combatPrep > 0) {
            return combatPrep;
        }

        // In de crypt? → coffin/Count
        if (local.getWorldLocation().getY() >= 9000) {
            return openCoffinAndKill(local);
        }

        // Boven de grond: nooit trap-actie proberen zolang we nog buiten staan.
        // Eerst via voordeur naar binnen; pas daarna afdalen.
        if (!manorBeganeGrondBinnen(local.getWorldLocation())) {
            return walkToManor(local);
        }

        return descendToBasement(local);
    }

    private int finishComplete() {
        QuestLog.step("Vamp", "quest voltooid");
        return randomDelay(1800, 3200);
    }

    /**
     * Recovery-pad als Dr Harlow zegt dat de stake "ergens" ligt en geen nieuwe meer geeft.
     * Stappen:
     *   1. Eerst de Draynor bank checken (snapshot + actuele Bank-API als die open is).
     *   2. Als de stake daadwerkelijk in de bank zit → withdrawen.
     *   3. Als bank leeg is en we hebben max checks gehad → log instructie naar Death's
     *      Office (account-tabel onthoudt de last-known coin/items state, dus user
     *      kan hier inspringen). Auto-pathing naar Death is bewust uitgeschakeld
     *      omdat de Death-flow account-specifiek is en handmatige bevestiging vraagt.
     */
    private int recoverMissingStake(IPlayer local) {
        // Snapshot-check (cheap, geen open bank nodig).
        Integer snapshotQty = bankSnapshotStakeQty();
        if (snapshotQty != null && snapshotQty > 0) {
            QuestLog.step("Vamp", "Quest: stake ligt in bank (snapshot " + snapshotQty + ") — ophalen");
        } else if (snapshotQty != null && snapshotQty == 0 && draynorBankStakeCheckCount >= 1) {
            // Snapshot zegt: stake niet in bank, en we hebben dat al ingame bevestigd.
            return reportStakeAtDeathAndPause();
        } else {
            QuestLog.step("Vamp", "Quest: stake zoek — Draynor bank openen om te verifiëren");
        }

        if (local.getWorldLocation().distanceTo(DRAYNOR_BANK_TILE) > 6) {
            issueWalk(DRAYNOR_BANK_TILE);
            return travelDelay();
        }
        inBankSession = true;
        if (!Bank.isOpen()) {
            openBankNow();
            lastBankActionMs = System.currentTimeMillis();
            QuestLog.force("Vamp", "Recover stake: Draynor bank openen");
            return randomDelay(1200, 2000);
        }
        if (System.currentTimeMillis() - lastBankActionMs < 600) {
            return randomDelay(300, 500);
        }
        if (Bank.contains("Stake")) {
            Bank.withdraw("Stake", 1);
            lastBankActionMs = System.currentTimeMillis();
            QuestLog.force("Vamp", "Recover stake: Stake uit bank gewithdrawn");
            // Bank dichtdoen na een korte tick zodat hasStake-check daarna doortikt.
            return randomDelay(700, 1200);
        }
        // Bank ingame leeg op stake.
        draynorBankStakeCheckCount++;
        QuestLog.force("Vamp", "Recover stake: niet in Draynor bank (check #" + draynorBankStakeCheckCount + ")");
        if (draynorBankStakeCheckCount >= 2) {
            inBankSession = false;
            Bank.close();
            return reportStakeAtDeathAndPause();
        }
        return randomDelay(800, 1300);
    }

    /**
     * Stake zit niet in inv en niet in Draynor bank → vrijwel zeker bij Death (na een dood
     * tijdens deze quest). Death-collect automatiseren is bewust uitgesteld omdat het een
     * account-specifieke instance-flow is.
     * <p>
     * I.p.v. eindeloos pauzeren zetten we een hard block-signaal zodat de plugin de quest
     * uitschakelt, doorroteert naar een andere skill (als die enabled is) of bij gebrek
     * daaraan account-switcht / uitlogt. Geen "niets doen"-loop meer.
     */
    private int reportStakeAtDeathAndPause() {
        QuestLog.step("Vamp", "Quest: stake niet beschikbaar — quest uitschakelen, andere skill proberen");
        QuestLog.force("Vamp", "Stake nergens te vinden (inv/bank leeg, Harlow weigert) → quest geblokkeerd."
                + " Plugin moet doorrouteren naar andere skill of account-switch/logout."
                + " Collect handmatig bij Death (Lumbridge Death's Office) om verder te gaan met de quest.");
        questBlockedStakeMissing = true;
        questBlockedStakeMissingMs = System.currentTimeMillis();
        // Korte delay — plugin pakt het signaal in de volgende tick op.
        return randomDelay(500, 900);
    }

    /** Hard block-signaal: quest kan nu niet verder (stake missing). Plugin moet uitroteren. */
    public boolean isQuestBlockedStakeMissing() {
        return questBlockedStakeMissing;
    }

    /** Wordt door de plugin aangeroepen nadat het signaal verwerkt is. */
    public void resetQuestBlockedStakeMissing() {
        questBlockedStakeMissing = false;
    }

    /**
     * @return aantal stakes in de bank-snapshot ({@code null} als er geen recente snapshot is).
     */
    private Integer bankSnapshotStakeQty() {
        try {
            int q = BankSnapshot.qty("Stake");
            if (q > 0) {
                return q;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private String currentDisplayName() {
        try {
            IPlayer lp = Players.getLocal();
            if (lp == null || lp.getName() == null) return null;
            return stripTags(lp.getName()).trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ===================================================================
    // Sub-stappen
    // ===================================================================

    private int getGarlicFromCupboard(IPlayer local) {
        if (Inventory.isFull()) {
            QuestLog.step("Vamp", "Quest: maak 1 inventory-slot vrij voor Garlic");
            return randomDelay(2000, 3500);
        }
        WorldPoint me = local.getWorldLocation();
        if (distance2D(me, MORGAN_TILE) > GARLIC_MORGAN_AREA_MAX_DIST) {
            issueWalk(MORGAN_TILE);
            QuestLog.step("Vamp", "Quest: garlic — eerst naar Morgan (Draynor)");
            QuestLog.force("Vamp", "Garlic: te ver van Morgan — lopen i.p.v. trap elders (bijv. Varrock inn)");
            return travelDelay();
        }
        if (local.getWorldLocation().getPlane() < 1) {
            if (!insideMorganHouse(local.getWorldLocation())) {
                issueWalkToMorganHouseInside();
                QuestLog.step("Vamp", "Quest: garlic — path naar Morgan huis binnen");
                return travelDelay();
            }
            morganDoorClickMs = 0L;
            return interactStaircase(local, "Climb-up", MORGAN_TILE, MORGAN_HOUSE_OBJECT_MAX_DIST);
        }
        if (me.getPlane() != 1) {
            QuestLog.step("Vamp", "Quest: garlic — naar boven in Morgan's huis");
            if (!insideMorganHouse(local.getWorldLocation())) {
                issueWalkToMorganHouseInside();
                return travelDelay();
            }
            return interactStaircase(local, "Climb-up", MORGAN_TILE, MORGAN_HOUSE_OBJECT_MAX_DIST);
        }
        if (!clickCooldownOk(lastObjMs)) {
            return randomDelay(400, 700);
        }
        if (recentInteractionInProgress(local, lastObjMs)) {
            QuestLog.step("Vamp", "Quest: wacht op cupboard-interactie…");
            return randomDelay(500, 900);
        }

        ITileObject cup = findMorganGarlicCupboard(local);
        if (cup != null) {
            interactGarlicCupboard(cup);
            lastObjMs = System.currentTimeMillis();
            // interaction marked
            return randomDelay(1800, 2800);
        }

        if (inMorganUpstairsZone(me)) {
            WorldPoint pathTarget = nextGarlicPathWaypoint(me);
            if (pathTarget != null) {
                issueWalkExact(pathTarget);
                QuestLog.step("Vamp", "Quest: garlic — pad naar cupboard");
            } else {
                QuestLog.step("Vamp", "Quest: garlic — cupboard laden…");
                QuestLog.force("Vamp", "Cupboard id " + CUPBOARD_ID + "/" + CUPBOARD_OPEN_ID
                        + " niet in scene @ " + me.getX() + "," + me.getY() + ",1");
            }
            return travelDelay();
        }

        issueWalkExact(GARLIC_CUPBOARD_APPROACH);
        QuestLog.step("Vamp", "Quest: garlic — naar bovenverdieping");
        return travelDelay();
    }

    private static boolean inMorganUpstairsZone(WorldPoint me) {
        return me != null && me.getPlane() == 1
                && me.distanceTo(UPSTAIRS_AREA) <= MORGAN_HOUSE_OBJECT_MAX_DIST;
    }

    /** Eerste waypoint op het trap→cupboard-pad dat nog niet bereikt is. */
    private static WorldPoint nextGarlicPathWaypoint(WorldPoint me) {
        if (me == null || me.getPlane() != 1) {
            return MORGAN_UPSTAIRS_GARLIC_PATH[0];
        }
        for (WorldPoint wp : MORGAN_UPSTAIRS_GARLIC_PATH) {
            if (me.distanceTo(wp) > GARLIC_PATH_NODE_REACHED) {
                return wp;
            }
        }
        return null;
    }

    /** Alleen garlic-cupboard IDs 2612 (dicht) / 2613 (open) op plane 1. */
    private ITileObject findMorganGarlicCupboard(IPlayer local) {
        WorldPoint me = local.getWorldLocation();
        ITileObject best = null;
        int bestDist = Integer.MAX_VALUE;
        try {
            for (ITileObject obj : TileObjects.getAll(o -> o != null
                    && o.getWorldLocation() != null
                    && o.getWorldLocation().getPlane() == 1
                    && isGarlicCupboardId(o.getId()))) {
                int d = obj.getWorldLocation().distanceTo(GARLIC_CUPBOARD_TILE);
                if (d < bestDist) {
                    bestDist = d;
                    best = obj;
                }
            }
        } catch (Throwable ignored) {
        }
        if (best != null && bestDist <= 6) {
            return best;
        }
        if (me == null || me.getPlane() != 1) {
            return best;
        }
        return TileObjects.getNearest(obj ->
                obj != null
                        && obj.getWorldLocation().getPlane() == 1
                        && isGarlicCupboardId(obj.getId())
                        && obj.getWorldLocation().distanceTo(me) <= CUPBOARD_SEARCH_NEAR_PLAYER_DIST);
    }

    private static boolean isGarlicCupboardId(int id) {
        return id == CUPBOARD_ID || id == CUPBOARD_OPEN_ID;
    }

    /**
     * Gesloten cupboard: eerst Open. Geopend: Search. Storm heeftAction is onbetrouwbaar
     * (zelfde patroon als Manor-trap).
     */
    private void interactGarlicCupboard(ITileObject cup) {
        int id = cup.getId();
        WorldPoint loc = cup.getWorldLocation();
        QuestLog.step("Vamp", "Quest: garlic — cupboard " + (id == CUPBOARD_OPEN_ID ? "search" : "open"));
        QuestLog.force("Vamp", "Cupboard klik id=" + id + " @ "
                + loc.getX() + "," + loc.getY() + ", plane=" + loc.getPlane());
        if (id == CUPBOARD_OPEN_ID) {
            if (cup.hasAction("Search")) {
                cup.interact("Search");
            } else {
                cup.interact("Open");
            }
            return;
        }
        if (cup.hasAction("Open")) {
            cup.interact("Open");
            return;
        }
        cup.interact("Open");
    }

    /** 2D tile-afstand (negeert plane) om trap-op/af pingpong rond Morgan-house te voorkomen. */
    private static int distance2D(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) {
            return Integer.MAX_VALUE;
        }
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        return Math.max(dx, dy);
    }

    private int getBeerFromBartender(IPlayer local) {
        if (inventoryCoinCount() < MIN_COINS_BEER) {
            // Eén bank-poging per fase; anders eindeloos open/sluit als bank leeg is.
            if (!visitedDraynorBank) {
                QuestLog.step("Vamp", "Quest: coins op — Draynor bank");
                return visitDraynorBank(local, Inventory.contains("Hammer"), false,
                        inventoryFoodCount() >= MIN_QUEST_FOOD);
            }
            QuestLog.step("Vamp", "Quest: te weinig coins (bank leeg?) — voeg gp toe of leeg inventaris");
            return randomDelay(3500, 5500);
        }
        if (local.getWorldLocation().distanceTo(BARTENDER_TILE) > 6) {
            issueWalk(BARTENDER_TILE);
            return travelDelay();
        }
        return talkToNpcContaining(local, "Bartender");
    }

    private int talkToHarlowForStake(IPlayer local) {
        if (local.getWorldLocation().distanceTo(HARLOW_TILE) > 6) {
            issueWalk(HARLOW_TILE);
            return travelDelay();
        }
        // OSRS: 1e Talk-to = Morgan-keten (eindigt met "koop drank"); 2e Talk-to met bier = "Here you go." → stake.
        // Iets kortere cooldown zodat het tweede gesprek snel start zodra het eerste dialoog dicht is.
        boolean beerButNoStake = inventoryHasFullBeer() && !inventoryHasStake();
        return talkToNpcContaining(local, "Dr Harlow", beerButNoStake ? 380L : 900L);
    }

    /**
     * FIX #13: Echte hammer-aankoop. Loopt naar shop, opent trade, koopt 1 hammer
     * via right-click "Buy 1" op het hammer-item in de shop-widget.
     */
    private static final QuestBankGePrep.Need HAMMER_NEED = new QuestBankGePrep.Need("Hammer", 1, QuestGePrices.HAMMER);

    private int buyHammer(IPlayer local) {
        // Supply-chain: bank/Imp snapshot/GE before Varrock shop walk.
        if (QuestBankGePrep.ensureUnnoted(HAMMER_NEED)) {
            QuestLog.step("Vamp", "Hammer bank/GE prep");
            return randomDelay(500, 900);
        }
        if (Inventory.contains("Hammer")) {
            return randomDelay(300, 500);
        }
        if (local.getWorldLocation().distanceTo(VARROCK_GEN_STORE) > 8) {
            issueWalk(VARROCK_GEN_STORE);
            return travelDelay();
        }

        // Shop al open?
        IWidget shopRoot = Widgets.get(SHOP_WIDGET_GROUP, 0);
        if (shopRoot != null && !shopRoot.isHidden()) {
            return buyHammerFromShopWidget();
        }

        if (!clickCooldownOk(lastNpcMs)) {
            return randomDelay(400, 800);
        }
        INPC seller = NPCs.getNearest(n -> n != null && n.getName() != null
                && (n.getName().contains("Shop keeper") || n.getName().contains("Shop assistant")));
        if (seller == null) {
            QuestLog.step("Vamp", "Quest: winkel-NPC zoeken…");
            return randomDelay(1000, 1800);
        }
        if (local.getWorldLocation().distanceTo(seller.getWorldLocation()) > 6) {
            issueWalk(seller.getWorldLocation());
            return travelDelay();
        }
        if (seller.hasAction("Trade")) {
            seller.interact("Trade");
        } else {
            seller.interact("Trade");
        }
        lastNpcMs = System.currentTimeMillis();
        QuestLog.force("Vamp", "Trade met " + seller.getName());
        return randomDelay(1500, 2400);
    }

    private int buyHammerFromShopWidget() {
        long now = System.currentTimeMillis();
        if (now - lastShopMs < 800) {
            return randomDelay(300, 600);
        }
        if (Shop.isOpen()) {
            Shop.buyOne("Hammer");
            lastShopMs = now;
            QuestLog.force("Vamp", "Hammer Buy-1 via Shop SDK");
            return randomDelay(1200, 1900);
        }
        // Loop door alle children van de shop-widget en zoek hammer
        IWidget shopRoot = Widgets.get(SHOP_WIDGET_GROUP, 16); // items container (vaak child 16)
        IWidget[] children = getWidgetChildren(shopRoot);
        if (children == null || children.length == 0) {
            // Fallback: alle child-widgets van groep 300 doorlopen
            for (int childId = 0; childId < 80; childId++) {
                IWidget w = Widgets.get(SHOP_WIDGET_GROUP, childId);
                if (w == null) continue;
                IWidget[] sub = getWidgetChildren(w);
                if (sub == null) continue;
                Integer delay = tryBuyHammerInChildren(sub);
                if (delay != null) return delay;
            }
            QuestLog.force("Vamp", "Shop open maar Hammer niet gevonden");
            return randomDelay(800, 1300);
        }
        Integer delay = tryBuyHammerInChildren(children);
        if (delay != null) return delay;
        QuestLog.force("Vamp", "Hammer niet in shop-children gevonden");
        return randomDelay(800, 1300);
    }

    private Integer tryBuyHammerInChildren(IWidget[] children) {
        for (IWidget item : children) {
            if (item == null) continue;
            String name = item.getName();
            if (name == null) continue;
            String clean = stripTags(name).toLowerCase(Locale.ROOT);
            if (clean.contains("hammer")) {
                if (item.hasAction("Buy 1")) {
                    item.interact("Buy 1");
                } else if (item.hasAction("Buy")) {
                    item.interact("Buy");
                } else {
                    item.interact("Buy 1");
                }
                lastShopMs = System.currentTimeMillis();
                QuestLog.force("Vamp", "Hammer gekocht uit shop-widget");
                return randomDelay(1200, 1900);
            }
        }
        return null;
    }

    /**
     * Buiten: tegel vóór deur → "Open Large door" → naar trap.
     * Al binnen (noordelijk van de voordeur): nooit terug naar voordeur — alleen naar de trap lopen.
     */
    private int walkToManor(IPlayer local) {
        WorldPoint me = local.getWorldLocation();
        if (manorBeganeGrondBinnen(me)) {
            if (me.distanceTo(MANOR_STAIRS_DOWN_TILE) > 4) {
                issueWalk(MANOR_STAIRS_DOWN_TILE);
                QuestLog.step("Vamp", "Quest: Manor — naar trap (kelder)");
                return travelDelay();
            }
            return randomDelay(300, 600);
        }
        if (me.distanceTo(MANOR_DOOR_STAND_TILE) > 4) {
            issueWalk(MANOR_DOOR_STAND_TILE);
            return travelDelay();
        }
        if (tryOpenManorLargeDoor(local)) {
            lastObjMs = System.currentTimeMillis();
            QuestLog.force("Vamp", "Manor: Large door geopend");
            return randomDelay(900, 1500);
        }
        if (me.distanceTo(MANOR_STAIRS_DOWN_TILE) > 4) {
            issueWalk(MANOR_STAIRS_DOWN_TILE);
            return travelDelay();
        }
        return randomDelay(450, 800);
    }

    /** Begane grond binnen Draynor Manor (niet op de binnenplaats ten zuiden van de grote deur). */
    private static boolean manorBeganeGrondBinnen(WorldPoint me) {
        return me.getPlane() == 0 && me.getY() >= MANOR_INTERIOR_MIN_Y;
    }

    /** Alleen nog voor generieke deur-helper / compat; cluster rond manor-ingang + trap. */
    private static boolean nearDraynorManorForDoor(IPlayer local) {
        WorldPoint me = local.getWorldLocation();
        return me.distanceTo(MANOR_DOOR_STAND_TILE) <= 14
                || me.distanceTo(MANOR_LARGE_DOOR_TILE) <= 14
                || me.distanceTo(MANOR_STAIRS_DOWN_TILE) <= 14;
    }

    /**
     * "Open Large door" op vaste tegel (geen willekeurige deuren onderweg).
     */
    private boolean tryOpenManorLargeDoor(IPlayer local) {
        if (!clickCooldownOk(lastObjMs)) {
            return false;
        }
        if (manorBeganeGrondBinnen(local.getWorldLocation())) {
            return false;
        }
        if (local.getWorldLocation().distanceTo(MANOR_LARGE_DOOR_TILE) > 9) {
            return false;
        }
        ITileObject door = TileObjects.getNearest(obj -> {
            if (obj == null || obj.getName() == null) {
                return false;
            }
            if (obj.getWorldLocation().distanceTo(MANOR_LARGE_DOOR_TILE) > 5) {
                return false;
            }
            String n = obj.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("door")) {
                return false;
            }
            return obj.hasAction("Open") || obj.hasAction("Handle");
        });
        if (door == null) {
            return false;
        }
        if (local.getWorldLocation().distanceTo(door.getWorldLocation()) > 9) {
            return false;
        }
        if (door.hasAction("Open")) {
            door.interact("Open");
        } else {
            door.interact("Handle");
        }
        return true;
    }

    private static boolean nearMorganHouseForDoor(IPlayer local) {
        return local.getWorldLocation().distanceTo(MORGAN_TILE) <= 12;
    }

    private static boolean insideMorganHouse(WorldPoint p) {
        if (p == null || p.getPlane() != 0) {
            return false;
        }
        // Iets ruimer dan 2 tegels: drempel na deur / pathfinder-stop valt anders buiten "binnen".
        if (p.distanceTo(MORGAN_HOUSE_INSIDE_TILE) <= 3) {
            return true;
        }
        return p.distanceTo(MORGAN_TILE) <= 1;
    }

    /**
     * Kelder: trap op 3115,3357 — "Walk-down Stairs" (geen hasAction in zoekfilter: SDK kan acties nog niet tonen).
     */
    private int descendToBasement(IPlayer local) {
        if (!clickCooldownOk(lastObjMs)) {
            return randomDelay(400, 700);
        }
        if (local.getWorldLocation().distanceTo(MANOR_STAIRS_DOWN_TILE) > 3) {
            issueWalk(MANOR_STAIRS_DOWN_TILE);
            QuestLog.step("Vamp", "Quest: Manor — dichter bij trap");
            return travelDelay();
        }

        ITileObject stairs = findManorStairsDownObject();
        if (stairs != null) {
            if (local.getWorldLocation().distanceTo(stairs.getWorldLocation()) > 3) {
                issueWalk(stairs.getWorldLocation());
                QuestLog.step("Vamp", "Quest: Manor — naar trap object");
                return travelDelay();
            }
            interactManorStairsWalkDown(stairs);
            lastObjMs = System.currentTimeMillis();
            return randomDelay(3200, 5000);
        }

        ITileObject trapdoor = TileObjects.getNearest(obj ->
                obj != null && obj.getName() != null
                        && obj.getWorldLocation().distanceTo(MANOR_STAIRS_DOWN_TILE) <= 12
                        && obj.getName().equalsIgnoreCase("Trapdoor"));

        if (trapdoor != null) {
            if (local.getWorldLocation().distanceTo(trapdoor.getWorldLocation()) > 3) {
                issueWalk(trapdoor.getWorldLocation());
                return travelDelay();
            }
            if (trapdoor.hasAction("Open")) {
                trapdoor.interact("Open");
                lastObjMs = System.currentTimeMillis();
                return randomDelay(1500, 2200);
            }
            if (trapdoor.hasAction("Climb-down")) {
                trapdoor.interact("Climb-down");
            } else {
                trapdoor.interact("Climb-down");
            }
            lastObjMs = System.currentTimeMillis();
            QuestLog.force("Vamp", "Manor trapdoor fallback");
            return randomDelay(3200, 5000);
        }

        QuestLog.force("Vamp", "Manor: geen trap gevonden — naar anker-tegel");
        issueWalk(MANOR_STAIRS_DOWN_TILE);
        return travelDelay();
    }

    /**
     * Keldertrap: alleen object-ID 2616 (RuneLite), dicht bij anker — geen losse "stair"-naam
     * (dan pakt getNearest soms een andere trap in het manor).
     */
    private ITileObject findManorStairsDownObject() {
        ITileObject byId = TileObjects.getNearest(obj ->
                obj != null
                        && obj.getId() == MANOR_STAIRS_DOWN_OBJECT_ID
                        && obj.getWorldLocation().getPlane() == 0
                        && obj.getWorldLocation().distanceTo(MANOR_STAIRS_DOWN_TILE) <= 24);
        if (byId != null) {
            return byId;
        }
        ITileObject loose = TileObjects.getNearest(obj ->
                obj != null
                        && obj.getId() == MANOR_STAIRS_DOWN_OBJECT_ID
                        && obj.getWorldLocation().getPlane() == 0);
        if (loose != null && loose.getWorldLocation().distanceTo(MANOR_STAIRS_DOWN_TILE) <= 40) {
            QuestLog.force("Vamp", "Manor trap: 2616 gevonden (brede radius)");
            return loose;
        }
        QuestLog.force("Vamp", "Manor trap: object 2616 niet in scene — loop dichterbij / wacht laden");
        return null;
    }

    /**
     * Storm geeft vaak geen hasAction voor "Walk-down"; interact(String) is dan een no-op.
     * Eerst expliciete acties, anders eerste menu-optie ({@code interact(0)}).
     */
    private void interactManorStairsWalkDown(ITileObject stairs) {
        QuestLog.step("Vamp", "Quest: Manor — trap naar beneden");
        String rawName = stairs.getName();
        QuestLog.force("Vamp", "Manor trap klik: id=" + stairs.getId() + " name="
                + (rawName != null ? stripTags(rawName) : "?"));

        if (stairs.hasAction("Walk-down")) {
            stairs.interact("Walk-down");
            QuestLog.force("Vamp", "Manor: Walk-down");
            return;
        }
        if (stairs.hasAction("Climb-down")) {
            stairs.interact("Climb-down");
            QuestLog.force("Vamp", "Manor: Climb-down");
            return;
        }
        stairs.interact("Walk-down");
        QuestLog.force("Vamp", "Manor: trap interact(0) — eerste menu-optie (Walk-down)");
    }

    private int openCoffinAndKill(IPlayer local) {
        int combatPrep = tickCountCombatReady(local);
        if (combatPrep > 0) {
            return combatPrep;
        }

        // Verplichte volgorde: eerst coffin openen → daarna spawnt JOUW Count. Andere Counts in
        // de crypt zijn van andere spelers en geven "The vampyre doesn't seem interested in
        // fighting you." Dus tot we de coffin hebben geopend NIET aanvallen.
        if (!coffinOpenedOnce) {
            return doFirstCoffinClick(local);
        }

        // Onze eigen Count zoeken: prioriteit op "ons aanvalt" of "we vechten er al mee".
        INPC ourCount = findOurCount(local);
        if (ourCount != null) {
            attackedCount = true;
            coffinOpenMs = 0;
            if (local.getWorldLocation().distanceTo(ourCount.getWorldLocation()) > 10) {
                issueWalk(ourCount.getWorldLocation());
                return travelDelay();
            }
            if (recentInteractionInProgress(local, lastObjMs)) {
                QuestLog.step("Vamp", "Quest: wacht op Count-interactie…");
                return randomDelay(500, 900);
            }
            if (local.getInteracting() == null) {
                lastAttackedCountIndex = ourCount.getIndex();
                lastAttackedCountClickMs = System.currentTimeMillis();
                if (ourCount.hasAction("Attack")) {
                    ourCount.interact("Attack");
                } else {
                    ourCount.interact("Attack");
                }
                // interaction marked
                QuestLog.force("Vamp", "Attack Count Draynor (idx=" + ourCount.getIndex() + ")");
                return randomDelay(800, 1400);
            }
            return randomDelay(600, 1100);
        }

        // Failsafe — als coffin lang open is en geen Count, pas dan opnieuw klikken.
        if (coffinOpenMs > 0 && System.currentTimeMillis() - coffinOpenMs > 90_000L
                && !recentInteractionInProgress(local, lastCoffinInteractMs)) {
            QuestLog.force("Vamp", "Failsafe: Count niet gespawnt, coffin opnieuw");
            coffinOpenMs = 0;
            attackedCount = false;
            coffinOpenedOnce = false;
            return randomDelay(500, 900);
        }

        // Coffin al geopend, geen "eigen" Count zichtbaar (alleen andere spelers' Counts).
        if (attackedCount) {
            QuestLog.step("Vamp", "Quest: Count verslagen — wachten op update");
            return randomDelay(1000, 2000);
        }
        if (local.getWorldLocation().distanceTo(COFFIN_TILE) > 8) {
            issueWalk(COFFIN_TILE);
            return travelDelay();
        }
        int blacklistedHere = countNearbyBlacklistedCounts(local);
        if (blacklistedHere > 0) {
            QuestLog.step("Vamp", "Quest: " + blacklistedHere + " andere Count's — wachten op eigen spawn");
        } else {
            QuestLog.step("Vamp", "Quest: wacht op eigen Count…");
        }
        return randomDelay(600, 1200);
    }

    /**
     * Zoek de Count Draynor die ECHT van ons is. Strategie:
     *  1. Count die {@code local} interact-target heeft (= valt ons aan).
     *  2. Count waarmee wij interact-target hebben (= we vechten al).
     *  3. Count zonder andere player als target, mits niet geblacklist.
     * Counts die ons "not interested"-bericht gaven worden geskipt.
     */
    private INPC findOurCount(IPlayer local) {
        long now = System.currentTimeMillis();
        // Eerst: een Count die ons al aanvalt of waar wij mee vechten (100% zekerheid).
        INPC engaged = NPCs.getNearest(n -> {
            if (n == null || n.getName() == null || !n.getName().contains("Count Draynor") || n.isDead()) return false;
            if (isBlacklistedCount(n, now)) return false;
            if (isAttackingUs(n, local)) return true;
            return local.getInteracting() != null && local.getInteracting().equals(n);
        });
        if (engaged != null) return engaged;

        // Daarna: Count zonder player-target (kandidaat — onze net-gespawnde Count heeft nog niemand).
        // Skip Counts die met een ANDERE player interacteren.
        return NPCs.getNearest(n -> {
            if (n == null || n.getName() == null || !n.getName().contains("Count Draynor") || n.isDead()) return false;
            if (isBlacklistedCount(n, now)) return false;
            Object t = n.getInteracting();
            if (t == null) return true;
            if (t.equals(local)) return true;
            // Interacteert met iemand anders → niet van ons.
            return false;
        });
    }

    private boolean isAttackingUs(INPC n, IPlayer local) {
        try {
            Object t = n.getInteracting();
            return t != null && t.equals(local);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isBlacklistedCount(INPC n, long now) {
        if (n == null) return false;
        Long expire = notOurCountUntilMs.get(n.getIndex());
        return expire != null && now < expire;
    }

    private int countNearbyBlacklistedCounts(IPlayer local) {
        long now = System.currentTimeMillis();
        java.util.List<INPC> all = NPCs.getAll(n -> n != null && n.getName() != null
                && n.getName().contains("Count Draynor") && !n.isDead()
                && isBlacklistedCount(n, now));
        return all != null ? all.size() : 0;
    }

    /** Eén keer coffin Open/Search; pas weer na failsafe. */
    private int doFirstCoffinClick(IPlayer local) {
        if (local.getWorldLocation().distanceTo(COFFIN_TILE) > 6) {
            issueWalk(COFFIN_TILE);
            return travelDelay();
        }
        if (!clickCooldownOk(lastObjMs)) {
            return randomDelay(400, 700);
        }
        if (recentInteractionInProgress(local, lastCoffinInteractMs)
                || recentInteractionInProgress(local, lastObjMs)) {
            QuestLog.step("Vamp", "Quest: wacht op coffin-interactie…");
            return randomDelay(600, 1100);
        }
        ITileObject coffin = TileObjects.getNearest(obj ->
                obj != null && obj.getName() != null
                        && obj.getName().toLowerCase(Locale.ROOT).contains("coffin")
                        && (obj.hasAction("Open") || obj.hasAction("Search")));
        if (coffin == null) {
            return randomDelay(800, 1400);
        }
        coffinOpenedOnce = true;
        if (coffin.hasAction("Open")) {
            coffin.interact("Open");
        } else {
            coffin.interact("Search");
        }
        lastObjMs = System.currentTimeMillis();
        lastCoffinInteractMs = lastObjMs;
        // interaction marked
        coffinOpenMs = System.currentTimeMillis();
        attackedCount = false;
        QuestLog.force("Vamp", "Coffin geopend (1x) — wacht op eigen Count");
        return randomDelay(2200, 3600);
    }

    /**
     * Wordt door de plugin aangeroepen op elke chat-message. Vangt de specifieke
     * "doesn't seem interested" speech af zodat we de net-aangeklikte Count blacklisten
     * en niet meer opnieuw proberen.
     */
    public void onChatMessage(String message) {
        if (message == null) return;
        String low = message.toLowerCase(Locale.ROOT);
        if (low.contains("doesn't seem interested in fighting you")
                || low.contains("does not seem interested in fighting you")) {
            // Blacklist alleen als we recent een Count geklikt hebben (binnen 8s).
            long now = System.currentTimeMillis();
            if (lastAttackedCountIndex >= 0 && now - lastAttackedCountClickMs < 8000L) {
                notOurCountUntilMs.put(lastAttackedCountIndex, now + NOT_OUR_COUNT_BLACKLIST_MS);
                QuestLog.force("Vamp", "Count idx=" + lastAttackedCountIndex
                        + " is van andere speler — blacklist 45s, wachten op eigen Count");
                // Reset zodat we niet dezelfde Count opnieuw als "lastAttacked" houden.
                lastAttackedCountIndex = -1;
                // Force een nieuwe coffin-open cyclus alleen als we ook geen running fight hebben.
                attackedCount = false;
            }
        }
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private int readQuestVar() {
        try {
            return Vars.getVarp(VARP_VAMPYRE_SLAYER);
        } catch (Exception e) {
            QuestLog.force("Vamp", "Vars.getVarp fout: " + e.getMessage());
            return 0;
        }
    }

    private int talkToNpcContaining(IPlayer local, String namePart) {
        return talkToNpcContaining(local, namePart, 900L);
    }

    /**
     * @param minMsSinceLastNpc minimaal ms sinds vorige NPC-klik (Dr Harlow 2e gesprek gebruikt korter interval).
     */
    private int talkToNpcContaining(IPlayer local, String namePart, long minMsSinceLastNpc) {
        if (System.currentTimeMillis() - lastNpcMs < minMsSinceLastNpc) {
            return randomDelay(400, 800);
        }
        if (recentInteractionInProgress(local, lastNpcMs)) {
            QuestLog.step("Vamp", "Quest: wacht op NPC-interactie…");
            return randomDelay(500, 900);
        }
        String key = namePart.toLowerCase(Locale.ROOT);
        INPC npc = NPCs.getNearest(n -> n != null && n.getName() != null
                && n.getName().toLowerCase(Locale.ROOT).contains(key));
        if (npc == null) {
            QuestLog.step("Vamp", "Quest: zoek " + namePart + "…");
            return randomDelay(800, 1400);
        }
        if (local.getWorldLocation().distanceTo(npc.getWorldLocation()) > 8) {
            issueWalk(npc.getWorldLocation());
            return travelDelay();
        }
        if (npc.hasAction("Talk-to")) {
            npc.interact("Talk-to");
        } else {
            npc.interact("Talk-to");
        }
        lastNpcMs = System.currentTimeMillis();
        QuestLog.force("Vamp", "Talk-to " + npc.getName());
        return randomDelay(1200, 2200);
    }

    private static boolean inventoryHasGarlic() {
        return Inventory.contains(i -> {
            if (i == null || i.getName() == null) {
                return false;
            }
            String n = stripTags(i.getName()).trim();
            return n.equalsIgnoreCase("Garlic");
        });
    }

    private static boolean inventoryHasFullBeer() {
        return Inventory.contains(i -> {
            if (i == null || i.getName() == null) {
                return false;
            }
            String n = stripTags(i.getName()).trim();
            return n.equalsIgnoreCase("Beer");
        });
    }

    private static boolean inventoryHasStake() {
        return Inventory.contains(i -> {
            if (i == null || i.getName() == null) {
                return false;
            }
            String n = stripTags(i.getName()).trim();
            return n.equalsIgnoreCase("Stake");
        });
    }

    /**
     * Coins in inventory (alle stacks, noted-aware). {@link Inventory#getCount(String)} geeft hier soms 0 terug
     * terwijl er wél een stack ligt — zelfde aanpak als {@link #totalItemCountInvPlusEq} / ImpsHandler.
     */
    private int inventoryCoinCount() {
        return totalItemCountInvPlusEq("Coins");
    }

    private int interactStaircase(IPlayer local, String action, WorldPoint hintTile) {
        return interactStaircase(local, action, hintTile, -1);
    }

    /**
     * @param maxDistFromHint als &gt;= 0: alleen trappen binnen deze Chebyshev/WorldPoint-afstand van {@code hintTile}
     *                        (nodig bij Morgan — anders pakt getNearest een trap in een ander Draynor-huis).
     */
    private int interactStaircase(IPlayer local, String action, WorldPoint hintTile, int maxDistFromHint) {
        if (!clickCooldownOk(lastObjMs)) {
            return randomDelay(400, 700);
        }
        if (recentInteractionInProgress(local, lastObjMs)) {
            QuestLog.step("Vamp", "Quest: wacht op object-interactie…");
            return randomDelay(500, 900);
        }
        final int maxD = maxDistFromHint;
        // Dubbele vangrail: nooit Climb-up nabij verkeerde trap (bijv. Blue Moon Inn) als hint Morgan is.
        if ("Climb-up".equals(action) && maxD >= 0
                && local.getWorldLocation().distanceTo(hintTile) > GARLIC_MORGAN_AREA_MAX_DIST + 6) {
            issueWalk(hintTile);
            QuestLog.force("Vamp", "Staircase Climb-up uitgesteld — eerst naar hint-tile");
            return travelDelay();
        }
        ITileObject stairs = TileObjects.getNearest(obj -> {
            if (obj == null || obj.getName() == null) {
                return false;
            }
            if (!obj.getName().toLowerCase(Locale.ROOT).contains("staircase") || !obj.hasAction(action)) {
                return false;
            }
            if (maxD >= 0 && obj.getWorldLocation().distanceTo(hintTile) > maxD) {
                return false;
            }
            return true;
        });
        if (stairs == null) {
            if (local.getWorldLocation().distanceTo(hintTile) > 3) {
                issueWalk(hintTile);
                return travelDelay();
            }
            return randomDelay(900, 1500);
        }
        if (local.getWorldLocation().distanceTo(stairs.getWorldLocation()) > 4) {
            issueWalk(stairs.getWorldLocation());
            return travelDelay();
        }
        stairs.interact(action);
        lastObjMs = System.currentTimeMillis();
        QuestLog.force("Vamp", "Staircase " + action);
        return randomDelay(2200, 3600);
    }

    private boolean tryClickNearbyDoor(IPlayer local) {
        long now = System.currentTimeMillis();
        pruneDoorQueue(now);
        if (recentInteractionInProgress(local, lastObjMs)) {
            return false;
        }
        if (!recentDoorMs.isEmpty() && now - recentDoorMs.peekLast() < 2200) {
            return false;
        }
        ITileObject door = TileObjects.getNearest(obj -> {
            if (obj == null || obj.getName() == null) {
                return false;
            }
            String n = obj.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("door") && !n.contains("gate")) {
                return false;
            }
            return obj.hasAction("Open") || obj.hasAction("Handle");
        });
        if (door != null && local.getWorldLocation().distanceTo(door.getWorldLocation()) <= 6) {
            if (door.hasAction("Open")) {
                door.interact("Open");
            } else {
                door.interact("Handle");
            }
            recentDoorMs.addLast(now);
            return true;
        }
        return false;
    }

    private boolean recentInteractionInProgress(IPlayer local, long lastInteractionMs) {
        return (System.currentTimeMillis() - lastInteractionMs < 900L);
    }

    private int inventoryFoodCount() {
        int count = 0;
        for (IInventoryItem item : Inventory.getAll()) {
            if (item != null && item.getName() != null && isQuestFoodName(item.getName())) {
                count += Math.max(1, item.getQuantity());
            }
        }
        return count;
    }

    private int tryEatAtOrBelowFiveHp() {
        int hp = currentHitpointsEstimate();
        if (hp > 5 && !shouldEatByHpPercent()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastEatMs < 1_200L) {
            return randomDelay(300, 600);
        }
        IInventoryItem food = Inventory.getFirst(item -> {
            if (item == null || item.getName() == null) {
                return false;
            }
            if (item.hasAction("Eat") || item.hasAction("Drink")) {
                return true;
            }
            return isQuestFoodName(item.getName());
        });
        if (food == null) {
            return 0;
        }
        String action = food.hasAction("Eat") ? "Eat" : (food.hasAction("Drink") ? "Drink" : "Eat");
        food.interact(action);
        // interaction marked
        lastEatMs = now;
        QuestLog.step("Vamp", "Quest: nood-eat bij " + hp + " HP");
        QuestLog.force("Vamp", "Nood-eat bij HP<=" + hp + ": " + food.getName());
        return randomDelay(900, 1400);
    }

    private int currentHitpointsEstimate() {
        try {
            double pct = Combat.getHealthPercent();
            int lvl = Math.max(1, Skills.getLevel(Skill.HITPOINTS));
            int cur = (int) Math.floor((pct * lvl) / 100.0);
            if (pct > 0 && cur < 1) {
                cur = 1;
            }
            return Math.max(0, cur);
        } catch (Throwable t) {
            return 99;
        }
    }

    private boolean shouldEatByHpPercent() {
        try {
            double hpPct = Combat.getHealthPercent();
            int maxHp = Math.max(1, Skills.getLevel(Skill.HITPOINTS));
            double threshold = 5.0 / maxHp;
            if (hpPct > 1.0) {
                threshold *= 100.0;
            }
            return hpPct <= threshold;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String firstAvailableQuestFoodInBank() {
        for (String food : QUEST_FOOD_PRIORITY) {
            try {
                if (Bank.contains(food)) {
                    return food;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean isQuestFoodName(String name) {
        if (name == null) {
            return false;
        }
        String clean = stripTags(name).trim();
        for (String food : QUEST_FOOD_PRIORITY) {
            if (food.equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    private void pruneDoorQueue(long now) {
        while (!recentDoorMs.isEmpty() && now - recentDoorMs.peekFirst() > 8000) {
            recentDoorMs.pollFirst();
        }
    }

    /** Zelfde idee als Imps: staff in equipment óf inventory die het element dekt. */
    private boolean hasStaffWithElement(String element) {
        if (element == null || element.isEmpty()) {
            return false;
        }
        String el = element.toLowerCase(Locale.ROOT);
        return Equipment.contains(item -> item != null && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains(el)
                && item.getName().toLowerCase(Locale.ROOT).contains("staff"))
                || Inventory.contains(item -> item != null && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains(el)
                && item.getName().toLowerCase(Locale.ROOT).contains("staff"));
    }

    private boolean hasStaffInInvOrEquip() {
        return Equipment.contains(item -> item != null && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("staff"))
                || Inventory.contains(item -> item != null && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("staff"));
    }

    private int totalItemCountInvPlusEq(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return 0;
        }
        int count = 0;
        try {
            count += Inventory.getCount(true, itemName);
        } catch (Exception e) {
            try {
                count += Inventory.getCount(itemName);
            } catch (Exception e2) {
                try {
                    for (IInventoryItem it : Inventory.getAll()) {
                        if (it != null && it.getName() != null && it.getName().equalsIgnoreCase(itemName)) {
                            count += it.getQuantity();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        try {
            var eq = Equipment.getAll(i -> i != null && i.getName() != null && i.getName().equalsIgnoreCase(itemName));
            if (eq != null) {
                for (var e : eq) {
                    if (e != null) {
                        count += e.getQuantity();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    /**
     * Count-gevecht: Fire Strike → Wind Strike (Air Strike) → melee.
     * @return delay ms als we nog equippen/autocast doen, 0 = klaar
     */
    private int tickCountCombatReady(IPlayer local) {
        SpellBook.Standard spell = resolveActiveCountMageSpell();
        if (spell != null) {
            if (!hasMageKitForCount(spell)) {
                return bankForCountCombat(local, "Quest: Count — bank voor " + spellName(spell));
            }
            long now = System.currentTimeMillis();
            if (hasMeleeWeaponEquipped() && tryUnequipEquippedMeleeWeapon()) {
                lastMeleeEquipMs = now;
                QuestLog.step("Vamp", "Quest: Count — melee af voor magic");
                return randomDelay(600, 1100);
            }
            if (!hasStaffEquippedForSpell(spell)
                    && now - lastMeleeEquipMs >= 900
                    && tryEquipStaffForSpell(spell)) {
                lastMeleeEquipMs = now;
                QuestLog.step("Vamp", "Quest: Count — " + spellName(spell) + " (staff)");
                return randomDelay(600, 1100);
            }
            ensureMagicAutocast(spell);
            if (!isAutocastActive(spell)) {
                QuestLog.step("Vamp", "Quest: Count — autocast " + spellName(spell));
                return randomDelay(500, 900);
            }
            QuestLog.step("Vamp", "Quest: Count — " + spellName(spell));
            return 0;
        }

        SpellBook.Standard preferred = resolvePreferredCountMageSpell();
        if (preferred != null) {
            return bankForCountCombat(local, "Quest: Count — bank voor " + spellName(preferred));
        }
        return tickCountMeleeReady(local);
    }

    private int bankForCountCombat(IPlayer local, String status) {
        if (countCombatBankNoProgressPasses >= 3) {
            QuestLog.force("Vamp", "Count combat: bank uitgeput — fallback melee");
            return tickCountMeleeReady(local);
        }
        QuestLog.step("Vamp", status);
        return visitDraynorBank(local, Inventory.contains("Hammer"),
                inventoryCoinCount() >= MIN_COINS_BEER,
                inventoryFoodCount() >= MIN_QUEST_FOOD);
    }

    /** Melee: staff uit weapon-slot, dan echt wapen — nooit staff+melee. */
    private int tickCountMeleeReady(IPlayer local) {
        if (hasMeleeWeaponEquipped()) {
            QuestLog.step("Vamp", "Quest: Count — melee");
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastMeleeEquipMs < 900) {
            return randomDelay(400, 700);
        }
        if (hasStaffEquippedInWeaponSlot()) {
            if (tryUnequipEquippedStaff()) {
                lastMeleeEquipMs = now;
                QuestLog.step("Vamp", "Quest: Count — staff af voor melee");
                QuestLog.force("Vamp", "Count: staff af — melee wapen equip volgt");
                return randomDelay(700, 1200);
            }
            QuestLog.step("Vamp", "Quest: Count — staff blokkeert melee");
            return randomDelay(800, 1200);
        }
        if (tryEquipBestMeleeFromInventory()) {
            lastMeleeEquipMs = now;
            QuestLog.step("Vamp", "Quest: Count — melee wapen");
            QuestLog.force("Vamp", "Count: melee wapen equipped");
            return randomDelay(600, 1100);
        }
        if (!hasMeleeWeaponInInvOrEquip()) {
            return bankForCountCombat(local, "Quest: Count — bank voor melee-wapen");
        }
        QuestLog.step("Vamp", "Quest: Count — melee wapen nodig");
        return randomDelay(1000, 1800);
    }

    /** Fire Strike → Wind Strike als magic haalbaar is; anders null (melee). */
    private SpellBook.Standard resolvePreferredCountMageSpell() {
        int mag = getMagicLevelSafe();
        if (mag >= spellLevelReq(SpellBook.Standard.FIRE_STRIKE)
                && mageKitObtainable(SpellBook.Standard.FIRE_STRIKE)) {
            return SpellBook.Standard.FIRE_STRIKE;
        }
        if (mageKitObtainable(SpellBook.Standard.WIND_STRIKE)) {
            return SpellBook.Standard.WIND_STRIKE;
        }
        return null;
    }

    /** Kit al compleet, of bank/inv kan magic-kit nog aanvullen. */
    private boolean mageKitObtainable(SpellBook.Standard spell) {
        if (spell == null) {
            return false;
        }
        if (hasMageKitForCount(spell)) {
            return true;
        }
        if (Bank.isOpen()) {
            return bankCanSupportMageSpell(spell);
        }
        if (hasStaffForSpell(spell) || hasStaffInInvOrEquip()) {
            return true;
        }
        if (Inventory.contains("Mind rune") || Inventory.contains("Air rune")
                || Inventory.contains(elementalRune(spell))) {
            return true;
        }
        int mag = getMagicLevelSafe();
        if (spell == SpellBook.Standard.FIRE_STRIKE) {
            return mag >= spellLevelReq(spell);
        }
        return mag >= spellLevelReq(SpellBook.Standard.WIND_STRIKE);
    }

    private boolean needsCountCombatSupplies() {
        SpellBook.Standard preferred = resolvePreferredCountMageSpell();
        if (preferred != null) {
            return !hasMageKitForCount(preferred);
        }
        return !hasMeleeWeaponInInvOrEquip();
    }

    private boolean hasCountCombatSuppliesReady() {
        SpellBook.Standard preferred = resolvePreferredCountMageSpell();
        if (preferred != null) {
            return hasMageKitForCount(preferred);
        }
        return hasMeleeWeaponInInvOrEquip();
    }

    private boolean hasCountCombatReady() {
        SpellBook.Standard spell = resolveActiveCountMageSpell();
        if (spell != null && hasMageKitForCount(spell)) {
            return hasStaffEquippedForSpell(spell);
        }
        return hasMeleeWeaponEquipped();
    }

    /** Fire Strike (13+) als kit compleet, anders Wind/Air Strike, anders null → melee. */
    private SpellBook.Standard resolveActiveCountMageSpell() {
        int mag = getMagicLevelSafe();
        if (mag >= spellLevelReq(SpellBook.Standard.FIRE_STRIKE)
                && hasMageKitForCount(SpellBook.Standard.FIRE_STRIKE)) {
            return SpellBook.Standard.FIRE_STRIKE;
        }
        if (hasMageKitForCount(SpellBook.Standard.WIND_STRIKE)) {
            return SpellBook.Standard.WIND_STRIKE;
        }
        return null;
    }

    private SpellBook.Standard pickQuestMageSpellForBankWithdraw() {
        int mag = getMagicLevelSafe();
        if (mag >= spellLevelReq(SpellBook.Standard.FIRE_STRIKE)
                && bankCanSupportMageSpell(SpellBook.Standard.FIRE_STRIKE)) {
            return SpellBook.Standard.FIRE_STRIKE;
        }
        if (bankCanSupportMageSpell(SpellBook.Standard.WIND_STRIKE)) {
            return SpellBook.Standard.WIND_STRIKE;
        }
        return null;
    }

    private boolean bankCanSupportMageSpell(SpellBook.Standard spell) {
        if (spell == null) {
            return false;
        }
        if (!bankHasStaffForSpell(spell) && !hasStaffForSpell(spell)) {
            return false;
        }
        return Bank.contains(catalystRune(spell))
                || totalItemCountInvPlusEq(catalystRune(spell)) >= MIN_RUNES_COUNT_FIGHT;
    }

    private Integer withdrawCountCombatFromBank() {
        SpellBook.Standard spell = pickQuestMageSpellForBankWithdraw();
        if (spell != null) {
            if (!hasStaffForSpell(spell) && bankHasStaffForSpell(spell)) {
                String staff = firstBankStaffForSpell(spell);
                if (staff != null) {
                    Bank.withdraw(staff, 1);
                    lastBankActionMs = System.currentTimeMillis();
                    QuestLog.force("Vamp", "Count prep: " + staff + " voor " + spellName(spell));
                    return randomDelay(700, 1200);
                }
            }
            int mindNeed = Math.max(0, MIN_QUEST_MAGE_MIND_RUNES - totalItemCountInvPlusEq("Mind rune"));
            if (mindNeed > 0 && Bank.contains("Mind rune")) {
                Bank.withdrawAll("Mind rune");
                lastBankActionMs = System.currentTimeMillis();
                QuestLog.force("Vamp", "Count prep: alle Mind runes uit bank (withdrawAll)");
                return randomDelay(700, 1200);
            }
            int airNeed = countAirRunesStillNeeded(spell);
            if (airNeed > 0 && Bank.contains("Air rune")) {
                Bank.withdrawAll("Air rune");
                lastBankActionMs = System.currentTimeMillis();
                QuestLog.force("Vamp", "Count prep: alle Air runes uit bank (withdrawAll)");
                return randomDelay(700, 1200);
            }
            if (hasMageKitForCount(spell)) {
                return null;
            }
        }
        if (!hasMeleeWeaponInInvOrEquip()) {
            String melee = firstMeleeWeaponInBank();
            if (melee != null) {
                Bank.withdraw(melee, 1);
                lastBankActionMs = System.currentTimeMillis();
                QuestLog.force("Vamp", "Count prep: melee fallback " + melee);
                return randomDelay(700, 1200);
            }
        }
        return null;
    }

    private boolean hasMageKitForCount(SpellBook.Standard spell) {
        if (spell == null || !hasStaffForSpell(spell)) {
            return false;
        }
        if (totalItemCountInvPlusEq(catalystRune(spell)) < MIN_RUNES_COUNT_FIGHT) {
            return false;
        }
        if (countAirRunesStillNeeded(spell) > 0) {
            return false;
        }
        String elementKey = elementalRune(spell).toLowerCase(Locale.ROOT).replace(" rune", "");
        if (!hasStaffWithElement(elementKey)
                && totalItemCountInvPlusEq(elementalRune(spell)) < MIN_RUNES_COUNT_FIGHT) {
            return false;
        }
        return true;
    }

    private int countAirRunesStillNeeded(SpellBook.Standard spell) {
        if (spell == null) {
            return 0;
        }
        if (hasStaffWithElement("air")) {
            return 0;
        }
        int target = MIN_QUEST_MAGE_AIR_RUNES;
        return Math.max(0, target - totalItemCountInvPlusEq("Air rune"));
    }

    private boolean hasStaffForSpell(SpellBook.Standard spell) {
        if (spell == null) {
            return false;
        }
        String elementKey = elementalRune(spell).toLowerCase(Locale.ROOT).replace(" rune", "");
        if (spell == SpellBook.Standard.WIND_STRIKE) {
            return hasStaffWithElement("air");
        }
        return hasStaffWithElement(elementKey);
    }

    private boolean hasStaffEquippedInWeaponSlot() {
        try {
            return Equipment.contains(item -> item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("staff"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasMeleeWeaponEquipped() {
        try {
            return Equipment.contains(item -> item != null && item.getName() != null
                    && isMeleeWeaponCandidate(item.getName().toLowerCase(Locale.ROOT))
                    && MeleeWeaponRequirements.canWield(stripTags(item.getName())));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean tryUnequipEquippedStaff() {
        if (Inventory.getFreeSlots() < 1) {
            QuestLog.force("Vamp", "Count: staff afhalen geblokkeerd — inventory vol");
            return false;
        }
        try {
            var equipped = Equipment.getAll(item -> item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("staff"));
            if (equipped == null) {
                return false;
            }
            for (var eq : equipped) {
                if (eq == null) {
                    continue;
                }
                for (String action : new String[]{"Remove", "Unequip"}) {
                    if (eq.hasAction(action)) {
                        eq.interact(action);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean tryUnequipEquippedMeleeWeapon() {
        if (Inventory.getFreeSlots() < 1) {
            return false;
        }
        try {
            var equipped = Equipment.getAll(item -> item != null && item.getName() != null
                    && isMeleeWeaponCandidate(item.getName().toLowerCase(Locale.ROOT)));
            if (equipped == null) {
                return false;
            }
            for (var eq : equipped) {
                if (eq == null) {
                    continue;
                }
                for (String action : new String[]{"Remove", "Unequip"}) {
                    if (eq.hasAction(action)) {
                        eq.interact(action);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isAutocastActive(SpellBook.Standard spell) {
        if (spell == null) {
            return false;
        }
        try {
            SpellBook.Standard std = spell;
            return std != null && Magic.isAutoCasting(std);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasStaffEquippedForSpell(SpellBook.Standard spell) {
        if (spell == null) {
            return false;
        }
        String elementKey = elementalRune(spell).toLowerCase(Locale.ROOT).replace(" rune", "");
        if (hasStaffWithElement(elementKey)) {
            return Equipment.contains(item -> item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains(elementKey)
                    && item.getName().toLowerCase(Locale.ROOT).contains("staff"));
        }
        return Equipment.contains(item -> item != null && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("staff"));
    }

    private boolean tryEquipStaffForSpell(SpellBook.Standard spell) {
        if (spell == null || hasStaffEquippedForSpell(spell)) {
            return false;
        }
        String preferred = preferredStaff(spell).toLowerCase(Locale.ROOT);
        IInventoryItem staff = Inventory.getFirst(item -> item != null && item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("staff")
                && (item.getName().toLowerCase(Locale.ROOT).contains(preferred.replace("staff of ", ""))
                || item.getName().equalsIgnoreCase(preferredStaff(spell))));
        if (staff == null) {
            String elementKey = elementalRune(spell).toLowerCase(Locale.ROOT).replace(" rune", "");
            staff = Inventory.getFirst(item -> item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("staff")
                    && item.getName().toLowerCase(Locale.ROOT).contains(elementKey));
        }
        if (staff == null) {
            staff = Inventory.getFirst(item -> item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("staff"));
        }
        if (staff == null) {
            return false;
        }
        String act = wieldVerb(staff);
        if (act == null) {
            staff.interact("Wield");
        } else {
            staff.interact(act);
        }
        return true;
    }

    private boolean bankHasStaffForSpell(SpellBook.Standard spell) {
        return firstBankStaffForSpell(spell) != null;
    }

    private String firstBankStaffForSpell(SpellBook.Standard spell) {
        if (spell == null) {
            return null;
        }
        String preferred = preferredStaff(spell);
        try {
            if (Bank.contains(preferred)) {
                return preferred;
            }
        } catch (Throwable ignored) {
        }
        String elementKey = elementalRune(spell).toLowerCase(Locale.ROOT).replace(" rune", "");
        for (String staff : QUEST_MAGE_STAFF_PRIORITY) {
            String low = staff.toLowerCase(Locale.ROOT);
            if (!low.contains(elementKey) && spell != SpellBook.Standard.WIND_STRIKE
                    && !low.contains("air")) {
                continue;
            }
            try {
                if (Bank.contains(staff)) {
                    return staff;
                }
            } catch (Throwable ignored) {
            }
        }
        for (String staff : QUEST_MAGE_STAFF_PRIORITY) {
            try {
                if (Bank.contains(staff)) {
                    return staff;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean hasMeleeWeaponInInvOrEquip() {
        try {
            if (Equipment.getAll() != null) {
                for (var item : Equipment.getAll()) {
                    if (item != null && item.getName() != null) {
                        String name = stripTags(item.getName());
                        if (isMeleeWeaponCandidate(name.toLowerCase(Locale.ROOT))
                                && MeleeWeaponRequirements.canWield(name)) {
                            return true;
                        }
                    }
                }
            }
            for (IInventoryItem item : Inventory.getAll()) {
                if (item != null && item.getName() != null) {
                    String name = stripTags(item.getName());
                    if (isMeleeWeaponCandidate(name.toLowerCase(Locale.ROOT))
                            && MeleeWeaponRequirements.canWield(name)
                            && wieldVerb(item) != null) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String firstMeleeWeaponInBank() {
        return MeleeWeaponRequirements.firstUsableInBank(QUEST_MELEE_BANK_PRIORITY);
    }

    private int getMagicLevelSafe() {
        try {
            return Skills.getLevel(Skill.MAGIC);
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private void ensureMagicAutocast(SpellBook.Standard spell) {
        if (spell == null) {
            return;
        }
        try {
            SpellBook.Standard std = spell;
            if (std == null) {
                return;
            }
            if (!Magic.isAutoCasting(std)) {
                Magic.setAutoCast(std, false);
                QuestLog.force("Vamp", "Count autocast: " + spellName(spell));
            }
        } catch (Exception e) {
            QuestLog.force("Vamp", "ensureMagicAutocast fout: " + e.getMessage());
        }
    }

    private boolean tryEquipBestMeleeFromInventory() {
        IInventoryItem best = null;
        int bestScore = -1;
        try {
            for (IInventoryItem it : Inventory.getAll()) {
                if (it == null || it.getName() == null) {
                    continue;
                }
                String n = stripTags(it.getName()).toLowerCase(Locale.ROOT);
                if (!isMeleeWeaponCandidate(n)) {
                    continue;
                }
                if (!MeleeWeaponRequirements.canWield(stripTags(it.getName()))) {
                    continue;
                }
                if (wieldVerb(it) == null) {
                    continue;
                }
                int sc = scoreMeleeWeaponFromName(n);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = it;
                }
            }
        } catch (Exception e) {
            QuestLog.force("Vamp", "tryEquipBestMeleeFromInventory: " + e.getMessage());
            return false;
        }
        if (best == null || bestScore < 0) {
            return false;
        }
        String act = wieldVerb(best);
        if (act == null) {
            return false;
        }
        best.interact(act);
        return true;
    }

    private static String wieldVerb(IInventoryItem it) {
        if (it.hasAction("Wield")) {
            return "Wield";
        }
        if (it.hasAction("Wear")) {
            return "Wear";
        }
        return null;
    }

    private static boolean isMeleeWeaponCandidate(String n) {
        if (n.contains("staff") || n.contains("bow") || n.contains("crossbow") || n.contains("salamander")) {
            return false;
        }
        if (n.contains("pickaxe") || "hammer".equals(n)) {
            return false;
        }
        if (n.contains(" dart") || n.endsWith(" dart") || n.contains("throwing")) {
            return false;
        }
        return n.contains("scimitar") || n.contains(" sword") || n.endsWith(" sword") || n.contains("longsword")
                || n.contains("dagger") || n.contains("mace") || n.contains("flail")
                || n.contains("battleaxe") || (n.contains("axe") && !n.contains("pickaxe"))
                || n.contains("halberd") || n.contains("spear") || n.contains("hasta")
                || n.contains("whip") || n.contains("claw") || n.contains("warhammer");
    }

    private static int scoreMeleeWeaponFromName(String n) {
        int tier;
        if (n.contains("abyssal whip") || n.contains("abyssal bludgeon")) {
            tier = 950;
        } else if (n.contains("dragon")) {
            tier = 900;
        } else if (n.contains("rune")) {
            tier = 800;
        } else if (n.contains("granite")) {
            tier = 780;
        } else if (n.contains("adamant")) {
            tier = 700;
        } else if (n.contains("mithril")) {
            tier = 600;
        } else if (n.contains("black")) {
            tier = 550;
        } else if (n.contains("white")) {
            tier = 520;
        } else if (n.contains("steel")) {
            tier = 500;
        } else if (n.contains("iron")) {
            tier = 400;
        } else if (n.contains("bronze")) {
            tier = 300;
        } else {
            tier = 450;
        }
        int typeBonus = 0;
        if (n.contains("scimitar")) {
            typeBonus = 5;
        } else if (n.contains("whip")) {
            typeBonus = 8;
        } else if (n.contains(" sword") || n.endsWith(" sword")) {
            typeBonus = 4;
        }
        return tier + typeBonus;
    }

    private void issueWalkExact(WorldPoint target) {
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900) {
            return;
        }
        lastWalkMs = now;
        Movement.walkTo(target);
    }

    private void issueWalk(WorldPoint target) {
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900) {
            return;
        }
        lastWalkMs = now;
        Movement.walkTo(target);
    }

    /** Geen RSN-tegel-shift: kleine huisjes + deur-lijn verkeerd bij ±1 random offset. */
    private void issueWalkToMorganHouseInside() {
        long now = System.currentTimeMillis();
        if (now - lastWalkMs < 900) {
            return;
        }
        lastWalkMs = now;
        Movement.walkTo(MORGAN_HOUSE_INSIDE_TILE);
    }

    private int travelDelay() {
        return randomDelay(400, 700);
    }

    private boolean clickCooldownOk(long last) {
        return System.currentTimeMillis() - last > 900;
    }

    private int randomDelay(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min);
    }

    private IWidget[] getWidgetChildren(IWidget parent) {
        if (parent == null) return null;
        IWidget[] children = parent.getDynamicChildren();
        if (children != null && children.length > 0) return children;
        children = parent.getChildren();
        if (children != null && children.length > 0) return children;
        return parent.getStaticChildren();
    }

    @SuppressWarnings("unused")
    private String localRsn(IPlayer local) {
        if (local.getName() == null) {
            return null;
        }
        return stripTags(local.getName());
    }

    private static String stripTags(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\u00A0', ' ').replaceAll("<[^>]+>", "").trim();
    }

    private boolean openBankNow() {
        try {
            return net.storm.sdk.game.BankHelper.openSdkBankAndWait();
        } catch (Throwable t) {
            try {
                Bank.open();
                return Bank.isOpen();
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private void pollChatMessages() {
        long now = System.currentTimeMillis();
        if (now - lastChatPollMs < 1200L) {
            return;
        }
        lastChatPollMs = now;
        try {
            java.util.List<String> lines = Chat.getRecentMessages(12);
            if (lines == null) {
                return;
            }
            for (String line : lines) {
                onChatMessage(line);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String spellName(SpellBook.Standard spell) {
        if (spell == null) {
            return "?";
        }
        try {
            return spell.getName();
        } catch (Throwable t) {
            return String.valueOf(spell);
        }
    }

    private static int spellLevelReq(SpellBook.Standard spell) {
        if (spell == SpellBook.Standard.FIRE_STRIKE) {
            return 13;
        }
        return 1;
    }

    private static String catalystRune(SpellBook.Standard spell) {
        return "Mind rune";
    }

    private static String elementalRune(SpellBook.Standard spell) {
        if (spell == SpellBook.Standard.FIRE_STRIKE) {
            return "Fire rune";
        }
        return "Air rune";
    }

    private static String preferredStaff(SpellBook.Standard spell) {
        if (spell == SpellBook.Standard.FIRE_STRIKE) {
            return "Staff of fire";
        }
        return "Staff of air";
    }

}
