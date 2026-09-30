package net.storm.sdk.game;

import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.storm.api.account.GameAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Game {

    private static final Logger log = LoggerFactory.getLogger(Game.class);
    private static volatile GameAccount currentAccount;

    private Game() {
    }

    public static boolean isLoggedIn() {
        return Client.isLoggedIn();
    }

    /**
     * Uitloggen — CombatBot account-switch: eerst bevestiging {@code Click here to logout},
     * daarna Logout-knop, anders tab openen. Caller retry't tot login-scherm.
     */
    public static boolean logout() {
        return logout(LogoutMethod.AUTO);
    }

    public enum LogoutMethod {
        AUTO,
        CONFIRM_CLICK_HERE,
        BUTTON_182_12,
        FIRST_ACTION_182_12,
        PACKED_LOGOUT,
        MENU_CC_OP,
        OPEN_TAB
    }

    public static boolean logout(LogoutMethod method) {
        LogoutMethod m = method != null ? method : LogoutMethod.AUTO;
        Boolean ok = Static.callOnClientThread(() -> {
            net.runelite.api.Client c = Static.getClient();
            if (c == null || !Client.isLoggedIn()) {
                return true;
            }
            try {
                if (net.storm.sdk.community.WorldHopper.isOpen()) {
                    net.storm.sdk.input.Keyboard.pressKey(java.awt.event.KeyEvent.VK_ESCAPE);
                    log.info("Game.logout() ESC world-hopper");
                }
                switch (m) {
                    case CONFIRM_CLICK_HERE:
                        return clickNamed(182, 6, "Click here to logout", "182,6");
                    case BUTTON_182_12:
                        return clickNamed(182, 12, "Logout", "182,12");
                    case FIRST_ACTION_182_12:
                        return clickFirstAction(182, 12, "182,12 first");
                    case PACKED_LOGOUT:
                        return clickPackedLogout();
                    case MENU_CC_OP:
                        return menuLogoutPacked();
                    case OPEN_TAB:
                        return openLogoutTab(c);
                    case AUTO:
                    default:
                        return logoutAuto(c);
                }
            } catch (Throwable t) {
                log.warn("Game.logout() fout: {}", t.toString());
                return false;
            }
        }, false);
        return ok != null && ok;
    }

    private static boolean logoutAuto(net.runelite.api.Client c) {
        if (clickNamed(182, 6, "Click here to logout", "182,6")) {
            return true;
        }
        if (clickPackedLogout()) {
            return true;
        }
        if (clickNamed(182, 12, "Logout", "182,12")) {
            return true;
        }
        boolean tab = openLogoutTab(c);
        // Zelfde tick: als de bevestiging al in de boom zit, meteen klikken.
        if (clickNamed(182, 6, "Click here to logout", "182,6")) {
            return true;
        }
        if (clickPackedLogout()) {
            return true;
        }
        if (clickNamed(182, 12, "Logout", "182,12")) {
            return true;
        }
        return tab;
    }

    private static boolean openLogoutTab(net.runelite.api.Client c) {
        try {
            c.runScript(915, 10);
        } catch (Throwable ignored) {
        }
        net.storm.api.domain.widgets.IWidget tab = net.storm.sdk.widgets.Widgets.get(182, 8);
        if (tab != null && !tab.isHidden() && menuClick(tab, "Logout", 1)) {
            log.info("Game.logout() tab 182,8 openen");
            return true;
        }
        log.info("Game.logout() tab via script 915");
        return false;
    }

    private static boolean clickNamed(int group, int child, String action, String where) {
        net.storm.api.domain.widgets.IWidget btn = net.storm.sdk.widgets.Widgets.get(group, child);
        if (btn == null || btn.isHidden() || !btn.hasAction(action)) {
            return false;
        }
        if (menuClick(btn, action, 1)) {
            log.info("Game.logout() {} {}", where, action);
            return true;
        }
        return false;
    }

    /** CombatBot {@code interact(0)} — eerste menu-actie, geen muis-fallback. */
    private static boolean clickFirstAction(int group, int child, String where) {
        net.storm.api.domain.widgets.IWidget btn = net.storm.sdk.widgets.Widgets.get(group, child);
        if (btn == null || btn.isHidden()) {
            return false;
        }
        if (menuClick(btn, "Logout", 1) || menuClick(btn, "Click here to logout", 1)) {
            log.info("Game.logout() {} first-action", where);
            return true;
        }
        return false;
    }

    /** Alleen {@code menuAction} — geen muis-hover als “succes”. */
    private static boolean menuClick(net.storm.api.domain.widgets.IWidget btn, String action, int identifier) {
        if (btn == null) {
            return false;
        }
        try {
            int packed = btn.getId();
            int p0 = btn.getIndex();
            if (p0 < 0) {
                p0 = -1;
            }
            String target = btn.getName();
            if (target == null || target.isEmpty()) {
                target = btn.getText();
            }
            if (target == null) {
                target = "";
            }
            String opt = (action != null && !action.isBlank()) ? action : "Logout";
            return net.storm.sdk.interact.MenuInteract.invokeMenu(
                    opt, target, identifier,
                    net.runelite.api.MenuAction.CC_OP.getId(),
                    p0, packed);
        } catch (Throwable t) {
            log.warn("Game.logout() menuClick: {}", t.toString());
            return false;
        }
    }

    private static boolean clickPackedLogout() {
        try {
            int packed = net.runelite.api.gameval.InterfaceID.Logout.LOGOUT;
            net.storm.api.domain.widgets.IWidget btn = net.storm.sdk.widgets.Widgets.get(packed);
            if (btn == null || btn.isHidden()) {
                return false;
            }
            if (btn.hasAction("Click here to logout") && menuClick(btn, "Click here to logout", 1)) {
                log.info("Game.logout() packed Click here to logout");
                return true;
            }
            if (btn.hasAction("Logout") && menuClick(btn, "Logout", 1)) {
                log.info("Game.logout() packed Logout");
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean menuLogoutPacked() {
        try {
            int packed = net.runelite.api.gameval.InterfaceID.Logout.LOGOUT;
            boolean invoked = net.storm.sdk.interact.MenuInteract.invokeMenu(
                    "Logout", "", 1,
                    net.runelite.api.MenuAction.CC_OP.getId(),
                    -1, packed);
            log.info("Game.logout() MenuInteract packed ok={}", invoked);
            return invoked;
        } catch (Throwable t) {
            log.warn("Game.logout() MenuInteract: {}", t.toString());
            return false;
        }
    }

    public static GameState getState() {
        net.runelite.api.Client c = Static.getClient();
        return c != null ? c.getGameState() : GameState.UNKNOWN;
    }

    public static boolean isOnLoginScreen() {
        GameState gs = getState();
        return gs == GameState.LOGIN_SCREEN
                || gs == GameState.LOGIN_SCREEN_AUTHENTICATOR
                || gs == GameState.LOGGING_IN;
    }

    /**
     * Current wilderness level for the local player.
     *
     * @return level ≥ 1 in wilderness, else 0 (best-effort WorldPoint formula / RL util reflection)
     */
    public static int getWildernessLevel() {
        Integer level = Static.callOnClientThread(() -> {
            net.runelite.api.Client c = Static.getClient();
            if (c == null) {
                return 0;
            }
            Player p = c.getLocalPlayer();
            if (p == null) {
                return 0;
            }
            WorldPoint wp = p.getWorldLocation();
            if (wp == null) {
                return 0;
            }
            Integer reflected = wildernessLevelReflect(wp);
            if (reflected != null) {
                return Math.max(0, reflected);
            }
            return wildernessLevelFromPoint(wp);
        }, 0);
        return level != null ? level : 0;
    }

    /** Storm alias for {@link #getWildernessLevel()}. */
    public static int getWildyLevel() {
        return getWildernessLevel();
    }

    public static boolean isInWilderness() {
        return getWildernessLevel() > 0;
    }

    private static Integer wildernessLevelReflect(WorldPoint wp) {
        try {
            Class<?> util = Class.forName("net.runelite.client.util.WildernessLevelUtil");
            Object v = util.getMethod("getWildernessLevelFrom", WorldPoint.class).invoke(null, wp);
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Surface wildy: Y ≥ 3523, X 2944–3391. Ondergronds: Y 9920–11299, zelfde X.
     * Zonder die grenzen telt de oude {@code (9920-Y)/8}-formule heel F2P als wildy.
     */
    private static int wildernessLevelFromPoint(WorldPoint point) {
        if (point == null) {
            return 0;
        }
        int x = point.getX();
        int y = point.getY();
        if (x < 2944 || x >= 3392) {
            return 0;
        }
        if (y >= 3523 && y < 6400) {
            return Math.max(0, ((y - 3520) / 8) + 1);
        }
        if (y >= 9920 && y < 11300) {
            return Math.max(0, ((y - 9920) / 8) + 1);
        }
        return 0;
    }

    public static GameAccount getGameAccount() {
        return currentAccount;
    }

    /**
     * Onthoud account lokaal + Storm-compat reflection.
     * Live Jagex-tokens op de injected client: {@code JagexRuntimeLogin} (paneel),
     * niet via herstart. Geen {@code setUsername} (klassieke login).
     */
    public static void setGameAccount(GameAccount account) {
        currentAccount = account;
        if (account == null) {
            return;
        }
        try {
            Client.setOAuthLoginMode();
            if (account.getCharacterId() != null && !account.getCharacterId().isEmpty()) {
                Client.setCharacterId(account.getCharacterId());
            }
            String session = account.getSessionId();
            if (session == null || session.isEmpty()) {
                session = account.getPassword();
            }
            if (session != null && !session.isEmpty()) {
                Client.setSessionId(session);
            }
            if (account.getDisplayName() != null && !account.getDisplayName().isEmpty()) {
                Client.setDisplayName(account.getDisplayName());
            }
            Client.promptCredentials(false);
            log.info("GameAccount prepared for Jagex OAuth: {}", account.getDisplayName());
        } catch (Throwable t) {
            log.warn("setGameAccount reflection: {}", t.toString());
        }
    }
}
