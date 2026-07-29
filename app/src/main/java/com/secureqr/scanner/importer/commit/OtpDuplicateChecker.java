package com.secureqr.scanner.importer.commit;

import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.importer.model.ImportedOtp;

import java.util.List;
import java.util.Locale;

public final class OtpDuplicateChecker {
    public OtpToken findDuplicate(ImportedOtp incoming, List<OtpToken> existing) {
        if (incoming == null || existing == null) return null;
        String secret = normalizeSecret(incoming.secret);
        String account = normalize(incoming.account);
        if (secret.isEmpty() || account.isEmpty()) return null;
        for (OtpToken token : existing) {
            if (token != null && secret.equals(normalizeSecret(token.secret)) && account.equals(normalize(token.accountName))) return token;
        }
        return null;
    }

    private String normalizeSecret(String value) { return normalize(value).replace(" ", ""); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
