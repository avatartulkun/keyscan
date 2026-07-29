package com.secureqr.scanner.ui.security;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.security.DatabaseKeyManager;
import com.secureqr.scanner.security.SecuritySettings;
import com.secureqr.scanner.utils.PinLockHelper;

public class SecurityPinFragment extends Fragment {
    private EditText currentInput;
    private EditText pinInput;
    private EditText confirmInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        boolean configured = PinLockHelper.isConfigured(requireContext());
        LinearLayout root = SecurityUi.page(this, getString(configured ? R.string.security_pin_change : R.string.security_pin_setup));
        scrollView.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView shield = SecurityUi.icon(this, "PIN", 0xFF2F7CF6);
        LinearLayout.LayoutParams shieldParams = new LinearLayout.LayoutParams(SecurityUi.dp(this, 72), SecurityUi.dp(this, 72));
        shieldParams.topMargin = SecurityUi.dp(this, 34);
        shieldParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        root.addView(shield, shieldParams);

        if (configured) {
            TextView current = SecurityUi.text(this, getString(R.string.security_pin_current_hint), 16, R.color.text_main, true);
            current.setGravity(android.view.Gravity.CENTER);
            root.addView(current, SecurityUi.matchWrap(this, 24));
            currentInput = pinEdit();
            root.addView(currentInput, SecurityUi.matchWrap(this, 12));
        }

        TextView hint = SecurityUi.text(this, getString(configured ? R.string.security_pin_new_hint : R.string.security_pin_input_hint), 16, R.color.text_main, true);
        hint.setGravity(android.view.Gravity.CENTER);
        root.addView(hint, SecurityUi.matchWrap(this, configured ? 22 : 24));
        pinInput = pinEdit();
        root.addView(pinInput, SecurityUi.matchWrap(this, 12));

        TextView confirm = SecurityUi.text(this, getString(R.string.security_pin_confirm), 16, R.color.text_main, true);
        confirm.setGravity(android.view.Gravity.CENTER);
        root.addView(confirm, SecurityUi.matchWrap(this, 22));
        confirmInput = pinEdit();
        root.addView(confirmInput, SecurityUi.matchWrap(this, 12));

        LinearLayout card = SecurityUi.card(this);
        card.addView(SecurityUi.text(this, getString(R.string.security_pin_access_title), 15, R.color.text_main, true));
        card.addView(SecurityUi.text(this,
                getString(R.string.security_pin_access_summary),
                14, R.color.text_secondary, false), SecurityUi.matchWrap(this, 8));
        root.addView(card, SecurityUi.matchWrap(this, 24));

        android.widget.Button save = SecurityUi.primaryButton(this, getString(configured ? R.string.security_pin_change : R.string.security_pin_save));
        save.setOnClickListener(v -> savePin());
        root.addView(save, SecurityUi.matchWrap(this, 28));
        return scrollView;
    }

    private EditText pinEdit() {
        EditText edit = new EditText(requireContext());
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        edit.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        edit.setGravity(android.view.Gravity.CENTER);
        edit.setTextSize(26);
        edit.setLetterSpacing(0.25f);
        edit.setBackgroundResource(R.drawable.bg_edit_text);
        edit.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_main));
        edit.setHint("••••");
        edit.setHintTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_hint));
        edit.setPadding(SecurityUi.dp(this, 12), 0, SecurityUi.dp(this, 12), 0);
        edit.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SecurityUi.dp(this, 54)));
        return edit;
    }

    private void savePin() {
        String pin = pinInput.getText().toString().trim();
        String confirm = confirmInput.getText().toString().trim();
        if (currentInput != null && !PinLockHelper.verifyPin(requireContext(), currentInput.getText().toString().trim())) {
            currentInput.setError(getString(R.string.security_pin_current_incorrect));
            return;
        }
        if (!PinLockHelper.isValidPin(pin)) {
            pinInput.setError(getString(R.string.security_pin_input_hint));
            return;
        }
        if (!pin.equals(confirm)) {
            confirmInput.setError(getString(R.string.security_pin_mismatch));
            return;
        }
        if (currentInput != null && !DatabaseKeyManager.ensurePersistentDatabaseKey(requireContext())) {
            Toast.makeText(requireContext(), R.string.security_pin_database_key_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        if (currentInput != null) {
            String dataKey = SecuritySettings.getDataEncryptionKey(requireContext());
            if (!DatabaseKeyManager.rewrapDatabaseKey(requireContext(), pin, dataKey)) {
                Toast.makeText(requireContext(), R.string.security_pin_database_key_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
        }
        SecuritySettings.savePin(requireContext(), pin);
        Toast.makeText(requireContext(), currentInput == null ? R.string.security_pin_saved : R.string.security_pin_changed, Toast.LENGTH_SHORT).show();
        if (currentInput == null) {
            getParentFragmentManager().popBackStack();
        } else {
            SecurityChangeBackupPrompt.show(this, () -> getParentFragmentManager().popBackStack());
        }
    }
}
