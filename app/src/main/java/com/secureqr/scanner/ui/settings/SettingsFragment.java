package com.secureqr.scanner.ui.settings;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.backup.BackupCoordinator;
import com.secureqr.scanner.backup.AttachmentBackupCoordinator;
import com.secureqr.scanner.backup.BackupPackageReader;
import com.secureqr.scanner.backup.BackupPayload;
import com.secureqr.scanner.backup.BackupRestoreManager;
import com.secureqr.scanner.backup.BackupRestoreResult;
import com.secureqr.scanner.backup.source.LocalBackupStreamSource;
import com.secureqr.scanner.backup.source.webdav.WebDavBackupStreamSource;
import com.secureqr.scanner.security.ConfigurationRebuildGuard;
import com.secureqr.scanner.security.RecentAuthSession;
import com.secureqr.scanner.security.SecurityAuditLog;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.security.SecureSecretStore;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.OperationModeManager;
import com.secureqr.scanner.security.SensitiveWindowGuard;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.data.database.AppDatabase;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.model.ScanRecord;
import com.secureqr.scanner.data.repository.OtpRepository;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.data.repository.RecordRepository;
import com.secureqr.scanner.network.NetworkAccessController;
import com.secureqr.scanner.MainActivity;
import com.secureqr.scanner.ui.scanner.ScannerFragment;
import com.secureqr.scanner.utils.CryptoHelper;
import com.secureqr.scanner.utils.LanBackupTransferServer;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.utils.PinLockHelper;
import com.secureqr.scanner.utils.QRGenerator;
import com.secureqr.scanner.utils.WebDAVClient;
import com.secureqr.scanner.utils.WebDavAutoSyncManager;
import com.secureqr.scanner.utils.LocalAutoBackupManager;
import com.secureqr.scanner.utils.FragmentUi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    private static final String PREFS = "secureqr_settings";
    private static final String LATEST_BACKUP = "/secure_backup.dat";
    private static final String KEY_BACKUP_PASSWORD = "webdav_backup_password";
    private static final String KEY_BACKUP_INDEPENDENT = "webdav_backup_password_independent";
    private static final String KEY_RECOVERY_KEY = "webdav_recovery_key";
    private static final String KEY_HISTORY_MAIN_URL = "webdav_history_main_url";
    private static final String KEY_HISTORY_MAIN_USER = "webdav_history_main_user";
    private static final String KEY_HISTORY_BACKUP_URL = "webdav_history_backup_url";
    private static final String KEY_HISTORY_BACKUP_USER = "webdav_history_backup_user";
    private static final String KEY_BACKUP_METHOD_SET = "webdav_backup_method_set";
    private static final int SECTION_NONE = 0;
    private static final int SECTION_WEBDAV = 1;
    private static final int SECTION_NETWORK_ACCESS = 2;
    private static final int SECTION_LAN_SHARE = 3;
    private static final int SECTION_SYNC_SETTINGS = 4;
    private static final int SECTION_BACKUP_DATA = 5;
    private static final int SECTION_LOCAL_BACKUP = 6;
    private static final int SECTION_BACKUP_ENCRYPTION = 7;
    private static final long AUTO_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final long LAN_TRANSFER_TTL_MS = 2 * 60 * 1000L;

    private EditText webdavUrl;
    private EditText webdavUser;
    private EditText webdavPass;
    private EditText backupEncryptionPassword;
    private EditText backupWebdavUrl;
    private EditText backupWebdavUser;
    private EditText backupWebdavPass;
    private CheckBox autoSync;
    private CheckBox localAutoBackup;
    private EditText backupFilePrefix;
    private RadioButton backupLedgerPassword;
    private RadioButton backupCustomPassword;
    private android.widget.RadioGroup backupEncryptionModeGroup;
    private Button convertLegacyButton;
    private Spinner algorithmSpinner;
    private Spinner mainSyncContent;
    private Spinner backupSyncContent;
    private android.widget.RadioGroup syncTargetGroup;
    private TextView lastSync;
    private TextView backupHistory;
    private TextView localBackupStatus;
    private TextView localBackupLocation;
    private TextView localBackupLatest;
    private LinearLayout cloudBackups;
    private LinearLayout webdavConfigContent;
    private LinearLayout backupEncryptionContent;
    private LinearLayout networkAccessContent;
    private LinearLayout lanShareContent;
    private LinearLayout syncSettingsContent;
    private LinearLayout backupDataContent;
    private LinearLayout localBackupContent;
    private LinearLayout localBackupHistoryContent;
    private TextView localBackupHistorySummary;
    private TextView localBackupHistoryArrow;
    private TextView webdavConfigArrow;
    private TextView backupEncryptionArrow;
    private TextView networkAccessArrow;
    private TextView lanShareArrow;
    private TextView syncSettingsArrow;
    private TextView backupDataArrow;
    private TextView localBackupArrow;
    private TextView webdavConfigStatus;
    private TextView backupEncryptionStatus;
    private TextView networkAccessStatus;
    private TextView lanShareStatus;
    private TextView syncSettingsStatus;
    private TextView backupDataStatus;
    private RecordRepository repository;
    private PasswordRepository passwordRepository;
    private OtpRepository otpRepository;
    private BackupCoordinator backupCoordinator;
    private BackupPackageReader backupPackageReader;
    private BackupRestoreManager backupRestoreManager;
    private ExecutorService executor;
    private final Handler secretHandler = new Handler(Looper.getMainLooper());
    private Runnable hideWebDavSecretsRunnable;
    private boolean mainWebDavTested;
    private boolean backupWebDavTested;
    private String mainWebDavTestFingerprint = "";
    private String backupWebDavTestFingerprint = "";
    private final List<SecureSecretStore.MigrationResult> secretMigrationResults = new ArrayList<>();
    private final Set<String> selectedBackupPaths = new HashSet<>();
    private final Set<String> expandedBackupDates = new HashSet<>();
    private final List<WebDAVClient.BackupFile> currentCloudBackupFiles = new ArrayList<>();
    private final Set<String> expandedLocalBackupItems = new HashSet<>();
    private String localBackupTreeUri = "";
    private String localBackupLatestLabel = "";
    private boolean localBackupPendingAfterPick;
    private boolean localAutoBackupPendingAfterPick;
    private boolean bindingLocalAutoBackup;
    private boolean localRestorePendingAfterPick;
    private Uri pendingLocalRestoreUri;
    private ActivityResultLauncher<Uri> localBackupLocationPicker;
    private ActivityResultLauncher<String[]> localBackupFilePicker;
    private int lanShareState = R.string.lan_status_not_started;
    private String pendingReceivedPackage;
    private JSONObject pendingReceivedObject;
    private LanBackupTransferServer activeLanBackupServer;
    private Bundle restoredSectionState;
    private TargetBackupUi mainBackupUi;
    private TargetBackupUi secondaryBackupUi;
    private String sessionLedgerPassword;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localBackupLocationPicker = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri == null) {
                localBackupPendingAfterPick = false;
                localAutoBackupPendingAfterPick = false;
                return;
            }
            try {
                requireContext().getContentResolver().takePersistableUriPermission(uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception error) {
                localBackupPendingAfterPick = false;
                localAutoBackupPendingAfterPick = false;
                Toast.makeText(requireContext(), R.string.backup_folder_permission_failed, Toast.LENGTH_LONG).show();
                return;
            }
            if (!hasPersistedTreeWritePermission(uri)) {
                localBackupPendingAfterPick = false;
                localAutoBackupPendingAfterPick = false;
                Toast.makeText(requireContext(), R.string.backup_folder_write_failed, Toast.LENGTH_LONG).show();
                return;
            }
            localBackupTreeUri = uri.toString();
            savePrefs();
            updateSectionSummaries();
            if (localBackupPendingAfterPick) {
                localBackupPendingAfterPick = false;
                backupToLocal();
            }
            if (localAutoBackupPendingAfterPick) {
                localAutoBackupPendingAfterPick = false;
                setLocalAutoBackupChecked(true);
                LocalAutoBackupManager.requestBackup(requireContext());
            }
        });
        localBackupFilePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::restoreFromLocalPickedFile);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings_backup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        restoredSectionState = savedInstanceState;
        executor = Executors.newSingleThreadExecutor();
        repository = RecordRepository.getInstance(requireContext());
        passwordRepository = PasswordRepository.getInstance(requireContext());
        otpRepository = OtpRepository.getInstance(requireContext());
        backupCoordinator = new BackupCoordinator(requireContext());
        backupPackageReader = new BackupPackageReader(requireContext());
        backupRestoreManager = new BackupRestoreManager(requireContext());
        view.findViewById(R.id.btn_webdav_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        view.findViewById(R.id.btn_webdav_help).setOnClickListener(v -> {
            Intent help = new Intent(requireContext(), com.secureqr.scanner.ui.help.HelpManualActivity.class);
            help.putExtra(com.secureqr.scanner.ui.help.HelpManualActivity.EXTRA_SECTION, "webdav");
            startActivity(help);
        });
        webdavUrl = view.findViewById(R.id.et_webdav_url);
        webdavUser = view.findViewById(R.id.et_webdav_user);
        webdavPass = view.findViewById(R.id.et_webdav_pass);
        backupEncryptionPassword = view.findViewById(R.id.et_backup_encryption_password);
        backupWebdavUrl = view.findViewById(R.id.et_webdav_backup_url);
        backupWebdavUser = view.findViewById(R.id.et_webdav_backup_user);
        backupWebdavPass = view.findViewById(R.id.et_webdav_backup_pass);
        autoSync = view.findViewById(R.id.cb_auto_sync);
        localAutoBackup = view.findViewById(R.id.cb_local_auto_backup);
        backupFilePrefix = createNetworkInput(getString(R.string.backup_file_prefix_hint));
        backupLedgerPassword = view.findViewById(R.id.rb_backup_encryption_ledger);
        backupCustomPassword = view.findViewById(R.id.rb_backup_encryption_custom);
        backupEncryptionModeGroup = view.findViewById(R.id.rg_backup_encryption_mode);
        convertLegacyButton = view.findViewById(R.id.btn_convert_main_legacy_backup);
        algorithmSpinner = view.findViewById(R.id.sp_webdav_algorithm);
        mainSyncContent = view.findViewById(R.id.sp_main_sync_content);
        backupSyncContent = view.findViewById(R.id.sp_backup_sync_content);
        lastSync = view.findViewById(R.id.tv_last_sync);
        backupHistory = view.findViewById(R.id.tv_backup_history);
        cloudBackups = view.findViewById(R.id.ll_cloud_backups);
        backupEncryptionContent = view.findViewById(R.id.content_backup_encryption);
        webdavConfigContent = view.findViewById(R.id.content_webdav_config);
        networkAccessContent = view.findViewById(R.id.content_network_access);
        lanShareContent = view.findViewById(R.id.content_lan_share);
        syncSettingsContent = view.findViewById(R.id.content_sync_settings);
        backupDataContent = view.findViewById(R.id.content_backup_data);
        webdavConfigArrow = view.findViewById(R.id.tv_webdav_config_arrow);
        backupEncryptionArrow = view.findViewById(R.id.tv_backup_encryption_arrow);
        networkAccessArrow = view.findViewById(R.id.tv_network_access_arrow);
        lanShareArrow = view.findViewById(R.id.tv_lan_share_arrow);
        syncSettingsArrow = view.findViewById(R.id.tv_sync_settings_arrow);
        backupDataArrow = view.findViewById(R.id.tv_backup_data_arrow);
        localBackupArrow = view.findViewById(R.id.tv_local_backup_arrow);
        webdavConfigStatus = view.findViewById(R.id.tv_webdav_config_status);
        backupEncryptionStatus = view.findViewById(R.id.tv_backup_encryption_method);
        networkAccessStatus = view.findViewById(R.id.tv_network_access_status);
        lanShareStatus = view.findViewById(R.id.tv_lan_share_status);
        syncSettingsStatus = view.findViewById(R.id.tv_sync_settings_status);
        backupDataStatus = view.findViewById(R.id.tv_backup_data_status);
        localBackupStatus = view.findViewById(R.id.tv_local_backup_status);
        localBackupLocation = view.findViewById(R.id.tv_local_backup_location);
        localBackupLatest = view.findViewById(R.id.tv_local_backup_latest);
        syncTargetGroup = view.findViewById(R.id.rg_sync_target);
        localBackupContent = view.findViewById(R.id.content_local_backup);
        localBackupHistoryContent = verticalContainer();
        localBackupHistoryContent.setVisibility(View.GONE);
        localBackupHistorySummary = createNetworkText(getString(R.string.backup_history_title), 14, true);
        localBackupHistoryArrow = createNetworkText("▶", 14, false);
        LinearLayout localHistoryRow = horizontalRow(44);
        localHistoryRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        localHistoryRow.addView(localBackupHistorySummary, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        localHistoryRow.addView(localBackupHistoryArrow);
        localBackupContent.addView(localHistoryRow);
        localBackupContent.addView(localBackupHistoryContent);
        migrateLegacySecrets();
        setupWebDavPasswordVisibilityToggle(view.findViewById(R.id.btn_toggle_webdav_pass), webdavPass);
        setupWebDavPasswordVisibilityToggle(view.findViewById(R.id.btn_toggle_backup_webdav_pass), backupWebdavPass);
        setupWebDavPasswordVisibilityToggle(view.findViewById(R.id.btn_toggle_backup_encryption_password), backupEncryptionPassword);
        setupHistoryDropdown(webdavUrl, KEY_HISTORY_MAIN_URL);
        setupHistoryDropdown(webdavUser, KEY_HISTORY_MAIN_USER);
        setupHistoryDropdown(backupWebdavUrl, KEY_HISTORY_BACKUP_URL);
        setupHistoryDropdown(backupWebdavUser, KEY_HISTORY_BACKUP_USER);

        algorithmSpinner.setAdapter(new ThemedSpinnerAdapter(requireContext(), java.util.Arrays.asList(CryptoHelper.supportedAlgorithms())));
        List<String> contentModes = syncContentLabels();
        mainSyncContent.setAdapter(new ThemedSpinnerAdapter(requireContext(), contentModes));
        backupSyncContent.setAdapter(new ThemedSpinnerAdapter(requireContext(), contentModes));

        loadPrefs();
        installBackupPrefixField(view);
        setSectionTitle(view.findViewById(R.id.row_sync_settings_title), getString(R.string.online_backup_restore_title));
        setSectionTitle(view.findViewById(R.id.row_local_backup_title), getString(R.string.local_backup_restore_title));
        setupBackupPasswordBinding();
        setupWebDavTestInvalidation();
        syncTargetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            savePrefs();
            updateSectionSummaries();
        });
        autoSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePrefs();
            updateSectionSummaries();
        });
        localAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingLocalAutoBackup) return;
            if (isChecked && (localBackupTreeUri == null || localBackupTreeUri.isEmpty()
                    || !hasPersistedTreeWritePermission(Uri.parse(localBackupTreeUri)))) {
                setLocalAutoBackupChecked(false);
                localAutoBackupPendingAfterPick = true;
                chooseLocalBackupLocation();
                return;
            }
            savePrefs();
            updateSectionSummaries();
            if (isChecked) LocalAutoBackupManager.requestBackup(requireContext());
        });
        view.findViewById(R.id.btn_save_webdav_settings).setOnClickListener(v -> saveWebDavSettingsAfterTest(false));
        view.findViewById(R.id.btn_save_backup_webdav_settings).setOnClickListener(v -> saveWebDavSettingsAfterTest(true));
        convertLegacyButton.setVisibility(View.GONE);
        view.findViewById(R.id.btn_test_main_webdav).setOnClickListener(v -> testTarget(mainTarget()));
        view.findViewById(R.id.btn_test_backup_webdav).setOnClickListener(v -> testTarget(backupTarget()));
        view.findViewById(R.id.btn_sync_main_webdav).setOnClickListener(v -> backupV5Now());
        view.findViewById(R.id.btn_sync_backup_webdav).setOnClickListener(v -> backupV5Now());
        view.findViewById(R.id.btn_restore_main_webdav).setOnClickListener(v -> restoreTarget(mainTarget()));
        view.findViewById(R.id.btn_restore_backup_webdav).setOnClickListener(v -> restoreTarget(backupTarget()));
        view.findViewById(R.id.btn_sync).setOnClickListener(v -> backupV5Now());
        view.findViewById(R.id.btn_choose_backup_target).setOnClickListener(v -> showRestoreTargetChooser());
        view.findViewById(R.id.btn_backup_history).setOnClickListener(v -> loadBackupHistory(true));
        view.findViewById(R.id.btn_local_backup_select_location).setOnClickListener(v -> chooseLocalBackupLocation());
        view.findViewById(R.id.btn_local_backup_now).setOnClickListener(v -> backupToLocal());
        view.findViewById(R.id.btn_local_backup_restore).setOnClickListener(v -> localBackupFilePicker.launch(new String[]{"*/*"}));
        localHistoryRow.setOnClickListener(v -> {
            boolean expand = localBackupHistoryContent.getVisibility() != View.VISIBLE;
            localBackupHistoryContent.setVisibility(expand ? View.VISIBLE : View.GONE);
            localBackupHistoryArrow.setText(expand ? "▼" : "▶");
            if (expand) loadLocalBackupHistory();
        });
        buildNetworkBackupGroups();
        addWebDavQrActions();
        setupCollapsibleSections(view);
        getParentFragmentManager().setFragmentResultListener(ScannerFragment.LAN_TRANSFER_SCAN_REQUEST, getViewLifecycleOwner(), (key, bundle) ->
                handleLanTransferQr(bundle.getString(ScannerFragment.LAN_TRANSFER_SCAN_VALUE, "")));
        getParentFragmentManager().setFragmentResultListener(ScannerFragment.WEBDAV_CONFIG_SCAN_REQUEST,getViewLifecycleOwner(),(key,bundle)->importWebDavQr(bundle.getString(ScannerFragment.WEBDAV_CONFIG_SCAN_VALUE,"")));
    }

    private void addWebDavQrActions(){LinearLayout row=horizontalRow(48);Button qr=new Button(requireContext());qr.setText(R.string.webdav_qr_generate_action);qr.setOnClickListener(v->SensitiveActionGuard.requireRecentAuth(requireActivity(),getString(R.string.webdav_qr_auth_prompt),()->new AlertDialog.Builder(requireContext()).setTitle(R.string.webdav_qr_choose_config).setItems(new String[]{"WebDAV 1","WebDAV 2"},(d,w)->showWebDavQr(w==1)).show()));Button scan=new Button(requireContext());scan.setText(R.string.webdav_qr_scan_action);scan.setOnClickListener(v->getParentFragmentManager().beginTransaction().replace(R.id.fragment_container,ScannerFragment.forWebDavConfigCapture()).addToBackStack(null).commit());row.addView(qr,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(scan,new LinearLayout.LayoutParams(0,dp(48),1));webdavConfigContent.addView(row);}

    private void installBackupPrefixField(View root) {
        if (!(autoSync.getParent() instanceof ViewGroup parent)) return;
        int index = parent.indexOfChild(autoSync) + 1;
        TextView label = createNetworkLabel(getString(R.string.backup_file_prefix_title));
        parent.addView(label, index);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        parent.addView(backupFilePrefix, index + 1, params);
        backupFilePrefix.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                backupFilePrefix.setText(normalizedBackupPrefix());
                savePrefs();
            }
        });
    }
    private void showWebDavQr(boolean backup){EditText url=backup?backupWebdavUrl:webdavUrl,user=backup?backupWebdavUser:webdavUser,pass=backup?backupWebdavPass:webdavPass;if(url.getText().toString().trim().isEmpty()||user.getText().toString().trim().isEmpty()||pass.getText().toString().isEmpty()){Toast.makeText(requireContext(),R.string.webdav_qr_fields_required,Toast.LENGTH_SHORT).show();return;}try{long expires=System.currentTimeMillis()+30000;JSONObject object=new JSONObject().put("v",1).put("expires",expires).put("target",backup?"backup":"main").put("url",url.getText().toString().trim()).put("user",user.getText().toString().trim()).put("pass",pass.getText().toString());String payload="keyscan://webdav-config?data="+android.util.Base64.encodeToString(object.toString().getBytes(StandardCharsets.UTF_8),android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);ImageView image=new ImageView(requireContext());image.setImageBitmap(QRGenerator.generateQR(payload,dp(260)));TextView countdown=createNetworkText(getString(R.string.webdav_qr_validity_seconds,30),14,true);LinearLayout box=verticalContainer();box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);box.addView(image,new LinearLayout.LayoutParams(dp(280),dp(280)));box.addView(countdown);AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle(R.string.webdav_qr_title).setMessage(R.string.webdav_qr_security_notice).setView(box).setNegativeButton(R.string.close,null).setPositiveButton(R.string.webdav_qr_refresh_after_expiry,null).create();Runnable tick=new Runnable(){public void run(){long left=Math.max(0,(expires-System.currentTimeMillis()+999)/1000);countdown.setText(left>0?getString(R.string.webdav_qr_validity_seconds,left):getString(R.string.webdav_qr_expired));if(left>0)countdown.postDelayed(this,1000);}};dialog.setOnShowListener(d->{tick.run();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(System.currentTimeMillis()<expires){Toast.makeText(requireContext(),R.string.webdav_qr_not_expired,Toast.LENGTH_SHORT).show();return;}dialog.dismiss();showWebDavQr(backup);});});dialog.show();}catch(Exception error){Toast.makeText(requireContext(),R.string.webdav_qr_generate_failed,Toast.LENGTH_SHORT).show();}}
    private void importWebDavQr(String raw){try{if(raw==null||!raw.startsWith("keyscan://webdav-config?data="))throw new IllegalArgumentException(getString(R.string.webdav_qr_invalid));String encoded=raw.substring(raw.indexOf("data=")+5);JSONObject object=new JSONObject(new String(android.util.Base64.decode(encoded,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING),StandardCharsets.UTF_8));if(System.currentTimeMillis()>object.optLong("expires"))throw new IllegalStateException(getString(R.string.webdav_qr_import_expired));boolean backup="backup".equals(object.optString("target"));EditText url=backup?backupWebdavUrl:webdavUrl,user=backup?backupWebdavUser:webdavUser,pass=backup?backupWebdavPass:webdavPass;url.setText(object.getString("url"));user.setText(object.getString("user"));pass.setText(object.getString("pass"));mainWebDavTested=false;backupWebDavTested=false;Toast.makeText(requireContext(),R.string.webdav_qr_import_success,Toast.LENGTH_LONG).show();}catch(Exception error){Toast.makeText(requireContext(),error.getMessage()==null?getString(R.string.webdav_qr_import_failed):error.getMessage(),Toast.LENGTH_LONG).show();}}

    private void insertTrashEntry(View view) {
        View anchor = view.findViewById(R.id.card_backup_encryption);
        if (!(anchor.getParent() instanceof LinearLayout root)) return;
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(14));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new TrashFragment()).addToBackStack(null).commit());
        TextView icon = new TextView(requireContext()); icon.setText(R.string.trash_settings_icon); icon.setTextColor(Color.WHITE); icon.setTextSize(18); icon.setGravity(android.view.Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable(); iconBg.setColor(Color.parseColor("#64748B")); iconBg.setCornerRadius(dp(12)); icon.setBackground(iconBg);
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout texts = new LinearLayout(requireContext()); texts.setOrientation(LinearLayout.VERTICAL); texts.setPadding(dp(12),0,0,0);
        TextView title = new TextView(requireContext()); title.setText(R.string.trash_title); title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_main)); title.setTextSize(17); title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD); texts.addView(title);
        TextView subtitle = new TextView(requireContext()); subtitle.setText(R.string.trash_settings_summary); subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary)); subtitle.setTextSize(12); texts.addView(subtitle);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = new TextView(requireContext()); arrow.setText("›"); arrow.setTextSize(24); arrow.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary)); card.addView(arrow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); params.bottomMargin = dp(12);
        root.addView(card, Math.min(2, root.getChildCount()), params);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("sync_section_webdav", isSectionExpanded(SECTION_WEBDAV));
        outState.putBoolean("sync_section_network", isSectionExpanded(SECTION_SYNC_SETTINGS));
        outState.putBoolean("sync_section_lan", isSectionExpanded(SECTION_LAN_SHARE));
        outState.putBoolean("sync_section_local", isSectionExpanded(SECTION_LOCAL_BACKUP));
        outState.putBoolean("sync_inner_access", isSectionExpanded(SECTION_NETWORK_ACCESS));
        outState.putBoolean("sync_inner_backup", isSectionExpanded(SECTION_BACKUP_DATA));
    }

    private void restructureSyncSections(View view) {
        LinearLayout root = (LinearLayout) ((View) view.findViewById(R.id.card_webdav_config)).getParent();
        LinearLayout syncCard = view.findViewById(R.id.card_sync_settings);
        LinearLayout lanCard = view.findViewById(R.id.card_lan_share);
        LinearLayout networkCard = view.findViewById(R.id.card_network_access);
        LinearLayout encryptionCard = view.findViewById(R.id.card_backup_encryption);
        LinearLayout backupCard = view.findViewById(R.id.card_backup_data);

        setSectionTitle(view.findViewById(R.id.row_webdav_config_title), getString(R.string.settings_webdav_config_title));
        setSectionTitle(view.findViewById(R.id.row_sync_settings_title), getString(R.string.online_backup_restore_title));
        setSectionTitle(view.findViewById(R.id.row_lan_share_title), getString(R.string.settings_lan_sync_title));
        setSectionTitle(view.findViewById(R.id.row_network_access_title), getString(R.string.settings_network_access_title));
        setSectionTitle(view.findViewById(R.id.row_backup_data_title), getString(R.string.settings_network_backup_title));
        setSectionTitle(view.findViewById(R.id.row_local_backup_title), getString(R.string.local_backup_restore_title));

        root.removeView(syncCard);
        int lanIndex = root.indexOfChild(lanCard);
        root.addView(syncCard, Math.max(0, lanIndex));

        if (autoSync.getParent() instanceof ViewGroup parent) parent.removeView(autoSync);
        syncSettingsContent.removeAllViews();
        moveInto(syncSettingsContent, networkCard, 0);
        syncSettingsContent.addView(autoSync, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        syncSettingsContent.addView(createNetworkLabel(getString(R.string.backup_file_prefix_title)));
        LinearLayout.LayoutParams prefixParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        prefixParams.bottomMargin = dp(8);
        syncSettingsContent.addView(backupFilePrefix, prefixParams);
        moveInto(syncSettingsContent, backupCard, syncSettingsContent.getChildCount());
        if (encryptionCard.getParent() instanceof ViewGroup parent) parent.removeView(encryptionCard);
        encryptionCard.setVisibility(View.GONE);
        styleNestedSection(networkCard);
        styleNestedSection(backupCard);
        buildWebDavConfigGroups();
        buildNetworkBackupGroups();
    }

    private void buildWebDavConfigGroups() {
        List<View> original = new ArrayList<>();
        while (webdavConfigContent.getChildCount() > 0) {
            View child = webdavConfigContent.getChildAt(0);
            webdavConfigContent.removeViewAt(0);
            original.add(child);
        }
        int split = original.size();
        for (int i = 0; i < original.size(); i++) {
            if (containsView(original.get(i), backupWebdavUrl)) {
                split = Math.max(0, i - 1);
                break;
            }
        }
        LinearLayout mainContent = verticalContainer();
        LinearLayout backupContent = verticalContainer();
        for (int i = 0; i < original.size(); i++) {
            View child = original.get(i);
            if (i == split && child instanceof TextView) continue;
            (i < split ? mainContent : backupContent).addView(child);
        }
        normalizeWebDavConfigLabels(mainContent);
        normalizeWebDavConfigLabels(backupContent);
        webdavConfigContent.addView(createNestedFold("WebDAV 1", mainContent, false));
        webdavConfigContent.addView(createNestedFold("WebDAV 2", backupContent, false));
    }

    private void normalizeWebDavConfigLabels(LinearLayout content) {
        String[] labels = {getString(R.string.webdav_address), getString(R.string.username), getString(R.string.webdav_password)};
        int labelIndex = 0;
        for (int i = 0; i < content.getChildCount() && labelIndex < labels.length; i++) {
            View child = content.getChildAt(i);
            if (child instanceof TextView && !(child instanceof Button)) {
                ((TextView) child).setText(labels[labelIndex++]);
            }
        }
        normalizeButtonText(content, R.id.btn_test_main_webdav, getString(R.string.test_connection));
        normalizeButtonText(content, R.id.btn_test_backup_webdav, getString(R.string.test_connection));
        normalizeButtonText(content, R.id.btn_save_webdav_settings, getString(R.string.save_configuration));
        normalizeButtonText(content, R.id.btn_save_backup_webdav_settings, getString(R.string.save_configuration));
    }

    private void normalizeButtonText(View root, int id, String text) {
        View button = root.findViewById(id);
        if (button instanceof Button) ((Button) button).setText(text);
    }

    private void buildNetworkBackupGroups() {
        mainBackupUi = createTargetBackupUi(false, "WebDAV 1");
        secondaryBackupUi = createTargetBackupUi(true, "WebDAV 2");
        backupDataContent.addView(mainBackupUi.root);
        backupDataContent.addView(secondaryBackupUi.root);
    }

    private TargetBackupUi createTargetBackupUi(boolean backup, String label) {
        TargetBackupUi ui = new TargetBackupUi(backup, label);
        ui.root = verticalContainer();
        ui.root.setPadding(0, dp(6), 0, dp(6));
        LinearLayout titleRow = horizontalRow(48);
        TextView title = createNetworkText(label, 15, true);
        ui.summary = createNetworkText(getString(R.string.backup_latest_none), 12, false);
        ui.arrow = createNetworkText("", 18, true);
        LinearLayout titleBlock = verticalContainer();
        titleBlock.addView(title);
        titleBlock.addView(ui.summary);
        titleRow.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(ui.arrow, new LinearLayout.LayoutParams(dp(40), dp(48)));
        ui.root.addView(titleRow);
        ui.content = verticalContainer();
        ui.content.setVisibility(View.VISIBLE);
        ui.latestName = createNetworkText(getString(R.string.backup_none), 14, true);
        ui.latestName.setSingleLine(true);
        ui.latestName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ui.latestName.setOnClickListener(v -> showFullBackupName(ui));
        ui.latestTime = createNetworkText("", 12, false);
        Button restore = new Button(requireContext());
        restore.setText(R.string.webdav_restore_button);
        restore.setEnabled(false);
        restore.setOnClickListener(v -> {
            WebDavTarget target = targetFor(ui.backup);
            if (target != null && ui.latest != null) confirmRestoreBackup(target, ui.latest.path);
        });
        ui.restore = restore;
        ui.content.addView(createNetworkText(getString(R.string.backup_latest), 13, true));
        ui.content.addView(ui.latestName);
        LinearLayout latestRow = horizontalRow(48);
        latestRow.addView(ui.latestTime, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        latestRow.addView(restore, new LinearLayout.LayoutParams(dp(96), dp(44)));
        ui.content.addView(latestRow);
        ui.historyRoot = verticalContainer();
        LinearLayout historyTitle = horizontalRow(48);
        ui.historySummary = createNetworkText(getString(R.string.backup_history_count, 0), 14, true);
        ui.historyArrow = createNetworkText("\u25b6", 18, true);
        historyTitle.addView(ui.historySummary, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        historyTitle.addView(ui.historyArrow, new LinearLayout.LayoutParams(dp(40), dp(48)));
        ui.historyRoot.addView(historyTitle);
        ui.historyContent = verticalContainer();
        ui.historyContent.setVisibility(View.GONE);
        ui.historyRoot.addView(ui.historyContent);
        ui.content.addView(ui.historyRoot);
        ui.root.addView(ui.content);
        titleRow.setOnClickListener(v -> refreshTargetSummary(ui));
        historyTitle.setOnClickListener(v -> {
            if (targetFor(ui.backup) == null) return;
            boolean expanded = ui.historyContent.getVisibility() != View.VISIBLE;
            setFoldVisible(ui.historyContent, ui.historyArrow, expanded);
            if (expanded) loadTargetHistory(ui);
            else collapseTargetHistory(ui);
        });
        refreshTargetSummary(ui);
        return ui;
    }

    private View createNestedFold(String title, LinearLayout content, boolean expanded) {
        LinearLayout root = verticalContainer();
        LinearLayout row = horizontalRow(48);
        TextView label = createNetworkText(title, 15, true);
        TextView arrow = createNetworkText(expanded ? "\u25bc" : "\u25b6", 18, true);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(arrow, new LinearLayout.LayoutParams(dp(40), dp(48)));
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        root.addView(row);
        root.addView(content);
        row.setOnClickListener(v -> setFoldVisible(content, arrow, content.getVisibility() != View.VISIBLE));
        return root;
    }

    private void setFoldVisible(View content, TextView arrow, boolean expanded) {
        if (expanded) {
            content.setAlpha(0f);
            content.setVisibility(View.VISIBLE);
            content.animate().alpha(1f).setDuration(180).start();
        } else {
            content.setVisibility(View.GONE);
            content.setAlpha(1f);
        }
        arrow.setText(expanded ? "\u25bc" : "\u25b6");
    }

    private LinearLayout verticalContainer() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private LinearLayout horizontalRow(int heightDp) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return row;
    }

    private boolean containsView(View root, View target) {
        if (root == target) return true;
        if (!(root instanceof ViewGroup group)) return false;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsView(group.getChildAt(i), target)) return true;
        }
        return false;
    }

    private void moveInto(LinearLayout destination, View child, int index) {
        if (child.getParent() instanceof ViewGroup parent) parent.removeView(child);
        destination.addView(child, Math.min(index, destination.getChildCount()));
    }

    private void styleNestedSection(LinearLayout section) {
        section.setElevation(0f);
        section.setPadding(0, dp(4), 0, dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        section.setLayoutParams(params);
    }

    private void setSectionTitle(View row, String title) {
        if (!(row instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView text && child.getId() == View.NO_ID) {
                text.setText(title);
                return;
            }
        }
    }

    private void loadPrefs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        webdavUrl.setText(prefs.getString("url", ""));
        webdavUser.setText(prefs.getString("user", ""));
        webdavPass.setText(readSecret("pass"));
        boolean customBackup = prefs.getBoolean(KEY_BACKUP_INDEPENDENT, false);
        setBackupEncryptionMode(customBackup);
        backupEncryptionPassword.setText("");
        backupWebdavUrl.setText(prefs.getString("backup_url", ""));
        backupWebdavUser.setText(prefs.getString("backup_user", ""));
        backupWebdavPass.setText(readSecret("backup_pass"));
        autoSync.setChecked(prefs.getBoolean("auto_sync", true));
        setLocalAutoBackupChecked(prefs.getBoolean(LocalAutoBackupManager.KEY_ENABLED, false));
        backupFilePrefix.setText(prefs.getString("backup_file_prefix", "filebackup"));
        String backupTarget = prefs.getString("backup_target_selection", "all");
        syncTargetGroup.check("main".equals(backupTarget) ? R.id.rb_sync_target_main
                : "backup".equals(backupTarget) ? R.id.rb_sync_target_backup
                : R.id.rb_sync_target_all);
        localBackupTreeUri = prefs.getString("local_backup_tree_uri", "");
        long localBackupTime = prefs.getLong("last_local_backup", 0);
        String localBackupName = prefs.getString("last_local_backup_name", "");
        localBackupLatestLabel = localBackupTime > 0
                ? (localBackupName.isEmpty() ? "" : localBackupName + " \u00b7 ") + formatLocalTime(localBackupTime)
                : "";
        setSyncContentSelection(mainSyncContent, prefs.getString("main_sync_content", "all"));
        setSyncContentSelection(backupSyncContent, prefs.getString("backup_sync_content", "all"));
        updateLastSyncText(prefs.getLong("last_sync", 0));
        updateSectionSummaries();
    }

    private void setupPasswordVisibilityToggle(Button toggleButton, EditText passwordInput) {
        if (toggleButton == null || passwordInput == null) return;
        passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        setIconButton(toggleButton, R.drawable.ic_visibility_off_24, getString(R.string.show_content));
        toggleButton.setOnClickListener(v -> {
            boolean isHidden = passwordInput.getTransformationMethod() instanceof PasswordTransformationMethod;
            passwordInput.setTransformationMethod(isHidden
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            setIconButton(toggleButton, isHidden ? R.drawable.ic_visibility_24 : R.drawable.ic_visibility_off_24, getString(isHidden ? R.string.hide_content : R.string.show_content));
            passwordInput.setSelection(passwordInput.getText().length());
        });
    }

    private void setupWebDavPasswordVisibilityToggle(Button toggleButton, EditText passwordInput) {
        if (toggleButton == null || passwordInput == null) return;
        ViewGroup.LayoutParams toggleParams = toggleButton.getLayoutParams();
        toggleParams.width = dp(42);
        toggleParams.height = dp(42);
        toggleButton.setLayoutParams(toggleParams);
        toggleButton.setMinWidth(dp(42));
        toggleButton.setMinHeight(dp(42));
        if (toggleButton.getParent() instanceof View parent) {
            ViewGroup.LayoutParams parentParams = parent.getLayoutParams();
            parentParams.height = dp(42);
            parent.setLayoutParams(parentParams);
        }
        passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        setIconButton(toggleButton, R.drawable.ic_visibility_off_24, getString(R.string.show_content));
        toggleButton.setOnClickListener(v -> {
            boolean isHidden = passwordInput.getTransformationMethod() instanceof PasswordTransformationMethod;
            if (isHidden) {
                SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.view_webdav_password), () -> {
                    boolean wasSecure = SensitiveWindowGuard.enable(requireActivity());
                    passwordInput.setTag(R.id.et_webdav_pass, wasSecure);
                    showSecretTemporarily(toggleButton, passwordInput);
                });
            } else {
                hidePassword(toggleButton, passwordInput);
            }
        });
    }

    private void showSecretTemporarily(Button toggleButton, EditText passwordInput) {
        passwordInput.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        setIconButton(toggleButton, R.drawable.ic_visibility_24, getString(R.string.hide_content));
        passwordInput.setSelection(passwordInput.getText().length());
        if (hideWebDavSecretsRunnable != null) secretHandler.removeCallbacks(hideWebDavSecretsRunnable);
        hideWebDavSecretsRunnable = this::hideAllWebDavSecrets;
        secretHandler.postDelayed(hideWebDavSecretsRunnable, 30_000L);
    }

    private void hidePassword(Button toggleButton, EditText passwordInput) {
        passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        setIconButton(toggleButton, R.drawable.ic_visibility_off_24, getString(R.string.show_content));
        passwordInput.setSelection(passwordInput.getText().length());
        Object tag = passwordInput.getTag(R.id.et_webdav_pass);
        if (tag instanceof Boolean) {
            SensitiveWindowGuard.restore(requireActivity(), (Boolean) tag);
            passwordInput.setTag(R.id.et_webdav_pass, null);
        }
    }

    private void hideAllWebDavSecrets() {
        View view = getView();
        if (view != null) {
            hidePassword(view.findViewById(R.id.btn_toggle_webdav_pass), webdavPass);
            hidePassword(view.findViewById(R.id.btn_toggle_backup_webdav_pass), backupWebdavPass);
        }
    }

    private void savePrefs() {
        saveInputHistory(KEY_HISTORY_MAIN_URL, webdavUrl.getText().toString().trim());
        saveInputHistory(KEY_HISTORY_MAIN_USER, webdavUser.getText().toString().trim());
        saveInputHistory(KEY_HISTORY_BACKUP_URL, backupWebdavUrl.getText().toString().trim());
        saveInputHistory(KEY_HISTORY_BACKUP_USER, backupWebdavUser.getText().toString().trim());
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("url", webdavUrl.getText().toString().trim())
                .putString("user", webdavUser.getText().toString().trim())
                .putBoolean(KEY_BACKUP_INDEPENDENT, SecuritySettings.hasDataEncryptionKey(requireContext()) || (backupCustomPassword != null && backupCustomPassword.isChecked()))
                .putString("backup_url", backupWebdavUrl.getText().toString().trim())
                .putString("backup_user", backupWebdavUser.getText().toString().trim())
                .putString("main_sync_content", selectedSyncContentKey(mainSyncContent))
                .putString("backup_sync_content", selectedSyncContentKey(backupSyncContent))
                .putString("local_backup_tree_uri", localBackupTreeUri)
                .putBoolean("auto_sync", autoSync.isChecked())
                .putBoolean(LocalAutoBackupManager.KEY_ENABLED, localAutoBackup != null && localAutoBackup.isChecked())
                .putString("backup_file_prefix", normalizedBackupPrefix())
                .putString("backup_target_selection", selectedTargetKey())
                .apply();
        if (!webdavPass.getText().toString().isEmpty()) writeSecret("pass", webdavPass.getText().toString());
        if (!backupWebdavPass.getText().toString().isEmpty()) writeSecret("backup_pass", backupWebdavPass.getText().toString());
        updateSectionSummaries();
    }

    private void migrateLegacySecrets() {
        secretMigrationResults.clear();
        secretMigrationResults.add(SecureSecretStore.migrateLegacyString(requireContext(), PREFS, "pass"));
        secretMigrationResults.add(SecureSecretStore.migrateLegacyString(requireContext(), PREFS, "backup_pass"));
        secretMigrationResults.add(SecureSecretStore.migrateLegacyString(requireContext(), PREFS, KEY_BACKUP_PASSWORD));
        secretMigrationResults.add(SecureSecretStore.migrateLegacyString(requireContext(), PREFS, KEY_RECOVERY_KEY));
        SecuritySettings.hasDataEncryptionKey(requireContext());
    }

    private void setLocalAutoBackupChecked(boolean checked) {
        bindingLocalAutoBackup = true;
        if (localAutoBackup != null) localAutoBackup.setChecked(checked);
        bindingLocalAutoBackup = false;
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(LocalAutoBackupManager.KEY_ENABLED, checked).apply();
    }

    private String readSecret(String key) {
        return SecureSecretStore.getSecret(requireContext(), PREFS, key);
    }

    private boolean writeSecret(String key, String value) {
        return SecureSecretStore.putSecret(requireContext(), PREFS, key, value);
    }

    private void setupWebDavTestInvalidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mainWebDavTested = mainWebDavFingerprint().equals(mainWebDavTestFingerprint);
                backupWebDavTested = backupWebDavFingerprint().equals(backupWebDavTestFingerprint);
                updateSectionSummaries();
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        webdavUrl.addTextChangedListener(watcher);
        webdavUser.addTextChangedListener(watcher);
        webdavPass.addTextChangedListener(watcher);
        backupWebdavUrl.addTextChangedListener(watcher);
        backupWebdavUser.addTextChangedListener(watcher);
        backupWebdavPass.addTextChangedListener(watcher);
    }

    private String mainWebDavFingerprint() {
        return webdavUrl.getText().toString().trim() + "\n"
                + webdavUser.getText().toString().trim() + "\n"
                + webdavPass.getText().toString();
    }

    private String backupWebDavFingerprint() {
        return backupWebdavUrl.getText().toString().trim() + "\n"
                + backupWebdavUser.getText().toString().trim() + "\n"
                + backupWebdavPass.getText().toString();
    }

    private void saveWebDavSettingsAfterTest(boolean backup) {
        boolean complete = backup
                ? isTargetInputComplete(backupWebdavUrl, backupWebdavUser, backupWebdavPass)
                : isTargetInputComplete(webdavUrl, webdavUser, webdavPass);
        if (!complete) {
            Toast.makeText(requireContext(), R.string.webdav_complete_config_required, Toast.LENGTH_SHORT).show();
            expandOnly(SECTION_WEBDAV);
            return;
        }
        boolean tested = backup ? backupWebDavTested : mainWebDavTested;
        if (!tested) {
            Toast.makeText(requireContext(), R.string.test_connection_first, Toast.LENGTH_SHORT).show();
            expandOnly(SECTION_WEBDAV);
            return;
        }
        saveWebDavSettingsWithRecoveryKey();
    }

    private void setupCollapsibleCard(View titleRow, LinearLayout card, TextView arrow, String key, boolean defaultExpanded) {
        if (titleRow == null || card == null || arrow == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setCardExpanded(card, arrow, prefs.getBoolean(key, defaultExpanded), false);
        titleRow.setClickable(true);
        titleRow.setFocusable(true);
        titleRow.setOnClickListener(v -> {
            boolean expanded = !isCardExpanded(card);
            setCardExpanded(card, arrow, expanded, true);
            prefs.edit().putBoolean(key, expanded).apply();
        });
        if (titleRow instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.setClickable(true);
                child.setFocusable(true);
                child.setOnClickListener(v -> {
                    boolean expanded = !isCardExpanded(card);
                    setCardExpanded(card, arrow, expanded, true);
                    prefs.edit().putBoolean(key, expanded).apply();
                });
            }
        }
    }

    private boolean isCardExpanded(LinearLayout card) {
        return card.getChildCount() < 2 || card.getChildAt(1).getVisibility() == View.VISIBLE;
    }

    private void setCardExpanded(LinearLayout card, TextView arrow, boolean expanded, boolean animate) {
        for (int i = 1; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (animate && expanded) {
                child.setAlpha(0f);
                child.setVisibility(View.VISIBLE);
                child.animate().alpha(1f).setDuration(300).start();
            } else if (animate) {
                child.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    child.setVisibility(View.GONE);
                    child.setAlpha(1f);
                }).start();
            } else {
                child.setVisibility(expanded ? View.VISIBLE : View.GONE);
                child.setAlpha(1f);
            }
        }
        arrow.setText(expanded ? "\u25bc" : "\u25b6");
    }

    private void setupHistoryDropdown(EditText input, String key) {
        input.setOnClickListener(v -> showInputHistory(input, key));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showInputHistory(input, key);
        });
    }

    private void setupCollapsibleSections(View view) {
        View webdavTitle = view.findViewById(R.id.row_webdav_config_title);
        View backupEncryptionTitle = view.findViewById(R.id.row_backup_encryption_title);
        View lanTitle = view.findViewById(R.id.row_lan_share_title);
        View syncTitle = view.findViewById(R.id.row_sync_settings_title);
        View localBackupTitle = view.findViewById(R.id.row_local_backup_title);
        if (webdavTitle != null) webdavTitle.setOnClickListener(v -> toggleSection(SECTION_WEBDAV));
        if (backupEncryptionTitle != null) backupEncryptionTitle.setOnClickListener(v -> toggleNestedSection(SECTION_BACKUP_ENCRYPTION));
        if (lanTitle != null) lanTitle.setOnClickListener(v -> toggleSection(SECTION_LAN_SHARE));
        if (syncTitle != null) syncTitle.setOnClickListener(v -> toggleSection(SECTION_SYNC_SETTINGS));
        if (localBackupTitle != null) localBackupTitle.setOnClickListener(v -> toggleSection(SECTION_LOCAL_BACKUP));
        restoreOrCollapseSections();
        updateSectionSummaries();
    }

    private void restoreOrCollapseSections() {
        expandOnly(SECTION_NONE);
        setSectionExpanded(SECTION_BACKUP_ENCRYPTION, false);
        if (restoredSectionState == null) return;
        setSectionExpanded(SECTION_WEBDAV, restoredSectionState.getBoolean("sync_section_webdav"));
        setSectionExpanded(SECTION_SYNC_SETTINGS, restoredSectionState.getBoolean("sync_section_network"));
        setSectionExpanded(SECTION_LAN_SHARE, restoredSectionState.getBoolean("sync_section_lan"));
        setSectionExpanded(SECTION_LOCAL_BACKUP, restoredSectionState.getBoolean("sync_section_local"));
    }

    private void toggleNestedSection(int section) {
        boolean expanded = !isSectionExpanded(section);
        setSectionExpanded(section, expanded);
        if (section == SECTION_BACKUP_DATA && !expanded) {
            selectedBackupPaths.clear();
            expandedBackupDates.clear();
            cloudBackups.removeAllViews();
            backupHistory.setText(R.string.network_backup_none);
        }
    }

    private void toggleSection(int section) {
        expandOnly(isSectionExpanded(section) ? SECTION_NONE : section);
    }

    private boolean isSectionExpanded(int section) {
        LinearLayout content = sectionContent(section);
        return content != null && content.getVisibility() == View.VISIBLE;
    }

    private void expandOnly(int section) {
        setSectionExpanded(SECTION_WEBDAV, section == SECTION_WEBDAV);
        setSectionExpanded(SECTION_LAN_SHARE, section == SECTION_LAN_SHARE);
        setSectionExpanded(SECTION_SYNC_SETTINGS, section == SECTION_SYNC_SETTINGS);
        setSectionExpanded(SECTION_LOCAL_BACKUP, section == SECTION_LOCAL_BACKUP);
    }

    private void setSectionExpanded(int section, boolean expanded) {
        LinearLayout content = sectionContent(section);
        TextView arrow = sectionArrow(section);
        if (content != null) content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (arrow != null) arrow.setText(expanded ? "\u25bc" : "\u25b6");
        if (section == SECTION_WEBDAV && !expanded) hideAllWebDavSecrets();
    }

    private LinearLayout sectionContent(int section) {
        if (section == SECTION_WEBDAV) return webdavConfigContent;
        if (section == SECTION_BACKUP_ENCRYPTION) return backupEncryptionContent;
        if (section == SECTION_NETWORK_ACCESS) return networkAccessContent;
        if (section == SECTION_LAN_SHARE) return lanShareContent;
        if (section == SECTION_SYNC_SETTINGS) return syncSettingsContent;
        if (section == SECTION_BACKUP_DATA) return backupDataContent;
        if (section == SECTION_LOCAL_BACKUP) return localBackupContent;
        return null;
    }

    private TextView sectionArrow(int section) {
        if (section == SECTION_WEBDAV) return webdavConfigArrow;
        if (section == SECTION_BACKUP_ENCRYPTION) return backupEncryptionArrow;
        if (section == SECTION_NETWORK_ACCESS) return networkAccessArrow;
        if (section == SECTION_LAN_SHARE) return lanShareArrow;
        if (section == SECTION_SYNC_SETTINGS) return syncSettingsArrow;
        if (section == SECTION_BACKUP_DATA) return backupDataArrow;
        if (section == SECTION_LOCAL_BACKUP) return localBackupArrow;
        return null;
    }

    private void showInputHistory(EditText input, String key) {
        List<String> history = readInputHistory(key);
        if (history.isEmpty()) return;
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_card);
        int width = Math.max(input.getWidth(), dp(260));
        PopupWindow popup = new PopupWindow(content, width, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        for (String value : history) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(6), dp(6), dp(6));
            TextView text = new TextView(requireContext());
            text.setText(value);
            text.setTextColor(getResources().getColor(R.color.text_main));
            text.setSingleLine(true);
            Button delete = new Button(requireContext());
            delete.setText("X");
            delete.setMinWidth(0);
            delete.setPadding(0, 0, 0, 0);
            row.addView(text, new LinearLayout.LayoutParams(0, dp(38), 1));
            row.addView(delete, new LinearLayout.LayoutParams(dp(38), dp(38)));
            row.setOnClickListener(v -> {
                input.setText(value);
                input.setSelection(input.getText().length());
                popup.dismiss();
            });
            delete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.input_history_delete_title)
                    .setMessage(R.string.input_history_delete_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        removeInputHistory(key, value);
                        popup.dismiss();
                    })
                    .show());
            content.addView(row);
        }
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(4));
        popup.showAsDropDown(input, 0, dp(4));
    }

    private List<String> readInputHistory(String key) {
        String raw = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String item : raw.split("\\n")) {
            String value = item.trim();
            if (!value.isEmpty() && !result.contains(value)) result.add(value);
        }
        return result;
    }

    private void saveInputHistory(String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        List<String> history = readInputHistory(key);
        history.remove(value);
        history.add(0, value);
        while (history.size() > 5) history.remove(history.size() - 1);
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, joinHistory(history))
                .apply();
    }

    private void removeInputHistory(String key, String value) {
        List<String> history = readInputHistory(key);
        history.remove(value);
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, joinHistory(history))
                .apply();
    }

    private String joinHistory(List<String> history) {
        StringBuilder builder = new StringBuilder();
        for (String item : history) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(item);
        }
        return builder.toString();
    }

    private void renderNetworkAccessControls() {
        if (networkAccessContent == null || !isAdded()) return;
        networkAccessContent.removeAllViews();
        TextView intro = createNetworkText(getString(R.string.network_access_intro), 14, true);
        networkAccessContent.addView(intro, topParams(58, 8));

        List<NetworkAccessController.Endpoint> autoEndpoints = NetworkAccessController.autoWebDavEndpoints(requireContext());
        StringBuilder autoText = new StringBuilder(getString(R.string.network_webdav_auto_allow_title)).append('\n');
        if (autoEndpoints.isEmpty()) {
            autoText.append(getString(R.string.not_configured));
        } else {
            for (NetworkAccessController.Endpoint endpoint : autoEndpoints) {
                autoText.append(endpoint.display()).append("\n");
            }
            autoText.append(getString(R.string.network_webdav_auto_allow_status));
        }
        networkAccessContent.addView(createNetworkText(autoText.toString().trim(), 13, false), topParams(autoEndpoints.isEmpty() ? 48 : 86, 8));

        NetworkAccessController.LanInfo activeLan = NetworkAccessController.activeLanInfo(requireContext());
        String lanSummary = activeLan == null
                ? getString(R.string.network_lan_access_disabled)
                : getString(R.string.network_lan_access_enabled, activeLan.subnet);
        networkAccessContent.addView(createNetworkText(lanSummary, 12, false), topParams(54, 8));

        networkAccessContent.addView(createNetworkText(getString(R.string.network_custom_allowlist), 14, true), topParams(28, 12));
        List<NetworkAccessController.Endpoint> endpoints = NetworkAccessController.manualEndpoints(requireContext());
        if (endpoints.isEmpty()) {
            networkAccessContent.addView(createNetworkText(getString(R.string.network_no_manual_allowlist), 12, false), topParams(28, 4));
        } else {
            for (NetworkAccessController.Endpoint endpoint : endpoints) {
                networkAccessContent.addView(createEndpointRow(endpoint), topParams(54, 6));
            }
        }
        Button add = new Button(requireContext());
        add.setText(R.string.network_add_allowlist_address);
        add.setOnClickListener(v -> showAllowlistDialog(null));
        networkAccessContent.addView(add, topParams(42, 10));

        Button test = new Button(requireContext());
        test.setText(R.string.network_run_security_test);
        test.setOnClickListener(v -> runNetworkSecurityTest());
        networkAccessContent.addView(test, topParams(42, 8));
    }

    private void renderLanShareControls() {
        if (lanShareContent == null || !isAdded()) return;
        lanShareContent.removeAllViews();
        NetworkAccessController.LanInfo current = NetworkAccessController.currentLanInfo(requireContext());
        NetworkAccessController.LanInfo active = NetworkAccessController.activeBackupLanInfo(requireContext());
        Switch enabled = new Switch(requireContext());
        enabled.setText(R.string.settings_lan_sync_title);
        enabled.setTextColor(getResources().getColor(R.color.text_main));
        enabled.setChecked(active != null);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                NetworkAccessController.LanInfo info = NetworkAccessController.currentLanInfo(requireContext());
                if (info == null) {
                    buttonView.setChecked(false);
                    Toast.makeText(requireContext(), R.string.lan_sync_wifi_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.lan_sync_enable_title)
                        .setMessage(getString(R.string.lan_sync_enable_message, info.subnet))
                        .setNegativeButton(R.string.cancel, (d, w) -> buttonView.setChecked(false))
                        .setPositiveButton(R.string.confirm, (d, w) -> enableLanShare(info))
                        .show();
            } else {
                disableLanShare(R.string.lan_status_not_started, false);
            }
        });
        lanShareContent.addView(enabled, topParams(44, 8));
        String detail = getString(R.string.lan_sync_network_detail,
                getString(current == null ? R.string.not_connected : R.string.connected),
                current == null ? "-" : current.ipv4, current == null ? "-" : current.subnet);
        lanShareContent.addView(createNetworkText(detail, 13, false), topParams(116, 8));

        Button test = new Button(requireContext());
        test.setText(R.string.test_connection);
        test.setOnClickListener(v -> testLanShare());
        lanShareContent.addView(test, topParams(42, 10));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button send = new Button(requireContext());
        send.setText(R.string.lan_scan_send);
        send.setEnabled(active != null);
        send.setOnClickListener(v -> SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.lan_send_backup_auth), this::startLanBackupSend));
        Button receive = new Button(requireContext());
        receive.setText(R.string.lan_scan_receive);
        receive.setEnabled(active != null);
        receive.setOnClickListener(v -> SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.lan_receive_backup_auth), this::startLanBackupReceive));
        actions.addView(send, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(42), 1);
        right.leftMargin = dp(8);
        actions.addView(receive, right);
        lanShareContent.addView(actions, topParams(42, 8));
        updateSectionSummaries();
    }

    private void enableLanShare(NetworkAccessController.LanInfo info) {
        NetworkAccessController.enableBackupLanSession(info);
        lanShareState = R.string.lan_status_network_allowed;
        renderNetworkAccessControls();
        renderLanShareControls();
    }

    private void disableLanShare(int state, boolean toast) {
        stopLanTransfer();
        NetworkAccessController.clearBackupLanSession();
        lanShareState = state;
        renderNetworkAccessControls();
        renderLanShareControls();
        if (toast && isAdded()) Toast.makeText(requireContext(), R.string.lan_share_network_changed, Toast.LENGTH_SHORT).show();
    }

    private void testLanShare() {
        NetworkAccessController.LanInfo active = NetworkAccessController.activeBackupLanInfo(requireContext());
        if (active == null) {
            Toast.makeText(requireContext(), R.string.lan_share_not_enabled, Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            java.net.ServerSocket server = null;
            Socket socket = null;
            try {
                server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName(active.ipv4));
                int port = server.getLocalPort();
                NetworkAccessController.Decision decision = NetworkAccessController.evaluate(requireContext(), "http://" + active.ipv4 + ":" + port);
                if (!decision.allowed) throw new IllegalStateException(getString(R.string.network_access_not_temporarily_allowed));
                long start = SystemClock.elapsedRealtimeNanos();
                socket = new Socket();
                socket.connect(new InetSocketAddress(active.ipv4, port), 3000);
                long elapsedMs = elapsedMs(start);
                runOnUi(() -> new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.lan_communication_ok_title)
                        .setMessage(getString(R.string.lan_communication_ok_message, formatLatency(elapsedMs), active.ipv4, active.subnet))
                        .setPositiveButton(R.string.confirm, null)
                        .show());
            } catch (Exception e) {
                runOnUi(() -> Toast.makeText(requireContext(), getString(R.string.lan_communication_failed, WebDAVClient.shortNetworkReason(requireContext(), e)), Toast.LENGTH_SHORT).show());
            } finally {
                if (socket != null) try { socket.close(); } catch (Exception ignored) { }
                if (server != null) try { server.close(); } catch (Exception ignored) { }
            }
        });
    }

    private void startLanBackupSend() {
        NetworkAccessController.LanInfo active = NetworkAccessController.activeBackupLanInfo(requireContext());
        if (active == null) {
            Toast.makeText(requireContext(), R.string.lan_share_not_enabled, Toast.LENGTH_SHORT).show();
            return;
        }
        withBackupPassword(false, backupPassword -> {
            lanShareState = R.string.lan_status_sending;
            updateSectionSummaries();
            executor.execute(() -> {
                try {
                    if (!VaultAccessManager.canAccessSensitiveData(requireContext())) {
                        runOnUi(() -> Toast.makeText(requireContext(), R.string.keyscan_unlock_first, Toast.LENGTH_SHORT).show());
                        return;
                    }
                    AppDatabase database = AppDatabase.getInstance(requireContext());
                    String json = toSyncJson(database.recordDao().getSyncRecords(), database.passwordEntryDao().getAllNow(), database.otpTokenDao().getAllNow(), "all");
                    String encrypted = createBackupEnvelope(json, backupPassword);
                    String checksum = sha256(encrypted);
                    long expiresAt = System.currentTimeMillis() + LAN_TRANSFER_TTL_MS;
                    stopLanTransfer();
                    activeLanBackupServer = new LanBackupTransferServer(encrypted, checksum, expiresAt, new LanBackupTransferServer.Listener() {
                        @Override public void onServed() { runOnUi(() -> { lanShareState = R.string.lan_status_transfer_complete; updateSectionSummaries(); }); }
                        @Override public void onExpired() { runOnUi(() -> { lanShareState = R.string.lan_status_not_started; stopLanTransfer(); updateSectionSummaries(); }); }
                        @Override public void onError(Exception error) { runOnUi(() -> Toast.makeText(requireContext(), R.string.lan_backup_send_failed, Toast.LENGTH_SHORT).show()); }
                    });
                    activeLanBackupServer.start(active.ipv4);
                    String payload = lanQrPayload(active, activeLanBackupServer, expiresAt);
                    runOnUi(() -> showLanSendDialog(payload, activeLanBackupServer.packageSize(), expiresAt));
                } catch (Exception e) {
                    runOnUi(() -> {
                        lanShareState = R.string.lan_status_network_allowed;
                        updateSectionSummaries();
                        Toast.makeText(requireContext(), e.getMessage() == null ? getString(R.string.lan_backup_send_failed) : e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void showLanSendDialog(String payload, long size, long expiresAt) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), 0);
        ImageView qr = new ImageView(requireContext());
        Bitmap bitmap = QRGenerator.generateQR(payload, dp(240));
        if (bitmap != null) qr.setImageBitmap(bitmap);
        content.addView(qr, topParams(250, 4));
        TextView info = createNetworkText(getString(R.string.lan_send_qr_info, formatFileSize(size)), 13, false);
        content.addView(info, topParams(88, 8));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lan_send_title)
                .setView(content)
                .setNegativeButton(R.string.lan_cancel_send, (d, w) -> {
                    stopLanTransfer();
                    lanShareState = NetworkAccessController.activeBackupLanInfo(requireContext()) == null ? R.string.lan_status_not_started : R.string.lan_status_network_allowed;
                    updateSectionSummaries();
                })
                .create();
        dialog.setOnDismissListener(d -> { if (System.currentTimeMillis() >= expiresAt) stopLanTransfer(); });
        dialog.show();
    }

    private String lanQrPayload(NetworkAccessController.LanInfo active, LanBackupTransferServer server, long expiresAt) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("protocolVersion", 1);
        payload.put("sessionId", randomHex(16));
        payload.put("senderDeviceName", android.os.Build.MODEL == null ? "KeyScan" : android.os.Build.MODEL);
        payload.put("senderIp", active.ipv4);
        payload.put("senderPort", server.port());
        payload.put("oneTimeToken", server.token());
        payload.put("expiresAt", expiresAt);
        payload.put("backupId", randomHex(8));
        payload.put("encryptedPackageSize", server.packageSize());
        payload.put("packageChecksum", server.sha256());
        payload.put("transferProtocol", "http");
        return "keyscan://lan-transfer?payload=" + android.util.Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP);
    }

    private void startLanBackupReceive() {
        if (NetworkAccessController.activeBackupLanInfo(requireContext()) == null) {
            Toast.makeText(requireContext(), R.string.lan_share_not_enabled, Toast.LENGTH_SHORT).show();
            return;
        }
        getParentFragmentManager().beginTransaction().replace(R.id.fragment_container, ScannerFragment.forLanTransferCapture()).addToBackStack(null).commit();
    }

    private void handleLanTransferQr(String raw) {
        if (raw == null || !raw.startsWith("keyscan://lan-transfer")) {
            Toast.makeText(requireContext(), R.string.lan_backup_regular_qr_notice, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String marker = "payload=";
            int index = raw.indexOf(marker);
            if (index < 0) throw new IllegalArgumentException(getString(R.string.lan_invalid_qr));
            String encoded = raw.substring(index + marker.length());
            JSONObject payload = new JSONObject(new String(android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE), StandardCharsets.UTF_8));
            if (payload.optInt("protocolVersion") != 1) throw new IllegalArgumentException(getString(R.string.lan_backup_unsupported_version));
            if (System.currentTimeMillis() > payload.optLong("expiresAt")) throw new IllegalArgumentException(getString(R.string.lan_backup_expired_qr));
            String host = payload.optString("senderIp");
            int port = payload.optInt("senderPort");
            NetworkAccessController.Decision decision = NetworkAccessController.evaluate(requireContext(), "http://" + host + ":" + port);
            if (!decision.allowed) throw new IllegalArgumentException(getString(R.string.lan_backup_wrong_subnet));
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.lan_backup_sender_found)
                    .setMessage(getString(R.string.lan_backup_sender_details, payload.optString("senderDeviceName", "KeyScan"), formatFileSize(payload.optLong("encryptedPackageSize"))))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.lan_request_receive, (d, w) -> downloadLanBackup(payload))
                    .show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), e.getMessage() == null ? getString(R.string.lan_invalid_qr) : e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadLanBackup(JSONObject payload) {
        lanShareState = R.string.lan_status_receiving;
        updateSectionSummaries();
        executor.execute(() -> {
            try {
                String host = payload.getString("senderIp");
                int port = payload.getInt("senderPort");
                String token = payload.getString("oneTimeToken");
                long expectedSize = payload.getLong("encryptedPackageSize");
                String expectedHash = payload.getString("packageChecksum");
                URL url = new URL("http://" + host + ":" + port + "/keyscan/lan-backup?token=" + token);
                NetworkAccessController.requireAllowed(requireContext(), url.toString(), "LAN_BACKUP_RECEIVE");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("X-KeyScan-Token", token);
                if (connection.getResponseCode() != 200) throw new IllegalStateException(getString(R.string.lan_sender_refused_or_expired));
                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line);
                }
                String encrypted = builder.toString();
                long actualSize = encrypted.getBytes(StandardCharsets.UTF_8).length;
                if (actualSize != expectedSize) throw new IllegalStateException(getString(R.string.lan_received_data_corrupt));
                if (!expectedHash.equals(sha256(encrypted))) throw new IllegalStateException(getString(R.string.lan_received_data_corrupt));
                pendingReceivedPackage = encrypted;
                runOnUi(() -> showLanDecryptDialog());
            } catch (Exception e) {
                runOnUi(() -> {
                    lanShareState = NetworkAccessController.activeBackupLanInfo(requireContext()) == null ? R.string.lan_status_not_started : R.string.lan_status_network_allowed;
                    updateSectionSummaries();
                    Toast.makeText(requireContext(), e.getMessage() == null ? getString(R.string.lan_receive_failed) : e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void showLanDecryptDialog() {
        lanShareState = R.string.lan_status_waiting_decryption;
        updateSectionSummaries();
        EditText input = createPasswordInput(getString(R.string.backup_decryption_password));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lan_data_received_title)
                .setMessage(R.string.lan_data_received_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, (d, w) -> pendingReceivedPackage = null)
                .setPositiveButton(R.string.decrypt_data, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = input.getText().toString();
            executor.execute(() -> {
                try {
                    JSONObject object = backupObjectFromJson(decryptBackupPayload(pendingReceivedPackage, password));
                    pendingReceivedObject = object;
                    runOnUi(() -> {
                        dialog.dismiss();
                        showLanImportPreview(object);
                    });
                } catch (Exception e) {
                    runOnUi(() -> input.setError(getString(R.string.lan_decryption_failed)));
                }
            });
        }));
        dialog.show();
    }
    private void showLanImportPreview(JSONObject object) {
        lanShareState = R.string.lan_import_waiting_confirmation;
        updateSectionSummaries();
        int records = object.optJSONArray("records") == null ? 0 : object.optJSONArray("records").length();
        int passwords = object.optJSONArray("passwords") == null ? 0 : object.optJSONArray("passwords").length();
        int otp = object.optJSONArray("otpTokens") == null ? 0 : object.optJSONArray("otpTokens").length();
        String message = getString(R.string.lan_import_confirm_message, passwords, otp, records);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lan_import_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_import, (d, w) -> mergeRestoredObject(object, () -> runOnUi(() -> Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_SHORT).show())))
                .show();
    }

    private void stopLanTransfer() {
        if (activeLanBackupServer != null) {
            activeLanBackupServer.stop();
            activeLanBackupServer = null;
        }
        pendingReceivedPackage = null;
        pendingReceivedObject = null;
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) builder.append(String.format(Locale.US, "%02x", b & 0xff));
        return builder.toString();
    }

    private String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        StringBuilder builder = new StringBuilder();
        for (byte b : data) builder.append(String.format(Locale.US, "%02x", b & 0xff));
        return builder.toString();
    }

    private View createEndpointRow(NetworkAccessController.Endpoint endpoint) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView text = createNetworkText(endpoint.display() + "\n" + getString(endpoint.enabled ? R.string.enabled : R.string.disabled), 12, false);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button toggle = new Button(requireContext());
        toggle.setText(endpoint.enabled ? R.string.disable : R.string.enable);
        toggle.setOnClickListener(v -> {
            List<NetworkAccessController.Endpoint> endpoints = NetworkAccessController.manualEndpoints(requireContext());
            for (NetworkAccessController.Endpoint item : endpoints) {
                if (item.endpointId.equals(endpoint.endpointId)) item.enabled = !item.enabled;
            }
            NetworkAccessController.saveManualEndpoints(requireContext(), endpoints);
            renderNetworkAccessControls();
        });
        ImageButton edit = createIconButton(R.drawable.ic_edit_24, getString(R.string.action_edit));
        edit.setOnClickListener(v -> showAllowlistDialog(endpoint));
        Button delete = new Button(requireContext());
        delete.setText(R.string.delete_short);
        delete.setOnClickListener(v -> {
            List<NetworkAccessController.Endpoint> endpoints = NetworkAccessController.manualEndpoints(requireContext());
            endpoints.removeIf(item -> item.endpointId.equals(endpoint.endpointId));
            NetworkAccessController.saveManualEndpoints(requireContext(), endpoints);
            renderNetworkAccessControls();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(42)));
        row.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(42)));
        return row;
    }

    private void setIconButton(Button button, int drawableRes, String contentDescription) {
        button.setText("");
        button.setGravity(android.view.Gravity.CENTER);
        button.setContentDescription(contentDescription);
        button.setCompoundDrawablesWithIntrinsicBounds(0, drawableRes, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.action_icon_tint)));
        button.setBackground(null);
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setMinWidth(0);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
    }

    private ImageButton createIconButton(int drawableRes, String contentDescription) {
        ImageButton button = new ImageButton(requireContext());
        button.setContentDescription(contentDescription);
        button.setImageResource(drawableRes);
        button.setColorFilter(ContextCompat.getColor(requireContext(), R.color.action_icon_tint));
        button.setBackgroundResource(R.drawable.bg_icon_action);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        return button;
    }

    private int iconForegroundFor(int drawableRes) {
        return R.color.action_icon_tint;
    }

    private int iconBackgroundFor(int drawableRes) {
        return R.drawable.bg_icon_action;
    }

    private void showAllowlistDialog(@Nullable NetworkAccessController.Endpoint editing) {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText address = createNetworkInput(getString(R.string.address));
        EditText note = createNetworkInput(getString(R.string.note));
        if (editing != null) {
            address.setText(editing.display());
            note.setText(editing.displayName);
        }
        content.addView(createNetworkLabel(getString(R.string.address)));
        content.addView(address, topParams(52, 4));
        content.addView(createNetworkLabel(getString(R.string.note)), topParams(24, 8));
        content.addView(note, topParams(52, 4));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(editing == null ? R.string.network_add_allowlist_title : R.string.network_edit_allowlist_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.network_validate_add, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            NetworkAccessController.Endpoint endpoint = NetworkAccessController.normalize(address.getText().toString());
            if (endpoint == null) {
                address.setError(getString(R.string.invalid_address_format));
                return;
            }
            endpoint.displayName = note.getText().toString().trim();
            List<NetworkAccessController.Endpoint> endpoints = NetworkAccessController.manualEndpoints(requireContext());
            if (editing != null) endpoints.removeIf(item -> item.endpointId.equals(editing.endpointId));
            endpoints.add(endpoint);
            NetworkAccessController.saveManualEndpoints(requireContext(), endpoints);
            dialog.dismiss();
            renderNetworkAccessControls();
        }));
        dialog.show();
    }

    private void runNetworkSecurityTest() {
        List<WebDavTarget> targets = new ArrayList<>();
        WebDavTarget main = mainTarget();
        WebDavTarget backup = backupTarget();
        if (main != null) targets.add(main);
        if (backup != null) targets.add(backup);
        List<NetworkAccessController.Endpoint> manual = NetworkAccessController.manualEndpoints(requireContext());
        NetworkAccessController.LanInfo activeLan = NetworkAccessController.activeLanInfo(requireContext());
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.network_security_test_title), getString(R.string.testing), true, false);
        executor.execute(() -> {
            StringBuilder result = new StringBuilder();
            result.append(getString(R.string.network_security_test_title)).append("\n\n");
            if (targets.isEmpty()) {
                result.append(getString(R.string.network_test_webdav_none)).append("\n\n");
            } else {
                result.append(getString(R.string.network_test_webdav_allowlist)).append('\n');
                for (WebDavTarget target : targets) {
                    WebDAVClient.TestResult test = target.client.testConnectionDetailed();
                    appendConnectionResult(result, target.label, test);
                }
                result.append('\n');
            }
            result.append(getString(R.string.network_custom_allowlist)).append('\n');
            boolean hasManual = false;
            for (NetworkAccessController.Endpoint endpoint : manual) {
                if (!endpoint.enabled) continue;
                hasManual = true;
                appendConnectionResult(result, endpoint.display(), testEndpointConnection(endpoint.display()));
            }
            if (!hasManual) result.append(getString(R.string.network_test_no_enabled_allowlist)).append('\n');
            result.append('\n');

            result.append(getString(R.string.network_test_lan_communication)).append('\n');
            if (activeLan == null) {
                result.append(getString(R.string.network_test_lan_disabled)).append("\n\n");
            } else {
                appendLanTestResult(result, activeLan);
                result.append('\n');
            }

            NetworkAccessController.Decision blocked = NetworkAccessController.evaluate(requireContext(), "https://blocked-keyscan-test.invalid");
            result.append(getString(R.string.network_test_unauthorized_address)).append('\n');
            result.append(getString(blocked.allowed ? R.string.network_test_block_failed : R.string.network_test_block_succeeded));
            result.append("\n\n").append(getString(R.string.network_test_egress_check)).append('\n');
            result.append(getString(blocked.allowed ? R.string.network_test_egress_failed : R.string.network_test_egress_succeeded));
            runOnUi(() -> {
                progress.dismiss();
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.network_security_test_title)
                        .setMessage(result.toString())
                        .setPositiveButton(R.string.confirm, null)
                        .show();
            });
        });
    }

    private void appendConnectionResult(StringBuilder result, String label, WebDAVClient.TestResult test) {
        result.append(label).append('\n');
        if (test.success) {
            result.append(getString(R.string.network_test_connection_success, formatLatency(test.latencyMs))).append('\n');
        } else {
            result.append(getString(R.string.network_test_connection_failed)).append("\n  ").append(test.reason == null || test.reason.isEmpty() ? getString(R.string.connection_failed) : test.reason).append('\n');
        }
    }

    private WebDAVClient.TestResult testEndpointConnection(String raw) {
        HttpURLConnection connection = null;
        try {
            NetworkAccessController.requireAllowed(requireContext(), raw, "NETWORK_TEST");
            long start = SystemClock.elapsedRealtimeNanos();
            URL url = new URL(raw);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            int code = connection.getResponseCode();
            long elapsedMs = elapsedMs(start);
            if (code == 401 || code == 403) return WebDAVClient.TestResult.failure(getString(R.string.authentication_failed));
            return WebDAVClient.TestResult.success(elapsedMs);
        } catch (Exception e) {
            return WebDAVClient.TestResult.failure(e instanceof java.net.MalformedURLException ? getString(R.string.address_format_error) : WebDAVClient.shortNetworkReason(requireContext(), e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void appendLanTestResult(StringBuilder result, NetworkAccessController.LanInfo active) {
        java.net.ServerSocket server = null;
        Socket socket = null;
        try {
            server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName(active.ipv4));
            long start = SystemClock.elapsedRealtimeNanos();
            socket = new Socket();
            socket.connect(new InetSocketAddress(active.ipv4, server.getLocalPort()), 3000);
            result.append(getString(R.string.network_test_communication_success, formatLatency(elapsedMs(start)))).append('\n');
        } catch (Exception e) {
            result.append(getString(R.string.network_test_communication_failed)).append("\n  ").append(WebDAVClient.shortNetworkReason(requireContext(), e)).append('\n');
        } finally {
            if (socket != null) try { socket.close(); } catch (Exception ignored) { }
            if (server != null) try { server.close(); } catch (Exception ignored) { }
        }
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0L, (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L);
    }

    private String formatLatency(long latencyMs) {
        return latencyMs <= 0 ? "<1 ms" : latencyMs + " ms";
    }

    private TextView createNetworkText(String text, int sp, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(getResources().getColor(bold ? R.color.text_main : R.color.text_secondary));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView createNetworkLabel(String text) {
        return createNetworkText(text, 13, true);
    }

    private EditText createNetworkInput(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF636366);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private void updateSectionSummaries() {
        if (backupEncryptionStatus != null) {
            backupEncryptionStatus.setText(backupCustomPassword != null && backupCustomPassword.isChecked()
                    ? R.string.backup_encryption_summary_custom
                    : R.string.backup_encryption_summary_ledger);
        }
        if (webdavConfigStatus != null) {
            boolean mainConfigured = isTargetInputComplete(webdavUrl, webdavUser, webdavPass);
            boolean backupConfigured = isTargetInputComplete(backupWebdavUrl, backupWebdavUser, backupWebdavPass);
            webdavConfigStatus.setText(getString(R.string.webdav_configuration_summary,
                    getString(mainConfigured ? R.string.configured : R.string.not_configured),
                    getString(backupConfigured ? R.string.configured : R.string.not_configured)));
        }
        if (networkAccessStatus != null) {
            networkAccessStatus.setText(R.string.network_protection_enabled);
        }
        if (lanShareStatus != null) {
            lanShareStatus.setText(lanShareState);
        }
        if (syncSettingsStatus != null) {
            String selected = selectedTargetKey();
            String targetLabel = "main".equals(selected) ? "WebDAV 1" : "backup".equals(selected) ? "WebDAV 2" : getString(R.string.all_targets);
            syncSettingsStatus.setText(getString(R.string.sync_settings_summary, targetLabel,
                    getString(autoSync != null && autoSync.isChecked() ? R.string.auto_sync_enabled : R.string.auto_sync_disabled)));
        }
        if (localBackupStatus != null) {
            localBackupStatus.setText(localBackupTreeUri == null || localBackupTreeUri.isEmpty()
                    ? getString(R.string.local_backup_none)
                    : getString(R.string.local_backup_latest, localBackupLatestLabel.isEmpty() ? getString(R.string.network_backup_detected) : localBackupLatestLabel));
        }
        if (localBackupLocation != null) {
            localBackupLocation.setText(localBackupTreeUri == null || localBackupTreeUri.isEmpty()
                    ? getString(R.string.local_backup_not_selected)
                    : localBackupTreeUri);
        }
        if (localBackupLatest != null) {
            localBackupLatest.setText(localBackupLatestLabel == null || localBackupLatestLabel.isEmpty()
                    ? getString(R.string.local_backup_none)
                    : getString(R.string.local_backup_latest, localBackupLatestLabel));
        }
        updateBackupDataSummaryFromPrefs();
    }

    private boolean isWebDavConfigured() {
        return isTargetInputComplete(webdavUrl, webdavUser, webdavPass)
                || isTargetInputComplete(backupWebdavUrl, backupWebdavUser, backupWebdavPass);
    }

    private boolean isTargetInputComplete(EditText url, EditText user, EditText pass) {
        return url != null && user != null && pass != null
                && !url.getText().toString().trim().isEmpty()
                && !user.getText().toString().trim().isEmpty()
                && !pass.getText().toString().isEmpty();
    }

    private String normalizedBackupPrefix() {
        String value = backupFilePrefix == null ? "" : backupFilePrefix.getText().toString().trim();
        value = value.replaceAll("[^A-Za-z0-9_-]", "");
        if (value.isEmpty()) value = "filebackup";
        if (value.length() > 32) value = value.substring(0, 32);
        return value;
    }

    private void updateBackupDataSummaryFromPrefs() {
        if (backupDataStatus == null) return;
        long time = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_sync", 0);
        if (!currentCloudBackupFiles.isEmpty()) {
            String latest = latestCloudBackupTimeLabel(currentCloudBackupFiles);
            if (latest != null) {
                backupDataStatus.setText(getString(R.string.network_backup_last, latest));
                return;
            }
            if (time > 0) {
                backupDataStatus.setText(getString(R.string.network_backup_last, formatLocalTime(time)));
                return;
            }
            backupDataStatus.setText(R.string.network_backup_detected);
            return;
        }
        if (time > 0) {
            backupDataStatus.setText(getString(R.string.network_backup_last, formatLocalTime(time)));
        } else {
            backupDataStatus.setText(R.string.network_backup_none);
        }
    }

    private void setupBackupPasswordBinding() {
        if (backupEncryptionModeGroup != null) {
            backupEncryptionModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                boolean custom = checkedId == R.id.rb_backup_encryption_custom;
                backupEncryptionPassword.setEnabled(custom);
                backupEncryptionPassword.setVisibility(custom ? View.VISIBLE : View.GONE);
                View toggle = getView() == null ? null : getView().findViewById(R.id.btn_toggle_backup_encryption_password);
                if (toggle != null) toggle.setVisibility(custom ? View.VISIBLE : View.GONE);
                if (!custom) backupEncryptionPassword.setText("");
                savePrefs();
                updateSectionSummaries();
            });
        }
        boolean custom = backupCustomPassword != null && backupCustomPassword.isChecked();
        backupEncryptionPassword.setEnabled(custom);
        backupEncryptionPassword.setVisibility(custom ? View.VISIBLE : View.GONE);
        View toggle = getView() == null ? null : getView().findViewById(R.id.btn_toggle_backup_encryption_password);
        if (toggle != null) toggle.setVisibility(custom ? View.VISIBLE : View.GONE);
    }

    private void setBackupEncryptionMode(boolean custom) {
        if (backupEncryptionModeGroup == null) return;
        int id = custom ? R.id.rb_backup_encryption_custom : R.id.rb_backup_encryption_ledger;
        backupEncryptionModeGroup.check(id);
        backupEncryptionPassword.setEnabled(custom);
        backupEncryptionPassword.setVisibility(custom ? View.VISIBLE : View.GONE);
        View toggle = getView() == null ? null : getView().findViewById(R.id.btn_toggle_backup_encryption_password);
        if (toggle != null) toggle.setVisibility(custom ? View.VISIBLE : View.GONE);
    }

    private void saveWebDavSettingsWithRecoveryKey() {
        savePrefs();
        updateSectionSummaries();
        Toast.makeText(requireContext(), R.string.webdav_saved, Toast.LENGTH_SHORT).show();
    }

    private void maybeShowBackupEncryptionSetup() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_BACKUP_METHOD_SET, false)) return;
        showInitialBackupPasswordDialog();
    }

    private void showInitialBackupPasswordDialog() {
        boolean wasSecure = SensitiveWindowGuard.enable(requireActivity());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        TextView message = new TextView(requireContext());
        message.setText(R.string.data_protection_key_explanation);
        message.setTextColor(getResources().getColor(R.color.text_main));
        EditText input = createPasswordInput(getString(R.string.data_protection_key));
        content.addView(message);
        content.addView(input, topParams(52, 12));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.data_protection_key_setup_title)
                .setView(content)
                .setPositiveButton(R.string.continue_action, null)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(d -> SensitiveWindowGuard.restore(requireActivity(), wasSecure));
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString();
            if (value.isEmpty()) {
                input.setError(getString(R.string.data_protection_key_required));
                return;
            }
            setBackupMethod(true, value);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void setBackupMethod(boolean independent, String password) {
        if (independent) SecuritySettings.saveDataEncryptionKey(requireContext(), password);
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_BACKUP_INDEPENDENT, true)
                .putBoolean(KEY_BACKUP_METHOD_SET, true)
                .apply();
        setBackupEncryptionMode(true);
        backupEncryptionPassword.setText("");
        updateSectionSummaries();
    }

    private void showChangeBackupPasswordDialog() {
        boolean wasSecure = SensitiveWindowGuard.enable(requireActivity());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText oldInput = createPasswordInput(getString(R.string.data_protection_key_current));
        EditText newInput = createPasswordInput(getString(R.string.data_protection_key_new));
        EditText confirmInput = createPasswordInput(getString(R.string.data_protection_key_confirm));
        content.addView(oldInput, topParams(52, 0));
        content.addView(newInput, topParams(52, 10));
        content.addView(confirmInput, topParams(52, 10));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.data_protection_key_change_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnDismissListener(d -> SensitiveWindowGuard.restore(requireActivity(), wasSecure));
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldValue = oldInput.getText().toString();
            String current = currentIndependentBackupPassword();
            if (backupCustomPassword != null && backupCustomPassword.isChecked() && !current.equals(oldValue)) {
                oldInput.setError(getString(R.string.current_password_incorrect));
                return;
            }
            String next = newInput.getText().toString();
            if (next.isEmpty() || !next.equals(confirmInput.getText().toString())) {
                confirmInput.setError(getString(R.string.passwords_do_not_match));
                return;
            }
            SecuritySettings.saveDataEncryptionKey(requireContext(), next);
            requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_BACKUP_INDEPENDENT, true)
                    .putBoolean(KEY_BACKUP_METHOD_SET, true)
                    .apply();
            setBackupEncryptionMode(true);
            backupEncryptionPassword.setText("");
            updateSectionSummaries();
            Toast.makeText(requireContext(), R.string.webdav_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showRecoveryKeyDialog(String recoveryKey) {
        TextView keyView = new TextView(requireContext());
        keyView.setText(recoveryKey);
        keyView.setTextColor(getResources().getColor(R.color.text_main));
        keyView.setTextSize(24);
        keyView.setTextIsSelectable(true);
        keyView.setGravity(android.view.Gravity.CENTER);
        keyView.setPadding(dp(16), dp(16), dp(16), dp(16));
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_recovery_key_title)
                .setMessage(R.string.webdav_recovery_key_message)
                .setView(keyView)
                .setPositiveButton(R.string.webdav_recovery_key_saved, (dialog, which) -> Toast.makeText(requireContext(), R.string.webdav_saved, Toast.LENGTH_SHORT).show())
                .setCancelable(false)
                .show();
    }

    private String generateRecoveryKey() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 16; i++) raw.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    private String normalizedRecoveryKey() {
        return readSecret(KEY_RECOVERY_KEY)
                .replace("-", "")
                .trim()
                .toUpperCase(Locale.US);
    }

    private String currentIndependentBackupPassword() {
        return SecuritySettings.getDataEncryptionKey(requireContext());
    }

    private void withBackupPassword(boolean automatic, BackupPasswordCallback callback) {
        String dataEncryptionKey = SecuritySettings.getDataEncryptionKey(requireContext());
        if (dataEncryptionKey != null && !dataEncryptionKey.isEmpty()) {
            callback.onPassword(dataEncryptionKey);
            return;
        }
        if (automatic) return;
        EditText input = createPasswordInput(getString(R.string.data_protection_key));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.data_protection_key_setup_title)
                .setMessage(R.string.data_protection_key_short_explanation)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String key = input.getText().toString();
            if (key.isEmpty()) {
                input.setError(getString(R.string.data_protection_key_required_short));
                return;
            }
            SecuritySettings.saveDataEncryptionKey(requireContext(), key);
            requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_BACKUP_INDEPENDENT, true)
                    .putBoolean(KEY_BACKUP_METHOD_SET, true)
                    .apply();
            dialog.dismiss();
            callback.onPassword(key);
        }));
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams topParams(int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private void maybeAutoSync() {
        if (autoSync.isChecked()) WebDavAutoSyncManager.syncOnAppOpen(requireContext());
    }

    private void showBackupTargetChooser() {
        savePrefs();
        List<WebDavTarget> all = configuredTargets(true);
        if (all.isEmpty()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_backup_target)
                .setItems(new String[]{getString(R.string.backup_target_main), getString(R.string.backup_target_backup), getString(R.string.backup_target_both)}, (dialog, which) -> {
                    syncTargetGroup.check(which == 0 ? R.id.rb_sync_target_main
                            : which == 1 ? R.id.rb_sync_target_backup : R.id.rb_sync_target_all);
                    savePrefs();
                    backupV5Now();
                })
                .show();
    }

    private void showRestoreTargetChooser() {
        savePrefs();
        List<WebDavTarget> targets = configuredTargets(true);
        if (targets.isEmpty()) return;
        if (targets.size() == 1) {
            restoreTarget(targets.get(0));
            return;
        }
        String[] labels = new String[targets.size()];
        for (int i = 0; i < targets.size(); i++) labels[i] = targets.get(i).label;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_choose_account)
                .setItems(labels, (dialog, which) -> restoreTarget(targets.get(which)))
                .show();
    }

    private void chooseLocalBackupLocation() {
        localBackupLocationPicker.launch(null);
    }

    private void backupToLocal() {
        if (localBackupTreeUri == null || localBackupTreeUri.isEmpty()) {
            localBackupPendingAfterPick = true;
            chooseLocalBackupLocation();
            return;
        }
        withBackupPassword(false, backupPassword -> executor.execute(() -> {
            try {
                BackupPayload payload = buildLocalBackupPayload();
                Uri tree = Uri.parse(localBackupTreeUri);
                if (!hasPersistedTreeWritePermission(tree)) {
                    throw new SecurityException(getString(R.string.backup_directory_permission_expired));
                }
                String name = "KS_" + new SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(new Date()) + ".dat";
                DocumentFile directory = DocumentFile.fromTreeUri(requireContext(), tree);
                if (directory == null || !directory.canWrite()) {
                    throw new IllegalStateException(getString(R.string.export_failed, "backup folder is unavailable"));
                }
                DocumentFile outputFile = directory.createFile("application/octet-stream", name);
                if (outputFile == null) throw new IllegalStateException(getString(R.string.export_failed, "create document failed"));
                Uri fileUri = outputFile.getUri();
                try (OutputStream os = requireContext().getContentResolver().openOutputStream(fileUri, "w")) {
                    if (os == null) throw new IllegalStateException(getString(R.string.export_failed, "open output failed"));
                    new AttachmentBackupCoordinator(requireContext()).write(os, payload, backupPassword);
                }
                long now = System.currentTimeMillis();
                requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putLong("last_local_backup", now)
                        .putString("last_local_backup_name", name)
                        .apply();
                localBackupLatestLabel = name + " \u00b7 " + formatLocalTime(now);
                runOnUi(() -> {
                    updateSectionSummaries();
                    if (localBackupHistoryContent.getVisibility() == View.VISIBLE) loadLocalBackupHistory();
                    Toast.makeText(requireContext(), R.string.local_backup_now, Toast.LENGTH_SHORT).show();
                });
            } catch (SecurityException e) {
                runOnUi(() -> Toast.makeText(requireContext(), getString(R.string.local_backup_failed_detail, e.getMessage()), Toast.LENGTH_LONG).show());
            } catch (java.io.FileNotFoundException e) {
                runOnUi(() -> Toast.makeText(requireContext(), R.string.local_backup_location_unavailable, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                String reason = e.getMessage() == null || e.getMessage().trim().isEmpty()
                        ? e.getClass().getSimpleName() : e.getMessage();
                runOnUi(() -> Toast.makeText(requireContext(), getString(R.string.local_backup_failed_detail, reason), Toast.LENGTH_LONG).show());
            }
        }));
    }

    private boolean hasPersistedTreeWritePermission(Uri treeUri) {
        if (treeUri == null) return false;
        for (android.content.UriPermission permission : requireContext().getContentResolver().getPersistedUriPermissions()) {
            if (treeUri.equals(permission.getUri()) && permission.isReadPermission() && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    private void loadLocalBackupHistory() {
        localBackupHistoryContent.removeAllViews();
        if (localBackupTreeUri == null || localBackupTreeUri.isEmpty()) {
            localBackupHistorySummary.setText(R.string.backup_history_no_folder);
            localBackupHistoryContent.addView(createNetworkText(getString(R.string.backup_choose_default_folder), 13, false));
            return;
        }
        Uri tree = Uri.parse(localBackupTreeUri);
        if (!hasPersistedTreeWritePermission(tree)) {
            localBackupHistorySummary.setText(R.string.backup_history_permission_expired);
            localBackupHistoryContent.addView(createNetworkText(getString(R.string.backup_folder_unreadable_help), 13, false));
            return;
        }
        executor.execute(() -> {
            List<DocumentFile> files = new ArrayList<>();
            DocumentFile directory = DocumentFile.fromTreeUri(requireContext(), tree);
            if (directory != null && directory.canRead()) {
                for (DocumentFile file : directory.listFiles()) {
                    String name = file.getName();
                    if (file.isFile() && name != null && name.startsWith("KS_") && name.toLowerCase(Locale.US).endsWith(".dat")) files.add(file);
                }
            }
            files.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            runOnUi(() -> renderLocalBackupHistory(files));
        });
    }

    private void renderLocalBackupHistory(List<DocumentFile> files) {
        localBackupHistoryContent.removeAllViews();
        localBackupHistorySummary.setText(getString(R.string.backup_history_count, files.size()));
        if (files.isEmpty()) {
            localBackupHistoryContent.addView(createNetworkText(getString(R.string.backup_history_empty_help), 13, false));
            return;
        }
        for (DocumentFile file : files) {
            LinearLayout row = horizontalRow(52);
            Button restore = new Button(requireContext());
            String time = file.lastModified() > 0 ? formatLocalTime(file.lastModified()) : getString(R.string.time_unknown);
            restore.setText(getString(R.string.backup_item_restore_hint, file.getName(), time, formatFileSize(file.length())));
            restore.setAllCaps(false);
            restore.setOnClickListener(v -> restoreFromLocalPickedFile(file.getUri()));
            row.addView(restore, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button delete = new Button(requireContext());
            delete.setText(R.string.delete);
            delete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.webdav_delete_backup_title)
                    .setMessage(R.string.webdav_delete_backup_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) -> executor.execute(() -> {
                        boolean removed = file.delete();
                        runOnUi(() -> {
                            Toast.makeText(requireContext(), removed ? R.string.webdav_backup_deleted : R.string.webdav_backup_delete_failed, Toast.LENGTH_SHORT).show();
                            loadLocalBackupHistory();
                        });
                    })).show());
            row.addView(delete, new LinearLayout.LayoutParams(dp(80), dp(44)));
            localBackupHistoryContent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private String buildLocalBackupJson() throws Exception {
        final String[] result = new String[1];
        final Exception[] error = new Exception[1];
        CountDownLatch latch = new CountDownLatch(1);
        backupCoordinator.createPayload(new BackupCoordinator.Callback() {
            @Override public void onSuccess(com.secureqr.scanner.backup.BackupPayload payload) {
                try {
                    result[0] = payload.toJson();
                } catch (Exception e) {
                    error[0] = e;
                } finally {
                    latch.countDown();
                }
            }

            @Override public void onFailure(Exception failure) {
                error[0] = failure;
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("local backup interrupted", e);
        }
        if (error[0] != null) throw error[0];
        if (result[0] == null) throw new IllegalStateException(getString(R.string.export_failed, "snapshot unavailable"));
        return result[0];
    }

    private BackupPayload buildLocalBackupPayload() throws Exception {
        final BackupPayload[] result = new BackupPayload[1];
        final Exception[] error = new Exception[1];
        CountDownLatch latch = new CountDownLatch(1);
        backupCoordinator.createPayload(new BackupCoordinator.Callback() {
            @Override public void onSuccess(BackupPayload payload) { result[0] = payload; latch.countDown(); }
            @Override public void onFailure(Exception failure) { error[0] = failure; latch.countDown(); }
        });
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("local backup interrupted", e); }
        if (error[0] != null) throw error[0];
        if (result[0] == null) throw new IllegalStateException(getString(R.string.export_failed, "snapshot unavailable"));
        return result[0];
    }

    private void restoreFromLocalPickedFile(@Nullable Uri uri) {
        if (uri == null) return;
        SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.backup_restore_auth_prompt), () -> {
            pendingLocalRestoreUri = uri;
            showLocalRestorePasswordDialog(uri);
        });
    }

    private void showLocalRestorePasswordDialog(Uri uri) {
        EditText input = createPasswordInput(getString(R.string.webdav_backup_password_required));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.local_backup_restore)
                .setMessage(R.string.webdav_backup_password_required)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = input.getText().toString().trim();
            if (password.isEmpty()) {
                input.setError(getString(R.string.webdav_backup_password_required));
                return;
            }
            dialog.dismiss();
            restoreFromLocal(uri, password);
        }));
        dialog.show();
    }

    private void restoreFromLocal(Uri uri, String password) {
        ProgressDialog dialog = ProgressDialog.show(requireContext(), getString(R.string.local_backup_restore), getString(R.string.webdav_restore_data_downloading), true, false);
        executor.execute(() -> {
            try {
                LocalBackupStreamSource source = new LocalBackupStreamSource(requireContext(), uri);
                if (!BackupPackageReader.isV5Container(source)) {
                    throw new IllegalStateException(getString(R.string.backup_v5_only));
                }
                BackupPayload payload = backupPackageReader.read(source, password);
                runOnUi(() -> {
                    dialog.dismiss();
                    showLocalRestorePreviewDialog(payload, uri, password);
                    /* Legacy v3 restore path is retained for WebDAV-only callers.
                        SecurityAuditLog.record(requireContext(), "本地备份恢复", true);
                        Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_SHORT).show();
                    */
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    SecurityAuditLog.record(requireContext(), "本地备份恢复", false);
                    Toast.makeText(requireContext(), restoreFailureMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void testTarget(WebDavTarget target) {
        savePrefs();
        if (target == null) return;
        executor.execute(() -> {
            boolean ok = target.client.testConnection();
            runOnUi(() -> {
                if (ok) {
                    if (target.isBackup) {
                        backupWebDavTested = true;
                        backupWebDavTestFingerprint = backupWebDavFingerprint();
                    } else {
                        mainWebDavTested = true;
                        mainWebDavTestFingerprint = mainWebDavFingerprint();
                    }
                }
                if (webdavConfigStatus != null) {
                    webdavConfigStatus.setText(ok ? R.string.configured : R.string.connection_abnormal);
                }
                if (!ok) expandOnly(SECTION_WEBDAV);
                Toast.makeText(requireContext(),
                        ok ? target.label + getString(R.string.webdav_connection_success) : getString(R.string.connection_abnormal),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void maybeLoadCloudBackups() {
        if (!webdavUrl.getText().toString().trim().isEmpty()
                && !webdavUser.getText().toString().trim().isEmpty()
                && !webdavPass.getText().toString().isEmpty()) {
            loadBackupHistoryForTarget(mainTarget(), false);
        } else if (!backupWebdavUrl.getText().toString().trim().isEmpty()
                && !backupWebdavUser.getText().toString().trim().isEmpty()
                && !backupWebdavPass.getText().toString().isEmpty()) {
            loadBackupHistoryForTarget(backupTarget(), false);
        }
    }

    private String createBackupEnvelope(String json, String backupPassword) throws Exception {
        String recoveryKey = normalizedRecoveryKey();
        if (recoveryKey.isEmpty()) {
            recoveryKey = normalizeRecoveryInput(generateRecoveryKey());
            writeSecret(KEY_RECOVERY_KEY, recoveryKey);
        }
        JSONObject envelope = new JSONObject();
        envelope.put("keyscanBackupVersion", 4);
        envelope.put("algorithm", "AES-GCM");
        envelope.put("payload", CryptoHelper.encrypt(json, backupPassword, "AES-GCM"));
        envelope.put("recovery", CryptoHelper.encrypt(backupPassword, recoveryKey, "AES-GCM"));
        return envelope.toString();
    }

    private String decryptBackupPayload(String encrypted, String backupPassword) throws Exception {
        String trimmed = encrypted == null ? "" : encrypted.trim();
        if (trimmed.startsWith("{")) {
            JSONObject envelope = new JSONObject(trimmed);
            if (envelope.optInt("keyscanBackupVersion") >= 4) {
                return CryptoHelper.decrypt(envelope.getString("payload"), backupPassword);
            }
        }
        return CryptoHelper.decrypt(trimmed, backupPassword);
    }

    private String restoreFailureMessage(Exception error) {
        String name = error == null ? "" : error.getClass().getSimpleName().toLowerCase(Locale.US);
        String message = error == null || error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
        if (message.contains("keyscan_backup_version_unsupported")) {
            return getString(R.string.backup_newer_version);
        }
        if (message.contains("legacy_backup_security_unsupported")) {
            return getString(R.string.backup_legacy_security_unsupported);
        }
        if (name.contains("aead") || message.contains("mac") || message.contains("tag")
                || message.contains("decrypt") || message.contains("badpadding")) {
            return getString(R.string.backup_wrong_key);
        }
        if (name.contains("json") || message.contains("json") || message.contains("base64")
                || message.contains("empty") || message.contains("文件")) {
            return getString(R.string.backup_incomplete);
        }
        return getString(R.string.security_credentials_mismatch);
    }

    private String backupPasswordFromRecovery(String encrypted, String recoveryKey) throws Exception {
        JSONObject envelope = new JSONObject(encrypted.trim());
        if (envelope.optInt("keyscanBackupVersion") < 4) {
            throw new IllegalStateException(getString(R.string.webdav_legacy_backup_unsupported));
        }
        return CryptoHelper.decrypt(envelope.getString("recovery"), normalizeRecoveryInput(recoveryKey));
    }

    private String normalizeRecoveryInput(String value) {
        return value == null ? "" : value.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.US);
    }

    private void checkLegacyBackupAvailability() {
        WebDavTarget target = mainTarget();
        if (target == null || convertLegacyButton == null) return;
        executor.execute(() -> {
            String encrypted = target.client.download(LATEST_BACKUP);
            boolean legacy = encrypted != null && !encrypted.trim().startsWith("{");
            runOnUi(() -> convertLegacyButton.setVisibility(legacy ? View.VISIBLE : View.GONE));
        });
    }

    private void convertLegacyBackup(WebDavTarget target) {
        savePrefs();
        if (target == null) return;
        withBackupPassword(false, backupPassword -> convertLegacyBackup(target, backupPassword));
    }

    private void convertLegacyBackup(WebDavTarget target, String backupPassword) {
        ProgressDialog dialog = ProgressDialog.show(requireContext(), getString(R.string.webdav_convert_legacy_title), getString(R.string.webdav_convert_legacy_progress), true, false);
        executor.execute(() -> {
            try {
                String encrypted = target.client.download(LATEST_BACKUP);
                if (encrypted == null || encrypted.isEmpty()) throw new IllegalStateException(getString(R.string.webdav_backup_file_empty));
                String json = CryptoHelper.decrypt(encrypted, backupPassword);
                String converted = createBackupEnvelope(json, backupPassword);
                boolean ok = target.client.upload(LATEST_BACKUP, converted);
                runOnUi(() -> {
                    dialog.dismiss();
                    convertLegacyButton.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), ok ? R.string.webdav_legacy_backup_converted : R.string.webdav_legacy_backup_upload_failed, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void syncTargets(List<WebDavTarget> targets, boolean automatic) {
        savePrefs();
        List<WebDavTarget> valid = new ArrayList<>();
        for (WebDavTarget target : targets) if (target != null) valid.add(target);
        if (valid.isEmpty()) {
            Toast.makeText(requireContext(), R.string.webdav_complete_config_required, Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(requireContext(),
                automatic ? getString(R.string.webdav_auto_sync) : getString(R.string.sync_now),
                getString(R.string.webdav_encrypting_uploading), true, false);
        WebDavAutoSyncManager.requestManualSync(requireContext(), "", (successCount, targetCount, error) ->
                runOnUi(() -> {
                    dialog.dismiss();
                    if (error == null) {
                        long now = System.currentTimeMillis();
                        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putLong("last_sync", now)
                                .apply();
                        updateLastSyncText(now);
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.webdav_sync_failed, error), Toast.LENGTH_SHORT).show();
                    }
                    updateSectionSummaries();
                    if (mainBackupUi != null) refreshTargetSummary(mainBackupUi);
                    if (secondaryBackupUi != null) refreshTargetSummary(secondaryBackupUi);
                }));
    }

    private void syncTargets(List<WebDavTarget> valid, boolean automatic, String backupPassword) {
        ProgressDialog dialog = ProgressDialog.show(requireContext(), automatic ? getString(R.string.webdav_auto_sync) : getString(R.string.sync_now), getString(R.string.webdav_encrypting_uploading), true, false);
        executor.execute(() -> {
            try {
                JSONObject remoteObject = downloadFirstAvailableObject(valid, backupPassword);
                if (remoteObject == null) {
                    uploadCurrentSnapshot(valid, dialog, backupPassword);
                } else {
                    mergeRemoteObject(remoteObject,
                            () -> uploadCurrentSnapshot(valid, dialog, backupPassword),
                            () -> runOnUi(dialog::dismiss));
                }
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.webdav_sync_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private JSONObject downloadFirstAvailableObject(List<WebDavTarget> targets, String backupPassword) throws Exception {
        for (WebDavTarget target : targets) {
            String encrypted = target.client.download(LATEST_BACKUP);
            if (encrypted != null && !encrypted.trim().isEmpty()) {
                return backupObjectFromJson(decryptBackupPayload(encrypted, backupPassword));
            }
        }
        return null;
    }

    private JSONObject backupObjectFromJson(String json) throws Exception {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("{")) return new JSONObject(trimmed);
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("records", new JSONArray(trimmed));
        root.put("passwords", new JSONArray());
        root.put("otpTokens", new JSONArray());
        return root;
    }

    private void uploadCurrentSnapshot(List<WebDavTarget> targets, ProgressDialog dialog, String backupPassword) {
        repository.getSyncRecords(records -> passwordRepository.getAll(passwords -> otpRepository.getAll(otpTokens -> executor.execute(() -> {
            int success = 0;
            List<String> results = new ArrayList<>();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            for (WebDavTarget target : targets) {
                try {
                    String json = toSyncJson(records, passwords, otpTokens, target.contentMode);
                    String encrypted = createBackupEnvelope(json, backupPassword);
                    boolean latestUploaded = target.client.upload(LATEST_BACKUP, encrypted);
                    boolean historyUploaded = latestUploaded && target.client.upload("/keybackup_" + stamp + ".dat", encrypted);
                    if (latestUploaded && historyUploaded) {
                        success++;
                        results.add(getString(R.string.target_sync_success, target.label));
                        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putLong(target.isBackup ? "last_sync_backup" : "last_sync_main", System.currentTimeMillis())
                                .apply();
                    } else {
                        results.add(getString(R.string.target_sync_failed, target.label));
                    }
                } catch (Exception targetError) {
                    results.add(getString(R.string.target_sync_failed_reason, target.label, WebDAVClient.shortNetworkReason(requireContext(), targetError)));
                }
            }
            int finalSuccess = success;
            runOnUi(() -> {
                dialog.dismiss();
                if (finalSuccess > 0) {
                    long now = System.currentTimeMillis();
                    requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_sync", now).apply();
                    updateLastSyncText(now);
                }
                updateSectionSummaries();
                if (mainBackupUi != null) refreshTargetSummary(mainBackupUi);
                if (secondaryBackupUi != null) refreshTargetSummary(secondaryBackupUi);
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.sync_now)
                        .setMessage(android.text.TextUtils.join("\n", results))
                        .setPositiveButton(R.string.confirm, null)
                        .show();
            });
        }))));
    }

    private void backupV5Now() {
        VaultAccessManager.requireUnlocked(requireActivity(),
                getString(R.string.backup_unlock_prompt), this::backupV5NowUnlocked);
    }

    private void backupV5NowUnlocked() {
        savePrefs();
        if (selectedTargets(true).isEmpty()) return;
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.sync_now),
                getString(R.string.webdav_encrypting_uploading), true, false);
        WebDavAutoSyncManager.requestBackupNow(requireContext(), (success, total, error) -> runOnUi(() -> {
            progress.dismiss();
            if (success > 0) {
                Toast.makeText(requireContext(), R.string.backup_complete_title, Toast.LENGTH_SHORT).show();
                if (mainBackupUi != null) refreshTargetSummary(mainBackupUi);
                if (secondaryBackupUi != null) refreshTargetSummary(secondaryBackupUi);
            } else {
                Toast.makeText(requireContext(), error == null ? getString(R.string.connection_failed) : error, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void restoreTarget(WebDavTarget target) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> restoreTarget(target));
            return;
        }
        savePrefs();
        if (target == null) return;
        showRestorePasswordDialog(target, LATEST_BACKUP);
    }

    private void restoreTargetWithPassword(WebDavTarget target, String remotePath, String password) {
        ProgressDialog dialog = ProgressDialog.show(requireContext(), getString(R.string.webdav_restore_data_title), getString(R.string.webdav_restore_data_downloading), true, false);
        executor.execute(() -> {
            try {
                String encrypted = target.client.download(remotePath);
                if (encrypted == null || encrypted.isEmpty()) throw new IllegalStateException(getString(R.string.webdav_backup_file_empty));
                JSONObject object = new JSONObject(decryptBackupPayload(encrypted, password));
                validateBackupVersion(object);
                runOnUi(() -> {
                    dialog.dismiss();
                    showRestorePreviewDialog(object, () -> mergeRestoredObject(object, () -> runOnUi(() -> {
                        SecuritySettings.saveDataEncryptionKey(requireContext(), password);
                        if (backupEncryptionPassword != null) backupEncryptionPassword.setText("");
                        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putBoolean(KEY_BACKUP_INDEPENDENT, true)
                                .putBoolean(KEY_BACKUP_METHOD_SET, true)
                                .apply();
                        savePrefs();
                        SecurityAuditLog.record(requireContext(), "WebDAV 备份恢复", true);
                        Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_SHORT).show();
                        promptSetLocalUnlockPasswordIfNeeded();
                    })));
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    SecurityAuditLog.record(requireContext(), "WebDAV 备份恢复", false);
                    showRestoreFailedDialog(target, remotePath, restoreFailureMessage(e));
                });
            }
        });
    }

    private void showRestorePasswordDialog(WebDavTarget target, String remotePath) {
        EditText input = createPasswordInput(getString(R.string.webdav_backup_password_required));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_restore_data_title)
                .setMessage(R.string.otp_export_password_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.webdav_restore_button, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = input.getText().toString();
                if (password.isEmpty()) {
                    input.setError(getString(R.string.webdav_backup_password_required));
                    return;
                }
                dialog.dismiss();
                restoreSelectedV5Backup(target, remotePath, password);
            });
        });
        dialog.show();
    }

    private void restoreSelectedV5Backup(WebDavTarget target, String remotePath, String dataProtectionKey) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this,
                    () -> restoreSelectedV5Backup(target, remotePath, dataProtectionKey));
            return;
        }
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.webdav_restore_data_title),
                getString(R.string.webdav_restore_data_downloading), true, false);
        executor.execute(() -> {
            try {
                WebDavBackupStreamSource source = new WebDavBackupStreamSource(target.client, remotePath);
                if (!BackupPackageReader.isV5Container(source)) throw new IllegalStateException(getString(R.string.backup_v5_only));
                BackupPayload payload = backupPackageReader.read(source, dataProtectionKey);
                runOnUi(() -> {
                    progress.dismiss();
                    showWebDavRestorePreviewDialog(payload, target, remotePath, dataProtectionKey);
                });
            } catch (Exception error) {
                runOnUi(() -> {
                    progress.dismiss();
                    Toast.makeText(requireContext(), restoreFailureMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showWebDavRestorePreviewDialog(BackupPayload payload, WebDavTarget target,
                                                 String remotePath, String dataProtectionKey) {
        String message = getString(R.string.backup_restore_verified_message, payload.version,
                payload.passwords.size(), payload.otpTokens.size(), payload.vaultItems.size(), payload.attachments.size());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_restore_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.webdav_restore_button, (dialog, which) -> {
                    // A freshly installed app can validate/decrypt the backup while its
                    // local vault session is still locked. Authenticate first, then allow
                    // BackupRestoreManager to merge the records and attachments.
                    VaultAccessManager.requireUnlocked(requireActivity(),
                            getString(R.string.backup_restore_auth_prompt), () -> {
                                WebDavBackupStreamSource source = new WebDavBackupStreamSource(target.client, remotePath);
                                backupRestoreManager.restore(payload, source, dataProtectionKey, new BackupRestoreManager.Callback() {
                                    @Override public void onComplete(BackupRestoreResult result) {
                                        runOnUi(() -> {
                                            SecuritySettings.saveDataEncryptionKey(requireContext(), dataProtectionKey);
                                            requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                                    .putBoolean("remote_backup_restored", true).apply();
                                            Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_LONG).show();
                                            updateSectionSummaries();
                                        });
                                    }

                                    @Override public void onFailure(Exception error) {
                                        runOnUi(() -> Toast.makeText(requireContext(), restoreFailureMessage(error), Toast.LENGTH_LONG).show());
                                    }
                                });
                            });
                }).show();
    }

    private void restoreCurrentV5Backup(String dataProtectionKey) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> restoreCurrentV5Backup(dataProtectionKey));
            return;
        }
        ProgressDialog progress = ProgressDialog.show(requireContext(), getString(R.string.webdav_restore_data_title),
                getString(R.string.webdav_restore_data_downloading), true, false);
        WebDavAutoSyncManager.restoreLatestBackup(requireContext(), dataProtectionKey, (preview, error) -> runOnUi(() -> {
            progress.dismiss();
            if (error != null || preview == null) {
                Toast.makeText(requireContext(), error == null ? getString(R.string.backup_read_failed) : error, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_LONG).show();
            updateSectionSummaries();
        }));
    }

    private void showRestoreFailedDialog(WebDavTarget target, String remotePath, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_restore_failed_title)
                .setMessage(message)
                .setPositiveButton(R.string.retry, (dialog, which) -> showRestorePasswordDialog(target, remotePath))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showRecoveryKeyResetDialog(WebDavTarget target, String remotePath) {
        EditText recoveryInput = createPasswordInput(getString(R.string.webdav_recovery_key_input_hint));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_recovery_key_reset_title)
                .setMessage(R.string.webdav_recovery_key_reset_message)
                .setView(recoveryInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String recoveryKey = normalizeRecoveryInput(recoveryInput.getText().toString());
            if (recoveryKey.length() != 16) {
                recoveryInput.setError(getString(R.string.webdav_recovery_key_reset_error));
                return;
            }
            dialog.dismiss();
            recoverWithRecoveryKey(target, remotePath, recoveryKey);
        }));
        dialog.show();
    }

    private void recoverWithRecoveryKey(WebDavTarget target, String remotePath, String recoveryKey) {
        ProgressDialog dialog = ProgressDialog.show(requireContext(), getString(R.string.webdav_restore_data_title), getString(R.string.webdav_restore_data_using_key), true, false);
        executor.execute(() -> {
            try {
                String encrypted = target.client.download(remotePath);
                if (encrypted == null || encrypted.isEmpty()) throw new IllegalStateException(getString(R.string.webdav_backup_file_empty));
                String oldBackupPassword = backupPasswordFromRecovery(encrypted, recoveryKey);
                JSONObject object = new JSONObject(decryptBackupPayload(encrypted, oldBackupPassword));
                runOnUi(() -> {
                    dialog.dismiss();
                    showNewBackupPasswordDialog(target, object);
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(), R.string.webdav_recovery_key_validation_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showNewBackupPasswordDialog(WebDavTarget target, JSONObject restoredObject) {
        EditText input = createPasswordInput(getString(R.string.webdav_backup_password_required));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_recovery_key_reset_title)
                .setMessage(R.string.webdav_recovery_key_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newPassword = input.getText().toString();
            if (newPassword.isEmpty()) {
                input.setError(getString(R.string.webdav_backup_password_required));
                return;
            }
            SecuritySettings.saveDataEncryptionKey(requireContext(), newPassword);
            backupEncryptionPassword.setText("");
            setBackupEncryptionMode(true);
            requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_BACKUP_INDEPENDENT, true)
                    .putBoolean(KEY_BACKUP_METHOD_SET, true)
                    .apply();
            savePrefs();
            dialog.dismiss();
            mergeRestoredObject(restoredObject, () -> {
                reuploadRestoredBackup(target, restoredObject, newPassword);
                runOnUi(this::promptSetLocalUnlockPasswordIfNeeded);
            });
        }));
        dialog.show();
    }

    private void mergeRestoredObject(JSONObject object, Runnable done) {
        mergeRemoteObject(object, done, null);
    }

    private void validateBackupVersion(JSONObject object) {
        if (object != null && object.optInt("version", 1) > 4) {
            throw new IllegalStateException("KEYSCAN_BACKUP_VERSION_UNSUPPORTED");
        }
    }

    private void showRestorePreviewDialog(JSONObject object, Runnable onConfirm) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_restore_confirm_title)
                .setMessage(backupSummaryText(object))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.webdav_restore_button, (dialog, which) -> onConfirm.run())
                .show();
    }

    private void showLocalRestorePreviewDialog(BackupPayload payload, Uri backupUri, String dataProtectionKey) {
        String restoreMessage = getString(R.string.backup_restore_verified_message, payload.version,
                payload.passwords.size(), payload.otpTokens.size(), payload.vaultItems.size(), payload.attachments.size());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_restore_confirm_title)
                .setMessage(restoreMessage)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.webdav_restore_button, (dialog, which) ->
                        VaultAccessManager.requireUnlocked(requireActivity(),
                                getString(R.string.backup_restore_auth_prompt), () ->
                        backupRestoreManager.restore(payload, new LocalBackupStreamSource(requireContext(), backupUri), dataProtectionKey, new BackupRestoreManager.Callback() {
                            @Override public void onComplete(BackupRestoreResult result) {
                                runOnUi(() -> {
                                    if (result.attachmentCount > 0) {
                                        String detail = getString(R.string.backup_attachment_restore_result,
                                                result.attachmentSuccess, result.attachmentSkipped, result.attachmentFailed);
                                        SecurityAuditLog.record(requireContext(), "本地备份恢复", result.attachmentFailed == 0);
                                        Toast.makeText(requireContext(), getString(R.string.backup_restore_completed_with_detail, detail), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    String suffix = result.attachmentCount > 0
                                            ? getString(R.string.backup_attachments_not_restored, result.attachmentCount) : "";
                                    SecurityAuditLog.record(requireContext(), "本地备份恢复", true);
                                    Toast.makeText(requireContext(), getString(R.string.backup_restore_completed, suffix), Toast.LENGTH_LONG).show();
                                });
                            }

                            @Override public void onFailure(Exception error) {
                                runOnUi(() -> {
                                    SecurityAuditLog.record(requireContext(), "本地备份恢复", false);
                                    Toast.makeText(requireContext(), restoreFailureMessage(error), Toast.LENGTH_LONG).show();
                                });
                            }
                        }))
                )
                .show();
    }

    private String backupSummaryText(JSONObject object) {
        int version = object.optInt("version", 1);
        return getString(R.string.backup_restore_legacy_summary,
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()), version,
                jsonCount(object, "records"), jsonCount(object, "passwords"), jsonCount(object, "otpTokens"),
                jsonCount(object, "vaultItems"), jsonCount(object, "vaultAttachments"));
    }

    private int jsonCount(JSONObject object, String key) {
        JSONArray array = object.optJSONArray(key);
        return array == null ? 0 : array.length();
    }

    private void mergeRemoteObject(JSONObject object, Runnable done, @Nullable Runnable onError) {
        try {
            List<ScanRecord> records = parseRecords(object.optJSONArray("records"));
            List<PasswordGroup> groups = parsePasswordGroups(object.optJSONArray("passwordGroups"));
            List<PasswordEntry> passwords = parsePasswords(object.optJSONArray("passwords"));
            List<OtpToken> otpTokens = parseOtpTokens(object.optJSONArray("otpTokens"));
            repository.mergeRecords(records, () -> passwordRepository.mergeGroups(groups, () -> passwordRepository.mergeEntries(passwords, () -> otpRepository.mergeTokens(otpTokens, done))));
        } catch (Exception e) {
            if (onError != null) onError.run();
            runOnUi(() -> Toast.makeText(requireContext(), restoreFailureMessage(e), Toast.LENGTH_LONG).show());
        }
    }

    private void reuploadRestoredBackup(WebDavTarget target, JSONObject restoredObject, String newPassword) {
        executor.execute(() -> {
            try {
                String encrypted = createBackupEnvelope(restoredObject.toString(), newPassword);
                target.client.upload(LATEST_BACKUP, encrypted);
            } catch (Exception ignored) {
            }
        });
    }

    private void promptSetLocalUnlockPasswordIfNeeded() {
        if (PinLockHelper.isConfigured(requireContext())) {
            Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_SHORT).show();
            return;
        }
        promptSetLocalUnlockPassword();
    }

    private void promptSetLocalUnlockPassword() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText passwordInput = createPasswordInput(getString(R.string.password_ledger_unlock_hint));
        Spinner questionSpinner = new Spinner(requireContext());
        questionSpinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, PinLockHelper.securityQuestions(requireContext())));
        EditText answerInput = createPasswordInput(getString(R.string.password_ledger_answer_hint));
        content.addView(passwordInput);
        content.addView(questionSpinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        content.addView(answerInput);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.password_ledger_setup_title)
                .setMessage(R.string.password_ledger_setup_message)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            String answer = answerInput.getText().toString().trim();
            if (!PinLockHelper.isValidPin(password)) {
                passwordInput.setError(getString(R.string.password_ledger_input_error));
                return;
            }
            if (answer.isEmpty()) {
                answerInput.setError(getString(R.string.password_ledger_security_answer_empty));
                return;
            }
            PinLockHelper.saveCredentials(requireContext(), password, "", questionSpinner.getSelectedItem().toString(), answer);
            dialog.dismiss();
            Toast.makeText(requireContext(), R.string.webdav_restore_done, Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void loadBackupHistory(boolean showActions) {
        savePrefs();
        List<WebDavTarget> targets = configuredTargets(true);
        if (targets.isEmpty()) return;
        if (targets.size() > 1 && showActions) {
            showTargetPicker(targets);
            return;
        }
        loadBackupHistoryForTarget(targets.get(0), showActions);
    }

    private void showTargetPicker(List<WebDavTarget> targets) {
        String[] labels = new String[targets.size()];
        for (int i = 0; i < targets.size(); i++) labels[i] = targets.get(i).label;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_choose_account)
                .setItems(labels, (dialog, which) -> loadBackupHistoryForTarget(targets.get(which), true))
                .show();
    }

    private void loadBackupHistoryForTarget(WebDavTarget target, boolean showActions) {
        backupHistory.setText(getString(R.string.webdav_cloud_scanning, target.label));
        cloudBackups.removeAllViews();
        executor.execute(() -> {
            List<WebDAVClient.BackupFile> backups = target.client.listBackupFiles();
            runOnUi(() -> {
                if (backups.isEmpty()) {
                    backupHistory.setText(getString(R.string.webdav_cloud_no_files, target.label));
                    if (backupDataStatus != null) backupDataStatus.setText(R.string.backup_none);
                    renderCloudBackupEmptyState(target);
                    return;
                }
                backupHistory.setText(getString(R.string.webdav_cloud_file_count, target.label, backups.size()));
                if (backupDataStatus != null) {
                    backupDataStatus.setText(getString(R.string.backup_last_time, formatBackupTime(backups.get(0))));
                }
                renderCloudBackups(target, backups);
            });
        });
    }

    private void showBackupPicker(WebDAVClient client, List<String> backups) {
        String[] labels = new String[backups.size()];
        for (int i = 0; i < backups.size(); i++) labels[i] = backups.get(i).substring(1);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_cloud_backup_title)
                .setItems(labels, (dialog, which) -> showBackupActions(client, backups.get(which)))
                .show();
    }

    private void showBackupActions(WebDAVClient client, String remotePath) {
        new AlertDialog.Builder(requireContext())
                .setTitle(remotePath.substring(1))
                .setItems(new String[]{getString(R.string.restore_this_backup), getString(R.string.delete_this_backup)}, (dialog, which) -> {
                    if (which == 0) confirmRestoreBackup(new WebDavTarget(getString(R.string.history_backup), client, "all"), remotePath);
                    else confirmDeleteBackup(new WebDavTarget(getString(R.string.history_backup), client, "all"), remotePath);
                })
                .show();
    }

    private void restoreBackup(WebDAVClient client, String remotePath) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> restoreBackup(client, remotePath));
            return;
        }
        showRestorePasswordDialog(new WebDavTarget(getString(R.string.history_backup), client, "all"), remotePath);
    }

    private void renderCloudBackupEmptyState(WebDavTarget target) {
        cloudBackups.removeAllViews();
        LinearLayout row = createBackupRowContainer();
        row.setOrientation(LinearLayout.VERTICAL);
        TextView empty = createBackupText(getString(R.string.webdav_backup_empty), 14, false, R.color.text_secondary);
        Button refresh = new Button(requireContext());
        refresh.setText(R.string.webdav_sync_now_button);
        refresh.setOnClickListener(v -> backupV5Now());
        row.addView(empty);
        row.addView(refresh, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        cloudBackups.addView(row);
    }

    private void renderCloudBackups(WebDavTarget target, List<WebDAVClient.BackupFile> backups) {
        cloudBackups.removeAllViews();
        List<WebDAVClient.BackupFile> backupSnapshot = new ArrayList<>(backups);
        currentCloudBackupFiles.clear();
        currentCloudBackupFiles.addAll(backupSnapshot);
        selectedBackupPaths.removeIf(path -> !containsBackupPath(backupSnapshot, path));

        Button deleteSelected = new Button(requireContext());
        deleteSelected.setText(selectedBackupPaths.isEmpty()
                ? getString(R.string.delete_selected)
                : getString(R.string.delete) + "(" + selectedBackupPaths.size() + ")");
        deleteSelected.setEnabled(!selectedBackupPaths.isEmpty());
        deleteSelected.setOnClickListener(v -> confirmDeleteSelectedBackups(target));
        cloudBackups.addView(deleteSelected, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        Map<String, List<WebDAVClient.BackupFile>> groups = new LinkedHashMap<>();
        for (WebDAVClient.BackupFile file : backupSnapshot) {
            String date = backupDateGroup(file);
            if (!groups.containsKey(date)) groups.put(date, new ArrayList<>());
            groups.get(date).add(file);
        }
        for (Map.Entry<String, List<WebDAVClient.BackupFile>> group : groups.entrySet()) {
            cloudBackups.addView(createBackupGroupHeader(target, group.getKey(), group.getValue()));
            if (expandedBackupDates.contains(group.getKey())) {
                for (WebDAVClient.BackupFile file : group.getValue()) {
                    cloudBackups.addView(createBackupFileRow(target, file, true));
                }
            }
        }
        updateSectionSummaries();
    }

    private View createBackupGroupHeader(WebDavTarget target, String date, List<WebDAVClient.BackupFile> files) {
        LinearLayout row = createBackupRowContainer();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        CheckBox checkBox = new CheckBox(requireContext());
        checkBox.setChecked(allBackupsSelected(files));
        checkBox.setOnClickListener(v -> {
            boolean checked = ((CheckBox) v).isChecked();
            for (WebDAVClient.BackupFile file : files) {
                if (checked) selectedBackupPaths.add(file.path);
                else selectedBackupPaths.remove(file.path);
            }
            renderCloudBackups(target, currentCloudBackupFiles);
        });
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = createBackupText(date + " (" + files.size() + ")", 15, true, R.color.text_main);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = createBackupText(expandedBackupDates.contains(date) ? "\u25bc" : "\u25b6", 18, true, R.color.text_secondary);
        row.addView(arrow);
        row.setOnClickListener(v -> {
            if (expandedBackupDates.contains(date)) expandedBackupDates.remove(date);
            else expandedBackupDates.add(date);
            renderCloudBackups(target, currentCloudBackupFiles);
        });
        row.setTag(files);
        return row;
    }

    private View createBackupFileRow(WebDavTarget target, WebDAVClient.BackupFile file, boolean selectable) {
        LinearLayout row = createBackupRowContainer();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        if (selectable) {
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setChecked(selectedBackupPaths.contains(file.path));
            checkBox.setOnClickListener(v -> {
                if (((CheckBox) v).isChecked()) selectedBackupPaths.add(file.path);
                else selectedBackupPaths.remove(file.path);
                renderCloudBackups(target, currentCloudBackupFiles);
            });
            row.addView(checkBox, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }

        LinearLayout left = new LinearLayout(requireContext());
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(createBackupText(file.name, 14, true, R.color.text_main));
        left.addView(createBackupText(formatBackupTime(file), 12, false, R.color.text_secondary));

        LinearLayout right = new LinearLayout(requireContext());
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(android.view.Gravity.END);
        right.addView(createBackupText(formatFileSize(file.size), 12, false, R.color.text_secondary));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.END);
        Button restore = new Button(requireContext());
        restore.setText(R.string.webdav_restore_button);
        restore.setOnClickListener(v -> confirmRestoreBackup(target, file.path));
        Button delete = new Button(requireContext());
        delete.setText(R.string.webdav_delete_backup_button);
        delete.setOnClickListener(v -> confirmDeleteBackup(target, file.path));
        actions.addView(restore, new LinearLayout.LayoutParams(dp(72), dp(40)));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(72), dp(40));
        deleteParams.leftMargin = dp(6);
        actions.addView(delete, deleteParams);
        right.addView(actions);

        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(right, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setTag(file);
        return row;
    }

    private boolean containsBackupPath(List<WebDAVClient.BackupFile> backups, String path) {
        for (WebDAVClient.BackupFile file : backups) {
            if (path.equals(file.path)) return true;
        }
        return false;
    }

    private boolean allBackupsSelected(List<WebDAVClient.BackupFile> files) {
        if (files.isEmpty()) return false;
        for (WebDAVClient.BackupFile file : files) {
            if (!selectedBackupPaths.contains(file.path)) return false;
        }
        return true;
    }

    private String backupDateGroup(WebDAVClient.BackupFile file) {
        String formatted = formatBackupTime(file);
        if (formatted != null && formatted.length() >= 10 && formatted.charAt(4) == '-') {
            return formatted.substring(0, 10);
        }
        return "";
    }

    private LinearLayout createBackupRowContainer() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setBackgroundResource(R.drawable.bg_card);
        row.setElevation(dp(2));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private TextView createBackupText(String text, int sp, boolean bold, int colorRes) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(getResources().getColor(colorRes));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private void confirmRestoreBackup(WebDavTarget target, String remotePath) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> confirmRestoreBackup(target, remotePath));
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_restore_backup_title)
                .setMessage(R.string.webdav_restore_backup_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> showRestorePasswordDialog(target, remotePath))
                .show();
    }

    private void confirmDeleteBackup(WebDavTarget target, String remotePath) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> confirmDeleteBackup(target, remotePath));
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_delete_backup_title)
                .setMessage(R.string.webdav_delete_backup_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> deleteBackup(target, remotePath))
                .show();
    }

    private void confirmDeleteSelectedBackups(WebDavTarget target) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> confirmDeleteSelectedBackups(target));
            return;
        }
        if (selectedBackupPaths.isEmpty()) return;
        int count = selectedBackupPaths.size();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.webdav_delete_backup_title)
                .setMessage(getString(R.string.backup_delete_selected_message, count))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> deleteSelectedBackups(target, new ArrayList<>(selectedBackupPaths)))
                .show();
    }

    private String formatBackupTime(WebDAVClient.BackupFile file) {
        String name = file.name;
        String stamp = "";
        if (name.startsWith("keybackup_")) {
            stamp = name.substring("keybackup_".length(), name.lastIndexOf('.'));
        } else if (name.startsWith("secure_backup_")) {
            stamp = name.substring("secure_backup_".length(), name.lastIndexOf('.'));
        } else if (name.matches("[A-Za-z0-9_-]{1,32}_[0-9]{8}_[0-9]{6}\\.dat")) {
            stamp = name.substring(name.length() - 19, name.length() - 4);
        }
        if (!stamp.isEmpty()) {
            try {
                Date date = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(stamp);
                return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date);
            } catch (Exception ignored) {
            }
        }
        String httpDate = parseHttpDate(file.lastModified);
        if (httpDate != null) return httpDate;
        return getString(R.string.network_backup_detected);
    }

    private String latestCloudBackupTimeLabel(List<WebDAVClient.BackupFile> files) {
        for (WebDAVClient.BackupFile file : files) {
            String label = formatBackupTime(file);
            if (label != null && !label.equals(getString(R.string.network_backup_detected))) {
                return label;
            }
        }
        return null;
    }

    private String formatLocalTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
    }

    private String parseHttpDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            Date date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(raw.trim());
            if (date == null) return null;
            return formatLocalTime(date.getTime());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return getString(R.string.webdav_size_unknown);
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb);
        return String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0);
    }

    private void deleteBackup(WebDavTarget target, String remotePath) {
        executor.execute(() -> {
            boolean ok = target.client.delete(remotePath);
            runOnUi(() -> {
                Toast.makeText(requireContext(), ok ? R.string.webdav_backup_deleted : R.string.webdav_backup_delete_failed, Toast.LENGTH_SHORT).show();
                if (ok) {
                    loadBackupHistoryForTarget(target, true);
                }
            });
        });
    }

    private void deleteSelectedBackups(WebDavTarget target, List<String> remotePaths) {
        executor.execute(() -> {
            int success = 0;
            for (String path : remotePaths) {
                if (target.client.delete(path)) success++;
            }
            int finalSuccess = success;
            runOnUi(() -> {
                selectedBackupPaths.clear();
                Toast.makeText(requireContext(), finalSuccess == remotePaths.size()
                        ? R.string.webdav_backup_deleted
                        : R.string.webdav_backup_delete_failed, Toast.LENGTH_SHORT).show();
                loadBackupHistoryForTarget(target, true);
            });
        });
    }

    private List<WebDavTarget> configuredTargets(boolean showToast) {
        WebDavTarget main = mainTarget();
        WebDavTarget backup = backupTarget();
        List<WebDavTarget> targets = new ArrayList<>();
        if (main != null) targets.add(main);
        if (backup != null) targets.add(backup);
        if (targets.isEmpty() && showToast) {
            Toast.makeText(requireContext(), R.string.webdav_complete_config_required, Toast.LENGTH_SHORT).show();
        }
        return targets;
    }

    private String selectedTargetKey() {
        int checked = syncTargetGroup == null ? R.id.rb_sync_target_all : syncTargetGroup.getCheckedRadioButtonId();
        if (checked == R.id.rb_sync_target_main) return "main";
        if (checked == R.id.rb_sync_target_backup) return "backup";
        return "all";
    }

    private List<WebDavTarget> selectedTargets(boolean showToast) {
        String selected = selectedTargetKey();
        List<WebDavTarget> targets = new ArrayList<>();
        WebDavTarget main = mainTarget();
        WebDavTarget backup = backupTarget();
        if (("main".equals(selected) || "all".equals(selected)) && main != null) targets.add(main);
        if (("backup".equals(selected) || "all".equals(selected)) && backup != null) targets.add(backup);
        if (targets.isEmpty() && showToast) {
            Toast.makeText(requireContext(), R.string.webdav_complete_config_required, Toast.LENGTH_SHORT).show();
        }
        return targets;
    }

    private WebDavTarget targetFor(boolean backup) {
        if (backup) {
            return isTargetInputComplete(backupWebdavUrl, backupWebdavUser, backupWebdavPass) ? backupTarget() : null;
        }
        return isTargetInputComplete(webdavUrl, webdavUser, webdavPass) ? mainTarget() : null;
    }

    private void refreshTargetSummary(TargetBackupUi ui) {
        WebDavTarget target = targetFor(ui.backup);
        if (target == null) {
            ui.latest = null;
            ui.latestName.setText(R.string.backup_none);
            ui.latestTime.setText("");
            ui.summary.setText(R.string.backup_latest_none);
            ui.restore.setEnabled(false);
            ui.historyRoot.setEnabled(false);
            ui.historyRoot.setAlpha(0.5f);
            return;
        }
        ui.historyRoot.setEnabled(true);
        ui.historyRoot.setAlpha(1f);
        ui.latestName.setText(R.string.backup_reading);
        ui.latestTime.setText("");
        executor.execute(() -> {
            List<WebDAVClient.BackupFile> files = target.client.listBackupFiles();
            WebDAVClient.BackupFile latest = findLatestBackup(files);
            List<WebDAVClient.BackupFile> history = historyOnly(files, latest);
            runOnUi(() -> {
                ui.allFiles.clear();
                ui.allFiles.addAll(files);
                ui.latest = latest;
                ui.historyCount = history.size();
                ui.historySummary.setText(getString(R.string.backup_history_count, history.size()));
                if (latest == null) {
                    ui.latestName.setText(R.string.backup_none);
                    ui.latestTime.setText("");
                    ui.summary.setText(R.string.backup_latest_none);
                    ui.restore.setEnabled(false);
                } else {
                    String time = formatBackupTime(latest);
                    ui.latestName.setText(latest.name);
                    ui.latestTime.setText(time);
                    ui.summary.setText(getString(R.string.backup_latest_value, latest.name,
                            time.isEmpty() ? "" : getString(R.string.backup_time_suffix, time)));
                    ui.restore.setEnabled(true);
                }
            });
        });
    }

    private WebDAVClient.BackupFile findLatestBackup(List<WebDAVClient.BackupFile> files) {
        if (files == null) return null;
        String preferred = "/" + normalizedBackupPrefix() + "_latest.dat";
        for (WebDAVClient.BackupFile file : files) if (preferred.equals(file.path)) return file;
        for (WebDAVClient.BackupFile file : files) {
            if (file.path != null && file.path.matches("/[A-Za-z0-9_-]{1,32}_latest\\.dat")) return file;
        }
        for (WebDAVClient.BackupFile file : files) {
            if (LATEST_BACKUP.equals(file.path)) return file;
        }
        return null;
    }

    private List<WebDAVClient.BackupFile> historyOnly(List<WebDAVClient.BackupFile> files, WebDAVClient.BackupFile latest) {
        List<WebDAVClient.BackupFile> result = new ArrayList<>();
        if (files != null) {
            for (WebDAVClient.BackupFile file : files) {
                if (latest != null && latest.path.equals(file.path)) continue;
                if (isSafeHistoryPath(file.path)) result.add(file);
            }
        }
        result.sort((left, right) -> right.name.compareTo(left.name));
        return result;
    }

    private boolean isSafeHistoryPath(String path) {
        return path != null && path.matches("/[A-Za-z0-9_-]{1,32}_[0-9]{8}_[0-9]{6}\\.dat");
    }

    private void loadTargetHistory(TargetBackupUi ui) {
        WebDavTarget target = targetFor(ui.backup);
        if (target == null) return;
        ui.historyContent.removeAllViews();
        ui.historyContent.addView(createNetworkText(getString(R.string.loading), 13, false));
        executor.execute(() -> {
            List<WebDAVClient.BackupFile> history = historyOnly(target.client.listBackupFiles(), null);
            runOnUi(() -> renderTargetHistory(ui, target, history));
        });
    }

    private void renderTargetHistory(TargetBackupUi ui, WebDavTarget target, List<WebDAVClient.BackupFile> history) {
        ui.historyContent.removeAllViews();
        ui.historyFiles.clear();
        ui.historyFiles.addAll(history);
        ui.selectedPaths.retainAll(historyPaths(history));
        ui.historyCount = history.size();
        ui.historySummary.setText(getString(R.string.backup_history_count, history.size()));
        if (history.isEmpty()) {
            ui.historyContent.addView(createNetworkText(getString(R.string.backup_no_history), 13, false));
            return;
        }
        for (WebDAVClient.BackupFile file : history) {
            LinearLayout row = horizontalRow(52);
            CheckBox item = new CheckBox(requireContext());
            String time = formatBackupTime(file);
            item.setText(getString(R.string.backup_history_item, file.name,
                    time.isEmpty() ? getString(R.string.backup_detected) : time, formatFileSize(file.size)));
            item.setChecked(ui.selectedPaths.contains(file.path));
            item.setOnCheckedChangeListener((button, checked) -> {
                if (checked) ui.selectedPaths.add(file.path);
                else ui.selectedPaths.remove(file.path);
                updateHistoryActions(ui);
            });
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button restore = new Button(requireContext());
            restore.setText(R.string.webdav_restore_button);
            restore.setOnClickListener(v -> confirmRestoreBackup(target, file.path));
            row.addView(restore, new LinearLayout.LayoutParams(dp(88), dp(44)));
            ui.historyContent.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ui.selectionLabel = createNetworkText(getString(R.string.items_selected, 0), 12, false);
        ui.historyContent.addView(ui.selectionLabel);
        LinearLayout actions = horizontalRow(48);
        Button clear = new Button(requireContext());
        clear.setText(R.string.cancel_selection);
        clear.setOnClickListener(v -> {
            ui.selectedPaths.clear();
            renderTargetHistory(ui, target, ui.historyFiles);
        });
        ui.deleteSelected = new Button(requireContext());
        ui.deleteSelected.setText(R.string.delete_selected);
        ui.deleteSelected.setEnabled(false);
        ui.deleteSelected.setOnClickListener(v -> confirmDeleteTargetHistory(ui, target));
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(ui.deleteSelected, new LinearLayout.LayoutParams(0, dp(44), 1));
        ui.historyContent.addView(actions);
        updateHistoryActions(ui);
    }

    private Set<String> historyPaths(List<WebDAVClient.BackupFile> files) {
        Set<String> paths = new HashSet<>();
        for (WebDAVClient.BackupFile file : files) paths.add(file.path);
        return paths;
    }

    private void updateHistoryActions(TargetBackupUi ui) {
        if (ui.selectionLabel != null) ui.selectionLabel.setText(getString(R.string.items_selected, ui.selectedPaths.size()));
        if (ui.deleteSelected != null) ui.deleteSelected.setEnabled(!ui.selectedPaths.isEmpty());
    }

    private void collapseTargetHistory(TargetBackupUi ui) {
        ui.selectedPaths.clear();
        if (ui.historyContent != null) ui.historyContent.removeAllViews();
        if (ui.historyContent != null) ui.historyContent.setVisibility(View.GONE);
        if (ui.historyArrow != null) ui.historyArrow.setText("\u25b6");
    }

    private void confirmDeleteTargetHistory(TargetBackupUi ui, WebDavTarget target) {
        if (!OperationModeManager.canModify(requireContext())) {
            OperationModeGuard.requireEdit(this, () -> confirmDeleteTargetHistory(ui, target));
            return;
        }
        List<String> paths = new ArrayList<>();
        for (String path : ui.selectedPaths) if (isSafeHistoryPath(path)) paths.add(path);
        if (paths.isEmpty()) return;
        SensitiveActionGuard.requireRecentAuth(requireActivity(), getString(R.string.delete_backup_history), () ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.delete_backup_history)
                        .setMessage(getString(R.string.delete_backup_history_confirm, paths.size()))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (dialog, which) -> deleteTargetHistory(ui, target, paths))
                        .show());
    }

    private void deleteTargetHistory(TargetBackupUi ui, WebDavTarget target, List<String> paths) {
        executor.execute(() -> {
            int success = 0;
            for (String path : paths) {
                if (isSafeHistoryPath(path) && target.client.delete(path)) success++;
            }
            int deleted = success;
            runOnUi(() -> {
                ui.selectedPaths.clear();
                Toast.makeText(requireContext(), getString(R.string.delete_backup_history_result, deleted, paths.size() - deleted), Toast.LENGTH_SHORT).show();
                loadTargetHistory(ui);
                refreshTargetSummary(ui);
            });
        });
    }

    private void showFullBackupName(TargetBackupUi ui) {
        if (ui.latest == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_file_name)
                .setMessage(ui.latest.name)
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    private WebDavTarget mainTarget() {
        return buildTarget(getString(R.string.backup_target_main), webdavUrl, webdavUser, webdavPass, "all", false);
    }

    private WebDavTarget backupTarget() {
        return buildTarget(getString(R.string.backup_target_backup), backupWebdavUrl, backupWebdavUser, backupWebdavPass, "all", true);
    }

    private WebDavTarget buildTarget(String label, EditText urlInput, EditText userInput, EditText passInput, String contentMode, boolean isBackup) {
        String url = urlInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String pass = passInput.getText().toString();
        if (url.isEmpty() && user.isEmpty() && pass.isEmpty()) return null;
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.webdav_complete_config_required) + "：" + label, Toast.LENGTH_SHORT).show();
            return null;
        }
        NetworkAccessController.rememberRuntimeWebDavEndpoint(url);
        return new WebDavTarget(label, new WebDAVClient(requireContext(), url, user, pass), contentMode, isBackup);
    }

    private String toSyncJson(List<ScanRecord> records, List<PasswordEntry> passwords, List<OtpToken> otpTokens, String contentMode) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 3);
        root.put("records", shouldSyncRecords(contentMode) ? recordsToJson(records) : new JSONArray());
        root.put("passwordGroups", shouldSyncPasswords(contentMode) ? passwordGroupsToJson(passwordRepository.getGroupsNow()) : new JSONArray());
        root.put("passwords", shouldSyncPasswords(contentMode) ? passwordsToJson(passwords) : new JSONArray());
        root.put("otpTokens", shouldSyncOtp(contentMode) ? otpTokensToJson(otpTokens) : new JSONArray());
        return root.toString();
    }

    private boolean shouldSyncRecords(String contentMode) {
        return "all".equals(contentMode) || "records".equals(contentMode);
    }

    private boolean shouldSyncPasswords(String contentMode) {
        return "all".equals(contentMode) || "passwords".equals(contentMode);
    }

    private boolean shouldSyncOtp(String contentMode) {
        return "all".equals(contentMode) || "otp".equals(contentMode);
    }

    private JSONArray recordsToJson(List<ScanRecord> records) throws Exception {
        JSONArray array = new JSONArray();
        for (ScanRecord record : records) {
            JSONObject object = new JSONObject();
            object.put("content", record.content);
            object.put("type", record.type);
            object.put("title", record.title);
            object.put("source", record.source);
            object.put("thumbnailBase64", record.thumbnailBase64);
            object.put("isStarred", record.isStarred);
            object.put("timestamp", record.timestamp);
            array.put(object);
        }
        return array;
    }

    private JSONArray otpTokensToJson(List<OtpToken> tokens) throws Exception {
        JSONArray array = new JSONArray();
        for (OtpToken token : tokens) {
            JSONObject object = new JSONObject();
            object.put("accountName", token.accountName);
            object.put("issuer", token.issuer);
            object.put("secret", token.secret);
            object.put("digits", token.digits);
            object.put("period", token.period);
            object.put("algorithm", token.algorithm);
            object.put("pinned", token.pinned);
            object.put("sortOrder", token.sortOrder);
            object.put("createdAt", token.createdAt);
            object.put("updatedAt", token.updatedAt);
            array.put(object);
        }
        return array;
    }

    private JSONArray passwordsToJson(List<PasswordEntry> passwords) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordEntry entry : passwords) {
            JSONObject object = new JSONObject();
            object.put("title", entry.title);
            object.put("websiteDomain", entry.websiteDomain);
            object.put("appPackageName", entry.appPackageName);
            object.put("username", entry.username);
            object.put("password", entry.password);
            object.put("account", entry.account);
            object.put("remark", entry.remark);
            object.put("notes", entry.notes);
            object.put("groupId", entry.groupId);
            object.put("lastUsedAt", entry.lastUsedAt);
            object.put("createdAt", entry.createdAt);
            object.put("updatedAt", entry.updatedAt);
            array.put(object);
        }
        return array;
    }

    private JSONArray passwordGroupsToJson(List<PasswordGroup> groups) throws Exception {
        JSONArray array = new JSONArray();
        for (PasswordGroup group : groups) {
            JSONObject object = new JSONObject();
            object.put("id", group.id);
            object.put("name", group.name);
            object.put("sortOrder", group.sortOrder);
            object.put("isDefault", group.isDefault);
            object.put("createdAt", group.createdAt);
            object.put("updatedAt", group.updatedAt);
            array.put(object);
        }
        return array;
    }

    private List<ScanRecord> parseRecords(String json) throws Exception {
        return parseRecords(new JSONArray(json));
    }

    private List<ScanRecord> parseRecords(@Nullable JSONArray array) throws Exception {
        List<ScanRecord> records = new ArrayList<>();
        if (array == null) return records;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            ScanRecord record = new ScanRecord();
            record.content = object.optString("content");
            record.type = object.optString("type", ScanRecord.detectType(record.content));
            record.title = object.optString("title", record.content);
            record.source = object.optString("source", "SCAN");
            record.thumbnailBase64 = object.optString("thumbnailBase64", "");
            record.isStarred = object.optBoolean("isStarred");
            record.timestamp = object.optLong("timestamp");
            records.add(record);
        }
        return records;
    }

    private List<PasswordEntry> parsePasswords(@Nullable JSONArray array) throws Exception {
        List<PasswordEntry> entries = new ArrayList<>();
        if (array == null) return entries;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            PasswordEntry entry = new PasswordEntry();
            entry.title = object.optString("title", object.optString("remark"));
            entry.websiteDomain = object.optString("websiteDomain", "");
            entry.appPackageName = object.optString("appPackageName", "");
            entry.username = object.optString("username", object.optString("account"));
            entry.password = object.optString("password");
            entry.account = object.optString("account");
            entry.remark = object.optString("remark");
            entry.notes = object.optString("notes", "");
            entry.groupId = object.optString("groupId", "");
            entry.lastUsedAt = object.optLong("lastUsedAt", 0);
            entry.createdAt = object.optLong("createdAt");
            entry.updatedAt = object.optLong("updatedAt", entry.createdAt);
            entries.add(entry);
        }
        return entries;
    }

    private List<PasswordGroup> parsePasswordGroups(@Nullable JSONArray array) throws Exception {
        List<PasswordGroup> groups = new ArrayList<>();
        if (array == null) return groups;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            PasswordGroup group = new PasswordGroup();
            group.id = object.optString("id", PasswordGroup.DEFAULT_ID);
            group.name = object.optString("name", PasswordGroup.DEFAULT_NAME);
            group.sortOrder = object.optInt("sortOrder", 0);
            group.isDefault = object.optBoolean("isDefault", PasswordGroup.DEFAULT_ID.equals(group.id));
            group.createdAt = object.optLong("createdAt", System.currentTimeMillis());
            group.updatedAt = object.optLong("updatedAt", group.createdAt);
            groups.add(group);
        }
        return groups;
    }

    private List<OtpToken> parseOtpTokens(@Nullable JSONArray array) throws Exception {
        List<OtpToken> tokens = new ArrayList<>();
        if (array == null) return tokens;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            OtpToken token = new OtpToken();
            token.accountName = object.optString("accountName");
            token.issuer = object.optString("issuer");
            token.secret = object.optString("secret");
            token.digits = object.optInt("digits", 6);
            token.period = object.optInt("period", 30);
            token.algorithm = object.optString("algorithm", "SHA1");
            token.pinned = object.optBoolean("pinned");
            token.sortOrder = object.optInt("sortOrder");
            token.createdAt = object.optLong("createdAt");
            token.updatedAt = object.optLong("updatedAt", token.createdAt);
            tokens.add(token);
        }
        return tokens;
    }

    private void updateLastSyncText(long time) {
        if (time > 0) {
            lastSync.setText(getString(R.string.webdav_last_upload_prefix) + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time)));
        } else {
            lastSync.setText(R.string.last_upload_never);
        }
    }

    private String selectedAlgorithm() {
        Object item = algorithmSpinner.getSelectedItem();
        return item == null ? "AES-GCM" : item.toString();
    }

    private EditText createPasswordInput(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(0xFF636366);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private List<String> syncContentLabels() {
        return java.util.Arrays.asList(
                getString(R.string.sync_content_records),
                getString(R.string.sync_content_passwords),
                getString(R.string.sync_content_otp),
                getString(R.string.sync_content_all)
        );
    }

    private String selectedSyncContentKey(Spinner spinner) {
        int position = spinner == null ? 3 : spinner.getSelectedItemPosition();
        if (position == 0) return "records";
        if (position == 1) return "passwords";
        if (position == 2) return "otp";
        return "all";
    }

    private void setSyncContentSelection(Spinner spinner, String saved) {
        int index = 3;
        if ("records".equals(saved) || getString(R.string.sync_content_records).equals(saved)) index = 0;
        else if ("passwords".equals(saved) || getString(R.string.sync_content_passwords).equals(saved)) index = 1;
        else if ("otp".equals(saved) || getString(R.string.sync_content_otp).equals(saved)) index = 2;
        else if ("all".equals(saved) || getString(R.string.sync_content_all).equals(saved)) index = 3;
        spinner.setSelection(index);
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equals(spinner.getItemAtPosition(i).toString())) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void runOnUi(Runnable runnable) {
        FragmentUi.run(this, runnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (NetworkAccessController.activeBackupLanInfo(requireContext()) == null && lanShareState != R.string.lan_status_not_started) {
            disableLanShare(R.string.lan_status_not_started, true);
        } else {
            renderLanShareControls();
            updateSectionSummaries();
        }
    }

    @Override
    public void onPause() {
        hideAllWebDavSecrets();
        if (!requireActivity().isChangingConfigurations() && !ConfigurationRebuildGuard.isInProgress()) {
            RecentAuthSession.clear();
            sessionLedgerPassword = null;
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        hideAllWebDavSecrets();
        stopLanTransfer();
        NetworkAccessController.clearBackupLanSession();
        if (hideWebDavSecretsRunnable != null) {
            secretHandler.removeCallbacks(hideWebDavSecretsRunnable);
            hideWebDavSecretsRunnable = null;
        }
        super.onDestroyView();
        if (executor != null) executor.shutdown();
    }

    private static class WebDavTarget {
        final String label;
        final WebDAVClient client;
        final String contentMode;
        final boolean isBackup;

        WebDavTarget(String label, WebDAVClient client, String contentMode) {
            this(label, client, contentMode, false);
        }

        WebDavTarget(String label, WebDAVClient client, String contentMode, boolean isBackup) {
            this.label = label;
            this.client = client;
            this.contentMode = contentMode;
            this.isBackup = isBackup;
        }
    }

    private static class TargetBackupUi {
        final boolean backup;
        final String label;
        LinearLayout root;
        LinearLayout content;
        LinearLayout historyRoot;
        LinearLayout historyContent;
        TextView arrow;
        TextView summary;
        TextView latestName;
        TextView latestTime;
        TextView historyArrow;
        TextView historySummary;
        TextView selectionLabel;
        Button restore;
        Button deleteSelected;
        WebDAVClient.BackupFile latest;
        int historyCount;
        final List<WebDAVClient.BackupFile> allFiles = new ArrayList<>();
        final List<WebDAVClient.BackupFile> historyFiles = new ArrayList<>();
        final Set<String> selectedPaths = new HashSet<>();

        TargetBackupUi(boolean backup, String label) {
            this.backup = backup;
            this.label = label;
        }
    }

    private interface BackupPasswordCallback {
        void onPassword(String password);
    }
}


