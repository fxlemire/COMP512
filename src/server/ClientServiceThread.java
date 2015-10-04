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

            String res = processRequest(request);

            DataOutputStream outputStream = new DataOutputStream(_clientSocket.getOutputStream());
            outputStream.writeUTF(res);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String processRequest(String requestString) {
        Socket rmSocket;
        DataOutputStream outputStream;
        DataInputStream inputStream;
        String res = "";

        try {
            rmSocket = new Socket(_rmIp, _rmPort);
            outputStream = new DataOutputStream(rmSocket.getOutputStream());

            String[] request = requestString.split(",");
            Command command = Command.getCommandForInterfaceCall(request[0]);

            String requestForRm = formatRequest(command, request);
            outputStream.writeUTF(requestForRm);
            outputStream.flush();

            inputStream = new DataInputStream(rmSocket.getInputStream());

            res = inputStream.readUTF();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return res;
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
