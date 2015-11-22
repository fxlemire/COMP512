package Util;

import java.util.Timer;
import java.util.TimerTask;

public class System {
    public static void shutInstance(int status) {
        Timer end = new Timer();
        end.schedule(new TimerTask() {
            @Override
            public void run() {
                java.lang.System.exit(status);
            }
        }, 1000);
    }
}
