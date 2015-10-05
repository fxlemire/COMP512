package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class RMManager {
    private RMHashtable _m_itemHT = new RMHashtable();

    public static void main(String[] args) {
    	RMManager manager = new RMManager();
        int port = Integer.parseInt(args[0]);
        Trace.info("Starting RM on port " + port);
        manager.start(port);
    }

    private void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    ResourceManagerImpl.RunNonBlocking(_m_itemHT, clientSocket);
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
