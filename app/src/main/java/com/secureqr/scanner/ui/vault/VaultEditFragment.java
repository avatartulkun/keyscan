package com.secureqr.scanner.ui.vault;

import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.vault.VaultTypes;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public final class VaultEditFragment extends Fragment {
    private static final String TYPE="type", ID="id";
    private static final String PREFILL_TITLE="prefill_title", PREFILL_NOTES="prefill_notes", PREFILL_FIELDS="prefill_fields";
    private static final String PREFILL_URIS="prefill_uris", PREFILL_PATHS="prefill_paths", PREFILL_NAMES="prefill_names";
    private final Map<String, EditText> inputs=new LinkedHashMap<>();
    private final List<Uri> pending=new ArrayList<>();
    private final Map<Uri,File> pendingTemporaryFiles=new LinkedHashMap<>();
    private VaultRepository repo; private VaultItem item; private VaultTypes.Type type;
    private LinearLayout form; private TextView attachmentState; private ActivityResultLauncher<String[]> picker;
    private ActivityResultLauncher<Uri> camera; private Uri pendingCameraUri; private File pendingCameraFile;
    private boolean saveStarted;

    public static VaultEditFragment newItem(String type){VaultEditFragment f=new VaultEditFragment();Bundle b=new Bundle();b.putString(TYPE,type);f.setArguments(b);return f;}
    public static VaultEditFragment edit(String id){VaultEditFragment f=new VaultEditFragment();Bundle b=new Bundle();b.putString(ID,id);f.setArguments(b);return f;}
    public static VaultEditFragment newScannedItem(String type,String title,String notes,String fields,
            ArrayList<String> uris,ArrayList<String> paths,ArrayList<String> names){
        VaultEditFragment f=new VaultEditFragment();Bundle b=new Bundle();b.putString(TYPE,type);
        b.putString(PREFILL_TITLE,title);b.putString(PREFILL_NOTES,notes);b.putString(PREFILL_FIELDS,fields);
        b.putStringArrayList(PREFILL_URIS,uris);b.putStringArrayList(PREFILL_PATHS,paths);b.putStringArrayList(PREFILL_NAMES,names);
        f.setArguments(b);return f;
    }

    @Override public void onCreate(@Nullable Bundle state){super.onCreate(state);picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri==null)return;try{requireContext().getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}storeAttachment(uri,requireContext().getContentResolver().getType(uri),null,false);});camera=registerForActivityResult(new ActivityResultContracts.TakePicture(),ok->{if(Boolean.TRUE.equals(ok)&&pendingCameraFile!=null&&pendingCameraFile.length()>0)showCameraPreview();else discardCameraPhoto();});}
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){return inflater.inflate(R.layout.fragment_vault_form,container,false);}

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        repo=new VaultRepository(requireContext()); form=view.findViewById(R.id.form_container);
        view.findViewById(R.id.btn_back).setOnClickListener(v->getParentFragmentManager().popBackStack());
        view.findViewById(R.id.btn_more).setVisibility(View.GONE);
        view.findViewById(R.id.btn_secondary).setVisibility(View.GONE);
        Button save=view.findViewById(R.id.btn_primary); save.setText(R.string.common_action_save); save.setOnClickListener(v->{
            if(item!=null&&item.createdTime>0)OperationModeGuard.requireEdit(this,this::save,
                    ()->getParentFragmentManager().popBackStack());
            else save();
        });
        String id=requireArguments().getString(ID);
        if(id!=null) repo.getById(id,loaded->FragmentUi.run(this,()->build(loaded)));
        else {
            type=VaultTypes.find(requireArguments().getString(TYPE));
            VaultItem created=new VaultItem(); created.type=VaultTypes.storageType(type); created.category=type.category;
            created.title=requireArguments().getString(PREFILL_TITLE,"");
            created.notes=requireArguments().getString(PREFILL_NOTES,"");
            created.fieldsJson=requireArguments().getString(PREFILL_FIELDS,"");
            if(created.fieldsJson.isEmpty()&&VaultTypes.IDENTITY.equals(type.category)) try{created.fieldsJson=new JSONObject().put("documentType",type.key).toString();}catch(Exception ignored){}
            build(created);
            restoreScannedAttachments();
        }
    }

    private void restoreScannedAttachments(){
        if(!pending.isEmpty())return;
        ArrayList<String> uris=requireArguments().getStringArrayList(PREFILL_URIS);
        ArrayList<String> paths=requireArguments().getStringArrayList(PREFILL_PATHS);
        if(uris==null)return;
        for(int i=0;i<uris.size();i++){
            Uri uri=Uri.parse(uris.get(i));pending.add(uri);
            if(paths!=null&&i<paths.size()&&!paths.get(i).isEmpty())pendingTemporaryFiles.put(uri,new File(paths.get(i)));
        }
        refreshPending();
    }

    private void build(VaultItem loaded){
        item=loaded; if(type==null||item.createdTime>0) type=VaultTypes.resolveStored(item.type,item.fieldsJson);
        ((TextView)requireView().findViewById(R.id.tv_title)).setText(getString(item.createdTime>0?R.string.vault_edit_title:R.string.vault_new_title,getString(type.labelRes)));
        form.removeAllViews(); inputs.clear(); JSONObject data; try{data=new JSONObject(item.fieldsJson);}catch(Exception e){data=new JSONObject();}
        for(VaultFormSchema.Section section:VaultFormSchema.forType(type)){
            LinearLayout card=VaultFormComponents.section(requireContext(),VaultFormSchema.sectionTitle(requireContext(),section),section.icon);
            for(int index=0;index<section.fields.size();index++){
                VaultFormSchema.Field field=section.fields.get(index);
                if(field.row!=null&&index+1<section.fields.size()&&field.row.equals(section.fields.get(index+1).row)){
                    LinearLayout row=new LinearLayout(requireContext()); row.setOrientation(LinearLayout.HORIZONTAL);
                    addField(row,field,data,true); addField(row,section.fields.get(++index),data,true); card.addView(row,new LinearLayout.LayoutParams(-1,-2));
                }else addField(card,field,data,false);
            }
            form.addView(card);
        }
        if(item.createdTime>0) repo.observeAttachments(item.id).observe(getViewLifecycleOwner(),list->{if(attachmentState!=null)attachmentState.setText(list.isEmpty()?getString(R.string.vault_attachment_none):getString(R.string.vault_attachment_saved_count,list.size()));});
    }

    private void addField(LinearLayout parent,VaultFormSchema.Field field,JSONObject data,boolean weighted){
        if(field.kind==VaultFormSchema.Kind.ATTACHMENT){
            String label=VaultFormSchema.fieldLabel(requireContext(),field); LinearLayout box=VaultFormComponents.field(requireContext(),label); Button pick=VaultFormComponents.attachment(requireContext(),label);
            pick.setOnClickListener(v->picker.launch(new String[]{"image/*","application/pdf","application/octet-stream","text/*"}));
            pick.setText(R.string.vault_action_choose_file);
            Button take=new Button(requireContext()); take.setText(R.string.vault_action_take_photo); take.setOnClickListener(v->launchCamera());
            LinearLayout actions=new LinearLayout(requireContext()); actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(pick,new LinearLayout.LayoutParams(0,VaultFormComponents.dp(requireContext(),46),1));
            actions.addView(take,new LinearLayout.LayoutParams(0,VaultFormComponents.dp(requireContext(),46),1));
            box.addView(actions,new LinearLayout.LayoutParams(-1,VaultFormComponents.dp(requireContext(),46)));
            attachmentState=new TextView(requireContext()); attachmentState.setText(R.string.vault_attachment_none); attachmentState.setTextColor(requireContext().getColor(R.color.text_secondary)); attachmentState.setTextSize(12); attachmentState.setPadding(4,6,4,6); box.addView(attachmentState); addBox(parent,box,weighted); return;
        }
        String value="title".equals(field.key)?item.title:("notes".equals(field.key)?item.notes:data.optString(field.key));
        String label=VaultFormSchema.fieldLabel(requireContext(),field); String hint=VaultFormSchema.fieldHint(requireContext(),field); LinearLayout box=VaultFormComponents.field(requireContext(),label); EditText edit;
        if(field.kind==VaultFormSchema.Kind.SECRET) edit=VaultFormComponents.secret(requireContext(),hint,value,box);
        else {boolean multi=field.kind==VaultFormSchema.Kind.MULTILINE;edit=VaultFormComponents.text(requireContext(),hint,value,multi);box.addView(edit,new LinearLayout.LayoutParams(-1,multi?VaultFormComponents.dp(requireContext(),112):VaultFormComponents.dp(requireContext(),46)));}
        if("documentType".equals(field.key)&&requireArguments().containsKey(PREFILL_URIS))installScannedDocumentTypePicker(edit,value);
        if("BANK_CARD".equals(type.key)&&"expiryDate".equals(field.key)) installCardExpiryFormatter(edit);
        inputs.put(field.key,edit); addBox(parent,box,weighted);
    }

    private void installScannedDocumentTypePicker(EditText edit,String value){
        String[] keys={"NATIONAL_ID","PASSPORT","DRIVER_LICENSE","OTHER_ID"};
        String[] labels=getResources().getStringArray(R.array.scanner_document_type_labels);
        int selected=3;for(int i=0;i<keys.length;i++)if(keys[i].equals(value)){selected=i;break;}
        edit.setText(labels[selected]);edit.setTag(keys[selected]);edit.setFocusable(false);edit.setClickable(true);
        edit.setOnClickListener(v->new AlertDialog.Builder(requireContext()).setTitle(R.string.scanner_document_type_title)
                .setItems(labels,(d,which)->{edit.setText(labels[which]);edit.setTag(keys[which]);}).show());
    }

    private void installCardExpiryFormatter(EditText edit){edit.setHint(R.string.vault_hint_card_expiry);edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);edit.addTextChangedListener(new TextWatcher(){boolean changing;public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){}public void afterTextChanged(Editable value){if(changing)return;String digits=value.toString().replaceAll("\\D","");if(digits.length()>4)digits=digits.substring(0,4);String formatted=digits.length()>2?digits.substring(0,2)+"/"+digits.substring(2):digits;if(!formatted.equals(value.toString())){changing=true;value.replace(0,value.length(),formatted);changing=false;}}});}

    private void addBox(LinearLayout parent,LinearLayout box,boolean weighted){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(weighted?0:-1,-2,weighted?1:0);p.setMargins(weighted?4:0,0,weighted?4:0,VaultFormComponents.dp(requireContext(),8));parent.addView(box,p);}
    private void refreshPending(){if(attachmentState!=null)attachmentState.setText(pending.isEmpty()?getString(R.string.vault_attachment_none):getString(R.string.vault_attachment_selected_count,pending.size()));}

    private void launchCamera(){
        discardCameraPhoto();
        File dir=new File(requireContext().getCacheDir(),"smart_scan");
        if(!dir.exists()&&!dir.mkdirs()){Toast.makeText(requireContext(),R.string.vault_camera_temp_failed,Toast.LENGTH_SHORT).show();return;}
        pendingCameraFile=new File(dir,"vault_attachment_"+System.currentTimeMillis()+".jpg");
        pendingCameraUri=FileProvider.getUriForFile(requireContext(),requireContext().getPackageName()+".fileprovider",pendingCameraFile);
        camera.launch(pendingCameraUri);
    }

    private void showCameraPreview(){
        ImageView preview=new ImageView(requireContext()); preview.setAdjustViewBounds(true); preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setImageBitmap(BitmapFactory.decodeFile(pendingCameraFile.getAbsolutePath()));
        AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle(R.string.vault_photo_confirm_title).setView(preview)
                .setNegativeButton(R.string.common_action_cancel,(d,w)->discardCameraPhoto()).setNeutralButton(R.string.vault_action_retake,null).setPositiveButton(R.string.vault_action_use_photo,null).create();
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{dialog.dismiss();launchCamera();});dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{Uri uri=pendingCameraUri;File file=pendingCameraFile;pendingCameraUri=null;pendingCameraFile=null;dialog.dismiss();storeAttachment(uri,"image/jpeg",file.getName(),true);});});
        dialog.show();
    }

    private void storeAttachment(Uri uri,String mime,String filename,boolean temporary){
        File temporaryFile=temporary?new File(requireContext().getCacheDir(),"smart_scan/"+filename):null;
        if(item!=null&&item.createdTime>0)repo.addAttachment(item.id,uri,mime,filename,e->{if(temporaryFile!=null)temporaryFile.delete();FragmentUi.run(this,()->Toast.makeText(requireContext(),e==null?R.string.vault_attachment_saved:R.string.vault_attachment_save_failed,Toast.LENGTH_SHORT).show());});
        else{pending.add(uri);if(temporaryFile!=null)pendingTemporaryFiles.put(uri,temporaryFile);refreshPending();}
    }

    private void discardCameraPhoto(){if(pendingCameraFile!=null&&pendingCameraFile.exists())pendingCameraFile.delete();pendingCameraFile=null;pendingCameraUri=null;}

    private void save(){
        EditText title=inputs.get("title"); if(title==null||title.getText().toString().trim().isEmpty()){if(title!=null)title.setError(getString(R.string.vault_name_required));return;}
        VaultAccessManager.requireUnlocked(requireActivity(),getString(R.string.vault_auth_save),this::persist);
    }

    private void persist(){
        EditText title=inputs.get("title");
        item.title=title.getText().toString().trim(); EditText notes=inputs.get("notes"); item.notes=notes==null?"":notes.getText().toString();
        JSONObject fields; try{fields=new JSONObject(item.fieldsJson);}catch(Exception e){fields=new JSONObject();}
        for(Map.Entry<String,EditText> entry:inputs.entrySet()) if(!"title".equals(entry.getKey())&&!"notes".equals(entry.getKey())) try{Object tagged=entry.getValue().getTag();fields.put(entry.getKey(),"documentType".equals(entry.getKey())&&tagged instanceof String?tagged:entry.getValue().getText().toString());}catch(Exception ignored){}
        if(VaultTypes.IDENTITY.equals(type.category)&&fields.optString("documentType").trim().isEmpty())try{fields.put("documentType",type.key);}catch(Exception ignored){}
        item.type=VaultTypes.storageType(type); item.category=type.category; item.fieldsJson=fields.toString();
        saveStarted=true;
        repo.save(item,()->{for(Uri uri:pending){String mime=requireContext().getContentResolver().getType(uri);if(mime==null&&pendingTemporaryFiles.containsKey(uri))mime="image/jpeg";String finalMime=mime;File temporaryFile=pendingTemporaryFiles.get(uri);repo.addAttachment(item.id,uri,finalMime,e->{if(temporaryFile!=null)temporaryFile.delete();});}FragmentUi.run(this,()->{Toast.makeText(requireContext(),R.string.vault_saved,Toast.LENGTH_SHORT).show();getParentFragmentManager().popBackStack();});});
    }

    @Override public void onDestroy(){discardCameraPhoto();if(!saveStarted&&!requireActivity().isChangingConfigurations())for(File file:pendingTemporaryFiles.values())if(file!=null&&file.exists())file.delete();super.onDestroy();}
}
