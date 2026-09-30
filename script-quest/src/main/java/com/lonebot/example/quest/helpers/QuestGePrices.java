package com.lonebot.example.quest.helpers;

/**
 * Minimale GE-bodprijs per quest-item (floor voor {@link QuestBankGePrep.Need#gePriceHint}).
 * Gebaseerd op OSRS Wiki realtime high (sep 2026) + buffer zodat offers sneller fillen.
 * {@link net.storm.sdk.items.GeRestockHelper} neemt {@code max(activelyTraded×premium, hint)}.
 */
public final class QuestGePrices {

    // Cook's Assistant — wiki high ≈ Egg 27 / Flour 108 / Milk 37
    public static final int EGG = 50;
    public static final int POT_OF_FLOUR = 200;
    public static final int BUCKET_OF_MILK = 60;

    // Doric's — wiki high ≈ Clay 77 / Copper 12 / Iron 72
    public static final int CLAY = 120;
    public static final int COPPER_ORE = 50;
    public static final int IRON_ORE = 150;

    // Goblin Diplomacy — wiki high ≈ Blue 385 / Orange 898 / Mail ~155–200
    public static final int BLUE_DYE = 500;
    public static final int ORANGE_DYE = 1_200;
    public static final int GOBLIN_MAIL = 400;

    // Romeo & Juliet — wiki high ≈ Cadava 99
    public static final int CADAVA_BERRIES = 150;

    // Vampire Slayer — wiki high ≈ Hammer 110
    public static final int HAMMER = 150;

    private QuestGePrices() {
    }
}
