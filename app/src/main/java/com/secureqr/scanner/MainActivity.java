package com.secureqr.scanner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.ui.generate.GenerateFragment;
import com.secureqr.scanner.ui.history.HistoryFragment;
import com.secureqr.scanner.ui.home.HomeFragment;
import com.secureqr.scanner.ui.home.PrimaryNavigationFragment;
import com.secureqr.scanner.ui.notes.PasswordNoteFragment;
import com.secureqr.scanner.ui.vault.VaultFragment;
import com.secureqr.scanner.ui.password.PasswordForgeFragment;
import com.secureqr.scanner.ui.password.RandomPasswordGeneratorFragment;
import com.secureqr.scanner.ui.otp.OtpAuthFragment;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.ui.scanner.SmartScanFragment;
import com.secureqr.scanner.ui.settings.AppearanceFragment;
import com.secureqr.scanner.ui.settings.AboutFragment;
import com.secureqr.scanner.ui.settings.ExportFragment;
import com.secureqr.scanner.ui.settings.SettingsFragment;
import com.secureqr.scanner.ui.settings.TrashFragment;
import com.secureqr.scanner.ui.exporter.ExportDataFragment;
import com.secureqr.scanner.ui.importer.ImportDataFragment;
import com.secureqr.scanner.clipboard.ClipboardImportActivity;
import com.secureqr.scanner.clipboard.ClipboardImportNotifier;
import com.secureqr.scanner.clipboard.ClipboardImportSession;
import com.secureqr.scanner.clipboard.ClipboardImportSettings;
import com.secureqr.scanner.clipboard.ClipboardSensitiveClassifier;
import com.secureqr.scanner.security.AuthDebugLogger;
import com.secureqr.scanner.security.ConfigurationRebuildGuard;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.DatabaseOpenState;
import com.secureqr.scanner.security.RecentAuthSession;
import com.secureqr.scanner.security.OperationModeManager;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.security.VaultSession;
import com.secureqr.scanner.ui.security.SecurityDatabaseErrorFragment;
import com.secureqr.scanner.ui.security.SecurityCenterFragment;
import com.secureqr.scanner.ui.security.SecurityVaultSetupFragment;
import com.secureqr.scanner.ui.quickaccess.QuickAccessFloatingView;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.PinLockHelper;
import com.secureqr.scanner.utils.LocaleHelper;
import com.secureqr.scanner.utils.WebDavAutoSyncManager;

public class MainActivity extends AppCompatActivity implements HomeFragment.HomeActions {
    private static final String PREFS = "secureqr_settings";
    public static final String KEY_DEFAULT_PAGE = "setting_default_page";
    public static final String DEFAULT_PAGE_HOME = "home";
    public static final String DEFAULT_PAGE_VAULT = "vault";
    public static final String DEFAULT_PAGE_PASSWORD_LEDGER = "password_ledger";
    public static final String DEFAULT_PAGE_OTP = "otp";
    public static final String KEY_OPEN_APPEARANCE_ON_RECREATE = "open_appearance_on_recreate";
    public static final String KEY_QUICK_ACCESS_ENABLED = "setting_quick_access_enabled";
    public static final String KEY_QUICK_ACCESS_ICON_STYLE = "setting_quick_access_icon_style";
    private boolean clipboardCheckedThisForeground;
    private boolean restoredFromSavedState;
    private boolean pendingNewPasswordRecord;
    private QuickAccessFloatingView quickAccessView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.apply(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedThemeMode();
        super.onCreate(savedInstanceState);
        if (!PrivacyConsentActivity.hasAcceptedCurrentPolicy(this)) {
            startActivity(PrivacyConsentActivity.createConsentIntent(this));
            finish();
            return;
        }
        restoredFromSavedState = savedInstanceState != null;
        if (restoredFromSavedState || ConfigurationRebuildGuard.isInProgress()) {
            clipboardCheckedThisForeground = true;
        }
        AuthDebugLogger.logActivityState(this, "MainActivity.onCreate", savedInstanceState);
        setContentView(R.layout.activity_main);
        setupOperationLockOverlay();
        setupQuickAccess();

        if (savedInstanceState == null) {
            openInitialPage();
        }
        handleClipboardImportIntent(getIntent());
    }

    private void setupOperationLockOverlay() {
        findViewById(R.id.operation_lock_overlay).setOnClickListener(v ->
                VaultAccessManager.requireAuthentication(this,
                        getString(R.string.operation_unlock_prompt), () -> {
                            OperationModeManager.unlock(this);
                            refreshOperationLockOverlay();
                        }));
        refreshOperationLockOverlay();
    }

    public void lockApplicationNow() {
        OperationModeManager.lock();
        refreshOperationLockOverlay();
    }

    public void refreshOperationLockOverlay() {
        android.view.View overlay = findViewById(R.id.operation_lock_overlay);
        if (overlay == null) return;
        boolean locked = OperationModeManager.isLocked(this);
        overlay.setVisibility(locked ? android.view.View.VISIBLE : android.view.View.GONE);
        if (quickAccessView != null) quickAccessView.setAlpha(locked ? 0.35f : 1f);
    }

    private void setupQuickAccess() {
        FrameLayout host = findViewById(R.id.quick_access_host);
        quickAccessView = new QuickAccessFloatingView(this, this::openQuickAccessDestination);
        host.addView(quickAccessView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentResumed(@NonNull androidx.fragment.app.FragmentManager fragmentManager,
                                                  @NonNull Fragment fragment) {
                        refreshQuickAccessVisibility(fragment);
                    }
                }, false);
        quickAccessView.setShortcutVisible(false);
    }

    public void refreshQuickAccessVisibility() {
        applyQuickAccessVisibility();
    }

    public void refreshQuickAccessStyle() {
        if (quickAccessView != null) {
            quickAccessView.refreshOrbStyle();
        }
    }

    private void refreshQuickAccessVisibility(Fragment fragment) {
        applyQuickAccessVisibility();
    }

    private void applyQuickAccessVisibility() {
        if (quickAccessView == null) return;
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_QUICK_ACCESS_ENABLED, true);
        // 首次安全初始化期间只显示设置流程，避免悬浮快捷窗遮挡 PIN / 数据保护密钥输入。
        boolean vaultReady = SecuritySettings.isVaultInitialized(this);
        quickAccessView.setShortcutVisible(enabled && vaultReady);
    }

    private void openQuickAccessDestination(@NonNull String destination) {
        switch (destination) {
            case QuickAccessFloatingView.DEST_SCAN:
                openScanner();
                break;
            case QuickAccessFloatingView.DEST_SHARE:
                openGenerator();
                break;
            case QuickAccessFloatingView.DEST_GENERATOR:
                openRandomPasswordGenerator();
                break;
            case QuickAccessFloatingView.DEST_VAULT:
                openPasswordNotes();
                break;
            case QuickAccessFloatingView.DEST_PASSWORDS:
                openPasswordForge();
                break;
            case QuickAccessFloatingView.DEST_OTP:
                openOtpAuth();
                break;
            case QuickAccessFloatingView.DEST_SETTINGS:
                openAppearance();
                break;
            case QuickAccessFloatingView.DEST_SECURITY:
                openSecurityCenter();
                break;
            case QuickAccessFloatingView.DEST_BACKUP:
                openWebDav();
                break;
            case QuickAccessFloatingView.DEST_EXPORT:
                openGenericExport();
                break;
            case QuickAccessFloatingView.DEST_TRASH:
                openTrash();
                break;
            case QuickAccessFloatingView.DEST_HISTORY:
                openHistory();
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (quickAccessView != null && quickAccessView.isExpanded()) {
            quickAccessView.collapse();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AuthDebugLogger.logActivityState(this, "MainActivity.onResume", null);
        applySensitiveWindowPolicy();
        if (quickAccessView != null) {
            quickAccessView.post(this::refreshQuickAccessVisibility);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && quickAccessView != null) {
            quickAccessView.post(this::refreshQuickAccessVisibility);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleClipboardImportIntent(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        AuthDebugLogger.logActivityState(this, "MainActivity.onStop", null);
        if (isChangingConfigurations() || ConfigurationRebuildGuard.isInProgress()) {
            return;
        }
        RecentAuthSession.clear();
        ClipboardImportSession.clearPending();
        clipboardCheckedThisForeground = false;
    }

    private void showFragment(Fragment fragment) {
        showFragment(fragment, !(fragment instanceof HomeFragment));
    }

    private void showFragment(Fragment fragment, boolean addToBackStack) {
        boolean isHome = fragment instanceof HomeFragment || fragment instanceof PrimaryNavigationFragment;
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment);
        if (addToBackStack && !isHome) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
        if (quickAccessView != null) {
            quickAccessView.post(this::refreshQuickAccessVisibility);
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void openInitialPage() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (ConfigurationRebuildGuard.isInProgress() || prefs.getBoolean(KEY_OPEN_APPEARANCE_ON_RECREATE, false)) {
            showFragment(new AppearanceFragment(), false);
            return;
        }
        if (isDatabaseOpenErrorState()) {
            showFragment(new SecurityDatabaseErrorFragment(), false);
            return;
        }
        if (!SecuritySettings.isVaultInitialized(this)) {
            showFragment(SecurityVaultSetupFragment.newInstance(SecurityVaultSetupFragment.DEST_HOME), false);
            return;
        }
        String page = normalizeDefaultPage(prefs.getString(KEY_DEFAULT_PAGE, DEFAULT_PAGE_HOME));
        if (DEFAULT_PAGE_VAULT.equals(page)) {
            showFragment(new PrimaryNavigationFragment(), false);
            openPasswordNotes();
        } else if (DEFAULT_PAGE_PASSWORD_LEDGER.equals(page)) {
            showFragment(new PrimaryNavigationFragment(), false);
            openPasswordForge(false);
        } else if (DEFAULT_PAGE_OTP.equals(page)) {
            openOtpAuth(false);
        } else {
            showFragment(new PrimaryNavigationFragment(), false);
        }
    }

    private String normalizeDefaultPage(String value) {
        if (value == null || value.isEmpty()) return DEFAULT_PAGE_HOME;
        if (value.equals(DEFAULT_PAGE_HOME)
                || value.equals(DEFAULT_PAGE_VAULT)
                || value.equals(DEFAULT_PAGE_PASSWORD_LEDGER)
                || value.equals(DEFAULT_PAGE_OTP)) {
            return value;
        }
        if (value.equals(getString(R.string.option_password_ledger)) || "Password Ledger".equals(value)) {
            return DEFAULT_PAGE_PASSWORD_LEDGER;
        }
        if (value.equals(getString(R.string.option_otp_authenticator)) || "TOTP认证器".equals(value) || "OTP认证器".equals(value)) {
            return DEFAULT_PAGE_OTP;
        }
        return DEFAULT_PAGE_HOME;
    }

    @Override
    @OptIn(markerClass = ExperimentalGetImage.class)
    public void openScanner() {
        showFragment(new SmartScanFragment());
    }

    private boolean isDatabaseOpenErrorState() {
        DatabaseOpenState state = DatabaseKeyManager.databaseOpenState(this);
        return state == DatabaseOpenState.DATABASE_KEY_ERROR
                || state == DatabaseOpenState.DATABASE_CORRUPTED
                || state == DatabaseOpenState.DATABASE_MIGRATION_ERROR
                || state == DatabaseOpenState.DATABASE_ACCESS_ERROR;
    }

    @Override
    public void openGenerator() {
        showFragment(new GenerateFragment());
    }

    @Override
    public void openHistory() {
        showFragment(new HistoryFragment());
    }

    @Override
    public void openWebDav() {
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_WEBDAV, true)) return;
        showFragment(new SettingsFragment());
    }

    @Override
    public void openExport() {
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_EXPORT, true)) return;
        showFragment(new ExportFragment());
    }

    @Override
    public void openGenericExport() {
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_GENERIC_EXPORT, true)) return;
        showFragment(new ExportDataFragment());
    }

    @Override
    public void openPasswordBookImport() {
        showFragment(new ImportDataFragment());
    }

    @Override
    public void openTrash() {
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_VAULT, true)) return;
        VaultAccessManager.requireUnlocked(this, getString(R.string.vault_unlock_prompt),
                () -> showFragment(new TrashFragment()));
    }

    @Override
    public void openNewSecureItem() {
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_VAULT, true)) return;
        VaultAccessManager.requireUnlocked(this, getString(R.string.vault_unlock_prompt),
                () -> showFragment(VaultFragment.createNew()));
    }

    @Override
    public void openPasswordNotes() {
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_VAULT, true)) return;
        VaultAccessManager.requireUnlocked(this, getString(R.string.vault_unlock_prompt),
                () -> showFragment(new VaultFragment()));
    }

    @Override
    public void openAppearance() {
        showFragment(new AppearanceFragment());
    }

    @Override
    public void openSecurityCenter() {
        showFragment(new SecurityCenterFragment());
    }

    @Override
    public void openAbout() {
        showFragment(new AboutFragment());
    }

    @Override
    public void openPasswordForge() {
        openPasswordForge(true, false);
    }

    @Override
    public void openNewPasswordRecord() {
        openPasswordForge(true, true);
    }

    private void openPasswordForge(boolean addToBackStack) {
        openPasswordForge(addToBackStack, false);
    }

    private void openPasswordForge(boolean addToBackStack, boolean openNewRecord) {
        pendingNewPasswordRecord = openNewRecord;
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_PASSWORD_LEDGER, addToBackStack)) return;
        if (RecentAuthSession.isAuthenticated()) {
            showFragment(openNewRecord ? PasswordForgeFragment.newRecord() : new PasswordForgeFragment(), addToBackStack);
            pendingNewPasswordRecord = false;
            return;
        }
        if (PinLockHelper.isConfigured(this)) {
            showPasswordVerifyDialog(addToBackStack);
        } else {
            showPasswordSetupDialog(true, addToBackStack);
        }
    }

    @Override
    public void openRandomPasswordGenerator() {
        showFragment(new RandomPasswordGeneratorFragment());
    }

    @Override
    @OptIn(markerClass = ExperimentalGetImage.class)
    public void openOtpAuth() {
        openOtpAuth(true);
    }

    @Override
    public void openOtpManualImport() {
        openOtpAuth(true, OtpAuthFragment.manualImport());
    }

    @Override
    public void openOtpBatchImport() {
        openOtpAuth(true, OtpAuthFragment.batchImport());
    }

    private void openOtpAuth(boolean addToBackStack) {
        openOtpAuth(addToBackStack, new OtpAuthFragment());
    }

    private void openOtpAuth(boolean addToBackStack, OtpAuthFragment fragment) {
        if (!ensureSecureVault(SecurityVaultSetupFragment.DEST_OTP, addToBackStack)) return;
        VaultAccessManager.requireUnlocked(this, getString(R.string.otp_unlock_prompt),
                () -> showFragment(fragment, addToBackStack));
    }

    private boolean ensureSecureVault(String destination, boolean addToBackStack) {
        if (SecuritySettings.isVaultInitialized(this)) return true;
        showFragment(SecurityVaultSetupFragment.newInstance(destination), addToBackStack);
        return false;
    }

    private void showPasswordSetupDialog(boolean enterAfterSave) {
        showPasswordSetupDialog(enterAfterSave, true);
    }

    public void openPasswordLedgerSetupFromSettings() {
        showPasswordSetupDialog(false, false);
    }

    private void showPasswordSetupDialog(boolean enterAfterSave, boolean addToBackStack) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);

        EditText passwordInput = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        EditText hintInput = createPlainInput(getString(R.string.password_input_hint));
        Spinner questionSpinner = new Spinner(this);
        questionSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PinLockHelper.securityQuestions(this)));
        EditText answerInput = createPlainInput(getString(R.string.password_ledger_answer_hint));

        content.addView(createPasswordInputRow(passwordInput));
        content.addView(hintInput);
        content.addView(questionSpinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        content.addView(answerInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.password_ledger_setup_title)
                .setMessage(R.string.password_ledger_setup_message)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            String answer = answerInput.getText().toString().trim();
            if (!PinLockHelper.isValidPin(password)) {
                passwordInput.setError(getString(R.string.password_ledger_input_error));
                return;
            }
            if (answer.isEmpty()) {
                answerInput.setError(getString(R.string.password_ledger_security_answer_empty));
                return;
            }
            PinLockHelper.saveCredentials(this, password, hintInput.getText().toString(), questionSpinner.getSelectedItem().toString(), answer);
            dialog.dismiss();
            RecentAuthSession.markAuthenticated();
            VaultSession.unlock(this);
            Toast.makeText(this, R.string.password_ledger_save_success, Toast.LENGTH_SHORT).show();
            if (enterAfterSave) {
                showFragment(pendingNewPasswordRecord ? PasswordForgeFragment.newRecord() : new PasswordForgeFragment(), addToBackStack);
                pendingNewPasswordRecord = false;
            }
        }));
        dialog.show();
    }

    private void showPasswordVerifyDialog() {
        showPasswordVerifyDialog(true);
    }

    private void showPasswordVerifyDialog(boolean addToBackStack) {
        // Biometric is the default path. PIN is shown only when the user cancels,
        // declines, or biometric authentication is unavailable.
        if (BiometricUnlockHelper.isEnabled(this)) {
            BiometricUnlockHelper.prompt(this,
                    () -> unlockPasswordLedger(null, addToBackStack),
                    () -> showPasswordVerifyDialogWithPin(addToBackStack));
            return;
        }
        showPasswordVerifyDialogWithPin(addToBackStack);
    }

    private void showPasswordVerifyDialogWithPin(boolean addToBackStack) {
        long remaining = PinLockHelper.remainingLockMs(this);
        if (remaining > 0) {
            Toast.makeText(this, getString(R.string.password_ledger_unlock_error) + ": " + secondsText(remaining), Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText input = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        TextView hint = new TextView(this);
        hint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        hint.setTextSize(13);
        String hintText = PinLockHelper.passwordHint(this);
        hint.setText(hintText.isEmpty() ? "" : getString(R.string.password_ledger_access_label) + ": " + hintText);
        TextView forgot = new TextView(this);
        forgot.setText(R.string.password_ledger_forgot_password);
        forgot.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        forgot.setGravity(Gravity.CENTER);
        forgot.setPadding(0, dp(12), 0, 0);
        forgot.setTextSize(14);
        Button biometricButton = null;
        content.addView(createPasswordInputRow(input));
        if (!hintText.isEmpty()) {
            content.addView(hint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (BiometricUnlockHelper.isEnabled(this)) {
            biometricButton = new Button(this);
            biometricButton.setText(R.string.biometric_unlock_button);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
            params.topMargin = dp(10);
            content.addView(biometricButton, params);
        }
        content.addView(forgot, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.password_ledger_enter_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        Button finalBiometricButton = biometricButton;
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long lockRemaining = PinLockHelper.remainingLockMs(this);
                if (lockRemaining > 0) {
                    Toast.makeText(this, getString(R.string.password_ledger_unlock_error) + ": " + secondsText(lockRemaining), Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    return;
                }
                String password = input.getText().toString().trim();
                if (!PinLockHelper.verifyPin(this, password)) {
                    PinLockHelper.recordFailedAttempt(this);
                    long nowLocked = PinLockHelper.remainingLockMs(this);
                    if (nowLocked > 0) {
                        Toast.makeText(this, R.string.password_ledger_unlock_error, Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    } else {
                        int remainingAttempts = Math.max(0, 5 - PinLockHelper.failedCount(this));
                    input.setError(getString(R.string.password_ledger_unlock_error));
                        Toast.makeText(this, getString(R.string.password_ledger_retry_attempts, remainingAttempts), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                PinLockHelper.clearFailedAttempts(this);
                unlockPasswordLedger(dialog, addToBackStack);
            });
            if (finalBiometricButton != null) {
                finalBiometricButton.setOnClickListener(v -> BiometricUnlockHelper.prompt(this,
                        () -> unlockPasswordLedger(dialog, addToBackStack),
                        null));
            }
            forgot.setOnClickListener(v -> {
                dialog.dismiss();
                showForgotPasswordDialog(addToBackStack);
            });
        });
        dialog.show();
    }

    private void unlockPasswordLedger(AlertDialog dialog, boolean addToBackStack) {
        PinLockHelper.clearFailedAttempts(this);
        RecentAuthSession.markAuthenticated();
        VaultSession.unlock(this);
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        showFragment(pendingNewPasswordRecord ? PasswordForgeFragment.newRecord() : new PasswordForgeFragment(), addToBackStack);
        pendingNewPasswordRecord = false;
    }

    private void showForgotPasswordDialog() {
        showForgotPasswordDialog(true);
    }

    private void showForgotPasswordDialog(boolean addToBackStack) {
        EditText answerInput = createPlainInput(getString(R.string.password_ledger_answer_hint));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.password_ledger_forgot_password)
                .setMessage(PinLockHelper.securityQuestion(this))
                .setView(answerInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!PinLockHelper.verifySecurityAnswer(this, answerInput.getText().toString())) {
                answerInput.setError(getString(R.string.password_ledger_unlock_error));
                Toast.makeText(this, R.string.password_ledger_unlock_error, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showResetPasswordDialog(addToBackStack);
        }));
        dialog.show();
    }

    private void showResetPasswordDialog() {
        showResetPasswordDialog(true);
    }

    private void showResetPasswordDialog(boolean addToBackStack) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText passwordInput = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        EditText hintInput = createPlainInput(getString(R.string.password_input_hint));
        hintInput.setText(PinLockHelper.passwordHint(this));
        content.addView(createPasswordInputRow(passwordInput));
        content.addView(hintInput);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.password_ledger_reset_title)
                .setMessage(R.string.password_ledger_reset_message)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            if (!PinLockHelper.isValidPin(password)) {
                passwordInput.setError(getString(R.string.password_ledger_input_error));
                return;
            }
            PinLockHelper.savePasswordAndHint(this, password, hintInput.getText().toString());
            PinLockHelper.clearFailedAttempts(this);
            dialog.dismiss();
            RecentAuthSession.markAuthenticated();
            VaultSession.unlock(this);
            Toast.makeText(this, R.string.password_ledger_reset_success, Toast.LENGTH_SHORT).show();
            showFragment(pendingNewPasswordRecord ? PasswordForgeFragment.newRecord() : new PasswordForgeFragment(), addToBackStack);
            pendingNewPasswordRecord = false;
        }));
        dialog.show();
    }

    private void handleClipboardImportIntent(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra(ClipboardImportActivity.EXTRA_OPEN_CLIPBOARD_IMPORT, false)) {
            openPasswordForge(true);
            return;
        }
        if (intent.getBooleanExtra(ClipboardImportNotifier.EXTRA_FORCE_CLIPBOARD_CHECK, false)
                || ClipboardImportNotifier.ACTION_IMPORT_CLIPBOARD.equals(intent.getAction())) {
            clipboardCheckedThisForeground = true;
            if (!PinLockHelper.isConfigured(this)) {
                Toast.makeText(this, R.string.password_ledger_setup_required, Toast.LENGTH_SHORT).show();
                return;
            }
            SensitiveActionGuard.requireRecentAuth(this, getString(R.string.clipboard_import_auth_reason), () -> readClipboardForSmartImport(true));
        }
    }

    private void maybeCheckClipboardSmartImport() {
        if (clipboardCheckedThisForeground) return;
        if (restoredFromSavedState || ConfigurationRebuildGuard.isInProgress() || isChangingConfigurations()) {
            clipboardCheckedThisForeground = true;
            return;
        }
        clipboardCheckedThisForeground = true;
        if (!ClipboardImportSettings.isSmartImportEnabled(this)) return;
        if (!PinLockHelper.isConfigured(this)) return;
        if (!RecentAuthSession.isAuthenticated()) {
            SensitiveActionGuard.requireRecentAuth(this, getString(R.string.clipboard_check_auth_reason), () -> readClipboardForSmartImport(false));
            return;
        }
        readClipboardForSmartImport(false);
    }

    private void readClipboardForSmartImport(boolean userRequested) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) {
            if (userRequested) Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                if (userRequested) Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            CharSequence value = clip.getItemAt(0).coerceToText(this);
            String text = value == null ? "" : value.toString();
            if (text.trim().isEmpty() || text.length() > 64 * 1024) {
                if (userRequested) Toast.makeText(this, R.string.clipboard_no_sensitive_content, Toast.LENGTH_SHORT).show();
                return;
            }
            if (ClipboardImportSession.isInternalCopy(text)) {
                if (userRequested) Toast.makeText(this, R.string.clipboard_own_content_skipped, Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardSensitiveClassifier.Result result = ClipboardSensitiveClassifier.classify(this, text);
            if (result.sensitive && ClipboardImportSession.begin(text, result, false)) {
                openPasswordForge(true);
            } else if (userRequested) {
                Toast.makeText(this, R.string.clipboard_no_sensitive_content, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            if (userRequested) Toast.makeText(this, R.string.clipboard_read_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private EditText createPasswordInput(String hint) {
        EditText input = createPlainInput(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return input;
    }

    private android.widget.FrameLayout createPasswordInputRow(EditText input) {
        android.widget.FrameLayout row = new android.widget.FrameLayout(this);
        ImageButton eye = new ImageButton(this);
        eye.setBackground(null);
        eye.setElevation(0f);
        eye.setStateListAnimator(null);
        eye.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        eye.setImageResource(R.drawable.ic_visibility_off_24);
        eye.setColorFilter(getResources().getColor(R.color.action_icon_tint));
        eye.setContentDescription(getString(R.string.credential_show_password_desc));
        final boolean[] visible = {false};
        Runnable update = () -> {
            input.setInputType(InputType.TYPE_CLASS_TEXT | (visible[0]
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            input.setSelection(input.getText().length());
            eye.setImageResource(visible[0] ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24);
            eye.setContentDescription(getString(visible[0]
                    ? R.string.credential_hide_password_desc
                    : R.string.credential_show_password_desc));
        };
        update.run();
        eye.setOnClickListener(v -> {
            visible[0] = !visible[0];
            update.run();
        });
        input.setPadding(input.getPaddingLeft(), input.getPaddingTop(), dp(52), input.getPaddingBottom());
        row.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        android.widget.FrameLayout.LayoutParams eyeParams = new android.widget.FrameLayout.LayoutParams(dp(48), dp(52), Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(eye, eyeParams);
        return row;
    }

    private EditText createPlainInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setMinHeight(dp(52));
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF80868B);
        int horizontalPadding = dp(12);
        input.setPadding(horizontalPadding, dp(8), horizontalPadding, dp(8));
        return input;
    }

    private String secondsText(long millis) {
        return Math.max(1, (int) Math.ceil(millis / 1000.0)) + getString(R.string.seconds_unit);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applySavedThemeMode() {
        String mode = getSharedPreferences("secureqr_settings", MODE_PRIVATE)
                .getString("theme_mode", "auto");
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(mode));
    }

    private void applySensitiveWindowPolicy() {
        // Screen capture and recording are intentionally allowed on every page.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    public static int toDelegateMode(String mode) {
        if ("light".equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        if ("dark".equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}


