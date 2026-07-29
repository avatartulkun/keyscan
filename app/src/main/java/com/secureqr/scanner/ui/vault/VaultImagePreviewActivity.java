package com.secureqr.scanner.ui.vault;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.FileProvider;

import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.BitmapDecodeHelper;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.vault.VaultWatermarkExporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public final class VaultImagePreviewActivity extends AppCompatActivity {
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_MIME = "mime";
    private File imageFile;
    private String mimeType;
    private String attachmentId;
    private File pendingExportFile;
    private final ActivityResultLauncher<String> saveDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/*"), uri -> {
                if (uri == null || pendingExportFile == null) return;
                try (FileInputStream input = new FileInputStream(pendingExportFile); OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) throw new IllegalStateException(getString(R.string.vault_target_file_failed));
                    byte[] buffer = new byte[64 * 1024]; int count;
                    while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
                    output.flush();
                    Toast.makeText(this, R.string.vault_attachment_downloaded, Toast.LENGTH_SHORT).show();
                } catch (Exception error) { Toast.makeText(this, getString(R.string.image_preview_download_failed, error.getMessage()), Toast.LENGTH_LONG).show(); }
                finally { pendingExportFile = null; }
            });

    public static void open(Context context, File file, String attachmentId, String title, String mimeType) {
        Intent intent = new Intent(context, VaultImagePreviewActivity.class);
        intent.putExtra(EXTRA_PATH, file.getAbsolutePath());
        intent.putExtra(EXTRA_ID, attachmentId);
        intent.putExtra(EXTRA_TITLE, title == null || title.trim().isEmpty() ? context.getString(R.string.image_preview_title) : title);
        intent.putExtra(EXTRA_MIME, mimeType == null || mimeType.trim().isEmpty() ? "image/*" : mimeType);
        context.startActivity(intent);
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        imageFile = new File(getIntent().getStringExtra(EXTRA_PATH));
        attachmentId = getIntent().getStringExtra(EXTRA_ID);
        mimeType = getIntent().getStringExtra(EXTRA_MIME);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07111D);

        ZoomImageView image = new ZoomImageView(this);
        image.setBackgroundColor(0xFF07111D);
        image.setScaleType(AppCompatImageView.ScaleType.MATRIX);
        Bitmap bitmap = BitmapDecodeHelper.decodeFile(imageFile.getAbsolutePath(), 4096);
        if (bitmap == null) {
            Toast.makeText(this, R.string.image_preview_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        image.setImageBitmap(bitmap);
        root.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(22), dp(8), dp(8));
        top.setBackgroundColor(0x66000000);
        ImageButton close = iconButton(R.drawable.ic_arrow_back_24, getString(R.string.image_preview_back));
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(17);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        top.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton share = iconButton(R.drawable.ic_export, getString(R.string.image_preview_share));
        share.setOnClickListener(v -> ExportSecurityGuard.require(this, getString(R.string.export_auth_prompt), this::shareImage));
        top.addView(share, new LinearLayout.LayoutParams(dp(48), dp(48)));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86), Gravity.TOP);
        root.addView(top, topParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(12), dp(8), dp(12), dp(18));
        bottom.setBackgroundColor(0xAA000000);
        bottom.addView(bottomAction(getString(R.string.image_preview_share), R.drawable.ic_export, v -> ExportSecurityGuard.require(this, getString(R.string.export_auth_prompt), this::shareImage)), new LinearLayout.LayoutParams(0, dp(54), 1));
        bottom.addView(bottomAction(getString(R.string.image_preview_export), R.drawable.ic_export, v -> ExportSecurityGuard.require(this, getString(R.string.export_auth_prompt), this::exportImage)), new LinearLayout.LayoutParams(0, dp(54), 1));
        bottom.addView(bottomAction(getString(R.string.image_preview_delete), R.drawable.ic_delete_24, v -> deleteAttachment()), new LinearLayout.LayoutParams(0, dp(54), 1));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bottom, bottomParams);
        setContentView(root);
    }

    private void shareImage() {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", imageFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType == null ? "image/*" : mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.image_preview_share_chooser)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.image_preview_share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportImage() {
        new AlertDialog.Builder(this).setTitle(R.string.image_preview_download_title)
                .setItems(new String[]{getString(R.string.image_preview_download_original), getString(R.string.image_preview_download_watermark)}, (dialog, which) -> {if(which==0)prepareExport(false,"",0,0,0);else showWatermarkSettings();}).show();
    }

    private void showWatermarkSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),0);
        EditText text=new EditText(this);text.setHint(R.string.image_preview_watermark_hint);text.setText(R.string.vault_watermark_text);box.addView(text);
        Spinner color=new Spinner(this);String[] names={getString(R.string.image_preview_color_blue),getString(R.string.image_preview_color_red),getString(R.string.image_preview_color_black),getString(R.string.image_preview_color_gray),getString(R.string.image_preview_color_green),getString(R.string.image_preview_color_white)};color.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));box.addView(color);
        TextView opacityLabel=new TextView(this);opacityLabel.setText(getString(R.string.image_preview_opacity,22));box.addView(opacityLabel);SeekBar opacity=new SeekBar(this);opacity.setMax(90);opacity.setProgress(22);box.addView(opacity);
        TextView sizeLabel=new TextView(this);sizeLabel.setText(getString(R.string.image_preview_size,100));box.addView(sizeLabel);SeekBar size=new SeekBar(this);size.setMax(150);size.setProgress(50);box.addView(size);
        opacity.setOnSeekBarChangeListener(labelListener(opacityLabel,getString(R.string.image_preview_opacity_prefix),10,"%"));size.setOnSeekBarChangeListener(labelListener(sizeLabel,getString(R.string.image_preview_size_prefix),50,"%"));
        new AlertDialog.Builder(this).setTitle(R.string.image_preview_watermark_title).setView(box).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.image_preview_generate_download,(d,w)->{int[] colors={0xFF195AB4,0xFFD32F2F,0xFF111111,0xFF777777,0xFF168A55,0xFFFFFFFF};prepareExport(true,text.getText().toString(),colors[color.getSelectedItemPosition()],opacity.getProgress()+10,size.getProgress()+50);}).show();
    }

    private SeekBar.OnSeekBarChangeListener labelListener(TextView label,String prefix,int offset,String suffix){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){label.setText(prefix+(value+offset)+suffix);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}};}

    private void prepareExport(boolean watermark,String text,int color,int opacity,int size) {
        try {
            pendingExportFile = watermark ? VaultWatermarkExporter.create(this, imageFile, mimeType, text,color,opacity,size) : imageFile;
            String name = imageFile.getName();
            if (watermark) name = name.replaceFirst("(?i)\\.[a-z0-9]+$", "") + "_watermarked.png";
            saveDocument.launch(name);
        } catch (Exception error) {
            Toast.makeText(this, getString(R.string.image_preview_watermark_generate_failed, error.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteAttachment() {
        if (attachmentId == null || attachmentId.trim().isEmpty()) {
            Toast.makeText(this, R.string.image_preview_delete_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        new VaultRepository(this).deleteAttachmentById(attachmentId, () -> runOnUiThread(() -> {
            Toast.makeText(this, R.string.image_preview_deleted, Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private LinearLayout bottomAction(String text, int icon, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(Color.WHITE);
        box.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        box.addView(label);
        box.setOnClickListener(listener);
        return box;
    }

    private ImageButton iconButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setColorFilter(Color.WHITE);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setContentDescription(description);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static final class ZoomImageView extends AppCompatImageView {
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private float scale = 1f;
        private float lastX;
        private float lastY;
        private boolean dragging;

        public ZoomImageView(Context context) {
            super(context);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    float next = Math.max(1f, Math.min(5f, scale * factor));
                    factor = next / scale;
                    scale = next;
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    setImageMatrix(matrix);
                    return true;
                }
            });
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            centerImage();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && !scaleDetector.isInProgress()) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        matrix.postTranslate(dx, dy);
                        setImageMatrix(matrix);
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default:
                    return true;
            }
        }

        private void centerImage() {
            if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
            int dw = getDrawable().getIntrinsicWidth();
            int dh = getDrawable().getIntrinsicHeight();
            float fit = Math.min(getWidth() / (float) dw, getHeight() / (float) dh);
            float dx = (getWidth() - dw * fit) / 2f;
            float dy = (getHeight() - dh * fit) / 2f;
            matrix.reset();
            matrix.postScale(fit, fit);
            matrix.postTranslate(dx, dy);
            scale = 1f;
            setImageMatrix(matrix);
        }
    }
}
