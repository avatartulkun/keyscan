package com.secureqr.scanner.ui.settings;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
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
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.MainActivity;
import com.secureqr.scanner.R;
import com.secureqr.scanner.autofill.AutofillGuideActivity;
import com.secureqr.scanner.autofill.ChromeAutofillHelper;
import com.secureqr.scanner.autofill.KeyScanAutofillService;
import com.secureqr.scanner.security.ConfigurationRebuildGuard;
import com.secureqr.scanner.security.OperationModeManager;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.LocaleHelper;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.PinLockHelper;
import com.secureqr.scanner.ui.quickaccess.QuickAccessFloatingView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AppearanceFragment extends Fragment {
    private static final String PREFS = "secureqr_settings";
    private static final int REQUEST_POST_NOTIFICATIONS = 901;
    private TextView autofillStatus;
    private TextView chromeAutofillStatus;
    private Switch notificationSwitch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appearance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btn_appearance_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        bindSwitch(view.findViewById(R.id.sw_scan_vibrate), "setting_scan_vibrate", true);
        bindSwitch(view.findViewById(R.id.sw_scan_sound), "setting_scan_sound", false);
        bindSwitch(view.findViewById(R.id.sw_scan_auto_copy), "setting_scan_auto_copy", false);
        bindSwitch(view.findViewById(R.id.sw_album_auto_save), "setting_album_auto_save", true);
        bindSwitch(view.findViewById(R.id.sw_continuous_scan), "setting_continuous_scan", false);
        Switch quickAccessSwitch = view.findViewById(R.id.sw_quick_access);
        quickAccessSwitch.setChecked(prefs().getBoolean(MainActivity.KEY_QUICK_ACCESS_ENABLED, true));
        quickAccessSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs().edit().putBoolean(MainActivity.KEY_QUICK_ACCESS_ENABLED, isChecked).apply();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshQuickAccessVisibility();
            }
        });
        RadioGroup quickAccessIconStyle = view.findViewById(R.id.rg_quick_access_icon_style);
        String savedQuickAccessStyle = prefs().getString(
                MainActivity.KEY_QUICK_ACCESS_ICON_STYLE,
                QuickAccessFloatingView.ORB_STYLE_SPARKLE);
        if (QuickAccessFloatingView.ORB_STYLE_KS.equals(savedQuickAccessStyle)) {
            quickAccessIconStyle.check(R.id.rb_quick_access_ks);
        } else if (QuickAccessFloatingView.ORB_STYLE_KEY.equals(savedQuickAccessStyle)) {
            quickAccessIconStyle.check(R.id.rb_quick_access_key);
        } else {
            quickAccessIconStyle.check(R.id.rb_quick_access_sparkle);
        }
        quickAccessIconStyle.setOnCheckedChangeListener((group, checkedId) -> {
            String style = checkedId == R.id.rb_quick_access_ks
                    ? QuickAccessFloatingView.ORB_STYLE_KS
                    : checkedId == R.id.rb_quick_access_key
                    ? QuickAccessFloatingView.ORB_STYLE_KEY
                    : QuickAccessFloatingView.ORB_STYLE_SPARKLE;
            prefs().edit().putString(MainActivity.KEY_QUICK_ACCESS_ICON_STYLE, style).apply();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshQuickAccessStyle();
            }
        });
        Spinner themeMode = view.findViewById(R.id.sp_theme_mode);
        bindSpinner(themeMode, "theme_mode_label", labelForTheme(prefs().getString("theme_mode", "auto")),
                Arrays.asList(getString(R.string.option_follow_system), getString(R.string.option_force_light), getString(R.string.option_force_dark)));
        themeMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String label = parent.getItemAtPosition(position).toString();
                String mode = position == 1 ? "light" : position == 2 ? "dark" : "auto";
                String oldMode = prefs().getString("theme_mode", "auto");
                prefs().edit().putString("theme_mode", mode).putString("theme_mode_label", label).apply();
                if (mode.equals(oldMode)) return;
                markReturnToAppearance();
                AppCompatDelegate.setDefaultNightMode(MainActivity.toDelegateMode(mode));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        bindDefaultPageSpinner(view.findViewById(R.id.sp_default_page));

        autofillStatus = view.findViewById(R.id.tv_autofill_status);
        chromeAutofillStatus = view.findViewById(R.id.tv_autofill_chrome_status);
        view.findViewById(R.id.btn_autofill_open_settings).setOnClickListener(v -> openAutofillSettings());
        view.findViewById(R.id.btn_autofill_guide).setOnClickListener(v -> startActivity(new Intent(requireContext(), AutofillGuideActivity.class)));
        view.findViewById(R.id.btn_autofill_chrome_settings).setOnClickListener(v -> ChromeAutofillHelper.openChromeAutofillSettings(requireContext()));
        setupNotificationSettings(view);
        setupOperationModeSettings(view);
        updateAutofillStatus();
        updateChromeAutofillStatus();
    }

    private void setupOperationModeSettings(View view) {
        RadioGroup group = view.findViewById(R.id.rg_operation_default_mode);
        String saved = prefs().getString(OperationModeManager.KEY_UNLOCK_DEFAULT,
                OperationModeManager.DEFAULT_VIEW);
        group.check(OperationModeManager.DEFAULT_EDIT.equals(saved)
                ? R.id.rb_operation_edit : R.id.rb_operation_view);
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> prefs().edit()
                .putString(OperationModeManager.KEY_UNLOCK_DEFAULT,
                        checkedId == R.id.rb_operation_edit
                                ? OperationModeManager.DEFAULT_EDIT
                                : OperationModeManager.DEFAULT_VIEW)
                .apply());
        bindSwitch(view.findViewById(R.id.sw_operation_vibrate),
                OperationModeManager.KEY_FEEDBACK_VIBRATE, true);
        bindSwitch(view.findViewById(R.id.sw_operation_message),
                OperationModeManager.KEY_FEEDBACK_MESSAGE, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAutofillStatus();
        updateChromeAutofillStatus();
        if (ConfigurationRebuildGuard.isInProgress()) {
            requireView().postDelayed(() -> {
                prefs().edit().remove(MainActivity.KEY_OPEN_APPEARANCE_ON_RECREATE).apply();
                ConfigurationRebuildGuard.clear();
            }, 250L);
        }
    }

    private void showLanguageDialog() {
        String[] tags = {LocaleHelper.SYSTEM_DEFAULT, "zh-CN", "en-US", "ja-JP", "zh-TW", "ko-KR", "de-DE", "fr-FR", "es-ES", "it-IT", "nl-NL", "pt-BR", "ru-RU"};
        String[] nativeNames = {"", "简体中文", "English (US)", "日本語", "繁體中文", "한국어", "Deutsch", "Français", "Español", "Italiano", "Nederlands", "Português (Brasil)", "Русский"};
        String current = LocaleHelper.currentLanguage(requireContext());
        Dialog dialog = new Dialog(requireContext());
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(18), dp(22), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(requireContext(), R.color.card_background));
        background.setCornerRadius(dp(22));
        card.setBackground(background);
        TextView title = languageHeader(getString(R.string.language), 21, 0xFF1F2937);
        card.addView(title);
        card.addView(languageHeader(getString(R.string.language_picker_follow_system), 13, 0xFF7A8494));
        LinearLayout group = new LinearLayout(requireContext());
        group.setOrientation(LinearLayout.VERTICAL);
        addLanguageRow(group, 0, getString(R.string.language_picker_system_default), getString(R.string.language_picker_system_summary), tags[0].equals(current), () -> selectLanguage(dialog, tags[0], current));
        card.addView(group);
        card.addView(languageHeader(getString(R.string.language_picker_available), 13, 0xFF7A8494));
        LinearLayout languages = new LinearLayout(requireContext());
        languages.setOrientation(LinearLayout.VERTICAL);
        card.addView(languages);
        for (int i = 1; i < tags.length; i++) {
            final int index = i;
            addLanguageRow(languages, i, nativeNames[i], localizedLanguageName(tags[i]), tags[i].equals(current), () -> selectLanguage(dialog, tags[index], current));
        }
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(card);
        int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
        int dialogHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.82f);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(dialogWidth, dialogHeight));
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(0);
        }
        dialog.show();
    }

    private void selectLanguage(Dialog dialog, String language, String current) {
        if (language.equals(current)) return;
        LocaleHelper.saveLanguage(requireContext(), language);
        dialog.dismiss();
        markReturnToAppearance();
        requireActivity().recreate();
    }

    private String localizedLanguageName(String languageTag) {
        if ("zh-CN".equals(languageTag)) return getString(R.string.language_simplified_chinese);
        if ("zh-TW".equals(languageTag)) return getString(R.string.language_traditional_chinese);
        Locale displayLocale = Build.VERSION.SDK_INT >= 24
                ? getResources().getConfiguration().getLocales().get(0)
                : getResources().getConfiguration().locale;
        Locale languageLocale = Locale.forLanguageTag(languageTag);
        String name = languageLocale.getDisplayLanguage(displayLocale);
        if (name == null || name.trim().isEmpty()) return languageLocale.getDisplayLanguage(Locale.ENGLISH);
        return name.substring(0, 1).toUpperCase(displayLocale) + name.substring(1);
    }

    private void addLanguageRow(LinearLayout group, int id, String nativeName, String explanation, boolean checked, Runnable action) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        RadioButton radio = new RadioButton(requireContext());
        radio.setId(id);
        radio.setText(nativeName);
        radio.setTextSize(16);
        radio.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        radio.setChecked(checked);
        radio.setOnClickListener(v -> action.run());
        row.addView(radio, new LinearLayout.LayoutParams(0, dp(50), 1));
        TextView detail = new TextView(requireContext());
        detail.setText(explanation);
        detail.setTextSize(13);
        detail.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        detail.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        detail.setOnClickListener(v -> action.run());
        row.addView(detail, new LinearLayout.LayoutParams(dp(138), dp(50)));
        group.addView(row);
        View divider = new View(requireContext());
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_stroke));
        group.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private TextView languageHeader(String text, int size, int color) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private String labelForTheme(String mode) {
        if ("light".equals(mode)) return getString(R.string.option_force_light);
        if ("dark".equals(mode)) return getString(R.string.option_force_dark);
        return getString(R.string.option_follow_system);
    }

    private void markReturnToAppearance() {
        ConfigurationRebuildGuard.begin("appearance_recreate");
        prefs().edit().putBoolean(MainActivity.KEY_OPEN_APPEARANCE_ON_RECREATE, true).apply();
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
                getString(R.string.vault_title),
                getString(R.string.option_password_ledger),
                getString(R.string.option_otp_authenticator));
        List<String> values = Arrays.asList(
                MainActivity.DEFAULT_PAGE_HOME,
                MainActivity.DEFAULT_PAGE_VAULT,
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
        if (saved.equals(getString(R.string.vault_title))) return 1;
        if (saved.equals(getString(R.string.option_password_ledger)) || "密码账本".equals(saved) || "Password Ledger".equals(saved)) return 2;
        if (saved.equals(getString(R.string.option_otp_authenticator)) || "TOTP认证器".equals(saved) || "TOTP Authenticator".equals(saved) || "OTP认证器".equals(saved) || "OTP Authenticator".equals(saved)) return 3;
        return 0;
    }

    private void bindSwitch(Switch view, String key, boolean defaultValue) {
        view.setChecked(prefs().getBoolean(key, defaultValue));
        view.setOnCheckedChangeListener((buttonView, isChecked) -> prefs().edit().putBoolean(key, isChecked).apply());
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
        hint.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        hint.setTextSize(13);
        String hintText = PinLockHelper.passwordHint(requireContext());
        hint.setText(hintText.isEmpty() ? "" : getString(R.string.password_ledger_access_label) + "：" + hintText);
        TextView forgot = new TextView(requireContext());
        forgot.setText(R.string.password_ledger_forgot_password);
        forgot.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
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
            Toast.makeText(requireContext(), R.string.password_ledger_reset_success, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private EditText createPasswordInput(String hint) {
        EditText input = createPlainInput(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return input;
    }

    private android.widget.FrameLayout createPasswordInputRow(EditText input) {
        android.widget.FrameLayout row = new android.widget.FrameLayout(requireContext());
        Button eye = new Button(requireContext());
        eye.setMinWidth(0);
        eye.setPadding(0, 0, 0, 0);
        final boolean[] visible = {false};
        Runnable update = () -> {
            input.setInputType(InputType.TYPE_CLASS_TEXT | (visible[0]
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            input.setSelection(input.getText().length());
            eye.setText("");
            eye.setCompoundDrawablesWithIntrinsicBounds(0, visible[0] ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24, 0, 0);
            eye.setCompoundDrawableTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.action_icon_tint)));
            eye.setBackground(null);
            eye.setElevation(0f);
            eye.setStateListAnimator(null);
            eye.setContentDescription(getString(visible[0] ? R.string.hide_content : R.string.show_content));
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

    private void setupBiometricSwitch(Switch biometricSwitch) {
        if (biometricSwitch == null) return;
        biometricSwitch.setChecked(BiometricUnlockHelper.isEnabled(requireContext()));
        final boolean[] updatingSwitch = {false};
        biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingSwitch[0]) return;
            boolean current = BiometricUnlockHelper.isEnabled(requireContext());
            if (isChecked == current) return;
            if (isChecked && !PinLockHelper.isConfigured(requireContext())) {
                updatingSwitch[0] = true;
                buttonView.setChecked(false);
                updatingSwitch[0] = false;
                Toast.makeText(requireContext(), R.string.biometric_unlock_need_password, Toast.LENGTH_SHORT).show();
                return;
            }
            if (isChecked && !BiometricUnlockHelper.isAvailable(requireContext())) {
                updatingSwitch[0] = true;
                buttonView.setChecked(false);
                updatingSwitch[0] = false;
                Toast.makeText(requireContext(), R.string.biometric_unlock_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            updatingSwitch[0] = true;
            buttonView.setChecked(current);
            updatingSwitch[0] = false;
            VaultAccessManager.requirePinAuthentication(
                    requireActivity(),
                    getString(R.string.biometric_change_pin_auth),
                    () -> {
                        BiometricUnlockHelper.setEnabled(requireContext(), isChecked);
                        SecuritySettings.prefs(requireContext()).edit()
                                .putBoolean(SecuritySettings.KEY_BIOMETRIC_ENABLED, isChecked)
                                .putLong(SecuritySettings.KEY_LAST_SECURITY_CHECK, System.currentTimeMillis())
                                .apply();
                        updatingSwitch[0] = true;
                        buttonView.setChecked(isChecked);
                        updatingSwitch[0] = false;
                        if (isChecked) {
                            Toast.makeText(requireContext(), R.string.biometric_unlock_enabled, Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void setupNotificationSettings(View view) {
        notificationSwitch = view.findViewById(R.id.sw_notifications);
        if (notificationSwitch == null) return;
        notificationSwitch.setChecked(prefs().getBoolean("setting_notifications", false));
        notificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs().edit().putBoolean("setting_notifications", isChecked).apply();
            if (isChecked && Build.VERSION.SDK_INT >= 33
                    && requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            }
        });
        view.findViewById(R.id.btn_notification_settings).setOnClickListener(v -> openNotificationSettings());
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + requireContext().getPackageName())));
        }
    }

    private void updateAutofillStatus() {
        if (autofillStatus == null || !isAdded()) return;
        boolean enabled = isAutofillEnabled();
        autofillStatus.setText(enabled ? R.string.autofill_enabled : R.string.autofill_disabled);
        autofillStatus.setTextColor(getResources().getColor(enabled ? R.color.accent : R.color.danger));
    }

    private void updateChromeAutofillStatus() {
        if (chromeAutofillStatus == null || !isAdded()) return;
        ChromeAutofillHelper.State state = ChromeAutofillHelper.readThirdPartyMode(requireContext());
        chromeAutofillStatus.setText(getString(R.string.autofill_chrome_status, ChromeAutofillHelper.stateLabel(requireContext())));
        int color = state == ChromeAutofillHelper.State.ENABLED ? R.color.accent
                : state == ChromeAutofillHelper.State.DISABLED ? R.color.danger
                : R.color.text_secondary;
        chromeAutofillStatus.setTextColor(getResources().getColor(color));
    }

    private void openAutofillSettings() {
        ComponentName componentName = new ComponentName(requireContext(), KeyScanAutofillService.class);
        Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(Uri.parse("package:" + requireContext().getPackageName()))
                .putExtra("android.provider.extra.AUTOFILL_SERVICE", componentName.flattenToString());
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean isAutofillEnabled() {
        ComponentName componentName = new ComponentName(requireContext(), KeyScanAutofillService.class);
        String enabled = Settings.Secure.getString(requireContext().getContentResolver(), "autofill_service");
        return componentName.flattenToString().equals(enabled);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_POST_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            prefs().edit().putBoolean("setting_notifications", granted).apply();
            if (notificationSwitch != null) notificationSwitch.setChecked(granted);
            Toast.makeText(requireContext(), granted ? R.string.notification_enabled : R.string.notification_disabled, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

