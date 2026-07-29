package com.secureqr.scanner.ui.security;

import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.LocalAutoBackupManager;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.utils.WebDavAutoSyncManager;

/** Offers an immediate fresh backup after changing vault credentials. */
final class SecurityChangeBackupPrompt {
    private SecurityChangeBackupPrompt() {}

    static void show(Fragment fragment, Runnable finish) {
        new androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.security_change_backup_title)
                .setMessage(R.string.security_change_backup_message)
                .setNegativeButton(R.string.security_change_backup_later, (dialog, which) -> finish.run())
                .setPositiveButton(R.string.security_change_backup_now, (dialog, which) -> {
                    WebDavAutoSyncManager.requestBackupNow(fragment.requireContext(), (successCount, targetCount, error) -> {
                        if (!fragment.isAdded()) return;
                        FragmentUi.run(fragment, () -> android.widget.Toast.makeText(
                                fragment.requireContext(),
                                error == null ? R.string.security_change_backup_started : R.string.security_change_backup_failed,
                                android.widget.Toast.LENGTH_LONG).show());
                    });
                    LocalAutoBackupManager.requestBackup(fragment.requireContext());
                    finish.run();
                })
                .setCancelable(false)
                .show();
    }
}
