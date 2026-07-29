package com.secureqr.scanner.ui.scanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.secureqr.scanner.R;
import com.secureqr.scanner.ui.otp.OtpAuthFragment;
import com.secureqr.scanner.utils.NavigationHelper;

public final class SmartScanFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle b){return i.inflate(R.layout.fragment_smart_scan,c,false);}
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        getParentFragmentManager().setFragmentResultListener(OtpAuthFragment.OTP_SCAN_REQUEST,
                getViewLifecycleOwner(), (key, result) -> {
                    String raw = result.getString(OtpAuthFragment.OTP_SCAN_VALUE, "");
                    if (!raw.isEmpty()) {
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, OtpAuthFragment.scannedUri(raw))
                                .commit();
                    }
                });
        view.findViewById(R.id.btn_smart_scan_home).setOnClickListener(v->NavigationHelper.openHome(this));
        view.findViewById(R.id.card_scan_qr).setOnClickListener(v->open(new ScannerFragment()));
        view.findViewById(R.id.card_scan_otp).setOnClickListener(v->open(ScannerFragment.forOtpCapture()));
        view.findViewById(R.id.card_scan_bank).setOnClickListener(v->open(OcrScanFragment.bankCard()));
        view.findViewById(R.id.card_scan_document).setOnClickListener(v->open(OcrScanFragment.document("OTHER_ID")));
        view.findViewById(R.id.card_scan_file).setOnClickListener(v->open(OcrScanFragment.secureFile()));
    }
    private void open(Fragment fragment){getParentFragmentManager().beginTransaction().replace(R.id.fragment_container,fragment).addToBackStack(null).commit();}
}
