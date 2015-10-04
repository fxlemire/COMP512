package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientServiceThread implements Runnable {
    private int _rmPort;
    private Socket _clientSocket;
    private String _rmIp;
    private Thread _runner;

    private ClientServiceThread(Socket clientSocket, String rmIp, int rmPort) {
        _clientSocket = clientSocket;
        _rmIp = rmIp;
        _rmPort = rmPort;
        _runner = new Thread(this);
    }

    public static ClientServiceThread RunNonBlocking(Socket clientSocket, String rmIp, int rmPort) {
        ClientServiceThread clientService = new ClientServiceThread(clientSocket, rmIp, rmPort);
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

    private void processRequest(String requestString) {
        Socket rmSocket;
        DataOutputStream outputStream;

        try {
            rmSocket = new Socket(_rmIp, _rmPort);
            outputStream = new DataOutputStream(rmSocket.getOutputStream());

            String[] request = requestString.split(",");
            Command command = Command.getCommandForInterfaceCall(request[0]);

            String requestForRm = formatRequest(command, request);
            outputStream.writeUTF(requestForRm);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatRequest(Command command, String[] request) {
        String requestForRm = command.getMethod();
        short arguments = command.getArguments();

        for (short i = 1; i < arguments; ++i) {
            requestForRm += "," + request[i];
        }

        return requestForRm;
    }
}
