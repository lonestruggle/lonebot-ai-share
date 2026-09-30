package net.storm.sdk.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Laadt {@code lonebot-api-catalog.json} — Storm→LoneBot API database.
 */
public final class ApiCatalog {

    private static final Logger log = LoggerFactory.getLogger(ApiCatalog.class);
    private static final Gson GSON = new Gson();
    private static volatile JsonObject root;

    private ApiCatalog() {
    }

    public static synchronized JsonObject load() {
        if (root != null) {
            return root;
        }
        try (InputStream in = ApiCatalog.class.getResourceAsStream("/lonebot-api-catalog.json")) {
            if (in == null) {
                log.warn("lonebot-api-catalog.json niet op classpath");
                root = new JsonObject();
                return root;
            }
            root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            log.info("API catalog geladen: {} packages — {}", packages().size(), summaryLine());
            return root;
        } catch (Exception e) {
            log.warn("API catalog load fail: {}", e.toString());
            root = new JsonObject();
            return root;
        }
    }

    /** Forceer herladen (na hot-edit van resource). */
    public static synchronized void reload() {
        root = null;
        load();
    }

    public static String catalogVersion() {
        JsonObject r = load();
        return r.has("version") ? r.get("version").getAsString() : "?";
    }

    public static List<JsonObject> packages() {
        JsonObject r = load();
        JsonArray arr = r.getAsJsonArray("packages");
        if (arr == null) {
            return Collections.emptyList();
        }
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonObject()) {
                out.add(e.getAsJsonObject());
            }
        }
        return out;
    }

    public static List<JsonObject> byStatus(String status) {
        if (status == null) {
            return Collections.emptyList();
        }
        String want = status.toLowerCase(Locale.ROOT);
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject p : packages()) {
            String s = p.has("status") ? p.get("status").getAsString() : "";
            if (want.equalsIgnoreCase(s)) {
                out.add(p);
            }
        }
        return out;
    }

    public static JsonObject find(String stormOrLonebotName) {
        if (stormOrLonebotName == null || stormOrLonebotName.isEmpty()) {
            return null;
        }
        String q = stormOrLonebotName.toLowerCase(Locale.ROOT);
        for (JsonObject p : packages()) {
            String storm = p.has("storm") && !p.get("storm").isJsonNull() ? p.get("storm").getAsString() : "";
            String lone = p.has("lonebot") && !p.get("lonebot").isJsonNull() ? p.get("lonebot").getAsString() : "";
            if (storm.toLowerCase(Locale.ROOT).contains(q) || lone.toLowerCase(Locale.ROOT).contains(q)) {
                return p;
            }
        }
        return null;
    }

    public static List<String> mouseFlow() {
        JsonObject r = load();
        JsonArray arr = r.getAsJsonArray("mouseFlow");
        if (arr == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr) {
            out.add(e.getAsString());
        }
        return out;
    }

    public static String summaryLine() {
        int done = 0, partial = 0, missing = 0, stub = 0;
        for (JsonObject p : packages()) {
            String s = p.has("status") ? p.get("status").getAsString() : "";
            switch (s) {
                case "done":
                    done++;
                    break;
                case "partial":
                    partial++;
                    break;
                case "stub":
                    stub++;
                    break;
                default:
                    missing++;
                    break;
            }
        }
        return "API DB v" + catalogVersion()
                + " done=" + done
                + " partial=" + partial
                + " stub=" + stub
                + " miss=" + missing;
    }
}
