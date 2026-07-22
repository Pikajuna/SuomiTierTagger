package com.tiertagger.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.tiertagger.ui.OverlayTheme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class AdvancedConfigScreenBase extends Screen {
    private static final int HEADER_HEIGHT = 44;
    private static final int TOP_NAV_HEIGHT = 30;
    private static final int SIDEBAR_WIDTH = 112;
    private static final int FOOTER_HEIGHT = 36;
    private static final int OUTER_MARGIN = 10;
    private static final int SHELL_INSET = 6;
    private static final int CONTENT_PADDING_WIDE = 10;
    private static final int CONTENT_PADDING_NARROW = 8;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int SCROLLBAR_GUTTER = 7;
    private static final int SCROLL_STEP = 24;
    private static final int INLINE_CONTROL_GAP = 10;
    private static final int MIN_INLINE_LABEL_WIDTH = 145;
    private static final int STACKED_CONTROL_TOP_GAP = 10;
    private static final int STACKED_ROW_HEIGHT = 42;
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("suomitiertagger", "icon.png");


    private final Screen parent;
    private final Map<Tab, Button> tabButtons = new EnumMap<>(Tab.class);
    private final Map<Button, ButtonVisual> themedButtons = new LinkedHashMap<>();
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    private Tab currentTab = Tab.GENERAL;
    private GeneralTab generalTab;
    private KitTab kitTab;
    private ColorsTab colorsTab;
    private AdvancedTab advancedTab;

    private boolean buildingContentWidgets;
    private double contentScroll;
    private boolean draggingScrollbar;
    private boolean dirty;
    private Button saveButton;
    private Button revertButton;
    private Button enabledButton;

    public enum Tab {
        GENERAL("tt.tab.general", "tt.tab.general.short"),
        KIT("tt.tab.kit", "tt.tab.kit.short"),
        COLORS("tt.tab.colors", "tt.tab.colors.short"),
        ADVANCED("tt.tab.advanced", "tt.tab.advanced.short");

        final String translationKey;
        final String shortTranslationKey;

        Tab(String translationKey, String shortTranslationKey) {
            this.translationKey = translationKey;
            this.shortTranslationKey = shortTranslationKey;
        }

        String displayName() {
            return I18n.get(translationKey);
        }
    }

    public enum LayoutTier {
        WIDE,
        MEDIUM,
        NARROW
    }

    public enum ButtonStyle {
        NAVIGATION,
        PRIMARY,
        SECONDARY,
        DANGER,
        TOGGLE,
        SEGMENT,
        TILE,
        ICON
    }

    private record ButtonVisual(ButtonStyle style, BooleanSupplier selected, boolean content) {
    }

    public AdvancedConfigScreenBase(Screen parent) {
        super(Component.translatable("tt.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureTabs();
        clampScroll();
        rebuildScreenWidgets();
    }

    private void ensureTabs() {
        if (generalTab == null) {
            generalTab = new GeneralTab((AdvancedConfigScreen) this);
        }
        if (kitTab == null) {
            kitTab = new KitTab((AdvancedConfigScreen) this);
        }
        if (colorsTab == null) {
            colorsTab = new ColorsTab((AdvancedConfigScreen) this);
        }
        if (advancedTab == null) {
            advancedTab = new AdvancedTab((AdvancedConfigScreen) this);
        }
    }

    private void rebuildScreenWidgets() {
        clearWidgets();
        tabButtons.clear();
        themedButtons.clear();
        contentWidgets.clear();

        initHeaderControls();
        initTabButtons();
        initFooterButtons();

        buildingContentWidgets = true;
        initCurrentTab();
        buildingContentWidgets = false;

        refreshContentWidgetVisibility();
    }

    private void initHeaderControls() {
        int buttonWidth = isNarrowLayout() ? 54 : 66;
        int x = getShellRight() - SHELL_INSET - getBarPadding() - buttonWidth;
        int y = getShellTop() + SHELL_INSET + 12;
        enabledButton = addFixedThemedButton(
                Component.translatable(generalTab.isEnabled() ? "tt.state.on" : "tt.state.off"),
                button -> {
                    generalTab.toggleEnabled();
                    button.setMessage(Component.translatable(generalTab.isEnabled() ? "tt.state.on" : "tt.state.off"));
                    markDirty();
                },
                x, y, buttonWidth, 20, ButtonStyle.TOGGLE, generalTab::isEnabled
        );
    }

    private void initTabButtons() {
        int count = Tab.values().length;
        if (getLayoutTier() == LayoutTier.WIDE) {
            int x = getTabStripLeft() + 8;
            int y = getTabStripTop() + 8;
            int width = getTabStripWidth() - 16;
            for (int i = 0; i < count; i++) {
                Tab tab = Tab.values()[i];
                Button button = addFixedThemedButton(Component.translatable(tab.translationKey), ignored -> switchTab(tab),
                        x, y + i * 32, width, 26, ButtonStyle.NAVIGATION, () -> currentTab == tab);
                tabButtons.put(tab, button);
            }
            return;
        }

        int left = getTabStripLeft() + 6;
        int availableWidth = getTabStripWidth() - 12;
        int gap = getTabGap();
        int buttonWidth = Math.max(58, (availableWidth - gap * (count - 1)) / count);
        int y = getTabStripTop() + 4;
        for (int i = 0; i < count; i++) {
            Tab tab = Tab.values()[i];
            int x = left + i * (buttonWidth + gap);
            String labelKey = isNarrowLayout() ? tab.shortTranslationKey : tab.translationKey;
            Button button = addFixedThemedButton(Component.translatable(labelKey), ignored -> switchTab(tab),
                    x, y, buttonWidth, 22, ButtonStyle.NAVIGATION, () -> currentTab == tab);
            tabButtons.put(tab, button);
        }
    }

    private void initFooterButtons() {
        int btnWidth = isNarrowLayout() ? 68 : 88;
        int gap = 6;
        int totalBtnWidth = btnWidth * 2 + gap;
        int startX = getFooterLeft() + getFooterWidth() - totalBtnWidth - getBarPadding();
        int footerY = getFooterTop() + (FOOTER_HEIGHT - 20) / 2;

        addFixedThemedButton(Component.translatable("tt.action.close"),
                ignored -> Minecraft.getInstance().setScreen(parent),
                startX, footerY, btnWidth, 20, ButtonStyle.SECONDARY, () -> false);

        saveButton = addFixedThemedButton(Component.translatable("tt.action.save"), ignored -> {
            saveAllSettings();
        }, startX + btnWidth + gap, footerY, btnWidth, 20, ButtonStyle.PRIMARY, () -> false);
        saveButton.active = dirty;

        if (!isNarrowLayout()) {
            revertButton = addFixedThemedButton(Component.translatable("tt.action.revert"), ignored -> {
                reloadAllTabsFromConfig();
                setDirty(false);
            }, getFooterLeft() + getBarPadding(), footerY, 112, 20, ButtonStyle.SECONDARY, () -> false);
            revertButton.active = dirty;
        } else {
            revertButton = null;
        }
    }

    private void switchTab(Tab tab) {
        if (tab == currentTab) {
            return;
        }
        currentTab = tab;
        contentScroll = 0;
        draggingScrollbar = false;
        rebuildScreenWidgets();
    }

    private void initCurrentTab() {
        switch (currentTab) {
            case GENERAL -> generalTab.init();
            case KIT -> kitTab.init();
            case COLORS -> colorsTab.init();
            case ADVANCED -> advancedTab.init();
        }
    }

    public void saveAllSettings() {
        generalTab.save();
        kitTab.save();
        colorsTab.save();
        advancedTab.save();
        ConfigManager.save();
        setDirty(false);
    }

    public void reloadAllTabsFromConfig() {
        ConfigManager.clearCache();
        generalTab.reloadFromConfig();
        kitTab.reloadFromConfig();
        colorsTab.reloadFromConfig();
        advancedTab.reloadFromConfig();
        contentScroll = 0;
        draggingScrollbar = false;
        dirty = false;
        rebuildScreenWidgets();
    }

    public void markDirty() {
        setDirty(true);
    }

    public boolean isDirty() {
        return dirty;
    }

    private void setDirty(boolean value) {
        dirty = value;
        if (saveButton != null) saveButton.active = value;
        if (revertButton != null) revertButton.active = value;
    }

    public void refreshCurrentView() {
        clampScroll();
        rebuildScreenWidgets();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderScrim(context);
        renderShell(context);

        context.enableScissor(getContentViewportLeft(), getContentViewportTop(), getContentViewportRight(), getContentViewportBottom());
        renderCurrentTabBackground(context, mouseX, mouseY, delta);
        context.disableScissor();

        super.extractRenderState(context, mouseX, mouseY, delta);
        renderThemedButtons(context);
        renderScrollbar(context);
    }

    private void renderCurrentTabBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        switch (currentTab) {
            case GENERAL -> generalTab.render(context, mouseX, mouseY, delta);
            case KIT -> kitTab.render(context, mouseX, mouseY, delta);
            case COLORS -> colorsTab.render(context, mouseX, mouseY, delta);
            case ADVANCED -> advancedTab.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderScrim(GuiGraphicsExtractor context) {
        OverlayTheme.drawScrim(context, width, height);
    }

    private void renderShell(GuiGraphicsExtractor context) {
        context.fill(getShellLeft() + 4, getShellTop() + 5, getShellRight() + 4,
                getShellTop() + getShellHeight() + 5, 0x66000000);
        OverlayTheme.drawPanel(context, getShellLeft(), getShellTop(), getShellWidth(), getShellHeight(),
                OverlayTheme.SHELL_BG, OverlayTheme.SHELL_EDGE);

        int barLeft = getShellLeft() + SHELL_INSET;
        int barTop  = getShellTop() + SHELL_INSET;
        int barW    = getShellWidth() - SHELL_INSET * 2;
        context.fill(barLeft, barTop, barLeft + barW, barTop + HEADER_HEIGHT, OverlayTheme.HEADER_BG);
        context.fill(barLeft, barTop + HEADER_HEIGHT - 2, barLeft + barW, barTop + HEADER_HEIGHT, OverlayTheme.ACCENT_DARK);

        int logoX = barLeft + getBarPadding();
        int logoY = barTop + 4;
        context.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                logoX, logoY, 0f, 0f,
                36, 36, 256, 256, 256, 256);
        int titleX = logoX + 44;
        context.text(font, "SuomiTierTagger", titleX, barTop + 9, OverlayTheme.TEXT_PRIMARY);
        context.text(font, versionLabel(), titleX, barTop + 23, OverlayTheme.TEXT_MUTED, false);

        if (!isNarrowLayout()) {
            String enabledLabel = I18n.get("tt.general.enabled");
            context.text(font, enabledLabel,
                    enabledButton.getX() - 10 - font.width(enabledLabel), barTop + 21,
                    OverlayTheme.TEXT_SECONDARY, false);
        }

        if (getLayoutTier() == LayoutTier.WIDE) {
            context.fill(getTabStripLeft(), getTabStripTop(), getTabStripLeft() + getTabStripWidth(),
                    getTabStripTop() + getTabStripHeight(), OverlayTheme.SIDEBAR_BG);
            context.fill(getTabStripLeft() + getTabStripWidth() - 1, getTabStripTop(),
                    getTabStripLeft() + getTabStripWidth(), getTabStripTop() + getTabStripHeight(),
                    OverlayTheme.SHELL_EDGE_SOFT);
        } else {
            context.fill(getTabStripLeft(), getTabStripTop(), getTabStripLeft() + getTabStripWidth(),
                    getTabStripTop() + TOP_NAV_HEIGHT, OverlayTheme.SIDEBAR_BG);
        }

        int footerDivY = getFooterTop();
        context.fill(getFooterLeft(), footerDivY, getFooterLeft() + getFooterWidth(),
                footerDivY + FOOTER_HEIGHT, OverlayTheme.HEADER_BG);
        context.fill(getFooterLeft(), footerDivY, getFooterLeft() + getFooterWidth(), footerDivY + 1, OverlayTheme.SHELL_EDGE_SOFT);
        String status = I18n.get(dirty ? "tt.state.unsaved" : "tt.state.saved");
        int statusColor = dirty ? OverlayTheme.ACCENT_HOVER : OverlayTheme.TEXT_MUTED;
        if (!isNarrowLayout()) {
            context.text(font, status, getFooterLeft() + getBarPadding() + 120,
                    footerDivY + 14, statusColor, false);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor context) {
        if (!isScrollable()) return;

        int trackX1 = getScrollbarX();
        int trackX2 = trackX1 + SCROLLBAR_WIDTH;
        int thumbHeight = getScrollbarThumbHeight();
        int thumbY = getScrollbarThumbY();
        int thumbColor = draggingScrollbar ? OverlayTheme.ACCENT_HOVER : OverlayTheme.ACCENT_DIM;
        context.fill(trackX1, thumbY, trackX2, thumbY + thumbHeight, thumbColor);
    }

    public void drawCard(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean accent) {
        OverlayTheme.drawCard(context, x, y, width, height, accent);
    }

    public void drawCardTitle(GuiGraphicsExtractor context, String title, String subtitle, int x, int y) {
        context.text(font, title, x, y, OverlayTheme.TEXT_PRIMARY);
        if (subtitle != null && !subtitle.isBlank()) {
            context.text(font, subtitle, x, y + 12, OverlayTheme.TEXT_MUTED, false);
        }
    }

    public void drawMutedText(GuiGraphicsExtractor context, String text, int x, int y) {
        context.text(font, text, x, y, OverlayTheme.TEXT_MUTED, false);
    }

    public void drawSettingHelp(GuiGraphicsExtractor context, String text, SettingRowLayout row,
                                int cardX, int cardWidth) {
        int right = row.stacked()
                ? cardX + cardWidth - getCardPadding()
                : row.controlX() - INLINE_CONTROL_GAP;
        int maxWidth = Math.max(40, right - row.labelX());
        int maxLines = row.stacked() ? 1 : 2;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            context.text(font, lines.get(i), row.labelX(), row.helpY() + i * 9,
                    OverlayTheme.TEXT_MUTED, false);
        }
    }

    public void drawDangerText(GuiGraphicsExtractor context, String text, int x, int y) {
        context.text(font, text, x, y, OverlayTheme.TEXT_DANGER, false);
    }

    public void drawSelectionFrame(GuiGraphicsExtractor context, AbstractWidget widget) {
        int x1 = widget.getX() - 3;
        int y1 = widget.getY() - 3;
        int x2 = widget.getX() + widget.getWidth() + 3;
        int y2 = widget.getY() + widget.getHeight() + 3;
        OverlayTheme.drawPanel(context, x1, y1, x2 - x1, y2 - y1, OverlayTheme.ACCENT_SURFACE, OverlayTheme.ACCENT);
    }

    public void drawTextField(GuiGraphicsExtractor context, EditBox field, boolean valid) {
        int x = field.getX() - 3;
        int y = field.getY() - 2;
        int width = field.getWidth() + 6;
        int height = field.getHeight() + 4;
        int border = !valid ? OverlayTheme.TEXT_DANGER
                : field.isFocused() ? OverlayTheme.ACCENT : OverlayTheme.CARD_EDGE;
        OverlayTheme.drawPanel(context, x, y, width, height, OverlayTheme.CONTROL_BG, border);
    }

    private void renderThemedButtons(GuiGraphicsExtractor context) {
        for (Map.Entry<Button, ButtonVisual> entry : themedButtons.entrySet()) {
            if (!entry.getValue().content()) {
                renderThemedButton(context, entry.getKey(), entry.getValue());
            }
        }

        context.enableScissor(getContentViewportLeft(), getContentViewportTop(),
                getContentViewportRight(), getContentViewportBottom());
        for (Map.Entry<Button, ButtonVisual> entry : themedButtons.entrySet()) {
            if (entry.getValue().content()) {
                renderThemedButton(context, entry.getKey(), entry.getValue());
            }
        }
        context.disableScissor();
    }

    private void renderThemedButton(GuiGraphicsExtractor context, Button button, ButtonVisual visual) {
        if (!button.visible) return;

        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        boolean selected = visual.selected() != null && visual.selected().getAsBoolean();
        boolean hovered = button.isHovered() && button.active;

        int fill = OverlayTheme.CONTROL_BG;
        int border = OverlayTheme.CARD_EDGE;
        int textColor = button.active ? OverlayTheme.TEXT_SECONDARY : OverlayTheme.TEXT_MUTED;

        switch (visual.style()) {
            case PRIMARY -> {
                fill = button.active ? (hovered ? OverlayTheme.ACCENT_HOVER : OverlayTheme.ACCENT) : OverlayTheme.CONTROL_DISABLED;
                border = button.active ? OverlayTheme.ACCENT_HOVER : OverlayTheme.SHELL_EDGE_SOFT;
                textColor = button.active ? 0xFFFFFFFF : OverlayTheme.TEXT_MUTED;
            }
            case DANGER -> {
                fill = hovered ? 0xFF4A2026 : OverlayTheme.ACCENT_SURFACE;
                border = button.active ? OverlayTheme.ACCENT_DARK : OverlayTheme.SHELL_EDGE_SOFT;
                textColor = button.active ? OverlayTheme.TEXT_DANGER : OverlayTheme.TEXT_MUTED;
            }
            case NAVIGATION -> {
                fill = selected ? OverlayTheme.ACCENT_SURFACE : hovered ? OverlayTheme.CONTROL_HOVER : OverlayTheme.SIDEBAR_BG;
                border = selected ? OverlayTheme.ACCENT_DARK : fill;
                textColor = selected ? OverlayTheme.TEXT_PRIMARY : hovered ? OverlayTheme.TEXT_SECONDARY : OverlayTheme.TEXT_MUTED;
            }
            case TOGGLE -> {
                fill = selected ? OverlayTheme.ACCENT_SURFACE : hovered ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG;
                border = selected ? OverlayTheme.ACCENT : OverlayTheme.CARD_EDGE;
                textColor = selected ? OverlayTheme.TEXT_PRIMARY : OverlayTheme.TEXT_MUTED;
            }
            case SEGMENT -> {
                fill = selected ? OverlayTheme.ACCENT_SURFACE : hovered ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG;
                border = selected ? OverlayTheme.ACCENT_DARK : OverlayTheme.CARD_EDGE;
                textColor = selected ? OverlayTheme.TEXT_PRIMARY : OverlayTheme.TEXT_SECONDARY;
            }
            case TILE -> {
                fill = selected ? OverlayTheme.ACCENT_SURFACE : hovered ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CARD_BG;
                border = selected ? OverlayTheme.ACCENT : OverlayTheme.CARD_EDGE;
                textColor = selected ? OverlayTheme.TEXT_PRIMARY : OverlayTheme.TEXT_SECONDARY;
            }
            case ICON, SECONDARY -> {
                fill = hovered ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG;
                border = button.isFocused() ? OverlayTheme.ACCENT : OverlayTheme.CARD_EDGE;
            }
        }

        if (!button.active && visual.style() != ButtonStyle.PRIMARY) {
            fill = OverlayTheme.CONTROL_DISABLED;
            border = OverlayTheme.SHELL_EDGE_SOFT;
        }

        OverlayTheme.drawPanel(context, x, y, width, height, fill, border);

        if (visual.style() == ButtonStyle.NAVIGATION && selected) {
            if (getLayoutTier() == LayoutTier.WIDE) {
                context.fill(x, y, x + 3, y + height, OverlayTheme.ACCENT);
            } else {
                context.fill(x + 4, y + height - 2, x + width - 4, y + height, OverlayTheme.ACCENT);
            }
        } else if (visual.style() == ButtonStyle.TILE && selected) {
            context.fill(x, y, x + width, y + 2, OverlayTheme.ACCENT);
        }

        if (visual.style() == ButtonStyle.TOGGLE) {
            renderToggleContents(context, button, selected, textColor);
        } else {
            Component label = fitButtonLabel(button.getMessage(), Math.max(8, width - 8));
            context.centeredText(font, label, x + width / 2,
                    y + (height - font.lineHeight) / 2, textColor);
        }

    }

    private void renderToggleContents(GuiGraphicsExtractor context, Button button, boolean selected, int textColor) {
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        int trackWidth = 28;
        int trackHeight = 12;
        int trackX = width < 70 ? x + (width - trackWidth) / 2 : x + width - trackWidth - 6;
        int trackY = y + (height - trackHeight) / 2;
        int trackFill = selected ? OverlayTheme.ACCENT : 0xFF3B414D;
        OverlayTheme.drawPanel(context, trackX, trackY, trackWidth, trackHeight, trackFill,
                selected ? OverlayTheme.ACCENT_HOVER : OverlayTheme.CARD_EDGE);
        int knobX = selected ? trackX + trackWidth - 10 : trackX + 2;
        context.fill(knobX, trackY + 2, knobX + 8, trackY + trackHeight - 2, 0xFFF7F8FA);

        if (width >= 70) {
            int labelWidth = width - trackWidth - 10;
            context.centeredText(font, button.getMessage(), x + labelWidth / 2 + 2,
                    y + (height - font.lineHeight) / 2, textColor);
        }
    }

    public Button addThemedButton(Component message, Consumer<Button> action,
                                        int x, int y, int width, int height,
                                        ButtonStyle style, BooleanSupplier selected) {
        Button button = buildThemedButton(message, action, x, y, width, height, style, selected, true);
        return addChild(button);
    }

    private Button addFixedThemedButton(Component message, Consumer<Button> action,
                                              int x, int y, int width, int height,
                                              ButtonStyle style, BooleanSupplier selected) {
        Button button = buildThemedButton(message, action, x, y, width, height, style, selected, false);
        return addRenderableWidget(button);
    }

    private Button buildThemedButton(Component message, Consumer<Button> action,
                                           int x, int y, int width, int height,
                                           ButtonStyle style, BooleanSupplier selected, boolean content) {
        Button.Builder builder = Button.builder(message, action::accept)
                .bounds(x, y, width, height);
        if (font != null && font.width(message) > Math.max(8, width - 8)) {
            builder.tooltip(Tooltip.create(message));
        }
        Button button = builder.build();
        button.setAlpha(0.0F);
        themedButtons.put(button, new ButtonVisual(style, selected, content));
        return button;
    }

    private Component fitButtonLabel(Component message, int maxWidth) {
        if (font.width(message) <= maxWidth) {
            return message;
        }

        String ellipsis = "...";
        int labelWidth = Math.max(0, maxWidth - font.width(ellipsis));
        return Component.literal(font.plainSubstrByWidth(message.getString(), labelWidth) + ellipsis);
    }

    private String versionLabel() {
        return FabricLoader.getInstance().getModContainer("suomitiertagger")
                .map(container -> "v" + container.getMetadata().getVersion().getFriendlyString())
                .orElse("settings");
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean consumed) {
        return handleMouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, consumed);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isPointInsideContent(mouseX, mouseY) || !isScrollable()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        setContentScroll(contentScroll - verticalAmount * SCROLL_STEP);
        return true;
    }

    protected boolean handleScrollbarDragged(double mouseY) {
        if (!draggingScrollbar) {
            return false;
        }

        updateScrollFromThumb(mouseY);
        return true;
    }

    protected void finishScrollbarDrag() {
        draggingScrollbar = false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // The custom shell fully replaces the vanilla menu background.
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isScrollable() && inside(mouseX, mouseY, getScrollbarX() - 2, getContentViewportTop(), SCROLLBAR_WIDTH + 4, getContentViewportHeight())) {
            draggingScrollbar = true;
            updateScrollFromThumb(mouseY);
            return true;
        }

        if (currentTab == Tab.COLORS) {
            return colorsTab.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    private void updateScrollFromThumb(double mouseY) {
        int trackHeight = getContentViewportHeight() - getScrollbarThumbHeight();
        if (trackHeight <= 0) {
            setContentScroll(0);
            return;
        }

        double thumbTop = mouseY - getScrollbarThumbHeight() / 2.0;
        double clampedThumbTop = Mth.clamp((int) thumbTop, getContentViewportTop(), getContentViewportBottom() - getScrollbarThumbHeight());
        double progress = (clampedThumbTop - getContentViewportTop()) / (double) trackHeight;
        setContentScroll(progress * getMaxContentScroll());
    }

    private void setContentScroll(double scroll) {
        contentScroll = Mth.clamp((int) Math.round(scroll), 0, getMaxContentScroll());
        rebuildScreenWidgets();
    }

    private void clampScroll() {
        contentScroll = Mth.clamp((int) Math.round(contentScroll), 0, getMaxContentScroll());
    }

    private void refreshContentWidgetVisibility() {
        int top = getContentViewportTop();
        int bottom = getContentViewportBottom();
        for (AbstractWidget widget : contentWidgets) {
            boolean visible = widget.getY() + widget.getHeight() > top && widget.getY() < bottom;
            widget.visible = visible;
            widget.active = visible;
        }
    }

    public Screen getParent() {
        return parent;
    }

    public int getShellLeft() {
        return (width - getShellWidth()) / 2;
    }

    public int getShellTop() {
        return (height - getShellHeight()) / 2;
    }

    public int getShellWidth() {
        return Math.min(Math.max(280, width - OUTER_MARGIN * 2), 720);
    }

    public int getShellHeight() {
        return Math.min(Math.max(250, height - OUTER_MARGIN * 2), 430);
    }

    public int getShellRight() {
        return getShellLeft() + getShellWidth();
    }

    public int getBarPadding() {
        return getLayoutTier() == LayoutTier.NARROW ? 10 : 14;
    }

    public int getTabStripLeft() {
        return getShellLeft() + SHELL_INSET;
    }

    public int getTabStripTop() {
        return getShellTop() + SHELL_INSET + HEADER_HEIGHT;
    }

    public int getTabStripWidth() {
        return getLayoutTier() == LayoutTier.WIDE
                ? SIDEBAR_WIDTH
                : getShellWidth() - SHELL_INSET * 2;
    }

    public int getTabStripHeight() {
        return getFooterTop() - getTabStripTop();
    }

    public int getTabGap() {
        return switch (getLayoutTier()) {
            case WIDE -> 5;
            case MEDIUM -> 4;
            case NARROW -> 3;
        };
    }

    public int getContentBoundsLeft() {
        return getLayoutTier() == LayoutTier.WIDE
                ? getShellLeft() + SHELL_INSET + SIDEBAR_WIDTH + 8
                : getShellLeft() + SHELL_INSET;
    }

    public int getContentBoundsTop() {
        int navigationHeight = getLayoutTier() == LayoutTier.WIDE ? 0 : TOP_NAV_HEIGHT;
        return getShellTop() + SHELL_INSET + HEADER_HEIGHT + navigationHeight + 4;
    }

    public int getContentBoundsWidth() {
        return getLayoutTier() == LayoutTier.WIDE
                ? getShellWidth() - SHELL_INSET * 2 - SIDEBAR_WIDTH - 8
                : getShellWidth() - SHELL_INSET * 2;
    }

    public int getContentBoundsHeight() {
        return getFooterTop() - getContentBoundsTop() - 8;
    }

    public int getContentPadding() {
        return getLayoutTier() == LayoutTier.NARROW ? CONTENT_PADDING_NARROW : CONTENT_PADDING_WIDE;
    }

    public int getContentViewportLeft() {
        return getContentBoundsLeft() + getContentPadding();
    }

    public int getContentViewportTop() {
        return getContentBoundsTop() + getContentPadding();
    }

    public int getContentViewportRight() {
        return getContentViewportLeft() + getContentViewportWidth();
    }

    public int getContentViewportBottom() {
        return getContentViewportTop() + getContentViewportHeight();
    }

    public int getContentViewportWidth() {
        return Math.max(120, getContentBoundsWidth() - getContentPadding() * 2 - SCROLLBAR_GUTTER - SCROLLBAR_WIDTH);
    }

    public int getContentViewportHeight() {
        return Math.max(80, getContentBoundsHeight() - getContentPadding() * 2);
    }

    public int getContentX() {
        return getContentViewportLeft();
    }

    public int getContentWidth() {
        return getContentViewportWidth();
    }

    public int contentY(int logicalY) {
        return getContentViewportTop() + logicalY - (int) contentScroll;
    }

    public int getContentBottomVisible() {
        return getContentViewportBottom();
    }

    public int getContentScroll() {
        return (int) contentScroll;
    }

    public int getCardPadding() {
        return getLayoutTier() == LayoutTier.NARROW ? 10 : 12;
    }

    public int getSectionGap() {
        return switch (getLayoutTier()) {
            case WIDE -> 10;
            case MEDIUM -> 9;
            case NARROW -> 8;
        };
    }

    public int getRowHeight() {
        return 26;
    }

    public int getControlWidth() {
        return switch (getLayoutTier()) {
            case WIDE -> 100;
            case MEDIUM -> 92;
            case NARROW -> 88;
        };
    }

    public int getControlHeight() {
        return 18;
    }

    public SettingRowLayout layoutSettingRow(int cardX, int cardWidth, int cardLogicalY, int rowOffset, int controlWidth) {
        int pad = getCardPadding();
        int labelX = cardX + pad;
        int controlX = cardX + cardWidth - pad - controlWidth;
        int labelY = contentY(cardLogicalY + rowOffset);
        return new SettingRowLayout(labelX, labelY, labelY + 13, controlX, labelY - 4, controlWidth, getControlHeight(), false, getRowHeight());
    }

    public SettingRowLayout layoutAdaptiveSettingRow(int cardX, int cardWidth, int cardLogicalY, int rowOffset, int controlWidth) {
        if (!useStackedSettingRowLayout(cardWidth, controlWidth)) {
            return layoutSettingRow(cardX, cardWidth, cardLogicalY, rowOffset, controlWidth);
        }

        int pad = getCardPadding();
        int labelX = cardX + pad;
        int controlX = labelX;
        int labelY = contentY(cardLogicalY + rowOffset);
        int controlY = labelY + STACKED_CONTROL_TOP_GAP + 14;
        return new SettingRowLayout(labelX, labelY, labelY + 13, controlX, controlY, controlWidth, getControlHeight(), true, STACKED_ROW_HEIGHT);
    }

    public boolean useStackedSettingRowLayout(int cardWidth, int controlWidth) {
        int innerWidth = Math.max(0, cardWidth - getCardPadding() * 2);
        int labelWidth = innerWidth - controlWidth - INLINE_CONTROL_GAP;
        return labelWidth < MIN_INLINE_LABEL_WIDTH;
    }

    public int getStackedRowHeight() {
        return STACKED_ROW_HEIGHT;
    }

    public LayoutTier getLayoutTier() {
        int availableWidth = Math.max(0, getShellWidth() - SHELL_INSET * 2);
        if (availableWidth >= 620) {
            return LayoutTier.WIDE;
        }
        if (availableWidth >= 420) {
            return LayoutTier.MEDIUM;
        }
        return LayoutTier.NARROW;
    }

    public boolean isNarrowLayout() {
        return getLayoutTier() == LayoutTier.NARROW;
    }

    public int getFooterLeft() {
        return getShellLeft() + SHELL_INSET;
    }

    public int getFooterTop() {
        return getShellTop() + getShellHeight() - FOOTER_HEIGHT - SHELL_INSET;
    }

    public int getFooterWidth() {
        return getShellWidth() - SHELL_INSET * 2;
    }

    public int getFooterRight() {
        return getFooterLeft() + getFooterWidth();
    }

    public int getPrimaryTextColor() {
        return OverlayTheme.TEXT_PRIMARY;
    }

    public int getMutedTextColor() {
        return OverlayTheme.TEXT_MUTED;
    }

    public int getAccentColor() {
        return OverlayTheme.ACCENT;
    }

    public Minecraft getClient() {
        return Minecraft.getInstance();
    }

    public Font getFont() {
        return font;
    }

    public int getScreenWidth() {
        return width;
    }

    public int getScreenHeight() {
        return height;
    }

    public int getCurrentTabContentHeight() {
        return switch (currentTab) {
            case GENERAL -> generalTab.getContentHeight();
            case KIT -> kitTab.getContentHeight();
            case COLORS -> colorsTab.getContentHeight();
            case ADVANCED -> advancedTab.getContentHeight();
        };
    }

    public int getMaxContentScroll() {
        return Math.max(0, getCurrentTabContentHeight() - getContentViewportHeight());
    }

    public boolean isScrollable() {
        return getMaxContentScroll() > 0;
    }

    private int getScrollbarX() {
        return getContentBoundsLeft() + getContentBoundsWidth() - getContentPadding() - SCROLLBAR_WIDTH;
    }

    private int getScrollbarThumbHeight() {
        int totalHeight = getCurrentTabContentHeight();
        int viewportHeight = getContentViewportHeight();
        if (totalHeight <= 0) {
            return viewportHeight;
        }
        return Math.max(24, viewportHeight * viewportHeight / totalHeight);
    }

    private int getScrollbarThumbY() {
        int maxScroll = getMaxContentScroll();
        if (maxScroll <= 0) {
            return getContentViewportTop();
        }
        int trackHeight = getContentViewportHeight() - getScrollbarThumbHeight();
        return getContentViewportTop() + (int) Math.round(trackHeight * (contentScroll / maxScroll));
    }

    private boolean isPointInsideContent(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, getContentBoundsLeft(), getContentBoundsTop(), getContentBoundsWidth(), getContentBoundsHeight());
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public <T extends GuiEventListener & Renderable & NarratableEntry> T addChild(T child) {
        if (buildingContentWidgets && child instanceof AbstractWidget clickable) {
            contentWidgets.add(clickable);
        }
        return addRenderableWidget(child);
    }

    public record SettingRowLayout(
            int labelX,
            int labelY,
            int helpY,
            int controlX,
            int controlY,
            int controlWidth,
            int controlHeight,
            boolean stacked,
            int rowHeight
    ) {
    }
}
