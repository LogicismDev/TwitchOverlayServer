package me.Logicism.TwitchOverlayServer;

public class OverlayConfig {

    private String webSocketIP;

    private int webSocketPort;

    private String httpServerIP;

    private int httpServerPort;

    private String clientID;

    private String clientSecret;

    private String callbackURI;

    private String overlayBaseURL;

    private String webhookSecret;

    public String getWebSocketIP() {
        return webSocketIP;
    }

    public int getWebSocketPort() {
        return webSocketPort;
    }

    public String getHttpServerIP() {
        return httpServerIP;
    }

    public int getHttpServerPort() {
        return httpServerPort;
    }

    public String getClientID() {
        return clientID;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getCallbackURI() {
        return callbackURI;
    }

    public String getOverlayBaseURL() {
        return overlayBaseURL;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}
