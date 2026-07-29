package com.secureqr.scanner.ui.notes;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.secureqr.scanner.R;
import com.secureqr.scanner.clipboard.ClipboardImportSession;
import com.secureqr.scanner.clipboard.ClipboardImportSettings;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.data.model.PasswordNote;
import com.secureqr.scanner.data.repository.PasswordNoteRepository;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.SensitiveWindowGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.OperationModeManager;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.GlobalWebDavSyncUi;
import com.secureqr.scanner.utils.ExcelExportHelper;
import com.secureqr.scanner.utils.PasswordGeneratorEngine;
import com.secureqr.scanner.utils.BiometricUnlockHelper;
import com.secureqr.scanner.utils.PinLockHelper;
import com.secureqr.scanner.utils.FragmentUi;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PasswordNoteFragment extends Fragment {
    private static final String ARG_LOGIN_ONLY = "login_only";
    private static final long SENSITIVE_REVEAL_MS = 30_000L;
    private final Handler sensitiveHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> hideSensitiveTasks = new ArrayList<>();
    private PasswordNoteRepository repository;
    private PasswordNoteAdapter adapter;
    private LiveData<List<PasswordNote>> observed;
    private String query = "";
    private boolean loginOnly;
    private View emptyView;

    public static PasswordNoteFragment loginOnly() {
        PasswordNoteFragment fragment = new PasswordNoteFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_LOGIN_ONLY, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_password_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        loginOnly = getArguments() != null && getArguments().getBoolean(ARG_LOGIN_ONLY);
        repository = PasswordNoteRepository.getInstance(requireContext());
        repository.syncLegacyPasswordEntries();

        TextView title = view.findViewById(R.id.tv_password_notes_title);
        title.setText(loginOnly ? R.string.home_password_forge : R.string.home_password_notes);
        view.findViewById(R.id.btn_notes_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        view.findViewById(R.id.btn_add_password_note).setOnClickListener(v -> showTypePicker());
        view.findViewById(R.id.btn_password_notes_menu).setOnClickListener(this::showExportMenu);
        emptyView = view.findViewById(R.id.layout_notes_empty);

        RecyclerView list = view.findViewById(R.id.recycler_password_notes);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PasswordNoteAdapter(new PasswordNoteAdapter.Listener() {
            @Override public void onOpen(PasswordNote note) { openNote(note); }
            @Override public void onDelete(PasswordNote note) { confirmDelete(note); }
        });
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return adapterNote(viewHolder.getBindingAdapterPosition()) == null ? 0 : super.getSwipeDirs(recyclerView, viewHolder);
            }
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                PasswordNote note = adapterNote(position);
                if (note != null) confirmDelete(note);
                if (position >= 0) adapter.notifyItemChanged(position);
            }
        }).attachToRecyclerView(list);

        SearchView search = view.findViewById(R.id.search_password_notes);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String newText) { bind(newText); return true; }
            @Override public boolean onQueryTextChange(String newText) { bind(newText); return true; }
        });
        bind("");
        view.post(this::maybeShowClipboardImportPrompt);
    }

    private void showExportMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.export_backup);
        menu.setOnMenuItemClickListener(item -> {
            ExportSecurityGuard.require(requireActivity(), getString(R.string.export_auth_prompt), this::showExportPasswordChoice);
            return true;
        });
        menu.show();
    }

    private void showExportPasswordChoice() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_export_open_password_title)
                .setMessage(R.string.password_export_open_password_message)
                .setPositiveButton(R.string.password_export_yes, (dialog, which) -> showExportPasswordInput())
                .setNegativeButton(R.string.password_export_no, (dialog, which) -> exportNotes(""))
                .show();
    }

    private void showExportPasswordInput() {
        EditText input = input(getString(R.string.password_export_password_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_export_set_password_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.password_export_action, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = input.getText().toString();
            if (password.isEmpty()) {
                input.setError(getString(R.string.password_export_password_required));
                return;
            }
            dialog.dismiss();
            exportNotes(password);
        }));
        dialog.show();
    }

    private void exportNotes(String password) {
        repository.getAllNow(notes -> {
            try {
                File exported = writeNotesExport(notes, password);
                FragmentUi.run(PasswordNoteFragment.this, () -> {
                    Toast.makeText(requireContext(), R.string.password_export_success, Toast.LENGTH_SHORT).show();
                    shareExportFile(exported);
                });
            } catch (Exception e) {
                FragmentUi.run(PasswordNoteFragment.this, () -> Toast.makeText(requireContext(), getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private File writeNotesExport(List<PasswordNote> notes, String password) throws Exception {
        File dir = new File(requireContext().getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException(getString(R.string.legacy_export_create_dir_failed));
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "keyscan_secure_vault_" + stamp + ".xlsx");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        List<List<String>> rows = new ArrayList<>();
        for (PasswordNote note : notes) {
            rows.add(Arrays.asList(
                    PasswordNoteAdapter.typeLabel(requireContext(), note.type),
                    firstNonEmpty(note.title, note.primaryText),
                    firstNonEmpty(note.primaryText),
                    firstNonEmpty(note.secondaryText),
                    note.contentJson == null ? "{}" : note.contentJson,
                    dateFormat.format(new Date(note.createdAt)),
                    dateFormat.format(new Date(note.updatedAt))
            ));
        }
        byte[] data = ExcelExportHelper.workbookBytes(
                "SecureVault",
                Arrays.asList(getString(R.string.note_export_type), getString(R.string.note_export_title),
                        getString(R.string.note_export_primary), getString(R.string.note_export_secondary),
                        getString(R.string.note_export_details), getString(R.string.note_export_created),
                        getString(R.string.note_export_updated)),
                rows,
                password
        );
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
        return file;
    }

    private void shareExportFile(File file) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.password_export_share)));
    }

    @Override
    public void onStop() {
        super.onStop();
        hideAllSensitiveFields();
    }

    private PasswordNote adapterNote(int position) {
        return adapter == null ? null : adapter.getItem(position);
    }

    private void openNote(PasswordNote note) {
        if (note == null) return;
        if (!isSensitiveNoteType(note.type)) {
            showEditor(note.type, note);
            return;
        }
        requireSensitiveUnlock(() -> showSensitiveDetail(note));
    }

    private boolean isSensitiveNoteType(String type) {
        return PasswordNote.TYPE_BANK_CARD.equals(type)
                || PasswordNote.TYPE_SOFTWARE_LICENSE.equals(type)
                || PasswordNote.TYPE_SERVER.equals(type)
                || PasswordNote.TYPE_IDENTITY.equals(type)
                || PasswordNote.TYPE_SECURE_NOTE.equals(type)
                || PasswordNote.TYPE_CUSTOM.equals(type);
    }

    private void requireSensitiveUnlock(Runnable onUnlocked) {
        if (!PinLockHelper.isConfigured(requireContext())) {
            Toast.makeText(requireContext(), R.string.password_ledger_setup_required, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText input = plainInput();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        content.addView(label(getString(R.string.password_ledger_password)));
        content.addView(input, topParams(52, 6));

        Button biometricButton = null;
        if (BiometricUnlockHelper.isEnabled(requireContext())) {
            biometricButton = compactButton(getString(R.string.biometric_unlock_button));
            content.addView(biometricButton, topParams(42, 10));
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.view_sensitive_note)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        Button finalBiometricButton = biometricButton;
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long remaining = PinLockHelper.remainingLockMs(requireContext());
                if (remaining > 0) {
                    input.setError(getString(R.string.password_ledger_unlock_error));
                    return;
                }
                if (!PinLockHelper.verifyPin(requireContext(), input.getText().toString())) {
                    PinLockHelper.recordFailedAttempt(requireContext());
                    input.setError(getString(R.string.password_ledger_unlock_error));
                    return;
                }
                PinLockHelper.clearFailedAttempts(requireContext());
                dialog.dismiss();
                onUnlocked.run();
            });
            if (finalBiometricButton != null) {
                finalBiometricButton.setOnClickListener(v -> BiometricUnlockHelper.prompt(
                        (FragmentActivity) requireActivity(),
                        () -> {
                            dialog.dismiss();
                            onUnlocked.run();
                        },
                        null
                ));
            }
        });
        dialog.show();
    }

    private void showSensitiveDetail(PasswordNote note) {
        if (note == null) return;
        boolean wasSecure = SensitiveWindowGuard.enable(requireActivity());
        JSONObject object;
        try {
            object = new JSONObject(note.contentJson == null ? "{}" : note.contentJson);
        } catch (Exception e) {
            object = new JSONObject();
        }

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        List<DetailSection> sections = detailSections(note, object);
        for (DetailSection section : sections) {
            addDetailSection(content, section, false);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(firstNonEmpty(note.title, note.primaryText, note.secondaryText, getString(R.string.untitled)))
                .setView(scrollView)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.edit, null)
                .create();
        dialog.setOnDismissListener(d -> {
            hideAllSensitiveFields();
            SensitiveWindowGuard.restore(requireActivity(), wasSecure);
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.dismiss();
            showEditor(note.type, note);
        }));
        dialog.show();
    }

    private List<DetailSection> detailSections(PasswordNote note, JSONObject object) {
        List<DetailSection> sections = new ArrayList<>();
        DetailSection related = new DetailSection(getString(R.string.related_information));
        String noteTitle = note.title == null ? "" : note.title.trim();
        String typeLabel = PasswordNoteAdapter.typeLabel(requireContext(), note.type);
        if (!noteTitle.isEmpty() && !noteTitle.equals(typeLabel)) {
            related.fields.add(new DetailField(getString(R.string.title), noteTitle, false, "title"));
        }

        for (DynamicField dynamicField : readDynamicFields(note.type, object)) {
            if (dynamicField.value == null || dynamicField.value.trim().isEmpty()) continue;
            if (isSystemDuplicateTypeField(note.type, dynamicField)) continue;
            DetailField field = new DetailField(dynamicField.label, dynamicField.value, dynamicField.sensitive, dynamicField.key, dynamicField.type);
            related.fields.add(field);
        }
        if (!related.fields.isEmpty()) sections.add(related);
        return sections;
    }

    private boolean isSystemDuplicateTypeField(String noteType, DynamicField field) {
        String typeLabel = PasswordNoteAdapter.typeLabel(requireContext(), noteType);
        String label = field.label == null ? "" : field.label.trim();
        String value = field.value == null ? "" : field.value.trim();
        String key = field.key == null ? "" : field.key.trim();
        if (!typeLabel.equals(value) && !typeLabel.equals(label)) return false;
        return isTypeMetaKey(key) || isTypeMetaLabel(label) || typeLabel.equals(label) || typeLabel.equals(value);
    }

    private boolean isTypeMetaKey(String key) {
        if (key == null) return false;
        String normalized = key.trim().toLowerCase();
        return "type".equals(normalized)
                || "category".equals(normalized)
                || "kind".equals(normalized)
                || "record_type".equals(normalized)
                || "note_type".equals(normalized)
                || "label".equals(normalized);
    }

    private boolean isTypeMetaLabel(String label) {
        if (label == null) return false;
        String normalized = label.trim();
        return "\u7c7b\u578b".equals(normalized)
                || "\u7c7b\u522b".equals(normalized)
                || "\u5206\u7c7b".equals(normalized)
                || "Type".equalsIgnoreCase(normalized)
                || "Category".equalsIgnoreCase(normalized)
                || "Kind".equalsIgnoreCase(normalized);
    }

    private String primarySectionTitle(String type) {
        return getString(R.string.related_information);
    }

    private void addDetailSection(LinearLayout parent, DetailSection section, boolean expandedByDefault) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackgroundResource(R.drawable.bg_edit_text);

        TextView header = new TextView(requireContext());
        header.setTextSize(16);
        header.setTextColor(getResources().getColor(R.color.text_main));
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        final boolean[] expanded = {expandedByDefault};

        Runnable updateHeader = () -> {
            header.setText((expanded[0] ? "\u25be " : "\u25b8 ") + section.title);
            body.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            if (!expanded[0]) hideSensitiveFieldsIn(body);
        };
        header.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            updateHeader.run();
        });
        box.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        for (DetailField field : section.fields) {
            addDetailField(body, field);
        }
        box.addView(body);
        updateHeader.run();

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        parent.addView(box, params);
    }

    private void addDetailField(LinearLayout parent, DetailField field) {
        TextView fieldLabel = label(field.label);
        parent.addView(fieldLabel, topParams(24, 8));

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = new TextView(requireContext());
        value.setTextColor(getResources().getColor(R.color.text_main));
        value.setTextSize(14);
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        value.setPadding(dp(12), dp(8), dp(12), dp(8));
        value.setBackgroundResource(R.drawable.bg_edit_text);
        value.setSingleLine(false);
        value.setMaxLines(field.value.length() > 120 ? 6 : 3);
        value.setText(field.sensitive ? maskSensitiveValue(field) : field.value);
        row.addView(value, new LinearLayout.LayoutParams(0, dp(field.value.length() > 120 ? 104 : 52), 1));

        if (field.sensitive && !field.value.trim().isEmpty()) {
            ImageButton eye = compactIconButton(R.drawable.ic_visibility_off_24, getString(R.string.show_content));
            ImageButton copy = compactIconButton(R.drawable.ic_content_copy_24, getString(R.string.copy));
            final boolean[] visible = {false};
            Runnable hide = () -> {
                visible[0] = false;
                value.setText(maskSensitiveValue(field));
                setButtonIcon(eye, R.drawable.ic_visibility_off_24, getString(R.string.show_content));
            };
            value.setTag(hide);
            eye.setOnClickListener(v -> {
                if (visible[0]) {
                    hide.run();
                    return;
                }
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.view_sensitive_content), () -> {
                    visible[0] = true;
                    value.setText(field.value);
                    setButtonIcon(eye, R.drawable.ic_visibility_24, getString(R.string.hide_content));
                    scheduleSensitiveHide(hide);
                });
            });
            copy.setOnClickListener(v -> SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.copy_sensitive_content), () -> {
                SecureClipboard.copySensitive(requireContext(), "KeyScan " + field.label, field.value, SENSITIVE_REVEAL_MS);
                setButtonIcon(copy, R.drawable.ic_check_24, getString(R.string.copied));
                copy.postDelayed(() -> setButtonIcon(copy, R.drawable.ic_content_copy_24, getString(R.string.copy)), 1000L);
                Snackbar.make(parent, R.string.copied_auto_clear_30_seconds, Snackbar.LENGTH_SHORT).show();
            }));
            row.addView(eye, fixedParams(52, 8));
            row.addView(copy, fixedParams(52, 8));
        }
        parent.addView(row, topParams(field.value.length() > 120 ? 104 : 52, 2));
    }

    private void scheduleSensitiveHide(Runnable hide) {
        hideSensitiveTasks.add(hide);
        sensitiveHandler.postDelayed(() -> {
            hide.run();
            hideSensitiveTasks.remove(hide);
        }, SENSITIVE_REVEAL_MS);
    }

    private void hideAllSensitiveFields() {
        for (Runnable task : new ArrayList<>(hideSensitiveTasks)) {
            task.run();
            sensitiveHandler.removeCallbacks(task);
        }
        hideSensitiveTasks.clear();
    }

    private void hideSensitiveFieldsIn(View view) {
        if (view == null) return;
        Object tag = view.getTag();
        if (tag instanceof Runnable) {
            ((Runnable) tag).run();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                hideSensitiveFieldsIn(group.getChildAt(i));
            }
        }
    }

    private boolean isSensitiveField(String type, String key, String label) {
        if (PasswordNote.TYPE_SECURE_NOTE.equals(type) || PasswordNote.TYPE_CUSTOM.equals(type)) return true;
        String normalized = (key + " " + label).toLowerCase();
        return normalized.contains("password")
                || normalized.contains("private")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("key")
                || normalized.contains("cvv")
                || normalized.contains("card_number")
                || normalized.contains("identity_number")
                || normalized.contains("license")
                || label.contains("\u5bc6\u7801")
                || label.contains("\u79c1\u94a5")
                || label.contains("\u5bc6\u94a5")
                || label.contains("\u5361\u53f7")
                || label.contains("\u8bc1\u4ef6\u53f7");
    }

    private String maskSensitiveValue(DetailField field) {
        String value = field.value == null ? "" : field.value.trim();
        if (value.isEmpty()) return "";
        String key = field.key == null ? "" : field.key;
        if ("private_key".equals(key)) return getString(R.string.private_key_saved);
        if ("card_number".equals(key)) return maskTail(value.replaceAll("\\s+", ""), "**** **** **** ");
        if ("identity_number".equals(key)) return maskIdentity(value);
        if ("license_key".equals(key)) return maskLicense(value);
        if (key.contains("password") || "CVV".equals(field.label) || value.length() <= 8) return "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
        return maskMiddle(value);
    }

    private String maskTail(String value, String prefix) {
        if (value.length() <= 4) return "\u2022\u2022\u2022\u2022";
        return prefix + value.substring(value.length() - 4);
    }

    private String maskIdentity(String value) {
        if (value.length() <= 8) return "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
        return value.substring(0, Math.min(3, value.length())) + "***********" + value.substring(value.length() - 4);
    }

    private String maskLicense(String value) {
        if (value.length() <= 4) return "XXXX";
        return "XXXX-XXXX-XXXX-" + value.substring(value.length() - 4);
    }

    private String maskMiddle(String value) {
        String singleLine = value.replace('\n', ' ').replace('\r', ' ');
        if (singleLine.length() <= 8) return "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
        return singleLine.substring(0, Math.min(4, singleLine.length())) + "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
                + singleLine.substring(singleLine.length() - 4);
    }

    private List<DynamicField> readDynamicFields(String type, JSONObject object) {
        List<DynamicField> fields = new ArrayList<>();
        JSONArray array = object == null ? null : object.optJSONArray("_dynamicFields");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                DynamicField field = new DynamicField(
                        item.optString("fieldId", UUID.randomUUID().toString()),
                        item.optString("label"),
                        item.optString("type", "text"),
                        item.optString("value"),
                        item.optBoolean("sensitive", false),
                        item.optString("maskType", "default"),
                        item.optInt("sortOrder", i),
                        item.optString("key", item.optString("label"))
                );
                if (!field.label.trim().isEmpty() && !field.value.trim().isEmpty()) fields.add(field);
            }
            return fields;
        }
        for (String label : fieldsFor(type)) {
            String key = keyFor(label);
            String value = object == null ? "" : object.optString(key);
            if (value == null || value.trim().isEmpty()) continue;
            String fieldType = fieldTypeForLegacy(type, key, label);
            fields.add(new DynamicField(UUID.randomUUID().toString(), label, fieldType, value,
                    isSensitiveField(type, key, label), maskTypeFor(fieldType), fields.size(), key));
        }
        return fields;
    }

    private JSONObject dynamicFieldsToJson(List<DynamicField> fields) {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();
        for (int i = 0; i < fields.size(); i++) {
            DynamicField field = fields.get(i);
            field.sortOrder = i;
            try {
                JSONObject item = new JSONObject();
                item.put("fieldId", firstNonEmpty(field.fieldId, UUID.randomUUID().toString()));
                item.put("label", field.label);
                item.put("type", field.type);
                item.put("value", field.value);
                item.put("sensitive", field.sensitive || isSensitiveFieldType(field.type));
                item.put("maskType", firstNonEmpty(field.maskType, maskTypeFor(field.type)));
                item.put("sortOrder", field.sortOrder);
                item.put("key", field.key);
                array.put(item);
                if (field.key != null && !field.key.trim().isEmpty()) {
                    object.put(field.key, field.value);
                }
            } catch (Exception ignored) {
            }
        }
        try {
            object.put("_dynamicFields", array);
        } catch (Exception ignored) {
        }
        return object;
    }

    private List<DynamicField> recommendedFields(String type) {
        List<DynamicField> fields = new ArrayList<>();
        if (PasswordNote.TYPE_BANK_CARD.equals(type)) {
            addRecommended(fields, R.string.note_field_card_number, "card", "card_number");
            addRecommended(fields, R.string.note_field_card_holder, "text", "card_holder");
            addRecommended(fields, R.string.note_field_expiry, "date", "expiry");
            addRecommended(fields, "CVV/CVC", "security_code", "cvv");
            addRecommended(fields, R.string.note_field_reserved_phone, "phone", "phone");
            addRecommended(fields, R.string.note_field_bank_account, "text", "bank_account");
            addRecommended(fields, R.string.note_field_online_username, "username", "online_username");
            addRecommended(fields, R.string.note_field_online_password, "password", "online_password");
            addRecommended(fields, R.string.note_field_payment_password, "password", "payment_password");
            addRecommended(fields, R.string.note_field_bank, "text", "bank");
            addRecommended(fields, R.string.note_field_notes, "multiline", "notes");
        } else if (PasswordNote.TYPE_SOFTWARE_LICENSE.equals(type)) {
            addRecommended(fields, R.string.note_field_software_name, "text", "software_name");
            addRecommended(fields, R.string.note_field_license_key, "license_key", "license_key");
            addRecommended(fields, R.string.note_field_activation_code, "license_key", "activation_code");
            addRecommended(fields, R.string.note_field_serial_number, "license_key", "serial_number");
            addRecommended(fields, R.string.note_field_registered_email, "email", "email");
            addRecommended(fields, R.string.note_field_order_number, "text", "order_number");
            addRecommended(fields, R.string.note_field_version, "text", "version");
            addRecommended(fields, R.string.note_field_expiry, "date", "expiry");
            addRecommended(fields, R.string.note_field_download_url, "url", "download_url");
            addRecommended(fields, R.string.note_field_notes, "multiline", "notes");
        } else if (PasswordNote.TYPE_SERVER.equals(type)) {
            addRecommended(fields, R.string.note_field_host, "text", "host");
            addRecommended(fields, R.string.note_field_port, "number", "port");
            addRecommended(fields, R.string.note_field_username, "username", "username");
            addRecommended(fields, R.string.note_field_password, "password", "password");
            addRecommended(fields, R.string.note_field_private_key, "private_key", "private_key");
            addRecommended(fields, R.string.note_field_public_key, "multiline", "public_key");
            addRecommended(fields, R.string.note_field_private_key_password, "password", "private_key_password");
            addRecommended(fields, R.string.note_field_fingerprint, "text", "fingerprint");
            addRecommended(fields, R.string.note_field_jump_host, "text", "jump_host");
            addRecommended(fields, R.string.note_field_notes, "multiline", "notes");
        } else if (PasswordNote.TYPE_IDENTITY.equals(type)) {
            addRecommended(fields, R.string.note_field_name, "text", "name");
            addRecommended(fields, R.string.note_field_identity_type, "text", "identity_type");
            addRecommended(fields, R.string.note_field_identity_number, "hidden", "identity_number");
            addRecommended(fields, R.string.note_field_birth_date, "date", "birth_date");
            addRecommended(fields, R.string.note_field_expiry, "date", "expiry");
            addRecommended(fields, R.string.note_field_issuer, "text", "issuer");
            addRecommended(fields, R.string.note_field_address, "multiline", "address");
            addRecommended(fields, R.string.note_field_notes, "multiline", "notes");
        } else {
            addRecommended(fields, "API Key", "api_key", "api_key");
            addRecommended(fields, "Access Token", "token", "access_token");
            addRecommended(fields, "Client Secret", "hidden", "client_secret");
            addRecommended(fields, R.string.note_field_content, "multiline", "content");
            addRecommended(fields, R.string.note_field_notes, "multiline", "notes");
        }
        return fields;
    }

    private void addRecommended(List<DynamicField> fields, String label, String type, String key) {
        fields.add(new DynamicField(UUID.randomUUID().toString(), label, type, "", isSensitiveFieldType(type), maskTypeFor(type), fields.size(), key));
    }

    private void addRecommended(List<DynamicField> fields, int labelRes, String type, String key) {
        addRecommended(fields, getString(labelRes), type, key);
    }

    private boolean containsField(List<DynamicField> fields, String key, String label) {
        for (DynamicField field : fields) {
            if (key != null && key.equals(field.key)) return true;
            if (label != null && label.equals(field.label)) return true;
        }
        return false;
    }

    private DynamicField fieldForClipboardImport(String type, ClipboardImportSession.Pending pending) {
        String fieldType = "hidden";
        String label = pending.result.displayLabel == null || pending.result.displayLabel.isEmpty()
                ? getString(R.string.imported_content)
                : pending.result.displayLabel;
        if (PasswordNote.TYPE_SOFTWARE_LICENSE.equals(type)) fieldType = "license_key";
        if (PasswordNote.TYPE_SERVER.equals(type)) fieldType = pending.text.contains("PRIVATE KEY") ? "private_key" : "password";
        return new DynamicField(UUID.randomUUID().toString(), label, fieldType, pending.text, true, maskTypeFor(fieldType), 0, "imported_" + System.currentTimeMillis());
    }

    private String fieldTypeForLegacy(String type, String key, String label) {
        if ("card_number".equals(key)) return "card";
        if ("identity_number".equals(key)) return "hidden";
        if ("license_key".equals(key)) return "license_key";
        if ("private_key".equals(key)) return "private_key";
        if ("password".equals(key) || label.contains("\u5bc6\u7801") || "CVV".equals(label)) return "password";
        if ("notes".equals(key) || "content".equals(key)) return "multiline";
        if ("username".equals(key)) return "username";
        return "text";
    }

    private boolean isSensitiveFieldType(String type) {
        return "password".equals(type)
                || "card".equals(type)
                || "security_code".equals(type)
                || "api_key".equals(type)
                || "token".equals(type)
                || "private_key".equals(type)
                || "license_key".equals(type)
                || "recovery_code".equals(type)
                || "hidden".equals(type);
    }

    private boolean isMultilineField(DynamicField field) {
        return "multiline".equals(field.type) || "private_key".equals(field.type) || (field.value != null && field.value.length() > 120);
    }

    private String maskTypeFor(String type) {
        if ("card".equals(type)) return "card";
        if ("license_key".equals(type)) return "license";
        if ("private_key".equals(type)) return "private_key";
        return "default";
    }

    private String dynamicPrimary(List<DynamicField> fields) {
        for (DynamicField field : fields) {
            if (!field.sensitive && !field.value.trim().isEmpty()) return field.value.trim();
        }
        for (DynamicField field : fields) {
            if (!field.value.trim().isEmpty()) return maskMiddle(field.value.trim());
        }
        return "";
    }

    private String dynamicSecondary(List<DynamicField> fields) {
        for (DynamicField field : fields) {
            if (!field.sensitive && !field.value.trim().isEmpty()) return field.label + ": " + field.value.trim();
        }
        return "";
    }

    private static class DynamicField {
        String fieldId;
        String label;
        String type;
        String value;
        boolean sensitive;
        String maskType;
        int sortOrder;
        String key;

        DynamicField(String fieldId, String label, String type, String value, boolean sensitive, String maskType, int sortOrder, String key) {
            this.fieldId = fieldId == null || fieldId.isEmpty() ? UUID.randomUUID().toString() : fieldId;
            this.label = label == null ? "" : label;
            this.type = type == null ? "text" : type;
            this.value = value == null ? "" : value;
            this.sensitive = sensitive || false;
            this.maskType = maskType == null ? "default" : maskType;
            this.sortOrder = sortOrder;
            this.key = key == null || key.isEmpty() ? this.label : key;
        }

        DynamicField copyEmpty() {
            return new DynamicField(UUID.randomUUID().toString(), label, type, "", sensitive || false, maskType, sortOrder, key);
        }
    }

    private static class DetailSection {
        final String title;
        final List<DetailField> fields = new ArrayList<>();

        DetailSection(String title) {
            this.title = title;
        }
    }

    private static class DetailField {
        final String label;
        final String value;
        final boolean sensitive;
        final String key;
        final String fieldType;

        DetailField(String label, String value, boolean sensitive, String key) {
            this(label, value, sensitive, key, "text");
        }

        DetailField(String label, String value, boolean sensitive, String key, String fieldType) {
            this.label = label;
            this.value = value == null ? "" : value;
            this.sensitive = sensitive;
            this.key = key;
            this.fieldType = fieldType == null ? "text" : fieldType;
        }
    }

    private void bind(String newQuery) {
        query = newQuery == null ? "" : newQuery;
        if (observed != null) observed.removeObservers(getViewLifecycleOwner());
        observed = repository.observe(query, loginOnly ? PasswordNote.TYPE_LOGIN : null);
        observed.observe(getViewLifecycleOwner(), notes -> {
            List<PasswordNote> visibleNotes = notes;
            if (!loginOnly && notes != null) {
                visibleNotes = new ArrayList<>();
                for (PasswordNote note : notes) {
                    if (!PasswordNote.TYPE_LOGIN.equals(note.type)) {
                        visibleNotes.add(note);
                    }
                }
            }
            adapter.submit(visibleNotes);
            emptyView.setVisibility(visibleNotes == null || visibleNotes.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void showTypePicker() {
        if (loginOnly) {
            showEditor(PasswordNote.TYPE_LOGIN, null);
            return;
        }
        String[] labels = {getString(R.string.legacy_note_type_secure), getString(R.string.legacy_note_type_bank), getString(R.string.legacy_note_type_license), getString(R.string.legacy_note_type_server), getString(R.string.legacy_note_type_identity), getString(R.string.legacy_note_type_custom)};
        String[] types = {PasswordNote.TYPE_SECURE_NOTE, PasswordNote.TYPE_BANK_CARD, PasswordNote.TYPE_SOFTWARE_LICENSE, PasswordNote.TYPE_SERVER, PasswordNote.TYPE_IDENTITY, PasswordNote.TYPE_CUSTOM};
        final String[] pickerTypes = types;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.legacy_note_choose_type)
                .setItems(labels, (dialog, which) -> showEditor(pickerTypes[which], null))
                .show();
    }

    private void showEditor(String type, @Nullable PasswordNote editing) {
        showEditor(type, editing, null, false);
    }

    private void showEditor(String type, @Nullable PasswordNote editing,
                            @Nullable ClipboardImportSession.Pending pendingImport,
                            boolean appendImport) {
        if (editing != null && !OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this,
                    () -> showEditor(type, editing, pendingImport, appendImport));
            return;
        }
        if (PasswordNote.TYPE_LOGIN.equals(type)) {
            showLoginEditor(editing);
            return;
        }

        List<DynamicField> fields = new ArrayList<>();
        if (editing != null) {
            try {
                fields.addAll(readDynamicFields(type, new JSONObject(editing.contentJson == null ? "{}" : editing.contentJson)));
            } catch (Exception ignored) {
            }
        }
        if (pendingImport != null) {
            DynamicField imported = fieldForClipboardImport(type, pendingImport);
            if (appendImport && !fields.isEmpty()) {
                fields.add(imported);
            } else {
                fields.add(imported);
            }
        }

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        EditText title = input(getString(R.string.title));
        title.setHint(null);
        if (editing != null) title.setText(editing.title == null ? "" : editing.title);
        addLabeledField(content, getString(R.string.title), title, 0, 52);

        LinearLayout fieldContainer = new LinearLayout(requireContext());
        fieldContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(fieldContainer);
        LinearLayout recommended = new LinearLayout(requireContext());
        recommended.setOrientation(LinearLayout.VERTICAL);
        content.addView(label(getString(R.string.dynamic_common_fields)), topParams(26, 12));
        content.addView(recommended);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            fieldContainer.removeAllViews();
            for (int i = 0; i < fields.size(); i++) {
                addDynamicFieldEditor(fieldContainer, fields, i, refresh[0]);
            }
            recommended.removeAllViews();
            addRecommendedButtons(recommended, type, fields, refresh[0]);
        };
        refresh[0].run();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(editing == null ? PasswordNoteAdapter.typeLabel(requireContext(), type) : editing.title)
                .setView(scrollView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);
        if (editing != null) builder.setNeutralButton(R.string.delete, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                PasswordNote note = editing == null ? new PasswordNote() : editing;
                note.type = type;
                note.title = title.getText().toString().trim();
                List<DynamicField> nonEmpty = new ArrayList<>();
                for (DynamicField field : fields) {
                    if (field.value != null && !field.value.trim().isEmpty()) {
                        nonEmpty.add(field);
                    }
                }
                JSONObject object = dynamicFieldsToJson(nonEmpty);
                note.contentJson = object.toString();
                note.primaryText = dynamicPrimary(nonEmpty);
                note.secondaryText = dynamicSecondary(nonEmpty);
                if (note.title.isEmpty()) note.title = note.primaryText.isEmpty() ? getString(R.string.legacy_note_unnamed) : note.primaryText;
                repository.save(note);
                if (pendingImport != null) {
                    if (ClipboardImportSettings.shouldClearAfterSave(requireContext())) {
                        SecureClipboard.clearIfMatches(requireContext(), pendingImport.text);
                    }
                    ClipboardImportSession.clearPending();
                }
                Toast.makeText(requireContext(), R.string.credential_save_success, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) neutral.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDelete(editing);
            });
        });
        dialog.show();
    }

    private void addDynamicFieldEditor(LinearLayout parent, List<DynamicField> fields, int index, Runnable refresh) {
        DynamicField field = fields.get(index);
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, 0);

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        EditText labelInput = plainInput();
        labelInput.setText(field.label);
        labelInput.setSingleLine(true);
        labelInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) field.label = labelInput.getText().toString().trim();
        });
        header.addView(labelInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button up = compactButton("\u2191");
        Button down = compactButton("\u2193");
        ImageButton delete = compactIconButton(R.drawable.ic_delete_24, getString(R.string.delete));
        up.setOnClickListener(v -> {
            if (index > 0) {
                DynamicField item = fields.remove(index);
                fields.add(index - 1, item);
                refresh.run();
            }
        });
        down.setOnClickListener(v -> {
            if (index < fields.size() - 1) {
                DynamicField item = fields.remove(index);
                fields.add(index + 1, item);
                refresh.run();
            }
        });
        delete.setOnClickListener(v -> {
            fields.remove(index);
            refresh.run();
        });
        header.addView(up, fixedParams(42, 6));
        header.addView(down, fixedParams(42, 6));
        header.addView(delete, fixedParams(52, 6));
        box.addView(header);

        EditText valueInput = plainInput();
        valueInput.setText(field.value);
        valueInput.setSingleLine(!isMultilineField(field));
        if (isMultilineField(field)) {
            valueInput.setMinLines(4);
            valueInput.setMaxLines(8);
            valueInput.setGravity(Gravity.TOP | Gravity.START);
            valueInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        valueInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                field.label = labelInput.getText().toString().trim();
                field.value = valueInput.getText().toString();
            }
        });
        valueInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { field.value = s.toString(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(isMultilineField(field) ? 120 : 52));
        valueParams.topMargin = dp(6);
        box.addView(valueInput, valueParams);
        parent.addView(box);
    }

    private void addRecommendedButtons(LinearLayout parent, String type, List<DynamicField> fields, Runnable refresh) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        for (DynamicField candidate : recommendedFields(type)) {
            if (containsField(fields, candidate.key, candidate.label)) continue;
            Button button = compactButton("+ " + candidate.label);
            button.setOnClickListener(v -> {
                fields.add(candidate.copyEmpty());
                refresh.run();
            });
            row.addView(button, topParams(42, 6));
        }
        Button custom = compactButton("+ " + getString(R.string.dynamic_add_custom_field));
        custom.setOnClickListener(v -> showCustomFieldDialog(fields, refresh));
        row.addView(custom, topParams(42, 8));
        parent.addView(row);
    }

    private void showCustomFieldDialog(List<DynamicField> fields, Runnable refresh) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText labelInput = plainInput();
        String[] labels = {getString(R.string.dynamic_type_text), getString(R.string.dynamic_type_multiline), getString(R.string.dynamic_type_username), getString(R.string.dynamic_type_password), getString(R.string.dynamic_type_card), getString(R.string.dynamic_type_security_code), getString(R.string.dynamic_type_date), getString(R.string.dynamic_type_phone), getString(R.string.dynamic_type_email), "URL", getString(R.string.dynamic_type_number), "API Key", "Token", getString(R.string.dynamic_type_private_key), getString(R.string.dynamic_type_license_key), getString(R.string.dynamic_type_recovery_code), getString(R.string.dynamic_type_hidden)};
        String[] values = {"text", "multiline", "username", "password", "card", "security_code", "date", "phone", "email", "url", "number", "api_key", "token", "private_key", "license_key", "recovery_code", "hidden"};
        android.widget.Spinner typeSpinner = new android.widget.Spinner(requireContext());
        typeSpinner.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels));
        android.widget.Switch sensitiveSwitch = new android.widget.Switch(requireContext());
        sensitiveSwitch.setText(R.string.dynamic_sensitive_field);
        content.addView(label(getString(R.string.dynamic_field_name)));
        content.addView(labelInput, topParams(52, 4));
        content.addView(label(getString(R.string.dynamic_field_type)), topParams(24, 8));
        content.addView(typeSpinner, topParams(52, 4));
        content.addView(sensitiveSwitch, topParams(42, 6));
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean forced = isSensitiveFieldType(values[position]);
                sensitiveSwitch.setChecked(forced);
                sensitiveSwitch.setEnabled(!forced);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dynamic_add_custom_field)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.dynamic_add, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String label = labelInput.getText().toString().trim();
            if (label.isEmpty()) {
                labelInput.setError(getString(R.string.dynamic_field_name_required));
                return;
            }
            int position = typeSpinner.getSelectedItemPosition();
            fields.add(new DynamicField(UUID.randomUUID().toString(), label, values[position], "", sensitiveSwitch.isChecked(), maskTypeFor(values[position]), fields.size(), label));
            dialog.dismiss();
            refresh.run();
        }));
        dialog.show();
    }

    private void maybeShowClipboardImportPrompt() {
        if (!isAdded() || loginOnly) return;
        ClipboardImportSession.Pending pending = ClipboardImportSession.current();
        if (pending == null) return;
        String message = getString(R.string.clipboard_import_preview_message,
                pending.result.displayLabel, pending.result.maskedPreview);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clipboard_smart_import_title)
                .setMessage(message)
                .setNegativeButton(R.string.clipboard_import_ignore, (dialog, which) -> ClipboardImportSession.ignorePending())
                .setPositiveButton(R.string.save, (dialog, which) -> SensitiveActionGuard.requireRecentAuth(
                        requireActivity(),
                        getString(R.string.clipboard_import_save_auth),
                        this::showClipboardImportModeDialog))
                .show();
    }

    private void showClipboardImportModeDialog() {
        ClipboardImportSession.Pending pending = ClipboardImportSession.current();
        if (pending == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.clipboard_import_save_to_notes)
                .setItems(new String[]{getString(R.string.clipboard_import_new_record), getString(R.string.clipboard_import_append_record)}, (dialog, which) -> {
                    if (which == 0) {
                        showClipboardTypePicker(pending, false);
                    } else {
                        showClipboardAddExisting(pending);
                    }
                })
                .show();
    }

    private void showClipboardTypePicker(ClipboardImportSession.Pending pending, boolean appendMode) {
        String[] labels = {getString(R.string.legacy_note_type_license), getString(R.string.clipboard_import_type_api), getString(R.string.clipboard_import_type_ssh), getString(R.string.clipboard_import_type_recovery), getString(R.string.clipboard_import_type_wifi), getString(R.string.legacy_note_type_secure), getString(R.string.legacy_note_type_custom)};
        String[] types = {PasswordNote.TYPE_SOFTWARE_LICENSE, PasswordNote.TYPE_SECURE_NOTE, PasswordNote.TYPE_SERVER,
                PasswordNote.TYPE_SECURE_NOTE, PasswordNote.TYPE_SECURE_NOTE, PasswordNote.TYPE_SECURE_NOTE, PasswordNote.TYPE_CUSTOM};
        int checked = indexForSuggestedType(types, pending.result.suggestedNoteType);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.legacy_note_choose_type)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String type = types[which];
                    if (appendMode) {
                        showClipboardExistingList(pending, type);
                    } else {
                        checkDuplicateThenOpenNew(pending, type);
                    }
                })
                .show();
    }

    private int indexForSuggestedType(String[] types, String suggestedType) {
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(suggestedType)) return i;
        }
        return 0;
    }

    private void showClipboardAddExisting(ClipboardImportSession.Pending pending) {
        showClipboardTypePicker(pending, true);
    }

    private void showClipboardExistingList(ClipboardImportSession.Pending pending, String type) {
        repository.getAllNow(notes -> FragmentUi.run(this, () -> {
            List<PasswordNote> matches = new ArrayList<>();
            for (PasswordNote note : notes) {
                if (type.equals(note.type)) matches.add(note);
            }
            if (matches.isEmpty()) {
                Toast.makeText(requireContext(), R.string.clipboard_import_no_matching_record, Toast.LENGTH_SHORT).show();
                checkDuplicateThenOpenNew(pending, type);
                return;
            }
            String[] labels = new String[matches.size()];
            for (int i = 0; i < matches.size(); i++) {
                PasswordNote note = matches.get(i);
                labels[i] = firstNonEmpty(note.title, PasswordNoteAdapter.typeLabel(requireContext(), note.type))
                        + "  " + firstNonEmpty(note.secondaryText, note.primaryText, "");
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clipboard_import_choose_existing)
                    .setItems(labels, (dialog, which) -> showEditor(type, matches.get(which), pending, true))
                    .show();
        }));
    }

    private void checkDuplicateThenOpenNew(ClipboardImportSession.Pending pending, String type) {
        repository.getAllNow(notes -> FragmentUi.run(this, () -> {
            PasswordNote duplicate = findDuplicate(notes, type, pending.text);
            if (duplicate == null) {
                showEditor(type, null, pending, false);
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.clipboard_import_duplicate_title)
                    .setMessage(R.string.clipboard_import_duplicate_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.clipboard_import_create_anyway, (dialog, which) -> showEditor(type, null, pending, false))
                    .setPositiveButton(R.string.clipboard_import_open_existing, (dialog, which) -> showEditor(duplicate.type, duplicate))
                    .show();
        }));
    }

    private PasswordNote findDuplicate(List<PasswordNote> notes, String type, String rawText) {
        if (notes == null || rawText == null) return null;
        for (PasswordNote note : notes) {
            if (note == null || !type.equals(note.type) || note.contentJson == null) continue;
            if (note.contentJson.contains(rawText)) return note;
        }
        return null;
    }

    private void applyClipboardImport(String type, EditText title, Map<String, EditText> inputs,
                                      ClipboardImportSession.Pending pending, boolean appendMode) {
        if (pending == null || pending.text == null) return;
        if (title.getText().toString().trim().isEmpty()) {
            title.setText(pending.result.displayLabel);
        }
        String preferredKey = preferredImportKey(type, pending);
        EditText preferred = findInputByKey(inputs, preferredKey);
        if (preferred == null && !inputs.isEmpty()) {
            preferred = inputs.values().iterator().next();
        }
        if (preferred == null) return;
        String value = pending.text;
        if (appendMode && preferred.getText().length() > 0) {
            value = preferred.getText().toString() + "\n\n[" + pending.result.displayLabel + "]\n" + pending.text;
        }
        preferred.setText(value);
        preferred.setSelection(preferred.getText().length());
    }

    private String preferredImportKey(String type, ClipboardImportSession.Pending pending) {
        if (PasswordNote.TYPE_SOFTWARE_LICENSE.equals(type)) return "license_key";
        if (PasswordNote.TYPE_SERVER.equals(type)) {
            if (pending.result.category == com.secureqr.scanner.clipboard.ClipboardSensitiveClassifier.Category.SSH_PRIVATE_KEY
                    || pending.result.category == com.secureqr.scanner.clipboard.ClipboardSensitiveClassifier.Category.SSH_PUBLIC_KEY) {
                return "private_key";
            }
            return "password";
        }
        if (PasswordNote.TYPE_CUSTOM.equals(type)) return "字段1";
        return "content";
    }

    private EditText findInputByKey(Map<String, EditText> inputs, String key) {
        for (Map.Entry<String, EditText> entry : inputs.entrySet()) {
            if (keyFor(entry.getKey()).equals(key)) return entry.getValue();
        }
        return null;
    }

    private void showLoginEditor(@Nullable PasswordNote editing) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        EditText titleInput = plainInput();
        EditText websiteInput = plainInput();
        EditText appInput = plainInput();
        EditText usernameInput = plainInput();
        EditText accountInput = plainInput();
        EditText passwordInput = plainInput();
        EditText remarkInput = plainInput();
        remarkInput.setSingleLine(false);
        remarkInput.setMinLines(4);
        remarkInput.setGravity(Gravity.TOP | Gravity.START);

        addLabeledField(content, getString(R.string.credential_label_title), titleInput, 0, 40);
        addLabeledField(content, getString(R.string.credential_label_website), websiteInput, 6, 40);
        addLabeledField(content, getString(R.string.credential_label_app_package), appInput, 6, 40);
        addLabeledField(content, getString(R.string.credential_label_username), usernameInput, 6, 40);
        addLabeledField(content, getString(R.string.credential_label_account), accountInput, 6, 40);

        LinearLayout passwordControls = new LinearLayout(requireContext());
        passwordControls.setOrientation(LinearLayout.HORIZONTAL);
        passwordControls.setGravity(Gravity.CENTER_VERTICAL);
        passwordControls.addView(passwordInput, new LinearLayout.LayoutParams(0, dp(40), 1));
        ImageButton eyeButton = compactIconButton(R.drawable.ic_visibility_off_24, getString(R.string.credential_show_password_desc));
        passwordControls.addView(eyeButton, fixedParams(52, 8));

        LinearLayout passwordRow = new LinearLayout(requireContext());
        passwordRow.setOrientation(LinearLayout.HORIZONTAL);
        passwordRow.setGravity(Gravity.CENTER_VERTICAL);
        passwordRow.addView(label(getString(R.string.credential_label_password)), new LinearLayout.LayoutParams(dp(76), dp(40)));
        passwordRow.addView(passwordControls, new LinearLayout.LayoutParams(0, dp(40), 1));
        content.addView(passwordRow, topParams(40, 6));

        addLabeledField(content, getString(R.string.credential_label_remark), remarkInput, 6, 120);

        if (editing != null) {
            titleInput.setText(editing.title == null ? "" : editing.title);
            try {
                JSONObject object = new JSONObject(editing.contentJson == null ? "{}" : editing.contentJson);
                websiteInput.setText(object.optString("website"));
                appInput.setText(object.optString("app"));
                usernameInput.setText(firstNonEmpty(object.optString("username"), object.optString("account")));
                accountInput.setText(object.optString("account"));
                passwordInput.setText(object.optString("password"));
                remarkInput.setText(object.optString("notes"));
            } catch (Exception ignored) {
            }
        }

        final boolean[] passwordVisible = {false};
        eyeButton.setOnClickListener(v -> {
            int startSelection = Math.max(0, passwordInput.getSelectionStart());
            int endSelection = Math.max(0, passwordInput.getSelectionEnd());
            passwordVisible[0] = !passwordVisible[0];
            passwordInput.setTransformationMethod(passwordVisible[0]
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            int length = passwordInput.getText().length();
            passwordInput.setSelection(Math.min(startSelection, length), Math.min(endSelection, length));
            setButtonIcon(eyeButton,
                    passwordVisible[0] ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24,
                    passwordVisible[0] ? getString(R.string.credential_hide_password_desc) : getString(R.string.credential_show_password_desc));
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.legacy_password_edit_title)
                .setView(scrollView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        if (editing != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.delete), (d, which) -> {
            });
        }
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                PasswordNote note = editing == null ? new PasswordNote() : editing;
                note.type = PasswordNote.TYPE_LOGIN;
                note.title = titleInput.getText().toString().trim();
                JSONObject object = new JSONObject();
                try {
                    object.put("website", websiteInput.getText().toString());
                    object.put("app", appInput.getText().toString());
                    object.put("username", usernameInput.getText().toString());
                    object.put("account", accountInput.getText().toString());
                    object.put("password", passwordInput.getText().toString());
                    object.put("notes", remarkInput.getText().toString());
                } catch (Exception ignored) {
                }
                note.contentJson = object.toString();
                note.primaryText = firstNonEmpty(object.optString("username"), object.optString("account"));
                note.secondaryText = firstNonEmpty(object.optString("website"), object.optString("app"));
                if (note.title.isEmpty()) {
                    note.title = note.primaryText.isEmpty() ? getString(R.string.legacy_note_unnamed) : note.primaryText;
                }
                repository.save(note);
                Toast.makeText(requireContext(), R.string.credential_save_success, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) neutral.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDelete(editing);
            });
        });
        dialog.show();
    }

    private void confirmDelete(@Nullable PasswordNote note) {
        if (note == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(R.string.legacy_note_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        OperationModeGuard.requireEdit(this, () -> repository.delete(note)))
                .show();
    }

    private String[] fieldsFor(String type) {
        if (PasswordNote.TYPE_SECURE_NOTE.equals(type)) return labels(R.string.note_field_content);
        if (PasswordNote.TYPE_BANK_CARD.equals(type)) return labels(R.string.note_field_card_holder, R.string.note_field_card_number,
                R.string.note_field_expiry, R.string.note_field_cvv, R.string.note_field_bank_name, R.string.note_field_notes);
        if (PasswordNote.TYPE_SOFTWARE_LICENSE.equals(type)) return labels(R.string.note_field_software_name, R.string.note_field_license_key,
                R.string.note_field_purchase_date, R.string.note_field_expiration_date, R.string.note_field_notes);
        if (PasswordNote.TYPE_SERVER.equals(type)) return labels(R.string.note_field_server_name, R.string.note_field_ip_domain,
                R.string.note_field_username, R.string.note_field_password, R.string.note_field_private_key, R.string.note_field_port, R.string.note_field_notes);
        if (PasswordNote.TYPE_IDENTITY.equals(type)) return labels(R.string.note_field_identity_type, R.string.note_field_id_number,
                R.string.note_field_name, R.string.note_field_issue_date, R.string.note_field_expiry, R.string.note_field_notes);
        if (PasswordNote.TYPE_CUSTOM.equals(type)) return labels(R.string.note_field_custom_1, R.string.note_field_custom_2,
                R.string.note_field_custom_3, R.string.note_field_notes);
        return labels(R.string.note_field_website, R.string.note_field_app_package, R.string.note_field_account,
                R.string.note_field_password, R.string.note_field_notes);
    }

    private String[] labels(int... resources) {
        String[] result = new String[resources.length];
        for (int i = 0; i < resources.length; i++) result[i] = getString(resources[i]);
        return result;
    }
    private String primaryFor(String type, JSONObject object) {
        if (PasswordNote.TYPE_LOGIN.equals(type)) return object.optString("username");
        if (PasswordNote.TYPE_BANK_CARD.equals(type)) return object.optString("card_number");
        if (PasswordNote.TYPE_SERVER.equals(type)) return object.optString("host");
        if (PasswordNote.TYPE_IDENTITY.equals(type)) return object.optString("identity_number");
        return object.optString(keyFor(fieldsFor(type)[0]));
    }

    private String secondaryFor(String type, JSONObject object) {
        if (PasswordNote.TYPE_LOGIN.equals(type)) return object.optString("website");
        if (PasswordNote.TYPE_BANK_CARD.equals(type)) return object.optString("bank");
        if (PasswordNote.TYPE_SERVER.equals(type)) return object.optString("username");
        return object.optString(keyFor(fieldsFor(type)[Math.min(1, fieldsFor(type).length - 1)]));
    }

    private String keyFor(String label) {
        if (matchesLabel(label, R.string.note_field_website, "网站")) return "website";
        if (matchesLabel(label, R.string.note_field_app_package, "App 包名")) return "app";
        if (matchesLabel(label, R.string.note_field_account, "账号")) return "account";
        if (matchesLabel(label, R.string.note_field_password, "密码")) return "password";
        if (matchesLabel(label, R.string.note_field_content, "内容")) return "content";
        if (matchesLabel(label, R.string.note_field_card_holder, "持卡人")) return "card_holder";
        if (matchesLabel(label, R.string.note_field_card_number, "卡号")) return "card_number";
        if (matchesLabel(label, R.string.note_field_bank_name, "银行名称")) return "bank";
        if (matchesLabel(label, R.string.note_field_license_key, "许可证密钥")) return "license_key";
        if (matchesLabel(label, R.string.note_field_ip_domain, "IP/域名")) return "host";
        if (matchesLabel(label, R.string.note_field_username, "用户名")) return "username";
        if (matchesLabel(label, R.string.note_field_private_key, "私钥")) return "private_key";
        if (matchesLabel(label, R.string.note_field_id_number, "证件号")) return "identity_number";
        if (matchesLabel(label, R.string.note_field_notes, "备注")) return "notes";
        if (matchesLabel(label, R.string.note_field_custom_1, "字段1")) return "字段1";
        if (matchesLabel(label, R.string.note_field_custom_2, "字段2")) return "字段2";
        if (matchesLabel(label, R.string.note_field_custom_3, "字段3")) return "字段3";
        return label.toLowerCase().replace("/", "_").replace(" ", "_");
    }

    private boolean matchesLabel(String value, int resource, String legacyChinese) {
        return getString(resource).equals(value) || legacyChinese.equals(value);
    }
    private void addLabeledField(LinearLayout parent, String labelText, EditText input, int topMarginDp, int inputHeightDp) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = label(labelText);
        row.addView(label, new LinearLayout.LayoutParams(dp(92), dp(inputHeightDp)));
        row.addView(input, new LinearLayout.LayoutParams(0, dp(inputHeightDp), 1));
        parent.addView(row, topParams(inputHeightDp, topMarginDp));
    }

    private TextView label(String text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(getResources().getColor(R.color.text_secondary));
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private EditText plainInput() {
        EditText input = new EditText(requireContext());
        input.setHint(null);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0x00000000);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private Button compactButton(String text) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private ImageButton compactIconButton(int drawableRes, String contentDescription) {
        ImageButton button = new ImageButton(requireContext());
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        setButtonIcon(button, drawableRes, contentDescription);
        return button;
    }

    private void setButtonIcon(ImageButton button, int drawableRes, String contentDescription) {
        button.setContentDescription(contentDescription);
        button.setImageResource(drawableRes);
        button.setColorFilter(ContextCompat.getColor(requireContext(), R.color.action_icon_tint));
        button.setBackgroundResource(R.drawable.bg_icon_action);
    }

    private int iconForegroundFor(int drawableRes) {
        return R.color.action_icon_tint;
    }

    private int iconBackgroundFor(int drawableRes) {
        return R.drawable.bg_icon_action;
    }

    private LinearLayout.LayoutParams topParams(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams fixedParams(int widthDp, int marginStartDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(widthDp), dp(52));
        params.leftMargin = dp(marginStartDp);
        return params;
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
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF80868B);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.bottomMargin = dp(8);
        input.setLayoutParams(params);
        return input;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
