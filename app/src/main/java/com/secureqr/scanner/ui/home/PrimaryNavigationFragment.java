package com.secureqr.scanner.ui.home;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.OperationModeManager;
import com.secureqr.scanner.security.PasswordSecurityCheck;
import com.secureqr.scanner.ui.settings.LanguageSettingsFragment;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.GlobalWebDavSyncUi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * First-stage shell for the redesigned primary navigation.
 * Existing feature fragments remain untouched and are opened through HomeActions.
 */
public class PrimaryNavigationFragment extends Fragment {
    private static final String STATE_PAGE = "primary_page";
    private static final String STATE_CORE_CARD = "primary_core_card";
    private static final int PAGE_TOOLS = 0;
    private static final int PAGE_HOME = 1;
    private static final int PAGE_DATA = 2;
    private static final int MENU_EXPORT_DATA = 1002;
    private static final String CONTACT_EMAIL = "userfeedback@zohomail.com";

    private HomeFragment.HomeActions actions;
    private View toolsPage;
    private View homePage;
    private View dataPage;
    private View toolsNav;
    private View homeNav;
    private View dataNav;
    private View subtitle;
    private int selectedPage = PAGE_HOME;
    private int selectedCoreCard = 0;
    private View[] coreCards;
    private TextView[] pageDots;
    private float coreTouchY;
    private float coreTouchX;
    private boolean coreSwipeHandled;
    private int touchedCoreCard = -1;
    private boolean pageAnimating;
    private PrimaryPageSwipeContainer pageContainer;
    private int pageDragTarget = -1;
    private TextView passwordSummary;
    private TextView vaultSummary;
    private TextView otpSummary;
    private ImageView securityBiometricIcon;
    private ImageView securityEncryptionIcon;
    private ImageView securityDuplicateIcon;
    private TextView securityBiometricText;
    private TextView securityEncryptionText;
    private TextView securityDuplicateText;
    private boolean coreDataObserversAttached;
    private int passwordCount;
    private int vaultCount;
    private int otpCount;
    private int duplicatePasswordCount = -1;
    private ImageButton operationLockButton;
    private TextView operationModeLabel;
    private long lastLockClickAt;
    private Runnable pendingModeToggle;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof HomeFragment.HomeActions) {
            actions = (HomeFragment.HomeActions) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_primary_navigation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            selectedCoreCard = savedInstanceState.getInt(STATE_CORE_CARD, 0);
        }
        // Tools and data management remain available through quick access and
        // their existing routes, while Home is the only visible top-level page.
        selectedPage = PAGE_HOME;

        toolsPage = view.findViewById(R.id.primary_page_tools);
        homePage = view.findViewById(R.id.primary_page_home);
        dataPage = view.findViewById(R.id.primary_page_data);
        toolsNav = view.findViewById(R.id.primary_nav_tools);
        homeNav = view.findViewById(R.id.primary_nav_home);
        dataNav = view.findViewById(R.id.primary_nav_data);
        subtitle = view.findViewById(R.id.primary_brand_subtitle);
        pageDots = new TextView[]{
                view.findViewById(R.id.primary_page_dot_tools),
                view.findViewById(R.id.primary_page_dot_home),
                view.findViewById(R.id.primary_page_dot_data)
        };

        pageContainer = view.findViewById(R.id.primary_page_container);
        pageContainer.setPageSwipeEnabled(false);

        bind(view, R.id.primary_tool_scan, () -> actions.openScanner());
        bind(view, R.id.primary_tool_share, () -> actions.openGenerator());
        bind(view, R.id.primary_tool_password_generator, () -> actions.openRandomPasswordGenerator());
        bind(view, R.id.primary_core_password, () -> actions.openPasswordForge());
        bind(view, R.id.primary_core_vault, () -> actions.openPasswordNotes());
        bind(view, R.id.primary_core_otp, () -> actions.openOtpAuth());
        bind(view, R.id.primary_data_backup, () -> actions.openWebDav());
        bind(view, R.id.primary_data_export, () -> actions.openGenericExport());
        bind(view, R.id.primary_data_trash, () -> actions.openTrash());
        bind(view, R.id.primary_data_history, () -> actions.openHistory());
        view.findViewById(R.id.primary_action_add).setOnClickListener(v -> openAddContentPage());
        view.findViewById(R.id.primary_action_cloud_sync).setOnClickListener(v -> GlobalWebDavSyncUi.start(this));
        GlobalWebDavSyncUi.bindState(this, view.findViewById(R.id.primary_action_cloud_sync));
        view.findViewById(R.id.primary_action_more).setOnClickListener(this::showOverflowMenu);
        operationLockButton = view.findViewById(R.id.primary_operation_lock);
        operationModeLabel = view.findViewById(R.id.primary_operation_mode_label);
        operationLockButton.setOnClickListener(this::handleOperationLockClick);
        renderOperationMode();

        setupCoreCardStack(view);
        bindSecurityStatus(view);
        observeCoreCardData(view);

        selectPage(PAGE_HOME);
    }

    private void observeCoreCardData(@NonNull View root) {
        passwordSummary = root.findViewById(R.id.primary_core_password_summary);
        vaultSummary = root.findViewById(R.id.primary_core_vault_summary);
        otpSummary = root.findViewById(R.id.primary_core_otp_summary);
        passwordSummary.setText(R.string.primary_password_locked_summary);
        vaultSummary.setText(R.string.primary_vault_locked_summary);
        otpSummary.setText(R.string.primary_otp_locked_summary);
        attachCoreDataObservers();
    }

    /**
     * 首页只展示数量和安全汇总，不展示账号、密码或 OTP 明文；因此不应依赖
     * 指纹/PIN 验证。敏感内容的读取与编辑仍由各自页面的访问保护负责。
     */
    private void attachCoreDataObservers() {
        if (coreDataObserversAttached || !isAdded()) return;
        coreDataObserversAttached = true;

        // 首页仅在内存中计算数量和风险汇总，不将任何条目的明文绑定到界面。
        // 不经过各业务仓库的内容访问门禁，避免未验证时 OTP 仓库主动抛出异常。
        AppDatabase database = AppDatabase.getInstance(requireContext());
        database.passwordEntryDao().observeAll()
                .observe(getViewLifecycleOwner(), entries -> {
                    passwordCount = entries == null ? 0 : entries.size();
                    passwordSummary.setText(getString(R.string.primary_password_summary, passwordCount));
                    updateDuplicatePasswordStatus(entries);
                });
        database.vaultItemDao().observeAll()
                .observe(getViewLifecycleOwner(), items -> {
                    vaultCount = items == null ? 0 : items.size();
                    vaultSummary.setText(getString(R.string.primary_vault_summary, vaultCount));
                    renderSelectedCardSecurityStatus();
                });
        database.otpTokenDao().observe("")
                .observe(getViewLifecycleOwner(), tokens -> {
                    otpCount = tokens == null ? 0 : tokens.size();
                    otpSummary.setText(getString(R.string.primary_otp_summary, otpCount));
                    renderSelectedCardSecurityStatus();
                });
    }

    private void bindSecurityStatus(@NonNull View root) {
        securityBiometricIcon = root.findViewById(R.id.primary_security_biometric_icon);
        securityEncryptionIcon = root.findViewById(R.id.primary_security_encryption_icon);
        securityDuplicateIcon = root.findViewById(R.id.primary_security_duplicate_icon);
        securityBiometricText = root.findViewById(R.id.primary_security_biometric_text);
        securityEncryptionText = root.findViewById(R.id.primary_security_encryption_text);
        securityDuplicateText = root.findViewById(R.id.primary_security_duplicate_text);
        renderSelectedCardSecurityStatus();
    }

    private void refreshStaticSecurityStatus() {
        renderSelectedCardSecurityStatus();
    }

    private void updateDuplicatePasswordStatus(@Nullable List<PasswordEntry> entries) {
        if (securityDuplicateText == null) return;
        PasswordSecurityCheck.Result result = PasswordSecurityCheck.analyze(requireContext(), entries);
        int affectedAccounts = result.riskCount();
        boolean healthy = affectedAccounts == 0;
        duplicatePasswordCount = affectedAccounts;
        securityDuplicateIcon.setImageResource(healthy
                ? R.drawable.ic_shield
                : R.drawable.ic_security_warning_24);
        securityDuplicateText.setText(healthy
                ? getString(R.string.primary_security_duplicate_none)
                : getString(R.string.primary_security_duplicate_found, affectedAccounts));
        setSecurityRowColor(securityDuplicateIcon, securityDuplicateText, healthy);
        renderSelectedCardSecurityStatus();
    }

    private void renderSelectedCardSecurityStatus() {
        if (!isAdded() || securityBiometricText == null) return;
        boolean encrypted = DatabaseKeyManager.isDatabaseProtectionEnabled(requireContext());
        securityBiometricIcon.setImageResource(R.drawable.ic_shield);
        securityEncryptionIcon.setImageResource(R.drawable.ic_security_lock_24);
        securityDuplicateIcon.setImageResource(R.drawable.ic_shield);

        if (selectedCoreCard == 1) {
            securityBiometricText.setText(getString(R.string.primary_vault_status_count, vaultCount));
            securityEncryptionText.setText(encrypted
                    ? R.string.primary_vault_status_local_encrypted
                    : R.string.primary_security_data_not_encrypted);
            securityDuplicateText.setText(R.string.primary_vault_status_attachments_encrypted);
            setSecurityRowColor(securityBiometricIcon, securityBiometricText, true);
            setSecurityRowColor(securityEncryptionIcon, securityEncryptionText, encrypted);
            setSecurityRowColor(securityDuplicateIcon, securityDuplicateText, true);
            return;
        }
        if (selectedCoreCard == 2) {
            securityBiometricText.setText(getString(R.string.primary_otp_status_count, otpCount));
            securityEncryptionText.setText(encrypted
                    ? R.string.primary_otp_status_key_encrypted
                    : R.string.primary_security_data_not_encrypted);
            securityDuplicateText.setText(R.string.primary_otp_status_secure_storage);
            setSecurityRowColor(securityBiometricIcon, securityBiometricText, true);
            setSecurityRowColor(securityEncryptionIcon, securityEncryptionText, encrypted);
            setSecurityRowColor(securityDuplicateIcon, securityDuplicateText, true);
            return;
        }

        securityBiometricText.setText(getString(R.string.primary_password_status_count, passwordCount));
        securityEncryptionText.setText(encrypted
                ? R.string.primary_security_data_encrypted
                : R.string.primary_security_data_not_encrypted);
        setSecurityRowColor(securityBiometricIcon, securityBiometricText, true);
        setSecurityRowColor(securityEncryptionIcon, securityEncryptionText, encrypted);
        boolean duplicateHealthy = duplicatePasswordCount == 0;
        securityDuplicateIcon.setImageResource(duplicateHealthy ? R.drawable.ic_shield : R.drawable.ic_security_warning_24);
        if (duplicatePasswordCount < 0) securityDuplicateText.setText(R.string.primary_security_duplicate_locked);
        else securityDuplicateText.setText(duplicateHealthy
                ? getString(R.string.primary_security_duplicate_none)
                : getString(R.string.primary_security_duplicate_found, duplicatePasswordCount));
        setSecurityRowColor(securityDuplicateIcon, securityDuplicateText, duplicateHealthy);
    }

    private void setSecurityRowColor(ImageView icon, TextView text, boolean healthy) {
        int color = requireContext().getColor(healthy ? R.color.success : R.color.warning);
        icon.setColorFilter(color);
        text.setTextColor(color);
    }

    @Override
    public void onResume() {
        super.onResume();
        renderOperationMode();
        refreshStaticSecurityStatus();
        attachCoreDataObservers();
        restoreCoreCardStack();
    }

    private void handleOperationLockClick(View button) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastLockClickAt <= 320L) {
            if (pendingModeToggle != null) button.removeCallbacks(pendingModeToggle);
            pendingModeToggle = null;
            lastLockClickAt = 0L;
            if (getActivity() instanceof com.secureqr.scanner.MainActivity) {
                ((com.secureqr.scanner.MainActivity) getActivity()).lockApplicationNow();
            }
            OperationModeGuard.feedback(this, R.string.operation_app_locked);
            return;
        }
        lastLockClickAt = now;
        pendingModeToggle = () -> {
            OperationModeManager.Mode mode = OperationModeManager.toggleViewEdit(requireContext());
            renderOperationMode();
            OperationModeGuard.feedback(this, mode == OperationModeManager.Mode.EDIT
                    ? R.string.operation_edit_enabled : R.string.operation_view_enabled);
            pendingModeToggle = null;
            lastLockClickAt = 0L;
        };
        button.postDelayed(pendingModeToggle, 320L);
    }

    private void renderOperationMode() {
        if (!isAdded() || operationLockButton == null) return;
        boolean edit = OperationModeManager.current(requireContext()) == OperationModeManager.Mode.EDIT;
        operationLockButton.setImageResource(edit
                ? R.drawable.ic_operation_lock_open : R.drawable.ic_operation_lock_closed);
        operationLockButton.setColorFilter(requireContext().getColor(
                edit ? R.color.settings_teal : R.color.text_secondary));
        operationLockButton.setContentDescription(getString(edit
                ? R.string.operation_lock_edit_desc : R.string.operation_lock_view_desc));
        operationModeLabel.setText(edit ? R.string.operation_edit_mode : R.string.operation_view_mode_short);
        operationModeLabel.setTextColor(requireContext().getColor(
                edit ? R.color.settings_teal : R.color.text_secondary));
        // The mode label sits directly below this control.  Keeping the button at its
        // fixed 44dp size prevents the edit-mode highlight from being clipped at the top.
        operationLockButton.animate().cancel();
        operationLockButton.setScaleX(1f);
        operationLockButton.setScaleY(1f);
    }

    @Override
    public void onPause() {
        restoreCoreCardStackNow();
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) restoreCoreCardStack();
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(R.string.security_center_title);
        menu.getMenu().add(R.string.settings_title);
        menu.getMenu().add(R.string.language);
        menu.getMenu().add(R.string.help_title);
        menu.getMenu().add(R.string.about_keyscan);
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (getString(R.string.security_center_title).equals(title)) {
                if (actions != null) actions.openSecurityCenter();
            } else if (getString(R.string.settings_title).equals(title)) {
                if (actions != null) actions.openAppearance();
            } else if (getString(R.string.language).equals(title)) {
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new LanguageSettingsFragment())
                        .addToBackStack(null)
                        .commit();
            } else if (getString(R.string.help_title).equals(title)) {
                showHelpCenter();
            } else if (getString(R.string.about_keyscan).equals(title)) {
                if (actions != null) actions.openAbout();
            }
            return true;
        });
        menu.show();
    }

    private void openAddContentPage() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new AddContentFragment())
                .addToBackStack(null)
                .commit();
    }

    private void showQuickAddMenu(View anchor) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_quick_add_panel);
        content.setPadding((int) dp(18), (int) dp(10), (int) dp(18), (int) dp(20));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.primary_quick_add_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .create();
        addQuickAddAction(content, R.drawable.ic_scan, R.string.home_scan,
                R.color.settings_blue, () -> actions.openScanner(), dialog);
        addQuickAddHeading(content, R.string.primary_quick_add_create_group);
        addQuickAddAction(content, R.drawable.ic_book_key, R.string.password_add_record,
                R.color.settings_purple, () -> actions.openNewPasswordRecord(), dialog);
        addQuickAddAction(content, R.drawable.ic_shield, R.string.primary_quick_add_secure_item,
                R.color.settings_green, () -> actions.openNewSecureItem(), dialog);
        addQuickAddHeading(content, R.string.primary_quick_add_import_group);
        addQuickAddAction(content, R.drawable.ic_download_24, R.string.import_password_book,
                R.color.settings_orange, () -> actions.openPasswordBookImport(), dialog);
        addQuickAddAction(content, R.drawable.ic_key_line, R.string.primary_otp_manual_import,
                R.color.settings_teal, () -> actions.openOtpManualImport(), dialog);
        addQuickAddAction(content, R.drawable.ic_vault_file_lock, R.string.primary_otp_batch_import,
                R.color.settings_blue, () -> actions.openOtpBatchImport(), dialog);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(android.view.Gravity.BOTTOM);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.58f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void addQuickAddHeading(LinearLayout parent, int labelRes) {
        TextView heading = new TextView(requireContext());
        heading.setText(labelRes);
        heading.setTextColor(requireContext().getColor(R.color.text_secondary));
        heading.setTextSize(12);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding((int) dp(4), (int) dp(14), (int) dp(4), (int) dp(6));
        parent.addView(heading);
    }

    private void addQuickAddAction(LinearLayout parent, int iconRes, int labelRes, int colorRes,
                                   Runnable action, AlertDialog dialog) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_quick_add_row);
        row.setPadding((int) dp(12), 0, (int) dp(14), 0);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        int color = requireContext().getColor(colorRes);
        icon.setColorFilter(android.graphics.Color.WHITE);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(color);
        iconBackground.setCornerRadius(dp(13));
        icon.setBackground(iconBackground);
        icon.setPadding((int) dp(10), (int) dp(10), (int) dp(10), (int) dp(10));
        row.addView(icon, new LinearLayout.LayoutParams((int) dp(46), (int) dp(46)));
        TextView label = new TextView(requireContext());
        label.setText(labelRes);
        label.setTextColor(requireContext().getColor(R.color.text_main));
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding((int) dp(14), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(requireContext());
        arrow.setText("›");
        arrow.setTextColor(requireContext().getColor(R.color.text_secondary));
        arrow.setTextSize(28);
        row.addView(arrow, new LinearLayout.LayoutParams((int) dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (actions != null) action.run();
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(64));
        rowParams.bottomMargin = (int) dp(7);
        parent.addView(row, rowParams);
    }

    private void openEmailFeedback() {
        String version = appVersion();
        String subject = Uri.encode(getString(R.string.feedback_subject_template, version));
        String body = Uri.encode(getString(R.string.feedback_body_template, version, Build.MODEL, Build.VERSION.RELEASE));
        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + CONTACT_EMAIL + "?subject=" + subject + "&body=" + body));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(requireContext(), getString(R.string.no_email_app, CONTACT_EMAIL), Toast.LENGTH_LONG).show();
        }
    }

    private void showDonateDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) dp(18), (int) dp(12), (int) dp(18), (int) dp(8));

        TextView message = new TextView(requireContext());
        message.setText(R.string.donate_message);
        message.setTextColor(requireContext().getColor(R.color.text_main));
        message.setTextSize(16);
        message.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(message);
        addDonateQr(content, R.string.wechat, R.drawable.donate_wechat_qr);
        addDonateQr(content, R.string.alipay, R.drawable.donate_alipay_qr);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.donate_title)
                .setView(scroll)
                .setPositiveButton(R.string.thanks, null)
                .show();
    }

    private void addDonateQr(LinearLayout parent, int labelRes, int imageRes) {
        TextView label = new TextView(requireContext());
        label.setText(labelRes);
        label.setTextColor(requireContext().getColor(R.color.text_main));
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, (int) dp(14), 0, (int) dp(6));
        parent.addView(label);
        ImageView qr = new ImageView(requireContext());
        qr.setImageBitmap(BitmapFactory.decodeResource(getResources(), imageRes));
        qr.setAdjustViewBounds(true);
        qr.setContentDescription(getString(labelRes));
        parent.addView(qr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(220)));
    }

    private void showHelpCenter() {
        startActivity(new Intent(requireContext(), com.secureqr.scanner.ui.help.HelpManualActivity.class));
    }

    private void addHelpSection(LinearLayout parent, int titleRes, int bodyRes) {
        TextView title = helpText(getString(titleRes), true);
        title.setPadding(0, (int) dp(12), 0, (int) dp(4));
        parent.addView(title);
        parent.addView(helpText(getString(bodyRes), false));
    }

    private TextView helpText(String value, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextColor(requireContext().getColor(bold ? R.color.text_main : R.color.text_secondary));
        text.setTextSize(bold ? 16 : 14);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private String appVersion() {
        try {
            return requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "1.0.0";
        }
    }

    private void setupCoreCardStack(View root) {
        coreCards = new View[]{
                root.findViewById(R.id.primary_core_password),
                root.findViewById(R.id.primary_core_vault),
                root.findViewById(R.id.primary_core_otp)
        };
        View stack = root.findViewById(R.id.primary_core_stack);
        stack.setOnTouchListener((v, event) -> handleCoreStackTouch(v, event));
        for (View card : coreCards) {
            card.setOnTouchListener((v, event) -> handleCoreStackTouch(v, event));
        }
        stack.post(this::restoreCoreCardStackNow);
    }

    private boolean handleCoreStackTouch(View stack, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchedCoreCard = coreCardIndex(stack);
                coreTouchX = event.getX();
                coreTouchY = event.getY();
                coreSwipeHandled = false;
                for (View card : coreCards) card.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - coreTouchX;
                float dy = event.getY() - coreTouchY;
                if (Math.abs(dy) > dp(12) && Math.abs(dy) > Math.abs(dx)) {
                    coreSwipeHandled = true;
                    stack.getParent().requestDisallowInterceptTouchEvent(true);
                    previewCoreCardDrag(dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
                stack.getParent().requestDisallowInterceptTouchEvent(false);
                float distance = event.getY() - coreTouchY;
                if (Math.abs(distance) >= dp(48)) {
                    selectedCoreCard = distance < 0
                            ? (selectedCoreCard + 1) % coreCards.length
                            : (selectedCoreCard + coreCards.length - 1) % coreCards.length;
                    arrangeCoreCards(true);
                } else if (!coreSwipeHandled) {
                    int target = touchedCoreCard >= 0 ? touchedCoreCard : selectedCoreCard;
                    coreCards[target].performClick();
                } else {
                    arrangeCoreCards(true);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                stack.getParent().requestDisallowInterceptTouchEvent(false);
                arrangeCoreCards(true);
                touchedCoreCard = -1;
                return true;
            default:
                return true;
        }
    }

    private int coreCardIndex(View candidate) {
        if (coreCards == null) return -1;
        for (int i = 0; i < coreCards.length; i++) {
            if (coreCards[i] == candidate) return i;
        }
        return -1;
    }

    private void previewCoreCardDrag(float distance) {
        float limited = Math.max(-dp(84), Math.min(dp(84), distance));
        int middle = (selectedCoreCard + coreCards.length - 1) % coreCards.length;
        int back = (selectedCoreCard + coreCards.length - 2) % coreCards.length;
        coreCards[selectedCoreCard].setY(dp(168) + limited * 0.42f);
        coreCards[middle].setY(dp(88) + limited * 0.14f);
        coreCards[back].setY(dp(8) + limited * 0.07f);
    }

    private void arrangeCoreCards(boolean animate) {
        if (coreCards == null || !isAdded()) return;

        for (View card : coreCards) {
            card.setVisibility(View.VISIBLE);
            card.setAlpha(1f);
            card.setTranslationX(0f);
            card.setRotation(0f);
        }

        int middle = (selectedCoreCard + coreCards.length - 1) % coreCards.length;
        int back = (selectedCoreCard + coreCards.length - 2) % coreCards.length;
        applyCoreCardSlot(coreCards[back], 8, 1f, 2f, animate);
        coreCards[back].bringToFront();
        applyCoreCardSlot(coreCards[middle], 88, 1f, 5f, animate);
        coreCards[middle].bringToFront();
        applyCoreCardSlot(coreCards[selectedCoreCard], 168, 1f, 9f, animate);
        coreCards[selectedCoreCard].bringToFront();
        renderSelectedCardSecurityStatus();

    }

    private void restoreCoreCardStack() {
        if (homePage != null) homePage.post(this::restoreCoreCardStackNow);
    }

    private void restoreCoreCardStackNow() {
        if (!isAdded() || getView() == null || coreCards == null) return;
        coreSwipeHandled = false;
        touchedCoreCard = -1;
        for (View card : coreCards) card.animate().cancel();
        arrangeCoreCards(false);
    }

    private void applyCoreCardSlot(View card, int topDp, float scale, float elevationDp,
                                   boolean animate) {
        float targetY = dp(topDp);
        if (animate) {
            card.animate()
                    .y(targetY)
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.7f))
                    .start();
        } else {
            card.setY(targetY);
            card.setScaleX(scale);
            card.setScaleY(scale);
        }
        card.setElevation(dp(elevationDp));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void bind(View root, int id, Runnable action) {
        root.findViewById(id).setOnClickListener(v -> {
            if (actions != null) action.run();
        });
    }

    private void selectPage(int page) {
        resetPageTransforms();
        selectedPage = page;
        toolsPage.setVisibility(page == PAGE_TOOLS ? View.VISIBLE : View.GONE);
        homePage.setVisibility(page == PAGE_HOME ? View.VISIBLE : View.GONE);
        dataPage.setVisibility(page == PAGE_DATA ? View.VISIBLE : View.GONE);
        subtitle.setVisibility(View.VISIBLE);
        updateNav(toolsNav, page == PAGE_TOOLS);
        updateNav(homeNav, page == PAGE_HOME);
        updateNav(dataNav, page == PAGE_DATA);
        updatePageDots(page);
        if (page == PAGE_HOME) restoreCoreCardStack();
    }

    private void handlePageSwipe(boolean left) {
        int target = pageTarget(left);
        if (target != selectedPage) selectPageAnimated(target, left);
        else cancelPageDrag();
    }

    private int pageTarget(boolean left) {
        if (selectedPage == PAGE_HOME) return left ? PAGE_TOOLS : PAGE_DATA;
        if (selectedPage == PAGE_TOOLS && !left) return PAGE_HOME;
        if (selectedPage == PAGE_DATA && left) return PAGE_HOME;
        return selectedPage;
    }

    private void handlePageDrag(float fraction) {
        if (pageAnimating || pageContainer == null) return;
        boolean left = fraction < 0f;
        int targetPage = pageTarget(left);
        View current = pageView(selectedPage);
        if (current == null) return;
        float width = Math.max(1f, pageContainer.getWidth());
        float offset = fraction * width;
        float progress = Math.min(1f, Math.abs(fraction));

        if (targetPage == selectedPage) {
            if (pageDragTarget >= 0) resetDraggedTarget(pageView(pageDragTarget));
            pageDragTarget = -1;
            current.setTranslationX(offset * 0.16f);
            current.setAlpha(1f - progress * 0.04f);
            return;
        }
        if (pageDragTarget >= 0 && pageDragTarget != targetPage) {
            resetDraggedTarget(pageView(pageDragTarget));
        }
        pageDragTarget = targetPage;
        View target = pageView(targetPage);
        if (target == null) return;
        target.setVisibility(View.VISIBLE);
        current.setTranslationX(offset);
        current.setAlpha(1f - progress * 0.12f);
        target.setTranslationX((left ? width : -width) + offset);
        target.setAlpha(0.88f + progress * 0.12f);
    }

    private void cancelPageDrag() {
        if (pageAnimating) return;
        View current = pageView(selectedPage);
        View target = pageDragTarget < 0 ? null : pageView(pageDragTarget);
        pageAnimating = true;
        if (current != null) {
            current.animate().translationX(0f).alpha(1f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(1.7f))
                    .withEndAction(() -> pageAnimating = false).start();
        } else {
            pageAnimating = false;
        }
        if (target != null) {
            float edge = target.getTranslationX() < 0f
                    ? -Math.max(1f, pageContainer.getWidth())
                    : Math.max(1f, pageContainer.getWidth());
            target.animate().translationX(edge).alpha(0.88f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(1.7f))
                    .withEndAction(() -> resetDraggedTarget(target)).start();
        }
        pageDragTarget = -1;
    }

    private void selectPageAnimated(int page, boolean left) {
        if (pageAnimating) return;
        View current = pageView(selectedPage);
        View target = pageView(page);
        if (current == null || target == null) {
            selectPage(page);
            return;
        }
        pageAnimating = true;
        float distance = Math.max(1f, pageContainer.getWidth());
        boolean continuingDrag = pageDragTarget == page;
        if (!continuingDrag) {
            target.setVisibility(View.VISIBLE);
            target.setAlpha(0.88f);
            target.setTranslationX(left ? distance : -distance);
        }
        float progress = Math.min(1f, Math.abs(current.getTranslationX()) / distance);
        long duration = Math.max(120L, (long) (280L * (1f - progress)));
        DecelerateInterpolator interpolator = new DecelerateInterpolator(1.7f);
        target.animate().alpha(1f).translationX(0f).setDuration(duration)
                .setInterpolator(interpolator).start();
        current.animate()
                .alpha(0.88f)
                .translationX(left ? -distance : distance)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(() -> {
                    current.setVisibility(View.GONE);
                    current.setAlpha(1f);
                    current.setTranslationX(0f);
                    pageAnimating = false;
                    if (selectedPage == PAGE_HOME) restoreCoreCardStack();
                })
                .start();
        pageDragTarget = -1;
        selectedPage = page;
        subtitle.setVisibility(View.VISIBLE);
        updateNav(toolsNav, page == PAGE_TOOLS);
        updateNav(homeNav, page == PAGE_HOME);
        updateNav(dataNav, page == PAGE_DATA);
        updatePageDots(page);
    }

    private void resetDraggedTarget(View target) {
        if (target == null || target == pageView(selectedPage)) return;
        target.animate().cancel();
        target.setVisibility(View.GONE);
        target.setAlpha(1f);
        target.setTranslationX(0f);
    }

    private void resetPageTransforms() {
        for (int page = PAGE_TOOLS; page <= PAGE_DATA; page++) {
            View view = pageView(page);
            if (view == null) continue;
            view.animate().cancel();
            view.setAlpha(1f);
            view.setTranslationX(0f);
        }
        pageDragTarget = -1;
        pageAnimating = false;
    }

    private View pageView(int page) {
        if (page == PAGE_TOOLS) return toolsPage;
        if (page == PAGE_HOME) return homePage;
        if (page == PAGE_DATA) return dataPage;
        return null;
    }

    private void updateNav(View item, boolean selected) {
        int color = requireContext().getColor(selected ? R.color.primary_reference_blue : R.color.text_secondary);
        item.setBackgroundColor(requireContext().getColor(selected ? R.color.primary_nav_selected : android.R.color.transparent));
        TextView label = item.findViewWithTag("label");
        ImageView icon = item.findViewWithTag("icon");
        if (label != null) label.setTextColor(color);
        if (icon != null) icon.setColorFilter(color);
    }

    private void updatePageDots(int page) {
        if (pageDots == null) return;
        int accent = requireContext().getColor(R.color.primary_reference_blue);
        int muted = requireContext().getColor(R.color.border_soft);
        for (int i = 0; i < pageDots.length; i++) {
            pageDots[i].setTextColor(i == page ? accent : muted);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_PAGE, PAGE_HOME);
        outState.putInt(STATE_CORE_CARD, selectedCoreCard);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDetach() {
        actions = null;
        super.onDetach();
    }

    @Override
    public void onDestroyView() {
        passwordSummary = null;
        vaultSummary = null;
        otpSummary = null;
        securityBiometricIcon = null;
        securityEncryptionIcon = null;
        securityDuplicateIcon = null;
        securityBiometricText = null;
        securityEncryptionText = null;
        securityDuplicateText = null;
        coreDataObserversAttached = false;
        super.onDestroyView();
    }
}
