package com.magdi.iptv;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Adapts an upstream HTTP range to WebView's intercepted InputStream reader.
 * Chromium seeks the Java stream by the ORIGINAL request offset before reading,
 * even when the upstream has already returned a 206 body at that offset.
 * See components/embedder_support/android/util/input_stream_reader.cc.
 */
final class WebViewRangeInputStream extends FilterInputStream {
 private long skippedUpstream;
 WebViewRangeInputStream(InputStream in,long offset){super(in);skippedUpstream=Math.max(0,offset);}
 // Network available() is a transient buffer estimate, never the full file size.
 // Returning unknown avoids WebView rejecting large offsets against that estimate.
 @Override public int available(){return 0;}
 @Override public long skip(long count)throws IOException {
  if(count<=0)return 0;
  long credited=Math.min(count,skippedUpstream);skippedUpstream-=credited;
  return credited+(count>credited?in.skip(count-credited):0);
 }
 @Override public int read()throws IOException{skippedUpstream=0;return in.read();}
 @Override public int read(byte[] bytes,int offset,int length)throws IOException{if(length>0)skippedUpstream=0;return in.read(bytes,offset,length);}
}
