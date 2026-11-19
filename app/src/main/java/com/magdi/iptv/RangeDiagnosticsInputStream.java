package com.magdi.iptv;

import java.io.*;
import java.util.function.Consumer;

/** Fixed diagnostic vocabulary and numeric counters only; never retain a URL or exception message. */
final class RangeDiagnosticsInputStream extends FilterInputStream {
 private final long id,started=System.nanoTime();private final Consumer<String> log;
 private long bytes;private boolean first,finished;
 RangeDiagnosticsInputStream(InputStream in,long id,Consumer<String> log){super(in);this.id=id;this.log=log;}
 private void received(int n){if(n>0){bytes+=n;if(!first){first=true;log.accept("Range body id="+id+" first-byte-ms="+elapsed());}}else if(n<0)finish("eof");}
 private long elapsed(){return (System.nanoTime()-started)/1000000;}
 private void finish(String outcome){if(!finished){finished=true;log.accept("Range body id="+id+" outcome="+outcome+" bytes="+bytes+" elapsed-ms="+elapsed());}}
 @Override public int read()throws IOException{try{int n=in.read();received(n<0?-1:1);return n;}catch(IOException e){finish("read-error");throw e;}}
 @Override public int read(byte[] b,int off,int len)throws IOException{try{int n=in.read(b,off,len);received(n);return n;}catch(IOException e){finish("read-error");throw e;}}
 @Override public void close()throws IOException{try{super.close();}finally{finish("closed");}}
}
