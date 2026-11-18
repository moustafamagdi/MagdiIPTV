package com.magdi.iptv;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticConnectionsTest {
 @Test public void firstConnectionStartsAndTemporaryCoverDoesNot(){DiagnosticConnections c=new DiagnosticConnections();assertTrue(c.connected(0));assertFalse(c.connected(100));assertFalse(c.connected(900000));}
 @Test public void reconnectWithinFiveMinutesKeepsSession(){DiagnosticConnections c=new DiagnosticConnections();c.connected(0);c.disconnected(100);assertFalse(c.connected(100+DiagnosticConnections.RECONNECT_MS-1));}
 @Test public void reconnectAtFiveMinutesStartsNewSession(){DiagnosticConnections c=new DiagnosticConnections();c.connected(0);c.disconnected(100);assertTrue(c.connected(100+DiagnosticConnections.RECONNECT_MS));}
 @Test public void duplicateDisconnectDoesNotExtendReconnectWindow(){DiagnosticConnections c=new DiagnosticConnections();c.connected(0);c.disconnected(100);c.disconnected(299999);assertTrue(c.connected(300100));}
 @Test public void processRestartBeginsANewCarSession(){DiagnosticConnections c=new DiagnosticConnections();assertTrue(c.connected(900000));assertTrue(new DiagnosticConnections().connected(900001));}
}
