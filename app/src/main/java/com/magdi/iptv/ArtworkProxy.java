package com.magdi.iptv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.net.*;
import java.util.*;

/** Raster artwork only. Separate from streaming, with no cookies, account headers or URL logging. */
final class ArtworkProxy {
 static final int MAX_DOWNLOAD=4*1024*1024,MAX_CACHE=4*1024*1024;
 private final LinkedHashMap<String,String> targets=new LinkedHashMap<>(64,.75f,true);
 private final LinkedHashMap<String,byte[]> cache=new LinkedHashMap<>(32,.75f,true);
 private int cacheBytes;
 static URL address(String value)throws IOException {
  try {URI uri=URI.create(value);if(value.length()>8192||!Arrays.asList("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null)throw new IOException("Unsupported artwork");return uri.toURL();}
  catch(IllegalArgumentException e){throw new IOException("Unsupported artwork");}
 }
 synchronized String register(String url)throws IOException {
  address(url);String token=null;
  for(Map.Entry<String,String> e:targets.entrySet())if(e.getValue().equals(url)){token=e.getKey();break;}
  if(token==null){token=UUID.randomUUID().toString();if(targets.size()>=4096)targets.remove(targets.keySet().iterator().next());targets.put(token,url);}
  else targets.get(token);
  return StreamProxy.ORIGIN+"/artwork/"+token;
 }
 synchronized void clear(){targets.clear();cache.clear();cacheBytes=0;}
 WebResourceResponse open(String token){
  String url;byte[] bytes;synchronized(this){url=targets.get(token);bytes=url==null?null:cache.get(url);}
  if(url==null)return StreamProxy.reply(404,"text/plain","");
  try{if(bytes==null){bytes=thumbnail(download(address(url)));synchronized(this){
    if(targets.containsKey(token)){byte[] previous=cache.put(url,bytes);cacheBytes+=bytes.length-(previous==null?0:previous.length);while(cacheBytes>MAX_CACHE||cache.size()>128){String oldest=cache.keySet().iterator().next();cacheBytes-=cache.remove(oldest).length;}}
   }}
   Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");headers.put("Content-Security-Policy","default-src 'none'");
   return new WebResourceResponse("image/png",null,200,"OK",headers,new ByteArrayInputStream(bytes));
  }catch(Exception e){return StreamProxy.reply(404,"text/plain","");}
 }
 static byte[] download(URL start)throws IOException {
  URL current=start;long deadline=android.os.SystemClock.elapsedRealtime()+15000;
  for(int redirects=0;redirects<=3;redirects++){
   address(current.toString());HttpURLConnection connection=(HttpURLConnection)current.openConnection();
   try{connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(4000);connection.setReadTimeout(5000);connection.setRequestProperty("Accept","image/*");connection.setRequestProperty("Accept-Encoding","identity");
    int status=connection.getResponseCode();if(status>=300&&status<400){String location=connection.getHeaderField("Location");if(location==null||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException();current=new URL(current,location);continue;}
    if(status!=200||connection.getContentLengthLong()>MAX_DOWNLOAD)throw new IOException();
    try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] block=new byte[8192];int count;while((count=input.read(block))!=-1){if(output.size()+count>MAX_DOWNLOAD||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException();output.write(block,0,count);}return output.toByteArray();}
   }finally{connection.disconnect();}
  }throw new IOException();
 }
 static byte[] thumbnail(byte[] input)throws IOException {
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(input,0,input.length,options);
  if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>24000000)throw new IOException("Invalid artwork");
  options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>768)options.inSampleSize*=2;options.inJustDecodeBounds=false;
  Bitmap bitmap=BitmapFactory.decodeByteArray(input,0,input.length,options);if(bitmap==null)throw new IOException("Invalid artwork");
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();return out.toByteArray();}finally{bitmap.recycle();}
 }
}
