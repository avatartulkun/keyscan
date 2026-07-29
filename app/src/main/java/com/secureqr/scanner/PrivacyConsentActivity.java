package com.secureqr.scanner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.secureqr.scanner.utils.LocaleHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PrivacyConsentActivity extends AppCompatActivity {
    private static final String PREFS = "keyscan_privacy_consent";
    private static final String KEY_ACCEPTED_VERSION = "accepted_policy_version";
    private static final String EXTRA_REVIEW_ONLY = "review_only";
    public static final int CURRENT_POLICY_VERSION = 1;

    public static boolean hasAcceptedCurrentPolicy(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_POLICY_VERSION;
    }

    public static Intent createConsentIntent(Context context) {
        return new Intent(context, PrivacyConsentActivity.class);
    }

    public static Intent createReviewIntent(Context context) {
        return new Intent(context, PrivacyConsentActivity.class)
                .putExtra(EXTRA_REVIEW_ONLY, true);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.apply(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String mode = getSharedPreferences("secureqr_settings", MODE_PRIVATE)
                .getString("theme_mode", "auto");
        AppCompatDelegate.setDefaultNightMode(MainActivity.toDelegateMode(mode));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_consent);

        boolean reviewOnly = getIntent().getBooleanExtra(EXTRA_REVIEW_ONLY, false);
        TextView policyBody = findViewById(R.id.tv_privacy_policy_body);
        policyBody.setText(readPolicyText());

        Button decline = findViewById(R.id.btn_privacy_decline);
        Button accept = findViewById(R.id.btn_privacy_accept);
        if (reviewOnly) {
            decline.setVisibility(View.GONE);
            accept.setText(R.string.privacy_policy_close);
            accept.setOnClickListener(v -> finish());
        } else {
            decline.setOnClickListener(v -> {
                setResult(RESULT_CANCELED);
                finishAffinity();
            });
            accept.setOnClickListener(v -> acceptAndContinue());
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (reviewOnly) {
                    finish();
                } else {
                    setResult(RESULT_CANCELED);
                    finishAffinity();
                }
            }
        });
    }

    private void acceptAndContinue() {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.edit()
                .putInt(KEY_ACCEPTED_VERSION, CURRENT_POLICY_VERSION)
                .putLong("accepted_at", System.currentTimeMillis())
                .apply();
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String readPolicyText() {
        try (InputStream input = getResources().openRawResource(
                R.raw.keyscan_user_agreement_privacy_policy);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return getString(R.string.privacy_policy_load_error);
        }
    }
}
