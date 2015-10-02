// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.*;
import javax.jws.WebService;


//@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImpl implements server.ws.ResourceManager, Runnable {
    
    protected RMHashtable m_itemHT;
    protected Socket m_sock;

    public static void RunNonBlocking(RMHashtable data, Socket sock)
    {    	
    	// This method is here to
    	// 1) hide the Thread from the connection logic
    	// 2) make sure that the rmImpl is constructed properly before running it
    	// 		(which would not happen if the thread was in the constructor)
    	ResourceManagerImpl instance = new ResourceManagerImpl(data, sock);
    	Thread self = new Thread(instance);
    	self.start();
    }
    
    private ResourceManagerImpl(RMHashtable data, Socket sock)
    {
    	m_itemHT = data;
    	m_sock = sock;
    }
    
    public void run()
    {
    	// Read input from socket
    	InputStream sock_in = null;
    	String input = null;
    	try
    	{
    		sock_in = m_sock.getInputStream();
    		BufferedReader in = new BufferedReader(new InputStreamReader(sock_in));
    		input = in.readLine();
    	}
    	catch (IOException e)
    	{
    		// TODO log failure and die
    		return;
    	}
    	
    	// Get command, arguments from input
    	String[] cmdParts = input.split(",");
    	for(int i = 0 ; i < cmdParts.length ; i++)
    		cmdParts[i] = cmdParts[i].trim();
    	
    	// Get method from command and typecheck
    	Method operation = null; // This is what we'll execute
    	Object[] params = null; // Parameters to the method
    	Class myClass = this.getClass();
    	boolean methodExists = false; // For error reporting purposes
    	
    	// We're doing it this way by looping through the methods
    	// so that we can have some "overloading" if we want to.
    	// (for example reserveItinerary <flight> vs.
    	// reserveItinerary <flight> <car> <hotel> could execute
    	// different methods)
    	Method[] myMethods = myClass.getMethods();
    	for (int i = 0 ; i < myMethods.length ; i++)
    	{
    		if (myMethods[i].getName().equalsIgnoreCase(cmdParts[0]))
    		{
    			methodExists = true;
    			Class[] paramTypes = myMethods[i].getParameterTypes();
    			
    			// Do the number of parameters match?
    			if (paramTypes.length + 1 != cmdParts.length)
    				continue;
    			
    			// Typecheck and collect arguments for the method
    			Object[] candidateParams = new Object[paramTypes.length];
    			boolean typechecks = true;
    			
    			for (int j = 0 ; j < paramTypes.length && typechecks ; j++) 
    			{
    				if (paramTypes[j].equals(String.class))
    				{
    					candidateParams[j] = cmdParts[j + 1];
    				}
    				else if (paramTypes[j].equals(int.class))
    				{
    					try
    					{
    						candidateParams[j] = Integer.parseInt(cmdParts[j + 1]);
    					}
    					catch (NumberFormatException e)
    					{
    						// Move on to next method
    						typechecks = false;
    						break;
    					}
    				}
    				else
    				{
    					// TODO What should we do here?
    					// We don't support other param types, so
    					// either error out, or keep searching for
    					// a suitable method? Assert?
    				}
    			}
    			
    			// If all types correspond, then we got our method and we're done.
    			if (typechecks)
    			{
    				params = candidateParams;
    				operation = myMethods[i];
    				break;
    			}
    		}
    	}
    	
    	// If we didn't find the method we needed, output 
    	if (operation == null)
    	{
    		if (methodExists)
    		{
    			// TODO return usage of this command
    		}
    		else
    		{
    			// TODO return no such command
    		}
    		
    		// TODO This will be changed
    		return;
    	}
    	
    	System.out.println(Arrays.toString(params));
    	Object result = null;
    	try
    	{
    		result = operation.invoke(this, params);    		
    	}
    	catch (InvocationTargetException e)
    	{
    		// TODO what should happen here?
    	}
    	catch (IllegalAccessException e)
    	{
    		// TODO what should happen here?
    	}
    	
    	if (!(result instanceof Serializable))
    	{
    		// Assertion because the programmer should return the proper thing.
    		throw new AssertionError("Result of method must be serializable");
    	}

    	// Send over socket
    	OutputStream sock_out = null;
    	try
    	{
    		sock_out = m_sock.getOutputStream();
    		ObjectOutputStream out = new ObjectOutputStream(sock_out);
    		out.writeObject(result);
    		m_sock.close();
    	}
    	catch (IOException e)
    	{
    		//TODO Log and die
    	}
    }
    
    // Basic operations on RMItem //
    
    // Read a data item.
    private RMItem readData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.get(key);
        }
    }

    // Write a data item.
    private void writeData(int id, String key, RMItem value) {
        synchronized(m_itemHT) {
            m_itemHT.put(key, value);
        }
    }
    
    // Remove the item out of storage.
    protected RMItem removeData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.remove(key);
        }
    }
    
    
    // Basic operations on ReservableItem //
    
    // Delete the entire item.
    protected boolean deleteItem(int id, String key) {
        Trace.info("RM::deleteItem(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        // Check if there is such an item in the storage.
        if (curObj == null) {
            Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed: " 
                    + " item doesn't exist.");
            return false;
        } else {
            if (curObj.getReserved() == 0) {
                removeData(id, curObj.getKey());
                Trace.info("RM::deleteItem(" + id + ", " + key + ") OK.");
                return true;
            }
            else {
                Trace.info("RM::deleteItem(" + id + ", " + key + ") failed: "
                        + "some customers have reserved it.");
                return false;
            }
        }
    }
    
    // Query the number of available seats/rooms/cars.
    protected int queryNum(int id, String key) {
        Trace.info("RM::queryNum(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0;  
        if (curObj != null) {
            value = curObj.getCount();
        }
        Trace.info("RM::queryNum(" + id + ", " + key + ") OK: " + value);
        return value;
    }    
    
    // Query the price of an item.
    protected int queryPrice(int id, String key) {
        Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0; 
        if (curObj != null) {
            value = curObj.getPrice();
        }
        Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") OK: $" + value);
        return value;
    }

    // Reserve an item.
    protected boolean reserveItem(int id, int customerId, 
                                  String key, String location) {
        Trace.info("RM::reserveItem(" + id + ", " + customerId + ", " 
                + key + ", " + location + ") called.");
        // Read customer object if it exists (and read lock it).
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
                   + key + ", " + location + ") failed: customer doesn't exist.");
            return false;
        } 
        
        // Check if the item is available.
        ReservableItem item = (ReservableItem) readData(id, key);
        if (item == null) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
                    + key + ", " + location + ") failed: item doesn't exist.");
            return false;
        } else if (item.getCount() == 0) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
                    + key + ", " + location + ") failed: no more items.");
            return false;
        } else {
            // Do reservation.
            cust.reserve(key, location, item.getPrice());
            writeData(id, cust.getKey(), cust);
            
            // Decrease the number of available items in the storage.
            item.setCount(item.getCount() - 1);
            item.setReserved(item.getReserved() + 1);
            
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
                    + key + ", " + location + ") OK.");
            return true;
        }
    }
    
    
    // Flight operations //
    
    // Create a new flight, or add seats to existing flight.
    // Note: if flightPrice <= 0 and the flight already exists, it maintains 
    // its current price.
    @Override
    public boolean addFlight(int id, int flightNumber, 
                             int numSeats, int flightPrice) {
        Trace.info("RM::addFlight(" + id + ", " + flightNumber 
                + ", $" + flightPrice + ", " + numSeats + ") called.");
        Flight curObj = (Flight) readData(id, Flight.getKey(flightNumber));
        if (curObj == null) {
            // Doesn't exist; add it.
            Flight newObj = new Flight(flightNumber, numSeats, flightPrice);
            writeData(id, newObj.getKey(), newObj);
            Trace.info("RM::addFlight(" + id + ", " + flightNumber 
                    + ", $" + flightPrice + ", " + numSeats + ") OK.");
        } else {
            // Add seats to existing flight and update the price.
            curObj.setCount(curObj.getCount() + numSeats);
            if (flightPrice > 0) {
                curObj.setPrice(flightPrice);
            }
            writeData(id, curObj.getKey(), curObj);
            Trace.info("RM::addFlight(" + id + ", " + flightNumber 
                    + ", $" + flightPrice + ", " + numSeats + ") OK: "
                    + "seats = " + curObj.getCount() + ", price = $" + flightPrice);
        }
        return(true);
    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {
        return deleteItem(id, Flight.getKey(flightNumber));
    }

    // Returns the number of empty seats on this flight.
    @Override
    public int queryFlight(int id, int flightNumber) {
        return queryNum(id, Flight.getKey(flightNumber));
    }

    // Returns price of this flight.
    public int queryFlightPrice(int id, int flightNumber) {
        return queryPrice(id, Flight.getKey(flightNumber));
    }

    /*
    // Returns the number of reservations for this flight. 
    public int queryFlightReservations(int id, int flightNumber) {
        Trace.info("RM::queryFlightReservations(" + id 
                + ", #" + flightNumber + ") called.");
        RMInteger numReservations = (RMInteger) readData(id, 
                Flight.getNumReservationsKey(flightNumber));
        if (numReservations == null) {
            numReservations = new RMInteger(0);
       }
        Trace.info("RM::queryFlightReservations(" + id + 
                ", #" + flightNumber + ") = " + numReservations);
        return numReservations.getValue();
    }
    */
    
    /*
    // Frees flight reservation record. Flight reservation records help us 
    // make sure we don't delete a flight if one or more customers are 
    // holding reservations.
    public boolean freeFlightReservation(int id, int flightNumber) {
        Trace.info("RM::freeFlightReservations(" + id + ", " 
                + flightNumber + ") called.");
        RMInteger numReservations = (RMInteger) readData(id, 
                Flight.getNumReservationsKey(flightNumber));
        if (numReservations != null) {
            numReservations = new RMInteger(
                    Math.max(0, numReservations.getValue() - 1));
        }
        writeData(id, Flight.getNumReservationsKey(flightNumber), numReservations);
        Trace.info("RM::freeFlightReservations(" + id + ", " 
                + flightNumber + ") OK: reservations = " + numReservations);
        return true;
    }
    */


    // Car operations //

    // Create a new car location or add cars to an existing location.
    // Note: if price <= 0 and the car location already exists, it maintains 
    // its current price.
    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {
        Trace.info("RM::addCars(" + id + ", " + location + ", " 
                + numCars + ", $" + carPrice + ") called.");
        Car curObj = (Car) readData(id, Car.getKey(location));
        if (curObj == null) {
            // Doesn't exist; add it.
            Car newObj = new Car(location, numCars, carPrice);
            writeData(id, newObj.getKey(), newObj);
            Trace.info("RM::addCars(" + id + ", " + location + ", " 
                    + numCars + ", $" + carPrice + ") OK.");
        } else {
            // Add count to existing object and update price.
            curObj.setCount(curObj.getCount() + numCars);
            if (carPrice > 0) {
                curObj.setPrice(carPrice);
            }
            writeData(id, curObj.getKey(), curObj);
            Trace.info("RM::addCars(" + id + ", " + location + ", " 
                    + numCars + ", $" + carPrice + ") OK: " 
                    + "cars = " + curObj.getCount() + ", price = $" + carPrice);
        }
        return(true);
    }

    // Delete cars from a location.
    @Override
    public boolean deleteCars(int id, String location) {
        return deleteItem(id, Car.getKey(location));
    }

    // Returns the number of cars available at a location.
    @Override
    public int queryCars(int id, String location) {
        return queryNum(id, Car.getKey(location));
    }

    // Returns price of cars at this location.
    @Override
    public int queryCarsPrice(int id, String location) {
        return queryPrice(id, Car.getKey(location));
    }
    

    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
        Trace.info("RM::addRooms(" + id + ", " + location + ", " 
                + numRooms + ", $" + roomPrice + ") called.");
        Room curObj = (Room) readData(id, Room.getKey(location));
        if (curObj == null) {
            // Doesn't exist; add it.
            Room newObj = new Room(location, numRooms, roomPrice);
            writeData(id, newObj.getKey(), newObj);
            Trace.info("RM::addRooms(" + id + ", " + location + ", " 
                    + numRooms + ", $" + roomPrice + ") OK.");
        } else {
            // Add count to existing object and update price.
            curObj.setCount(curObj.getCount() + numRooms);
            if (roomPrice > 0) {
                curObj.setPrice(roomPrice);
            }
            writeData(id, curObj.getKey(), curObj);
            Trace.info("RM::addRooms(" + id + ", " + location + ", " 
                    + numRooms + ", $" + roomPrice + ") OK: " 
                    + "rooms = " + curObj.getCount() + ", price = $" + roomPrice);
        }
        return(true);
    }

    // Delete rooms from a location.
    @Override
    public boolean deleteRooms(int id, String location) {
        return deleteItem(id, Room.getKey(location));
    }

    // Returns the number of rooms available at a location.
    @Override
    public int queryRooms(int id, String location) {
        return queryNum(id, Room.getKey(location));
    }
    
    // Returns room price at this location.
    @Override
    public int queryRoomsPrice(int id, String location) {
        return queryPrice(id, Room.getKey(location));
    }


    // Customer operations //

    @Override
    public int newCustomer(int id) {
        Trace.info("INFO: RM::newCustomer(" + id + ") called.");
        // Generate a globally unique Id for the new customer.
        int customerId = Integer.parseInt(String.valueOf(id) +
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                String.valueOf(Math.round(Math.random() * 100 + 1)));
        Customer cust = new Customer(customerId);
        writeData(id, cust.getKey(), cust);
        Trace.info("RM::newCustomer(" + id + ") OK: " + customerId);
        return customerId;
    }

    // This method makes testing easier.
    @Override
    public boolean newCustomerId(int id, int customerId) {
        Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            cust = new Customer(customerId);
            writeData(id, cust.getKey(), cust);
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") OK.");
            return true;
        } else {
            Trace.info("INFO: RM::newCustomer(" + id + ", " + 
                    customerId + ") failed: customer already exists.");
            return false;
        }
    }

    // Delete customer from the database. 
    @Override
    public boolean deleteCustomer(int id, int customerId) {
        Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::deleteCustomer(" + id + ", " 
                    + customerId + ") failed: customer doesn't exist.");
            return false;
        } else {            
            // Increase the reserved numbers of all reservable items that 
            // the customer reserved. 
            RMHashtable reservationHT = cust.getReservations();
            for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
                String reservedKey = (String) (e.nextElement());
                ReservedItem reservedItem = cust.getReservedItem(reservedKey);
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): " 
                        + "deleting " + reservedItem.getCount() + " reservations "
                        + "for item " + reservedItem.getKey());
                ReservableItem item = 
                        (ReservableItem) readData(id, reservedItem.getKey());
                item.setReserved(item.getReserved() - reservedItem.getCount());
                item.setCount(item.getCount() + reservedItem.getCount());
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): "
                        + reservedItem.getKey() + " reserved/available = " 
                        + item.getReserved() + "/" + item.getCount());
            }
            // Remove the customer from the storage.
            removeData(id, cust.getKey());
            Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
            return true;
        }
    }

    // Return data structure containing customer reservation info. 
    // Returns null if the customer doesn't exist. 
    // Returns empty RMHashtable if customer exists but has no reservations.
    public RMHashtable getCustomerReservations(int id, int customerId) {
        Trace.info("RM::getCustomerReservations(" + id + ", " 
                + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.info("RM::getCustomerReservations(" + id + ", " 
                    + customerId + ") failed: customer doesn't exist.");
            return null;
        } else {
            return cust.getReservations();
        }
    }

    // Return a bill.
    @Override
    public String queryCustomerInfo(int id, int customerId) {
        Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + ") called.");
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::queryCustomerInfo(" + id + ", " 
                    + customerId + ") failed: customer doesn't exist.");
            // Returning an empty bill means that the customer doesn't exist.
            return "";
        } else {
            String s = cust.printBill();
            Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + "): \n");
            System.out.println(s);
            return s;
        }
    }

    // Add flight reservation to this customer.  
    @Override
    public boolean reserveFlight(int id, int customerId, int flightNumber) {
        return reserveItem(id, customerId, 
                Flight.getKey(flightNumber), String.valueOf(flightNumber));
    }

    // Add car reservation to this customer. 
    @Override
    public boolean reserveCar(int id, int customerId, String location) {
        return reserveItem(id, customerId, Car.getKey(location), location);
    }

    // Add room reservation to this customer. 
    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
        return reserveItem(id, customerId, Room.getKey(location), location);
    }
    

    // Reserve an itinerary.
    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
        return false;
    }

}
