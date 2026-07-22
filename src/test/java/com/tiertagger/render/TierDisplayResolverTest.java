package com.tiertagger.render;

import com.tiertagger.config.ConfigManager;
import com.tiertagger.util.TierUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TierDisplayResolverTest {
    @Test
    void sharedResolverUsesCurrentBeforeRetiredAndBetterPeakWhenEnabled() {
        TierDisplayResolver.TierDisplay display = TierDisplayResolver.resolve(
                "All",
                Map.of(TierUtils.KIT_SWORD, "LT2"),
                Map.of(TierUtils.KIT_SWORD, "RHT1", TierUtils.KIT_AXE, "RLT2"),
                Map.of(TierUtils.KIT_SWORD, "HT2"),
                true
        );

        assertEquals(new TierDisplayResolver.TierDisplay(TierUtils.KIT_SWORD, "HT2"), display);
    }

    @Test
    void sharedResolverReturnsNullWithoutBaselineTier() {
        assertNull(TierDisplayResolver.resolve("Sword", Map.of(), Map.of(), Map.of("Sword", "HT1"), true));
    }

    @Test
    void sharedFormatterProducesConsistentNametagAndChatOutput() {
        TierDisplayResolver.TierDisplay display =
                new TierDisplayResolver.TierDisplay(TierUtils.KIT_SWORD, "RHT2");

        String nametag = TierTextFormatter.formatNametag(
                display,
                ConfigManager.SlotPosition.LEFT,
                ConfigManager.SeparatorMode.STATIC,
                ignored -> 0xFFFFFF
        ).getString();
        String chat = TierTextFormatter.formatChat(display, ignored -> 0xFFFFFF).getString();

        assertEquals("\uE706 RHT2 | ", nametag);
        assertEquals("\uE706RHT2", chat);
    }
}
