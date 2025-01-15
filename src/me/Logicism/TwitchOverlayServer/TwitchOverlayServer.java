package me.Logicism.TwitchOverlayServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sun.net.httpserver.HttpServer;
import me.Logicism.TwitchOverlayServer.http.handlers.OverlayHandler;
import me.Logicism.TwitchOverlayServer.http.handlers.StaticPageHandler;
import me.Logicism.TwitchOverlayServer.http.handlers.TokenHandler;
import me.Logicism.TwitchOverlayServer.http.handlers.WebhookHandler;
import me.Logicism.TwitchOverlayServer.http.oauth.TokenHandle;
import me.Logicism.TwitchOverlayServer.logging.TwitchOverlayLogger;
import me.Logicism.TwitchOverlayServer.ws.OverlayWebsocketServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TwitchOverlayServer {

    private static OverlayConfig config;
    private static TwitchOverlayLogger logger;
    private static TokenHandle appAccessTokenHandle;
    private static List<TokenHandle> tokenHandleList;

    private static Map<TokenHandle, ScheduledFuture> tokenHandleExpirations = new HashMap<>();
    private static ScheduledFuture appAccessTokenExpiration;

    private static OverlayWebsocketServer websocketServer;
    private static HttpServer server;

    public static TwitchOverlayServer INSTANCE;

    public static void main(String[] args) {
        INSTANCE = new TwitchOverlayServer();

        try {
            websocketServer = new OverlayWebsocketServer();
            websocketServer.start();

            server = HttpServer.create(new InetSocketAddress(config.getHttpServerIP(), config.getHttpServerPort()),
                    0);

            server.createContext("/", new StaticPageHandler());
            server.createContext("/callback", new TokenHandler());
            server.createContext("/webhook", new WebhookHandler());
            server.createContext("/overlay", new OverlayHandler());

            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
            server.setExecutor(executor);

            server.start();

            System.out.println("HTTP is listening on " + config.getHttpServerIP() + ":" + config.getHttpServerPort());

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        websocketServer.stop();
                        server.stop(0);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public TwitchOverlayServer() {
        File tokenHandleDatabase = new File("tokenHandles.dat");

        if (!tokenHandleDatabase.exists()) {
            tokenHandleList = new ArrayList<>();

            try {
                writeTokenHandleList(tokenHandleDatabase);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                readTokenHandleList(tokenHandleDatabase);

                for (TokenHandle tokenHandle : tokenHandleList) {
                    tokenHandle.setExpired(true);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            config = mapper.readValue(new File("config.yml"), OverlayConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger = new TwitchOverlayLogger(TwitchOverlayServer.class);
    }

    public List<TokenHandle> getTokenHandleList() {
        return tokenHandleList;
    }

    public boolean containsUserTokenHandle(String userId, String scope) {
        return getTokenHandle(userId, scope) != null;
    }

    public TokenHandle getTokenHandle(String userId, String scope) {
        for (TokenHandle handle : tokenHandleList) {
            if (handle.getUserId().equals(userId) && handle.getScopes().contains(scope)) {
                return handle;
            }
        }

        return null;
    }

    public void refreshHandle(String userAgent, TokenHandle handle) throws IOException {
        handle.refreshToken(userAgent);

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
    }

    public void readTokenHandleList(File file) throws IOException, ClassNotFoundException {
        ObjectInputStream databaseInputStream = new ObjectInputStream(new FileInputStream(file));

        tokenHandleList = (List<TokenHandle>) databaseInputStream.readObject();
    }

    public void writeTokenHandleList(File file) throws IOException {
        ObjectOutputStream databaseOutputStream = new ObjectOutputStream(new FileOutputStream(file));

        databaseOutputStream.writeObject(tokenHandleList);
    }

    public OverlayConfig getConfig() {
        return config;
    }

    public OverlayWebsocketServer getWebsocketServer() {
        return websocketServer;
    }

    public TokenHandle getAppAccessTokenHandle() {
        return appAccessTokenHandle;
    }

    public void setAppAccessTokenHandle(TokenHandle appAccessTokenHandle) {
        TwitchOverlayServer.appAccessTokenHandle = appAccessTokenHandle;
    }

    public Map<TokenHandle, ScheduledFuture> getTokenHandleExpirations() {
        return tokenHandleExpirations;
    }

    public ScheduledFuture getAppAccessTokenExpiration() {
        return appAccessTokenExpiration;
    }

    public void setAppAccessTokenExpiration(ScheduledFuture appAccessTokenExpiration) {
        TwitchOverlayServer.appAccessTokenExpiration = appAccessTokenExpiration;
    }

    public TwitchOverlayLogger getLogger() {
        return logger;
    }
}
