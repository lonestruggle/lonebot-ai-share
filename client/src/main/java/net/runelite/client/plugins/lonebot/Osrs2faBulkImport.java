package net.runelite.client.plugins.lonebot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Import van {@code osrs 2FA/accounts.json}:
 * {@code [{ "email", "pass", "secret", "last_code" }, ...]}.
 */
public final class Osrs2faBulkImport {

    private static final Logger log = LoggerFactory.getLogger(Osrs2faBulkImport.class);
    private static final Gson GSON = new Gson();

    private Osrs2faBulkImport() {
    }

    public static final class Result {
        public int added;
        public int updated;
        public int skipped;
        public String fatalError;

        public String summary() {
            if (fatalError != null && !fatalError.isEmpty()) {
                return fatalError;
            }
            return added + " nieuw, " + updated + " bijgewerkt"
                    + (skipped > 0 ? ", " + skipped + " overgeslagen" : "");
        }
    }

    public static Result importFile(File file, List<ManagedAccountsStore.ManagedAccount> into) {
        Result res = new Result();
        if (file == null || !file.isFile()) {
            res.fatalError = "⚠ Bestand niet gevonden";
            return res;
        }
        if (into == null) {
            res.fatalError = "⚠ Geen accountlijst";
            return res;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return importJson(json, into);
        } catch (Throwable t) {
            log.warn("2FA JSON lezen mislukt: {}", t.toString());
            res.fatalError = "⚠ Leesfout: " + t.getMessage();
            return res;
        }
    }

    public static Result importJson(String json, List<ManagedAccountsStore.ManagedAccount> into) {
        Result res = new Result();
        if (json == null || json.isBlank()) {
            res.fatalError = "⚠ Lege JSON";
            return res;
        }
        try {
            JsonElement root = new JsonParser().parse(json.trim());
            JsonArray arr;
            if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("accounts")) {
                arr = root.getAsJsonObject().getAsJsonArray("accounts");
            } else {
                res.fatalError = "⚠ Verwacht een JSON-array of {\"accounts\":[...]}";
                return res;
            }
            if (arr == null) {
                res.fatalError = "⚠ Geen accounts-array";
                return res;
            }
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    res.skipped++;
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String email = str(o, "email");
                String pass = str(o, "pass");
                if (pass.isEmpty()) {
                    pass = str(o, "password");
                }
                String secret = TotpHelper.normalizeSecret(str(o, "secret"));
                String lastCode = str(o, "last_code");
                if (lastCode.isEmpty()) {
                    lastCode = str(o, "lastCode");
                }
                String user = str(o, "user");
                if (email.isEmpty() && secret.isEmpty()) {
                    res.skipped++;
                    continue;
                }
                ManagedAccountsStore.ManagedAccount row = findMatch(into, email);
                boolean neu = row == null;
                if (neu) {
                    row = new ManagedAccountsStore.ManagedAccount();
                    row.rotationEnabled = true;
                    row.enabled = true;
                    into.add(row);
                }
                if (!email.isEmpty()) {
                    row.loginEmail = email;
                    if (row.displayName == null || row.displayName.isBlank()
                            || row.displayName.toLowerCase(Locale.ROOT).startsWith("account")
                            || "GEEN".equalsIgnoreCase(row.displayName)) {
                        row.displayName = !user.isEmpty() && !"GEEN".equalsIgnoreCase(user)
                                ? user
                                : email;
                    }
                }
                if (!pass.isEmpty()) {
                    row.loginPassword = pass;
                }
                if (!secret.isEmpty()) {
                    row.totpSecret = secret;
                }
                if (!lastCode.isEmpty()) {
                    row.lastMailCode = lastCode;
                }
                if (neu) {
                    res.added++;
                } else {
                    res.updated++;
                }
            }
            log.info("2FA import: {} added, {} updated, {} skipped", res.added, res.updated, res.skipped);
            return res;
        } catch (Throwable t) {
            log.warn("2FA JSON parse mislukt: {}", t.toString());
            res.fatalError = "⚠ Parsefout: " + t.getMessage();
            return res;
        }
    }

    private static ManagedAccountsStore.ManagedAccount findMatch(
            List<ManagedAccountsStore.ManagedAccount> into, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String want = email.trim().toLowerCase(Locale.ROOT);
        for (ManagedAccountsStore.ManagedAccount a : into) {
            if (a == null) {
                continue;
            }
            if (a.loginEmail != null && want.equals(a.loginEmail.trim().toLowerCase(Locale.ROOT))) {
                return a;
            }
            if (a.displayName != null && want.equals(a.displayName.trim().toLowerCase(Locale.ROOT))) {
                return a;
            }
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        if (o == null || key == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        try {
            return o.get(key).getAsString().trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
