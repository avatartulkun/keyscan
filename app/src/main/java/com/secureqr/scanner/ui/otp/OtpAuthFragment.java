package com.secureqr.scanner.ui.otp;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.ExperimentalGetImage;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.utils.CryptoHelper;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.GlobalWebDavSyncUi;
import com.secureqr.scanner.utils.OtpHelper;
import com.secureqr.scanner.utils.FragmentUi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

@ExperimentalGetImage
public class OtpAuthFragment extends Fragment {
    public static final String OTP_SCAN_REQUEST = "otp_scan_request";
    public static final String OTP_SCAN_VALUE = "otp_scan_value";
    private static final int MENU_SCAN = 1;
    private static final int MENU_MANUAL = 2;
    private static final int MENU_IMPORT = 3;
    private static final int MENU_EXPORT = 4;
    private static final String ARG_INITIAL_ACTION = "initial_action";
    private static final String ARG_SCANNED_URI = "scanned_uri";
    private static final int ACTION_MANUAL = 1;
    private static final int ACTION_BATCH = 2;

    public static OtpAuthFragment manualImport() { return withAction(ACTION_MANUAL, null); }
    public static OtpAuthFragment batchImport() { return withAction(ACTION_BATCH, null); }
    public static OtpAuthFragment scannedUri(String raw) { return withAction(0, raw); }
    private static OtpAuthFragment withAction(int action, String raw) {
        OtpAuthFragment fragment = new OtpAuthFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_INITIAL_ACTION, action);
        if (raw != null) args.putString(ARG_SCANNED_URI, raw);
        fragment.setArguments(args);
        return fragment;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private OtpRepository repository;
    private OtpAdapter adapter;
    private String exportPayload = "";
    private String exportPassword = "";
    private boolean encryptedJsonExport;
    private boolean otpDragInProgress;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (adapter != null) adapter.tick(System.currentTimeMillis());
            handler.postDelayed(this, 1000);
        }
    };

    private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importFile);
    private final ActivityResultLauncher<String> fileCreator = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), this::writeExportFile);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_otp_auth, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = OtpRepository.getInstance(requireContext());
        RecyclerView list = view.findViewById(R.id.recycler_otp);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OtpAdapter(new OtpAdapter.Listener() {
            @Override public void onCopy(String code) { SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.otp_auth_copy_code), () -> copy(code)); }
            @Override public void onEdit(OtpToken token) { OperationModeGuard.requireEdit(OtpAuthFragment.this, () -> SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.otp_auth_edit), () -> showEditDialog(token))); }
            @Override public void onDelete(OtpToken token) { SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.otp_auth_delete), () -> confirmDelete(token)); }
            @Override public void onMoreActions(OtpToken token, String code) { SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.otp_auth_actions), () -> showTokenActions(token, code)); }
            @Override public void onUnlockRequested() { SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.otp_auth_view_code), () -> adapter.notifyDataSetChanged()); }
        });
        list.setAdapter(adapter);
        attachDragSorting(list);

        SearchView search = view.findViewById(R.id.search_otp);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { observe(query); return true; }
            @Override public boolean onQueryTextChange(String newText) { observe(newText); return true; }
        });
        view.findViewById(R.id.btn_otp_export).setOnClickListener(this::showMoreMenu);
        view.findViewById(R.id.btn_otp_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        getParentFragmentManager().setFragmentResultListener(OTP_SCAN_REQUEST, getViewLifecycleOwner(), (requestKey, result) -> addFromUri(result.getString(OTP_SCAN_VALUE, "")));
        observe("");
        handler.post(ticker);
        if (savedInstanceState == null && getArguments() != null) {
            int action = getArguments().getInt(ARG_INITIAL_ACTION, 0);
            String scannedUri = getArguments().getString(ARG_SCANNED_URI, "");
            view.post(() -> {
                if (!scannedUri.isEmpty()) addFromUri(scannedUri);
                else if (action == ACTION_MANUAL) showManualDialog();
                else if (action == ACTION_BATCH) filePicker.launch(new String[]{"text/plain", "application/json", "*/*"});
            });
        }
    }

    private void observe(String query) {
        repository.observe(query).observe(getViewLifecycleOwner(), tokens -> {
            // A Room refresh during a drag resets the item's position and makes sorting feel like it only moves one row.
            if (!otpDragInProgress) adapter.submit(tokens);
        });
    }

    private void attachDragSorting(RecyclerView list) {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    otpDragInProgress = true;
                }
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (!com.secureqr.scanner.security.OperationModeManager.canModify(requireContext())) {
                    OperationModeGuard.requireEdit(OtpAuthFragment.this, () -> {});
                    return false;
                }
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                adapter.move(from, to);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (!otpDragInProgress) return;
                otpDragInProgress = false;
                long updatedAt = System.currentTimeMillis();
                List<OtpToken> ordered = adapter.orderedItems();
                for (int i = 0; i < ordered.size(); i++) {
                    ordered.get(i).sortOrder = i;
                    ordered.get(i).updatedAt = updatedAt;
                }
                repository.updateSortOrder(ordered);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                OtpToken token = adapter.getItem(position);
                adapter.notifyItemChanged(position);
                OperationModeGuard.requireEdit(OtpAuthFragment.this, () -> confirmDelete(token));
            }
        });
        helper.attachToRecyclerView(list);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, MENU_SCAN, 0, R.string.otp_add_scan);
        menu.getMenu().add(0, MENU_MANUAL, 1, R.string.otp_add_manual);
        menu.getMenu().add(0, MENU_IMPORT, 2, R.string.otp_add_bulk_import);
        menu.getMenu().add(0, MENU_EXPORT, 3, R.string.otp_menu_export_records);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_SCAN) openScanner();
            else if (item.getItemId() == MENU_MANUAL) showManualDialog();
            else if (item.getItemId() == MENU_IMPORT) filePicker.launch(new String[]{"text/plain", "application/json", "*/*"});
            else ExportSecurityGuard.require(requireActivity(), getString(R.string.export_auth_prompt), this::exportTokens);
            return true;
        });
        menu.show();
    }

    private void openScanner() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, ScannerFragment.forOtpCapture())
                .addToBackStack(null)
                .commit();
    }

    private void showManualDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText issuer = input(getString(R.string.otp_issuer_hint));
        EditText account = input(getString(R.string.otp_account_hint));
        EditText secret = input(getString(R.string.otp_secret_hint));
        content.addView(issuer);
        content.addView(account);
        content.addView(secret);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.otp_add_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (secret.getText().toString().trim().isEmpty() || account.getText().toString().trim().isEmpty()) {
                secret.setError(getString(R.string.otp_secret_required));
                return;
            }
            OtpToken token = new OtpToken();
            token.issuer = issuer.getText().toString().trim();
            token.accountName = account.getText().toString().trim();
            token.secret = OtpHelper.normalizeSecret(secret.getText().toString());
            long now = System.currentTimeMillis();
            token.createdAt = now;
            token.updatedAt = now;
            repository.insert(token);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showEditDialog(OtpToken token) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText issuer = input(getString(R.string.otp_issuer_hint));
        EditText account = input(getString(R.string.otp_account_hint));
        issuer.setText(token.issuer == null ? "" : token.issuer);
        account.setText(token.accountName == null ? "" : token.accountName);
        content.addView(issuer);
        content.addView(account);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.otp_edit_title)
                .setMessage(R.string.otp_secret_edit_locked)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String accountValue = account.getText().toString().trim();
            if (accountValue.isEmpty()) {
                account.setError(getString(R.string.otp_account_empty_error));
                return;
            }
            token.issuer = issuer.getText().toString().trim();
            token.accountName = accountValue;
            token.updatedAt = System.currentTimeMillis();
            repository.update(token);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void addFromUri(String raw) {
        try {
            repository.insert(OtpHelper.parseUri(requireContext(), raw));
            Toast.makeText(requireContext(), R.string.otp_added_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void importFile(Uri uri) {
        if (uri == null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(requireContext().getContentResolver().openInputStream(uri)))) {
            String line;
            StringBuilder raw = new StringBuilder();
            while ((line = reader.readLine()) != null) raw.append(line).append('\n');
            String payload = raw.toString();
            int count = importPayload(payload);
            if (count == 0 && payload.trim().startsWith("{")) count = importJsonPayload(payload);
            if (count == 0 && !payload.contains("otpauth://")) showImportPasswordDialog(payload);
            Toast.makeText(requireContext(), getString(R.string.otp_import_done, count), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportTokens() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.otp_export_title)
                .setItems(new String[]{getString(R.string.otp_export_uri_list), getString(R.string.otp_export_encrypted_json)}, (dialog, which) -> {
                    if (which == 0) exportUriList();
                    else exportEncryptedJson();
                })
                .show();
    }

    private void exportUriList() {
        encryptedJsonExport = false;
        exportPassword = "";
        repository.getAll(tokens -> {
            StringBuilder builder = new StringBuilder();
            for (OtpToken token : tokens) builder.append(OtpHelper.toUri(token)).append('\n');
            exportPayload = builder.toString();
            runOnUi(() -> fileCreator.launch("keyscan_otp_uri_export.txt"));
        });
    }

    private void exportEncryptedJson() {
        EditText input = input(getString(R.string.otp_export_password_hint));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.otp_export_title)
                .setMessage(R.string.otp_export_password_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    exportPassword = input.getText().toString();
                    if (exportPassword.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.otp_export_password_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    encryptedJsonExport = true;
                    repository.getAll(tokens -> {
                        exportPayload = tokensToJson(tokens);
                        runOnUi(() -> fileCreator.launch("keyscan_otp_export.json"));
                    });
                })
                .show();
    }

    private void writeExportFile(Uri uri) {
        if (uri == null) return;
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
            String body = encryptedJsonExport ? CryptoHelper.encrypt(exportPayload, exportPassword, "AES-GCM") : exportPayload;
            out.write(body.getBytes());
            Toast.makeText(requireContext(), R.string.otp_export_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private int importPayload(String payload) {
        int count = 0;
        for (String line : payload.split("\\R")) {
            int idx = line.indexOf("otpauth://");
            if (idx >= 0) {
                addFromUri(line.substring(idx).trim());
                count++;
            }
        }
        return count;
    }

    private int importJsonPayload(String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            JSONArray array = root.optJSONArray("otpTokens");
            if (array == null) return 0;
            int count = 0;
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                OtpToken token = new OtpToken();
                token.issuer = object.optString("issuer");
                token.accountName = object.optString("accountName");
                token.secret = OtpHelper.normalizeSecret(object.optString("secret"));
                token.digits = object.optInt("digits", 6);
                token.period = object.optInt("period", 30);
                token.algorithm = object.optString("algorithm", "SHA1");
                long now = System.currentTimeMillis();
                token.createdAt = object.optLong("createdAt", now);
                token.updatedAt = now;
                repository.insert(token);
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private String tokensToJson(List<OtpToken> tokens) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "keyscan-otp-json");
            JSONArray array = new JSONArray();
            for (OtpToken token : tokens) {
                JSONObject object = new JSONObject();
                object.put("issuer", token.issuer);
                object.put("accountName", token.accountName);
                object.put("secret", token.secret);
                object.put("digits", token.digits);
                object.put("period", token.period);
                object.put("algorithm", token.algorithm);
                object.put("createdAt", token.createdAt);
                array.put(object);
            }
            root.put("otpTokens", array);
            return root.toString();
        } catch (Exception e) {
            return "{\"otpTokens\":[]}";
        }
    }

    private void showImportPasswordDialog(String encryptedPayload) {
        EditText input = input(getString(R.string.otp_import_file_password_hint));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.otp_import_file_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.import_action, (dialog, which) -> {
                    try {
                        String decrypted = CryptoHelper.decrypt(encryptedPayload.trim(), input.getText().toString());
                        int count = decrypted.trim().startsWith("{") ? importJsonPayload(decrypted) : importPayload(decrypted);
                        Toast.makeText(requireContext(), getString(R.string.otp_import_done, count), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), R.string.otp_import_file_empty_password, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void confirmDelete(OtpToken token) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.otp_delete_title)
                .setMessage(R.string.otp_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> repository.delete(token))
                .show();
    }

    private void showTokenActions(OtpToken token, String code) {
        new AlertDialog.Builder(requireContext())
                .setTitle(token.issuer == null || token.issuer.isEmpty() ? getString(R.string.otp_action_title) : token.issuer)
                .setItems(new String[]{getString(R.string.action_edit), getString(R.string.action_delete), getString(R.string.action_copy_account), getString(R.string.action_copy_secret), getString(R.string.action_copy_code)}, (dialog, which) -> {
                    if (which == 0) {
                        OperationModeGuard.requireEdit(this, () -> showEditDialog(token));
                    } else if (which == 1) {
                        OperationModeGuard.requireEdit(this, () -> confirmDelete(token));
                    } else if (which == 2) {
                        copyText(getString(R.string.otp_copy_account), token.accountName);
                    } else if (which == 3) {
                        copyText(getString(R.string.otp_copy_secret), token.secret);
                    } else {
                        copy(code);
                    }
                })
                .show();
    }

    private void copy(String code) {
        copyText("KeyScan TOTP", code);
    }

    private void copyText(String label, String text) {
        SecureClipboard.copySensitive(requireContext(), label, text == null ? "" : text);
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private EditText input(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF636366);
        return input;
    }

    private void runOnUi(Runnable runnable) {
        FragmentUi.run(this, runnable);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
    }
}

