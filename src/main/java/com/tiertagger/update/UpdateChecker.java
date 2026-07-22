package com.tiertagger.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Checks Modrinth for a newer compatible release. */
public final class UpdateChecker {
    private static final UpdateChecker INSTANCE = new UpdateChecker();
    private static final String MOD_ID = "suomitiertagger";
    private static final String MODRINTH_PROJECT = "suomi-tier-tagger";
    private static final String LOADER = "fabric";
    private static final String USER_AGENT = "SuomiTierTagger updater (https://modrinth.com/mod/suomi-tier-tagger)";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private UpdateChecker() {}

    public static UpdateChecker getInstance() {
        return INSTANCE;
    }

    public static final class UpdateInfo {
        public final String currentVersion;
        public final String latestVersion;
        public final String downloadUrl;
        public final String changelog;
        public final String hashAlgorithm;
        public final String hash;

        public UpdateInfo(String currentVersion, String latestVersion, String downloadUrl,
                          String changelog, String hashAlgorithm, String hash) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.changelog = changelog;
            this.hashAlgorithm = hashAlgorithm;
            this.hash = hash;
        }
    }

    /** Shared state that client thread reads without blocking. */
    public static final class State {
        public static volatile boolean checked = false;
        public static volatile UpdateInfo latest = null;
    }

    public void checkAsync() {
        State.checked = false;
        State.latest = null;
        CompletableFuture.runAsync(this::checkOnce);
    }

    private void checkOnce() {
        try {
            String currentVersion = FabricLoader.getInstance()
                    .getModContainer(MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("0.0.0");
            String gameMcVersion = getGameMinecraftVersion();
            String builtForMcVersion = extractMcFromModVersion(currentVersion);
            String mcVersion = gameMcVersion != null ? gameMcVersion : builtForMcVersion;
            if (mcVersion == null) mcVersion = "unknown";

            System.out.println("[SuomiTierTagger] Checking Modrinth for updates: current="
                    + currentVersion + ", minecraft=" + mcVersion);

            HttpRequest request = HttpRequest.newBuilder(createVersionsUri(mcVersion))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                System.err.println("[SuomiTierTagger] Modrinth update check returned HTTP "
                        + response.statusCode());
                return;
            }

            UpdateInfo info = parseAndCompare(
                    response.body(), currentVersion, mcVersion, builtForMcVersion);
            if (info != null) {
                State.latest = info;
                System.out.println("[SuomiTierTagger] Update available: "
                        + currentVersion + " -> " + info.latestVersion);
            } else {
                System.out.println("[SuomiTierTagger] No Modrinth update available for Minecraft "
                        + mcVersion);
            }
        } catch (Exception error) {
            System.err.println("[SuomiTierTagger] Modrinth update check failed: " + safeMessage(error));
        } finally {
            State.checked = true;
        }
    }

    private static URI createVersionsUri(String minecraftVersion) {
        String loaders = URLEncoder.encode("[\"" + LOADER + "\"]", StandardCharsets.UTF_8);
        String gameVersions = URLEncoder.encode(
                "[\"" + minecraftVersion + "\"]", StandardCharsets.UTF_8);
        return URI.create("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT
                + "/version?loaders=" + loaders
                + "&game_versions=" + gameVersions
                + "&include_changelog=true");
    }

    UpdateInfo parseAndCompare(String json, String currentVersion,
                               String mcVersion, String builtForMcVersion) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) return null;

        JsonObject bestVersion = null;
        JsonObject bestFile = null;
        String bestNumber = null;
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();
            if (!"release".equalsIgnoreCase(getString(version, "version_type"))) continue;
            String versionNumber = trimToNull(getString(version, "version_number"));
            JsonObject file = selectDownloadFile(version.getAsJsonArray("files"));
            if (versionNumber == null || file == null) continue;
            if (bestNumber == null || isNewer(versionNumber, bestNumber)) {
                bestVersion = version;
                bestFile = file;
                bestNumber = versionNumber;
            }
        }
        if (bestVersion == null || bestFile == null) return null;

        boolean shouldOffer = isNewer(bestNumber, currentVersion);
        String wantedMc = normalizeMcVersion(mcVersion);
        String builtMc = normalizeMcVersion(builtForMcVersion);
        if (!shouldOffer && wantedMc != null && builtMc != null && !wantedMc.equals(builtMc)
                && baseVersion(bestNumber).equals(baseVersion(currentVersion))) {
            shouldOffer = true;
        }
        if (!shouldOffer) return null;

        String downloadUrl = trimToNull(getString(bestFile, "url"));
        JsonObject hashes = bestFile.getAsJsonObject("hashes");
        String sha512 = hashes == null ? null : trimToNull(getString(hashes, "sha512"));
        if (downloadUrl == null || sha512 == null) return null;
        String changelog = getString(bestVersion, "changelog");
        return new UpdateInfo(currentVersion, bestNumber, downloadUrl,
                changelog == null ? "" : changelog, "SHA-512", sha512);
    }

    private static JsonObject selectDownloadFile(JsonArray files) {
        if (files == null || files.isEmpty()) return null;
        JsonObject fallback = null;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            String filename = getString(file, "filename");
            if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) continue;
            if (fallback == null) fallback = file;
            JsonElement primary = file.get("primary");
            if (primary != null && primary.isJsonPrimitive() && primary.getAsBoolean()) return file;
        }
        return fallback;
    }

    public static String resolveMinecraftVersion() {
        String game = getGameMinecraftVersion();
        if (game != null) return game;
        try {
            String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
            String extracted = extractMcFromModVersion(version);
            if (extracted != null) return extracted;
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static String getGameMinecraftVersion() {
        try {
            String metadataVersion = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .map(UpdateChecker::normalizeMcVersion).orElse(null);
            if (metadataVersion != null) return metadataVersion;
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            return minecraft == null ? null : normalizeMcVersion(minecraft.getLaunchedVersion());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractMcFromModVersion(String version) {
        if (version == null) return null;
        int index = version.indexOf("+mc");
        return index >= 0 && index + 3 < version.length()
                ? trimToNull(version.substring(index + 3)) : null;
    }

    private static String normalizeMcVersion(String version) {
        String value = trimToNull(version);
        if (value == null) return null;
        var matcher = java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)*)").matcher(value);
        return matcher.find() ? matcher.group(1) : value;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static boolean isNewer(String remote, String local) {
        String[] remoteParts = baseVersion(remote).split("\\.");
        String[] localParts = baseVersion(local).split("\\.");
        int length = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < length; i++) {
            int remotePart = i < remoteParts.length ? parseIntSafe(remoteParts[i]) : 0;
            int localPart = i < localParts.length ? parseIntSafe(localParts[i]) : 0;
            if (remotePart != localPart) return remotePart > localPart;
        }
        return false;
    }

    private static String baseVersion(String version) {
        if (version == null || version.isBlank()) return "0.0.0";
        int plus = version.indexOf('+');
        if (plus >= 0) version = version.substring(0, plus);
        int dash = version.indexOf('-');
        if (dash >= 0) version = version.substring(0, dash);
        return version.trim();
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
