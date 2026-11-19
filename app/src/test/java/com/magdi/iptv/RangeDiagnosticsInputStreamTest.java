package com.magdi.iptv;
import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RangeDiagnosticsInputStreamTest {
 @Test public void countsReadsWithoutDoubleCountingAndFinishesOnce()throws Exception{
  List<String> logs=new ArrayList<>();InputStream in=new RangeDiagnosticsInputStream(new ByteArrayInputStream(new byte[]{1,2,3}),7,logs::add);
  assertEquals(1,in.read());assertEquals(2,in.read(new byte[8]));assertEquals(-1,in.read());in.close();
  assertEquals(2,logs.size());assertTrue(logs.get(0).contains("first-byte-ms="));assertTrue(logs.get(1).contains("outcome=eof bytes=3"));
 }
 @Test public void errorsNeverLogExceptionMessagesOrCredentials()throws Exception{
  List<String> logs=new ArrayList<>();InputStream in=new RangeDiagnosticsInputStream(new InputStream(){public int read()throws IOException{throw new IOException("https://private.example/password/token");}},8,logs::add);
  try{in.read();fail();}catch(IOException expected){}in.close();assertEquals(1,logs.size());assertTrue(logs.get(0).contains("outcome=read-error bytes=0"));assertFalse(logs.toString().contains("private"));assertFalse(logs.toString().contains("password"));
 }
 @Test public void closingWithoutDataIsDistinctFromReadFailure()throws Exception{
  List<String> logs=new ArrayList<>();InputStream in=new RangeDiagnosticsInputStream(new ByteArrayInputStream(new byte[]{1}),9,logs::add);in.close();in.close();assertEquals(1,logs.size());assertTrue(logs.get(0).contains("outcome=closed bytes=0"));
 }
}
