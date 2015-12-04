// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package Util;

// A simple wrapper around System.out.println, allows us to disable some of
// the verbose output from RM, TM, and WC if we want.

import java.io.*;
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

    public static boolean persist(String filename, String sentence, boolean append) {
        PrintStream ps = new PrintStream(getFileOutputStream(filename, append));
        ps.println(sentence);
        ps.close();
        return true;
    }

    public static boolean persist(String filename, List<Object> objects, boolean append) {
        boolean isPersisted = true;

        try {
            ObjectOutputStream oos = getOutputStream(filename, append);
            Iterator it = objects.iterator();
            while (it.hasNext()) {
                oos.writeObject(it.next());
            }
            oos.close();
        } catch (IOException e) {
            Trace.error(e.toString());
            isPersisted = false;
        }

        return isPersisted;
    }

    private static ObjectOutputStream getOutputStream(String filename, boolean append) {
        ObjectOutputStream oos = null;

        try {
            oos = new ObjectOutputStream(getFileOutputStream(filename, append));
        } catch (IOException e) {
            Trace.error(e.toString());
        }

        return oos;
    }

    private static FileOutputStream getFileOutputStream(String filename, boolean append) {
        File file = System.createFile(filename);
        FileOutputStream fos = null;

        if (file != null) {
            try {
                fos = new FileOutputStream(file, append);
                if (!append) {
                    fos.getChannel().truncate(0);
                }
            } catch (IOException e) {
                Trace.error(e.toString());
            }
        }

        return fos;
    }

    private static String getThreadID() {
        return Thread.currentThread().getName();
    }
}