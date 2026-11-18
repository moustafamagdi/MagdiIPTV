package com.magdi.iptv;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DiagnosticStoreTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 private DiagnosticStore store;private final long now=1795000000000L;
 @Before public void setup()throws Exception{store=new DiagnosticStore(folder.getRoot());}
 @Test public void sessionsRemainSeparateAndCurrentIsFirst()throws Exception{store.start("Car",now);store.append("first\n",now);String first=store.currentName();store.start("Manual",now);store.append("second\n",now);assertTrue(store.read(first).contains("first\n"));assertFalse(store.read(first).contains("second\n"));assertEquals(store.currentName(),store.list().get(0).getName());assertEquals(2,store.list().size());}
 @Test public void unicodeEventsRoundTripAcrossNumberedParts()throws Exception{
  store.start("Car",now);StringBuilder expected=new StringBuilder();String line=String.join("",Collections.nCopies(500,"تشغيل 🚗 "));
  for(int i=0;i<80;i++){String event=i+":"+line+"\n";expected.append(event);store.append(event,now+i);}
  List<File> files=store.list();files.sort(Comparator.comparing(File::getName));assertTrue(files.size()>1);StringBuilder actual=new StringBuilder();
  for(File f:files){assertTrue(f.length()<=DiagnosticStore.PART_BYTES);String data=store.read(f.getName());assertTrue(data.endsWith("\n"));assertFalse(data.contains("\ufffd"));actual.append(data);}
  assertTrue(actual.toString().endsWith(expected.toString()));
 }
 @Test public void multilineAppendSplitsOnlyBetweenEventLines()throws Exception{store.start("App",now);String a="a".repeat(80000)+"\n",b="ش".repeat(30000)+"\n";store.append(a+b,now);assertEquals(2,store.list().size());String joined="";List<File> f=store.list();Collections.reverse(f);for(File part:f)joined+=store.read(part.getName());assertTrue(joined.endsWith(a+b));}
 @Test public void oversizedSingleLineIsRejectedWithoutPartialWrite()throws Exception{store.start("App",now);String name=store.currentName(),before=store.read(name);try{store.append("valid\n"+"x".repeat(DiagnosticStore.PART_BYTES+1),now);fail();}catch(IOException expected){}assertEquals(before,store.read(name));}
 @Test public void countRetentionRemovesOldestAndKeepsExactlyForty()throws Exception{store.start("Manual",now);String oldest=store.currentName();for(int i=1;i<45;i++)store.start("Manual",now+i);assertEquals(40,store.list().size());try{store.read(oldest);fail();}catch(FileNotFoundException expected){}assertTrue(store.read(store.currentName()).contains("Session started"));}
 @Test public void ageRetentionExpiresEvenAnInactiveCurrentPart()throws Exception{store.start("Car",now);store.prune(now+DiagnosticStore.MAX_AGE_MS);assertTrue(store.list().isEmpty());store.append("after expiration\n",now+DiagnosticStore.MAX_AGE_MS);assertEquals(1,store.list().size());assertTrue(store.read(store.currentName()).contains("after expiration"));}
 @Test public void retentionDoesNotDeleteUnrelatedFilesOrExternalExports()throws Exception{File unrelated=new File(folder.getRoot(),"diagnostic-sessions/keep.txt"),external=new File(folder.getRoot(),"saved-export.txt");Files.write(unrelated.toPath(),"untouched".getBytes(StandardCharsets.UTF_8));Files.write(external.toPath(),"saved".getBytes(StandardCharsets.UTF_8));store.start("App",now);store.prune(now+DiagnosticStore.MAX_AGE_MS);assertTrue(unrelated.exists());assertTrue(external.exists());}
 private File legacy(String name,String text)throws Exception{File f=new File(folder.getRoot(),name);Files.write(f.toPath(),text.getBytes(StandardCharsets.UTF_8));return f;}
 @Test public void migrationPreservesBothFilesInOrderAndRunsOnce()throws Exception{File previous=legacy("diagnostics.previous.txt","السابق 🚗\n"),current=legacy("diagnostics.txt","الحالي\n");store.migrateLegacy(folder.getRoot(),now);assertFalse(previous.exists());assertFalse(current.exists());assertEquals(1,store.list().size());String body=store.read(store.list().get(0).getName());assertTrue(body.endsWith("السابق 🚗\nالحالي\n"));new DiagnosticStore(folder.getRoot()).migrateLegacy(folder.getRoot(),now+1);assertEquals(1,store.list().size());}
 @Test public void failedMigrationRetainsOriginalsAndRetryDoesNotDuplicate()throws Exception{
  File old=legacy("diagnostics.previous.txt","old event\n"),recent=legacy("diagnostics.txt","current event\n");
  DiagnosticStore failing=new DiagnosticStore(folder.getRoot()){@Override void commitMigration(File a,File b)throws IOException{throw new IOException("Simulated storage failure");}};
  try{failing.migrateLegacy(folder.getRoot(),now);fail();}catch(IOException expected){}
  assertTrue(old.exists());assertTrue(recent.exists());assertTrue(store.list().isEmpty());store.migrateLegacy(folder.getRoot(),now+1);assertEquals(1,store.list().size());String body=store.read(store.list().get(0).getName());assertEquals(1,body.split("old event",-1).length-1);assertTrue(body.endsWith("old event\ncurrent event\n"));
 }
 @Test public void crashAfterCommitDoesNotReimportOriginals()throws Exception{store.migrateLegacy(folder.getRoot(),now);legacy("diagnostics.txt","original\n");store.migrateLegacy(folder.getRoot(),now);int count=store.list().size();legacy("diagnostics.txt","original\n");new DiagnosticStore(folder.getRoot()).migrateLegacy(folder.getRoot(),now+1);assertEquals(count,store.list().size());assertFalse(new File(folder.getRoot(),"diagnostics.txt").exists());}
 @Test public void oversizeLegacyLineKeepsOldFilesUntilSuccessfulRetry()throws Exception{File old=legacy("diagnostics.txt","x".repeat(DiagnosticStore.PART_BYTES+1));try{store.migrateLegacy(folder.getRoot(),now);fail();}catch(IOException expected){}assertTrue(old.exists());assertTrue(store.list().isEmpty());Files.write(old.toPath(),"recovered\n".getBytes(StandardCharsets.UTF_8));store.migrateLegacy(folder.getRoot(),now);assertFalse(old.exists());assertEquals(1,store.list().size());}
 @Test public void migrationRedactsUnsafeLegacyData()throws Exception{legacy("diagnostics.txt","Stream https://private.example/live/private-user/private-pass/1.m3u8?token=abc\nusername=private-user\npassword=private-pass\n");store.migrateLegacy(folder.getRoot(),now);String body=store.read(store.list().get(0).getName());for(String secret:new String[]{"private.example","private-user","private-pass","abc"})assertFalse(body.contains(secret));assertTrue(body.contains("Redacted"));}
 @Test public void traversalAndUnknownPartReadsAreRejected()throws Exception{store.start("App",now);for(String name:new String[]{"../diagnostics.txt","/etc/passwd","unknown.txt"})try{store.read(name);fail();}catch(FileNotFoundException expected){}}
}
