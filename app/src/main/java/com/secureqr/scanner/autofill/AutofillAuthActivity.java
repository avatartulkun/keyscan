package com.secureqr.scanner.autofill;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.service.autofill.Dataset;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.secureqr.scanner.MainActivity;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.repository.PasswordGenerationRepository;
import com.secureqr.scanner.security.VaultSession;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.PinLockHelper;
import com.secureqr.scanner.utils.PasswordGeneratorEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutofillAuthActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText passwordInput;
    private long credentialId;
    private AutofillId usernameId;
    private AutofillId passwordId;
    private AutofillId newPasswordId;
    private AutofillId confirmPasswordId;
    private String requestPackage = "";
    private String requestDomain = "";
    private boolean bindSelectedApp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Intent intent = getIntent();
        requestPackage = intent.getStringExtra(KeyScanAutofillService.EXTRA_PACKAGE_NAME);
        requestDomain = intent.getStringExtra(KeyScanAutofillService.EXTRA_DOMAIN);
        if (KeyScanAutofillService.MODE_GENERATE.equals(intent.getStringExtra(KeyScanAutofillService.EXTRA_MODE))) {
            newPasswordId = intent.getParcelableExtra(KeyScanAutofillService.EXTRA_NEW_PASSWORD_ID);
            confirmPasswordId = intent.getParcelableExtra(KeyScanAutofillService.EXTRA_CONFIRM_PASSWORD_ID);
            showGeneratePassword(intent.getIntExtra(KeyScanAutofillService.EXTRA_MAX_LENGTH, -1));
            return;
        }
        if (KeyScanAutofillService.MODE_SEARCH.equals(intent.getStringExtra(KeyScanAutofillService.EXTRA_MODE))) {
            usernameId = intent.getParcelableExtra(KeyScanAutofillService.EXTRA_USERNAME_ID);
            passwordId = intent.getParcelableExtra(KeyScanAutofillService.EXTRA_PASSWORD_ID);
            requestPackage = intent.getStringExtra(KeyScanAutofillService.EXTRA_PACKAGE_NAME);
            requestDomain = intent.getStringExtra(KeyScanAutofillService.EXTRA_DOMAIN);
            if (VaultSession.isUnlocked(this)) showSearchVault(intent);
            else showSearchAuthForm(intent);
            return;
        }
        credentialId = intent.getLongExtra(KeyScanAutofillService.EXTRA_CREDENTIAL_ID, 0);
        usernameId = intent.getParcelableExtra(KeyScanAutofillService.EXTRA_USERNAME_ID);
        passwordId = intent.getParcelableExtra(KeyScanAutofillService.EXTRA_PASSWORD_ID);
        if (VaultSession.isUnlocked(this)) {
            fillCredential();
            return;
        }
        showAuthForm(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showSearchVault(Intent intent) {
        LinearLayout card = baseCard();
        addTitle(card, getString(R.string.autofill_no_match_title));
        addMessage(card, getString(R.string.autofill_no_match_message));
        executor.execute(() -> {
            java.util.List<PasswordEntry> entries = new PasswordAutofillRepository(this).getAll();
            runOnUiThread(() -> {
                int count = Math.min(entries.size(), 12);
                for (int i = 0; i < count; i++) {
                    PasswordEntry entry = entries.get(i);
                    Button choice = secondaryButton(AutofillCredentialMatcher.displayTitle(entry) + "\n" + AutofillCredentialMatcher.displayUsername(entry));
                    choice.setAllCaps(false);
                    choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    choice.setOnClickListener(v -> {
                        credentialId = entry.id;
                        bindSelectedApp = true;
                        showAuthForm(intent.putExtra(KeyScanAutofillService.EXTRA_CREDENTIAL_ID, entry.id)
                                .putExtra(KeyScanAutofillService.EXTRA_TITLE, AutofillCredentialMatcher.displayTitle(entry))
                                .putExtra(KeyScanAutofillService.EXTRA_USERNAME, AutofillCredentialMatcher.displayUsername(entry)));
                    });
                    card.addView(choice, matchWrap(8));
                }
                Button open = primaryButton(getString(R.string.autofill_open_vault));
                open.setOnClickListener(v -> {
                    startActivity(new Intent(this, MainActivity.class));
                    setResult(RESULT_CANCELED);
                    finish();
                });
                card.addView(open, matchWrap(14));
            });
        });
        setContentView(wrap(card));
    }

    private void showSearchAuthForm(Intent intent) {
        LinearLayout card = baseCard();
        addTitle(card, getString(R.string.autofill_unlock_required_title));
        addMessage(card, getString(R.string.autofill_unlock_required_message));
        if (!PinLockHelper.isConfigured(this)) {
            addRisk(card, getString(R.string.autofill_setup_required));
            Button open = primaryButton(getString(R.string.autofill_open_settings));
            open.setOnClickListener(v -> {
                startActivity(new Intent(this, AutofillGuideActivity.class));
                setResult(RESULT_CANCELED);
                finish();
            });
            card.addView(open, matchWrap(0, 14));
            setContentView(wrap(card));
            return;
        }
        EditText pinInput = new EditText(this);
        pinInput.setHint(R.string.autofill_auth_hint);
        pinInput.setSingleLine(true);
        pinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pinInput.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        pinInput.setBackgroundResource(R.drawable.bg_edit_text);
        pinInput.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        pinInput.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        pinInput.setPadding(dp(12), 0, dp(12), 0);
        card.addView(pinInput, matchFixed(dp(52), 14));
        if (BiometricUnlockHelper.isEnabled(this)) {
            Button biometric = secondaryButton(getString(R.string.biometric_unlock_button));
            biometric.setOnClickListener(v -> BiometricUnlockHelper.prompt(this, () -> {
                VaultSession.unlock(this);
                showSearchVault(intent);
            }, null));
            card.addView(biometric, matchWrap(0, 10));
        }
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = secondaryButton(getString(R.string.cancel));
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        Button confirm = primaryButton(getString(R.string.confirm));
        confirm.setOnClickListener(v -> {
            if (!PinLockHelper.verifyPin(this, pinInput.getText().toString().trim())) {
                PinLockHelper.recordFailedAttempt(this);
                pinInput.setError(getString(R.string.autofill_auth_failed));
                Toast.makeText(this, R.string.autofill_auth_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            PinLockHelper.clearFailedAttempts(this);
            VaultSession.unlock(this);
            showSearchVault(intent);
        });
        buttons.addView(cancel, weightWrap(1, 0));
        LinearLayout.LayoutParams confirmParams = weightWrap(1, 0);
        confirmParams.leftMargin = dp(10);
        buttons.addView(confirm, confirmParams);
        card.addView(buttons, matchWrap(0, 14));
        setContentView(wrap(card));
    }

    private void showAuthForm(Intent intent) {
        LinearLayout card = baseCard();
        addTitle(card, getString(R.string.autofill_auth_title));
        String title = intent.getStringExtra(KeyScanAutofillService.EXTRA_TITLE);
        String username = intent.getStringExtra(KeyScanAutofillService.EXTRA_USERNAME);
        String domain = intent.getStringExtra(KeyScanAutofillService.EXTRA_DOMAIN);
        String scheme = intent.getStringExtra(KeyScanAutofillService.EXTRA_SCHEME);
        String packageName = intent.getStringExtra(KeyScanAutofillService.EXTRA_PACKAGE_NAME);
        addMessage(card, getString(R.string.autofill_auth_message,
                AutofillCredentialMatcher.nonEmpty(title, domain, packageName),
                AutofillCredentialMatcher.nonEmpty(username, getString(R.string.autofill_unknown_account))));
        if (TextUtils.isEmpty(domain)) {
            addRisk(card, getString(R.string.autofill_app_only_warning));
        } else if (!TextUtils.isEmpty(scheme) && !"https".equalsIgnoreCase(scheme)) {
            addRisk(card, getString(R.string.autofill_non_https_warning));
        }
        if (!PinLockHelper.isConfigured(this)) {
            addRisk(card, getString(R.string.autofill_setup_required));
            Button open = primaryButton(getString(R.string.autofill_open_settings));
            open.setOnClickListener(v -> {
                startActivity(new Intent(this, AutofillGuideActivity.class));
                setResult(RESULT_CANCELED);
                finish();
            });
            card.addView(open, matchWrap(0, 14));
            setContentView(wrap(card));
            return;
        }
        passwordInput = new EditText(this);
        passwordInput.setHint(R.string.autofill_auth_hint);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        passwordInput.setBackgroundResource(R.drawable.bg_edit_text);
        passwordInput.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        passwordInput.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        passwordInput.setPadding(dp(12), 0, dp(12), 0);
        card.addView(passwordInput, matchFixed(dp(52), 14));
        if (BiometricUnlockHelper.isEnabled(this)) {
            Button biometric = secondaryButton(getString(R.string.biometric_unlock_button));
            biometric.setOnClickListener(v -> BiometricUnlockHelper.prompt(this, () -> {
                VaultSession.unlock(this);
                fillCredential();
            }, null));
            card.addView(biometric, matchWrap(0, 10));
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = secondaryButton(getString(R.string.cancel));
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        Button confirm = primaryButton(getString(R.string.confirm));
        confirm.setOnClickListener(v -> verifyAndFill());
        buttons.addView(cancel, weightWrap(1, 0));
        LinearLayout.LayoutParams confirmParams = weightWrap(1, 0);
        confirmParams.leftMargin = dp(10);
        buttons.addView(confirm, confirmParams);
        card.addView(buttons, matchWrap(0, 14));
        setContentView(wrap(card));
    }

    private void showGeneratePassword(int maxLength) {
        LinearLayout card = baseCard();
        addTitle(card, getString(R.string.autofill_generate_password_title));
        addMessage(card, getString(R.string.autofill_generate_password_message));
        TextView passwordView = new TextView(this);
        passwordView.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        passwordView.setTextSize(18);
        passwordView.setTypeface(passwordView.getTypeface(), android.graphics.Typeface.BOLD);
        passwordView.setBackgroundResource(R.drawable.bg_edit_text);
        passwordView.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.addView(passwordView, matchWrap(12));

        final int[] mode = {0};
        final int[] length = {maxLength > 0 ? Math.min(16, maxLength) : 16};
        final String[] password = {""};
        Runnable regenerate = () -> {
            PasswordGeneratorEngine.Options options = new PasswordGeneratorEngine.Options();
            options.length = Math.max(4, length[0]);
            options.includeUpper = mode[0] != 2;
            options.includeLower = mode[0] != 2;
            options.includeDigits = true;
            options.includeSymbols = mode[0] == 0;
            options.excludeZeroO = true;
            options.excludeOneI = true;
            options.excludeLowerL = true;
            password[0] = PasswordGeneratorEngine.generate(options);
            passwordView.setText(password[0]);
        };
        regenerate.run();

        Button recommended = secondaryButton(getString(R.string.autofill_rule_recommended));
        Button compatible = secondaryButton(getString(R.string.autofill_rule_compatible));
        Button pin = secondaryButton(getString(R.string.autofill_rule_pin));
        recommended.setOnClickListener(v -> { mode[0] = 0; regenerate.run(); });
        compatible.setOnClickListener(v -> { mode[0] = 1; regenerate.run(); });
        pin.setOnClickListener(v -> { mode[0] = 2; length[0] = Math.min(length[0], 8); regenerate.run(); });
        card.addView(recommended, matchWrap(10));
        card.addView(compatible, matchWrap(8));
        card.addView(pin, matchWrap(8));

        LinearLayout lengthRow = new LinearLayout(this);
        lengthRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView len = bodyText(getString(R.string.autofill_password_length, length[0]));
        Button minus = secondaryButton("-");
        Button plus = secondaryButton("+");
        minus.setOnClickListener(v -> { if (length[0] > 4) { length[0]--; len.setText(getString(R.string.autofill_password_length, length[0])); regenerate.run(); }});
        plus.setOnClickListener(v -> { if (length[0] < 32 && (maxLength <= 0 || length[0] < maxLength)) { length[0]++; len.setText(getString(R.string.autofill_password_length, length[0])); regenerate.run(); }});
        lengthRow.addView(len, weightWrap(1, 0));
        lengthRow.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        lengthRow.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        card.addView(lengthRow, matchWrap(10));

        Button refresh = secondaryButton(getString(R.string.autofill_regenerate));
        refresh.setOnClickListener(v -> regenerate.run());
        card.addView(refresh, matchWrap(8));
        Button use = primaryButton(getString(R.string.autofill_use_password));
        use.setOnClickListener(v -> fillGeneratedPassword(password[0]));
        card.addView(use, matchWrap(12));
        setContentView(wrap(card));
    }

    private void fillGeneratedPassword(String password) {
        Dataset.Builder builder = new Dataset.Builder();
        if (newPasswordId != null) builder.setValue(newPasswordId, AutofillValue.forText(password));
        if (confirmPasswordId != null) builder.setValue(confirmPasswordId, AutofillValue.forText(password));
        Intent result = new Intent();
        result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build());
        PasswordGenerationRepository.getInstance(this).recordRegistrationFill(password,
                TextUtils.isEmpty(requestDomain) ? requestPackage : requestDomain,
                getIntent().getStringExtra(KeyScanAutofillService.EXTRA_USERNAME));
        setResult(RESULT_OK, result);
        finish();
    }

    private void verifyAndFill() {
        String password = passwordInput.getText().toString().trim();
        if (!PinLockHelper.verifyPin(this, password)) {
            PinLockHelper.recordFailedAttempt(this);
            passwordInput.setError(getString(R.string.autofill_auth_failed));
            Toast.makeText(this, R.string.autofill_auth_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        PinLockHelper.clearFailedAttempts(this);
        VaultSession.unlock(this);
        fillCredential();
    }

    private void fillCredential() {
        executor.execute(() -> {
            PasswordAutofillRepository repository = new PasswordAutofillRepository(this);
            PasswordEntry entry = repository.findById(credentialId);
            if (entry == null || TextUtils.isEmpty(entry.password)) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.autofill_failed, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                    finish();
                });
                return;
            }
            repository.updateLastUsed(entry.id, System.currentTimeMillis());
            if (bindSelectedApp) {
                repository.bindApp(entry.id, requestPackage, appLabel(requestPackage));
            }
            Dataset.Builder builder = new Dataset.Builder(createPresentation(entry));
            if (usernameId != null) {
                builder.setValue(usernameId, AutofillValue.forText(AutofillCredentialMatcher.displayUsername(entry)));
            }
            if (passwordId != null) {
                builder.setValue(passwordId, AutofillValue.forText(entry.password));
            }
            Intent result = new Intent();
            result.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build());
            runOnUiThread(() -> {
                setResult(RESULT_OK, result);
                finish();
            });
        });
    }

    private RemoteViews createPresentation(PasswordEntry entry) {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.item_autofill_credential);
        views.setTextViewText(R.id.tv_autofill_title, AutofillCredentialMatcher.displayTitle(entry));
        views.setTextViewText(R.id.tv_autofill_subtitle, AutofillCredentialMatcher.displayUsername(entry));
        views.setTextViewText(R.id.tv_autofill_badge, getString(R.string.autofill_candidate_badge));
        return views;
    }

    private ScrollView wrap(LinearLayout card) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_light));
        LinearLayout outer = new LinearLayout(this);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(dp(18), dp(18), dp(18), dp(18));
        outer.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.MATCH_PARENT));
        return scrollView;
    }

    private LinearLayout baseCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        return card;
    }

    private void addTitle(LinearLayout parent, String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        parent.addView(title, matchWrap(0, 0));
    }

    private void addMessage(LinearLayout parent, String text) {
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        message.setTextSize(14);
        message.setLineSpacing(0, 1.15f);
        parent.addView(message, matchWrap(0, 10));
    }

    private void addRisk(LinearLayout parent, String text) {
        TextView risk = new TextView(this);
        risk.setText(text);
        risk.setTextColor(ContextCompat.getColor(this, R.color.danger));
        risk.setTextSize(13);
        parent.addView(risk, matchWrap(0, 8));
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        view.setTextSize(14);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        return matchWrap(0, topMargin);
    }

    private LinearLayout.LayoutParams matchWrap(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : height
        );
        params.topMargin = dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams matchFixed(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
        params.topMargin = dp(topMargin);
        return params;
    }

    private LinearLayout.LayoutParams weightWrap(float weight, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.topMargin = dp(topMargin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String appLabel(String packageName) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? "" : label.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
