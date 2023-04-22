package me.Logicism.TwitchOverlayServer.util;

import java.io.*;

public class FileUtils {

    public static String fileToString(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        StringBuilder sb = new StringBuilder();
        String s;

        while ((s = br.readLine()) != null) {
            sb.append(s);
        }

        return sb.toString();
    }

}
