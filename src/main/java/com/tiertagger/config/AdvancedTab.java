package com.tiertagger.config;

import com.tiertagger.net.RankService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class AdvancedTab {
    private static final int SETTINGS_CARD_HEIGHT = 148;
    private static final int ACTIONS_CARD_HEIGHT = 166;
    private static final int FIRST_ROW_OFFSET = 42;
    private static final int ROW_SPACING = 36;

    private final AdvancedConfigScreen parent;

    private EditBox refreshField;
    private Button autoUpdateButton;

    private int tempRefreshMinutes;
    private boolean tempAutoUpdate;

    public AdvancedTab(AdvancedConfigScreen parent) {
        this.parent = parent;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        var cfg = ConfigManager.get();
        tempRefreshMinutes = cfg.refreshMinutes;
        tempAutoUpdate = ConfigManager.isAutoUpdateEnabled();
    }

    public void init() {
        Layout layout = layout();
        int controlWidth = Math.min(92, parent.getControlWidth());
        AdvancedConfigScreen.SettingRowLayout refreshRow = parent.layoutAdaptiveSettingRow(
                layout.settingsX, layout.settingsWidth, layout.settingsY, FIRST_ROW_OFFSET, controlWidth
        );
        AdvancedConfigScreen.SettingRowLayout autoRow = parent.layoutAdaptiveSettingRow(
                layout.settingsX, layout.settingsWidth, layout.settingsY, layout.autoUpdateOffset, controlWidth
        );
        AdvancedConfigScreen.SettingRowLayout refreshNowRow = parent.layoutAdaptiveSettingRow(
                layout.actionsX, layout.actionsWidth, layout.actionsY, layout.refreshNowOffset, layout.actionControlWidth
        );
        AdvancedConfigScreen.SettingRowLayout resetRow = parent.layoutAdaptiveSettingRow(
                layout.actionsX, layout.actionsWidth, layout.actionsY, layout.resetOffset, layout.actionControlWidth
        );

        refreshField = new EditBox(
                parent.getFont(),
                refreshRow.controlX(),
                refreshRow.controlY(),
                refreshRow.controlWidth(),
                refreshRow.controlHeight(),
                Component.translatable("tt.advanced.refresh_interval")
        );
        refreshField.setMaxLength(4);
        refreshField.setValue(Integer.toString(tempRefreshMinutes));
        refreshField.setCentered(true);
        refreshField.setResponder(value -> {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > 0 && parsed <= 1440) {
                    tempRefreshMinutes = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
            parent.markDirty();
        });
        parent.addChild(refreshField);

        autoUpdateButton = parent.addThemedButton(stateLabel(tempAutoUpdate), button -> {
            tempAutoUpdate = !tempAutoUpdate;
            button.setMessage(stateLabel(tempAutoUpdate));
            parent.markDirty();
        }, autoRow.controlX(), autoRow.controlY(), autoRow.controlWidth(), autoRow.controlHeight(),
                AdvancedConfigScreen.ButtonStyle.TOGGLE, () -> tempAutoUpdate);

        parent.addThemedButton(Component.translatable("tt.action.refresh"), ignored -> {
            RankService.getInstance().refreshNow();
            if (parent.getClient() != null && parent.getClient().player != null) {
                parent.getClient().player.sendSystemMessage(Component.translatable("tt.message.refreshing"));
            }
        },
                refreshNowRow.controlX(),
                refreshNowRow.controlY(),
                refreshNowRow.controlWidth(),
                refreshNowRow.controlHeight(),
                AdvancedConfigScreen.ButtonStyle.SECONDARY, () -> false);

        parent.addThemedButton(Component.translatable("tt.action.reset"), ignored -> {
            ConfigManager.resetToDefaults();
            RankService.getInstance().reschedule();
            parent.reloadAllTabsFromConfig();
        },
                resetRow.controlX(),
                resetRow.controlY(),
                resetRow.controlWidth(),
                resetRow.controlHeight(),
                AdvancedConfigScreen.ButtonStyle.DANGER, () -> false);

        AdvancedConfigScreen.SettingRowLayout clearCacheRow = parent.layoutAdaptiveSettingRow(
                layout.actionsX, layout.actionsWidth, layout.actionsY,
                layout.clearCacheOffset, layout.actionControlWidth
        );
        parent.addThemedButton(Component.translatable("tt.action.clear_cache"), ignored -> {
            com.tiertagger.TierTaggerClient.clearNametagCache();
            if (parent.getClient() != null && parent.getClient().player != null)
                parent.getClient().player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("tt.message.cache_cleared"));
        },
                clearCacheRow.controlX(), clearCacheRow.controlY(),
                clearCacheRow.controlWidth(), clearCacheRow.controlHeight(),
                AdvancedConfigScreen.ButtonStyle.SECONDARY, () -> false);
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        int controlWidth = Math.min(92, parent.getControlWidth());
        int pad = parent.getCardPadding();
        AdvancedConfigScreen.SettingRowLayout refreshRow = parent.layoutAdaptiveSettingRow(
                layout.settingsX, layout.settingsWidth, layout.settingsY, FIRST_ROW_OFFSET, controlWidth
        );
        AdvancedConfigScreen.SettingRowLayout autoRow = parent.layoutAdaptiveSettingRow(
                layout.settingsX, layout.settingsWidth, layout.settingsY, layout.autoUpdateOffset, controlWidth
        );
        AdvancedConfigScreen.SettingRowLayout refreshNowRow = parent.layoutAdaptiveSettingRow(
                layout.actionsX, layout.actionsWidth, layout.actionsY, layout.refreshNowOffset, layout.actionControlWidth
        );
        AdvancedConfigScreen.SettingRowLayout resetRow = parent.layoutAdaptiveSettingRow(
                layout.actionsX, layout.actionsWidth, layout.actionsY, layout.resetOffset, layout.actionControlWidth
        );

        int settingsY = parent.contentY(layout.settingsY);
        int actionsY = parent.contentY(layout.actionsY);

        parent.drawCard(context, layout.settingsX, settingsY, layout.settingsWidth, layout.settingsHeight, true);
        parent.drawCardTitle(
                context,
                tr("tt.advanced.title"),
                tr("tt.advanced.help"),
                layout.settingsX + pad,
                settingsY + 10
        );
        context.text(parent.getFont(), tr("tt.advanced.refresh_minutes"), refreshRow.labelX(), refreshRow.labelY(), parent.getPrimaryTextColor());
        parent.drawSettingHelp(context, tr("tt.advanced.refresh_minutes.help"), refreshRow,
                layout.settingsX, layout.settingsWidth);
        context.text(parent.getFont(), tr("tt.advanced.auto_update"), autoRow.labelX(), autoRow.labelY(), parent.getPrimaryTextColor());
        parent.drawSettingHelp(context, tr("tt.advanced.auto_update.help"), autoRow,
                layout.settingsX, layout.settingsWidth);
        parent.drawCard(context, layout.actionsX, actionsY, layout.actionsWidth, layout.actionsHeight, false);
        parent.drawCardTitle(context, tr("tt.advanced.actions"), tr("tt.advanced.actions.help"), layout.actionsX + pad, actionsY + 10);
        context.text(parent.getFont(), tr("tt.advanced.refresh_now"), refreshNowRow.labelX(), refreshNowRow.labelY(), parent.getPrimaryTextColor());
        parent.drawSettingHelp(context, tr("tt.advanced.refresh_now.help"), refreshNowRow,
                layout.actionsX, layout.actionsWidth);
        context.text(parent.getFont(), tr("tt.advanced.reset"), resetRow.labelX(), resetRow.labelY(), parent.getPrimaryTextColor());
        parent.drawSettingHelp(context, tr("tt.advanced.reset.help"), resetRow,
                layout.actionsX, layout.actionsWidth);

        AdvancedConfigScreen.SettingRowLayout clearCacheRow = parent.layoutAdaptiveSettingRow(
                layout.actionsX, layout.actionsWidth, layout.actionsY,
                layout.clearCacheOffset, layout.actionControlWidth
        );
        context.text(parent.getFont(), tr("tt.advanced.cache"),
                clearCacheRow.labelX(), clearCacheRow.labelY(), parent.getPrimaryTextColor());
        parent.drawSettingHelp(context, tr("tt.advanced.cache.help"), clearCacheRow,
                layout.actionsX, layout.actionsWidth);

        parent.drawDangerText(context, tr("tt.advanced.danger_help"),
                layout.actionsX + pad, actionsY + layout.actionsHeight - 16);

    }

    public void save() {
        var cfg = ConfigManager.get();
        boolean refreshIntervalChanged = cfg.refreshMinutes != tempRefreshMinutes;
        cfg.refreshMinutes = tempRefreshMinutes;
        cfg.autoUpdate = tempAutoUpdate;
        if (refreshIntervalChanged) RankService.getInstance().reschedule();
    }

    public int getContentHeight() {
        Layout layout = layout();
        return Math.max(layout.settingsY + layout.settingsHeight, layout.actionsY + layout.actionsHeight);
    }

    private Layout layout() {
        int gap = parent.getSectionGap();
        int fullWidth = parent.getContentWidth();

        if (parent.isNarrowLayout()) {
            int controlWidth = Math.min(92, parent.getControlWidth());
            boolean stackedSettings = parent.useStackedSettingRowLayout(fullWidth, controlWidth);
            int autoUpdateOffset = stackedSettings
                    ? FIRST_ROW_OFFSET + parent.getStackedRowHeight() + 8
                    : FIRST_ROW_OFFSET + ROW_SPACING;
            int settingsHeight = stackedSettings ? autoUpdateOffset + parent.getStackedRowHeight() + 20 : SETTINGS_CARD_HEIGHT;
            int actionControlWidth = Math.min(124, Math.max(104, fullWidth - parent.getCardPadding() * 2 - 60));
            boolean stackedActions = parent.useStackedSettingRowLayout(fullWidth, actionControlWidth);
            int resetOffset = stackedActions
                    ? FIRST_ROW_OFFSET + parent.getStackedRowHeight() + 8
                    : FIRST_ROW_OFFSET + ROW_SPACING;
            int clearCacheOffset = stackedActions
                    ? resetOffset + parent.getStackedRowHeight() + 8
                    : resetOffset + ROW_SPACING;
            int actionsHeight = stackedActions
                    ? clearCacheOffset + parent.getStackedRowHeight() + 28
                    : ACTIONS_CARD_HEIGHT;
            return new Layout(
                    parent.getContentX(),
                    0,
                    fullWidth,
                    settingsHeight,
                    autoUpdateOffset,
                    parent.getContentX(),
                    settingsHeight + gap,
                    fullWidth,
                    actionControlWidth,
                    FIRST_ROW_OFFSET,
                    resetOffset,
                    clearCacheOffset,
                    actionsHeight,
                    actionsHeight - 18
            );
        }

        int settingsWidth = Math.max(280, (fullWidth - gap) * 58 / 100);
        int actionsWidth = fullWidth - settingsWidth - gap;
        int settingsControlWidth = Math.min(92, parent.getControlWidth());
        boolean stackedSettings = parent.useStackedSettingRowLayout(settingsWidth, settingsControlWidth);
        int autoUpdateOffset = stackedSettings
                ? FIRST_ROW_OFFSET + parent.getStackedRowHeight() + 8
                : FIRST_ROW_OFFSET + ROW_SPACING;
        int settingsHeight = stackedSettings ? autoUpdateOffset + parent.getStackedRowHeight() + 20 : SETTINGS_CARD_HEIGHT;
        int actionControlWidth = Math.min(124, Math.max(104, actionsWidth - parent.getCardPadding() * 2 - 60));
        boolean stackedActions = parent.useStackedSettingRowLayout(actionsWidth, actionControlWidth);
        int refreshNowOffset = FIRST_ROW_OFFSET;
        int resetOffset = stackedActions ? refreshNowOffset + parent.getStackedRowHeight() + 8 : FIRST_ROW_OFFSET + ROW_SPACING;
        int clearCacheOffset = stackedActions ? resetOffset + parent.getStackedRowHeight() + 8 : resetOffset + ROW_SPACING;
        int actionsHeight = stackedActions ? clearCacheOffset + parent.getStackedRowHeight() + 28 : ACTIONS_CARD_HEIGHT;
        int dangerOffset = actionsHeight - 16;
        return new Layout(
                parent.getContentX(),
                0,
                settingsWidth,
                settingsHeight,
                autoUpdateOffset,
                parent.getContentX() + settingsWidth + gap,
                0,
                actionsWidth,
                actionControlWidth,
                refreshNowOffset,
                resetOffset,
                clearCacheOffset,
                actionsHeight,
                dangerOffset
        );
    }

    private Component stateLabel(boolean enabled) {
        return Component.translatable(enabled ? "tt.state.on" : "tt.state.off");
    }

    private static String tr(String key) {
        return I18n.get(key);
    }

    private record Layout(
            int settingsX,
            int settingsY,
            int settingsWidth,
            int settingsHeight,
            int autoUpdateOffset,
            int actionsX,
            int actionsY,
            int actionsWidth,
            int actionControlWidth,
            int refreshNowOffset,
            int resetOffset,
            int clearCacheOffset,
            int actionsHeight,
            int dangerOffset
    ) {
    }
}
