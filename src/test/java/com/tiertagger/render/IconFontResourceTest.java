package com.tiertagger.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IconFontResourceTest {
    private static final String[] FONT_IDS = {
            "tiertagger-icons",
            "tiertagger-icons-md",
            "tiertagger-icons-sm"
    };

    @Test
    void everyIconFontMapsAllKitGlyphsToPackagedTextures() throws Exception {
        for (String fontId : FONT_IDS) {
            String fontPath = "/assets/minecraft/font/" + fontId + ".json";
            try (InputStream stream = getClass().getResourceAsStream(fontPath)) {
                assertNotNull(stream, "Missing font resource " + fontPath);
                JsonObject font = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray providers = font.getAsJsonArray("providers");
                Set<Integer> glyphs = new HashSet<>();

                for (var providerElement : providers) {
                    JsonObject provider = providerElement.getAsJsonObject();
                    String[] resourceId = provider.get("file").getAsString().split(":", 2);
                    String texturePath = "/assets/" + resourceId[0] + "/textures/" + resourceId[1];
                    try (InputStream texture = getClass().getResourceAsStream(texturePath)) {
                        assertNotNull(texture, "Missing texture " + texturePath);
                    }

                    for (var row : provider.getAsJsonArray("chars")) {
                        row.getAsString().codePoints().forEach(glyphs::add);
                    }
                }

                assertEquals(expectedGlyphs(), glyphs, "Wrong glyph map in " + fontPath);
            }
        }
    }

    private static Set<Integer> expectedGlyphs() {
        Set<Integer> glyphs = new HashSet<>();
        for (int glyph = 0xE701; glyph <= 0xE708; glyph++) {
            glyphs.add(glyph);
        }
        return glyphs;
    }
}
