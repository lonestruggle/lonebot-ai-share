package net.storm.sdk.widgets;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.storm.api.domain.widgets.IWidget;
import net.storm.api.widgets.IDialog;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Static;
import net.storm.sdk.input.Keyboard;
import net.storm.sdk.interact.ClickPoints;
import net.storm.sdk.interact.MenuInteract;
import net.storm.sdk.interact.mouse.MouseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Storm {@code Dialog} — NPC/player continue, option lists, amount/name input.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/widgets/Dialog.html">Storm Dialog</a>
 */
public final class Dialog {

    private static final Logger log = LoggerFactory.getLogger(Dialog.class);

    public static final IDialog API = new Api();

    static {
        net.storm.api.Static.bindDialog(API);
    }

    public Dialog() {
    }

    public static boolean isOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            if (visible(safe(c, WidgetInfo.DIALOG_NPC_TEXT))
                    || visible(safe(c, WidgetInfo.DIALOG_PLAYER_TEXT))
                    || visible(safe(c, WidgetInfo.DIALOG_OPTION_OPTIONS))) {
                return true;
            }
            // "You show X your clue" e.d. — alleen Click here / sprite, geen NPC-text
            if (continueWidget(c) != null || visibleSpriteDialog(c)) {
                return true;
            }
            return isEnterInputOpenOnClient(c);
        }, false));
    }

    public static boolean canContinue() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            // Continue-regel zit vaak onder OPTIONS; dat is géén keuze-menu
            if (continueWidget(c) != null) {
                return true;
            }
            if (visible(safe(c, WidgetInfo.DIALOG_OPTION_OPTIONS))) {
                return false;
            }
            Widget npc = safe(c, WidgetInfo.DIALOG_NPC_TEXT);
            Widget player = safe(c, WidgetInfo.DIALOG_PLAYER_TEXT);
            if (visible(npc) || visible(player)) {
                return true;
            }
            return visibleSpriteDialog(c);
        }, false));
    }

    public static void continueSpace() {
        Keyboard.pressKey(KeyEvent.VK_SPACE);
    }

    public static void continueTutorial() {
        continueSpace();
    }

    private static volatile long lastAutoContinueMs;
    private static volatile long lastAutoContinueLogMs;

    /**
     * Auto-continue: bij elke “Click here to continue” / NPC-continue / sprite-show-clue
     * → Space + eventueel {@code WIDGET_CONTINUE}-invoke.
     * Kiest <strong>geen</strong> multi-choice opties ({@link #isViewingOptions} met echte keuzes).
     * Respecteert {@link BotRuntime#dialogAutoContinue} tenzij {@code force}.
     *
     * @param force {@code true} = altijd (Clue/Reldo); {@code false} = alleen als config aan
     * @return {@code true} als continue werd gezet of throttle nog loopt (caller: korte delay)
     */
    public static boolean autoContinue(boolean force) {
        if (!force && !BotRuntime.dialogAutoContinue) {
            return false;
        }
        if (!canContinue()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoContinueMs < 280L) {
            return true;
        }
        lastAutoContinueMs = now;

        boolean invoked = Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            Widget w = continueWidget(c);
            if (w == null || w.isHidden()) {
                return false;
            }
            String text = w.getText();
            return invokeWidgetContinue(w, text != null ? WidgetText.strip(text) : "Click here to continue");
        }, false));

        continueSpace();

        if (now - lastAutoContinueLogMs >= 1_500L) {
            lastAutoContinueLogMs = now;
            String how = invoked ? "invoke+space" : "space";
            String scope = force ? "force" : "on";
            log.info("[Dialog] auto-continue ({}, {})", how, scope);
            BotRuntime.logConsole("[Dialog] auto-continue (" + how + ", " + scope + ")");
        }
        return true;
    }

    /** Zelfde als {@link #autoContinue(boolean)} met force=false (respecteert config). */
    public static boolean autoContinue() {
        return autoContinue(false);
    }

    /**
     * @param force zie {@link #autoContinue(boolean)}
     * @return delay ms als continue gedaan; {@code -1} als er niets te continuen was / uitgeschakeld
     */
    public static int autoContinueDelay(boolean force) {
        return autoContinue(force) ? 280 : -1;
    }

    public static int autoContinueDelay() {
        return autoContinueDelay(false);
    }

    public static boolean isEnterInputOpen() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            return c != null && isEnterInputOpenOnClient(c);
        }, false));
    }

    public static void enterFriendName(String input) {
        enterText(input);
    }

    public static void enterChatChannelName(String input) {
        enterText(input);
    }

    public static void enterName(String input) {
        enterText(input);
    }

    public static void enterText(String input) {
        if (input != null) {
            Keyboard.type(input);
        }
        Keyboard.pressKey(KeyEvent.VK_ENTER);
    }

    public static void enterAmount(int input) {
        enterText(String.valueOf(input));
    }

    public static void input(int inputType, String value) {
        enterText(value);
    }

    public static boolean isViewingOptions() {
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            return visible(safe(c, WidgetInfo.DIALOG_OPTION_OPTIONS));
        }, false));
    }

    public static boolean chooseOption(int index) {
        List<IWidget> opts = getOptions();
        if (index >= 0 && index < opts.size()) {
            IWidget w = opts.get(index);
            if (clickOptionWidget(w)) {
                return true;
            }
        }
        return pressOptionKey(index);
    }

    public static boolean chooseOption(String... options) {
        if (options == null) {
            return false;
        }
        for (String o : options) {
            if (o != null && chooseOption(t -> WidgetText.containsIgnoreCase(t, o)
                    || WidgetText.equalsIgnoreCase(t, o))) {
                return true;
            }
        }
        return false;
    }

    public static boolean chooseOption(Predicate<String> option) {
        if (option == null) {
            return false;
        }
        List<IWidget> opts = getOptions();
        for (int i = 0; i < opts.size(); i++) {
            IWidget w = opts.get(i);
            String text = optionText(w);
            if (text != null && option.test(text)) {
                if (clickOptionWidget(w) || pressOptionKey(i)) {
                    logChoose(text, i);
                    return true;
                }
            }
        }
        log.debug("[Dialog] chooseOption — no matching option");
        return false;
    }

    public static boolean chooseAnyOption(String... options) {
        return chooseOption(options);
    }

    public static boolean hasOption(String option) {
        if (option == null) {
            return false;
        }
        return hasOption(t -> WidgetText.containsIgnoreCase(t, option)
                || WidgetText.equalsIgnoreCase(t, option));
    }

    public static boolean hasOption(Predicate<String> option) {
        if (option == null) {
            return false;
        }
        for (IWidget w : getOptions()) {
            String t = optionText(w);
            if (t != null && option.test(t)) {
                return true;
            }
        }
        return false;
    }

    public static IWidget getOption(String option) {
        if (option == null) {
            return null;
        }
        return getOption(t -> WidgetText.containsIgnoreCase(t, option)
                || WidgetText.equalsIgnoreCase(t, option));
    }

    public static IWidget getOption(Predicate<String> option) {
        if (option == null) {
            return null;
        }
        for (IWidget w : getOptions()) {
            String t = optionText(w);
            if (t != null && option.test(t)) {
                return w;
            }
        }
        return null;
    }

    public static IWidget getOptionTitle() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget options = safe(c, WidgetInfo.DIALOG_OPTION_OPTIONS);
            if (options == null) {
                return null;
            }
            Widget parent = options.getParent();
            if (parent != null) {
                Widget[] kids = parent.getChildren();
                if (kids != null && kids.length > 0 && kids[0] != null) {
                    return Widgets.wrap(kids[0]);
                }
            }
            return Widgets.wrap(options);
        }, null);
    }

    /**
     * Storm: option widgets (not raw strings).
     */
    public static List<IWidget> getOptions() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return Collections.emptyList();
            }
            Widget options = safe(c, WidgetInfo.DIALOG_OPTION_OPTIONS);
            if (options == null || options.isHidden()) {
                return Collections.emptyList();
            }
            List<IWidget> out = new ArrayList<>();
            Widget[] children = options.getChildren();
            if (children == null) {
                children = options.getDynamicChildren();
            }
            if (children == null) {
                return out;
            }
            for (Widget child : children) {
                if (child == null || child.isHidden()) {
                    continue;
                }
                String t = child.getText();
                if (t == null || WidgetText.strip(t).isEmpty()) {
                    continue;
                }
                String stripped = WidgetText.strip(t).toLowerCase(Locale.ROOT);
                if (stripped.contains("select an option") || stripped.contains("click here to continue")) {
                    continue;
                }
                out.add(Widgets.wrap(child));
            }
            return out;
        }, Collections.emptyList());
    }

    public static void forceOpen() {
        log.debug("[Dialog] forceOpen — no client hook; no-op");
    }

    public static void forceClose() {
        Keyboard.pressKey(KeyEvent.VK_ESCAPE);
    }

    public static String getText() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            Widget dialog = safe(c, WidgetInfo.DIALOG_NPC_TEXT);
            if (visible(dialog) && dialog.getText() != null) {
                return WidgetText.strip(dialog.getText());
            }
            Widget player = safe(c, WidgetInfo.DIALOG_PLAYER_TEXT);
            if (visible(player) && player.getText() != null) {
                return WidgetText.strip(player.getText());
            }
            return null;
        }, null);
    }

    public static String getName() {
        return Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return null;
            }
            try {
                Widget name = c.getWidget(WidgetInfo.DIALOG_NPC_NAME);
                if (visible(name) && name.getText() != null) {
                    return WidgetText.strip(name.getText());
                }
            } catch (Throwable ignored) {
            }
            Widget npc = safe(c, WidgetInfo.DIALOG_NPC_HEAD_MODEL);
            if (npc != null && npc.getName() != null && !npc.getName().isEmpty()) {
                return WidgetText.strip(npc.getName());
            }
            return null;
        }, null);
    }

    /**
     * Chat-opties (“Select an option”) zijn geen inventory-CC_OP.
     * {@code interact("Continue")} vuurt CC_OP en “lukt” zonder dat de optie gekozen wordt.
     */
    private static boolean clickOptionWidget(IWidget w) {
        if (w == null) {
            return false;
        }
        Widget raw = unwrap(w);
        try {
            if (raw != null) {
                net.runelite.api.Point p = ClickPoints.forWidget(raw);
                // Chatbox = UI-zone: allowUi, anders blokkeert UiClickGuard de klik.
                if (p != null && MouseManager.interactAt(p, true)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return invokeWidgetContinue(raw, optionText(w));
    }

    private static boolean invokeWidgetContinue(Widget raw, String optionText) {
        if (raw == null) {
            return false;
        }
        return Boolean.TRUE.equals(Static.callOnClientThread(() -> {
            Client c = Static.getClient();
            if (c == null) {
                return false;
            }
            int p0 = raw.getIndex();
            if (p0 < 0) {
                p0 = -1;
            }
            int packed = raw.getId();
            String opt = "Continue";
            String target = optionText != null ? optionText : "";
            try {
                c.menuAction(p0, packed, MenuAction.WIDGET_CONTINUE, 0, 0, opt, target);
                return true;
            } catch (Throwable t) {
                return MenuInteract.invokeMenu(opt, target, 0, MenuAction.WIDGET_CONTINUE.getId(),
                        p0, packed, 0);
            }
        }, false));
    }

    private static Widget unwrap(IWidget w) {
        if (w instanceof RlWidget) {
            return ((RlWidget) w).raw();
        }
        return null;
    }

    private static String optionText(IWidget w) {
        if (w == null) {
            return null;
        }
        String t = w.getText();
        if (t != null && !t.isBlank()) {
            return WidgetText.strip(t);
        }
        return WidgetText.strip(w.getName());
    }

    private static boolean pressOptionKey(int index) {
        if (index < 0 || index > 8) {
            return false;
        }
        return Keyboard.pressDigit(index + 1);
    }

    private static volatile long lastChooseLogMs;

    private static void logChoose(String text, int index) {
        long now = System.currentTimeMillis();
        if (now - lastChooseLogMs < 1500L) {
            return;
        }
        lastChooseLogMs = now;
        String t = text != null ? text : "?";
        log.info("[Dialog] kies [{}] {}", index + 1, t);
        BotRuntime.logConsole("[Dialog] kies " + (index + 1) + " — " + t);
    }

    private static boolean isEnterInputOpenOnClient(Client c) {
        try {
            Widget chat = c.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
            if (visible(chat)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Widget title = c.getWidget(WidgetInfo.CHATBOX_TITLE);
            if (visible(title) && title.getText() != null) {
                String t = title.getText().toLowerCase(Locale.ROOT);
                if (t.contains("enter amount") || t.contains("enter name")
                        || t.contains("how many") || t.contains("enter the")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Widget continueWidget(Client c) {
        Widget options = safe(c, WidgetInfo.DIALOG_OPTION_OPTIONS);
        if (options != null) {
            Widget[] kids = options.getChildren();
            if (kids == null) {
                kids = options.getDynamicChildren();
            }
            if (kids != null) {
                for (Widget k : kids) {
                    if (k == null || k.getText() == null || k.isHidden()) {
                        continue;
                    }
                    if (k.getText().toLowerCase(Locale.ROOT).contains("click here to continue")) {
                        return k;
                    }
                }
            }
        }
        return null;
    }

    /** Sprite/object-box dialog (clue tonen aan NPC) — groep OBJECTBOX. */
    private static boolean visibleSpriteDialog(Client c) {
        try {
            Widget root = c.getWidget(net.storm.api.widgets.WidgetGroup.DIALOG_SPRITE_GROUP_ID, 0);
            if (visible(root)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Widget root = c.getWidget(net.storm.api.widgets.WidgetGroup.DIALOG_SPRITE_GROUP_ID, 1);
            return visible(root);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Widget safe(Client c, WidgetInfo info) {
        try {
            return c.getWidget(info);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean visible(Widget w) {
        return w != null && !w.isHidden();
    }

    private static final class Api implements IDialog {
        @Override
        public void continueTutorial() {
            Dialog.continueTutorial();
        }

        @Override
        public boolean isEnterInputOpen() {
            return Dialog.isEnterInputOpen();
        }

        @Override
        public boolean isOpen() {
            return Dialog.isOpen();
        }

        @Override
        public void input(int inputType, String value) {
            Dialog.input(inputType, value);
        }

        @Override
        public IWidget getOption(String option) {
            return Dialog.getOption(option);
        }

        @Override
        public IWidget getOption(Predicate<String> option) {
            return Dialog.getOption(option);
        }

        @Override
        public boolean chooseOption(Predicate<String> option) {
            return Dialog.chooseOption(option);
        }

        @Override
        public boolean chooseOption(int index) {
            return Dialog.chooseOption(index);
        }

        @Override
        public boolean canContinue() {
            return Dialog.canContinue();
        }

        @Override
        public void continueSpace() {
            Dialog.continueSpace();
        }

        @Override
        public boolean autoContinue() {
            return Dialog.autoContinue(false);
        }

        @Override
        public boolean autoContinue(boolean force) {
            return Dialog.autoContinue(force);
        }

        @Override
        public int autoContinueDelay() {
            return Dialog.autoContinueDelay(false);
        }

        @Override
        public int autoContinueDelay(boolean force) {
            return Dialog.autoContinueDelay(force);
        }

        @Override
        public IWidget getOptionTitle() {
            return Dialog.getOptionTitle();
        }

        @Override
        public List<IWidget> getOptions() {
            return Dialog.getOptions();
        }

        @Override
        public void forceOpen() {
            Dialog.forceOpen();
        }

        @Override
        public void forceClose() {
            Dialog.forceClose();
        }

        @Override
        public String getText() {
            return Dialog.getText();
        }

        @Override
        public String getName() {
            return Dialog.getName();
        }
    }
}
