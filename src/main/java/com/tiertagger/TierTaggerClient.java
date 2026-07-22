package com.tiertagger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.tiertagger.config.AdvancedConfigScreen;
import com.tiertagger.config.ConfigManager;
import com.tiertagger.net.RankService;
import com.tiertagger.screen.SttPlayerLookupScreen;
import com.tiertagger.update.UpdateAvailableScreen;
import com.tiertagger.update.UpdateChecker;
import com.tiertagger.update.UpdateFinisher;
import com.tiertagger.util.GameProfileCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class TierTaggerClient implements ClientModInitializer {
    private static final DateTimeFormatter STATUS_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Minecraft MC = Minecraft.getInstance();

    private static final int NAMETAG_CACHE_MAX = 500;
    public static final Map<String, net.minecraft.network.chat.Component> nametagCache =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, net.minecraft.network.chat.Component> eldest) {
                    return size() > NAMETAG_CACHE_MAX;
                }
            });

    public static void clearNametagCache() {
        nametagCache.clear();
    }

    public static void putNametagCache(String key, net.minecraft.network.chat.Component value) {
        nametagCache.put(key, value);
    }

    private KeyMapping openMenuKey;
    private Screen queuedScreen;

    @Override
    public void onInitializeClient() {
        System.out.println("[SuomiTierTagger] TierTaggerClient.onInitializeClient starting");
        UpdateFinisher.reportPreviousResult(FabricLoader.getInstance().getGameDir().resolve("mods"));
        try {
            RankService.getInstance().start();
        } catch (Throwable t) {
            System.err.println("[SuomiTierTagger] Failed to start RankService; mod will continue without tiers");
            t.printStackTrace();
        }

        try {
            if (ConfigManager.isAutoUpdateEnabled()) {
                UpdateChecker.getInstance().checkAsync();
            }
        } catch (Throwable t) {
            System.err.println("[SuomiTierTagger] Failed to schedule update check");
            t.printStackTrace();
        }

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.suomitiertagger.open_menu",
                GLFW.GLFW_KEY_K,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("suomitiertagger", "suomitiertagger"))
        ));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearNametagCache());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearNametagCache());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("stt")
                    .then(ClientCommands.argument("player", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> {
                                if (MC != null && MC.getConnection() != null) {
                                    MC.getConnection().getOnlinePlayers().stream()
                                            .map(info -> GameProfileCompat.getName(info.getProfile()))
                                            .filter(name -> name != null && !name.isBlank())
                                            .forEach(builder::suggest);
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                String playerName = StringArgumentType.getString(ctx, "player");
                                displayPlayerTiers(playerName);
                                return 1;
                            })
                    )
                    .then(ClientCommands.literal("refresh")
                        .executes(ctx -> {
                            RankService.getInstance().refreshNow();
                            clearNametagCache();
                            if (MC != null && MC.player != null)
                                MC.player.sendSystemMessage(
                                        net.minecraft.network.chat.Component.translatable("tt.message.refreshing"));
                            return 1;
                        })
                    )
                    .then(ClientCommands.literal("status")
                        .executes(ctx -> {
                            showSyncStatus(RankService.getInstance().getSyncStatus());
                            if (MC != null && MC.player != null) {
                                ConfigManager.SlotPosition a = ConfigManager.getSlotAPosition();
                                MC.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                        "tt.status.settings",
                                        a.name(),
                                        net.minecraft.network.chat.Component.translatable(ConfigManager.isSeparatorAdaptive()
                                                ? "tt.separator.adaptive" : "tt.separator.static"),
                                        net.minecraft.network.chat.Component.translatable(ConfigManager.isShowPeakInNametag()
                                                ? "tt.state.on" : "tt.state.off")
                                ));
                            }
                            return 1;
                        })
                    )
                    .executes(ctx -> {
                        if (MC != null && MC.player != null) {
                            MC.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("tt.command.usage"));
                        }
                        return 1;
                    })
            );

        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey != null && openMenuKey.consumeClick()) {
                if (MC.screen == null) {
                    MC.setScreen(new AdvancedConfigScreen(null));
                }
            }

            if (queuedScreen != null) {
                client.setScreen(queuedScreen);
                queuedScreen = null;
            }

            if (client != null
                    && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen
                    && UpdateChecker.State.checked
                    && UpdateChecker.State.latest != null) {
                var info = UpdateChecker.State.latest;
                UpdateChecker.State.latest = null;
                client.setScreen(new UpdateAvailableScreen(client.screen, info));
            }
        });
    }

    private static void showSyncStatus(RankService.SyncStatus status) {
        if (MC == null || MC.player == null) return;
        long now = System.currentTimeMillis();
        addStatusLine(net.minecraft.network.chat.Component.translatable("tt.status.title"));
        addStatusLine(net.minecraft.network.chat.Component.translatable("tt.status.last_success", status.lastSuccessfulUpdateMillis() == 0
                ? net.minecraft.network.chat.Component.translatable("tt.status.never")
                : STATUS_TIME.format(Instant.ofEpochMilli(status.lastSuccessfulUpdateMillis()))));
        addStatusLine(net.minecraft.network.chat.Component.translatable("tt.status.failed_endpoints", status.failedEndpoints().isEmpty()
                ? net.minecraft.network.chat.Component.translatable("tt.status.none")
                : String.join(", ", status.failedEndpoints())));
        addStatusLine(net.minecraft.network.chat.Component.translatable("tt.status.player_count", status.loadedPlayerCount()));
        addStatusLine(net.minecraft.network.chat.Component.translatable("tt.status.data_age", status.dataAgeMillis() < 0
                ? net.minecraft.network.chat.Component.translatable("tt.status.no_data")
                : formatDuration(status.dataAgeMillis())));
        addStatusLine(net.minecraft.network.chat.Component.translatable("tt.status.next_refresh", status.nextRefreshMillis() <= 0
                ? net.minecraft.network.chat.Component.translatable("tt.status.after_refresh")
                : net.minecraft.network.chat.Component.translatable("tt.status.at_in",
                        STATUS_TIME.format(Instant.ofEpochMilli(status.nextRefreshMillis())),
                        formatDuration(Math.max(0, status.nextRefreshMillis() - now)))));
        addStatusLine(net.minecraft.network.chat.Component.translatable("tt.status.in_progress",
                net.minecraft.network.chat.Component.translatable(status.refreshInProgress() ? "tt.state.yes" : "tt.state.no")));
    }

    private static void addStatusLine(net.minecraft.network.chat.Component text) {
        MC.player.sendSystemMessage(text);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1_000);
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainingSeconds = seconds % 60;
        if (days > 0) return net.minecraft.client.resources.language.I18n.get("tt.duration.days_hours", days, hours);
        if (hours > 0) return net.minecraft.client.resources.language.I18n.get("tt.duration.hours_minutes", hours, minutes);
        if (minutes > 0) return net.minecraft.client.resources.language.I18n.get("tt.duration.minutes_seconds", minutes, remainingSeconds);
        return net.minecraft.client.resources.language.I18n.get("tt.duration.seconds", remainingSeconds);
    }

    private void displayPlayerTiers(String playerName) {
        if (MC == null) return;
        SttPlayerLookupScreen.ResultData result = SttPlayerLookupScreen.buildResult(MC, resolveSttPlayerName(playerName));
        queuedScreen = new SttPlayerLookupScreen(result);
    }


    private static @Nullable String resolveSttPlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }

        String trimmed = playerName.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }

        if (MC == null || MC.getConnection() == null) {
            return trimmed;
        }

        String lowerQuery = trimmed.toLowerCase(Locale.ROOT);
        for (PlayerInfo entry : MC.getConnection().getOnlinePlayers()) {
            String profileName = GameProfileCompat.getName(entry.getProfile());
            if (profileName != null && profileName.toLowerCase(Locale.ROOT).equals(lowerQuery)) {
                return profileName;
            }
        }

        return trimmed;
    }
}
