package server.ws;

import java.util.Vector;

public abstract class ResourceManagerAbstract implements ResourceManager {
    public boolean crash(String rm) {
        printNotImplemented("CRASH");
        return false;
    }

    public boolean prepare(int id) {
        printNotImplemented("PREPARE");
        return false;
    }

    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers, String location, boolean car, boolean room) {
        printNotImplemented("RESERVE ITINERARY");
        return false;
    }

    public boolean selfDestruct() {
        System.exit(-1);
        return true;
    }

    public int start() {
        printNotImplemented("START");
        return -1;
    }
    
    public void signalCrash(int id, int whence) {
    	printNotImplemented("SIGNAL CRASH");
    }
    
    public boolean queryTxnResult(int id, int whence) {
    	printNotImplemented("QUERY TXN RESULT");
    	return false;
    }

    public boolean isStillActive(int id) {
        printNotImplemented("HEARTBEAT (IS STILL ACTIVE)");
        return false;
    }

    private void printNotImplemented(String method) {
        System.out.println("Error: " + method + " not implemented.");
    }
}
