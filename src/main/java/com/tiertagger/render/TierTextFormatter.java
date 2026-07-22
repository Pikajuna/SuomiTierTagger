package com.tiertagger.render;

import com.tiertagger.config.ConfigManager;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/** Builds tier text consistently for all display surfaces. */
public final class TierTextFormatter {
    private static final Identifier MEDIUM_ICON_FONT = Identifier.fromNamespaceAndPath("minecraft", "tiertagger-icons-md");
    private static final Identifier SMALL_ICON_FONT = Identifier.fromNamespaceAndPath("minecraft", "tiertagger-icons-sm");
    private static final String SEPARATOR = " | ";

    private TierTextFormatter() {}

    public static MutableComponent formatNametag(
            TierDisplayResolver.TierDisplay display,
            ConfigManager.SlotPosition position,
            ConfigManager.SeparatorMode separatorMode,
            ToIntFunction<String> colorResolver
    ) {
        if (display == null) return null;
        String baseTier = TierDisplayResolver.baseTier(display.tier());
        int tierColor = colorResolver.applyAsInt(baseTier);
        MutableComponent result = Component.empty();

        if (position == ConfigManager.SlotPosition.RIGHT) {
            appendSeparator(result, separatorMode, tierColor, colorResolver);
        }
        appendIcon(result, display.kit(), MEDIUM_ICON_FONT, true);
        result.append(colored(display.tier(), tierColor));
        if (position == ConfigManager.SlotPosition.LEFT) {
            appendSeparator(result, separatorMode, tierColor, colorResolver);
        }
        return result;
    }

    public static MutableComponent formatChat(
            TierDisplayResolver.TierDisplay display,
            ToIntFunction<String> colorResolver
    ) {
        if (display == null) return null;
        MutableComponent result = Component.empty();
        appendIcon(result, display.kit(), SMALL_ICON_FONT, false);
        result.append(colored(display.tier(), colorResolver.applyAsInt(TierDisplayResolver.baseTier(display.tier()))));
        return result;
    }

    public static String iconForKit(String kit) {
        if (kit == null) return null;
        return switch (kit) {
            case "Axe" -> "\uE701";
            case "Mace" -> "\uE702";
            case "NethPot" -> "\uE703";
            case "DiaPot" -> "\uE704";
            case "SMP" -> "\uE705";
            case "Sword" -> "\uE706";
            case "UHC" -> "\uE707";
            case "Crystal" -> "\uE708";
            default -> null;
        };
    }

    private static void appendIcon(MutableComponent target, String kit, Identifier font, boolean trailingSpace) {
        String icon = iconForKit(kit);
        if (icon == null) return;
        target.append(Component.literal(icon).setStyle(
                Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(font))));
        if (trailingSpace) target.append(Component.literal(" "));
    }

    private static MutableComponent colored(String value, int color) {
        return Component.literal(value).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    private static void appendSeparator(
            MutableComponent target,
            ConfigManager.SeparatorMode mode,
            int tierColor,
            ToIntFunction<String> colorResolver
    ) {
        if (mode == ConfigManager.SeparatorMode.OFF) {
            target.append(Component.literal(" "));
            return;
        }
        int color = mode == ConfigManager.SeparatorMode.ADAPTIVE
                ? tierColor
                : colorResolver.applyAsInt("SEPARATOR_STATIC");
        target.append(colored(SEPARATOR, color));
    }
}
