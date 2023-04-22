package me.Logicism.TwitchOverlayServer.ws;

import org.java_websocket.WebSocket;

public class WebSocketHandle {

    private String session_id;
    private String user_id;
    private WebSocket webSocket;

    public WebSocketHandle(String session_id, String user_id, WebSocket webSocket) {
        this.session_id = session_id;
        this.user_id = user_id;
        this.webSocket = webSocket;
    }

    public String getSessionId() {
        return session_id;
    }

    public String getUserId() {
        return user_id;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }
}
