package me.Logicism.TwitchOverlayServer.http;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class BrowserData {

    private String url;
    private Map<String, List<String>> headers;
    private int resCode;
    private long resLength;
    private InputStream response;
    private String contentType;

    public BrowserData(String url, Map<String, List<String>> headers, int resCode, long resLength, InputStream response,
                       String contentType) {
        this.url = url;
        this.headers = headers;
        this.resCode = resCode;
        this.resLength = resLength;
        this.response = response;
        this.contentType = contentType;
    }

    public String getURL() {
        return url;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public int getResponseCode() {
        return resCode;
    }

    public long getResponseLength() {
        return resLength;
    }

    public InputStream getResponse() {
        return response;
    }

    public String getContentType() {
        return contentType;
    }
}
