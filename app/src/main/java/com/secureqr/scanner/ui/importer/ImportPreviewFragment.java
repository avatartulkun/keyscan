package com.secureqr.scanner.ui.importer;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.importer.commit.ImportCommitter;
import com.secureqr.scanner.importer.commit.OtpImportCommitter;
import com.secureqr.scanner.importer.model.ImportedOtp;
import com.secureqr.scanner.importer.model.ImportedPassword;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.importer.session.ImportSessionManager;
import com.secureqr.scanner.utils.FragmentUi;

import java.util.ArrayList;

public class ImportPreviewFragment extends Fragment {
    private static final String ARG_SOURCE = "source";
    private static final String ARG_FORMAT = "format";
    private static final String ARG_SESSION_ID = "session_id";
    private ImportCommitter.ConflictStrategy conflictStrategy = ImportCommitter.ConflictStrategy.SKIP;

    public static ImportPreviewFragment newInstance(String sourceName, String format, String sessionId) {
        ImportPreviewFragment fragment = new ImportPreviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SOURCE, sourceName == null ? "" : sourceName);
        args.putString(ARG_FORMAT, format == null ? "" : format);
        args.putString(ARG_SESSION_ID, sessionId == null ? "" : sessionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_light));
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String source = getArguments() == null ? "" : getArguments().getString(ARG_SOURCE, "");
        String format = getArguments() == null ? "" : getArguments().getString(ARG_FORMAT, "");
        ImportSessionManager.ImportSession session = ImportSessionManager.get(sessionId());
        if (session == null) return expiredSessionView();
        ArrayList<ImportedPassword> passwords = new ArrayList<>(session.passwords());
        ArrayList<ImportedOtp> otps = new ArrayList<>(session.otps());

        root.addView(cardTitle(getString(R.string.import_preview_title)), wrap());
        root.addView(infoCard(source, format), wrap(12));
        root.addView(summaryCard(passwords.size(), otps.size()), wrap(12));
        if (!passwords.isEmpty()) root.addView(passwordListCard(passwords), wrap(12));
        if (!otps.isEmpty()) root.addView(otpListCard(otps), wrap(12));
        root.addView(noteCard(), wrap(12));

        Button conflict = new Button(requireContext());
        conflict.setText(R.string.import_conflict_handling);
        conflict.setOnClickListener(v -> showConflictDialog());
        root.addView(conflict, wrap(18));

        Button complete = new Button(requireContext());
        complete.setText(R.string.import_confirm_action);
        complete.setEnabled(!passwords.isEmpty() || !otps.isEmpty());
        complete.setOnClickListener(v -> {
            Runnable commit = () -> {
                if (!otps.isEmpty()) commitOtp(otps);
                else commitPasswords(passwords);
            };
            if (conflictStrategy == ImportCommitter.ConflictStrategy.OVERWRITE) {
                OperationModeGuard.requireEdit(this, commit);
            } else {
                commit.run();
            }
        });
        root.addView(complete, wrap(10));
        return scroll;
    }

    private LinearLayout infoCard(String source, String format) {
        LinearLayout card = card();
        card.addView(text(getString(R.string.import_source_value, source.isEmpty() ? getString(R.string.import_not_selected) : source), 15, R.color.text_main, false));
        card.addView(text(getString(R.string.import_format_value, emptyAs(format, getString(R.string.import_unknown_format))), 15, R.color.text_main, false), wrap(6));
        return card;
    }

    private LinearLayout summaryCard(int passwordCount, int otpCount) {
        LinearLayout card = card();
        card.addView(text(getString(R.string.import_statistics), 17, R.color.text_main, true));
        card.addView(text(getString(R.string.import_password_count, passwordCount), 14, R.color.text_main, false), wrap(8));
        if (otpCount > 0) card.addView(text(getString(R.string.import_otp_count, otpCount), 14, R.color.text_main, false), wrap(4));
        return card;
    }

    private LinearLayout otpListCard(ArrayList<ImportedOtp> otps) {
        LinearLayout card = card();
        card.addView(text(getString(R.string.import_otp_preview), 17, R.color.text_main, true));
        int count = Math.min(otps.size(), 20);
        for (int i = 0; i < count; i++) {
            ImportedOtp item = otps.get(i);
            card.addView(text(emptyAs(item.issuer, getString(R.string.import_unnamed_service)), 15, R.color.text_main, true), wrap(10));
            card.addView(text(emptyAs(item.account, getString(R.string.import_account_missing)), 13, R.color.text_secondary, false), wrap(2));
        }
        if (otps.size() > count) card.addView(text(getString(R.string.import_preview_limit, count), 13, R.color.text_secondary, false), wrap(10));
        return card;
    }

    private LinearLayout passwordListCard(ArrayList<ImportedPassword> passwords) {
        LinearLayout card = card();
        card.addView(text(getString(R.string.import_password_preview), 17, R.color.text_main, true));
        int count = Math.min(passwords.size(), 20);
        for (int i = 0; i < count; i++) {
            ImportedPassword item = passwords.get(i);
            String title = emptyAs(item.title, getString(R.string.import_unnamed_credential));
            String website = emptyAs(item.websiteDomain, getString(R.string.import_website_missing));
            String username = emptyAs(item.username, getString(R.string.import_account_missing));
            card.addView(text(title, 15, R.color.text_main, true), wrap(10));
            card.addView(text(website + " · " + username, 13, R.color.text_secondary, false), wrap(2));
        }
        if (passwords.size() > count) {
            card.addView(text(getString(R.string.import_preview_limit, count), 13, R.color.text_secondary, false), wrap(10));
        }
        return card;
    }

    private String sessionId() {
        return getArguments() == null ? "" : getArguments().getString(ARG_SESSION_ID, "");
    }

    private View expiredSessionView() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(24));
        root.addView(cardTitle(getString(R.string.import_session_expired)));
        root.addView(text(getString(R.string.import_session_expired_help), 15, R.color.text_secondary, false), wrap(12));
        Button back = new Button(requireContext());
        back.setText(R.string.import_return);
        back.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        root.addView(back, wrap(20));
        return root;
    }

    private String emptyAs(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private LinearLayout noteCard() {
        LinearLayout card = card();
        card.setBackgroundResource(R.drawable.bg_detail_secret);
        card.addView(text(getString(R.string.import_preview_secret_hidden), 14, R.color.text_main, false));
        return card;
    }

    private void showConflictDialog() {
        ImportConflictDialog dialog = new ImportConflictDialog();
        dialog.setListener(mode -> {
            conflictStrategy = toStrategy(mode);
            Toast.makeText(requireContext(), getString(R.string.import_conflict_mode, conflictModeLabel(mode)), Toast.LENGTH_SHORT).show();
        });
        dialog.show(getParentFragmentManager(), "import_conflict");
    }

    private void commitPasswords(ArrayList<ImportedPassword> passwords) {
        SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.import_password_auth), () ->
                new ImportCommitter(requireContext()).commitPasswords(passwords, conflictStrategy, result ->
                        FragmentUi.run(ImportPreviewFragment.this, () -> showResultAndClear(result))));
    }

    private void commitOtp(ArrayList<ImportedOtp> otps) {
        SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.import_otp_auth), () ->
                new OtpImportCommitter(requireContext()).commit(otps, conflictStrategy, result -> {
                    FragmentUi.run(ImportPreviewFragment.this, () -> showResultAndClear(result));
                }));
    }

    private void showResultAndClear(com.secureqr.scanner.importer.commit.ImportCommitResult result) {
        ImportSessionManager.clear(sessionId());
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, ImportResultFragment.newInstance(result.successCount, result.skipCount, result.failCount))
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroy() {
        ImportSessionManager.clear(sessionId());
        super.onDestroy();
    }

    private ImportCommitter.ConflictStrategy toStrategy(ImportConflictDialog.ConflictMode mode) {
        if (mode == ImportConflictDialog.ConflictMode.OVERWRITE) return ImportCommitter.ConflictStrategy.OVERWRITE;
        if (mode == ImportConflictDialog.ConflictMode.KEEP_BOTH) return ImportCommitter.ConflictStrategy.KEEP_BOTH;
        return ImportCommitter.ConflictStrategy.SKIP;
    }

    private String conflictModeLabel(ImportConflictDialog.ConflictMode mode) {
        if (mode == ImportConflictDialog.ConflictMode.OVERWRITE) return getString(R.string.import_conflict_overwrite);
        if (mode == ImportConflictDialog.ConflictMode.KEEP_BOTH) return getString(R.string.import_conflict_keep_both);
        return getString(R.string.import_conflict_skip);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        return card;
    }

    private TextView cardTitle(String value) {
        TextView title = new TextView(requireContext());
        title.setText(value);
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        title.setGravity(Gravity.START);
        return title;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (bold) text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
        return text;
    }

    private LinearLayout.LayoutParams wrap() {
        return wrap(0);
    }

    private LinearLayout.LayoutParams wrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
