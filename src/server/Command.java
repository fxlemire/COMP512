package server;

public class Command {
    private Command _command;
    private String _method;

    public enum INTERFACE {
        NEW_FLIGHT,
        NEW_CAR,
        NEW_ROOM,
        NEW_CUSTOMER,
        NEW_CUSTOMER_ID,
        DELETE_FLIGHT,
        DELETE_CAR,
        DELETE_ROOM,
        DELETE_CUSTOMER,
        QUERY_FLIGHT,
        QUERY_CAR,
        QUERY_ROOM,
        QUERY_CUSTOMER,
        QUERY_FLIGHT_PRICE,
        QUERY_CAR_PRICE,
        QUERY_ROOM_PRICE,
        RESERVE_FLIGHT,
        RESERVE_CAR,
        RESERVE_ROOM,
        ITINERARY,
        UNKNOWN_COMMAND
    }

    public final static String ADD_FLIGHT = "addFlight";
    public final static String ADD_CARS = "addCars";
    public final static String ADD_ROOMS = "addRooms";
    public final static String NEW_CUSTOMER = "newCustomer";
    public final static String NEW_CUSTOMER_ID = "newCustomerId";
    public final static String DELETE_FLIGHT  = "deleteFlight";
    public final static String DELETE_CARS = "deleteCars";
    public final static String DELETE_ROOMS = "deleteRooms";
    public final static String DELETE_CUSTOMER = "deleteCustomer";
    public final static String QUERY_FLIGHT  = "queryFlight";
    public final static String QUERY_CARS = "queryCars";
    public final static String QUERY_ROOMS = "queryRooms";
    public final static String QUERY_CUSTOMER_INFO = "queryCustomerInfo";
    public final static String QUERY_FLIGHT_PRICE = "queryFlightPrice";
    public final static String QUERY_CARS_PRICE = "queryCarsPrice";
    public final static String QUERY_ROOMS_PRICE = "queryRoomsPrice";
    public final static String RESERVE_FLIGHT = "reserveFlight";
    public final static String RESERVE_CAR = "reserveCar";
    public final static String RESERVE_ROOM = "reserveRoom";
    public final static String RESERVE_ITINERARY = "reserveItinerary";

    public static Command getCommandForInterfaceCall(String commandString) {
        Command command = new Command();
        INTERFACE call = getCall(commandString);

        switch (commandString) {
            case ADD_FLIGHT:
                command = COMMAND.NEW_FLIGHT;
                break;
            case "newcar":
                command = COMMAND.NEW_CAR;
                break;
            case "newroom":
                command = COMMAND.NEW_ROOM;
                break;
            case "newcustomer":
                command = COMMAND.NEW_CUSTOMER;
                break;
            case "newcustomerid":
                command = COMMAND.NEW_CUSTOMER_ID;
                break;
            case "deleteflight":
                command = COMMAND.DELETE_FLIGHT;
                break;
            case "deletecar":
                command = COMMAND.DELETE_CAR;
                break;
            case "deleteroom":
                command = COMMAND.DELETE_ROOM;
                break;
            case "deletecustomer":
                command = COMMAND.DELETE_CUSTOMER;
                break;
            case "queryflight":
                command = COMMAND.QUERY_FLIGHT;
                break;
            case "querycar":
                command = COMMAND.QUERY_CAR;
                break;
            case "queryroom":
                command = COMMAND.QUERY_ROOM;
                break;
            case "querycustomer":
                command = COMMAND.QUERY_CUSTOMER;
                break;
            case "queryflightprice":
                command = COMMAND.QUERY_FLIGHT_PRICE;
                break;
            case "querycarprice":
                command = COMMAND.QUERY_CAR_PRICE;
                break;
            case "queryroomprice":
                command = COMMAND.QUERY_ROOM_PRICE;
                break;
            case "reserveflight":
                command = COMMAND.RESERVE_FLIGHT;
                break;
            case "reservecar":
                command = COMMAND.RESERVE_CAR;
                break;
            case "reserveroom":
                command = COMMAND.RESERVE_ROOM;
                break;
            case "itinerary":
                command = COMMAND.ITINERARY;
                break;
            default:
                command = COMMAND.UNKNOWN_COMMAND;
                break;
        }

        return command;
    }

    private static INTERFACE getCall(String command) {
        INTERFACE call;
        call = INTERFACE.DELETE_CAR;
        return call;
    }
}
