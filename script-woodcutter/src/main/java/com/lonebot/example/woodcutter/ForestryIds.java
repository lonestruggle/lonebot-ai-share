package com.lonebot.example.woodcutter;

import java.util.Locale;

final class ForestryIds {

    static final String FORESTRY_KIT_NAME = "Forestry kit";
    /** Huidige OSRS item-id (wiki). */
    static final int FORESTRY_KIT_ITEM_ID = 28136;
    /** Oude id — nog accepteren voor detectie/shop-fallback. */
    static final int FORESTRY_KIT_ITEM_ID_LEGACY = 27506;
    /** Anima-infused bark (stackable currency). */
    static final int ANIMA_BARK_ITEM_ID = 28134;
    /**
     * ItemContainer id van de forestry kit (RuneLite {@code InventoryID.FORESTRY_KIT}).
     * Client houdt bark hier bij zolang je een kit hebt — View openen is niet nodig om te lezen.
     */
    static final int FORESTRY_KIT_CONTAINER_ID = 814;

    static final int RISING_ROOTS = 47482;
    /** Groene ader / anima-infused — bij voorkeur hakken (meer bark). */
    static final int RISING_ROOTS_SPECIAL = 47483;
    /** Beide spawn-types: bruin eerst (~15–21s), daarna groen. */
    static final int[] RISING_ROOTS_ANY_IDS = {RISING_ROOTS, RISING_ROOTS_SPECIAL};

    static boolean isRisingRootId(int id) {
        return id == RISING_ROOTS || id == RISING_ROOTS_SPECIAL;
    }

    static boolean isRisingRootSpecial(int id) {
        return id == RISING_ROOTS_SPECIAL;
    }

    static final int[] SAPLING_OBJECT_IDS = {
            47484, 47485, 47486, 47487, 47488, 47489,
            47490, 47491, 47492, 50699, 50700, 50701
    };

    /**
     * Mulch-piles (world objects). Zelfde set als CombatBot.
     * Bekende namen uit live logs: 47494 Green leaves, 47495 Droppings, 47496 Wild mushrooms;
     * overige: Rotting leaves / Splintered bark / … (scene-naam).
     */
    static final int[] SAPLING_INGREDIENT_IDS = {
            47493, 47494, 47495, 47496, 47497, 47498, 47499
    };
    static final int PILE_GREEN_LEAVES = 47494;
    static final int PILE_DROPPINGS = 47495;
    static final int PILE_WILD_MUSHROOMS = 47496;

    /**
     * Tas-mulch (live confirm): 1 collect → 28648, 2 → 28649, 3 packed → 28650.
     * Pas Add-mulch bij packed. Oude 1-stuks id: 28183.
     */
    static final int MULCH_INV_1 = 28648;
    static final int MULCH_INV_1_LEGACY = 28183;
    static final int MULCH_INV_2 = 28649;
    static final int MULCH_INV_PACKED = 28650;

    static final int ENTLING_NPC = 12543;
    static final int FRIENDLY_FORESTER_NPC_ID = 11427;

    /** Friendly Forester shop (hover capture). */
    static final int FRIENDLY_FORESTER_SHOP_IFACE = 819;
    static final int FRIENDLY_FORESTER_SHOP_TITLE_CHILD = 3;
    static final int FRIENDLY_FORESTER_SHOP_KIT_CHILD = 34;
    static final int FRIENDLY_FORESTER_SHOP_SELECTED_KIT_CHILD = 31;
    static final int FRIENDLY_FORESTER_SHOP_BUY1_CHILD = 22;

    static final int[] FORESTRY_EVENT_ITEM_IDS = {
            28183, 28648, 28649, 28650, 28192, 28193, 28644, 28646
    };

    private ForestryIds() {
    }

    static boolean isForestryKitItemId(int id) {
        return id == FORESTRY_KIT_ITEM_ID || id == FORESTRY_KIT_ITEM_ID_LEGACY;
    }

    static boolean isSaplingObject(int id) {
        for (int sapId : SAPLING_OBJECT_IDS) {
            if (sapId == id) {
                return true;
            }
        }
        return false;
    }

    static boolean isSaplingIngredient(int id) {
        for (int ingId : SAPLING_INGREDIENT_IDS) {
            if (ingId == id) {
                return true;
            }
        }
        return false;
    }

    /** 0 = geen, 1 = één pile, 2 = twee, 3 = packed (inleveren). */
    static int mulchInvStage(int itemId) {
        if (itemId == MULCH_INV_PACKED) {
            return 3;
        }
        if (itemId == MULCH_INV_2) {
            return 2;
        }
        if (itemId == MULCH_INV_1 || itemId == MULCH_INV_1_LEGACY) {
            return 1;
        }
        return 0;
    }

    static boolean isForestryLeftover(int itemId, String name) {
        for (int id : FORESTRY_EVENT_ITEM_IDS) {
            if (id == itemId) {
                return true;
            }
        }
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("forestry kit") || lower.contains("anima-infused bark")) {
            return false;
        }
        return lower.equals("mulch") || lower.startsWith("mulch (") || lower.equals("packed mulch")
                || lower.equals("droppings") || lower.equals("green leaves")
                || lower.equals("rotting leaves") || lower.equals("splintered bark")
                || lower.equals("wild mushrooms") || lower.equals("strange pollen")
                || lower.equals("unfired cup") || lower.equals("smoker fuel")
                || lower.equals("smoker canister");
    }
}
