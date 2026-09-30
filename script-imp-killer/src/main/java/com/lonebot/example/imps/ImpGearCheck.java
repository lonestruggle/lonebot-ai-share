package com.lonebot.example.imps;

import net.runelite.api.Skill;
import net.storm.api.domain.items.IInventoryItem;
import net.storm.sdk.combat.RangedAmmoKit;
import net.storm.sdk.game.Skills;
import net.storm.sdk.items.Bank;
import net.storm.sdk.items.Equipment;
import net.storm.sdk.items.Inventory;
import net.storm.sdk.magic.Magic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CombatBot-parity gear/rune/level gates voor Imp Killer.
 * <p>
 * Scheiding (zoals CombatBot):
 * <ul>
 *   <li>{@link Snapshot#adequateSupplies} — trip-setup op zak (inv+equip): genoeg casts/arrows/wapen</li>
 *   <li>{@link Snapshot#combatReady} — staff/wapen <b>equipped</b> + ≥1 cast / ammo + canCast</li>
 *   <li>{@link Snapshot#readyToHunt} — adequate + combatReady + coins ≥ min (mag boot naar Karamja)</li>
 * </ul>
 */
public final class ImpGearCheck {

    public static final int BOAT_FARE = 30;
    /** Min. casts vóór vertrek (CombatBot clamp 50–100). */
    public static final int MIN_CASTS_DEPART = 50;
    /** Mind rune floor (CombatBot: 100 effectief). */
    public static final int MIND_TRIP_FLOOR = 100;
    public static final int MIN_ARROWS = 100;
    public static final int CATALYST_WITHDRAW = 200;
    public static final int AIR_WITHDRAW = 400;
    public static final int ARROW_WITHDRAW = 200;

    private ImpGearCheck() {
    }

    public static final class Snapshot {
        public boolean adequateSupplies;
        public boolean combatReady;
        public boolean readyToHunt;
        public String reason = "niet geëvalueerd";
        public ImpsTypes.ImpsMageSpell activeSpell;

        public int coinCount;
        public int needCoins;

        public String meleeWeapon;
        public boolean needEquipMelee;

        public String bowName;
        public String arrowName;
        public int arrowCount;
        public int equippedAmmo;
        public int needArrows;
        public boolean needEquipBow;
        public boolean needEquipArrows;

        public String staffName;
        public boolean staffEquipped;
        public boolean needEquipStaff;
        public boolean preferFireStaff;

        public int catalystHave;
        public int catalystNeed; // withdraw amount target gap
        public int elementalHave;
        public int elementalNeed;
        public int airHave;
        public int airNeed;
        public int usableCasts;
        public int minCastsWanted;
    }

    public static Snapshot evaluate(
            ImpsTypes.ImpsCombatStyle style,
            ImpsTypes.ImpsMageSpell configuredSpell,
            boolean magicAutoUpdate,
            int minCoins
    ) {
        Snapshot s = new Snapshot();
        int coinsWanted = Math.max(BOAT_FARE, Math.max(0, minCoins));
        s.coinCount = countPerson("Coins");
        s.needCoins = Math.max(0, coinsWanted - s.coinCount);

        if (style == ImpsTypes.ImpsCombatStyle.RANGED) {
            evalRanged(s);
        } else if (style == ImpsTypes.ImpsCombatStyle.MAGE) {
            evalMage(s, configuredSpell, magicAutoUpdate);
        } else {
            evalMelee(s);
        }

        s.readyToHunt = s.adequateSupplies && s.combatReady && s.needCoins <= 0;
        if (s.readyToHunt) {
            s.reason = "ok";
        } else if (s.combatReady && s.adequateSupplies && s.needCoins > 0) {
            s.reason = "coins " + s.coinCount + "/" + coinsWanted;
        }
        return s;
    }

    // ---- MELEE ----

    private static void evalMelee(Snapshot s) {
        s.meleeWeapon = firstUsableMeleeOnPerson();
        if (s.meleeWeapon == null) {
            s.reason = "geen wieldbaar melee (Attack " + Skills.getLevel(Skill.ATTACK) + ")";
            s.adequateSupplies = false;
            s.combatReady = false;
            return;
        }
        s.needEquipMelee = !Equipment.contains(i -> i != null && i.getName() != null
                && i.getName().equalsIgnoreCase(s.meleeWeapon));
        s.adequateSupplies = true;
        s.combatReady = !s.needEquipMelee;
        if (s.needEquipMelee) {
            s.reason = "equip " + s.meleeWeapon;
        }
    }

    // ---- RANGED ----

    private static void evalRanged(Snapshot s) {
        int rng = Skills.getLevel(Skill.RANGED);
        s.bowName = firstBowOnPerson();
        s.arrowName = firstArrowNameOnPerson();
        s.equippedAmmo = RangedAmmoKit.getEquippedRangedAmmoQuantity();
        s.arrowCount = RangedAmmoKit.getTotalUsableRangedAmmoCount(rng);
        if (s.bowName == null) {
            s.reason = "geen bow";
            s.adequateSupplies = false;
            s.combatReady = false;
            return;
        }
        if (s.arrowCount < 1) {
            s.reason = "geen arrows";
            s.adequateSupplies = false;
            s.combatReady = false;
            return;
        }
        s.needArrows = Math.max(0, MIN_ARROWS - s.arrowCount);
        s.needEquipBow = !Equipment.contains(i -> i != null && i.getName() != null
                && i.getName().equalsIgnoreCase(s.bowName));
        s.needEquipArrows = RangedAmmoKit.quiverNeedsRefillFromInventory(0L)
                && RangedAmmoKit.inventoryHasUsableRangedAmmo(rng);
        s.adequateSupplies = s.arrowCount >= MIN_ARROWS;
        // Trip-klaar: bow + ammo ergens. Attack-gate (quiver) zit in enforceRangedAmmoOrDelay.
        s.combatReady = !s.needEquipBow && s.arrowCount >= 1;
        if (s.needEquipBow) {
            s.reason = "equip " + s.bowName;
        } else if (s.needEquipArrows) {
            s.reason = "equip arrows (quiver leeg, " + s.arrowCount + " in inv)";
        } else if (s.equippedAmmo < 1) {
            s.reason = "quiver leeg";
            s.combatReady = false;
        } else if (!s.adequateSupplies) {
            s.reason = "arrows " + s.arrowCount + "/" + MIN_ARROWS;
        }
    }

    // ---- MAGE ----

    private static void evalMage(Snapshot s, ImpsTypes.ImpsMageSpell configured, boolean magicAutoUpdate) {
        ImpsTypes.ImpsMageSpell spell = resolveSpell(configured, magicAutoUpdate);
        s.activeSpell = spell;
        int mag = Skills.getLevel(Skill.MAGIC);
        s.minCastsWanted = MIN_CASTS_DEPART;
        s.preferFireStaff = isAutoFire(magicAutoUpdate, mag)
                || spell == ImpsTypes.ImpsMageSpell.FIRE_STRIKE;

        if (mag < spell.getLevelReq()) {
            s.reason = "Magic " + mag + " < " + spell.getLevelReq() + " (" + spell.getSpellName() + ")";
            s.adequateSupplies = false;
            s.combatReady = false;
            return;
        }

        s.staffName = findBestStaff(spell, s.preferFireStaff);
        s.staffEquipped = hasAnyStaffEquipped();
        // Preferred staff in inv but not equipped
        if (s.staffName != null && Inventory.contains(s.staffName) && !Equipment.contains(s.staffName)) {
            s.needEquipStaff = true;
        } else if (!s.staffEquipped && s.staffName != null && Inventory.contains(s.staffName)) {
            s.needEquipStaff = true;
        } else {
            s.needEquipStaff = !s.staffEquipped && Inventory.contains(i -> i != null && i.getName() != null
                    && i.getName().toLowerCase(Locale.ROOT).contains("staff"));
        }

        boolean hasStaffSomewhere = s.staffName != null || hasAnyStaffOnPerson();
        if (s.preferFireStaff && !hasStaffWithElement("fire")) {
            s.reason = "Magic auto-update: Staff of fire verplicht";
            s.adequateSupplies = false;
            s.combatReady = false;
            // still compute rune needs for withdraw UI
            fillMageRuneNeeds(s, spell, s.preferFireStaff);
            return;
        }
        if (!hasStaffSomewhere) {
            s.reason = "geen staff (wil " + spell.getPreferredStaff() + ")";
            s.adequateSupplies = false;
            s.combatReady = false;
            fillMageRuneNeeds(s, spell, s.preferFireStaff);
            return;
        }

        fillMageRuneNeeds(s, spell, s.preferFireStaff);

        boolean oneCast = hasRunesForCasts(spell, s.preferFireStaff, 1);
        boolean tripCasts = s.usableCasts >= s.minCastsWanted;
        boolean catalystTripOk = !isBelowCatalystTripMinimum(s.catalystHave, spell);
        boolean elemTripOk = elementalCovered(spell) || s.elementalHave >= minElementalForTrip(spell);
        boolean airTripOk = !spell.needsAirRune() || airStaffCovers(spell, s.preferFireStaff)
                || s.usableCasts >= s.minCastsWanted;

        s.adequateSupplies = hasStaffSomewhere && catalystTripOk && elemTripOk && airTripOk && tripCasts;
        boolean canCast;
        try {
            canCast = Magic.canCast(spell.getStandardSpell());
        } catch (Throwable t) {
            canCast = oneCast;
        }
        s.combatReady = s.staffEquipped && oneCast && canCast;

        if (!s.staffEquipped) {
            s.reason = "equip staff (" + (s.staffName != null ? s.staffName : "any") + ")";
        } else if (!oneCast) {
            s.reason = "runes < 1 cast: " + spell.getCatalystRune() + "=" + s.catalystHave
                    + " elem=" + s.elementalHave + " air=" + s.airHave;
        } else if (!canCast) {
            s.reason = "canCast false (" + spell.getSpellName() + ")";
        } else if (!s.adequateSupplies) {
            s.reason = "trip tekort casts=" + s.usableCasts + "/" + s.minCastsWanted
                    + " catNeed=" + s.catalystNeed + " airNeed=" + s.airNeed
                    + " elemNeed=" + s.elementalNeed;
        }
    }

    private static void fillMageRuneNeeds(Snapshot s, ImpsTypes.ImpsMageSpell spell, boolean preferFire) {
        int catPer = catalystPerCast(spell);
        s.catalystHave = countPerson(spell.getCatalystRune());
        s.elementalHave = countPerson(spell.getElementalRune());
        s.airHave = countPerson("Air rune");
        s.usableCasts = usableCasts(spell, preferFire);

        int catFloor = effectiveCatalystTripMinimum(spell);
        s.catalystNeed = Math.max(0, catFloor - s.catalystHave);

        if (elementalCovered(spell)) {
            s.elementalNeed = 0;
        } else {
            s.elementalNeed = Math.max(0, minElementalForTrip(spell) - s.elementalHave);
        }

        if (!spell.needsAirRune() || airStaffCovers(spell, preferFire) || s.usableCasts >= MIN_CASTS_DEPART) {
            s.airNeed = 0;
        } else {
            int airPer = Math.max(1, airPerCast(spell));
            int targetAir = MIN_CASTS_DEPART * airPer;
            s.airNeed = Math.max(0, targetAir - s.airHave);
        }

        if (s.usableCasts < MIN_CASTS_DEPART) {
            int catNeedCasts = Math.max(0, MIN_CASTS_DEPART - (s.catalystHave / Math.max(1, catPer)));
            s.catalystNeed = Math.max(s.catalystNeed, catNeedCasts * catPer);
        }
    }

    public static ImpsTypes.ImpsMageSpell resolveSpell(
            ImpsTypes.ImpsMageSpell configured,
            boolean magicAutoUpdate
    ) {
        int mag = Skills.getLevel(Skill.MAGIC);
        // CombatBot: auto-update OF expliciet Fire Strike + level → Fire Strike
        if (isAutoFire(magicAutoUpdate, mag)
                || (configured == ImpsTypes.ImpsMageSpell.FIRE_STRIKE
                && mag >= ImpsTypes.ImpsMageSpell.FIRE_STRIKE.getLevelReq())) {
            return ImpsTypes.ImpsMageSpell.FIRE_STRIKE;
        }
        ImpsTypes.ImpsMageSpell want = configured != null ? configured : ImpsTypes.ImpsMageSpell.WIND_STRIKE;
        if (mag >= want.getLevelReq()) {
            return want;
        }
        if (want == ImpsTypes.ImpsMageSpell.FIRE_STRIKE) {
            return ImpsTypes.ImpsMageSpell.WIND_STRIKE;
        }
        ImpsTypes.ImpsMageSpell best = ImpsTypes.ImpsMageSpell.WIND_STRIKE;
        for (ImpsTypes.ImpsMageSpell sp : ImpsTypes.ImpsMageSpell.values()) {
            if (mag >= sp.getLevelReq() && sp.getLevelReq() >= best.getLevelReq()) {
                best = sp;
            }
        }
        return best;
    }

    public static boolean isAutoFire(boolean magicAutoUpdate, int magicLevel) {
        return magicAutoUpdate && magicLevel >= ImpsTypes.ImpsMageSpell.FIRE_STRIKE.getLevelReq();
    }

    public static int catalystPerCast(ImpsTypes.ImpsMageSpell spell) {
        if (spell == null) {
            return 1;
        }
        return spell.getCatalystRune().toLowerCase(Locale.ROOT).contains("chaos") ? 2 : 1;
    }

    public static int elementalPerCast(ImpsTypes.ImpsMageSpell spell) {
        if (spell == null) {
            return 1;
        }
        switch (spell) {
            case FIRE_STRIKE:
            case FIRE_BOLT:
                return 3;
            case WIND_BOLT:
            case WATER_BOLT:
            case EARTH_BOLT:
                return 2;
            default:
                return 1;
        }
    }

    public static int airPerCast(ImpsTypes.ImpsMageSpell spell) {
        if (spell == null || !spell.needsAirRune()) {
            // Wind Strike: air = elemental, counted separately
            return 0;
        }
        return 2;
    }

    public static int effectiveCatalystTripMinimum(ImpsTypes.ImpsMageSpell spell) {
        if (spell != null && spell.getCatalystRune().equalsIgnoreCase("Mind rune")) {
            return MIND_TRIP_FLOOR + catalystPerCast(spell);
        }
        return Math.max(50, MIN_CASTS_DEPART * catalystPerCast(spell));
    }

    public static boolean isBelowCatalystTripMinimum(int have, ImpsTypes.ImpsMageSpell spell) {
        int min = effectiveCatalystTripMinimum(spell);
        int buf = catalystPerCast(spell);
        return have + buf < min;
    }

    public static int minElementalForTrip(ImpsTypes.ImpsMageSpell spell) {
        if (spell == null || elementalCovered(spell)) {
            return 0;
        }
        return Math.max(50, MIN_CASTS_DEPART * elementalPerCast(spell));
    }

    public static boolean elementalCovered(ImpsTypes.ImpsMageSpell spell) {
        if (spell == null) {
            return false;
        }
        String el = spell.getElementalRune().toLowerCase(Locale.ROOT).replace(" rune", "").trim();
        return hasStaffWithElement(el);
    }

    public static boolean airStaffCovers(ImpsTypes.ImpsMageSpell spell, boolean preferFireStaff) {
        if (preferFireStaff) {
            return false; // Auto-Fire: air staff telt niet
        }
        return hasStaffWithElement("air");
    }

    public static int usableCasts(ImpsTypes.ImpsMageSpell spell, boolean preferFireStaff) {
        if (spell == null) {
            return 0;
        }
        int perCat = Math.max(1, catalystPerCast(spell));
        int fromCat = countPerson(spell.getCatalystRune()) / perCat;
        if (!elementalCovered(spell)) {
            int perEl = Math.max(1, elementalPerCast(spell));
            fromCat = Math.min(fromCat, countPerson(spell.getElementalRune()) / perEl);
        }
        if (spell.needsAirRune()) {
            if (!airStaffCovers(spell, preferFireStaff)) {
                int perAir = Math.max(1, airPerCast(spell));
                fromCat = Math.min(fromCat, countPerson("Air rune") / perAir);
            }
        }
        return fromCat;
    }

    public static boolean hasRunesForCasts(ImpsTypes.ImpsMageSpell spell, boolean preferFireStaff, int casts) {
        return usableCasts(spell, preferFireStaff) >= casts;
    }

    // ---- melee tiers ----

    private static final String[] MELEE_PRIORITY = {
            "Rune scimitar", "Rune sword", "Rune longsword", "Rune battleaxe", "Rune mace", "Rune dagger",
            "Adamant scimitar", "Adamant sword", "Adamant longsword", "Adamant battleaxe", "Adamant mace", "Adamant dagger",
            "Mithril scimitar", "Mithril sword", "Mithril longsword", "Mithril battleaxe", "Mithril mace", "Mithril dagger",
            "Black scimitar", "Black sword", "Black longsword", "Black battleaxe", "Black mace", "Black dagger",
            "Steel scimitar", "Steel sword", "Steel longsword", "Steel battleaxe", "Steel mace", "Steel dagger",
            "Iron scimitar", "Iron sword", "Iron longsword", "Iron battleaxe", "Iron mace", "Iron dagger",
            "Bronze scimitar", "Bronze sword", "Bronze longsword", "Bronze battleaxe", "Bronze mace", "Bronze dagger"
    };

    public static int requiredAttackLevel(String weaponName) {
        if (weaponName == null) {
            return 1;
        }
        String n = weaponName.toLowerCase(Locale.ROOT);
        if (n.contains("rune")) {
            return 40;
        }
        if (n.contains("adamant")) {
            return 30;
        }
        if (n.contains("mithril")) {
            return 20;
        }
        if (n.contains("black")) {
            return 10;
        }
        if (n.contains("steel")) {
            return 5;
        }
        return 1;
    }

    public static boolean canWieldMelee(String weaponName) {
        return Skills.getLevel(Skill.ATTACK) >= requiredAttackLevel(weaponName);
    }

    public static String firstUsableMeleeOnPerson() {
        for (String w : MELEE_PRIORITY) {
            if (!canWieldMelee(w)) {
                continue;
            }
            String found = com.lonebot.example.gear.GearItemNames.findBestNameOnPerson(w);
            if (found != null) {
                return found;
            }
        }
        for (IInventoryItem i : Equipment.getAll()) {
            if (i != null && i.getName() != null && isMeleeWeaponName(i.getName()) && canWieldMelee(i.getName())) {
                return i.getName();
            }
        }
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && i.getName() != null && isMeleeWeaponName(i.getName()) && canWieldMelee(i.getName())) {
                return i.getName();
            }
        }
        return null;
    }

    public static String firstUsableMeleeInBank() {
        if (!Bank.isOpen()) {
            return null;
        }
        for (String w : MELEE_PRIORITY) {
            if (!canWieldMelee(w)) {
                continue;
            }
            String found = com.lonebot.example.gear.GearItemNames.findInBank(w);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static boolean isMeleeWeaponName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("pickaxe") || lower.contains("hatchet")) {
            return false;
        }
        if (lower.contains("battleaxe")) {
            return true;
        }
        if (lower.contains("axe")) {
            return false;
        }
        return lower.contains("scimitar") || lower.contains("sword") || lower.contains("dagger")
                || lower.contains("mace") || lower.contains("warhammer") || lower.contains("longsword")
                || lower.contains("halberd");
    }

    // ---- ranged ----

    private static final String[] BOW_PRIORITY = {
            "Magic shortbow", "Yew shortbow", "Maple shortbow", "Willow shortbow", "Oak shortbow", "Shortbow",
            "Magic longbow", "Yew longbow", "Maple longbow", "Willow longbow", "Oak longbow", "Longbow"
    };
    private static final String[] ARROW_PRIORITY = {
            "Rune arrow", "Adamant arrow", "Mithril arrow", "Steel arrow", "Iron arrow", "Bronze arrow"
    };

    /** OSRS F2P bow Ranged req (beste dat je mag = first match in priority + canUse). */
    public static int requiredRangedForBow(String bowName) {
        if (bowName == null) {
            return 1;
        }
        String n = bowName.toLowerCase(Locale.ROOT);
        if (n.contains("magic")) {
            return 50; // members; skip via canUse if F2P low
        }
        if (n.contains("yew")) {
            return 40;
        }
        if (n.contains("maple")) {
            return 30;
        }
        if (n.contains("willow")) {
            return 20;
        }
        if (n.contains("oak")) {
            return 5;
        }
        return 1; // Shortbow / Longbow
    }

    public static int requiredRangedForArrow(String arrowName) {
        if (arrowName == null) {
            return 1;
        }
        String n = arrowName.toLowerCase(Locale.ROOT);
        if (n.contains("rune")) {
            return 40;
        }
        if (n.contains("adamant")) {
            return 30;
        }
        if (n.contains("mithril")) {
            return 20;
        }
        if (n.contains("steel")) {
            return 5;
        }
        return 1;
    }

    public static boolean canUseBow(String bowName) {
        return Skills.getLevel(Skill.RANGED) >= requiredRangedForBow(bowName);
    }

    public static boolean canUseArrow(String arrowName) {
        return Skills.getLevel(Skill.RANGED) >= requiredRangedForArrow(arrowName);
    }

    public static String firstBowOnPerson() {
        for (String b : BOW_PRIORITY) {
            if (!canUseBow(b)) {
                continue;
            }
            if (Equipment.contains(b) || Inventory.contains(b)) {
                return b;
            }
        }
        for (IInventoryItem i : Equipment.getAll()) {
            if (i != null && i.getName() != null && i.getName().toLowerCase(Locale.ROOT).contains("bow")
                    && canUseBow(i.getName())) {
                return i.getName();
            }
        }
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && i.getName() != null && i.getName().toLowerCase(Locale.ROOT).contains("bow")
                    && canUseBow(i.getName())) {
                return i.getName();
            }
        }
        return null;
    }

    public static String firstBowInBank() {
        if (!Bank.isOpen()) {
            return null;
        }
        for (String b : BOW_PRIORITY) {
            if (canUseBow(b) && Bank.contains(b)) {
                return b;
            }
        }
        return null;
    }

    public static String firstArrowNameOnPerson() {
        for (String a : ARROW_PRIORITY) {
            if (!canUseArrow(a)) {
                continue;
            }
            if (countPerson(a) > 0) {
                return a;
            }
        }
        return null;
    }

    public static String firstArrowInBank() {
        if (!Bank.isOpen()) {
            return null;
        }
        for (String a : ARROW_PRIORITY) {
            if (canUseArrow(a) && Bank.contains(a)) {
                return a;
            }
        }
        return null;
    }

    // ---- staff ----

    public static String findBestStaff(ImpsTypes.ImpsMageSpell spell, boolean preferFire) {
        if (preferFire) {
            String fire = firstMatchingStaffOnPerson("fire");
            if (fire != null) {
                return fire;
            }
        }
        if (spell != null && spell.getPreferredStaff() != null) {
            if (Equipment.contains(spell.getPreferredStaff()) || Inventory.contains(spell.getPreferredStaff())) {
                return spell.getPreferredStaff();
            }
        }
        String el = elementKey(spell);
        if (el != null) {
            String match = firstMatchingStaffOnPerson(el);
            if (match != null) {
                return match;
            }
        }
        return firstAnyStaffOnPerson();
    }

    public static String findStaffInBank(ImpsTypes.ImpsMageSpell spell, boolean preferFire) {
        if (!Bank.isOpen()) {
            return null;
        }
        List<String> order = new ArrayList<>();
        if (preferFire) {
            order.add("Staff of fire");
            order.add("Mystic fire staff");
            order.add("Lava battlestaff");
            order.add("Fire battlestaff");
        }
        if (spell != null && spell.getPreferredStaff() != null) {
            order.add(spell.getPreferredStaff());
        }
        order.add("Staff of air");
        order.add("Staff of water");
        order.add("Staff of earth");
        order.add("Staff of fire");
        order.add("Mystic air staff");
        order.add("Mystic water staff");
        order.add("Mystic earth staff");
        order.add("Mystic fire staff");
        for (String n : order) {
            if (Bank.contains(n)) {
                return n;
            }
        }
        return null;
    }

    private static String elementKey(ImpsTypes.ImpsMageSpell spell) {
        if (spell == null) {
            return null;
        }
        String e = spell.getElementalRune().toLowerCase(Locale.ROOT);
        if (e.contains("air")) {
            return "air";
        }
        if (e.contains("water")) {
            return "water";
        }
        if (e.contains("earth")) {
            return "earth";
        }
        if (e.contains("fire")) {
            return "fire";
        }
        return null;
    }

    public static boolean hasStaffWithElement(String element) {
        if (element == null || element.isEmpty()) {
            return false;
        }
        String el = element.toLowerCase(Locale.ROOT);
        return Equipment.contains(i -> i != null && i.getName() != null
                && i.getName().toLowerCase(Locale.ROOT).contains("staff")
                && i.getName().toLowerCase(Locale.ROOT).contains(el))
                || Inventory.contains(i -> i != null && i.getName() != null
                && i.getName().toLowerCase(Locale.ROOT).contains("staff")
                && i.getName().toLowerCase(Locale.ROOT).contains(el));
    }

    public static boolean hasAnyStaffEquipped() {
        return Equipment.contains(i -> i != null && i.getName() != null
                && i.getName().toLowerCase(Locale.ROOT).contains("staff"));
    }

    public static boolean hasAnyStaffOnPerson() {
        return hasAnyStaffEquipped()
                || Inventory.contains(i -> i != null && i.getName() != null
                && i.getName().toLowerCase(Locale.ROOT).contains("staff"));
    }

    private static String firstMatchingStaffOnPerson(String element) {
        String el = element.toLowerCase(Locale.ROOT);
        for (IInventoryItem i : Equipment.getAll()) {
            if (i != null && i.getName() != null) {
                String n = i.getName().toLowerCase(Locale.ROOT);
                if (n.contains("staff") && n.contains(el)) {
                    return i.getName();
                }
            }
        }
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && i.getName() != null) {
                String n = i.getName().toLowerCase(Locale.ROOT);
                if (n.contains("staff") && n.contains(el)) {
                    return i.getName();
                }
            }
        }
        return null;
    }

    private static String firstAnyStaffOnPerson() {
        for (IInventoryItem i : Equipment.getAll()) {
            if (i != null && i.getName() != null && i.getName().toLowerCase(Locale.ROOT).contains("staff")) {
                return i.getName();
            }
        }
        for (IInventoryItem i : Inventory.getAll()) {
            if (i != null && i.getName() != null && i.getName().toLowerCase(Locale.ROOT).contains("staff")) {
                return i.getName();
            }
        }
        return null;
    }

    /** Inv + equipment quantity (stacked). */
    public static int countPerson(String name) {
        if (name == null) {
            return 0;
        }
        int n = 0;
        try {
            n += Inventory.getCount(true, name);
        } catch (Throwable ignored) {
        }
        try {
            n += Equipment.getCount(true, name);
        } catch (Throwable ignored) {
        }
        return n;
    }

    public static String statusLine(Snapshot s) {
        if (s == null) {
            return "gear:?";
        }
        String spell = s.activeSpell != null ? s.activeSpell.getSpellName() : "-";
        return (s.readyToHunt ? "READY" : "WAIT")
                + " combat=" + s.combatReady
                + " trip=" + s.adequateSupplies
                + " coins=" + s.coinCount
                + " quiver=" + s.equippedAmmo
                + " spell=" + spell
                + " casts=" + s.usableCasts
                + " | " + s.reason;
    }
}
