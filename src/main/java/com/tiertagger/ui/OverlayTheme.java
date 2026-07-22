package com.tiertagger.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class OverlayTheme {
    public static final int SCRIM             = 0xC20A0B0E;
    public static final int SHELL_BG          = 0xFA0E1015;
    public static final int SHELL_EDGE        = 0xFF30343D;
    public static final int SHELL_EDGE_SOFT   = 0xFF242832;
    public static final int HEADER_BG         = 0xFF12151B;
    public static final int SIDEBAR_BG        = 0xFF101218;
    public static final int CARD_BG           = 0xF5181B22;
    public static final int CARD_BG_ALT       = 0xF51C2028;
    public static final int CARD_EDGE         = 0xFF303541;
    public static final int CONTROL_BG        = 0xFF20242D;
    public static final int CONTROL_HOVER     = 0xFF292E39;
    public static final int CONTROL_DISABLED  = 0xFF171A20;
    public static final int ACCENT            = 0xFFE5393F;
    public static final int ACCENT_HOVER      = 0xFFFF4D52;
    public static final int ACCENT_DARK       = 0xFF8F1E24;
    public static final int ACCENT_DIM        = 0x55E5393F;
    public static final int ACCENT_SURFACE    = 0xFF351A20;
    public static final int TEXT_PRIMARY      = 0xFFF4F6F8;
    public static final int TEXT_SECONDARY    = 0xFFC9CED6;
    public static final int TEXT_MUTED        = 0xFF8D96A3;
    public static final int TEXT_DANGER       = 0xFFFF6B70;
    public static final int TEXT_SUCCESS      = 0xFF71D69A;

    private OverlayTheme() {
    }

    public static void drawScrim(GuiGraphicsExtractor context, int width, int height) {
        context.fill(0, 0, width, height, SCRIM);
        context.fillGradient(0, 0, width, height / 2, 0x220E1118, 0x00000000);
        context.fillGradient(0, height / 3, width, height, 0x00000000, 0x55000000);
    }

    public static void drawPanel(GuiGraphicsExtractor context, int x, int y, int width, int height, int fillColor, int borderColor) {
        int x2 = x + width;
        int y2 = y + height;
        context.fill(x, y, x2, y2, fillColor);
        context.fill(x, y, x2, y + 1, borderColor);
        context.fill(x, y2 - 1, x2, y2, borderColor);
        context.fill(x, y, x + 1, y2, borderColor);
        context.fill(x2 - 1, y, x2, y2, borderColor);
    }

    public static void drawDivider(GuiGraphicsExtractor context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, CARD_EDGE);
    }

    public static void drawCard(GuiGraphicsExtractor context, int x, int y, int width, int height, boolean accent) {
        drawPanel(context, x, y, width, height, accent ? CARD_BG_ALT : CARD_BG, CARD_EDGE);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, 0x33FFFFFF);
        if (accent) {
            context.fill(x, y, x + 2, y + height, ACCENT);
        }
    }

    public static void drawInset(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        drawPanel(context, x, y, width, height, CONTROL_BG, SHELL_EDGE_SOFT);
    }
}
