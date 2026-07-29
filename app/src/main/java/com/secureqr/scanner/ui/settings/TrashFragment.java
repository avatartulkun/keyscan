package com.secureqr.scanner.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.TrashItem;
import com.secureqr.scanner.data.repository.TrashRepository;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.security.OperationModeGuard;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

public class TrashFragment extends Fragment {
    private TrashRepository repository;
    private LinearLayout list;
    private TextView empty;
    private TextView retention;
    private CheckBox selectAll;
    private Button restoreSelected;
    private Button deleteSelected;
    private Button clear;
    private final Set<String> selectedIds = new HashSet<>();
    private final Set<String> expandedIds = new HashSet<>();
    private List<TrashItem> currentItems = new ArrayList<>();
    private boolean updatingSelection;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_trash, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = new TrashRepository(requireContext());
        list = view.findViewById(R.id.list_trash);
        empty = view.findViewById(R.id.tv_trash_empty);
        retention = view.findViewById(R.id.tv_trash_retention);
        selectAll = view.findViewById(R.id.cb_trash_select_all);
        restoreSelected = view.findViewById(R.id.btn_trash_restore_selected);
        deleteSelected = view.findViewById(R.id.btn_trash_delete_selected);
        clear = view.findViewById(R.id.btn_trash_clear);
        view.findViewById(R.id.btn_trash_back).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.card_trash_retention).setOnClickListener(v -> chooseRetention());
        selectAll.setOnCheckedChangeListener((button, checked) -> { if (updatingSelection) return; selectedIds.clear(); if (checked) for (TrashItem item : currentItems) selectedIds.add(item.id); render(currentItems); });
        restoreSelected.setOnClickListener(v ->
                OperationModeGuard.requireEdit(this, this::confirmRestoreSelected));
        deleteSelected.setOnClickListener(v ->
                OperationModeGuard.requireEdit(this, this::confirmDeleteSelected));
        clear.setOnClickListener(v ->
                OperationModeGuard.requireEdit(this, this::confirmClear));
        updateRetention();
        repository.observeAll().observe(getViewLifecycleOwner(), this::render);
    }

    private void chooseRetention() {
        String[] labels = getResources().getStringArray(R.array.trash_retention_labels);
        int[] values = {7, 30, 90, TrashRepository.KEEP_FOREVER};
        int checked = 1; int current = repository.retentionDays();
        for (int i=0;i<values.length;i++) if (values[i] == current) checked=i;
        new AlertDialog.Builder(requireContext()).setTitle(R.string.trash_retention_dialog_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> { repository.setRetentionDays(values[which]); updateRetention(); dialog.dismiss(); })
                .setNegativeButton(R.string.common_action_cancel, null).show();
    }

    private void updateRetention() {
        int days = repository.retentionDays();
        retention.setText(days == 0 ? getString(R.string.trash_retention_forever) : getResources().getQuantityString(R.plurals.trash_retention_days, days, days));
    }

    private void render(List<TrashItem> items) {
        currentItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        Set<String> available = new HashSet<>(); for (TrashItem item : currentItems) available.add(item.id); selectedIds.retainAll(available); expandedIds.retainAll(available);
        list.removeAllViews();
        empty.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        updateBatchActions();
        if (items == null) return;
        for (TrashItem item : items) list.addView(row(item));
    }

    private View row(TrashItem item) {
        LinearLayout card = new LinearLayout(requireContext()); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12)); card.setBackgroundResource(R.drawable.bg_card); card.setElevation(dp(1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); cp.topMargin=dp(10); card.setLayoutParams(cp);
        LinearLayout heading=new LinearLayout(requireContext());heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        CheckBox selected=new CheckBox(requireContext());selected.setChecked(selectedIds.contains(item.id));selected.setOnCheckedChangeListener((b,checked)->{if(checked)selectedIds.add(item.id);else selectedIds.remove(item.id);updateBatchActions();});heading.addView(selected,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=new TextView(requireContext());title.setText(item.title);title.setTextColor(ContextCompat.getColor(requireContext(),R.color.text_main));title.setTextSize(16);title.setTypeface(title.getTypeface(),1);heading.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView arrow=new TextView(requireContext());arrow.setText(expandedIds.contains(item.id)?"▴":"▾");arrow.setTextSize(19);arrow.setGravity(android.view.Gravity.CENTER);arrow.setTextColor(ContextCompat.getColor(requireContext(),R.color.text_secondary));heading.addView(arrow,new LinearLayout.LayoutParams(dp(44),dp(44)));card.addView(heading);
        TextView info=new TextView(requireContext());info.setText(getString(R.string.trash_deleted_at,typeLabel(item.type),DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.getDefault()).format(new Date(item.deletedAt))));info.setTextColor(ContextCompat.getColor(requireContext(),R.color.text_secondary));info.setTextSize(12);card.addView(info);
        LinearLayout details=new LinearLayout(requireContext());details.setOrientation(LinearLayout.VERTICAL);details.setVisibility(expandedIds.contains(item.id)?View.VISIBLE:View.GONE);
        TextView content=new TextView(requireContext());content.setText(contentSummary(item));content.setTextColor(ContextCompat.getColor(requireContext(),R.color.text_main));content.setTextSize(13);content.setPadding(0,dp(8),0,0);details.addView(content);
        Button full=new Button(requireContext());full.setText(R.string.trash_summary_open_details);full.setAllCaps(false);full.setOnClickListener(v->showDetails(item));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44));fp.topMargin=dp(8);details.addView(full,fp);
        LinearLayout actions=new LinearLayout(requireContext());actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(8),0,0);
        Button restore=new Button(requireContext());restore.setText(R.string.trash_action_restore);restore.setOnClickListener(v -> OperationModeGuard.requireEdit(this,()->repository.restoreWithResult(item,(ok,failed,message)->FragmentUi.run(this,()->android.widget.Toast.makeText(requireContext(),failed==0?getString(R.string.trash_restore_success):(message==null?getString(R.string.trash_restore_failed):message),android.widget.Toast.LENGTH_LONG).show()))));actions.addView(restore,new LinearLayout.LayoutParams(0,dp(44),1));
        Button delete=new Button(requireContext());delete.setText(R.string.trash_action_delete_permanently);delete.setOnClickListener(v -> OperationModeGuard.requireEdit(this,()->confirmDelete(item)));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,dp(44),1);dp.leftMargin=dp(8);actions.addView(delete,dp);details.addView(actions);card.addView(details);
        View.OnClickListener toggle=v->{boolean expanded=!expandedIds.contains(item.id);if(expanded)expandedIds.add(item.id);else expandedIds.remove(item.id);details.setVisibility(expanded?View.VISIBLE:View.GONE);arrow.setText(expanded?"▴":"▾");};
        heading.setClickable(true);heading.setOnClickListener(toggle);title.setOnClickListener(toggle);arrow.setOnClickListener(toggle);
        return card;
    }

    private void confirmDelete(TrashItem item){new AlertDialog.Builder(requireContext()).setTitle(R.string.trash_delete_permanently_title).setMessage(R.string.trash_delete_permanently_message).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.trash_action_delete_permanently,(d,w)->repository.permanentlyDelete(item,null)).show();}
    private void showDetails(TrashItem item){getParentFragmentManager().beginTransaction().replace(R.id.fragment_container,TrashDetailFragment.newInstance(item.id)).addToBackStack(null).commit();}
    private String contentSummary(TrashItem item){try{JSONObject j=new JSONObject(item.payload);if(TrashItem.PASSWORD.equals(item.type))return lines(getString(R.string.trash_summary_account),first(j.optString("username"),j.optString("account")),getString(R.string.trash_summary_website_app),first(j.optString("websiteDomain"),j.optString("appPackageName")),getString(R.string.trash_summary_notes),j.optString("notes"));if(TrashItem.OTP.equals(item.type))return lines(getString(R.string.trash_summary_issuer),j.optString("issuer"),getString(R.string.trash_summary_account),j.optString("accountName"),getString(R.string.trash_summary_digits),String.valueOf(j.optInt("digits",6)));if(TrashItem.NOTE.equals(item.type))return lines(getString(R.string.trash_summary_primary),j.optString("primaryText"),getString(R.string.trash_summary_secondary),j.optString("secondaryText"));if(TrashItem.VAULT.equals(item.type))return lines(getString(R.string.trash_summary_type),j.optString("type"),getString(R.string.trash_summary_notes),j.optString("notes"),getString(R.string.trash_summary_fields),safeFieldSummary(j.optString("fieldsJson")));}catch(Exception ignored){}return getString(R.string.trash_summary_open_details);}
    private String safeFieldSummary(String raw){try{JSONObject fields=new JSONObject(raw);StringBuilder out=new StringBuilder();java.util.Iterator<String> keys=fields.keys();while(keys.hasNext()&&out.length()<100){String key=keys.next();String lower=key.toLowerCase(Locale.ROOT);if(lower.contains("password")||lower.contains("secret")||lower.contains("pin")||lower.contains("cvv"))continue;if(out.length()>0)out.append(getString(R.string.trash_summary_separator));out.append(getString(R.string.trash_summary_pair,key,fields.optString(key)));}return out.toString();}catch(Exception e){return getString(R.string.trash_summary_open_details);}}
    private String lines(String... values){StringBuilder out=new StringBuilder();for(int i=0;i+1<values.length;i+=2)if(values[i+1]!=null&&!values[i+1].trim().isEmpty()){if(out.length()>0)out.append('\n');out.append(getString(R.string.trash_summary_pair,values[i],values[i+1]));}return out.length()==0?getString(R.string.trash_summary_open_details):out.toString();}
    private String first(String... values){for(String value:values)if(value!=null&&!value.trim().isEmpty())return value;return "";}
    private List<TrashItem> selectedItems(){List<TrashItem> out=new ArrayList<>();for(TrashItem item:currentItems)if(selectedIds.contains(item.id))out.add(item);return out;}
    private void updateBatchActions(){int count=selectedIds.size();restoreSelected.setEnabled(count>0);deleteSelected.setEnabled(count>0);restoreSelected.setText(count>0?getResources().getQuantityString(R.plurals.trash_restore_selected_count,count,count):getString(R.string.trash_action_restore_selected));deleteSelected.setText(count>0?getResources().getQuantityString(R.plurals.trash_delete_selected_count,count,count):getString(R.string.trash_action_delete_selected_permanently));clear.setEnabled(!currentItems.isEmpty());updatingSelection=true;selectAll.setChecked(!currentItems.isEmpty()&&count==currentItems.size());updatingSelection=false;}
    private void confirmRestoreSelected(){List<TrashItem> chosen=selectedItems();int count=chosen.size();new AlertDialog.Builder(requireContext()).setTitle(R.string.trash_restore_selected_title).setMessage(getResources().getQuantityString(R.plurals.trash_restore_confirm_items,count,count)).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.trash_action_restore,(d,w)->{selectedIds.clear();repository.restoreManyWithResult(chosen,(ok,failed,message)->FragmentUi.run(this,()->{String detail=message==null?"":getString(R.string.trash_error_detail,message);String result=failed==0?getResources().getQuantityString(R.plurals.trash_restore_result,ok,ok):getResources().getQuantityString(R.plurals.trash_restore_partial_result,ok,ok,failed,detail);android.widget.Toast.makeText(requireContext(),result,android.widget.Toast.LENGTH_LONG).show();}));}).show();}
    private void confirmDeleteSelected(){List<TrashItem> chosen=selectedItems();int count=chosen.size();new AlertDialog.Builder(requireContext()).setTitle(R.string.trash_delete_selected_title).setMessage(getResources().getQuantityString(R.plurals.trash_delete_confirm_items,count,count)).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.trash_detail_delete_title,(d,w)->{selectedIds.clear();repository.permanentlyDeleteMany(chosen,null);}).show();}
    private void confirmClear(){new AlertDialog.Builder(requireContext()).setTitle(R.string.trash_clear_title).setMessage(R.string.trash_clear_message).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.trash_clear,(d,w)->{selectedIds.clear();repository.clearAll(null);}).show();}
    private String typeLabel(String type){if(TrashItem.PASSWORD.equals(type))return getString(R.string.trash_type_password);if(TrashItem.OTP.equals(type))return getString(R.string.trash_type_otp);if(TrashItem.VAULT.equals(type))return getString(R.string.trash_type_vault);return getString(R.string.trash_type_note);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
