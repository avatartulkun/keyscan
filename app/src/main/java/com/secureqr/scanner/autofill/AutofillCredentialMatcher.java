package com.secureqr.scanner.autofill;

import android.text.TextUtils;

import com.secureqr.scanner.data.model.PasswordEntry;

import java.net.URI;
import java.util.Locale;

public final class AutofillCredentialMatcher {
    private AutofillCredentialMatcher() {
    }

    public static String displayTitle(PasswordEntry entry) {
        if (entry == null) return "";
        return nonEmpty(entry.title, entry.remark, entry.websiteDomain, entry.appPackageName);
    }

    public static String displayUsername(PasswordEntry entry) {
        if (entry == null) return "";
        return nonEmpty(entry.username, entry.account);
    }

    public static String normalizeDomain(String value) {
        if (value == null) return "";
        String input = value.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) return "";
        try {
            URI uri = input.contains("://") ? URI.create(input) : URI.create("https://" + input);
            if (uri.getHost() != null) input = uri.getHost();
        } catch (Exception ignored) {
            int slash = input.indexOf('/');
            if (slash >= 0) input = input.substring(0, slash);
        }
        int at = input.lastIndexOf('@');
        if (at >= 0 && at + 1 < input.length()) input = input.substring(at + 1);
        int colon = input.indexOf(':');
        if (colon >= 0) input = input.substring(0, colon);
        while (input.startsWith("www.")) input = input.substring(4);
        return input.trim();
    }

    public static String normalizePackage(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matches(PasswordEntry entry, String packageName, String webDomain) {
        if (entry == null || TextUtils.isEmpty(entry.password)) return false;
        String targetDomain = normalizeDomain(webDomain);
        if (!targetDomain.isEmpty()) {
            String entryDomain = normalizeDomain(nonEmpty(entry.websiteDomain, entry.remark, entry.title));
            return domainMatches(targetDomain, entryDomain);
        }

        String targetPackage = normalizePackage(packageName);
        String entryPackage = normalizePackage(entry.appPackageName);
        return !targetPackage.isEmpty() && targetPackage.equals(entryPackage);
    }

    public static boolean sameUsername(PasswordEntry entry, String username) {
        if (entry == null || TextUtils.isEmpty(username)) return false;
        return username.trim().equals(displayUsername(entry));
    }

    public static String registrableDomain(String value) {
        String domain = normalizeDomain(value);
        if (domain.isEmpty()) return "";
        String[] labels = domain.split("\\.");
        int suffixLabels = publicSuffixLabelCount(labels);
        if (labels.length <= suffixLabels) return domain;
        StringBuilder builder = new StringBuilder();
        for (int i = labels.length - suffixLabels - 1; i < labels.length; i++) {
            if (builder.length() > 0) builder.append('.');
            builder.append(labels[i]);
        }
        return builder.toString();
    }

    public static boolean sameRegistrableDomain(String first, String second) {
        String firstDomain = registrableDomain(first);
        String secondDomain = registrableDomain(second);
        return !firstDomain.isEmpty() && firstDomain.equals(secondDomain);
    }

    public static String bestTitleFromContext(String webDomain, String packageName) {
        String domain = normalizeDomain(webDomain);
        if (!domain.isEmpty()) return domain;
        return normalizePackage(packageName);
    }

    public static boolean domainMatches(String targetDomain, String entryDomain) {
        if (targetDomain == null || entryDomain == null) return false;
        String target = normalizeDomain(targetDomain);
        String entry = normalizeDomain(entryDomain);
        if (target.isEmpty() || entry.isEmpty()) return false;
        return target.equals(entry)
                || target.endsWith("." + entry)
                || sameRegistrableDomain(target, entry);
    }

    public static String nonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static int publicSuffixLabelCount(String[] labels) {
        if (labels.length < 2) return 1;
        String suffix2 = labels[labels.length - 2] + "." + labels[labels.length - 1];
        if ("com.cn".equals(suffix2)
                || "net.cn".equals(suffix2)
                || "org.cn".equals(suffix2)
                || "gov.cn".equals(suffix2)
                || "co.uk".equals(suffix2)
                || "org.uk".equals(suffix2)
                || "ac.uk".equals(suffix2)
                || "com.au".equals(suffix2)
                || "net.au".equals(suffix2)
                || "co.jp".equals(suffix2)
                || "com.hk".equals(suffix2)) {
            return 2;
        }
        return 1;
    }
}
