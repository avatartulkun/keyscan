package com.secureqr.scanner.clipboard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.secureqr.scanner.MainActivity;
import com.secureqr.scanner.R;

public class ClipboardImportActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_CLIPBOARD_IMPORT = "open_clipboard_import";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String text = readSharedText(getIntent());
        ClipboardSensitiveClassifier.Result result = ClipboardSensitiveClassifier.classify(this, text);
        if (!result.sensitive || !ClipboardImportSession.begin(text, result, true)) {
            Toast.makeText(this, R.string.clipboard_no_sensitive_content, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_CLIPBOARD_IMPORT, true);
        startActivity(intent);
        finish();
    }

    private String readSharedText(Intent intent) {
        if (intent == null) return "";
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        return text == null ? "" : text.toString();
    }
}
