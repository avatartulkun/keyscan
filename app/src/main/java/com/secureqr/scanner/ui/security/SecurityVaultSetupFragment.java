package com.secureqr.scanner.ui.security;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.VaultKeyEnvelopeManager;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.VaultSession;
import com.secureqr.scanner.ui.exporter.ExportDataFragment;
import com.secureqr.scanner.ui.otp.OtpAuthFragment;
import com.secureqr.scanner.ui.password.PasswordForgeFragment;
import com.secureqr.scanner.ui.home.PrimaryNavigationFragment;
import com.secureqr.scanner.ui.settings.ExportFragment;
import com.secureqr.scanner.ui.settings.SettingsFragment;
import com.secureqr.scanner.ui.vault.VaultFragment;
import com.secureqr.scanner.utils.PinLockHelper;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecurityVaultSetupFragment extends Fragment {
    public static final String DEST_HOME = "home";
    public static final String DEST_PASSWORD_LEDGER = "password_ledger";
    public static final String DEST_OTP = "otp";
    public static final String DEST_VAULT = "vault";
    public static final String DEST_WEBDAV = "webdav";
    public static final String DEST_EXPORT = "export";
    public static final String DEST_GENERIC_EXPORT = "generic_export";

    private static final String ARG_DESTINATION = "destination";
    private static final String KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private EditText pinInput;
    private EditText keyInput;
    private CheckBox keySavedCheck;
    private TextView keyLengthValue;
    private int keyLength = 16;
    private boolean keyExported;
    private String exportedKey = "";
    private ActivityResultLauncher<String> keyDownloader;
    private ActivityResultLauncher<String[]> keyImporter;

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        keyDownloader = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/plain"),
                uri -> {
                    if (uri == null) return;
                    String key = normalizeKey(keyInput == null ? "" : keyInput.getText().toString());
                    try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri, "w")) {
                        if (output == null) {
                            throw new IllegalStateException(getString(R.string.vault_setup_download_failed, ""));
                        }
                        output.write(keyDocument(key).getBytes(StandardCharsets.UTF_8));
                        markKeyExported(key, getString(R.string.vault_setup_key_downloaded));
                    } catch (Exception error) {
                        Toast.makeText(
                                requireContext(),
                                getString(R.string.vault_setup_download_failed, error.getMessage()),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
        keyImporter = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || keyInput == null) return;
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException(getString(R.string.data_key_import_failed));
                String imported = parseDownloadedKey(new String(readAllBytes(input), StandardCharsets.UTF_8));
                if (imported.isEmpty()) throw new IllegalArgumentException(getString(R.string.data_key_import_invalid));
                keyInput.setText(imported);
                keyInput.setSelection(keyInput.length());
                markKeyPreserved(imported);
                Toast.makeText(requireContext(), R.string.data_key_import_success, Toast.LENGTH_SHORT).show();
            } catch (Exception error) {
                Toast.makeText(requireContext(), error.getMessage() == null
                        ? getString(R.string.data_key_import_failed) : error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public static SecurityVaultSetupFragment newInstance(String destination) {
        SecurityVaultSetupFragment fragment = new SecurityVaultSetupFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DESTINATION, destination);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        LinearLayout root = SecurityUi.page(this, getString(R.string.vault_setup_title));
        root.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_light));
        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        ImageView hero = new ImageView(requireContext());
        hero.setImageResource(R.drawable.ic_vault_setup_hero);
        hero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(dp(150), dp(150));
        heroParams.topMargin = dp(12);
        heroParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(hero, heroParams);

        root.addView(createRestoreNotice(), SecurityUi.matchWrap(this, 8));
        root.addView(stepPinCard(), SecurityUi.matchWrap(this, 18));
        root.addView(stepKeyCard(), SecurityUi.matchWrap(this, 14));

        Button save = createSaveButton();
        save.setOnClickListener(v -> createVault());
        LinearLayout.LayoutParams saveParams = SecurityUi.matchWrap(this, 18);
        saveParams.height = dp(56);
        root.addView(save, saveParams);
        return scrollView;
    }

    private LinearLayout createRestoreNotice() {
        LinearLayout notice = new LinearLayout(requireContext());
        notice.setOrientation(LinearLayout.HORIZONTAL);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setPadding(dp(16), dp(14), dp(16), dp(14));
        notice.setBackgroundResource(R.drawable.bg_vault_setup_notice);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_shield);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        icon.setBackgroundResource(R.drawable.bg_vault_setup_notice_icon);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        notice.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView message = SecurityUi.text(
                this,
                getString(R.string.vault_setup_restore_warning),
                14,
                R.color.text_main,
                false
        );
        message.setLineSpacing(0, 1.22f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        messageParams.setMarginStart(dp(14));
        notice.addView(message, messageParams);
        return notice;
    }

    private LinearLayout stepPinCard() {
        LinearLayout card = setupCard();
        addStepHeader(
                card,
                "1",
                getString(R.string.vault_setup_pin_step),
                getString(R.string.vault_setup_pin_summary)
        );
        if (PinLockHelper.isConfigured(requireContext())) {
            card.addView(
                    SecurityUi.text(this, getString(R.string.vault_setup_pin_existing), 14, R.color.success, true),
                    SecurityUi.matchWrap(this, 12)
            );
            return card;
        }

        pinInput = pinEdit(getString(R.string.vault_setup_pin_hint));
        card.addView(createPasswordInputRow(pinInput, true), SecurityUi.matchWrap(this, 16));
        return card;
    }

    private LinearLayout stepKeyCard() {
        LinearLayout card = setupCard();
        addStepHeader(
                card,
                "2",
                getString(R.string.vault_setup_key_step),
                getString(R.string.vault_setup_key_summary)
        );
        card.addView(
                SecurityUi.text(this, getString(R.string.vault_setup_manual_auto_hint), 13, R.color.accent, false),
                SecurityUi.matchWrap(this, 8)
        );
        card.addView(createKeyLengthSelector(), SecurityUi.matchWrap(this, 10));

        LinearLayout keyInputRow = createKeyInputRow();
        keyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (exportedKey.equals(normalizeKey(s.toString()))) return;
                keyExported = false;
                if (keySavedCheck != null) {
                    keySavedCheck.setChecked(false);
                    keySavedCheck.setText(R.string.vault_setup_key_not_preserved);
                }
            }

            @Override public void afterTextChanged(Editable editable) {}
        });

        card.addView(keyInputRow, SecurityUi.matchWrap(this, 14));
        Button importKey = createSecondaryButton(getString(R.string.data_key_import_downloaded), R.drawable.ic_download_24);
        importKey.setOnClickListener(v -> keyImporter.launch(new String[]{"text/plain", "text/*"}));
        card.addView(importKey, SecurityUi.matchWrap(this, 10));

        LinearLayout exportActions = new LinearLayout(requireContext());
        exportActions.setOrientation(LinearLayout.HORIZONTAL);
        Button download = createSecondaryButton(
                getString(R.string.vault_setup_download_key),
                R.drawable.ic_download_24
        );
        download.setOnClickListener(v -> downloadKey());
        Button copy = createSecondaryButton(
                getString(R.string.vault_setup_copy_key),
                R.drawable.ic_content_copy_24
        );
        copy.setOnClickListener(v -> copyKey());

        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        downloadParams.setMarginEnd(dp(6));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        copyParams.setMarginStart(dp(6));
        exportActions.addView(download, downloadParams);
        exportActions.addView(copy, copyParams);
        card.addView(exportActions, SecurityUi.matchWrap(this, 14));

        keySavedCheck = new CheckBox(requireContext());
        keySavedCheck.setText(R.string.vault_setup_key_not_preserved);
        keySavedCheck.setEnabled(false);
        keySavedCheck.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        card.addView(keySavedCheck, SecurityUi.matchWrap(this, 8));
        return card;
    }

    private LinearLayout setupCard() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundResource(R.drawable.bg_vault_setup_card);
        card.setElevation(dp(2));
        return card;
    }

    private void addStepHeader(LinearLayout card, String number, String title, String summary) {
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);

        TextView badge = new TextView(requireContext());
        badge.setText(number);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(20);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        badge.setBackgroundResource(R.drawable.bg_vault_setup_step_badge);
        header.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView heading = SecurityUi.text(this, title, 18, R.color.text_main, true);
        heading.setLineSpacing(0, 1.1f);
        texts.addView(heading);
        TextView description = SecurityUi.text(this, summary, 13, R.color.text_secondary, false);
        description.setLineSpacing(0, 1.22f);
        texts.addView(description, SecurityUi.matchWrap(this, 4));

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.setMarginStart(dp(14));
        header.addView(texts, textParams);
        card.addView(header);
    }

    private LinearLayout createKeyInputRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_vault_setup_input);

        keyInput = passwordEdit(getString(R.string.vault_setup_input_password_short));
        row.addView(keyInput, new LinearLayout.LayoutParams(0, dp(58), 1));

        ImageButton generate = keyIconButton(
                R.drawable.ic_refresh_24,
                getString(R.string.action_generate),
                R.color.accent
        );
        generate.setOnClickListener(v -> generateKeyIntoFields());
        row.addView(generate);

        ImageButton visibility = keyIconButton(
                R.drawable.ic_visibility_off_24,
                getString(R.string.show_password),
                R.color.text_secondary
        );
        visibility.setOnClickListener(v -> toggleFieldVisibility(keyInput, visibility, false));
        row.addView(visibility);
        return row;
    }

    private LinearLayout createKeyLengthSelector() {
        LinearLayout selector = new LinearLayout(requireContext());
        selector.setOrientation(LinearLayout.VERTICAL);
        selector.setPadding(dp(14), dp(12), dp(14), dp(10));
        selector.setBackgroundResource(R.drawable.bg_vault_setup_input);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = SecurityUi.text(this, getString(R.string.vault_setup_length_title), 14, R.color.text_main, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        keyLengthValue = SecurityUi.text(this, getString(R.string.vault_setup_length_current, keyLength), 14, R.color.primary_blue, true);
        keyLengthValue.setGravity(Gravity.END);
        header.addView(keyLengthValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        selector.addView(header);

        SeekBar lengthSlider = new SeekBar(requireContext());
        lengthSlider.setMax(24);
        lengthSlider.setProgress(keyLength - 8);
        lengthSlider.setProgressDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_vault_key_length_track));
        lengthSlider.setThumb(ContextCompat.getDrawable(requireContext(), R.drawable.bg_vault_key_length_thumb));
        lengthSlider.setContentDescription(getString(R.string.vault_setup_length_value, keyLength));
        lengthSlider.setPadding(0, dp(3), 0, dp(3));
        lengthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyLength = 8 + progress;
                updateKeyLengthText(seekBar);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        selector.addView(lengthSlider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        LinearLayout limits = new LinearLayout(requireContext());
        limits.setGravity(Gravity.CENTER_VERTICAL);
        limits.addView(SecurityUi.text(this, getString(R.string.vault_setup_length_min), 12, R.color.text_secondary, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView max = SecurityUi.text(this, getString(R.string.vault_setup_length_max), 12, R.color.text_secondary, false);
        max.setGravity(Gravity.END);
        limits.addView(max, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        selector.addView(limits);
        return selector;
    }

    private void updateKeyLengthText(SeekBar slider) {
        if (keyLengthValue != null) {
            keyLengthValue.setText(getString(R.string.vault_setup_length_current, keyLength));
        }
        if (slider != null) {
            slider.setContentDescription(getString(R.string.vault_setup_length_value, keyLength));
        }
    }

    private LinearLayout createPasswordInputRow(EditText input, boolean numeric) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_vault_setup_input);
        row.addView(input, new LinearLayout.LayoutParams(0, dp(58), 1));

        ImageButton visibility = keyIconButton(
                R.drawable.ic_visibility_off_24,
                getString(R.string.show_password),
                R.color.text_secondary
        );
        visibility.setOnClickListener(v -> toggleFieldVisibility(input, visibility, numeric));
        row.addView(visibility);
        return row;
    }

    private ImageButton keyIconButton(int icon, String description, int tintColorRes) {
        ImageButton button = new ImageButton(requireContext());
        button.setImageResource(icon);
        button.setColorFilter(ContextCompat.getColor(requireContext(), tintColorRes));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private void generateKeyIntoFields() {
        String key = generateDataProtectionKey();
        keyInput.setText(key);
        keyInput.setSelection(keyInput.length());
        Toast.makeText(requireContext(), R.string.data_key_keep_safe_warning, Toast.LENGTH_LONG).show();
    }

    private void toggleFieldVisibility(EditText input, ImageButton button, boolean numeric) {
        boolean hidden = input.getTransformationMethod() instanceof PasswordTransformationMethod;
        input.setTransformationMethod(hidden ? null : PasswordTransformationMethod.getInstance());
        button.setImageResource(hidden ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24);
        button.setContentDescription(getString(hidden ? R.string.hide_password : R.string.show_password));
        input.setSelection(input.length());
    }

    private EditText pinEdit(String hint) {
        EditText edit = new EditText(requireContext());
        edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        edit.setTransformationMethod(PasswordTransformationMethod.getInstance());
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        edit.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        edit.setPadding(dp(16), 0, dp(8), 0);
        return edit;
    }

    private EditText passwordEdit(String hint) {
        EditText edit = new EditText(requireContext());
        edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setTransformationMethod(PasswordTransformationMethod.getInstance());
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        edit.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        edit.setPadding(dp(16), 0, dp(8), 0);
        return edit;
    }

    private Button createSecondaryButton(String text, int iconRes) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue));
        button.setBackgroundResource(R.drawable.bg_vault_setup_action);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(8));
        button.setCompoundDrawableTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.primary_blue)
        ));
        return button;
    }

    private Button createSaveButton() {
        Button button = new Button(requireContext());
        button.setText(R.string.common_action_save);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setBackgroundResource(R.drawable.bg_vault_setup_primary);
        button.setElevation(dp(3));
        return button;
    }

    private void createVault() {
        String pin = "";
        String key = normalizeKey(keyInput.getText().toString());
        boolean pinAlreadyConfigured = PinLockHelper.isConfigured(requireContext());
        if (!pinAlreadyConfigured) {
            pin = pinInput.getText().toString().trim();
            if (!PinLockHelper.isValidPin(pin)) {
                pinInput.setError(getString(R.string.security_pin_input_hint));
                return;
            }
        }
        if (!isValidDataProtectionKey(key)) {
            keyInput.setError(getString(R.string.vault_setup_key_format));
            return;
        }
        if (!keyExported || !key.equals(exportedKey)) {
            Toast.makeText(requireContext(), R.string.vault_setup_preserve_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pinAlreadyConfigured) {
            SecuritySettings.savePin(requireContext(), pin);
        }
        boolean keyReady = SecuritySettings.saveDataEncryptionKey(requireContext(), key)
                && (VaultKeyEnvelopeManager.isInitialized(requireContext())
                || DatabaseKeyManager.initializeDatabaseKey(requireContext(), pin))
                && SecuritySettings.markVaultInitialized(requireContext());
        if (!keyReady) {
            Toast.makeText(requireContext(), R.string.vault_setup_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        VaultSession.unlock(requireContext());
        Toast.makeText(requireContext(), R.string.vault_setup_success, Toast.LENGTH_SHORT).show();
        openDestination();
    }

    private String generateDataProtectionKey() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keyLength; i++) {
            builder.append(KEY_ALPHABET.charAt(random.nextInt(KEY_ALPHABET.length())));
        }
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < builder.length(); i++) {
            if (i > 0 && i % 4 == 0) formatted.append('-');
            formatted.append(builder.charAt(i));
        }
        return formatted.toString();
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.US);
    }

    private boolean isValidDataProtectionKey(String key) {
        String compact = key.replace("-", "");
        return key.matches("[A-Z0-9-]+") && compact.matches("[A-Z0-9]{8,32}");
    }

    private String parseDownloadedKey(String document) {
        if (document == null) return "";
        Matcher matcher = Pattern.compile("(?m)^\\s*([A-Za-z0-9-]{8,39})\\s*$").matcher(document);
        while (matcher.find()) {
            String candidate = normalizeKey(matcher.group(1));
            if (isValidDataProtectionKey(candidate)) return candidate;
        }
        return "";
    }

    private byte[] readAllBytes(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private void downloadKey() {
        String key = currentValidKey();
        if (key == null) return;
        keyDownloader.launch("KeyScan_Data_Protection_Key.txt");
    }

    private void copyKey() {
        String key = currentValidKey();
        if (key == null) return;
        SecureClipboard.copySensitive(requireContext(), "KeyScan", key, 30_000L);
        markKeyPreserved(key);
        Toast.makeText(requireContext(), R.string.vault_setup_copy_warning, Toast.LENGTH_LONG).show();
    }

    private String currentValidKey() {
        String key = normalizeKey(keyInput.getText().toString());
        if (!isValidDataProtectionKey(key)) {
            keyInput.setError(getString(R.string.vault_setup_key_generate_first));
            return null;
        }
        return key;
    }

    private String keyDocument(String key) {
        return getString(R.string.vault_setup_key_document, key);
    }

    private void markKeyPreserved(String key) {
        keyExported = true;
        exportedKey = key;
        keySavedCheck.setChecked(true);
        keySavedCheck.setText(R.string.vault_setup_key_preserved);
    }

    private void markKeyExported(String key, String message) {
        markKeyPreserved(key);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void openDestination() {
        Fragment fragment;
        String destination = getArguments() == null
                ? ""
                : getArguments().getString(ARG_DESTINATION, "");
        if (DEST_HOME.equals(destination)) fragment = new PrimaryNavigationFragment();
        else if (DEST_OTP.equals(destination)) fragment = new OtpAuthFragment();
        else if (DEST_VAULT.equals(destination)) fragment = new VaultFragment();
        else if (DEST_WEBDAV.equals(destination)) fragment = new SettingsFragment();
        else if (DEST_EXPORT.equals(destination)) fragment = new ExportFragment();
        else if (DEST_GENERIC_EXPORT.equals(destination)) fragment = new ExportDataFragment();
        else fragment = new PasswordForgeFragment();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private int dp(int value) {
        return SecurityUi.dp(this, value);
    }
}
