package com.secureqr.scanner.ui.exporter;

import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.exporter.BitwardenJsonExporter;
import com.secureqr.scanner.exporter.ExportOtpItem;
import com.secureqr.scanner.exporter.ExportPasswordItem;
import com.secureqr.scanner.exporter.ExportVaultItem;
import com.secureqr.scanner.exporter.OtpJsonExporter;
import com.secureqr.scanner.exporter.OtpUriExporter;
import com.secureqr.scanner.exporter.PasswordCsvExporter;
import com.secureqr.scanner.exporter.VaultJsonExporter;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.FragmentUi;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

public class ExportDataFragment extends Fragment {
    private static final int PASSWORD_FORMAT_CSV = 1;
    private static final int PASSWORD_FORMAT_BITWARDEN = 2;
    private static final int OTP_FORMAT_URI = 3;
    private static final int OTP_FORMAT_JSON = 4;

    private CheckBox passwordCheck;
    private CheckBox otpCheck;
    private CheckBox vaultCheck;
    private RadioGroup passwordFormatGroup;
    private RadioGroup otpFormatGroup;
    private LinearLayout passwordFormatCard;
    private LinearLayout otpFormatCard;
    private LinearLayout vaultFormatCard;
    private TextView statusText;
    private byte[] pendingBytes;

    private ActivityResultLauncher<String> csvDocumentCreator;
    private ActivityResultLauncher<String> jsonDocumentCreator;
    private ActivityResultLauncher<String> textDocumentCreator;
    private ActivityResultLauncher<String> zipDocumentCreator;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        csvDocumentCreator = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), this::writeExportFile);
        jsonDocumentCreator = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::writeExportFile);
        textDocumentCreator = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), this::writeExportFile);
        zipDocumentCreator = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), this::writeExportFile);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_light));
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        ImageButton home = new ImageButton(requireContext());
        home.setImageResource(R.drawable.ic_home_24);
        home.setBackgroundColor(0x00000000);
        home.setContentDescription(getString(R.string.transfer_home_desc));
        home.setOnClickListener(v -> NavigationHelper.openHome(this));
        top.addView(home, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text(getString(R.string.export_data_title), 26, R.color.text_main, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = dp(8);
        top.addView(title, titleParams);
        root.addView(top, matchWrap(0));

        root.addView(infoCard(), matchWrap(16));
        root.addView(contentCard(), matchWrap(16));
        passwordFormatCard = passwordFormatCard();
        otpFormatCard = otpFormatCard();
        vaultFormatCard = vaultFormatCard();
        root.addView(passwordFormatCard, matchWrap(12));
        root.addView(otpFormatCard, matchWrap(12));
        root.addView(vaultFormatCard, matchWrap(12));
        root.addView(warningCard(), matchWrap(12));

        Button export = new Button(requireContext());
        export.setText(R.string.export_action);
        export.setOnClickListener(v -> confirmExport());
        root.addView(export, matchWrap(18));

        statusText = text("", 13, R.color.text_secondary, false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, matchWrap(10));
        updateFormatVisibility();
        return scroll;
    }

    private LinearLayout infoCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.export_migration_warning), 15, R.color.text_main, true));
        card.addView(text(getString(R.string.export_scope_note), 13, R.color.text_secondary, false), matchWrap(8));
        return card;
    }

    private LinearLayout contentCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.export_choose_content), 17, R.color.text_main, true));
        passwordCheck = checkbox(getString(R.string.export_password_vault), true);
        otpCheck = checkbox(getString(R.string.export_otp), false);
        vaultCheck = checkbox(getString(R.string.export_vault_text), false);
        passwordCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateFormatVisibility());
        otpCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateFormatVisibility());
        vaultCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updateFormatVisibility());
        card.addView(passwordCheck, matchWrap(10));
        card.addView(otpCheck, matchWrap(4));
        card.addView(vaultCheck, matchWrap(4));
        card.addView(text(getString(R.string.export_supported_note), 13, R.color.text_secondary, false), matchWrap(8));
        return card;
    }

    private LinearLayout passwordFormatCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.export_password_format), 17, R.color.text_main, true));
        passwordFormatGroup = new RadioGroup(requireContext());
        passwordFormatGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton csv = radio("CSV");
        csv.setId(PASSWORD_FORMAT_CSV);
        csv.setChecked(true);
        RadioButton bitwarden = radio("Bitwarden JSON");
        bitwarden.setId(PASSWORD_FORMAT_BITWARDEN);
        passwordFormatGroup.addView(csv);
        passwordFormatGroup.addView(bitwarden);
        card.addView(passwordFormatGroup, matchWrap(10));
        return card;
    }

    private LinearLayout otpFormatCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.export_otp_format), 17, R.color.text_main, true));
        otpFormatGroup = new RadioGroup(requireContext());
        otpFormatGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton uri = radio("otpauth URI");
        uri.setId(OTP_FORMAT_URI);
        uri.setChecked(true);
        RadioButton json = radio("JSON");
        json.setId(OTP_FORMAT_JSON);
        otpFormatGroup.addView(uri);
        otpFormatGroup.addView(json);
        card.addView(otpFormatGroup, matchWrap(10));
        return card;
    }

    private LinearLayout vaultFormatCard() {
        LinearLayout card = card();
        card.addView(text(getString(R.string.export_vault_format), 17, R.color.text_main, true));
        card.addView(text(getString(R.string.export_vault_json_note), 13, R.color.text_secondary, false), matchWrap(6));
        return card;
    }

    private LinearLayout warningCard() {
        LinearLayout card = card();
        card.setBackgroundResource(R.drawable.bg_detail_secret);
        card.addView(text(getString(R.string.security_tip), 16, R.color.text_main, true));
        card.addView(text(getString(R.string.export_security_warning), 13, R.color.text_secondary, false), matchWrap(8));
        return card;
    }

    private void confirmExport() {
        if (!includePasswords() && !includeOtp() && !includeVault()) {
            Toast.makeText(requireContext(), R.string.export_select_one, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.export_confirm_title)
                .setMessage(R.string.export_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, (dialog, which) ->
                        ExportSecurityGuard.require(requireActivity(), getString(R.string.export_auth_prompt), this::exportSelected))
                .show();
    }

    private void exportSelected() {
        ExportRequest request = new ExportRequest();
        request.includePasswords = includePasswords();
        request.includeOtp = includeOtp();
        request.includeVault = includeVault();
        request.passwordFormat = passwordFormatGroup == null ? PASSWORD_FORMAT_CSV : passwordFormatGroup.getCheckedRadioButtonId();
        request.otpFormat = otpFormatGroup == null ? OTP_FORMAT_URI : otpFormatGroup.getCheckedRadioButtonId();
        setStatus(getString(R.string.export_reading));

        if (request.includePasswords) {
            PasswordRepository repository = PasswordRepository.getInstance(requireContext());
            repository.getGroups(groups -> repository.getAll(entries -> {
                request.passwordItems = mapPasswords(entries, groups);
                loadOtpIfNeeded(request);
            }));
            return;
        }
        loadOtpIfNeeded(request);
    }

    private void loadOtpIfNeeded(ExportRequest request) {
        if (!request.includeOtp) {
            loadVaultIfNeeded(request);
            return;
        }
        OtpRepository.getInstance(requireContext()).getAll(tokens -> {
            request.otpItems = mapOtp(tokens);
            loadVaultIfNeeded(request);
        });
    }

    private void loadVaultIfNeeded(ExportRequest request) {
        if (!request.includeVault) {
            finishExport(request);
            return;
        }
        VaultRepository vaultRepository = new VaultRepository(requireContext());
        vaultRepository.getAllNow(items -> {
            request.vaultItems = mapVault(items);
            finishExport(request);
        });
    }

    private void finishExport(ExportRequest request) {
        try {
            boolean hasPasswords = request.passwordItems != null && !request.passwordItems.isEmpty();
            boolean hasOtp = request.otpItems != null && !request.otpItems.isEmpty();
            boolean hasVault = request.vaultItems != null && !request.vaultItems.isEmpty();
            if (!hasPasswords && !hasOtp && !hasVault) {
                runOnUi(() -> {
                    setStatus("");
                    Toast.makeText(requireContext(), R.string.export_no_data, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            ExportPayload payload = buildPayload(request, hasPasswords, hasOtp, hasVault);
            pendingBytes = payload.bytes;
            runOnUi(() -> {
                setStatus(getString(R.string.export_choose_location));
                launchDocumentCreator(payload);
            });
        } catch (Exception e) {
            runOnUi(() -> {
                setStatus("");
                Toast.makeText(requireContext(), R.string.export_failed_short, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private ExportPayload buildPayload(ExportRequest request, boolean hasPasswords, boolean hasOtp, boolean hasVault) throws Exception {
        if ((hasPasswords && hasOtp) || (hasPasswords && hasVault) || (hasOtp && hasVault) || (hasPasswords && hasOtp && hasVault)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                if (hasPasswords) {
                    addZipEntry(zip, passwordFilename(request.passwordFormat), passwordBody(request).getBytes(StandardCharsets.UTF_8));
                }
                if (hasOtp) {
                    addZipEntry(zip, otpFilename(request.otpFormat), otpBody(request).getBytes(StandardCharsets.UTF_8));
                }
                if (hasVault) {
                    addZipEntry(zip, "vault.json", vaultBody(request).getBytes(StandardCharsets.UTF_8));
                }
            }
            return new ExportPayload(bytes.toByteArray(), "KeyScan_Export.zip", "zip");
        }
        if (hasPasswords) {
            String body = passwordBody(request);
            String filename = passwordFilename(request.passwordFormat);
            String type = request.passwordFormat == PASSWORD_FORMAT_BITWARDEN ? "json" : "csv";
            return new ExportPayload(body.getBytes(StandardCharsets.UTF_8), filename, type);
        }
        if (hasOtp) {
            String body = otpBody(request);
            String filename = otpFilename(request.otpFormat);
            String type = request.otpFormat == OTP_FORMAT_JSON ? "json" : "text";
            return new ExportPayload(body.getBytes(StandardCharsets.UTF_8), filename, type);
        }
        String body = vaultBody(request);
        return new ExportPayload(body.getBytes(StandardCharsets.UTF_8), "vault.json", "json");
    }

    private String passwordBody(ExportRequest request) throws Exception {
        if (request.passwordFormat == PASSWORD_FORMAT_BITWARDEN) {
            return BitwardenJsonExporter.export(request.passwordItems);
        }
        return PasswordCsvExporter.export(request.passwordItems);
    }

    private String otpBody(ExportRequest request) throws Exception {
        if (request.otpFormat == OTP_FORMAT_JSON) {
            return OtpJsonExporter.export(request.otpItems);
        }
        return OtpUriExporter.export(request.otpItems);
    }

    private String vaultBody(ExportRequest request) throws Exception {
        return VaultJsonExporter.export(request.vaultItems);
    }

    private String passwordFilename(int format) {
        return format == PASSWORD_FORMAT_BITWARDEN ? "keyscan_passwords_bitwarden.json" : "keyscan_passwords.csv";
    }

    private String otpFilename(int format) {
        return format == OTP_FORMAT_JSON ? "keyscan_otp.json" : "keyscan_otp_otpauth.txt";
    }

    private void addZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private void launchDocumentCreator(ExportPayload payload) {
        if ("zip".equals(payload.type)) {
            zipDocumentCreator.launch(payload.filename);
        } else if ("json".equals(payload.type)) {
            jsonDocumentCreator.launch(payload.filename);
        } else if ("text".equals(payload.type)) {
            textDocumentCreator.launch(payload.filename);
        } else {
            csvDocumentCreator.launch(payload.filename);
        }
    }

    private List<ExportPasswordItem> mapPasswords(List<PasswordEntry> entries, List<PasswordGroup> groups) {
        Map<String, String> groupNames = new HashMap<>();
        if (groups != null) {
            for (PasswordGroup group : groups) {
                if (group != null && group.id != null) groupNames.put(group.id, group.displayName());
            }
        }
        List<ExportPasswordItem> out = new ArrayList<>();
        if (entries == null) return out;
        for (PasswordEntry entry : entries) {
            if (entry == null) continue;
            boolean titleFromRemark = isBlank(entry.title) && !isBlank(entry.remark);
            String title = firstNonEmpty(entry.title, entry.remark, entry.websiteDomain);
            String notes = firstNonEmpty(entry.notes, titleFromRemark ? "" : entry.remark);
            out.add(new ExportPasswordItem(
                    title,
                    entry.websiteDomain,
                    firstNonEmpty(entry.username, entry.account),
                    entry.account,
                    entry.password,
                    notes,
                    groupNames.get(entry.groupId)
            ));
        }
        return out;
    }

    private List<ExportOtpItem> mapOtp(List<OtpToken> tokens) {
        List<ExportOtpItem> out = new ArrayList<>();
        if (tokens == null) return out;
        for (OtpToken token : tokens) {
            if (token == null || isBlank(token.secret)) continue;
            out.add(new ExportOtpItem(
                    token.issuer,
                    token.accountName,
                    token.secret,
                    token.algorithm,
                    token.digits,
                    token.period,
                    token.pinned,
                    token.sortOrder
            ));
        }
        return out;
    }

    private List<ExportVaultItem> mapVault(List<VaultItem> items) {
        List<ExportVaultItem> out = new ArrayList<>();
        if (items == null) return out;
        for (VaultItem item : items) {
            if (item == null) continue;
            out.add(new ExportVaultItem(
                    item.title,
                    item.type,
                    item.category,
                    parseFields(item.fieldsJson),
                    item.notes,
                    item.createdTime,
                    item.updatedTime
            ));
        }
        return out;
    }

    private String parseFields(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "{}";
        try {
            JSONObject object = new JSONObject(raw);
            return object.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void writeExportFile(Uri uri) {
        if (uri == null || pendingBytes == null) {
            setStatus("");
            return;
        }
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("open output failed");
            out.write(pendingBytes);
            Toast.makeText(requireContext(), R.string.export_completed, Toast.LENGTH_SHORT).show();
            setStatus(getString(R.string.export_completed));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.export_failed_short, Toast.LENGTH_SHORT).show();
            setStatus("");
        } finally {
            pendingBytes = null;
        }
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox box = new CheckBox(requireContext());
        box.setText(label);
        box.setChecked(checked);
        box.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        box.setTextSize(15);
        return box;
    }

    private RadioButton radio(String label) {
        RadioButton button = new RadioButton(requireContext());
        button.setText(label);
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main));
        button.setTextSize(15);
        button.setPadding(0, dp(4), 0, dp(4));
        return button;
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
        text.setLineSpacing(0, 1.18f);
        return text;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMargin);
        return params;
    }

    private void updateFormatVisibility() {
        if (passwordFormatCard != null) passwordFormatCard.setVisibility(includePasswords() ? View.VISIBLE : View.GONE);
        if (otpFormatCard != null) otpFormatCard.setVisibility(includeOtp() ? View.VISIBLE : View.GONE);
        if (vaultFormatCard != null) vaultFormatCard.setVisibility(includeVault() ? View.VISIBLE : View.GONE);
    }

    private boolean includePasswords() {
        return passwordCheck != null && passwordCheck.isChecked();
    }

    private boolean includeOtp() {
        return otpCheck != null && otpCheck.isChecked();
    }

    private boolean includeVault() {
        return vaultCheck != null && vaultCheck.isChecked();
    }

    private void setStatus(String value) {
        if (statusText != null) statusText.setText(value == null ? "" : value);
    }

    private void runOnUi(Runnable runnable) {
        FragmentUi.run(this, runnable);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "";
    }

    private static final class ExportRequest {
        boolean includePasswords;
        boolean includeOtp;
        boolean includeVault;
        int passwordFormat;
        int otpFormat;
        List<ExportPasswordItem> passwordItems = new ArrayList<>();
        List<ExportOtpItem> otpItems = new ArrayList<>();
        List<ExportVaultItem> vaultItems = new ArrayList<>();
    }

    private static final class ExportPayload {
        final byte[] bytes;
        final String filename;
        final String type;

        ExportPayload(byte[] bytes, String filename, String type) {
            this.bytes = bytes;
            this.filename = filename;
            this.type = type;
        }
    }
}
