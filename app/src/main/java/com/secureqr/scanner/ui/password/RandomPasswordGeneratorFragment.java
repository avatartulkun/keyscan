package com.secureqr.scanner.ui.password;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.repository.PasswordGenerationRepository;
import com.secureqr.scanner.utils.PasswordGeneratorEngine;

public class RandomPasswordGeneratorFragment extends Fragment {
    private final PasswordGeneratorEngine.Options options = new PasswordGeneratorEngine.Options();
    private TextView passwordText;
    private TextView toggleButton;
    private TextView lengthText;
    private ProgressBar strengthProgress;
    private TextView strengthText;
    private String currentPassword = "";
    private boolean visible;
    private PasswordGenerationRepository historyRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_random_password_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        passwordText = view.findViewById(R.id.tv_random_password);
        historyRepository = PasswordGenerationRepository.getInstance(requireContext());
        toggleButton = view.findViewById(R.id.btn_toggle_password);
        lengthText = view.findViewById(R.id.tv_length);
        strengthProgress = view.findViewById(R.id.progress_strength);
        strengthText = view.findViewById(R.id.tv_strength);

        SeekBar lengthSeek = view.findViewById(R.id.seek_length);
        CheckBox upper = view.findViewById(R.id.cb_upper);
        CheckBox lower = view.findViewById(R.id.cb_lower);
        CheckBox digits = view.findViewById(R.id.cb_digits);
        CheckBox symbols = view.findViewById(R.id.cb_symbols);
        CheckBox zeroO = view.findViewById(R.id.cb_exclude_zero_o);
        CheckBox oneI = view.findViewById(R.id.cb_exclude_one_i);
        CheckBox lowerL = view.findViewById(R.id.cb_exclude_lower_l);
        toggleButton.setOnClickListener(v -> {
            visible = !visible;
            updatePasswordView();
        });
        view.findViewById(R.id.btn_copy_password).setOnClickListener(v -> copyPassword());
        view.findViewById(R.id.btn_regenerate_password).setOnClickListener(v -> generate());
        view.findViewById(R.id.btn_view_generation_history).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new PasswordGenerationHistoryFragment())
                        .addToBackStack(null)
                        .commit());
        lengthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                options.length = progress + 4;
                generate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        View.OnClickListener optionListener = v -> {
            options.includeUpper = upper.isChecked();
            options.includeLower = lower.isChecked();
            options.includeDigits = digits.isChecked();
            options.includeSymbols = symbols.isChecked();
            options.excludeZeroO = zeroO.isChecked();
            options.excludeOneI = oneI.isChecked();
            options.excludeLowerL = lowerL.isChecked();
            generate();
        };
        upper.setOnClickListener(optionListener);
        lower.setOnClickListener(optionListener);
        digits.setOnClickListener(optionListener);
        symbols.setOnClickListener(optionListener);
        zeroO.setOnClickListener(optionListener);
        oneI.setOnClickListener(optionListener);
        lowerL.setOnClickListener(optionListener);
        generate();
    }

    private void generate() {
        currentPassword = PasswordGeneratorEngine.generate(options);
        if (currentPassword.isEmpty()) {
            Toast.makeText(requireContext(), R.string.keep_one_charset, Toast.LENGTH_SHORT).show();
            return;
        }
        visible = true;
        updatePasswordView();
        updateStrength();
        historyRepository.insert(currentPassword, options.length, configSummary());
    }

    private void updatePasswordView() {
        passwordText.setText(visible ? currentPassword : mask(currentPassword.length()));
        toggleButton.setText(visible ? R.string.hide_password : R.string.show_password);
        lengthText.setText(getString(R.string.length_units, options.length));
    }

    private void updateStrength() {
        int score = PasswordGeneratorEngine.strengthScore(currentPassword);
        int color;
        String label;
        if (score < 55) {
            color = Color.parseColor("#D93025");
            label = getString(R.string.strength_weak);
        } else if (score < 85) {
            color = Color.parseColor("#F9AB00");
            label = getString(R.string.strength_medium);
        } else {
            color = Color.parseColor("#00A878");
            label = getString(R.string.strength_strong);
        }
        strengthProgress.setProgress(score);
        strengthProgress.setProgressTintList(ColorStateList.valueOf(color));
        strengthText.setText(label);
    }

    private void copyPassword() {
        copyText(currentPassword);
    }

    private void copyText(String text) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("KeyScan random password", text));
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private String configSummary() {
        StringBuilder builder = new StringBuilder();
        if (options.includeUpper) builder.append(getString(R.string.config_upper)).append(' ');
        if (options.includeLower) builder.append(getString(R.string.config_lower)).append(' ');
        if (options.includeDigits) builder.append(getString(R.string.config_digits)).append(' ');
        if (options.includeSymbols) builder.append(getString(R.string.config_symbols)).append(' ');
        if (options.excludeZeroO || options.excludeOneI || options.excludeLowerL) builder.append(getString(R.string.config_exclude_confusing));
        return builder.toString().trim();
    }

    private String mask(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) builder.append("•");
        return builder.toString();
    }
}

