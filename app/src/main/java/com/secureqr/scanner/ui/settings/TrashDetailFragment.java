package com.secureqr.scanner.ui.settings;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.OtpToken;
import com.secureqr.scanner.data.model.TrashItem;
import com.secureqr.scanner.data.model.VaultAttachment;
import com.secureqr.scanner.data.repository.TrashRepository;
import com.secureqr.scanner.security.SensitiveActionGuard;
import com.secureqr.scanner.ui.vault.VaultImagePreviewActivity;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.utils.BitmapDecodeHelper;
import com.secureqr.scanner.utils.OtpHelper;
import com.secureqr.scanner.vault.VaultTypes;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only presentation of an item's pre-deletion snapshot. */
public final class TrashDetailFragment extends Fragment {
    private static final String ARG_ID = "trash_id";
    private TrashRepository repository;
    private TrashItem item;
    private LinearLayout content;
    private Runnable otpTicker;

    public static TrashDetailFragment newInstance(String id) { TrashDetailFragment f=new TrashDetailFragment();Bundle b=new Bundle();b.putString(ARG_ID,id);f.setArguments(b);return f; }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){
        LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(ContextCompat.getColor(requireContext(),R.color.surface_light));
        LinearLayout top=new LinearLayout(requireContext());top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(10),dp(16),dp(6));
        ImageButton back=new ImageButton(requireContext());back.setImageResource(R.drawable.ic_arrow_back_24);back.setContentDescription(getString(R.string.trash_back_description));back.setBackgroundColor(Color.TRANSPARENT);back.setOnClickListener(v->getParentFragmentManager().popBackStack());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=text(getString(R.string.trash_detail_title),22,true,R.color.text_main);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));top.addView(new View(requireContext()),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(top);
        ScrollView scroll=new ScrollView(requireContext());content=new LinearLayout(requireContext());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(18));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout actions=new LinearLayout(requireContext());actions.setPadding(dp(18),dp(8),dp(18),dp(14));Button restore=new Button(requireContext());restore.setText(R.string.trash_action_restore);restore.setOnClickListener(v->OperationModeGuard.requireEdit(this,this::restore));Button delete=new Button(requireContext());delete.setText(R.string.trash_detail_delete_title);delete.setOnClickListener(v->OperationModeGuard.requireEdit(this,this::delete));actions.addView(restore,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.leftMargin=dp(10);actions.addView(delete,p);root.addView(actions);
        return root;
    }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){repository=new TrashRepository(requireContext());repository.getById(requireArguments().getString(ARG_ID),loaded->FragmentUi.run(this,()->show(loaded)));}
    @Override public void onDestroyView(){if(otpTicker!=null&&content!=null)content.removeCallbacks(otpTicker);super.onDestroyView();}

    private void show(TrashItem loaded){if(loaded==null){getParentFragmentManager().popBackStack();return;}item=loaded;content.removeAllViews();
        TextView hero=text(item.title,24,true,R.color.text_main);content.addView(hero);String deleted=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.getDefault()).format(new Date(item.deletedAt));content.addView(text(getString(R.string.trash_deleted_at,typeLabel(item.type),deleted),13,false,R.color.text_secondary),top(6));
        try{JSONObject j=new JSONObject(item.payload);if(TrashItem.PASSWORD.equals(item.type))showPassword(j);else if(TrashItem.OTP.equals(item.type))showOtp(j);else if(TrashItem.VAULT.equals(item.type))showVault(j);else showNote(j);}catch(Exception e){section(getString(R.string.trash_type_note)).addView(text(getString(R.string.trash_detail_unreadable),14,false,R.color.text_secondary));}}

    private void showPassword(JSONObject j){LinearLayout card=section(getString(R.string.trash_detail_password_section));row(card,getString(R.string.trash_detail_field_title),j.optString("title"),false);row(card,getString(R.string.trash_detail_field_website),j.optString("websiteDomain"),false);row(card,getString(R.string.trash_detail_field_package),j.optString("appPackageName"),false);row(card,getString(R.string.trash_detail_field_username),j.optString("username"),false);row(card,getString(R.string.trash_summary_account),j.optString("account"),false);row(card,getString(R.string.trash_detail_field_password),j.optString("password"),true);row(card,getString(R.string.trash_summary_notes),first(j.optString("notes"),j.optString("remark")),false);}
    private void showOtp(JSONObject j){LinearLayout card=section(getString(R.string.trash_detail_otp_section));row(card,getString(R.string.trash_summary_issuer),j.optString("issuer"),false);row(card,getString(R.string.trash_summary_account),j.optString("accountName"),false);row(card,getString(R.string.trash_detail_field_secret),j.optString("secret"),true);row(card,getString(R.string.trash_summary_digits),String.valueOf(j.optInt("digits",6)),false);row(card,getString(R.string.trash_detail_field_algorithm),j.optString("algorithm","SHA1"),false);TextView code=text("------",28,true,R.color.action_icon_tint);card.addView(code,top(12));TextView remain=text("",12,false,R.color.text_secondary);card.addView(remain);OtpToken token=new OtpToken();token.secret=j.optString("secret");token.digits=j.optInt("digits",6);token.period=j.optInt("period",30);token.algorithm=j.optString("algorithm","SHA1");otpTicker=new Runnable(){@Override public void run(){try{long now=System.currentTimeMillis();code.setText(OtpHelper.code(token,now));int seconds=OtpHelper.remainingSeconds(token,now);remain.setText(getString(R.string.trash_detail_otp_countdown,seconds));content.postDelayed(this,1000);}catch(Exception e){code.setText(R.string.trash_detail_otp_unavailable);}}};otpTicker.run();}
    private void showVault(JSONObject j){JSONObject fields;try{fields=new JSONObject(j.optString("fieldsJson","{}"));}catch(Exception e){fields=new JSONObject();}VaultTypes.Type type=VaultTypes.resolveStored(j.optString("type"),j.optString("fieldsJson"));LinearLayout card=section(getString(type.labelRes));Set<String> shown=new HashSet<>();for(VaultTypes.Field f:type.fields){String value=fields.optString(f.key);if(!value.isEmpty()){row(card,getString(f.labelRes),value,f.secret);shown.add(f.key);}}Iterator<String> keys=fields.keys();while(keys.hasNext()){String key=keys.next();if(shown.contains(key)||"documentType".equals(key))continue;Object value=fields.opt(key);if(value!=null&&value!=JSONObject.NULL)row(card,label(key),String.valueOf(value),isSecret(key));}row(card,getString(R.string.trash_summary_notes),j.optString("notes"),false);repository.getVaultAttachments(item.originalId,attachments->FragmentUi.run(this,()->showAttachments(attachments)));}
    private void showAttachments(List<VaultAttachment> attachments){
        if(!isAdded()||attachments==null||attachments.isEmpty())return;
        LinearLayout card=section(getString(R.string.trash_detail_attachment_section));
        for(VaultAttachment attachment:attachments){
            LinearLayout file=new LinearLayout(requireContext());file.setOrientation(LinearLayout.VERTICAL);file.setPadding(0,dp(8),0,dp(8));
            boolean image=attachment.mimeType!=null&&attachment.mimeType.startsWith("image/");
            ImageView preview=null;
            if(image){
                preview=new ImageView(requireContext());preview.setImageResource(R.drawable.ic_vault_photo);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setBackgroundResource(R.drawable.bg_card);
                file.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(180)));
            }
            TextView name=text(attachment.filename,15,true,R.color.text_main);name.setPadding(0,dp(8),0,0);file.addView(name);
            file.addView(text((attachment.mimeType==null?"":attachment.mimeType)+" · "+Formatter.formatFileSize(requireContext(),attachment.size),12,false,R.color.text_secondary));
            card.addView(file);
            ImageView target=preview;
            if(image)repository.decryptVaultAttachment(attachment.id,result->FragmentUi.run(this,()->{
                if(result.error!=null||result.file==null)return;
                Bitmap bitmap=BitmapDecodeHelper.decodeFile(result.file.getAbsolutePath(),720);if(bitmap!=null)target.setImageBitmap(bitmap);
                View.OnClickListener open=v->SensitiveActionGuard.requireRecentAuth(requireActivity(),getString(R.string.trash_detail_view_sensitive),()->VaultImagePreviewActivity.open(requireContext(),result.file,attachment.id,attachment.filename,attachment.mimeType));
                target.setOnClickListener(open);file.setOnClickListener(open);
            }));
        }
        card.addView(text(getString(R.string.trash_detail_attachment_help),12,false,R.color.text_secondary),top(8));
    }
    private void showNote(JSONObject j){LinearLayout card=section(getString(R.string.trash_detail_note_section));row(card,getString(R.string.trash_summary_type),j.optString("type"),false);row(card,getString(R.string.trash_detail_field_title),j.optString("title"),false);row(card,getString(R.string.trash_summary_primary),j.optString("primaryText"),false);row(card,getString(R.string.trash_summary_secondary),j.optString("secondaryText"),false);try{flatten(card,new JSONObject(j.optString("contentJson","{}")),"");}catch(Exception ignored){}}
    private void flatten(LinearLayout card,JSONObject object,String prefix){Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();Object value=object.opt(key);if(value instanceof JSONObject)flatten(card,(JSONObject)value,prefix);else if(value instanceof JSONArray)flattenArray(card,(JSONArray)value);else if(value!=null&&value!=JSONObject.NULL)row(card,label(key),String.valueOf(value),isSecret(key));}}
    private void flattenArray(LinearLayout card,JSONArray array){for(int i=0;i<array.length();i++){Object value=array.opt(i);if(value instanceof JSONObject){JSONObject o=(JSONObject)value;String label=first(o.optString("label"),o.optString("key"),getString(R.string.trash_detail_field_generic));String text=first(o.optString("value"),o.optString("content"));if(!text.isEmpty())row(card,label,text,o.optBoolean("sensitive")||isSecret(o.optString("key")));}}}

    private LinearLayout section(String title){TextView heading=text(title,16,true,R.color.text_main);content.addView(heading,top(18));LinearLayout card=new LinearLayout(requireContext());card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(10),dp(14),dp(10));card.setBackgroundResource(R.drawable.bg_card);content.addView(card,top(8));return card;}
    private void row(LinearLayout card,String label,String raw,boolean secret){if(raw==null||raw.trim().isEmpty())return;LinearLayout line=new LinearLayout(requireContext());line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(9),0,dp(9));TextView name=text(label,13,false,R.color.text_secondary);line.addView(name,new LinearLayout.LayoutParams(dp(110),ViewGroup.LayoutParams.WRAP_CONTENT));TextView value=text(secret?mask(raw):raw,15,false,R.color.text_main);value.setTextIsSelectable(!secret);line.addView(value,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if(secret){ImageButton eye=new ImageButton(requireContext());eye.setImageResource(R.drawable.ic_visibility_off_24);eye.setBackgroundResource(R.drawable.bg_icon_action);eye.setOnClickListener(v->{if(raw.contentEquals(value.getText())){value.setText(mask(raw));eye.setImageResource(R.drawable.ic_visibility_off_24);}else SensitiveActionGuard.requireRecentAuth(requireActivity(),getString(R.string.trash_detail_view_sensitive),()->{value.setText(raw);eye.setImageResource(R.drawable.ic_visibility_24);});});line.addView(eye,new LinearLayout.LayoutParams(dp(42),dp(42)));}card.addView(line);}
    private void restore(){if(item==null)return;new AlertDialog.Builder(requireContext()).setTitle(R.string.trash_detail_restore_title).setMessage(R.string.trash_detail_restore_message).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.trash_action_restore,(d,w)->repository.restoreWithResult(item,(ok,failed,message)->FragmentUi.run(this,()->{if(failed==0)getParentFragmentManager().popBackStack();else android.widget.Toast.makeText(requireContext(),message==null?getString(R.string.trash_restore_failed):message,android.widget.Toast.LENGTH_LONG).show();}))).show();}
    private void delete(){if(item==null)return;new AlertDialog.Builder(requireContext()).setTitle(R.string.trash_detail_delete_title).setMessage(R.string.trash_detail_delete_message).setNegativeButton(R.string.common_action_cancel,null).setPositiveButton(R.string.trash_detail_delete_title,(d,w)->repository.permanentlyDelete(item,()->FragmentUi.run(this,()->getParentFragmentManager().popBackStack()))).show();}
    private TextView text(String value,int size,boolean bold,int color){TextView t=new TextView(requireContext());t.setText(value);t.setTextSize(size);t.setTextColor(ContextCompat.getColor(requireContext(),color));if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return t;}
    private LinearLayout.LayoutParams top(int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=dp(margin);return p;}
    private String mask(String raw){int n=Math.max(6,Math.min(16,raw.length()));StringBuilder b=new StringBuilder();while(b.length()<n)b.append('•');return b.toString();}
    private boolean isSecret(String key){String k=key==null?"":key.toLowerCase(Locale.ROOT);return k.contains("password")||k.contains("secret")||k.contains("token")||k.contains("pin")||k.contains("cvv")||k.contains("seed")||k.contains("key")||k.contains("number");}
    private String label(String key){if(key==null)return getString(R.string.trash_detail_field_generic);return key.replaceAll("([a-z])([A-Z])","$1 $2");}
    private String first(String... values){for(String v:values)if(v!=null&&!v.trim().isEmpty())return v;return "";}
    private String typeLabel(String type){if(TrashItem.PASSWORD.equals(type))return getString(R.string.trash_type_password);if(TrashItem.OTP.equals(type))return getString(R.string.trash_type_otp);if(TrashItem.VAULT.equals(type))return getString(R.string.trash_type_vault);return getString(R.string.trash_type_note);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
