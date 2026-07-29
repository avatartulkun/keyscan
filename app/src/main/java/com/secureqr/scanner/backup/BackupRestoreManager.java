package com.secureqr.scanner.backup;

import android.content.Context;
import com.secureqr.scanner.backup.source.BackupStreamSource;

import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.PasswordGenerationRepository;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.VaultAccessManager;

/** Restores v3/v5 text data only. Attachment bytes are deliberately excluded. */
public final class BackupRestoreManager {
    public interface Callback { void onComplete(BackupRestoreResult result); void onFailure(Exception error); }
    private final Context appContext;
    private final RecordRepository records;
    private final PasswordRepository passwords;
    private final OtpRepository otpTokens;
    private final PasswordGenerationRepository passwordGenerations;
    private final VaultRepository vault;

    public BackupRestoreManager(Context context) { appContext = context.getApplicationContext(); records = RecordRepository.getInstance(appContext); passwords = PasswordRepository.getInstance(appContext); otpTokens = OtpRepository.getInstance(appContext); passwordGenerations=PasswordGenerationRepository.getInstance(appContext); vault = new VaultRepository(appContext); }

    public void restore(BackupPayload payload, Callback callback) {
        restore(payload, null, null, callback);
    }

    public void restore(BackupPayload payload, BackupStreamSource backupSource, String dataProtectionKey, Callback callback) {
        if (payload == null) { callback.onFailure(new IllegalArgumentException("Backup payload is required")); return; }
        if (!VaultAccessManager.canAccessSensitiveData(appContext)) { callback.onFailure(new SecurityException("Vault access is required for secure restore")); return; }
        BackupRestoreResult result = new BackupRestoreResult(); result.attachmentCount = payload.attachments.size();
        records.mergeRecords(payload.records, () -> passwords.mergeGroups(payload.passwordGroups, () -> otpTokens.mergeTokens(payload.otpTokens, () -> passwords.mergeEntries(payload.passwords, () -> passwordGenerations.mergeRecords(payload.passwordGenerations, () -> restoreVaultItems(payload, backupSource, dataProtectionKey, 0, result, callback))))));
    }

    private void restoreVaultItems(BackupPayload payload, BackupStreamSource backupSource, String dataProtectionKey, int index, BackupRestoreResult result, Callback callback) {
        if (index >= payload.vaultItems.size()) { restoreAttachments(payload, backupSource, dataProtectionKey, result, callback); return; }
        try { vault.save(payload.vaultItems.get(index), () -> { result.vaultSuccess++; restoreVaultItems(payload, backupSource, dataProtectionKey, index + 1, result, callback); }); }
        catch (Exception error) { result.vaultFailed++; result.addError("Vault item restore failed"); restoreVaultItems(payload, backupSource, dataProtectionKey, index + 1, result, callback); }
    }

    private void restoreAttachments(BackupPayload payload, BackupStreamSource backupSource, String dataProtectionKey, BackupRestoreResult result, Callback callback) {
        result.passwordSuccess = payload.passwords.size(); result.otpSuccess = payload.otpTokens.size();
        if (payload.attachments.isEmpty()) { callback.onComplete(result); return; }
        if (backupSource == null || dataProtectionKey == null) { result.attachmentSkipped = payload.attachments.size(); callback.onComplete(result); return; }
        try { new AttachmentRestoreCoordinator(appContext).restore(backupSource::openStream, dataProtectionKey, payload, result); callback.onComplete(result); }
        catch (Exception error) { result.addError("Attachment restore failed"); result.attachmentFailed += Math.max(0, payload.attachments.size() - result.attachmentSuccess - result.attachmentSkipped); callback.onComplete(result); }
    }
}
