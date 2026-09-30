package net.storm.sdk.movement.pathfinder;

import net.storm.api.domain.items.IInventoryItem;
import net.storm.api.movement.pathfinder.ChargeRequirement;
import net.storm.api.movement.pathfinder.UnlockRequirement;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.quests.Quests;
import net.runelite.api.Quest;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Charge + unlock tracking for teleports / transports.
 *
 * @see <a href="https://stormjavadocs.z6.web.core.windows.net/net/storm/sdk/movement/pathfinder/ChargeManager.html">Storm ChargeManager</a>
 */
public final class ChargeManager {

    private static final Pattern CHARGE_SUFFIX = Pattern.compile("\\((?:t)?(\\d+)\\)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Set<ChargeRequirement> CHARGE_REQS = ConcurrentHashMap.newKeySet();
    private static final Set<UnlockRequirement> UNLOCK_REQS = ConcurrentHashMap.newKeySet();
    private static final Map<ChargeRequirement, Integer> CHARGE_OVERRIDE = new ConcurrentHashMap<>();
    private static final Map<UnlockRequirement, Boolean> UNLOCK_OVERRIDE = new ConcurrentHashMap<>();

    static {
        registerChargeRequirement(ChargeRequirement.RING_OF_DUELING);
        registerChargeRequirement(ChargeRequirement.GAMES_NECKLACE);
        registerChargeRequirement(ChargeRequirement.RING_OF_WEALTH);
        registerChargeRequirement(ChargeRequirement.AMULET_OF_GLORY);
        registerChargeRequirement(ChargeRequirement.COMBAT_BRACELET);
        registerChargeRequirement(ChargeRequirement.SKILLS_NECKLACE);
        registerChargeRequirement(ChargeRequirement.BURNING_AMULET);
        registerChargeRequirement(ChargeRequirement.NECKLACE_OF_PASSAGE);
        registerChargeRequirement(ChargeRequirement.SLAYER_RING);

        registerUnlockRequirement(UnlockRequirement.ARDOUGNE_CLOAK);
        registerUnlockRequirement(UnlockRequirement.EXPLORERS_RING);
        registerUnlockRequirement(UnlockRequirement.KARAMJA_GLOVES);
        registerUnlockRequirement(UnlockRequirement.FALADOR_SHIELD);
        registerUnlockRequirement(UnlockRequirement.VARROCK_ARMOUR);
        registerUnlockRequirement(UnlockRequirement.WILDERNESS_SWORD);
        registerUnlockRequirement(UnlockRequirement.MONKEY_MADNESS);
    }

    public ChargeManager() {
    }

    public static int getCharges(ChargeRequirement requirement) {
        if (requirement == null) {
            return 0;
        }
        Integer override = CHARGE_OVERRIDE.get(requirement);
        if (override != null) {
            return Math.max(0, override);
        }
        return Math.max(0, scanCharges(requirement));
    }

    public static boolean hasCharges(ChargeRequirement requirement) {
        return getCharges(requirement) > 0;
    }

    public static void setCharges(ChargeRequirement requirement, int charges) {
        if (requirement == null) {
            return;
        }
        CHARGE_REQS.add(requirement);
        CHARGE_OVERRIDE.put(requirement, Math.max(0, charges));
    }

    public static boolean isUnlocked(UnlockRequirement requirement) {
        if (requirement == null) {
            return false;
        }
        Boolean override = UNLOCK_OVERRIDE.get(requirement);
        if (override != null) {
            return override;
        }
        String questName = requirement.getQuestName();
        if (questName != null && !questName.isBlank()) {
            Quest q = Quests.find(questName);
            if (q != null) {
                return Quests.isFinished(q);
            }
        }
        return hasNamedItem(requirement.getName());
    }

    public static void setUnlocked(UnlockRequirement requirement, boolean unlocked) {
        if (requirement == null) {
            return;
        }
        UNLOCK_REQS.add(requirement);
        UNLOCK_OVERRIDE.put(requirement, unlocked);
    }

    public static void registerChargeRequirement(ChargeRequirement requirement) {
        if (requirement != null) {
            CHARGE_REQS.add(requirement);
        }
    }

    public static void registerUnlockRequirement(UnlockRequirement requirement) {
        if (requirement != null) {
            UNLOCK_REQS.add(requirement);
        }
    }

    public static void unregisterChargeRequirement(ChargeRequirement requirement) {
        if (requirement == null) {
            return;
        }
        CHARGE_REQS.remove(requirement);
        CHARGE_OVERRIDE.remove(requirement);
    }

    public static void unregisterUnlockRequirement(UnlockRequirement requirement) {
        if (requirement == null) {
            return;
        }
        UNLOCK_REQS.remove(requirement);
        UNLOCK_OVERRIDE.remove(requirement);
    }

    private static int scanCharges(ChargeRequirement requirement) {
        String prefix = requirement.getName().toLowerCase(Locale.ROOT);
        int best = 0;
        boolean found = false;
        for (IInventoryItem item : Inventory.getAll()) {
            int c = parseCharges(item != null ? item.getName() : null, prefix);
            if (c >= 0) {
                found = true;
                best = Math.max(best, c);
            }
        }
        for (IInventoryItem item : Equipment.getAll()) {
            int c = parseCharges(item != null ? item.getName() : null, prefix);
            if (c >= 0) {
                found = true;
                best = Math.max(best, c);
            }
        }
        return found ? best : 0;
    }

    /**
     * @return charge count, or -1 if the item is not this requirement
     */
    static int parseCharges(String itemName, String prefixLower) {
        if (itemName == null || prefixLower == null || prefixLower.isEmpty()) {
            return -1;
        }
        String n = itemName.trim();
        if (!n.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
            return -1;
        }
        Matcher m = CHARGE_SUFFIX.matcher(n);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean hasNamedItem(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String want = name.toLowerCase(Locale.ROOT);
        for (IInventoryItem item : Equipment.getAll()) {
            if (item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains(want)) {
                return true;
            }
        }
        for (IInventoryItem item : Inventory.getAll()) {
            if (item != null && item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains(want)) {
                return true;
            }
        }
        return false;
    }
}
