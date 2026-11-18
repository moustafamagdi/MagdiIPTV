package com.magdi.iptv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/** Adapted from Magdi Tube's session store. All access belongs to one background I/O queue. */
class DiagnosticStore {
 static final int PART_BYTES=128*1024,MAX_PARTS=40;
 static final long MAX_AGE_MS=7L*24*60*60*1000;
 private final File directory;
 private final boolean retention;
 private String session;
 private int part;
 private File current;
 private long lastSessionTime;
 DiagnosticStore(File files)throws IOException{this(new File(files,"diagnostic-sessions"),true);}
 private DiagnosticStore(File dir,boolean retention)throws IOException{directory=dir;this.retention=retention;if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Log directory unavailable");}
 void start(String kind,long now)throws IOException{
  if(!Arrays.asList("App","Car","Manual","Imported").contains(kind))throw new IllegalArgumentException("Unknown log kind");
  long stamp=Math.max(now,lastSessionTime+1);lastSessionTime=stamp;
  session=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date(stamp))+"-"+kind+"-"+UUID.randomUUID().toString().substring(0,8);
  part=0;nextPart();append("=== Session started: "+session+" ===\n",now);
 }
 private void nextPart(){current=new File(directory,session+String.format(Locale.US,"-part%06d.txt",++part));}
 void append(String text,long now)throws IOException{
  String[] lines=text.split("(?<=\\n)");
  // Reject an impossible single event before writing anything; never split or silently omit it.
  for(String line:lines)if(line.getBytes(StandardCharsets.UTF_8).length>PART_BYTES)throw new IOException("Diagnostic line exceeds part limit");
  if(current==null){if(session==null)start("App",now);else nextPart();}
  for(String line:lines){byte[] bytes=line.getBytes(StandardCharsets.UTF_8);if(bytes.length==0)continue;
   if(current.length()+bytes.length>PART_BYTES)nextPart();
   try(FileOutputStream out=new FileOutputStream(current,true)){out.write(bytes);}
   current.setLastModified(now);
  }
  if(retention)prune(now);
 }
 private static boolean partFile(File file){return file.isFile()&&file.getName().matches("[0-9]{8}-[0-9]{6}-[0-9]{3}-(App|Car|Manual|Imported)-[a-f0-9]{8}-part[0-9]+\\.txt");}
 private List<File> files(){List<File> result=new ArrayList<>();for(File dir:new File[]{directory,new File(directory,"legacy")}){File[] entries=dir.listFiles();if(entries!=null)for(File f:entries)if(partFile(f))result.add(f);}return result;}
 List<File> list(){List<File> result=files();result.sort(Comparator.<File,Boolean>comparing(f->f.equals(current)).reversed().thenComparing(Comparator.comparingLong(File::lastModified).reversed()).thenComparing(Comparator.comparing(File::getName).reversed()));return result;}
 String currentName(){return current==null?null:current.getName();}
 String read(String name)throws IOException{for(File f:files())if(f.getName().equals(name))return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);throw new FileNotFoundException("Log part expired");}
 void prune(long now){
  List<File> ordered=files();ordered.sort(Comparator.comparingLong(File::lastModified).reversed().thenComparing(Comparator.comparing(File::getName).reversed()));
  int kept=0;for(File f:ordered){if(now-f.lastModified()>=MAX_AGE_MS||kept>=MAX_PARTS){if(f.delete()&&f.equals(current))current=null;}else kept++;}
 }
 void migrateLegacy(File files,long now)throws IOException{
  File committed=new File(directory,"legacy"),staging=new File(directory,".legacy-pending");
  File[] sources={new File(files,"diagnostics.previous.txt"),new File(files,"diagnostics.txt")};
  if(!committed.isDirectory()){
   boolean any=false;for(File f:sources)any|=f.isFile();if(!any)return;
   // A failed attempt is invisible to readers. Rebuild it from untouched originals.
   File[] pending=staging.listFiles();if(pending!=null)for(File f:pending){if(!partFile(f)||!f.delete())throw new IOException("Pending import unavailable");}
   DiagnosticStore imported=new DiagnosticStore(staging,false);imported.start("Imported",now);
   for(File f:sources)if(f.isFile()){
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)imported.append(DiagnosticPrivacy.clean(line)+"\n",now);}
   }
   for(File f:imported.files())try(FileOutputStream out=new FileOutputStream(f,true)){out.getFD().sync();}
   commitMigration(staging,committed);
  }
  // The committed directory is the durable once-only marker, even after retention empties it.
  for(File f:sources)if(f.isFile()&&!f.delete())throw new IOException("Legacy log retirement pending");
  prune(now);
 }
 void commitMigration(File staging,File committed)throws IOException{if(!staging.renameTo(committed))throw new IOException("Unable to commit log migration");}
}
