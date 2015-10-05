package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientServiceThread implements Runnable {
    private int[] _rmPorts;
    private Socket _clientSocket;
    private String[] _rmIps;
    private Thread _runner;

    private ClientServiceThread(Socket clientSocket, String[] rmIps, int[] rmPorts) {
        _clientSocket = clientSocket;
        _rmIps = rmIps;
        _rmPorts = rmPorts;
        _runner = new Thread(this);
    }

    public static ClientServiceThread RunNonBlocking(Socket clientSocket, String[] rmIps, int[] rmPorts) {
        ClientServiceThread clientService = new ClientServiceThread(clientSocket, rmIps, rmPorts);
        clientService.getRunner().start();
        return clientService;
    }

    public Thread getRunner() { return _runner; }

    public void run() {
        try {
            DataInputStream inputStream = new DataInputStream(_clientSocket.getInputStream());
            String request = inputStream.readUTF();

            RMResult res = processIfComposite(request);
            if (res == null) {
            	res = processIfCIDRequired(request);
            	if (res == null) {
            		res = processAtomicRequest(request);
            	}
            }

            ObjectOutputStream outputStream = new ObjectOutputStream(_clientSocket.getOutputStream());
            outputStream.writeObject(res);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private RMResult processAtomicCustomerRequest(String requestString) {
    	// Process the given request by first checking that the given customer
    	// exists, and if yes creating it at the relevant RM as necessary.
    	
    	Integer cid = getCustomerId(requestString);
    	RMResult existenceCheck = processAtomicRequest("checkCustomerExists,1," + cid);
    	if (existenceCheck.IsError())
    		return existenceCheck;
    	
    	if (!existenceCheck.AsBool()) {
    		RMResult failure = new RMResult(new Exception("Customer " + cid + " does not exist."));
    		return failure;
    	}
    	
    	// Create the customer on the given RM (no side-effects if customer already exists.
    	int rmId = getRMIndexForRequest(requestString);
    	processAtomicRequest("newcustomerid,1," + cid, _rmIps[rmId], _rmPorts[rmId]);
    	
    	// Actually execute the request
    	return processAtomicRequest(requestString, _rmIps[rmId], _rmPorts[rmId]);
    	
    }
    
    private RMResult processAtomicRequest(String requestString, String givenRM, Integer givenPort) {
    	// Process the given request. If the request does not target a specific RM
    	// (basically when it's newcustomerid), it is expected that the givenRM and givenPort
    	// parameters point to the RM on which to execute the request. If the parameters are not
    	// given, we attempt to find the proper RM on which to execute the command.
    	
    	Socket rmSocket;
        DataOutputStream outputStream;
        ObjectInputStream inputStream;
        RMResult res = null;

        try {
        	if (givenRM == null || givenPort == null) {
        		int rmId = getRMIndexForRequest(requestString);
        		givenRM = _rmIps[rmId];
        		givenPort = _rmPorts[rmId];
        	}
        	
            rmSocket = new Socket(givenRM, givenPort);
            outputStream = new DataOutputStream(rmSocket.getOutputStream());

            String[] request = requestString.split(",");
            Command command = Command.getCommandForInterfaceCall(request[0]);

            String requestForRm = formatRequest(command, request);
            outputStream.writeUTF(requestForRm);
            outputStream.flush();

            inputStream = new ObjectInputStream(rmSocket.getInputStream());

            res = (RMResult) inputStream.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return res;
    }
    
    private RMResult processAtomicRequest(String requestString) {
        return processAtomicRequest(requestString, null, null);
    }

    private RMResult processIfCIDRequired(String request) {
    	// Try to execute a request that requires to check the
    	// customer RM for customer existence first. Return null
    	// if the request does not require the customer ID.
    	String[] parts = request.split(",");
    	switch(parts[0]) {
    	case Command.INTERFACE_RESERVE_CAR:
    	case Command.INTERFACE_RESERVE_FLIGHT:
    	case Command.INTERFACE_RESERVE_ROOM:
    		return processAtomicCustomerRequest(request);
    	default:
    		return null;
    	}
    	
    }
    
    private int getRMIndexForRequest(String requestString) {
    	String[] parts = requestString.split(",");
    	switch(parts[0]) {
    	case Command.INTERFACE_ADD_FLIGHT:
    	case Command.INTERFACE_DELETE_FLIGHT:
    	case Command.INTERFACE_QUERY_FLIGHT:
    	case Command.INTERFACE_QUERY_FLIGHT_PRICE:
    	case Command.INTERFACE_RESERVE_FLIGHT:
    		return 0;
    	case Command.INTERFACE_ADD_CARS:
    	case Command.INTERFACE_DELETE_CARS:
    	case Command.INTERFACE_QUERY_CARS:
    	case Command.INTERFACE_QUERY_CARS_PRICE:
    	case Command.INTERFACE_RESERVE_CAR:
    		return 1;
    	case Command.INTERFACE_ADD_ROOMS:
    	case Command.INTERFACE_DELETE_ROOMS:
    	case Command.INTERFACE_QUERY_ROOMS:
    	case Command.INTERFACE_QUERY_ROOMS_PRICE:
    	case Command.INTERFACE_RESERVE_ROOM:
    		return 2;
    	case Command.INTERFACE_NEW_CUSTOMER:
    		return 3;
        default:
        	return -1;
    	}
    }
    
    private int getCustomerId(String request) {
    	// Obtain the customer ID from this request
    	String[] parts = request.split(",");
    	switch (parts[0]) {
    	case Command.INTERFACE_RESERVE_CAR:
    	case Command.INTERFACE_RESERVE_FLIGHT:
    	case Command.INTERFACE_RESERVE_ROOM:
    		return Integer.parseInt(parts[2].trim());
    	default:
    		return -1;
    	}
    }
    
    private RMResult processIfComposite(String requestString) {
    	// Execute a composite method for this request if necessary.
    	// Returns a result, or null if the request corresponds to 
    	// no composite action.
    	String[] parts = requestString.split(",");
    	switch (parts[0]) {
    	case Command.INTERFACE_DELETE_CUSTOMER:
    		//TODO Delete customer from all RMs
    		break;
    	case Command.INTERFACE_RESERVE_ITINERARY:
    		//TODO Book an itinerary...
    		break;
    	case Command.INTERFACE_QUERY_CUSTOMER_INFO:
    		//TODO Query customer over all RMs
    		break;
    	default:
    		return null;
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
