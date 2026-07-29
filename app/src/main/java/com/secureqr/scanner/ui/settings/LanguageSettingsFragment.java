package com.secureqr.scanner.ui.settings;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.LocaleHelper;
import com.secureqr.scanner.utils.NavigationHelper;

import java.util.Locale;

/** Dedicated application-language page opened from the home overflow menu. */
public class LanguageSettingsFragment extends Fragment {
    private static final String[] TAGS = {
            LocaleHelper.SYSTEM_DEFAULT, "zh-CN", "en-US", "ja-JP", "zh-TW", "ko-KR",
            "de-DE", "fr-FR", "es-ES", "it-IT", "nl-NL", "pt-BR", "ru-RU"
    };
    private static final String[] NATIVE_NAMES = {
            "", "简体中文", "English (US)", "日本語", "繁體中文", "한국어",
            "Deutsch", "Français", "Español", "Italiano", "Nederlands",
            "Português (Brasil)", "Русский"
    };
    private boolean expanded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_language_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btn_language_back).setOnClickListener(v -> {
            if (!getParentFragmentManager().popBackStackImmediate()) {
                NavigationHelper.openHome(this);
            }
        });
        buildLanguageList(view);
        view.findViewById(R.id.card_application_language).setOnClickListener(v -> toggleList(view));
        renderCurrentLanguage(view);
        ((ImageView) view.findViewById(R.id.language_expand_arrow)).setImageResource(R.drawable.ic_chevron_down_24);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) renderCurrentLanguage(getView());
    }

    private void renderCurrentLanguage(View root) {
        TextView value = root.findViewById(R.id.tv_current_application_language);
        String tag = LocaleHelper.currentLanguage(requireContext());
        if (LocaleHelper.SYSTEM_DEFAULT.equals(tag)) {
            value.setText(R.string.language_picker_system_default);
            return;
        }
        if ("zh-CN".equals(tag)) {
            value.setText(R.string.language_simplified_chinese);
            return;
        }
        if ("zh-TW".equals(tag)) {
            value.setText(R.string.language_traditional_chinese);
            return;
        }
        Locale displayLocale = Build.VERSION.SDK_INT >= 24
                ? getResources().getConfiguration().getLocales().get(0)
                : getResources().getConfiguration().locale;
        Locale language = Locale.forLanguageTag(tag);
        String label = language.getDisplayLanguage(displayLocale);
        value.setText(label == null || label.trim().isEmpty() ? tag : label);
    }

    private void toggleList(View root) {
        expanded = !expanded;
        root.findViewById(R.id.language_list_container)
                .setVisibility(expanded ? View.VISIBLE : View.GONE);
        ImageView arrow = root.findViewById(R.id.language_expand_arrow);
        arrow.setImageResource(expanded ? R.drawable.ic_chevron_up_24 : R.drawable.ic_chevron_down_24);
        arrow.animate().cancel();
        arrow.setScaleX(0.88f); arrow.setScaleY(0.88f);
        arrow.animate().scaleX(1f).scaleY(1f).setDuration(150L).start();
    }

    private void buildLanguageList(View root) {
        LinearLayout list = root.findViewById(R.id.language_list_container);
        list.removeAllViews();
        addHeading(list, R.string.language_picker_follow_system);
        addLanguageRow(list, TAGS[0],
                getString(R.string.language_picker_system_default),
                getString(R.string.language_picker_system_summary));
        addHeading(list, R.string.language_picker_available);
        for (int i = 1; i < TAGS.length; i++) {
            addLanguageRow(list, TAGS[i], NATIVE_NAMES[i], localizedLanguageName(TAGS[i]));
        }
    }

    private void addHeading(LinearLayout parent, int textRes) {
        TextView heading = new TextView(requireContext());
        heading.setText(textRes);
        heading.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        heading.setTextSize(12);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        heading.setPadding(dp(4), dp(10), dp(4), dp(4));
        parent.addView(heading);
    }

    private void addLanguageRow(LinearLayout parent, String tag, String nativeName, String translatedName) {
        boolean checked = tag.equals(LocaleHelper.currentLanguage(requireContext()));
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, dp(4), 0);
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        RadioButton radio = new RadioButton(requireContext());
        radio.setChecked(checked);
        radio.setClickable(false);
        radio.setFocusable(false);
        row.addView(radio, new LinearLayout.LayoutParams(dp(40), dp(44)));

        TextView nativeLabel = new TextView(requireContext());
        nativeLabel.setText(nativeName);
        nativeLabel.setTextColor(ContextCompat.getColor(requireContext(),
                checked ? R.color.settings_blue : R.color.text_main));
        nativeLabel.setTextSize(15);
        nativeLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nativeLabel.setMaxLines(1);
        nativeLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(nativeLabel, new LinearLayout.LayoutParams(0, dp(44), 1f));
        nativeLabel.setGravity(Gravity.CENTER_VERTICAL);

        TextView translatedLabel = new TextView(requireContext());
        translatedLabel.setText(translatedName);
        translatedLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        translatedLabel.setTextSize(13);
        translatedLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        translatedLabel.setMaxLines(1);
        translatedLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(translatedLabel, new LinearLayout.LayoutParams(dp(110), dp(44)));

        row.setOnClickListener(v -> selectLanguage(tag));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        View divider = new View(requireContext());
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_stroke));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.leftMargin = dp(40);
        parent.addView(divider, dividerParams);
    }

    private void selectLanguage(String tag) {
        if (tag.equals(LocaleHelper.currentLanguage(requireContext()))) {
            toggleList(requireView());
            return;
        }
        LocaleHelper.saveLanguage(requireContext(), tag);
        requireActivity().recreate();
    }

    private String localizedLanguageName(String tag) {
        if ("zh-CN".equals(tag)) return getString(R.string.language_simplified_chinese);
        if ("zh-TW".equals(tag)) return getString(R.string.language_traditional_chinese);
        Locale displayLocale = Build.VERSION.SDK_INT >= 24
                ? getResources().getConfiguration().getLocales().get(0)
                : getResources().getConfiguration().locale;
        Locale language = Locale.forLanguageTag(tag);
        String name = language.getDisplayLanguage(displayLocale);
        if (name == null || name.trim().isEmpty()) return language.getDisplayLanguage(Locale.ENGLISH);
        return name.substring(0, 1).toUpperCase(displayLocale) + name.substring(1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
