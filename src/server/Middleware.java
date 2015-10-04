package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Middleware {
    public static void main(String[] args) {
        Middleware middleware = new Middleware();
        int port = Integer.parseInt(args[0]);
        String rmIp = args[1];
        int rmPort = Integer.parseInt(args[2]);
        middleware.start(port, rmIp, rmPort);
    }

    private void start(int port, String rmIp, int rmPort) {
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
}
