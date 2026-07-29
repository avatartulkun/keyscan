package com.secureqr.scanner.ui.vault;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.vault.VaultTypes;
import com.secureqr.scanner.utils.BrandIconRegistry;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class VaultRecordAdapter extends RecyclerView.Adapter<VaultRecordAdapter.H> {
    interface Listener {
        void onClick(VaultItem item);
        void onFavorite(VaultItem item, boolean favorite);
    }
    private final Listener listener;
    private List<VaultItem> shown = Collections.emptyList();

    VaultRecordAdapter(Listener listener) { this.listener = listener; }

    void submit(List<VaultItem> items, String typeKey, String query) {
        ArrayList<VaultItem> result = new ArrayList<>();
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        VaultTypes.Type visibleType = VaultTypes.find(typeKey);
        for (VaultItem item : items == null ? Collections.<VaultItem>emptyList() : items) {
            if (!VaultTypes.matches(visibleType, item.type, item.fieldsJson)) continue;
            if (search.isEmpty() || matchesSearch(item, visibleType, search)) result.add(item);
        }
        shown = result;
        notifyDataSetChanged();
    }

    private boolean matchesSearch(VaultItem item, VaultTypes.Type type, String search) {
        if (item.title != null && item.title.toLowerCase(Locale.ROOT).contains(search)) return true;
        if (item.notes != null && item.notes.toLowerCase(Locale.ROOT).contains(search)) return true;
        try {
            if (VaultAdapter.VaultHost.context.getString(type.labelRes).toLowerCase(Locale.ROOT).contains(search)) return true;
        } catch (Exception ignored) {}
        try {
            JSONObject object = new JSONObject(item.fieldsJson == null ? "{}" : item.fieldsJson);
            String label = object.optString("label", "");
            String provider = object.optString("provider", object.optString("service", ""));
            return label.toLowerCase(Locale.ROOT).contains(search) || provider.toLowerCase(Locale.ROOT).contains(search);
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull @Override public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new H(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vault_record, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull H holder, int position) {
        VaultItem item = shown.get(position);
        VaultTypes.Type type = VaultTypes.resolveStored(item.type, item.fieldsJson);
        holder.title.setText(item.title);
        holder.meta.setText(VaultAdapter.VaultHost.context.getString(R.string.vault_updated_format,
                VaultAdapter.VaultHost.context.getString(type.labelRes),
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(item.updatedTime))));
        BrandIconRegistry.BrandIcon brand = bankOrPaymentBrand(holder, item, type);
        if (brand != null) {
            holder.icon.setImageBitmap(brand.bitmap);
            holder.icon.setImageTintList(null);
            holder.icon.setPadding(dp(holder.itemView, 6), dp(holder.itemView, 6),
                    dp(holder.itemView, 6), dp(holder.itemView, 6));
            holder.icon.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        } else {
            holder.icon.setImageResource(VaultRecordIcons.iconFor(item, type));
            int iconColor = holder.itemView.getContext().getColor(VaultRecordIcons.colorFor(item, type));
            holder.icon.setImageTintList(ColorStateList.valueOf(iconColor));
            holder.icon.setPadding(dp(holder.itemView, 9), dp(holder.itemView, 9),
                    dp(holder.itemView, 9), dp(holder.itemView, 9));
            holder.icon.setBackground(softRound(iconColor, holder.itemView));
        }
        holder.favorite.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override public int getItemCount() { return shown.size(); }

    private BrandIconRegistry.BrandIcon bankOrPaymentBrand(H holder, VaultItem item, VaultTypes.Type type) {
        if (!"BANK_CARD".equals(type.key) && !"PAYMENT".equals(type.key)) return null;
        try {
            JSONObject fields = new JSONObject(item.fieldsJson == null ? "{}" : item.fieldsJson);
            BrandIconRegistry registry = BrandIconRegistry.get(holder.itemView.getContext());
            BrandIconRegistry.BrandIcon bank = registry.namedBrand(fields.optString("bank"));
            if (bank != null) return bank;
            BrandIconRegistry.BrandIcon network = registry.namedBrand(fields.optString("network"));
            if (network != null) return network;
            BrandIconRegistry.BrandIcon cardType = registry.namedBrand(fields.optString("cardType"));
            if (cardType != null) return cardType;
            return registry.namedBrand(item.title);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable softRound(int color, View view) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor((color & 0x00FFFFFF) | 0x1A000000);
        drawable.setCornerRadius(view.getResources().getDisplayMetrics().density * 10);
        return drawable;
    }

    static final class H extends RecyclerView.ViewHolder {
        final TextView title, meta;
        final ImageView icon, favorite;
        H(View view) {
            super(view);
            title = view.findViewById(R.id.tv_vault_record_title);
            meta = view.findViewById(R.id.tv_vault_record_meta);
            icon = view.findViewById(R.id.iv_vault_record_icon);
            favorite = view.findViewById(R.id.iv_vault_record_favorite);
        }
    }
}
