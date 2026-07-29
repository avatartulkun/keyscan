package com.secureqr.scanner.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.NavigationHelper;

/** Full-page add/import hub opened from the primary + action. */
public class AddContentFragment extends Fragment {
    private HomeFragment.HomeActions actions;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof HomeFragment.HomeActions) {
            actions = (HomeFragment.HomeActions) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bind(view, R.id.add_action_scan, () -> actions.openScanner());
        bind(view, R.id.add_action_password, () -> actions.openNewPasswordRecord());
        bind(view, R.id.add_action_vault, () -> actions.openNewSecureItem());
        bind(view, R.id.add_action_password_import, () -> actions.openPasswordBookImport());
        bind(view, R.id.add_action_otp_manual, () -> actions.openOtpManualImport());
        bind(view, R.id.add_action_otp_batch, () -> actions.openOtpBatchImport());
        view.findViewById(R.id.btn_add_content_cancel).setOnClickListener(v -> NavigationHelper.openHome(this));
    }

    private void bind(View root, int id, Runnable action) {
        root.findViewById(id).setOnClickListener(v -> {
            if (actions != null) action.run();
        });
    }

    @Override
    public void onDetach() {
        actions = null;
        super.onDetach();
    }
}
