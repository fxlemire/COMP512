package server;

public class Command {
    private short _arguments;
    private INTERFACE _command;
    private String _method;

    public short getArguments() { return _arguments; }
    public void setArguments(short arguments) { _arguments = arguments; }

    public INTERFACE getCommand() { return _command; }
    public void setCommand(INTERFACE command) { _command = command; }

    public String getMethod() { return _method; }
    public void setMethod(String method) { _method = method; }

    public enum INTERFACE {
        ADD_FLIGHT,
        ADD_CARS,
        ADD_ROOMS,
        NEW_CUSTOMER,
        NEW_CUSTOMER_ID,
        DELETE_FLIGHT,
        DELETE_CARS,
        DELETE_ROOMS,
        DELETE_CUSTOMER,
        QUERY_FLIGHT,
        QUERY_CARS,
        QUERY_ROOMS,
        QUERY_CUSTOMER_INFO,
        QUERY_FLIGHT_PRICE,
        QUERY_CARS_PRICE,
        QUERY_ROOMS_PRICE,
        RESERVE_FLIGHT,
        RESERVE_CAR,
        RESERVE_ROOM,
        RESERVE_ITINERARY,
        UNKNOWN_COMMAND
    }

    public final static String SERVER_ADD_FLIGHT = "addFlight";
    public final static String SERVER_ADD_CARS = "addCars";
    public final static String SERVER_ADD_ROOMS = "addRooms";
    public final static String SERVER_NEW_CUSTOMER = "newCustomer";
    public final static String SERVER_NEW_CUSTOMER_ID = "newCustomerId";
    public final static String SERVER_DELETE_FLIGHT  = "deleteFlight";
    public final static String SERVER_DELETE_CARS = "deleteCars";
    public final static String SERVER_DELETE_ROOMS = "deleteRooms";
    public final static String SERVER_DELETE_CUSTOMER = "deleteCustomer";
    public final static String SERVER_QUERY_FLIGHT  = "queryFlight";
    public final static String SERVER_QUERY_CARS = "queryCars";
    public final static String SERVER_QUERY_ROOMS = "queryRooms";
    public final static String SERVER_QUERY_CUSTOMER_INFO = "queryCustomerInfo";
    public final static String SERVER_QUERY_FLIGHT_PRICE = "queryFlightPrice";
    public final static String SERVER_QUERY_CARS_PRICE = "queryCarsPrice";
    public final static String SERVER_QUERY_ROOMS_PRICE = "queryRoomsPrice";
    public final static String SERVER_RESERVE_FLIGHT = "reserveFlight";
    public final static String SERVER_RESERVE_CAR = "reserveCar";
    public final static String SERVER_RESERVE_ROOM = "reserveRoom";
    public final static String SERVER_RESERVE_ITINERARY = "reserveItinerary";
    public final static String SERVER_UNKNOWN_COMMAND = "unknownCommand";
    public final static String SERVER_CHECK_CUSTOMER_EXISTS = "checkCustomerExists";

    public final static String INTERFACE_ADD_FLIGHT = "newflight";
    public final static String INTERFACE_ADD_CARS = "newcar";
    public final static String INTERFACE_ADD_ROOMS = "newroom";
    public final static String INTERFACE_NEW_CUSTOMER = "newcustomer";
    public final static String INTERFACE_NEW_CUSTOMER_ID = "newcustomerid";
    public final static String INTERFACE_DELETE_FLIGHT  = "deleteflight";
    public final static String INTERFACE_DELETE_CARS = "deletecar";
    public final static String INTERFACE_DELETE_ROOMS = "deleteroom";
    public final static String INTERFACE_DELETE_CUSTOMER = "deletecustomer";
    public final static String INTERFACE_QUERY_FLIGHT  = "queryflight";
    public final static String INTERFACE_QUERY_CARS = "querycar";
    public final static String INTERFACE_QUERY_ROOMS = "queryroom";
    public final static String INTERFACE_QUERY_CUSTOMER_INFO = "querycustomer";
    public final static String INTERFACE_QUERY_FLIGHT_PRICE = "queryflightprice";
    public final static String INTERFACE_QUERY_CARS_PRICE = "querycarprice";
    public final static String INTERFACE_QUERY_ROOMS_PRICE = "queryroomprice";
    public final static String INTERFACE_RESERVE_FLIGHT = "reserveflight";
    public final static String INTERFACE_RESERVE_CAR = "reservecar";
    public final static String INTERFACE_RESERVE_ROOM = "reserveroom";
    public final static String INTERFACE_RESERVE_ITINERARY = "itinerary";

    public final static short ARGUMENTS_ADD_FLIGHT = 4;
    public final static short ARGUMENTS_ADD_CARS = 4;
    public final static short ARGUMENTS_ADD_ROOMS = 4;
    public final static short ARGUMENTS_NEW_CUSTOMER = 1;
    public final static short ARGUMENTS_NEW_CUSTOMER_ID = 2;
    public final static short ARGUMENTS_DELETE_FLIGHT  = 2;
    public final static short ARGUMENTS_DELETE_CARS = 2;
    public final static short ARGUMENTS_DELETE_ROOMS = 2;
    public final static short ARGUMENTS_DELETE_CUSTOMER = 2;
    public final static short ARGUMENTS_QUERY_FLIGHT  = 2;
    public final static short ARGUMENTS_QUERY_CARS = 2;
    public final static short ARGUMENTS_QUERY_ROOMS = 2;
    public final static short ARGUMENTS_QUERY_CUSTOMER_INFO = 2;
    public final static short ARGUMENTS_QUERY_FLIGHT_PRICE = 2;
    public final static short ARGUMENTS_QUERY_CARS_PRICE = 2;
    public final static short ARGUMENTS_QUERY_ROOMS_PRICE = 2;
    public final static short ARGUMENTS_RESERVE_FLIGHT = 3;
    public final static short ARGUMENTS_RESERVE_CAR = 3;
    public final static short ARGUMENTS_RESERVE_ROOM = 3;
    public final static short ARGUMENTS_RESERVE_ITINERARY = 6;
    public final static short ARGUMENTS_UNKNOWN_COMMAND = 0;

    public static Command getCommandForInterfaceCall(String commandString) {
        Command command = new Command();

        switch (commandString) {
            case INTERFACE_ADD_FLIGHT:
                command.setArguments(ARGUMENTS_ADD_FLIGHT);
                command.setCommand(INTERFACE.ADD_FLIGHT);
                command.setMethod(SERVER_ADD_FLIGHT);
                break;
            case INTERFACE_ADD_CARS:
                command.setArguments(ARGUMENTS_ADD_CARS);
                command.setCommand(INTERFACE.ADD_CARS);
                command.setMethod(SERVER_ADD_CARS);
                break;
            case INTERFACE_ADD_ROOMS:
                command.setArguments(ARGUMENTS_ADD_ROOMS);
                command.setCommand(INTERFACE.ADD_ROOMS);
                command.setMethod(SERVER_ADD_ROOMS);
                break;
            case INTERFACE_NEW_CUSTOMER:
                command.setArguments(ARGUMENTS_NEW_CUSTOMER);
                command.setCommand(INTERFACE.NEW_CUSTOMER);
                command.setMethod(SERVER_NEW_CUSTOMER);
                break;
            case INTERFACE_NEW_CUSTOMER_ID:
                command.setArguments(ARGUMENTS_NEW_CUSTOMER_ID);
                command.setCommand(INTERFACE.NEW_CUSTOMER_ID);
                command.setMethod(SERVER_NEW_CUSTOMER_ID);
                break;
            case INTERFACE_DELETE_FLIGHT:
                command.setArguments(ARGUMENTS_DELETE_FLIGHT);
                command.setCommand(INTERFACE.DELETE_FLIGHT);
                command.setMethod(SERVER_DELETE_FLIGHT);
                break;
            case INTERFACE_DELETE_CARS:
                command.setArguments(ARGUMENTS_DELETE_CARS);
                command.setCommand(INTERFACE.DELETE_CARS);
                command.setMethod(SERVER_DELETE_CARS);
                break;
            case INTERFACE_DELETE_ROOMS:
                command.setArguments(ARGUMENTS_DELETE_ROOMS);
                command.setCommand(INTERFACE.DELETE_ROOMS);
                command.setMethod(SERVER_DELETE_ROOMS);
                break;
            case INTERFACE_DELETE_CUSTOMER:
                command.setArguments(ARGUMENTS_DELETE_CUSTOMER);
                command.setCommand(INTERFACE.DELETE_CUSTOMER);
                command.setMethod(SERVER_DELETE_CUSTOMER);
                break;
            case INTERFACE_QUERY_FLIGHT:
                command.setArguments(ARGUMENTS_QUERY_FLIGHT);
                command.setCommand(INTERFACE.QUERY_FLIGHT);
                command.setMethod(SERVER_QUERY_FLIGHT);
                break;
            case INTERFACE_QUERY_CARS:
                command.setArguments(ARGUMENTS_QUERY_CARS);
                command.setCommand(INTERFACE.QUERY_CARS);
                command.setMethod(SERVER_QUERY_CARS);
                break;
            case INTERFACE_QUERY_ROOMS:
                command.setArguments(ARGUMENTS_QUERY_ROOMS);
                command.setCommand(INTERFACE.QUERY_ROOMS);
                command.setMethod(SERVER_QUERY_ROOMS);
                break;
            case INTERFACE_QUERY_CUSTOMER_INFO:
                command.setArguments(ARGUMENTS_QUERY_CUSTOMER_INFO);
                command.setCommand(INTERFACE.QUERY_CUSTOMER_INFO);
                command.setMethod(SERVER_QUERY_CUSTOMER_INFO);
                break;
            case INTERFACE_QUERY_FLIGHT_PRICE:
                command.setArguments(ARGUMENTS_QUERY_FLIGHT_PRICE);
                command.setCommand(INTERFACE.QUERY_FLIGHT_PRICE);
                command.setMethod(SERVER_QUERY_FLIGHT_PRICE);
                break;
            case INTERFACE_QUERY_CARS_PRICE:
                command.setArguments(ARGUMENTS_QUERY_CARS_PRICE);
                command.setCommand(INTERFACE.QUERY_CARS_PRICE);
                command.setMethod(SERVER_QUERY_CARS_PRICE);
                break;
            case INTERFACE_QUERY_ROOMS_PRICE:
                command.setArguments(ARGUMENTS_QUERY_ROOMS_PRICE);
                command.setCommand(INTERFACE.QUERY_ROOMS_PRICE);
                command.setMethod(SERVER_QUERY_ROOMS_PRICE);
                break;
            case INTERFACE_RESERVE_FLIGHT:
                command.setArguments(ARGUMENTS_RESERVE_FLIGHT);
                command.setCommand(INTERFACE.RESERVE_FLIGHT);
                command.setMethod(SERVER_RESERVE_FLIGHT);
                break;
            case INTERFACE_RESERVE_CAR:
                command.setArguments(ARGUMENTS_RESERVE_CAR);
                command.setCommand(INTERFACE.RESERVE_CAR);
                command.setMethod(SERVER_RESERVE_CAR);
                break;
            case INTERFACE_RESERVE_ROOM:
                command.setArguments(ARGUMENTS_RESERVE_ROOM);
                command.setCommand(INTERFACE.RESERVE_ROOM);
                command.setMethod(SERVER_RESERVE_ROOM);
                break;
            case INTERFACE_RESERVE_ITINERARY:
                command.setArguments(ARGUMENTS_RESERVE_ITINERARY);
                command.setCommand(INTERFACE.RESERVE_ITINERARY);
                command.setMethod(SERVER_RESERVE_ITINERARY);
                break;
            default:
                command.setArguments(ARGUMENTS_UNKNOWN_COMMAND);
                command.setCommand(INTERFACE.UNKNOWN_COMMAND);
                command.setMethod(SERVER_UNKNOWN_COMMAND);
                break;
        }

        return command;
    }
}
