package com.tiertagger.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class UpdateInstaller {
    private static final String MOD_ID = "suomitiertagger";
    private static final long MAX_DOWNLOAD_BYTES = 128L * 1024L * 1024L;

    private UpdateInstaller() {}

    static InstallResult downloadAndInstall(
            HttpClient http,
            UpdateChecker.UpdateInfo info,
            Path modsDir,
            String minecraftVersion,
            Path currentJar
    ) throws IOException, InterruptedException {
        String expectedHash = normalizeHash(info.hashAlgorithm, info.hash);
        String versionForFile = versionForFile(info.latestVersion, minecraftVersion);
        if (!versionForFile.matches("[A-Za-z0-9._+\\-]+")) {
            throw new IOException("Update version contains unsafe filename characters");
        }

        Path normalizedModsDir = modsDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedModsDir);
        Path target = normalizedModsDir.resolve("suomitiertagger-" + versionForFile + ".jar").normalize();
        if (!target.getParent().equals(normalizedModsDir)) {
            throw new IOException("Update target escapes mods directory");
        }
        if (Files.exists(target)) {
            throw new IOException("Update target already exists: " + target.getFileName());
        }

        Path temporary = Files.createTempFile(normalizedModsDir, ".suomitiertagger-update-", ".jar.pending");
        boolean keepPending = false;
        try {
            URI downloadUri;
            try {
                downloadUri = URI.create(info.downloadUrl);
            } catch (IllegalArgumentException e) {
                throw new IOException("Update download URL is invalid", e);
            }
            requireHttps(downloadUri);
            HttpRequest request = HttpRequest.newBuilder(downloadUri)
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Update download returned HTTP " + response.statusCode());
            }
            requireHttps(response.uri());

            long written = Files.size(temporary);
            OptionalLong contentLength = response.headers().firstValueAsLong("content-length");
            if (written <= 0 || written > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Update JAR has invalid size: " + written);
            }
            if (contentLength.isPresent() && contentLength.getAsLong() != written) {
                throw new IOException("Update download is incomplete");
            }
            verifyHash(temporary, info.hashAlgorithm, expectedHash);
            validateJarMetadata(temporary, info.latestVersion, minecraftVersion);

            Path normalizedCurrent = normalizeCurrentJar(currentJar, normalizedModsDir);
            if (normalizedCurrent != null && Files.isRegularFile(normalizedCurrent)) {
                launchDeferredInstaller(normalizedModsDir, normalizedCurrent, temporary, target);
                keepPending = true;
                return new InstallResult(target, temporary, true);
            }

            atomicMove(temporary, target);
            return new InstallResult(target, null, false);
        } finally {
            if (!keepPending) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void launchDeferredInstaller(
            Path modsDir,
            Path currentJar,
            Path pendingJar,
            Path targetJar
    ) throws IOException {
        Path helperDirectory = Files.createTempDirectory("suomitiertagger-update-helper-");
        Path helperJar = helperDirectory.resolve("update-helper.jar");
        boolean launched = false;
        try {
            // Running helper from current mod JAR would keep that JAR locked.
            Files.copy(currentJar, helperJar);

            String executableName = System.getProperty("os.name", "")
                    .toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", executableName)
                    .toAbsolutePath().normalize();
            if (!Files.isRegularFile(javaExecutable)) {
                throw new IOException("Java executable was not found");
            }

            Process helperProcess = new ProcessBuilder(
                    javaExecutable.toString(),
                    "-cp",
                    helperJar.toString(),
                    UpdateFinisher.class.getName(),
                    Long.toString(ProcessHandle.current().pid()),
                    helperDirectory.toString(),
                    modsDir.toString(),
                    currentJar.toString(),
                    pendingJar.toString(),
                    targetJar.toString()
            )
                    .redirectInput(ProcessBuilder.Redirect.PIPE)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                if (helperProcess.waitFor(250L, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    throw new IOException("Update helper exited before Minecraft shutdown");
                }
            } catch (InterruptedException e) {
                helperProcess.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while starting update helper", e);
            }
            launched = true;
        } finally {
            if (!launched) {
                Files.deleteIfExists(helperJar);
                Files.deleteIfExists(helperDirectory);
            }
        }
    }

    static void verifyHash(Path file, String algorithm, String expectedHash) throws IOException {
        byte[] expected = HexFormat.of().parseHex(normalizeHash(algorithm, expectedHash));
        byte[] actual;
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            actual = digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(algorithm + " is unavailable", e);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IOException("Update " + algorithm + " verification failed");
        }
    }

    static void validateJarMetadata(Path jarPath, String expectedVersion, String minecraftVersion) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile(), true)) {
            JarEntry metadataEntry = jar.getJarEntry("fabric.mod.json");
            if (metadataEntry == null) throw new IOException("Update JAR has no fabric.mod.json");
            JsonObject metadata;
            try (InputStreamReader reader = new InputStreamReader(
                    jar.getInputStream(metadataEntry), StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) throw new IOException("Invalid fabric.mod.json");
                metadata = parsed.getAsJsonObject();
            }

            if (!MOD_ID.equals(getString(metadata, "id"))) {
                throw new IOException("Update JAR has unexpected mod id");
            }
            String jarVersion = getString(metadata, "version");
            if (jarVersion == null || !baseVersion(jarVersion).equals(baseVersion(expectedVersion))) {
                throw new IOException("Update JAR version does not match update metadata");
            }
            JsonObject depends = metadata.has("depends") && metadata.get("depends").isJsonObject()
                    ? metadata.getAsJsonObject("depends")
                    : null;
            if (depends == null || !depends.has("minecraft")
                    || !mentionsMinecraftVersion(depends.get("minecraft"), minecraftVersion)) {
                throw new IOException("Update JAR does not target Minecraft " + minecraftVersion);
            }
        } catch (SecurityException e) {
            throw new IOException("Update JAR signature verification failed", e);
        }
    }

    private static boolean mentionsMinecraftVersion(JsonElement requirement, String version) {
        if (requirement == null || requirement.isJsonNull() || version == null) return false;
        if (requirement.isJsonArray()) {
            for (JsonElement element : requirement.getAsJsonArray()) {
                if (mentionsMinecraftVersion(element, version)) return true;
            }
            return false;
        }
        if (!requirement.isJsonPrimitive()) return false;
        String value = requirement.getAsString().trim();
        return value.equals("*") || value.matches(
                ".*(?<![0-9.])" + java.util.regex.Pattern.quote(version) + "(?![0-9.]).*");
    }

    static boolean hasValidHash(String algorithm, String hash) {
        int hexLength = "SHA-512".equalsIgnoreCase(algorithm) ? 128
                : "SHA-256".equalsIgnoreCase(algorithm) ? 64 : -1;
        return hexLength > 0 && hash != null
                && hash.trim().matches("(?i)[0-9a-f]{" + hexLength + "}");
    }

    private static String normalizeHash(String algorithm, String hash) throws IOException {
        if (!hasValidHash(algorithm, hash)) {
            throw new IOException("Update metadata has no valid supported checksum");
        }
        return hash.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String versionForFile(String version, String minecraftVersion) throws IOException {
        if (version == null || version.isBlank()) throw new IOException("Update metadata has no version");
        if (minecraftVersion == null || minecraftVersion.isBlank() || version.contains("+mc")) return version;
        return version + "+mc" + minecraftVersion;
    }

    private static void requireHttps(URI uri) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("Update download must use HTTPS");
        }
    }

    private static Path normalizeCurrentJar(Path currentJar, Path modsDir) {
        if (currentJar == null) return null;
        Path normalized = currentJar.toAbsolutePath().normalize();
        return normalized.getParent() != null && normalized.getParent().equals(modsDir) ? normalized : null;
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String baseVersion(String version) {
        if (version == null) return "";
        int suffix = version.indexOf('+');
        return (suffix >= 0 ? version.substring(0, suffix) : version).trim();
    }

    record InstallResult(Path installedJar, Path pendingJar, boolean deferred) {}
}
