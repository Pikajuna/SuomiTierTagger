package com.tiertagger.config;

import com.tiertagger.render.TierTextFormatter;
import com.tiertagger.ui.OverlayTheme;
import com.tiertagger.util.TierUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

public class KitTab {
    private static final Identifier ICON_FONT = Identifier.fromNamespaceAndPath("minecraft", "tiertagger-icons-md");
    private static final int TILE_HEIGHT = 30;
    private static final int TILE_GAP = 7;

    private final AdvancedConfigScreen parent;
    private final List<String> options = new ArrayList<>();
    private int selectedIndex;

    public KitTab(AdvancedConfigScreen parent) {
        this.parent = parent;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        rebuildOptions();
        var cfg = ConfigManager.get();
        String current = cfg.selectedKit != null ? TierUtils.canonicalKit(cfg.selectedKit) : "All";
        int index = options.indexOf(current);
        selectedIndex = index >= 0 ? index : 0;
    }

    public void init() {
        Layout layout = layout();
        for (int i = 0; i < options.size(); i++) {
            int row = i / layout.columns;
            int col = i % layout.columns;
            int x = layout.gridX + col * (layout.buttonWidth + TILE_GAP);
            int y = parent.contentY(layout.gridY + row * (TILE_HEIGHT + TILE_GAP));
            final int index = i;
            parent.addThemedButton(optionText(options.get(i)), ignored -> {
                selectedIndex = index;
                parent.markDirty();
                com.tiertagger.TierTaggerClient.clearNametagCache();
            }, x, y, layout.buttonWidth, TILE_HEIGHT,
                    AdvancedConfigScreen.ButtonStyle.TILE, () -> selectedIndex == index);
        }
    }

    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Layout layout = layout();
        int cardY = parent.contentY(0);
        int pad = parent.getCardPadding();

        parent.drawCard(context, parent.getContentX(), cardY, parent.getContentWidth(), layout.cardHeight, true);
        parent.drawCardTitle(context, tr("tt.kit.title"), tr("tt.kit.help"),
                parent.getContentX() + pad, cardY + 10);

        String selected = options.isEmpty() ? "All" : options.get(selectedIndex);
        int previewX = parent.getContentX() + pad;
        int previewY = cardY + 40;
        int previewWidth = parent.getContentWidth() - pad * 2;
        OverlayTheme.drawInset(context, previewX, previewY, previewWidth, 32);
        context.text(parent.getFont(), tr("tt.kit.selected"), previewX + 8, previewY + 4,
                OverlayTheme.TEXT_MUTED, false);
        context.text(parent.getFont(), selectedPreview(selected),
                previewX + 8, previewY + 17, OverlayTheme.TEXT_PRIMARY);

        int helpWidth = parent.getContentWidth() - pad * 2;
        var helpLines = parent.getFont().split(Component.literal(tr("tt.kit.selection_help")), helpWidth);
        for (int i = 0; i < Math.min(2, helpLines.size()); i++) {
            context.text(parent.getFont(), helpLines.get(i), parent.getContentX() + pad,
                    cardY + layout.cardHeight - 25 + i * 9, OverlayTheme.TEXT_MUTED, false);
        }
    }

    private Component selectedPreview(String kit) {
        MutableComponent result = Component.empty();
        String icon = TierTextFormatter.iconForKit("All".equals(kit) ? "Sword" : kit);
        if (icon != null) {
            result.append(Component.literal(icon).setStyle(Style.EMPTY.withFont(new FontDescription.Resource(ICON_FONT))));
            result.append(Component.literal("  "));
        }
        result.append(Component.literal(displayOption(kit)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xF4F6F8))));
        result.append(Component.literal("   HT2").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ConfigManager.colorFor("HT2")))));
        return result;
    }

    private Component optionText(String kit) {
        String icon = TierTextFormatter.iconForKit(kit);
        if (icon == null) return Component.literal(displayOption(kit));
        return Component.empty()
                .append(Component.literal(icon).setStyle(Style.EMPTY.withFont(new FontDescription.Resource(ICON_FONT))))
                .append(Component.literal("  " + displayOption(kit)));
    }

    public void save() {
        if (!options.isEmpty()) ConfigManager.setSelectedKit(options.get(selectedIndex));
    }

    public int getContentHeight() {
        return layout().cardHeight;
    }

    private Layout layout() {
        int width = parent.getContentWidth();
        int columns;
        if (parent.getLayoutTier() == AdvancedConfigScreen.LayoutTier.WIDE) {
            columns = 3;
        } else if (parent.getLayoutTier() == AdvancedConfigScreen.LayoutTier.MEDIUM) {
            columns = 3;
        } else {
            columns = width >= 300 ? 2 : 1;
        }

        int innerWidth = width - parent.getCardPadding() * 2;
        int buttonWidth = (innerWidth - (columns - 1) * TILE_GAP) / columns;
        int rows = (int) Math.ceil(options.size() / (double) columns);
        int gridWidth = columns * buttonWidth + (columns - 1) * TILE_GAP;
        int gridX = parent.getContentX() + Math.max(parent.getCardPadding(), (width - gridWidth) / 2);
        int gridY = 82;
        int cardHeight = gridY + rows * TILE_HEIGHT + Math.max(0, rows - 1) * TILE_GAP + 30;
        return new Layout(columns, buttonWidth, gridX, gridY, cardHeight);
    }

    private void rebuildOptions() {
        options.clear();
        options.add("All");
        for (String kit : new String[]{"Sword", "Axe", "NethPot", "UHC", "DiaPot", "Crystal", "SMP", "Mace"}) {
            options.add(kit);
        }
    }

    private String displayOption(String option) {
        return "All".equals(option) ? tr("tt.kit.all") : option;
    }

    private static String tr(String key) {
        return I18n.get(key);
    }

    private record Layout(int columns, int buttonWidth, int gridX, int gridY, int cardHeight) {
    }
}
