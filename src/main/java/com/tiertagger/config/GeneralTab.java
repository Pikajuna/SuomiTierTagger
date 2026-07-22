package com.tiertagger.config;

import com.tiertagger.ui.OverlayTheme;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

public class GeneralTab {
    private static final int VISIBILITY_CARD_HEIGHT = 184;
    private static final int PRESENTATION_CARD_HEIGHT = 148;
    private static final int PREVIEW_CARD_HEIGHT = 170;
    private static final int FIRST_ROW_OFFSET = 42;
    private static final int ROW_SPACING = 36;
    private static final Identifier ICON_FONT = Identifier.fromNamespaceAndPath("minecraft", "tiertagger-icons-md");

    private final AdvancedConfigScreen parent;

    private boolean tempEnabled;
    private boolean tempWorld;
    private boolean tempTab;
    private boolean tempChat;
    private boolean tempSelf;
    private boolean tempPeak;
    private ConfigManager.SeparatorMode tempSeparatorMode;
    private ConfigManager.SlotPosition tempSlot;

    public GeneralTab(AdvancedConfigScreen parent) {
        this.parent = parent;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        tempEnabled = !ConfigManager.isDisabled();
        tempWorld = ConfigManager.isShowWorldNametags();
        tempTab = ConfigManager.isShowTabTiers();
        tempChat = ConfigManager.isShowChatTiers();
        tempSelf = ConfigManager.get().showSelf == null || ConfigManager.get().showSelf;
        tempPeak = ConfigManager.isShowPeakInNametag();
        tempSeparatorMode = ConfigManager.getSeparatorMode();
        tempSlot = ConfigManager.getSlotAPosition();
    }

    public void init() {
        Layout layout = layout();
        int controlWidth = parent.getControlWidth();

        toggleButton(layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 0, controlWidth,
                () -> tempWorld, () -> tempWorld = !tempWorld);
        toggleButton(layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 1, controlWidth,
                () -> tempTab, () -> tempTab = !tempTab);
        toggleButton(layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 2, controlWidth,
                () -> tempChat, () -> tempChat = !tempChat);
        toggleButton(layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 3, controlWidth,
                () -> tempSelf, () -> tempSelf = !tempSelf);

        toggleButton(layout.presentationX, layout.presentationWidth, layout.presentationY, 0, controlWidth,
                () -> tempPeak, () -> tempPeak = !tempPeak);

        AdvancedConfigScreen.SettingRowLayout separatorRow = row(
                layout.presentationX, layout.presentationWidth, layout.presentationY, 1, controlWidth);
        parent.addThemedButton(Component.translatable(separatorModeKey(tempSeparatorMode)), button -> {
            tempSeparatorMode = cycleSeparatorMode(tempSeparatorMode);
            button.setMessage(Component.translatable(separatorModeKey(tempSeparatorMode)));
            settingChanged();
        }, separatorRow.controlX(), separatorRow.controlY(), separatorRow.controlWidth(), separatorRow.controlHeight(),
                AdvancedConfigScreen.ButtonStyle.SEGMENT, () -> true);

        AdvancedConfigScreen.SettingRowLayout slotRow = row(
                layout.presentationX, layout.presentationWidth, layout.presentationY, 2, controlWidth);
        parent.addThemedButton(Component.translatable(slotPositionKey(tempSlot)), button -> {
            tempSlot = cyclePosition(tempSlot);
            button.setMessage(Component.translatable(slotPositionKey(tempSlot)));
            settingChanged();
        }, slotRow.controlX(), slotRow.controlY(), slotRow.controlWidth(), slotRow.controlHeight(),
                AdvancedConfigScreen.ButtonStyle.SEGMENT, () -> true);
    }

    private void toggleButton(int cardX, int cardWidth, int cardY, int rowIndex, int controlWidth,
                              BooleanSupplier state, Runnable toggle) {
        AdvancedConfigScreen.SettingRowLayout row = row(cardX, cardWidth, cardY, rowIndex, controlWidth);
        parent.addThemedButton(Component.translatable(state.getAsBoolean() ? "tt.state.on" : "tt.state.off"), button -> {
            toggle.run();
            button.setMessage(Component.translatable(state.getAsBoolean() ? "tt.state.on" : "tt.state.off"));
            settingChanged();
        }, row.controlX(), row.controlY(), row.controlWidth(), row.controlHeight(),
                AdvancedConfigScreen.ButtonStyle.TOGGLE, state);
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        int pad = parent.getCardPadding();
        int controlWidth = parent.getControlWidth();
        int visibilityY = parent.contentY(layout.visibilityY);
        int presentationY = parent.contentY(layout.presentationY);
        int previewY = parent.contentY(layout.previewY);

        parent.drawCard(context, layout.visibilityX, visibilityY, layout.visibilityWidth, visibilityCardHeight(layout.visibilityWidth), true);
        parent.drawCardTitle(context, tr("tt.general.visibility"), tr("tt.general.visibility.help"),
                layout.visibilityX + pad, visibilityY + 10);
        drawSetting(context, layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 0, controlWidth,
                "tt.general.world", "tt.general.world.help");
        drawSetting(context, layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 1, controlWidth,
                "tt.general.tab", "tt.general.tab.help");
        drawSetting(context, layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 2, controlWidth,
                "tt.general.chat", "tt.general.chat.help");
        drawSetting(context, layout.visibilityX, layout.visibilityWidth, layout.visibilityY, 3, controlWidth,
                "tt.general.self", "tt.general.self.help");

        parent.drawCard(context, layout.presentationX, presentationY, layout.presentationWidth, presentationCardHeight(layout.presentationWidth), false);
        parent.drawCardTitle(context, tr("tt.general.presentation"), tr("tt.general.presentation.help"),
                layout.presentationX + pad, presentationY + 10);
        drawSetting(context, layout.presentationX, layout.presentationWidth, layout.presentationY, 0, controlWidth,
                "tt.general.peak", "tt.general.peak.help");
        drawSetting(context, layout.presentationX, layout.presentationWidth, layout.presentationY, 1, controlWidth,
                "tt.general.separator", "tt.general.separator.help");
        drawSetting(context, layout.presentationX, layout.presentationWidth, layout.presentationY, 2, controlWidth,
                "tt.general.position", "tt.general.position.help");

        drawLivePreview(context, layout.previewX, previewY, layout.previewWidth);
    }

    private void drawLivePreview(GuiGraphicsExtractor context, int x, int y, int width) {
        int pad = parent.getCardPadding();
        parent.drawCard(context, x, y, width, PREVIEW_CARD_HEIGHT, true);
        parent.drawCardTitle(context, tr("tt.preview.title"), tr("tt.preview.help"), x + pad, y + 10);

        drawPreviewRow(context, x + pad, y + 40, width - pad * 2,
                tr("tt.preview.nametag"), tempEnabled && tempWorld ? previewNametag() : hiddenText());
        drawPreviewRow(context, x + pad, y + 80, width - pad * 2,
                tr("tt.preview.tab"), tempEnabled && tempTab ? previewNametag() : hiddenText());
        drawPreviewRow(context, x + pad, y + 120, width - pad * 2,
                tr("tt.preview.chat"), tempEnabled && tempChat ? previewChat() : hiddenText());
    }

    private void drawPreviewRow(GuiGraphicsExtractor context, int x, int y, int width, String label, Component preview) {
        OverlayTheme.drawInset(context, x, y, width, 32);
        context.text(parent.getFont(), label, x + 7, y + 4, OverlayTheme.TEXT_MUTED, false);
        context.text(parent.getFont(), preview, x + 7, y + 17, OverlayTheme.TEXT_PRIMARY);
    }

    private Component previewNametag() {
        MutableComponent result = Component.empty();
        MutableComponent tier = previewTier();
        Component player = Component.literal("Steve").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xF4F6F8)));
        if (tempSlot == ConfigManager.SlotPosition.RIGHT) {
            result.append(player).append(separator()).append(tier);
        } else if (tempSlot == ConfigManager.SlotPosition.OFF) {
            result.append(player);
        } else {
            result.append(tier).append(separator()).append(player);
        }
        return result;
    }

    private Component previewChat() {
        return Component.empty().append(Component.literal("<Steve> ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xC9CED6))))
                .append(previewTier()).append(Component.literal(" gg"));
    }

    private MutableComponent previewTier() {
        MutableComponent tier = Component.empty();
        String icon = "\uE706";
        tier.append(Component.literal(icon).setStyle(Style.EMPTY.withFont(new FontDescription.Resource(ICON_FONT))));
        tier.append(Component.literal(" HT2").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ConfigManager.colorFor("HT2")))));
        if (tempPeak) {
            tier.append(Component.literal(" ↑HT1").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ConfigManager.colorFor("PEAK_INDICATOR")))));
        }
        return tier;
    }

    private Component separator() {
        if (tempSeparatorMode == ConfigManager.SeparatorMode.OFF) return Component.literal(" ");
        int color = tempSeparatorMode == ConfigManager.SeparatorMode.ADAPTIVE
                ? ConfigManager.colorFor("HT2")
                : ConfigManager.colorFor("SEPARATOR_STATIC");
        return Component.literal(" | ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    private Component hiddenText() {
        return Component.translatable("tt.preview.hidden").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x8D96A3)));
    }

    private void drawSetting(GuiGraphicsExtractor context, int cardX, int cardWidth, int cardY,
                             int rowIndex, int controlWidth, String labelKey, String helpKey) {
        AdvancedConfigScreen.SettingRowLayout row = row(cardX, cardWidth, cardY, rowIndex, controlWidth);
        context.text(parent.getFont(), tr(labelKey), row.labelX(), row.labelY(), parent.getPrimaryTextColor());
        parent.drawSettingHelp(context, tr(helpKey), row, cardX, cardWidth);
    }

    private AdvancedConfigScreen.SettingRowLayout row(int cardX, int cardWidth, int cardY,
                                                       int rowIndex, int controlWidth) {
        int spacing = parent.useStackedSettingRowLayout(cardWidth, controlWidth) ? 48 : ROW_SPACING;
        return parent.layoutAdaptiveSettingRow(cardX, cardWidth, cardY,
                FIRST_ROW_OFFSET + spacing * rowIndex, controlWidth);
    }

    public boolean isEnabled() {
        return tempEnabled;
    }

    public void toggleEnabled() {
        tempEnabled = !tempEnabled;
        clearPreviewCache();
    }

    public void save() {
        ConfigManager.get().showSelf = tempSelf;
        ConfigManager.setDisabled(!tempEnabled);
        ConfigManager.setShowWorldNametags(tempWorld);
        ConfigManager.setShowTabTiers(tempTab);
        ConfigManager.setShowChatTiers(tempChat);
        ConfigManager.setShowPeakInNametag(tempPeak);
        ConfigManager.setSeparatorMode(tempSeparatorMode);
        ConfigManager.setSlotAPosition(tempSlot);
        clearPreviewCache();
    }

    public int getContentHeight() {
        Layout layout = layout();
        return Math.max(
                Math.max(layout.visibilityY + visibilityCardHeight(layout.visibilityWidth),
                        layout.presentationY + presentationCardHeight(layout.presentationWidth)),
                layout.previewY + PREVIEW_CARD_HEIGHT
        );
    }

    private Layout layout() {
        int gap = parent.getSectionGap();
        int fullWidth = parent.getContentWidth();
        int x = parent.getContentX();
        if (parent.getLayoutTier() == AdvancedConfigScreen.LayoutTier.WIDE) {
            int leftWidth = (fullWidth - gap) * 58 / 100;
            int rightWidth = fullWidth - leftWidth - gap;
            int visibilityHeight = visibilityCardHeight(leftWidth);
            return new Layout(
                    x, 0, leftWidth,
                    x, visibilityHeight + gap, leftWidth,
                    x + leftWidth + gap, 0, rightWidth
            );
        }

        int visibilityHeight = visibilityCardHeight(fullWidth);
        int presentationHeight = presentationCardHeight(fullWidth);
        int presentationY = visibilityHeight + gap;
        int previewY = presentationY + presentationHeight + gap;
        return new Layout(x, 0, fullWidth, x, presentationY, fullWidth, x, previewY, fullWidth);
    }

    private int visibilityCardHeight(int cardWidth) {
        return usesStackedRows(cardWidth) ? 240 : VISIBILITY_CARD_HEIGHT;
    }

    private int presentationCardHeight(int cardWidth) {
        return usesStackedRows(cardWidth) ? 194 : PRESENTATION_CARD_HEIGHT;
    }

    private boolean usesStackedRows(int cardWidth) {
        return parent.useStackedSettingRowLayout(cardWidth, parent.getControlWidth());
    }

    private void settingChanged() {
        parent.markDirty();
        clearPreviewCache();
    }

    private ConfigManager.SlotPosition cyclePosition(ConfigManager.SlotPosition current) {
        return current == ConfigManager.SlotPosition.RIGHT
                ? ConfigManager.SlotPosition.LEFT
                : ConfigManager.SlotPosition.RIGHT;
    }

    private String slotPositionKey(ConfigManager.SlotPosition position) {
        return position == ConfigManager.SlotPosition.RIGHT ? "tt.position.right" : "tt.position.left";
    }

    private ConfigManager.SeparatorMode cycleSeparatorMode(ConfigManager.SeparatorMode current) {
        return switch (current) {
            case ADAPTIVE -> ConfigManager.SeparatorMode.STATIC;
            case STATIC -> ConfigManager.SeparatorMode.OFF;
            case OFF -> ConfigManager.SeparatorMode.ADAPTIVE;
        };
    }

    private String separatorModeKey(ConfigManager.SeparatorMode mode) {
        return switch (mode) {
            case ADAPTIVE -> "tt.separator.adaptive";
            case STATIC -> "tt.separator.static";
            case OFF -> "tt.state.off";
        };
    }

    private static String tr(String key) {
        return I18n.get(key);
    }

    private void clearPreviewCache() {
        com.tiertagger.TierTaggerClient.clearNametagCache();
    }

    private record Layout(
            int visibilityX,
            int visibilityY,
            int visibilityWidth,
            int presentationX,
            int presentationY,
            int presentationWidth,
            int previewX,
            int previewY,
            int previewWidth
    ) {
    }
}
