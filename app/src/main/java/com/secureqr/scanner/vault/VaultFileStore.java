package com.secureqr.scanner.vault;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public final class VaultFileStore {
    private static final String KEY_ALIAS = "keyscan_vault_attachment_v1";
    private final Context context;
    public VaultFileStore(Context context) { this.context=context.getApplicationContext(); }

    public Stored encrypt(Uri source, String attachmentId) throws Exception {
        File dir = new File(context.getFilesDir(), "vault/attachments");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create vault storage");
        File target = new File(dir, attachmentId + ".enc");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        long size = 0;
        try (InputStream in=context.getContentResolver().openInputStream(source); FileOutputStream out=new FileOutputStream(target)) {
            if (in == null) throw new IllegalStateException("Cannot open attachment");
            byte[] iv=cipher.getIV(); out.write(iv.length); out.write(iv);
            byte[] buffer=new byte[32*1024]; int n;
            while((n=in.read(buffer))>0){ digest.update(buffer,0,n); size+=n; byte[] encrypted=cipher.update(buffer,0,n); if(encrypted!=null) out.write(encrypted); }
            byte[] last=cipher.doFinal(); if(last!=null) out.write(last);
        }
        return new Stored(relative(target), hex(digest.digest()), size);
    }

    public File decrypt(String encryptedPath, String exportName) throws Exception {
        File source=resolve(encryptedPath);
        File dir=new File(context.getCacheDir(),"vault_exports"); if(!dir.exists()) dir.mkdirs();
        File target=new File(dir, safe(exportName));
        try(FileInputStream in=new FileInputStream(source); FileOutputStream out=new FileOutputStream(target)){
            int ivLength=in.read(); if(ivLength<12||ivLength>32) throw new SecurityException("Invalid vault file");
            byte[] iv=new byte[ivLength]; if(in.read(iv)!=ivLength) throw new SecurityException("Invalid vault file");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            byte[] buffer=new byte[32*1024]; int n; while((n=in.read(buffer))>0){ byte[] plain=cipher.update(buffer,0,n); if(plain!=null) out.write(plain); }
            byte[] last=cipher.doFinal(); if(last!=null) out.write(last);
        }
        return target;
    }
    public byte[] decryptBytes(String encryptedPath) throws Exception {
        File source=resolve(encryptedPath); try(FileInputStream in=new FileInputStream(source);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            int ivLength=in.read(); if(ivLength<12||ivLength>32)throw new SecurityException("Invalid vault file");byte[] iv=new byte[ivLength];if(in.read(iv)!=ivLength)throw new SecurityException("Invalid vault file");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));byte[] b=new byte[32768];int n;while((n=in.read(b))>0){byte[] p=cipher.update(b,0,n);if(p!=null)out.write(p);}byte[] last=cipher.doFinal();if(last!=null)out.write(last);return out.toByteArray();}
    }
    /** Streams decrypted content to a caller-owned destination without creating a plaintext file. */
    public void decryptTo(String encryptedPath, java.io.OutputStream destination) throws Exception {
        File source=resolve(encryptedPath);try(FileInputStream in=new FileInputStream(source)){
            int ivLength=in.read();if(ivLength<12||ivLength>32)throw new SecurityException("Invalid vault file");byte[] iv=new byte[ivLength];if(in.read(iv)!=ivLength)throw new SecurityException("Invalid vault file");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));byte[] b=new byte[32768];int n;while((n=in.read(b))>0){byte[] p=cipher.update(b,0,n);if(p!=null)destination.write(p);}byte[] last=cipher.doFinal();if(last!=null)destination.write(last);
        }
    }
    /** Streams plaintext into the local Keystore-protected store and validates it before committing. */
    public Stored encrypt(java.io.InputStream plain,String attachmentId,long expectedSize,String expectedHash) throws Exception {
        File dir=new File(context.getFilesDir(),"vault/attachments");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create vault storage");File target=new File(dir,attachmentId+".enc");File partial=new File(dir,attachmentId+".enc.partial");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());long size=0;
        try(FileOutputStream out=new FileOutputStream(partial)){byte[] iv=cipher.getIV();out.write(iv.length);out.write(iv);byte[] b=new byte[32768];int n;while((n=plain.read(b))>0){digest.update(b,0,n);size+=n;byte[] encrypted=cipher.update(b,0,n);if(encrypted!=null)out.write(encrypted);}byte[] last=cipher.doFinal();if(last!=null)out.write(last);}catch(Exception e){partial.delete();throw e;}
        String hash=hex(digest.digest());if(size!=expectedSize||expectedHash==null||!expectedHash.equalsIgnoreCase(hash)){partial.delete();throw new SecurityException("Attachment integrity verification failed");}
        if(target.exists()&&!target.delete()){partial.delete();throw new IllegalStateException("Cannot replace vault attachment");}if(!partial.renameTo(target)){partial.delete();throw new IllegalStateException("Cannot finalize vault attachment");}return new Stored(relative(target),hash,size);
    }
    public Stored encryptBytes(byte[] plain,String attachmentId) throws Exception {
        File dir=new File(context.getFilesDir(),"vault/attachments");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create vault storage");File target=new File(dir,attachmentId+".enc");MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(plain);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());try(FileOutputStream out=new FileOutputStream(target)){byte[] iv=cipher.getIV();out.write(iv.length);out.write(iv);out.write(cipher.doFinal(plain));}return new Stored(relative(target),hex(digest.digest()),plain.length);
    }
    public File resolve(String relative){ return new File(context.getFilesDir(),relative); }
    public void delete(String relative){ File f=resolve(relative); if(f.exists()) f.delete(); }
    public String filename(Uri uri){ try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){ if(c!=null&&c.moveToFirst()) return c.getString(0); }catch(Exception ignored){} return "attachment"; }
    private SecretKey key() throws Exception { KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null); if(ks.containsAlias(KEY_ALIAS)) return (SecretKey)ks.getKey(KEY_ALIAS,null); KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore"); g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); return g.generateKey(); }
    private String relative(File f){ return "vault/attachments/"+f.getName(); }
    private String safe(String n){ return n==null?"attachment":n.replace("/","_").replace("\\","_"); }
    private String hex(byte[] bytes){ StringBuilder b=new StringBuilder(); for(byte v:bytes)b.append(String.format("%02x",v)); return b.toString(); }
    public static final class Stored { public final String path,hash; public final long size; Stored(String p,String h,long s){path=p;hash=h;size=s;} }
}
