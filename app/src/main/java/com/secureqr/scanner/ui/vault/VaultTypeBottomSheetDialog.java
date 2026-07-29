package com.secureqr.scanner.ui.vault;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.vault.VaultTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class VaultTypeBottomSheetDialog {
    private static final List<String> ID_PRIMARY = Arrays.asList(
            "NATIONAL_ID", "PASSPORT", "DRIVER_LICENSE", "SOCIAL_SECURITY", "OTHER_ID");
    private static final List<String> ID_INTERNATIONAL = Arrays.asList(
            "US_DRIVER_LICENSE", "MY_NUMBER_CARD", "PERSONALAUSWEIS", "FRANCE_CNI",
            "KOREA_ID", "CANADA_ID", "AUSTRALIA_ID", "OTHER_ID");

    static void show(Fragment fragment, VaultTypes.Category category, List<VaultItem> items) {
        show(fragment, category, items, false);
    }

    private static void show(Fragment fragment, VaultTypes.Category category, List<VaultItem> items, boolean internationalIds) {
        BottomSheetDialog dialog = new BottomSheetDialog(fragment.requireContext());
        LinearLayout root = new LinearLayout(fragment.requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(fragment, 16), dp(fragment, 8), dp(fragment, 16), dp(fragment, 12));
        root.setBackground(roundRect(fragment.requireContext().getColor(R.color.card_background), dp(fragment, 18), Color.TRANSPARENT, 0));

        View handle = new View(fragment.requireContext());
        handle.setBackground(roundRect(fragment.requireContext().getColor(R.color.card_stroke), dp(fragment, 2), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(fragment, 44), dp(fragment, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(fragment, 14);
        root.addView(handle, handleParams);

        LinearLayout titleRow = new LinearLayout(fragment.requireContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(fragment.requireContext());
        title.setText(internationalIds ? fragment.getString(R.string.vault_other_international_id) : fragment.getString(category.labelRes));
        title.setTextColor(fragment.requireContext().getColor(R.color.text_main));
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton close = new ImageButton(fragment.requireContext());
        close.setImageResource(R.drawable.ic_more_vert_24);
        close.setRotation(45f);
        close.setColorFilter(fragment.requireContext().getColor(R.color.text_secondary));
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setContentDescription(fragment.getString(R.string.close));
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(fragment, 42), dp(fragment, 42)));
        root.addView(titleRow);

        TextView subtitle = new TextView(fragment.requireContext());
        subtitle.setText(R.string.vault_type_sheet_subtitle);
        subtitle.setTextColor(fragment.requireContext().getColor(R.color.text_secondary));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(fragment, 8));
        root.addView(subtitle);

        RecyclerView recycler = new RecyclerView(fragment.requireContext());
        recycler.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        TypeAdapter adapter = new TypeAdapter(fragment, dialog, category, items, internationalIds);
        recycler.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                return adapter.move(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {}
            @Override public boolean isLongPressDragEnabled() { return true; }
        }).attachToRecyclerView(recycler);
        root.addView(recycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = new Button(fragment.requireContext());
        add.setText(fragment.getString(R.string.vault_new_category, fragment.getString(category.labelRes)));
        add.setTextColor(fragment.requireContext().getColor(R.color.primary_blue));
        add.setTextSize(15);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setAllCaps(false);
        add.setBackground(roundRect(0xFFEAF2FF, dp(fragment, 10), 0xFFD5E5FF, 1));
        add.setOnClickListener(v -> {
            dialog.dismiss();
            VaultNavigation.showTypePicker(fragment, category.key);
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(fragment, 46));
        addParams.topMargin = dp(fragment, 10);
        root.addView(add, addParams);

        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(root);
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            int height = (int) (fragment.getResources().getDisplayMetrics().heightPixels * 0.50f);
            sheet.getLayoutParams().height = height;
            sheet.requestLayout();
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            behavior.setPeekHeight(height);
            behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });
        dialog.show();
    }

    private static final class TypeChoice {
        final VaultTypes.Type type;
        final int count;
        final boolean group;
        TypeChoice(VaultTypes.Type type, int count, boolean group) { this.type = type; this.count = count; this.group = group; }
    }

    private static final class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.Holder> {
        private final Fragment fragment;
        private final BottomSheetDialog dialog;
        private final VaultTypes.Category category;
        private final List<VaultItem> items;
        private final boolean internationalIds;
        private final List<TypeChoice> choices = new ArrayList<>();

        TypeAdapter(Fragment fragment, BottomSheetDialog dialog, VaultTypes.Category category, List<VaultItem> items, boolean internationalIds) {
            this.fragment = fragment;
            this.dialog = dialog;
            this.category = category;
            this.items = items == null ? Collections.emptyList() : items;
            this.internationalIds = internationalIds;
            rebuild();
        }

        private void rebuild() {
            choices.clear();
            for (VaultTypes.Type type : VaultUiState.sortedTypes(fragment.requireContext(), category)) {
                if (VaultTypes.IDENTITY.equals(category.key)) {
                    if (internationalIds) {
                        if (!ID_INTERNATIONAL.contains(type.key) || "OTHER_ID".equals(type.key)) continue;
                    } else if (!ID_PRIMARY.contains(type.key)) {
                        continue;
                    }
                }
                int count = count(type);
                if (!internationalIds && VaultTypes.IDENTITY.equals(category.key) && "OTHER_ID".equals(type.key)) {
                    choices.add(new TypeChoice(type, countInternational(), true));
                } else if (count > 0) {
                    choices.add(new TypeChoice(type, count, false));
                }
            }
        }

        boolean move(int from, int to) {
            if (from < 0 || to < 0 || from >= choices.size() || to >= choices.size()) return false;
            Collections.swap(choices, from, to);
            List<VaultTypes.Type> ordered = new ArrayList<>();
            for (TypeChoice choice : choices) ordered.add(choice.type);
            VaultUiState.saveTypeOrder(fragment.requireContext(), category, ordered);
            notifyItemMoved(from, to);
            return true;
        }

        private int count(VaultTypes.Type type) {
            int count = 0;
            for (VaultItem item : items) if (VaultTypes.matches(type, item.type, item.fieldsJson)) count++;
            return count;
        }

        private int countInternational() {
            int count = 0;
            for (VaultTypes.Type type : category.types) {
                if (ID_INTERNATIONAL.contains(type.key)) count += count(type);
            }
            return count;
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_type, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (choices.isEmpty()) {
                holder.icon.setImageResource(R.drawable.ic_vault_file_lock);
                holder.icon.setImageTintList(ColorStateList.valueOf(holder.itemView.getContext().getColor(R.color.text_hint)));
                holder.title.setText(R.string.vault_existing_empty);
                holder.count.setText("");
                holder.itemView.setOnClickListener(null);
                return;
            }
            TypeChoice choice = choices.get(position);
            holder.icon.setImageResource(icon(choice.type));
            holder.icon.setImageTintList(ColorStateList.valueOf(holder.itemView.getContext().getColor(color(choice.type))));
            holder.title.setText(choice.group ? fragment.getString(R.string.vault_other_international_id) : fragment.getString(choice.type.labelRes));
            holder.count.setText(String.valueOf(choice.count));
            holder.itemView.setOnClickListener(v -> {
                if (choice.group) {
                    dialog.dismiss();
                    show(fragment, category, items, true);
                    return;
                }
                dialog.dismiss();
                fragment.getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, VaultRecordListFragment.newInstance(choice.type.key))
                        .addToBackStack(null)
                        .commit();
            });
        }

        @Override public int getItemCount() { return choices.isEmpty() ? 1 : choices.size(); }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon; final TextView title, count;
            Holder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.iv_vault_type_icon);
                title = itemView.findViewById(R.id.tv_vault_type_title);
                count = itemView.findViewById(R.id.tv_vault_type_count);
            }
        }
    }

    private static int icon(VaultTypes.Type type) {
        if (VaultTypes.IDENTITY.equals(type.category)) {
            if ("PASSPORT".equals(type.key)) return R.drawable.ic_vault_passport;
            if ("DRIVER_LICENSE".equals(type.key) || "US_DRIVER_LICENSE".equals(type.key)) return R.drawable.ic_vault_car;
            if ("SOCIAL_SECURITY".equals(type.key)) return R.drawable.ic_vault_number;
            return R.drawable.ic_vault_identity;
        }
        return VaultRecordIcons.iconFor(new VaultItem(), type);
    }

    private static int color(VaultTypes.Type type) {
        if (VaultTypes.IDENTITY.equals(type.category)) return R.color.vault_icon_green;
        if (VaultTypes.FINANCIAL.equals(type.category)) return R.color.vault_icon_orange;
        if (VaultTypes.CONTACT.equals(type.category)) return R.color.vault_icon_purple;
        if (VaultTypes.FILES.equals(type.category)) return R.color.vault_icon_cyan;
        return R.color.vault_icon_blue;
    }

    private static GradientDrawable roundRect(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private static int dp(Fragment fragment, int value) {
        return Math.round(value * fragment.getResources().getDisplayMetrics().density);
    }

    private VaultTypeBottomSheetDialog() {}
}
