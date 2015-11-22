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

    public int start() {
        printNotImplemented("START");
        return -1;
    }

    private void printNotImplemented(String method) {
        System.out.println("Error: " + method + " not implemented.");
    }
}
