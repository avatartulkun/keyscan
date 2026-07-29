package com.secureqr.scanner.ui.scanner;

import android.Manifest;
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
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Base64;
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
import androidx.lifecycle.Lifecycle;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.secureqr.scanner.R;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.OperationModeManager;
import com.secureqr.scanner.clipboard.SecureClipboard;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.ui.password.PasswordForgeFragment;
import com.secureqr.scanner.ui.otp.OtpAuthFragment;
import com.secureqr.scanner.ui.share.SecureShareProtocol;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.utils.NavigationHelper;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

@ExperimentalGetImage
public class ScannerFragment extends Fragment {
    private static final String ARG_PASSWORD_CAPTURE = "password_capture";
    private static final String ARG_OTP_CAPTURE = "otp_capture";
    private static final String ARG_LAN_TRANSFER_CAPTURE = "lan_transfer_capture";
    private static final String ARG_LAN_FILE_TRANSFER_CAPTURE = "lan_file_transfer_capture";
    private static final String ARG_WEBDAV_CONFIG_CAPTURE = "webdav_config_capture";
    private static final String ARG_SECURE_SHARE_CAPTURE = "secure_share_capture";
    public static final String LAN_TRANSFER_SCAN_REQUEST = "keyscan_lan_transfer_scan_request";
    public static final String LAN_TRANSFER_SCAN_VALUE = "keyscan_lan_transfer_scan_value";
    public static final String LAN_FILE_TRANSFER_SCAN_REQUEST = "keyscan_lan_file_transfer_scan_request";
    public static final String LAN_FILE_TRANSFER_SCAN_VALUE = "keyscan_lan_file_transfer_scan_value";
    public static final String WEBDAV_CONFIG_SCAN_REQUEST="keyscan_webdav_config_scan_request",WEBDAV_CONFIG_SCAN_VALUE="keyscan_webdav_config_scan_value";
    public static final String SECURE_SHARE_SCAN_REQUEST="keyscan_secure_share_scan_request",SECURE_SHARE_SCAN_VALUE="keyscan_secure_share_scan_value";
    private PreviewView previewView;
    private ToggleButton toggleContinuous;
    private RecordRepository repository;
    private ExecutorService analysisExecutor;
    private BarcodeScanner scanner;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private boolean viewDestroyed = true;
    private long lastScanAt;
    private String lastValue = "";
    private boolean pausedAfterSingleScan;
    private LinearLayout resultPanel;
    private TextView resultText;
    private TextView resultTitle;
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

    public static ScannerFragment forLanTransferCapture() {
        ScannerFragment fragment = new ScannerFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_LAN_TRANSFER_CAPTURE, true);
        fragment.setArguments(args);
        return fragment;
    }

    public static ScannerFragment forLanFileTransferCapture() {
        ScannerFragment fragment = new ScannerFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_LAN_FILE_TRANSFER_CAPTURE, true);
        fragment.setArguments(args);
        return fragment;
    }
    public static ScannerFragment forWebDavConfigCapture(){ScannerFragment fragment=new ScannerFragment();Bundle args=new Bundle();args.putBoolean(ARG_WEBDAV_CONFIG_CAPTURE,true);fragment.setArguments(args);return fragment;}
    public static ScannerFragment forSecureShareCapture(){ScannerFragment fragment=new ScannerFragment();Bundle args=new Bundle();args.putBoolean(ARG_SECURE_SHARE_CAPTURE,true);fragment.setArguments(args);return fragment;}

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded() || getView() == null || viewDestroyed) return;
                if (granted) startCamera();
                else Toast.makeText(requireContext(), R.string.scanner_camera_permission_required, Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String> galleryPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && isAdded() && getView() != null && !viewDestroyed) scanImageFromGallery(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewDestroyed = false;
        previewView = view.findViewById(R.id.previewView);
        toggleContinuous = view.findViewById(R.id.toggle_continuous);
        View home = view.findViewById(R.id.btn_scanner_home);
        ImageButton flash = view.findViewById(R.id.btn_flash);
        ImageButton gallery = view.findViewById(R.id.btn_gallery);
        importImageButton = view.findViewById(R.id.btn_import_image);
        scanHint = view.findViewById(R.id.tv_scan_hint);
        resultPanel = view.findViewById(R.id.layout_scan_result);
        resultText = view.findViewById(R.id.tv_scan_result);
        resultTitle = view.findViewById(R.id.tv_scan_result_title);
        Button resultCopy = view.findViewById(R.id.btn_result_copy);
        resultOpenUrl = view.findViewById(R.id.btn_result_open_url);
        Button resultNote = view.findViewById(R.id.btn_result_note);
        Button resultContinue = view.findViewById(R.id.btn_result_continue);
        repository = RecordRepository.getInstance(requireContext());
        analysisExecutor = Executors.newSingleThreadExecutor();
        try {
            scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build());
        } catch (RuntimeException e) {
            showCameraError(e);
            return;
        }

        toggleContinuous.setChecked(false);
        updateContinuousButtonTint(false);
        toggleContinuous.setOnCheckedChangeListener((buttonView, isChecked) -> {
            pausedAfterSingleScan = false;
            lastValue = "";
            updateContinuousButtonTint(isChecked);
            Toast.makeText(requireContext(), isChecked ? R.string.scanner_continuous_enabled : R.string.scanner_single_enabled, Toast.LENGTH_SHORT).show();
        });
        home.setOnClickListener(v -> NavigationHelper.openHome(this));
        flash.setOnClickListener(v -> toggleTorch());
        gallery.setOnClickListener(v -> galleryPicker.launch("image/*"));
        importImageButton.setOnClickListener(v -> galleryPicker.launch("image/*"));
        resultCopy.setOnClickListener(v -> copyResult());
        resultOpenUrl.setOnClickListener(v -> openResultUrl());
        resultNote.setOnClickListener(v -> shareResult());
        resultContinue.setOnClickListener(v -> resumeSingleScan());

        if (!isInternalCaptureMode()) toggleContinuous.setVisibility(View.GONE);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    @ExperimentalGetImage
    private void startCamera() {
        if (!isAdded() || getView() == null || viewDestroyed || previewView == null
                || analysisExecutor == null || analysisExecutor.isShutdown()) return;
        final Context context = requireContext().getApplicationContext();
        ListenableFuture<ProcessCameraProvider> providerFuture;
        try {
            providerFuture = ProcessCameraProvider.getInstance(context);
        } catch (RuntimeException e) {
            showCameraError(e);
            return;
        }
        providerFuture.addListener(() -> {
            try {
                if (!isAdded() || getView() == null || viewDestroyed || previewView == null
                        || analysisExecutor == null || analysisExecutor.isShutdown()
                        || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
                ProcessCameraProvider provider = providerFuture.get();
                cameraProvider = provider;
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeImage);

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                showCameraError(e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    @ExperimentalGetImage
    private void analyzeImage(ImageProxy imageProxy) {
        try {
            if (viewDestroyed || scanner == null || pausedAfterSingleScan || imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (viewDestroyed || !isAdded() || getView() == null) return;
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
        } catch (RuntimeException e) {
            imageProxy.close();
        }
    }

    private void onScanSuccess(String raw) {
        if (viewDestroyed || !isAdded() || getView() == null) return;
        long now = SystemClock.elapsedRealtime();
        if (raw.equals(lastValue) && now - lastScanAt < 1800) return;
        lastValue = raw;
        lastScanAt = now;
        vibrate();
        if (!isInternalCaptureMode() && SecureShareProtocol.isDirect(raw)) {
            importDirectSecureShare(raw);
            return;
        }
        if (isPasswordCaptureMode()) {
            FragmentUi.run(this, () -> {
                Bundle result = new Bundle();
                result.putString(PasswordForgeFragment.PASSWORD_SCAN_VALUE, raw);
                getParentFragmentManager().setFragmentResult(PasswordForgeFragment.PASSWORD_SCAN_REQUEST, result);
                getParentFragmentManager().popBackStack();
            });
            return;
        }
        if (isOtpCaptureMode()) {
            FragmentUi.run(this, () -> {
                Bundle result = new Bundle();
                result.putString(OtpAuthFragment.OTP_SCAN_VALUE, raw);
                getParentFragmentManager().setFragmentResult(OtpAuthFragment.OTP_SCAN_REQUEST, result);
                getParentFragmentManager().popBackStack();
            });
            return;
        }
        if (isLanTransferCaptureMode()) {
            FragmentUi.run(this, () -> {
                Bundle result = new Bundle();
                result.putString(LAN_TRANSFER_SCAN_VALUE, raw);
                getParentFragmentManager().setFragmentResult(LAN_TRANSFER_SCAN_REQUEST, result);
                getParentFragmentManager().popBackStack();
            });
            return;
        }
        if (isLanFileTransferCaptureMode()) {
            FragmentUi.run(this, () -> {
                Bundle result = new Bundle();
                result.putString(LAN_FILE_TRANSFER_SCAN_VALUE, raw);
                getParentFragmentManager().setFragmentResult(LAN_FILE_TRANSFER_SCAN_REQUEST, result);
                getParentFragmentManager().popBackStack();
            });
            return;
        }
        if(isWebDavConfigCaptureMode()){FragmentUi.run(this,()->confirmWebDavConfigSave(raw));return;}
        if(isSecureShareCaptureMode()){
            FragmentUi.run(this,()->{
                Bundle result=new Bundle();
                result.putString(SECURE_SHARE_SCAN_VALUE,raw);
                getParentFragmentManager().setFragmentResult(SECURE_SHARE_SCAN_REQUEST,result);
                getParentFragmentManager().popBackStack();
            });
            return;
        }
        pausedAfterSingleScan = true;
        FragmentUi.run(this, () -> showScanResultPanel(raw));
    }

    private boolean isPasswordCaptureMode() {
        return getArguments() != null && getArguments().getBoolean(ARG_PASSWORD_CAPTURE, false);
    }

    private void importDirectSecureShare(String raw) {
        pausedAfterSingleScan = true;
        try {
            JSONObject payload = SecureShareProtocol.decryptDirect(raw);
            String shareId = payload.optString("_shareId");
            android.content.SharedPreferences used = requireContext().getSharedPreferences(
                    "secure_share_received", Context.MODE_PRIVATE);
            if (shareId.isEmpty() || used.getBoolean(shareId, false)) {
                Toast.makeText(requireContext(), R.string.secure_share_already_imported, Toast.LENGTH_LONG).show();
                resumeSingleScan();
                return;
            }
            long now = System.currentTimeMillis();
            PasswordEntry entry = new PasswordEntry();
            entry.title = payload.optString("title");
            entry.remark = entry.title;
            entry.websiteDomain = payload.optString("website");
            entry.username = payload.optString("username");
            entry.account = entry.username;
            entry.password = payload.optString("password");
            entry.createdAt = now;
            entry.updatedAt = now;
            long sharedAt = payload.optLong("sharedAt", now);
            entry.notes = getString(R.string.secure_share_source_note,
                    java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                            .format(new java.util.Date(sharedAt)));
            PasswordRepository.getInstance(requireContext()).insertSecureShare(entry, () ->
                    FragmentUi.run(this, () -> {
                        used.edit().putBoolean(shareId, true).apply();
                        Toast.makeText(requireContext(), R.string.secure_share_imported, Toast.LENGTH_LONG).show();
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new PasswordForgeFragment())
                                .commit();
                    }));
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.secure_share_decrypt_failed, Toast.LENGTH_LONG).show();
            resumeSingleScan();
        }
    }

    private boolean isOtpCaptureMode() {
        return getArguments() != null && getArguments().getBoolean(ARG_OTP_CAPTURE, false);
    }

    private boolean isLanTransferCaptureMode() {
        return getArguments() != null && getArguments().getBoolean(ARG_LAN_TRANSFER_CAPTURE, false);
    }

    private boolean isLanFileTransferCaptureMode() {
        return getArguments() != null && getArguments().getBoolean(ARG_LAN_FILE_TRANSFER_CAPTURE, false);
    }
    private boolean isWebDavConfigCaptureMode(){return getArguments()!=null&&getArguments().getBoolean(ARG_WEBDAV_CONFIG_CAPTURE,false);}
    private boolean isSecureShareCaptureMode(){return getArguments()!=null&&getArguments().getBoolean(ARG_SECURE_SHARE_CAPTURE,false);}

    /** A scanned connection QR must be explicitly assigned before it changes either WebDAV target. */
    private void confirmWebDavConfigSave(String raw){
        pausedAfterSingleScan=true;
        String target=getString(R.string.backup_target_main);
        try{
            if(raw==null||!raw.startsWith("keyscan://webdav-config?data="))throw new IllegalArgumentException();
            String encoded=raw.substring(raw.indexOf("data=")+5);
            JSONObject data=new JSONObject(new String(Base64.decode(encoded,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING), StandardCharsets.UTF_8));
            if("backup".equals(data.optString("target")))target=getString(R.string.backup_target_backup);
        }catch(Exception ignored){
            Toast.makeText(requireContext(),R.string.webdav_qr_invalid,Toast.LENGTH_SHORT).show();
            resumeSingleScan();
            return;
        }
        final String selectedTarget=target;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_qr_save_confirm_title)
                .setMessage(getString(R.string.webdav_qr_save_confirm_message,selectedTarget))
                .setNegativeButton(R.string.common_action_cancel,(dialog,which)->resumeSingleScan())
                .setPositiveButton(R.string.webdav_qr_save_confirm_action,(dialog,which)->{
                    Bundle result=new Bundle();
                    result.putString(WEBDAV_CONFIG_SCAN_VALUE,raw);
                    getParentFragmentManager().setFragmentResult(WEBDAV_CONFIG_SCAN_REQUEST,result);
                    getParentFragmentManager().popBackStack();
                })
                .setOnCancelListener(dialog->resumeSingleScan())
                .show();
    }

    private boolean isInternalCaptureMode() {
        return isPasswordCaptureMode() || isOtpCaptureMode() || isLanTransferCaptureMode() || isLanFileTransferCaptureMode()||isWebDavConfigCaptureMode()||isSecureShareCaptureMode();
    }

    private void showScanResultPanel(String raw) {
        currentResult = raw;
        String type = qrContentType(raw);
        resultTitle.setText("URL".equals(type) ? R.string.scanner_result_url : "WIFI".equals(type) ? R.string.scanner_result_wifi : "CONTACT".equals(type) ? R.string.scanner_result_contact : R.string.scanner_result_text);
        resultText.setText(formatQrResult(raw, type));
        resultOpenUrl.setVisibility("TEXT".equals(type) ? View.GONE : View.VISIBLE);
        resultOpenUrl.setText("URL".equals(type) ? R.string.scanner_action_open : "WIFI".equals(type) ? R.string.scanner_action_connect_wifi : R.string.scanner_action_save_contact);
        importImageButton.setVisibility(View.GONE);
        scanHint.setVisibility(View.GONE);
        resultPanel.setVisibility(View.VISIBLE);
    }

    private void copyResult() {
        SecureClipboard.copySensitive(requireContext(), "KeyScan", currentResult);
        Toast.makeText(requireContext(), R.string.scanner_copied, Toast.LENGTH_SHORT).show();
    }

    private void openResultUrl() {
        try {
            String type = qrContentType(currentResult);
            if ("URL".equals(type)) {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(currentResult)), getString(R.string.scanner_open_chooser)));
            } else if ("WIFI".equals(type)) {
                startActivity(new Intent(android.os.Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS));
            } else if ("CONTACT".equals(type)) {
                Map<String, String> contact = parseContact(currentResult);
                Intent insert = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                insert.putExtra(ContactsContract.Intents.Insert.NAME, contact.get("name"));
                insert.putExtra(ContactsContract.Intents.Insert.PHONE, contact.get("phone"));
                insert.putExtra(ContactsContract.Intents.Insert.EMAIL, contact.get("email"));
                startActivity(insert);
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.scanner_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareResult() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, currentResult);
        startActivity(Intent.createChooser(share, getString(R.string.scanner_share_result)));
    }

    private String qrContentType(String raw) {
        String lower = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return "URL";
        if (lower.startsWith("wifi:")) return "WIFI";
        if (lower.startsWith("begin:vcard") || lower.startsWith("mecard:")) return "CONTACT";
        return "TEXT";
    }

    private String formatQrResult(String raw, String type) {
        if ("WIFI".equals(type)) { Map<String,String> w=parseWifi(raw); return getString(R.string.scanner_wifi_result,w.get("ssid"),w.get("password")); }
        if ("CONTACT".equals(type)) { Map<String,String> c=parseContact(raw); return getString(R.string.scanner_contact_result,c.get("name"),c.get("phone"),c.get("email")); }
        return raw;
    }

    private Map<String,String> parseWifi(String raw) { Map<String,String> out=new java.util.LinkedHashMap<>();out.put("ssid",qrField(raw,"S"));out.put("password",qrField(raw,"P"));return out; }
    private String qrField(String raw,String key) { java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:^|;)"+key+":((?:\\\\.|[^;])*)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw==null?"":raw);return m.find()?m.group(1).replace("\\;",";").replace("\\:",":"):""; }
    private Map<String,String> parseContact(String raw) { Map<String,String> out=new java.util.LinkedHashMap<>();out.put("name",firstLine(raw,"FN",firstLine(raw,"N",qrField(raw,"N"))));out.put("phone",firstLine(raw,"TEL",qrField(raw,"TEL")));out.put("email",firstLine(raw,"EMAIL",qrField(raw,"EMAIL")));return out; }
    private String firstLine(String raw,String key,String fallback) { java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?im)^"+key+"(?:;[^:]*)?:([^\\r\\n]+)").matcher(raw==null?"":raw);return m.find()?m.group(1).trim():fallback; }

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
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, this::showNoteDialog);
            return;
        }
        if (currentRecord == null) {
            Toast.makeText(requireContext(), R.string.scanner_record_pending, Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(requireContext());
        input.setHint(R.string.scanner_note_hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF636366);
        input.setText(currentRecord.title == null ? "" : currentRecord.title);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.scanner_note_title)
                .setView(input)
                .setNegativeButton(R.string.common_action_cancel, null)
                .setPositiveButton(R.string.common_action_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            currentRecord.title = input.getText().toString().trim();
            repository.update(currentRecord);
            Toast.makeText(requireContext(), R.string.scanner_note_saved, Toast.LENGTH_SHORT).show();
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
            if (scanner == null || viewDestroyed || !isAdded()) return;
            InputImage image = InputImage.fromFilePath(requireContext(), uri);
            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (viewDestroyed || !isAdded() || getView() == null) return;
                        for (Barcode barcode : barcodes) {
                            String raw = barcode.getRawValue();
                            if (raw != null && !raw.isEmpty()) {
                                onScanSuccess(raw);
                                Toast.makeText(requireContext(), R.string.scanner_local_result_confirm, Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        Toast.makeText(requireContext(), R.string.scanner_code_not_found, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded() && !viewDestroyed) {
                            Toast.makeText(requireContext(), getString(R.string.scanner_recognition_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            if (isAdded() && !viewDestroyed) {
                Toast.makeText(requireContext(), getString(R.string.scanner_image_read_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showCameraError(Throwable error) {
        if (!isAdded() || viewDestroyed) return;
        String detail = error == null || error.getMessage() == null
                ? error == null ? "" : error.getClass().getSimpleName()
                : error.getMessage();
        FragmentUi.run(this, () -> {
            if (!isAdded() || viewDestroyed) return;
            Toast.makeText(requireContext(), getString(R.string.scanner_camera_start_failed, detail), Toast.LENGTH_LONG).show();
        });
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
        viewDestroyed = true;
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        if (cameraProvider != null) cameraProvider.unbindAll();
        camera = null;
        cameraProvider = null;
        imageAnalysis = null;
        previewView = null;
        if (analysisExecutor != null) analysisExecutor.shutdownNow();
        analysisExecutor = null;
        if (scanner != null) scanner.close();
        scanner = null;
        super.onDestroyView();
    }
}

