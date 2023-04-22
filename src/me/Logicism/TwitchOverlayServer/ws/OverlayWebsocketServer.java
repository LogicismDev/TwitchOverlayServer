package me.Logicism.TwitchOverlayServer.ws;

import me.Logicism.TwitchOverlayServer.TwitchOverlayServer;
import me.Logicism.TwitchOverlayServer.http.BrowserClient;
import me.Logicism.TwitchOverlayServer.http.BrowserData;
import me.Logicism.TwitchOverlayServer.http.oauth.TokenHandle;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
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

                    if (handle.isExpired()) {
                        try {
                            handle.refreshToken(messageObject.getString("user_agent"));

                            if (TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().containsKey(handle)) {
                                TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().get(handle)
                                        .cancel(true);

                                TwitchOverlayServer.INSTANCE.getTokenHandleExpirations().replace(handle,
                                        TwitchOverlayServer.INSTANCE.getWebsocketServer().getExecutor()
                                                .schedule(new Runnable() {
                                            @Override
                                            public void run() {
                                                handle.setExpired(true);
                                            }
                                        }, handle.getTimeToExpire(), TimeUnit.SECONDS));
                            } else {
                                TwitchOverlayServer.INSTANCE.getTokenHandleExpirations()
                                        .put(handle, TwitchOverlayServer.INSTANCE.getWebsocketServer().getExecutor()
                                                .schedule(new Runnable() {
                                            @Override
                                            public void run() {
                                                handle.setExpired(true);
                                            }
                                        }, handle.getTimeToExpire(), TimeUnit.SECONDS));
                            }
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
            }
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
