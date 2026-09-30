package net.runelite.client.plugins.lonebot;

import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.bot.LoneBotPaths;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Private-GitHub update check + download. Melding + knop (niet geforceerd).
 * <p>
 * Manifest: {@code update/version.json} in de repo.
 * Asset: GitHub Release tag {@code vVERSION} met zip {@code assetName}.
 */
public final class LoneBotUpdater {

    private static final Logger log = LoggerFactory.getLogger(LoneBotUpdater.class);
    private static final Gson GSON = new Gson();
    private static final AtomicReference<CheckResult> LAST = new AtomicReference<>(CheckResult.idle());

    /** Default Updates-tab repo (owner/naam). */
    public static final String DEFAULT_REPO = "lonestruggle/lonebot";

    private LoneBotUpdater() {
    }

    public static CheckResult lastResult() {
        CheckResult r = LAST.get();
        return r != null ? r : CheckResult.idle();
    }

    public static CheckResult check(String repo, String token) {
        CheckResult r = doCheck(repo, token);
        LAST.set(r);
        return r;
    }

    private static CheckResult doCheck(String repo, String token) {
        String current = LoneBotBootstrapPlugin.VERSION;
        if (repo == null || repo.trim().isEmpty() || !repo.contains("/")) {
            return CheckResult.error(current, "Stel GitHub repo in (owner/naam)");
        }
        repo = repo.trim();
        token = token != null ? token.trim() : "";
        try {
            VersionManifest man = fetchManifest(repo, token);
            if (man == null || man.version == null || man.version.trim().isEmpty()) {
                return CheckResult.error(current, "version.json leeg / ongeldig");
            }
            String remote = man.version.trim();
            int cmp = compareVersions(remote, current);
            if (cmp <= 0) {
                return CheckResult.upToDate(current, remote, man.notes);
            }
            String asset = man.assetName != null && !man.assetName.isEmpty()
                    ? man.assetName : "lonebot-client.zip";
            return CheckResult.updateAvailable(current, remote, man.notes, asset);
        } catch (Exception e) {
            log.warn("[Updater] check failed: {}", e.toString());
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (msg.contains("HTTP 404") || msg.contains("HTTP 401") || msg.contains("HTTP 403")) {
                msg = "Repo privé of version.json ontbreekt — vul GitHub token (Contents: Read) in, of check of update/version.json op main staat";
            }
            return CheckResult.error(current, msg);
        }
    }

    /**
     * Download release zip → update-pending → write apply-update.bat → restart.
     * @return status message for UI
     */
    public static String installAndRestart(String repo, String token, CheckResult pending) {
        if (pending == null || !pending.updateAvailable) {
            return "Geen update klaar — eerst Controleer updates";
        }
        if (repo == null || repo.trim().isEmpty()) {
            return "Repo ontbreekt";
        }
        if (token == null) {
            token = "";
        }
        try {
            Path installRoot = resolveInstallRoot();
            if (installRoot == null) {
                return "Installatiemap niet gevonden — sluit de launcher en start vanuit runtime\\bin\\client.bat";
            }
            Path pendingDir = installRoot.resolve("update-pending");
            Files.createDirectories(pendingDir);
            Path zipPath = pendingDir.resolve("lonebot-update.zip");

            long assetId = findReleaseAssetId(repo.trim(), token.trim(), pending.remoteVersion, pending.assetName);
            if (assetId <= 0) {
                return "Release-asset niet gevonden — tag v" + pending.remoteVersion
                        + " met " + pending.assetName + "?";
            }
            downloadAsset(repo.trim(), token.trim(), assetId, zipPath);

            Path extractDir = pendingDir.resolve("extracted");
            if (Files.isDirectory(extractDir)) {
                deleteRecursive(extractDir);
            }
            Files.createDirectories(extractDir);
            unzip(zipPath, extractDir);

            // Zip mag root-map "client" bevatten of direct bin/lib
            Path payload = findPayloadRoot(extractDir);
            if (payload == null) {
                return "Zip ongeldig — verwacht bin/ + lib/";
            }

            stageAllScriptJarsFromLib(payload.resolve("lib"));

            Path applyBat = pendingDir.resolve("apply-update.bat");
            writeApplyScript(applyBat, installRoot, payload);
            log.info("[Updater] update klaar → herstart via {}", applyBat);

            new ProcessBuilder("cmd.exe", "/c", "start", "\"LoneBot Update\"", applyBat.toAbsolutePath().toString())
                    .directory(pendingDir.toFile())
                    .start();
            Thread.sleep(600);
            System.exit(0);
            return "Herstarten…";
        } catch (Exception e) {
            log.error("[Updater] install failed", e);
            return "Update mislukt: " + e.getMessage();
        }
    }

    private static VersionManifest fetchManifest(String repo, String token) throws Exception {
        // Contents API → download_url of base64; gebruik raw via API media type
        String api = "https://api.github.com/repos/" + repo + "/contents/update/version.json?ref=main";
        String body = httpGet(api, token, "application/vnd.github.raw+json");
        return GSON.fromJson(body, VersionManifest.class);
    }

    private static long findReleaseAssetId(String repo, String token, String version, String assetName)
            throws Exception {
        String tag = version.startsWith("v") ? version : "v" + version;
        String api = "https://api.github.com/repos/" + repo + "/releases/tags/" + tag;
        String body = httpGet(api, token, "application/vnd.github+json");
        JsonObject rel = GSON.fromJson(body, JsonObject.class);
        if (rel == null || !rel.has("assets")) {
            return -1;
        }
        JsonArray assets = rel.getAsJsonArray("assets");
        String want = assetName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < assets.size(); i++) {
            JsonObject a = assets.get(i).getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            if (name.toLowerCase(Locale.ROOT).equals(want)) {
                return a.get("id").getAsLong();
            }
        }
        // fallback: first zip
        for (int i = 0; i < assets.size(); i++) {
            JsonObject a = assets.get(i).getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                return a.get("id").getAsLong();
            }
        }
        return -1;
    }

    private static void downloadAsset(String repo, String token, long assetId, Path dest) throws Exception {
        String api = "https://api.github.com/repos/" + repo + "/releases/assets/" + assetId;
        HttpURLConnection conn = open(api, token, "application/octet-stream");
        int code = conn.getResponseCode();
        // GitHub may 302 to S3 — follow manually if needed
        if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM
                || code == 307 || code == 308) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(loc).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            code = conn.getResponseCode();
        }
        if (code != 200) {
            throw new IllegalStateException("Download HTTP " + code);
        }
        Files.createDirectories(dest.getParent());
        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }

    private static String httpGet(String url, String token, String accept) throws Exception {
        HttpURLConnection conn = open(url, token, accept);
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("GitHub HTTP " + code + ": " + truncate(body, 200));
        }
        return body;
    }

    private static HttpURLConnection open(String url, String token, String accept) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", accept);
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        conn.setRequestProperty("User-Agent", "LoneBot-Updater");
        return conn;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static void unzip(Path zip, Path dest) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IllegalStateException("Zip-slip: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static Path findPayloadRoot(Path extractDir) throws Exception {
        if (Files.isDirectory(extractDir.resolve("bin")) && Files.isDirectory(extractDir.resolve("lib"))) {
            return extractDir;
        }
        try (Stream<Path> stream = Files.list(extractDir)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> Files.isDirectory(p.resolve("bin")) && Files.isDirectory(p.resolve("lib")))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Imp + WC + Fish + Imps2 + Star + example uit {@code lib/} → {@code ~/.lonebot/scripts}.
     * Zonder dit blijft de andere PC op oude staged jars na een GitHub-client-update.
     */
    private static void stageAllScriptJarsFromLib(Path libDir) {
        if (libDir == null || !Files.isDirectory(libDir)) {
            return;
        }
        LoneBotPaths.ensureDirs();
        int n = 0;
        for (String name : LoneBotPaths.STAGED_SCRIPT_JARS) {
            Path src = libDir.resolve(name);
            if (!Files.isRegularFile(src)) {
                continue;
            }
            Path dest = LoneBotPaths.stagedScriptJar(name).toPath();
            try {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                n++;
            } catch (Exception e) {
                String nextName = name.endsWith(".jar")
                        ? name.substring(0, name.length() - 4) + ".next.jar"
                        : name + ".next";
                Path next = LoneBotPaths.scriptsDir().toPath().resolve(nextName);
                try {
                    Files.copy(src, next, StandardCopyOption.REPLACE_EXISTING);
                    n++;
                    log.warn("[Updater] {} locked → {}", name, next.getFileName());
                } catch (Exception e2) {
                    log.warn("[Updater] script-jar {} niet gekopieerd: {}", name, e2.toString());
                }
            }
        }
        log.info("[Updater] {} script-jar(s) → ~/.lonebot/scripts", n);
        BotRuntime.logConsole("[Updater] alle scripts mee (" + n + " jars) → ~/.lonebot/scripts");
    }

    private static void writeApplyScript(Path bat, Path installRoot, Path payload) throws Exception {
        // Copy payload over install root after short delay (JVM exited)
        String src = payload.toAbsolutePath().toString();
        String dst = installRoot.toAbsolutePath().toString();
        String clientBat = installRoot.resolve("bin").resolve("client.bat").toAbsolutePath().toString();
        StringBuilder jars = new StringBuilder();
        for (String name : LoneBotPaths.STAGED_SCRIPT_JARS) {
            if (jars.length() > 0) {
                jars.append(' ');
            }
            jars.append(name);
        }
        String script = "@echo off\r\n"
                + "echo LoneBot update toepassen...\r\n"
                + "timeout /t 2 /nobreak >nul\r\n"
                + "xcopy /E /Y /I /Q \"" + src + "\\*\" \"" + dst + "\\\"\r\n"
                + "if errorlevel 1 (\r\n"
                + "  echo Update kopieren mislukt\r\n"
                + "  pause\r\n"
                + "  exit /b 1\r\n"
                + ")\r\n"
                + "echo Script-jars (Imp WC Fish Imps2 Star example) naar %%USERPROFILE%%\\.lonebot\\scripts\r\n"
                + "mkdir \"%USERPROFILE%\\.lonebot\\scripts\" >nul 2>&1\r\n"
                + "for %%J in (" + jars + ") do (\r\n"
                + "  if exist \"" + dst + "\\lib\\%%J\" (\r\n"
                + "    copy /Y \"" + dst + "\\lib\\%%J\" \"%USERPROFILE%\\.lonebot\\scripts\\%%J\" >nul\r\n"
                + "    if errorlevel 1 copy /Y \"" + dst + "\\lib\\%%J\" \"%USERPROFILE%\\.lonebot\\scripts\\%%~nJ.next.jar\" >nul\r\n"
                + "  )\r\n"
                + ")\r\n"
                + "echo Klaar — herstart client\r\n"
                + "start \"LoneBot\" /D \"" + dst + "\\bin\" \"" + clientBat + "\"\r\n"
                + "exit\r\n";
        Files.write(bat, script.getBytes(StandardCharsets.UTF_8));
    }

    private static Path resolveInstallRoot() {
        Path detected = com.lonebot.client.LoneBotInstallRoot.detect();
        if (detected != null) {
            return detected;
        }
        FileBat bat = resolveClientBatFile();
        if (bat != null && bat.path.getParent() != null) {
            // .../bin/client.bat → install-root
            return bat.path.getParent().getParent();
        }
        return null;
    }

    private static FileBat resolveClientBatFile() {
        String[] candidates = {
                System.getProperty("lonebot.client.bat"),
                "client\\build\\install\\client\\bin\\client.bat",
                "bin\\client.bat"
        };
        for (String c : candidates) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            Path p = Paths.get(c);
            if (!p.isAbsolute()) {
                p = Paths.get("").toAbsolutePath().resolve(c).normalize();
            }
            if (Files.isRegularFile(p)) {
                return new FileBat(p);
            }
        }
        return null;
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    /** Semver-ish: 0.3.10 > 0.3.9 */
    static int compareVersions(String a, String b) {
        int[] pa = parseVer(a);
        int[] pb = parseVer(b);
        for (int i = 0; i < 3; i++) {
            if (pa[i] != pb[i]) {
                return Integer.compare(pa[i], pb[i]);
            }
        }
        return 0;
    }

    private static int[] parseVer(String v) {
        String s = v == null ? "0" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        String[] parts = s.split("[^0-9]+");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                try {
                    out[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        s = s.replace('\n', ' ').trim();
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static final class FileBat {
        final Path path;

        FileBat(Path path) {
            this.path = path;
        }
    }

    public static final class VersionManifest {
        @SerializedName("version")
        public String version;
        @SerializedName("notes")
        public String notes;
        @SerializedName("assetName")
        public String assetName;
    }

    public static final class CheckResult {
        public final String localVersion;
        public final String remoteVersion;
        public final String notes;
        public final String assetName;
        public final boolean updateAvailable;
        public final boolean error;
        public final String message;

        private CheckResult(String local, String remote, String notes, String asset,
                            boolean update, boolean error, String message) {
            this.localVersion = local;
            this.remoteVersion = remote;
            this.notes = notes;
            this.assetName = asset;
            this.updateAvailable = update;
            this.error = error;
            this.message = message;
        }

        static CheckResult idle() {
            return new CheckResult(LoneBotBootstrapPlugin.VERSION, "", "", "", false, false, "Nog niet gecontroleerd");
        }

        static CheckResult upToDate(String local, String remote, String notes) {
            return new CheckResult(local, remote, notes, "", false, false,
                    "Up-to-date (v" + local + ")");
        }

        static CheckResult updateAvailable(String local, String remote, String notes, String asset) {
            String msg = "Update beschikbaar: v" + local + " → v" + remote;
            if (notes != null && !notes.isEmpty()) {
                msg += " — " + notes;
            }
            return new CheckResult(local, remote, notes, asset, true, false, msg);
        }

        static CheckResult error(String local, String err) {
            return new CheckResult(local, "", "", "", false, true, "Update-check: " + err);
        }
    }
}
