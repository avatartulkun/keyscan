package com.secureqr.scanner;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.ExperimentalGetImage;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.ui.generate.GenerateFragment;
import com.secureqr.scanner.ui.history.HistoryFragment;
import com.secureqr.scanner.ui.home.HomeFragment;
import com.secureqr.scanner.ui.password.PasswordForgeFragment;
import com.secureqr.scanner.ui.password.RandomPasswordGeneratorFragment;
import com.secureqr.scanner.ui.otp.OtpAuthFragment;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.ui.settings.AppearanceFragment;
import com.secureqr.scanner.ui.settings.ExportFragment;
import com.secureqr.scanner.ui.settings.SettingsFragment;
import com.secureqr.scanner.utils.PinLockHelper;
import com.secureqr.scanner.utils.LocaleHelper;

public class MainActivity extends AppCompatActivity implements HomeFragment.HomeActions {
    private static final String PREFS = "secureqr_settings";
    public static final String KEY_DEFAULT_PAGE = "setting_default_page";
    public static final String DEFAULT_PAGE_HOME = "home";
    public static final String DEFAULT_PAGE_SCAN = "scanner";
    public static final String DEFAULT_PAGE_PASSWORD_LEDGER = "password_ledger";
    public static final String DEFAULT_PAGE_OTP = "otp";
    private static final String KEY_LAST_BACKUP_REMINDER = "last_data_insurance_backup_reminder";
    private static final long BACKUP_REMINDER_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.apply(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedThemeMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            openInitialPage();
        }
        maybeShowBackupReminder();
    }

    private void showFragment(Fragment fragment) {
        showFragment(fragment, !(fragment instanceof HomeFragment));
    }

    private void showFragment(Fragment fragment, boolean addToBackStack) {
        boolean isHome = fragment instanceof HomeFragment;
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment);
        if (addToBackStack && !isHome) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void openInitialPage() {
        String page = normalizeDefaultPage(getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_DEFAULT_PAGE, DEFAULT_PAGE_HOME));
        if (DEFAULT_PAGE_SCAN.equals(page)) {
            showFragment(new ScannerFragment(), false);
        } else if (DEFAULT_PAGE_PASSWORD_LEDGER.equals(page)) {
            showFragment(new HomeFragment(), false);
            openPasswordForge(false);
        } else if (DEFAULT_PAGE_OTP.equals(page)) {
            showFragment(new OtpAuthFragment(), false);
        } else {
            showFragment(new HomeFragment(), false);
        }
    }

    private String normalizeDefaultPage(String value) {
        if (value == null || value.isEmpty()) return DEFAULT_PAGE_HOME;
        if (DEFAULT_PAGE_HOME.equals(value)
                || DEFAULT_PAGE_SCAN.equals(value)
                || DEFAULT_PAGE_PASSWORD_LEDGER.equals(value)
                || DEFAULT_PAGE_OTP.equals(value)) {
            return value;
        }
        if (value.equals(getString(R.string.option_scan_page)) || "扫码页".equals(value) || "Scanner".equals(value)) {
            return DEFAULT_PAGE_SCAN;
        }
        if (value.equals(getString(R.string.option_password_ledger)) || "密码账本".equals(value) || "Password Ledger".equals(value)) {
            return DEFAULT_PAGE_PASSWORD_LEDGER;
        }
        if (value.equals(getString(R.string.option_otp_authenticator)) || "OTP 认证器".equals(value) || "OTP Authenticator".equals(value)) {
            return DEFAULT_PAGE_OTP;
        }
        return DEFAULT_PAGE_HOME;
    }

    private void maybeShowBackupReminder() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_BACKUP_REMINDER, 0);
        if (System.currentTimeMillis() - last < BACKUP_REMINDER_INTERVAL_MS) return;
        prefs.edit().putLong(KEY_LAST_BACKUP_REMINDER, System.currentTimeMillis()).apply();
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_reminder)
                .setMessage(R.string.backup_reminder_message)
                .setNegativeButton(R.string.later, null)
                .setPositiveButton(R.string.backup_now, (dialog, which) -> openExport())
                .show();
    }

    @Override
    @OptIn(markerClass = ExperimentalGetImage.class)
    public void openScanner() {
        showFragment(new ScannerFragment());
    }

    @Override
    public void openGenerator() {
        new GenerateFragment().show(getSupportFragmentManager(), "generate");
    }

    @Override
    public void openHistory() {
        showFragment(new HistoryFragment());
    }

    @Override
    public void openWebDav() {
        showFragment(new SettingsFragment());
    }

    @Override
    public void openExport() {
        showFragment(new ExportFragment());
    }

    @Override
    public void openAppearance() {
        showFragment(new AppearanceFragment());
    }

    @Override
    public void openPasswordForge() {
        openPasswordForge(true);
    }

    private void openPasswordForge(boolean addToBackStack) {
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
        showFragment(new OtpAuthFragment());
    }

    private void showPasswordSetupDialog(boolean enterAfterSave) {
        showPasswordSetupDialog(enterAfterSave, true);
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

        content.addView(passwordInput);
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
            Toast.makeText(this, R.string.password_ledger_save_success, Toast.LENGTH_SHORT).show();
            if (enterAfterSave) {
                showFragment(new PasswordForgeFragment(), addToBackStack);
            }
        }));
        dialog.show();
    }

    private void showPasswordVerifyDialog() {
        showPasswordVerifyDialog(true);
    }

    private void showPasswordVerifyDialog(boolean addToBackStack) {
        long remaining = PinLockHelper.remainingLockMs(this);
        if (remaining > 0) {
            Toast.makeText(this, getString(R.string.password_ledger_unlock_error) + "：" + secondsText(remaining), Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText input = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        TextView hint = new TextView(this);
        hint.setTextColor(0xFF8A8F98);
        hint.setTextSize(13);
        String hintText = PinLockHelper.passwordHint(this);
        hint.setText(hintText.isEmpty() ? "" : getString(R.string.password_ledger_access_label) + "：" + hintText);
        TextView forgot = new TextView(this);
        forgot.setText(R.string.password_ledger_forgot_password);
        forgot.setTextColor(0xFF8A8F98);
        forgot.setGravity(Gravity.CENTER);
        forgot.setPadding(0, dp(12), 0, 0);
        forgot.setTextSize(14);
        content.addView(input);
        if (!hintText.isEmpty()) {
            content.addView(hint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        content.addView(forgot, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.password_ledger_enter_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long lockRemaining = PinLockHelper.remainingLockMs(this);
                if (lockRemaining > 0) {
                    Toast.makeText(this, getString(R.string.password_ledger_unlock_error) + "：" + secondsText(lockRemaining), Toast.LENGTH_LONG).show();
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
                dialog.dismiss();
                showFragment(new PasswordForgeFragment(), addToBackStack);
            });
            forgot.setOnClickListener(v -> {
                dialog.dismiss();
                showForgotPasswordDialog(addToBackStack);
            });
        });
        dialog.show();
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
        content.addView(passwordInput);
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
            Toast.makeText(this, R.string.password_ledger_reset_success, Toast.LENGTH_SHORT).show();
            showFragment(new PasswordForgeFragment(), addToBackStack);
        }));
        dialog.show();
    }

    private EditText createPasswordInput(String hint) {
        EditText input = createPlainInput(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
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

    public static int toDelegateMode(String mode) {
        if ("light".equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        if ("dark".equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}

