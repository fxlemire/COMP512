package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Middleware {
    /*
     *  Middleware assumes it will receive as arguments his server port and the four RM IPs and RM Ports in the following format:
     *  0: server port
     *  1: rm1 ip
     *  2: rm1 port
     *  3: rm2 ip
     *  4: rm2 port
     *  and so on.
     *  RM 1: flight
     *  RM 2: car
     *  RM 3: room
     *  RM 4: customer
     */

    public static void main(String[] args) {
        Middleware middleware = new Middleware();

        int port = Integer.parseInt(args[0]);
        String[] rmIps = middleware.getRmIps(args);
        int[] rmPorts = middleware.getRmPorts(args);

        middleware.start(port, rmIps, rmPorts);
    }

    private void start(int port, String[] rmIp, int[] rmPort) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    ClientServiceThread.RunNonBlocking(clientSocket, rmIp, rmPort);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] getRmIps(String[] args) {
        String[] rmIps = new String[4];
        for (int i = 0; i < rmIps.length; ++i) {
            rmIps[i] = args[2 * i + 1];
        }

        return rmIps;
    }

    private int[] getRmPorts(String[] args) {
        int[] rmPorts = new int[4];
        for (int i = 0; i < rmPorts.length; ++i) {
            rmPorts[i] = Integer.parseInt(args[2 * i + 2]);
        }

        return rmPorts;
    }
}
