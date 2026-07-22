package com.tiertagger.util;

import java.util.Locale;

public final class TierUtils {
    private TierUtils() {}

    public static final String KIT_SWORD   = "Sword";
    public static final String KIT_AXE     = "Axe";
    public static final String KIT_NETHPOT = "NethPot";
    public static final String KIT_UHC     = "UHC";
    public static final String KIT_DIAPOT  = "DiaPot";
    public static final String KIT_CRYSTAL = "Crystal";
    public static final String KIT_SMP     = "SMP";
    public static final String KIT_MACE    = "Mace";

    public static int tierScore(String tier) {
        if (tier == null) return Integer.MAX_VALUE;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "HT1" -> 1;
            case "LT1" -> 2;
            case "HT2" -> 3;
            case "LT2" -> 4;
            case "HT3" -> 5;
            case "LT3" -> 6;
            case "HT4" -> 7;
            case "LT4" -> 8;
            case "HT5" -> 9;
            case "LT5" -> 10;
            default -> Integer.MAX_VALUE;
        };
    }

    public static int tierPoints(String tier) {
        if (tier == null || tier.isBlank()) return 0;
        String normalized = tier.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("R")) normalized = normalized.substring(1);
        return switch (normalized) {
            case "HT1" -> 60;
            case "LT1" -> 45;
            case "HT2" -> 30;
            case "LT2" -> 20;
            case "HT3" -> 10;
            case "LT3" -> 6;
            case "HT4" -> 4;
            case "LT4" -> 3;
            case "HT5" -> 2;
            case "LT5" -> 1;
            default -> 0;
        };
    }

    /** Strips leading emoji/symbols from a sheet tab name and maps it to a canonical kit name. */
    public static String canonicalKit(String displayTabName) {
        if (displayTabName == null) return null;
        String s = displayTabName;
        int i = 0;
        while (i < s.length() && !Character.isLetterOrDigit(s.charAt(i))) i++;
        s = s.substring(i).trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("beast") || lower.contains("sword")) return KIT_SWORD;
        if (lower.contains("axe")) return KIT_AXE;
        if (lower.contains("neth")) return KIT_NETHPOT;
        if (lower.contains("dia") && lower.contains("pot")) return KIT_DIAPOT;
        if (lower.contains("uhc")) return KIT_UHC;
        if (lower.contains("crystal")) return KIT_CRYSTAL;
        if (lower.contains("smp")) return KIT_SMP;
        if (lower.contains("mace")) return KIT_MACE;
        return s;
    }
}
