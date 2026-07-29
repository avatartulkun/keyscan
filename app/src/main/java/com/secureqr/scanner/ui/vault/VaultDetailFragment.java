package com.secureqr.scanner.ui.vault;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultAttachment;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.utils.BitmapDecodeHelper;
import com.secureqr.scanner.vault.VaultTypes;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Presentation-only detail surface. Vault entities and repository contracts remain unchanged. */
public final class VaultDetailFragment extends Fragment {
    private static final String ARG = "id";
    private static final int COLOR_BLUE = 0xFF1A73E8;
    private static final int COLOR_GREEN = 0xFF16A66A;
    private static final int COLOR_PURPLE = 0xFF7C3AED;
    private static final int COLOR_ORANGE = 0xFFF59E0B;
    private static final int COLOR_CYAN = 0xFF0891B2;
    private static final int COLOR_CARD_STROKE = 0xFFE3EAF2;
    private static final int COLOR_SOFT_SURFACE = 0xFFF7FAFF;

    private VaultRepository repo;
    private VaultItem item;
    private LinearLayout content;
    private LinearLayout attachmentList;
    private ActivityResultLauncher<String[]> picker;
    private ActivityResultLauncher<String> attachmentDownloader;
    private VaultAttachment pendingDownload;

    public static VaultDetailFragment newInstance(String id) {
        VaultDetailFragment fragment = new VaultDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG, id);
        fragment.setArguments(args);
        return fragment;
    }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || item == null) return;
            repo.addAttachment(item.id, uri, requireContext().getContentResolver().getType(uri), error ->
                    FragmentUi.run(VaultDetailFragment.this, () -> Toast.makeText(requireContext(),
                            error == null ? getString(R.string.vault_attachment_saved) : getString(R.string.vault_attachment_save_failed), Toast.LENGTH_SHORT).show()));
        });
        attachmentDownloader = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            VaultAttachment attachment = pendingDownload;
            pendingDownload = null;
            if (uri == null || attachment == null) return;
            try {
                OutputStream output = requireContext().getContentResolver().openOutputStream(uri, "w");
                if (output == null) throw new IllegalStateException(getString(R.string.vault_target_file_failed));
                repo.exportAttachment(attachment.id, output, error -> {
                    try { output.close(); } catch (Exception ignored) { }
                    FragmentUi.run(VaultDetailFragment.this, () -> Toast.makeText(requireContext(),
                            error == null ? getString(R.string.vault_attachment_downloaded) : getString(R.string.vault_attachment_download_failed, error.getMessage()), Toast.LENGTH_LONG).show());
                });
            } catch (Exception error) { Toast.makeText(requireContext(), getString(R.string.vault_attachment_download_failed, error.getMessage()), Toast.LENGTH_LONG).show(); }
        });
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                   @Nullable ViewGroup container,
                                                   @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_vault_form, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repo = new VaultRepository(requireContext());
        content = view.findViewById(R.id.form_container);
        content.setPadding(dp(16), dp(8), dp(16), dp(18));
        view.findViewById(R.id.btn_back).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.btn_more).setOnClickListener(this::showMoreMenu);

        Button primary = view.findViewById(R.id.btn_primary);
        primary.setText(R.string.vault_action_edit);
        primary.setOnClickListener(v -> OperationModeGuard.requireEdit(this, () -> {
            if (item == null) return;
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, VaultEditFragment.edit(item.id)).addToBackStack(null).commit();
        }));

        Button secondary = view.findViewById(R.id.btn_secondary);
        secondary.setText(R.string.delete);
        secondary.setOnClickListener(v ->
                OperationModeGuard.requireEdit(this, this::confirmDelete));

        repo.getById(requireArguments().getString(ARG), loaded -> FragmentUi.run(this, () -> show(loaded)));
    }

    private void showMoreMenu(View anchor) {
        if (item == null) return;
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, getString(R.string.vault_action_add_attachment));
        menu.getMenu().add(0, 2, 1, getString(R.string.vault_action_edit_record));
        menu.setOnMenuItemClickListener(this::handleMenu);
        menu.show();
    }

    private boolean handleMenu(MenuItem menuItem) {
        if (menuItem.getItemId() == 1) {
            OperationModeGuard.requireEdit(this, () ->
                    picker.launch(new String[]{"image/*", "application/pdf", "application/octet-stream", "text/*"}));
        } else if (menuItem.getItemId() == 2 && item != null) {
            OperationModeGuard.requireEdit(this, () ->
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, VaultEditFragment.edit(item.id)).addToBackStack(null).commit());
        }
        return true;
    }

    private void show(VaultItem loaded) {
        if (loaded == null) {
            getParentFragmentManager().popBackStack();
            return;
        }
        item = loaded;
        attachmentList = null;
        VaultTypes.Type type = VaultTypes.resolveStored(item.type, item.fieldsJson);
        ((TextView) requireView().findViewById(R.id.tv_title)).setText(getString(type.labelRes));

        JSONObject values;
        try { values = new JSONObject(nullToEmpty(item.fieldsJson)); } catch (Exception ignored) { values = new JSONObject(); }

        content.removeAllViews();
        addHero(type);
        LinearLayout infoCard = sectionCard(getString(R.string.vault_section_info), iconFor(type), typeColor(type));
        LinearLayout notesCard = null;
        boolean hasAttachmentField = false;
        for (VaultFormSchema.Section schemaSection : VaultFormSchema.forType(type)) {
            for (VaultFormSchema.Field field : schemaSection.fields) {
                if (field.kind == VaultFormSchema.Kind.ATTACHMENT) {
                    hasAttachmentField = true;
                    continue;
                }
                String value = "notes".equals(field.key) ? nullToEmpty(item.notes)
                        : ("title".equals(field.key) ? nullToEmpty(item.title) : values.optString(field.key));
                if (value.trim().isEmpty()) continue;
                String localizedLabel = VaultFormSchema.fieldLabel(requireContext(), field);
                if (field.kind == VaultFormSchema.Kind.SECRET) {
                    secretRow(infoCard, localizedLabel, field.key, value);
                } else if ("label".equals(field.key)) {
                    tagRow(infoCard, localizedLabel, value);
                } else if ("notes".equals(field.key)) {
                    if (notesCard == null) notesCard = sectionCard(getString(R.string.vault_section_notes), R.drawable.ic_edit_24, COLOR_ORANGE);
                    noteBlock(notesCard, value);
                } else {
                    detailRow(infoCard, localizedLabel, value, field.kind == VaultFormSchema.Kind.MULTILINE);
                }
            }
        }
        if (infoCard.getChildCount() > 1) content.addView(infoCard, cardParams());
        if (hasAttachmentField || attachmentList == null) addAttachmentSection(null);
        if (notesCard != null && notesCard.getChildCount() > 1) content.addView(notesCard, cardParams());
        addSecurityFooter();
        repo.observeAttachments(item.id).observe(getViewLifecycleOwner(), this::showAttachments);
    }

    private void addHero(VaultTypes.Type type) {
        LinearLayout hero = new LinearLayout(requireContext());
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(15), dp(14), dp(15));
        hero.setBackground(roundRect(ContextCompat.getColor(requireContext(), R.color.card_background), dp(12),
                ContextCompat.getColor(requireContext(), R.color.card_stroke), 1));

        int color = typeColor(type);
        ImageView icon = iconView(iconFor(type), color, dp(54), dp(12));
        hero.addView(icon);

        LinearLayout text = new LinearLayout(requireContext());
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, dp(8), 0);

        TextView title = new TextView(requireContext());
        title.setText(nullToEmpty(item.title));
        title.setTextColor(requireContext().getColor(R.color.text_main));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(getString(R.string.vault_created_subtitle, getString(type.labelRes), formatTime(item.createdTime)));
        subtitle.setTextColor(requireContext().getColor(R.color.text_secondary));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(5), 0, 0);

        text.addView(title);
        text.addView(subtitle);
        hero.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton edit = action(R.drawable.ic_edit_24, getString(R.string.vault_action_edit_record));
        edit.setOnClickListener(v -> OperationModeGuard.requireEdit(this, () ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, VaultEditFragment.edit(item.id)).addToBackStack(null).commit()));
        hero.addView(edit);
        content.addView(hero, cardParams());
    }

    private LinearLayout sectionCard(String title, int iconRes, int color) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(8));
        card.setBackground(roundRect(ContextCompat.getColor(requireContext(), R.color.card_background), dp(12),
                ContextCompat.getColor(requireContext(), R.color.card_stroke), 1));

        LinearLayout heading = new LinearLayout(requireContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(0, 0, 0, dp(9));
        heading.addView(iconView(iconRes, color, dp(36), dp(8)));

        TextView label = new TextView(requireContext());
        label.setText(title);
        label.setTextColor(requireContext().getColor(R.color.text_main));
        label.setTextSize(18);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(dp(10), 0, 0, 0);
        heading.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(heading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return card;
    }

    private void detailRow(LinearLayout parent, String labelText, String valueText, boolean multiline) {
        LinearLayout row = baseRow();
        TextView label = rowLabel(labelText);
        row.addView(label, new LinearLayout.LayoutParams(dp(108), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView value = rowValue(valueText);
        boolean expandable = multiline || valueText.length() > 32 || valueText.contains("\n");
        value.setMaxLines(expandable ? 1 : 2);
        value.setEllipsize(expandable ? TextUtils.TruncateAt.END : null);
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (expandable) {
            TextView expand = textAction(getString(R.string.vault_expand));
            expand.setOnClickListener(v -> {
                boolean collapsed = value.getMaxLines() == 1;
                value.setMaxLines(collapsed ? Integer.MAX_VALUE : 1);
                value.setEllipsize(collapsed ? null : TextUtils.TruncateAt.END);
                expand.setText(collapsed ? R.string.vault_collapse : R.string.vault_expand);
            });
            row.addView(expand);
        }

        ImageButton copy = action(R.drawable.ic_content_copy_24, getString(R.string.vault_copy_content));
        copy.setOnClickListener(v -> copyToClipboard(valueText, false));
        row.addView(copy);
        parent.addView(row);
        parent.addView(separator());
    }

    private void tagRow(LinearLayout parent, String labelText, String valueText) {
        LinearLayout row = baseRow();
        row.setGravity(Gravity.TOP);
        row.addView(rowLabel(labelText), new LinearLayout.LayoutParams(dp(108), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout chips = new LinearLayout(requireContext());
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        String[] parts = valueText.split("[,，;；\\s]+");
        int added = 0;
        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            TextView chip = new TextView(requireContext());
            chip.setText(part.trim());
            chip.setTextSize(14);
            chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            chip.setTextColor(added % 2 == 0 ? COLOR_BLUE : COLOR_GREEN);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setBackground(roundRect(added % 2 == 0 ? 0xFFEAF2FF : 0xFFEAF8F0, dp(8), Color.TRANSPARENT, 0));
            String tag = part.trim();
            chip.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, VaultFragment.search(tag))
                    .addToBackStack(null)
                    .commit());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            chips.addView(chip, params);
            added++;
        }
        if (added == 0) chips.addView(rowValue(valueText));
        row.addView(chips, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton copy = action(R.drawable.ic_content_copy_24, getString(R.string.vault_copy_label));
        copy.setOnClickListener(v -> copyToClipboard(valueText, false));
        row.addView(copy);
        parent.addView(row);
        parent.addView(separator());
    }

    private void secretRow(LinearLayout parent, String labelText, String key, String raw) {
        LinearLayout row = baseRow();
        row.addView(rowLabel(labelText), new LinearLayout.LayoutParams(dp(108), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView value = rowValue(mask(key, raw));
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton eye = action(R.drawable.ic_visibility_off_24, getString(R.string.vault_view_sensitive));
        eye.setOnClickListener(v -> {
            if (raw.contentEquals(value.getText())) {
                value.setText(mask(key, raw));
                eye.setImageResource(R.drawable.ic_visibility_off_24);
                return;
            }
            SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.vault_view_sensitive_auth), () -> {
                value.setText(raw);
                eye.setImageResource(R.drawable.ic_visibility_24);
            });
        });
        row.addView(eye);

        ImageButton copy = action(R.drawable.ic_content_copy_24, getString(R.string.vault_copy_sensitive));
        copy.setOnClickListener(v -> SensitiveActionGuard.requireRecentAuth(requireActivity(),
                getString(R.string.vault_copy_sensitive_auth), () -> copyToClipboard(raw, true)));
        row.addView(copy);
        parent.addView(row);
        parent.addView(separator());
    }

    private void noteBlock(LinearLayout parent, String valueText) {
        LinearLayout block = new LinearLayout(requireContext());
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setGravity(Gravity.TOP);
        block.setPadding(dp(12), dp(10), dp(8), dp(10));
        block.setBackground(roundRect(0xFFFFFBF0, dp(10), 0xFFF6C36A, 1));

        TextView value = rowValue(valueText);
        value.setTextSize(15);
        value.setLineSpacing(dp(2), 1f);
        value.setMaxLines(4);
        value.setEllipsize(TextUtils.TruncateAt.END);
        block.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.VERTICAL);
        TextView expand = textAction(getString(R.string.vault_expand));
        expand.setOnClickListener(v -> {
            boolean collapsed = value.getMaxLines() == 4;
            value.setMaxLines(collapsed ? Integer.MAX_VALUE : 4);
            value.setEllipsize(collapsed ? null : TextUtils.TruncateAt.END);
            expand.setText(collapsed ? R.string.vault_collapse : R.string.vault_expand);
        });
        ImageButton copy = action(R.drawable.ic_content_copy_24, getString(R.string.vault_copy_notes));
        copy.setOnClickListener(v -> copyToClipboard(valueText, false));
        actions.addView(expand);
        actions.addView(copy);
        block.addView(actions);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        params.bottomMargin = dp(8);
        parent.addView(block, params);
    }

    private void addAttachmentSection(@Nullable LinearLayout existingCard) {
        if (attachmentList != null) return;
        LinearLayout card = existingCard == null
                ? sectionCard(getString(R.string.vault_section_attachments), R.drawable.ic_vault_attachment, COLOR_CYAN)
                : existingCard;

        TextView hint = new TextView(requireContext());
        hint.setText(R.string.vault_attachment_encrypted_hint);
        hint.setTextColor(requireContext().getColor(R.color.text_secondary));
        hint.setTextSize(13);
        hint.setPadding(dp(2), 0, dp(2), dp(10));
        card.addView(hint);

        attachmentList = new LinearLayout(requireContext());
        attachmentList.setOrientation(LinearLayout.VERTICAL);
        card.addView(attachmentList);

        Button add = new Button(requireContext());
        add.setText(R.string.vault_action_add_attachment);
        add.setTextColor(COLOR_BLUE);
        add.setAllCaps(false);
        add.setBackground(roundRect(0xFFEAF2FF, dp(9), 0xFFD5E5FF, 1));
        add.setOnClickListener(v -> picker.launch(new String[]{"image/*", "application/pdf", "application/octet-stream", "text/*"}));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        addParams.topMargin = dp(4);
        card.addView(add, addParams);
        content.addView(card, cardParams());
    }

    private void showAttachments(List<VaultAttachment> attachments) {
        if (attachmentList == null) return;
        attachmentList.removeAllViews();
        if (attachments.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.vault_attachment_empty);
            empty.setTextColor(requireContext().getColor(R.color.text_hint));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            empty.setBackground(roundRect(ContextCompat.getColor(requireContext(), R.color.surface_light), dp(10),
                    ContextCompat.getColor(requireContext(), R.color.card_stroke), 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            attachmentList.addView(empty, params);
            return;
        }
        for (VaultAttachment attachment : attachments) {
            LinearLayout file = new LinearLayout(requireContext());
            file.setGravity(Gravity.CENTER_VERTICAL);
            file.setPadding(dp(11), dp(10), dp(8), dp(10));
            file.setBackground(roundRect(ContextCompat.getColor(requireContext(), R.color.surface_light), dp(10),
                    ContextCompat.getColor(requireContext(), R.color.card_stroke), 1));
            LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fileParams.bottomMargin = dp(8);

            boolean image = attachment.mimeType != null && attachment.mimeType.startsWith("image/");
            ImageView preview = iconView(image ? R.drawable.ic_vault_photo : R.drawable.ic_vault_attachment,
                    image ? COLOR_GREEN : COLOR_CYAN, dp(42), dp(9));
            if (image && com.secureqr.scanner.security.VaultSession.isUnlocked(requireContext())) {
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                repo.decrypt(attachment, result -> FragmentUi.run(VaultDetailFragment.this, () -> {
                    if (result.error == null && result.file != null) {
                        Bitmap bitmap = BitmapDecodeHelper.decodeFile(result.file.getAbsolutePath(), 720);
                        if (bitmap != null) {
                            preview.setColorFilter(null);
                            preview.setPadding(0, 0, 0, 0);
                            preview.setImageBitmap(bitmap);
                        }
                    }
                }));
            }
            file.addView(preview);

            LinearLayout labels = new LinearLayout(requireContext());
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(11), 0, 0, 0);
            TextView name = rowValue(attachment.filename);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            TextView meta = new TextView(requireContext());
            meta.setText(fileType(attachment.mimeType) + " · " + sizeText(attachment.size));
            meta.setTextColor(requireContext().getColor(R.color.text_secondary));
            meta.setTextSize(12);
            meta.setPadding(0, dp(3), 0, 0);
            labels.addView(name);
            labels.addView(meta);
            file.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            ImageButton open = action(R.drawable.ic_export, getString(R.string.vault_attachment_open_export));
            file.addView(open);
            View.OnClickListener listener = v -> com.secureqr.scanner.security.ExportSecurityGuard.require(requireActivity(),
                    getString(R.string.vault_attachment_open_auth), () -> open(attachment));
            file.setOnClickListener(listener);
            open.setOnClickListener(listener);
            file.setOnLongClickListener(v -> {
                new AlertDialog.Builder(requireContext()).setMessage(getString(R.string.vault_attachment_delete_message, attachment.filename))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (d, w) -> repo.deleteAttachment(attachment))
                        .show();
                return true;
            });
            attachmentList.addView(file, fileParams);
        }
    }

    private void addSecurityFooter() {
        LinearLayout footer = new LinearLayout(requireContext());
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(12), dp(10), dp(12), dp(10));
        footer.setBackground(roundRect(0xFFEFF6FF, dp(10), 0xFFBBD7FF, 1));
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_shield);
        icon.setColorFilter(COLOR_BLUE);
        footer.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView text = new TextView(requireContext());
        text.setText(R.string.vault_local_encrypted_notice);
        text.setTextColor(COLOR_BLUE);
        text.setTextSize(14);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setPadding(dp(8), 0, 0, 0);
        footer.addView(text);
        LinearLayout.LayoutParams params = cardParams();
        params.topMargin = dp(2);
        content.addView(footer, params);
    }

    private void open(VaultAttachment attachment) {
        repo.decrypt(attachment, result -> FragmentUi.run(this, () -> {
            if (result.error != null) {
                Toast.makeText(requireContext(), R.string.vault_attachment_open_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            if (attachment.mimeType != null && attachment.mimeType.startsWith("image/")) {
                VaultImagePreviewActivity.open(requireContext(), result.file, attachment.id, attachment.filename, attachment.mimeType);
            } else new AlertDialog.Builder(requireContext()).setTitle(attachment.filename)
                    .setItems(new String[]{getString(R.string.vault_attachment_open), getString(R.string.vault_attachment_download)}, (dialog, which) -> {
                        if (which == 0) openAttachmentFile(result.file, attachment.mimeType);
                        else downloadAttachment(attachment);
                    }).show();
        }));
    }

    private void downloadAttachment(VaultAttachment attachment) {
        pendingDownload = attachment;
        attachmentDownloader.launch(attachment.filename == null || attachment.filename.trim().isEmpty() ? "KeyScan_attachment" : attachment.filename);
    }

    private void openAttachmentFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.vault_attachment_view_export_chooser)));
    }

    private File watermark(File source, String name) {
        try {
            Bitmap bitmap = BitmapDecodeHelper.decodeFile(source.getAbsolutePath(), 2048);
            if (bitmap == null) return source;
            Bitmap out = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xAAFFFFFF);
            paint.setTextSize(Math.max(28, out.getWidth() / 18f));
            paint.setShadowLayer(4, 1, 1, 0x99000000);
            canvas.drawText(getString(R.string.vault_watermark_text), out.getWidth() * 0.06f, out.getHeight() * 0.90f, paint);
            File dir = new File(requireContext().getCacheDir(), "vault_exports");
            if (!dir.exists()) dir.mkdirs();
            File target = new File(dir, "watermarked_" + (name == null ? "attachment.jpg" : name.replace("/", "_").replace("\\", "_")));
            try (FileOutputStream stream = new FileOutputStream(target)) { out.compress(Bitmap.CompressFormat.JPEG, 92, stream); }
            if (out != bitmap) out.recycle();
            bitmap.recycle();
            return target;
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.vault_watermark_failed, Toast.LENGTH_SHORT).show();
            return source;
        }
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        row.setPadding(0, dp(7), 0, dp(7));
        return row;
    }

    private TextView rowLabel(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextColor(requireContext().getColor(R.color.text_secondary));
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(40));
        return view;
    }

    private TextView rowValue(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextColor(requireContext().getColor(R.color.text_main));
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(40));
        return view;
    }

    private TextView textAction(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextColor(COLOR_BLUE);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(6), 0);
        view.setMinWidth(dp(48));
        view.setMinHeight(dp(40));
        return view;
    }

    private View separator() {
        View view = new View(requireContext());
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_stroke));
        view.setAlpha(0.9f);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return view;
    }

    private ImageButton action(int icon, String description) {
        ImageButton button = new ImageButton(requireContext());
        button.setImageResource(icon);
        button.setContentDescription(description);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(COLOR_BLUE);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return button;
    }

    private ImageView iconView(int iconRes, int color, int size, int padding) {
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        icon.setBackground(roundRect(color, dp(9), Color.TRANSPARENT, 0));
        icon.setPadding(padding, padding, padding, padding);
        icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return icon;
    }

    private GradientDrawable roundRect(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private void copyToClipboard(String value, boolean sensitive) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("KeyScan", value);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), sensitive ? R.string.vault_copied_sensitive : R.string.copied, Toast.LENGTH_SHORT).show();
        if (!sensitive) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ClipData current = clipboard.getPrimaryClip();
            if (current == null || current.getItemCount() == 0) return;
            CharSequence text = current.getItemAt(0).coerceToText(requireContext());
            if (value.contentEquals(text)) clipboard.setPrimaryClip(ClipData.newPlainText("KeyScan", ""));
        }, 30_000);
    }

    private String mask(String key, String value) {
        String compact = value.replace(" ", "");
        if ("cardNumber".equals(key) && compact.length() >= 8)
            return compact.substring(0, 4) + " **** **** " + compact.substring(compact.length() - 4);
        if ("cvv".equals(key)) return "***";
        if ("pin".equals(key)) return "****";
        int count = Math.max(10, Math.min(22, value.length()));
        StringBuilder masked = new StringBuilder();
        while (count-- > 0) masked.append('•');
        return masked.toString();
    }

    private int sectionColor(VaultFormSchema.Section section) {
        if (section.icon == R.drawable.ic_key_line || section.icon == R.drawable.ic_shield) return COLOR_PURPLE;
        if (section.icon == R.drawable.ic_link) return COLOR_GREEN;
        if (section.icon == R.drawable.ic_edit_24) return COLOR_ORANGE;
        if (section.icon == R.drawable.ic_vault_attachment || section.icon == R.drawable.ic_vault_photo) return COLOR_CYAN;
        return COLOR_BLUE;
    }

    private int typeColor(VaultTypes.Type type) {
        if (VaultTypes.FINANCIAL.equals(type.category)) return COLOR_GREEN;
        if (VaultTypes.IDENTITY.equals(type.category)) return COLOR_PURPLE;
        if (VaultTypes.FILES.equals(type.category)) return COLOR_CYAN;
        if (VaultTypes.CONTACT.equals(type.category)) return COLOR_ORANGE;
        return COLOR_BLUE;
    }

    private int iconFor(VaultTypes.Type type) {
        if ("BANK_CARD".equals(type.key)) return R.drawable.ic_vault_card;
        if ("PASSPORT".equals(type.key)) return R.drawable.ic_vault_passport;
        if ("DRIVER_LICENSE".equals(type.key)) return R.drawable.ic_vault_car;
        if ("API_KEY".equals(type.key)) return R.drawable.ic_key_line;
        if (VaultTypes.IDENTITY.equals(type.category)) return R.drawable.ic_vault_identity;
        if (VaultTypes.FINANCIAL.equals(type.category)) return R.drawable.ic_vault_wallet;
        if (VaultTypes.CONTACT.equals(type.category)) return R.drawable.ic_vault_contact;
        if (VaultTypes.FILES.equals(type.category)) return R.drawable.ic_vault_attachment;
        return R.drawable.ic_vault_gear;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        return params;
    }

    private String formatTime(long time) {
        return time <= 0 ? getString(R.string.vault_unknown_time) : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
    }

    private String fileType(String mime) {
        if (mime == null || mime.isEmpty()) return getString(R.string.vault_file_type_file);
        if (mime.startsWith("image/")) return getString(R.string.vault_file_type_image);
        if (mime.contains("pdf")) return getString(R.string.vault_file_type_pdf);
        if (mime.startsWith("text/")) return getString(R.string.vault_file_type_text);
        return getString(R.string.vault_file_type_encrypted);
    }

    private String sizeText(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, bytes / 1024) + " KB";
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1024f / 1024f);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void confirmDelete() {
        if (item == null) return;
        new AlertDialog.Builder(requireContext()).setMessage(getString(R.string.vault_delete_record_message, item.title))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (d, w) -> {
                    repo.delete(item);
                    getParentFragmentManager().popBackStack();
                }).show();
    }
}
