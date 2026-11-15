package com.capstone.fraudcase.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable hashing used for deterministic fixtures, request hashes and audit payload hashes. */
public final class Hashing {

    private Hashing() {
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Deterministic value in [0, 1) derived from the joined parts. Same input always yields the same value. */
    public static double unit(String... parts) {
        String hex = sha256(String.join("|", parts));
        long value = Long.parseUnsignedLong(hex.substring(0, 13), 16);
        return value / (double) (1L << 52);
    }

    /** Deterministic integer in [minInclusive, maxInclusive]. */
    public static int range(int minInclusive, int maxInclusive, String... parts) {
        return minInclusive + (int) Math.floor(unit(parts) * (maxInclusive - minInclusive + 1));
    }
}
