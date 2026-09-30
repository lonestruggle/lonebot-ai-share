package com.lonebot.example.clue;

import net.runelite.api.coords.WorldPoint;
import net.storm.api.domain.actors.INPC;
import net.storm.api.domain.actors.IPlayer;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.entities.NPCs;
import net.storm.sdk.entities.Players;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.widgets.Dialog;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Charlie the Tramp beginner clue FSM (CombatBot port):
 * FIRST_TALK → DELIVER → AWAIT_NEW_CLUE (3 Talk-to's).
 */
public final class CharlieClueHelper {

    public enum CharliePhase {
        FIRST_TALK,
        DELIVER,
        AWAIT_NEW_CLUE
    }

    private final ClueOpenNpcApproachHelper approach = ClueOpenNpcApproachHelper.charlie();
    private final BeginnerClueContainerHelper dialogReader = new BeginnerClueContainerHelper();

    private CharliePhase phase = CharliePhase.FIRST_TALK;
    private String requestedItem;
    private int invQtyAtRequest = -1;
    private int talkCount;
    private boolean deliveryComplete;
    private boolean thirdTalkDialogWasOpen;
    private boolean thirdTalkJustFinished;
    private boolean scrollRefreshRequested;
    private long lastDialogMs;
    private long lastGiveMs;
    private long lastLogMs;
    private String lastLogMsg = "";

    public void reset() {
        beginStep();
    }

    public void beginStep() {
        requestedItem = null;
        invQtyAtRequest = -1;
        deliveryComplete = false;
        phase = CharliePhase.FIRST_TALK;
        talkCount = 0;
        lastGiveMs = 0L;
        thirdTalkDialogWasOpen = false;
        thirdTalkJustFinished = false;
        scrollRefreshRequested = false;
        approach.reset();
        logThrottled("[Clue/Charlie] stap start");
    }

    public CharliePhase getPhase() {
        return phase;
    }

    public String getRequestedItem() {
        return requestedItem;
    }

    public boolean isDeliveryComplete() {
        return deliveryComplete;
    }

    public boolean consumeScrollRefreshRequest() {
        if (!scrollRefreshRequested) {
            return false;
        }
        scrollRefreshRequested = false;
        return true;
    }

    public String statusLine() {
        String item = requestedItem != null ? requestedItem : "?";
        return "charlie " + phase.name().toLowerCase(Locale.ROOT) + " " + item;
    }

    public void onGameMessage(String message) {
        if (message == null || message.isEmpty() || deliveryComplete) {
            return;
        }
        String low = ClueScrollHelper.strip(message).toLowerCase(Locale.ROOT);
        if ((low.contains("thank") && low.contains("charlie"))
                || low.contains("charlie gives you")
                || (low.contains("charlie") && low.contains("another clue"))
                || (low.contains("charlie") && low.contains("clue scroll"))) {
            checkItemConsumed();
            if (deliveryConfirmed()) {
                if (talkCount >= 3) {
                    completeDelivery("chat bedankt");
                } else {
                    phase = CharliePhase.AWAIT_NEW_CLUE;
                    logThrottled("[Clue/Charlie] chat bedankt — nog gesprek 3");
                }
            }
        }
        String parsed = BeginnerClueReference.parseCharlieItemRequestFromDialog(message);
        if (parsed != null && !parsed.equals(requestedItem)) {
            recordItemRequest(parsed, "chat");
            onFirstTalkComplete();
        }
    }

    /**
     * Main Charlie tick.
     *
     * @param clueText current scroll text (may be empty)
     * @param bank     bank helper for unnoted withdraw / noted fix
     * @return delay ms
     */
    public int tick(String clueText, ClueBankHelper bank) {
        applyPostDeliveryFromScroll(clueText);
        checkItemConsumed();
        updateThirdTalkDialogState();

        Integer dialog = handleCharlieDialog();
        if (dialog != null) {
            return dialog;
        }
        rememberItemFromDialog();
        checkItemConsumed();

        if (thirdTalkJustFinished) {
            thirdTalkJustFinished = false;
            return afterThirdTalk();
        }

        if (deliveryComplete) {
            return afterThirdTalk();
        }

        if (tryCompleteAfterGive(clueText)) {
            scrollRefreshRequested = true;
            return vary(500, 900);
        }

        // Item in inv → Talk-to (niet Use-on). Charlie vraagt item via dialoog.
        if (phase == CharliePhase.DELIVER && requestedItem != null) {
            if (!hasItemExact(requestedItem)) {
                int fetch = bank.ensureCharlieItem(requestedItem);
                if (fetch >= 0) {
                    return fetch;
                }
                logThrottled("[Clue/Charlie] mist " + requestedItem + " (bank leeg / geen GE)");
                return vary(2_000, 3_500);
            }
            refreshInvBaselineBeforeDelivery();
            if (tryCompleteAfterGive(clueText)) {
                scrollRefreshRequested = true;
                return vary(500, 900);
            }
            // CombatBot: eerst dialog-optie via Talk; Use-on als fallback
            int give = tryGiveUseOn();
            if (give > 0) {
                return give;
            }
            return walkAndTalk("gesprek 2");
        }

        if (phase == CharliePhase.AWAIT_NEW_CLUE) {
            if (tryCompleteAfterGive(clueText)) {
                scrollRefreshRequested = true;
                return vary(500, 900);
            }
            return walkAndTalk("gesprek 3");
        }

        applyItemFromScrollIfPresent(clueText);
        if (phase == CharliePhase.DELIVER && requestedItem != null) {
            return vary(400, 700);
        }
        return walkAndTalk("gesprek 1");
    }

    private int afterThirdTalk() {
        scrollRefreshRequested = true;
        deliveryComplete = true;
        logThrottled("[Clue/Charlie] gesprek 3 klaar — scroll lezen");
        return vary(450, 750);
    }

    private void applyItemFromScrollIfPresent(String clueText) {
        if (clueText == null || clueText.isEmpty()) {
            return;
        }
        if (!BeginnerClueReference.scrollTextRequiresCharlieDelivery(clueText)) {
            return;
        }
        String parsed = BeginnerClueReference.parseCharlieItemRequestFromDialog(clueText);
        if (parsed != null && !parsed.equals(requestedItem)) {
            recordItemRequest(parsed, "scroll");
        }
    }

    private void applyPostDeliveryFromScroll(String clueText) {
        if (clueText == null || clueText.isEmpty()) {
            return;
        }
        if (!BeginnerClueReference.scrollTextCharliePostDeliveryTalk(clueText)) {
            return;
        }
        String item = BeginnerClueReference.parseCharlieItemFromPostDeliveryScroll(clueText);
        if (item != null) {
            requestedItem = item;
        }
        phase = CharliePhase.AWAIT_NEW_CLUE;
        talkCount = 2;
        invQtyAtRequest = 0;
        logThrottled("[Clue/Charlie] scroll: item al gegeven"
                + (item != null ? " (" + item + ")" : "")
                + " — gesprek 3");
    }

    private boolean scrollNeedsFinishTalk(String clueText) {
        return BeginnerClueReference.scrollTextCharliePostDeliveryTalk(clueText);
    }

    private void recordItemRequest(String item, String source) {
        if (item == null || item.isEmpty()) {
            return;
        }
        requestedItem = item;
        invQtyAtRequest = countUnnoted(item);
        phase = CharliePhase.DELIVER;
        logThrottled("[Clue/Charlie] opdracht (" + source + "): " + item
                + " (inv=" + invQtyAtRequest + ")");
    }

    private void onFirstTalkComplete() {
        if (talkCount < 1) {
            talkCount = 1;
        }
        if (requestedItem != null) {
            invQtyAtRequest = countUnnoted(requestedItem);
            phase = CharliePhase.DELIVER;
            logThrottled("[Clue/Charlie] gesprek 1 klaar — wil " + requestedItem
                    + " (inv=" + invQtyAtRequest + ")");
        }
    }

    private void onSecondTalkStarted() {
        if (talkCount < 2) {
            talkCount = 2;
            logThrottled("[Clue/Charlie] gesprek 2 (item afgeven)");
        }
    }

    private void checkItemConsumed() {
        if (requestedItem == null || invQtyAtRequest < 0) {
            return;
        }
        if (phase == CharliePhase.AWAIT_NEW_CLUE || deliveryComplete) {
            return;
        }
        int now = countUnnoted(requestedItem);
        if (now < invQtyAtRequest) {
            phase = CharliePhase.AWAIT_NEW_CLUE;
            logThrottled("[Clue/Charlie] item uit inv: " + requestedItem
                    + " (" + invQtyAtRequest + "→" + now + ") — gesprek 3");
        }
    }

    private boolean itemConsumed() {
        if (requestedItem == null || invQtyAtRequest < 0) {
            return false;
        }
        return countUnnoted(requestedItem) < invQtyAtRequest;
    }

    private boolean deliveryConfirmed() {
        return itemConsumed();
    }

    private void refreshInvBaselineBeforeDelivery() {
        if (requestedItem == null || phase != CharliePhase.DELIVER) {
            return;
        }
        int qty = countUnnoted(requestedItem);
        if (qty <= 0) {
            return;
        }
        if (invQtyAtRequest < 0 || qty > invQtyAtRequest) {
            invQtyAtRequest = qty;
            logThrottled("[Clue/Charlie] inv-baseline: " + requestedItem + "=" + qty);
        }
    }

    private boolean tryCompleteAfterGive(String clueText) {
        if (deliveryComplete || requestedItem == null) {
            return false;
        }
        checkItemConsumed();
        if (Dialog.isOpen()) {
            return false;
        }
        if (phase != CharliePhase.AWAIT_NEW_CLUE) {
            return false;
        }
        if (talkCount < 3 && !scrollNeedsFinishTalk(clueText)) {
            return false;
        }
        // Only complete after talk 3 when scroll no longer looks like Charlie delivery.
        if (clueText != null && !clueText.isEmpty()
                && !BeginnerClueReference.looksLikeCharlie(clueText)
                && !BeginnerClueReference.scrollTextRequiresCharlieDelivery(clueText)
                && !BeginnerClueReference.scrollTextCharliePostDeliveryTalk(clueText)) {
            completeDelivery("nieuwe clue");
            return true;
        }
        return false;
    }

    private void completeDelivery(String reason) {
        logThrottled("[Clue/Charlie] afgerond (" + reason + ")"
                + (requestedItem != null ? ": " + requestedItem : ""));
        deliveryComplete = true;
        scrollRefreshRequested = true;
        requestedItem = null;
        phase = CharliePhase.FIRST_TALK;
        talkCount = 0;
        invQtyAtRequest = -1;
        lastGiveMs = 0L;
    }

    private void updateThirdTalkDialogState() {
        if (deliveryComplete) {
            thirdTalkDialogWasOpen = false;
            return;
        }
        if (Dialog.isOpen()) {
            if (phase == CharliePhase.AWAIT_NEW_CLUE && talkCount >= 3) {
                thirdTalkDialogWasOpen = true;
            }
            return;
        }
        if (thirdTalkDialogWasOpen
                && phase == CharliePhase.AWAIT_NEW_CLUE
                && talkCount >= 3) {
            thirdTalkDialogWasOpen = false;
            thirdTalkJustFinished = true;
            logThrottled("[Clue/Charlie] gesprek 3 dialoog gesloten");
        }
    }

    private int walkAndTalk(String talkLabel) {
        if (Dialog.isOpen()) {
            Integer d = handleCharlieDialog();
            return d != null ? d : 200;
        }
        IPlayer local = Players.getLocal();
        INPC charlie = findCharlie();
        int approachDelay = approach.approachBeforeTalk(local, charlie);
        if (approachDelay > 0) {
            return approachDelay;
        }
        charlie = findCharlie();
        if (charlie == null) {
            WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
            int dist = me != null ? me.distanceTo(BeginnerClueReference.CHARLIE_TILE) : 999;
            logThrottled("[Clue/Charlie] NPC niet in scene (dist=" + dist + ")");
            approachDelay = approach.approachBeforeTalk(local, null);
            return approachDelay > 0 ? approachDelay : vary(400, 700);
        }
        WorldPoint me = local != null ? local.getWorldLocation() : ClueWalk.me();
        if (me == null || !approach.canTalkNow(me, charlie)) {
            return approach.approachBeforeTalk(local, charlie);
        }
        if (phase == CharliePhase.DELIVER) {
            refreshInvBaselineBeforeDelivery();
            onSecondTalkStarted();
        } else if (phase == CharliePhase.AWAIT_NEW_CLUE && talkCount < 3) {
            talkCount = 3;
            logThrottled("[Clue/Charlie] gesprek 3 (nieuwe clue)");
        }
        logThrottled("[Clue/Charlie] Talk-to (" + talkLabel + ")");
        if (ClueNpcClick.talkToInvoke(charlie)) {
            return vary(500, 800);
        }
        int cos = ClueNpcClick.talkOrApproach(charlie, BeginnerClueReference.CHARLIE_TILE);
        return cos > 0 ? cos : 400;
    }

    /**
     * CombatBot fallback: Use item on Charlie als dialoog geen give-optie toont.
     * Primair blijft Talk-to + dialog (zie handleCharlieDialog).
     */
    private int tryGiveUseOn() {
        if (Dialog.isOpen()) {
            return 0;
        }
        String needed = requestedItem;
        if (needed == null || !hasItemExact(needed)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastGiveMs < 1_100L) {
            return 300;
        }
        IPlayer local = Players.getLocal();
        INPC charlie = findCharlie();
        if (charlie == null) {
            return 0;
        }
        WorldPoint me = local != null ? local.getWorldLocation() : null;
        if (me == null || !approach.canTalkNow(me, charlie)) {
            return 0;
        }
        IInventoryItem invItem = Inventory.getFirst(i -> i != null && i.getName() != null
                && !i.isNoted() && i.getName().equalsIgnoreCase(needed));
        if (invItem == null) {
            return 0;
        }
        if (invItem.useOn(charlie)) {
            lastGiveMs = now;
            BotRuntime.logConsole("[Clue/Charlie] Use " + needed + " on Charlie (fallback)");
            return vary(1_100, 1_900);
        }
        return 0;
    }

    private Integer handleCharlieDialog() {
        if (!Dialog.isOpen()) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - lastDialogMs < 350L) {
            return 120;
        }
        lastDialogMs = now;
        rememberItemFromDialog();

        // Default tramp-menu = geen clue-gesprek → klaar, scroll opnieuw lezen
        if (isDefaultTrampOptions()) {
            Dialog.continueSpace();
            completeDelivery("tramp-opties (Who are you / Okay here you go)");
            BotRuntime.logConsole("[Clue/Charlie] tramp-opties → scroll lezen");
            return vary(500, 900);
        }

        if (Dialog.isViewingOptions()) {
            if (Dialog.hasOption(s -> optionContains(s, "what can i do"))) {
                Dialog.chooseOption(s -> optionContains(s, "what can i do"));
                BotRuntime.logConsole("[Clue/Charlie] dialog: What can I do for you?");
                return vary(500, 900);
            }
            if (requestedItem != null
                    && Dialog.hasOption(s -> optionContains(s, "sure")
                    || optionContains(s, "glad to help"))) {
                Dialog.chooseOption(s -> optionContains(s, "sure")
                        || optionContains(s, "glad to help"));
                BotRuntime.logConsole("[Clue/Charlie] dialog: Sure");
                return vary(500, 900);
            }
            if (requestedItem != null && hasItemExact(requestedItem)) {
                String item = requestedItem;
                String lowItem = item.toLowerCase(Locale.ROOT);
                if (Dialog.hasOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowItem))) {
                    Dialog.chooseOption(s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowItem));
                    lastGiveMs = System.currentTimeMillis();
                    BotRuntime.logConsole("[Clue/Charlie] dialog: geef " + item);
                    return vary(500, 900);
                }
            }
            // Clue give-phrases alleen als géén tramp-menu
            String[] givePhrases = {"here you are", "i have the", "i'll give", "take this"};
            for (String phrase : givePhrases) {
                if (Dialog.hasOption(s -> optionContains(s, phrase))) {
                    Dialog.chooseOption(s -> optionContains(s, phrase));
                    return vary(500, 900);
                }
            }
            // Opties open maar onbekend — niet Talk-to spammen
            return vary(300, 500);
        }

        String body = dialogReader.readDialogBody();
        if (body != null) {
            String low = body.toLowerCase(Locale.ROOT);
            if (low.contains("spare some change")) {
                Dialog.continueSpace();
                completeDelivery("Spare some change — geen clue-gesprek");
                BotRuntime.logConsole("[Clue/Charlie] Spare some change → scroll lezen");
                return vary(500, 900);
            }
            if (low.contains("please") && low.contains("help")) {
                Dialog.continueSpace();
                BotRuntime.logConsole("[Clue/Charlie] dialog: continue (help)");
                return vary(500, 900);
            }
            if (requestedItem != null && low.contains("really need")) {
                onFirstTalkComplete();
                Dialog.continueSpace();
                return vary(500, 900);
            }
            if (low.contains("thank") || low.contains("another clue")
                    || low.contains("well done") || low.contains("here's your")
                    || low.contains("here is your") || low.contains("here is a")
                    || low.contains("clue scroll") || low.contains("new clue")
                    || low.contains("take this")) {
                checkItemConsumed();
                if (deliveryConfirmed() && talkCount >= 3) {
                    Dialog.continueSpace();
                    thirdTalkJustFinished = true;
                    BotRuntime.logConsole("[Clue/Charlie] bedankt (gesprek 3)");
                    return vary(500, 900);
                } else if (deliveryConfirmed()) {
                    phase = CharliePhase.AWAIT_NEW_CLUE;
                    logThrottled("[Clue/Charlie] bedankt — nog gesprek 3");
                }
                Dialog.continueSpace();
                return vary(500, 900);
            }
        }
        if (Dialog.canContinue()) {
            Dialog.continueSpace();
            return vary(500, 900);
        }
        Dialog.continueSpace();
        return vary(500, 900);
    }

    /** Standaard Charlie-tramp menu (geen treasure-trail). */
    static boolean isDefaultTrampOptions() {
        if (!Dialog.isViewingOptions()) {
            return false;
        }
        boolean who = Dialog.hasOption(s -> optionContains(s, "who are you"));
        boolean job = Dialog.hasOption(s -> optionContains(s, "go get a job"));
        boolean sorry = Dialog.hasOption(s -> optionContains(s, "haven't got any")
                || optionContains(s, "have not got any"));
        return who && (job || sorry);
    }

    private void rememberItemFromDialog() {
        if (deliveryComplete) {
            return;
        }
        String body = dialogReader.readDialogBody();
        String parsed = BeginnerClueReference.parseCharlieItemRequestFromDialog(body);
        if (parsed != null && !parsed.equals(requestedItem)) {
            recordItemRequest(parsed, "dialoog");
            onFirstTalkComplete();
            BotRuntime.logConsole("[Clue/Charlie] vraagt: " + parsed);
        }
    }

    static boolean hasItemExact(String itemName) {
        return countUnnoted(itemName) > 0;
    }

    static int countUnnoted(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return 0;
        }
        String canon = BeginnerClueReference.canonicalCharlieItemName(itemName);
        int total = 0;
        try {
            for (IInventoryItem item : Inventory.getAll()) {
                if (item == null || item.getName() == null || item.isNoted()) {
                    continue;
                }
                if (item.getName().equalsIgnoreCase(canon)) {
                    total += Math.max(0, item.getQuantity());
                }
            }
        } catch (Throwable ignored) {
        }
        return total;
    }

    static boolean hasNotedItem(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return false;
        }
        String canon = BeginnerClueReference.canonicalCharlieItemName(itemName);
        try {
            for (IInventoryItem item : Inventory.getAll()) {
                if (item == null || item.getName() == null || !item.isNoted()) {
                    continue;
                }
                if (item.getName().equalsIgnoreCase(canon) && item.getQuantity() > 0) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static INPC findCharlie() {
        try {
            INPC byId = NPCs.getNearest(n -> n != null
                    && n.getId() == BeginnerClueReference.NPC_ID_CHARLIE_TRAMP);
            if (byId != null) {
                return byId;
            }
            INPC exact = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().equalsIgnoreCase("Charlie the Tramp"));
            if (exact != null) {
                return exact;
            }
            INPC byName = NPCs.getNearest(n -> n != null && n.getName() != null
                    && n.getName().toLowerCase(Locale.ROOT).contains("charlie"));
            if (byName != null) {
                return byName;
            }
            return NPCs.getNearest(n -> n != null
                    && n.getWorldLocation() != null
                    && n.getWorldLocation().distanceTo(BeginnerClueReference.CHARLIE_TILE) <= 22
                    && ((n.getId() == BeginnerClueReference.NPC_ID_CHARLIE_TRAMP)
                    || (n.getName() != null && (n.getName().toLowerCase(Locale.ROOT).contains("charlie")
                    || n.getName().toLowerCase(Locale.ROOT).contains("tramp")))));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean optionContains(String option, String phrase) {
        if (option == null || phrase == null) {
            return false;
        }
        return ClueScrollHelper.strip(option).toLowerCase(Locale.ROOT).contains(phrase);
    }

    private static int vary(int min, int max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLogMsg) && now - lastLogMs < 1_500L) {
            return;
        }
        lastLogMs = now;
        lastLogMsg = msg;
        BotRuntime.logConsole(msg);
    }
}
