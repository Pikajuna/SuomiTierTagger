package com.tiertagger.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class ColorsTab {
    private static final String[] HT_CODES = {"HT1", "HT2", "HT3", "HT4", "HT5"};
    private static final String[] LT_CODES = {"LT1", "LT2", "LT3", "LT4", "LT5"};
    private static final String[] EXTRA_CODES  = {"SEPARATOR_STATIC", "PEAK_INDICATOR"};
    private static final String[] EXTRA_LABEL_KEYS = {"tt.colors.separator", "tt.colors.peak"};
    private static final int FIELD_WIDTH = 68;
    private static final int PREVIEW_SIZE = 18;
    private static final int RESET_WIDTH = 16;
    private static final int GROUP_WIDTH = FIELD_WIDTH + 6 + PREVIEW_SIZE + 4 + RESET_WIDTH;
    private static final int COMPARISON_CARD_HEIGHT = 300;
    private static final int STACKED_CARD_HEIGHT = 388;

    private final AdvancedConfigScreen parent;
    private final Map<String, EditBox> fields = new LinkedHashMap<>();
    private final Map<String, PreviewBounds> previews = new LinkedHashMap<>();
    private final Map<String, String> tempColors = new LinkedHashMap<>();

    public ColorsTab(AdvancedConfigScreen parent) {
        this.parent = parent;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        tempColors.clear();
        var cfg = ConfigManager.get();
        if (cfg.tierColors != null) {
            tempColors.putAll(cfg.tierColors);
        }
        for (String code : HT_CODES) {
            tempColors.putIfAbsent(code, "#FFFFFF");
        }
        for (String code : LT_CODES) {
            tempColors.putIfAbsent(code, "#FFFFFF");
        }
        for (String code : EXTRA_CODES) {
            tempColors.putIfAbsent(code, "#FFFFFF");
        }
    }

    public void init() {
        fields.clear();
        previews.clear();
        Layout layout = layout();

        if (layout.stacked()) {
            initStacked(layout);
            return;
        }

        for (int i = 0; i < HT_CODES.length; i++) {
            int logicalY = layout.firstRowY() + i * layout.rowGap();
            createField(HT_CODES[i], layout.htFieldX(), parent.contentY(logicalY) - 4);
            createField(LT_CODES[i], layout.ltFieldX(), parent.contentY(logicalY) - 4);
        }
        int extraBaseLogicalY = layout.firstRowY() + HT_CODES.length * layout.rowGap() + 8;
        for (int i = 0; i < EXTRA_CODES.length; i++) {
            createField(EXTRA_CODES[i], layout.htFieldX(), parent.contentY(extraBaseLogicalY + i * 28) - 4);
        }
    }

    private void initStacked(Layout layout) {
        for (int i = 0; i < HT_CODES.length; i++) {
            int blockTop = layout.firstRowY() + i * layout.rowGap();
            createField(HT_CODES[i], layout.htFieldX(), parent.contentY(blockTop + 12) - 4);
            createField(LT_CODES[i], layout.ltFieldX(), parent.contentY(blockTop + 30) - 4);
        }
        int extraBaseLogicalY = layout.firstRowY() + HT_CODES.length * layout.rowGap() + 8;
        for (int i = 0; i < EXTRA_CODES.length; i++) {
            createField(EXTRA_CODES[i], layout.htFieldX(), parent.contentY(extraBaseLogicalY + i * 28) - 4);
        }
    }

    private void createField(String code, int x, int y) {
        EditBox field = new EditBox(parent.getFont(), x, y, FIELD_WIDTH, parent.getControlHeight(), Component.literal(code));
        field.setMaxLength(7);
        field.setValue(tempColors.getOrDefault(code, "#FFFFFF"));
        field.setBordered(false);
        field.setResponder(value -> {
            String normalized = normalizeHex(value);
            if (normalized != null) {
                tempColors.put(code, normalized);
            }
            parent.markDirty();
        });
        parent.addChild(field);
        fields.put(code, field);
        int previewX = x + FIELD_WIDTH + 6;
        previews.put(code, new PreviewBounds(previewX, y - 1));
        parent.addThemedButton(Component.literal("R"), ignored -> resetColor(code),
                previewX + PREVIEW_SIZE + 4, y, RESET_WIDTH, parent.getControlHeight(),
                AdvancedConfigScreen.ButtonStyle.ICON, () -> false);
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        int renderY = parent.contentY(layout.cardY());
        int pad = parent.getCardPadding();

        parent.drawCard(context, layout.cardX(), renderY, layout.cardWidth(), layout.cardHeight(), true);
        parent.drawCardTitle(context, tr("tt.colors.title"), tr("tt.colors.help"), layout.cardX() + pad, renderY + 10);

        int sampleX = layout.cardX() + pad;
        int sampleY = renderY + 38;
        int sampleWidth = layout.cardWidth() - pad * 2;
        com.tiertagger.ui.OverlayTheme.drawInset(context, sampleX, sampleY, sampleWidth, 30);
        context.text(parent.getFont(), tr("tt.colors.preview"), sampleX + 7, sampleY + 4,
                parent.getMutedTextColor(), false);
        Component sample = Component.literal("HT2 | Steve").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(
                net.minecraft.network.chat.TextColor.fromRgb(currentColor("HT2"))));
        context.text(parent.getFont(), sample, sampleX + 7, sampleY + 16, parent.getPrimaryTextColor());

        for (EditBox field : fields.values()) {
            parent.drawTextField(context, field, normalizeHex(field.getValue()) != null);
        }

        if (layout.stacked()) {
            renderStacked(context, layout, renderY, pad);
        } else {
            renderComparison(context, layout, renderY, pad);
        }

        parent.drawMutedText(context, tr("tt.colors.input_help"), layout.cardX() + pad, renderY + layout.cardHeight() - 14);
    }

    private void renderComparison(GuiGraphicsExtractor context, Layout layout, int renderY, int pad) {
        int headerY = renderY + 76;
        context.text(parent.getFont(), tr("tt.colors.tier"), layout.tierLabelX(), headerY, parent.getPrimaryTextColor());
        drawGroupHeader(context, "HT", layout.htFieldX(), headerY);
        drawGroupHeader(context, "LT", layout.ltFieldX(), headerY);

        for (int i = 0; i < HT_CODES.length; i++) {
            EditBox htField = fields.get(HT_CODES[i]);
            int rowY = htField.getY() + 4;

            context.text(parent.getFont(), tr("tt.colors.tier_number", i + 1), layout.tierLabelX(), rowY, parent.getPrimaryTextColor());
            drawPreview(context, HT_CODES[i]);
            drawPreview(context, LT_CODES[i]);

            if (i < HT_CODES.length - 1) {
                int dividerY = rowY + 17;
                context.fill(layout.cardX() + pad, dividerY, layout.cardX() + layout.cardWidth() - pad, dividerY + 1,
                        com.tiertagger.ui.OverlayTheme.SHELL_EDGE_SOFT);
            }
        }
        int extraBaseY = parent.contentY(layout.firstRowY() + HT_CODES.length * layout.rowGap() + 8);
        for (int i = 0; i < EXTRA_CODES.length; i++) {
            int rowY = extraBaseY + i * 28;
            context.text(parent.getFont(), tr(EXTRA_LABEL_KEYS[i]), layout.tierLabelX(), rowY + 4, parent.getPrimaryTextColor());
            drawPreview(context, EXTRA_CODES[i]);
        }
    }

    private void renderStacked(GuiGraphicsExtractor context, Layout layout, int renderY, int pad) {
        int labelX = layout.cardX() + pad;

        for (int i = 0; i < HT_CODES.length; i++) {
            int blockY = renderY + layout.firstRowY() + i * layout.rowGap();
            EditBox htField = fields.get(HT_CODES[i]);
            EditBox ltField = fields.get(LT_CODES[i]);

            context.text(parent.getFont(), tr("tt.colors.tier_number", i + 1), labelX, blockY, parent.getPrimaryTextColor());
            parent.drawMutedText(context, "HT", labelX, htField.getY() + 4);
            parent.drawMutedText(context, "LT", labelX, ltField.getY() + 4);
            drawPreview(context, HT_CODES[i]);
            drawPreview(context, LT_CODES[i]);

            if (i < HT_CODES.length - 1) {
                int dividerY = ltField.getY() + PREVIEW_SIZE + 2;
                context.fill(layout.cardX() + pad, dividerY, layout.cardX() + layout.cardWidth() - pad, dividerY + 1,
                        com.tiertagger.ui.OverlayTheme.SHELL_EDGE_SOFT);
            }
        }
        int extraBaseY = parent.contentY(layout.firstRowY() + HT_CODES.length * layout.rowGap() + 8);
        for (int i = 0; i < EXTRA_CODES.length; i++) {
            int rowY = extraBaseY + i * 28;
            context.text(parent.getFont(), tr(EXTRA_LABEL_KEYS[i]), layout.tierLabelX(), rowY + 4, parent.getPrimaryTextColor());
            drawPreview(context, EXTRA_CODES[i]);
        }
    }

    private void drawGroupHeader(GuiGraphicsExtractor context, String label, int fieldX, int y) {
        int headerX = fieldX + (GROUP_WIDTH - parent.getFont().width(label)) / 2;
        context.text(parent.getFont(), label, headerX, y, parent.getAccentColor());
    }

    private void drawPreview(GuiGraphicsExtractor context, String code) {
        PreviewBounds preview = previews.get(code);
        EditBox field = fields.get(code);
        String normalized = normalizeHex(field.getValue());
        int rgb = normalized != null ? parseHex(normalized) : parseHex(tempColors.getOrDefault(code, "#FFFFFF"));
        context.fill(preview.x() - 2, preview.y() - 2, preview.x() + PREVIEW_SIZE + 2, preview.y() + PREVIEW_SIZE + 2,
                com.tiertagger.ui.OverlayTheme.CARD_EDGE);
        context.fill(preview.x(), preview.y(), preview.x() + PREVIEW_SIZE, preview.y() + PREVIEW_SIZE, 0xFF000000 | (rgb & 0xFFFFFF));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        String hit = hitPreview((int) mouseX, (int) mouseY);
        if (hit == null) {
            return false;
        }

        EditBox field = fields.get(hit);
        parent.getClient().setScreen(new ColorPickerScreen(parent, field.getValue(), hex -> {
            field.setValue(hex);
            tempColors.put(hit, hex);
            parent.markDirty();
        }));
        return true;
    }

    private String hitPreview(int mouseX, int mouseY) {
        for (Map.Entry<String, PreviewBounds> entry : previews.entrySet()) {
            PreviewBounds preview = entry.getValue();
            if (inside(mouseX, mouseY, preview.x() - 2, preview.y() - 2, PREVIEW_SIZE + 4, PREVIEW_SIZE + 4)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void save() {
        Map<String, String> colors = new LinkedHashMap<>(tempColors);
        for (Map.Entry<String, EditBox> entry : fields.entrySet()) {
            String normalized = normalizeHex(entry.getValue().getValue());
            if (normalized != null) {
                colors.put(entry.getKey(), normalized);
            }
        }
        ConfigManager.setTierColors(colors);
    }

    public int getContentHeight() {
        return layout().cardHeight();
    }

    private Layout layout() {
        int cardX = parent.getContentX();
        int cardWidth = parent.getContentWidth();
        int pad = parent.getCardPadding();

        if (cardWidth < 320) {
            int fieldX = cardX + cardWidth - pad - GROUP_WIDTH;
            int tierLabelX = cardX + pad;
            return new Layout(cardX, 0, cardWidth, STACKED_CARD_HEIGHT, true, tierLabelX, fieldX, fieldX, 74, 48);
        }

        int innerWidth = cardWidth - pad * 2;
        int tierWidth = 46;
        int gap = parent.getLayoutTier() == AdvancedConfigScreen.LayoutTier.WIDE ? 14 : 10;
        int gridWidth = tierWidth + gap + GROUP_WIDTH + gap + GROUP_WIDTH;
        int tierLabelX = cardX + pad + Math.max(0, (innerWidth - gridWidth) / 2);
        int htFieldX = tierLabelX + tierWidth + gap;
        int ltFieldX = htFieldX + GROUP_WIDTH + gap;

        return new Layout(cardX, 0, cardWidth, COMPARISON_CARD_HEIGHT, false, tierLabelX, htFieldX, ltFieldX, 96, 24);
    }

    private void resetColor(String code) {
        String value = defaultColor(code);
        EditBox field = fields.get(code);
        if (field != null) field.setValue(value);
        tempColors.put(code, value);
        parent.markDirty();
    }

    private int currentColor(String code) {
        EditBox field = fields.get(code);
        String normalized = field == null ? tempColors.get(code) : normalizeHex(field.getValue());
        return parseHex(normalized == null ? defaultColor(code) : normalized);
    }

    private static String defaultColor(String code) {
        return switch (code) {
            case "HT1", "LT1" -> "#990000";
            case "HT2", "LT2" -> "#FF0000";
            case "HT3", "LT3" -> "#FF9900";
            case "HT4", "LT4" -> "#FFCC00";
            case "HT5", "LT5" -> "#FFFF00";
            case "SEPARATOR_STATIC" -> "#555555";
            case "PEAK_INDICATOR" -> "#AAAAAA";
            default -> "#FFFFFF";
        };
    }

    private static String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != 6) {
            return null;
        }
        for (int i = 0; i < 6; i++) {
            char c = text.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) {
                return null;
            }
        }
        return "#" + text.toUpperCase(Locale.ROOT);
    }

    private static int parseHex(String hex) {
        String value = hex;
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            return 0xFFFFFF;
        }
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    private record Layout(
            int cardX,
            int cardY,
            int cardWidth,
            int cardHeight,
            boolean stacked,
            int tierLabelX,
            int htFieldX,
            int ltFieldX,
            int firstRowY,
            int rowGap
    ) {
    }

    private record PreviewBounds(int x, int y) {
    }
}
