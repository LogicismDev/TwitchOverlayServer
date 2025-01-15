package me.Logicism.TwitchOverlayServer.http;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class BrowserClient {

    public static BrowserData executeGETRequest(URL url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
        c.setConnectTimeout(30000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(false);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                c.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        int resCode = c.getResponseCode();
        int resLength = c.getContentLength();

        return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength,
                c.getInputStream() != null ? c.getInputStream() : c.getErrorStream(), c.getContentType());
    }

    public static BrowserData executePOSTRequest(URL url, String data, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
        c.setConnectTimeout(30000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(false);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                c.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        c.setDoInput(true);
        c.setDoOutput(true);

        DataOutputStream dos = new DataOutputStream(c.getOutputStream());
        dos.writeBytes(data);
        dos.flush();
        dos.close();

        int resCode = c.getResponseCode();
        int resLength = c.getContentLength();

        return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength,
                c.getInputStream() != null ? c.getInputStream() : c.getErrorStream(), c.getContentType());
    }

    public static BrowserData executeDELETERequest(URL url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
        c.setConnectTimeout(30000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("DELETE");

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                c.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        int resCode = c.getResponseCode();
        int resLength = c.getContentLength();

        return new BrowserData(c.getURL().toString(), c.getHeaderFields(), resCode, resLength,
                c.getInputStream() != null ? c.getInputStream() : c.getErrorStream(), c.getContentType());
    }

    public static String requestToString(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String s;
        StringBuilder sb = new StringBuilder();
        while ((s = br.readLine()) != null) {
            sb.append(s);
        }

        is.close();

        return sb.toString();
    }

}
