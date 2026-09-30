package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.game.Chat;
import net.storm.sdk.game.Game;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.widgets.Dialog;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Beginner Treasure Trail loop — anagram, cryptic talk, emote+Uri, map dig, hot/cold, Charlie.
 */
public final class BeginnerClueHandler {

    /** Uri (Double Agent) — RuneLite/wiki NPC id. */
    private static final int NPC_ID_URI = 8638;
    /** Na Talk-to Uri despawned hij; dat is geen “niet gespawnd”. */
    private static final long URI_POST_TALK_GRACE_MS = 25_000L;
    /** Pas emote opnieuw als Uri na deze tijd nog niet zichtbaar is. */
    private static final long URI_SPAWN_RETRY_EMOTE_MS = 10_000L;
    private static final int HOT_COLD_PROBE_MIN = 10;
    private static final int HOT_COLD_PROBE_MAX = 25;
    private static final int HOT_COLD_ZONE_NEAR = 5;
    private static final int MAX_HOT_COLD_GRID = 14;
    private static final long HOT_COLD_REPLY_TIMEOUT_MS = 3_500L;

    private final BeginnerClueContainerHelper container = new BeginnerClueContainerHelper();
    private final BeginnerHotColdSolver hotCold = new BeginnerHotColdSolver();
    private final ClueTalkHelper talk = new ClueTalkHelper();
    private final ClueBankHelper bank = new ClueBankHelper();
    private final ClueRewardHelper reward = new ClueRewardHelper();
    private final CharlieClueHelper charlie = new CharlieClueHelper();

    private String status = "idle";
    private BeginnerClueReference.StepType step = BeginnerClueReference.StepType.UNKNOWN;
    private String charlieItem;
    private long lastEmoteMs;
    private long lastDigMs;
    private long lastFeelMs;
    private long lastUriTalkMs;
    private long lastDialogMs;
    private String lastClueNorm = "";
    private boolean charlieStepStarted;
    private boolean emotePerformedThisStep;
    private boolean uriTalkedAfterEmote;
    /** Cached map dig while map UI is closed mid-travel (avoid re-Read). */
    private BeginnerClueReference.MapDig activeMapDig;
    private WorldPoint hotColdMoveTarget;
    private BeginnerHotColdSolver.BeginnerZone hotColdProbeZone;
    private int hotColdFeelAttempts;
    private long hotColdFeelSentMs;
    private boolean hotColdAwaitingReply;
    private WorldPoint lastHotColdFeelTile;
    private long lastChatPollMs;
    private final Set<String> seenChat = new HashSet<>();

    public String getStatus() {
        return status;
    }

    public void reset() {
        container.reset();
        ClueContainerAcquireHelper.reset();
        hotCold.reset();
        talk.reset();
        bank.reset();
        reward.reset();
        charlie.reset();
        ReldoApproachHelper.reset();
        BrianShopApproachHelper.reset();
        HairdresserShopApproachHelper.reset();
        DoricHouseApproachHelper.reset();
        ClueWalk.reset();
        status = "idle";
        step = BeginnerClueReference.StepType.UNKNOWN;
        charlieItem = null;
        charlieStepStarted = false;
        lastEmoteMs = 0L;
        lastDigMs = 0L;
        lastFeelMs = 0L;
        lastUriTalkMs = 0L;
        lastDialogMs = 0L;
        lastClueNorm = "";
        activeMapDig = null;
        resetEmoteUriProgress();
        hotColdMoveTarget = null;
        hotColdProbeZone = null;
        hotColdFeelAttempts = 0;
        hotColdFeelSentMs = 0L;
        hotColdAwaitingReply = false;
        lastHotColdFeelTile = null;
        lastChatPollMs = 0L;
        seenChat.clear();
    }

    public void onGameMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String clean = ClueScrollHelper.strip(message);
        WorldPoint me = ClueWalk.me();
        WorldPoint feelAt = lastHotColdFeelTile != null ? lastHotColdFeelTile : me;
        if (feelAt != null && hotCold.signal(feelAt, clean)) {
            hotColdAwaitingReply = false;
            BotRuntime.logConsole("[Clue/HotCold] chat → " + hotCold.statusLine());
        }
        charlie.onGameMessage(clean);
        String n = ClueScrollHelper.normalize(message);
        if (charlieItem == null) {
            String item = BeginnerClueReference.parseCharlieItemRequestFromDialog(message);
            if (item != null) {
                charlieItem = item;
                BotRuntime.logConsole("[Clue/Charlie] item=" + item);
            }
        }
        if (n.contains("you have been given") || n.contains("you find a")
                || n.contains("clue scroll")) {
            container.reset();
        }
        if (n.contains("dig") && n.contains("nothing")) {
            BotRuntime.logConsole("[Clue/Dig] niets");
        }
    }

    public int loop() {
        if (!Game.isLoggedIn()) {
            status = "login";
            return 800;
        }
        // Emote/Uri dialog handled inside doEmote; Reldo/anagram via helpers.
        // UNKNOWN + tramp-opties: niet Okay kiezen (Charlie net klaar → re-read race).
        if (Dialog.isOpen() && CharlieClueHelper.isDefaultTrampOptions()) {
            Dialog.continueSpace();
            status = "tramp dialog → scroll";
            lastClueNorm = "";
            container.reset();
            BotRuntime.logConsole("[Clue] tramp-opties → force scroll re-read");
            return container.openAndRead();
        }
        // Clue: altijd Continue (force) — ook als algemene Auto-continue dialog uit staat
        int autoCont = Dialog.autoContinueDelay(true);
        if (autoCont >= 0) {
            status = "auto-continue";
            return autoCont;
        }
        int dialog = talk.handleDialog();
        // CRYPTIC (Reldo): dialog niet overslaan — anders Talk-to opnieuw = clue-show loop
        if (dialog >= 0 && step != BeginnerClueReference.StepType.CHARLIE
                && step != BeginnerClueReference.StepType.ANAGRAM
                && step != BeginnerClueReference.StepType.EMOTE) {
            status = "dialog";
            return dialog;
        }

        int loot = BeginnerClueGroundPickupHelper.pickupIfNeeded(
                step == BeginnerClueReference.StepType.HOT_COLD, charlieItem);
        if (loot >= 0) {
            status = "loot";
            return loot;
        }

        int rewardWait = reward.takeLoot();
        if (rewardWait >= 0) {
            status = "reward";
            container.reset();
            return rewardWait;
        }
        if (reward.consumeClosed()) {
            lastClueNorm = "";
            container.reset();
            clearHotColdSession();
            step = BeginnerClueReference.StepType.UNKNOWN;
            BotRuntime.logConsole("[Clue/Reward] interface dicht — verder");
            return 400;
        }

        if (BeginnerClueContainerHelper.hasBeginnerCasket()) {
            if (BotRuntime.clueBankCasket) {
                status = "casket → bank";
                clearHotColdSession();
                return bank.bankRewardCasket();
            }
            return openCasket();
        }

        int ge = bank.tickGeBuy();
        if (ge >= 0) {
            status = "GE buy";
            return ge;
        }

        // Spade eerst (inv/bank/GE) vóór Read — voorkomt bank↔scroll-loop
        if (BeginnerClueContainerHelper.hasBeginnerClue()
                && !Inventory.contains(BeginnerClueReference.SPADE)) {
            int prep = bank.ensureTools(true, false, null);
            if (prep >= 0) {
                status = "prep spade";
                return prep;
            }
            // GE net gequeued
            ge = bank.tickGeBuy();
            if (ge >= 0) {
                status = "GE buy spade";
                return ge;
            }
        }

        if (!BeginnerClueContainerHelper.hasBeginnerClue()
                && !BeginnerClueContainerHelper.isRewardOpen()) {
            clearHotColdSession();
            int deviceBank = bank.bankStrangeDeviceIfIdle();
            if (deviceBank >= 0) {
                status = "device → bank";
                return deviceBank;
            }
            int acquire = ClueContainerAcquireHelper.tick();
            if (acquire >= 0) {
                status = "container → clue";
                return acquire;
            }
            if (BotRuntime.resumeAfterClueIfNeeded()) {
                status = "resume skill";
                return 400;
            }
            status = "wacht op clue";
            ClueContainerAcquireHelper.logWaitingIfDue();
            return 1_800;
        }

        // Tijdens bank/GE: niet Read spammen (widget timeout-loop)
        if (bank.isBanking() || bank.hasGeBuyPending()) {
            int prep = bank.hasGeBuyPending() ? bank.tickGeBuy() : bank.ensureTools(true, false, null);
            if (prep >= 0) {
                status = bank.hasGeBuyPending() ? "GE buy" : "bank tools";
                return prep;
            }
        }

        BeginnerClueReference.MapDig map = container.openMap();
        String text = container.readOpenOrCached();
        if ((text == null || text.isBlank()) && map == null) {
            if (!BeginnerClueContainerHelper.hasBeginnerClue()) {
                clearHotColdSession();
                activeMapDig = null;
                int deviceBank = bank.bankStrangeDeviceIfIdle();
                if (deviceBank >= 0) {
                    status = "device → bank";
                    return deviceBank;
                }
                if (BotRuntime.resumeAfterClueIfNeeded()) {
                    status = "resume skill";
                    return 400;
                }
                status = "wacht op clue";
                ClueContainerAcquireHelper.logWaitingIfDue();
                return 1_200;
            }
            // Known dig/travel steps: never re-open scroll mid-walk
            if (step == BeginnerClueReference.StepType.MAP_DIG && activeMapDig != null) {
                return doMapDig(activeMapDig);
            }
            if (step == BeginnerClueReference.StepType.HOT_COLD) {
                return doHotCold();
            }
            if (ClueWalk.isTravelBusy()) {
                status = "travel…";
                return 400;
            }
            status = "read clue";
            return container.openAndRead();
        }
        if (text == null) {
            text = "";
        }
        String norm = ClueScrollHelper.normalize(text);
        if (!norm.isEmpty() && !norm.equals(lastClueNorm)) {
            lastClueNorm = norm;
            talk.reset();
            hotCold.reset();
            ClueWalk.reset();
            resetEmoteUriProgress();
            BeginnerClueReference.StepType prev = step;
            identify(text, map);
            if (step == BeginnerClueReference.StepType.CHARLIE
                    && prev != BeginnerClueReference.StepType.CHARLIE) {
                charlie.beginStep();
                charlieStepStarted = true;
            } else if (step != BeginnerClueReference.StepType.CHARLIE) {
                charlieStepStarted = false;
            }
            BotRuntime.logConsole("[Clue] step=" + step);
        } else if (step == BeginnerClueReference.StepType.UNKNOWN) {
            identify(text, map);
            if (step == BeginnerClueReference.StepType.CHARLIE && !charlieStepStarted) {
                charlie.beginStep();
                charlieStepStarted = true;
            }
        }
        if (charlie.getRequestedItem() != null) {
            charlieItem = charlie.getRequestedItem();
        } else if (charlieItem == null && step == BeginnerClueReference.StepType.CHARLIE) {
            charlieItem = BeginnerClueReference.parseCharlieItemRequestFromDialog(text);
            String body = container.readDialogBody();
            if (charlieItem == null && body != null) {
                charlieItem = BeginnerClueReference.parseCharlieItemRequestFromDialog(body);
            }
        }

        // Tools pas na bekende stap — Charlie-items via CharlieClueHelper/bank.ensureCharlieItem.
        if (step != BeginnerClueReference.StepType.UNKNOWN || map != null) {
            boolean needDevice = step == BeginnerClueReference.StepType.HOT_COLD;
            boolean needSpade = step == BeginnerClueReference.StepType.MAP_DIG
                    || step == BeginnerClueReference.StepType.HOT_COLD
                    || map != null;
            if (step != BeginnerClueReference.StepType.CHARLIE) {
                int banked = bank.ensureTools(needSpade, needDevice, null);
                if (banked >= 0) {
                    status = "bank " + (needDevice ? "device" : "spade");
                    return banked;
                }
            }
        }
        ClueBankHelper.openInventory();

        switch (step) {
            case ANAGRAM:
                return doTalk(BeginnerClueReference.anagramNpc(text), text, "anagram");
            case CRYPTIC_TALK:
                return doTalk(BeginnerClueReference.crypticNpc(text), text, "cryptic");
            case EMOTE:
                return doEmote(text);
            case MAP_DIG:
                return doMapDig(map);
            case HOT_COLD:
                return doHotCold();
            case CHARLIE:
                return doCharlie(text);
            case SEARCH:
            case UNKNOWN:
            default:
                if (map != null) {
                    return doMapDig(map);
                }
                status = "onbekende clue";
                BotRuntime.logConsole("[Clue] onbekend: " + preview(text));
                return 800;
        }
    }

    private void identify(String text, BeginnerClueReference.MapDig map) {
        if (map != null) {
            step = BeginnerClueReference.StepType.MAP_DIG;
            activeMapDig = map;
            return;
        }
        activeMapDig = null;
        if (BeginnerClueReference.looksLikeCharlie(text)) {
            step = BeginnerClueReference.StepType.CHARLIE;
            return;
        }
        // Device al binnen (na Reldo) → hot/cold, niet opnieuw Reldo Talk-to
        BeginnerClueReference.NpcTarget cryptic = BeginnerClueReference.crypticNpc(text);
        if (Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)
                && (BeginnerClueReference.looksLikeHotCold(text)
                || (cryptic != null && cryptic.npcName != null
                && cryptic.npcName.equalsIgnoreCase("Reldo")))) {
            step = BeginnerClueReference.StepType.HOT_COLD;
            return;
        }
        if (BeginnerClueReference.looksLikeHotCold(text)
                || (Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)
                && BeginnerClueReference.anagramNpc(text) == null
                && cryptic == null
                && BeginnerClueReference.emoteStep(text) == null)) {
            if (BeginnerClueReference.looksLikeHotCold(text)
                    || Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)) {
                if (BeginnerClueReference.emoteStep(text) == null
                        && BeginnerClueReference.anagramNpc(text) == null
                        && cryptic == null
                        && !BeginnerClueReference.looksLikeCharlie(text)) {
                    step = BeginnerClueReference.StepType.HOT_COLD;
                    return;
                }
            }
        }
        if (BeginnerClueReference.anagramNpc(text) != null
                && BeginnerClueReference.looksLikeAnagram(text)) {
            step = BeginnerClueReference.StepType.ANAGRAM;
            return;
        }
        BeginnerClueReference.EmoteStep emote = BeginnerClueReference.emoteStep(text);
        if (emote != null) {
            step = BeginnerClueReference.StepType.EMOTE;
            return;
        }
        if (cryptic != null) {
            step = BeginnerClueReference.StepType.CRYPTIC_TALK;
            return;
        }
        if (BeginnerClueReference.anagramNpc(text) != null) {
            step = BeginnerClueReference.StepType.ANAGRAM;
            return;
        }
        if (Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)) {
            step = BeginnerClueReference.StepType.HOT_COLD;
            return;
        }
        step = BeginnerClueReference.StepType.UNKNOWN;
    }

    private int doTalk(BeginnerClueReference.NpcTarget target, String text, String kind) {
        if (target == null) {
            status = kind + " ?";
            return 600;
        }
        status = kind + " " + target.npcName;
        int delay = talk.talkTo(target, text);
        if (delay == -2) {
            lastClueNorm = "";
            container.reset();
            step = BeginnerClueReference.StepType.UNKNOWN;
            status = "Reldo → device / scroll";
            BotRuntime.logConsole("[Clue] Reldo klaar — force scroll re-read");
            return container.openAndRead();
        }
        return delay;
    }

    private int doEmote(String text) {
        BeginnerClueReference.EmoteStep emote = BeginnerClueReference.emoteStep(text);
        if (emote == null) {
            status = "emote ?";
            return 600;
        }
        status = "emote " + emote.emote;

        if (emote.equipItems != null) {
            for (String itemName : emote.equipItems) {
                int eqDelay = bank.ensureEquipped(itemName);
                if (eqDelay >= 0) {
                    status = "emote equip " + itemName;
                    return eqDelay;
                }
            }
        }

        // Indoor shops (Bob/Iffie/Flynn/GE/Aris): Open deur ≤12 van dest, dan ≤2 tegels
        boolean arrived = ClueWalk.approachEmoteTile(
                emote.tile, emote.bobPath, BeginnerClueReference.EMOTE_ARRIVAL);
        if (!arrived) {
            return 400;
        }
        // Niet emote terwijl walker/deur nog bezig is (anders spam “klik mislukt”)
        try {
            if (net.storm.sdk.movement.Movement.isWalking()
                    || net.storm.sdk.movement.MovementHelper.getActivePath() != null) {
                status = "emote wait stil";
                return 280;
            }
        } catch (Throwable ignored) {
        }

        if (!emotePerformedThisStep) {
            if (System.currentTimeMillis() - lastEmoteMs < 1_200L) {
                return 300;
            }
            if (!net.storm.sdk.widgets.Tabs.isOpen(net.storm.api.widgets.Tab.EMOTES)) {
                net.storm.sdk.widgets.Tabs.open(net.storm.api.widgets.Tab.EMOTES);
                EmoteWidgetRegistry.invalidateCache();
                status = "emote-tab";
                return 400;
            }
            if (EmoteWidgetRegistry.clickEmote(emote.emote)) {
                emotePerformedThisStep = true;
                lastEmoteMs = System.currentTimeMillis();
                ClueWalk.clearWalk();
                BotRuntime.logConsole("[Clue/Emote] " + emote.emote + " — wacht Uri");
                return 700;
            }
            // Cache stale na tab-open — één force refresh
            EmoteWidgetRegistry.invalidateCache();
            return 450;
        }

        Integer uriDialogEarly = handleUriAfterEmoteDialog();
        if (uriDialogEarly != null) {
            return uriDialogEarly;
        }
        if (uriTalkedAfterEmote) {
            return handleUriPostTalkPhase();
        }

        INPC uri = findUriNpc();
        if (uri == null) {
            long sinceUriTalk = System.currentTimeMillis() - lastUriTalkMs;
            if (emotePerformedThisStep && lastUriTalkMs > 0
                    && sinceUriTalk > 150 && sinceUriTalk < URI_POST_TALK_GRACE_MS) {
                uriTalkedAfterEmote = true;
                return handleUriPostTalkPhase();
            }
            if (emote.tile != null && !ClueWalk.near(emote.tile, BeginnerClueReference.EMOTE_ARRIVAL)) {
                ClueWalk.approachEmoteTile(emote.tile, emote.bobPath, BeginnerClueReference.EMOTE_ARRIVAL);
                return 400;
            }
            long sinceEmote = System.currentTimeMillis() - lastEmoteMs;
            if (sinceEmote > URI_SPAWN_RETRY_EMOTE_MS) {
                emotePerformedThisStep = false;
                BotRuntime.logConsole("[Clue/Uri] niet gevonden na " + (sinceEmote / 1000)
                        + "s — emote opnieuw");
            } else {
                status = "wacht Uri";
            }
            return 400;
        }

        emotePerformedThisStep = true;
        Integer uriDialog = handleUriAfterEmoteDialog();
        if (uriDialog != null) {
            return uriDialog;
        }
        if (!uriTalkedAfterEmote) {
            status = "Talk-to Uri";
            if (talkUri(uri)) {
                uriTalkedAfterEmote = true;
                lastUriTalkMs = System.currentTimeMillis();
                BotRuntime.logConsole("[Clue/Uri] Talk-to");
                return 550;
            }
            return 400;
        }
        return handleUriPostTalkPhase();
    }

    /** Uri al gesproken / despawned — scroll vernieuwen. */
    private int handleUriPostTalkPhase() {
        Integer uriDialog = handleUriAfterEmoteDialog();
        if (uriDialog != null) {
            return uriDialog;
        }
        status = "Uri → scroll";
        lastClueNorm = "";
        container.reset();
        resetEmoteUriProgress();
        step = BeginnerClueReference.StepType.UNKNOWN;
        BotRuntime.logConsole("[Clue/Uri] force scroll re-read");
        return container.openAndRead();
    }

    private Integer handleUriAfterEmoteDialog() {
        if (!Dialog.isOpen()) {
            return null;
        }
        if (!emotePerformedThisStep && !uriTalkedAfterEmote) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - lastDialogMs < 200L) {
            return 150;
        }
        lastDialogMs = now;
        if (Dialog.isViewingOptions()) {
            if (Dialog.chooseOption(0)) {
                return 400;
            }
            return 300;
        }
        Dialog.continueSpace();
        return 350;
    }

    private void resetEmoteUriProgress() {
        emotePerformedThisStep = false;
        uriTalkedAfterEmote = false;
        lastUriTalkMs = 0L;
    }

    private static INPC findUriNpc() {
        try {
            INPC byId = NPCs.getNearest(n -> n != null && n.getId() == NPC_ID_URI);
            if (byId != null) {
                return byId;
            }
            INPC exact = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase("Uri"));
            if (exact != null) {
                return exact;
            }
            INPC byName = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains("uri"));
            if (byName != null) {
                return byName;
            }
            WorldPoint me = ClueWalk.me();
            if (me != null) {
                return NPCs.getNearest(n -> n != null && n.getWorldLocation() != null
                        && n.getName() != null
                        && me.distanceTo(n.getWorldLocation()) <= 18
                        && (n.getId() == NPC_ID_URI
                                || n.getName().toLowerCase(Locale.ROOT).contains("uri")));
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean talkUri(INPC uri) {
        return ClueNpcClick.talkToInvoke(uri);
    }

    private int doMapDig(BeginnerClueReference.MapDig map) {
        BeginnerClueReference.MapDig use = map != null ? map : container.openMap();
        if (use == null) {
            use = activeMapDig;
        }
        if (use == null) {
            if (ClueWalk.isTravelBusy()) {
                status = "map travel";
                return 400;
            }
            int opened = container.openAndRead();
            use = container.openMap();
            if (use == null) {
                status = "map ?";
                return opened;
            }
        }
        activeMapDig = use;
        status = "map " + use.label;
        if (!ClueWalk.walkUntilNear(use.dig, BeginnerClueReference.MAP_DIG_ARRIVAL)) {
            return 400;
        }
        if (!ClueWalk.readyToDig(use.dig, BeginnerClueReference.MAP_DIG_ARRIVAL)) {
            status = "map wait dig";
            return 300;
        }
        return digHere("map");
    }

    private int doHotCold() {
        pollChatForDevice();
        status = "hot/cold " + hotCold.statusLine();
        // Device verplicht (CombatBot ensureStrangeDeviceAvailable)
        if (!Inventory.contains(BeginnerClueReference.STRANGE_DEVICE)) {
            int banked = bank.ensureTools(false, true, null);
            if (banked >= 0) {
                return banked;
            }
            // Geen device in bank → Reldo cryptic
            BeginnerClueReference.NpcTarget reldo = BeginnerClueReference.crypticNpc(
                    "buried beneath the ground");
            status = "hot/cold → Reldo device";
            return doTalk(reldo, "buried beneath the ground", "Reldo");
        }
        if (!Inventory.contains(BeginnerClueReference.SPADE)) {
            int needSpade = bank.ensureTools(true, false, null);
            if (needSpade >= 0) {
                return needSpade;
            }
        }

        WorldPoint me = ClueWalk.me();
        if (me == null) {
            return 500;
        }

        // Altijd wachten op Feel-antwoord — geen spam-klik
        int reply = deviceReplyState();
        if (reply == 1) {
            status = "hot/cold wacht Feel-antwoord";
            return 350;
        }

        if (hotCold.hasFinalDigSpot()
                || hotCold.lastTemp() == BeginnerHotColdSolver.Temperature.VISIBLY_SHAKING) {
            WorldPoint dig = hotCold.getFinalDigSpot() != null ? hotCold.getFinalDigSpot() : me;
            if (!ClueWalk.near(dig, BeginnerClueReference.MAP_DIG_ARRIVAL)) {
                ClueWalk.walkUntilNear(dig, BeginnerClueReference.MAP_DIG_ARRIVAL);
                return 400;
            }
            if (!ClueWalk.readyToDig(dig, BeginnerClueReference.MAP_DIG_ARRIVAL)) {
                status = "hot/cold wait dig";
                return 300;
            }
            int dug = digHere("hotcold-shaking");
            if (dug >= 600) {
                clearHotColdSession();
            }
            return dug;
        }

        BeginnerHotColdSolver.BeginnerZone sole = hotCold.soleZone();
        if (sole != null) {
            return doHotColdKnownZone(me, sole);
        }

        if (!hotCold.hasAnyFeel()) {
            return feelDevice(me);
        }

        if (hotColdMoveTarget != null) {
            if (!ClueWalk.near(hotColdMoveTarget, 2)) {
                ClueWalk.walkUntilNear(hotColdMoveTarget, 2);
                return 400;
            }
            hotColdMoveTarget = null;
            return feelDevice(me);
        }

        // Na Feel: eerst 10–25 tiles verplaatsen, dan opnieuw Feel (CombatBot)
        WorldPoint probe = chooseHotColdProbe(me);
        if (probe != null && me.distanceTo(probe) > 2) {
            hotColdMoveTarget = probe;
            BotRuntime.logConsole("[Clue/HotCold] probe → " + probe.getX() + "," + probe.getY()
                    + " zones=" + hotCold.possibleCount());
            ClueWalk.walkUntilNear(probe, 2);
            return 400;
        }
        if (hotColdFeelAttempts < 20) {
            // Zelfde tegel niet opnieuw Feel zonder verplaatsing
            if (lastHotColdFeelTile != null && me.distanceTo(lastHotColdFeelTile) < HOT_COLD_PROBE_MIN
                    && reply != 2) {
                WorldPoint nudge = chooseHotColdProbe(me);
                if (nudge != null) {
                    hotColdMoveTarget = nudge;
                    ClueWalk.walkUntilNear(nudge, 2);
                    return 400;
                }
            }
            return feelDevice(me);
        }
        WorldPoint digFallback = hotCold.centroidOfPossibilities() != null
                ? hotCold.centroidOfPossibilities() : me;
        if (!ClueWalk.near(digFallback, 1)) {
            ClueWalk.walkUntilNear(digFallback, 1);
            return 400;
        }
        if (!ClueWalk.readyToDig(digFallback, 1)) {
            status = "hot/cold wait dig";
            return 300;
        }
        return digHere("hotcold-fallback");
    }

    private int doHotColdKnownZone(WorldPoint me, BeginnerHotColdSolver.BeginnerZone sole) {
        pollChatForDevice();
        WorldPoint center = sole.center;
        int reply = deviceReplyState();
        if (reply == 1) {
            status = "hot/cold wacht Feel-antwoord";
            return 350;
        }
        if (hotCold.hasFinalDigSpot()
                || hotCold.lastTemp() == BeginnerHotColdSolver.Temperature.VISIBLY_SHAKING) {
            WorldPoint dig = hotCold.getFinalDigSpot() != null ? hotCold.getFinalDigSpot() : center;
            if (!ClueWalk.near(dig, 1)) {
                ClueWalk.walkUntilNear(dig, 1);
                return 400;
            }
            if (!ClueWalk.readyToDig(dig, 1)) {
                status = "hot/cold wait dig";
                return 300;
            }
            return digHere("hotcold-zone");
        }
        if (me.distanceTo(center) > HOT_COLD_ZONE_NEAR) {
            BotRuntime.logConsole("[Clue/HotCold] → " + sole.label);
            ClueWalk.walkUntilNear(center, HOT_COLD_ZONE_NEAR);
            return 400;
        }
        BeginnerHotColdSolver.Temperature temp = hotCold.lastTemp();
        if (temp == null || reply == 2) {
            return feelDevice(me);
        }
        if (temp == BeginnerHotColdSolver.Temperature.VISIBLY_SHAKING) {
            return digHere("hotcold-zone");
        }
        if (temp.rank() >= BeginnerHotColdSolver.Temperature.VERY_HOT.rank()
                && me.distanceTo(center) <= 3) {
            return digHere("hotcold-veryhot");
        }
        hotColdFeelAttempts++;
        if (hotColdFeelAttempts > MAX_HOT_COLD_GRID) {
            BotRuntime.logConsole("[Clue/HotCold] grid uitgeput — dig centrum");
            if (!ClueWalk.near(center, 1)) {
                ClueWalk.walkUntilNear(center, 1);
                return 400;
            }
            if (!ClueWalk.readyToDig(center, 1)) {
                status = "hot/cold wait dig";
                return 300;
            }
            return digHere("hotcold-grid-end");
        }
        WorldPoint next = offsetHotColdSearch(center, hotColdFeelAttempts);
        if (me.distanceTo(next) > 1) {
            ClueWalk.walkUntilNear(next, 1);
            return 400;
        }
        return feelDevice(me);
    }

    private WorldPoint chooseHotColdProbe(WorldPoint me) {
        BeginnerHotColdSolver.BeginnerZone zone = ensureProbeZone(me);
        WorldPoint toward = zone != null ? zone.center : hotCold.centroidOfPossibilities();
        if (toward == null) {
            toward = me;
        }
        int step = hotColdProbeStepSize(me, toward);
        int dx = Integer.compare(toward.getX(), me.getX());
        int dy = Integer.compare(toward.getY(), me.getY());
        if (dx == 0 && dy == 0) {
            dx = 1;
        }
        return new WorldPoint(me.getX() + dx * step, me.getY() + dy * step, me.getPlane());
    }

    private BeginnerHotColdSolver.BeginnerZone ensureProbeZone(WorldPoint me) {
        java.util.Set<BeginnerHotColdSolver.BeginnerZone> possible = hotCold.getPossibleZones();
        if (possible.isEmpty()) {
            hotColdProbeZone = null;
            return null;
        }
        if (hotColdProbeZone != null && possible.contains(hotColdProbeZone)) {
            return hotColdProbeZone;
        }
        BeginnerHotColdSolver.BeginnerZone best = null;
        int bestD = Integer.MAX_VALUE;
        for (BeginnerHotColdSolver.BeginnerZone z : possible) {
            int d = me.distanceTo(z.center);
            if (d < bestD) {
                bestD = d;
                best = z;
            }
        }
        hotColdProbeZone = best != null ? best : possible.iterator().next();
        return hotColdProbeZone;
    }

    private static int hotColdProbeStepSize(WorldPoint me, WorldPoint target) {
        if (me == null || target == null) {
            return HOT_COLD_PROBE_MIN;
        }
        int dist = me.distanceTo(target);
        if (dist <= HOT_COLD_PROBE_MIN) {
            return Math.max(1, dist);
        }
        if (dist >= HOT_COLD_PROBE_MAX) {
            return HOT_COLD_PROBE_MAX;
        }
        return HOT_COLD_PROBE_MIN;
    }

    private static WorldPoint offsetHotColdSearch(WorldPoint center, int attempt) {
        if (center == null || attempt <= 1) {
            return center;
        }
        int[][] offs = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 1}, {-2, 1}, {1, 2}, {-1, 2}
        };
        int i = (attempt - 1) % offs.length;
        return new WorldPoint(center.getX() + offs[i][0], center.getY() + offs[i][1], center.getPlane());
    }

    /** 0 = reply binnen; 1 = wacht; 2 = timeout. */
    private int deviceReplyState() {
        if (!hotColdAwaitingReply) {
            return 0;
        }
        if (System.currentTimeMillis() - hotColdFeelSentMs < HOT_COLD_REPLY_TIMEOUT_MS) {
            return 1;
        }
        hotColdAwaitingReply = false;
        BotRuntime.logConsole("[Clue/HotCold] Feel-antwoord timeout — opnieuw na verplaatsing");
        return 2;
    }

    /** Failsafe: chatbuffer pollen als event-hook mist (CombatBot krijgt ChatMessage direct). */
    private void pollChatForDevice() {
        long now = System.currentTimeMillis();
        if (now - lastChatPollMs < 400L) {
            return;
        }
        lastChatPollMs = now;
        try {
            List<String> recent = Chat.getRecentMessages(12);
            if (recent == null) {
                return;
            }
            for (String line : recent) {
                if (line == null || line.isEmpty()) {
                    continue;
                }
                String key = ClueScrollHelper.strip(line);
                if (!key.toLowerCase(Locale.ROOT).contains("device")) {
                    continue;
                }
                if (!seenChat.add(key)) {
                    continue;
                }
                onGameMessage(key);
            }
            if (seenChat.size() > 64) {
                seenChat.clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private int feelDevice(WorldPoint at) {
        if (System.currentTimeMillis() - lastFeelMs < 1_400L) {
            return 300;
        }
        if (hotColdAwaitingReply) {
            return 350;
        }
        closeClueUi();
        IInventoryItem device = Inventory.getFirst(BeginnerClueReference.STRANGE_DEVICE);
        if (device == null) {
            return bank.ensureTools(false, true, null);
        }
        boolean ok = device.interact("Feel") || device.interact("Operate");
        if (ok) {
            lastFeelMs = System.currentTimeMillis();
            hotColdFeelSentMs = lastFeelMs;
            hotColdAwaitingReply = true;
            hotColdFeelAttempts++;
            lastHotColdFeelTile = at != null ? at : ClueWalk.me();
            BotRuntime.logConsole("[Clue/HotCold] Feel @"
                    + (lastHotColdFeelTile != null
                    ? lastHotColdFeelTile.getX() + "," + lastHotColdFeelTile.getY() : "?")
                    + " — wacht chat");
            status = "Feel";
            return 900;
        }
        return 400;
    }

    private void clearHotColdSession() {
        boolean had = hotCold.hasAnyFeel() || hotColdFeelAttempts > 0 || lastHotColdFeelTile != null
                || step == BeginnerClueReference.StepType.HOT_COLD;
        hotCold.reset();
        hotColdFeelAttempts = 0;
        hotColdFeelSentMs = 0L;
        hotColdAwaitingReply = false;
        hotColdMoveTarget = null;
        hotColdProbeZone = null;
        lastHotColdFeelTile = null;
        if (step == BeginnerClueReference.StepType.HOT_COLD) {
            step = BeginnerClueReference.StepType.UNKNOWN;
        }
        if (had) {
            BotRuntime.logConsole("[Clue/HotCold] session reset (geen actieve clue)");
        }
    }

    private int doCharlie(String text) {
        if (!charlieStepStarted) {
            charlie.beginStep();
            charlieStepStarted = true;
        }
        status = charlie.statusLine();
        int delay = charlie.tick(text, bank);
        if (charlie.getRequestedItem() != null) {
            charlieItem = charlie.getRequestedItem();
        }
        if (charlie.consumeScrollRefreshRequest() || charlie.isDeliveryComplete()) {
            lastClueNorm = "";
            container.reset();
            charlieStepStarted = false;
            charlieItem = null;
            step = BeginnerClueReference.StepType.UNKNOWN;
            status = "charlie → nieuwe scroll";
            closeClueUi();
            BotRuntime.logConsole("[Clue/Charlie] force scroll re-read");
            return container.openAndRead();
        }
        return delay;
    }

    private int openCasket() {
        IInventoryItem casket = BeginnerClueContainerHelper.findBeginnerCasket();
        if (casket == null) {
            return 400;
        }
        status = "casket";
        if (casket.interact("Open")) {
            BotRuntime.logConsole("[Clue/Casket] Open");
            container.reset();
            return 700;
        }
        return 500;
    }

    private int digHere(String why) {
        if (System.currentTimeMillis() - lastDigMs < 900L) {
            return 300;
        }
        closeClueUi();
        IInventoryItem spade = Inventory.getFirst(BeginnerClueReference.SPADE);
        if (spade == null) {
            status = "geen spade";
            return bank.ensureTools(true, false, null);
        }
        if (spade.interact("Dig")) {
            lastDigMs = System.currentTimeMillis();
            ClueWalk.clearWalk();
            BotRuntime.logConsole("[Clue/Dig] " + why + " (+clear walk)");
            status = "dig " + why;
            lastClueNorm = "";
            container.reset();
            return 700;
        }
        return 400;
    }

    private void closeClueUi() {
        try {
            String open = container.readOpenOrCached();
            if (open != null && !open.isBlank()) {
                net.storm.sdk.input.Keyboard.pressKey(java.awt.event.KeyEvent.VK_ESCAPE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ');
        return t.length() > 70 ? t.substring(0, 70) : t;
    }
}
