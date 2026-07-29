package com.secureqr.scanner.security;

import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;

public final class OperationModeGuard {
    private OperationModeGuard() {}

    public static boolean requireEdit(Fragment fragment, Runnable action) {
        return requireEdit(fragment, action, null);
    }

    public static boolean requireEdit(Fragment fragment, Runnable action, Runnable onModeEnabled) {
        if (fragment == null || !fragment.isAdded()) return false;
        if (OperationModeManager.canModify(fragment.requireContext())) {
            action.run();
            return true;
        }
        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.operation_view_mode_title)
                .setMessage(R.string.operation_view_mode_message)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.operation_mode_settings, (dialog, which) -> {
                    if (fragment.getActivity() instanceof com.secureqr.scanner.MainActivity) {
                        ((com.secureqr.scanner.MainActivity) fragment.getActivity()).openAppearance();
                    }
                })
                .setPositiveButton(R.string.operation_enable_edit, (dialog, which) -> {
                    OperationModeManager.enterEdit();
                    feedback(fragment, onModeEnabled == null
                            ? R.string.operation_edit_enabled
                            : R.string.operation_edit_retry);
                    if (onModeEnabled != null) onModeEnabled.run();
                })
                .show();
        return false;
    }

    public static void feedback(Fragment fragment, int messageRes) {
        if (OperationModeManager.vibrationEnabled(fragment.requireContext())) {
            Vibrator vibrator = (Vibrator) fragment.requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(35, 80));
                else vibrator.vibrate(35);
            }
        }
        if (OperationModeManager.messageEnabled(fragment.requireContext())) {
            Toast.makeText(fragment.requireContext(), messageRes, Toast.LENGTH_SHORT).show();
        }
    }
}
