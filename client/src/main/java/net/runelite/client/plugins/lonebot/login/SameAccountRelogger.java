package net.runelite.client.plugins.lonebot.login;

import net.runelite.client.plugins.lonebot.JagexCredentialsHelper;
import net.runelite.client.plugins.lonebot.JagexRuntimeLogin;
import net.runelite.client.plugins.lonebot.LoneBotConfig;
import net.runelite.client.plugins.lonebot.ManagedAccountsStore;
import net.storm.api.account.GameAccount;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.RelogSafeGate;
import net.storm.sdk.game.Client;
import net.storm.sdk.game.Game;
import net.storm.sdk.game.Static;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Periodiek uit- en weer inloggen met hetzelfde account (CombatBot SameAccountRelogger).
 * <p>Timer → wacht veilig (geen combat/loot/bank/wildy/random) → {@link Game#logout()},
 * pauze op login-scherm, Play Now + welkomst-lobby. Scripts hervatten pas in-world.
 */
public class SameAccountRelogger {

    private enum State {
        IDLE,
        WAIT_SAFE,
        LOGGING_OUT,
        WAITING_LOGOUT,
        PAUSED_ON_LOGIN,
        LOGGING_IN
    }

    private final LoneBotConfig config;
    private final Random random = new Random();

    private State state = State.IDLE;
    private Instant sessionStart;
    private int nextRelogMinutes;

    private GameAccount gameAccount;
    private JagexCredentialsHelper.ParsedJagexAccount parsed;
    private boolean isJagexAccount = true;
    private String lastAccountConfig = null;

    private int loginRetries = 0;
    private static final int MAX_LOGIN_RETRIES = 24;
    private Instant pauseUntil;
    private long lastLogoutAttemptMs;
    private long waitLogoutSinceMs;
    private long waitSafeSinceMs;
    private int logoutRetryWhileLoggedIn;
    private static final int MAX_LOGOUT_RETRY_WHILE_LOGGED_IN = 10;
    private static final long ABORT_LOGOUT_STUCK_AFTER_MS = 90_000L;
    private static final long MIN_MS_BETWEEN_RELOG_LOGOUT_RETRIES = 4500L;
    private static final int MS_LOOP_PAUSE_AFTER_RELOG_LOGOUT = 2200;

    public SameAccountRelogger(LoneBotConfig config) {
        this.config = config;
        resetTimer();
    }

    /** Uitloggen / pauze / inloggen — LoopHost slaat alle scripts over. */
    public boolean isRelogFlowActive() {
        return state == State.LOGGING_OUT
                || state == State.WAITING_LOGOUT
                || state == State.PAUSED_ON_LOGIN
                || state == State.LOGGING_IN;
    }

    /** Timer klaar, nog niet uitgelogd — skill-scripts geen nieuwe acties. */
    public boolean isWaitingSafe() {
        return state == State.WAIT_SAFE;
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    public void resetToIdleFromPanel() {
        state = State.IDLE;
        loginRetries = 0;
        pauseUntil = null;
        lastLogoutAttemptMs = 0;
        waitLogoutSinceMs = 0;
        waitSafeSinceMs = 0;
        logoutRetryWhileLoggedIn = 0;
        BotRuntime.breakPending = false;
        resetTimer();
        updatePaintStatus(config.reLogoutEnabled());
        RelogLog.log("Relog", "reset → IDLE");
    }

    private void invokeGameLogout() {
        RelogLog.log("Relog/out", "Game.logout()");
        Game.logout();
    }

    private void abortRelogDueToLogoutLoop() {
        debug("abortRelogDueToLogoutLoop: te veel logout-pogingen of timeout");
        state = State.IDLE;
        logoutRetryWhileLoggedIn = 0;
        waitSafeSinceMs = 0;
        BotRuntime.breakPending = false;
        resetTimer();
        setStatus("⛔ Re-log gestopt: uitloggen lukt niet — log uit via de client of herstart");
    }

    public void forceLogoutNow() {
        state = State.LOGGING_OUT;
        loginRetries = 0;
        sessionStart = Instant.now();
        setStatus("🔁 Re-log (force) — uitloggen");
        debug("forceLogoutNow: state=LOGGING_OUT");
    }

    /** UI / remote: start dezelfde break als de timer, inclusief veilig-wacht. */
    public void requestBreakNow() {
        if (state == State.WAIT_SAFE || isRelogFlowActive()) {
            RelogLog.log("Relog", "Break nu genegeerd — al bezig (" + state + ")");
            return;
        }
        enterWaitSafe("Break nu");
    }

    /**
     * Stop de break. Nog in-world → scripts hervatten. Al op login/welcome → pauze overslaan en inloggen.
     */
    public void cancelBreak() {
        if (state == State.IDLE) {
            RelogLog.log("Relog", "Annuleer break: geen break actief");
            return;
        }
        boolean inWorld = false;
        try {
            inWorld = Game.isLoggedIn() && WelcomeScreenPlayHelper.isInGameWorld();
        } catch (Throwable ignored) {
            inWorld = Game.isLoggedIn();
        }
        if (inWorld && (state == State.WAIT_SAFE
                || state == State.LOGGING_OUT
                || state == State.WAITING_LOGOUT)) {
            RelogLog.log("Relog", "Annuleer break → IDLE (blijf in game, state was " + state + ")");
            resetToIdleFromPanel();
            setStatus("✓ Break geannuleerd — script hervat");
            return;
        }
        RelogLog.log("Relog", "Annuleer break: pauze overslaan → inloggen (state was " + state + ")");
        pauseUntil = null;
        loginRetries = 0;
        waitSafeSinceMs = 0;
        BotRuntime.breakPending = false;
        state = State.LOGGING_IN;
        setStatus("🔁 Break geannuleerd — inloggen");
        updatePaintStatus(true);
    }

    private void debug(String msg) {
        RelogLog.log("Relog", msg);
    }

    private void setStatus(String msg) {
        BotRuntime.lastRelogStatus = msg != null ? msg : "";
        RelogLog.logThrottled("Relog", "status", msg);
    }

    /**
     * Hoofdmethode – aanroepen vanuit RelogLoop (ook op login-scherm).
     * &gt; 0 = wachten; 0 = idle, scripts mogen door.
     */
    public int check() {
        boolean relogAllowed = config.reLogoutEnabled();
        if (state == State.IDLE && BotRuntime.botEnabled && Game.isOnLoginScreen() && !Game.isLoggedIn()) {
            if (!relogAllowed) {
                RelogLog.logThrottled("Relog", "login-screen-off",
                        "check: login-scherm maar re-log uit — niet auto-inloggen (zet re-log aan of gebruik Login-test)");
                return 0;
            }
            state = State.LOGGING_IN;
            loginRetries = 0;
            pauseUntil = null;
            setStatus("🔐 Inloggen...");
            return 35 + random.nextInt(40);
        }
        if (!relogAllowed && state == State.IDLE) {
            updatePaintStatus(false);
            return 0;
        }
        if (!relogAllowed && state != State.IDLE) {
            RelogLog.logThrottled("Relog", "flow-off",
                    "check: re-log staat uit in config maar state=" + state + " — flow wordt nog afgemaakt");
        }

        switch (state) {
            case IDLE:
                int idleDelay = checkTimer();
                updatePaintStatus(true);
                return idleDelay;
            case WAIT_SAFE:
                int dSafe = handleWaitSafe();
                updatePaintStatus(true);
                return dSafe;
            case LOGGING_OUT:
                int d1 = handleLogout();
                updatePaintStatus(true);
                return d1;
            case WAITING_LOGOUT:
                int d2 = handleWaitLogout();
                updatePaintStatus(true);
                return d2;
            case PAUSED_ON_LOGIN:
                int d3 = handlePausedOnLogin();
                updatePaintStatus(true);
                return d3;
            case LOGGING_IN:
                int d4 = handleLogin();
                updatePaintStatus(true);
                return d4;
            default:
                updatePaintStatus(true);
                return 0;
        }
    }

    private int checkTimer() {
        if (sessionStart == null) {
            sessionStart = Instant.now();
            resetTimer();
            return 0;
        }

        syncTimerBoundsFromConfig();
        long elapsedMinutes = Duration.between(sessionStart, Instant.now()).toMinutes();
        if (elapsedMinutes >= nextRelogMinutes) {
            enterWaitSafe("timer " + nextRelogMinutes + " min");
            return 400 + random.nextInt(200);
        }

        return 0;
    }

    private void enterWaitSafe(String why) {
        state = State.WAIT_SAFE;
        waitSafeSinceMs = System.currentTimeMillis();
        BotRuntime.breakPending = true;
        loginRetries = 0;
        setStatus("⏸ Break: wacht veilig moment…");
        debug("→ WAIT_SAFE (" + why + ")");
    }

    private int handleWaitSafe() {
        if (!Game.isLoggedIn()) {
            BotRuntime.breakPending = false;
            state = State.WAITING_LOGOUT;
            waitLogoutSinceMs = System.currentTimeMillis();
            lastLogoutAttemptMs = System.currentTimeMillis();
            setStatus("🔁 Re-log: al uitgelogd");
            return 400;
        }
        long waited = waitSafeSinceMs > 0L
                ? System.currentTimeMillis() - waitSafeSinceMs
                : 0L;
        boolean force = waited >= RelogSafeGate.FORCE_TIMEOUT_MS;
        String reason = RelogSafeGate.blockReason();
        if (reason != null && !force) {
            RelogSafeGate.tryMakeSafe();
            long left = Math.max(0L, (RelogSafeGate.FORCE_TIMEOUT_MS - waited) / 1000L);
            setStatus("⏸ Break: wacht " + reason + " (" + RelogSafeGate.formatMmSs(left) + ")");
            RelogLog.logThrottled("Relog/safe", "wait", "wacht " + reason + " left=" + left + "s");
            return 450 + random.nextInt(250);
        }
        if (force && reason != null) {
            RelogLog.log("Relog/safe", "timeout 2 min — logout ondanks " + reason);
        }
        BotRuntime.breakPending = false;
        state = State.LOGGING_OUT;
        setStatus("🔁 Re-log: uitloggen...");
        debug("WAIT_SAFE klaar → LOGGING_OUT force=" + force + " reason=" + reason);
        return 280 + random.nextInt(180);
    }

    private int handleLogout() {
        debug("handleLogout: Game.logout()");
        logoutRetryWhileLoggedIn = 0;
        invokeGameLogout();
        lastLogoutAttemptMs = System.currentTimeMillis();
        waitLogoutSinceMs = System.currentTimeMillis();
        state = State.WAITING_LOGOUT;
        return MS_LOOP_PAUSE_AFTER_RELOG_LOGOUT;
    }

    private int handleWaitLogout() {
        if (Game.isLoggedIn()) {
            long now = System.currentTimeMillis();
            if (now - waitLogoutSinceMs >= ABORT_LOGOUT_STUCK_AFTER_MS) {
                abortRelogDueToLogoutLoop();
                return 3000;
            }
            if (now - lastLogoutAttemptMs >= MIN_MS_BETWEEN_RELOG_LOGOUT_RETRIES) {
                if (logoutRetryWhileLoggedIn >= MAX_LOGOUT_RETRY_WHILE_LOGGED_IN) {
                    abortRelogDueToLogoutLoop();
                    return 3000;
                }
                logoutRetryWhileLoggedIn++;
                debug("handleWaitLogout: nog ingelogd — opnieuw Game.logout() ("
                        + logoutRetryWhileLoggedIn + "/" + MAX_LOGOUT_RETRY_WHILE_LOGGED_IN + ")");
                invokeGameLogout();
                lastLogoutAttemptMs = now;
                setStatus("🔄 Re-log: opnieuw uitloggen… ("
                        + logoutRetryWhileLoggedIn + "/" + MAX_LOGOUT_RETRY_WHILE_LOGGED_IN + ")");
                return MS_LOOP_PAUSE_AFTER_RELOG_LOGOUT + random.nextInt(400);
            }
            return 850 + random.nextInt(300);
        }
        if (Game.isOnLoginScreen()) {
            int minPause = Math.max(0, config.reLogoutPauseMinMinutes());
            int maxPause = Math.max(minPause, config.reLogoutPauseMaxMinutes());
            if (maxPause <= 0) {
                debug("handleWaitLogout: geen pauze ingesteld → direct LOGGING_IN");
                state = State.LOGGING_IN;
                return 150 + random.nextInt(120);
            }
            if (maxPause < minPause) {
                maxPause = minPause;
            }
            int pauseMinutes = (maxPause == minPause)
                    ? minPause
                    : (minPause + random.nextInt(maxPause - minPause + 1));
            long pauseSeconds = Math.max(10, pauseMinutes * 60L);
            pauseUntil = Instant.now().plusSeconds(pauseSeconds);
            state = State.PAUSED_ON_LOGIN;
            setStatus("⏸ Pauze (re-log) ~" + pauseMinutes + " min");
            debug("handleWaitLogout: login screen → PAUSED_ON_LOGIN voor ~" + pauseMinutes + " min");
            return 1200;
        }
        return 650 + random.nextInt(200);
    }

    private int handlePausedOnLogin() {
        if (!Game.isOnLoginScreen()) {
            debug("handlePausedOnLogin: niet meer op login screen → LOGGING_IN");
            state = State.LOGGING_IN;
            return 1000;
        }
        if (pauseUntil == null) {
            debug("handlePausedOnLogin: pauseUntil null → LOGGING_IN");
            state = State.LOGGING_IN;
            return 1000;
        }
        if (Instant.now().isAfter(pauseUntil)) {
            state = State.LOGGING_IN;
            setStatus("🔁 Pauze voorbij — opnieuw inloggen");
            debug("handlePausedOnLogin: pauze voorbij → LOGGING_IN");
            return 1000;
        }
        return 3000;
    }

    public boolean tryLoginNow() {
        if (!Game.isOnLoginScreen()) {
            RelogLog.log("Relog/in", "tryLoginNow: niet op login-scherm");
            return false;
        }
        if (!ensureAccountLoaded()) {
            return false;
        }
        applyAccountToClient();
        debug("tryLoginNow: Game.setGameAccount + Play-knop");
        JagexLauncherPlayButton.clickPlayButton();
        setStatus("🔐 Log in geprobeerd");
        return true;
    }

    public void startTestAutoLoginFromLoginScreen() {
        if (!Game.isOnLoginScreen()) {
            setStatus("⚠ Test auto-login: open eerst het login-scherm");
            debug("startTestAutoLoginFromLoginScreen: geen login screen");
            return;
        }
        loginRetries = 0;
        pauseUntil = null;
        state = State.LOGGING_IN;
        setStatus("🔁 Test auto-login gestart (re-log pad)");
        debug("startTestAutoLoginFromLoginScreen: state=LOGGING_IN");
    }

    private int handleLogin() {
        if (!ensureAccountLoaded()) {
            debug("handleLogin: ensureAccountLoaded() faalde, re-log uit tot volgende timer");
            state = State.IDLE;
            resetTimer();
            return 0;
        }

        int world = WelcomeScreenPlayHelper.tickUntilInGameWorld();
        if (world == 0) {
            debug("handleLogin: in game world → re-log voltooid, timer reset, script hervat");
            state = State.IDLE;
            sessionStart = Instant.now();
            resetTimer();
            BotRuntime.breakPending = false;
            BotRuntime.relogFlowActive = false;
            setStatus("✓ Break klaar — script hervat");
            RelogLog.log("Relog", "in-world → scripts hervatten (botEnabled=" + BotRuntime.botEnabled + ")");
            return 1000;
        }

        if (world < 0) {
            if (loginRetries == 0) {
                applyAccountToClient();
                debug("handleLogin: eerste poging → Game.setGameAccount(...) aangeroepen");
            }
            if (JagexLauncherPlayButton.clickPlayButton()) {
                debug("handleLogin: Jagex Play Now aangeklikt");
            }
            loginRetries++;
            if (loginRetries > MAX_LOGIN_RETRIES) {
                setStatus("⚠ Re-log mislukt, probeer later opnieuw");
                debug("handleLogin: MAX_LOGIN_RETRIES bereikt → stop en reset timer");
                state = State.IDLE;
                resetTimer();
                return 4000;
            }
            setStatus("🔁 Re-log: Play Now…");
            return 260 + random.nextInt(140);
        }

        setStatus("🔁 Re-log: Welcome / Play…");
        debug("handleLogin: Welcome/CLICK HERE TO PLAY delay=" + world);
        return world;
    }

    private void resetTimer() {
        int min = Math.max(1, config.reLogoutMinMinutes());
        int max = Math.max(min + 1, config.reLogoutMaxMinutes());
        if (max <= min) {
            max = min + 1;
        }
        nextRelogMinutes = min + random.nextInt(max - min);
        sessionStart = Instant.now();
    }

    /** Slider-wijziging: houd next-break binnen min–max. */
    private void syncTimerBoundsFromConfig() {
        int min = Math.max(1, config.reLogoutMinMinutes());
        int max = Math.max(min, config.reLogoutMaxMinutes());
        if (nextRelogMinutes >= min && nextRelogMinutes <= max) {
            return;
        }
        int span = Math.max(1, max - min + 1);
        nextRelogMinutes = min + random.nextInt(span);
        debug("timer herzet door config → " + nextRelogMinutes + " min");
    }

    private void updatePaintStatus(boolean enabled) {
        if (!enabled) {
            BotRuntime.relogInfoEnabled = false;
            BotRuntime.relogSecondsUntilLogout = 0;
            BotRuntime.relogPauseSeconds = 0;
            BotRuntime.relogInPause = false;
            BotRuntime.relogPhase = "";
            BotRuntime.relogLauncherLabel = "";
            BotRuntime.breakPending = false;
            return;
        }
        if (!config.reLogoutEnabled() && state == State.IDLE) {
            BotRuntime.relogInfoEnabled = false;
            BotRuntime.relogSecondsUntilLogout = 0;
            BotRuntime.relogPauseSeconds = 0;
            BotRuntime.relogInPause = false;
            BotRuntime.relogPhase = "";
            BotRuntime.relogLauncherLabel = "";
            return;
        }
        Instant now = Instant.now();
        long secondsUntilLogout = 0;
        long pauseSeconds = 0;
        boolean inPause = false;
        String phase = "idle";
        String label = "";

        if (state == State.WAIT_SAFE) {
            phase = "wait";
            long left = waitSafeSinceMs > 0L
                    ? Math.max(0L, (RelogSafeGate.FORCE_TIMEOUT_MS
                    - (System.currentTimeMillis() - waitSafeSinceMs)) / 1000L)
                    : 0L;
            String why = RelogSafeGate.blockReason();
            label = "wacht " + (why != null ? why : "ok") + " " + RelogSafeGate.formatMmSs(left);
        } else if (state == State.LOGGING_OUT || state == State.WAITING_LOGOUT) {
            phase = "logout";
            label = "uitloggen…";
        } else if (state == State.PAUSED_ON_LOGIN && pauseUntil != null) {
            inPause = true;
            pauseSeconds = Math.max(0, Duration.between(now, pauseUntil).getSeconds());
            phase = "pause";
            label = "pauze " + RelogSafeGate.formatMmSs(pauseSeconds);
        } else if (state == State.LOGGING_IN) {
            phase = "login";
            label = "inloggen…";
        } else if (sessionStart != null && nextRelogMinutes > 0) {
            long elapsed = Duration.between(sessionStart, now).getSeconds();
            long total = nextRelogMinutes * 60L;
            secondsUntilLogout = Math.max(0, total - elapsed);
            label = "break " + RelogSafeGate.formatMmSs(secondsUntilLogout);
        }
        BotRuntime.relogInfoEnabled = true;
        BotRuntime.relogSecondsUntilLogout = secondsUntilLogout;
        BotRuntime.relogPauseSeconds = pauseSeconds;
        BotRuntime.relogInPause = inPause;
        BotRuntime.relogPhase = phase;
        BotRuntime.relogLauncherLabel = label;
    }

    private boolean ensureAccountLoaded() {
        String cfg = config.reLogoutAccount();
        if (cfg == null || cfg.trim().isEmpty()) {
            return loadDefaultJagexAccount();
        }

        cfg = cfg.trim();
        if (cfg.equals(lastAccountConfig) && gameAccount != null) {
            setLoginMode();
            return true;
        }
        RelogLog.logThrottled("Relog", "acc-cfg",
                "ensureAccountLoaded: config='" + redactAccount(cfg) + "'");

        lastAccountConfig = cfg;
        if (cfg.toLowerCase().startsWith("pasted:")) {
            String displayName = cfg.substring(7).trim();
            if (displayName.isEmpty()) {
                setStatus("⚠ Re-log: pasted: zonder display naam");
                debug("ensureAccountLoaded: pasted: maar displayName leeg");
                return false;
            }
            return bindManaged(ManagedAccountsStore.findByDisplayName(displayName), displayName);
        }
        if (cfg.toLowerCase().startsWith("jagex:")) {
            String path = cfg.substring(6).trim();
            JagexCredentialsHelper.ParsedJagexAccount acc = JagexCredentialsHelper.loadFromFile(new File(path));
            if (acc == null) {
                setStatus("⚠ Re-log: credentials niet gevonden (" + new File(path).getName() + ")");
                return false;
            }
            return bindParsed(acc);
        }
        String[] parts = cfg.split(":", 2);
        if (parts.length != 2) {
            setStatus("⚠ Re-log: account formaat ongeldig");
            return false;
        }
        String username = parts[0].trim();
        String password = parts[1].trim();
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("⚠ Re-log: lege username of password");
            return false;
        }
        gameAccount = new GameAccount(username, password);
        parsed = null;
        isJagexAccount = false;
        applyAccountToClient();
        return true;
    }

    private boolean loadDefaultJagexAccount() {
        String launched = System.getProperty("lonebot.account");
        String cacheKey = launched != null && !launched.trim().isEmpty()
                ? "launcher:" + launched.trim()
                : "default";
        if (cacheKey.equals(lastAccountConfig) && gameAccount != null) {
            setLoginMode();
            return true;
        }
        if (launched != null && !launched.trim().isEmpty()) {
            ManagedAccountsStore.ManagedAccount row = ManagedAccountsStore.findByDisplayName(launched.trim());
            if (row != null) {
                lastAccountConfig = cacheKey;
                RelogLog.logThrottled("Relog", "acc-launch",
                        "ensureAccountLoaded: launcher-account '" + launched.trim() + "'");
                return bindManaged(row, launched.trim());
            }
        }
        List<ManagedAccountsStore.ManagedAccount> rows = enabledAccounts();
        if (rows.size() == 1) {
            ManagedAccountsStore.ManagedAccount only = rows.get(0);
            lastAccountConfig = "store:" + only.displayName;
            RelogLog.logThrottled("Relog", "acc-store",
                    "ensureAccountLoaded: enige account '" + only.displayName + "'");
            return bindManaged(only, only.displayName);
        }
        if (rows.size() > 1) {
            setStatus("⚠ Re-log: meerdere accounts — vul reLogoutAccount in (pasted:Naam) of start via launcher");
            debug("ensureAccountLoaded: meerdere accounts, user moet kiezen");
            return false;
        }
        setStatus("⚠ Re-log: geen Jagex-account (launcher-account of Accounts-tab)");
        debug("ensureAccountLoaded: geen account gevonden");
        return false;
    }

    private static List<ManagedAccountsStore.ManagedAccount> enabledAccounts() {
        ManagedAccountsStore.ensureLoaded();
        List<ManagedAccountsStore.ManagedAccount> out = new ArrayList<>();
        for (ManagedAccountsStore.ManagedAccount a : ManagedAccountsStore.getAccounts()) {
            if (a == null) {
                continue;
            }
            if (a.rotationEnabled || a.enabled) {
                out.add(a);
            }
        }
        return out;
    }

    private boolean bindManaged(ManagedAccountsStore.ManagedAccount row, String name) {
        if (row == null) {
            setStatus("⚠ Re-log: " + name + " niet in Accounts");
            return false;
        }
        return bindParsed(JagexCredentialsHelper.fromManaged(row));
    }

    private boolean bindParsed(JagexCredentialsHelper.ParsedJagexAccount acc) {
        if (acc == null) {
            return false;
        }
        parsed = acc;
        gameAccount = acc.toGameAccount();
        isJagexAccount = true;
        applyAccountToClient();
        RelogLog.logThrottled("Relog", "acc-bind",
                "ensureAccountLoaded: Jagex-account '" + acc.displayName + "'");
        return true;
    }

    private void applyAccountToClient() {
        setLoginMode();
        if (gameAccount != null) {
            Game.setGameAccount(gameAccount);
        }
        if (parsed != null) {
            try {
                net.runelite.api.Client c = Static.getClient();
                if (c != null) {
                    JagexRuntimeLogin.applyInMemory(c, parsed);
                }
            } catch (Throwable t) {
                debug("applyAccountToClient: " + t.toString());
            }
        }
    }

    private void setLoginMode() {
        if (isJagexAccount) {
            Client.setOAuthLoginMode();
        } else {
            Client.setNormalLoginMode();
        }
    }

    private static String redactAccount(String cfg) {
        if (cfg == null) {
            return "";
        }
        int colon = cfg.indexOf(':');
        if (colon > 0 && !cfg.toLowerCase().startsWith("pasted:") && !cfg.toLowerCase().startsWith("jagex:")) {
            return cfg.substring(0, colon) + ":***";
        }
        return cfg;
    }
}
