package me.Logicism.TwitchOverlayServer.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class HTTPUtils {

    public static boolean containsFile(String query) {
        query = "pages" + query;

        return new File(query).exists();
    }

    public static boolean containsPage(String query) {
        query = "pages" + query;

        if (!(query.split("\\.").length > 0)) {
            return new File(query + ".html").exists() || new File(query + ".htm").exists() ||
                    new File(query + ".mhtml").exists() || new File(query + ".js").exists() ||
                    new File(query + ".css").exists() || new File(query + ".php").exists();
        }

        return false;
    }

    public static File getFile(String query) {
        query = "pages" + query;

        return new File(query);
    }

    public static void throwSuccessChallenge(HttpExchange exchange, String challenge) throws IOException {
        if (challenge != null) {
            OutputStream os = exchange.getResponseBody();

            exchange.sendResponseHeaders(200, challenge.length());
            os.write(challenge.getBytes());
            os.flush();
            os.close();
        } else {
            throwError(exchange, null);
        }
    }

    public static void throwSuccessFile(HttpExchange exchange, File file) throws IOException {
        if (file != null && file.exists()) {
            OutputStream os = exchange.getResponseBody();

            exchange.sendResponseHeaders(200, file.length());
            Files.copy(file.toPath(), os);
            os.flush();
            os.close();
        } else {
            throw404(exchange);
        }

    }

    public static void throwSuccessPage(HttpExchange exchange, File file) throws IOException {
        if (file != null && file.exists()) {
            OutputStream os = exchange.getResponseBody();
            String response = FileUtils.fileToString(file);

            exchange.sendResponseHeaders(200, response.length());
            os.write(response.getBytes());
            os.flush();
            os.close();
        } else {
            throw404(exchange);
        }
    }

    public static void throwSuccessHTML(HttpExchange exchange, String html) throws IOException {
        OutputStream os = exchange.getResponseBody();

        exchange.sendResponseHeaders(200, html.length());
        os.write(html.getBytes());
        os.flush();
        os.close();
    }

    public static void throwSuccess(HttpExchange exchange) throws IOException {
        OutputStream os = exchange.getResponseBody();

        exchange.sendResponseHeaders(204, -1);
        os.flush();
        os.close();
    }

    public static void throwError(HttpExchange exchange, String error) throws IOException {
        OutputStream os = exchange.getResponseBody();
        String response = FileUtils.fileToString(getFile("/callbackError.html"))
                .replace("{error}", error != null ? error : "Invalid Request!");

        exchange.sendResponseHeaders(405, response.length());
        os.write(response.getBytes());
        os.flush();
        os.close();
    }

    public static void throw404(HttpExchange exchange) throws IOException {
        OutputStream os = exchange.getResponseBody();
        String response = FileUtils.fileToString(getFile("/404.html"));

        exchange.sendResponseHeaders(404, response.length());
        os.write(response.getBytes());
        os.flush();
        os.close();
    }

}
