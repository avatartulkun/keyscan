package com.secureqr.scanner.security;

import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.PinLockHelper;

public final class VaultAccessManager {
    private VaultAccessManager() {
    }

    public static boolean isUnlocked(FragmentActivity activity) {
        return activity != null && VaultSession.isUnlocked(activity);
    }

    public static boolean canAccessSensitiveData(Context context) {
        if (context == null || !VaultSession.isUnlocked(context)) return false;
        VaultLockManager.onSensitiveAccess(context);
        return true;
    }

    public static void requireUnlocked(FragmentActivity activity, String reason, Runnable onUnlocked) {
        if (activity == null || onUnlocked == null) return;
        if (VaultSession.isUnlocked(activity)) {
            VaultLockManager.onSensitiveAccess(activity);
            onUnlocked.run();
            return;
        }
        AuthDebugLogger.logAuthTrigger(activity, "VaultAccessManager.requireUnlocked", reason);
        Runnable fallback = () -> showPinDialog(activity, reason, onUnlocked);
        if (BiometricUnlockHelper.isEnabled(activity)) {
            BiometricUnlockHelper.prompt(activity, () -> unlockAndRun(activity, onUnlocked), fallback);
        } else {
            fallback.run();
        }
    }
    public static void requireAuthentication(FragmentActivity activity,String reason,Runnable onSuccess){if(activity==null||onSuccess==null)return;Runnable fallback=()->showPinDialog(activity,reason,onSuccess);if(BiometricUnlockHelper.isEnabled(activity))BiometricUnlockHelper.prompt(activity,()->unlockAndRun(activity,onSuccess),fallback);else fallback.run();}

    /**
     * Requires the user's PIN specifically. This deliberately does not offer
     * biometric fallback because it protects changes to biometric settings.
     */
    public static void requirePinAuthentication(FragmentActivity activity, String reason, Runnable onSuccess) {
        if (activity == null || onSuccess == null) return;
        showPinDialog(activity, reason, onSuccess, false);
    }

    private static void unlockAndRun(FragmentActivity activity, Runnable onUnlocked) {
        VaultSession.unlock(activity);
        RecentAuthSession.markAuthenticated();
        onUnlocked.run();
    }

    private static void showPinDialog(FragmentActivity activity, String reason, Runnable onUnlocked) {
        showPinDialog(activity, reason, onUnlocked, true);
    }

    private static void showPinDialog(FragmentActivity activity, String reason, Runnable onUnlocked, boolean unlockSession) {
        if (!PinLockHelper.isConfigured(activity)) {
            Toast.makeText(activity, R.string.password_ledger_setup_required, Toast.LENGTH_SHORT).show();
            return;
        }
        long remaining = PinLockHelper.remainingLockMs(activity);
        if (remaining > 0) {
            Toast.makeText(activity, activity.getString(R.string.pin_locked_retry, Math.max(1, remaining / 1000)), Toast.LENGTH_SHORT).show();
            return;
        }
        boolean wasSecure = SensitiveWindowGuard.enable(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
        EditText input = new EditText(activity);
        input.setHint(R.string.password_ledger_unlock_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        input.setBackgroundResource(R.drawable.bg_edit_text);
        content.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 48)));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(reason == null || reason.trim().isEmpty() ? activity.getString(R.string.authentication_required) : reason)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnDismissListener(d -> SensitiveWindowGuard.restore(activity, wasSecure));
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long lockRemaining = PinLockHelper.remainingLockMs(activity);
            if (lockRemaining > 0) {
                input.setError(activity.getString(R.string.retry_in_seconds, Math.max(1, lockRemaining / 1000)));
                return;
            }
            if (!PinLockHelper.verifyPin(activity, input.getText().toString())) {
                PinLockHelper.recordFailedAttempt(activity);
                input.setError(activity.getString(R.string.password_ledger_unlock_error));
                return;
            }
            PinLockHelper.clearFailedAttempts(activity);
            dialog.dismiss();
            if (unlockSession) {
                unlockAndRun(activity, onUnlocked);
            } else {
                onUnlocked.run();
            }
        }));
        dialog.show();
    }

    private static int dp(FragmentActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
