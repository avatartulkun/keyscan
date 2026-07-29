package com.secureqr.scanner.ui.importer;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import com.secureqr.scanner.importer.model.ImportedPassword;
import com.secureqr.scanner.importer.model.ImportedOtp;
import com.secureqr.scanner.importer.parser.BitwardenJsonImporter;
import com.secureqr.scanner.importer.parser.CsvPasswordImporter;
import com.secureqr.scanner.importer.session.ImportSessionManager;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.FragmentUi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImportDataFragment extends Fragment {
    private static final long MAX_IMPORT_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_IMPORT_RECORDS = 10000;
    private final ActivityResultLauncher<String> filePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onFilePicked);

    private TextView stateText;
    private TextView formatText;
    private TextView sourceText;
    private Button previewButton;
    private Uri selectedUri;
    private String selectedName = "";
    private String detectedFormat = "";
    private ImportWorkflowState state = ImportWorkflowState.IDLE;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_light));
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton home = new ImageButton(requireContext());
        home.setImageResource(R.drawable.ic_home_24);
        home.setBackgroundColor(0x00000000);
        home.setContentDescription(getString(R.string.transfer_home_desc));
        home.setOnClickListener(v -> NavigationHelper.openHome(this));
        top.addView(home, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text(getString(R.string.import_password_book), 26, R.color.text_main, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = dp(8);
        top.addView(title, titleParams);
        root.addView(top, matchWrap(0));

        root.addView(infoCard(), matchWrap(16));
        root.addView(formatCard(), matchWrap(12));
        root.addView(stateCard(), matchWrap(12));

        Button choose = new Button(requireContext());
        choose.setText(R.string.import_choose_file);
        choose.setOnClickListener(v -> filePicker.launch("*/*"));
        root.addView(choose, matchWrap(18));

        previewButton = new Button(requireContext());
        previewButton.setText(R.string.import_continue_preview);
        previewButton.setEnabled(false);
        previewButton.setOnClickListener(v -> openPreview());
        root.addView(previewButton, matchWrap(10));
        return scroll;
    }

    private LinearLayout infoCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.import_intro), 15, R.color.text_main, true));
        card.addView(text(getString(R.string.import_supported_intro), 13, R.color.text_secondary, false), matchWrap(8));
        return card;
    }

    private LinearLayout formatCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.import_supported_formats), 17, R.color.text_main, true));
        card.addView(text(getString(R.string.import_password_formats), 14, R.color.text_main, false), matchWrap(8));
        card.addView(text(getString(R.string.import_not_supported_note), 13, R.color.text_secondary, false), matchWrap(8));
        return card;
    }

    private LinearLayout stateCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.import_status_title), 17, R.color.text_main, true));
        stateText = text("IDLE", 16, R.color.text_secondary, false);
        sourceText = text(getString(R.string.import_source_none), 14, R.color.text_main, false);
        formatText = text(getString(R.string.import_format_unknown), 14, R.color.text_main, false);
        card.addView(sourceText, matchWrap(8));
        card.addView(formatText, matchWrap(4));
        card.addView(stateText, matchWrap(8));
        return card;
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.import_no_file_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        selectedUri = uri;
        selectedName = resolveDisplayName(uri);
        detectedFormat = detectFormat(selectedName, requireContext().getContentResolver().getType(uri));
        updateState(ImportWorkflowState.FILE_SELECTED);
        updateState(ImportWorkflowState.FORMAT_DETECTED);
        sourceText.setText(getString(R.string.import_source_line, selectedName.isEmpty() ? getString(R.string.import_unknown_file) : selectedName));
        formatText.setText(getString(R.string.import_format_line, detectedFormat));
        previewButton.setEnabled(true);
        Toast.makeText(requireContext(), R.string.import_file_selected, Toast.LENGTH_SHORT).show();
    }

    private void openPreview() {
        if (selectedUri == null) {
            Toast.makeText(requireContext(), R.string.import_choose_first, Toast.LENGTH_SHORT).show();
            return;
        }
        long size = resolveSize(selectedUri);
        if (size > MAX_IMPORT_BYTES) {
            Toast.makeText(requireContext(), R.string.import_file_too_large, Toast.LENGTH_SHORT).show();
            return;
        }
        previewButton.setEnabled(false);
        importExecutor.execute(() -> parseInBackground(selectedUri, selectedName, detectedFormat));
    }

    private void parseInBackground(Uri uri, String name, String format) {
        try {
            List<ImportedPassword> passwords = new ArrayList<>();
            List<ImportedOtp> otps = new ArrayList<>();
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Missing selected file");
                if ("CSV".equals(format)) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                        passwords = new CsvPasswordImporter().parse(reader);
                    }
                } else if ("Bitwarden JSON".equals(format)) {
                    passwords = new BitwardenJsonImporter().parse(input);
                } else {
                    throw new IllegalArgumentException("Unsupported format");
                }
            }
            if (passwords.size() + otps.size() > MAX_IMPORT_RECORDS) throw new IllegalArgumentException("Too many records");
            List<ImportedPassword> parsedPasswords = passwords;
            List<ImportedOtp> parsedOtps = otps;
            FragmentUi.run(this, () -> showPreview(name, format, parsedPasswords, parsedOtps));
        } catch (Exception ignored) {
            FragmentUi.run(this, () -> {
                previewButton.setEnabled(true);
                Toast.makeText(requireContext(), R.string.import_unrecognized, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showPreview(String name, String format, List<ImportedPassword> passwords, List<ImportedOtp> otps) {
        if (!isAdded()) {
            ImportSessionManager.wipe(passwords, otps);
            return;
        }
        updateState(ImportWorkflowState.PREVIEW_READY);
        String sessionId = ImportSessionManager.create(passwords, otps);
        ImportSessionManager.wipe(passwords, otps);
        ImportPreviewFragment fragment = ImportPreviewFragment.newInstance(name, format, sessionId);
        getParentFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).addToBackStack(null).commit();
    }

    private String resolveDisplayName(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
    }

    private long resolveSize(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) { }
        return -1L;
    }

    @Override
    public void onDestroy() {
        importExecutor.shutdownNow();
        super.onDestroy();
    }

    private String detectFormat(String name, String mime) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".csv") || "text/csv".equals(mime)) return "CSV";
        if (lower.endsWith(".json") || "application/json".equals(mime)) return "Bitwarden JSON";
        return getString(R.string.import_unknown_format);
    }

    private void updateState(ImportWorkflowState next) {
        state = next;
        stateText.setText(getString(R.string.import_state_line, state.name()));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        return card;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (bold) text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
        return text;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
