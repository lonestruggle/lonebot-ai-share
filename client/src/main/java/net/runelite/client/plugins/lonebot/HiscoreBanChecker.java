package net.runelite.client.plugins.lonebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Hiscore-ban check: alleen {@link OsrsHiscoreApi.Status#NOT_FOUND} → vermoedelijk banned.
 * Netwerk/API-fouten ({@link OsrsHiscoreApi.Status#TRANSIENT_ERROR}) wijzigen de ban-flag <b>niet</b>.
 */
public final class HiscoreBanChecker {

    private static final Logger log = LoggerFactory.getLogger(HiscoreBanChecker.class);
    private static final long STALE_MS = 30L * 60L * 1000L;
    private static final AtomicBoolean inFlight = new AtomicBoolean(false);

    public enum Outcome {
        OK,
        SUSPECT_BANNED,
        /** Tijdelijke fout — vorige ban-flag ongemoeid. */
        UNCERTAIN,
        /**
         * Geen echte RSN (e-mail/placeholder) — hiscore niet van toepassing.
         * <p>Met een echte in-game naam is 404 altijd {@link #SUSPECT_BANNED}, nooit dit.
         */
        UNRANKED
    }

    public static final class Result {
        public final int checked;
        public final int banned;
        public final int ok;
        public final int uncertain;
        public final String message;

        public Result(int checked, int banned, int ok, int uncertain, String message) {
            this.checked = checked;
            this.banned = banned;
            this.ok = ok;
            this.uncertain = uncertain;
            this.message = message;
        }
    }

    private HiscoreBanChecker() {
    }

    /**
     * @return true alleen bij bevestigde NOT_FOUND (suspect banned)
     */
    public static boolean checkAndPersist(String displayName) {
        return checkAndPersistOutcome(displayName) == Outcome.SUSPECT_BANNED;
    }

    public static Outcome checkAndPersistOutcome(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return Outcome.UNCERTAIN;
        }
        String rsn = displayName.trim();
        ManagedAccountsStore.ManagedAccount row = ManagedAccountsStore.findByDisplayName(rsn);
        if (row == null) {
            for (ManagedAccountsStore.ManagedAccount a : ManagedAccountsStore.getAccounts()) {
                if (a != null && a.displayName != null
                        && rsn.equalsIgnoreCase(a.displayName.trim())) {
                    row = a;
                    break;
                }
            }
        }
        String cid = row != null ? row.characterId : "";
        if (!com.lonebot.launcher.jagex.HiscoreNameRules.isHiscoreEligibleName(rsn, cid)) {
            log.info("hiscore-check {} → skip (geen RSN)", rsn);
            return Outcome.UNRANKED;
        }
        // Echte RSN = geen "nieuw" meer: import-flag mag 404 niet meer beschermen.
        if (row != null && row.jagexUnrankedNew) {
            row.jagexUnrankedNew = false;
            ManagedAccountsStore.save();
            log.info("hiscore-check {} → jagexUnrankedNew gewist (heeft RSN)", rsn);
        }
        OsrsHiscoreApi.FetchResult fr = OsrsHiscoreApi.fetchFullSkillLevelsResult(rsn);

        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps = AccountStatSnapshotsStore.load();
        AccountStatSnapshotsStore.AccountStatSnapshot prev =
                AccountStatSnapshotsStore.snapshotForRow(snaps, rsn);
        AccountStatSnapshotsStore.AccountStatSnapshot snap =
                prev != null ? prev : new AccountStatSnapshotsStore.AccountStatSnapshot();

        if (fr.status == OsrsHiscoreApi.Status.OK) {
            Map<String, String> live = linesFromOk(fr);
            if (live != null) {
                snap.attack = parseLvl(live.get("Attack"));
                snap.defence = parseLvl(live.get("Defence"));
                snap.strength = parseLvl(live.get("Strength"));
                snap.magic = parseLvl(live.get("Magic"));
                snap.prayer = parseLvl(live.get("Prayer"));
                snap.woodcutting = parseLvl(live.get("Woodcutting"));
                snap.mining = parseLvl(live.get("Mining"));
                snap.fishing = parseLvl(live.get("Fishing"));
                try {
                    snap.combatLevel = Integer.parseInt(OsrsHiscoreApi.computeCombatLevelReactDisplay(live));
                } catch (Exception ignored) {
                }
            }
            boolean wasBanned = snap.hiscoreSuspectBanned;
            snap.hiscoreSuspectBanned = false;
            snap.updatedEpochMs = System.currentTimeMillis();
            AccountStatSnapshotsStore.merge(rsn, snap);
            if (wasBanned) {
                log.info("hiscore-check {} → OK (ban-flag gewist)", rsn);
            } else {
                log.info("hiscore-check {} → OK", rsn);
            }
            return Outcome.OK;
        }

        if (fr.status == OsrsHiscoreApi.Status.TRANSIENT_ERROR) {
            // Geen false ban: flag niet aanzetten; timestamp wél (voorkomt spam) tenzij we banned willen rechecken
            snap.updatedEpochMs = System.currentTimeMillis();
            AccountStatSnapshotsStore.merge(rsn, snap);
            log.info("hiscore-check {} → UNCERTAIN ({}) — ban-flag ongewijzigd ({})",
                    rsn, fr.detail, snap.hiscoreSuspectBanned);
            return Outcome.UNCERTAIN;
        }

        // NOT_FOUND + echte RSN → vermoedelijk banned (geen XP-hiscore = weg/banned)
        snap.hiscoreSuspectBanned = true;
        snap.updatedEpochMs = System.currentTimeMillis();
        AccountStatSnapshotsStore.merge(rsn, snap);
        AccountStatSnapshotsStore.disableRotationForBanned(rsn);
        log.info("hiscore-check {} → SUSPECT_BANNED ({})", rsn, fr.detail);
        return Outcome.SUSPECT_BANNED;
    }

    private static Map<String, String> linesFromOk(OsrsHiscoreApi.FetchResult fr) {
        if (fr == null || fr.lines == null || fr.lines.size() < 16) {
            return null;
        }
        // Kleine helper via tweede publieke API: bouw map handmatig zoals OsrsHiscoreApi
        return rebuildMap(fr.lines);
    }

    private static Map<String, String> rebuildMap(List<String> lines) {
        String[] names = {
                "Overall",
                "Attack", "Defence", "Strength", "Hitpoints",
                "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
                "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing",
                "Mining", "Herblore", "Agility", "Thieving", "Slayer",
                "Farming", "Runecraft", "Hunter", "Construction",
                "Sailing"
        };
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            if (i >= lines.size()) {
                out.put(names[i], "—");
                continue;
            }
            String line = lines.get(i);
            String[] p = line != null ? line.split(",") : new String[0];
            if (p.length < 2) {
                out.put(names[i], "—");
            } else if ("-1".equals(p[1].trim())) {
                out.put(names[i], "Hitpoints".equals(names[i]) ? "10" : "1");
            } else {
                out.put(names[i], p[1].trim());
            }
        }
        return out;
    }

    public static void checkDueAsync(boolean forceAll, Consumer<Result> onDone) {
        if (!inFlight.compareAndSet(false, true)) {
            if (onDone != null) {
                onDone.accept(new Result(0, 0, 0, 0, "Hiscore-check loopt al…"));
            }
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Result r = checkDueBlocking(forceAll);
                if (onDone != null) {
                    onDone.accept(r);
                }
            } finally {
                inFlight.set(false);
            }
        }, "hiscore-ban-check");
        t.setDaemon(true);
        t.start();
    }

    /**
     * @param forceAll true = alle <b>niet-banned</b> accounts (ook recent). Banned alleen na "Wis ban-flags".
     */
    public static Result checkDueBlocking(boolean forceAll) {
        List<ManagedAccountsStore.ManagedAccount> rows =
                new ArrayList<>(ManagedAccountsStore.getAccounts());
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps =
                AccountStatSnapshotsStore.load();
        long now = System.currentTimeMillis();
        List<String> due = new ArrayList<>();
        for (ManagedAccountsStore.ManagedAccount r : rows) {
            if (r == null || r.isEmpty() || r.displayName == null || r.displayName.trim().isEmpty()) {
                continue;
            }
            // Placeholder-namen (e-mail/cid) overslaan; echte RSN wél checken — ook als
            // jagexUnrankedNew nog aan stond (die flag wordt bij check gewist).
            if (!com.lonebot.launcher.jagex.HiscoreNameRules.isHiscoreEligibleName(r.displayName, r.characterId)) {
                continue;
            }
            String rsn = r.displayName.trim();
            AccountStatSnapshotsStore.AccountStatSnapshot s =
                    AccountStatSnapshotsStore.snapshotForRow(snaps, rsn);
            // Banned: nooit opnieuw tot "Wis ban-flags"
            if (s != null && s.hiscoreSuspectBanned) {
                continue;
            }
            if (forceAll) {
                due.add(rsn);
                continue;
            }
            boolean neverChecked = s == null || s.updatedEpochMs <= 0;
            boolean stale = !neverChecked && (now - s.updatedEpochMs >= STALE_MS);
            if (neverChecked || stale) {
                due.add(rsn);
            }
        }
        if (due.isEmpty()) {
            return new Result(0, 0, 0, 0, "Geen accounts om te checken (alles recent)");
        }
        int banned = 0;
        int ok = 0;
        int uncertain = 0;
        for (String rsn : due) {
            Outcome o = checkAndPersistOutcome(rsn);
            if (o == Outcome.SUSPECT_BANNED) {
                banned++;
            } else if (o == Outcome.OK) {
                ok++;
            } else {
                uncertain++;
            }
            try {
                Thread.sleep(350L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        String msg = "Hiscore: " + due.size() + " — " + ok + " ok, "
                + banned + " banned(404), " + uncertain + " onzeker (flag ongewijzigd)";
        return new Result(due.size(), banned, ok, uncertain, msg);
    }

    /** Wis alle hiscoreSuspectBanned-flags (handmatig herstel). */
    public static int clearAllBanFlags() {
        Map<String, AccountStatSnapshotsStore.AccountStatSnapshot> snaps =
                AccountStatSnapshotsStore.load();
        int n = 0;
        for (Map.Entry<String, AccountStatSnapshotsStore.AccountStatSnapshot> e : snaps.entrySet()) {
            AccountStatSnapshotsStore.AccountStatSnapshot s = e.getValue();
            if (s != null && s.hiscoreSuspectBanned) {
                s.hiscoreSuspectBanned = false;
                s.updatedEpochMs = System.currentTimeMillis();
                AccountStatSnapshotsStore.merge(e.getKey(), s);
                n++;
            }
        }
        return n;
    }

    private static int parseLvl(String v) {
        if (v == null || v.isEmpty() || "—".equals(v)) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
