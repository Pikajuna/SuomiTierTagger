package com.tiertagger.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    private static final Gson GSON = new Gson();

    @Test
    void preservesDisabledTabTiersAcrossJsonRoundTrip() {
        ConfigManager.ConfigData loaded = GSON.fromJson(
                "{\"configVersion\":7,\"showTabTiers\":false}",
                ConfigManager.ConfigData.class
        );

        ConfigManager.ConfigData normalized = ConfigManager.withDefaults(loaded);
        ConfigManager.ConfigData reloaded = GSON.fromJson(
                GSON.toJson(normalized),
                ConfigManager.ConfigData.class
        );

        assertFalse(ConfigManager.withDefaults(reloaded).showTabTiers);
    }

    @Test
    void addsEnabledTabTiersDefaultToOldConfig() {
        ConfigManager.ConfigData loaded = GSON.fromJson(
                "{\"configVersion\":7}",
                ConfigManager.ConfigData.class
        );

        ConfigManager.ConfigData normalized = ConfigManager.withDefaults(loaded);

        assertTrue(normalized.showTabTiers);
        assertTrue(ConfigManager.needsWriteBack(loaded, normalized));
        assertTrue(GSON.toJson(normalized).contains("\"showTabTiers\":true"));
    }

    @Test
    void addsEnabledIndependentDisplayDefaultsToOldConfig() {
        ConfigManager.ConfigData loaded = GSON.fromJson(
                "{\"configVersion\":7}",
                ConfigManager.ConfigData.class
        );

        ConfigManager.ConfigData normalized = ConfigManager.withDefaults(loaded);

        assertTrue(normalized.showWorldNametags);
        assertTrue(normalized.showTabTiers);
        assertTrue(normalized.showChatTiers);
        assertTrue(normalized.showSelf);
        assertFalse(normalized.showPeakInNametag);
        assertTrue(ConfigManager.needsWriteBack(loaded, normalized));
    }

    @Test
    void preservesDisabledIndependentDisplaySettings() {
        ConfigManager.ConfigData loaded = GSON.fromJson("""
                {
                  "configVersion": 8,
                  "showWorldNametags": false,
                  "showTabTiers": false,
                  "showChatTiers": false,
                  "showSelf": false,
                  "showPeakInNametag": true
                }
                """, ConfigManager.ConfigData.class);

        ConfigManager.ConfigData normalized = ConfigManager.withDefaults(loaded);

        assertFalse(normalized.showWorldNametags);
        assertFalse(normalized.showTabTiers);
        assertFalse(normalized.showChatTiers);
        assertFalse(normalized.showSelf);
        assertTrue(normalized.showPeakInNametag);
    }

    @Test
    void removesLegacyUnusedRenderSettingsOnWriteBack() {
        ConfigManager.ConfigData loaded = GSON.fromJson(
                "{\"configVersion\":9,\"drawDistanceBlocks\":32,\"yOffset\":1.0,\"scale\":0.5}",
                ConfigManager.ConfigData.class
        );

        String normalizedJson = GSON.toJson(ConfigManager.withDefaults(loaded));

        assertFalse(normalizedJson.contains("drawDistanceBlocks"));
        assertFalse(normalizedJson.contains("yOffset"));
        assertFalse(normalizedJson.contains("scale"));
    }
}
