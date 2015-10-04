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
        } catch (IOException e) {
            e.printStackTrace();
        }

        //TODO: send to proper RM via socket, receive response and send back response to client
        String[] request = requestString.split(",");

        switch(Command.getCommandForInterfaceCall(request[0])) {
            case NEW_FLIGHT:

                break;
            case NEW_CAR:
                break;
            case NEW_ROOM:
                break;
            case NEW_CUSTOMER:
                break;
            case NEW_CUSTOMER_ID:
                break;
            case DELETE_FLIGHT:
                break;
            case DELETE_CAR:
                break;
            case DELETE_ROOM:
                break;
            case DELETE_CUSTOMER:
                break;
            case QUERY_FLIGHT:
                break;
            case QUERY_CAR:
                break;
            case QUERY_ROOM:
                break;
            case QUERY_CUSTOMER:
                break;
            case QUERY_FLIGHT_PRICE:
                break;
            case QUERY_CAR_PRICE:
                break;
            case QUERY_ROOM_PRICE:
                break;
            case RESERVE_FLIGHT:
                break;
            case RESERVE_CAR:
                break;
            case RESERVE_ROOM:
                break;
            case ITINERARY:
                break;
            case UNKNOWN_COMMAND:
                break;
            default:
                //even more unknown...
                break;
        }
    }
}
