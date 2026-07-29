package com.secureqr.scanner.ui.scanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.ui.vault.VaultDetailFragment;
import com.secureqr.scanner.ui.vault.VaultFragment;
import com.secureqr.scanner.ui.vault.VaultEditFragment;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.vault.VaultTypes;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OcrScanFragment extends Fragment {
    private static final String ARG_MODE = "mode";
    private static final String ARG_TYPE = "type";
    private String mode;
    private String type;
    private Uri pendingUri;
    private Uri frontUri;
    private Uri backUri;
    private File pendingFile;
    private File frontFile;
    private File backFile;
    private String recognizedText = "";
    private PreviewView previewView;
    private OcrScanOverlayView overlay;
    private TextView hint;
    private Button capture;
    private ImageCapture imageCapture;
    private Camera camera;
    private TextRecognizer recognizer;
    private VaultRepository vault;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private boolean viewDestroyed = true;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded() || getView() == null || viewDestroyed) return;
                if (granted) startCamera();
                else Toast.makeText(requireContext(), R.string.scanner_camera_permission_ocr_required, Toast.LENGTH_SHORT).show();
            });

    public static OcrScanFragment bankCard() {
        OcrScanFragment fragment = new OcrScanFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, "BANK");
        args.putString(ARG_TYPE, "BANK_CARD");
        fragment.setArguments(args);
        return fragment;
    }

    public static OcrScanFragment document(String type) {
        OcrScanFragment fragment = new OcrScanFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, "DOCUMENT");
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }
    public static OcrScanFragment secureFile(){OcrScanFragment fragment=document("IMPORTANT_DOCUMENT");fragment.requireArguments().putString(ARG_MODE,"FILE");return fragment;}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_ocr_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewDestroyed = false;
        mode = getArguments() == null ? "BANK" : getArguments().getString(ARG_MODE, "BANK");
        type = getArguments() == null ? "BANK_CARD" : getArguments().getString(ARG_TYPE, "BANK_CARD");
        vault = new VaultRepository(requireContext());
        cameraExecutor = Executors.newSingleThreadExecutor();
        recognizer = "BANK".equals(mode)
                ? TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                : TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        previewView = view.findViewById(R.id.preview_ocr_camera);
        overlay = view.findViewById(R.id.ocr_overlay);
        hint = view.findViewById(R.id.tv_ocr_hint);
        capture = view.findViewById(R.id.btn_ocr_capture);
        TextView title = view.findViewById(R.id.tv_ocr_title);
                title.setText("BANK".equals(mode) ? getString(R.string.scanner_bank_title) : "FILE".equals(mode)?getString(R.string.scanner_file_title):getString(VaultTypes.find(type).labelRes));
        overlay.configure(mode, type);
        hint.setText(initialHint());
        view.findViewById(R.id.btn_ocr_back).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.btn_ocr_flash).setOnClickListener(v -> toggleTorch());
        capture.setOnClickListener(v -> captureFrame());
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (!isAdded() || getView() == null || viewDestroyed || cameraExecutor == null || cameraExecutor.isShutdown()) return;
        Context context = requireContext().getApplicationContext();
        ListenableFuture<ProcessCameraProvider> future;
        try {
            future = ProcessCameraProvider.getInstance(context);
        } catch (RuntimeException error) {
            showCameraError(error);
            return;
        }
        future.addListener(() -> {
            if (!isAdded() || getView() == null || viewDestroyed || previewView == null) return;
            try {
                ProcessCameraProvider provider = future.get();
                cameraProvider = provider;
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                showCameraError(e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void captureFrame() {
        if (imageCapture == null) {
                Toast.makeText(requireContext(), R.string.scanner_camera_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(requireContext().getCacheDir(), "smart_scan");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException(getString(R.string.ocr_cache_create_failed));
            pendingFile = new File(dir, (frontUri == null ? "front_" : "back_") + System.currentTimeMillis() + ".jpg");
            pendingUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", pendingFile);
            capture.setEnabled(false);
                hint.setText(R.string.scanner_capturing);
            ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(pendingFile).build();
            imageCapture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                    FragmentUi.run(OcrScanFragment.this, () -> onCaptured());
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    FragmentUi.run(OcrScanFragment.this, () -> {
                        capture.setEnabled(true);
                        hint.setText(initialHint());
                        Toast.makeText(requireContext(), getString(R.string.scanner_capture_failed, exception.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            capture.setEnabled(true);
                Toast.makeText(requireContext(), getString(R.string.scanner_cannot_capture, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void onCaptured() {
        try {
            cropCapturedImageToScanBox(pendingFile);
        } catch (Exception error) {
            capture.setEnabled(true);
            hint.setText(initialHint());
                Toast.makeText(requireContext(), getString(R.string.scanner_crop_failed, error.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        String quality = inspectQuality(pendingFile);
        overlay.setFeedback(quality);
        if (needsBackSide() && frontUri == null) {
            frontUri = pendingUri;
            frontFile = pendingFile;
            pendingUri = null;
            pendingFile = null;
                hint.setText(R.string.scanner_id_front_done);
                capture.setText(R.string.scanner_capture_back);
            capture.setEnabled(true);
            return;
        }
        if (frontUri == null) { frontUri = pendingUri; frontFile = pendingFile; }
        else { backUri = pendingUri; backFile = pendingFile; }
        pendingUri = null;
        pendingFile = null;
                hint.setText(R.string.scanner_ocr_processing);
        recognizeAll();
    }

    private void recognizeAll() {
        List<Uri> images = new ArrayList<>();
        if (frontUri != null) images.add(frontUri);
        if (backUri != null) images.add(backUri);
        recognizeNext(images, 0, new StringBuilder());
    }

    private void recognizeNext(List<Uri> images, int index, StringBuilder all) {
        if (index >= images.size()) {
            capture.setEnabled(true);
            recognizedText = all.toString().trim();
            showConfirmation(parse(recognizedText));
            return;
        }
        try {
            InputImage image = InputImage.fromFilePath(requireContext(), images.get(index));
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        all.append(text.getText()).append('\n');
                        if ("BANK".equals(mode)) recognizeEnhancedBank(images, index, all);
                        else recognizeNext(images, index + 1, all);
                    })
                    .addOnFailureListener(e -> {
                        capture.setEnabled(true);
                Toast.makeText(requireContext(), getString(R.string.scanner_local_ocr_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            capture.setEnabled(true);
                Toast.makeText(requireContext(), R.string.scanner_photo_read_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void recognizeEnhancedBank(List<Uri> images,int index,StringBuilder all){
        try(InputStream input=requireContext().getContentResolver().openInputStream(images.get(index))){
            Bitmap source=BitmapFactory.decodeStream(input);if(source==null){recognizeNext(images,index+1,all);return;}
            Bitmap enhanced=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
            ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0f);ColorMatrix contrast=new ColorMatrix(new float[]{1.9f,0,0,0,-115,0,1.9f,0,0,-115,0,0,1.9f,0,-115,0,0,0,1,0});matrix.postConcat(contrast);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setColorFilter(new ColorMatrixColorFilter(matrix));new Canvas(enhanced).drawBitmap(source,0,0,paint);source.recycle();
            recognizer.process(InputImage.fromBitmap(enhanced,0)).addOnSuccessListener(text->{all.append(text.getText()).append('\n');enhanced.recycle();recognizeNext(images,index+1,all);}).addOnFailureListener(error->{enhanced.recycle();recognizeNext(images,index+1,all);});
        }catch(Exception error){recognizeNext(images,index+1,all);}
    }

    private Map<String, String> parse(String text) {
        return "BANK".equals(mode) ? parseBank(text) : parseDocument(text);
    }

    private Map<String, String> parseBank(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        String digits = "";
        Matcher matcher = Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,19}(?!\\d)").matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group().replaceAll("\\D", "");
            if (candidate.length() > digits.length() && (luhn(candidate) || digits.isEmpty())) digits = candidate;
        }
        values.put("bank", bankFor(digits));
        values.put("cardType", text.contains("信用") ? "信用卡" : text.contains("借记") ? "借记卡" : "");
        values.put("cardholder", "");
        values.put("cardNumber", digits);
        values.put("expiryDate", normalizeCardExpiry(match(text, "(0[1-9]|1[0-2])[/.-]?(\\d{2}|20\\d{2})")));
        values.put("cvv", "");
        values.put("pin", "");
        values.put("securityPassword", "");
        return values;
    }

    private Map<String, String> parseDocument(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("documentType", type);
        if ("NATIONAL_ID".equals(type)) {
            values.put("fullName", after(text, "姓名"));
            values.put("gender", after(text, "性别"));
            values.put("ethnicity", after(text, "民族"));
            values.put("documentNumber", match(text, "[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]"));
            values.put("birthDate", dateAfter(text, "出生"));
            values.put("address", after(text, "住址"));
            values.put("authority", after(text, "签发机关"));
            String validity = after(text, "有效期限");
            String[] dates = validity.split("[-—至]");
            if (dates.length > 0) values.put("validFrom", dates[0].trim());
            if (dates.length > 1) values.put("expiryDate", dates[dates.length - 1].trim());
        } else if ("PASSPORT".equals(type)) {
            values.put("fullName", after(text, "姓名"));
            values.put("surname", after(text, "Surname"));
            values.put("givenName", after(text, "Given"));
            values.put("nationality", after(text, "国籍"));
            values.put("passportNumber", firstNonEmpty(match(text, "[A-Z][0-9]{7,8}"), after(text, "护照号码")));
            values.put("birthDate", dateAfter(text, "出生"));
            values.put("expiryDate", dateAfter(text, "有效"));
            values.put("authority", after(text, "签发机关"));
        } else if ("DRIVER_LICENSE".equals(type)) {
            values.put("fullName", after(text, "姓名"));
            values.put("licenseNumber", firstNonEmpty(after(text, "证号"), match(text, "[1-9]\\d{16}[0-9Xx]")));
            values.put("licenseClass", after(text, "准驾车型"));
            values.put("expiryDate", dateAfter(text, "有效期限"));
            values.put("authority", after(text, "签发"));
        } else {
            values.put("documentNumber", match(text, "[A-Z0-9-]{6,20}"));
            values.put("country", "");
            values.put("expiryDate", dateAfter(text, "有效"));
        }
        return values;
    }

    private void showConfirmation(Map<String, String> recognized) {
        if ("DOCUMENT".equals(mode)) {
            chooseDocumentType(recognized);
            return;
        }
        VaultTypes.Type definition = VaultTypes.find(type);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), dp(8));
        TextView warning = new TextView(requireContext());
                warning.setText(R.string.scanner_ocr_notice);
        warning.setTextColor(0xFFB54708);
        content.addView(warning);

        ImageView photo = confirmationPhoto(frontUri);
        if (photo != null) content.addView(photo, params(180));

        EditText title = input(getString(R.string.ocr_field_name), false);
        title.setText(defaultTitle(definition, recognized));
        content.addView(title, params(52));
        Map<String, EditText> edits = new LinkedHashMap<>();
        for (VaultTypes.Field field : definition.fields) {
            EditText edit = input(getString(field.labelRes), false);
            edit.setText(recognized.getOrDefault(field.key, ""));
            if ("BANK".equals(mode) && "expiryDate".equals(field.key)) installCardExpiryFormatter(edit);
            content.addView(edit, params(52));
            edits.put(field.key, edit);
        }
        addExtraFieldIfMissing(content, edits, recognized, "cardType", getString(R.string.ocr_field_card_type), false);
        addExtraFieldIfMissing(content, edits, recognized, "cvv", getString(R.string.ocr_field_cvv), false);
        addExtraFieldIfMissing(content, edits, recognized, "pin", getString(R.string.ocr_field_pin_optional), false);
        addExtraFieldIfMissing(content, edits, recognized, "securityPassword", getString(R.string.ocr_field_security_password_optional), false);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("BANK".equals(mode) ? R.string.ocr_bank_result_title : "FILE".equals(mode) ? R.string.ocr_file_result_title : R.string.ocr_document_result_title)
                .setView(scroll)
                .setNegativeButton(R.string.scanner_action_rescan, (d, w) -> reset())
                .setPositiveButton(R.string.scanner_action_save_vault, null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) {
                title.setError(getString(R.string.scanner_name_required));
                return;
            }
            save(title.getText().toString().trim(), edits, dialog);
        }));
        dialog.show();
    }

    /**
     * OCR only extracts information from the photographed document.  The user
     * chooses the vault credential type afterwards, so a scan is never saved
     * into an assumed type by mistake.
     */
    private void chooseDocumentType(Map<String, String> recognized) {
        final String[] documentTypes = {
                "NATIONAL_ID", "PASSPORT", "DRIVER_LICENSE", "SOCIAL_SECURITY",
                "RESIDENT_REGISTRATION", "OTHER_ID"
        };
        String[] labels = new String[documentTypes.length];
        for (int i = 0; i < documentTypes.length; i++) {
            labels[i] = getString(VaultTypes.find(documentTypes[i]).labelRes);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.scanner_document_type_title)
                .setItems(labels, (dialog, which) -> openDocumentEditor(recognized, documentTypes[which]))
                .setNegativeButton(R.string.scanner_action_rescan, (dialog, which) -> reset())
                .show();
    }

    private void openDocumentEditor(Map<String, String> recognized, String documentType) {
        JSONObject fields = new JSONObject();
        try {
            for (Map.Entry<String, String> entry : recognized.entrySet()) {
                fields.put(entry.getKey(), entry.getValue());
            }
            // The selected type controls both the form and how this credential
            // is categorised in the vault; OCR's initial scan type does not.
            fields.put("documentType", documentType);
            if ("SOCIAL_SECURITY".equals(documentType) && !fields.has("number")) {
                fields.put("number", firstNonEmpty(
                        recognized.get("documentNumber"),
                        recognized.get("passportNumber"),
                        recognized.get("licenseNumber")));
            }
        } catch (Exception ignored) { }
        ArrayList<String> uris = new ArrayList<>();
        ArrayList<String> paths = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        if (frontUri != null) {
            uris.add(frontUri.toString());
            paths.add(frontFile == null ? "" : frontFile.getAbsolutePath());
            names.add(frontAttachmentName());
        }
        if (backUri != null) {
            uris.add(backUri.toString());
            paths.add(backFile == null ? "" : backFile.getAbsolutePath());
            names.add(backAttachmentName());
        }
        VaultEditFragment editor = VaultEditFragment.newScannedItem(
                documentType, defaultTitle(VaultTypes.find(documentType), recognized),
                recognizedText, fields.toString(), uris, paths, names);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, editor)
                .commit();
    }

    private void save(String title, Map<String, EditText> edits, AlertDialog dialog) {
        VaultAccessManager.requireUnlocked(requireActivity(), getString(R.string.ocr_save_bank_auth_prompt),
                () -> persistToVault(title, edits, dialog));
    }

    private void persistToVault(String title, Map<String, EditText> edits, AlertDialog dialog) {
        VaultTypes.Type definition = VaultTypes.find(type);
        VaultItem item = new VaultItem();
        item.type = VaultTypes.storageType(definition);
        item.category = definition.category;
        item.title = title;
        JSONObject fields = new JSONObject();
        try {
            if (VaultTypes.IDENTITY.equals(definition.category)) fields.put("documentType", type);
            for (Map.Entry<String, EditText> entry : edits.entrySet()) {
                fields.put(entry.getKey(), entry.getValue().getText().toString().trim());
            }
        } catch (Exception ignored) {
        }
        item.fieldsJson = fields.toString();
        vault.save(item, () -> {
            List<AttachmentInput> inputs = new ArrayList<>();
            if (frontUri != null) inputs.add(new AttachmentInput(frontUri, frontAttachmentName()));
            if (backUri != null) inputs.add(new AttachmentInput(backUri, backAttachmentName()));
            if (inputs.isEmpty()) {
                showSaved(dialog, item.id);
                return;
            }
            AtomicInteger remaining = new AtomicInteger(inputs.size());
            for (AttachmentInput input : inputs) {
                vault.addAttachment(item.id, input.uri, "image/jpeg", input.name, error -> {
                    if (remaining.decrementAndGet() == 0) showSaved(dialog, item.id);
                });
            }
        });
    }

    private void showSaved(AlertDialog dialog, String itemId) {
        FragmentUi.run(this, () -> {
            dialog.dismiss();
            String location = "BANK".equals(mode)
                    ? getString(R.string.ocr_vault_bank_location)
                    : getString(R.string.ocr_vault_identity_location, getString(VaultTypes.find(type).labelRes));
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.ocr_save_success_title)
                    .setMessage(getString(R.string.ocr_save_success_message, location))
                    .setNegativeButton(R.string.ocr_return_scanner, (d, w) -> getParentFragmentManager().popBackStack())
                    .setPositiveButton(R.string.ocr_view_record, (d, w) -> getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, VaultDetailFragment.newInstance(itemId))
                            .addToBackStack(null)
                            .commit())
                    .setNeutralButton(R.string.ocr_open_vault, (d, w) -> getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new VaultFragment())
                            .addToBackStack(null)
                            .commit())
                    .show();
        });
    }

    private void showCameraError(Throwable error) {
        FragmentUi.run(this, () -> Toast.makeText(
                requireContext(),
                getString(R.string.scanner_camera_start_failed, error == null ? "" : error.getMessage()),
                Toast.LENGTH_SHORT
        ).show());
    }

    private void reset() {
        frontUri = null;
        backUri = null;
        frontFile = null;
        backFile = null;
        pendingUri = null;
        pendingFile = null;
                capture.setText(R.string.scanner_capture);
        capture.setEnabled(true);
        hint.setText(initialHint());
                overlay.setFeedback(getString(R.string.scanner_overlay_feedback));
    }

    private boolean needsBackSide() {
        return "NATIONAL_ID".equals(type);
    }

    private String initialHint() {
        if ("BANK".equals(mode)) return getString(R.string.ocr_guide_bank);
        if ("FILE".equals(mode)) return getString(R.string.ocr_guide_file);
        if ("PASSPORT".equals(type)) return getString(R.string.ocr_guide_passport);
        if ("DRIVER_LICENSE".equals(type)) return getString(R.string.ocr_guide_driver_license);
        if ("NATIONAL_ID".equals(type)) return getString(R.string.ocr_guide_id_front);
        return getString(R.string.ocr_guide_document);
    }

    private String frontAttachmentName() {
        if ("BANK".equals(mode)) return getString(R.string.ocr_file_bank_front);
        if ("FILE".equals(mode)) return getString(R.string.ocr_file_scanned_document);
        if ("NATIONAL_ID".equals(type)) return getString(R.string.ocr_file_id_front);
        if ("PASSPORT".equals(type)) return getString(R.string.ocr_file_passport_photo);
        if ("DRIVER_LICENSE".equals(type)) return getString(R.string.ocr_file_driver_license);
        return getString(R.string.ocr_file_document);
    }

    private String backAttachmentName() {
        return getString("NATIONAL_ID".equals(type) ? R.string.ocr_file_id_back : R.string.ocr_file_document_back);
    }

    private String defaultTitle(VaultTypes.Type definition, Map<String, String> recognized) {
        if ("BANK".equals(mode)) {
            String bank = recognized.get("bank");
            return bank == null || bank.trim().isEmpty() ? getString(R.string.ocr_bank_default_name) : getString(R.string.ocr_bank_named, bank);
        }
        String name = recognized.get("fullName");
        return name == null || name.trim().isEmpty() ? getString(definition.labelRes) : getString(R.string.ocr_document_named, name, getString(definition.labelRes));
    }

    private void addExtraFieldIfMissing(LinearLayout content, Map<String, EditText> edits, Map<String, String> recognized, String key, String label, boolean secret) {
        if (!"BANK".equals(mode) || edits.containsKey(key)) return;
        EditText edit = input(label, secret);
        edit.setText(recognized.getOrDefault(key, ""));
        content.addView(edit, params(52));
        edits.put(key, edit);
    }

    private void cropCapturedImageToScanBox(File file) throws Exception {
        if (file == null || !file.exists()) throw new IllegalStateException(getString(R.string.ocr_photo_missing));
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (decoded == null) throw new IllegalStateException(getString(R.string.ocr_photo_unreadable));
        Bitmap oriented = orientBitmap(decoded, file);
        if (oriented != decoded) decoded.recycle();

        int viewWidth = previewView.getWidth();
        int viewHeight = previewView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            oriented.recycle();
            throw new IllegalStateException(getString(R.string.ocr_frame_not_ready));
        }
        RectF box = overlay.scanBoxBounds();
        float scale = Math.max(viewWidth / (float) oriented.getWidth(), viewHeight / (float) oriented.getHeight());
        float overflowX = (oriented.getWidth() * scale - viewWidth) / 2f;
        float overflowY = (oriented.getHeight() * scale - viewHeight) / 2f;
        int left = clamp(Math.round((box.left + overflowX) / scale), 0, oriented.getWidth() - 1);
        int top = clamp(Math.round((box.top + overflowY) / scale), 0, oriented.getHeight() - 1);
        int right = clamp(Math.round((box.right + overflowX) / scale), left + 1, oriented.getWidth());
        int bottom = clamp(Math.round((box.bottom + overflowY) / scale), top + 1, oriented.getHeight());
        Bitmap cropped = Bitmap.createBitmap(oriented, left, top, right - left, bottom - top);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            if (!cropped.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                throw new IllegalStateException(getString(R.string.ocr_photo_write_failed));
            }
        } finally {
            if (cropped != oriented) cropped.recycle();
            oriented.recycle();
        }
    }

    private Bitmap orientBitmap(Bitmap source, File file) throws Exception {
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Matrix matrix = new Matrix();
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) matrix.postRotate(90);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) matrix.postRotate(180);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) matrix.postRotate(270);
        else if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) matrix.postScale(-1, 1);
        else if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) matrix.postScale(1, -1);
        else if (orientation == ExifInterface.ORIENTATION_TRANSPOSE) { matrix.postScale(-1, 1); matrix.postRotate(270); }
        else if (orientation == ExifInterface.ORIENTATION_TRANSVERSE) { matrix.postScale(-1, 1); matrix.postRotate(90); }
        if (matrix.isIdentity()) return source;
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    @Nullable
    private ImageView confirmationPhoto(Uri uri) {
        if (uri == null) return null;
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) return null;
            ImageView image = new ImageView(requireContext());
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setAdjustViewBounds(false);
            image.setContentDescription(getString(R.string.ocr_bank_photo_description));
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String inspectQuality(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return getString(R.string.ocr_analyzing);
            int bright = 0;
            int dark = 0;
            long diff = 0;
            int samples = 0;
            for (int y = 1; y < bitmap.getHeight(); y += 3) {
                for (int x = 1; x < bitmap.getWidth(); x += 3) {
                    int c = bitmap.getPixel(x, y);
                    int l = (((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255)) / 3;
                    if (l > 242) bright++;
                    if (l < 24) dark++;
                    int p = bitmap.getPixel(x - 1, y - 1);
                    int lp = (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255)) / 3;
                    diff += Math.abs(l - lp);
                    samples++;
                }
            }
            bitmap.recycle();
            if (samples == 0) return getString(R.string.ocr_analyzing);
            if (bright > samples * 0.18f) return getString(R.string.ocr_glare_warning);
            if (dark > samples * 0.45f) return getString(R.string.ocr_dark_warning);
            if (diff / samples < 7) return getString(R.string.ocr_blur_warning);
            return getString(R.string.ocr_clarity_normal);
        } catch (Exception ignored) {
            return getString(R.string.ocr_analyzing);
        }
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            Integer on = camera.getCameraInfo().getTorchState().getValue();
            camera.getCameraControl().enableTorch(on == null || on == 0);
        }
    }

    private EditText input(String hint, boolean secret) {
        EditText edit = new EditText(requireContext());
        edit.setHint(hint);
        edit.setSingleLine();
        if (secret) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return edit;
    }

    private String normalizeCardExpiry(String value){String digits=value==null?"":value.replaceAll("\\D","");if(digits.length()==6&&digits.startsWith("20"))digits=digits.substring(4)+digits.substring(2,4);if(digits.length()>=4)return digits.substring(0,2)+"/"+digits.substring(digits.length()-2);return value==null?"":value;}
    private void installCardExpiryFormatter(EditText edit){edit.setHint(R.string.ocr_expiry_hint);edit.setInputType(InputType.TYPE_CLASS_NUMBER);edit.addTextChangedListener(new TextWatcher(){boolean changing;public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){}public void afterTextChanged(Editable value){if(changing)return;String digits=value.toString().replaceAll("\\D","");if(digits.length()>4)digits=digits.substring(0,4);String formatted=digits.length()>2?digits.substring(0,2)+"/"+digits.substring(2):digits;if(!formatted.equals(value.toString())){changing=true;value.replace(0,value.length(),formatted);changing=false;}}});}

    private LinearLayout.LayoutParams params(int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.topMargin = dp(8);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String after(String text, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "[：: ]*([^\\n]{1,40})", Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String dateAfter(String text, String label) {
        String near = after(text, label);
        String found = match(near, "(?:19|20)\\d{2}[./年-]\\d{1,2}[./月-]\\d{1,2}日?");
        return found.isEmpty() ? match(text, "(?:19|20)\\d{2}[./年-]\\d{1,2}[./月-]\\d{1,2}日?") : found;
    }

    private String match(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text == null ? "" : text.replace(" ", ""));
        return matcher.find() ? matcher.group() : "";
    }

    private boolean luhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (alternate) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            alternate = !alternate;
        }
        return number.length() >= 12 && sum % 10 == 0;
    }

    private String bankFor(String number) {
        if (number == null) return "";
        if (number.startsWith("6225") || number.startsWith("6226")) return "招商银行";
        if (number.startsWith("6217") || number.startsWith("6222")) return "中国工商银行";
        if (number.startsWith("6216")) return "中国银行";
        if (number.startsWith("6228")) return "中国农业银行";
        if (number.startsWith("6214")) return "中国建设银行";
        return "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    @Override
    public void onDestroyView() {
        viewDestroyed = true;
        if (cameraProvider != null) cameraProvider.unbindAll();
        cameraProvider = null;
        camera = null;
        imageCapture = null;
        previewView = null;
        overlay = null;
        hint = null;
        capture = null;
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
        cameraExecutor = null;
        if (recognizer != null) recognizer.close();
        recognizer = null;
        super.onDestroyView();
    }

    private static final class AttachmentInput {
        final Uri uri;
        final String name;

        AttachmentInput(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }
}
