package com.secureqr.scanner.autofill;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.secureqr.scanner.R;

public class AutofillGuideActivity extends AppCompatActivity {
    private TextView status;
    private TextView chromeStatus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private ScrollView buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_light));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));

        TextView title = new TextView(this);
        title.setText(R.string.autofill_guide_title);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap(0));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams cardParams = matchWrap(18);
        root.addView(card, cardParams);

        status = bodyText("");
        card.addView(status, matchWrap(0));
        chromeStatus = bodyText("");
        card.addView(chromeStatus, matchWrap(6));
        TextView guide = bodyText(getString(R.string.autofill_guide_message));
        card.addView(guide, matchWrap(12));

        Button openSettings = new Button(this);
        openSettings.setText(R.string.autofill_open_settings);
        openSettings.setOnClickListener(v -> openAutofillSettings());
        card.addView(openSettings, matchWrap(14));

        Button openChrome = new Button(this);
        openChrome.setText(R.string.autofill_open_chrome_settings);
        openChrome.setOnClickListener(v -> ChromeAutofillHelper.openChromeAutofillSettings(this));
        card.addView(openChrome, matchWrap(8));

        Button diagnostics = new Button(this);
        diagnostics.setText(R.string.autofill_view_diagnostics);
        diagnostics.setOnClickListener(v -> showDiagnostics());
        card.addView(diagnostics, matchWrap(8));

        Button bankDiag = new Button(this);
        bankDiag.setText(R.string.autofill_bank_diagnostics);
        bankDiag.setOnClickListener(v -> showBankAppDiagnosis());
        card.addView(bankDiag, matchWrap(8));

        Button close = new Button(this);
        close.setText(R.string.back);
        close.setOnClickListener(v -> finish());
        root.addView(close, matchWrap(18));

        scrollView.addView(root);
        return scrollView;
    }

    private void updateStatus() {
        if (status == null) return;
        status.setText(getString(isAutofillEnabled() ? R.string.autofill_enabled : R.string.autofill_disabled));
        status.setTextColor(ContextCompat.getColor(this, isAutofillEnabled() ? R.color.accent : R.color.danger));
        if (chromeStatus != null) {
            ChromeAutofillHelper.State state = ChromeAutofillHelper.readThirdPartyMode(this);
            chromeStatus.setText(getString(R.string.autofill_chrome_status, ChromeAutofillHelper.stateLabel(this)));
            int color = state == ChromeAutofillHelper.State.ENABLED ? R.color.accent
                    : state == ChromeAutofillHelper.State.DISABLED ? R.color.danger
                    : R.color.text_secondary;
            chromeStatus.setTextColor(ContextCompat.getColor(this, color));
        }
    }

    private void openAutofillSettings() {
        ComponentName componentName = new ComponentName(this, KeyScanAutofillService.class);
        Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(Uri.parse("package:" + getPackageName()))
                .putExtra("android.provider.extra.AUTOFILL_SERVICE", componentName.flattenToString());
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void showBankAppDiagnosis() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(10), dp(18), dp(0));

        EditText packageInput = new EditText(this);
        packageInput.setHint(R.string.autofill_bank_package_hint);
        packageInput.setSingleLine(true);
        packageInput.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        packageInput.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        content.addView(packageInput, matchWrap(0));

        TextView hint = bodyText(getString(R.string.autofill_bank_diagnostics_hint));
        content.addView(hint, matchWrap(10));

        new AlertDialog.Builder(this)
                .setTitle(R.string.autofill_bank_diagnostics)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.autofill_start_diagnostics, (dialog, which) -> {
                    String packageName = packageInput.getText() == null ? "" : packageInput.getText().toString().trim();
                    StringBuilder result = new StringBuilder();
                    result.append(getString(isAutofillEnabled() ? R.string.autofill_keyscan_enabled : R.string.autofill_keyscan_disabled));
                    result.append("\n");
                    if (packageName.isEmpty()) {
                        result.append(getString(R.string.autofill_target_missing));
                    } else {
                        result.append(getString(R.string.autofill_target_package, packageName)).append('\n');
                        result.append(getString(isInstalled(packageName) ? R.string.autofill_target_installed : R.string.autofill_target_not_installed));
                    }
                    result.append("\n\n").append(getString(R.string.autofill_common_reasons)).append('\n');
                    result.append(getString(R.string.autofill_reason_hints)).append('\n');
                    result.append(getString(R.string.autofill_reason_secure_keyboard)).append('\n');
                    result.append(getString(R.string.autofill_reason_webview)).append('\n');
                    result.append(getString(R.string.autofill_reason_service)).append('\n');
                    result.append(getString(R.string.autofill_reason_scenario));
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.autofill_diagnostics_result)
                            .setMessage(result.toString())
                            .setPositiveButton(R.string.confirm, null)
                            .show();
                })
                .show();
    }

    private void showDiagnostics() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.autofill_diagnostics_title)
                .setMessage(AutofillDiagnostics.format(this))
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isAutofillEnabled() {
        ComponentName componentName = new ComponentName(this, KeyScanAutofillService.class);
        String enabled = Settings.Secure.getString(getContentResolver(), "autofill_service");
        return componentName.flattenToString().equals(enabled);
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        view.setTextSize(14);
        view.setGravity(Gravity.START);
        view.setLineSpacing(0, 1.18f);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMargin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
