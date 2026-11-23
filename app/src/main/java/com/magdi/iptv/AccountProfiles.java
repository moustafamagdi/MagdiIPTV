package com.magdi.iptv;

import java.util.Iterator;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The entire book, including account labels, is stored in the existing encrypted envelope. */
final class AccountProfiles {
    private final JSONObject accounts;
    private String active;
    private String lastConnected;

    AccountProfiles(JSONObject saved) throws JSONException {
        JSONObject entries = saved.optJSONObject("profiles");
        if (entries != null) {
            accounts = entries;
            active = saved.optString("active");
            lastConnected = saved.optString("lastConnected");
        } else {
            accounts = new JSONObject();
            active = "";
            lastConnected = "";
            if (saved.length() != 0) {
                active = "original";
                lastConnected = active;
                accounts.put(active, saved);
            }
        }
    }

    JSONObject current() {
        JSONObject account = accounts.optJSONObject(active);
        return account == null ? new JSONObject() : account;
    }

    String activeId() { return active; }

    JSONArray labels() throws JSONException {
        JSONArray labels = new JSONArray();
        for (Iterator<String> ids = accounts.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject account = accounts.getJSONObject(id);
            labels.put(new JSONObject().put("id", id).put("name", account.optString("label")));
        }
        return labels;
    }

    void save(String expected, boolean adding, JSONObject account) throws JSONException {
        if (!active.equals(expected)) throw new IllegalArgumentException("Active account changed. Open settings again.");
        if (adding || active.isEmpty()) {
            if (accounts.length() >= 20) throw new IllegalArgumentException("You can save up to 20 accounts.");
            active = UUID.randomUUID().toString();
        }
        accounts.put(active, account);
    }

    void select(String id) {
        if (accounts.optJSONObject(id) == null) throw new IllegalArgumentException("Saved account was not found.");
        active = id;
    }

    boolean remember(String expected) {
        if (!active.equals(expected) || active.isEmpty() || active.equals(lastConnected)) return false;
        lastConnected = active;
        return true;
    }

    boolean restore() {
        if (active.equals(lastConnected) || accounts.optJSONObject(lastConnected) == null) return false;
        active = lastConnected;
        return true;
    }

    void remove(String expected) {
        if (!active.equals(expected)) throw new IllegalArgumentException("Active account changed. Open settings again.");
        if (active.equals(lastConnected)) lastConnected = "";
        accounts.remove(active);
        active = accounts.keys().hasNext() ? accounts.keys().next() : "";
    }

    JSONObject encoded() throws JSONException {
        return new JSONObject().put("profiles", accounts).put("active", active).put("lastConnected", lastConnected);
    }
}
