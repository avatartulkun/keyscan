package com.secureqr.scanner.autofill;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

import com.secureqr.scanner.R;

public final class ChromeAutofillHelper {
    private static final String[] CHROME_PACKAGES = {
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary"
    };
    private static final String CONTENT_PROVIDER_NAME = ".AutofillThirdPartyModeContentProvider";
    private static final String THIRD_PARTY_MODE_COLUMN = "autofill_third_party_state";
    private static final String THIRD_PARTY_MODE_PATH = "autofill_third_party_mode";

    private ChromeAutofillHelper() {
    }

    public enum State {
        ENABLED,
        DISABLED,
        UNKNOWN
    }

    public static State readThirdPartyMode(Context context) {
        for (String packageName : CHROME_PACKAGES) {
            State state = readThirdPartyMode(context, packageName);
            if (state != State.UNKNOWN) return state;
        }
        return State.UNKNOWN;
    }

    public static void openChromeAutofillSettings(Context context) {
        Intent intent = new Intent(Intent.ACTION_APPLICATION_PREFERENCES);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addCategory(Intent.CATEGORY_APP_BROWSER);
        intent.addCategory(Intent.CATEGORY_PREFERENCE);
        String packageName = firstInstalledChromePackage(context);
        if (packageName != null) intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.autofill_chrome_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    public static String stateLabel(Context context) {
        State state = readThirdPartyMode(context);
        if (state == State.ENABLED) return context.getString(R.string.autofill_chrome_enabled);
        if (state == State.DISABLED) return context.getString(R.string.autofill_chrome_disabled);
        return context.getString(R.string.autofill_chrome_unknown);
    }

    private static State readThirdPartyMode(Context context, String chromePackage) {
        if (!isInstalled(context, chromePackage)) return State.UNKNOWN;
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(chromePackage + CONTENT_PROVIDER_NAME)
                .path(THIRD_PARTY_MODE_PATH)
                .build();
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{THIRD_PARTY_MODE_COLUMN},
                null,
                null,
                null)) {
            if (cursor == null || !cursor.moveToFirst()) return State.UNKNOWN;
            int index = cursor.getColumnIndex(THIRD_PARTY_MODE_COLUMN);
            if (index < 0) return State.UNKNOWN;
            return cursor.getInt(index) == 1 ? State.ENABLED : State.DISABLED;
        } catch (Exception ignored) {
            return State.UNKNOWN;
        }
    }

    private static String firstInstalledChromePackage(Context context) {
        for (String packageName : CHROME_PACKAGES) {
            if (isInstalled(context, packageName)) return packageName;
        }
        return null;
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
