// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package Util;

// A simple wrapper around System.out.println, allows us to disable some of
// the verbose output from RM, TM, and WC if we want.

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.*;
import java.util.*;

public class Trace {

    public static void info(String msg) {
        java.lang.System.out.println(getThreadID() + " INFO: " + msg);
    }

    public static void warn(String msg) {
        java.lang.System.out.println(getThreadID() + " WARN: " + msg);
    }

    public static void error(String msg) {
        java.lang.System.err.println(getThreadID() + " ERROR: " + msg);
    }

    public static boolean persist(String filename, List<Object> objects, boolean append) {
        boolean isPersisted = true;

        try {
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

                file.createNewFile();
            }

            FileOutputStream fos = new FileOutputStream(file, append);
            if (!append) {
                fos.getChannel().truncate(0);
            }

            ObjectOutputStream next_oos = new ObjectOutputStream(fos);
            Iterator it = objects.iterator();
            while (it.hasNext()) {
                next_oos.writeObject(it.next());
            }
            next_oos.close();
        } catch (IOException e) {
            Trace.error(e.toString());
            isPersisted = false;
        }

        return isPersisted;
    }

    private static String getThreadID() {
        return Thread.currentThread().getName();
    }
}