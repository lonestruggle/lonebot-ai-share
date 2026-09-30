package net.storm.sdk.game;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Thin guide-price stub for GE estimates (no live wiki).
 */
public final class Prices {

    private static final Map<String, Integer> BY_NAME = new HashMap<>();

    static {
        put("Black bead", 800);
        put("Red bead", 800);
        put("Yellow bead", 800);
        put("White bead", 800);
        put("Mind talisman", 1500);
        put("Fiendish ashes", 400);
        put("Mind rune", 3);
        put("Air rune", 4);
        put("Fire rune", 4);
        put("Water rune", 4);
        put("Earth rune", 4);
        put("Chaos rune", 50);
        put("Law rune", 120);
        put("Bronze arrow", 2);
        put("Iron arrow", 5);
        put("Steel arrow", 15);
        put("Mithril arrow", 30);
        put("Adamant arrow", 55);
        put("Coins", 1);
        // Quest GE floors (wiki high + buffer — keep in sync with QuestGePrices)
        put("Egg", 50);
        put("Pot of flour", 200);
        put("Bucket of milk", 60);
        put("Clay", 120);
        put("Copper ore", 50);
        put("Iron ore", 150);
        put("Blue dye", 500);
        put("Orange dye", 1_200);
        put("Goblin mail", 400);
        put("Cadava berries", 150);
        put("Hammer", 150);
    }

    private Prices() {
    }

    private static void put(String name, int gp) {
        BY_NAME.put(name.toLowerCase(Locale.ROOT), gp);
    }

    public static int getItemPrice(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        Integer v = BY_NAME.get(name.toLowerCase(Locale.ROOT));
        return v != null ? v : 100;
    }

    public static int getItemPrice(int itemId) {
        // No id table — callers should prefer name
        return 50;
    }
}
