package me.Logicism.TwitchOverlayServer.http.oauth;

import me.Logicism.TwitchOverlayServer.TwitchOverlayServer;
import me.Logicism.TwitchOverlayServer.http.BrowserClient;
import me.Logicism.TwitchOverlayServer.http.BrowserData;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TokenHandle implements Serializable {

    private String userId;
    private String accessToken;
    private String refreshToken;
    private List<String> scopes;
    private long timeToExpire;
    private boolean expired;

    public TokenHandle(String userId, String accessToken, String refreshToken, List<String> scopes, long timeToExpire) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.scopes = scopes;
        this.timeToExpire = timeToExpire;
        this.expired = false;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public long getTimeToExpire() {
        return timeToExpire;
    }

    public void setTimeToExpire(long timeToExpire) {
        this.timeToExpire = timeToExpire;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public void refreshToken(String userAgent) throws IOException {
        Map<String, String> headers = new HashMap<>();

        headers.put("User-Agent", userAgent);

        BrowserData bd;
        if (refreshToken != null) {
            bd = BrowserClient.executePOSTRequest(new URL("https://id.twitch.tv/oauth2/token"),
                    "client_id=" + TwitchOverlayServer.INSTANCE.getConfig().getClientID() +
                            "&client_secret=" + TwitchOverlayServer.INSTANCE.getConfig().getClientSecret() +
                            "&refresh_token=" + refreshToken +
                            "&grant_type=refresh_token", headers);

            JSONObject tokenObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

            setAccessToken(tokenObject.getString("access_token"));
            setRefreshToken(tokenObject.getString("refresh_token"));
            setTimeToExpire(tokenObject.getLong("expires_in"));
            setExpired(false);
        } else {
            bd = BrowserClient.executePOSTRequest(new URL("https://id.twitch.tv/oauth2/token"),
                    "client_id=" + TwitchOverlayServer.INSTANCE.getConfig().getClientID() +
                            "&client_secret=" + TwitchOverlayServer.INSTANCE.getConfig().getClientSecret() +
                            "&grant_type=client_credentials", headers);

            JSONObject tokenObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

            setAccessToken(tokenObject.getString("access_token"));
            setTimeToExpire(tokenObject.getLong("expires_in"));
            setExpired(false);
        }
    }
}
