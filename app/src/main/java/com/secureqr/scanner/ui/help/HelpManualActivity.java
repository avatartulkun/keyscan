package com.secureqr.scanner.ui.help;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.secureqr.scanner.utils.LocaleHelper;

public class HelpManualActivity extends AppCompatActivity {
    public static final String EXTRA_SECTION = "help_section";
    private static final String HELP_URL = "file:///android_asset/help/manual.html";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WebView web = new WebView(this);
        setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("mailto:")) {
                    startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url)));
                    return true;
                }
                if (url.startsWith("https://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });
        String language = LocaleHelper.currentLanguage(this);
        if (language == null || "system".equals(language)) {
            language = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        }
        String section = getIntent().getStringExtra(EXTRA_SECTION);
        String anchor = section == null || section.trim().isEmpty() ? "" : "#" + Uri.encode(section.trim());
        web.loadUrl(HELP_URL + "?lang=" + Uri.encode(language) + anchor);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
