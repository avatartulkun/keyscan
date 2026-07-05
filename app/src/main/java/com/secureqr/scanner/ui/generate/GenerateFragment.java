package com.secureqr.scanner.ui.generate;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.utils.QRGenerator;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public class GenerateFragment extends BottomSheetDialogFragment {
    private EditText input;
    private EditText noteInput;
    private ImageView preview;
    private TextView placeholder;
    private Bitmap currentBitmap;
    private Button styleButton;
    private String qrStyle = "classic";
    private int foregroundColor = Color.BLACK;
    private int backgroundColor = Color.WHITE;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        input = view.findViewById(R.id.et_input);
        noteInput = view.findViewById(R.id.et_qr_note);
        preview = view.findViewById(R.id.iv_qr_preview);
        placeholder = view.findViewById(R.id.tv_qr_placeholder);
        styleButton = view.findViewById(R.id.btn_qr_style);

        view.findViewById(R.id.btn_generate_qr).setOnClickListener(v -> updateQR());
        view.findViewById(R.id.btn_save_png).setOnClickListener(v -> savePng());
        view.findViewById(R.id.btn_save_record).setOnClickListener(v -> saveRecord());
        styleButton.setOnClickListener(v -> showStyleDialog());
        view.findViewById(R.id.btn_qr_foreground).setOnClickListener(v -> showColorDialog(true));
        view.findViewById(R.id.btn_qr_background).setOnClickListener(v -> showColorDialog(false));
    }

    private void updateQR() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            preview.setImageBitmap(null);
            placeholder.setVisibility(View.VISIBLE);
            currentBitmap = null;
            Toast.makeText(requireContext(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }
        currentBitmap = QRGenerator.generateStyledQR(text, 400, qrStyle, foregroundColor, backgroundColor);
        preview.setImageBitmap(currentBitmap);
        placeholder.setVisibility(currentBitmap == null ? View.VISIBLE : View.GONE);
    }

    private void savePng() {
        if (currentBitmap == null) {
            Toast.makeText(requireContext(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "secureqr_" + System.currentTimeMillis() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KeyScan");
            Uri uri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException(getString(R.string.image_create_failed));
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                Bitmap exportBitmap = buildExportBitmap();
                exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                if (exportBitmap != currentBitmap) {
                    exportBitmap.recycle();
                }
            }
            saveGeneratedHistory();
            Toast.makeText(requireContext(), R.string.saved_to_album, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveRecord() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentBitmap == null) {
            currentBitmap = QRGenerator.generateStyledQR(text, 400, qrStyle, foregroundColor, backgroundColor);
            preview.setImageBitmap(currentBitmap);
        }
        saveGeneratedHistory();
        Toast.makeText(requireContext(), R.string.saved_to_record, Toast.LENGTH_SHORT).show();
    }

    private void saveGeneratedHistory() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        ScanRecord record = ScanRecord.fromGeneratedContent(text, getNote(), thumbnailBase64(currentBitmap));
        String note = getNote();
        if (!note.isEmpty()) {
            record.title = note;
        }
        RecordRepository.getInstance(requireContext()).insert(record);
    }

    private String thumbnailBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        Bitmap thumb = Bitmap.createScaledBitmap(bitmap, 96, 96, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        thumb.compress(Bitmap.CompressFormat.PNG, 80, output);
        if (thumb != bitmap) thumb.recycle();
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private void showStyleDialog() {
        String[] labels = {
                getString(R.string.qr_style_classic),
                getString(R.string.qr_style_blue_purple),
                getString(R.string.qr_style_orange_yellow),
                getString(R.string.qr_style_dots),
                getString(R.string.qr_style_rounded),
                getString(R.string.qr_style_logo)
        };
        String[] codes = {"classic", "blue_purple", "orange_yellow", "dots", "rounded", "logo"};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.qr_style_title)
                .setItems(labels, (dialog, which) -> {
                    qrStyle = codes[which];
                    styleButton.setText(getString(R.string.qr_style_prefix, labels[which]));
                    if (currentBitmap != null) updateQR();
                })
                .show();
    }

    private void showColorDialog(boolean foreground) {
        String[] names = {
                getString(R.string.color_black),
                getString(R.string.color_warm_orange),
                getString(R.string.color_blue),
                getString(R.string.color_purple),
                getString(R.string.color_green),
                getString(R.string.color_white)
        };
        int[] colors = {Color.BLACK, Color.rgb(255, 140, 0), Color.rgb(21, 101, 192), Color.rgb(142, 36, 170), Color.rgb(0, 168, 120), Color.WHITE};
        new AlertDialog.Builder(requireContext())
                .setTitle(foreground ? R.string.foreground_color : R.string.background_color)
                .setItems(names, (dialog, which) -> {
                    if (foreground) foregroundColor = colors[which];
                    else backgroundColor = colors[which];
                    if (currentBitmap != null) updateQR();
                })
                .show();
    }

    private Bitmap buildExportBitmap() {
        String note = getNote();
        if (note.isEmpty()) return currentBitmap;

        int padding = 32;
        int width = currentBitmap.getWidth() + padding * 2;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(32, 33, 36));
        paint.setTextSize(34f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);

        String[] lines = wrapText(note, paint, width - padding * 2);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        int lineHeight = (int) Math.ceil(metrics.descent - metrics.ascent) + 8;
        int noteHeight = lineHeight * lines.length + padding;
        int height = currentBitmap.getHeight() + padding * 2 + noteHeight;

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(currentBitmap, padding, padding, null);

        float y = padding + currentBitmap.getHeight() + padding - metrics.ascent;
        for (String line : lines) {
            canvas.drawText(line, width / 2f, y, paint);
            y += lineHeight;
        }
        return output;
    }

    private String[] wrapText(String text, Paint paint, int maxWidth) {
        if (paint.measureText(text) <= maxWidth) return new String[]{text};
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = line.toString() + text.charAt(i);
            if (paint.measureText(next) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(text.charAt(i));
            if (lines.size() == 2 && i < text.length() - 1) {
                line.append("...");
                break;
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    private String getNote() {
        return noteInput == null ? "" : noteInput.getText().toString().trim();
    }
}

