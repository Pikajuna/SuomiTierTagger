package com.tiertagger.render;

import com.tiertagger.util.TierUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Selects the single kit/tier pair shown by nametag, tab-list, and chat renderers. */
public final class TierDisplayResolver {
    private TierDisplayResolver() {}

    public static TierDisplay resolve(
            String selectedKit,
            Map<String, String> current,
            Map<String, String> retired,
            Map<String, String> peaks,
            boolean showPeak
    ) {
        Map<String, String> safeCurrent = current == null ? Map.of() : current;
        Map<String, String> safeRetired = retired == null ? Map.of() : retired;
        Map<String, String> safePeaks = peaks == null ? Map.of() : peaks;
        String canonical = canonicalSelectedKit(selectedKit);

        if (!"All".equalsIgnoreCase(canonical)) {
            String baseline = baselineFor(canonical, safeCurrent, safeRetired);
            String tier = displayedTier(canonical, baseline, safePeaks, showPeak);
            return tier == null ? null : new TierDisplay(canonical, tier);
        }

        Set<String> kits = new LinkedHashSet<>();
        kits.addAll(safeCurrent.keySet());
        kits.addAll(safeRetired.keySet());

        TierDisplay best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String kit : kits) {
            String tier = displayedTier(kit, baselineFor(kit, safeCurrent, safeRetired), safePeaks, showPeak);
            if (tier == null) continue;
            int score = TierUtils.tierScore(baseTier(tier));
            if (score < bestScore) {
                best = new TierDisplay(kit, tier);
                bestScore = score;
            }
        }
        return best;
    }

    static String canonicalSelectedKit(String selectedKit) {
        if (selectedKit == null || selectedKit.isBlank() || "all".equalsIgnoreCase(selectedKit.trim())) {
            return "All";
        }
        return TierUtils.canonicalKit(selectedKit);
    }

    static String baseTier(String tier) {
        if (tier == null) return null;
        return tier.startsWith("R") ? tier.substring(1) : tier;
    }

    private static String baselineFor(String kit, Map<String, String> current, Map<String, String> retired) {
        String tier = current.get(kit);
        if (tier == null) tier = retired.get(kit);
        return normalizeTier(tier);
    }

    private static String displayedTier(
            String kit,
            String baseline,
            Map<String, String> peaks,
            boolean showPeak
    ) {
        if (baseline == null) return null;
        if (!showPeak) return baseline;
        String peak = normalizeTier(peaks.get(kit));
        if (peak == null) return baseline;
        return TierUtils.tierScore(baseTier(peak)) < TierUtils.tierScore(baseTier(baseline)) ? peak : baseline;
    }

    private static String normalizeTier(String tier) {
        if (tier == null || tier.isBlank()) return null;
        return tier.trim();
    }

    public record TierDisplay(String kit, String tier) {}
}
