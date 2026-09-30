package net.runelite.client.plugins.lonebot;

import com.lonebot.example.giants.GiantsLocations;
import com.lonebot.example.giants.GiantsTypes;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

/**
 * Giants settings for Control-venster Skills → Giants + Settings.
 */
final class LoneBotGiantsSettingsUi {

    private LoneBotGiantsSettingsUi() {
    }

    static void fillBasis(JPanel sec, LoneBotWcImpSettingsUi.Host h, JCheckBox sharedEnable) {
        LoneBotConfig cfg = h.config();
        if (sharedEnable != null) {
            LoneBotWcImpSettingsUi.addExistingToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                    "Giants inschakelen", sharedEnable);
        }
        LoneBotWcImpSettingsUi.addEnum(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Combat style", "giantsCombatStyle",
                GiantsTypes.GiantsCombatStyle.values(), cfg.giantsCombatStyle(),
                LoneBotBootstrapPlugin::applyGiantsSettingsFromUi);
        LoneBotWcImpSettingsUi.addEnum(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Mage spell", "giantsMageSpell",
                GiantsTypes.GiantsMageSpell.values(), cfg.giantsMageSpell(),
                LoneBotBootstrapPlugin::applyGiantsSettingsFromUi);
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Eten ≤ HP %", "giantsEatPercent", cfg.giantsEatPercent(),
                5, 90, LoneBotBootstrapPlugin::applyGiantsSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Clues doen (scroll/container → solver)", "giantsClueSolver", cfg.giantsClueSolver(),
                LoneBotBootstrapPlugin::applyGiantsSettingsFromUi);

        h.addComp(sec, hint(h, "Hunt 3115,9837 r24 · shed+key of Edge trapdoor 12342"));
    }

    static void fillLoot(JPanel sec, LoneBotWcImpSettingsUi.Host h, JCheckBox overlay) {
        LoneBotConfig cfg = h.config();
        LoneBotWcImpSettingsUi.addTextField(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Loot lijst", "giantsLootItems",
                cfg.giantsLootItems() != null ? cfg.giantsLootItems() : GiantsLocations.DEFAULT_LOOT_CSV,
                LoneBotBootstrapPlugin::applyGiantsSettingsFromUi);
        if (overlay != null) {
            LoneBotWcImpSettingsUi.addExistingToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                    "Giants overlay", overlay);
        }
        h.addComp(sec, hint(h, "Defaults: Big bones, Limpwurt, runes, ore, arrows, coins"));
    }

    static void fillFood(JPanel sec, LoneBotWcImpSettingsUi.Host h) {
        LoneBotConfig cfg = h.config();
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Food-bank drempel", "giantsFoodBankThreshold", cfg.giantsFoodBankThreshold(),
                1, 20, LoneBotBootstrapPlugin::applyGiantsSettingsFromUi);
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Food uit bank", "giantsFoodAmount", cfg.giantsFoodAmount(),
                1, 28, LoneBotBootstrapPlugin::applyGiantsSettingsFromUi);
        h.addComp(sec, hint(h, "Eigen food-bank: geen food → bank, los van Monk/global"));
    }

    private static javax.swing.JLabel hint(LoneBotWcImpSettingsUi.Host h, String text) {
        javax.swing.JLabel l = new javax.swing.JLabel("<html><i>" + text + "</i></html>");
        h.styleLabel(l);
        return l;
    }
}
