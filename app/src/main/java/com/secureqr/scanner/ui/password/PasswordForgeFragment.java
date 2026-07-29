package com.secureqr.scanner.ui.password;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.SearchView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.autofill.AutofillCredentialMatcher;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.model.PasswordHistory;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.ui.share.SecureShareDirectDialog;
import com.secureqr.scanner.ui.share.SecureShareStateStore;
import com.secureqr.scanner.ui.otp.OtpAuthFragment;
import com.secureqr.scanner.utils.ExcelExportHelper;
import com.secureqr.scanner.utils.GlobalWebDavSyncUi;
import com.secureqr.scanner.security.PasswordSecurityCheck;
import com.secureqr.scanner.utils.LanCredentialShareServer;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.QRGenerator;
import com.secureqr.scanner.utils.OtpHelper;
import com.secureqr.scanner.utils.FragmentUi;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PasswordForgeFragment extends Fragment {
    private static final String ARG_OPEN_NEW_RECORD = "open_new_record";
    public static final String PASSWORD_SCAN_REQUEST = "password_scan_request";
    public static final String PASSWORD_SCAN_VALUE = "password_scan_value";

    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final long LAN_SHARE_DURATION_MS = 60_000L;
    private static final int EDIT_ICON_BLUE = 0xFF2F7CF6;
    private static final int EDIT_ICON_PURPLE = 0xFF8B5CF6;
    private static final int EDIT_ICON_GREEN = 0xFF22C55E;
    private static final int EDIT_ICON_ORANGE = 0xFFF59E0B;

    private PasswordRepository repository;
    private OtpRepository otpRepository;
    private PasswordEntryAdapter adapter;
    private View emptyState;
    private LiveData<List<PasswordEntry>> observedEntries;
    private LiveData<List<PasswordGroup>> observedGroups;
    private CredentialEditor activeEditor;
    private AlertDialog activeCredentialDialog;
    private PasswordEntry pendingScanEntry;
    private PasswordEntry pendingOtpLogin;
    private String pendingScanSite = "";
    private String pendingScanAccount = "";
    private String pendingScanGroupId = "";
    private String pendingScanPassword = "";
    private LanCredentialShareServer activeLanShareServer;
    private final List<PasswordEntry> latestEntries = new ArrayList<>();
    private PasswordSecurityCheck.Result securityResult;
    private PasswordSecurityCheckButton passwordSecurityCheck;
    private boolean passwordSecurityCheckPerformed;
    private final List<PasswordGroup> latestGroups = new ArrayList<>();
    private String currentQuery = "";

    public static PasswordForgeFragment newRecord() {
        PasswordForgeFragment fragment = new PasswordForgeFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_OPEN_NEW_RECORD, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_password_forge, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = PasswordRepository.getInstance(requireContext());
        otpRepository = OtpRepository.getInstance(requireContext());
        emptyState = view.findViewById(R.id.layout_password_empty);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_password_entries);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PasswordEntryAdapter(new PasswordEntryAdapter.Listener() {
            @Override
            public void onCopy(PasswordEntry entry) {
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_copy), () -> copyText(entry.password));
            }

            @Override
            public void onDelete(PasswordEntry entry) {
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_delete), () -> confirmDelete(entry));
            }

            @Override
            public void onEdit(PasswordEntry entry) {
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_edit), () -> showCredentialDialog(entry));
            }

            @Override
            public void onGroupMenu(View anchor, PasswordGroup group, int count) {
                showGroupActions(anchor, group, count);
            }

            @Override
            public void onFavorite(PasswordEntry entry, boolean favorite) {
                PasswordUiState.setFavorite(requireContext(), entry.id, favorite);
                Toast.makeText(requireContext(), favorite ? R.string.password_favorite_added : R.string.password_favorite_removed, Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onEntryMenu(View anchor, PasswordEntry entry) {
                showEntryActions(anchor, entry);
            }

            @Override
            public void onRisk(PasswordEntry entry) {
                showRiskConfirmation(entry);
            }

            @Override
            public void onShare(PasswordEntry entry) {
                if (entry != null) SecureShareDirectDialog.show(
                        PasswordForgeFragment.this, entry, adapter::notifyDataSetChanged);
            }
        });
        recyclerView.setAdapter(adapter);
        attachSwipeDelete(recyclerView);

        view.findViewById(R.id.btn_add_password_entry).setOnClickListener(this::showAddActions);
        view.findViewById(R.id.btn_password_menu).setOnClickListener(this::showMenu);
        view.findViewById(R.id.btn_password_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        passwordSecurityCheck = view.findViewById(R.id.btn_password_security_check);
        passwordSecurityCheck.setOnClickListener(v -> runPasswordSecurityCheck(true));
        SearchView searchView = view.findViewById(R.id.search_password_entries);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                observeEntries(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                observeEntries(newText);
                return true;
            }
        });

        getParentFragmentManager().setFragmentResultListener(PASSWORD_SCAN_REQUEST, getViewLifecycleOwner(), (requestKey, result) -> {
            String raw = result.getString(PASSWORD_SCAN_VALUE, "");
            if (pendingScanEntry != null || !pendingScanSite.isEmpty() || !pendingScanAccount.isEmpty()) {
                PasswordEntry entry = pendingScanEntry;
                String site = pendingScanSite;
                String account = pendingScanAccount;
                String groupId = pendingScanGroupId;
                pendingScanEntry = null;
                pendingScanSite = "";
                pendingScanAccount = "";
                pendingScanGroupId = "";
                pendingScanPassword = "";
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_edit), () -> showCredentialDialog(entry, site, account, groupId, raw));
            } else if (activeEditor != null) {
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_change), () -> activeEditor.applyScannedPassword(raw));
            } else {
                showCredentialDialog(null, "", "", "", raw);
            }
        });
        getParentFragmentManager().setFragmentResultListener(OtpAuthFragment.OTP_SCAN_REQUEST, getViewLifecycleOwner(), (requestKey, result) -> {
            PasswordEntry login = pendingOtpLogin; pendingOtpLogin = null;
            if (login != null) confirmCreateScannedOtp(login, result.getString(OtpAuthFragment.OTP_SCAN_VALUE, ""));
        });
        observeGroups();
        observeEntries("");
        if (savedInstanceState == null && getArguments() != null
                && getArguments().getBoolean(ARG_OPEN_NEW_RECORD, false)) {
            view.post(this::showAddDialog);
        }
    }

    private void observeEntries(String query) {
        currentQuery = query == null ? "" : query;
        if (observedEntries != null) {
            observedEntries.removeObservers(getViewLifecycleOwner());
        }
        observedEntries = repository.observe(currentQuery);
        observedEntries.observe(getViewLifecycleOwner(), entries -> {
            latestEntries.clear();
            if (entries != null) latestEntries.addAll(entries);
            refreshPasswordList();
        });
    }

    private void observeGroups() {
        if (observedGroups != null) {
            observedGroups.removeObservers(getViewLifecycleOwner());
        }
        observedGroups = repository.observeGroups();
        observedGroups.observe(getViewLifecycleOwner(), groups -> {
            latestGroups.clear();
            if (groups != null) latestGroups.addAll(groups);
            refreshPasswordList();
        });
    }

    private void refreshPasswordList() {
        adapter.submit(latestGroups, latestEntries, currentQuery == null || currentQuery.trim().isEmpty());
        securityResult = PasswordSecurityCheck.analyze(requireContext(), securityCheckEntries());
        adapter.setSecurityResult(securityResult);
        if (passwordSecurityCheckPerformed) updatePasswordSecurityIcon();
        boolean empty = latestEntries.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void attachSwipeDelete(RecyclerView recyclerView) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                PasswordEntry entry = adapter.getItem(position);
                adapter.notifyItemChanged(position);
                confirmDelete(entry);
            }
        }).attachToRecyclerView(recyclerView);
    }

    private void showAddDialog() {
        loadGroupsForEditor(null, "", "", "", "");
    }

    private void showAddActions(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.password_add_record);
        menu.getMenu().add(0, 2, 1, R.string.add_password_group);
        menu.getMenu().add(0, 3, 2, R.string.password_generation_records_title);
        menu.getMenu().add(0, 4, 3, R.string.import_password_book);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showAddDialog();
            else if (item.getItemId() == 2) showGroupNameDialog(null);
            else if (item.getItemId() == 3) getParentFragmentManager().beginTransaction().replace(R.id.fragment_container, PasswordGenerationHistoryFragment.registrationRecords()).addToBackStack(null).commit();
            else if (item.getItemId() == 4 && requireActivity() instanceof com.secureqr.scanner.MainActivity) {
                ((com.secureqr.scanner.MainActivity) requireActivity()).openPasswordBookImport();
            }
            return true;
        });
        menu.show();
    }

    private void showSecureSharePasswordChooser() {
        repository.getAll(entries -> FragmentUi.run(this, () -> {
            if (entries == null || entries.isEmpty()) {
                Toast.makeText(requireContext(), R.string.secure_share_no_passwords, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                PasswordEntry entry = entries.get(i);
                labels[i] = entry.displayTitle() + (TextUtils.isEmpty(entry.displayUsername())
                        ? "" : " · " + entry.displayUsername());
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.secure_share_choose_password)
                    .setItems(labels, (dialog, which) ->
                            SecureShareDirectDialog.show(this, entries.get(which),
                                    adapter::notifyDataSetChanged))
                    .setNegativeButton(R.string.common_action_cancel, null)
                    .show();
        }));
    }

    private String groupDisplayName(PasswordGroup group) {
        if (group == null || PasswordGroup.DEFAULT_ID.equals(group.id)) {
            return getString(R.string.password_default_group);
        }
        if (PasswordGroup.SECURE_SHARE_ID.equals(group.id)) {
            return getString(R.string.secure_share_group_name);
        }
        return group.displayName();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.export_backup);
        menu.getMenu().add(0, 2, 1, R.string.manage_password_groups);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 2) {
                showGroupManager();
            } else {
                showExportFormatDialog();
            }
            return true;
        });
        menu.show();
    }

    private void showGroupActions(View anchor, PasswordGroup group, int count) {
        if (group == null) return;
        if (PasswordGroup.SECURE_SHARE_ID.equals(group.id)) return;
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.rename_password_group);
        boolean isDefault = PasswordGroup.DEFAULT_ID.equals(group.id) || group.isDefault;
        if (!isDefault) menu.getMenu().add(0, 2, 1, R.string.delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showGroupNameDialog(group);
            else if (item.getItemId() == 2) confirmDeleteGroup(group, count);
            return true;
        });
        menu.show();
    }

    private void showEntryActions(View anchor, PasswordEntry entry) {
        openWebsite(entry);
    }

    private void runPasswordSecurityCheck(boolean showResult) {
        if (passwordSecurityCheck == null) return;
        passwordSecurityCheck.startChecking();
        passwordSecurityCheck.postDelayed(() -> {
            passwordSecurityCheckPerformed = true;
            securityResult = PasswordSecurityCheck.analyze(requireContext(), securityCheckEntries());
            adapter.setSecurityResult(securityResult);
            updatePasswordSecurityIcon();
            if (showResult) showPasswordSecurityResult();
        }, 520L);
    }

    private List<PasswordEntry> securityCheckEntries() {
        List<PasswordEntry> entries = new ArrayList<>();
        for (PasswordEntry entry : latestEntries) {
            if (entry != null && !PasswordGroup.SECURE_SHARE_ID.equals(entry.groupId)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private void updatePasswordSecurityIcon() {
        if (passwordSecurityCheck == null || securityResult == null) return;
        boolean safe = securityResult.riskCount() == 0;
        passwordSecurityCheck.setResult(safe);
    }

    private void showPasswordSecurityResult() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(18));
        addSecurityResultCard(content, R.drawable.ic_password_risk_safe,
                R.string.password_security_normal_title, R.string.password_security_normal_description,
                -1, R.color.settings_green);
        addSecurityResultCard(content, R.drawable.ic_password_risk_weak,
                R.string.password_security_weak_title, R.string.password_security_weak_description,
                securityResult.weakCount, R.color.settings_orange);
        addSecurityResultCard(content, R.drawable.ic_password_risk_stale,
                R.string.password_security_stale_title, R.string.password_security_stale_description,
                securityResult.staleCount, R.color.vault_icon_yellow);
        addSecurityResultCard(content, R.drawable.ic_password_risk_dot,
                R.string.password_security_duplicate_title, R.string.password_security_duplicate_description,
                securityResult.duplicateCount, R.color.primary_blue);
        addSecurityResultCard(content, R.drawable.ic_password_risk_confirmed,
                R.string.password_security_confirmed_title, R.string.password_security_confirmed_description,
                -1, R.color.settings_green);

        LinearLayout title = new LinearLayout(requireContext());
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        title.setPadding(dp(20), dp(12), dp(8), dp(4));
        TextView heading = new TextView(requireContext());
        heading.setText(R.string.password_security_result);
        heading.setTextSize(20);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        title.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView close = new TextView(requireContext());
        close.setText(R.string.common_action_close_symbol);
        close.setTextSize(28);
        close.setGravity(android.view.Gravity.CENTER);
        close.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        title.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setCustomTitle(title)
                .setView(content)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void addSecurityResultCard(LinearLayout parent, int iconRes, int titleRes,
                                       int descriptionRes, int count, int statusColor) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_password_security_result_row);
        row.setPadding(dp(14), dp(10), dp(12), dp(10));
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView name = new TextView(requireContext());
        name.setText(titleRes);
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        name.setTextSize(15);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        labels.addView(name);
        TextView note = new TextView(requireContext());
        note.setText(descriptionRes);
        note.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        note.setTextSize(11);
        note.setMaxLines(2);
        labels.addView(note);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView status = new TextView(requireContext());
        status.setText(count < 0 ? getString(R.string.password_security_good)
                : getString(R.string.password_security_count, count));
        status.setTextColor(ContextCompat.getColor(requireContext(), statusColor));
        status.setTextSize(14);
        status.setGravity(android.view.Gravity.CENTER);
        if (count >= 0) {
            android.graphics.drawable.GradientDrawable badge = new android.graphics.drawable.GradientDrawable();
            badge.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                    ContextCompat.getColor(requireContext(), statusColor), 24));
            badge.setCornerRadius(dp(14));
            status.setBackground(badge);
            status.setPadding(dp(8), dp(4), dp(8), dp(4));
        }
        row.addView(status);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
        params.topMargin = dp(6);
        parent.addView(row, params);
    }

    @SuppressWarnings("unused")
    private void showPasswordSecurityResultLegacy() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(6), dp(22), dp(10));
        TextView guideTitle = new TextView(requireContext());
        guideTitle.setText("颜色规范说明");
        guideTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        guideTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        guideTitle.setTextSize(14);
        content.addView(guideTitle);
        addSecurityLegend(content, R.drawable.ic_password_risk_safe, getString(R.string.password_security_normal), "安全");
        addSecurityLegend(content, R.drawable.ic_password_risk_weak, getString(R.string.password_security_weak), "强度不足");
        addSecurityLegend(content, R.drawable.ic_password_risk_stale, getString(R.string.password_security_stale), "建议修改（超过 90 天）");
        addSecurityLegend(content, R.drawable.ic_password_risk_dot, getString(R.string.password_security_duplicate), "已重复使用");
        addSecurityLegend(content, R.drawable.ic_password_risk_confirmed, "已确认", "不再提示");
        TextView result = new TextView(requireContext());
        result.setText(securityResult.riskCount() == 0 ? getString(R.string.password_security_safe)
                : getString(R.string.password_security_weak) + "  " + securityResult.weakCount + "\n"
                + getString(R.string.password_security_stale) + "  " + securityResult.staleCount + "\n"
                + getString(R.string.password_security_duplicate) + "  " + securityResult.duplicateCount);
        result.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        result.setTextSize(17);
        result.setPadding(0, dp(20), 0, dp(14));
        content.addView(result);
        LinearLayout title = new LinearLayout(requireContext());
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        title.setPadding(dp(22), dp(12), dp(10), 0);
        TextView heading = new TextView(requireContext());
        heading.setText(R.string.password_security_result);
        heading.setTextSize(20);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        title.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView close = new TextView(requireContext());
        close.setText("×");
        close.setTextSize(30);
        close.setGravity(android.view.Gravity.CENTER);
        close.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        title.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setCustomTitle(title)
                .setView(content)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void addSecurityLegend(LinearLayout parent, int iconRes, String name, String note) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_security_legend_row);
        row.setPadding(0, 0, 0, 0);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(32)));
        TextView text = new TextView(requireContext());
        text.setText(name + "（" + note + "）");
        text.setGravity(android.view.Gravity.CENTER_VERTICAL);
        text.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        text.setTextSize(13);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(32), 1));
        parent.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
    }

    private void showRiskConfirmation(PasswordEntry entry) {
        if (securityResult == null) securityResult = PasswordSecurityCheck.analyze(requireContext(), securityCheckEntries());
        PasswordSecurityCheck.Risk risk = securityResult.riskFor(entry.id);
        if (risk == PasswordSecurityCheck.Risk.NORMAL) {
            showPasswordSecurityResult();
            return;
        }
        if (risk == PasswordSecurityCheck.Risk.CONFIRMED) {
            showPasswordSecurityResult();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(entry.displayTitle())
                .setMessage(getString(R.string.password_security_confirm))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    PasswordSecurityCheck.confirm(requireContext(), securityResult, entry);
                    runPasswordSecurityCheck(false);
                }).show();
    }

    private void openWebsite(PasswordEntry entry) {
        String website = entry.websiteDomain == null ? "" : entry.websiteDomain.trim();
        if (website.isEmpty()) website = entry.displayTitle().trim();
        if (website.isEmpty()) { Toast.makeText(requireContext(), R.string.password_open_website_unavailable, Toast.LENGTH_SHORT).show(); return; }
        if (!website.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) website = "https://" + website;
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(website))); }
        catch (Exception e) { Toast.makeText(requireContext(), R.string.password_open_website_unavailable, Toast.LENGTH_SHORT).show(); }
    }

    @ExperimentalGetImage
    private void openPasswordScanner() {
        if (activeEditor != null) {
            pendingScanSite = firstNonEmpty(
                    activeEditor.titleInput.getText().toString(),
                    activeEditor.websiteInput.getText().toString(),
                    activeEditor.appPackageInput.getText().toString(),
                    activeEditor.remarkInput.getText().toString()
            );
            pendingScanAccount = firstNonEmpty(
                    activeEditor.usernameInput.getText().toString(),
                    activeEditor.accountInput.getText().toString()
            );
            pendingScanGroupId = activeEditor.selectedGroupId();
            pendingScanPassword = activeEditor.currentPassword;
            if (activeCredentialDialog != null) {
                activeCredentialDialog.dismiss();
            }
        }
        ScannerFragment fragment = ScannerFragment.forPasswordCapture();
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showCredentialDialog(PasswordEntry entry) {
        if (entry != null && !com.secureqr.scanner.security.VaultAccessManager.isUnlocked(requireActivity())) {
            SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_edit), () -> showCredentialDialog(entry));
            return;
        }
        loadGroupsForEditor(entry, entry.displayTitle(), entry.displayUsername(), entry.groupId, entry.password);
    }

    @Override
    public void onDestroyView() {
        stopActiveLanShare();
        super.onDestroyView();
    }

    private void showCredentialDialog(@Nullable PasswordEntry editingEntry, String initialSite, String initialAccount, String initialGroupId, String initialPassword) {
        CredentialEditor editor = new CredentialEditor(editingEntry, initialSite, initialAccount, initialGroupId, initialPassword, new ArrayList<>(latestGroups));
        activeEditor = editor;
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(credentialPage(editor, editingEntry))
                .create();
        activeCredentialDialog = dialog;
        dialog.setOnDismissListener(d -> {
            editor.stopOtpTicker();
            if (activeEditor == editor) activeEditor = null;
            if (activeCredentialDialog == dialog) activeCredentialDialog = null;
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
        }
    }

    private View credentialPage(CredentialEditor editor, @Nullable PasswordEntry entry) {
        LinearLayout page = new LinearLayout(requireContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_light));

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(14), dp(12), dp(14), dp(6));
        ImageButton back = new ImageButton(requireContext());
        back.setImageResource(R.drawable.ic_arrow_back_24);
        back.setBackgroundColor(0x00000000);
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        back.setOnClickListener(v -> {
            if (activeCredentialDialog != null) activeCredentialDialog.dismiss();
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = new TextView(requireContext());
        title.setText(entry == null ? getString(R.string.credential_add_title) : getString(R.string.credential_edit_title));
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        title.setGravity(android.view.Gravity.CENTER);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (entry != null) {
            ImageButton favorite = new ImageButton(requireContext());
            boolean isFavorite = PasswordUiState.isFavorite(requireContext(), entry.id);
            favorite.setImageResource(isFavorite ? R.drawable.ic_star : R.drawable.ic_star_border);
            favorite.setColorFilter(ContextCompat.getColor(requireContext(), isFavorite ? R.color.vault_icon_orange : R.color.text_secondary));
            favorite.setBackgroundResource(R.drawable.bg_icon_action);
            favorite.setPadding(dp(8), dp(8), dp(8), dp(8));
            favorite.setOnClickListener(v -> OperationModeGuard.requireEdit(this, () -> {
                boolean next = !PasswordUiState.isFavorite(requireContext(), entry.id);
                PasswordUiState.setFavorite(requireContext(), entry.id, next);
                favorite.setImageResource(next ? R.drawable.ic_star : R.drawable.ic_star_border);
                favorite.setColorFilter(ContextCompat.getColor(requireContext(), next ? R.color.vault_icon_orange : R.color.text_secondary));
                adapter.notifyDataSetChanged();
            }));
            top.addView(favorite, new LinearLayout.LayoutParams(dp(42), dp(42)));
        } else {
            TextView spacer = new TextView(requireContext());
            top.addView(spacer, new LinearLayout.LayoutParams(dp(42), dp(42)));
        }
        page.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));
        page.addView(editor.root, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(requireContext());
        bottom.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(18), dp(10), dp(18), dp(14));
        Button cancel = compactButton(getString(R.string.cancel));
        cancel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        cancel.setBackgroundResource(R.drawable.bg_icon_action);
        cancel.setOnClickListener(v -> {
            if (activeCredentialDialog != null) activeCredentialDialog.dismiss();
        });
        Button save = compactButton(getString(R.string.save));
        save.setTextColor(android.graphics.Color.WHITE);
        save.setBackgroundResource(R.drawable.bg_detail_primary_button);
        save.setOnClickListener(v -> {
            if (entry.id > 0) OperationModeGuard.requireEdit(this,
                    () -> saveCredential(editor, entry),
                    () -> {
                        if (activeCredentialDialog != null) activeCredentialDialog.dismiss();
                    });
            else saveCredential(editor, entry);
        });
        bottom.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        saveParams.leftMargin = dp(10);
        bottom.addView(save, saveParams);
        page.addView(bottom, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)));
        return page;
    }

    private void saveCredential(CredentialEditor editor, @Nullable PasswordEntry editingEntry) {
        String title = editor.titleInput.getText().toString().trim();
        String website = editor.websiteInput.getText().toString().trim();
        String appPackage = editor.appPackageInput.getText().toString().trim();
        String username = editor.usernameInput.getText().toString().trim();
        String account = editor.accountInput.getText().toString().trim();
        String remark = editor.remarkInput.getText().toString().trim();
        String groupId = editor.selectedGroupId();
        String site = firstNonEmpty(title, website, appPackage, remark);
        String loginName = firstNonEmpty(username, account);
        if (TextUtils.isEmpty(site) || TextUtils.isEmpty(loginName)) {
            Toast.makeText(requireContext(), R.string.credential_fill_complete_info, Toast.LENGTH_SHORT).show();
            if (TextUtils.isEmpty(site)) editor.titleInput.setError(getString(R.string.credential_site_empty_error));
            if (TextUtils.isEmpty(loginName)) editor.usernameInput.setError(getString(R.string.credential_account_empty_error));
            return;
        }
        PasswordEntry entry = editingEntry == null ? new PasswordEntry() : editingEntry;
        String previousPassword = editingEntry == null ? null : editingEntry.password;
        long now = System.currentTimeMillis();
        String domain = AutofillCredentialMatcher.normalizeDomain(firstNonEmpty(website, title));
        entry.title = firstNonEmpty(title, site);
        entry.websiteDomain = domain;
        entry.appPackageName = appPackage;
        entry.username = firstNonEmpty(username, loginName);
        entry.remark = firstNonEmpty(remark, site);
        entry.account = firstNonEmpty(account, loginName);
        entry.password = editor.currentPassword;
        entry.groupId = firstNonEmpty(groupId, PasswordGroup.DEFAULT_ID);
        entry.updatedAt = now;
        if (editingEntry == null) {
            entry.createdAt = now;
            repository.insert(entry);
        } else {
            if (entry.createdAt <= 0) entry.createdAt = now;
            repository.update(entry);
        }
        if (activeCredentialDialog != null) activeCredentialDialog.dismiss();
        Toast.makeText(requireContext(), R.string.credential_save_success, Toast.LENGTH_SHORT).show();
        if (editingEntry != null && !java.util.Objects.equals(previousPassword, entry.password)
                && SecureShareStateStore.shouldRemindAfterPasswordChange(requireContext(), entry)) {
            showSharedPasswordChanged(entry);
        }
    }

    private void showSharedPasswordChanged(PasswordEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.secure_share_password_changed_title)
                .setMessage(R.string.secure_share_password_changed_message)
                .setNegativeButton(R.string.secure_share_end_reminders, (dialog, which) -> {
                    SecureShareStateStore.endShare(requireContext(), entry);
                    adapter.notifyDataSetChanged();
                })
                .setPositiveButton(R.string.secure_share_share_again, (dialog, which) ->
                        SecureShareDirectDialog.show(this, entry, adapter::notifyDataSetChanged))
                .show();
    }

    private void loadGroupsForEditor(@Nullable PasswordEntry editingEntry, String initialSite, String initialAccount, String initialGroupId, String initialPassword) {
        repository.getGroups(groups -> FragmentUi.run(this, () -> {
            latestGroups.clear();
            if (groups != null) latestGroups.addAll(groups);
            showCredentialDialog(editingEntry, initialSite, initialAccount, initialGroupId, initialPassword);
        }));
    }

    private void confirmLanShare(CredentialEditor editor) {
        String website = firstNonEmpty(
                editor.titleInput.getText().toString(),
                editor.websiteInput.getText().toString(),
                editor.appPackageInput.getText().toString(),
                editor.remarkInput.getText().toString()
        );
        String account = firstNonEmpty(
                editor.usernameInput.getText().toString(),
                editor.accountInput.getText().toString()
        );
        String password = editor.currentPassword == null ? "" : editor.currentPassword;
        if (TextUtils.isEmpty(website) || TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
            Toast.makeText(requireContext(), R.string.lan_share_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lan_share_confirm_title)
                .setMessage(R.string.lan_share_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> showLanShareDialog(website, account, password))
                .show();
    }

    private void showLanShareDialog(String website, String account, String password) {
        stopActiveLanShare();
        long expiresAt = System.currentTimeMillis() + LAN_SHARE_DURATION_MS;
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        try {
            String credentialJson = new JSONObject()
                    .put("type", "credential")
                    .put("website", website)
                    .put("account", account)
                    .put("password", password)
                    .toString();
            LanCredentialShareServer server = new LanCredentialShareServer(credentialJson, expiresAt, new LanCredentialShareServer.Listener() {
                @Override
                public void onServed() {
                    handler.post(() -> {
                        if (finished[0]) return;
                        finished[0] = true;
                        handler.removeCallbacksAndMessages(null);
                        activeLanShareServer = null;
                        if (!isAdded()) return;
                        AlertDialog dialog = dialogHolder[0];
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        Toast.makeText(requireContext(), R.string.lan_share_sent, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onExpired() {
                    handler.post(() -> {
                        if (finished[0]) return;
                        finished[0] = true;
                        handler.removeCallbacksAndMessages(null);
                        activeLanShareServer = null;
                        if (!isAdded()) return;
                        AlertDialog dialog = dialogHolder[0];
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        Toast.makeText(requireContext(), R.string.lan_share_timeout, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(Exception error) {
                    handler.post(() -> {
                        if (finished[0]) return;
                        finished[0] = true;
                        handler.removeCallbacksAndMessages(null);
                        activeLanShareServer = null;
                        if (!isAdded()) return;
                        AlertDialog dialog = dialogHolder[0];
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        showLanShareError(error);
                    });
                }
            });
            server.start();
            activeLanShareServer = server;

            JSONObject qrPayload = new JSONObject()
                    .put("type", "keyscan_lan_pair")
                    .put("version", 1)
                    .put("token", server.getToken())
                    .put("website", website)
                    .put("port", server.getPort())
                    .put("pingPath", server.getPingPath())
                    .put("credentialPath", server.getCredentialPath())
                    .put("expiresAt", server.getExpiresAt());

            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(18), dp(10), dp(18), dp(4));

            TextView status = createLanShareText(getString(R.string.lan_share_waiting), 18, true, R.color.text_main);
            TextView countdown = createLanShareText("", 14, false, R.color.text_secondary);
            TextView url = createLanShareText(server.getShareUrl(), 13, false, R.color.text_main);
            url.setTextIsSelectable(true);
            TextView hint = createLanShareText(getString(R.string.lan_share_hint), 12, false, R.color.text_secondary);
            ImageView qr = new ImageView(requireContext());
            Bitmap qrBitmap = QRGenerator.generateQR(qrPayload.toString(), dp(220));
            if (qrBitmap != null) {
                qr.setImageBitmap(qrBitmap);
            }
            qr.setAdjustViewBounds(true);
            qr.setContentDescription(getString(R.string.lan_share_qr_desc));

            content.addView(status);
            content.addView(countdown, topParams(28, 6));
            content.addView(url, topParams(56, 8));
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240));
            qrParams.topMargin = dp(8);
            content.addView(qr, qrParams);
            content.addView(hint, topParams(44, 8));

            Runnable ticker = new Runnable() {
                @Override
                public void run() {
                    if (finished[0]) return;
                    long remaining = Math.max(0, (server.getExpiresAt() - System.currentTimeMillis() + 999) / 1000);
                    countdown.setText(getString(R.string.lan_share_countdown, remaining));
                    if (remaining > 0) {
                        handler.postDelayed(this, 1000);
                    }
                }
            };
            ticker.run();

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.lan_share_title)
                    .setView(content)
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            dialogHolder[0] = dialog;
            dialog.setOnDismissListener(d -> {
                handler.removeCallbacksAndMessages(null);
                if (!finished[0]) {
                    stopActiveLanShare();
                }
            });
            dialog.show();
        } catch (Exception error) {
            stopActiveLanShare();
            showLanShareError(error);
        }
    }

    private void showLanShareError(Exception error) {
        String message = error.getMessage();
        if ("No LAN IPv4 address found".equals(message)) {
            Toast.makeText(requireContext(), R.string.lan_share_no_lan, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), getString(R.string.lan_share_failed, message == null ? "" : message), Toast.LENGTH_LONG).show();
        }
    }

    private TextView createLanShareText(String text, int sp, boolean bold, int colorRes) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private void stopActiveLanShare() {
        if (activeLanShareServer != null) {
            activeLanShareServer.stop();
            activeLanShareServer = null;
        }
    }

    private class CredentialEditor {
        final LinearLayout root;
        final LinearLayout form;
        final EditText titleInput;
        final EditText websiteInput;
        final EditText appPackageInput;
        final EditText usernameInput;
        final EditText accountInput;
        final Spinner groupSpinner;
        final EditText passwordValue;
        final EditText remarkInput;
        final Button eye;
        final PasswordEntry editingEntry;
        final List<PasswordGroup> availableGroups;
        LinearLayout otpContent;
        Runnable otpTicker;
        String currentPassword;
        String selectedGroupId;
        boolean visible = false;
        boolean bindingPassword = false;

        CredentialEditor(@Nullable PasswordEntry editingEntry, String initialSite, String initialAccount, String initialGroupId, String initialPassword, List<PasswordGroup> groups) {
            this.editingEntry = editingEntry;
            this.availableGroups = groups == null ? new ArrayList<>() : groups;
            root = new LinearLayout(requireContext());
            root.setOrientation(LinearLayout.VERTICAL);
            ScrollView scrollView = new ScrollView(requireContext());
            root.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            form = new LinearLayout(requireContext());
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(18), dp(8), dp(18), dp(8));
            scrollView.addView(form, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
            ));

            titleInput = credentialInput();
            websiteInput = credentialInput();
            appPackageInput = credentialInput();
            usernameInput = credentialInput();
            accountInput = credentialInput();
            groupSpinner = new Spinner(requireContext());
            groupSpinner.setBackgroundResource(R.drawable.bg_edit_text);
            passwordValue = credentialInput();
            passwordValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordValue.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            passwordValue.setBackgroundResource(R.drawable.bg_edit_text);
            passwordValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            passwordValue.setTextSize(18);
            passwordValue.setPadding(dp(12), 0, dp(12), 0);
            remarkInput = credentialInput();
            remarkInput.setSingleLine(false);
            remarkInput.setMinLines(3);
            remarkInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            remarkInput.setPadding(dp(12), dp(10), dp(12), dp(10));

            eye = compactButton("");
            setInlineEyeIcon(eye, R.drawable.ic_visibility_off_24, getString(R.string.credential_show_password_desc));
            LinearLayout credentialCard = sectionCard(form, getString(R.string.password_section_credentials), R.drawable.ic_text, EDIT_ICON_BLUE, 0);
            addLabeledField(credentialCard, getString(R.string.credential_label_title), titleInput, 0, 52);
            addLabeledField(credentialCard, getString(R.string.credential_label_website), websiteInput, 10, 52);
            addLabeledField(credentialCard, getString(R.string.credential_label_app_package), appPackageInput, 10, 52);
            addLabeledField(credentialCard, getString(R.string.credential_label_username), usernameInput, 10, 52);
            addLabeledField(credentialCard, getString(R.string.credential_label_account), accountInput, 10, 52);
            credentialCard.addView(fieldLabel(getString(R.string.credential_label_group)), topParams(22, 10));
            LinearLayout groupRow = new LinearLayout(requireContext());
            groupRow.setOrientation(LinearLayout.HORIZONTAL);
            groupRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            groupRow.addView(groupSpinner, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button createGroup = compactButton(getString(R.string.add_password_group));
            createGroup.setBackgroundResource(R.drawable.bg_icon_action);
            createGroup.setTextColor(ContextCompat.getColor(requireContext(), R.color.action_icon_tint));
            createGroup.setOnClickListener(v -> showCreateGroupForEditor(this));
            groupRow.addView(createGroup, fixedParams(104, 8));
            credentialCard.addView(groupRow, topParams(48, 4));

            LinearLayout passwordCard = sectionCard(form, getString(R.string.password_section_password), R.drawable.ic_key_line, EDIT_ICON_BLUE, 12);
            passwordCard.addView(fieldLabel(getString(R.string.credential_label_password)), topParams(22, 0));
            LinearLayout passwordRow = new LinearLayout(requireContext());
            passwordRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            passwordRow.setOrientation(LinearLayout.HORIZONTAL);
            android.widget.FrameLayout passwordBox = new android.widget.FrameLayout(requireContext());
            passwordValue.setPadding(dp(12), 0, dp(52), 0);
            passwordBox.addView(passwordValue, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            eye.setBackground(null);
            eye.setElevation(0f);
            eye.setStateListAnimator(null);
            passwordBox.addView(eye, new android.widget.FrameLayout.LayoutParams(
                    dp(48), dp(48), android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL));
            passwordRow.addView(passwordBox, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button copy = compactButton("");
            setIconButton(copy, R.drawable.ic_content_copy_24, getString(R.string.copy));
            passwordRow.addView(copy, fixedParams(48, 8));
            passwordCard.addView(passwordRow, topParams(48, 4));
            addPasswordHistoryCard(form, editingEntry);
            if (editingEntry != null) addOtpCard(form, editingEntry, this);

            LinearLayout remarkCard = sectionCard(form, getString(R.string.password_section_remark), R.drawable.ic_edit_24, EDIT_ICON_GREEN, 12);
            addLabeledField(remarkCard, getString(R.string.credential_label_remark), remarkInput, 0, 96);

            bindInitialFields(initialSite, initialAccount);
            bindGroups(initialGroupId);
            currentPassword = initialPassword == null ? "" : initialPassword;
            updatePasswordView();

            eye.setOnClickListener(v -> {
                if(visible){visible=false;updatePasswordVisibility();return;}
                SensitiveActionGuard.requireAuthentication(requireActivity(),getString(R.string.password_auth_view),()->{visible=true;updatePasswordVisibility();});
            });
            copy.setOnClickListener(v -> SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_copy), () -> copyText(currentPassword)));
            passwordValue.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (bindingPassword) return;
                    currentPassword = s.toString();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        private void bindGroups(String initialGroupId) {
            List<PasswordGroup> groups = new ArrayList<>(availableGroups);
            if (groups.isEmpty()) {
                PasswordGroup fallback = new PasswordGroup();
                fallback.id = PasswordGroup.DEFAULT_ID;
                fallback.name = PasswordGroup.DEFAULT_NAME;
                fallback.isDefault = true;
                fallback.sortOrder = 0;
                fallback.createdAt = System.currentTimeMillis();
                fallback.updatedAt = fallback.createdAt;
                groups.add(fallback);
            }
            List<String> labels = new ArrayList<>();
            int selectedIndex = 0;
            String desired = firstNonEmpty(initialGroupId, editingEntry == null ? "" : editingEntry.groupId, PasswordGroup.DEFAULT_ID);
            for (int i = 0; i < groups.size(); i++) {
                PasswordGroup group = groups.get(i);
                labels.add(groupDisplayName(group));
                if (desired.equals(group.id)) selectedIndex = i;
            }
            groupSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels));
            groupSpinner.setSelection(selectedIndex);
            selectedGroupId = groups.get(selectedIndex).id;
            groupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < groups.size()) {
                        selectedGroupId = groups.get(position).id;
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
        }

        private void updatePasswordView() {
            bindingPassword = true;
            passwordValue.setText(currentPassword);
            passwordValue.setSelection(passwordValue.getText().length());
            bindingPassword = false;
            passwordValue.setTransformationMethod(visible ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            passwordValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            passwordValue.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
            setInlineEyeIcon(eye, visible ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24, getString(visible ? R.string.credential_hide_password_desc : R.string.credential_show_password_desc));
            updatePasswordVisibility();
        }

        private void updatePasswordVisibility() {
            int start = Math.max(0, passwordValue.getSelectionStart());
            int end = Math.max(0, passwordValue.getSelectionEnd());
            passwordValue.setTransformationMethod(visible ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            int length = passwordValue.getText().length();
            passwordValue.setSelection(Math.min(start, length), Math.min(end, length));
            passwordValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
            setInlineEyeIcon(eye, visible ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24, getString(visible ? R.string.credential_hide_password_desc : R.string.credential_show_password_desc));
        }

        private String selectedGroupId() {
            return firstNonEmpty(selectedGroupId, PasswordGroup.DEFAULT_ID);
        }

        private void applyScannedPassword(String value) {
            currentPassword = value == null ? "" : value;
            visible = false;
            updatePasswordView();
            Toast.makeText(requireContext(), R.string.credential_password_filled, Toast.LENGTH_SHORT).show();
        }

        private void bindInitialFields(String initialSite, String initialAccount) {
            if (editingEntry == null) {
                String site = initialSite == null ? "" : initialSite;
                String account = initialAccount == null ? "" : initialAccount;
                titleInput.setText(site);
                websiteInput.setText(AutofillCredentialMatcher.normalizeDomain(site));
                appPackageInput.setText("");
                usernameInput.setText(account);
                accountInput.setText(account);
                remarkInput.setText(site);
                return;
            }
            titleInput.setText(firstNonEmpty(editingEntry.title, editingEntry.displayTitle()));
            websiteInput.setText(firstNonEmpty(editingEntry.websiteDomain, ""));
            appPackageInput.setText(firstNonEmpty(editingEntry.appPackageName, ""));
            usernameInput.setText(firstNonEmpty(editingEntry.username, editingEntry.displayUsername()));
            accountInput.setText(firstNonEmpty(editingEntry.account, ""));
            remarkInput.setText(firstNonEmpty(editingEntry.remark, ""));
        }

        void stopOtpTicker() { if (otpTicker != null && otpContent != null) otpContent.removeCallbacks(otpTicker); otpTicker = null; }
    }

    private LinearLayout sectionCard(LinearLayout parent, String title, int topMarginDp) {
        return sectionCard(parent, title, R.drawable.ic_text, EDIT_ICON_BLUE, topMarginDp);
    }

    private LinearLayout sectionCard(LinearLayout parent, String title, int iconRes, int iconColor, int topMarginDp) {
        LinearLayout headingRow = new LinearLayout(requireContext());
        headingRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(round(iconColor, dp(10)));
        headingRow.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        titleView.setTextSize(16);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setPadding(dp(9), 0, 0, 0);
        headingRow.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(headingRow, topWrapParams(topMarginDp));

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(12), dp(10), dp(12), dp(12));
        parent.addView(card, topWrapParams(8));
        return card;
    }

    private void addOtpCard(LinearLayout parent, PasswordEntry login, CredentialEditor editor) {
        editor.otpContent = sectionCard(parent, getString(R.string.otp_link_section_title), R.drawable.ic_key_line, EDIT_ICON_PURPLE, 12);
        renderOtpCard(login, editor);
    }

    private void renderOtpCard(PasswordEntry login, CredentialEditor editor) {
        editor.stopOtpTicker();
        LinearLayout card = editor.otpContent; card.removeAllViews();
        if (login.otpId == null) {
            TextView hint = fieldLabel(getString(R.string.otp_link_empty)); card.addView(hint);
            Button add = compactButton(getString(R.string.otp_link_add)); add.setBackgroundResource(R.drawable.bg_detail_primary_button); add.setTextColor(Color.WHITE);
            add.setOnClickListener(v -> showOtpAddOptions(login)); card.addView(add, topParams(46, 10)); return;
        }
        otpRepository.getById(login.otpId, token -> FragmentUi.run(this, () -> {
            if (!isAdded() || editor.otpContent != card) return;
            card.removeAllViews();
            if (token == null) { login.otpId = null; login.otpItemId=null; repository.update(login); renderOtpCard(login, editor); return; }
            TextView identity = new TextView(requireContext()); identity.setText(firstNonEmpty(token.issuer, "OTP") + "\n" + firstNonEmpty(token.accountName, "")); identity.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main)); identity.setTextSize(14); card.addView(identity);
            TextView code = new TextView(requireContext()); code.setTextColor(ContextCompat.getColor(requireContext(), R.color.action_icon_tint)); code.setTextSize(28); code.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); code.setPadding(0,dp(10),0,0); card.addView(code);
            TextView countdown = new TextView(requireContext()); countdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary)); countdown.setTextSize(12); card.addView(countdown);
            editor.otpTicker = new Runnable(){@Override public void run(){try{long now=System.currentTimeMillis();code.setText(OtpHelper.code(token,now));countdown.setText(getString(R.string.otp_link_countdown_format,OtpHelper.remainingSeconds(token,now)));card.postDelayed(this,1000);}catch(Exception e){code.setText(R.string.otp_code_unavailable);countdown.setText(e.getMessage()==null?getString(R.string.otp_config_error):e.getMessage());}}}; editor.otpTicker.run();
            LinearLayout actions=new LinearLayout(requireContext());actions.setOrientation(LinearLayout.HORIZONTAL);Button change=compactButton(getString(R.string.otp_link_change));change.setOnClickListener(v->chooseExistingOtp(login));Button unlink=compactButton(getString(R.string.otp_link_unlink));unlink.setOnClickListener(v->confirmUnlinkOtp(login));actions.addView(change,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(0,dp(44),1);up.leftMargin=dp(8);actions.addView(unlink,up);card.addView(actions,topParams(44,10));
        }));
    }

    private void showOtpAddOptions(PasswordEntry login) {
        new AlertDialog.Builder(requireContext()).setTitle(R.string.otp_link_add).setItems(new String[]{getString(R.string.otp_link_choose_existing), getString(R.string.otp_link_scan_qr), getString(R.string.otp_link_manual_secret)}, (d, which) -> {
            if (which == 0) chooseExistingOtp(login); else if (which == 1) scanOtpForLogin(login); else showManualOtpForLogin(login);
        }).setNegativeButton(R.string.common_action_cancel, null).show();
    }

    private void chooseExistingOtp(PasswordEntry login) {
        otpRepository.getAvailableForLogin(login.id, tokens -> FragmentUi.run(this, () -> {
            List<OtpToken> choices=new ArrayList<>();for(OtpToken token:tokens)if(login.otpId==null||token.id!=login.otpId)choices.add(token);
            if(choices.isEmpty()){Toast.makeText(requireContext(),R.string.otp_link_none_available,Toast.LENGTH_SHORT).show();return;}
            String[] labels=new String[choices.size()];for(int i=0;i<choices.size();i++)labels[i]=firstNonEmpty(choices.get(i).issuer,"OTP")+" · "+firstNonEmpty(choices.get(i).accountName,"");
            new AlertDialog.Builder(requireContext()).setTitle(R.string.otp_link_choose_title).setItems(labels,(d,index)->confirmBindOtp(login,choices.get(index))).setNegativeButton(R.string.common_action_cancel,null).show();
        }));
    }

    private void confirmBindOtp(PasswordEntry login, OtpToken token) {
        new AlertDialog.Builder(requireContext()).setTitle(R.string.otp_link_confirm_title).setMessage(getString(R.string.otp_link_confirm_message,login.displayTitle(),firstNonEmpty(token.issuer,token.accountName,"OTP")))
                .setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.otp_link_confirm_action,(d,w)->{login.otpId=token.id;login.otpItemId=token.itemId;repository.update(login);if(activeEditor!=null)renderOtpCard(login,activeEditor);}).show();
    }

    private void confirmUnlinkOtp(PasswordEntry login) {
        new AlertDialog.Builder(requireContext()).setTitle(R.string.otp_unlink_title).setMessage(R.string.otp_unlink_message)
                .setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.otp_link_unlink,(d,w)->{login.otpId=null;login.otpItemId=null;repository.update(login);if(activeEditor!=null)renderOtpCard(login,activeEditor);}).show();
    }

    private void scanOtpForLogin(PasswordEntry login) {
        pendingOtpLogin=login;if(activeCredentialDialog!=null)activeCredentialDialog.dismiss();getParentFragmentManager().beginTransaction().replace(R.id.fragment_container,ScannerFragment.forOtpCapture()).addToBackStack(null).commit();
    }

    private void confirmCreateScannedOtp(PasswordEntry login, String raw) {
        try { OtpToken token=OtpHelper.parseUri(requireContext(),raw);new AlertDialog.Builder(requireContext()).setTitle(R.string.otp_create_bind_confirm_title).setMessage(firstNonEmpty(token.issuer,"OTP")+" · "+firstNonEmpty(token.accountName,"")).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.otp_action_create_bind,(d,w)->createAndBindOtp(login,token)).show(); }
        catch(Exception e){Toast.makeText(requireContext(),e.getMessage()==null?getString(R.string.otp_invalid_qr):e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void showManualOtpForLogin(PasswordEntry login) {
        LinearLayout content=new LinearLayout(requireContext());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),0);EditText issuer=credentialInput();issuer.setHint(R.string.otp_field_issuer);EditText account=credentialInput();account.setHint(R.string.otp_field_account);EditText secret=credentialInput();secret.setHint("Secret");content.addView(issuer);content.addView(account);content.addView(secret);
        AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle(R.string.otp_create_bind_title).setView(content).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.password_dialog_continue,null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(secret.getText().toString().trim().isEmpty()){secret.setError(getString(R.string.otp_secret_enter_error));return;}OtpToken token=new OtpToken();token.issuer=issuer.getText().toString().trim();token.accountName=account.getText().toString().trim();token.secret=OtpHelper.normalizeSecret(secret.getText().toString());long now=System.currentTimeMillis();token.createdAt=now;token.updatedAt=now;dialog.dismiss();new AlertDialog.Builder(requireContext()).setTitle(R.string.otp_create_bind_confirm_title).setMessage(firstNonEmpty(token.issuer,token.accountName,"OTP")).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.common_action_confirm,(x,y)->createAndBindOtp(login,token)).show();}));dialog.show();
    }

    private void createAndBindOtp(PasswordEntry login, OtpToken token) {
        otpRepository.insert(token,id->FragmentUi.run(this,()->{if(id<=0){Toast.makeText(requireContext(),R.string.otp_create_failed,Toast.LENGTH_SHORT).show();return;}login.otpId=id;login.otpItemId=token.itemId;repository.update(login);Toast.makeText(requireContext(),R.string.otp_created_bound,Toast.LENGTH_SHORT).show();showCredentialDialog(login);}));
    }

    private void addPasswordHistoryCard(LinearLayout parent, @Nullable PasswordEntry entry) {
        TextView heading = new TextView(requireContext());
        heading.setText(R.string.password_history_title);
        heading.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        heading.setTextSize(16);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        parent.addView(heading, topWrapParams(12));

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView count = new TextView(requireContext());
        count.setText(entry == null ? R.string.password_history_not_saved : R.string.password_history_loading);
        count.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        count.setTextSize(15);
        count.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView recent = new TextView(requireContext());
        recent.setText(entry == null ? R.string.password_history_recent_none : R.string.password_history_recent_loading);
        recent.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        recent.setTextSize(13);
        card.addView(count);
        card.addView(recent, topWrapParams(8));
        parent.addView(card, topWrapParams(8));
        if (entry == null) return;
        card.setOnClickListener(v -> SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.password_auth_view_history), () -> openPasswordHistory(entry.id)));
        repository.getPasswordHistory(entry.itemId, histories -> FragmentUi.run(this, () -> {
            int size = histories == null ? 0 : histories.size();
            count.setText(getResources().getQuantityString(R.plurals.password_history_records_count, size, size));
            if (size == 0) {
                recent.setText(R.string.password_history_recent_empty);
            } else {
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(histories.get(0).createdAt));
                recent.setText(getString(R.string.password_history_recent_format, date));
            }
        }));
    }

    private void openPasswordHistory(long entryId) {
        if (activeCredentialDialog != null) activeCredentialDialog.dismiss();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, PasswordHistoryFragment.newInstance(entryId))
                .addToBackStack(null)
                .commit();
    }

    private void addLabeledField(LinearLayout parent, String label, View input, int topMarginDp, int inputHeightDp) {
        parent.addView(fieldLabel(label), topParams(22, topMarginDp));
        parent.addView(input, topParams(inputHeightDp, 4));
    }

    private TextView fieldLabel(String label) {
        TextView view = new TextView(requireContext());
        view.setText(label);
        view.setTextSize(13);
        view.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return view;
    }

    private EditText credentialInput() {
        EditText input = input("");
        input.setHint(null);
        input.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint));
        input.setPadding(dp(12), 0, dp(12), 0);
        return input;
    }

    private void setIconButton(Button button, int drawableRes, String contentDescription) {
        button.setText("");
        button.setContentDescription(contentDescription);
        button.setCompoundDrawablesWithIntrinsicBounds(0, drawableRes, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.action_icon_tint)));
        button.setBackgroundResource(R.drawable.bg_icon_action);
        button.setMinWidth(0);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
    }

    private void setInlineEyeIcon(Button button, int drawableRes, String contentDescription) {
        setIconButton(button, drawableRes, contentDescription);
        button.setBackground(null);
        button.setElevation(0f);
        button.setStateListAnimator(null);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private EditText input(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        input.setHintTextColor(0xFF8A8F98);
        return input;
    }

    private Button compactButton(String text) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void showQrHelp() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.credential_qr_help_title)
                .setMessage(R.string.credential_qr_help_message)
                .setPositiveButton(R.string.got_it, null)
                .show();
    }

    private LinearLayout.LayoutParams params(int heightDp) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private LinearLayout.LayoutParams topParams(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = params(heightDp);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams topWrapParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams fixedParams(int sizeDp, int marginStartDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.leftMargin = dp(marginStartDp);
        return params;
    }

    private void copyText(String text) {
        SecureClipboard.copySensitive(requireContext(), "KeyScan password", text);
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(PasswordEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.credential_delete_title)
                .setMessage(R.string.credential_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        OperationModeGuard.requireEdit(this, () -> repository.delete(entry)))
                .show();
    }

    private void showGroupManager() {
        repository.getGroups(groups -> repository.getAll(entries -> FragmentUi.run(this, () -> {
            List<PasswordGroup> snapshot = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
            if (snapshot.isEmpty()) {
                PasswordGroup fallback = new PasswordGroup();
                fallback.id = PasswordGroup.DEFAULT_ID;
                fallback.name = PasswordGroup.DEFAULT_NAME;
                fallback.isDefault = true;
                fallback.sortOrder = 0;
                fallback.createdAt = System.currentTimeMillis();
                fallback.updatedAt = fallback.createdAt;
                snapshot.add(fallback);
            }
            Map<String, Integer> counts = new HashMap<>();
            if (entries != null) {
                for (PasswordEntry entry : entries) {
                    String groupId = firstNonEmpty(entry.groupId, PasswordGroup.DEFAULT_ID);
                    counts.put(groupId, counts.getOrDefault(groupId, 0) + 1);
                }
            }
            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(20), dp(8), dp(20), 0);
            Button add = new Button(requireContext());
            add.setText(R.string.add_password_group);
            add.setOnClickListener(v -> showGroupNameDialog(null));
            content.addView(add);
            for (PasswordGroup group : snapshot) {
                content.addView(buildGroupRow(group, counts.getOrDefault(group.id, 0)));
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.manage_password_groups)
                    .setView(content)
                    .setPositiveButton(R.string.close, null)
                    .show();
        })));
    }

    private View buildGroupRow(PasswordGroup group, int count) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView title = new TextView(requireContext());
        title.setText(groupDisplayName(group) + "  (" + count + ")");
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        title.setTextSize(15);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button rename = new Button(requireContext());
        rename.setText(R.string.rename);
        rename.setOnClickListener(v ->
                OperationModeGuard.requireEdit(this, () -> showGroupNameDialog(group)));
        row.addView(rename);

        if (!group.isDefault && !PasswordGroup.DEFAULT_ID.equals(group.id)
                && !PasswordGroup.SECURE_SHARE_ID.equals(group.id)) {
            Button delete = new Button(requireContext());
            delete.setText(R.string.delete);
            delete.setOnClickListener(v ->
                    OperationModeGuard.requireEdit(this, () -> confirmDeleteGroup(group, count)));
            row.addView(delete);
        }
        return row;
    }

    private void showGroupNameDialog(@Nullable PasswordGroup group) {
        EditText input = input(getString(R.string.group_name_hint));
        if (group != null) input.setText(groupDisplayName(group));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(group == null ? R.string.add_password_group : R.string.rename_password_group)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(getString(R.string.group_name_empty));
                return;
            }
            if (group == null) {
                repository.createGroup(name);
            } else {
                repository.renameGroup(group, name);
            }
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showCreateGroupForEditor(CredentialEditor editor) {
        EditText input = input(getString(R.string.group_name_hint));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_password_group)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(getString(R.string.group_name_empty));
                return;
            }
            repository.createGroup(name, created -> FragmentUi.run(this, () -> {
                editor.availableGroups.add(created);
                editor.bindGroups(created.id);
            }));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void confirmDeleteGroup(PasswordGroup group, int count) {
        if (group == null || group.isDefault || PasswordGroup.DEFAULT_ID.equals(group.id)
                || PasswordGroup.SECURE_SHARE_ID.equals(group.id)) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(count > 0
                        ? getString(R.string.delete_password_group_with_records, count)
                        : getString(R.string.delete_password_group_message))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> repository.deleteGroup(group.id))
                .show();
    }

    private void showExportFormatDialog() {
        ExportSecurityGuard.require(requireActivity(), getString(R.string.export_auth_prompt), this::showExportPasswordChoice);
    }

    private void showExportPasswordChoice() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_export_open_password_title)
                .setMessage(R.string.password_export_open_password_message)
                .setPositiveButton(R.string.password_export_yes, (dialog, which) -> showExportPasswordInput())
                .setNegativeButton(R.string.password_export_no, (dialog, which) -> exportEntries(""))
                .show();
    }

    private void showExportPasswordInput() {
        EditText input = input(getString(R.string.password_export_password_hint));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_export_set_password_title)
                .setView(input)
                .setNegativeButton(R.string.common_action_cancel, null)
                .setPositiveButton(R.string.password_export_action, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = input.getText().toString();
            if (password.isEmpty()) {
                input.setError(getString(R.string.password_export_password_required));
                return;
            }
            dialog.dismiss();
            exportEntries(password);
        }));
        dialog.show();
    }

    private void exportEntries(String password) {
        repository.getAll(entries -> {
            try {
                File exported = writeExport(entries, password);
                FragmentUi.run(PasswordForgeFragment.this, () -> {
                    Toast.makeText(requireContext(), R.string.password_export_success, Toast.LENGTH_SHORT).show();
                    shareFile(exported, EXCEL_MIME);
                });
            } catch (Exception e) {
                FragmentUi.run(PasswordForgeFragment.this, () -> Toast.makeText(requireContext(), getString(R.string.password_export_error_format, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private File writeExport(List<PasswordEntry> entries, String password) throws Exception {
        File dir = new File(requireContext().getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException(getString(R.string.password_export_directory_failed));
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "keyscan_passwords_" + stamp + ".xlsx");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        List<List<String>> rows = new ArrayList<>();
        for (PasswordEntry entry : entries) {
            rows.add(Arrays.asList(entry.remark, entry.account, entry.password, format.format(new Date(entry.createdAt))));
        }
        byte[] data = ExcelExportHelper.workbookBytes("Passwords", Arrays.asList(
                getString(R.string.password_export_column_website),
                getString(R.string.password_export_column_account),
                getString(R.string.password_export_column_password),
                getString(R.string.password_export_column_created)), rows, password);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
        return file;
    }

    private void shareFile(File file, String mime) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.password_export_share)));
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

