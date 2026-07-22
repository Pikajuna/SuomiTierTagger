package com.tiertagger.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateInstallerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void verifiesExpectedHashesAndRejectsMismatch() throws IOException {
        Path file = temporaryDirectory.resolve("update.jar");
        Files.writeString(file, "verified update", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> UpdateInstaller.verifyHash(file, "SHA-256",
                "59f19f34399b14e5f1628642e9ce341d660094ba76898e4db6b1875f525b6a6a"));
        assertDoesNotThrow(() -> UpdateInstaller.verifyHash(file, "SHA-512",
                "475ce9ffbf5a4de2b7640d459c02c74a7b76c4d1153083b394de8dbf326fa5c3"
                        + "8632d9e3e2cffbcefa7f9f3f19a10ac113a44d896975320a54adb0171a211184"));
        assertThrows(IOException.class, () -> UpdateInstaller.verifyHash(file, "SHA-256",
                "0000000000000000000000000000000000000000000000000000000000000000"));
        assertThrows(IOException.class, () -> UpdateInstaller.verifyHash(file, "SHA-512", null));
    }

    @Test
    void validatesFabricJarIdentityVersionAndMinecraftTarget() throws IOException {
        Path valid = createJar("suomitiertagger", "6.4.0+mc1.21.11", "1.21.11");
        assertDoesNotThrow(() -> UpdateInstaller.validateJarMetadata(valid, "6.4.0", "1.21.11"));

        Path wrongId = createJar("other-mod", "6.4.0+mc1.21.11", "1.21.11");
        Path wrongVersion = createJar("suomitiertagger", "6.5.0+mc1.21.11", "1.21.11");
        Path wrongMinecraft = createJar("suomitiertagger", "6.4.0+mc1.21.12", "1.21.12");
        assertThrows(IOException.class,
                () -> UpdateInstaller.validateJarMetadata(wrongId, "6.4.0", "1.21.11"));
        assertThrows(IOException.class,
                () -> UpdateInstaller.validateJarMetadata(wrongVersion, "6.4.0", "1.21.11"));
        assertThrows(IOException.class,
                () -> UpdateInstaller.validateJarMetadata(wrongMinecraft, "6.4.0", "1.21.11"));
    }

    @Test
    void finisherReplacesJarAfterProcessExit() throws IOException {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("mods"));
        Path current = mods.resolve("suomitiertagger-6.3.0+mc1.21.11.jar");
        Path pending = mods.resolve(".suomitiertagger-update-test.jar.pending");
        Path target = mods.resolve("suomitiertagger-6.4.0+mc1.21.11.jar");
        Files.writeString(current, "old", StandardCharsets.UTF_8);
        Files.writeString(pending, "new", StandardCharsets.UTF_8);

        UpdateFinisher.FinishResult result = UpdateFinisher.finishInstall(mods, current, pending, target);

        assertFalse(Files.exists(current));
        assertFalse(Files.exists(pending));
        assertEquals("new", Files.readString(target));
        assertEquals("old", Files.readString(result.disabledOldJar()));
        assertTrue(result.disabledOldJar().getFileName().toString().endsWith(".jar.disabled"));
    }

    @Test
    void finisherRejectsTargetCollisionWithoutTouchingCurrentJar() throws IOException {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("collision-mods"));
        Path current = mods.resolve("suomitiertagger-6.3.0.jar");
        Path pending = mods.resolve(".suomitiertagger-update-test.jar.pending");
        Path target = mods.resolve("suomitiertagger-6.4.0.jar");
        Files.writeString(current, "old", StandardCharsets.UTF_8);
        Files.writeString(pending, "new", StandardCharsets.UTF_8);
        Files.writeString(target, "existing", StandardCharsets.UTF_8);

        assertThrows(IOException.class,
                () -> UpdateFinisher.finishInstall(mods, current, pending, target));
        assertEquals("old", Files.readString(current));
        assertEquals("new", Files.readString(pending));
        assertEquals("existing", Files.readString(target));
    }

    @Test
    void finisherRejectsPathsOutsideModsDirectory() throws IOException {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("safe-mods"));
        Path current = mods.resolve("suomitiertagger-6.3.0.jar");
        Path pending = temporaryDirectory.resolve("escaped.jar.pending");
        Path target = mods.resolve("suomitiertagger-6.4.0.jar");
        Files.writeString(current, "old", StandardCharsets.UTF_8);
        Files.writeString(pending, "new", StandardCharsets.UTF_8);

        assertThrows(IOException.class,
                () -> UpdateFinisher.finishInstall(mods, current, pending, target));
        assertEquals("old", Files.readString(current));
    }

    private Path createJar(String id, String version, String minecraftVersion) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "metadata-", ".jar");
        String metadata = """
                {"schemaVersion":1,"id":"%s","version":"%s","depends":{"minecraft":"%s"}}
                """.formatted(id, version, minecraftVersion);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("fabric.mod.json"));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }
}
