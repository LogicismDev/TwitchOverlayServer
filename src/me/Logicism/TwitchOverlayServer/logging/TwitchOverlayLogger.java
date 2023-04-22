package me.Logicism.TwitchOverlayServer.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwitchOverlayLogger {

    private static Logger logger;

    public TwitchOverlayLogger(Class<?> clazz) {
        logger = LoggerFactory.getLogger(clazz);

        LoggingOutputStream.redirectSysOutAndSysErr(logger);
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public void warn(String msg) {
        logger.warn(msg);
    }

    public void error(String msg) {
        logger.error(msg);
    }
}
