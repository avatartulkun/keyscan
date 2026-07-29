/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.ActivityNotFoundException
 *  android.content.Context
 *  android.content.Intent
 *  android.graphics.Bitmap
 *  android.graphics.Bitmap$CompressFormat
 *  android.graphics.BitmapFactory
 *  android.graphics.Typeface
 *  android.net.Uri
 *  android.os.Build
 *  android.os.Build$VERSION
 *  android.os.Bundle
 *  android.os.Handler
 *  android.os.Looper
 *  android.os.VibrationEffect
 *  android.os.Vibrator
 *  android.text.TextUtils$TruncateAt
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.Button
 *  android.widget.FrameLayout
 *  android.widget.FrameLayout$LayoutParams
 *  android.widget.ImageView
 *  android.widget.ImageView$ScaleType
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.PopupMenu
 *  android.widget.TextView
 *  android.widget.Toast
 *  androidx.activity.result.ActivityResultLauncher
 *  androidx.activity.result.contract.ActivityResultContract
 *  androidx.activity.result.contract.ActivityResultContracts$GetContent
 *  androidx.annotation.NonNull
 *  androidx.annotation.Nullable
 *  androidx.appcompat.app.AlertDialog$Builder
 *  androidx.fragment.app.Fragment
 *  com.secureqr.scanner.R$color
 *  com.secureqr.scanner.R$drawable
 *  com.secureqr.scanner.R$id
 *  com.secureqr.scanner.R$layout
 *  com.secureqr.scanner.R$string
 */
package com.secureqr.scanner.ui.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.secureqr.scanner.R;
import com.secureqr.scanner.ui.settings.LanguageSettingsFragment;
import java.io.File;
import java.io.OutputStream;

public class HomeFragment
extends Fragment {
    private static final int MENU_EXPORT_DATA = 1002;
    private static final String CONTACT_EMAIL = "userfeedback@zohomail.com";
    private View homeContent;
    private FrameLayout helpContainer;
    private HomeActions actions;

    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof HomeActions) {
            this.actions = (HomeActions)context;
        }
    }

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.tool_scan).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openScanner();
            }
        });
        view.findViewById(R.id.tool_generate).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openGenerator();
            }
        });
        view.findViewById(R.id.tool_history).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openHistory();
            }
        });
        view.findViewById(R.id.tool_sync).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openWebDav();
            }
        });
        view.findViewById(R.id.tool_password_notes).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openPasswordNotes();
            }
        });
        view.findViewById(R.id.tool_settings).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openAppearance();
            }
        });
        view.findViewById(R.id.tool_password_forge).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openPasswordForge();
            }
        });
        view.findViewById(R.id.tool_password_generator).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openRandomPasswordGenerator();
            }
        });
        view.findViewById(R.id.tool_otp_auth).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openOtpAuth();
            }
        });
        view.findViewById(R.id.btn_home_menu).setOnClickListener(this::showMenu);
        this.homeContent = view.findViewById(R.id.home_content);
        this.helpContainer = (FrameLayout)view.findViewById(R.id.help_container);
        this.helpContainer.setVisibility(View.GONE);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this.requireContext(), anchor);
        menu.getMenu().add(R.string.security_center_title);
        menu.getMenu().add(R.string.settings_title);
        menu.getMenu().add(R.string.language);
        menu.getMenu().add(R.string.help_title);
        menu.getMenu().add(R.string.about_keyscan);
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (this.getString(R.string.security_center_title).equals(title)) {
                if (this.actions != null) {
                    this.actions.openSecurityCenter();
                }
            } else if (this.getString(R.string.settings_title).equals(title)) {
                if (this.actions != null) {
                    this.actions.openAppearance();
                }
            } else if (this.getString(R.string.language).equals(title)) {
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new LanguageSettingsFragment())
                        .addToBackStack(null)
                        .commit();
            } else if (this.getString(R.string.help_title).equals(title)) {
                this.showHelpCenter();
            } else if (this.getString(R.string.about_keyscan).equals(title)) {
                if (this.actions != null) {
                    this.actions.openAbout();
                }
            }
            return true;
        });
        menu.show();
    }





















































    private void flip(View from, View to) {
        to.setRotationY(-90.0f);
        to.setVisibility(0);
        from.animate().rotationY(90.0f).setDuration(300L).withEndAction(() -> {
            from.setVisibility(8);
            from.setRotationY(0.0f);
            to.animate().rotationY(0.0f).setDuration(300L).start();
        }).start();
    }


    private void vibrateLight() {
        try {
            Vibrator vibrator = (Vibrator)this.requireContext().getSystemService("vibrator");
            if (vibrator == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot((long)18L, (int)-1));
            } else {
                vibrator.vibrate(18L);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void showHelpCenter() {
        startActivity(new Intent(requireContext(), com.secureqr.scanner.ui.help.HelpManualActivity.class));
    }

    // Kept only for compatibility with the legacy embedded-home layout. All active help
    // entry points now open the complete manual activity above.
    private void showLegacyHelpCenter() {
        this.helpContainer.removeAllViews();
        LinearLayout root = new LinearLayout(this.requireContext());
        root.setOrientation(1);
        root.setBackgroundColor(this.getResources().getColor(R.color.surface_light));
        LinearLayout header = new LinearLayout(this.requireContext());
        header.setOrientation(0);
        header.setGravity(16);
        header.setPadding(this.dp(6), this.dp(22), this.dp(12), this.dp(4));
        TextView back = new TextView(this.requireContext());
        back.setText((CharSequence)"\u2190");
        back.setTextColor(this.getResources().getColor(R.color.text_main));
        back.setTextSize(26.0f);
        back.setGravity(17);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setContentDescription((CharSequence)this.getString(R.string.back));
        back.setOnClickListener(v -> this.hideHelpCenter());
        TextView title = new TextView(this.requireContext());
        title.setText(R.string.help_title);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(24.0f);
        title.setGravity(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        View spacer = new View(this.requireContext());
        header.addView((View)back, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(52), this.dp(52)));
        header.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(52), 1.0f));
        header.addView(spacer, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(52), this.dp(52)));
        ScrollView scroll = new ScrollView(this.requireContext());
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(this.requireContext());
        content.setOrientation(1);
        content.setPadding(this.dp(18), this.dp(8), this.dp(18), this.dp(28));
        content.addView((View)this.createHelpSectionTitle(R.string.help_features_section_title));
        int[][] features = new int[][]{
                {R.string.help_feature_scan_title, R.string.help_feature_scan_desc},
                {R.string.help_feature_album_title, R.string.help_feature_album_desc},
                {R.string.help_feature_password_title, R.string.help_feature_password_desc},
                {R.string.help_feature_history_title, R.string.help_feature_history_desc},
                {R.string.help_feature_generate_title, R.string.help_feature_generate_desc},
                {R.string.help_feature_otp_title, R.string.help_feature_otp_desc},
                {R.string.help_feature_random_password_title, R.string.help_feature_random_password_desc},
                {R.string.help_feature_webdav_title, R.string.help_feature_webdav_desc},
                {R.string.help_feature_data_insurance_title, R.string.help_feature_data_insurance_desc},
        };
        for (int[] feature : features) {
            content.addView(this.createHelpFeatureCard(feature[0], feature[1]));
        }
        content.addView(this.createHelpWebDavCard());
        TextView contact = this.createHelpBodyText((CharSequence)this.getString(R.string.help_contact_footer, new Object[]{CONTACT_EMAIL}));
        contact.setGravity(17);
        contact.setPadding(0, this.dp(10), 0, 0);
        content.addView((View)contact);
        scroll.addView((View)content, (ViewGroup.LayoutParams)new ScrollView.LayoutParams(-1, -2));
        root.addView((View)header);
        root.addView((View)scroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.helpContainer.addView((View)root);
        this.flip(this.homeContent, (View)this.helpContainer);
    }

    private void hideHelpCenter() {
        this.flip((View)this.helpContainer, this.homeContent);
    }

    private TextView createHelpSectionTitle(int titleRes) {
        TextView title = new TextView(this.requireContext());
        title.setText(titleRes);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(20.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, this.dp(4), 0, this.dp(10));
        return title;
    }

    private View createHelpFeatureCard(int titleRes, int bodyRes) {
        LinearLayout card = this.createHelpCard();
        card.addView((View)this.createHelpCardTitle(titleRes));
        card.addView((View)this.createHelpBodyText((CharSequence)this.getString(bodyRes)));
        return card;
    }

    private View createHelpWebDavCard() {
        LinearLayout card = this.createHelpCard();
        card.addView((View)this.createHelpCardTitle(R.string.help_webdav_title));
        TextView subtitle = this.createHelpBodyText((CharSequence)this.getString(R.string.help_webdav_subtitle));
        subtitle.setPadding(0, 0, 0, this.dp(8));
        card.addView((View)subtitle);
        this.addHelpProviderSection(card, R.string.help_webdav_jianguoyun_title, R.string.help_webdav_jianguoyun_space, R.string.help_webdav_jianguoyun_url, R.string.help_webdav_jianguoyun_steps);
        this.addHelpProviderSection(card, R.string.help_webdav_koofr_title, R.string.help_webdav_koofr_space, R.string.help_webdav_koofr_url, R.string.help_webdav_koofr_steps);
        card.addView((View)this.createHelpSubTitle(R.string.help_webdav_recommended_title));
        card.addView((View)this.createHelpBodyText((CharSequence)this.getString(R.string.help_webdav_recommended_body)));
        return card;
    }

    private void addHelpProviderSection(LinearLayout card, int titleRes, int spaceRes, int urlRes, int stepsRes) {
        card.addView((View)this.createHelpSubTitle(titleRes));
        card.addView((View)this.createHelpBodyText((CharSequence)this.getString(spaceRes)));
        TextView url = this.createHelpBodyText((CharSequence)this.getString(urlRes));
        url.setTextColor(this.getResources().getColor(R.color.primary));
        card.addView((View)url);
        TextView steps = this.createHelpBodyText((CharSequence)this.getString(stepsRes));
        steps.setPadding(0, this.dp(4), 0, this.dp(10));
        card.addView((View)steps);
    }

    private LinearLayout createHelpCard() {
        LinearLayout card = new LinearLayout(this.requireContext());
        card.setOrientation(1);
        card.setPadding(this.dp(16), this.dp(14), this.dp(16), this.dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius((float)this.dp(16));
        background.setColor(this.getResources().getColor(R.color.card_background));
        background.setStroke(this.dp(1), this.getResources().getColor(R.color.card_stroke));
        card.setBackground(background);
        card.setElevation((float)this.dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = this.dp(12);
        card.setLayoutParams((ViewGroup.LayoutParams)params);
        return card;
    }

    private TextView createHelpCardTitle(int titleRes) {
        TextView title = new TextView(this.requireContext());
        title.setText(titleRes);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(16.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, this.dp(6));
        return title;
    }

    private TextView createHelpSubTitle(int titleRes) {
        TextView title = new TextView(this.requireContext());
        title.setText(titleRes);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(15.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, this.dp(8), 0, this.dp(4));
        return title;
    }

    private TextView createHelpBodyText(CharSequence body) {
        TextView text = new TextView(this.requireContext());
        text.setText(body);
        text.setTextColor(this.getResources().getColor(R.color.text_secondary));
        text.setTextSize(14.0f);
        text.setLineSpacing(0.0f, 1.12f);
        return text;
    }

    private void openEmailFeedback() {
        String version = this.appVersion();
        String subject = Uri.encode((String)this.getString(R.string.feedback_subject_template, new Object[]{version}));
        String body = Uri.encode((String)this.getString(R.string.feedback_body_template, new Object[]{version, Build.MODEL, Build.VERSION.RELEASE}));
        Uri mailUri = Uri.parse((String)("mailto:userfeedback@zohomail.com?subject=" + subject + "&body=" + body));
        Intent intent = new Intent("android.intent.action.SENDTO", mailUri);
        try {
            this.startActivity(intent);
        }
        catch (ActivityNotFoundException error) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)this.getString(R.string.no_email_app, new Object[]{CONTACT_EMAIL}), (int)1).show();
        }
    }

    private void openMail(String subject, String body) {
        Uri mailUri = Uri.parse((String)("mailto:userfeedback@zohomail.com?subject=" + Uri.encode((String)subject) + "&body=" + Uri.encode((String)body)));
        Intent intent = new Intent("android.intent.action.SENDTO", mailUri);
        try {
            this.startActivity(intent);
        }
        catch (ActivityNotFoundException error) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)this.getString(R.string.no_email_app, new Object[]{CONTACT_EMAIL}), (int)1).show();
        }
    }

    private void showDonateDialog() {
        Bitmap wechatQr = BitmapFactory.decodeResource((android.content.res.Resources)this.getResources(), (int)R.drawable.donate_wechat_qr);
        Bitmap alipayQr = BitmapFactory.decodeResource((android.content.res.Resources)this.getResources(), (int)R.drawable.donate_alipay_qr);
        LinearLayout content = new LinearLayout(this.requireContext());
        content.setOrientation(1);
        content.setPadding(this.dp(18), this.dp(14), this.dp(18), this.dp(8));
        content.setBackgroundColor(this.getResources().getColor(R.color.surface_light));
        TextView message = new TextView(this.requireContext());
        message.setText((CharSequence)this.getString(R.string.donate_message));
        message.setTextColor(this.getResources().getColor(R.color.text_main));
        message.setTextSize(16.0f);
        message.setTypeface(Typeface.DEFAULT_BOLD);
        message.setLineSpacing(0.0f, 1.08f);
        TextView hint = new TextView(this.requireContext());
        hint.setText((CharSequence)this.getString(R.string.donate_tap_hint));
        hint.setTextColor(this.getResources().getColor(R.color.text_secondary));
        hint.setTextSize(13.0f);
        hint.setPadding(0, this.dp(6), 0, this.dp(14));
        LinearLayout cards = new LinearLayout(this.requireContext());
        cards.setOrientation(1);
        cards.addView((View)this.createDonateCard(this.getString(R.string.wechat), 0xFF07C160, "W", "wechat", wechatQr));
        cards.addView((View)this.createDonateCard(this.getString(R.string.alipay), 0xFF1677FF, "A", "alipay", alipayQr));
        content.addView((View)message);
        content.addView((View)hint);
        content.addView((View)cards);
        ScrollView scroll = new ScrollView(this.requireContext());
        scroll.setFillViewport(true);
        scroll.addView((View)content);
        new AlertDialog.Builder(this.requireContext()).setTitle(R.string.donate_title).setView((View)scroll).setPositiveButton(R.string.thanks, null).show();
    }

    private DonateFlipCardView createDonateCard(String label, int brandColor, String badgeText, String filePrefix, Bitmap bitmap) {
        DonateFlipCardView card = new DonateFlipCardView(this.requireContext(), label, brandColor, badgeText, filePrefix, bitmap);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, this.dp(170));
        params.topMargin = this.dp(10);
        card.setLayoutParams((ViewGroup.LayoutParams)params);
        return card;
    }

    private void showDonateQrPreview(String label, String filePrefix, Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        FrameLayout root = new FrameLayout(this.requireContext());
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(this.dp(16), this.dp(16), this.dp(16), this.dp(16));
        ImageView image = new ImageView(this.requireContext());
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription((CharSequence)label);
        image.setOnLongClickListener(v -> {
            this.showDonateSaveDialog(label, filePrefix, bitmap);
            return true;
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
        params.gravity = 17;
        root.addView((View)image, (ViewGroup.LayoutParams)params);
        AlertDialog dialog = new AlertDialog.Builder(this.requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).setTitle((CharSequence)(this.getString(R.string.preview_qr) + " - " + label)).setView((View)root).setPositiveButton(R.string.close, null).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        }
    }

    private void showDonateSaveDialog(String label, String filePrefix, Bitmap bitmap) {
        new AlertDialog.Builder(this.requireContext()).setTitle(R.string.save_image).setMessage((CharSequence)(this.getString(R.string.save_image) + " " + label + "?")).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save_image, (dialog, which) -> this.saveBitmapToGallery(bitmap, "donate_" + filePrefix + "_" + System.currentTimeMillis() + ".png")).show();
    }

    private void saveBitmapToGallery(Bitmap bitmap, String fileName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KeyScan");
            Uri uri = this.requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException(this.getString(R.string.image_create_failed));
            }
            try (OutputStream out = this.requireContext().getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException(this.getString(R.string.image_create_failed));
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Toast.makeText((Context)this.requireContext(), (int)R.string.saved_to_album, (int)0).show();
        }
        catch (Exception e) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)this.getString(R.string.save_failed, new Object[]{e.getMessage()}), (int)0).show();
        }
    }

    private final class DonateFlipCardView
    extends FrameLayout {
        private final FrameLayout frontFace;
        private final FrameLayout backFace;
        private boolean showingBack;
        private boolean animating;

        DonateFlipCardView(Context context, String label, int brandColor, String badgeText, String filePrefix, Bitmap bitmap) {
            super(context);
            this.setClickable(true);
            this.setFocusable(true);
            this.setCameraDistance((float)HomeFragment.this.dp(24000));
            this.setElevation((float)HomeFragment.this.dp(4));
            this.setBackground(this.makeCardBackground());
            int padding = HomeFragment.this.dp(14);
            this.frontFace = this.buildFrontFace(label, brandColor, badgeText, padding);
            this.backFace = this.buildBackFace(label, filePrefix, bitmap, padding);
            this.backFace.setVisibility(8);
            this.backFace.setRotationY(180.0f);
            this.addView((View)this.frontFace, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
            this.addView((View)this.backFace, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
            this.setOnClickListener(v -> this.toggleFace());
        }

        private FrameLayout buildFrontFace(String label, int brandColor, String badgeText, int padding) {
            FrameLayout face = new FrameLayout(this.getContext());
            face.setPadding(padding, padding, padding, padding);
            LinearLayout column = new LinearLayout(this.getContext());
            column.setOrientation(1);
            column.setGravity(17);
            TextView badge = new TextView(this.getContext());
            badge.setText((CharSequence)badgeText);
            badge.setTextColor(-1);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextSize(14.0f);
            badge.setGravity(17);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(1);
            badgeBg.setColor(brandColor);
            badge.setBackground(badgeBg);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(HomeFragment.this.dp(44), HomeFragment.this.dp(44));
            badgeParams.bottomMargin = HomeFragment.this.dp(10);
            TextView title = new TextView(this.getContext());
            title.setText((CharSequence)label);
            title.setTextColor(brandColor);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextSize(18.0f);
            title.setGravity(17);
            TextView tip = new TextView(this.getContext());
            tip.setText((CharSequence)HomeFragment.this.getString(R.string.donate_tap_hint));
            tip.setTextColor(HomeFragment.this.getResources().getColor(R.color.text_secondary));
            tip.setTextSize(12.0f);
            tip.setGravity(17);
            tip.setPadding(0, HomeFragment.this.dp(6), 0, 0);
            column.addView((View)badge, (ViewGroup.LayoutParams)badgeParams);
            column.addView((View)title);
            column.addView((View)tip);
            face.addView((View)column, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1, 17));
            return face;
        }

        private FrameLayout buildBackFace(String label, String filePrefix, Bitmap bitmap, int padding) {
            FrameLayout face = new FrameLayout(this.getContext());
            face.setPadding(padding, padding, padding, padding);
            ImageView qr = new ImageView(this.getContext());
            qr.setImageBitmap(bitmap);
            qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
            qr.setAdjustViewBounds(true);
            qr.setContentDescription((CharSequence)label);
            qr.setOnClickListener(v -> this.toggleFace());
            qr.setOnLongClickListener(v -> {
                HomeFragment.this.showDonateSaveDialog(label, filePrefix, bitmap);
                return true;
            });
            FrameLayout.LayoutParams qrParams = new FrameLayout.LayoutParams(-1, -1);
            qrParams.gravity = 17;
            face.addView((View)qr, (ViewGroup.LayoutParams)qrParams);
            return face;
        }

        private void toggleFace() {
            if (this.animating) {
                return;
            }
            this.flipTo(!this.showingBack);
        }

        private void flipTo(boolean showBack) {
            if (showBack == this.showingBack) {
                return;
            }
            this.animating = true;
            final float start = this.showingBack ? 180.0f : 0.0f;
            final float end = showBack ? 180.0f : 0.0f;
            final boolean[] swapped = new boolean[]{false};
            ValueAnimator animator = ValueAnimator.ofFloat((float[])new float[]{start, end});
            animator.setDuration(400L);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                float value = ((Float)animation.getAnimatedValue()).floatValue();
                this.setRotationY(value);
                if (!swapped[0] && (showBack && value >= 90.0f || !showBack && value <= 90.0f)) {
                    this.frontFace.setVisibility(showBack ? 8 : 0);
                    this.backFace.setVisibility(showBack ? 0 : 8);
                    swapped[0] = true;
                }
            });
            animator.addListener(new AnimatorListenerAdapter(){
                @Override
                public void onAnimationEnd(Animator animation) {
                    DonateFlipCardView.this.showingBack = showBack;
                    DonateFlipCardView.this.animating = false;
                    DonateFlipCardView.this.setRotationY(end);
                    DonateFlipCardView.this.frontFace.setVisibility(showBack ? 8 : 0);
                    DonateFlipCardView.this.backFace.setVisibility(showBack ? 0 : 8);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    DonateFlipCardView.this.animating = false;
                }
            });
            animator.start();
        }

        private GradientDrawable makeCardBackground() {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius((float)HomeFragment.this.dp(12));
            drawable.setColor(HomeFragment.this.getResources().getColor(R.color.card_background));
            drawable.setStroke(HomeFragment.this.dp(1), HomeFragment.this.getResources().getColor(R.color.card_stroke));
            return drawable;
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */




    private String appVersion() {
        try {
            return this.requireContext().getPackageManager().getPackageInfo((String)this.requireContext().getPackageName(), (int)0).versionName;
        }
        catch (Exception ignored) {
            return "1.0.0";
        }
    }

    private int dp(int value) {
        return Math.round((float)value * this.getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams topLayoutParams(int height, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.topMargin = this.dp(topMargin);
        return params;
    }

    private void shareAppInfo() {
        String shareText = getString(R.string.keyscan_share_text);
        Intent intent = new Intent("android.intent.action.SEND");
        intent.setType("text/plain");
        intent.putExtra("android.intent.extra.SUBJECT", "KeyScan");
        intent.putExtra("android.intent.extra.TEXT", shareText);
        try {
            this.startActivity(Intent.createChooser((Intent)intent, getString(R.string.share_keyscan)));
        }
        catch (ActivityNotFoundException error) {
            Toast.makeText((Context)this.requireContext(), R.string.no_share_app_available, (int)0).show();
        }
    }

    public void onDetach() {
        super.onDetach();
        this.actions = null;
    }

    public static interface HomeActions {
        public void openScanner();

        public void openGenerator();

        public void openHistory();

        public void openWebDav();

        public void openExport();

        public void openGenericExport();

        public void openTrash();


        public void openNewPasswordRecord();

        public void openNewSecureItem();

        public void openPasswordBookImport();

        public void openOtpManualImport();

        public void openOtpBatchImport();

        public void openPasswordNotes();

        public void openAppearance();

        public void openSecurityCenter();

        public void openAbout();

        public void openPasswordForge();

        public void openRandomPasswordGenerator();

        public void openOtpAuth();
    }
}
