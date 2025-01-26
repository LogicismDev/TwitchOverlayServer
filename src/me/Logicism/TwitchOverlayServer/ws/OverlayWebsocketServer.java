package me.Logicism.TwitchOverlayServer.ws;

import me.Logicism.TwitchOverlayServer.TwitchOverlayServer;
import me.Logicism.TwitchOverlayServer.http.BrowserClient;
import me.Logicism.TwitchOverlayServer.http.BrowserData;
import me.Logicism.TwitchOverlayServer.http.oauth.TokenHandle;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class OverlayWebsocketServer extends WebSocketServer {

    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(Runtime.getRuntime()
            .availableProcessors());
    private List<WebSocketHandle> webSocketHandles = new CopyOnWriteArrayList<>();
    private Map<String, ScheduledFuture> webSocketTimeouts = new ConcurrentHashMap<>();
    private Map<String, ScheduledFuture> webSocketPings = new ConcurrentHashMap<>();

    private Map<String, ScheduledFuture> mkwiiScheduledFutures = new HashMap<>();

    public OverlayWebsocketServer() {
        super(new InetSocketAddress(TwitchOverlayServer.INSTANCE.getConfig().getWebSocketIP(), TwitchOverlayServer
                .INSTANCE.getConfig().getWebSocketPort()));
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        String session_id = UUID.randomUUID().toString();
        JSONObject welcomeObject = new JSONObject().put("session_id", session_id).put("type", "welcome");

        webSocket.send(welcomeObject.toString());

        webSocketTimeouts.put(session_id, executor.schedule(new Runnable() {
            @Override
            public void run() {
                webSocket.close();

                webSocketTimeouts.remove(session_id);
            }
        }, 10, TimeUnit.SECONDS));
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        for (WebSocketHandle handle : webSocketHandles) {
            if (handle.getWebSocket().isClosing()) {
                System.out.println("Disconnected WebSocket client");
                webSocketHandles.remove(handle);
                webSocketPings.remove(handle.getSessionId());

                if (mkwiiScheduledFutures.containsKey(handle.getUserId())) {
                    mkwiiScheduledFutures.get(handle.getUserId()).cancel(true);
                    mkwiiScheduledFutures.remove(handle.getUserId());
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        try {
            JSONObject messageObject = new JSONObject(s);

            if (messageObject.getString("type").equals("welcome")) {
                if (!webSocketTimeouts.containsKey(messageObject.getString("session_id")) &&
                        !TwitchOverlayServer.INSTANCE.containsUserTokenHandle(messageObject.getString("user_id"),
                                messageObject.getString("scope"))) {
                    webSocket.close();
                    return;
                }

                System.out.println("Connected new WebSocket client");

                if (messageObject.has("scope") && messageObject.getString("scope")
                        .startsWith("channel:read:predictions")) {
                    TokenHandle handle = TwitchOverlayServer.INSTANCE.getTokenHandle(messageObject
                            .getString("user_id"), messageObject.getString("scope"));

                    if (handle != null) {
                        if (handle.isExpired()) {
                            try {
                                TwitchOverlayServer.INSTANCE.refreshHandle(messageObject.getString("user_agent"), handle);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        Map<String, String> headers = new HashMap<>();

                        headers.put("User-Agent", messageObject.getString("user_agent"));
                        headers.put("Authorization", "Bearer " + handle.getAccessToken());
                        headers.put("Client-Id", TwitchOverlayServer.INSTANCE.getConfig().getClientID());

                        try {
                            BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                    "https://api.twitch.tv/helix/predictions?broadcaster_id="
                                            + messageObject.getString("user_id")), headers);

                            JSONObject predictionObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()))
                                    .getJSONArray("data").getJSONObject(0);

                            if (predictionObject.getString("status").equals("ACTIVE")) {
                                predictionObject.put("type", "channel.prediction.begin");
                                webSocket.send(predictionObject.toString());
                            } else if (predictionObject.getString("status").equals("LOCKED")) {
                                predictionObject.put("type", "channel.prediction.lock");
                                webSocket.send(predictionObject.toString());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (messageObject.has("scope") && messageObject.getString("scope")
                        .startsWith("channel:read:polls")) {
                    TokenHandle handle = TwitchOverlayServer.INSTANCE.getTokenHandle(messageObject
                            .getString("user_id"), messageObject.getString("scope"));

                    if (handle != null) {
                        if (handle.isExpired()) {
                            try {
                                TwitchOverlayServer.INSTANCE.refreshHandle(messageObject.getString("user_agent"), handle);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        Map<String, String> headers = new HashMap<>();

                        headers.put("User-Agent", messageObject.getString("user_agent"));
                        headers.put("Authorization", "Bearer " + handle.getAccessToken());
                        headers.put("Client-Id", TwitchOverlayServer.INSTANCE.getConfig().getClientID());

                        try {
                            BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                    "https://api.twitch.tv/helix/polls?broadcaster_id="
                                            + messageObject.getString("user_id")), headers);

                            JSONObject pollObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()))
                                    .getJSONArray("data").getJSONObject(0);

                            if (pollObject.getString("status").equals("ACTIVE")) {
                                pollObject.put("type", "channel.poll.begin");
                                webSocket.send(pollObject.toString());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (messageObject.has("scope") && messageObject.getString("scope")
                        .startsWith("mkwii:read:vr")) {
                    Map<String, String> headers = new HashMap<>();

                    headers.put("User-Agent", messageObject.getString("user_agent"));

                    try {
                        if (messageObject.getString("data_type").equalsIgnoreCase("wiimmfi")) {
                            long vr = 0;

                            BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                    "https://wiimmfi.de/stats/mkw/?m=json"), headers);


                            JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject object = jsonArray.getJSONObject(i);

                                if (object.getString("type").equals("room")) {
                                    JSONArray members = object.getJSONArray("members");

                                    for (int i1 = 0; i1 < members.length(); i1++) {
                                        if (members.getJSONObject(i1).getString("fc").equals(messageObject.getString("user_id")) && members.getJSONObject(i1).getLong("ev") != -1) {
                                            vr = members.getJSONObject(i1).getLong("ev");
                                            break;
                                        }
                                    }
                                }
                            }

                            JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                            webSocket.send(vrObject.toString());

                            boolean found = false;
                            for (WebSocketHandle handle : webSocketHandles) {
                                if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found && !mkwiiScheduledFutures.containsKey(messageObject.getString("user_id"))) {
                                mkwiiScheduledFutures.put(messageObject.getString("user_id"),
                                        executor.scheduleAtFixedRate(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    long vr = 0;

                                                    BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                                            "http://zplwii.xyz/api/groups"), headers);


                                                    JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                                                    for (int i = 0; i < jsonArray.length(); i++) {
                                                        JSONObject object = jsonArray.getJSONObject(i).getJSONObject("players");

                                                        for (String key : object.keySet()) {
                                                            JSONObject playerObject = object.getJSONObject(key);

                                                            if (playerObject.getString("fc").equals(messageObject.getString("user_id"))) {
                                                                if (playerObject.has("ev")) {
                                                                    vr = Long.parseLong(playerObject.getString("ev"));
                                                                }
                                                                break;
                                                            }
                                                        }
                                                    }

                                                    JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                                                    for (WebSocketHandle handle : webSocketHandles) {
                                                        if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                                            handle.getWebSocket().send(vrObject.toString());
                                                        }
                                                    }
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, 10, 10, TimeUnit.SECONDS));
                            }
                        } else if (messageObject.getString("data_type").equalsIgnoreCase("retrorewind")) {
                            long vr = 0;

                            BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                    "http://zplwii.xyz/api/groups"), headers);


                            JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject object = jsonArray.getJSONObject(i).getJSONObject("players");

                                for (String key : object.keySet()) {
                                    JSONObject playerObject = object.getJSONObject(key);

                                    if (playerObject.getString("fc").equals(messageObject.getString("user_id"))) {
                                        if (playerObject.has("ev")) {
                                            vr = Long.parseLong(playerObject.getString("ev"));
                                        }
                                        break;
                                    }
                                }
                            }

                            JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                            webSocket.send(vrObject.toString());

                            boolean found = false;
                            for (WebSocketHandle handle : webSocketHandles) {
                                if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found && !mkwiiScheduledFutures.containsKey(messageObject.getString("user_id"))) {
                                mkwiiScheduledFutures.put(messageObject.getString("user_id"),
                                        executor.scheduleAtFixedRate(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    long vr = 0;

                                                    BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                                            "http://zplwii.xyz/api/groups"), headers);


                                                    JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                                                    for (int i = 0; i < jsonArray.length(); i++) {
                                                        JSONObject object = jsonArray.getJSONObject(i).getJSONObject("players");

                                                        for (String key : object.keySet()) {
                                                            JSONObject playerObject = object.getJSONObject(key);

                                                            if (playerObject.getString("fc").equals(messageObject.getString("user_id"))) {
                                                                if (playerObject.has("ev")) {
                                                                    vr = Long.parseLong(playerObject.getString("ev"));
                                                                }
                                                                break;
                                                            }
                                                        }
                                                    }

                                                    JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                                                    for (WebSocketHandle handle : webSocketHandles) {
                                                        if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                                            handle.getWebSocket().send(vrObject.toString());
                                                        }
                                                    }
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, 10, 10, TimeUnit.SECONDS));
                            }
                        } else if (messageObject.getString("data_type").equalsIgnoreCase("wiimmfi_battle")) {
                            long vr = 0;

                            BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                    "https://wiimmfi.de/stats/mkw/?m=json"), headers);


                            JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject object = jsonArray.getJSONObject(i);

                                if (object.getString("type").equals("room")) {
                                    JSONArray members = object.getJSONArray("members");

                                    for (int i1 = 0; i1 < members.length(); i1++) {
                                        if (members.getJSONObject(i1).getString("fc").equals(messageObject.getString("user_id")) && members.getJSONObject(i1).getLong("eb") != -1) {
                                            vr = members.getJSONObject(i1).getLong("eb");
                                            break;
                                        }
                                    }
                                }
                            }

                            JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                            webSocket.send(vrObject.toString());

                            boolean found = false;
                            for (WebSocketHandle handle : webSocketHandles) {
                                if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found && !mkwiiScheduledFutures.containsKey(messageObject.getString("user_id"))) {
                                mkwiiScheduledFutures.put(messageObject.getString("user_id"),
                                        executor.scheduleAtFixedRate(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    long vr = 0;

                                                    BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                                            "http://zplwii.xyz/api/groups"), headers);


                                                    JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                                                    for (int i = 0; i < jsonArray.length(); i++) {
                                                        JSONObject object = jsonArray.getJSONObject(i).getJSONObject("players");

                                                        for (String key : object.keySet()) {
                                                            JSONObject playerObject = object.getJSONObject(key);

                                                            if (playerObject.getString("fc").equals(messageObject.getString("user_id"))) {
                                                                if (playerObject.has("ev")) {
                                                                    vr = Long.parseLong(playerObject.getString("ev"));
                                                                }
                                                                break;
                                                            }
                                                        }
                                                    }

                                                    JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                                                    for (WebSocketHandle handle : webSocketHandles) {
                                                        if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                                            handle.getWebSocket().send(vrObject.toString());
                                                        }
                                                    }
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, 10, 10, TimeUnit.SECONDS));
                            }
                        } else if (messageObject.getString("data_type").equalsIgnoreCase("retrorewind_battle")) {
                            long vr = 0;

                            BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                    "http://zplwii.xyz/api/groups"), headers);


                            JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject object = jsonArray.getJSONObject(i).getJSONObject("players");

                                for (String key : object.keySet()) {
                                    JSONObject playerObject = object.getJSONObject(key);

                                    if (playerObject.getString("fc").equals(messageObject.getString("user_id"))) {
                                        if (playerObject.has("eb")) {
                                            vr = Long.parseLong(playerObject.getString("eb"));
                                        }
                                        break;
                                    }
                                }
                            }

                            JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                            webSocket.send(vrObject.toString());

                            boolean found = false;
                            for (WebSocketHandle handle : webSocketHandles) {
                                if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found && !mkwiiScheduledFutures.containsKey(messageObject.getString("user_id"))) {
                                mkwiiScheduledFutures.put(messageObject.getString("user_id"),
                                        executor.scheduleAtFixedRate(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    long vr = 0;

                                                    BrowserData bd = BrowserClient.executeGETRequest(new URL(
                                                            "http://zplwii.xyz/api/groups"), headers);


                                                    JSONArray jsonArray = new JSONArray(BrowserClient.requestToString(bd.getResponse()));

                                                    for (int i = 0; i < jsonArray.length(); i++) {
                                                        JSONObject object = jsonArray.getJSONObject(i).getJSONObject("players");

                                                        for (String key : object.keySet()) {
                                                            JSONObject playerObject = object.getJSONObject(key);

                                                            if (playerObject.getString("fc").equals(messageObject.getString("user_id"))) {
                                                                if (playerObject.has("ev")) {
                                                                    vr = Long.parseLong(playerObject.getString("ev"));
                                                                }
                                                                break;
                                                            }
                                                        }
                                                    }

                                                    JSONObject vrObject = new JSONObject().put("type", "vrUpdate").put("vr", vr);

                                                    for (WebSocketHandle handle : webSocketHandles) {
                                                        if (handle.getUserId().equals(messageObject.getString("user_id"))) {
                                                            handle.getWebSocket().send(vrObject.toString());
                                                        }
                                                    }
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, 10, 10, TimeUnit.SECONDS));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            webSocketHandles.add(new WebSocketHandle(messageObject.getString("session_id"),
                    messageObject.getString("user_id"), webSocket));

            webSocketTimeouts.get(messageObject.getString("session_id")).cancel(true);
            webSocketTimeouts.remove(messageObject.getString("session_id"));

            webSocketPings.put(messageObject.getString("session_id"), executor
                    .scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run() {
                            webSocket.sendPing();
                        }
                    }, 20, 20, TimeUnit.SECONDS));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        System.out.println("WebSocket Client has an Error");
        e.printStackTrace();
    }

    @Override
    public void onStart() {
        try {
            Map<String, String> headers = new HashMap<>();

            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");

            BrowserData bd = BrowserClient.executePOSTRequest(new URL("https://id.twitch.tv/oauth2/token"),
                    "client_id=" + TwitchOverlayServer.INSTANCE.getConfig().getClientID() +
                            "&client_secret=" + TwitchOverlayServer.INSTANCE.getConfig().getClientSecret() +
                            "&grant_type=client_credentials", headers);

            JSONObject tokenObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));
            System.out.println("Obtained new App Access Token");

            TokenHandle appAccessTokenHandle = new TokenHandle(null, tokenObject.getString("access_token"),
                    null, null, tokenObject.getInt("expires_in"));

            ScheduledFuture appAccessTokenExpiration = executor.schedule(new Runnable() {
                                                                             @Override
                                                                             public void run() {
                                                                                 appAccessTokenHandle.setExpired(true);
                                                                             }
                                                                         }, appAccessTokenHandle.getTimeToExpire(),
                    TimeUnit.SECONDS);

            TwitchOverlayServer.INSTANCE.setAppAccessTokenHandle(appAccessTokenHandle);
            TwitchOverlayServer.INSTANCE.setAppAccessTokenExpiration(appAccessTokenExpiration);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("WebSocket is listening on " + TwitchOverlayServer.INSTANCE.getConfig().getWebSocketIP()
                + ":" + TwitchOverlayServer.INSTANCE.getConfig().getWebSocketPort());
    }

    public List<WebSocketHandle> getWebSocketHandles() {
        return webSocketHandles;
    }

    public ScheduledThreadPoolExecutor getExecutor() {
        return executor;
    }
}
