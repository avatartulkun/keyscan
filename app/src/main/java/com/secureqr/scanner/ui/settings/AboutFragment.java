package com.secureqr.scanner.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.secureqr.scanner.PrivacyConsentActivity;
import com.secureqr.scanner.R;
import com.secureqr.scanner.utils.AppConstants;

/** Integrated About page for app information, agreements, feedback and releases. */
public class AboutFragment extends Fragment {
    private static final String PROJECT_URL = "https://github.com/avatartulkun/keyscan";

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.surface_light));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(topBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(hero(), matchWrap(18));

        LinearLayout actions = card();
        actions.addView(actionRow(R.drawable.ic_vault_contract,
                getString(R.string.about_privacy_policy),
                view -> startActivity(PrivacyConsentActivity.createReviewIntent(requireContext()))));
        actions.addView(divider());
        actions.addView(actionRow(R.drawable.ic_vault_mail,
                getString(R.string.feedback),
                view -> sendFeedback()));
        actions.addView(divider());
        actions.addView(actionRow(R.drawable.ic_vault_code,
                getString(R.string.about_open_source),
                view -> copyProjectUrl()));
        root.addView(actions, matchWrap(24));

        View footerDivider = divider();
        LinearLayout.LayoutParams footerDividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        footerDividerParams.topMargin = dp(24);
        root.addView(footerDivider, footerDividerParams);
        TextView copyright = text(getString(R.string.about_copyright), 13,
                R.color.text_secondary, false);
        copyright.setGravity(Gravity.CENTER);
        copyright.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(copyright, matchWrap(18));
        TextView rights = text(getString(R.string.about_rights_reserved), 13,
                R.color.text_secondary, false);
        rights.setGravity(Gravity.CENTER);
        rights.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(rights, matchWrap(5));
        return scroll;
    }

    private View topBar() {
        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(requireContext());
        back.setImageResource(R.drawable.ic_arrow_back_24);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(view -> getParentFragmentManager().popBackStack());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text(getString(R.string.about), 22, R.color.text_main, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(new View(requireContext()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        return top;
    }

    private View hero() {
        LinearLayout hero = new LinearLayout(requireContext());
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(icon, new LinearLayout.LayoutParams(dp(96), dp(96)));

        ImageView name = new ImageView(requireContext());
        name.setImageResource(R.drawable.keyscan_wordmark);
        name.setContentDescription(getString(R.string.app_name));
        name.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(dp(188), dp(60));
        nameParams.gravity = Gravity.CENTER_HORIZONTAL;
        nameParams.topMargin = dp(12);
        hero.addView(name, nameParams);

        TextView slogan = text(getString(R.string.about_keyscan_slogan), 15,
                R.color.text_secondary, false);
        slogan.setGravity(Gravity.CENTER);
        hero.addView(slogan, matchWrap(7));

        TextView version = text(getString(R.string.about_version_format, versionName()),
                14, R.color.action_icon_tint, true);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = matchWrap(12);
        versionParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        versionParams.gravity = Gravity.CENTER_HORIZONTAL;
        hero.addView(version, versionParams);
        return hero;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(1));
        return card;
    }

    private View actionRow(@DrawableRes int iconRes, String title,
                           View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(10), dp(11));
        row.setMinimumHeight(dp(72));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(listener);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setColorFilter(color(R.color.action_icon_tint));
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView label = text(title, 16, R.color.text_main, true);
        label.setPadding(dp(12), 0, dp(8), 0);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = text("›", 29, R.color.text_secondary, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(48)));
        return row;
    }

    private View divider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(color(R.color.border_soft));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
    }

    private void sendFeedback() {
        String version = versionName();
        String subject = getString(R.string.feedback_subject_template, version);
        String body = getString(R.string.feedback_body_template,
                version, Build.MODEL, Build.VERSION.RELEASE);
        Intent email = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + AppConstants.FEEDBACK_EMAIL));
        email.putExtra(Intent.EXTRA_SUBJECT, subject);
        email.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(email);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(requireContext(), R.string.status_no_email_app,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void copyProjectUrl() {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.about_open_source), PROJECT_URL));
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private String versionName() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "--";
        }
    }

    private TextView text(String value, int sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color(colorRes));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int color(int resource) {
        return ContextCompat.getColor(requireContext(), resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
