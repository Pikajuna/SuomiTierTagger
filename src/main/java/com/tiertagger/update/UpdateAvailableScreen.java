package com.tiertagger.update;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Simple GUI popup that informs the player about a new version and
 * can download the updated JAR into the mods directory.
 */
public class UpdateAvailableScreen extends Screen {
    private final Screen parent;
    private final UpdateChecker.UpdateInfo info;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private boolean downloading = false;
    private String statusMessage = "";
    private boolean statusIsError = false;

    private Button ignoreButton;
    private Button updateButton;
    private Button restartButton;
    private Button continueButton;

    public UpdateAvailableScreen(Screen parent, UpdateChecker.UpdateInfo info) {
        super(Component.translatable("tt.update.title"));
        this.parent = parent;
        this.info = info;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        ignoreButton = Button.builder(
                        Component.translatable("tt.update.skip"),
                        button -> onClose()
                )
                .bounds(centerX - 100, centerY + 40, 90, 20)
                .build();
        this.addRenderableWidget(ignoreButton);

        updateButton = Button.builder(
                        Component.translatable("tt.update.now"),
                        button -> startVerifiedDownload()
                )
                .bounds(centerX + 10, centerY + 40, 90, 20)
                .build();
        this.addRenderableWidget(updateButton);
    }

    private void startVerifiedDownload() {
        if (downloading) return;
        if (!UpdateInstaller.hasValidHash(info.hashAlgorithm, info.hash)) {
            statusMessage = I18n.get("tt.update.missing_checksum");
            statusIsError = true;
            return;
        }

        downloading = true;
        statusMessage = I18n.get("tt.update.downloading");
        statusIsError = false;
        setInitialButtonsVisible(false);

        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        Path currentJar = findCurrentJar().orElse(null);
        String minecraftVersion = UpdateChecker.resolveMinecraftVersion();
        CompletableFuture.runAsync(() -> {
            try {
                UpdateInstaller.InstallResult result = UpdateInstaller.downloadAndInstall(
                        http, info, modsDir, minecraftVersion, currentJar);
                Minecraft.getInstance().execute(() -> {
                    statusMessage = result.deferred()
                            ? I18n.get("tt.update.staged")
                            : I18n.get("tt.update.installed");
                    statusIsError = false;
                    downloading = false;
                    showPostDownloadButtons();
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = I18n.get("tt.update.failed", safeErrorMessage(e));
                    statusIsError = true;
                    downloading = false;
                    setInitialButtonsVisible(true);
                });
            }
        });
    }

    private Optional<Path> findCurrentJar() {
        return FabricLoader.getInstance().getModContainer("suomitiertagger")
                .stream()
                .flatMap(container -> container.getOrigin().getPaths().stream())
                .filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".jar"))
                .findFirst();
    }

    private void setInitialButtonsVisible(boolean visible) {
        if (ignoreButton != null) {
            ignoreButton.visible = visible;
            ignoreButton.active = visible;
        }
        if (updateButton != null) {
            updateButton.visible = visible;
            updateButton.active = visible;
        }
    }

    private static String safeErrorMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Let the base Screen handle background + widgets (buttons) first.
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int y = this.height / 2 - 40;

        // While downloading, only show the progress text.
        if (downloading) {
            drawWrappedStatus(context, centerX, y);
            return;
        }

        // After a successful download (restart/continue buttons visible),
        // just show the completion text.
        if (restartButton != null && restartButton.visible) {
            drawWrappedStatus(context, centerX, y);
            return;
        }

        // Normal pre-download / error state: show full info.
        // Title in Finnish
        context.centeredText(this.font,
                Component.translatable("tt.update.available"),
                centerX, y, 0xFF55FFFF); // light blue
        y += 14;

        // Version info (strip +mc suffix for display)
        String currentDisplay = stripMinecraftSuffix(info.currentVersion);
        String latestDisplay = stripMinecraftSuffix(info.latestVersion);

        // Old version in red
        context.centeredText(this.font,
                Component.translatable("tt.update.current", currentDisplay),
                centerX, y, 0xFFFF5555);
        y += 12;

        // New version in green
        context.centeredText(this.font,
                Component.translatable("tt.update.latest", latestDisplay),
                centerX, y, 0xFF55FF55);
        y += 12;

        // Status message (e.g. errors)
        if (!statusMessage.isEmpty()) {
            drawWrappedStatus(context, centerX, y);
        }
    }

    private void drawWrappedStatus(GuiGraphicsExtractor context, int centerX, int y) {
        if (statusMessage.isEmpty()) return;
        int maxWidth = Math.max(120, this.width - 40);
        int color = statusIsError ? 0xFFFF5555 : 0xFF55FF55;
        for (var line : this.font.split(Component.literal(statusMessage), maxWidth)) {
            context.text(this.font, line, centerX - this.font.width(line) / 2, y, color, true);
            y += this.font.lineHeight + 2;
        }
    }

    private static String stripMinecraftSuffix(String version) {
        if (version == null) return "";
        int idx = version.indexOf("+mc");
        if (idx >= 0) {
            return version.substring(0, idx);
        }
        return version;
    }

    private void showPostDownloadButtons() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (restartButton == null) {
            restartButton = Button.builder(
                    Component.translatable("tt.update.restart"),
                    button -> {
                        Minecraft client = Minecraft.getInstance();
                        if (client != null) {
                            client.stop();
                        }
                    }
            )
            .bounds(centerX - 140, centerY + 40, 130, 20)
            .build();
        }

        if (continueButton == null) {
            continueButton = Button.builder(
                    Component.translatable("tt.update.continue"),
                    button -> onClose()
            )
            .bounds(centerX + 10, centerY + 40, 130, 20)
            .build();
        }

        this.addRenderableWidget(restartButton);
        this.addRenderableWidget(continueButton);
    }
}
