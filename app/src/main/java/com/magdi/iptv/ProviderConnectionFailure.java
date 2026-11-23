package com.magdi.iptv;

import java.io.IOException;
import org.json.JSONObject;

/** Fixed diagnostic classifications. No response text or addresses leave this boundary. */
final class ProviderConnectionFailure extends IOException {
    final int code;
    final boolean retryProtocol;

    private ProviderConnectionFailure(int code, String message, boolean retryProtocol) {
        super(message);
        this.code = code;
        this.retryProtocol = retryProtocol;
    }

    static ProviderConnectionFailure from(JSONObject failure) {
        int status = failure.optInt("status", 0);
        switch (failure.optString("kind")) {
            case "challenge": return new ProviderConnectionFailure(6, "The IPTV provider returned a protection challenge. Ask the provider for an app-compatible address.", false);
            case "redirect": return new ProviderConnectionFailure(9, "The IPTV server returned an invalid redirect. Check the address with the provider.", false);
            case "response":
                if (status == 7) return new ProviderConnectionFailure(7, "The IPTV server returned a web page. Check that the address is the Xtream server, including its port.", false);
                return new ProviderConnectionFailure(8, "The IPTV server returned data in an unexpected format. Check the source type with the provider.", false);
            case "http":
                if (status >= 100 && status <= 599) return new ProviderConnectionFailure(status, "The IPTV server returned HTTP " + status + ".", false);
                break;
            case "tls": return new ProviderConnectionFailure(10, "A secure connection to the IPTV server could not be established.", failure.optBoolean("canTryHttp"));
        }
        return new ProviderConnectionFailure(5, "Could not reach the IPTV server. Check the address and network.", failure.optBoolean("canTryHttp"));
    }
}
