package me.Logicism.TwitchOverlayServer.http.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import me.Logicism.TwitchOverlayServer.TwitchOverlayServer;
import me.Logicism.TwitchOverlayServer.util.FileUtils;
import me.Logicism.TwitchOverlayServer.util.HTTPUtils;
import me.Logicism.TwitchOverlayServer.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class OverlayHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        TwitchOverlayServer.INSTANCE.getLogger().info(exchange.getRequestMethod() + " - " +
                (exchange.getRequestHeaders().containsKey("X-Forwarded-For") ?
                        exchange.getRequestHeaders().get("X-Forwarded-For").get(0) :
                        exchange.getRemoteAddress().getAddress()) + " - " +
                exchange.getRequestHeaders().get("User-Agent").get(0) + " - " + exchange.getRequestURI().toString());

        if (exchange.getRequestURI().toString().startsWith("/overlay/predictions")) {
            Map<String, String> queryMap = TextUtils.queryToMap(exchange.getRequestURI().getQuery());
            if (queryMap.containsKey("id")) {
                if (TwitchOverlayServer.INSTANCE.containsUserTokenHandle(queryMap.get("id"),
                        "channel:read:predictions")) {
                    if (HTTPUtils.containsFile("/overlay/predictions-premium/" + queryMap.get("id") + ".html")) {
                        HTTPUtils.throwSuccessPage(exchange, HTTPUtils.getFile("/overlay/predictions-premium/" + queryMap.get("id") + ".html"));
                    } else {
                        String response = FileUtils.fileToString(HTTPUtils.getFile("/overlay/predictions.html"))
                                .replace("{user_id}", queryMap.get("id"));

                        HTTPUtils.throwSuccessHTML(exchange, response);
                    }
                } else {
                    HTTPUtils.throwError(exchange, "User is not authenticated! Please reauthenticate and refresh!");
                }
            } else {
                HTTPUtils.throwError(exchange, "URL does not have id query!");
            }
        } else {
            HTTPUtils.throwError(exchange, "Cannot find that overlay!");
        }
    }
}
