/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.AlertDialog
 *  android.app.AlertDialog$Builder
 *  android.content.ContentResolver
 *  android.content.ContentValues
 *  android.content.Context
 *  android.content.DialogInterface
 *  android.content.Intent
 *  android.database.Cursor
 *  android.graphics.Bitmap
 *  android.graphics.Bitmap$CompressFormat
 *  android.graphics.Bitmap$Config
 *  android.graphics.Canvas
 *  android.graphics.Color
 *  android.graphics.Paint
 *  android.graphics.Paint$Align
 *  android.graphics.Paint$FontMetrics
 *  android.graphics.Typeface
 *  android.net.Uri
 *  android.os.Bundle
 *  android.os.Handler
 *  android.os.Looper
 *  android.provider.MediaStore$Images$Media
 *  android.text.TextUtils
 *  android.util.Base64
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.ArrayAdapter
 *  android.widget.Button
 *  android.widget.EditText
 *  android.widget.ImageView
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.RadioButton
 *  android.widget.RadioGroup
 *  android.widget.Spinner
 *  android.widget.SpinnerAdapter
 *  android.widget.Switch
 *  android.widget.TextView
 *  android.widget.Toast
 *  androidx.activity.result.ActivityResultLauncher
 *  androidx.activity.result.contract.ActivityResultContract
 *  androidx.activity.result.contract.ActivityResultContracts$OpenMultipleDocuments
 *  androidx.annotation.NonNull
 *  androidx.annotation.Nullable
 *  androidx.fragment.app.Fragment
 *  com.secureqr.scanner.R$color
 *  com.secureqr.scanner.R$drawable
 *  com.secureqr.scanner.R$id
 *  com.secureqr.scanner.R$layout
 *  com.secureqr.scanner.R$string
 *  com.secureqr.scanner.clipboard.SecureClipboard
 *  com.secureqr.scanner.data.model.ScanRecord
 *  com.secureqr.scanner.data.repository.RecordRepository
 *  com.secureqr.scanner.lan.NearbyLanIdentityStore
 *  com.secureqr.scanner.lan.NearbyLanIdentityStore$Identity
 *  com.secureqr.scanner.lan.NearbyLanShareManager
 *  com.secureqr.scanner.lan.NearbyLanShareManager$IncomingInvite
 *  com.secureqr.scanner.lan.NearbyLanShareManager$Listener
 *  com.secureqr.scanner.lan.NearbyLanShareManager$Peer
 *  com.secureqr.scanner.network.NetworkAccessController
 *  com.secureqr.scanner.network.NetworkAccessController$Decision
 *  com.secureqr.scanner.network.NetworkAccessController$LanInfo
 *  com.secureqr.scanner.ui.generate.GenerateFragment$4
 *  com.secureqr.scanner.ui.scanner.ScannerFragment
 *  com.secureqr.scanner.utils.LanFileTransferServer
 *  com.secureqr.scanner.utils.LanFileTransferServer$FileItem
 *  com.secureqr.scanner.utils.LanFileTransferServer$Listener
 *  com.secureqr.scanner.utils.NavigationHelper
 *  com.secureqr.scanner.utils.QRGenerator
 *  org.json.JSONObject
 */
package com.secureqr.scanner.ui.generate;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import com.secureqr.scanner.R;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.lan.NearbyLanIdentityStore;
import com.secureqr.scanner.lan.NearbyLanShareManager;
import com.secureqr.scanner.network.NetworkAccessController;
import com.secureqr.scanner.ui.generate.GenerateFragment;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.utils.LanFileTransferServer;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.QRGenerator;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

public class GenerateFragment
extends Fragment {
    private static final long LINK_SHARE_TTL_MS = 300000L;
    private static final long FILE_SHARE_TTL_MS = 300000L;
    private static final int DEFAULT_MAX_RECEIVERS = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fileTransferMonitor = () -> {
        if (this.activeFileServer != null) {
            NetworkAccessController.LanInfo current = NetworkAccessController.activeLanInfo((Context)this.requireContext());
            if (current == null || this.activeFileStartLan == null || !current.ipv4.equals(this.activeFileStartLan.ipv4) || current.prefixLength != this.activeFileStartLan.prefixLength || !current.subnet.equals(this.activeFileStartLan.subnet)) {
                this.stopLanFileTransfer();
                this.updateFileTransferStatus(this.getString(R.string.lan_status_network_changed));
                Toast.makeText((Context)this.requireContext(), R.string.lan_network_changed_message, (int)1).show();
                return;
            }
            this.updateFileTransferStatus(this.activeFileServer.statusSummary());
            this.updateFileTransferDetails();
            this.handler.postDelayed(this.fileTransferMonitor, 1000L);
        }
    };
    private EditText input;
    private ImageView preview;
    private TextView placeholder;
    private Button styleButton;
    private Bitmap currentBitmap;
    private String qrStyle = "classic";
    private int foregroundColor = -16777216;
    private int backgroundColor = -1;
    private LinearLayout linkShareContent;
    private LinearLayout lanFileTransferContent;
    private TextView linkShareArrow;
    private TextView linkShareStatus;
    private TextView lanFileTransferArrow;
    private TextView lanFileTransferStatus;
    private TextView lanNetworkText;
    private TextView lanDetailsText;
    private TextView selectedTitle;
    private TextView selectedSummary;
    private TextView receiverSummary;
    private TextView receiverTitle;
    private LinearLayout selectedFilesContainer;
    private LinearLayout selectedActions;
    private Button nextButton;
    private TextView nearbyDeviceName;
    private TextView nearbyStatusText;
    private LinearLayout nearbyDevicesContainer;
    private LinearLayout incomingInvitesContainer;
    private Button editNearbyDeviceName;
    private final ArrayList<LanFileTransferServer.FileItem> selectedFiles = new ArrayList();
    private ActivityResultLauncher<String[]> filePicker;
    private ActivityResultLauncher<Uri> folderPicker;
    private LanFileTransferServer activeFileServer;
    private NetworkAccessController.LanInfo activeFileStartLan;
    private NearbyLanShareManager nearbyLanShareManager;
    private NearbyLanShareManager.Peer selectedReceiverPeer;
    private final ArrayList<NearbyLanShareManager.Peer> selectedReceiverPeers = new ArrayList<>();
    private final ArrayList<String> shownInviteIds = new ArrayList<>();
    private String selectedShareText = "";
    private String accessPassword = "";
    private int maxReceivers = 1;
    private boolean requirePassword = true;
    private boolean requireConfirmation = true;
    private boolean linkShareExpanded = true;
    private boolean lanFileExpanded = false;
    private boolean restartingFileShare;
    private boolean lanShareEnabled;
    private long lanShareExpiresAt;
    private int lanAutoCloseMinutes = 10;
    private final Runnable lanShareTimeout = () -> this.setLanShareEnabled(false);
    private final Runnable lanCountdownUpdater = new Runnable() {
        @Override public void run() {
            updateLanSessionUi();
            if (lanShareEnabled) handler.postDelayed(this, 1000L);
        }
    };

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.filePicker = this.registerForActivityResult((ActivityResultContract)new ActivityResultContracts.OpenMultipleDocuments(), this::onFilesPicked);
        this.folderPicker = this.registerForActivityResult((ActivityResultContract)new ActivityResultContracts.OpenDocumentTree(), this::onFolderPicked);
    }

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generate, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        this.input = (EditText)view.findViewById(R.id.et_input);
        this.preview = (ImageView)view.findViewById(R.id.iv_qr_preview);
        this.placeholder = (TextView)view.findViewById(R.id.tv_qr_placeholder);
        this.styleButton = (Button)view.findViewById(R.id.btn_qr_style);
        this.linkShareContent = (LinearLayout)view.findViewById(R.id.content_link_share);
        this.lanFileTransferContent = (LinearLayout)view.findViewById(R.id.content_lan_file_transfer);
        this.linkShareArrow = (TextView)view.findViewById(R.id.tv_link_share_arrow);
        this.linkShareStatus = (TextView)view.findViewById(R.id.tv_link_share_status);
        this.lanFileTransferArrow = (TextView)view.findViewById(R.id.tv_lan_file_transfer_arrow);
        this.lanFileTransferStatus = (TextView)view.findViewById(R.id.tv_lan_file_transfer_status);
        this.lanNetworkText = (TextView)view.findViewById(R.id.tv_lan_file_transfer_network);
        this.lanDetailsText = (TextView)view.findViewById(R.id.tv_lan_file_transfer_details);
        this.selectedTitle = (TextView)view.findViewById(R.id.tv_lan_selected_title);
        this.selectedSummary = (TextView)view.findViewById(R.id.tv_lan_selected_summary);
        this.receiverSummary = (TextView)view.findViewById(R.id.tv_lan_receiver_summary);
        this.receiverTitle = (TextView)view.findViewById(R.id.tv_lan_receiver_title);
        this.selectedFilesContainer = (LinearLayout)view.findViewById(R.id.ll_lan_selected_files);
        this.selectedActions = (LinearLayout)view.findViewById(R.id.ll_lan_selected_actions);
        this.nearbyDeviceName = (TextView)view.findViewById(R.id.tv_lan_device_name);
        this.nearbyStatusText = (TextView)view.findViewById(R.id.tv_lan_nearby_status);
        this.nearbyDevicesContainer = (LinearLayout)view.findViewById(R.id.ll_lan_nearby_devices);
        this.incomingInvitesContainer = (LinearLayout)view.findViewById(R.id.ll_lan_incoming_invites);
        this.editNearbyDeviceName = (Button)view.findViewById(R.id.btn_lan_edit_device_name);
        View legacyLocalDeviceCard = view.findViewById(R.id.card_lan_legacy_local_device);
        if (legacyLocalDeviceCard != null) legacyLocalDeviceCard.setVisibility(View.GONE);
        view.findViewById(R.id.btn_generate_home).setOnClickListener(v -> NavigationHelper.openHome((Fragment)this));
        View linkShareTitle = view.findViewById(R.id.row_link_share_title);
        View lanFileTransferTitle = view.findViewById(R.id.row_lan_file_transfer_title);
        if (linkShareTitle != null) {
            linkShareTitle.setClickable(true);
            linkShareTitle.setFocusable(true);
            linkShareTitle.setOnClickListener(v -> this.toggleLinkShare());
        }
        if (lanFileTransferTitle != null) {
            lanFileTransferTitle.setClickable(true);
            lanFileTransferTitle.setFocusable(true);
            lanFileTransferTitle.setOnClickListener(v -> this.toggleLanFileTransfer());
        }
        if (this.linkShareArrow != null) {
            this.linkShareArrow.setOnClickListener(v -> this.toggleLinkShare());
        }
        if (this.linkShareStatus != null) {
            this.linkShareStatus.setOnClickListener(v -> this.toggleLinkShare());
        }
        if (this.lanFileTransferArrow != null) {
            this.lanFileTransferArrow.setOnClickListener(v -> this.toggleLanFileTransfer());
        }
        if (this.lanFileTransferStatus != null) {
            this.lanFileTransferStatus.setOnClickListener(v -> this.toggleLanFileTransfer());
        }
        view.findViewById(R.id.qr_preview_container).setOnClickListener(v -> this.updateQR());
        view.findViewById(R.id.btn_save_png).setOnClickListener(v -> this.promptSavePng());
        view.findViewById(R.id.btn_qr_foreground).setOnClickListener(v -> this.showColorDialog(true));
        view.findViewById(R.id.btn_qr_background).setOnClickListener(v -> this.showColorDialog(false));
        this.styleButton.setOnClickListener(v -> this.showStyleDialog());
        Button chooseFilesButton = (Button)view.findViewById(R.id.btn_lan_send_files);
        chooseFilesButton.setOnClickListener(v -> this.pickFiles());
        view.findViewById(R.id.btn_lan_receive_files).setOnClickListener(v -> this.showShareTextDialog());
        View qrLinkShareButton = view.findViewById(R.id.btn_lan_qr_link_share);
        if (qrLinkShareButton != null) {
            qrLinkShareButton.setOnClickListener(v -> this.startNetworkReachableShare(true));
        }
        Button reselectButton = (Button)view.findViewById(R.id.btn_lan_reselect);
        reselectButton.setOnClickListener(v -> this.pickFiles());
        this.nextButton = (Button)view.findViewById(R.id.btn_lan_next);
        this.nextButton.setEnabled(false);
        this.nextButton.setOnClickListener(v -> this.showNearbySendConfirmDialog());
        view.findViewById(R.id.btn_lan_copy_password).setOnClickListener(v -> this.copyLanAccessPassword());
        view.findViewById(R.id.btn_lan_copy_share_link).setOnClickListener(v -> this.copyLanShareLink());
        view.findViewById(R.id.btn_lan_end_share).setOnClickListener(v -> this.confirmEndLanShare());
        view.findViewById(R.id.btn_lan_refresh_devices).setOnClickListener(v -> this.refreshNearbyDiscovery());
        view.findViewById(R.id.tv_lan_pasted_text).setOnClickListener(v -> {
            if (!selectedShareText.trim().isEmpty()) {
                selectedShareText = "";
                updateLanSessionUi();
                updateReceiverSelectionState();
            }
        });
        if (this.editNearbyDeviceName != null) {
            this.editNearbyDeviceName.setOnClickListener(v -> this.showNearbyDeviceNameDialog());
        }
        this.nearbyLanShareManager = NearbyLanShareManager.getInstance((Context)this.requireContext());
        this.nearbyLanShareManager.setListener(new NearbyLanShareManager.Listener() {
            @Override
            public void onStateChanged() {
                GenerateFragment.this.refreshNearbyDeviceSections();
                GenerateFragment.this.showPendingIncomingInvite();
            }

            @Override
            public void onError(String message) {
                if (GenerateFragment.this.isAdded()) {
                    Toast.makeText((Context)GenerateFragment.this.requireContext(), (CharSequence)(message == null || message.isEmpty() ? getString(R.string.nearby_device_error) : message), (int)0).show();
                }
            }
        });
        androidx.appcompat.widget.SwitchCompat lanSwitch = view.findViewById(R.id.switch_lan_file_share);
        lanSwitch.setChecked(false);
        lanSwitch.setOnCheckedChangeListener((button, checked) -> this.setLanShareEnabled(checked));
        view.findViewById(R.id.tv_lan_auto_close).setOnClickListener(v -> this.showLanAutoCloseDialog());
        this.setLanShareEnabled(false);
        this.getParentFragmentManager().setFragmentResultListener("keyscan_lan_file_transfer_scan_request", this.getViewLifecycleOwner(), (key, bundle) -> this.handleLanFileTransferQr(bundle.getString("keyscan_lan_file_transfer_scan_value", "")));
        this.setLinkShareExpanded(true);
        this.setLanFileTransferExpanded(false);
        this.updateLinkShareStatus();
        this.updateFileTransferStatus(getString(R.string.lan_status_not_started));
        this.updateFileTransferDetails();
        this.refreshNearbyDeviceSections();
    }

    public void onDestroyView() {
        this.stopLanFileTransfer();
        if (this.nearbyLanShareManager != null) {
            this.nearbyLanShareManager.setListener(null);
            this.nearbyLanShareManager.stop();
        }
        this.handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void toggleLinkShare() {
        boolean expand = !this.linkShareExpanded;
        this.setLinkShareExpanded(expand);
        if (expand) this.setLanFileTransferExpanded(false);
    }

    private void toggleLanFileTransfer() {
        boolean expand = !this.lanFileExpanded;
        this.setLanFileTransferExpanded(expand);
        if (expand) this.setLinkShareExpanded(false);
    }

    private void setLanShareEnabled(boolean enabled) {
        this.lanShareEnabled = enabled;
        View root = getView();
        if (root == null) return;
        LinearLayout content = root.findViewById(R.id.content_lan_file_transfer);
        for (int i = 2; i < content.getChildCount(); i++) content.getChildAt(i).setVisibility(enabled ? View.VISIBLE : View.GONE);
        View legacyLocalDeviceCard = root.findViewById(R.id.card_lan_legacy_local_device);
        if (legacyLocalDeviceCard != null) legacyLocalDeviceCard.setVisibility(View.GONE);
        View legacyReceiverTitle = root.findViewById(R.id.tv_lan_receiver_title);
        View legacyReceiverSummary = root.findViewById(R.id.tv_lan_receiver_summary);
        View legacyInvitesTitle = root.findViewById(R.id.tv_lan_incoming_title);
        View legacyInvites = root.findViewById(R.id.ll_lan_incoming_invites);
        if (legacyReceiverTitle != null) legacyReceiverTitle.setVisibility(View.GONE);
        if (legacyReceiverSummary != null) legacyReceiverSummary.setVisibility(View.GONE);
        if (legacyInvitesTitle != null) legacyInvitesTitle.setVisibility(View.GONE);
        if (legacyInvites != null) legacyInvites.setVisibility(View.GONE);
        if (enabled) {
            this.accessPassword = String.format(Locale.US, "%03d %03d", new java.security.SecureRandom().nextInt(1000), new java.security.SecureRandom().nextInt(1000));
            this.lanShareExpiresAt = lanAutoCloseMinutes > 0 ? System.currentTimeMillis() + lanAutoCloseMinutes * 60_000L : 0L;
            this.handler.removeCallbacks(this.lanShareTimeout);
            this.handler.removeCallbacks(this.lanCountdownUpdater);
            if (lanAutoCloseMinutes > 0) this.handler.postDelayed(this.lanShareTimeout, lanAutoCloseMinutes * 60_000L);
            this.handler.post(this.lanCountdownUpdater);
            if (this.nearbyLanShareManager != null) this.nearbyLanShareManager.start();
            this.updateFileTransferStatus(getString(R.string.status_enabled));
            this.refreshNearbyDeviceSections();
        } else {
            this.handler.removeCallbacks(this.lanShareTimeout);
            this.handler.removeCallbacks(this.lanCountdownUpdater);
            this.stopLanFileTransfer();
            if (this.nearbyLanShareManager != null) this.nearbyLanShareManager.stop();
            this.accessPassword = "";
            this.lanShareExpiresAt = 0L;
            this.selectedFiles.clear();
            this.selectedShareText = "";
            this.selectedReceiverPeers.clear();
            this.updateFileTransferStatus(getString(R.string.status_not_enabled));
        }
        this.updateLanSessionUi();
    }

    private void updateLanSessionUi() {
        View root = getView();
        if (root == null) return;
        TextView password = root.findViewById(R.id.tv_lan_access_password);
        TextView countdown = root.findViewById(R.id.tv_lan_countdown);
        TextView pasted = root.findViewById(R.id.tv_lan_pasted_text);
        if (password != null) password.setText(lanShareEnabled && !accessPassword.isEmpty() ? accessPassword : "--- ---");
        if (pasted != null) {
            pasted.setText(selectedShareText.trim().isEmpty() ? getString(R.string.share_no_text) : selectedShareText);
            pasted.setTextColor(getResources().getColor(selectedShareText.trim().isEmpty() ? R.color.text_secondary : R.color.text_main));
        }
        if (countdown != null) {
            if (!lanShareEnabled) countdown.setText(R.string.share_lan_disabled);
            else if (lanShareExpiresAt <= 0) countdown.setText(R.string.share_close_after_transfer);
            else {
                long seconds = Math.max(0L, (lanShareExpiresAt - System.currentTimeMillis() + 999L) / 1000L);
                countdown.setText(getString(R.string.share_close_countdown, seconds / 60L, seconds % 60L));
            }
        }
    }

    private String clipboardText() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence value = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(requireContext());
        return value == null ? "" : value.toString();
    }

    private void showShareTextDialog() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), 0);
        EditText edit = new EditText(requireContext());
        edit.setHint(R.string.share_text_hint);
        edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        edit.setMinLines(5);
        edit.setMaxLines(12);
        edit.setText(selectedShareText);
        edit.setSelection(edit.length());
        root.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button paste = new Button(requireContext());
        paste.setText(R.string.share_paste_clipboard);
        paste.setOnClickListener(v -> {
            String value = clipboardText();
            if (value.trim().isEmpty()) Toast.makeText(requireContext(), R.string.share_clipboard_empty, Toast.LENGTH_SHORT).show();
            else edit.getText().insert(Math.max(0, edit.getSelectionStart()), value);
        });
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        pasteParams.topMargin = dp(8);
        root.addView(paste, pasteParams);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_text_title).setView(root).setNeutralButton(R.string.share_clear, null)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok, null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> edit.setText(""));
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                selectedShareText = edit.getText().toString();
                updateLanSessionUi();
                updateReceiverSelectionState();
                updateFileTransferDetails();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void copyLanAccessPassword() {
        if (!lanShareEnabled || accessPassword.isEmpty()) {
            Toast.makeText(requireContext(), R.string.share_lan_enable_first, Toast.LENGTH_SHORT).show();
            return;
        }
        SecureClipboard.copySensitive(requireContext(), "KeyScan LAN access password", accessPassword);
        Toast.makeText(requireContext(), R.string.share_access_password_copied, Toast.LENGTH_SHORT).show();
    }

    private List<LanFileTransferServer.FileItem> currentShareItems() {
        ArrayList<LanFileTransferServer.FileItem> items = new ArrayList<>(selectedFiles);
        if (!selectedShareText.trim().isEmpty()) items.add(LanFileTransferServer.FileItem.fromText(selectedShareText));
        return items;
    }

    private boolean hasLanShareContent() {
        return !selectedFiles.isEmpty() || !selectedShareText.trim().isEmpty();
    }

    private void startNetworkReachableShare(boolean showQr) {
        if (!lanShareEnabled) {
            Toast.makeText(requireContext(), R.string.share_lan_enable_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasLanShareContent()) {
            Toast.makeText(requireContext(), R.string.share_choose_content_first, Toast.LENGTH_SHORT).show();
            return;
        }
        startLanFileTransfer(true, accessPassword, 3, false, showQr);
    }

    private void copyLanShareLink() {
        if (activeFileServer == null) startNetworkReachableShare(false);
        if (activeFileServer != null) {
            SecureClipboard.copySensitive(requireContext(), "KeyScan LAN share link", activeFileServer.getBrowserEntryUrl());
            Toast.makeText(requireContext(), R.string.share_link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmEndLanShare() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_end_title)
                .setMessage(R.string.share_end_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.share_end_title, (dialog, which) -> {
                    androidx.appcompat.widget.SwitchCompat toggle = getView() == null ? null : getView().findViewById(R.id.switch_lan_file_share);
                    if (toggle != null) toggle.setChecked(false); else setLanShareEnabled(false);
                }).show();
    }

    private void refreshNearbyDiscovery() {
        if (!lanShareEnabled || nearbyLanShareManager == null) {
            Toast.makeText(requireContext(), R.string.share_lan_enable_first, Toast.LENGTH_SHORT).show();
            return;
        }
        nearbyStatusText.setText(R.string.share_searching_nearby);
        nearbyLanShareManager.stop();
        nearbyLanShareManager.start();
        handler.postDelayed(this::refreshNearbyDeviceSections, 600L);
    }

    private void showLanAutoCloseDialog() {
        String[] labels = {getString(R.string.share_duration_5_minutes), getString(R.string.share_duration_10_minutes), getString(R.string.share_duration_30_minutes), getString(R.string.share_duration_60_minutes), getString(R.string.share_close_after_transfer)};
        int[] values = {5, 10, 30, 60, -1};
        int checked = lanAutoCloseMinutes == 5 ? 0 : lanAutoCloseMinutes == 30 ? 2 : lanAutoCloseMinutes == 60 ? 3 : lanAutoCloseMinutes < 0 ? 4 : 1;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_auto_close_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    lanAutoCloseMinutes = values[which];
                    TextView label = getView() == null ? null : getView().findViewById(R.id.tv_lan_auto_close);
                    if (label != null) label.setText(getString(R.string.share_auto_close_value, labels[which]));
                    if (lanShareEnabled) {
                        handler.removeCallbacks(lanShareTimeout);
                        lanShareExpiresAt = lanAutoCloseMinutes > 0 ? System.currentTimeMillis() + lanAutoCloseMinutes * 60_000L : 0L;
                        if (lanAutoCloseMinutes > 0) handler.postDelayed(lanShareTimeout, lanAutoCloseMinutes * 60_000L);
                    }
                    dialog.dismiss();
                }).show();
    }

    private void setLinkShareExpanded(boolean expanded) {
        this.linkShareExpanded = expanded;
        if (this.linkShareContent != null) {
            this.linkShareContent.setVisibility(expanded ? 0 : 8);
        }
        if (this.linkShareArrow != null) {
            this.linkShareArrow.setText((CharSequence)(expanded ? "\u25bc" : "\u25b6"));
        }
        this.updateLinkShareStatus();
    }

    private void setLanFileTransferExpanded(boolean expanded) {
        this.lanFileExpanded = expanded;
        if (this.lanFileTransferContent != null) {
            this.lanFileTransferContent.setVisibility(expanded ? 0 : 8);
        }
        if (this.lanFileTransferArrow != null) {
            this.lanFileTransferArrow.setText((CharSequence)(expanded ? "\u25bc" : "\u25b6"));
        }
        if (expanded) {
            this.updateFileTransferDetails();
        }
    }

    private void updateLinkShareStatus() {
        if (this.linkShareStatus != null) {
            this.linkShareStatus.setText(this.linkShareExpanded ? R.string.section_expanded : R.string.section_collapsed);
        }
    }

    private void updateFileTransferStatus(String status) {
        if (this.lanFileTransferStatus != null) {
            this.lanFileTransferStatus.setText((CharSequence)(status == null || status.isEmpty() ? getString(R.string.lan_status_not_started) : status));
        }
    }

    private void updateFileTransferDetails() {
        if (this.lanNetworkText == null || this.lanDetailsText == null) {
            return;
        }
        NetworkAccessController.LanInfo info = NetworkAccessController.currentLanInfo((Context)this.requireContext());
        NearbyLanIdentityStore.Identity identity = nearbyLanShareManager == null ? null : nearbyLanShareManager.getIdentity();
        String deviceName = identity == null ? "KeyScan" : identity.displayName;
        this.lanNetworkText.setText(getString(R.string.share_device_line, deviceName, getString(info == null ? R.string.share_connect_same_wifi : R.string.share_waiting_nearby)));
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.share_content_support_hint));
        if (this.selectedFiles.isEmpty()) {
            builder.append("\n\n").append(getString(R.string.lan_no_file_selected_summary));
        } else {
            builder.append("\n\n").append(getString(R.string.lan_selected_files_summary, this.selectedFiles.size(), this.formatSize(this.totalSelectedSize())));
        }
        this.lanDetailsText.setText((CharSequence)builder.toString());
        this.renderSelectedFiles();
    }

    private void renderSelectedFiles() {
        if (this.selectedFilesContainer == null || this.selectedTitle == null || this.selectedSummary == null || this.selectedActions == null) {
            return;
        }
        this.selectedFilesContainer.removeAllViews();
        boolean hasFiles = !this.selectedFiles.isEmpty();
        boolean hasContent = this.hasLanShareContent();
        this.selectedTitle.setVisibility(View.VISIBLE);
        this.selectedSummary.setVisibility(0);
        this.selectedActions.setVisibility(View.VISIBLE);
        this.selectedTitle.setText(R.string.share_content_title);
        if (!hasFiles) {
            this.selectedSummary.setText((CharSequence)this.getString(R.string.lan_no_files_selected));
            this.updateReceiverSelectionState();
            if (this.nextButton != null) this.nextButton.setEnabled(hasContent && !selectedReceiverPeers.isEmpty());
            return;
        }
        for (LanFileTransferServer.FileItem item : this.selectedFiles) {
            LinearLayout row = new LinearLayout(this.requireContext());
            row.setOrientation(0);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(10));
            row.setBackgroundResource(R.drawable.bg_card);
            TextView name = new TextView(this.requireContext());
            name.setText((CharSequence)(item.name + (item.risky ? getString(R.string.lan_risk_suffix) : "")));
            name.setTextColor(this.getResources().getColor(R.color.text_main));
            name.setTextSize(14.0f);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            TextView meta = new TextView(this.requireContext());
            meta.setText((CharSequence)(item.mimeType + " \u00b7 " + this.formatSize(item.size)));
            meta.setTextColor(this.getResources().getColor(R.color.text_secondary));
            meta.setTextSize(12.0f);
            LinearLayout fileTexts = new LinearLayout(requireContext());
            fileTexts.setOrientation(LinearLayout.VERTICAL);
            fileTexts.addView(name);
            fileTexts.addView(meta);
            Button remove = new Button(requireContext());
            remove.setText(R.string.share_remove);
            remove.setMinWidth(0);
            remove.setOnClickListener(v -> {
                selectedFiles.remove(item);
                updateFileTransferDetails();
            });
            row.addView(fileTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(remove, new LinearLayout.LayoutParams(dp(68), dp(40)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = this.dp(8);
            this.selectedFilesContainer.addView((View)row, (ViewGroup.LayoutParams)params);
        }
        this.selectedSummary.setText((CharSequence)this.getString(R.string.lan_selected_files_summary, new Object[]{this.selectedFiles.size(), this.formatSize(this.totalSelectedSize())}));
        this.updateReceiverSelectionState();
    }

    private void selectReceiverPeer(@NonNull NearbyLanShareManager.Peer peer) {
        NearbyLanShareManager.Peer existing = null;
        for (NearbyLanShareManager.Peer selected : selectedReceiverPeers) {
            if (selected.peerId.equals(peer.peerId)) { existing = selected; break; }
        }
        if (existing == null) selectedReceiverPeers.add(peer); else selectedReceiverPeers.remove(existing);
        this.selectedReceiverPeer = selectedReceiverPeers.isEmpty() ? null : selectedReceiverPeers.get(0);
        this.updateReceiverSelectionState();
        this.renderNearbyDevices();
    }

    private void updateReceiverSelectionState() {
        boolean hasFiles = hasLanShareContent();
        boolean hasPeer = !this.selectedReceiverPeers.isEmpty();
        if (this.receiverTitle != null) {
            this.receiverTitle.setVisibility(hasFiles ? 0 : 8);
        }
        if (this.nearbyStatusText != null) {
            this.nearbyStatusText.setVisibility(hasFiles ? 0 : 8);
        }
        if (this.nearbyDevicesContainer != null) {
            this.nearbyDevicesContainer.setVisibility(hasFiles ? 0 : 8);
        }
        if (this.receiverSummary != null) {
            if (hasFiles && hasPeer) {
                this.receiverSummary.setText(getString(R.string.share_receivers_selected, this.selectedReceiverPeers.size()));
                this.receiverSummary.setVisibility(0);
            } else {
                this.receiverSummary.setVisibility(8);
            }
        }
        if (this.nextButton != null) {
            this.nextButton.setEnabled(hasFiles && hasPeer);
        }
    }

    private long totalSelectedSize() {
        long total = 0L;
        for (LanFileTransferServer.FileItem item : this.selectedFiles) {
            total += Math.max(0L, item.size);
        }
        return total;
    }

    private void pickFiles() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_choose_content_title)
                .setItems(new String[]{getString(R.string.share_choose_file), getString(R.string.share_choose_folder)}, (dialog, which) -> {
                    if (which == 0 && filePicker != null) filePicker.launch(new String[]{"*/*"});
                    if (which == 1 && folderPicker != null) folderPicker.launch(null);
                }).show();
    }

    private void onFolderPicked(@Nullable Uri treeUri) {
        if (treeUri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        DocumentFile root = DocumentFile.fromTreeUri(requireContext(), treeUri);
        if (root == null || !root.isDirectory()) {
            Toast.makeText(requireContext(), R.string.share_folder_unreadable, Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<LanFileTransferServer.FileItem> folderFiles = new ArrayList<>();
        collectFolderFiles(root, safeDocumentName(root, getString(R.string.share_folder_fallback)), folderFiles);
        if (folderFiles.isEmpty()) {
            Toast.makeText(requireContext(), R.string.share_folder_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        selectedFiles.clear();
        selectedFiles.addAll(folderFiles);
        lanFileExpanded = true;
        if (lanFileTransferContent != null) lanFileTransferContent.setVisibility(View.VISIBLE);
        if (lanFileTransferArrow != null) lanFileTransferArrow.setText("▼");
        updateFileTransferStatus(getString(R.string.share_folder_selected));
        updateFileTransferDetails();
        refreshNearbyDeviceSections();
        Toast.makeText(requireContext(), getString(R.string.share_folder_files_loaded, folderFiles.size()), Toast.LENGTH_SHORT).show();
    }

    private void collectFolderFiles(DocumentFile directory, String relativePath, List<LanFileTransferServer.FileItem> output) {
        for (DocumentFile child : directory.listFiles()) {
            String childName = safeDocumentName(child, getString(child.isDirectory() ? R.string.share_folder_fallback : R.string.share_file_fallback));
            String childPath = relativePath + "/" + childName;
            if (child.isDirectory()) collectFolderFiles(child, childPath, output);
            else if (child.isFile() && child.canRead()) output.add(new LanFileTransferServer.FileItem(
                    child.getUri(), childPath, child.getType(), Math.max(0L, child.length())));
        }
    }

    private String safeDocumentName(DocumentFile file, String fallback) {
        String name = file == null ? null : file.getName();
        return TextUtils.isEmpty(name) ? fallback : name;
    }

    private void onFilesPicked(List<Uri> uris) {
        this.selectedFiles.clear();
        if (uris == null || uris.isEmpty()) {
            this.selectedReceiverPeer = null;
            this.updateReceiverSelectionState();
            this.updateFileTransferDetails();
            this.refreshNearbyDeviceSections();
            return;
        }
        ContentResolver resolver = this.requireContext().getContentResolver();
        for (Uri uri : uris) {
            try {
                try {
                    this.requireContext().getContentResolver().takePersistableUriPermission(uri, 1);
                }
                catch (Exception exception) {
                    // empty catch block
                }
                String name = this.queryDisplayName(resolver, uri);
                long size = this.querySize(resolver, uri);
                String mime = resolver.getType(uri);
                this.selectedFiles.add(new LanFileTransferServer.FileItem(uri, name, mime, size));
            }
            catch (Exception e) {
                Toast.makeText((Context)this.requireContext(), getString(R.string.lan_read_file_failed, e.getMessage()), (int)0).show();
            }
        }
        if (this.selectedFiles.isEmpty()) {
            Toast.makeText((Context)this.requireContext(), R.string.lan_no_usable_file, (int)0).show();
        } else {
            this.lanFileExpanded = true;
            if (this.lanFileTransferContent != null) {
                this.lanFileTransferContent.setVisibility(0);
            }
            if (this.lanFileTransferArrow != null) {
                this.lanFileTransferArrow.setText((CharSequence)"\u25bc");
            }
            this.updateFileTransferStatus(getString(R.string.lan_status_files_selected));
            this.updateReceiverSelectionState();
        }
        this.updateFileTransferDetails();
        this.refreshNearbyDeviceSections();
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{"_display_name"}, null, null, null);){
            String value;
            int index;
            if (cursor != null && cursor.moveToFirst() && (index = cursor.getColumnIndex("_display_name")) >= 0 && !TextUtils.isEmpty((CharSequence)(value = cursor.getString(index)))) {
                String string2 = value;
                return string2;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (uri.getLastPathSegment() == null) {
            return "file";
        }
        String string2 = uri.getLastPathSegment();
        return string2;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private long querySize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{"_size"}, null, null, null);){
            if (cursor == null) return 0L;
            if (!cursor.moveToFirst()) return 0L;
            int index = cursor.getColumnIndex("_size");
            if (index < 0) return 0L;
            long l = cursor.getLong(index);
            return l;
        }
        catch (Exception exception) {
            // empty catch block
        }
        return 0L;
    }

    private void showLanSendSettingsDialog(@Nullable NearbyLanShareManager.Peer targetPeer) {
        if (this.selectedFiles.isEmpty()) {
            Toast.makeText((Context)this.requireContext(), R.string.lan_select_files_first, (int)0).show();
            return;
        }
        if (targetPeer != null) {
            this.showNearbySendConfirmDialog(targetPeer);
            return;
        }
        LinearLayout root = new LinearLayout(this.requireContext());
        root.setOrientation(1);
        root.setPadding(this.dp(18), this.dp(12), this.dp(18), this.dp(8));
        TextView intro = new TextView(this.requireContext());
        intro.setText(R.string.lan_link_share_intro);
        intro.setTextColor(this.getResources().getColor(R.color.text_main));
        intro.setTextSize(18.0f);
        intro.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView((View)intro);
        TextView subtitle = new TextView(this.requireContext());
        subtitle.setText(R.string.lan_link_share_subtitle);
        subtitle.setTextColor(this.getResources().getColor(R.color.text_secondary));
        subtitle.setTextSize(13.0f);
        subtitle.setPadding(0, this.dp(6), 0, 0);
        root.addView((View)subtitle);
        RadioGroup modeGroup = new RadioGroup(this.requireContext());
        modeGroup.setOrientation(1);
        RadioButton single = new RadioButton(this.requireContext());
        single.setText(R.string.lan_single_receiver);
        RadioButton multi = new RadioButton(this.requireContext());
        multi.setText(R.string.lan_multiple_receivers);
        modeGroup.addView((View)single);
        modeGroup.addView((View)multi);
        single.setChecked(true);
        root.addView((View)modeGroup);
        TextView maxLabel = new TextView(this.requireContext());
        maxLabel.setText(R.string.lan_max_receivers);
        maxLabel.setPadding(0, this.dp(10), 0, 0);
        root.addView((View)maxLabel);
        Spinner maxSpinner = new Spinner(this.requireContext());
        maxSpinner.setAdapter((SpinnerAdapter)new ArrayAdapter(this.requireContext(), 0x1090009, (Object[])new Integer[]{1, 2, 3}));
        root.addView((View)maxSpinner);
        Switch passwordSwitch = new Switch(this.requireContext());
        passwordSwitch.setText(R.string.lan_require_password);
        passwordSwitch.setChecked(true);
        if (targetPeer == null) {
            passwordSwitch.setEnabled(false);
        }
        root.addView((View)passwordSwitch);
        EditText passwordInput = new EditText(this.requireContext());
        passwordInput.setHint(R.string.lan_access_password);
        passwordInput.setText((CharSequence)LanFileTransferServer.randomPasswordDigits((int)6));
        passwordInput.setBackgroundResource(R.drawable.bg_edit_text);
        passwordInput.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(10));
        passwordInput.setTextColor(this.getResources().getColor(R.color.text_main));
        root.addView((View)passwordInput);
        LinearLayout passwordButtons = new LinearLayout(this.requireContext());
        passwordButtons.setOrientation(0);
        passwordButtons.setPadding(0, this.dp(8), 0, 0);
        Button copyPassword = new Button(this.requireContext());
        copyPassword.setText(R.string.copy);
        Button regenPassword = new Button(this.requireContext());
        regenPassword.setText(R.string.lan_regenerate);
        passwordButtons.addView((View)copyPassword, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams regenParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        regenParams.leftMargin = this.dp(10);
        passwordButtons.addView((View)regenPassword, (ViewGroup.LayoutParams)regenParams);
        root.addView((View)passwordButtons);
        Switch confirmSwitch = new Switch(this.requireContext());
        confirmSwitch.setText(R.string.lan_confirm_each_device);
        confirmSwitch.setChecked(true);
        root.addView((View)confirmSwitch);
        AlertDialog dialog = new AlertDialog.Builder(this.requireContext()).setTitle(R.string.lan_link_share_title).setView((View)root).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.generate, null).create();
        copyPassword.setOnClickListener(v -> SecureClipboard.copySensitive((Context)this.requireContext(), (String)"KeyScan LAN file transfer password", (String)passwordInput.getText().toString()));
        regenPassword.setOnClickListener(v -> passwordInput.setText((CharSequence)LanFileTransferServer.randomPasswordDigits((int)6)));
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            boolean multiMode = multi.isChecked();
            int max = multiMode ? (Integer)maxSpinner.getSelectedItem() : 1;
            this.startLanFileTransfer(passwordSwitch.isChecked(), passwordInput.getText().toString().trim(), max, confirmSwitch.isChecked(), true);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void startLanFileTransfer(boolean usePassword, String password, int maxDevices, boolean needConfirm) {
        this.startLanFileTransfer(usePassword, password, maxDevices, needConfirm, true);
    }

    private void startLanFileTransfer(boolean usePassword, String password, int maxDevices, boolean needConfirm, boolean showShareDialog) {
        NetworkAccessController.LanInfo current = NetworkAccessController.currentLanInfo((Context)this.requireContext());
        if (current == null) {
            Toast.makeText((Context)this.requireContext(), R.string.lan_connect_wifi_first, (int)0).show();
            return;
        }
        if (this.activeFileServer != null) {
            this.stopLanFileTransfer();
        }
        this.activeFileStartLan = current;
        this.accessPassword = usePassword ? password : "";
        this.requirePassword = usePassword;
        this.requireConfirmation = needConfirm;
        this.maxReceivers = Math.max(1, Math.min(3, maxDevices));
        this.updateFileTransferStatus(getString(R.string.lan_status_sending));
        try {
            long expiresAt = this.lanShareExpiresAt > 0 ? this.lanShareExpiresAt : System.currentTimeMillis() + 3600000L;
            this.activeFileServer = new LanFileTransferServer(this.requireContext(), this.currentShareItems(), this.accessPassword, expiresAt, this.maxReceivers, this.requirePassword, this.requireConfirmation, new LanFileTransferServer.Listener() {
                @Override
                public void onStateChanged() {
                    GenerateFragment.this.runOnUi(() -> {
                        if (GenerateFragment.this.activeFileServer != null) {
                            GenerateFragment.this.updateFileTransferStatus(GenerateFragment.this.activeFileServer.statusSummary());
                            GenerateFragment.this.updateFileTransferDetails();
                        }
                    });
                }

                @Override
                public void onExpired() {
                    GenerateFragment.this.runOnUi(() -> {
                        GenerateFragment.this.stopLanFileTransfer();
                        GenerateFragment.this.updateFileTransferStatus(getString(R.string.lan_status_timed_out));
                        Toast.makeText((Context)GenerateFragment.this.requireContext(), R.string.lan_transfer_timed_out, (int)1).show();
                    });
                }

                @Override
                public void onError(Exception error) {
                    GenerateFragment.this.runOnUi(() -> {
                        GenerateFragment.this.stopLanFileTransfer();
                        GenerateFragment.this.updateFileTransferStatus(getString(R.string.lan_status_failed));
                        Toast.makeText((Context)GenerateFragment.this.requireContext(), (CharSequence)(error.getMessage() == null ? getString(R.string.lan_transfer_failed) : error.getMessage()), (int)1).show();
                    });
                }
            });
            this.activeFileServer.start(current.ipv4);
            NetworkAccessController.enableLanSession((NetworkAccessController.LanInfo)current);
            this.setLanFileTransferExpanded(true);
            if (showShareDialog) {
                this.showLanFileTransferDialog();
            }
            this.refreshNearbyDeviceSections();
            this.handler.removeCallbacks(this.fileTransferMonitor);
            this.handler.post(this.fileTransferMonitor);
        }
        catch (Exception e) {
            this.stopLanFileTransfer();
            this.updateFileTransferStatus(getString(R.string.lan_status_failed));
            Toast.makeText((Context)this.requireContext(), (CharSequence)(e.getMessage() == null ? getString(R.string.lan_transfer_failed) : e.getMessage()), (int)1).show();
        }
    }

    private void startNearbyPeerTransfer(@NonNull NearbyLanShareManager.Peer peer) {
        if (this.selectedFiles.isEmpty()) {
            return;
        }
        this.startLanFileTransfer(false, "", 1, true, false);
        if (this.activeFileServer != null && this.nearbyLanShareManager != null) {
            this.nearbyLanShareManager.sendInvite(peer, this.activeFileServer);
        }
    }

    private void showNearbySendConfirmDialog() {
        if (!hasLanShareContent()) {
            Toast.makeText(requireContext(), R.string.share_choose_content_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedReceiverPeers.isEmpty()) {
            Toast.makeText(requireContext(), R.string.share_receiver_required, Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder names = new StringBuilder();
        for (NearbyLanShareManager.Peer peer : selectedReceiverPeers) {
            if (names.length() > 0) names.append("、");
            names.append(peer.displayName == null || peer.displayName.trim().isEmpty() ? getString(R.string.share_nearby_device) : peer.displayName.trim());
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_selected_devices_title)
                .setMessage(getString(R.string.share_selected_devices_message, names, selectedFiles.size(),
                        selectedShareText.trim().isEmpty() ? "" : getString(R.string.share_includes_text)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.share_send, (dialog, which) -> startSelectedPeerTransfers())
                .show();
    }

    private void startSelectedPeerTransfers() {
        ArrayList<NearbyLanShareManager.Peer> targets = new ArrayList<>(selectedReceiverPeers);
        startLanFileTransfer(false, "", Math.max(1, targets.size()), true, false);
        if (activeFileServer != null && nearbyLanShareManager != null) {
            for (NearbyLanShareManager.Peer peer : targets) nearbyLanShareManager.sendInvite(peer, activeFileServer);
            Toast.makeText(requireContext(), getString(R.string.share_requests_sent, targets.size()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showNearbySendConfirmDialog(@Nullable NearbyLanShareManager.Peer peer) {
        if (this.selectedFiles.isEmpty()) {
            Toast.makeText((Context)this.requireContext(), R.string.lan_select_files_first, (int)0).show();
            return;
        }
        if (peer == null) {
            Toast.makeText((Context)this.requireContext(), R.string.lan_select_receiver_first, (int)0).show();
            return;
        }
        LinearLayout root = new LinearLayout(this.requireContext());
        root.setOrientation(1);
        root.setPadding(this.dp(18), this.dp(12), this.dp(18), this.dp(8));
        TextView title = new TextView(this.requireContext());
        title.setText((CharSequence)this.getString(R.string.lan_send_confirm_title));
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(18.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView((View)title);
        TextView fileLine = new TextView(this.requireContext());
        fileLine.setText((CharSequence)this.getString(R.string.lan_send_confirm_files, new Object[]{this.selectedFiles.size(), this.formatSize(this.totalSelectedSize())}));
        fileLine.setTextColor(this.getResources().getColor(R.color.text_secondary));
        fileLine.setPadding(0, this.dp(8), 0, 0);
        root.addView((View)fileLine);
        TextView receiverLine = new TextView(this.requireContext());
        receiverLine.setText((CharSequence)this.getString(R.string.lan_send_confirm_receiver, new Object[]{peer.displayName == null || peer.displayName.trim().isEmpty() ? getString(R.string.nearby_device) : peer.displayName.trim()}));
        receiverLine.setTextColor(this.getResources().getColor(R.color.text_secondary));
        receiverLine.setPadding(0, this.dp(6), 0, 0);
        root.addView((View)receiverLine);
        AlertDialog dialog = new AlertDialog.Builder(this.requireContext()).setTitle((CharSequence)this.getString(R.string.lan_send_confirm_title)).setView((View)root).setNegativeButton((CharSequence)this.getString(R.string.lan_cancel), null).setPositiveButton((CharSequence)this.getString(R.string.lan_send), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            this.startNearbyPeerTransfer(peer);
            this.selectedReceiverPeer = null;
            this.updateReceiverSelectionState();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showLanFileTransferDialog() throws Exception {
        if (this.activeFileServer == null) {
            return;
        }
        LinearLayout content = new LinearLayout(this.requireContext());
        content.setOrientation(1);
        content.setPadding(this.dp(16), this.dp(10), this.dp(16), this.dp(4));
        TextView status = new TextView(this.requireContext());
        status.setTextColor(this.getResources().getColor(R.color.text_main));
        status.setTextSize(18.0f);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView((View)status);
        TextView hint = new TextView(this.requireContext());
        hint.setText(R.string.lan_receive_hint);
        hint.setTextColor(this.getResources().getColor(R.color.text_secondary));
        hint.setTextSize(13.0f);
        hint.setPadding(0, this.dp(8), 0, 0);
        content.addView((View)hint);
        ImageView qr = new ImageView(this.requireContext());
        Bitmap qrBitmap = QRGenerator.generateQR((String)("keyscan://lan-file-transfer?payload=" + Base64.encodeToString((byte[])this.activeFileServer.buildQrPayload().toString().getBytes(StandardCharsets.UTF_8), (int)10)), (int)this.dp(220));
        if (qrBitmap != null) {
            qr.setImageBitmap(qrBitmap);
        }
        qr.setAdjustViewBounds(true);
        qr.setPadding(0, this.dp(10), 0, 0);
        qr.setContentDescription(getString(R.string.lan_qr_description));
        content.addView((View)qr, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(240)));
        TextView link = new TextView(this.requireContext());
        link.setText((CharSequence)this.activeFileServer.getBrowserEntryUrl());
        link.setTextColor(this.getResources().getColor(R.color.primary));
        link.setTextIsSelectable(true);
        link.setPadding(0, this.dp(10), 0, 0);
        content.addView((View)link);
        TextView password = new TextView(this.requireContext());
        password.setText(this.requirePassword ? getString(R.string.lan_password_value, this.accessPassword) : getString(R.string.lan_password_disabled));
        password.setTextColor(this.getResources().getColor(R.color.text_main));
        password.setPadding(0, this.dp(8), 0, 0);
        content.addView((View)password);
        TextView devices = new TextView(this.requireContext());
        devices.setTextColor(this.getResources().getColor(R.color.text_secondary));
        devices.setPadding(0, this.dp(8), 0, 0);
        content.addView((View)devices);
        Button copyLink = new Button(this.requireContext());
        copyLink.setText(R.string.lan_copy_link);
        Button systemShare = new Button(this.requireContext());
        systemShare.setText(R.string.lan_system_share);
        Button copyPwd = new Button(this.requireContext());
        copyPwd.setText(R.string.lan_copy_password);
        Button regenPwd = new Button(this.requireContext());
        regenPwd.setText(R.string.lan_regenerate);
        Button pause = new Button(this.requireContext());
        pause.setText(R.string.lan_pause_new_receivers);
        Button stop = new Button(this.requireContext());
        stop.setText(R.string.lan_end_share);
        LinearLayout row1 = new LinearLayout(this.requireContext());
        row1.setOrientation(0);
        row1.setPadding(0, this.dp(10), 0, 0);
        row1.addView((View)copyLink, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams row1b = new LinearLayout.LayoutParams(0, -2, 1.0f);
        row1b.leftMargin = this.dp(10);
        row1.addView((View)systemShare, (ViewGroup.LayoutParams)row1b);
        content.addView((View)row1);
        LinearLayout row2 = new LinearLayout(this.requireContext());
        row2.setOrientation(0);
        row2.setPadding(0, this.dp(8), 0, 0);
        row2.addView((View)copyPwd, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams row2b = new LinearLayout.LayoutParams(0, -2, 1.0f);
        row2b.leftMargin = this.dp(10);
        row2.addView((View)regenPwd, (ViewGroup.LayoutParams)row2b);
        content.addView((View)row2);
        content.addView((View)pause, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(-1, -2);
        stopParams.topMargin = this.dp(8);
        content.addView((View)stop, (ViewGroup.LayoutParams)stopParams);
        AlertDialog dialog = new AlertDialog.Builder(this.requireContext()).setTitle(R.string.lan_file_transfer_title).setView((View)content).setNegativeButton(R.string.close, null).create();
        Runnable refresher = new Runnable() {
            @Override
            public void run() {
                if (GenerateFragment.this.activeFileServer == null || !dialog.isShowing()) {
                    return;
                }
                status.setText((CharSequence)GenerateFragment.this.statusTextForServer());
                devices.setText(getString(R.string.lan_active_devices, GenerateFragment.this.activeFileServer.getActiveSessionCount(), GenerateFragment.this.maxReceivers));
                password.setText(GenerateFragment.this.requirePassword ? getString(R.string.lan_password_value, GenerateFragment.this.accessPassword) : getString(R.string.lan_password_disabled));
                GenerateFragment.this.handler.postDelayed(this, 1000L);
            }
        };
        copyLink.setOnClickListener(v -> SecureClipboard.copySensitive((Context)this.requireContext(), (String)"KeyScan LAN file transfer link", (String)this.activeFileServer.getBrowserEntryUrl()));
        systemShare.setOnClickListener(v -> {
            Intent intent = new Intent("android.intent.action.SEND");
            intent.setType("text/plain");
            intent.putExtra("android.intent.extra.TEXT", this.activeFileServer.getBrowserEntryUrl());
            this.startActivity(Intent.createChooser((Intent)intent, getString(R.string.lan_system_share)));
        });
        copyPwd.setOnClickListener(v -> {
            if (this.requirePassword) {
                SecureClipboard.copySensitive((Context)this.requireContext(), (String)"KeyScan LAN file transfer password", (String)this.accessPassword);
            } else {
                Toast.makeText((Context)this.requireContext(), R.string.lan_password_already_disabled, (int)0).show();
            }
        });
        regenPwd.setOnClickListener(v -> {
            this.accessPassword = LanFileTransferServer.randomPasswordDigits((int)6);
            if (this.activeFileServer != null) {
                this.restartingFileShare = true;
                this.stopLanFileTransfer();
                this.startLanFileTransfer(true, this.accessPassword, this.maxReceivers, this.requireConfirmation);
                dialog.dismiss();
            }
        });
        pause.setOnClickListener(v -> Toast.makeText((Context)this.requireContext(), R.string.lan_pause_v1_notice, (int)0).show());
        stop.setOnClickListener(v -> {
            this.stopLanFileTransfer();
            this.updateFileTransferStatus(getString(R.string.lan_status_ended));
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> {
            this.handler.removeCallbacks(this.fileTransferMonitor);
            if (this.restartingFileShare) {
                this.restartingFileShare = false;
                return;
            }
            if (this.activeFileServer != null) {
                this.stopLanFileTransfer();
            }
        });
        dialog.show();
        refresher.run();
    }

    private String statusTextForServer() {
        if (this.activeFileServer == null) {
            return getString(R.string.lan_status_not_started);
        }
        return this.activeFileServer.statusSummary();
    }

    private void stopLanFileTransfer() {
        if (this.activeFileServer != null) {
            this.activeFileServer.stop();
            this.activeFileServer = null;
        }
        this.activeFileStartLan = null;
        NetworkAccessController.clearLanSession();
        this.handler.removeCallbacks(this.fileTransferMonitor);
        this.refreshNearbyDeviceSections();
    }

    private void openLanReceiveScanner() {
        ScannerFragment fragment = ScannerFragment.forLanFileTransferCapture();
        this.getParentFragmentManager().beginTransaction().replace(R.id.fragment_container, (Fragment)fragment).addToBackStack(null).commit();
    }

    private void handleLanFileTransferQr(String raw) {
        if (raw == null || raw.isEmpty() || !raw.startsWith("keyscan://lan-file-transfer")) {
            Toast.makeText((Context)this.requireContext(), R.string.lan_regular_qr_notice, (int)0).show();
            return;
        }
        try {
            int index = raw.indexOf("payload=");
            if (index < 0) {
                throw new IllegalArgumentException(getString(R.string.lan_invalid_qr));
            }
            String encoded = raw.substring(index + "payload=".length());
            String json = new String(Base64.decode((String)encoded, (int)8), StandardCharsets.UTF_8);
            JSONObject payload = new JSONObject(json);
            if (payload.optInt("protocolVersion") != 1) {
                throw new IllegalArgumentException(getString(R.string.lan_unsupported_version));
            }
            if (System.currentTimeMillis() > payload.optLong("expiresAt")) {
                throw new IllegalArgumentException(getString(R.string.lan_expired_qr));
            }
            String host = payload.optString("senderIp");
            int port = payload.optInt("senderPort");
            NetworkAccessController.Decision decision = NetworkAccessController.evaluate((Context)this.requireContext(), (String)("http://" + host + ":" + port));
            if (!decision.allowed) {
                throw new IllegalArgumentException(getString(R.string.lan_wrong_subnet));
            }
            String openUrl = "http://" + host + ":" + port + "/s/" + payload.optString("sessionId") + "?token=" + payload.optString("requestToken")
                    + "&credential=" + android.net.Uri.encode(payload.optString("accessCredential", ""));
            new AlertDialog.Builder(this.requireContext()).setTitle(R.string.lan_device_found_title).setMessage(getString(R.string.lan_device_found_message, host, port, payload.optInt("fileCount"), this.formatSize(payload.optLong("totalSize")))).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.lan_open_browser, (d, w) -> {
                Intent intent = new Intent("android.intent.action.VIEW", Uri.parse((String)openUrl));
                this.startActivity(intent);
            }).show();
        }
        catch (Exception e) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)(e.getMessage() == null ? getString(R.string.lan_invalid_qr) : e.getMessage()), (int)0).show();
        }
    }

    private void refreshNearbyDeviceSections() {
        if (!this.isAdded()) {
            return;
        }
        NearbyLanShareManager manager = this.nearbyLanShareManager;
        if (manager == null) {
            return;
        }
        NearbyLanIdentityStore.Identity identity = manager.getIdentity();
        if (this.nearbyDeviceName != null) {
            this.nearbyDeviceName.setText((CharSequence)(identity == null ? "KeyScan" : identity.displayName));
        }
        if (this.nearbyStatusText != null) {
            if (!manager.isRunning()) {
                this.nearbyStatusText.setText(R.string.nearby_connect_same_wifi);
            } else {
                this.nearbyStatusText.setText(R.string.nearby_discovery_enabled);
            }
        }
        this.renderNearbyDevices();
        this.updateReceiverSelectionState();
        this.renderIncomingInvites();
    }

    private void renderNearbyDevices() {
        if (this.nearbyDevicesContainer == null || this.nearbyLanShareManager == null) {
            return;
        }
        this.nearbyDevicesContainer.removeAllViews();
        List<NearbyLanShareManager.Peer> peers = new ArrayList<>();
        for (NearbyLanShareManager.Peer peer : this.nearbyLanShareManager.getPeers()) {
            if (!peer.self) peers.add(peer);
        }
        if (peers.isEmpty()) {
            TextView empty = new TextView(this.requireContext());
            empty.setText(R.string.nearby_no_devices);
            empty.setTextColor(this.getResources().getColor(R.color.text_secondary));
            empty.setTextSize(13.0f);
            this.nearbyDevicesContainer.addView((View)empty);
            return;
        }
        ArrayList<String> livePeerIds = new ArrayList<>();
        for (NearbyLanShareManager.Peer peer : peers) {
            livePeerIds.add(peer.peerId);
            this.nearbyDevicesContainer.addView(this.buildPeerRow(peer));
        }
        this.selectedReceiverPeers.removeIf(peer -> !livePeerIds.contains(peer.peerId));
        this.selectedReceiverPeer = selectedReceiverPeers.isEmpty() ? null : selectedReceiverPeers.get(0);
        this.updateReceiverSelectionState();
    }

    private View buildPeerRow(NearbyLanShareManager.Peer peer) {
        LinearLayout row = new LinearLayout(this.requireContext());
        row.setOrientation(0);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(10));
        row.setBackgroundResource(R.drawable.bg_card);
        TextView title = new TextView(this.requireContext());
        title.setText((CharSequence)(peer.displayName == null || peer.displayName.isEmpty() ? getString(R.string.unknown_device) : peer.displayName));
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(15.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView meta = new TextView(this.requireContext());
        meta.setText(getString(R.string.nearby_device_meta, peer.host == null ? "" : peer.host, peer.port <= 0 ? getString(R.string.unknown_port) : String.valueOf(peer.port)));
        meta.setTextColor(this.getResources().getColor(R.color.text_secondary));
        meta.setTextSize(12.0f);
        meta.setPadding(0, this.dp(4), 0, 0);
        TextView state = new TextView(this.requireContext());
        state.setText((CharSequence)this.deviceStateText(peer));
        state.setTextColor(this.getResources().getColor(R.color.primary));
        state.setTextSize(12.0f);
        state.setPadding(0, this.dp(6), 0, 0);
        android.widget.CheckBox check = new android.widget.CheckBox(requireContext());
        check.setChecked(isPeerSelected(peer));
        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(title);
        texts.addView(meta);
        texts.addView(state);
        row.addView(check, new LinearLayout.LayoutParams(dp(42), dp(48)));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> {
            this.selectReceiverPeer(peer);
        });
        check.setOnClickListener(v -> this.selectReceiverPeer(peer));
        return row;
    }

    private boolean isPeerSelected(NearbyLanShareManager.Peer peer) {
        for (NearbyLanShareManager.Peer selected : selectedReceiverPeers) {
            if (selected.peerId.equals(peer.peerId)) return true;
        }
        return false;
    }

    private void renderIncomingInvites() {
        if (this.incomingInvitesContainer == null || this.nearbyLanShareManager == null) {
            return;
        }
        this.incomingInvitesContainer.removeAllViews();
        List<NearbyLanShareManager.IncomingInvite> invites = this.nearbyLanShareManager.getIncomingInvites();
        if (invites.isEmpty()) {
            TextView empty = new TextView(this.requireContext());
            empty.setText(R.string.nearby_no_invites);
            empty.setTextColor(this.getResources().getColor(R.color.text_secondary));
            empty.setTextSize(13.0f);
            this.incomingInvitesContainer.addView((View)empty);
            return;
        }
        for (NearbyLanShareManager.IncomingInvite invite : invites) {
            this.incomingInvitesContainer.addView(this.buildInviteRow(invite));
        }
    }

    private void showPendingIncomingInvite() {
        if (!isAdded() || nearbyLanShareManager == null) return;
        for (NearbyLanShareManager.IncomingInvite invite : nearbyLanShareManager.getIncomingInvites()) {
            if (shownInviteIds.contains(invite.inviteId)) continue;
            shownInviteIds.add(invite.inviteId);
            String sender = invite.senderDisplayName == null || invite.senderDisplayName.trim().isEmpty() ? getString(R.string.share_nearby_keyscan_device) : invite.senderDisplayName.trim();
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.share_invite_title)
                    .setMessage(getString(R.string.share_invite_message, sender, invite.fileCount, formatSize(invite.totalSize)))
                    .setNegativeButton(R.string.share_decline, (dialog, which) -> {
                        nearbyLanShareManager.declineInvite(invite);
                        shownInviteIds.remove(invite.inviteId);
                    })
                    .setPositiveButton(R.string.share_accept, (dialog, which) -> {
                        nearbyLanShareManager.acceptInvite(invite);
                        shownInviteIds.remove(invite.inviteId);
                        openTransferUrl(invite.senderTransferUrl);
                    })
                    .setOnCancelListener(dialog -> {
                        nearbyLanShareManager.declineInvite(invite);
                        shownInviteIds.remove(invite.inviteId);
                    })
                    .show();
            break;
        }
    }

    private View buildInviteRow(NearbyLanShareManager.IncomingInvite invite) {
        LinearLayout row = new LinearLayout(this.requireContext());
        row.setOrientation(1);
        row.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(10));
        row.setBackgroundResource(R.drawable.bg_card);
        TextView title = new TextView(this.requireContext());
        title.setText(getString(R.string.nearby_invite_title, invite.senderDisplayName == null || invite.senderDisplayName.isEmpty() ? getString(R.string.unknown_device) : invite.senderDisplayName));
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(15.0f);
        TextView meta = new TextView(this.requireContext());
        meta.setText(getString(R.string.nearby_invite_meta, invite.fileCount, this.formatSize(invite.totalSize)));
        meta.setTextColor(this.getResources().getColor(R.color.text_secondary));
        meta.setTextSize(12.0f);
        meta.setPadding(0, this.dp(4), 0, 0);
        LinearLayout actions = new LinearLayout(this.requireContext());
        actions.setOrientation(0);
        actions.setPadding(0, this.dp(10), 0, 0);
        Button decline = new Button(this.requireContext());
        decline.setText(R.string.decline);
        Button accept = new Button(this.requireContext());
        accept.setText(R.string.nearby_accept_open);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1.0f);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1.0f);
        right.leftMargin = this.dp(10);
        actions.addView((View)decline, (ViewGroup.LayoutParams)left);
        actions.addView((View)accept, (ViewGroup.LayoutParams)right);
        decline.setOnClickListener(v -> this.nearbyLanShareManager.declineInvite(invite));
        accept.setOnClickListener(v -> {
            this.nearbyLanShareManager.acceptInvite(invite);
            this.openTransferUrl(invite.senderTransferUrl);
        });
        row.addView((View)title);
        row.addView((View)meta);
        row.addView((View)actions);
        return row;
    }

    private String deviceStateText(NearbyLanShareManager.Peer peer) {
        if (peer == null) {
            return "";
        }
        if (isPeerSelected(peer)) {
            return getString(R.string.selected);
        }
        if (peer.state == null || peer.state.isEmpty()) {
            return getString(R.string.nearby_tap_to_invite);
        }
        return peer.stateMessage == null || peer.stateMessage.isEmpty() ? peer.state : peer.stateMessage;
    }

    private void openTransferUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse((String)url)));
        }
        catch (Exception e) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)(e.getMessage() == null ? getString(R.string.lan_open_page_failed) : e.getMessage()), (int)0).show();
        }
    }

    private void showNearbyDeviceNameDialog() {
        if (!this.isAdded() || this.nearbyLanShareManager == null) {
            return;
        }
        EditText edit = new EditText(this.requireContext());
        NearbyLanIdentityStore.Identity identity = this.nearbyLanShareManager.getIdentity();
        edit.setText((CharSequence)(identity == null ? "" : identity.displayName));
        edit.setSelection(edit.getText().length());
        edit.setHint((CharSequence)"");
        new AlertDialog.Builder(this.requireContext()).setTitle(R.string.nearby_rename_device).setView((View)edit).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, (d, w) -> {
            String value = NearbyLanIdentityStore.sanitizeDisplayName((String)edit.getText().toString());
            this.nearbyLanShareManager.renameDisplayName(value);
        }).show();
    }

    private void updateQR() {
        String text = this.input.getText().toString().trim();
        if (text.isEmpty()) {
            this.preview.setImageBitmap(null);
            this.placeholder.setVisibility(0);
            this.currentBitmap = null;
            Toast.makeText((Context)this.requireContext(), (int)R.string.input_required, (int)0).show();
            return;
        }
        this.currentBitmap = QRGenerator.generateStyledQR((String)text, (int)400, (String)this.qrStyle, (int)this.foregroundColor, (int)this.backgroundColor);
        this.preview.setImageBitmap(this.currentBitmap);
        this.placeholder.setVisibility(this.currentBitmap == null ? 0 : 8);
    }

    private void promptSavePng() {
        String text = this.input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }
        EditText note = new EditText(requireContext());
        note.setHint(R.string.share_save_note_hint);
        note.setSingleLine(false);
        note.setMinLines(2);
        note.setMaxLines(4);
        note.setBackgroundResource(R.drawable.bg_edit_text);
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        TextView notice = new TextView(requireContext());
        notice.setText(R.string.share_save_history_notice);
        notice.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary));
        notice.setTextSize(13);
        content.addView(notice);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(10);
        content.addView(note, noteParams);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_add_note_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) ->
                        savePng(note.getText().toString().trim()))
                .show();
    }

    private void savePng(String note) {
        String text = this.input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }
        this.currentBitmap = QRGenerator.generateStyledQR(
                text, 400, this.qrStyle, this.foregroundColor, this.backgroundColor);
        if (this.currentBitmap == null) {
            Toast.makeText(requireContext(), R.string.qr_generation_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        this.preview.setImageBitmap(this.currentBitmap);
        this.placeholder.setVisibility(View.GONE);
        try {
            ContentValues values = new ContentValues();
            values.put("_display_name", "secureqr_" + System.currentTimeMillis() + ".png");
            values.put("mime_type", "image/png");
            values.put("relative_path", "Pictures/KeyScan");
            Uri uri = this.requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException(this.getString(R.string.image_create_failed));
            }
            Bitmap exportBitmap = this.buildExportBitmap(note);
            try (OutputStream out = this.requireContext().getContentResolver().openOutputStream(uri);){
                exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } finally {
                if (exportBitmap != this.currentBitmap && !exportBitmap.isRecycled()) {
                    exportBitmap.recycle();
                }
            }
            this.saveGeneratedHistory(note, () -> {
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.share_saved_album_and_history, Toast.LENGTH_LONG).show();
                }
            });
        }
        catch (Exception e) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)this.getString(R.string.save_failed, new Object[]{e.getMessage()}), (int)0).show();
        }
    }

    private void saveRecord() {
        String text = this.input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText((Context)this.requireContext(), (int)R.string.input_required, (int)0).show();
            return;
        }
        if (this.currentBitmap == null) {
            this.currentBitmap = QRGenerator.generateStyledQR((String)text, (int)400, (String)this.qrStyle, (int)this.foregroundColor, (int)this.backgroundColor);
            this.preview.setImageBitmap(this.currentBitmap);
        }
        this.saveGeneratedHistory("");
        Toast.makeText((Context)this.requireContext(), (int)R.string.saved_to_record, (int)0).show();
    }

    private void saveGeneratedHistory(String note) {
        saveGeneratedHistory(note, null);
    }

    private void saveGeneratedHistory(String note, Runnable onSaved) {
        String text = this.input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        ScanRecord record = ScanRecord.fromGeneratedContent(text, note, this.thumbnailBase64(this.currentBitmap));
        if (!note.isEmpty()) {
            record.title = note;
        }
        RecordRepository.getInstance(requireContext()).insert(record, saved -> {
            if (onSaved != null) handler.post(onSaved);
        });
    }

    private String thumbnailBase64(Bitmap bitmap) {
        if (bitmap == null) {
            return "";
        }
        Bitmap thumb = Bitmap.createScaledBitmap((Bitmap)bitmap, (int)96, (int)96, (boolean)true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        thumb.compress(Bitmap.CompressFormat.PNG, 80, (OutputStream)output);
        if (thumb != bitmap) {
            thumb.recycle();
        }
        return Base64.encodeToString((byte[])output.toByteArray(), (int)2);
    }

    private void showStyleDialog() {
        CharSequence[] labels = new String[]{this.getString(R.string.qr_style_classic), this.getString(R.string.qr_style_blue_purple), this.getString(R.string.qr_style_orange_yellow), this.getString(R.string.qr_style_dots), this.getString(R.string.qr_style_rounded), this.getString(R.string.qr_style_logo)};
        String[] codes = new String[]{"classic", "blue_purple", "orange_yellow", "dots", "rounded", "logo"};
        new AlertDialog.Builder(this.requireContext()).setTitle(R.string.qr_style_title).setItems(labels, (arg_0, arg_1) -> this.lambda$showStyleDialog$31(codes, (String[])labels, arg_0, arg_1)).show();
    }

    private void showColorDialog(boolean foreground) {
        CharSequence[] names = new String[]{this.getString(R.string.color_black), this.getString(R.string.color_warm_orange), this.getString(R.string.color_blue), this.getString(R.string.color_purple), this.getString(R.string.color_green), this.getString(R.string.color_white)};
        int[] colors = new int[]{-16777216, Color.rgb((int)255, (int)140, (int)0), Color.rgb((int)21, (int)101, (int)192), Color.rgb((int)142, (int)36, (int)170), Color.rgb((int)0, (int)168, (int)120), -1};
        new AlertDialog.Builder(this.requireContext()).setTitle(foreground ? R.string.foreground_color : R.string.background_color).setItems(names, (dialog, which) -> {
            if (foreground) {
                this.foregroundColor = colors[which];
            } else {
                this.backgroundColor = colors[which];
            }
            if (this.currentBitmap != null) {
                this.updateQR();
            }
        }).show();
    }

    private Bitmap buildExportBitmap(String note) {
        note = note == null ? "" : note.trim();
        if (note.isEmpty()) {
            return this.currentBitmap;
        }
        int padding = 32;
        int width = this.currentBitmap.getWidth() + padding * 2;
        Paint paint = new Paint(1);
        paint.setColor(Color.rgb((int)32, (int)33, (int)36));
        paint.setTextSize(34.0f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        String[] lines = this.wrapText(note, paint, width - padding * 2);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        int lineHeight = (int)Math.ceil(metrics.descent - metrics.ascent) + 8;
        int noteHeight = lineHeight * lines.length + padding;
        int height = this.currentBitmap.getHeight() + padding * 2 + noteHeight;
        Bitmap output = Bitmap.createBitmap((int)width, (int)height, (Bitmap.Config)Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(-1);
        canvas.drawBitmap(this.currentBitmap, (float)padding, (float)padding, null);
        float y = (float)(padding + this.currentBitmap.getHeight() + padding) - metrics.ascent;
        for (String line : lines) {
            canvas.drawText(line, (float)width / 2.0f, y, paint);
            y += (float)lineHeight;
        }
        return output;
    }

    private String[] wrapText(String text, Paint paint, int maxWidth) {
        if (paint.measureText(text) <= (float)maxWidth) {
            return new String[]{text};
        }
        ArrayList<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); ++i) {
            String next = line.toString() + text.charAt(i);
            if (paint.measureText(next) > (float)maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(text.charAt(i));
            if (lines.size() != 2 || i >= text.length() - 1) continue;
            line.append("...");
            break;
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.toArray(new String[0]);
    }

    private String formatSize(long size) {
        int unit;
        if (size <= 0L) {
            return "0 B";
        }
        String[] units = new String[]{"B", "KB", "MB", "GB"};
        double value = size;
        for (unit = 0; value >= 1024.0 && unit < units.length - 1; value /= 1024.0, ++unit) {
        }
        return unit == 0 ? String.format(Locale.US, "%d %s", (long)value, units[unit]) : String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    private void runOnUi(Runnable runnable) {
        if (this.isAdded()) {
            FragmentUi.run(this, runnable);
        }
    }

    private int dp(int value) {
        float density = this.getResources().getDisplayMetrics().density;
        return Math.round((float)value * density);
    }

    private /* synthetic */ void lambda$showStyleDialog$31(String[] codes, String[] labels, DialogInterface dialog, int which) {
        this.qrStyle = codes[which];
        this.styleButton.setText((CharSequence)this.getString(R.string.qr_style_prefix, new Object[]{labels[which]}));
        if (this.currentBitmap != null) {
            this.updateQR();
        }
    }
}


