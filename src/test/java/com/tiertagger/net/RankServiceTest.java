package com.tiertagger.net;

import com.google.gson.JsonParseException;
import com.tiertagger.util.TierUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RankServiceTest {
    @Test
    void parsesDirectusPlacementsWithExpandedRelations() {
        String json = """
                {"data":[
                  {"player_id":{"current_name":" Alice "},"game_mode_id":{"slug":"sword"},"tier":2,"rank":"H"},
                  {"player_id":{"current_name":"Alice"},"game_mode_id":{"slug":"axe"},"tier":1,"rank":"L"},
                  {"player_id":null,"game_mode_id":{"slug":"mace"},"tier":1,"rank":"H"},
                  {"player_id":{"current_name":"Bad"},"game_mode_id":{"slug":"unknown"},"tier":7,"rank":"X"}
                ]}
                """;
        Map<String, RankService.Best> best = new HashMap<>();
        Map<String, Map<String, String>> kits = new LinkedHashMap<>();

        RankService.parsePlacements(json, best, kits);

        assertEquals(Map.of(TierUtils.KIT_SWORD, "HT2", TierUtils.KIT_AXE, "LT1"), kits.get("alice"));
        assertEquals("LT1", best.get("alice").tierCode());
        assertEquals(TierUtils.KIT_AXE, best.get("alice").kit());
        assertFalse(kits.containsKey("bad"));
    }

    @Test
    void parsesDirectusHistoricalStats() {
        String json = """
                {"data":[
                  {"player_id":{"current_name":"Alice"},"game_mode_id":{"slug":"sword"},"peak_tier":"ht1","retired_tier":"RLT2"},
                  {"player_id":{"current_name":"Alice"},"game_mode_id":{"slug":"mace"},"peak_tier":"RHT1","retired_tier":"bad"}
                ]}
                """;

        RankService.HistoricalTiers parsed = RankService.parsePlayerModeStats(json);

        assertEquals(Map.of(TierUtils.KIT_SWORD, "RLT2"), parsed.retired().get("alice"));
        assertEquals(Map.of(TierUtils.KIT_SWORD, "HT1"), parsed.peaks().get("alice"));
    }

    @Test
    void directusParsersRejectMissingDataEnvelope() {
        assertThrows(JsonParseException.class,
                () -> RankService.parsePlacements("[]", new HashMap<>(), new LinkedHashMap<>()));
        assertThrows(JsonParseException.class, () -> RankService.parsePlayerModeStats("{}"));
    }

    @Test
    void activeTiersOverrideRetiredTiers() {
        Map<String, String> resolved = RankService.resolveBaselineTiers(
                Map.of(TierUtils.KIT_SWORD, "LT3"),
                Map.of(TierUtils.KIT_SWORD, "RHT1", TierUtils.KIT_AXE, "RLT2")
        );

        assertEquals("LT3", resolved.get(TierUtils.KIT_SWORD));
        assertEquals("RLT2", resolved.get(TierUtils.KIT_AXE));
    }

    @Test
    void peakMustHaveBaselineAndBeStrictlyBetter() {
        Map<String, String> visible = RankService.visiblePeaks(
                Map.of(TierUtils.KIT_SWORD, "RLT2", TierUtils.KIT_AXE, "HT2"),
                Map.of(TierUtils.KIT_SWORD, "HT2", TierUtils.KIT_AXE, "HT2", TierUtils.KIT_MACE, "HT1")
        );

        assertEquals(Map.of(TierUtils.KIT_SWORD, "HT2"), visible);
    }

    @Test
    void overallIgnoresRetiredPrefixAndUsesKitPriorityForTies() {
        RankService.OverallBest best = RankService.overallBestForTiers(Map.of(
                TierUtils.KIT_AXE, "HT2", TierUtils.KIT_SWORD, "RHT2", TierUtils.KIT_MACE, "HT1"));

        assertNotNull(best);
        assertEquals(TierUtils.KIT_MACE, best.kit);
        assertEquals("HT1", best.tierCode);

        RankService.OverallBest tied = RankService.overallBestForTiers(Map.of(
                TierUtils.KIT_AXE, "HT2", TierUtils.KIT_SWORD, "RHT2"));
        assertEquals(TierUtils.KIT_SWORD, tied.kit);
        assertEquals("RHT2", tied.tierCode);
        assertEquals("HT2", tied.baseTierCode);
    }

    @Test
    void overallPlacementUsesBestSourceOncePerKitAndSumsAllKits() {
        Map<String, Map<String, String>> current = Map.of("alice", Map.of(
                TierUtils.KIT_SWORD, "LT2",
                TierUtils.KIT_AXE, "HT3",
                TierUtils.KIT_NETHPOT, "LT5",
                TierUtils.KIT_UHC, "LT5",
                TierUtils.KIT_DIAPOT, "LT5",
                TierUtils.KIT_CRYSTAL, "LT5",
                TierUtils.KIT_SMP, "LT5",
                TierUtils.KIT_MACE, "LT5"
        ));
        Map<String, Map<String, String>> retired = Map.of("alice", Map.of(
                TierUtils.KIT_SWORD, "RHT2",
                TierUtils.KIT_AXE, "RLT2"
        ));
        Map<String, Map<String, String>> peaks = Map.of("alice", Map.of(
                TierUtils.KIT_SWORD, "HT1",
                TierUtils.KIT_AXE, "LT1"
        ));

        RankService.OverallPlacement result = RankService.overallPlacementForName(
                "Alice", current, retired, peaks);

        assertNotNull(result);
        assertEquals(1, result.placement());
        assertEquals(111, result.points());
    }

    @Test
    void placementIncludesHistoricalPlayersAndKeepsSourceOrderForTies() {
        Map<String, Map<String, String>> current = new LinkedHashMap<>();
        current.put("zeta", Map.of(TierUtils.KIT_SWORD, "HT1"));
        current.put("alpha", Map.of(TierUtils.KIT_SWORD, "HT1"));
        current.put("invalid", Map.of(TierUtils.KIT_SWORD, "unknown"));
        Map<String, Map<String, String>> retired = Map.of(
                "beta", Map.of(TierUtils.KIT_SWORD, "RHT1")
        );
        Map<String, Map<String, String>> peaks = Map.of(
                "gamma", Map.of(TierUtils.KIT_SWORD, "LT1")
        );

        RankService.OverallPlacement zeta = RankService.overallPlacementForName("zeta", current, retired, peaks);
        RankService.OverallPlacement alpha = RankService.overallPlacementForName("alpha", current, retired, peaks);
        RankService.OverallPlacement beta = RankService.overallPlacementForName("beta", current, retired, peaks);
        RankService.OverallPlacement gamma = RankService.overallPlacementForName("gamma", current, retired, peaks);

        assertEquals(new RankService.OverallPlacement(1, 60), zeta);
        assertEquals(new RankService.OverallPlacement(2, 60), alpha);
        assertEquals(new RankService.OverallPlacement(3, 60), beta);
        assertEquals(new RankService.OverallPlacement(4, 45), gamma);
        assertNull(RankService.overallPlacementForName("invalid", current, retired, peaks));
        assertNull(RankService.overallPlacementForName("missing", current, retired, peaks));
    }

    @Test
    void partialEndpointFailureKeepsPreviousSnapshotAndDisplayCache() {
        RankService.RefreshDecision decision = RankService.refreshDecision(false, true);

        assertFalse(decision.commitSnapshot());
        assertFalse(decision.invalidateDisplayCache());
    }

    @Test
    void unchangedRefreshKeepsValidDisplayCache() {
        RankService.RefreshDecision decision = RankService.refreshDecision(true, false);

        assertTrue(decision.commitSnapshot());
        assertFalse(decision.invalidateDisplayCache());
    }

    @Test
    void changedRefreshInvalidatesStaleDisplayCache() {
        RankService.RefreshDecision decision = RankService.refreshDecision(true, true);

        assertTrue(decision.commitSnapshot());
        assertTrue(decision.invalidateDisplayCache());
    }

    @Test
    void refreshReschedulingInvalidatesPreviousGeneration() {
        long original = 4;
        long rescheduled = RankService.nextScheduleGeneration(original);

        assertEquals(5, rescheduled);
        assertFalse(RankService.isCurrentSchedule(original, rescheduled));
        assertTrue(RankService.isCurrentSchedule(rescheduled, rescheduled));
    }
}
