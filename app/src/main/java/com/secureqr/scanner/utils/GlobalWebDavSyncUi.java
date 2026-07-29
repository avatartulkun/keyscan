package com.secureqr.scanner.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.secureqr.scanner.R;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.VaultAccessManager;

public final class GlobalWebDavSyncUi {
    private static final String PREFS = "secureqr_settings";
    private GlobalWebDavSyncUi() {}

    public static void start(Fragment fragment) {
        if (fragment == null || !fragment.isAdded()) return;
        if (!VaultAccessManager.isUnlocked(fragment.requireActivity())) {
            VaultAccessManager.requireUnlocked(
                    fragment.requireActivity(),
                    fragment.getString(R.string.password_ledger_enter_title),
                    () -> startUnlocked(fragment)
            );
            return;
        }
        startUnlocked(fragment);
    }

    private static void startUnlocked(Fragment fragment) {
        if (fragment == null || !fragment.isAdded()) return;
        if (SecuritySettings.hasDataEncryptionKey(fragment.requireContext())) {
            runSync(fragment, null);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.global_sync_to_webdav)
                .setMessage(R.string.global_sync_key_required)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.go_to_settings, null)
                .create();
        dialog.show();
    }

    public static void bindState(Fragment fragment, ImageButton button) {
        SharedPreferences prefs = fragment.requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.OnSharedPreferenceChangeListener listener = (shared, key) -> {
            if (DataSyncState.KEY_DIRTY.equals(key) || "last_sync".equals(key)) updateCloudColor(button, shared);
        };
        updateCloudColor(button, prefs);
        prefs.registerOnSharedPreferenceChangeListener(listener);
        fragment.getViewLifecycleOwner().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onResume(LifecycleOwner owner) { updateCloudColor(button, prefs); }
            @Override public void onDestroy(LifecycleOwner owner) { prefs.unregisterOnSharedPreferenceChangeListener(listener); }
        });
    }

    private static void updateCloudColor(ImageButton button, SharedPreferences prefs) {
        boolean synced = prefs.getLong("last_sync", 0L) > 0L && !prefs.getBoolean(DataSyncState.KEY_DIRTY, true);
        button.setColorFilter(ContextCompat.getColor(button.getContext(), synced ? R.color.action_icon_tint : R.color.text_secondary));
        button.setContentDescription(button.getContext().getString(synced
                ? R.string.global_sync_synced_description : R.string.global_sync_pending_description));
    }

    private static void runSync(Fragment fragment, String ledgerPassword) {
        ProgressDialog progress = ProgressDialog.show(fragment.requireContext(),
                fragment.getString(R.string.global_sync_title), fragment.getString(R.string.global_sync_progress), true, false);
        WebDavAutoSyncManager.requestManualSync(fragment.requireContext(), ledgerPassword, (success, total, error) -> {
            FragmentUi.run(fragment, () -> {
                progress.dismiss();
                String message;
                if (error != null && !error.trim().isEmpty()) message = fragment.getString(R.string.global_sync_failed, error);
                else if (success == total && total > 0) message = fragment.getString(R.string.global_sync_completed);
                else message = fragment.getString(R.string.global_sync_partial, success, total - success);
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show();
            });
        });
    }
}
