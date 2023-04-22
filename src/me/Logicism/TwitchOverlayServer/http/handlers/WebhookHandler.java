package me.Logicism.TwitchOverlayServer.http.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import me.Logicism.TwitchOverlayServer.TwitchOverlayServer;
import me.Logicism.TwitchOverlayServer.http.BrowserClient;
import me.Logicism.TwitchOverlayServer.util.HTTPUtils;
import me.Logicism.TwitchOverlayServer.util.TextUtils;
import me.Logicism.TwitchOverlayServer.ws.WebSocketHandle;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class WebhookHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        TwitchOverlayServer.INSTANCE.getLogger().info(exchange.getRequestMethod() + " - " +
                (exchange.getRequestHeaders().containsKey("X-Forwarded-For") ?
                        exchange.getRequestHeaders().get("X-Forwarded-For").get(0) :
                        exchange.getRemoteAddress().getAddress()) + " - " +
                exchange.getRequestHeaders().get("User-Agent").get(0) + " - /callback");
        if (exchange.getRequestMethod().equals("POST")) {
            if (exchange.getRequestHeaders().containsKey("Twitch-Eventsub-Message-Type".toLowerCase())) {
                String body = BrowserClient.requestToString(exchange.getRequestBody());

                if (exchange.getRequestHeaders().get("Twitch-Eventsub-Message-Type").get(0).equals("notification")) {
                    if (exchange.getRequestHeaders().containsKey("Twitch-Eventsub-Message-Id".toLowerCase())
                            && exchange.getRequestHeaders().containsKey("Twitch-Eventsub-Message-Timestamp"
                            .toLowerCase()) && exchange.getRequestHeaders().containsKey(
                                    "Twitch-Eventsub-Message-Signature".toLowerCase())) {
                        String user_id = exchange.getRequestURI().toString().substring("/webhook/predictions/".length());
                        String hmac = exchange.getRequestHeaders().get("Twitch-Eventsub-Message-Id".toLowerCase())
                                .get(0) + exchange.getRequestHeaders().get("Twitch-Eventsub-Message-Timestamp"
                                .toLowerCase()).get(0) + body;

                        try {
                            String hmacHex = "sha256=" + TextUtils.generateHMAC("HmacSHA256", hmac,
                                    TwitchOverlayServer.INSTANCE.getConfig().getWebhookSecret());
                            String twitchHmac = exchange.getRequestHeaders()
                                    .get("Twitch-Eventsub-Message-Signature".toLowerCase()).get(0);

                            if (hmacHex.equals(twitchHmac)) {
                                for (WebSocketHandle handle : TwitchOverlayServer.INSTANCE.getWebsocketServer()
                                        .getWebSocketHandles()) {
                                    if (handle.getUserId().equals(user_id)) {
                                        handle.getWebSocket().send(body);
                                    }
                                }

                                HTTPUtils.throwSuccess(exchange);
                            } else {
                                HTTPUtils.throwError(exchange, "Invalid Request!");
                            }
                        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                            HTTPUtils.throwError(exchange, "Invalid Request!");
                        }
                    } else {
                        HTTPUtils.throwError(exchange, "Invalid Request!");
                    }
                } else if (exchange.getRequestHeaders().get("Twitch-Eventsub-Message-Type").get(0)
                        .equals("webhook_callback_verification")) {
                    JSONObject bodyObject = new JSONObject(body);

                    HTTPUtils.throwSuccessChallenge(exchange, bodyObject.getString("challenge"));
                } else if (exchange.getRequestHeaders().get("Twitch-Eventsub-Message-Type").get(0)
                        .equals("revocation")) {
                    JSONObject bodyObject = new JSONObject(body);
                    if (bodyObject.getJSONObject("subscription").getString("type")
                            .startsWith("channel.prediction")) {
                        String user_id = bodyObject.getJSONObject("subscription").getJSONObject("condition")
                                .getString("broadcaster_user_id");

                        TwitchOverlayServer.INSTANCE.getTokenHandleList()
                                .remove(TwitchOverlayServer.INSTANCE
                                        .getTokenHandle(user_id, "channel:read:predictions"));
                    }

                    HTTPUtils.throwSuccess(exchange);
                }
            } else {
                HTTPUtils.throwError(exchange, "Invalid Request!");
            }
        } else {
            HTTPUtils.throwError(exchange, "Invalid Request!");
        }
    }

}
