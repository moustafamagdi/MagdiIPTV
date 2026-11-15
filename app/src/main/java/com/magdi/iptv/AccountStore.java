package com.magdi.iptv;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials stay in application storage, encrypted with an Android Keystore key. */
final class AccountStore {
 private final Context app;
 AccountStore(Context c){app=c.getApplicationContext();}
 private SecretKey key() throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias("iptv-account")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("iptv-account",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
  return (SecretKey)store.getKey("iptv-account",null);
 }
 synchronized JSONObject read() throws Exception {
  android.content.SharedPreferences p=app.getSharedPreferences("account",0);
  String encrypted=p.getString("data",null);
  if(encrypted==null){
   if(!p.getBoolean("seeded",false)){
    JSONObject seed;
    try(java.io.InputStream in=app.getAssets().open("initial-account.json")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);seed=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
    catch(java.io.FileNotFoundException e){seed=new JSONObject();}
    if(seed.length()>0){save(seed);return seed;}
    p.edit().putBoolean("seeded",true).commit();
   }
   return new JSONObject();
  }
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
  return new JSONObject(new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8));
 }
 synchronized void save(JSONObject account) throws Exception {
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  String data=Base64.encodeToString(cipher.doFinal(account.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
  if(!app.getSharedPreferences("account",0).edit().putString("data",data).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putBoolean("seeded",true).commit())throw new java.io.IOException("Save failed");
 }
 synchronized void clear(){app.getSharedPreferences("account",0).edit().clear().putBoolean("seeded",true).commit();}
}
