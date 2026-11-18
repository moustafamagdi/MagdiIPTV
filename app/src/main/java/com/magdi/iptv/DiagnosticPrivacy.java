package com.magdi.iptv;

import java.util.regex.Pattern;

/** Defense in depth; callers still emit only static labels, bounded codes and numeric samples. */
final class DiagnosticPrivacy {
 private static final Pattern URL=Pattern.compile("[a-z][a-z0-9+.-]*://[^\\s\"<>]+",Pattern.CASE_INSENSITIVE);
 private static final Pattern SENSITIVE=Pattern.compile("\\b[a-z0-9_.-]*(?:username|user|password|passwd|pwd|token|secret|cookie|authorization|credential[s]?|api[-_]?key|account|provider|server|host)[\"']?\\s*[:=][^\\r\\n]*",Pattern.CASE_INSENSITIVE);
 private static final Pattern AUTH=Pattern.compile("\\b(?:Bearer|Basic)\\s+[^\\r\\n]+",Pattern.CASE_INSENSITIVE);
 static String clean(String text){if(text==null)return "";String safe=URL.matcher(text.replace("\\/","/")).replaceAll("[Redacted URL]");safe=SENSITIVE.matcher(safe).replaceAll("[Redacted account details]");return AUTH.matcher(safe).replaceAll("[Redacted authorization]");}
 private DiagnosticPrivacy(){}
}
