package com.tiertagger.update;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Completes a staged update in a separate JVM after Minecraft exits.
 * Windows keeps the running mod JAR locked until then.
 */
public final class UpdateFinisher {
    private static final String RESULT_FILE = ".suomitiertagger-update-result.txt";

    private UpdateFinisher() {}

    public static void main(String[] args) {
        Path helperDirectory = null;
        Path modsDirectory = null;
        try {
            if (args.length != 6) {
                throw new IllegalArgumentException("Expected parent PID, helper directory, mods directory, current JAR, pending JAR, and target JAR");
            }

            long parentPid = Long.parseLong(args[0]);
            helperDirectory = Path.of(args[1]).toAbsolutePath().normalize();
            modsDirectory = Path.of(args[2]).toAbsolutePath().normalize();
            Path currentJar = Path.of(args[3]);
            Path pendingJar = Path.of(args[4]);
            Path targetJar = Path.of(args[5]);

            waitForExit(parentPid);
            FinishResult result = finishInstall(modsDirectory, currentJar, pendingJar, targetJar);
            writeResult(modsDirectory, "SUCCESS\nInstalled: " + result.installedJar()
                    + "\nDisabled: " + result.disabledOldJar());
        } catch (Throwable error) {
            if (modsDirectory != null) {
                try {
                    writeResult(modsDirectory, "FAILED\n" + safeMessage(error));
                } catch (IOException ignored) {
                }
            }
        } finally {
            scheduleHelperCleanup(helperDirectory);
        }
    }

    /** Reports and removes result left by helper on next game launch. */
    public static void reportPreviousResult(Path modsDirectory) {
        if (modsDirectory == null) return;
        Path result = modsDirectory.toAbsolutePath().normalize().resolve(RESULT_FILE);
        if (!Files.isRegularFile(result)) return;
        try {
            String message = Files.readString(result);
            if (message.startsWith("SUCCESS")) {
                System.out.println("[SuomiTierTagger] Deferred update completed:\n" + message);
            } else {
                System.err.println("[SuomiTierTagger] Deferred update failed:\n" + message);
            }
            Files.deleteIfExists(result);
        } catch (IOException error) {
            System.err.println("[SuomiTierTagger] Could not read deferred update result: " + safeMessage(error));
        }
    }

    static FinishResult finishInstall(
            Path modsDirectory,
            Path currentJar,
            Path pendingJar,
            Path targetJar
    ) throws IOException {
        Path normalizedMods = modsDirectory.toAbsolutePath().normalize();
        Path normalizedCurrent = requireManagedPath(currentJar, normalizedMods, ".jar");
        Path normalizedPending = requireManagedPath(pendingJar, normalizedMods, ".jar.pending");
        Path normalizedTarget = requireManagedPath(targetJar, normalizedMods, ".jar");

        if (normalizedCurrent.equals(normalizedTarget)) {
            throw new IOException("Current and target update JAR paths are identical");
        }
        if (!Files.isRegularFile(normalizedPending)) {
            throw new IOException("Pending update JAR is missing");
        }
        if (!Files.isRegularFile(normalizedCurrent)) {
            throw new IOException("Current mod JAR is missing");
        }
        if (Files.exists(normalizedTarget)) {
            throw new IOException("Update target already exists");
        }

        Path disabledOldJar = uniqueDisabledPath(normalizedCurrent);
        atomicMove(normalizedCurrent, disabledOldJar);
        try {
            atomicMove(normalizedPending, normalizedTarget);
        } catch (IOException installFailure) {
            try {
                if (Files.exists(disabledOldJar) && !Files.exists(normalizedCurrent)) {
                    atomicMove(disabledOldJar, normalizedCurrent);
                }
            } catch (IOException restoreFailure) {
                installFailure.addSuppressed(restoreFailure);
            }
            throw installFailure;
        }

        return new FinishResult(normalizedTarget, disabledOldJar);
    }

    private static void waitForExit(long parentPid) throws InterruptedException {
        ProcessHandle parent = ProcessHandle.of(parentPid).orElse(null);
        while (parent != null && parent.isAlive()) {
            Thread.sleep(250L);
        }
    }

    private static Path requireManagedPath(Path path, Path modsDirectory, String suffix) throws IOException {
        if (path == null) throw new IOException("Update path is missing");
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getParent() == null || !normalized.getParent().equals(modsDirectory)) {
            throw new IOException("Update path escapes mods directory");
        }
        if (!normalized.getFileName().toString().endsWith(suffix)) {
            throw new IOException("Update path has unexpected filename");
        }
        return normalized;
    }

    private static Path uniqueDisabledPath(Path currentJar) {
        String base = currentJar.getFileName() + ".disabled";
        Path candidate = currentJar.resolveSibling(base);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = currentJar.resolveSibling(base + "." + suffix++);
        }
        return candidate;
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void writeResult(Path modsDirectory, String message) throws IOException {
        Path normalizedMods = modsDirectory.toAbsolutePath().normalize();
        Files.writeString(normalizedMods.resolve(RESULT_FILE), message);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static void scheduleHelperCleanup(Path helperDirectory) {
        if (helperDirectory == null) return;
        try {
            Path normalized = helperDirectory.toAbsolutePath().normalize();
            Path tempRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            if (normalized.getParent() == null
                    || !normalized.getParent().equals(tempRoot)
                    || !normalized.getFileName().toString().startsWith("suomitiertagger-update-helper-")) {
                return;
            }

            normalized.toFile().deleteOnExit();
            try (var children = Files.list(normalized)) {
                children.forEach(path -> path.toFile().deleteOnExit());
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    record FinishResult(Path installedJar, Path disabledOldJar) {}
}
