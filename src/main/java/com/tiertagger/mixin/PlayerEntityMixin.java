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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void tiertagger$appendTierLabel(CallbackInfoReturnable<Component> cir) {
        if (ConfigManager.isDisabled() || !ConfigManager.isShowWorldNametags()) return;
        Component original = cir.getReturnValue();
        if (original == null) return;

        ConfigManager.ConfigData config = ConfigManager.get();
        Minecraft client = Minecraft.getInstance();
        Player self = (Player) (Object) this;
        if (client != null && client.player != null
                && self.getUUID().equals(client.player.getUUID())
                && Boolean.FALSE.equals(config.showSelf)) {
            return;
        }

        String name = GameProfileCompat.getName(self.getGameProfile());
        if (name == null || name.isBlank()) name = original.getString();
        if (name == null || name.isBlank()) return;

        ConfigManager.SlotPosition position = ConfigManager.getSlotAPosition();
        if (position == ConfigManager.SlotPosition.OFF) return;
        ConfigManager.SeparatorMode separatorMode = ConfigManager.getSeparatorMode();
        boolean showPeak = ConfigManager.isShowPeakInNametag();
        String cacheKey = "tier:" + name.toLowerCase(Locale.ROOT) + ":" + config.selectedKit
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

        MutableComponent result = Component.empty();
        if (position == ConfigManager.SlotPosition.LEFT) result.append(tierText);
        result.append(original);
        if (position == ConfigManager.SlotPosition.RIGHT) result.append(tierText);
        cir.setReturnValue(result);
    }
}
