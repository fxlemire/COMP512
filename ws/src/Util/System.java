package Util;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

    public static File createFile(String filename) {
        File file = new File(filename);

        if (!file.exists()) {
            if (filename.contains("/")) {
                ArrayList<String> splitFileName = new ArrayList<>(Arrays.asList(filename.split("/")));
                splitFileName.remove(splitFileName.size() - 1);
                String dir;

                if (splitFileName.size() > 1) {
                    StringJoiner sj = new StringJoiner("/");
                    splitFileName.forEach(sj::add);
                    dir = sj.toString();
                } else {
                    dir = splitFileName.get(0);
                }

                File directory = new File(dir);
                directory.mkdirs();
            }

            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        return file;
    }
}
