package com.magdi.iptv;

import android.webkit.WebResourceResponse;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/** App-only URL interception: no listening server, open proxy port, or upstream credentials in page URLs. */
final class StreamProxy {
 private static final java.util.concurrent.atomic.AtomicLong rangeIds=new java.util.concurrent.atomic.AtomicLong();
 static final String ORIGIN="https://app.magdi.local";
 private final LinkedHashMap<String,String> targets=new LinkedHashMap<>(64,.75f,true);
 private final Map<String,String> reverse=new HashMap<>();
 synchronized void clear(){targets.clear();reverse.clear();}
 synchronized String register(String url){
  URI u=URI.create(url);if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null)throw new IllegalArgumentException("Unsupported media address");
  String token=reverse.get(url);if(token==null){if(targets.size()>=4096){String oldest=targets.keySet().iterator().next();reverse.remove(targets.remove(oldest));}token=UUID.randomUUID().toString();targets.put(token,url);reverse.put(url,token);}else targets.get(token);
  return ORIGIN+"/media/"+token;
 }
 static WebResourceResponse reply(int code,String mime,String text){return new WebResourceResponse(mime,"UTF-8",code,code==200?"OK":"Request failed",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));}
 WebResourceResponse open(String token,Map<String,String> requestHeaders){
  String url;synchronized(this){url=targets.get(token);}if(url==null)return reply(404,"text/plain","Media expired; select it again");
  HttpURLConnection connection=null;long rangeId=0,started=System.nanoTime();
  for(Map.Entry<String,String> h:requestHeaders.entrySet())if(h.getKey().equalsIgnoreCase("Range")&&h.getValue().matches("bytes=[1-9][0-9]*-[0-9]*")){rangeId=rangeIds.incrementAndGet();Diagnostics.event("Range request id="+rangeId+" started");break;}
  try {
   URL current=new URL(url);
   for(int redirects=0;redirects<=6;redirects++){
    if(!Arrays.asList("http","https").contains(current.getProtocol()))throw new IOException();
    connection=(HttpURLConnection)current.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(25000);connection.setRequestProperty("User-Agent","VLC/3.0.18 LibVLC/3.0.18");connection.setRequestProperty("Accept-Encoding","identity");
    for(Map.Entry<String,String> h:requestHeaders.entrySet())if(h.getKey().equalsIgnoreCase("Range"))connection.setRequestProperty("Range",h.getValue());
    int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null||redirects==6)throw new IOException();current=new URL(current,location);continue;}break;
   }
   int status=connection.getResponseCode();if(rangeId!=0)Diagnostics.event("Range response id="+rangeId+" status="+status+" headers-ms="+((System.nanoTime()-started)/1000000));if(status<200||status>=300){connection.disconnect();Diagnostics.event("Stream HTTP status="+status);return reply(status>=400&&status<=599?status:502,"text/plain","Stream unavailable");}
   String range=null;for(Map.Entry<String,String> h:requestHeaders.entrySet())if(h.getKey().equalsIgnoreCase("Range"))range=h.getValue();
   String rangeFailure=rangeFailure(status,range,connection.getHeaderField("Content-Range"));
   if(rangeFailure!=null){connection.disconnect();Diagnostics.event("Stream range response="+rangeFailure);return reply(502,"text/plain","Requested media range unavailable");}
   String type=connection.getContentType();if(type==null)type="application/octet-stream";type=type.split(";")[0];
   InputStream in=connection.getInputStream();
   if(type.toLowerCase(Locale.ROOT).contains("mpegurl")||current.getPath().toLowerCase(Locale.ROOT).endsWith(".m3u8")){
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
    while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>2*1024*1024)throw new IOException();}in.close();connection.disconnect();
    String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);if(!text.trim().startsWith("#EXTM3U"))return reply(502,"text/plain","Invalid HLS playlist");
    return reply(200,"application/vnd.apple.mpegurl",rewrite(text,current));
   }
   if(type.contains("html")){in.close();connection.disconnect();return reply(502,"text/plain","Network returned a web page, not a stream");}
   final HttpURLConnection active=connection;
   InputStream closing=new FilterInputStream(in){public void close() throws IOException {try{super.close();}finally{active.disconnect();}}};
   Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("Access-Control-Allow-Origin",ORIGIN);
   for(String h:new String[]{"Content-Length","Content-Range","Accept-Ranges"})if(connection.getHeaderField(h)!=null)headers.put(h,connection.getHeaderField(h));
   long skipped=0;if(status==206&&range!=null){Matcher requested=Pattern.compile("bytes=([0-9]+)-[0-9]*").matcher(range);if(requested.matches())skipped=Long.parseLong(requested.group(1));}
   InputStream measured=rangeId==0?closing:new RangeDiagnosticsInputStream(closing,rangeId,Diagnostics::event);
   return new WebResourceResponse(type,null,status,status==206?"Partial Content":"OK",headers,new WebViewRangeInputStream(measured,skipped));
  }catch(Exception e){if(connection!=null)connection.disconnect();if(rangeId!=0)Diagnostics.event("Range request id="+rangeId+" connection-failed");Diagnostics.event("Stream connection error="+e.getClass().getSimpleName());return reply(502,"text/plain","Stream connection failed");}
 }
 // Never pass a full file as if it were a requested nonzero byte offset.
 static String rangeFailure(int status,String requested,String received){
  try{
   Matcher request=Pattern.compile("bytes=([0-9]+)-([0-9]*)").matcher(requested==null?"":requested);
   boolean single=request.matches();long start=single?Long.parseLong(request.group(1)):0;
   if(single&&start>0&&status==200)return "ignored";
   if(status!=206)return null;
   Matcher response=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)").matcher(received==null?"":received);
   if(!response.matches())return "invalid";
   long first=Long.parseLong(response.group(1)),last=Long.parseLong(response.group(2));
   if(last<first||(!"*".equals(response.group(3))&&Long.parseLong(response.group(3))<=last))return "invalid";
   if(single&&(first!=start||(!request.group(2).isEmpty()&&last>Long.parseLong(request.group(2)))))return "mismatch";
   return null;
  }catch(NumberFormatException e){return "invalid";}
 }
 String rewrite(String text,URL base) throws Exception {
  StringBuilder output=new StringBuilder();Pattern uri=Pattern.compile("URI=\"([^\"]+)\"");
  for(String line:text.split("\r?\n")){
   String result=line;
   if(line.startsWith("#")){Matcher m=uri.matcher(line);StringBuffer b=new StringBuffer();while(m.find())m.appendReplacement(b,Matcher.quoteReplacement("URI=\""+register(new URL(base,m.group(1)).toString())+"\""));m.appendTail(b);result=b.toString();}
   else if(!line.trim().isEmpty())result=register(new URL(base,line.trim()).toString());
   output.append(result).append('\n');
  }return output.toString();
 }
}
