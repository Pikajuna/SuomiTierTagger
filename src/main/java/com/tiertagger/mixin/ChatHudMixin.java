package com.tiertagger.mixin;

import com.tiertagger.TierTaggerClient;
import com.tiertagger.config.ConfigManager;
import com.tiertagger.net.RankService;
import com.tiertagger.render.TierDisplayResolver;
import com.tiertagger.render.TierTextFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    private static final Pattern NAME_PATTERN = Pattern.compile("<([A-Za-z0-9_]{1,16})>");

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component tiertagger$injectChatIcon(Component message) {
        if (ConfigManager.isDisabled() || !ConfigManager.isShowChatTiers() || message == null) return message;

        Matcher matcher = NAME_PATTERN.matcher(message.getString());
        if (!matcher.find()) return message;
        String name = matcher.group(1);
        ConfigManager.ConfigData config = ConfigManager.get();
        boolean showPeak = ConfigManager.isShowPeakInNametag();
        String cacheKey = "chat:" + name.toLowerCase(Locale.ROOT) + ":" + config.selectedKit + ":" + showPeak;

        Component prefix = TierTaggerClient.nametagCache.get(cacheKey);
        if (prefix == null) {
            RankService ranks = RankService.getInstance();
            TierDisplayResolver.TierDisplay display = TierDisplayResolver.resolve(
                    config.selectedKit,
                    ranks.getAllKitTiersForName(name),
                    ranks.getAllRetiredKitTiersForName(name),
                    ranks.getPeakKitTiersForName(name),
                    showPeak
            );
            prefix = TierTextFormatter.formatChat(display, ConfigManager::colorFor);
            if (prefix == null) return message;
            TierTaggerClient.putNametagCache(cacheKey, prefix.copy());
        }

        MutableComponent result = Component.empty().append(prefix).append(Component.literal(" ")).append(message);
        return result;
    }
}
