package com.secureqr.scanner.utils;

import java.security.SecureRandom;

public final class PasswordGeneratorEngine {
    public static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String DIGITS = "0123456789";
    public static final String SYMBOLS = "!@#$%^&*";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGeneratorEngine() {
    }

    public static String generate(Options options) {
        String lower = options.includeLower ? filter(LOWER, options) : "";
        String upper = options.includeUpper ? filter(UPPER, options) : "";
        String digits = options.includeDigits ? filter(DIGITS, options) : "";
        String symbols = options.includeSymbols ? SYMBOLS : "";
        String pool = lower + upper + digits + symbols;
        if (pool.isEmpty()) return "";

        StringBuilder builder = new StringBuilder();
        appendRequired(builder, lower, options.length);
        appendRequired(builder, upper, options.length);
        appendRequired(builder, digits, options.length);
        appendRequired(builder, symbols, options.length);
        while (builder.length() < options.length) {
            builder.append(randomChar(pool));
        }
        return shuffle(builder.toString());
    }

    public static int strengthScore(String password) {
        if (password == null) return 0;
        int score = 0;
        if (password.length() >= 8) score += 20;
        if (password.length() >= 12) score += 20;
        if (password.matches(".*[a-z].*")) score += 15;
        if (password.matches(".*[A-Z].*")) score += 15;
        if (password.matches(".*\\d.*")) score += 15;
        if (password.matches(".*[^A-Za-z0-9].*")) score += 15;
        return Math.min(100, score);
    }

    private static void appendRequired(StringBuilder builder, String chars, int maxLength) {
        if (!chars.isEmpty() && builder.length() < maxLength) builder.append(randomChar(chars));
    }

    private static String filter(String source, Options options) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (options.excludeZeroO && (c == '0' || c == 'O')) continue;
            if (options.excludeOneI && (c == '1' || c == 'I')) continue;
            if (options.excludeLowerL && c == 'l') continue;
            builder.append(c);
        }
        return builder.toString();
    }

    private static char randomChar(String pool) {
        return pool.charAt(RANDOM.nextInt(pool.length()));
    }

    private static String shuffle(String value) {
        char[] chars = value.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    public static final class Options {
        public int length = 8;
        public boolean includeUpper = true;
        public boolean includeLower = true;
        public boolean includeDigits = true;
        public boolean includeSymbols = true;
        public boolean excludeZeroO;
        public boolean excludeOneI;
        public boolean excludeLowerL;

        public Options copy() {
            Options copy = new Options();
            copy.length = length;
            copy.includeUpper = includeUpper;
            copy.includeLower = includeLower;
            copy.includeDigits = includeDigits;
            copy.includeSymbols = includeSymbols;
            copy.excludeZeroO = excludeZeroO;
            copy.excludeOneI = excludeOneI;
            copy.excludeLowerL = excludeLowerL;
            return copy;
        }
    }
}

