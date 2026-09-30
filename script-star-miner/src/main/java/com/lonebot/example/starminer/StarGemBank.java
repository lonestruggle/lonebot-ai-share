package com.lonebot.example.starminer;

import com.lonebot.example.StarMinerPlugin;
import net.runelite.api.coords.WorldPoint;
import net.storm.sdk.bot.BotRuntime;
import net.storm.sdk.commons.Rand;
import net.storm.sdk.entities.Players;
import net.storm.sdk.game.BankHelper;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.HumanBanking;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.movement.MovementHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Uncut gems banken — alleen buiten actief minen (start / vóór hop / na skip).
 * Emir's Arena-chest (3352,3277) is een ster-crash-tegel, geen bruikbare booth.
 */
final class StarGemBank {

    private static final int OPEN_RANGE = 6;
    private static final int AVOID_STAR_TILES = 10;
    private static final int FAIL_BANK_TILES = 6;
    private static final int MAX_FAILED_BANKS = 4;
    /** F2P-lijst-punt: chest op de crash-tegel, geen Bank-booth. */
    static final WorldPoint EMIRS_ARENA_CHEST = new WorldPoint(3352, 3277, 0);

    private boolean active;
    private long lastLogMs;
    private String lastLog = "";
    private String status = "gem bank";
    private WorldPoint dest;
    private long openSinceMs;
    private final List<WorldPoint> failedBanks = new ArrayList<>();

    void reset() {
        active = false;
        status = "gem bank";
        dest = null;
        openSinceMs = 0L;
        failedBanks.clear();
    }

    String status() {
        return status;
    }

    boolean isActive() {
        return active;
    }

    static int gemCount() {
        try {
            return Inventory.getCount(true, StarPickaxes.UNCUT_GEMS);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** True als we mogen banken: optie aan + genoeg gems + niet midden in een mine-sessie. */
    boolean shouldBank(boolean miningActive) {
        if (!StarMinerPlugin.bankGemsEnabled) {
            return false;
        }
        if (miningActive) {
            return false;
        }
        if (active) {
            return true;
        }
        return gemCount() >= StarMinerPlugin.bankGemsAt();
    }

    void arm() {
        active = true;
        dest = null;
        openSinceMs = 0L;
        log("start gem-bank (" + gemCount() + " ≥ " + StarMinerPlugin.bankGemsAt() + ")");
    }

    int tick(WorldPoint... avoidStarTiles) {
        active = true;
        try {
            if (Bank.isOpen()) {
                if (yieldForLamp(true)) {
                    status = "gem bank — lamp eerst";
                    return Rand.nextInt(500, 800);
                }
                openSinceMs = 0L;
                return depositGems();
            }
        } catch (Throwable ignored) {
        }
        Players.LocalSnap me = Players.snapshotLocal();
        WorldPoint pos = me != null ? me.worldLocation : null;
        if (pos == null) {
            status = "gem bank (geen pos)";
            return 800;
        }
        try {
            if (BankHelper.ensureLumbridgeCastleStairProgress()) {
                status = "gem bank → Lumb trap";
                return Rand.nextInt(700, 1200);
            }
        } catch (Throwable ignored) {
        }
        if (dest != null && skipBank(dest, avoidStarTiles)) {
            log("bank-doel ongeldig " + dest.getX() + "," + dest.getY() + " — andere bank");
            dest = null;
            openSinceMs = 0L;
        }
        WorldPoint bank = dest != null ? dest : pickBank(pos, avoidStarTiles);
        dest = bank;
        if (bank == null) {
            status = "gem bank (geen F2P-bank)";
            log("geen F2P-bank (chest/ster-tegel overgeslagen)");
            active = false;
            return 800;
        }
        int d = pos.getPlane() == bank.getPlane() ? pos.distanceTo(bank) : 99;
        if (d <= OPEN_RANGE && yieldForLamp(false)) {
            openSinceMs = 0L;
            status = "gem bank — lamp eerst";
            return Rand.nextInt(500, 800);
        }
        if (d > OPEN_RANGE) {
            openSinceMs = 0L;
            log("loop naar bank " + bank.getX() + "," + bank.getY() + " d=" + d);
            StarWalk.Result wr = StarWalk.toward(pos, bank, 2, "→ gem-bank");
            status = wr.status;
            return wr.delayMs;
        }
        long now = System.currentTimeMillis();
        if (openSinceMs <= 0L) {
            openSinceMs = now;
        } else if (now - openSinceMs > 20_000L) {
            markFailedBank(bank, d);
            dest = null;
            openSinceMs = 0L;
            if (failedBanks.size() >= MAX_FAILED_BANKS) {
                active = false;
                status = "gem bank skip";
                log("banken lukt niet (" + failedBanks.size() + "×) — skip gems deze ronde");
                return Rand.nextInt(400, 700);
            }
            WorldPoint next = pickBank(pos, avoidStarTiles);
            dest = next;
            if (next == null) {
                active = false;
                status = "gem bank (geen F2P-bank)";
                log("geen andere F2P-bank — skip gems");
                return Rand.nextInt(400, 700);
            }
            log("loop naar andere bank " + next.getX() + "," + next.getY());
            StarWalk.Result wr = StarWalk.toward(pos, next, 2, "→ gem-bank andere");
            status = wr.status;
            return wr.delayMs;
        }
        try {
            if (BankHelper.tryOpenFullBank()) {
                clampWalkToChosenBank(bank);
                status = "gem bank openen";
                log("open bank voor gems d=" + d + " @" + bank.getX() + "," + bank.getY());
                return Rand.nextInt(500, 900);
            }
        } catch (Throwable t) {
            log("open fout " + t.getClass().getSimpleName());
        }
        status = "gem bank open retry";
        return Rand.nextInt(400, 700);
    }

    /** Dichtstbijzijnde échte F2P-booth — nooit Emir's Arena-chest / crash-tegel. */
    WorldPoint pickBank(WorldPoint from, WorldPoint... avoidStarTiles) {
        if (from == null) {
            return null;
        }
        WorldPoint pick = BankHelper.getNearestF2pBankPoint(from, w -> skipBank(w, avoidStarTiles));
        if (pick != null) {
            BotRuntime.logConsole("[Star/gems] bank " + pick.getX() + "," + pick.getY()
                    + ",p" + pick.getPlane());
        }
        return pick;
    }

    private boolean skipBank(WorldPoint w, WorldPoint... avoidStarTiles) {
        if (w == null) {
            return true;
        }
        if (isEmirsArenaChest(w)) {
            return true;
        }
        if (nearAny(w, failedBanks, FAIL_BANK_TILES)) {
            return true;
        }
        if (avoidStarTiles == null) {
            return false;
        }
        for (WorldPoint avoid : avoidStarTiles) {
            if (avoid != null && w.getPlane() == avoid.getPlane()
                    && w.distanceTo(avoid) <= AVOID_STAR_TILES) {
                return true;
            }
        }
        return false;
    }

    private void markFailedBank(WorldPoint bank, int d) {
        if (bank == null) {
            return;
        }
        failedBanks.add(bank);
        log("bank open timeout d=" + d + " @" + bank.getX() + "," + bank.getY()
                + " — skip deze bank");
    }

    static boolean isEmirsArenaChest(WorldPoint w) {
        return w != null && w.getPlane() == EMIRS_ARENA_CHEST.getPlane()
                && w.distanceTo(EMIRS_ARENA_CHEST) <= 8;
    }

    private static boolean nearAny(WorldPoint w, List<WorldPoint> list, int tiles) {
        if (w == null || list == null) {
            return false;
        }
        for (WorldPoint p : list) {
            if (p != null && w.getPlane() == p.getPlane() && w.distanceTo(p) <= tiles) {
                return true;
            }
        }
        return false;
    }

    private boolean yieldForLamp(boolean bankAlreadyOpen) {
        try {
            if (net.storm.sdk.game.RandomEventHandler.shouldYieldForLampAtBank()) {
                log(bankAlreadyOpen ? "lamp — geen deposit" : "lamp eerst, daarna bank");
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private int depositGems() {
        int before = gemCount();
        if (before <= 0) {
            Bank.close();
            active = false;
            dest = null;
            status = "gem bank klaar";
            log("geen gems meer — klaar");
            return Rand.nextInt(350, 600);
        }
        boolean any = false;
        try {
            for (int id : StarPickaxes.UNCUT_GEMS) {
                if (Inventory.getCount(true, id) <= 0) {
                    continue;
                }
                if (Bank.deposit(id, Integer.MAX_VALUE)) {
                    any = true;
                    break;
                }
            }
        } catch (Throwable t) {
            log("deposit fout " + t.getClass().getSimpleName());
        }
        status = any ? "gem deposit…" : "gem deposit-miss";
        log(status + " had=" + before);
        if (gemCount() <= 0) {
            Bank.close();
            active = false;
            dest = null;
            status = "gem bank klaar";
            log("gems gestort");
        }
        return HumanBanking.afterActionMs();
    }

    /** COS/blocked mag niet naar Shantay of een andere bank lopen. */
    private void clampWalkToChosenBank(WorldPoint bank) {
        if (bank == null) {
            return;
        }
        try {
            WorldPoint going = MovementHelper.getActiveDestination();
            if (going == null) {
                return;
            }
            if (going.getPlane() == bank.getPlane() && going.distanceTo(bank) <= 18) {
                return;
            }
            log("niet naar " + going.getX() + "," + going.getY() + " — blijf bij "
                    + bank.getX() + "," + bank.getY());
            MovementHelper.clearPath();
        } catch (Throwable ignored) {
        }
    }

    private void log(String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastLog) && now - lastLogMs < 1600L) {
            return;
        }
        lastLog = msg;
        lastLogMs = now;
        BotRuntime.logConsole("[Star/gems] " + msg);
    }
}
