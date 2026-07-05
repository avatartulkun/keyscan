package com.secureqr.scanner.ui.otp;

import android.content.res.ColorStateList;
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

import java.util.ArrayList;
import java.util.List;

public class OtpAdapter extends RecyclerView.Adapter<OtpAdapter.Holder> {
    public interface Listener {
        void onCopy(String code);
        void onPin(OtpToken token);
        void onEdit(OtpToken token);
        void onDelete(OtpToken token);
        void onMoreActions(OtpToken token, String code);
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
        holder.issuer.setText(displayIssuer(token));
        int remaining = OtpHelper.remainingSeconds(token, now);
        int normalColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.accent);
        int dangerColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.danger);
        int activeColor = remaining <= 5 ? dangerColor : normalColor;
        try {
            String code = OtpHelper.code(token, now);
            holder.code.setText(formatCode(code));
            holder.code.setTextColor(activeColor);
            holder.next.setText(remaining <= 5 ? "下个\n" + formatCode(OtpHelper.code(token, now + token.period * 1000L)) : "");
            holder.codeArea.setOnClickListener(v -> listener.onCopy(code));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onMoreActions(token, code);
                return true;
            });
        } catch (Exception e) {
            holder.code.setText("------");
        }
        holder.remaining.setText(remaining + "s");
        holder.remaining.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_main));
        holder.progress.setMax(Math.max(1, token.period));
        holder.progress.setProgress(Math.max(0, remaining));
        holder.progress.setProgressTintList(ColorStateList.valueOf(activeColor));
        holder.pin.setText(token.pinned ? "★" : "☆");
        holder.pin.setOnClickListener(v -> listener.onPin(token));
        holder.edit.setOnClickListener(v -> listener.onEdit(token));
        holder.itemView.setOnClickListener(v -> listener.onEdit(token));
        holder.delete.setOnClickListener(v -> listener.onDelete(token));
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

    private String displayIssuer(OtpToken token) {
        if (token.issuer != null && !token.issuer.trim().isEmpty()) return token.issuer;
        return token.accountName == null || token.accountName.trim().isEmpty() ? "未命名 OTP" : token.accountName;
    }

    private String formatCode(String code) {
        if (code == null) return "";
        String clean = code.replace(" ", "");
        if (clean.length() == 6) return clean.substring(0, 3) + " " + clean.substring(3);
        return code;
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView account;
        TextView issuer;
        TextView next;
        TextView code;
        TextView remaining;
        LinearLayout codeArea;
        ProgressBar progress;
        Button pin;
        Button edit;
        Button delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            account = itemView.findViewById(R.id.tv_otp_account);
            issuer = itemView.findViewById(R.id.tv_otp_issuer);
            next = itemView.findViewById(R.id.tv_otp_next);
            code = itemView.findViewById(R.id.tv_otp_code);
            remaining = itemView.findViewById(R.id.tv_otp_remaining);
            codeArea = itemView.findViewById(R.id.layout_otp_code_area);
            progress = itemView.findViewById(R.id.progress_otp_remaining);
            pin = itemView.findViewById(R.id.btn_otp_pin);
            edit = itemView.findViewById(R.id.btn_otp_edit);
            delete = itemView.findViewById(R.id.btn_otp_delete);
        }
    }
}

