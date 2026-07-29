package com.secureqr.scanner.security;

import androidx.fragment.app.FragmentActivity;

import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.BiometricUnlockHelper;

/** Requires biometric verification followed by the PIN before data can leave the app. */
public final class ExportSecurityGuard {
    private ExportSecurityGuard() { }

    public static void require(FragmentActivity activity, String reason, Runnable onVerified) {
        if (activity == null || onVerified == null) return;
        Runnable requirePin = () -> VaultAccessManager.requirePinAuthentication(activity, reason, onVerified);
        if (BiometricUnlockHelper.isEnabled(activity)) {
            BiometricUnlockHelper.promptStrict(activity, reason,
                    activity.getString(R.string.export_auth_biometric_then_pin), requirePin, () -> { });
        } else {
            // PIN remains mandatory on devices without enabled/available biometrics.
            requirePin.run();
        }
    }
}
