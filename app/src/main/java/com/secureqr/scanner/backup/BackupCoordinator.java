package com.secureqr.scanner.backup;

import android.content.Context;

import com.secureqr.scanner.data.model.VaultAttachment;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.PasswordGenerationRepository;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.VaultAccessManager;

import java.util.ArrayList;
import java.util.List;

/** Collects a portable backup snapshot through repositories only. */
public final class BackupCoordinator {
    public interface Callback {
        void onSuccess(BackupPayload payload);
        void onFailure(Exception error);
    }

    private final Context appContext;
    private final RecordRepository records;
    private final PasswordRepository passwords;
    private final OtpRepository otpTokens;
    private final PasswordGenerationRepository passwordGenerations;
    private final VaultRepository vault;

    public BackupCoordinator(Context context) {
        appContext = context.getApplicationContext();
        records = RecordRepository.getInstance(appContext);
        passwords = PasswordRepository.getInstance(appContext);
        otpTokens = OtpRepository.getInstance(appContext);
        passwordGenerations = PasswordGenerationRepository.getInstance(appContext);
        vault = new VaultRepository(appContext);
    }

    public void createPayload(Callback callback) {
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) {
            callback.onFailure(new SecurityException("Vault access is required for secure backup"));
            return;
        }
        records.getSyncRecords(recordList -> passwords.getGroups(groups -> passwords.getAll(passwordList ->
                otpTokens.getAll(otpList -> passwordGenerations.getAll(generationList -> vault.getAllNow(vaultItems -> vault.getAllAttachments(attachments -> {
                    List<BackupAttachment> portableAttachments = new ArrayList<>();
                    for (VaultAttachment attachment : attachments) {
                        portableAttachments.add(BackupAttachment.from(attachment));
                    }
                    callback.onSuccess(new BackupPayload(recordList, groups, passwordList, otpList, generationList,
                            vaultItems, portableAttachments));
                })))))));
    }
}
