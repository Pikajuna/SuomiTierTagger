package com.tiertagger.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UpdateCheckerTest {
    private static final String SHA512 = "a".repeat(128);

    @Test
    void selectsNewestModrinthReleaseAndPrimaryJar() {
        String json = """
                [
                  {"version_number":"6.7.0+mc26.1","version_type":"beta","files":[
                    {"filename":"beta.jar","primary":true,"url":"https://cdn/beta.jar","hashes":{"sha512":"%s"}}]},
                  {"version_number":"6.6.3+mc26.1","version_type":"release","changelog":"Fixes","files":[
                    {"filename":"sources.jar","primary":false,"url":"https://cdn/sources.jar","hashes":{"sha512":"%s"}},
                    {"filename":"mod.jar","primary":true,"url":"https://cdn/mod.jar","hashes":{"sha512":"%s"}}]}
                ]
                """.formatted(SHA512, SHA512, SHA512);

        UpdateChecker.UpdateInfo info = UpdateChecker.getInstance()
                .parseAndCompare(json, "6.6.2+mc26.1", "26.1", "26.1");

        assertNotNull(info);
        assertEquals("6.6.3+mc26.1", info.latestVersion);
        assertEquals("https://cdn/mod.jar", info.downloadUrl);
        assertEquals("SHA-512", info.hashAlgorithm);
        assertEquals(SHA512, info.hash);
        assertEquals("Fixes", info.changelog);
    }

    @Test
    void ignoresCurrentVersion() {
        String json = """
                [{"version_number":"6.6.2+mc26.1","version_type":"release","files":[
                  {"filename":"mod.jar","primary":true,"url":"https://cdn/mod.jar","hashes":{"sha512":"%s"}}]}]
                """.formatted(SHA512);

        assertNull(UpdateChecker.getInstance()
                .parseAndCompare(json, "6.6.2+mc26.1", "26.1", "26.1"));
    }
}
