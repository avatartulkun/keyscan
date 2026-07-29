package com.secureqr.scanner.ui.otp;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.utils.OtpHelper;
import com.secureqr.scanner.utils.BrandIconRegistry;

import java.util.ArrayList;
import java.util.List;

public class OtpAdapter extends RecyclerView.Adapter<OtpAdapter.Holder> {
    public interface Listener {
        void onCopy(String code);
        void onEdit(OtpToken token);
        void onDelete(OtpToken token);
        void onMoreActions(OtpToken token, String code);
        void onUnlockRequested();
    }

    private final List<OtpToken> tokens = new ArrayList<>();
    private final Listener listener;
    private long now = System.currentTimeMillis();

    public OtpAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_otp_token, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OtpToken token = tokens.get(position);
        holder.account.setText(token.accountName);
        holder.issuer.setText(displayIssuer(holder.itemView.getContext(), token));
        String issuerName = displayIssuer(holder.itemView.getContext(), token);
        BrandIconRegistry.BrandIcon brand = BrandIconRegistry.get(
                holder.itemView.getContext()).issuerBrand(issuerName);
        if (brand != null) {
            holder.brand.setImageBitmap(brand.bitmap);
            holder.brand.setVisibility(View.VISIBLE);
            holder.brandFallback.setVisibility(View.GONE);
            holder.brandContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        } else {
            holder.brand.setImageDrawable(null);
            holder.brand.setVisibility(View.GONE);
            holder.brandFallback.setText(initial(issuerName));
            holder.brandFallback.setVisibility(View.VISIBLE);
            int[] colors = avatarColors(issuerName);
            holder.brandFallback.setTextColor(colors[0]);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(colors[1]);
            holder.brandContainer.setBackground(background);
        }
        int remaining = OtpHelper.remainingSeconds(token, now);
        int normalColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.accent);
        int dangerColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.danger);
        int activeColor = remaining <= 5 ? dangerColor : normalColor;
        if (!com.secureqr.scanner.security.VaultSession.isUnlocked(holder.itemView.getContext())) {
            holder.code.setText(R.string.otp_code_tap_to_view);
            holder.code.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
            holder.next.setText("");
            holder.codeArea.setOnClickListener(v -> listener.onUnlockRequested());
            holder.itemView.setOnLongClickListener(v -> {
                listener.onEdit(token);
                return true;
            });
        } else try {
            String code = OtpHelper.code(token, now);
            holder.code.setText(formatCode(code));
            holder.code.setTextColor(activeColor);
            holder.next.setText(remaining <= 5 ? holder.itemView.getContext().getString(
                    R.string.otp_next_code_format,
                    formatCode(OtpHelper.code(token, now + token.period * 1000L))) : "");
            holder.codeArea.setOnClickListener(v -> listener.onCopy(code));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onEdit(token);
                return true;
            });
        } catch (Exception e) {
            holder.code.setText("------");
        }
        holder.remaining.setText(holder.itemView.getContext().getString(R.string.otp_remaining_seconds_format, remaining));
        holder.remaining.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_main));
        holder.progress.setMax(Math.max(1, token.period));
        holder.progress.setProgress(Math.max(0, remaining));
        holder.progress.setProgressTintList(ColorStateList.valueOf(activeColor));
        holder.itemView.setOnClickListener(null);
    }

    @Override
    public int getItemCount() {
        return tokens.size();
    }

    public void submit(List<OtpToken> newTokens) {
        tokens.clear();
        if (newTokens != null) tokens.addAll(newTokens);
        notifyDataSetChanged();
    }

    public void tick(long now) {
        this.now = now;
        notifyDataSetChanged();
    }

    public OtpToken getItem(int position) {
        return tokens.get(position);
    }

    public void move(int from, int to) {
        OtpToken token = tokens.remove(from);
        tokens.add(to, token);
        notifyItemMoved(from, to);
    }

    public List<OtpToken> orderedItems() {
        return new ArrayList<>(tokens);
    }

    private String displayIssuer(android.content.Context context, OtpToken token) {
        if (token.issuer != null && !token.issuer.trim().isEmpty()) return token.issuer;
        return token.accountName == null || token.accountName.trim().isEmpty()
                ? context.getString(R.string.otp_unnamed)
                : token.accountName;
    }

    private String formatCode(String code) {
        if (code == null) return "";
        String clean = code.replace(" ", "");
        if (clean.length() == 6) return clean.substring(0, 3) + " " + clean.substring(3);
        return code;
    }

    private String initial(String value) {
        if (value == null || value.trim().isEmpty()) return "T";
        return value.trim().substring(0, 1).toUpperCase(java.util.Locale.getDefault());
    }

    private int[] avatarColors(String value) {
        int[][] palette = {
                {0xFFFFFFFF, 0xFF7A8F79},
                {0xFFFFFFFF, 0xFF667FB5},
                {0xFFFFFFFF, 0xFF9B6EA6},
                {0xFFFFFFFF, 0xFFB07A66},
                {0xFFFFFFFF, 0xFF5F9090},
                {0xFFFFFFFF, 0xFF927E58}
        };
        int hash = value == null ? 0 : value.toLowerCase(java.util.Locale.ROOT).hashCode();
        return palette[Math.floorMod(hash, palette.length)];
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView account;
        TextView issuer;
        TextView next;
        TextView code;
        TextView remaining;
        LinearLayout codeArea;
        ProgressBar progress;
        android.widget.ImageView brand;
        TextView brandFallback;
        View brandContainer;

        Holder(@NonNull View itemView) {
            super(itemView);
            account = itemView.findViewById(R.id.tv_otp_account);
            issuer = itemView.findViewById(R.id.tv_otp_issuer);
            next = itemView.findViewById(R.id.tv_otp_next);
            code = itemView.findViewById(R.id.tv_otp_code);
            remaining = itemView.findViewById(R.id.tv_otp_remaining);
            codeArea = itemView.findViewById(R.id.layout_otp_code_area);
            progress = itemView.findViewById(R.id.progress_otp_remaining);
            brand = itemView.findViewById(R.id.iv_otp_brand);
            brandFallback = itemView.findViewById(R.id.tv_otp_brand_fallback);
            brandContainer = itemView.findViewById(R.id.layout_otp_brand);
        }
    }
}

