package com.secureqr.scanner.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.secureqr.scanner.R;
import com.secureqr.scanner.security.AuthDebugLogger;

public final class BiometricUnlockHelper {
    public static final String KEY_BIOMETRIC_UNLOCK = "setting_biometric_unlock";

    private static final String PREFS = "secureqr_settings";
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
            | BiometricManager.Authenticators.BIOMETRIC_WEAK;

    private BiometricUnlockHelper() {
    }

    public static boolean isAvailable(Context context) {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static boolean isEnabled(Context context) {
        return PinLockHelper.isConfigured(context)
                && isAvailable(context)
                && prefs(context).getBoolean(KEY_BIOMETRIC_UNLOCK, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_UNLOCK, enabled).apply();
    }

    public static void prompt(FragmentActivity activity, Runnable onSuccess, Runnable onCancelOrError) {
        AuthDebugLogger.logAuthTrigger(activity, "BiometricUnlockHelper.prompt", "biometric_prompt");
        BiometricPrompt prompt = new BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        if (onSuccess != null) onSuccess.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        if (onCancelOrError != null) onCancelOrError.run();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Keep the dialog open so the user can retry or fall back to the master password.
                    }
                }
        );
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_unlock_title))
                .setSubtitle(activity.getString(R.string.biometric_unlock_subtitle))
                .setNegativeButtonText(activity.getString(R.string.cancel))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();
        prompt.authenticate(promptInfo);
    }

    public static void promptStrict(FragmentActivity activity, String title, String subtitle, Runnable onSuccess, Runnable onCancelOrError) {
        AuthDebugLogger.logAuthTrigger(activity, "BiometricUnlockHelper.promptStrict", title);
        BiometricPrompt prompt = new BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        if (onSuccess != null) onSuccess.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        if (onCancelOrError != null) onCancelOrError.run();
                    }
                }
        );
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title == null || title.trim().isEmpty() ? activity.getString(R.string.biometric_unlock_title) : title)
                .setSubtitle(subtitle == null ? "" : subtitle)
                .setNegativeButtonText(activity.getString(R.string.cancel))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();
        prompt.authenticate(promptInfo);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
