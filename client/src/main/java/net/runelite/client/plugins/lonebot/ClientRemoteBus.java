package net.runelite.client.plugins.lonebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * File-IPC tussen launcher en sub-clients (per instance-map).
 * <ul>
 *   <li>{@code remote.cmd} — one-shot: START / PAUSE / STOP / CLOSE / APPLY / RELOAD_SCRIPTS / UNLOAD_SCRIPTS</li>
 *   <li>{@code remote.status} — client schrijft RUNNING / PAUSED / STOPPED + pid</li>
 * </ul>
 */
public final class ClientRemoteBus {

    private static final Logger log = LoggerFactory.getLogger(ClientRemoteBus.class);
    private static final AtomicLong SEQ = new AtomicLong();
    private static final ConcurrentHashMap<String, Process> LIVE = new ConcurrentHashMap<>();

    public enum Command {
        START, PAUSE, STOP, CLOSE, APPLY, RELOAD_SCRIPTS, UNLOAD_SCRIPTS, BREAK_NOW, CANCEL_BREAK
    }

    public enum State {
        UNKNOWN, STARTING, READY, RUNNING, PAUSED, STOPPED, DEAD
    }

    public static final class Status {
        public final State state;
        public final long pid;
        public final long updatedEpochMs;
        public final boolean processAlive;
        public final String breakLabel;

        public Status(State state, long pid, long updatedEpochMs, boolean processAlive) {
            this(state, pid, updatedEpochMs, processAlive, "");
        }

        public Status(State state, long pid, long updatedEpochMs, boolean processAlive, String breakLabel) {
            this.state = state;
            this.pid = pid;
            this.updatedEpochMs = updatedEpochMs;
            this.processAlive = processAlive;
            this.breakLabel = breakLabel != null ? breakLabel : "";
        }

        public String shortLabel() {
            if (!processAlive && state != State.STARTING) {
                return "—";
            }
            switch (state) {
                case RUNNING:
                    return "▶";
                case PAUSED:
                    return "⏸";
                case STARTING:
                    return "…";
                case READY:
                    return "✓";
                case STOPPED:
                    return "■";
                default:
                    return processAlive ? "?" : "—";
            }
        }
    }

    private ClientRemoteBus() {
    }

    public static File cmdFile(ManagedAccountsStore.ManagedAccount account) {
        return new File(LoneBotClientLauncher.profileDirFor(account), "remote.cmd");
    }

    public static File statusFile(ManagedAccountsStore.ManagedAccount account) {
        return new File(LoneBotClientLauncher.profileDirFor(account), "remote.status");
    }

    public static File cmdFileForDisplayName(String displayName) {
        ManagedAccountsStore.ManagedAccount found = ManagedAccountsStore.findByDisplayName(displayName);
        if (found != null) {
            return cmdFile(found);
        }
        ManagedAccountsStore.ManagedAccount tmp = new ManagedAccountsStore.ManagedAccount();
        tmp.displayName = displayName;
        return cmdFile(tmp);
    }

    public static File statusFileForDisplayName(String displayName) {
        ManagedAccountsStore.ManagedAccount found = ManagedAccountsStore.findByDisplayName(displayName);
        if (found != null) {
            return statusFile(found);
        }
        ManagedAccountsStore.ManagedAccount tmp = new ManagedAccountsStore.ManagedAccount();
        tmp.displayName = displayName;
        return statusFile(tmp);
    }

    public static void registerProcess(String displayName, Process process) {
        if (displayName == null || process == null) {
            return;
        }
        String key = key(displayName);
        Process old = LIVE.put(key, process);
        if (old != null && old.isAlive() && old != process) {
            try {
                old.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
        writeStatus(displayName, State.STARTING, process.pid());
    }

    public static void rekeyProcess(String oldDisplayName, String newDisplayName) {
        if (oldDisplayName == null || newDisplayName == null) {
            return;
        }
        String oldK = key(oldDisplayName);
        String newK = key(newDisplayName);
        if (oldK.equals(newK)) {
            return;
        }
        Process p = LIVE.remove(oldK);
        if (p != null && p.isAlive()) {
            LIVE.put(newK, p);
            Status prev = readStatus(oldDisplayName);
            State st = prev.state != State.UNKNOWN ? prev.state : State.READY;
            writeStatus(newDisplayName, st, p.pid());
        } else if (p != null) {
            LIVE.put(newK, p);
        }
    }

    public static Process getProcess(String displayName) {
        if (displayName == null) {
            return null;
        }
        Process p = LIVE.get(key(displayName));
        if (p != null && !p.isAlive()) {
            LIVE.remove(key(displayName), p);
            return null;
        }
        return p;
    }

    public static boolean isClientAlive(String displayName) {
        Process p = getProcess(displayName);
        if (p != null && p.isAlive()) {
            return true;
        }
        // Na launcher-herstart is de Process-handle weg; OS-pid in remote.status telt.
        Status s = readStatus(displayName);
        if (s.pid > 0 && s.state != State.DEAD && s.state != State.UNKNOWN && isOsProcessAlive(s.pid)) {
            return true;
        }
        return false;
    }

    /**
     * Alleen écht lopende clients (OS-pid of launcher Process-handle).
     * Negeert stale READY/RUNNING uit vorige sessie.
     */
    public static Set<String> listLiveDisplayNames() {
        Set<String> live = new LinkedHashSet<>();
        ManagedAccountsStore.ensureLoaded();
        for (ManagedAccountsStore.ManagedAccount a : ManagedAccountsStore.getAccounts()) {
            if (a == null || a.displayName == null || a.displayName.trim().isEmpty()) {
                continue;
            }
            String name = a.displayName.trim();
            if (isClientAlive(name)) {
                live.add(name);
            }
        }
        return live;
    }

    /** Markeer status DEAD als pid dood is — voorkomt oude tabs na herstart. */
    public static void purgeStaleStatuses() {
        ManagedAccountsStore.ensureLoaded();
        for (ManagedAccountsStore.ManagedAccount a : ManagedAccountsStore.getAccounts()) {
            if (a == null || a.displayName == null || a.displayName.trim().isEmpty()) {
                continue;
            }
            String name = a.displayName.trim();
            Status s = readStatus(name);
            if (s.state == State.DEAD || s.state == State.UNKNOWN) {
                continue;
            }
            if (getProcess(name) != null) {
                continue;
            }
            if (s.pid > 0 && isOsProcessAlive(s.pid)) {
                continue;
            }
            if (s.pid <= 0 || !isOsProcessAlive(s.pid)) {
                writeStatus(name, State.DEAD, 0);
            }
        }
    }

    public static boolean sendCommand(String displayName, Command cmd) {
        if (displayName == null || cmd == null) {
            return false;
        }
        try {
            File f = cmdFileForDisplayName(displayName);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String line = cmd.name() + " " + SEQ.incrementAndGet() + " " + System.currentTimeMillis();
            Files.write(f.toPath(), line.getBytes(StandardCharsets.UTF_8));
            log.info("Remote cmd → {} : {}", displayName, cmd);
            return true;
        } catch (Exception e) {
            log.warn("Remote cmd schrijven mislukt: {}", e.toString());
            return false;
        }
    }

    /** Launcher: sluit client-proces (+ CLOSE-cmd zodat client netjes kan afsluiten). */
    public static boolean closeClient(String displayName) {
        sendCommand(displayName, Command.CLOSE);
        Process p = getProcess(displayName);
        boolean killed = false;
        if (p != null) {
            try {
                p.destroy();
                if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
                killed = true;
            } catch (Exception e) {
                try {
                    p.destroyForcibly();
                    killed = true;
                } catch (Exception ignored) {
                }
            }
            LIVE.remove(key(displayName));
        } else {
            Status s = readStatus(displayName);
            if (s.pid > 0) {
                killed = killOsPid(s.pid);
            }
        }
        writeStatus(displayName, State.DEAD, 0);
        return killed;
    }

    public static Status readStatus(String displayName) {
        File f = statusFileForDisplayName(displayName);
        State state = State.UNKNOWN;
        long pid = 0;
        long ts = 0;
        String breakLabel = "";
        if (f.isFile()) {
            try {
                String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                for (String line : raw.split("\\R")) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String k = line.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                    String v = line.substring(eq + 1).trim();
                    if ("STATE".equals(k)) {
                        try {
                            state = State.valueOf(v.toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {
                        }
                    } else if ("PID".equals(k)) {
                        try {
                            pid = Long.parseLong(v);
                        } catch (Exception ignored) {
                        }
                    } else if ("TS".equals(k)) {
                        try {
                            ts = Long.parseLong(v);
                        } catch (Exception ignored) {
                        }
                    } else if ("BREAK".equals(k)) {
                        breakLabel = v;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        boolean alive = getProcess(displayName) != null
                || (pid > 0 && isOsProcessAlive(pid) && state != State.DEAD);
        return new Status(state, pid, ts, alive, breakLabel);
    }

    public static void writeStatus(String displayName, State state, long pid) {
        if (displayName == null || state == null) {
            return;
        }
        try {
            File f = statusFileForDisplayName(displayName);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String label = "";
            try {
                if (net.storm.sdk.bot.BotRuntime.relogLauncherLabel != null) {
                    label = net.storm.sdk.bot.BotRuntime.relogLauncherLabel.trim();
                }
            } catch (Throwable ignored) {
            }
            StringBuilder body = new StringBuilder();
            body.append("STATE=").append(state.name()).append('\n');
            body.append("PID=").append(pid).append('\n');
            body.append("TS=").append(System.currentTimeMillis()).append('\n');
            if (!label.isEmpty()) {
                body.append("BREAK=").append(label.replace('\n', ' ')).append('\n');
            }
            Files.write(f.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("status schrijven: {}", e.toString());
        }
    }

    /**
     * Client-side: lees + consumeer remote.cmd. Return null als niets nieuws.
     */
    public static Command pollCommand(String displayName) {
        File f = cmdFileForDisplayName(displayName);
        if (!f.isFile()) {
            return null;
        }
        try {
            String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            // consumeer meteen
            try {
                Files.deleteIfExists(f.toPath());
            } catch (Exception ignored) {
            }
            if (raw.isEmpty()) {
                return null;
            }
            String first = raw.split("\\s+")[0].trim().toUpperCase(Locale.ROOT);
            return Command.valueOf(first);
        } catch (Exception e) {
            return null;
        }
    }

    /** Client: publiceer huidige bot-state. */
    public static void publishLocalBotStatus(String displayName) {
        State st;
        if (net.storm.sdk.bot.BotRuntime.botEnabled) {
            st = State.RUNNING;
        } else if (net.storm.sdk.bot.BotRuntime.pausedForResume) {
            st = State.PAUSED;
        } else {
            st = State.STOPPED;
        }
        long pid = ProcessHandle.current().pid();
        writeStatus(displayName, st, pid);
    }

    /** Client net opgestart (plugin startUp) — launcher mag volgende account voorbereiden. */
    public static void publishReady(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return;
        }
        writeStatus(displayName.trim(), State.READY, ProcessHandle.current().pid());
    }

    /**
     * Wacht tot sub-client remote.status publiceert (plugin gestart).
     */
    public static boolean waitUntilReady(String displayName, long timeoutMs) {
        if (displayName == null) {
            return false;
        }
        long deadline = System.currentTimeMillis() + Math.max(5_000L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            Process p = getProcess(displayName);
            if (p != null && !p.isAlive()) {
                log.warn("Process dood tijdens wait: {}", displayName);
                return false;
            }
            Status s = readStatus(displayName);
            boolean up = (p != null && p.isAlive()) || s.processAlive;
            if (up && s.pid > 0 && (s.state == State.READY || s.state == State.STOPPED
                    || s.state == State.RUNNING || s.state == State.PAUSED)) {
                log.info("Client ready: {} state={} pid={}", displayName, s.state, s.pid);
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(400L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("Timeout wachten op client: {}", displayName);
        return false;
    }

    private static String key(String displayName) {
        return displayName.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isOsProcessAlive(long pid) {
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean killOsPid(long pid) {
        try {
            return ProcessHandle.of(pid).map(ph -> {
                ph.destroy();
                return true;
            }).orElse(false);
        } catch (Throwable t) {
            return false;
        }
    }
}
