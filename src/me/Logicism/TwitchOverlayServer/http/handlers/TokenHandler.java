package me.Logicism.TwitchOverlayServer.http.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import me.Logicism.TwitchOverlayServer.TwitchOverlayServer;
import me.Logicism.TwitchOverlayServer.http.BrowserClient;
import me.Logicism.TwitchOverlayServer.http.BrowserData;
import me.Logicism.TwitchOverlayServer.http.oauth.TokenHandle;
import me.Logicism.TwitchOverlayServer.util.FileUtils;
import me.Logicism.TwitchOverlayServer.util.HTTPUtils;
import me.Logicism.TwitchOverlayServer.util.TextUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TokenHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            TwitchOverlayServer.INSTANCE.getLogger().info(exchange.getRequestMethod() + " - " +
                    (exchange.getRequestHeaders().containsKey("X-Forwarded-For") ?
                            exchange.getRequestHeaders().get("X-Forwarded-For").get(0) :
                            exchange.getRemoteAddress().getAddress()) + " - " +
                    (exchange.getRequestHeaders().containsKey("User-Agent") ?
                            exchange.getRequestHeaders().get("User-Agent").get(0) : "Unknown User-Agent")
                    + " - /webhook");
            Map<String, String> queryMap = TextUtils.queryToMap(exchange.getRequestURI().getQuery());

            if (queryMap.containsKey("error")) {
                HTTPUtils.throwError(exchange, queryMap.get("error_description").replace("+", " "));
            } else {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", exchange.getRequestHeaders().get("User-Agent").get(0));

                BrowserData bd = BrowserClient.executePOSTRequest(new URL("https://id.twitch.tv/oauth2/token"),
                        "client_id=" + TwitchOverlayServer.INSTANCE.getConfig().getClientID() +
                                "&client_secret=" + TwitchOverlayServer.INSTANCE.getConfig().getClientSecret() +
                                "&code=" + queryMap.get("code") + "&grant_type=authorization_code" +
                                "&redirect_uri=" + TwitchOverlayServer.INSTANCE.getConfig().getCallbackURI(), headers);

                if (bd.getResponseCode() == 200) {
                    JSONObject tokenObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

                    headers.put("Authorization", "Bearer " + tokenObject.getString("access_token"));
                    headers.put("Client-Id", TwitchOverlayServer.INSTANCE.getConfig().getClientID());

                    bd = BrowserClient.executeGETRequest(new URL("https://api.twitch.tv/helix/users"), headers);

                    JSONObject userObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()))
                            .getJSONArray("data").getJSONObject(0);

                    if (TwitchOverlayServer.INSTANCE.containsUserTokenHandle(userObject.getString("id"),
                            tokenObject.getJSONArray("scope").getString(0))) {
                        TokenHandle handle = TwitchOverlayServer.INSTANCE.getTokenHandle(userObject
                                .getString("id"), tokenObject.getJSONArray("scope")
                                .getString(0));

                        handle.setAccessToken(tokenObject.getString("access_token"));
                        handle.setRefreshToken(tokenObject.getString("refresh_token"));
                        handle.setTimeToExpire(tokenObject.getLong("expires_in"));
                        handle.setExpired(false);

                        if (TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().containsKey(handle)) {
                            TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().get(handle)
                                    .cancel(true);

                            TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().replace(handle, TwitchOverlayServer
                                    .INSTANCE.getWebsocketServer().getExecutor().schedule(new Runnable() {
                                        @Override
                                        public void run() {
                                            handle.setExpired(true);
                                        }
                                    }, handle.getTimeToExpire(), TimeUnit.SECONDS));
                        } else {
                            TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().put(handle, TwitchOverlayServer
                                    .INSTANCE.getWebsocketServer().getExecutor().schedule(new Runnable() {
                                        @Override
                                        public void run() {
                                            handle.setExpired(true);
                                        }
                                    }, handle.getTimeToExpire(), TimeUnit.SECONDS));
                        }
                    } else {
                        TokenHandle handle = new TokenHandle(userObject.getString("id"),
                                tokenObject.getString("access_token"), tokenObject.getString("refresh_token"),
                                Collections.singletonList(tokenObject.getJSONArray("scope").getString(0)),
                                tokenObject.getLong("expires_in"));

                        TwitchOverlayServer.INSTANCE.getTokenHandleList().add(handle);
                        TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().put(handle, TwitchOverlayServer
                                .INSTANCE.getWebsocketServer().getExecutor().schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        handle.setExpired(true);
                                    }
                                }, handle.getTimeToExpire(), TimeUnit.SECONDS));


                        File tokenHandleDatabase = new File("tokenHandles.dat");

                        try {
                            TwitchOverlayServer.INSTANCE.writeTokenHandleList(tokenHandleDatabase);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    String overlaySubBaseURL = "";

                    headers.put("Content-Type", "application/json");

                    if (TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().isExpired()) {
                        TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle()
                                .refreshToken("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                        "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");


                        if (!TwitchOverlayServer.INSTANCE.getAppAccessTokenExpiration().isDone()) {
                            TwitchOverlayServer.INSTANCE.getAppAccessTokenExpiration()
                                    .cancel(true);

                            TwitchOverlayServer.INSTANCE.setAppAccessTokenExpiration(
                                    TwitchOverlayServer.INSTANCE.getWebsocketServer().getExecutor()
                                            .schedule(new Runnable() {
                                                @Override
                                                public void run() {
                                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle()
                                                            .setExpired(true);
                                                }
                                            }, TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle()
                                                    .getTimeToExpire(), TimeUnit.SECONDS));
                        }
                    }
                    headers.replace("Authorization", "Bearer " + TwitchOverlayServer.INSTANCE
                            .getAppAccessTokenHandle().getAccessToken());

                    if (tokenObject.getJSONArray("scope").getString(0).equals("channel:read:predictions")) {
                        overlaySubBaseURL = "/predictions";

                        bd = BrowserClient.executeGETRequest(new URL(
                                "https://api.twitch.tv/helix/eventsub/subscriptions"), headers);

                        JSONObject eventSubObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

                        if (!hasEventHandler(eventSubObject, "channel.prediction.begin",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.prediction.begin", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.prediction.progress",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.prediction.progress", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.prediction.lock",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.prediction.lock", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.prediction.end",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.prediction.end", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }
                    } else if (tokenObject.getJSONArray("scope").getString(0).equals("channel:read:polls")) {
                        overlaySubBaseURL = "/polls";

                        bd = BrowserClient.executeGETRequest(new URL(
                                "https://api.twitch.tv/helix/eventsub/subscriptions"), headers);

                        JSONObject eventSubObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

                        if (!hasEventHandler(eventSubObject, "channel.poll.begin",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.poll.begin", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.poll.progress",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.poll.progress", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.poll.end",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.poll.end", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }
                    } else if (tokenObject.getJSONArray("scope").getString(0).equals("bits:read")) {
                        overlaySubBaseURL = "/alerts";

                        bd = BrowserClient.executeGETRequest(new URL(
                                "https://api.twitch.tv/helix/eventsub/subscriptions"), headers);

                        JSONObject eventSubObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

                        if (!hasEventHandler(eventSubObject, "channel.follow",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.follow", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 2);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.subscribe",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.subscribe", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.subscription.gift",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.subscription.gift", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.subscription.message",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.subscription.message", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.cheer",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.cheer", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }

                        if (!hasEventHandler(eventSubObject, "channel.raid",
                                userObject.getString("id"), exchange.getRequestHeaders().get("User-Agent").get(0), overlaySubBaseURL, headers)) {
                            registerEventSub("channel.raid", userObject.getString("id"),
                                    exchange.getRequestHeaders().get("User-Agent").get(0),
                                    TwitchOverlayServer.INSTANCE.getAppAccessTokenHandle().getAccessToken(),
                                    overlaySubBaseURL, 1);
                        }
                    }

                    String response = FileUtils.fileToString(HTTPUtils.getFile("/callback.html"))
                            .replace("{username}", userObject.getString("display_name"))
                            .replace("{url}", TwitchOverlayServer.INSTANCE.getConfig()
                                    .getOverlayBaseURL() + "/overlay" + overlaySubBaseURL + "?id=" +
                                    userObject.getString("id") + (overlaySubBaseURL.equals("/alerts") ? "<br><br>" +
                                    "If you wish to use ko-fi.com integration, use the following Webhook URL:" +
                                    "<br><br>" + TwitchOverlayServer.INSTANCE.getConfig().getOverlayBaseURL() +
                                    "/webhook/ko-fi/" + userObject.getString("id") : ""));

                    HTTPUtils.throwSuccessHTML(exchange, response);
                } else {
                    HTTPUtils.throwError(exchange, "Cannot grab access token! Please reauthenticate to Twitch again!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasEventHandler(JSONObject eventSubObject, String subType, String userId, String userAgent, String overlaySubBaseURL, Map<String, String> headers) throws IOException {
        for (int i = 0; i < eventSubObject.getJSONArray("data").length(); i++) {
            JSONObject eventSubObj = eventSubObject.getJSONArray("data").getJSONObject(i);
            if (eventSubObj.getString("type").equals(subType)) {
                if (eventSubObj.getJSONObject("transport").getString("method").equals("webhook") &&
                        eventSubObj.getJSONObject("transport").getString("callback")
                                .equals(TwitchOverlayServer.INSTANCE.getConfig().getOverlayBaseURL()
                                        + "/webhook" + overlaySubBaseURL + "/" + userId) &&
                        eventSubObj.getString("status").equals("enabled")) {
                    return true;
                } else if (eventSubObj.getJSONObject("transport").getString("method").equals("webhook") &&
                        eventSubObj.getJSONObject("transport").getString("callback")
                                .equals(TwitchOverlayServer.INSTANCE.getConfig().getOverlayBaseURL()
                                        + "/webhook" + overlaySubBaseURL + "/" + userId) &&
                        !eventSubObj.getString("status").equals("enabled")) {
                    deleteEventSub(eventSubObject.getString("id"), userAgent, overlaySubBaseURL);

                    return false;
                }
            }
        }

        if (eventSubObject.getJSONObject("pagination").has("cursor")) {
            BrowserData bd = BrowserClient.executeGETRequest(new URL(
                    "https://api.twitch.tv/helix/eventsub/subscriptions?after=" + eventSubObject.getJSONObject("pagination").getString("cursor")), headers);

            eventSubObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

            return hasEventHandler(eventSubObject, subType, userId, userAgent, overlaySubBaseURL, headers);
        }

        return false;
    }

    public void registerEventSub(String subType, String userId, String userAgent, String accessToken
            , String overlaySubBaseURL, int version) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Client-Id", TwitchOverlayServer.INSTANCE.getConfig().getClientID());
        headers.put("Content-Type", "application/json");

        JSONObject payload = new JSONObject().put("type", subType).put("version", String.valueOf(version)).put("transport", new JSONObject().put("method", "webhook")
                .put("callback", TwitchOverlayServer.INSTANCE.getConfig().getOverlayBaseURL() + "/webhook"
                        + overlaySubBaseURL + "/" + userId).put("secret",
                        TwitchOverlayServer.INSTANCE.getConfig().getWebhookSecret()));

        JSONObject condition = new JSONObject();
        if (subType.equals("channel.follow")) {
            condition = condition.put("broadcaster_user_id", userId).put("moderator_user_id", userId);
        } else if (subType.equals("channel.raid")) {
            condition = condition.put("to_broadcaster_user_id", userId);
        } else {
            condition = condition.put("broadcaster_user_id", userId);
        }

        payload = payload.put("condition", condition);

        BrowserClient.executePOSTRequest(new URL("https://api.twitch.tv/helix/eventsub/subscriptions"),
                payload.toString(), headers);
    }

    public void deleteEventSub(String eventId, String userAgent, String accessToken) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Client-Id", TwitchOverlayServer.INSTANCE.getConfig().getClientID());
        headers.put("Content-Type", "application/json");

        BrowserClient.executeDELETERequest(new URL("https://api.twitch.tv/helix/eventsub/subscriptions?id=" + eventId), headers);
    }

}
