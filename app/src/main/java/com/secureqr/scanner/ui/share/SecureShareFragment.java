package com.secureqr.scanner.ui.share;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.QRGenerator;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Offline password exchange using a receiver-owned temporary ECDH key. */
public final class SecureShareFragment extends Fragment {
    private static final String ARG_PASSWORD_ENTRY_ID = "password_entry_id";
    private static final long[] LIFETIMES = {30_000L, 60_000L, 300_000L, 600_000L};
    private PasswordRepository repository;
    private PasswordEntry pendingEntry;

    public static SecureShareFragment newInstance() { return newInstance(-1L); }

    public static SecureShareFragment newInstance(long passwordEntryId) {
        SecureShareFragment fragment = new SecureShareFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_PASSWORD_ENTRY_ID, passwordEntryId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_secure_share, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = PasswordRepository.getInstance(requireContext());
        view.findViewById(R.id.btn_secure_share_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        long entryId = getArguments() == null ? -1L : getArguments().getLong(ARG_PASSWORD_ENTRY_ID, -1L);
        if (entryId > 0L) {
            TextView hint = view.findViewById(R.id.secure_share_direct_hint);
            hint.setVisibility(View.VISIBLE);
            repository.getEntry(entryId, entry -> {
                pendingEntry = entry;
                runOnUi(() -> updateDirectHint(hint, entry));
            });
        }
        getParentFragmentManager().setFragmentResultListener(
                ScannerFragment.SECURE_SHARE_SCAN_REQUEST, getViewLifecycleOwner(),
                (key, bundle) -> handleScan(bundle.getString(ScannerFragment.SECURE_SHARE_SCAN_VALUE)));
        view.findViewById(R.id.card_secure_share_send).setOnClickListener(v -> startSend());
        view.findViewById(R.id.card_secure_share_receive).setOnClickListener(v -> chooseReceiveLifetime());
    }

    private void startSend() {
        if (pendingEntry != null) {
            openScanner();
            return;
        }
        repository.getAll(entries -> runOnUi(() -> showPasswordChooser(entries)));
    }

    private void showPasswordChooser(List<PasswordEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            Toast.makeText(requireContext(), R.string.secure_share_no_passwords, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            PasswordEntry entry = entries.get(i);
            String account = entry.displayUsername();
            labels[i] = entry.displayTitle() + (account.isEmpty() ? "" : " · " + account);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.secure_share_choose_password)
                .setItems(labels, (dialog, which) -> {
                    pendingEntry = entries.get(which);
                    openScanner();
                })
                .setNegativeButton(R.string.common_action_cancel, null)
                .show();
    }

    private void openScanner() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, ScannerFragment.forSecureShareCapture())
                .addToBackStack(null).commit();
    }

    private void chooseReceiveLifetime() {
        String[] choices = getResources().getStringArray(R.array.secure_share_lifetime_labels);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.secure_share_choose_lifetime)
                .setItems(choices, (dialog, which) -> createReceiveRequest(LIFETIMES[which]))
                .setNegativeButton(R.string.common_action_cancel, null)
                .show();
    }

    private void createReceiveRequest(long lifetimeMs) {
        try {
            String qr = SecureShareProtocol.createReceiverRequest(lifetimeMs);
            showQrDialog(R.string.secure_share_receive_qr_title, qr, lifetimeMs,
                    R.string.secure_share_scan_response, () -> openScanner());
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.secure_share_generate_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void handleScan(String raw) {
        if (SecureShareProtocol.isRequest(raw)) {
            if (pendingEntry == null) {
                Toast.makeText(requireContext(), R.string.secure_share_choose_password_first, Toast.LENGTH_SHORT).show();
                return;
            }
            ExportSecurityGuard.require(requireActivity(), getString(R.string.secure_share_auth_prompt),
                    () -> createEncryptedResponse(raw));
        } else if (SecureShareProtocol.isResponse(raw)) {
            decryptResponse(raw);
        } else {
            Toast.makeText(requireContext(), R.string.secure_share_invalid_qr, Toast.LENGTH_LONG).show();
        }
    }

    private void createEncryptedResponse(String requestQr) {
        try {
            PasswordEntry entry = pendingEntry;
            JSONObject payload = new JSONObject()
                    .put("title", safe(entry.displayTitle()))
                    .put("website", safe(entry.websiteDomain))
                    .put("username", safe(entry.displayUsername()))
                    .put("password", safe(entry.password))
                    .put("createdAt", entry.createdAt)
                    .put("sharedAt", System.currentTimeMillis());
            String response = SecureShareProtocol.encryptResponse(requestQr, payload);
            SecureShareStateStore.recordShare(requireContext(), entry);
            showQrDialog(R.string.secure_share_response_qr_title, response, remainingLifetime(response),
                    0, null);
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.secure_share_request_expired, Toast.LENGTH_LONG).show();
        }
    }

    private void decryptResponse(String raw) {
        try {
            JSONObject payload = SecureShareProtocol.decryptResponse(raw);
            showReceivedPassword(payload);
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.secure_share_decrypt_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showReceivedPassword(JSONObject payload) {
        String title = payload.optString("title");
        String website = payload.optString("website");
        String username = payload.optString("username");
        String password = payload.optString("password");
        String message = getString(R.string.secure_share_received_detail, title, website, username, password);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.secure_share_received_title)
                .setMessage(message)
                .setNegativeButton(R.string.secure_share_view_only, null)
                .setPositiveButton(R.string.secure_share_save_to_ledger, (dialog, which) -> saveReceived(payload))
                .show();
    }

    private void saveReceived(JSONObject payload) {
        long now = System.currentTimeMillis();
        PasswordEntry entry = new PasswordEntry();
        entry.title = payload.optString("title");
        entry.remark = entry.title;
        entry.websiteDomain = payload.optString("website");
        entry.username = payload.optString("username");
        entry.account = entry.username;
        entry.password = payload.optString("password");
        long sharedAt = payload.optLong("sharedAt", now);
        entry.notes = getString(R.string.secure_share_source_note,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(sharedAt)));
        entry.createdAt = now;
        entry.updatedAt = now;
        repository.insert(entry);
        Toast.makeText(requireContext(), R.string.secure_share_saved, Toast.LENGTH_SHORT).show();
    }

    private void showQrDialog(int titleRes, String content, long lifetimeMs, int actionRes, Runnable action) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(18);
        box.setPadding(padding, padding, padding, 0);
        ImageView image = new ImageView(requireContext());
        Bitmap bitmap = QRGenerator.generateQR(content, dp(300));
        image.setImageBitmap(bitmap);
        box.addView(image, new LinearLayout.LayoutParams(dp(310), dp(310)));
        TextView countdown = new TextView(requireContext());
        countdown.setGravity(Gravity.CENTER);
        countdown.setPadding(0, dp(8), 0, 0);
        box.addView(countdown);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(titleRes).setView(box)
                .setNegativeButton(R.string.common_action_close, null);
        if (actionRes != 0) builder.setPositiveButton(actionRes, null);
        AlertDialog dialog = builder.create();
        CountDownTimer timer = new CountDownTimer(Math.max(1_000L, lifetimeMs), 1_000L) {
            public void onTick(long millis) {
                countdown.setText(getString(R.string.secure_share_expires_seconds, Math.max(1, (millis + 999) / 1000)));
            }
            public void onFinish() {
                countdown.setText(R.string.secure_share_expired);
                if (actionRes != 0 && dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            }
        };
        dialog.setOnShowListener(unused -> {
            if (actionRes != 0) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                if (action != null) action.run();
            });
            timer.start();
        });
        dialog.setOnDismissListener(unused -> timer.cancel());
        dialog.show();
    }

    private long remainingLifetime(String response) {
        try {
            String encoded = response.substring(SecureShareProtocol.RESPONSE_PREFIX.length());
            JSONObject json = new JSONObject(new String(android.util.Base64.decode(encoded,
                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING),
                    java.nio.charset.StandardCharsets.UTF_8));
            return Math.max(1_000L, json.optLong("exp") - System.currentTimeMillis());
        } catch (Exception ignored) {
            return 30_000L;
        }
    }

    private void runOnUi(Runnable action) {
        if (isAdded()) requireActivity().runOnUiThread(() -> { if (isAdded()) action.run(); });
    }

    private void updateDirectHint(TextView hint, PasswordEntry entry) {
        int count = SecureShareStateStore.shareCount(requireContext(), entry);
        long last = SecureShareStateStore.lastSharedAt(requireContext(), entry);
        if (count <= 0 || last <= 0) {
            hint.setText(R.string.secure_share_direct_password_selected);
        } else {
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(last));
            hint.setText(getString(R.string.secure_share_direct_password_status, count, time));
        }
    }

    private String safe(String value) { return value == null ? "" : value; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
