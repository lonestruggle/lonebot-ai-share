package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.Skill;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.game.Skills;
import net.storm.sdk.game.WorldHopGate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merges Discord + OSRS Portal + 07.gg + JSON into an in-memory star list; drives the local HTTP API.
 */
public final class StarFeed {

    private static final long POLL_MS = 20_000L;
    private static final long LIVE_MS = 55 * 60_000L;

    private static final StarFeed INSTANCE = new StarFeed();

    private final Map<Integer, StarCall> byWorld = new ConcurrentHashMap<>();
    private final StarDiscordClient discord = new StarDiscordClient();
    private final StarPortalClient portal = new StarPortalClient();
    private final StarGgClient gg = new StarGgClient();
    private final StarHttpJson httpJson = new StarHttpJson();
    private final StarLocalApi api = new StarLocalApi();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile StarFeedSettings settings = StarFeedSettings.load();
    private volatile long updatedAtMs;
    private volatile boolean discordOk;
    private volatile boolean portalOk;
    private volatile boolean ggOk;
    private volatile int lastMining = 1;
    private volatile long lastUiPublishMs;
    private Thread worker;

    public static StarFeed get() {
        return INSTANCE;
    }

    public synchronized void start() {
        settings = StarFeedSettings.load();
        api.start(settings.apiPort);
        if (running.compareAndSet(false, true)) {
            worker = new Thread(this::loop, "lonebot-star-feed");
            worker.setDaemon(true);
            worker.start();
        }
        logStatus();
    }

    public synchronized void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        api.stop();
    }

    public List<StarCall> active() {
        prune();
        List<StarCall> list = new ArrayList<>();
        for (StarCall c : byWorld.values()) {
            if (c != null && c.usable()) {
                list.add(c);
            }
        }
        list.sort(Comparator.comparingLong((StarCall c) -> c.calledAtMs).reversed());
        return list;
    }

    public StarCall best(int miningLevel, boolean avoidWildy, boolean f2pOnly) {
        return best(miningLevel, avoidWildy, f2pOnly, null);
    }

    public StarCall best(int miningLevel, boolean avoidWildy, boolean f2pOnly, Set<Integer> excludeWorlds) {
        StarCall mineNow = bestMatching(miningLevel, avoidWildy, f2pOnly, excludeWorlds, true);
        if (mineNow != null) {
            return mineNow;
        }
        return bestMatching(miningLevel, avoidWildy, f2pOnly, excludeWorlds, false);
    }

    /** Alleen sterren waarvan de feed-laag nu minebaar is (niet wachten / T?). */
    public StarCall bestMineableNow(int miningLevel, boolean avoidWildy, boolean f2pOnly,
                                    Set<Integer> excludeWorlds) {
        return bestMatching(miningLevel, avoidWildy, f2pOnly, excludeWorlds, true);
    }

    private StarCall bestMatching(int miningLevel, boolean avoidWildy, boolean f2pOnly,
                                  Set<Integer> excludeWorlds, boolean mineableOnly) {
        StarCall best = null;
        for (StarCall c : active()) {
            if (excludeWorlds != null && excludeWorlds.contains(c.world)) {
                continue;
            }
            if (!usableFor(c, miningLevel, avoidWildy, f2pOnly)) {
                continue;
            }
            boolean can = canMineNow(c, miningLevel);
            if (mineableOnly != can) {
                continue;
            }
            if (best == null || betterThan(c, best, miningLevel)) {
                best = c;
            }
        }
        return best;
    }

    public StarCall onWorld(int world) {
        if (world <= 0) {
            return null;
        }
        for (StarCall c : active()) {
            if (c != null && c.world == world) {
                return c;
            }
        }
        return null;
    }

    public int countUsable(int miningLevel, boolean avoidWildy, boolean f2pOnly) {
        return countUsable(miningLevel, avoidWildy, f2pOnly, null);
    }

    public int countUsable(int miningLevel, boolean avoidWildy, boolean f2pOnly, Set<Integer> excludeWorlds) {
        int n = 0;
        for (StarCall c : active()) {
            if (excludeWorlds != null && excludeWorlds.contains(c.world)) {
                continue;
            }
            if (usableFor(c, miningLevel, avoidWildy, f2pOnly)) {
                n++;
            }
        }
        return n;
    }

    public int countMineableNow(int miningLevel, boolean avoidWildy, boolean f2pOnly,
                                Set<Integer> excludeWorlds) {
        int n = 0;
        for (StarCall c : active()) {
            if (excludeWorlds != null && excludeWorlds.contains(c.world)) {
                continue;
            }
            if (usableFor(c, miningLevel, avoidWildy, f2pOnly) && canMineNow(c, miningLevel)) {
                n++;
            }
        }
        return n;
    }

    public static String filterDebug(int miningLevel, boolean avoidWildy, boolean f2pOnly) {
        int p2pLoc = 0;
        int membersW = 0;
        int wild = 0;
        int lvl = 0;
        int hopBlock = 0;
        int questBlock = 0;
        int lowTier = 0;
        int ok = 0;
        int mineNow = 0;
        for (StarCall c : get().active()) {
            if (c == null || c.spot == null) {
                continue;
            }
            if (avoidWildy && c.spot.wilderness) {
                wild++;
                continue;
            }
            if (f2pOnly && !c.spot.f2p) {
                p2pLoc++;
                continue;
            }
            if (!StarSpotGate.accessible(c.spot)) {
                questBlock++;
                continue;
            }
            if (c.tier > 0 && c.tier < StarMinerPlugin.minTier()) {
                lowTier++;
                continue;
            }
            String block = WorldHopGate.blockReason(c.world, f2pOnly);
            if (block != null) {
                hopBlock++;
                continue;
            }
            if (c.tier > 0 && miningLevel < c.miningLevelRequired()) {
                lvl++;
                if (!StarMinerPlugin.wouldVisitTier(c.tier, miningLevel)) {
                    continue;
                }
            } else if (canMineNow(c, miningLevel)) {
                mineNow++;
            }
            ok++;
        }
        return "ok=" + ok + " nu=" + mineNow + " p2pLoc=" + p2pLoc + " geenHop=" + hopBlock
                + " quest=" + questBlock + " lowT=" + lowTier + " wild=" + wild + " teHoog=" + lvl
                + " minT" + StarMinerPlugin.minTier()
                + " extra=+" + StarMinerPlugin.waitTiersAbove()
                + " maxT" + StarMinerPlugin.maxVisitTier(miningLevel)
                + " F2P-skills=" + WorldHopGate.cachedF2pTotal()
                + " totaal=" + WorldHopGate.cachedTotalLevel();
    }

    static boolean usableFor(StarCall c, int miningLevel, boolean avoidWildy, boolean f2pOnly) {
        if (c == null || c.spot == null) {
            return false;
        }
        if (c.tier > 0 && c.tier < StarMinerPlugin.minTier()) {
            return false;
        }
        if (c.tier > 0 && !StarMinerPlugin.wouldVisitTier(c.tier, miningLevel)) {
            return false;
        }
        if (avoidWildy && c.spot.wilderness) {
            return false;
        }
        if (f2pOnly) {
            if (!c.spot.f2p) {
                return false;
            }
        }
        if (!StarSpotGate.accessible(c.spot)) {
            return false;
        }
        return WorldHopGate.blockReason(c.world, f2pOnly) == null;
    }

    public void ingestPlayerChat() {
        long now = System.currentTimeMillis();
        List<StarCall> chat = StarChatScout.poll(now);
        if (chat.isEmpty()) {
            return;
        }
        merge(chat);
        prune();
        publishUiSnapshot(null, true);
    }

    public void markDead(int world) {
        if (world <= 0) {
            return;
        }
        StarCall old = byWorld.get(world);
        if (old == null) {
            return;
        }
        byWorld.put(world, new StarCall(old.world, old.tier, old.locationRaw, old.spot,
                old.miners, old.calledAtMs, true, old.source, old.raw));
    }

    public long updatedAtMs() {
        return updatedAtMs;
    }

    public boolean discordOk() {
        return discordOk;
    }

    public boolean portalOk() {
        return portalOk;
    }

    public boolean ggOk() {
        return ggOk;
    }

    public int apiPort() {
        return api.port();
    }

    public StarFeedSettings settings() {
        return settings;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"updatedAt\":").append(updatedAtMs)
                .append(",\"api\":\"http://127.0.0.1:").append(api.port()).append("/stars\"")
                .append(",\"stars\":[");
        boolean first = true;
        for (StarCall c : active()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"world\":").append(c.world)
                    .append(",\"tier\":").append(c.tier)
                    .append(",\"location\":\"").append(esc(c.spot != null ? c.spot.shortName : c.locationRaw)).append('"')
                    .append(",\"locationKey\":\"").append(c.spot != null ? c.spot.key : "").append('"');
            if (c.tile() != null) {
                sb.append(",\"x\":").append(c.tile().getX())
                        .append(",\"y\":").append(c.tile().getY())
                        .append(",\"plane\":").append(c.tile().getPlane());
            }
            sb.append(",\"wilderness\":").append(c.spot != null && c.spot.wilderness)
                    .append(",\"f2p\":").append(c.spot != null && c.spot.f2p)
                    .append(",\"miners\":").append(c.miners)
                    .append(",\"calledAt\":").append(c.calledAtMs)
                    .append(",\"source\":\"").append(esc(c.source)).append('"')
                    .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private void loop() {
        while (running.get()) {
            try {
                refresh();
            } catch (Throwable t) {
                BotRuntime.logConsole("[Star/feed] poll fout: " + t.getClass().getSimpleName());
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void refresh() {
        settings = StarFeedSettings.load();
        long now = System.currentTimeMillis();
        List<StarCall> incoming = new ArrayList<>();
        if (settings.hasDiscord()) {
            List<StarCall> d = discord.poll(settings, now);
            incoming.addAll(d);
            discordOk = !d.isEmpty() || discordOk;
        } else {
            discordOk = false;
        }
        if (settings.portalEnabled) {
            List<StarCall> p = portal.poll(StarPortalClient.DEFAULT_URL, now);
            incoming.addAll(p);
            portalOk = portal.lastOk();
        } else {
            portalOk = false;
        }
        if (settings.ggEnabled) {
            List<StarCall> g = gg.poll(now);
            incoming.addAll(g);
            ggOk = gg.lastOk();
        } else {
            ggOk = false;
        }
        String url = StarMinerPlugin.jsonUrl;
        if (url == null || url.isBlank()) {
            url = settings.jsonUrl;
        }
        if (url != null && !url.isBlank()) {
            incoming.addAll(httpJson.poll(url, now));
        }
        List<StarCall> chat = StarChatScout.poll(now);
        if (!chat.isEmpty()) {
            incoming.addAll(chat);
        }
        merge(incoming);
        prune();
        updatedAtMs = now;
        publishUiSnapshot(null, true);
        StarParkHandoff.onFeedUpdated();
    }

    /** Overlay/loop: zelfde lijst als de bot, plus huidig doel als die niet in de feed staat. */
    public void publishUiSnapshot(StarCall extra) {
        publishUiSnapshot(extra, false);
    }

    private void publishUiSnapshot(StarCall extra, boolean force) {
        long t0 = System.currentTimeMillis();
        if (!force && t0 - lastUiPublishMs < 1000L) {
            return;
        }
        lastUiPublishMs = t0;
        List<StarCall> list = new ArrayList<>(active());
        if (extra != null && extra.spot != null && extra.world > 0) {
            boolean have = false;
            for (StarCall c : list) {
                if (c != null && c.world == extra.world && extra.spot.key != null
                        && extra.spot.key.equals(c.spot != null ? c.spot.key : null)) {
                    have = true;
                    break;
                }
            }
            if (!have) {
                list.add(0, extra);
            }
        }
        List<String> lines = new ArrayList<>(list.size());
        List<Boolean> visitOk = new ArrayList<>(list.size());
        long now = System.currentTimeMillis();
        boolean avoidWildy = StarMinerPlugin.avoidWilderness;
        boolean f2pOnly = StarMinerPlugin.f2pOnly;
        int mining = lastMining;
        try {
            int m = Skills.getLevel(Skill.MINING);
            if (m > 1) {
                lastMining = m;
                mining = m;
            } else if (lastMining <= 1) {
                mining = m;
            }
        } catch (Throwable ignored) {
        }
        Map<Integer, String> hopCache = new HashMap<>();
        for (StarCall c : list) {
            if (c == null || c.spot == null) {
                continue;
            }
            String hop = hopCache.computeIfAbsent(c.world, id -> WorldHopGate.blockReason(id, f2pOnly));
            if (WorldHopGate.isPvpBlockReason(hop)) {
                continue;
            }
            String why = uiBlockReason(c, mining, avoidWildy, f2pOnly, hop);
            if (hideFromList(why, hop, f2pOnly, avoidWildy)) {
                continue;
            }
            if (why != null) {
                lines.add(c.tabLine(now) + "  ✕ " + why);
                visitOk.add(Boolean.FALSE);
            } else {
                lines.add(c.tabLine(now));
                visitOk.add(Boolean.TRUE);
            }
        }
        BotRuntime.starFeedCount = lines.size();
        BotRuntime.starFeedLines = List.copyOf(lines);
        BotRuntime.starFeedVisitOk = List.copyOf(visitOk);
    }

    /**
     * F2P-filter: members/wildy uit de lijst (niet rood-drukte). Uitvinken “Alleen F2P” toont ze weer.
     * Skill-total / te hoog / quest op F2P blijven zichtbaar (rood). Hopper-timeout niet verbergen.
     */
    private static boolean hideFromList(String why, String hop, boolean f2pOnly, boolean avoidWildy) {
        if (why == null) {
            return false;
        }
        String w = why.toLowerCase(java.util.Locale.ROOT);
        if (avoidWildy && (w.contains("wilderness") || "wilderness".equals(w))) {
            return true;
        }
        if (!f2pOnly) {
            return false;
        }
        if (w.contains("members")) {
            return true;
        }
        if (hop != null && hop.toLowerCase(java.util.Locale.ROOT).contains("members")) {
            return true;
        }
        return false;
    }

    /** Rood in de lijst; PvP-werelden worden niet getoond. Hopper-timeout ≠ verbergen. */
    private static String uiBlockReason(StarCall c, int mining, boolean avoidWildy, boolean f2pOnly, String hop) {
        if (avoidWildy && c.spot.wilderness) {
            return "wilderness";
        }
        if (f2pOnly && !c.spot.f2p) {
            return "members-locatie";
        }
        if (!StarSpotGate.accessible(c.spot)) {
            String r = StarSpotGate.blockReason(c.spot);
            return r != null ? r : "niet bereikbaar";
        }
        if (hop != null) {
            return hop;
        }
        if (c.tier > 0 && c.tier < StarMinerPlugin.minTier()) {
            return "onder min T" + StarMinerPlugin.minTier();
        }
        if (c.tier > 0 && !StarMinerPlugin.wouldVisitTier(c.tier, mining)) {
            return "te hoog (max T" + StarMinerPlugin.maxVisitTier(mining) + ")";
        }
        return null;
    }

    private void merge(List<StarCall> incoming) {
        for (StarCall c : incoming) {
            if (c == null || c.world <= 0) {
                continue;
            }
            if (c.dead) {
                markDead(c.world);
                continue;
            }
            if (!c.usable()) {
                continue;
            }
            StarCall prev = byWorld.get(c.world);
            if (prev == null) {
                byWorld.put(c.world, c);
                if ("chat".equals(c.source)) {
                    StarChatScout.logOnce("speler-call W" + c.world + " "
                            + (c.spot != null ? c.spot.shortName : "?"));
                }
                continue;
            }
            boolean chat = "chat".equals(c.source);
            boolean prevChat = "chat".equals(prev.source);
            boolean chatFillsLoc = chat && c.spot != null && (prev.spot == null || prev.tile() == null);
            if (chatFillsLoc) {
                StarChatScout.logOnce("speler-call vult locatie W" + c.world + " " + c.spot.shortName);
                byWorld.put(c.world, c);
                continue;
            }
            if (chat && !prevChat) {
                continue; // live-update wint; chat alleen als er geen locatie was
            }
            if (c.calledAtMs >= prev.calledAtMs) {
                byWorld.put(c.world, c);
            }
        }
    }

    private void prune() {
        long now = System.currentTimeMillis();
        byWorld.entrySet().removeIf(e -> {
            StarCall c = e.getValue();
            return c == null || c.dead || now - c.calledAtMs > LIVE_MS;
        });
    }

    /** Bekende laag die Mining nu aankan — T? en te-hoog tellen niet. */
    public static boolean canMineNow(StarCall c, int miningLevel) {
        return c != null && c.tier > 0 && miningLevel >= c.miningLevelRequired();
    }

    /**
     * Alleen onder de Star-filters (min-tier, extra wachtlagen, F2P, wildy).
     * Minebaar: hoogste laag die Mining aankan, daarna minste miners.
     * Wachten: laagste te-hoge laag (T5 → T6, niet T8), daarna minste miners.
     */
    private static boolean betterThan(StarCall c, StarCall best, int miningLevel) {
        boolean can = canMineNow(c, miningLevel);
        boolean bestCan = canMineNow(best, miningLevel);
        if (can != bestCan) {
            return can;
        }
        if (can) {
            if (c.tier != best.tier) {
                return c.tier > best.tier;
            }
            return miners(c) < miners(best);
        }
        // Wacht-groep: bekende laag wint van T?; daarna dichtst bij jouw max-minebare.
        if (c.tier <= 0 && best.tier > 0) {
            return false;
        }
        if (c.tier > 0 && best.tier <= 0) {
            return true;
        }
        if (c.tier != best.tier) {
            return c.tier < best.tier;
        }
        return miners(c) < miners(best);
    }

    private static int miners(StarCall c) {
        return c.miners >= 0 ? c.miners : 50;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void logStatus() {
        StarFeedSettings s = settings;
        BotRuntime.logConsole("[Star/feed] discord=" + (s.hasDiscord() ? "aan (" + s.channelIds.size() + " ch)" : "uit")
                + " portal=" + (s.portalEnabled ? "aan" : "uit")
                + " 07.gg=" + (s.ggEnabled ? "aan" : "uit")
                + " json=" + (s.hasJson() || (StarMinerPlugin.jsonUrl != null && !StarMinerPlugin.jsonUrl.isBlank())
                ? "aan" : "uit")
                + " file=" + StarFeedSettings.file().getAbsolutePath());
    }
}
