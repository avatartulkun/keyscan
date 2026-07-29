package com.secureqr.scanner.ui.password;

import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordHistory;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.utils.FragmentUi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PasswordHistoryFragment extends Fragment {
    private static final String ARG_ENTRY_ID = "entry_id";
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private PasswordRepository repository;
    private LinearLayout list;
    private PasswordEntry entry;

    public static PasswordHistoryFragment newInstance(long entryId) {
        PasswordHistoryFragment fragment = new PasswordHistoryFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_ENTRY_ID, entryId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        repository = PasswordRepository.getInstance(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_light));
        root.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        ImageButton back = new ImageButton(requireContext());
        back.setImageResource(R.drawable.ic_arrow_back_24);
        back.setBackgroundColor(0x00000000);
        back.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text(getString(R.string.password_history_title), 20, true, R.color.text_main);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        SpaceShim space = new SpaceShim(requireContext());
        top.addView(space, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        TextView notice = text(getString(R.string.password_history_local_notice), 13, false, R.color.text_secondary);
        notice.setBackgroundResource(R.drawable.bg_icon_action);
        notice.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(notice, topWrap(12));

        ScrollView scroll = new ScrollView(requireContext());
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        load();
        return root;
    }

    private void load() {
        long entryId = getArguments() == null ? 0 : getArguments().getLong(ARG_ENTRY_ID);
        repository.getEntry(entryId, loaded -> FragmentUi.run(this, () -> {
            entry = loaded;
            if (entry == null) {
                renderEmpty(getString(R.string.password_history_entry_missing));
                return;
            }
            repository.getPasswordHistory(entry.itemId, histories -> FragmentUi.run(this, () -> render(histories)));
        }));
    }

    private void render(List<PasswordHistory> histories) {
        list.removeAllViews();
        if (histories == null || histories.isEmpty()) {
            renderEmpty(getString(R.string.password_history_empty));
            return;
        }
        for (PasswordHistory history : histories) list.addView(historyCard(history), topWrap(10));
    }

    private void renderEmpty(String message) {
        list.removeAllViews();
        TextView empty = text(message, 15, true, R.color.text_secondary);
        empty.setGravity(Gravity.CENTER);
        list.addView(empty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)));
    }

    private View historyCard(PasswordHistory history) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(text(format.format(new Date(history.createdAt)), 15, true, R.color.text_main));
        TextView password = text(mask(history.oldPassword), 18, true, R.color.text_main);
        card.addView(password, topWrap(14));
        card.addView(text(getString(R.string.password_history_source_format, sourceName(history.source)), 12, false, R.color.text_secondary), topWrap(8));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        final boolean[] visible = {false};
        final ImageButton[] revealButton = new ImageButton[1];
        revealButton[0] = action(R.drawable.ic_visibility_off_24, getString(R.string.password_history_action_view), 0xFF1A73E8, 0xFFEAF2FF, () -> {
            if (visible[0]) { visible[0] = false; password.setText(mask(history.oldPassword)); revealButton[0].setImageResource(R.drawable.ic_visibility_off_24); return; }
            SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_view_historical), () -> { visible[0] = true; password.setTransformationMethod(null); password.setText(history.oldPassword); revealButton[0].setImageResource(R.drawable.ic_visibility_24); });
        });
        actions.addView(revealButton[0]);
        actions.addView(action(R.drawable.ic_content_copy_24, getString(R.string.common_action_copy), 0xFF1A73E8, 0xFFEAF2FF, () ->
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_copy_historical), () -> {
                    SecureClipboard.copySensitive(requireContext(), "KeyScan password history", history.oldPassword);
                    Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
                })));
        actions.addView(action(R.drawable.ic_refresh_24, getString(R.string.password_history_action_restore), 0xFF334155, 0xFFF1F5F9, () -> confirmRestore(history)));
        actions.addView(action(R.drawable.ic_delete_24, getString(R.string.common_action_delete), 0xFFE53935, 0xFFFFEBEE, () -> confirmDelete(history)));
        card.addView(actions, topWrap(10));
        return card;
    }

    private ImageButton action(int icon, String desc, Runnable click) {
        return action(icon, desc, ContextCompat.getColor(requireContext(), R.color.action_icon_tint), ContextCompat.getColor(requireContext(), R.color.action_icon_bg), click);
    }

    private ImageButton action(int icon, String desc, int tint, int background, Runnable click) {
        ImageButton button = new ImageButton(requireContext());
        button.setImageResource(icon);
        button.setContentDescription(desc);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(10));
        button.setBackground(bg);
        button.setColorFilter(tint);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.leftMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private void confirmRestore(PasswordHistory history) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_history_restore_title)
                .setMessage(R.string.password_history_restore_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    OperationModeGuard.requireEdit(this, () -> {
                        repository.restorePasswordFromHistory(entry.id, history.historyId);
                        Toast.makeText(requireContext(), R.string.password_history_restored, Toast.LENGTH_SHORT).show();
                        load();
                    });
                })
                .show();
    }

    private void confirmDelete(PasswordHistory history) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_history_delete_title)
                .setMessage(R.string.password_history_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    OperationModeGuard.requireEdit(this, () -> {
                        repository.deletePasswordHistory(history);
                        load();
                    });
                })
                .show();
    }

    private String mask(String value) {
        int length = Math.max(8, value == null ? 0 : Math.min(value.length(), 16));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) out.append('•');
        return out.toString();
    }

    private String sourceName(String source) {
        if ("auto_update".equals(source)) return getString(R.string.password_history_source_auto_update);
        if ("restore".equals(source)) return getString(R.string.password_history_source_restore);
        return getString(R.string.password_history_source_manual);
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(ContextCompat.getColor(requireContext(), color));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams topWrap(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(top);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static final class SpaceShim extends View {
        public SpaceShim(android.content.Context context) { super(context); }
    }
}
