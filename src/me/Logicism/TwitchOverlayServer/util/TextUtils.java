package me.Logicism.TwitchOverlayServer.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class TextUtils {

    public static Map<String, String> queryToMap(String query) {
        Map<String, String> map = new HashMap<>();

        for (String entry : query.split("&")) {
            String[] entryQ = entry.split("=");

            map.put(entryQ[0], entryQ[1]);
        }

        return map;
    }

    public static String generateHMAC(String algorithm, String data, String key) throws NoSuchAlgorithmException
            , InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(secretKeySpec);

        return bytesToHex(mac.doFinal(data.getBytes()));
    }

    public static String bytesToHex(byte[] array) {
        StringBuilder sb = new StringBuilder(array.length * 2);

        for (byte b : array) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

}
