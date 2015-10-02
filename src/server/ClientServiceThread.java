package server;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientServiceThread implements Runnable {
    private Socket _clientSocket;
    private Thread _runner;

    private ClientServiceThread(Socket clientSocket) {
        _clientSocket = clientSocket;
        _runner = new Thread(this);
    }

    public static ClientServiceThread RunNonBlocking(Socket clientSocket) {
        ClientServiceThread clientService = new ClientServiceThread(clientSocket);
        clientService.getRunner().start();
        return clientService;
    }

    public Thread getRunner() { return _runner; }

    public void run() {
        try {
            DataInputStream inputStream = new DataInputStream(_clientSocket.getInputStream());
            String request = inputStream.readUTF();
            processRequest(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processRequest(String request) {
        //TODO: send to proper RM via socket, receive response and send back response to client
    }
}
