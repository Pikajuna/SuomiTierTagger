
package com.tiertagger.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ConfigManager {
    public enum SlotPosition { LEFT, RIGHT, OFF }
    public enum SeparatorMode { ADAPTIVE, STATIC, OFF }

    private static final String FILE_NAME = "tiertagger.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_CONFIG_VERSION = 10;
    private static ConfigData cached;

    private ConfigManager() {}

    public static synchronized ConfigData get() {
        if (cached != null) return cached;
        Path cfgDir = FabricLoader.getInstance().getConfigDir();
        Path cfgFile = cfgDir.resolve(FILE_NAME);
        if (Files.exists(cfgFile)) {
            try (Reader r = Files.newBufferedReader(cfgFile, StandardCharsets.UTF_8)) {
                ConfigData d = GSON.fromJson(r, ConfigData.class);
                ConfigData normalized = withDefaults(d);
                boolean changed = needsWriteBack(d, normalized);
                if (changed) {
                    try (Writer w = Files.newBufferedWriter(cfgFile, StandardCharsets.UTF_8)) {
                        GSON.toJson(normalized, w);
                    }
                }
                cached = normalized;
                return cached;
            } catch (IOException e) {
                // I/O error -> fall through to write defaults
            } catch (RuntimeException e) {
                // JsonSyntaxException or other parse problems: reset to defaults instead of breaking init
                System.err.println("[SuomiTierTagger] Failed to read config, resetting to defaults: " + e);
            }
        }
        // Write defaults if missing or failed to read/parse
        ConfigData def = defaults();
        try {
            Files.createDirectories(cfgDir);
            try (Writer w = Files.newBufferedWriter(cfgFile, StandardCharsets.UTF_8)) {
                GSON.toJson(def, w);
            }
        } catch (IOException ignored) {}
        cached = def;
        return cached;
    }

    public static synchronized void setSelectedKit(String kit) {
        ConfigData d = get();
        d.selectedKit = kit;
        persist(d);
    }

    public static synchronized void setDisabled(boolean disabled) {
        ConfigData d = get();
        d.disabled = disabled;
        persist(d);
    }

    public static synchronized boolean isDisabled() {
        return Boolean.TRUE.equals(get().disabled);
    }

    public static synchronized void setAutoUpdateEnabled(boolean enabled) {
        ConfigData d = get();
        d.autoUpdate = enabled;
        persist(d);
    }

    public static synchronized boolean isAutoUpdateEnabled() {
        Boolean v = get().autoUpdate;
        // Oletuksena automaattiset päivitykset ovat päällä, jos asetusta ei ole.
        return v == null || v;
    }
    
    public static synchronized SlotPosition getSlotAPosition() {
        return parseSlotPosition(get().slotAPosition, SlotPosition.LEFT);
    }

    public static synchronized void setSlotAPosition(SlotPosition pos) {
        ConfigData d = get(); d.slotAPosition = pos.name(); persist(d);
    }

    public static synchronized SeparatorMode getSeparatorMode() {
        ConfigData d = get();
        if (d.separatorMode != null) {
            try { return SeparatorMode.valueOf(d.separatorMode.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) {}
        }
        // Migrate from old boolean field
        if (Boolean.TRUE.equals(d.separatorAdaptiveColor)) return SeparatorMode.ADAPTIVE;
        return SeparatorMode.STATIC;
    }

    public static synchronized void setSeparatorMode(SeparatorMode mode) {
        ConfigData d = get();
        d.separatorMode = mode.name();
        d.separatorAdaptiveColor = (mode == SeparatorMode.ADAPTIVE);
        persist(d);
    }

    public static synchronized boolean isSeparatorAdaptive() {
        return getSeparatorMode() == SeparatorMode.ADAPTIVE;
    }

    public static synchronized void setSeparatorAdaptive(boolean adaptive) {
        setSeparatorMode(adaptive ? SeparatorMode.ADAPTIVE : SeparatorMode.STATIC);
    }

    public static synchronized boolean isShowPeakInNametag() {
        return Boolean.TRUE.equals(get().showPeakInNametag);
    }

    public static synchronized void setShowPeakInNametag(boolean show) {
        ConfigData d = get(); d.showPeakInNametag = show; persist(d);
    }

    public static synchronized boolean isShowTabTiers() {
        Boolean v = get().showTabTiers;
        return v == null || v;
    }

    public static synchronized boolean isShowWorldNametags() {
        Boolean v = get().showWorldNametags;
        return v == null || v;
    }

    public static synchronized void setShowWorldNametags(boolean show) {
        ConfigData d = get(); d.showWorldNametags = show; persist(d);
    }

    public static synchronized boolean isShowChatTiers() {
        Boolean v = get().showChatTiers;
        return v == null || v;
    }

    public static synchronized void setShowChatTiers(boolean show) {
        ConfigData d = get(); d.showChatTiers = show; persist(d);
    }

    public static synchronized void setShowTabTiers(boolean show) {
        ConfigData d = get(); d.showTabTiers = show; persist(d);
    }

    private static SlotPosition parseSlotPosition(String s, SlotPosition fallback) {
        if (s == null) return fallback;
        try { return SlotPosition.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    public static synchronized void save() {
        persist(get());
    }

    public static synchronized void resetToDefaults() {
        cached = defaults();
        persist(cached);
    }

    public static synchronized void clearCache() {
        cached = null;
    }

    private static void persist(ConfigData d) {
        try {
            Path cfgDir = FabricLoader.getInstance().getConfigDir();
            Path cfgFile = cfgDir.resolve(FILE_NAME);
            Files.createDirectories(cfgDir);
            try (Writer w = Files.newBufferedWriter(cfgFile, StandardCharsets.UTF_8)) {
                GSON.toJson(d, w);
            }
        } catch (IOException ignored) {}
    }

    public static synchronized void setTierColors(Map<String, String> colors) {
        if (colors == null) return;
        ConfigData d = get();
        // Normalize keys to upper-case and values to #RRGGBB format
        Map<String, String> norm = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : colors.entrySet()) {
            String key = e.getKey() == null ? null : e.getKey().toUpperCase(Locale.ROOT);
            if (key == null || key.isBlank()) continue;
            String val = normalizeHex(e.getValue());
            if (val != null) norm.put(key, val);
        }
        if (!norm.isEmpty()) {
            d.tierColors = norm;
            persist(d);
        }
    }

    public static synchronized void setTierColor(String tierCode, String hex) {
        if (tierCode == null || hex == null) return;
        ConfigData d = get();
        if (d.tierColors == null) d.tierColors = new LinkedHashMap<>();
        String key = tierCode.toUpperCase(Locale.ROOT);
        String val = normalizeHex(hex);
        if (val != null) {
            d.tierColors.put(key, val);
            persist(d);
        }
    }

    private static String normalizeHex(String hex) {
        if (hex == null) return null;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return null;
        }
        return "#" + s.toUpperCase(Locale.ROOT);
    }

    static boolean needsWriteBack(ConfigData before, ConfigData after) {
        if (before == null) return true;
        if (before.refreshMinutes != after.refreshMinutes) return true;
        if (!Objects.equals(before.showSelf, after.showSelf)) return true;
        if (!Objects.equals(before.tierColors, after.tierColors)) return true;
        if (!Objects.equals(before.selectedKit, after.selectedKit)) return true;
        if (!Objects.equals(before.configVersion, after.configVersion)) return true;
        if (!Objects.equals(before.disabled, after.disabled)) return true;
        if (!Objects.equals(before.autoUpdate, after.autoUpdate)) return true;
        if (!Objects.equals(before.slotAPosition,          after.slotAPosition))          return true;
        if (!Objects.equals(before.separatorAdaptiveColor, after.separatorAdaptiveColor)) return true;
        if (!Objects.equals(before.separatorMode,          after.separatorMode))          return true;
        if (!Objects.equals(before.showPeakInNametag,      after.showPeakInNametag))      return true;
        if (!Objects.equals(before.showTabTiers,            after.showTabTiers))            return true;
        if (!Objects.equals(before.showWorldNametags,       after.showWorldNametags))       return true;
        if (!Objects.equals(before.showChatTiers,           after.showChatTiers))           return true;
        return false;
    }

    static ConfigData withDefaults(ConfigData in) {
        if (in == null) return defaults();
        ConfigData def = defaults();
        ConfigData d = new ConfigData();
        d.refreshMinutes = in.refreshMinutes <= 0 ? def.refreshMinutes : in.refreshMinutes;
        d.showSelf = in.showSelf == null ? def.showSelf : in.showSelf;
        // Force color update for version 3 (new RGB color scheme)
        boolean forceColorUpdate = in.configVersion == null || in.configVersion < 3;
        Map<String, String> resolvedColors;
        if (in.tierColors == null || in.tierColors.isEmpty() || forceColorUpdate) {
            resolvedColors = new LinkedHashMap<>(def.tierColors);
        } else {
            resolvedColors = new LinkedHashMap<>(in.tierColors);
            resolvedColors.putIfAbsent("SEPARATOR_STATIC", def.tierColors.get("SEPARATOR_STATIC"));
            resolvedColors.putIfAbsent("PEAK_INDICATOR",   def.tierColors.get("PEAK_INDICATOR"));
        }
        d.tierColors = resolvedColors;
        
        d.selectedKit = (in.selectedKit == null || in.selectedKit.isBlank()) ? def.selectedKit : in.selectedKit;
        d.configVersion = (in.configVersion == null || in.configVersion < CURRENT_CONFIG_VERSION) ? CURRENT_CONFIG_VERSION : in.configVersion;
        d.disabled = in.disabled == null ? def.disabled : in.disabled;
        d.autoUpdate = in.autoUpdate == null ? def.autoUpdate : in.autoUpdate;
        d.slotAPosition           = (in.slotAPosition == null)           ? def.slotAPosition           : in.slotAPosition;
        d.separatorAdaptiveColor  = (in.separatorAdaptiveColor == null)  ? def.separatorAdaptiveColor  : in.separatorAdaptiveColor;
        d.separatorMode           = (in.separatorMode == null)           ? def.separatorMode           : in.separatorMode;
        d.showPeakInNametag       = (in.showPeakInNametag == null)       ? def.showPeakInNametag       : in.showPeakInNametag;
        d.showTabTiers            = (in.showTabTiers == null)            ? def.showTabTiers            : in.showTabTiers;
        d.showWorldNametags       = (in.showWorldNametags == null)       ? def.showWorldNametags       : in.showWorldNametags;
        d.showChatTiers           = (in.showChatTiers == null)           ? def.showChatTiers           : in.showChatTiers;
        return d;
    }

    private static ConfigData defaults() {
        ConfigData d = new ConfigData();
        d.refreshMinutes = 5;
        d.showSelf = true;
        d.selectedKit = "All";
        d.configVersion = CURRENT_CONFIG_VERSION;
        d.disabled = false;
        d.autoUpdate = true;
        d.slotAPosition = "LEFT";
        d.separatorAdaptiveColor = false;
        d.separatorMode = "STATIC";
        d.showPeakInNametag = false;
        d.showTabTiers = true;
        d.showWorldNametags = true;
        d.showChatTiers = true;
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("HT1", "#990000"); // T1: 153-0-0 (dark red)
        colors.put("LT1", "#990000"); // T1: 153-0-0 (dark red)
        colors.put("HT2", "#FF0000"); // T2: 255-0-0 (red)
        colors.put("LT2", "#FF0000"); // T2: 255-0-0 (red)
        colors.put("HT3", "#FF9900"); // T3: 255-153-0 (orange)
        colors.put("LT3", "#FF9900"); // T3: 255-153-0 (orange)
        colors.put("HT4", "#FFCC00"); // T4: 255-204-0 (gold)
        colors.put("LT4", "#FFCC00"); // T4: 255-204-0 (gold)
        colors.put("HT5", "#FFFF00"); // T5: 255-255-0 (yellow)
        colors.put("LT5", "#FFFF00"); // T5: 255-255-0 (yellow)
        colors.put("SEPARATOR_STATIC", "#555555");
        colors.put("PEAK_INDICATOR",   "#AAAAAA");
        d.tierColors = colors;
        return d;
    }

    public static int colorFor(String tierCode) {
        if (tierCode == null) return 0xFFFFFF;
        String hex = get().tierColors.getOrDefault(tierCode.toUpperCase(Locale.ROOT), "#FFFFFF");
        return parseHex(hex);
    }

    private static int parseHex(String hex) {
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) {
            try { return Integer.parseInt(s, 16); } catch (NumberFormatException ignored) {}
        }
        return 0xFFFFFF;
    }

    public static final class ConfigData {
        @SerializedName("refreshMinutes")
        public int refreshMinutes;
        @SerializedName("showSelf")
        public Boolean showSelf;
        @SerializedName("tierColors")
        public Map<String, String> tierColors;
        @SerializedName("selectedKit")
        public String selectedKit;
        @SerializedName("configVersion")
        public Integer configVersion;
        @SerializedName("disabled")
        public Boolean disabled;
        @SerializedName("autoUpdate")
        public Boolean autoUpdate;

        @SerializedName("slotAPosition")
        public String slotAPosition;

        @SerializedName("separatorAdaptiveColor")
        public Boolean separatorAdaptiveColor;

        @SerializedName("separatorMode")
        public String separatorMode;

        @SerializedName("showPeakInNametag")
        public Boolean showPeakInNametag;

        @SerializedName("showTabTiers")
        public Boolean showTabTiers;

        @SerializedName("showWorldNametags")
        public Boolean showWorldNametags;

        @SerializedName("showChatTiers")
        public Boolean showChatTiers;

    }
}
