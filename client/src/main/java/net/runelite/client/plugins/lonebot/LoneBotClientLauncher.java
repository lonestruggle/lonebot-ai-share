package net.runelite.client.plugins.lonebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Start een nieuwe LoneBot-client per account — sequentieel:
 * start → wacht tot client klaar → pas dán volgende account (creds + launch).
 */
public final class LoneBotClientLauncher {

    private static final Logger log = LoggerFactory.getLogger(LoneBotClientLauncher.class);
    private static final long READY_TIMEOUT_MS = 90_000L;
    private static final long POST_READY_SETTLE_MS = 1_500L;

    public static final class LaunchResult {
        public final int started;
        public final int failed;
        public final String message;

        public LaunchResult(int started, int failed, String message) {
            this.started = started;
            this.failed = failed;
            this.message = message;
        }
    }

    private LoneBotClientLauncher() {
    }

    public static File instancesRoot() {
        return new File(System.getProperty("user.home"), ".lonebot" + File.separator + "instances");
    }

    public static File profileDirFor(ManagedAccountsStore.ManagedAccount account) {
        String raw = "account";
        if (account != null) {
            if (account.profileKey != null && !account.profileKey.isBlank()) {
                raw = account.profileKey;
            } else if (account.displayName != null && !account.displayName.isBlank()) {
                raw = account.displayName;
            }
        }
        return new File(instancesRoot(), sanitize(raw));
    }

    public static File credentialsFileFor(ManagedAccountsStore.ManagedAccount account) {
        return new File(new File(profileDirFor(account), "jagex"), "credentials.properties");
    }

    public static LaunchResult launchAccounts(List<ManagedAccountsStore.ManagedAccount> accounts, long staggerMs) {
        if (accounts == null || accounts.isEmpty()) {
            return new LaunchResult(0, 0, "Geen accounts geselecteerd");
        }
        List<ManagedAccountsStore.ManagedAccount> snapshots = new ArrayList<>();
        Set<String> seenChar = new LinkedHashSet<>();
        Set<String> seenSess = new LinkedHashSet<>();
        for (ManagedAccountsStore.ManagedAccount raw : accounts) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ManagedAccountsStore.ManagedAccount a = raw.copy();
            String cid = a.characterId != null ? a.characterId.trim() : "";
            String sid = a.sessionId != null ? a.sessionId.trim() : "";
            if (!cid.isEmpty() && !seenChar.add(cid)) {
                log.warn("Dubbele characterId bij launch: {} ({})", cid, a.displayName);
            }
            if (!sid.isEmpty() && !seenSess.add(sid)) {
                log.warn("Dubbele sessionId bij launch: {}", a.displayName);
            }
            snapshots.add(a);
        }
        if (snapshots.isEmpty()) {
            return new LaunchResult(0, 0, "Geen geldige accounts");
        }

        int ok = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        long minGap = Math.max(500L, staggerMs);
        Set<Integer> batchAvoidWorlds = new LinkedHashSet<>();

        try {
            ScriptReloadHelper.stageAllScripts(msg -> log.info("[Scripts] {}", msg));
        } catch (Throwable t) {
            log.warn("Scripts stagen vóór launch: {}", t.toString());
        }

        for (int i = 0; i < snapshots.size(); i++) {
            ManagedAccountsStore.ManagedAccount a = snapshots.get(i);
            try {
                // Markeer placeholder — oude status telt niet als ready
                ClientRemoteBus.writeStatus(a.displayName, ClientRemoteBus.State.STARTING, 0);
                // Creds alleen voor DIT account (instance-pad), pas vlak vóór start
                writeInstanceCredentials(a);
                launchOne(a, batchAvoidWorlds);
                ok++;

                boolean ready = ClientRemoteBus.waitUntilReady(a.displayName, READY_TIMEOUT_MS);
                if (!ready) {
                    errors.add(a.displayName + ": timeout wachten op start");
                    log.warn("Timeout na launch {}", a.displayName);
                } else {
                    // Na boot: forceer live apply van déze account-creds
                    ClientRemoteBus.sendCommand(a.displayName, ClientRemoteBus.Command.APPLY);
                    TimeUnit.MILLISECONDS.sleep(POST_READY_SETTLE_MS);
                }
                // Extra gap vóór volgende account (creds wisselen)
                if (i + 1 < snapshots.size()) {
                    TimeUnit.MILLISECONDS.sleep(minGap);
                }
            } catch (Exception e) {
                fail++;
                String msg = (a != null ? a.displayName : "?") + ": " + e.getMessage();
                errors.add(msg);
                log.warn("Launch mislukt: {}", msg);
            }
        }
        String summary = ok + " client(s) gestart (sequentieel)"
                + (fail > 0 ? ", " + fail + " mislukt" : "")
                + (errors.isEmpty() ? "" : " — " + String.join("; ", errors));
        return new LaunchResult(ok, fail, summary);
    }

    /** Schrijf credentials alleen onder instance — nooit shared ~/.runelite. */
    public static void writeInstanceCredentials(ManagedAccountsStore.ManagedAccount account) throws Exception {
        if (account == null) {
            throw new IllegalArgumentException("leeg account");
        }
        File profile = profileDirFor(account);
        File jagexHome = new File(profile, "jagex");
        File loneDir = new File(profile, "config");
        if (!jagexHome.exists() && !jagexHome.mkdirs()) {
            throw new IllegalStateException("kan jagex-dir niet maken");
        }
        if (!loneDir.exists() && !loneDir.mkdirs()) {
            throw new IllegalStateException("kan config-dir niet maken");
        }
        JagexCredentialsHelper.ParsedJagexAccount parsed = JagexCredentialsHelper.fromManaged(account);
        // Primair: onder runelite.dir (bs) als credentials.properties — RL default path
        File cred = new File(loneDir, "credentials.properties");
        if (!JagexCredentialsHelper.writeToFile(cred, parsed)) {
            throw new IllegalStateException("credentials schrijven mislukt");
        }
        JagexCredentialsHelper.writeToFile(new File(jagexHome, "credentials.properties"), parsed);
        // Sequentiële launch: ook canonical ~/.runelite vlak vóór start (RL fallback)
        File shared = new File(System.getProperty("user.home") + File.separator + ".runelite",
                "credentials.properties");
        JagexCredentialsHelper.writeToFile(shared, parsed);
        log.info("Instance creds klaar voor {} → {}", account.displayName, cred.getAbsolutePath());
    }

    public static void launchOne(ManagedAccountsStore.ManagedAccount account) throws Exception {
        launchOne(account, null);
    }

    public static void launchOne(ManagedAccountsStore.ManagedAccount account, Set<Integer> batchAvoidWorlds)
            throws Exception {
        if (account == null || account.isEmpty()) {
            throw new IllegalArgumentException("leeg account");
        }
        if (account.sessionId == null || account.sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException(account.displayName + " heeft geen session id");
        }
        if (account.characterId == null || account.characterId.trim().isEmpty()) {
            throw new IllegalArgumentException(account.displayName + " heeft geen character id");
        }

        File profile = profileDirFor(account);
        File jagexHome = new File(profile, "jagex");
        File loneDir = new File(profile, "config");
        File cred = new File(loneDir, "credentials.properties");
        if (!cred.isFile()) {
            writeInstanceCredentials(account);
        }

        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            javaBin += ".exe";
        }
        if (!new File(javaBin).isFile()) {
            throw new IllegalStateException("java niet gevonden: " + javaBin);
        }

        String cp = System.getProperty("java.class.path");
        if (cp == null || cp.isEmpty()) {
            throw new IllegalStateException("geen classpath — start LoneBot via installDist/client.bat");
        }

        // Geen absolute credentials.path — RL leest runelite.dir/credentials.properties (default)
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        com.lonebot.launcher.RuneliteDeveloperMode.addJvmArgs(cmd);
        cmd.add("-Dlonebot.brand=LoneBot");
        cmd.add("-Dlonebot.role=client");
        cmd.add("-Drunelite.title=LoneBot - " + account.displayName);
        cmd.add("-Drunelite.dir=" + loneDir.getAbsolutePath());
        cmd.add("-Djagex.userhome=" + loneDir.getAbsolutePath());
        cmd.add("-Dlonebot.account=" + account.displayName);
        cmd.add("-Dlonebot.characterId=" + account.characterId.trim());
        java.util.List<Integer> worlds = account.preferredWorldIds();
        boolean hop = account.accountSwitchWorldHopEnabled && !worlds.isEmpty();
        if (hop) {
            int pick = InstanceDefaultWorldConfig.writeForLaunch(
                    loneDir, worlds, accountKey(account), batchAvoidWorlds);
            if (pick > 0 && batchAvoidWorlds != null) {
                batchAvoidWorlds.add(pick);
            }
            StringBuilder wb = new StringBuilder();
            for (int i = 0; i < worlds.size(); i++) {
                if (i > 0) {
                    wb.append(',');
                }
                wb.append(worlds.get(i));
            }
            cmd.add("-Dlonebot.worlds=" + wb);
            if (pick > 0) {
                cmd.add("-Dlonebot.world=" + pick);
            }
            cmd.add("-Dlonebot.worldHop=true");
            log.info("Launch wereld {} → w{} (pool={})", account.displayName, pick, wb);
        } else {
            InstanceDefaultWorldConfig.clearForLaunch(loneDir);
            // Expliciet: geen geërfde -Dlonebot.world van parent JVM
            cmd.add("-Dlonebot.worldHop=false");
            cmd.add("-Dlonebot.world=");
            cmd.add("-Dlonebot.worlds=");
            log.info("Launch wereld {} → geen voorkeur (Default World uit)", account.displayName);
        }
        String projectRoot = System.getProperty("lonebot.projectRoot");
        if (projectRoot != null && !projectRoot.isEmpty()) {
            cmd.add("-Dlonebot.projectRoot=" + projectRoot);
        }
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add("com.lonebot.client.LoneBotMain");
        com.lonebot.launcher.RuneliteDeveloperMode.addProgramArgs(cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(System.getProperty("user.dir", ".")));
        pb.redirectErrorStream(true);
        File logFile = new File(profile, "launch.log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        Process p = pb.start();
        ClientRemoteBus.registerProcess(account.displayName, p);
        log.info("Client gestart pid={} account={} char={} creds={} developerMode={}",
                p.pid(), account.displayName, account.characterId, cred.getAbsolutePath(),
                com.lonebot.launcher.RuneliteDeveloperMode.isEnabled());
    }

    private static String accountKey(ManagedAccountsStore.ManagedAccount account) {
        if (account == null) {
            return "";
        }
        String cid = account.characterId != null ? account.characterId.trim() : "";
        if (!cid.isEmpty()) {
            return cid;
        }
        return account.displayName != null ? account.displayName.trim() : "";
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "account";
        }
        String s = name.trim().replaceAll("[^a-zA-Z0-9._\\- ]", "_");
        s = s.replace(' ', '_');
        if (s.length() > 48) {
            s = s.substring(0, 48);
        }
        return s;
    }
}
