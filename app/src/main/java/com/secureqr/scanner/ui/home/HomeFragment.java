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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.secureqr.scanner.R;
import com.secureqr.scanner.ui.home.PuzzleGameView;
import com.secureqr.scanner.ui.home.SokobanGameView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class HomeFragment
extends Fragment {
    private static final String OPEN_SOURCE_URL = "https://github.com/avatartulkun/keyscan";
    private static final String CONTACT_EMAIL = "tulkun@foxmail.com";
    private int puzzleTapCount;
    private View homeContent;
    private FrameLayout puzzleContainer;
    private PuzzleGameView puzzleView;
    private SokobanGameView sokobanView;
    private TetrisGameView tetrisView;
    private ImageView puzzleReferencePreview;
    private TextView puzzleStats;
    private final boolean[] sokobanSolved = new boolean[SokobanGameView.getLevelCount()];
    private int sokobanUnlockedLevel = 0;
    private int pendingSokobanLevel = 0;
    private boolean gameVibrationEnabled = true;
    private ActivityResultLauncher<String> puzzleImagePicker;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable(){

        @Override
        public void run() {
            HomeFragment.this.updatePuzzleStats();
            HomeFragment.this.timerHandler.postDelayed((Runnable)this, 1000L);
        }
    };
    private HomeActions actions;

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.puzzleImagePicker = this.registerForActivityResult((ActivityResultContract)new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }
            Bitmap bitmap = this.decodeSquareBitmap((Uri)uri, 900);
            if (bitmap == null) {
                Toast.makeText((Context)this.requireContext(), (int)R.string.image_load_failed, (int)0).show();
                return;
            }
            this.saveCustomPuzzleBitmap(bitmap);
            if (this.puzzleView != null) {
                this.puzzleView.setCustomBitmap(bitmap);
                this.puzzleView.reset(this.puzzleView.getSizeMode());
                this.updatePuzzleReferencePreview(this.puzzleReferencePreview);
                this.updatePuzzleStats();
            }
            Toast.makeText((Context)this.requireContext(), (int)R.string.custom_image_loaded, (int)0).show();
        });
    }

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
        view.findViewById(R.id.tool_export).setOnClickListener(v -> {
            if (this.actions != null) {
                this.actions.openExport();
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
        this.puzzleContainer = (FrameLayout)view.findViewById(R.id.puzzle_container);
        view.findViewById(R.id.tv_puzzle_hint).setOnClickListener(v -> {
            this.vibrateLight();
            ++this.puzzleTapCount;
            if (this.puzzleTapCount >= 3) {
                this.puzzleTapCount = 0;
                this.showPuzzleGame();
            }
        });
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this.requireContext(), anchor);
        menu.getMenu().add(R.string.settings_title);
        menu.getMenu().add(R.string.feedback);
        menu.getMenu().add(R.string.donate_menu);
        menu.getMenu().add(R.string.help_title);
        menu.getMenu().add(R.string.about_keyscan);
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (this.getString(R.string.settings_title).equals(title)) {
                if (this.actions != null) {
                    this.actions.openAppearance();
                }
            } else if (this.getString(R.string.feedback).equals(title)) {
                this.openEmailFeedback();
            } else if (this.getString(R.string.donate_menu).equals(title)) {
                this.showDonateDialog();
            } else if (this.getString(R.string.help_title).equals(title)) {
                this.showHelpCenter();
            } else if (this.getString(R.string.about_keyscan).equals(title)) {
                new AlertDialog.Builder(this.requireContext()).setTitle(R.string.about_keyscan_title).setMessage((CharSequence)this.getString(R.string.about_keyscan_message, new Object[]{OPEN_SOURCE_URL, CONTACT_EMAIL})).setPositiveButton(R.string.ok, null).show();
            }
            return true;
        });
        menu.show();
    }

    private void showPuzzleGame() {
        this.buildGameLobby();
        this.flip(this.homeContent, (View)this.puzzleContainer);
    }

    private void hidePuzzleGame() {
        this.flip((View)this.puzzleContainer, this.homeContent);
        this.timerHandler.removeCallbacks(this.timerTick);
        if (this.tetrisView != null) {
            this.tetrisView.stop();
        }
    }

    private void buildGameLobby() {
        if (this.tetrisView != null) {
            this.tetrisView.stop();
            this.tetrisView = null;
        }
        this.puzzleContainer.removeAllViews();
        LinearLayout root = new LinearLayout(this.requireContext());
        root.setOrientation(1);
        root.setGravity(17);
        root.setPadding(this.dp(24), this.dp(28), this.dp(24), this.dp(24));
        root.setBackgroundColor(this.getResources().getColor(R.color.surface_light));
        TextView title = new TextView(this.requireContext());
        title.setText(R.string.game_lobby);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(26.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(17);
        Button puzzle = new Button(this.requireContext());
        puzzle.setText(R.string.puzzle_game);
        Button sokoban = new Button(this.requireContext());
        sokoban.setText(R.string.sokoban_game);
        Button tetris = new Button(this.requireContext());
        tetris.setText(R.string.tetris_game);
        Button back = new Button(this.requireContext());
        back.setText(R.string.back);
        root.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(60)));
        root.addView((View)puzzle, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(48)));
        root.addView((View)sokoban, (ViewGroup.LayoutParams)this.topLayoutParams(this.dp(48), 12));
        root.addView((View)tetris, (ViewGroup.LayoutParams)this.topLayoutParams(this.dp(48), 12));
        root.addView((View)back, (ViewGroup.LayoutParams)this.topLayoutParams(this.dp(48), 12));
        puzzle.setOnClickListener(v -> this.buildPuzzleScreen());
        sokoban.setOnClickListener(v -> this.buildSokobanScreen());
        tetris.setOnClickListener(v -> this.buildTetrisScreen());
        back.setOnClickListener(v -> this.hidePuzzleGame());
        this.puzzleContainer.addView((View)root);
    }

    private void buildPuzzleScreen() {
        this.puzzleContainer.removeAllViews();
        LinearLayout root = new LinearLayout(this.requireContext());
        root.setOrientation(1);
        root.setPadding(this.dp(18), this.dp(28), this.dp(18), this.dp(18));
        root.setBackgroundColor(this.getResources().getColor(R.color.surface_light));
        LinearLayout header = new LinearLayout(this.requireContext());
        header.setGravity(16);
        header.setOrientation(0);
        TextView title = new TextView(this.requireContext());
        title.setText(R.string.puzzle_game);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(18.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        Button mode = new Button(this.requireContext());
        mode.setText((CharSequence)"4\u00d74");
        Button imageMode = new Button(this.requireContext());
        imageMode.setText(R.string.image_mode);
        Button preset = new Button(this.requireContext());
        preset.setText(R.string.next_picture);
        Button back = new Button(this.requireContext());
        back.setText(R.string.back);
        header.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(44), 1.0f));
        header.addView((View)imageMode, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(58), this.dp(40)));
        header.addView((View)preset, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(58), this.dp(40)));
        header.addView((View)mode, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(58), this.dp(40)));
        header.addView((View)back, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(58), this.dp(40)));
        this.puzzleStats = new TextView(this.requireContext());
        this.puzzleStats.setTextColor(this.getResources().getColor(R.color.text_secondary));
        this.puzzleStats.setTextSize(14.0f);
        this.puzzleStats.setPadding(0, this.dp(8), 0, this.dp(8));
        this.puzzleView = new PuzzleGameView(this.requireContext());
        Bitmap savedPuzzleBitmap = this.loadCustomPuzzleBitmap();
        if (savedPuzzleBitmap != null) {
            this.puzzleView.setCustomBitmap(savedPuzzleBitmap);
        }
        this.puzzleReferencePreview = new ImageView(this.requireContext());
        this.puzzleReferencePreview.setBackgroundResource(R.drawable.bg_card);
        this.puzzleReferencePreview.setPadding(this.dp(4), this.dp(4), this.dp(4), this.dp(4));
        this.puzzleReferencePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        this.puzzleReferencePreview.setVisibility(this.puzzleView.isPictureMode() ? 0 : 8);
        this.updatePuzzleReferencePreview(this.puzzleReferencePreview);
        this.puzzleReferencePreview.setOnClickListener(v -> this.showPuzzleReferenceDialog());
        this.puzzleView.setListener(new PuzzleGameView.Listener(){

            @Override
            public void onStatsChanged(int moves, int seconds) {
                HomeFragment.this.updatePuzzleStats();
            }

            @Override
            public void onSolved(int moves, int seconds) {
                HomeFragment.this.showPuzzleSolvedDialog(moves, seconds);
            }
        });
        mode.setOnClickListener(v -> {
            int next = this.puzzleView.getSizeMode() == 3 ? 4 : 3;
            this.puzzleView.reset(next);
            mode.setText((CharSequence)(next == 3 ? "4\u00d74" : "3\u00d73"));
        });
        imageMode.setOnClickListener(v -> {
            this.puzzleView.setPictureMode(!this.puzzleView.isPictureMode());
            imageMode.setText(this.puzzleView.isPictureMode() ? R.string.number_mode : R.string.image_mode);
            this.updatePuzzleReferencePreview(this.puzzleReferencePreview);
            this.updatePuzzleStats();
        });
        preset.setOnClickListener(v -> {
            this.puzzleView.nextPicturePreset();
            this.updatePuzzleReferencePreview(this.puzzleReferencePreview);
            Toast.makeText((Context)this.requireContext(), (CharSequence)this.getString(R.string.preset_picture_changed, new Object[]{this.puzzleView.getPicturePreset() + 1}), (int)0).show();
        });
        back.setOnClickListener(v -> {
            this.timerHandler.removeCallbacks(this.timerTick);
            this.buildGameLobby();
        });
        LinearLayout imageTools = new LinearLayout(this.requireContext());
        imageTools.setOrientation(0);
        Button uploadImage = new Button(this.requireContext());
        uploadImage.setText(R.string.upload_image);
        Button restoreDefault = new Button(this.requireContext());
        restoreDefault.setText(R.string.restore_default_image);
        imageTools.addView((View)uploadImage, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(40), 1.0f));
        imageTools.addView((View)restoreDefault, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(40), 1.0f));
        uploadImage.setOnClickListener(v -> this.puzzleImagePicker.launch("image/*"));
        restoreDefault.setOnClickListener(v -> {
            this.deleteCustomPuzzleBitmap();
            if (this.puzzleView != null) {
                this.puzzleView.setCustomBitmap(null);
                this.puzzleView.setPictureMode(true);
                this.puzzleView.reset(this.puzzleView.getSizeMode());
                this.updatePuzzleReferencePreview(this.puzzleReferencePreview);
                this.updatePuzzleStats();
            }
            Toast.makeText((Context)this.requireContext(), (int)R.string.default_image_restored, (int)0).show();
        });
        root.addView((View)header);
        root.addView((View)this.puzzleStats);
        root.addView((View)imageTools);
        FrameLayout puzzleArea = new FrameLayout(this.requireContext());
        puzzleArea.addView((View)this.puzzleView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(this.dp(88), this.dp(88), 0x800033);
        previewParams.leftMargin = this.dp(12);
        previewParams.topMargin = this.dp(12);
        puzzleArea.addView((View)this.puzzleReferencePreview, (ViewGroup.LayoutParams)previewParams);
        root.addView((View)puzzleArea, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.puzzleContainer.addView((View)root);
        this.updatePuzzleStats();
        this.timerHandler.post(this.timerTick);
    }

    private void buildSokobanScreen() {
        this.timerHandler.removeCallbacks(this.timerTick);
        this.puzzleContainer.removeAllViews();
        LinearLayout root = new LinearLayout(this.requireContext());
        root.setOrientation(1);
        root.setPadding(this.dp(18), this.dp(28), this.dp(18), this.dp(18));
        root.setBackgroundColor(this.getResources().getColor(R.color.surface_light));
        LinearLayout header = new LinearLayout(this.requireContext());
        header.setGravity(16);
        header.setOrientation(0);
        TextView title = new TextView(this.requireContext());
        title.setText(R.string.sokoban_game);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(24.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        Button reset = new Button(this.requireContext());
        reset.setText(R.string.reset);
        this.applyPrimaryActionButtonStyle(reset);
        Button back = new Button(this.requireContext());
        back.setText(R.string.back);
        this.applyPrimaryActionButtonStyle(back);
        header.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(44), 1.0f));
        header.addView((View)reset, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(66), this.dp(40)));
        header.addView((View)back, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(66), this.dp(40)));
        this.puzzleStats = new TextView(this.requireContext());
        this.puzzleStats.setTextColor(this.getResources().getColor(R.color.text_secondary));
        this.puzzleStats.setTextSize(14.0f);
        this.puzzleStats.setPadding(0, this.dp(8), 0, this.dp(8));
        this.sokobanView = new SokobanGameView(this.requireContext());
        this.sokobanView.setVibrationEnabled(this.gameVibrationEnabled);
        this.sokobanView.setListener(new SokobanGameView.Listener(){

            @Override
            public void onStatsChanged(int level, int moves) {
                HomeFragment.this.puzzleStats.setText((CharSequence)HomeFragment.this.getString(R.string.level_moves, new Object[]{level, moves}));
            }

            @Override
            public void onSolved(int level, int moves) {
                if (level > 0 && level <= HomeFragment.this.sokobanSolved.length) {
                    HomeFragment.this.sokobanSolved[level - 1] = true;
                }
                HomeFragment.this.sokobanUnlockedLevel = Math.max(HomeFragment.this.sokobanUnlockedLevel, Math.min(SokobanGameView.getLevelCount() - 1, level));
                if (level == SokobanGameView.getLevelCount()) {
                    Toast.makeText((Context)HomeFragment.this.requireContext(), (int)R.string.all_levels_complete, (int)0).show();
                    HomeFragment.this.showGameSolvedDialog(HomeFragment.this.getString(R.string.sokoban_game), HomeFragment.this.getString(R.string.level_mode, new Object[]{level}), 0, moves);
                } else {
                    new AlertDialog.Builder(HomeFragment.this.requireContext()).setTitle(R.string.level_unlocked_title).setNegativeButton(R.string.stay_current_level, null).setPositiveButton(R.string.enter_next_level, (dialog, which) -> {
                        HomeFragment.this.pendingSokobanLevel = level;
                        HomeFragment.this.buildSokobanScreen();
                    }).show();
                }
            }
        });
        LinearLayout levelGrid = new LinearLayout(this.requireContext());
        levelGrid.setOrientation(1);
        for (int rowIndex = 0; rowIndex < 2; ++rowIndex) {
            LinearLayout levelRow = new LinearLayout(this.requireContext());
            levelRow.setOrientation(0);
            levelGrid.addView((View)levelRow, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(38)));
            for (int column = 0; column < 8; ++column) {
                int i = rowIndex * 8 + column;
                if (i >= SokobanGameView.getLevelCount()) {
                    break;
                }
            int level = i;
            Button levelButton = new Button(this.requireContext());
            boolean locked = i > this.sokobanUnlockedLevel;
            boolean solved = this.sokobanSolved[i];
            levelButton.setText((CharSequence)(locked ? "\ud83d\udd12" : (solved ? i + 1 + "\u2713" : String.valueOf(i + 1))));
            levelButton.setEnabled(!locked);
            if (locked) {
                this.applyMutedGameButtonStyle(levelButton);
            } else {
                this.applyPrimaryActionButtonStyle(levelButton);
            }
            levelButton.setOnClickListener(v -> this.sokobanView.loadLevel(level));
                levelRow.addView((View)levelButton, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(36), 1.0f));
            }
        }
        Switch vibration = this.createGameVibrationSwitch();
        LinearLayout controlsUp = new LinearLayout(this.requireContext());
        controlsUp.setGravity(17);
        Button up = this.createRoundControlButton("\u2191");
        controlsUp.addView((View)up, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(58), this.dp(58)));
        LinearLayout controlsMiddle = new LinearLayout(this.requireContext());
        controlsMiddle.setGravity(17);
        Button left = this.createRoundControlButton("\u2190");
        Button undo = new Button(this.requireContext());
        undo.setText(R.string.undo_step);
        undo.setTextSize(12.0f);
        this.applyPrimaryActionButtonStyle(undo);
        Button right = this.createRoundControlButton("\u2192");
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(this.dp(58), this.dp(58));
        leftParams.leftMargin = this.dp(5);
        leftParams.rightMargin = this.dp(5);
        controlsMiddle.addView((View)left, (ViewGroup.LayoutParams)leftParams);
        controlsMiddle.addView((View)undo, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(78), this.dp(58)));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(this.dp(58), this.dp(58));
        rightParams.leftMargin = this.dp(5);
        controlsMiddle.addView((View)right, (ViewGroup.LayoutParams)rightParams);
        LinearLayout controlsDown = new LinearLayout(this.requireContext());
        controlsDown.setGravity(17);
        Button down = this.createRoundControlButton("\u2193");
        controlsDown.addView((View)down, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(58), this.dp(58)));
        reset.setOnClickListener(v -> this.sokobanView.resetLevel());
        undo.setOnClickListener(v -> {
            this.vibrateGame();
            this.sokobanView.undo();
        });
        back.setOnClickListener(v -> this.buildGameLobby());
        up.setOnClickListener(v -> {
            this.vibrateGame();
            this.sokobanView.move(-1, 0);
        });
        down.setOnClickListener(v -> {
            this.vibrateGame();
            this.sokobanView.move(1, 0);
        });
        left.setOnClickListener(v -> {
            this.vibrateGame();
            this.sokobanView.move(0, -1);
        });
        right.setOnClickListener(v -> {
            this.vibrateGame();
            this.sokobanView.move(0, 1);
        });
        root.addView((View)header);
        root.addView((View)this.puzzleStats);
        root.addView((View)vibration, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(42)));
        root.addView((View)levelGrid);
        root.addView((View)this.sokobanView, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        root.addView((View)controlsUp);
        root.addView((View)controlsMiddle);
        root.addView((View)controlsDown);
        this.puzzleContainer.addView((View)root);
        if (this.pendingSokobanLevel > 0) {
            int nextLevel = this.pendingSokobanLevel;
            this.pendingSokobanLevel = 0;
            this.sokobanView.loadLevel(nextLevel);
        }
    }

    private void buildTetrisScreen() {
        this.timerHandler.removeCallbacks(this.timerTick);
        if (this.tetrisView != null) {
            this.tetrisView.stop();
        }
        this.puzzleContainer.removeAllViews();
        LinearLayout root = new LinearLayout(this.requireContext());
        root.setOrientation(1);
        root.setPadding(this.dp(18), this.dp(28), this.dp(18), this.dp(18));
        root.setBackgroundColor(this.getResources().getColor(R.color.surface_light));
        LinearLayout header = new LinearLayout(this.requireContext());
        header.setGravity(16);
        header.setOrientation(0);
        TextView title = new TextView(this.requireContext());
        title.setText(R.string.tetris_game);
        title.setTextColor(this.getResources().getColor(R.color.text_main));
        title.setTextSize(24.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        Button reset = new Button(this.requireContext());
        reset.setText(R.string.reset);
        this.applyPrimaryActionButtonStyle(reset);
        Button back = new Button(this.requireContext());
        back.setText(R.string.back);
        this.applyPrimaryActionButtonStyle(back);
        header.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(44), 1.0f));
        header.addView((View)reset, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(66), this.dp(40)));
        header.addView((View)back, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(66), this.dp(40)));
        this.puzzleStats = new TextView(this.requireContext());
        this.puzzleStats.setTextColor(this.getResources().getColor(R.color.text_secondary));
        this.puzzleStats.setTextSize(14.0f);
        this.puzzleStats.setPadding(0, this.dp(8), 0, this.dp(8));
        this.tetrisView = new TetrisGameView(this.requireContext());
        this.tetrisView.setVibrationEnabled(this.gameVibrationEnabled);
        this.tetrisView.setListener(new TetrisGameView.Listener(){

            @Override
            public void onStatsChanged(int score, int lines, int level) {
                HomeFragment.this.puzzleStats.setText((CharSequence)HomeFragment.this.getString(R.string.tetris_stats, new Object[]{score, lines, level}));
            }

            @Override
            public void onGameOver(int score) {
                Toast.makeText((Context)HomeFragment.this.requireContext(), (int)R.string.tetris_game_over, (int)0).show();
            }
        });
        Switch vibration = this.createGameVibrationSwitch();
        LinearLayout controlsRotate = new LinearLayout(this.requireContext());
        controlsRotate.setGravity(17);
        Button rotate = this.createPrimaryControlButton(R.string.rotate);
        controlsRotate.addView((View)rotate, (ViewGroup.LayoutParams)this.controlButtonParams(0, 0));
        LinearLayout controlsMove = new LinearLayout(this.requireContext());
        controlsMove.setGravity(17);
        Button left = this.createRoundControlButton("\u2190");
        Button right = this.createRoundControlButton("\u2192");
        Button down = this.createRoundControlButton("\u2193");
        controlsMove.addView((View)left, (ViewGroup.LayoutParams)this.controlButtonParams(0, 6));
        controlsMove.addView((View)down, (ViewGroup.LayoutParams)this.controlButtonParams(6, 6));
        controlsMove.addView((View)right, (ViewGroup.LayoutParams)this.controlButtonParams(6, 0));
        LinearLayout controlsDrop = new LinearLayout(this.requireContext());
        controlsDrop.setGravity(17);
        Button drop = this.createPrimaryControlButton(R.string.drop);
        controlsDrop.addView((View)drop, (ViewGroup.LayoutParams)this.controlButtonParams(0, 0));
        reset.setOnClickListener(v -> {
            this.vibrateGame();
            this.tetrisView.resetGame();
        });
        back.setOnClickListener(v -> {
            if (this.tetrisView != null) {
                this.tetrisView.stop();
            }
            this.buildGameLobby();
        });
        left.setOnClickListener(v -> {
            this.vibrateGame();
            this.tetrisView.moveLeft();
        });
        right.setOnClickListener(v -> {
            this.vibrateGame();
            this.tetrisView.moveRight();
        });
        down.setOnClickListener(v -> {
            this.vibrateGame();
            this.tetrisView.softDrop();
        });
        rotate.setOnClickListener(v -> {
            this.vibrateGame();
            this.tetrisView.rotate();
        });
        drop.setOnClickListener(v -> this.tetrisView.hardDrop());
        root.addView((View)header);
        root.addView((View)this.puzzleStats);
        root.addView((View)vibration, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(42)));
        root.addView((View)this.tetrisView, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        root.addView((View)controlsRotate);
        root.addView((View)controlsMove);
        root.addView((View)controlsDrop);
        this.puzzleContainer.addView((View)root);
        this.tetrisView.startGame();
    }

    private Button createRoundControlButton(CharSequence text) {
        Button button = new Button(this.requireContext());
        button.setText(text);
        button.setTextSize(22.0f);
        button.setTextColor(this.gameButtonTextColor());
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(17);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(this.getResources().getColor(R.color.primary));
        background.setStroke(this.dp(1), this.getResources().getColor(R.color.card_stroke));
        button.setBackground(background);
        button.setElevation((float)this.dp(3));
        return button;
    }

    private Button createPrimaryControlButton(int textRes) {
        Button button = new Button(this.requireContext());
        button.setText(textRes);
        this.applyPrimaryActionButtonStyle(button);
        button.setTextSize(12.0f);
        return button;
    }

    private void applyPrimaryActionButtonStyle(Button button) {
        button.setTextColor(this.gameButtonTextColor());
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(17);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(this.dp(4), 0, this.dp(4), 0);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius((float)this.dp(14));
        background.setColor(this.getResources().getColor(R.color.primary));
        background.setStroke(this.dp(1), this.getResources().getColor(R.color.card_stroke));
        button.setBackground(background);
        button.setElevation((float)this.dp(3));
    }

    private void applyMutedGameButtonStyle(Button button) {
        button.setTextColor(this.getResources().getColor(R.color.text_secondary));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(17);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(this.dp(4), 0, this.dp(4), 0);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius((float)this.dp(10));
        background.setColor(this.getResources().getColor(R.color.card_background));
        background.setStroke(this.dp(1), this.getResources().getColor(R.color.card_stroke));
        button.setBackground(background);
    }

    private LinearLayout.LayoutParams controlButtonParams(int leftMargin, int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(this.dp(58), this.dp(58));
        params.leftMargin = this.dp(leftMargin);
        params.rightMargin = this.dp(rightMargin);
        params.topMargin = this.dp(3);
        params.bottomMargin = this.dp(3);
        return params;
    }

    private int gameButtonTextColor() {
        int color = this.getResources().getColor(R.color.primary);
        int brightness = Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114;
        return brightness > 150000 ? Color.rgb(15, 23, 42) : Color.WHITE;
    }

    private Switch createGameVibrationSwitch() {
        Switch vibration = new Switch(this.requireContext());
        vibration.setText(R.string.game_vibration);
        vibration.setTextColor(this.getResources().getColor(R.color.text_secondary));
        vibration.setTextSize(14.0f);
        vibration.setGravity(16);
        vibration.setChecked(this.gameVibrationEnabled);
        vibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.gameVibrationEnabled = isChecked;
            if (this.sokobanView != null) {
                this.sokobanView.setVibrationEnabled(isChecked);
            }
            if (this.tetrisView != null) {
                this.tetrisView.setVibrationEnabled(isChecked);
            }
        });
        return vibration;
    }

    private void updatePuzzleStats() {
        if (this.puzzleStats == null || this.puzzleView == null) {
            return;
        }
        this.puzzleStats.setText((CharSequence)this.getString(R.string.puzzle_stats, new Object[]{this.puzzleView.getSizeMode(), this.puzzleView.isPictureMode() ? this.getString(R.string.picture_suffix) : this.getString(R.string.number_suffix), this.puzzleView.getSeconds(), this.puzzleView.getMoves()}));
    }

    private void updatePuzzleReferencePreview(@Nullable ImageView preview) {
        if (preview == null || this.puzzleView == null) {
            return;
        }
        Bitmap bitmap = this.puzzleView.createReferenceBitmap(this.dp(160));
        if (bitmap == null) {
            preview.setVisibility(8);
        } else {
            preview.setImageBitmap(bitmap);
            preview.setVisibility(0);
        }
    }

    private void showPuzzleReferenceDialog() {
        if (this.puzzleView == null || !this.puzzleView.isPictureMode()) {
            return;
        }
        Bitmap bitmap = this.puzzleView.createReferenceBitmap(this.dp(420));
        if (bitmap == null) {
            return;
        }
        ImageView image = new ImageView(this.requireContext());
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(this.dp(12), this.dp(12), this.dp(12), this.dp(12));
        new AlertDialog.Builder(this.requireContext()).setTitle(R.string.reference_image).setView((View)image).setPositiveButton(R.string.close, null).show();
    }

    private void showPuzzleSolvedDialog(int moves, int seconds) {
        this.timerHandler.removeCallbacks(this.timerTick);
        String mode = this.puzzleView == null ? "3\u00d73" : this.puzzleView.getSizeMode() + "\u00d7" + this.puzzleView.getSizeMode() + (this.puzzleView.isPictureMode() ? " " + this.getString(R.string.picture_suffix) : " " + this.getString(R.string.number_suffix));
        this.showGameSolvedDialog(this.getString(R.string.puzzle_game), mode, seconds, moves);
    }

    private void showGameSolvedDialog(String game, String mode, int seconds, int moves) {
        new AlertDialog.Builder(this.requireContext()).setTitle(R.string.smart_title).setMessage(R.string.smart_message).setNegativeButton(R.string.hero_anonymous, null).setPositiveButton(R.string.tell_developer, (dialog, which) -> this.sendGameFeedback(game, mode, seconds, moves)).show();
    }

    private void sendGameFeedback(String game, String mode, int seconds, int moves) {
        String body = this.getString(R.string.game_feedback_body, new Object[]{game, mode, seconds, moves, this.appVersion(), Build.MODEL, Build.VERSION.RELEASE});
        this.openMail(this.getString(R.string.game_feedback_subject), body);
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

    private void vibrateGame() {
        if (this.gameVibrationEnabled) {
            this.vibrateLight();
        }
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
        this.timerHandler.removeCallbacks(this.timerTick);
        if (this.tetrisView != null) {
            this.tetrisView.stop();
        }
        this.puzzleContainer.removeAllViews();
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
                {R.string.help_feature_puzzle_title, R.string.help_feature_puzzle_desc},
                {R.string.help_feature_sokoban_title, R.string.help_feature_sokoban_desc}
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
        this.puzzleContainer.addView((View)root);
        this.flip(this.homeContent, (View)this.puzzleContainer);
    }

    private void hideHelpCenter() {
        this.flip((View)this.puzzleContainer, this.homeContent);
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
        Uri mailUri = Uri.parse((String)("mailto:tulkun@foxmail.com?subject=" + subject + "&body=" + body));
        Intent intent = new Intent("android.intent.action.SENDTO", mailUri);
        try {
            this.startActivity(intent);
        }
        catch (ActivityNotFoundException error) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)this.getString(R.string.no_email_app, new Object[]{CONTACT_EMAIL}), (int)1).show();
        }
    }

    private void openMail(String subject, String body) {
        Uri mailUri = Uri.parse((String)("mailto:tulkun@foxmail.com?subject=" + Uri.encode((String)subject) + "&body=" + Uri.encode((String)body)));
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
            qr.setOnClickListener(v -> HomeFragment.this.showDonateQrPreview(label, filePrefix, bitmap));
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
    private Bitmap decodeSquareBitmap(Uri uri, int targetSize) {
        try (InputStream input = this.requireContext().getContentResolver().openInputStream(uri);){
            Bitmap source = BitmapFactory.decodeStream((InputStream)input);
            if (source == null) {
                Bitmap bitmap2 = null;
                return bitmap2;
            }
            int side = Math.min(source.getWidth(), source.getHeight());
            int left = (source.getWidth() - side) / 2;
            int top = (source.getHeight() - side) / 2;
            Bitmap cropped = Bitmap.createBitmap((Bitmap)source, (int)left, (int)top, (int)side, (int)side);
            Bitmap scaled = Bitmap.createScaledBitmap((Bitmap)cropped, (int)targetSize, (int)targetSize, (boolean)true);
            if (cropped != source) {
                cropped.recycle();
            }
            if (scaled != source && !source.isRecycled()) {
                source.recycle();
            }
            Bitmap bitmap = scaled;
            return bitmap;
        }
        catch (Exception error) {
            return null;
        }
    }

    private File customPuzzleFile() {
        return new File(this.requireContext().getFilesDir(), "custom_puzzle.png");
    }

    private void saveCustomPuzzleBitmap(Bitmap bitmap) {
        try (FileOutputStream output = new FileOutputStream(this.customPuzzleFile());){
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, (OutputStream)output);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private Bitmap loadCustomPuzzleBitmap() {
        File file = this.customPuzzleFile();
        if (!file.exists()) {
            return null;
        }
        return BitmapFactory.decodeFile((String)file.getAbsolutePath());
    }

    private void deleteCustomPuzzleBitmap() {
        File file = this.customPuzzleFile();
        if (file.exists()) {
            file.delete();
        }
    }

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
        String shareText = "KeyScan \u5bc6\u626b\u5b89\u5168\u5de5\u5177\u7bb1\n\u5f00\u6e90\u5730\u5740\uff1ahttps://github.com/avatartulkun/keyscan\n\u8054\u7cfb\u65b9\u5f0f\uff1atulkun@foxmail.com";
        Intent intent = new Intent("android.intent.action.SEND");
        intent.setType("text/plain");
        intent.putExtra("android.intent.extra.SUBJECT", "KeyScan");
        intent.putExtra("android.intent.extra.TEXT", shareText);
        try {
            this.startActivity(Intent.createChooser((Intent)intent, (CharSequence)"\u5206\u4eab KeyScan"));
        }
        catch (ActivityNotFoundException error) {
            Toast.makeText((Context)this.requireContext(), (CharSequence)"\u672a\u68c0\u6d4b\u5230\u53ef\u7528\u7684\u5206\u4eab\u5e94\u7528\u3002", (int)0).show();
        }
    }

    public void onDetach() {
        super.onDetach();
        this.timerHandler.removeCallbacks(this.timerTick);
        if (this.tetrisView != null) {
            this.tetrisView.stop();
        }
        this.actions = null;
    }

    public static interface HomeActions {
        public void openScanner();

        public void openGenerator();

        public void openHistory();

        public void openWebDav();

        public void openExport();

        public void openAppearance();

        public void openPasswordForge();

        public void openRandomPasswordGenerator();

        public void openOtpAuth();
    }
}
