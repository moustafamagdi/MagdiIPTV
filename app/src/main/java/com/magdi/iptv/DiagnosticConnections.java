package com.magdi.iptv;

/** Logging boundary only: temporary onStop/onStart covers never call disconnected(). */
final class DiagnosticConnections {
 static final long RECONNECT_MS=5*60*1000;
 private boolean seen,connected;
 private long detachedAt;
 boolean connected(long elapsed){if(connected)return false;connected=true;boolean fresh=!seen||elapsed-detachedAt>=RECONNECT_MS;seen=true;return fresh;}
 void disconnected(long elapsed){if(connected){connected=false;detachedAt=elapsed;}}
}
