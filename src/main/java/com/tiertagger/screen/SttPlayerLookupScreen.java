package com.tiertagger.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import com.tiertagger.config.ConfigManager;
import com.tiertagger.net.RankService;
import com.tiertagger.util.TierUtils;
import com.tiertagger.ui.OverlayTheme;
import com.tiertagger.util.GameProfileCompat;

public class SttPlayerLookupScreen extends Screen {
    private static final int TIER_CHIP_SIZE = 22;
    private static final int TIER_ROW_HEIGHT = 28;
    private static final int TIER_ROW_GAP = 12;
    private static final int SKIN_WIDTH = 160;
    private static final int SKIN_HEIGHT = 300;
    private static final int COLUMN_GAP = 48;
    private static final int CLOSE_BUTTON_WIDTH = 20;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final Identifier KIT_ICON_FONT = Identifier.fromNamespaceAndPath("minecraft", "tiertagger-icons");
    private static final int ICON_FONT_HEIGHT = 22;
    private static final int ICON_FONT_ASCENT = 8;
    private static final float ICON_SCALE = 0.75f;
    private static final float ICON_CENTER_X_NUDGE = -1.5f;
    private static final int POSITION_GOLD = 0xFFFFD700;
    private static final int POSITION_SILVER = 0xFFC0C0C0;
    private static final int POSITION_BRONZE = 0xFFCD7F32;

    private final ResultData data;
    private PlayerSkinWidget skinWidget;

    public SttPlayerLookupScreen(ResultData data) {
        super(Component.translatable("tt.lookup.title"));
        this.data = java.util.Objects.requireNonNull(data, "data");
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("X"), button -> onClose())
                .bounds(this.width - CLOSE_BUTTON_WIDTH - 10, 10, CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
                .build());
        if (minecraft != null && !data.playerName().isBlank()) {
            ResolvableProfile profile = ResolvableProfile.createUnresolved(data.playerName());
            skinWidget = new PlayerSkinWidget(
                    SKIN_WIDTH,
                    SKIN_HEIGHT,
                    minecraft.getEntityModels(),
                    () -> minecraft.playerSkinRenderCache().createLookup(profile).get().playerSkin()
            );
            Layout layout = layout();
            skinWidget.setPosition(layout.skinX(), layout.skinY());
            addRenderableWidget(skinWidget);
        }
    }

    public static ResultData buildResult(Minecraft client, String rawPlayerName) {
        String playerName = rawPlayerName == null ? "" : rawPlayerName.trim();
        RankService rankService = RankService.getInstance();
        Map<String, String> directKitTiers = rankService.getAllKitTiersForName(playerName);
        RankService.OverallBest overallBest = rankService.getOverallBestForName(playerName);
        Map<String, String> retiredKitTiers = rankService.getAllRetiredKitTiersForName(playerName);
        Map<String, String> peakTiers = rankService.getPeakKitTiersForName(playerName);
        RankService.OverallPlacement overallPlacement = rankService.getOverallPlacementForName(playerName);

        List<KitTierEntry> orderedEntries = new ArrayList<>();
        for (String kit : canonicalKitOrder()) {
            String tier = firstNonBlank(
                    directKitTiers.get(kit),
                    retiredKitTiers.get(kit),
                    overallBest != null && kit.equals(overallBest.kit) ? overallBest.tierCode : null
            );
            orderedEntries.add(new KitTierEntry(kit, tier));
        }

        String overallTier = overallBest != null ? overallBest.tierCode : null;
        String bestKit = overallBest != null ? overallBest.kit : null;
        if (overallTier == null || bestKit == null) {
            KitTierEntry bestFromKits = findBestKitTier(orderedEntries);
            if (bestFromKits != null) {
                overallTier = bestFromKits.tierCode();
                bestKit = bestFromKits.kit();
            }
        }

        PlayerInfo playerEntry = findPlayerEntry(client, playerName);
        String resolvedPlayerName = playerEntry != null
                ? firstNonBlank(GameProfileCompat.getName(playerEntry.getProfile()), playerName)
                : playerName;
        String displayName = firstNonBlank(resolvedPlayerName, playerName, "");

        boolean notFound = overallTier == null && orderedEntries.stream().noneMatch(KitTierEntry::hasTier);
        return new ResultData(
                displayName,
                notFound,
                overallTier,
                bestKit,
                overallPlacement == null ? null : overallPlacement.placement(),
                overallPlacement == null ? null : overallPlacement.points(),
                List.copyOf(orderedEntries),
                Map.copyOf(peakTiers)
        );
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // This screen draws its own overlay and does not use the vanilla blur path.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        OverlayTheme.drawScrim(context, this.width, this.height);
        drawPlayerName(context, layout);
        drawTierRows(context, layout);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private Layout layout() {
        int infoColumnWidth = 160;
        int totalWidth = SKIN_WIDTH + COLUMN_GAP + infoColumnWidth;

        int blockX = (this.width - totalWidth) / 2;
        int blockY = (this.height - SKIN_HEIGHT) / 2;

        int infoX = blockX + SKIN_WIDTH + COLUMN_GAP;
        int nameY = blockY + 4;
        int rankingY = nameY + 18;
        int dividerY = rankingY + 13;
        int firstTierRowY = dividerY + 10;

        return new Layout(
                blockX,
                blockY,
                infoX,
                nameY,
                rankingY,
                dividerY,
                firstTierRowY,
                infoColumnWidth
        );
    }

    private void drawPlayerName(GuiGraphicsExtractor context, Layout layout) {
        String name = data.playerName().isBlank() ? I18n.get("tt.lookup.unknown_player") : data.playerName();
        drawScaledText(context, name, layout.infoX(), layout.nameY(), 1.5f, OverlayTheme.TEXT_PRIMARY);
        if (data.overallPlacement() != null && data.totalPoints() != null) {
            String position = I18n.get("tt.lookup.position", data.overallPlacement());
            String points = I18n.get("tt.lookup.points", data.totalPoints());
            context.text(
                    this.font,
                    position,
                    layout.infoX(),
                    layout.rankingY(),
                    positionColor(data.overallPlacement())
            );
            context.text(
                    this.font,
                    points,
                    layout.infoX() + this.font.width(position),
                    layout.rankingY(),
                    OverlayTheme.TEXT_MUTED
            );
        }
        context.fill(layout.infoX(), layout.dividerY(), layout.infoX() + layout.infoColumnWidth(), layout.dividerY() + 1, OverlayTheme.SHELL_EDGE_SOFT);
    }

    private void drawTierRows(GuiGraphicsExtractor context, Layout layout) {
        List<KitTierEntry> kits = data.kitTiers();
        for (int i = 0; i < kits.size(); i++) {
            int rowY = layout.firstTierRowY() + i * TIER_ROW_HEIGHT;
            drawTierRow(context, layout.infoX(), rowY, kits.get(i));
        }
    }

    private void drawTierRow(GuiGraphicsExtractor context, int x, int y, KitTierEntry entry) {
        boolean hasTier = entry.hasTier();
        int tierColor = hasTier
                ? toArgb(ConfigManager.colorFor(stripRetiredPrefix(entry.tierCode())))
                : OverlayTheme.TEXT_MUTED;
        String tierText = hasTier ? entry.tierCode() : "—";

        int textY = y + (TIER_ROW_HEIGHT - this.font.lineHeight) / 2;
        int boxSize = TIER_CHIP_SIZE;
        int boxX = x;
        int boxY = y + (TIER_ROW_HEIGHT - boxSize) / 2;
        context.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0x66000000);
        context.fill(boxX, boxY, boxX + boxSize, boxY + 1, 0xFF444444);
        context.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0xFF444444);
        context.fill(boxX, boxY, boxX + 1, boxY + boxSize, 0xFF444444);
        context.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0xFF444444);
        drawKitGlyphCentered(context, boxX, boxY, boxSize, glyphForKit(entry.kit()), 0xFFFFFFFF);

        int textX = x + TIER_CHIP_SIZE + TIER_ROW_GAP;
        context.text(this.font, tierText, textX, textY, tierColor);

        if (hasTier && ConfigManager.isShowPeakInNametag()) {
            String peak = data.peakTiers().get(entry.kit());
            if (peak != null && !peak.isBlank()) {
                String basePeak    = stripRetiredPrefix(peak);
                String baseCurrent = stripRetiredPrefix(entry.tierCode());
                if (TierUtils.tierScore(basePeak) < TierUtils.tierScore(baseCurrent)) {
                    int peakColor  = toArgb(ConfigManager.colorFor("PEAK_INDICATOR"));
                    String peakLabel = " ↑" + peak;
                    int peakX = textX + this.font.width(tierText) + 2;
                    context.text(this.font, peakLabel, peakX, textY, peakColor);
                }
            }
        }
    }

    private void drawKitGlyphCentered(GuiGraphicsExtractor context, int boxX, int boxY, int boxSize, char glyph, int color) {
        Component iconText = Component.literal(String.valueOf(glyph))
                .setStyle(net.minecraft.network.chat.Style.EMPTY.withFont(
                        new net.minecraft.network.chat.FontDescription.Resource(KIT_ICON_FONT)));
        int glyphWidth = this.font.width(iconText);
        float scaledWidth = glyphWidth * ICON_SCALE;
        float scaledHeight = ICON_FONT_HEIGHT * ICON_SCALE;
        float glyphTopOffset = (7 - ICON_FONT_ASCENT) * ICON_SCALE;
        float iconX = boxX + (boxSize - scaledWidth) / 2.0f + ICON_CENTER_X_NUDGE;
        float iconY = boxY + (boxSize - scaledHeight) / 2.0f - glyphTopOffset;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(iconX, iconY);
        matrices.scale(ICON_SCALE, ICON_SCALE);
        context.text(this.font, iconText, 0, 0, color, false);
        matrices.popMatrix();
    }

    private void drawScaledText(GuiGraphicsExtractor context, String text, int x, int y, float scale, int color) {
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(y / scale);
        context.text(this.font, text, scaledX, scaledY, color);
        matrices.popMatrix();
    }

    private static int toArgb(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static int positionColor(int placement) {
        return switch (placement) {
            case 1 -> POSITION_GOLD;
            case 2 -> POSITION_SILVER;
            case 3 -> POSITION_BRONZE;
            default -> OverlayTheme.TEXT_PRIMARY;
        };
    }

    private static String stripRetiredPrefix(String tierCode) {
        if (tierCode == null || tierCode.isBlank()) return tierCode;
        return tierCode.startsWith("R") ? tierCode.substring(1) : tierCode;
    }

    private static @Nullable KitTierEntry findBestKitTier(List<KitTierEntry> entries) {
        KitTierEntry best = null;
        int bestScore = Integer.MAX_VALUE;
        for (KitTierEntry entry : entries) {
            if (!entry.hasTier()) continue;
            int score = TierUtils.tierScore(stripRetiredPrefix(entry.tierCode()));
            if (score < bestScore) {
                best = entry;
                bestScore = score;
            }
        }
        return best;
    }

    private static String[] canonicalKitOrder() {
        return new String[]{"Sword", "Axe", "NethPot", "UHC", "DiaPot", "Crystal", "SMP", "Mace"};
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static @Nullable PlayerInfo findPlayerEntry(Minecraft client, String playerName) {
        if (client == null || client.getConnection() == null || playerName == null || playerName.isBlank()) {
            return null;
        }
        for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
            String profileName = GameProfileCompat.getName(entry.getProfile());
            if (profileName != null && profileName.equalsIgnoreCase(playerName)) return entry;
        }
        return null;
    }

    private static char glyphForKit(String canonicalKit) {
        return switch (canonicalKit) {
            case "Sword"   -> '\uE706';
            case "Axe"     -> '\uE701';
            case "NethPot" -> '\uE703';
            case "UHC"     -> '\uE707';
            case "DiaPot"  -> '\uE704';
            case "Crystal" -> '\uE708';
            case "SMP"     -> '\uE705';
            case "Mace"    -> '\uE702';
            default        -> '\uE706';
        };
    }

    private record Layout(
            int skinX,
            int skinY,
            int infoX,
            int nameY,
            int rankingY,
            int dividerY,
            int firstTierRowY,
            int infoColumnWidth
    ) {}

    public record ResultData(
            String playerName,
            boolean notFound,
            @Nullable String overallTierCode,
            @Nullable String bestKit,
            @Nullable Integer overallPlacement,
            @Nullable Integer totalPoints,
            List<KitTierEntry> kitTiers,
            Map<String, String> peakTiers
    ) {
        public boolean hasAnyTierData() {
            return kitTiers.stream().anyMatch(KitTierEntry::hasTier);
        }
    }

    public record KitTierEntry(String kit, @Nullable String tierCode) {
        public boolean hasTier() {
            return tierCode != null && !tierCode.isBlank();
        }
    }
}
