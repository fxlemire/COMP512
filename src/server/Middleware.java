package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Middleware {
    public static void main(String[] args) {
        Middleware middleware = new Middleware();
        int port = Integer.parseInt(args[0]);
        middleware.start(port);
    }

    private void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    ClientServiceThread.RunNonBlocking(clientSocket);
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
