package com.secureqr.scanner.ui.security;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.DatabaseOpenState;

public class SecurityDatabaseErrorFragment extends Fragment {
    @Nullable
    @Override
    public android.view.View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(R.string.security_database_title));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        DatabaseOpenState state = DatabaseKeyManager.databaseOpenState(requireContext());
        LinearLayout card = SecurityUi.card(this);
        card.addView(SecurityUi.text(this, getString(R.string.security_database_open_failed), 20, R.color.text_main, true));
        card.addView(SecurityUi.text(this, messageFor(state), 15, R.color.text_secondary, false), SecurityUi.matchWrap(this, 10));
        card.addView(SecurityUi.text(this, getString(R.string.security_database_preserved), 14, R.color.warning, true), SecurityUi.matchWrap(this, 12));
        root.addView(card, SecurityUi.matchWrap(this, 24));

        android.widget.Button recovery = SecurityUi.primaryButton(this, getString(R.string.security_open_recovery));
        recovery.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SecurityRecoveryFragment())
                .addToBackStack(null)
                .commit());
        root.addView(recovery, SecurityUi.matchWrap(this, 18));

        android.widget.Button status = SecurityUi.primaryButton(this, getString(R.string.security_view_status));
        status.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SecurityStatusFragment())
                .addToBackStack(null)
                .commit());
        root.addView(status, SecurityUi.matchWrap(this, 10));
        return scrollView;
    }

    private String messageFor(DatabaseOpenState state) {
        if (state == DatabaseOpenState.DATABASE_MIGRATION_ERROR) {
            return getString(R.string.security_database_migration_failed);
        }
        if (state == DatabaseOpenState.DATABASE_CORRUPTED) {
            return getString(R.string.security_database_abnormal);
        }
        if (state == DatabaseOpenState.DATABASE_ACCESS_ERROR) {
            return getString(R.string.security_database_access_failed);
        }
        return getString(R.string.security_database_credentials_failed);
    }
}
