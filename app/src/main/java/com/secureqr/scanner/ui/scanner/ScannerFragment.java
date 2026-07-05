package com.secureqr.scanner.ui.scanner;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.ui.home.HomeFragment;
import com.secureqr.scanner.ui.password.PasswordForgeFragment;
import com.secureqr.scanner.ui.otp.OtpAuthFragment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExperimentalGetImage
public class ScannerFragment extends Fragment {
    private static final String ARG_PASSWORD_CAPTURE = "password_capture";
    private static final String ARG_OTP_CAPTURE = "otp_capture";
    private PreviewView previewView;
    private ToggleButton toggleContinuous;
    private RecordRepository repository;
    private ExecutorService analysisExecutor;
    private BarcodeScanner scanner;
    private Camera camera;
    private long lastScanAt;
    private String lastValue = "";
    private boolean pausedAfterSingleScan;
    private LinearLayout resultPanel;
    private TextView resultText;
    private Button resultOpenUrl;
    private Button importImageButton;
    private TextView scanHint;
    private String currentResult = "";
    private ScanRecord currentRecord;

    public static ScannerFragment forPasswordCapture() {
        ScannerFragment fragment = new ScannerFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_PASSWORD_CAPTURE, true);
        fragment.setArguments(args);
        return fragment;
    }

    public static ScannerFragment forOtpCapture() {
        ScannerFragment fragment = new ScannerFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_OTP_CAPTURE, true);
        fragment.setArguments(args);
        return fragment;
    }

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(requireContext(), "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String> galleryPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) scanImageFromGallery(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        previewView = view.findViewById(R.id.previewView);
        toggleContinuous = view.findViewById(R.id.toggle_continuous);
        Button home = view.findViewById(R.id.btn_scanner_home);
        ImageButton flash = view.findViewById(R.id.btn_flash);
        ImageButton gallery = view.findViewById(R.id.btn_gallery);
        importImageButton = view.findViewById(R.id.btn_import_image);
        scanHint = view.findViewById(R.id.tv_scan_hint);
        resultPanel = view.findViewById(R.id.layout_scan_result);
        resultText = view.findViewById(R.id.tv_scan_result);
        Button resultCopy = view.findViewById(R.id.btn_result_copy);
        resultOpenUrl = view.findViewById(R.id.btn_result_open_url);
        Button resultNote = view.findViewById(R.id.btn_result_note);
        Button resultContinue = view.findViewById(R.id.btn_result_continue);
        repository = RecordRepository.getInstance(requireContext());
        analysisExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build());

        toggleContinuous.setChecked(false);
        updateContinuousButtonTint(false);
        toggleContinuous.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pausedAfterSingleScan = false;
            lastValue = "";
            updateContinuousButtonTint(isChecked);
            Toast.makeText(requireContext(), isChecked ? "已开启连续扫描" : "已切换为单次扫描", Toast.LENGTH_SHORT).show();
        });
        home.setOnClickListener(v -> openHome());
        flash.setOnClickListener(v -> toggleTorch());
        gallery.setOnClickListener(v -> galleryPicker.launch("image/*"));
        importImageButton.setOnClickListener(v -> galleryPicker.launch("image/*"));
        resultCopy.setOnClickListener(v -> copyResult());
        resultOpenUrl.setOnClickListener(v -> openResultUrl());
        resultNote.setOnClickListener(v -> showNoteDialog());
        resultContinue.setOnClickListener(v -> resumeSingleScan());

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void openHome() {
        getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
    }

    @ExperimentalGetImage
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(requireContext());
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyzeImage);

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "相机启动失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @ExperimentalGetImage
    private void analyzeImage(ImageProxy imageProxy) {
        if (pausedAfterSingleScan || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String raw = barcode.getRawValue();
                        if (raw != null && !raw.isEmpty()) {
                            onScanSuccess(raw);
                            break;
                        }
                    }
                })
                .addOnFailureListener(e -> { })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onScanSuccess(String raw) {
        long now = SystemClock.elapsedRealtime();
        if (raw.equals(lastValue) && now - lastScanAt < 1800) return;
        lastValue = raw;
        lastScanAt = now;
        vibrate();
        if (isPasswordCaptureMode()) {
            requireActivity().runOnUiThread(() -> {
                Bundle result = new Bundle();
                result.putString(PasswordForgeFragment.PASSWORD_SCAN_VALUE, raw);
                getParentFragmentManager().setFragmentResult(PasswordForgeFragment.PASSWORD_SCAN_REQUEST, result);
                getParentFragmentManager().popBackStack();
            });
            return;
        }
        if (isOtpCaptureMode()) {
            requireActivity().runOnUiThread(() -> {
                Bundle result = new Bundle();
                result.putString(OtpAuthFragment.OTP_SCAN_VALUE, raw);
                getParentFragmentManager().setFragmentResult(OtpAuthFragment.OTP_SCAN_REQUEST, result);
                getParentFragmentManager().popBackStack();
            });
            return;
        }
        if (toggleContinuous.isChecked()) {
            repository.insert(ScanRecord.fromContent(raw));
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "已保存扫描结果", Toast.LENGTH_SHORT).show());
            return;
        }
        pausedAfterSingleScan = true;
        ScanRecord record = ScanRecord.fromContent(raw);
        repository.insert(record, saved -> {
            currentRecord = saved;
            requireActivity().runOnUiThread(() -> showScanResultPanel(raw));
        });
    }

    private boolean isPasswordCaptureMode() {
        return getArguments() != null && getArguments().getBoolean(ARG_PASSWORD_CAPTURE, false);
    }

    private boolean isOtpCaptureMode() {
        return getArguments() != null && getArguments().getBoolean(ARG_OTP_CAPTURE, false);
    }

    private void showScanResultPanel(String raw) {
        currentResult = raw;
        boolean isUrl = ScanRecord.detectType(raw).equals("URL");
        resultText.setText(raw);
        resultOpenUrl.setVisibility(isUrl ? View.VISIBLE : View.GONE);
        importImageButton.setVisibility(View.GONE);
        scanHint.setVisibility(View.GONE);
        resultPanel.setVisibility(View.VISIBLE);
    }

    private void copyResult() {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("KeyScan", currentResult));
        }
        Toast.makeText(requireContext(), "已复制", Toast.LENGTH_SHORT).show();
    }

    private void openResultUrl() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentResult)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "没有可用浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void resumeSingleScan() {
        pausedAfterSingleScan = false;
        lastValue = "";
        currentResult = "";
        currentRecord = null;
        if (resultPanel != null) resultPanel.setVisibility(View.GONE);
        if (importImageButton != null) importImageButton.setVisibility(View.VISIBLE);
        if (scanHint != null) scanHint.setVisibility(View.VISIBLE);
    }

    private void showNoteDialog() {
        if (currentRecord == null) {
            Toast.makeText(requireContext(), "当前记录尚未保存完成", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(requireContext());
        input.setHint("例如：会议室 Wi-Fi、GitHub 备用账号");
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF636366);
        input.setText(currentRecord.title == null ? "" : currentRecord.title);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("填入备注")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            currentRecord.title = input.getText().toString().trim();
            repository.update(currentRecord);
            Toast.makeText(requireContext(), "备注已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void updateContinuousButtonTint(boolean enabled) {
        int color = enabled ? Color.parseColor("#2196F3") : Color.parseColor("#66000000");
        toggleContinuous.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void scanImageFromGallery(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(requireContext(), uri);
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String raw = barcode.getRawValue();
                            if (raw != null && !raw.isEmpty()) {
                                onScanSuccess(raw);
                                Toast.makeText(requireContext(), "已识别并保存", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        Toast.makeText(requireContext(), "未识别到二维码或条形码", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(requireContext(), "识别失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "读取图片失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(80);
        }
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            Integer on = camera.getCameraInfo().getTorchState().getValue();
            camera.getCameraControl().enableTorch(on == null || on == 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (analysisExecutor != null) analysisExecutor.shutdown();
        if (scanner != null) scanner.close();
    }
}

