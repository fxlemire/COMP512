package client;

import java.util.*;
import java.io.*;


public class Client extends WSClient {

    public Client(String serviceName, String serviceHost, int servicePort) 
    throws Exception {
        super(serviceName, serviceHost, servicePort);
    }

    public static void main(String[] args) {
        try {
        
            if (args.length != 3) {
                System.out.println("Usage: Client <service-name> " 
                        + "<service-host> <service-port>");
                System.exit(-1);
            }
            
            String serviceName = args[0];
            String serviceHost = args[1];
            int servicePort = Integer.parseInt(args[2]);
            
            Client client = new Client(serviceName, serviceHost, servicePort);
            
            client.run();
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    public void run() {
    
        int id;
        int flightNumber;
        int flightPrice;
        int numSeats;
        boolean room;
        boolean car;
        int price;
        int numRooms;
        int numCars;
        String location;

        String command = "";
        Vector arguments = new Vector();

        BufferedReader stdin = 
                new BufferedReader(new InputStreamReader(System.in));
        
        System.out.println("Client Interface");
        System.out.println("Type \"help\" for list of supported commands");

        while (true) {
        
            try {
                //read the next command
                command = stdin.readLine();
            }
            catch (IOException io) {
                System.out.println("Unable to read from standard in");
                System.exit(1);
            }
            //remove heading and trailing white space
            command = command.trim();
            arguments = parse(command);
            
            //decide which of the commands this was
            switch(findChoice((String) arguments.elementAt(0))) {

            case 1: //help section
                if (arguments.size() == 1)   //command was "help"
                    listCommands();
                else if (arguments.size() == 2)  //command was "help <commandname>"
                    listSpecific((String) arguments.elementAt(1));
                else  //wrong use of help command
                    System.out.println("Improper use of help command. Type help or help, <commandname>");
                break;
                
            case 2:  //new flight
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new Flight using id: " + arguments.elementAt(1));
                System.out.println("Flight number: " + arguments.elementAt(2));
                System.out.println("Add Flight Seats: " + arguments.elementAt(3));
                System.out.println("Set Flight Price: " + arguments.elementAt(4));
                
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    numSeats = getInt(arguments.elementAt(3));
                    flightPrice = getInt(arguments.elementAt(4));
                    
                    if (proxy.addFlight(id, flightNumber, numSeats, flightPrice))
                        System.out.println("Flight added");
                    else
                        System.out.println("Flight could not be added");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 3:  //new car
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new car using id: " + arguments.elementAt(1));
                System.out.println("car Location: " + arguments.elementAt(2));
                System.out.println("Add Number of cars: " + arguments.elementAt(3));
                System.out.println("Set Price: " + arguments.elementAt(4));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    numCars = getInt(arguments.elementAt(3));
                    price = getInt(arguments.elementAt(4));

                    if (proxy.addCars(id, location, numCars, price))
                        System.out.println("cars added");
                    else
                        System.out.println("cars could not be added");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 4:  //new room
                if (arguments.size() != 5) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new room using id: " + arguments.elementAt(1));
                System.out.println("room Location: " + arguments.elementAt(2));
                System.out.println("Add Number of rooms: " + arguments.elementAt(3));
                System.out.println("Set Price: " + arguments.elementAt(4));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));
                    numRooms = getInt(arguments.elementAt(3));
                    price = getInt(arguments.elementAt(4));

                    if (proxy.addRooms(id, location, numRooms, price))
                        System.out.println("rooms added");
                    else
                        System.out.println("rooms could not be added");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 5:  //new Customer
                if (arguments.size() != 2) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new Customer using id: " + arguments.elementAt(1));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = proxy.newCustomer(id);

                    if (customer != -1) {
                        System.out.println("new customer id: " + customer);
                    } else {
                        System.out.println("could not get a new customer id. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 6: //delete Flight
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting a flight using id: " + arguments.elementAt(1));
                System.out.println("Flight Number: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));

                    if (proxy.deleteFlight(id, flightNumber))
                        System.out.println("Flight Deleted");
                    else
                        System.out.println("Flight could not be deleted");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 7: //delete car
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting the cars from a particular location  using id: " + arguments.elementAt(1));
                System.out.println("car Location: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    if (proxy.deleteCars(id, location))
                        System.out.println("cars Deleted");
                    else
                        System.out.println("cars could not be deleted");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 8: //delete room
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting all rooms from a particular location  using id: " + arguments.elementAt(1));
                System.out.println("room Location: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    if (proxy.deleteRooms(id, location))
                        System.out.println("rooms Deleted");
                    else
                        System.out.println("rooms could not be deleted");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 9: //delete Customer
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Deleting a customer from the database using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));

                    if (proxy.deleteCustomer(id, customer))
                        System.out.println("Customer Deleted");
                    else
                        System.out.println("Customer could not be deleted");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 10: //querying a flight
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a flight using id: " + arguments.elementAt(1));
                System.out.println("Flight number: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));
                    int seats = proxy.queryFlight(id, flightNumber);

                    if (seats != -1) {
                        System.out.println("Number of seats available: " + seats);
                    } else {
                        System.out.println("could not fetch the number of seats. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 11: //querying a car Location
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a car location using id: " + arguments.elementAt(1));
                System.out.println("car location: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    numCars = proxy.queryCars(id, location);

                    if (numCars != -1) {
                        System.out.println("number of cars at this location: " + numCars);
                    } else {
                        System.out.println("could not fetch the number of cars. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 12: //querying a room location
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a room location using id: " + arguments.elementAt(1));
                System.out.println("room location: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    numRooms = proxy.queryRooms(id, location);

                    if (numRooms != -1) {
                        System.out.println("number of rooms at this location: " + numRooms);
                    } else {
                        System.out.println("could not fetch the number of rooms. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 13: //querying Customer Information
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying Customer information using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));

                    String bill = proxy.queryCustomerInfo(id, customer);

                    if (bill != null) {
                        System.out.println("Customer info: " + bill);
                    } else {
                        System.out.println("could not fetch the customer info. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;               
                
            case 14: //querying a flight Price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a flight Price using id: " + arguments.elementAt(1));
                System.out.println("Flight number: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    flightNumber = getInt(arguments.elementAt(2));

                    price = proxy.queryFlightPrice(id, flightNumber);

                    if (price != -1) {
                        System.out.println("Price of a seat: " + price);
                    } else {
                        System.out.println("could not fetch the flight price. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 15: //querying a car Price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a car price using id: " + arguments.elementAt(1));
                System.out.println("car location: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    price = proxy.queryCarsPrice(id, location);

                    if (price != -1) {
                        System.out.println("Price of a car at this location: " + price);
                    } else {
                        System.out.println("could not fetch the car price. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }                
                break;

            case 16: //querying a room price
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Querying a room price using id: " + arguments.elementAt(1));
                System.out.println("room Location: " + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    location = getString(arguments.elementAt(2));

                    price = proxy.queryRoomsPrice(id, location);

                    if (price != 1) {
                        System.out.println("Price of rooms at this location: " + price);
                    } else {
                        System.out.println("could not fetch the room price. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 17:  //reserve a flight
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving a seat on a flight using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Flight number: " + arguments.elementAt(3));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    flightNumber = getInt(arguments.elementAt(3));

                    if (proxy.reserveFlight(id, customer, flightNumber))
                        System.out.println("Flight Reserved");
                    else
                        System.out.println("Flight could not be reserved.");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 18:  //reserve a car
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving a car at a location using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Location: " + arguments.elementAt(3));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    location = getString(arguments.elementAt(3));
                    
                    if (proxy.reserveCar(id, customer, location))
                        System.out.println("car Reserved");
                    else
                        System.out.println("car could not be reserved.");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 19:  //reserve a room
                if (arguments.size() != 4) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving a room at a location using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                System.out.println("Location: " + arguments.elementAt(3));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    location = getString(arguments.elementAt(3));
                    
                    if (proxy.reserveRoom(id, customer, location))
                        System.out.println("room Reserved");
                    else
                        System.out.println("room could not be reserved.");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                
            case 20:  //reserve an Itinerary
                if (arguments.size()<7) {
                    wrongNumber();
                    break;
                }
                System.out.println("Reserving an Itinerary using id: " + arguments.elementAt(1));
                System.out.println("Customer id: " + arguments.elementAt(2));
                for (int i = 0; i<arguments.size()-6; i++)
                    System.out.println("Flight number" + arguments.elementAt(3 + i));
                System.out.println("Location for car/room booking: " + arguments.elementAt(arguments.size()-3));
                System.out.println("car to book?: " + arguments.elementAt(arguments.size()-2));
                System.out.println("room to book?: " + arguments.elementAt(arguments.size()-1));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));
                    Vector flightNumbers = new Vector();
                    for (int i = 0; i < arguments.size()-6; i++)
                        flightNumbers.addElement(arguments.elementAt(3 + i));
                    location = getString(arguments.elementAt(arguments.size()-3));
                    car = getBoolean(arguments.elementAt(arguments.size()-2));
                    room = getBoolean(arguments.elementAt(arguments.size()-1));
                    
                    if (proxy.reserveItinerary(id, customer, flightNumbers, 
                            location, car, room))
                        System.out.println("Itinerary Reserved");
                    else
                        System.out.println("Itinerary could not be reserved.");
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;
                            
            case 21:  //quit the client
                if (arguments.size() != 1) {
                    wrongNumber();
                    break;
                }
                System.out.println("Quitting client.");
                return;
                
            case 22:  //new Customer given id
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }
                System.out.println("Adding a new Customer using id: "
                        + arguments.elementAt(1)  +  " and cid "  + arguments.elementAt(2));
                try {
                    id = getInt(arguments.elementAt(1));
                    int customer = getInt(arguments.elementAt(2));

                    boolean c = proxy.newCustomerId(id, customer);
                    if (c) {
                        System.out.println("new customer id: " + customer);
                    } else {
                        System.out.println("could not add new customer. Make sure your parameters are correct. Transaction id might be wrong or expired.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;

			case 23:  //start a transaction
                if (arguments.size() != 1) {
                    wrongNumber();
                    break;
                }

                try {
                    System.out.println("Id to be used in your operations: " + proxy.start());
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;

            case 24:  //commit a transaction
                if (arguments.size() != 2) {
                    wrongNumber();
                    break;
                }

                try {
                    id = getInt(arguments.elementAt(1));
                    System.out.println("Committing transaction ID " + id + "...");
                    boolean isCommitted = proxy.commit(id);

                    if (isCommitted) {
                        System.out.println("Transaction ID " + id + " has been successfully committed.");
                    } else {
                        System.out.println("Transaction ID " + id + " could not be committed.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;

            case 25:  //abort a transaction
                if (arguments.size() != 2) {
                    wrongNumber();
                    break;
                }

                try {
                    id = getInt(arguments.elementAt(1));
                    System.out.println("Aborting transaction ID " + id + "...");
                    boolean isAborted = proxy.abort(id);

                    if (isAborted) {
                        System.out.println("Transaction ID " + id + " has been successfully aborted.");
                    } else {
                        System.out.println("Transaction ID " + id + " could not be aborted.");
                    }
                }
                catch(Exception e) {
                    printErrorMessage(e);
                }
                break;

			case 26: //Shutdown
				proxy.shutdown();
				return;

            case 27: //crash
                if (arguments.size() != 2) {
                    wrongNumber();
                    break;
                }

                try {
                    String rm = getString(arguments.elementAt(1));
                    System.out.println("Crashing " + rm + " RM...");
                    boolean isCrashed = proxy.crash(rm);

                    if (isCrashed) {
                        System.out.println(rm + " RM has been successfully crashed.");
                    } else {
                        System.out.println(rm + " RM's survival instinct prevented the crashing process. Long live the " + rm + " RM!");
                    }
                } catch(Exception e) {
                    printErrorMessage(e);
                }
                break;

            case 28: //setdie
                if (arguments.size() != 3) {
                    wrongNumber();
                    break;
                }

                try {
                    String server = getString(arguments.elementAt(1));
                    String when = getString(arguments.elementAt(2));
                    System.out.println("Ordering " + server + " server to crash at moment " + when + "...");
                    boolean isSetToDie = proxy.setDie(server, when);

                    if (isSetToDie) {
                        System.out.println(server + " server has been successfully set to die at moment " + when + ".");
                    } else {
                        System.out.println(server + " server's survival instinct prevented us from convincing him to die. Long live the " + server + " server!");
                        System.out.println("(Or you used the command in the wrong way... or it's been already used once... resetdie first, then setdie again!");
                    }
                } catch (Exception e) {
                    printErrorMessage(e);
                }
                break;

            case 29: //resetdie
                if (arguments.size() != 1) {
                    wrongNumber();
                    break;
                }

                if (proxy.resetDie()) {
                    System.out.println("Successfully reset all server crash settings to default values.");
                } else {
                    System.out.println("An error occurred. Somehow...");
                }
                break;

            default:
                System.out.println("The interface does not support this command.");
                break;
            }
        }
    }
        
    public Vector parse(String command) {
        Vector arguments = new Vector();
        StringTokenizer tokenizer = new StringTokenizer(command, ",");
        String argument = "";
        while (tokenizer.hasMoreTokens()) {
            argument = tokenizer.nextToken();
            argument = argument.trim();
            arguments.add(argument);
        }
        return arguments;
    }

    public void listCommands() {
        System.out.println("\nWelcome to the client interface provided to test your project.");
        System.out.println("Commands accepted by the interface are: ");
        System.out.println("help");
        System.out.println("newflight\nnewcar\nnewroom\nnewcustomer\nnewcustomerid\ndeleteflight\ndeletecar\ndeleteroom");
        System.out.println("deletecustomer\nqueryflight\nquerycar\nqueryroom\nquerycustomer");
        System.out.println("queryflightprice\nquerycarprice\nqueryroomprice");
        System.out.println("reserveflight\nreservecar\nreserveroom\nitinerary");
        System.out.println("start\ncommit\nabort\nshutdown\ncrash\nsetdie\nresetdie");
        System.out.println("quit");
        System.out.println("\ntype help, <commandname> for detailed info (note the use of comma).");
    }
    
    public void listSpecific(String command) {
        System.out.print("Help on: ");
        switch(findChoice(command)) {
            case 1:
            System.out.println("Help");
            System.out.println("\nTyping help on the prompt gives a list of all the commands available.");
            System.out.println("Typing help, <commandname> gives details on how to use the particular command.");
            break;

            case 2:  //new flight
            System.out.println("Adding a new Flight.");
            System.out.println("Purpose: ");
            System.out.println("\tAdd information about a new flight.");
            System.out.println("\nUsage: ");
            System.out.println("\tnewflight, <id>, <flightnumber>, <numSeats>, <flightprice>");
            break;
            
            case 3:  //new car
            System.out.println("Adding a new car.");
            System.out.println("Purpose: ");
            System.out.println("\tAdd information about a new car location.");
            System.out.println("\nUsage: ");
            System.out.println("\tnewcar, <id>, <location>, <numberofcars>, <pricepercar>");
            break;
            
            case 4:  //new room
            System.out.println("Adding a new room.");
            System.out.println("Purpose: ");
            System.out.println("\tAdd information about a new room location.");
            System.out.println("\nUsage: ");
            System.out.println("\tnewroom, <id>, <location>, <numberofrooms>, <priceperroom>");
            break;
            
            case 5:  //new Customer
            System.out.println("Adding a new Customer.");
            System.out.println("Purpose: ");
            System.out.println("\tGet the system to provide a new customer id. (same as adding a new customer)");
            System.out.println("\nUsage: ");
            System.out.println("\tnewcustomer, <id>");
            break;
            
            
            case 6: //delete Flight
            System.out.println("Deleting a flight");
            System.out.println("Purpose: ");
            System.out.println("\tDelete a flight's information.");
            System.out.println("\nUsage: ");
            System.out.println("\tdeleteflight, <id>, <flightnumber>");
            break;
            
            case 7: //delete car
            System.out.println("Deleting a car");
            System.out.println("Purpose: ");
            System.out.println("\tDelete all cars from a location.");
            System.out.println("\nUsage: ");
            System.out.println("\tdeletecar, <id>, <location>, <numCars>");
            break;
            
            case 8: //delete room
            System.out.println("Deleting a room");
            System.out.println("\nPurpose: ");
            System.out.println("\tDelete all rooms from a location.");
            System.out.println("Usage: ");
            System.out.println("\tdeleteroom, <id>, <location>, <numRooms>");
            break;
            
            case 9: //delete Customer
            System.out.println("Deleting a Customer");
            System.out.println("Purpose: ");
            System.out.println("\tRemove a customer from the database.");
            System.out.println("\nUsage: ");
            System.out.println("\tdeletecustomer, <id>, <customerid>");
            break;
            
            case 10: //querying a flight
            System.out.println("Querying flight.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain Seat information about a certain flight.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryflight, <id>, <flightnumber>");
            break;
            
            case 11: //querying a car Location
            System.out.println("Querying a car location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain number of cars at a certain car location.");
            System.out.println("\nUsage: ");
            System.out.println("\tquerycar, <id>, <location>");        
            break;
            
            case 12: //querying a room location
            System.out.println("Querying a room Location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain number of rooms at a certain room location.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryroom, <id>, <location>");        
            break;
            
            case 13: //querying Customer Information
            System.out.println("Querying Customer Information.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain information about a customer.");
            System.out.println("\nUsage: ");
            System.out.println("\tquerycustomer, <id>, <customerid>");
            break;               
            
            case 14: //querying a flight for price 
            System.out.println("Querying flight.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain price information about a certain flight.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryflightprice, <id>, <flightnumber>");
            break;
            
            case 15: //querying a car Location for price
            System.out.println("Querying a car location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain price information about a certain car location.");
            System.out.println("\nUsage: ");
            System.out.println("\tquerycarprice, <id>, <location>");        
            break;
            
            case 16: //querying a room location for price
            System.out.println("Querying a room Location.");
            System.out.println("Purpose: ");
            System.out.println("\tObtain price information about a certain room location.");
            System.out.println("\nUsage: ");
            System.out.println("\tqueryroomprice, <id>, <location>");        
            break;

            case 17:  //reserve a flight
            System.out.println("Reserving a flight.");
            System.out.println("Purpose: ");
            System.out.println("\tReserve a flight for a customer.");
            System.out.println("\nUsage: ");
            System.out.println("\treserveflight, <id>, <customerid>, <flightnumber>");
            break;
            
            case 18:  //reserve a car
            System.out.println("Reserving a car.");
            System.out.println("Purpose: ");
            System.out.println("\tReserve a given number of cars for a customer at a particular location.");
            System.out.println("\nUsage: ");
            System.out.println("\treservecar, <id>, <customerid>, <location>, <nummberofcars>");
            break;
            
            case 19:  //reserve a room
            System.out.println("Reserving a room.");
            System.out.println("Purpose: ");
            System.out.println("\tReserve a given number of rooms for a customer at a particular location.");
            System.out.println("\nUsage: ");
            System.out.println("\treserveroom, <id>, <customerid>, <location>, <nummberofrooms>");
            break;
            
            case 20:  //reserve an Itinerary
            System.out.println("Reserving an Itinerary.");
            System.out.println("Purpose: ");
            System.out.println("\tBook one or more flights.Also book zero or more cars/rooms at a location.");
            System.out.println("\nUsage: ");
            System.out.println("\titinerary, <id>, <customerid>, "
                    + "<flightnumber1>....<flightnumberN>, "
                    + "<LocationToBookcarsOrrooms>, <NumberOfcars>, <NumberOfroom>");
            break;
            

            case 21:  //quit the client
            System.out.println("Quitting client.");
            System.out.println("Purpose: ");
            System.out.println("\tExit the client application.");
            System.out.println("\nUsage: ");
            System.out.println("\tquit");
            break;
            
            case 22:  //new customer with id
            System.out.println("Create new customer providing an id");
            System.out.println("Purpose: ");
            System.out.println("\tCreates a new customer with the id provided");
            System.out.println("\nUsage: ");
            System.out.println("\tnewcustomerid, <id>, <customerid>");
            break;

            case 23:  //start
            System.out.println("Start a session");
            System.out.println("Purpose: ");
            System.out.println("\tReturns an ID with which the user can initiate a transaction");
            System.out.println("\nUsage: ");
            System.out.println("\tstart");
            break;

            case 24:  //commit
            System.out.println("Commit a completed transaction");
            System.out.println("Purpose: ");
            System.out.println("\tEnsure the completed transaction is successfully saved");
            System.out.println("\nUsage: ");
            System.out.println("\tcommit, <id>");
            break;

            case 25:  //abort
            System.out.println("Abort the current transaction");
            System.out.println("Purpose: ");
            System.out.println("\tCancel all operations that have been taken during the transaction");
            System.out.println("\nUsage: ");
            System.out.println("\tabort, <id>");
            break;

            case 26: //shutdown
            System.out.println("Shutdown the system");
            System.out.println("Purpose: ");
            System.out.println("\tShuts the entire system down with all its rms");
            System.out.println("\nUsage: ");
            System.out.println("\tshutdown");
            break;

            case 27: //crash
            System.out.println("Crash the specified RM");
            System.out.println("Purpose: ");
            System.out.println("\tProvoke the specified RM to crash");
            System.out.println("\nUsage: ");
            System.out.println("\tcrash, <rm name (e.g. customer, flight, car, room, mw)>");
            break;

            case 28: //setdie
            System.out.println("Set a moment for a server to die");
            System.out.println("Purpose: ");
            System.out.println("\tSet a specific moment for the specified server to crash");
            System.out.println("\nUsage: ");
            System.out.println("\tsetdie, <server name (e.g. customer, flight, car, room, mw)>, <when>");
            System.out.println("\t<when>: if server = mw:");
            System.out.println("\t\tbeforevote: crash before sending vote request");
            System.out.println("\t\taftervote_some: crash after receiving some replies but not all");
            System.out.println("\t\tbeforedecide: crash after receiving all replies but before deciding");
            System.out.println("\t\tafterdecide_none: crash after deciding but before sending decision");
            System.out.println("\t\tafterdecide_some: crash after sending some but not all decisions");
            System.out.println("\t\tafterdecide_all: crash after having sent all decisions");
            System.out.println("\t<when>: else:");
            System.out.println("\t\tbeforevote: crash after receive vote request but before sending answer");
            System.out.println("\t\taftervote: crash after sending answer");
            System.out.println("\t\tafterdecide: crash after receiving decision but before committing/aborting");
            break;

            case 29: //resetdie
            System.out.println("Resets all settings set via `setdie`");
            System.out.println("Purpose: ");
            System.out.println("\tRemoves all settings originally set with the command `setdie` such that servers won't die");
            System.out.println("\nUsage: ");
            System.out.println("\tresetdie");
            break;

            default:
            System.out.println(command);
            System.out.println("The interface does not support this command.");
            break;
        }
    }
    
    public void wrongNumber() {
        System.out.println("The number of arguments provided in this command are wrong.");
        System.out.println("Type help, <commandname> to check usage of this command.");
    }

    public int getInt(Object temp) throws Exception {
        try {
            return (new Integer((String)temp)).intValue();
        }
        catch(Exception e) {
            throw e;
        }
    }
    
    public boolean getBoolean(Object temp) throws Exception {
        try {
            return (new Boolean((String)temp)).booleanValue();
        }
        catch(Exception e) {
            throw e;
        }
    }

    public String getString(Object temp) throws Exception {
        try {    
            return (String)temp;
        }
        catch (Exception e) {
            throw e;
        }
    }

    private void printErrorMessage(Exception e) {
        System.out.print("Error: ");
        System.out.println(e.getMessage());
    }
}
