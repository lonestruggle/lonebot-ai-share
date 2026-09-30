package net.runelite.client.plugins.lonebot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bulk-import van Storm {@code storm-accounts.json} (accounts-array).
 */
public final class StormAccountsBulkImport {

    public static final class Result {
        public int added;
        public int skippedDuplicate;
        public int skippedInvalid;
        public int skippedNonJagex;
        public String fatalError;

        public String summary() {
            if (fatalError != null) {
                return fatalError;
            }
            return added + " toegevoegd, " + skippedDuplicate + " bestond al, "
                    + skippedInvalid + " ongeldig"
                    + (skippedNonJagex > 0 ? ", " + skippedNonJagex + " niet-jagex" : "");
        }
    }

    private StormAccountsBulkImport() {
    }

    public static Result importInto(List<ManagedAccountsStore.ManagedAccount> existing, String json) {
        Result out = new Result();
        if (json == null || json.trim().isEmpty()) {
            out.fatalError = "Lege JSON";
            return out;
        }
        json = stripBom(json.trim());

        Set<String> seenCid = new HashSet<>();
        Set<String> seenSid = new HashSet<>();
        Set<String> seenDisp = new HashSet<>();
        if (existing != null) {
            for (ManagedAccountsStore.ManagedAccount r : existing) {
                if (r == null) {
                    continue;
                }
                addNorm(seenCid, r.characterId);
                addNorm(seenSid, r.sessionId);
                addNorm(seenDisp, normalizeDisplay(r.displayName));
            }
        }

        JsonElement rootEl;
        try {
            rootEl = new Gson().fromJson(json, JsonElement.class);
        } catch (Exception e) {
            out.fatalError = "Ongeldige JSON: " + e.getMessage();
            return out;
        }
        JsonArray accounts;
        if (rootEl == null) {
            out.fatalError = "Lege JSON";
            return out;
        } else if (rootEl.isJsonArray()) {
            accounts = rootEl.getAsJsonArray();
        } else if (rootEl.isJsonObject()) {
            JsonObject root = rootEl.getAsJsonObject();
            accounts = root.getAsJsonArray("accounts");
            if (accounts == null) {
                accounts = root.getAsJsonArray("Accounts");
            }
        } else {
            out.fatalError = "JSON moet \"accounts\"-array bevatten";
            return out;
        }
        if (accounts == null || accounts.size() == 0) {
            out.fatalError = "Geen accounts in bestand";
            return out;
        }

        List<ManagedAccountsStore.ManagedAccount> toAdd = new ArrayList<>();
        for (JsonElement el : accounts) {
            if (el == null || !el.isJsonObject()) {
                out.skippedInvalid++;
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String type = str(o, "type");
            if (type != null && !type.trim().isEmpty() && !"jagex".equalsIgnoreCase(type.trim())) {
                out.skippedNonJagex++;
                continue;
            }

            String characterId = firstNonBlank(str(o, "character_id"), str(o, "characterId"), str(o, "JX_CHARACTER_ID"));
            String sessionId = extractSessionToken(o);
            String rawDisplay = firstNonBlank(str(o, "display_name"), str(o, "displayName"), str(o, "username"));
            if (sessionId == null || sessionId.isEmpty()) {
                out.skippedInvalid++;
                continue;
            }

            String cidKey = normKey(characterId);
            String sidKey = normKey(sessionId);
            if ((!cidKey.isEmpty() && seenCid.contains(cidKey)) || seenSid.contains(sidKey)) {
                out.skippedDuplicate++;
                continue;
            }

            String display = resolveDisplayName(rawDisplay, characterId, sessionId);
            String dispNorm = normalizeDisplay(display);
            if (!dispNorm.isEmpty() && seenDisp.contains(dispNorm)) {
                out.skippedDuplicate++;
                continue;
            }

            ManagedAccountsStore.ManagedAccount row = new ManagedAccountsStore.ManagedAccount();
            row.displayName = display;
            row.characterId = characterId != null ? characterId : "";
            row.sessionId = sessionId;
            row.enabled = true;
            row.rotationEnabled = true;
            String notes = str(o, "notes");
            row.notes = notes != null && !notes.isEmpty() ? notes : "Storm import";

            toAdd.add(row);
            if (!cidKey.isEmpty()) {
                seenCid.add(cidKey);
            }
            seenSid.add(sidKey);
            if (!dispNorm.isEmpty()) {
                seenDisp.add(dispNorm);
            }
            out.added++;
        }

        if (!toAdd.isEmpty()) {
            ManagedAccountsStore.addAll(toAdd);
        }
        return out;
    }

    public static Result importFile(java.io.File file) {
        Result out = new Result();
        if (file == null || !file.isFile()) {
            out.fatalError = "Bestand niet gevonden";
            return out;
        }
        try {
            String json = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            ManagedAccountsStore.ensureLoaded();
            return importInto(new ArrayList<>(ManagedAccountsStore.getAccounts()), json);
        } catch (Exception e) {
            out.fatalError = "Lezen mislukt: " + e.getMessage();
            return out;
        }
    }

    private static void addNorm(Set<String> set, String s) {
        String k = normKey(s);
        if (!k.isEmpty()) {
            set.add(k);
        }
    }

    private static String normKey(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplay(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace(' ', ' ');
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return null;
        }
        for (String a : parts) {
            if (a != null && !a.trim().isEmpty()) {
                return a.trim();
            }
        }
        return null;
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static String extractSessionToken(JsonObject o) {
        String flat = firstNonBlank(
                str(o, "session_id"), str(o, "sessionId"), str(o, "JX_SESSION_ID"),
                str(o, "JX_ACCESS_TOKEN"), str(o, "access_token"), str(o, "accessToken"),
                str(o, "storm_session"), str(o, "session"));
        if (flat != null) {
            return flat;
        }
        if (o.has("credentials") && o.get("credentials").isJsonObject()) {
            JsonObject c = o.getAsJsonObject("credentials");
            return firstNonBlank(str(c, "session_id"), str(c, "sessionId"), str(c, "JX_SESSION_ID"),
                    str(c, "JX_ACCESS_TOKEN"), str(c, "access_token"));
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        return null;
    }

    private static boolean isGenericDisplayLabel(String s) {
        if (s == null || s.trim().isEmpty()) {
            return true;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        return "unknown".equals(t) || "onbekend".equals(t) || "?".equals(t) || "-".equals(t);
    }

    private static String resolveDisplayName(String rawDisplay, String characterId, String sessionId) {
        if (!isGenericDisplayLabel(rawDisplay)) {
            return rawDisplay.trim();
        }
        if (characterId != null && !characterId.trim().isEmpty()) {
            return "Char " + characterId.trim();
        }
        if (sessionId != null && sessionId.length() >= 8) {
            return "Sess " + sessionId.trim().substring(0, Math.min(12, sessionId.trim().length()));
        }
        return "Storm account";
    }
}
