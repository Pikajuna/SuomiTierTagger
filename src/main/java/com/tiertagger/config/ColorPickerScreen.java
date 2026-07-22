package com.tiertagger.config;

import com.mojang.blaze3d.platform.NativeImage;
import com.tiertagger.ui.OverlayTheme;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onPick;

    private float hue;   // 0..360
    private float sat;   // 0..1
    private float val;   // 0..1

    private int hueX;
    private int hueY;
    private int hueW;
    private int hueH;
    private int svX;
    private int svY;
    private int svW;
    private int svH;

    private boolean draggingHue = false;
    private boolean draggingSV = false;
    private Button applyButton;
    private Button cancelButton;

    private DynamicTexture hueBarTexture;
    private DynamicTexture svTexture;
    private float lastSvHue = Float.NaN;

    private static final Identifier HUE_BAR_ID = Identifier.fromNamespaceAndPath("tiertagger", "color_picker_hue");
    private static final Identifier SV_ID = Identifier.fromNamespaceAndPath("tiertagger", "color_picker_sv");

    public ColorPickerScreen(Screen parent, String initialHex, Consumer<String> onPick) {
        super(Component.translatable("tt.color_picker.title"));
        this.parent = parent;
        this.onPick = onPick;
        int rgb = parseHexSafe(initialHex);
        float[] hsv = rgbToHsv(rgb);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int panelY = this.height / 2 - 82;

        hueW = 18;
        hueH = 100;
        hueX = centerX - 132;
        hueY = panelY + 50;

        svW = 100;
        svH = 100;
        svX = centerX - svW / 2;
        svY = panelY + 50;

        // Textures built lazily in render() to ensure GPU context is ready

        applyButton = Button.builder(Component.translatable("tt.action.apply"), b -> {
            String hex = toHex(hsvToRgb(hue, sat, val));
            onPick.accept(hex);
            Minecraft.getInstance().setScreen(parent);
        }).bounds(centerX + 3, svY + svH + 18, 82, 20).build();
        applyButton.setAlpha(0.0F);
        this.addRenderableWidget(applyButton);

        cancelButton = Button.builder(Component.translatable("gui.cancel"), b -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(centerX - 85, svY + svH + 18, 82, 20).build();
        cancelButton.setAlpha(0.0F);
        this.addRenderableWidget(cancelButton);
    }

    private void buildHueBarTexture() {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, hueW, hueH, false);
        for (int i = 0; i < hueH; i++) {
            float h = 360f * i / (hueH - 1);
            int abgr = toNativeImageArgb(hsvToRgb(h, 1f, 1f));
            for (int x = 0; x < hueW; x++) {
                img.setPixel(x, i, abgr);
            }
        }
        if (hueBarTexture != null) hueBarTexture.close();
        hueBarTexture = new DynamicTexture(() -> "tiertagger_color_picker_hue", img);
        Minecraft.getInstance().getTextureManager().register(HUE_BAR_ID, hueBarTexture);
    }

    private void buildSvTexture() {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, svW, svH, false);
        for (int y = 0; y < svH; y++) {
            float v = 1f - (float) y / (svH - 1);
            for (int x = 0; x < svW; x++) {
                float s = (float) x / (svW - 1);
                img.setPixel(x, y, toNativeImageArgb(hsvToRgb(hue, s, v)));
            }
        }
        if (svTexture != null) svTexture.close();
        svTexture = new DynamicTexture(() -> "tiertagger_color_picker_sv", img);
        Minecraft.getInstance().getTextureManager().register(SV_ID, svTexture);
        lastSvHue = hue;
    }

    // NativeImage pixel format is ABGR, not ARGB
    private static int toNativeImageArgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        OverlayTheme.drawScrim(ctx, this.width, this.height);
        int panelWidth = Math.min(304, this.width - 8);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = this.height / 2 - 82;
        int panelHeight = 190;
        ctx.fill(panelX + 4, panelY + 5, panelX + panelWidth + 4, panelY + panelHeight + 5, 0x66000000);
        OverlayTheme.drawPanel(ctx, panelX, panelY, panelWidth, panelHeight,
                OverlayTheme.SHELL_BG, OverlayTheme.SHELL_EDGE);
        ctx.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 34, OverlayTheme.HEADER_BG);
        ctx.fill(panelX, panelY + 33, panelX + panelWidth, panelY + 35, OverlayTheme.ACCENT_DARK);
        ctx.text(this.font, this.title, panelX + 12, panelY + 12, OverlayTheme.TEXT_PRIMARY);

        // Build hue bar once; rebuild SV texture when hue changes
        if (hueBarTexture == null) buildHueBarTexture();
        if (Float.isNaN(lastSvHue) || lastSvHue != hue) {
            buildSvTexture();
        }

        // Hue bar — one draw call
        ctx.text(this.font, Component.translatable("tt.color_picker.hue"), hueX, hueY - 13,
                OverlayTheme.TEXT_MUTED, false);
        ctx.fill(hueX - 2, hueY - 2, hueX + hueW + 2, hueY + hueH + 2, OverlayTheme.CARD_EDGE);
        ctx.blit(RenderPipelines.GUI_TEXTURED, HUE_BAR_ID, hueX, hueY, 0f, 0f, hueW, hueH, hueW, hueH);
        int selY = (int) (hueY + (hue / 360f) * (hueH - 1));
        ctx.fill(hueX - 2, selY - 1, hueX + hueW + 2, selY + 1, 0xFFFFFFFF);

        // SV square — one draw call
        ctx.text(this.font, Component.translatable("tt.color_picker.saturation"), svX, svY - 13,
                OverlayTheme.TEXT_MUTED, false);
        ctx.fill(svX - 2, svY - 2, svX + svW + 2, svY + svH + 2, OverlayTheme.CARD_EDGE);
        ctx.blit(RenderPipelines.GUI_TEXTURED, SV_ID, svX, svY, 0f, 0f, svW, svH, svW, svH);
        int sx = (int) (svX + sat * (svW - 1));
        int sy = (int) (svY + (1f - val) * (svH - 1));
        ctx.fill(sx - 4, sy - 1, sx + 5, sy + 1, 0xFFFFFFFF);
        ctx.fill(sx - 1, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);

        // Preview swatch
        int rgb = hsvToRgb(hue, sat, val);
        int cx = svX + svW + 22;
        int cy = svY;
        ctx.text(this.font, Component.translatable("tt.color_picker.preview"), cx, cy - 13,
                OverlayTheme.TEXT_MUTED, false);
        ctx.fill(cx - 2, cy - 2, cx + 38, cy + 38, OverlayTheme.CARD_EDGE);
        ctx.fill(cx, cy, cx + 36, cy + 36, 0xFF000000 | (rgb & 0xFFFFFF));
        ctx.text(this.font, toHex(rgb), cx, cy + 43, OverlayTheme.TEXT_PRIMARY);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        drawThemedButton(ctx, cancelButton, false);
        drawThemedButton(ctx, applyButton, true);
    }

    private void drawThemedButton(GuiGraphicsExtractor context, Button button, boolean primary) {
        if (button == null || !button.visible) return;
        int fill = primary
                ? (button.isHovered() ? OverlayTheme.ACCENT_HOVER : OverlayTheme.ACCENT)
                : (button.isHovered() ? OverlayTheme.CONTROL_HOVER : OverlayTheme.CONTROL_BG);
        int border = primary ? OverlayTheme.ACCENT_HOVER : OverlayTheme.CARD_EDGE;
        OverlayTheme.drawPanel(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), fill, border);
        context.centeredText(this.font, button.getMessage(),
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - this.font.lineHeight) / 2,
                primary ? 0xFFFFFFFF : OverlayTheme.TEXT_SECONDARY);
    }

    @Override
    public void removed() {
        super.removed();
        if (hueBarTexture != null) {
            Minecraft.getInstance().getTextureManager().release(HUE_BAR_ID);
            hueBarTexture.close();
            hueBarTexture = null;
        }
        if (svTexture != null) {
            Minecraft.getInstance().getTextureManager().release(SV_ID);
            svTexture.close();
            svTexture = null;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean consumed) {
        return handleMouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, consumed);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        return handleMouseDragged(click.x(), click.y(), click.button(), dx, dy) || super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        handleMouseReleased(click.x(), click.y(), click.button());
        return super.mouseReleased(click);
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (inside((int) mouseX, (int) mouseY, hueX, hueY, hueW, hueH)) {
            draggingHue = true;
            updateHue((int) mouseY);
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, svX, svY, svW, svH)) {
            draggingSV = true;
            updateSV((int) mouseX, (int) mouseY);
            return true;
        }
        return false;
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        draggingHue = false;
        draggingSV = false;
        return true;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingHue) {
            updateHue((int) mouseY);
            return true;
        }
        if (draggingSV) {
            updateSV((int) mouseX, (int) mouseY);
            return true;
        }
        return false;
    }

    private void updateHue(int y) {
        y = Math.max(hueY, Math.min(hueY + hueH - 1, y));
        hue = Math.max(0f, Math.min(360f, (y - hueY) / (float) (hueH - 1) * 360f));
    }

    private void updateSV(int x, int y) {
        x = Math.max(svX, Math.min(svX + svW - 1, x));
        y = Math.max(svY, Math.min(svY + svH - 1, y));
        sat = (x - svX) / (float) (svW - 1);
        val = 1f - (y - svY) / (float) (svH - 1);
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int parseHexSafe(String hex) {
        if (hex == null) return 0xFFFFFF;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return 0xFFFFFF;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private static String toHex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hh = (h / 60f) % 6f;
        float x = c * (1f - Math.abs((hh % 2f) - 1f));
        float r1 = 0, g1 = 0, b1 = 0;
        if      (hh < 1) { r1 = c; g1 = x; }
        else if (hh < 2) { r1 = x; g1 = c; }
        else if (hh < 3) { g1 = c; b1 = x; }
        else if (hh < 4) { g1 = x; b1 = c; }
        else if (hh < 5) { r1 = x; b1 = c; }
        else             { r1 = c; b1 = x; }
        float m = v - c;
        int r = Math.round((r1 + m) * 255f);
        int g = Math.round((g1 + m) * 255f);
        int b = Math.round((b1 + m) * 255f);
        return (r << 16) | (g << 8) | b;
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if      (d == 0)   h = 0;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * (((b - r) / d) + 2f);
        else               h = 60f * (((r - g) / d) + 4f);
        if (h < 0) h += 360f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
