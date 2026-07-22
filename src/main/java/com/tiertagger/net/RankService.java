package com.tiertagger.net;

import com.google.gson.*;
import com.tiertagger.TierTaggerClient;
import com.tiertagger.config.ConfigManager;
import com.tiertagger.util.TierUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches rank data from the public SuomTierList database and provides name->rank lookup.
 */
public final class RankService {
    private static final long DEFAULT_REFRESH_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final String DATA_BASE_URL = "https://data.suomitierlist.com/items/";
    private static final String PLACEMENTS_ENDPOINT = DATA_BASE_URL
            + "placements?limit=-1&fields=player_id.current_name,game_mode_id.slug,tier,rank";
    private static final String PLAYER_STATS_ENDPOINT = DATA_BASE_URL
            + "player_mode_stats?limit=-1&fields=player_id.current_name,game_mode_id.slug,peak_tier,retired_tier";
    private static final List<String> SCORING_KITS = List.of(
            TierUtils.KIT_SWORD,
            TierUtils.KIT_AXE,
            TierUtils.KIT_NETHPOT,
            TierUtils.KIT_UHC,
            TierUtils.KIT_DIAPOT,
            TierUtils.KIT_CRYSTAL,
            TierUtils.KIT_SMP,
            TierUtils.KIT_MACE
    );

    private static final RankService INSTANCE = new RankService();
    public static RankService getInstance() { return INSTANCE; }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TierTagger-RankService");
        t.setDaemon(true);
        return t;
    });

    private volatile DataSnapshot snapshot = DataSnapshot.empty();
    private volatile long refreshMillis = DEFAULT_REFRESH_MILLIS;
    private volatile boolean started = false;
    private final Object scheduleLock = new Object();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> scheduledRefresh;
    private volatile long scheduleGeneration = 0;
    private volatile long lastSuccessfulUpdateMillis = 0;
    private volatile long nextRefreshMillis = 0;
    private volatile List<String> failedEndpoints = List.of();

    private RankService() {}

    private void loadFromConfig() {
        var cfg = ConfigManager.get();
        this.refreshMillis = Duration.ofMinutes(cfg.refreshMinutes).toMillis();
    }

    public void start() {
        loadFromConfig();
        synchronized (scheduleLock) {
            if (!started) {
                started = true;
                scheduleLocked();
            }
        }
        refreshNow();
    }

    public void refreshNow() {
        exec.execute(this::refresh);
    }

    public void reschedule() {
        loadFromConfig();
        synchronized (scheduleLock) {
            if (!started) return;
            if (scheduledRefresh != null) scheduledRefresh.cancel(false);
            scheduleLocked();
        }
    }

    private void scheduleLocked() {
        long generation = nextScheduleGeneration(scheduleGeneration);
        scheduleGeneration = generation;
        long interval = refreshMillis;
        nextRefreshMillis = System.currentTimeMillis() + interval;
        scheduledRefresh = exec.scheduleWithFixedDelay(
                () -> runScheduledRefresh(generation, interval),
                interval,
                interval,
                TimeUnit.MILLISECONDS
        );
    }

    private void runScheduledRefresh(long generation, long interval) {
        if (!isCurrentSchedule(generation, scheduleGeneration)) return;
        nextRefreshMillis = 0;
        try {
            refresh();
        } finally {
            if (isCurrentSchedule(generation, scheduleGeneration)) {
                nextRefreshMillis = System.currentTimeMillis() + interval;
            }
        }
    }

    public SyncStatus getSyncStatus() {
        DataSnapshot current = snapshot;
        Set<String> players = new HashSet<>(current.nameToKits.keySet());
        players.addAll(current.nameToRetiredKits.keySet());
        players.addAll(current.nameToPeakKits.keySet());
        return new SyncStatus(
                lastSuccessfulUpdateMillis,
                failedEndpoints,
                players.size(),
                lastSuccessfulUpdateMillis == 0 ? -1 : Math.max(0, System.currentTimeMillis() - lastSuccessfulUpdateMillis),
                nextRefreshMillis,
                refreshInProgress.get()
        );
    }

    public String getBestLabelForName(String name) {
        // Returns the tier+kit, e.g., "HT2 Sword".
        if (name == null) return null;
        Best b = snapshot.nameToBest.get(name.toLowerCase(Locale.ROOT));
        if (b == null) return null;
        return b.tierCode + " " + b.kit;
    }

    public String getBestTierCodeForName(String name) {
        if (name == null) return null;
        Best b = snapshot.nameToBest.get(name.toLowerCase(Locale.ROOT));
        return b == null ? null : b.tierCode;
    }

    public String getBestKitNameForName(String name) {
        if (name == null) return null;
        Best b = snapshot.nameToBest.get(name.toLowerCase(Locale.ROOT));
        return b == null ? null : b.kit;
    }

    public Map<String, String> getAllKitTiersForName(String name) {
        if (name == null) return Map.of();
        Map<String, String> m = snapshot.nameToKits.get(name.toLowerCase(Locale.ROOT));
        return m == null ? Map.of() : m;
    }

    private void refresh() {
        if (!refreshInProgress.compareAndSet(false, true)) return;
        Map<String, Best> aggregateBest = new HashMap<>();
        Map<String, Map<String, String>> aggregateKits = new LinkedHashMap<>();
        Map<String, Map<String, String>> retired = Map.of();
        Map<String, Map<String, String>> peaks = Map.of();
        List<String> failures = new ArrayList<>();

        try {
            try {
                parsePlacements(fetchJson(PLACEMENTS_ENDPOINT), aggregateBest, aggregateKits);
            } catch (IOException | InterruptedException | RuntimeException e) {
                recordFailure(failures, PLACEMENTS_ENDPOINT, e);
            }
            try {
                HistoricalTiers historical = parsePlayerModeStats(fetchJson(PLAYER_STATS_ENDPOINT));
                retired = historical.retired();
                peaks = historical.peaks();
            } catch (IOException | InterruptedException | RuntimeException e) {
                recordFailure(failures, PLAYER_STATS_ENDPOINT, e);
            }
            failedEndpoints = List.copyOf(failures);
            RefreshDecision endpointDecision = refreshDecision(failures.isEmpty(), false);
            if (endpointDecision.commitSnapshot()) {
                DataSnapshot next = new DataSnapshot(
                        Collections.unmodifiableMap(new HashMap<>(aggregateBest)),
                        immutableOrderedTierSnapshot(aggregateKits),
                        immutableOrderedTierSnapshot(retired),
                        immutableOrderedTierSnapshot(peaks)
                );
                DataSnapshot previous = snapshot;
                RefreshDecision decision = refreshDecision(true, !next.equals(previous));
                if (decision.commitSnapshot()) {
                    snapshot = next;
                    lastSuccessfulUpdateMillis = System.currentTimeMillis();
                    if (decision.invalidateDisplayCache()) TierTaggerClient.clearNametagCache();
                }
            }
        } finally {
            refreshInProgress.set(false);
        }
    }

    private String fetchJson(String endpoint) throws IOException, InterruptedException {
        if (endpoint == null) throw new IOException("Missing endpoint");
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private static void recordFailure(List<String> failures, String endpoint, Exception error) {
        failures.add(endpointLabel(endpoint));
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
    }

    static RefreshDecision refreshDecision(boolean allEndpointsSucceeded, boolean snapshotChanged) {
        return new RefreshDecision(allEndpointsSucceeded, allEndpointsSucceeded && snapshotChanged);
    }

    static long nextScheduleGeneration(long currentGeneration) {
        return currentGeneration + 1;
    }

    static boolean isCurrentSchedule(long scheduledGeneration, long currentGeneration) {
        return scheduledGeneration == currentGeneration;
    }

    private static String endpointLabel(String endpoint) {
        if (endpoint == null) return "unknown";
        if (endpoint.contains("player_mode_stats")) return "player_mode_stats";
        if (endpoint.contains("placements")) return "placements";
        int slash = endpoint.lastIndexOf('/');
        String label = slash >= 0 ? endpoint.substring(slash + 1) : endpoint;
        int query = label.indexOf('?');
        return query >= 0 ? label.substring(0, query) : label;
    }

    static void parsePlacements(String json, Map<String, Best> best, Map<String, Map<String, String>> kits) {
        JsonArray rows = directusDataArray(json, "placements");
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            String player = nestedString(row, "player_id", "current_name");
            String kit = matrixHeaderToKit(nestedString(row, "game_mode_id", "slug"));
            Integer tier = getInt(row.get("tier"));
            String tierCode = tier == null ? null : hlToTierCode(getString(row.get("rank")), tier);
            if (normalizePlayerKey(player) == null || kit == null || tierCode == null) continue;
            putBest(best, player, tierCode, kit);
            putKit(kits, player, kit, tierCode);
        }
    }

    static HistoricalTiers parsePlayerModeStats(String json) {
        JsonArray rows = directusDataArray(json, "player_mode_stats");
        Map<String, Map<String, String>> retired = new LinkedHashMap<>();
        Map<String, Map<String, String>> peaks = new LinkedHashMap<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            String player = nestedString(row, "player_id", "current_name");
            String kit = matrixHeaderToKit(nestedString(row, "game_mode_id", "slug"));
            if (normalizePlayerKey(player) == null || kit == null) continue;

            String retiredTier = normalizedHistoricalTier(row.get("retired_tier"), MatrixKind.RETIRED);
            if (retiredTier != null) putKit(retired, player, kit, retiredTier);
            String peakTier = normalizedHistoricalTier(row.get("peak_tier"), MatrixKind.PEAK);
            if (peakTier != null) putKit(peaks, player, kit, peakTier);
        }
        return new HistoricalTiers(
                immutableOrderedTierSnapshot(retired),
                immutableOrderedTierSnapshot(peaks)
        );
    }

    private static JsonArray directusDataArray(String json, String collection) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new JsonParseException(collection + " response must be an object");
        JsonElement data = root.getAsJsonObject().get("data");
        if (data == null || !data.isJsonArray()) {
            throw new JsonParseException(collection + " response must contain a data array");
        }
        return data.getAsJsonArray();
    }

    private static String getString(JsonElement e) {
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    private static Integer getInt(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        try {
            return e.getAsInt();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String nestedString(JsonObject row, String relation, String field) {
        JsonElement relationValue = row.get(relation);
        if (relationValue == null || !relationValue.isJsonObject()) return null;
        return getString(relationValue.getAsJsonObject().get(field));
    }

    private static String normalizedHistoricalTier(JsonElement value, MatrixKind kind) {
        String tier = trimToNull(getString(value));
        if (tier == null) return null;
        tier = tier.toUpperCase(Locale.ROOT);
        return kind.accepts(tier) ? tier : null;
    }

    private static String normalizePlayerKey(String name) {
        String trimmed = trimToNull(name);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String hlToTierCode(String hOrL, int n) {
        if (hOrL == null || n < 1 || n > 5) return null;
        String x = hOrL.trim();
        if (x.equalsIgnoreCase("H") || x.toLowerCase(Locale.ROOT).startsWith("h")) return "HT" + n;
        if (x.equalsIgnoreCase("L") || x.toLowerCase(Locale.ROOT).startsWith("l")) return "LT" + n;
        return normalizeTier(x); // fallback if already HTn/LTn form
    }

    private static void putBest(Map<String, Best> aggregate, String playerName, String tierCode, String kit) {
        String key = normalizePlayerKey(playerName);
        if (key == null || tierCode == null) return;
        int score = TierUtils.tierScore(tierCode);
        if (score == Integer.MAX_VALUE) return;
        Best existing = aggregate.get(key);
        if (existing == null || score < existing.score
                || (score == existing.score && kitPriority(kit) < kitPriority(existing.kit))) {
            aggregate.put(key, new Best(tierCode, kit, score));
        }
    }

    private static void putKit(Map<String, Map<String, String>> aggregateKits, String playerName, String canonicalKit, String tierCode) {
        String key = normalizePlayerKey(playerName);
        if (key == null || canonicalKit == null || canonicalKit.isBlank() || tierCode == null) return;
        aggregateKits.computeIfAbsent(key, k -> new LinkedHashMap<>())
                .put(canonicalKit, tierCode);
    }

    private static String normalizeTier(String s) {
        if (s == null) return null;
        String x = s.trim();
        // Common forms: HT1, LT2, "HT 1", "High Tier 1", "Low Tier 2"
        x = x.replaceAll("(?i)high\\s*tier", "HT");
        x = x.replaceAll("(?i)low\\s*tier", "LT");
        x = x.replaceAll("\\s+", "");
        var m = java.util.regex.Pattern.compile("(?i)^(HT|LT)\s*([1-5])$").matcher(x);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT) + m.group(2);
        }
        return null;
    }

    public String getOverallTierForName(String name) {
        OverallBest b = getOverallBestForName(name);
        return b == null ? null : b.tierCode;
    }

    public OverallBest getOverallBestForName(String name) {
        String key = normalizePlayerKey(name);
        if (key == null) return null;
        DataSnapshot current = snapshot;
        return overallBestForTiers(resolveBaselineTiers(
                current.nameToKits.getOrDefault(key, Map.of()),
                current.nameToRetiredKits.getOrDefault(key, Map.of())
        ));
    }
    
    public Map<String, String> getAllRetiredKitTiersForName(String name) {
        String key = normalizePlayerKey(name);
        if (key == null) return Map.of();
        Map<String, String> kitTiers = snapshot.nameToRetiredKits.get(key);
        return kitTiers == null ? Map.of() : kitTiers;
    }

    public Map<String, String> getPeakKitTiersForName(String name) {
        String key = normalizePlayerKey(name);
        if (key == null) return Map.of();
        DataSnapshot current = snapshot;
        Map<String, String> baseline = resolveBaselineTiers(
                current.nameToKits.getOrDefault(key, Map.of()),
                current.nameToRetiredKits.getOrDefault(key, Map.of())
        );
        return visiblePeaks(baseline, current.nameToPeakKits.getOrDefault(key, Map.of()));
    }

    public OverallPlacement getOverallPlacementForName(String name) {
        DataSnapshot current = snapshot;
        return overallPlacementForName(
                name,
                current.nameToKits,
                current.nameToRetiredKits,
                current.nameToPeakKits
        );
    }

    static OverallPlacement overallPlacementForName(
            String name,
            Map<String, Map<String, String>> current,
            Map<String, Map<String, String>> retired,
            Map<String, Map<String, String>> peaks
    ) {
        String playerKey = normalizePlayerKey(name);
        if (playerKey == null) return null;

        Set<String> playerKeys = new LinkedHashSet<>();
        playerKeys.addAll(current.keySet());
        playerKeys.addAll(retired.keySet());
        playerKeys.addAll(peaks.keySet());

        Map<String, Integer> totals = new LinkedHashMap<>();
        for (String key : playerKeys) {
            int total = totalPointsForPlayer(
                    current.getOrDefault(key, Map.of()),
                    retired.getOrDefault(key, Map.of()),
                    peaks.getOrDefault(key, Map.of())
            );
            if (total > 0) totals.put(key, total);
        }

        Integer playerPoints = totals.get(playerKey);
        if (playerPoints == null) return null;

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(totals.entrySet());
        ranked.sort(Comparator
                .<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed());
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getKey().equals(playerKey)) {
                return new OverallPlacement(i + 1, playerPoints);
            }
        }
        return null;
    }

    private static int totalPointsForPlayer(
            Map<String, String> current,
            Map<String, String> retired,
            Map<String, String> peaks
    ) {
        int total = 0;
        for (String kit : SCORING_KITS) {
            int best = Math.max(TierUtils.tierPoints(current.get(kit)), TierUtils.tierPoints(retired.get(kit)));
            best = Math.max(best, TierUtils.tierPoints(peaks.get(kit)));
            total += best;
        }
        return total;
    }

    private static Map<String, Map<String, String>> immutableOrderedTierSnapshot(
            Map<String, Map<String, String>> source
    ) {
        Map<String, Map<String, String>> snapshot = new LinkedHashMap<>();
        source.forEach((player, tiers) -> snapshot.put(
                player,
                Collections.unmodifiableMap(new LinkedHashMap<>(tiers))
        ));
        return Collections.unmodifiableMap(snapshot);
    }

    static Map<String, String> visiblePeaks(Map<String, String> baseline, Map<String, String> sheetPeaks) {
        Map<String, String> visible = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sheetPeaks.entrySet()) {
            String baselineTier = baseline.get(entry.getKey());
            if (baselineTier == null) continue;
            int baselineScore = TierUtils.tierScore(stripRetiredPrefix(baselineTier));
            int peakScore = TierUtils.tierScore(entry.getValue());
            if (peakScore < baselineScore) visible.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(visible);
    }

    static Map<String, String> resolveBaselineTiers(Map<String, String> current, Map<String, String> retired) {
        Map<String, String> resolved = new LinkedHashMap<>(retired);
        resolved.putAll(current);
        return Map.copyOf(resolved);
    }

    static OverallBest overallBestForTiers(Map<String, String> tiers) {
        String bestKit = null;
        String bestTier = null;
        String bestBase = null;
        int bestScore = Integer.MAX_VALUE;
        for (Map.Entry<String, String> entry : tiers.entrySet()) {
            String base = stripRetiredPrefix(entry.getValue());
            int score = TierUtils.tierScore(base);
            if (score < bestScore || (score == bestScore && kitPriority(entry.getKey()) < kitPriority(bestKit))) {
                bestKit = entry.getKey();
                bestTier = entry.getValue();
                bestBase = base;
                bestScore = score;
            }
        }
        return bestKit == null ? null : new OverallBest(bestKit, bestTier, bestBase);
    }

    private static String stripRetiredPrefix(String tier) {
        return tier != null && tier.startsWith("R") ? tier.substring(1) : tier;
    }

    private static String matrixHeaderToKit(String header) {
        if (header == null) return null;
        return switch (header.trim().toLowerCase(Locale.ROOT)) {
            case "sword" -> TierUtils.KIT_SWORD;
            case "axe" -> TierUtils.KIT_AXE;
            case "nethpot" -> TierUtils.KIT_NETHPOT;
            case "uhc" -> TierUtils.KIT_UHC;
            case "diapot" -> TierUtils.KIT_DIAPOT;
            case "crystal" -> TierUtils.KIT_CRYSTAL;
            case "smp" -> TierUtils.KIT_SMP;
            case "mace" -> TierUtils.KIT_MACE;
            default -> null;
        };
    }

    enum MatrixKind {
        RETIRED("R[HL]T[1-5]"),
        PEAK("[HL]T[1-5]");

        private final java.util.regex.Pattern tierPattern;

        MatrixKind(String pattern) {
            this.tierPattern = java.util.regex.Pattern.compile(pattern);
        }

        boolean accepts(String tier) {
            return tier != null && tierPattern.matcher(tier).matches();
        }
    }

    private static int kitPriority(String kit) {
        if (kit == null) return Integer.MAX_VALUE;
        return switch (kit) {
            case TierUtils.KIT_SWORD   -> 1;
            case TierUtils.KIT_AXE     -> 2;
            case TierUtils.KIT_NETHPOT -> 3;
            case TierUtils.KIT_UHC     -> 4;
            case TierUtils.KIT_DIAPOT  -> 5;
            case TierUtils.KIT_CRYSTAL -> 6;
            case TierUtils.KIT_SMP     -> 7;
            case TierUtils.KIT_MACE    -> 8;
            default -> 999;
        };
    }

    record Best(String tierCode, String kit, int score) {}

    record HistoricalTiers(
            Map<String, Map<String, String>> retired,
            Map<String, Map<String, String>> peaks
    ) {}

    private record DataSnapshot(
            Map<String, Best> nameToBest,
            Map<String, Map<String, String>> nameToKits,
            Map<String, Map<String, String>> nameToRetiredKits,
            Map<String, Map<String, String>> nameToPeakKits
    ) {
        private static DataSnapshot empty() {
            return new DataSnapshot(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public record SyncStatus(
            long lastSuccessfulUpdateMillis,
            List<String> failedEndpoints,
            int loadedPlayerCount,
            long dataAgeMillis,
            long nextRefreshMillis,
            boolean refreshInProgress
    ) {}

    record RefreshDecision(boolean commitSnapshot, boolean invalidateDisplayCache) {}

    public static final class OverallBest {
        public final String kit; // canonical kit name (Sword, Axe, ...)
        public final String tierCode; // may include leading R (e.g., RHT2)
        public final String baseTierCode; // without R (e.g., HT2)
        public OverallBest(String kit, String tierCode, String baseTierCode) {
            this.kit = kit; this.tierCode = tierCode; this.baseTierCode = baseTierCode;
        }
    }

    public record OverallPlacement(int placement, int points) {}
}
