package net.runelite.client.plugins.lonebot;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

/**
 * Star Miner settings for Control-venster Skills → Star.
 */
final class LoneBotStarSettingsUi {

    private LoneBotStarSettingsUi() {
    }

    static void fillBasis(JPanel sec, LoneBotWcImpSettingsUi.Host h, JCheckBox sharedOverlay) {
        LoneBotConfig cfg = h.config();
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Geen wilderness", "starMinerAvoidWilderness", cfg.starMinerAvoidWilderness(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Alleen F2P (lijst + hop)", "starMinerF2pOnly", cfg.starMinerF2pOnly(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "World hop", "starMinerHopEnabled", cfg.starMinerHopEnabled(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Min tier (skip T1…)", "starMinerMinTier", cfg.starMinerMinTier(),
                1, 9, LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Wacht extra lagen", "starMinerWaitTiersAbove", cfg.starMinerWaitTiersAbove(),
                0, 8, LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addEnum(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Tijdens wachten", "starMinerWaitActivity",
                LoneBotConfig.StarWaitActivity.values(), cfg.starMinerWaitActivity(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addEnum(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Travel-mode (test)", "starMinerTravelMode",
                LoneBotConfig.StarTravelModeOpt.values(), cfg.starMinerTravelMode(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Travel via WorldWalker", "starMinerWorldWalkerTravel", cfg.starMinerWorldWalkerTravel(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Teleports (F2P)", "starMinerTeleportsEnabled", cfg.starMinerTeleportsEnabled(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Gems banken", "starMinerBankGemsEnabled", cfg.starMinerBankGemsEnabled(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        LoneBotWcImpSettingsUi.addToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Clues doen (geode/scroll → solver)", "starClueSolver", cfg.starClueSolver(),
                LoneBotBootstrapPlugin::applyStarSettingsFromUi);

        LoneBotWcImpSettingsUi.addSlider(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                "Gems banken vanaf", "starMinerBankGemsAt", cfg.starMinerBankGemsAt(),
                1, 28, LoneBotBootstrapPlugin::applyStarSettingsFromUi);
        if (sharedOverlay != null) {
            LoneBotWcImpSettingsUi.addExistingToggle(sec, h, LoneBotWcImpSettingsUi.Wire.LIVE,
                    "Star overlay", sharedOverlay);
        }
        h.addComp(sec, LoneBotStarHelpUi.panel());
    }
}
