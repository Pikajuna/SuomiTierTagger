package com.tiertagger.mixin;

import com.tiertagger.TierTaggerClient;
import com.tiertagger.config.ConfigManager;
import com.tiertagger.net.RankService;
import com.tiertagger.render.TierDisplayResolver;
import com.tiertagger.render.TierTextFormatter;
import com.tiertagger.util.GameProfileCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.scores.PlayerTeam;

@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin {
    private static final Pattern PAPI_PATTERN = Pattern.compile("%\\w+%");

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void tiertagger$appendTierToTabList(CallbackInfoReturnable<Component> cir) {
        if (ConfigManager.isDisabled() || !ConfigManager.isShowTabTiers()) return;
        Component original = cir.getReturnValue();
        PlayerInfo self = (PlayerInfo) (Object) this;
        String name = GameProfileCompat.getName(self.getProfile());
        if ((name == null || name.isBlank()) && original != null) name = original.getString();
        if (name == null || name.isBlank()) return;

        ConfigManager.ConfigData config = ConfigManager.get();
        ConfigManager.SlotPosition position = ConfigManager.getSlotAPosition();
        if (position == ConfigManager.SlotPosition.OFF) return;
        ConfigManager.SeparatorMode separatorMode = ConfigManager.getSeparatorMode();
        boolean showPeak = ConfigManager.isShowPeakInNametag();
        String cacheKey = "tabtier:" + name.toLowerCase(Locale.ROOT) + ":" + config.selectedKit
                + ":" + position.name() + ":" + separatorMode.name() + ":" + showPeak;

        Component cached = TierTaggerClient.nametagCache.get(cacheKey);
        MutableComponent tierText;
        if (cached != null) {
            tierText = cached.copy();
        } else {
            RankService ranks = RankService.getInstance();
            TierDisplayResolver.TierDisplay display = TierDisplayResolver.resolve(
                    config.selectedKit,
                    ranks.getAllKitTiersForName(name),
                    ranks.getAllRetiredKitTiersForName(name),
                    ranks.getPeakKitTiersForName(name),
                    showPeak
            );
            tierText = TierTextFormatter.formatNametag(display, position, separatorMode, ConfigManager::colorFor);
            if (tierText == null) return;
            TierTaggerClient.putNametagCache(cacheKey, tierText.copy());
        }

        Component baseName = original != null
                ? stripPapiPlaceholders(original)
                : formatTeamName(self.getTeam(), name);
        MutableComponent result = Component.empty();
        if (position == ConfigManager.SlotPosition.LEFT) result.append(tierText);
        result.append(baseName);
        if (position == ConfigManager.SlotPosition.RIGHT) result.append(tierText);
        cir.setReturnValue(result);
    }

    private static Component stripPapiPlaceholders(Component text) {
        return text.getString().contains("%") ? rebuildStripped(text) : text;
    }

    private static Component formatTeamName(PlayerTeam team, String name) {
        MutableComponent literal = Component.literal(name);
        return team != null ? team.getFormattedName(literal) : literal;
    }

    private static MutableComponent rebuildStripped(Component text) {
        MutableComponent result;
        if (text.getContents() instanceof PlainTextContents plain) {
            String raw = plain.text();
            String cleaned = PAPI_PATTERN.matcher(raw).replaceAll("");
            if (!cleaned.equals(raw)) cleaned = cleaned.strip();
            result = Component.literal(cleaned).setStyle(text.getStyle());
        } else {
            result = MutableComponent.create(text.getContents()).setStyle(text.getStyle());
        }
        for (Component sibling : text.getSiblings()) result.append(rebuildStripped(sibling));
        return result;
    }
}
