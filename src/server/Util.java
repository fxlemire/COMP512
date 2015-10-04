package server;

public class Util {
    public static Command getCommandForInterfaceCall(String commandString) {
        Command command;
        commandString = commandString.toLowerCase();

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
}
