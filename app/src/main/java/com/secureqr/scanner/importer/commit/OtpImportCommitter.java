package com.secureqr.scanner.importer.commit;

import android.content.Context;

import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.importer.model.ImportedOtp;

import java.util.ArrayList;
import java.util.List;

/** Coordinates OTP imports through OtpRepository only. */
public final class OtpImportCommitter {
    private final OtpRepository repository;
    private final OtpDuplicateChecker duplicateChecker = new OtpDuplicateChecker();

    public OtpImportCommitter(Context context) { repository = OtpRepository.getInstance(context.getApplicationContext()); }

    public void commit(List<ImportedOtp> imported, ImportCommitter.ConflictStrategy strategy, ImportCommitter.Callback callback) {
        List<ImportedOtp> source = imported == null ? new ArrayList<>() : new ArrayList<>(imported);
        repository.getAll(existing -> {
            ImportCommitResult result = new ImportCommitResult();
            List<OtpToken> inserts = new ArrayList<>();
            List<OtpToken> updates = new ArrayList<>();
            for (ImportedOtp item : source) {
                try {
                    OtpToken duplicate = duplicateChecker.findDuplicate(item, existing);
                    if (duplicate != null && strategy == ImportCommitter.ConflictStrategy.SKIP) {
                        result.skipCount++;
                        continue;
                    }
                    OtpToken token = map(item);
                    if (duplicate != null && strategy == ImportCommitter.ConflictStrategy.OVERWRITE) {
                        token.id = duplicate.id;
                        updates.add(token);
                    } else {
                        inserts.add(token);
                    }
                } catch (Exception ignored) { result.failCount++; }
            }
            repository.importTokens(inserts, updates, (success, failed) -> {
                result.successCount = success;
                result.failCount += failed;
                if (callback != null) callback.onComplete(result);
            });
        });
    }

    private OtpToken map(ImportedOtp source) {
        OtpToken token = new OtpToken();
        long now = System.currentTimeMillis();
        token.issuer = trim(source.issuer);
        token.accountName = trim(source.account);
        token.secret = source.secret == null ? "" : source.secret;
        token.algorithm = trim(source.algorithm).isEmpty() ? "SHA1" : trim(source.algorithm);
        token.digits = source.digits > 0 ? source.digits : 6;
        token.period = source.period > 0 ? source.period : 30;
        token.createdAt = now;
        token.updatedAt = now;
        return token;
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
}
