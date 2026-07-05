package com.secureqr.scanner.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.MainActivity;
import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.AppConstants;
import com.secureqr.scanner.utils.LocaleHelper;
import com.secureqr.scanner.utils.PinLockHelper;

import java.util.Arrays;
import java.util.List;

public class AppearanceFragment extends Fragment {
    private static final String PREFS = "secureqr_settings";
    private TextView pinStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appearance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        pinStatus = view.findViewById(R.id.tv_password_forge_pin_status);
        bindSpinner(view.findViewById(R.id.sp_auto_lock), "setting_auto_lock", getString(R.string.option_1_minute),
                Arrays.asList(getString(R.string.option_now), getString(R.string.option_1_minute), getString(R.string.option_5_minutes), getString(R.string.option_10_minutes), getString(R.string.option_never)));
        bindSpinner(view.findViewById(R.id.sp_clipboard_clear), "setting_clipboard_clear", getString(R.string.option_1_minute),
                Arrays.asList(getString(R.string.option_30_seconds), getString(R.string.option_1_minute), getString(R.string.option_5_minutes), getString(R.string.option_never)));
        bindSwitch(view.findViewById(R.id.sw_hide_sensitive), "setting_hide_sensitive", false);
        bindSwitch(view.findViewById(R.id.sw_scan_vibrate), "setting_scan_vibrate", true);
        bindSwitch(view.findViewById(R.id.sw_scan_sound), "setting_scan_sound", false);
        bindSwitch(view.findViewById(R.id.sw_scan_auto_copy), "setting_scan_auto_copy", false);
        bindSwitch(view.findViewById(R.id.sw_album_auto_save), "setting_album_auto_save", true);
        bindSwitch(view.findViewById(R.id.sw_continuous_scan), "setting_continuous_scan", false);
        bindSpinner(view.findViewById(R.id.sp_history_limit), "setting_history_limit", getString(R.string.option_500_items),
                Arrays.asList(getString(R.string.option_100_items), getString(R.string.option_500_items), getString(R.string.option_1000_items), getString(R.string.option_unlimited)));
        bindSpinner(view.findViewById(R.id.sp_history_cleanup), "setting_history_cleanup", getString(R.string.option_forever),
                Arrays.asList(getString(R.string.option_1_month), getString(R.string.option_3_months), getString(R.string.option_6_months), getString(R.string.option_forever)));
        bindSwitch(view.findViewById(R.id.sw_qr_history), "setting_qr_history", true);
        bindSpinner(view.findViewById(R.id.sp_auto_sync_freq), "setting_auto_sync_freq", getString(R.string.option_manual),
                Arrays.asList(getString(R.string.option_manual), getString(R.string.option_after_each_change), getString(R.string.option_daily), getString(R.string.option_weekly)));
        bindSwitch(view.findViewById(R.id.sw_wifi_sync), "setting_wifi_sync", true);
        bindSwitch(view.findViewById(R.id.sw_sync_confirm), "setting_sync_confirm", false);
        Spinner themeMode = view.findViewById(R.id.sp_theme_mode);
        bindSpinner(themeMode, "theme_mode_label", labelForTheme(prefs().getString("theme_mode", "auto")),
                Arrays.asList(getString(R.string.option_follow_system), getString(R.string.option_force_light), getString(R.string.option_force_dark)));
        themeMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String label = parent.getItemAtPosition(position).toString();
                String mode = position == 1 ? "light" : position == 2 ? "dark" : "auto";
                prefs().edit().putString("theme_mode", mode).putString("theme_mode_label", label).apply();
                AppCompatDelegate.setDefaultNightMode(MainActivity.toDelegateMode(mode));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        bindDefaultPageSpinner(view.findViewById(R.id.sp_default_page));

        view.findViewById(R.id.btn_set_password_forge_pin).setOnClickListener(v -> beginCredentialChange());
        view.findViewById(R.id.btn_language).setOnClickListener(v -> showLanguageDialog());
        view.findViewById(R.id.btn_about_keyscan).setOnClickListener(v -> showAboutDialog());
        updatePinStatus();
    }

    private void showLanguageDialog() {
        String[] labels = {getString(R.string.language_simplified_chinese), getString(R.string.language_english)};
        String current = LocaleHelper.currentLanguage(requireContext());
        int checked = "en".equals(current) ? 1 : 0;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.language) + " / Language")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    LocaleHelper.saveLanguage(requireContext(), which == 1 ? "en" : "zh");
                    dialog.dismiss();
                    requireActivity().recreate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String labelForTheme(String mode) {
        if ("light".equals(mode)) return getString(R.string.option_force_light);
        if ("dark".equals(mode)) return getString(R.string.option_force_dark);
        return getString(R.string.option_follow_system);
    }

    private void bindSpinner(Spinner spinner, String key, String defaultValue, List<String> values) {
        spinner.setAdapter(new ThemedSpinnerAdapter(requireContext(), values));
        String saved = prefs().getString(key, defaultValue);
        int index = Math.max(0, values.indexOf(saved));
        spinner.setSelection(index);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs().edit().putString(key, values.get(position)).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void bindDefaultPageSpinner(Spinner spinner) {
        List<String> labels = Arrays.asList(
                getString(R.string.option_home),
                getString(R.string.option_scan_page),
                getString(R.string.option_password_ledger),
                getString(R.string.option_otp_authenticator));
        List<String> values = Arrays.asList(
                MainActivity.DEFAULT_PAGE_HOME,
                MainActivity.DEFAULT_PAGE_SCAN,
                MainActivity.DEFAULT_PAGE_PASSWORD_LEDGER,
                MainActivity.DEFAULT_PAGE_OTP);
        spinner.setAdapter(new ThemedSpinnerAdapter(requireContext(), labels));
        String saved = prefs().getString(MainActivity.KEY_DEFAULT_PAGE, MainActivity.DEFAULT_PAGE_HOME);
        int index = values.indexOf(saved);
        if (index < 0) index = legacyDefaultPageIndex(saved);
        spinner.setSelection(Math.max(0, index));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs().edit().putString(MainActivity.KEY_DEFAULT_PAGE, values.get(position)).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private int legacyDefaultPageIndex(String saved) {
        if (saved == null) return 0;
        if (saved.equals(getString(R.string.option_scan_page)) || "扫码页".equals(saved) || "Scanner".equals(saved)) return 1;
        if (saved.equals(getString(R.string.option_password_ledger)) || "密码账本".equals(saved) || "Password Ledger".equals(saved)) return 2;
        if (saved.equals(getString(R.string.option_otp_authenticator)) || "OTP 认证器".equals(saved) || "OTP Authenticator".equals(saved)) return 3;
        return 0;
    }

    private void bindSwitch(Switch view, String key, boolean defaultValue) {
        view.setChecked(prefs().getBoolean(key, defaultValue));
        view.setOnCheckedChangeListener((buttonView, isChecked) -> prefs().edit().putBoolean(key, isChecked).apply());
    }

    private void showAboutDialog() {
        String version = "1.0.0";
        try {
            version = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("关于 KeyScan")
                .setMessage("KeyScan 密扫\n版本：" + version + "\n\n专业二维码、密码账本与 OTP 安全工具。\n\n开源许可：请以项目仓库为准。\n反馈邮箱：" + AppConstants.FEEDBACK_EMAIL)
                .setPositiveButton("确定", null)
                .show();
    }

    private void beginCredentialChange() {
        if (PinLockHelper.isConfigured(requireContext())) {
            verifyCurrentPasswordBeforeChange();
        } else {
            showSetCredentialsDialog();
        }
    }

    private void verifyCurrentPasswordBeforeChange() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText currentInput = createPasswordInput(getString(R.string.password_ledger_current_password_hint));
        TextView hint = new TextView(requireContext());
        hint.setTextColor(0xFF8A8F98);
        hint.setTextSize(13);
        String hintText = PinLockHelper.passwordHint(requireContext());
        hint.setText(hintText.isEmpty() ? "" : getString(R.string.password_ledger_access_label) + "：" + hintText);
        TextView forgot = new TextView(requireContext());
        forgot.setText(R.string.password_ledger_forgot_password);
        forgot.setTextColor(0xFF8A8F98);
        forgot.setGravity(Gravity.CENTER);
        forgot.setPadding(0, dp(12), 0, 0);
        forgot.setTextSize(14);
        content.addView(createPasswordInputRow(currentInput));
        if (!hintText.isEmpty()) {
            content.addView(hint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        content.addView(forgot, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_ledger_enter_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!PinLockHelper.verifyPin(requireContext(), currentInput.getText().toString().trim())) {
                    currentInput.setError(getString(R.string.password_ledger_unlock_error));
                    Toast.makeText(requireContext(), R.string.password_ledger_unlock_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                showSetCredentialsDialog();
            });
            forgot.setOnClickListener(v -> {
                dialog.dismiss();
                showForgotPasswordDialog();
            });
        });
        dialog.show();
    }

    private void showSetCredentialsDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, 0);

        EditText passwordInput = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        EditText hintInput = createPlainInput(getString(R.string.password_input_hint));
        Spinner questionSpinner = new Spinner(requireContext());
        questionSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, PinLockHelper.securityQuestions(requireContext())));
        EditText answerInput = createPlainInput(getString(R.string.password_ledger_answer_hint));
        content.addView(createPasswordInputRow(passwordInput));
        hintInput.setText(PinLockHelper.passwordHint(requireContext()));
        content.addView(hintInput);
        content.addView(questionSpinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        content.addView(answerInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(PinLockHelper.isConfigured(requireContext()) ? R.string.password_ledger_reset_title : R.string.password_ledger_setup_title)
                .setMessage(R.string.password_ledger_setup_message)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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
            PinLockHelper.saveCredentials(requireContext(), password, hintInput.getText().toString(), questionSpinner.getSelectedItem().toString(), answer);
            updatePinStatus();
            Toast.makeText(requireContext(), R.string.password_ledger_save_success, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showForgotPasswordDialog() {
        EditText answerInput = createPlainInput(getString(R.string.password_ledger_answer_hint));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_ledger_forgot_password)
                .setMessage(PinLockHelper.securityQuestion(requireContext()))
                .setView(answerInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!PinLockHelper.verifySecurityAnswer(requireContext(), answerInput.getText().toString())) {
                answerInput.setError(getString(R.string.password_ledger_unlock_error));
                Toast.makeText(requireContext(), R.string.password_ledger_unlock_error, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showResetPasswordDialog();
        }));
        dialog.show();
    }

    private void showResetPasswordDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText passwordInput = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        EditText hintInput = createPlainInput(getString(R.string.password_input_hint));
        hintInput.setText(PinLockHelper.passwordHint(requireContext()));
        content.addView(createPasswordInputRow(passwordInput));
        content.addView(hintInput);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
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
            PinLockHelper.savePasswordAndHint(requireContext(), password, hintInput.getText().toString());
            updatePinStatus();
            Toast.makeText(requireContext(), R.string.password_ledger_reset_success, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private EditText createPasswordInput(String hint) {
        EditText input = createPlainInput(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private LinearLayout createPasswordInputRow(EditText input) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button eye = new Button(requireContext());
        eye.setMinWidth(0);
        eye.setPadding(0, 0, 0, 0);
        final boolean[] visible = {false};
        Runnable update = () -> {
            input.setInputType(InputType.TYPE_CLASS_TEXT | (visible[0]
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            input.setSelection(input.getText().length());
            eye.setText(visible[0] ? "🙈" : "👁");
            eye.setContentDescription(getString(visible[0]
                    ? R.string.credential_hide_password_desc
                    : R.string.credential_show_password_desc));
        };
        update.run();
        eye.setOnClickListener(v -> {
            visible[0] = !visible[0];
            update.run();
        });
        row.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams eyeParams = new LinearLayout.LayoutParams(dp(48), dp(52));
        eyeParams.leftMargin = dp(8);
        row.addView(eye, eyeParams);
        return row;
    }

    private EditText createPlainInput(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setMinHeight(dp(52));
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF80868B);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private void updatePinStatus() {
        if (pinStatus == null) return;
        pinStatus.setText(getString(R.string.password_ledger_access_label) + "：" + (PinLockHelper.isConfigured(requireContext()) ? getString(R.string.password_forge_pin_set) : getString(R.string.password_forge_pin_unset)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

