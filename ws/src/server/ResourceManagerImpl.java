// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package server;

import Util.Trace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.jws.HandlerChain;
import javax.jws.WebService;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

@WebService(endpointInterface = "server.ws.ResourceManager")
@HandlerChain(file="rm_handler.xml")
public class ResourceManagerImpl extends server.ws.ResourceManagerAbstract {

    protected final Object bidon = new Object();
    protected Hashtable<Integer, LinkedList<ClientOperation>> _temporaryOperations = new Hashtable<Integer, LinkedList<ClientOperation>>();
    protected RMHashtable m_itemHT = new RMHashtable();
    protected String thisRmName;
    
    public ResourceManagerImpl()
    {
    	Context env = null;
		try
		{
			env = (Context) new InitialContext().lookup("java:comp/env");
			thisRmName = (String) env.lookup("name");
		} 
		catch (NamingException e)
		{
			throw new RuntimeException(e);
		}
    }
    
    // Basic operations on RMItem //

    /**
     * Read a data item. If the requested item has been locally updated, read this version.
     */
    private RMItem readData(int id, String key) {
        synchronized(bidon) {
            RMItem item = null;
            LinkedList<ClientOperation> operations = _temporaryOperations.get(id);

            boolean wasFound = false;

            if (operations != null && ClientOperation.hasOp(operations, key)) {
                item = ClientOperation.getLatest(operations, key);
                wasFound = true;
            }

            if (!wasFound) {
            	// Get a copy of the item that's in the hashTable.
            	item = (RMItem) m_itemHT.get(key);
            	if (item != null)
            		item = (RMItem) item.clone();
            }

            return item;
        }
    }

    // Write a data item.
    private void writeData(int id, String key, RMItem value) {
        synchronized(bidon) {
            addTemporaryOperation(id, key, value, ClientOperation.Type.WRITE);
        }
    }
    
    // Remove the item out of storage.
    protected void removeData(int id, String key) {
        synchronized(bidon) {
            addTemporaryOperation(id, key, null, ClientOperation.Type.DELETE);
        }
    }

    // Abort a transaction.
    public boolean abort(int id) {
        synchronized(bidon) {
            _temporaryOperations.remove(id);
        }
        
        try {
			Files.deleteIfExists(Paths.get(thisRmName + "." + id + ".next"));
		} catch (IOException e) {
			//Technically this isn't really problematic.
			e.printStackTrace();
		}

        Trace.info("Abort confirm for " + id);
		
		return true;
    }

    @Override
    public boolean prepare(int id) {
    	RMHashtable next_write = new RMHashtable();
    	HashSet<String> next_remove = new HashSet<String>();
    	
    	boolean result = true;
    	
        LinkedList<ClientOperation> operations = _temporaryOperations.get(id);

        if (operations == null) {
            Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " vote " + id + " " + true, true);
            return true;
        }

        Iterator<ClientOperation> it = operations.iterator();

        while (it.hasNext()) {
            ClientOperation op = it.next();

            switch (op.getOperationType()) {
                case WRITE:
                    next_write.put(op.getKey(), op.getItem());
                    break;
                case DELETE:
                	// In next_write, we only want the latest version of the things
                	// we'll write. So something that gets deleted shouldn't be in there
                	// (This could happen if we did w(x), d(x), commit)
                	next_write.remove(op.getKey());
                	next_remove.add(op.getKey());
                    break;
                default:
                    break;
            }
        }

        List<Object> objects = new ArrayList<>();
        objects.add(next_write);
        objects.add(next_remove);

        boolean isPersisted = Trace.persist("data/" + thisRmName + "." + id + ".next", objects, false);

        if (!isPersisted) {
        	result = false;
        }
        
    	// This trace will need to occur AFTER we wrote the data to disk
        Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " vote " + id + " " + result, true);
    	return result;
    }

    // Commit a transaction.
    public boolean commit(int id) {
        synchronized(bidon) {
            LinkedList<ClientOperation> operations = _temporaryOperations.get(id);

            if (operations == null) {
                Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " commit " + id, true);
                return true;
            }

            Iterator<ClientOperation> it = operations.iterator();

            while (it.hasNext()) {
                ClientOperation op = it.next();

                switch (op.getOperationType()) {
                    case WRITE:
                        m_itemHT.put(op.getKey(), op.getItem());
                        break;
                    case DELETE:
                        m_itemHT.remove(op.getKey());
                        break;
                    default:
                        //nothing to do
                }
            }

            _temporaryOperations.remove(id);

            List<Object> objects = new ArrayList<>();
            objects.add(m_itemHT);

            Trace.persist("data/" + thisRmName + "." + id + ".master", objects, true);

            try {
                Files.deleteIfExists(Paths.get("data/" + thisRmName + "." + id + ".next"));
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
        }

        Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " commit " + id, true);
        return true;
    }
    
    // Basic operations on ReservableItem //
    
    // Delete the entire item.
    protected boolean deleteItem(int id, String key) {
        Trace.info("RM::deleteItem(" + id + ", " + key + ") called.");

    	// Potential data corruption: we test getReserved
    	// and find 0, but before we delete it, another thread makes
    	// a reservation on the item. Hence, we synchronize this access.
        synchronized (bidon) {
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
        
        // Potential concurrency errors:
        // - We read the customer, and someone else deletes it while we reserve.
        //   In that case, the delete would be "undone".
        // - Two threads end up in the "do reservation" branch but only one item
        //   remains, leading to overbooking.
        // - Item has one reservation, but before performing the new reservation,
        //   the old customer is deleted and then the item is deleted. This will
        //   write a reservation for an unexisting item.
        synchronized (bidon) {
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

                addTemporaryOperation(id, key, item, ClientOperation.Type.WRITE);
	            Trace.info("RM::reserveItem(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
	            return true;
	        }
        }
    }
    
    
    // Flight operations //
    
    // Create a new flight, or add seats to existing flight.
    // Note: if flightPrice <= 0 and the flight already exists, it maintains 
    // its current price.
    
    public boolean addFlight(int id, int flightNumber, 
                             int numSeats, int flightPrice) {
        Trace.info("RM::addFlight(" + id + ", " + flightNumber 
                + ", $" + flightPrice + ", " + numSeats + ") called.");
        
        // There are no sync issues here. Maybe there was no flight
        // when we read, but then a new one appeared. In this case
        // we have to keep one or the other anyways.
        // Maybe there was a flight when we read, but then it was deleted
        // Then it will be restored. In any case, no data will be
        // corrupted.
        
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

    
    public boolean deleteFlight(int id, int flightNumber) {
        boolean isDeleted = deleteItem(id, Flight.getKey(flightNumber));
        return isDeleted;
    }

    // Returns the number of empty seats on this flight.
    
    public int queryFlight(int id, int flightNumber) {
        int emptySeats = queryNum(id, Flight.getKey(flightNumber));
        return emptySeats;
    }

    // Returns price of this flight.
    public int queryFlightPrice(int id, int flightNumber) {
        int price = queryPrice(id, Flight.getKey(flightNumber));
        return price;
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
    
    public boolean deleteCars(int id, String location) {
        boolean isDeleted = deleteItem(id, Car.getKey(location));
        return isDeleted;
    }

    // Returns the number of cars available at a location.
    
    public int queryCars(int id, String location) {
        int cars = queryNum(id, Car.getKey(location));
        return cars;
    }

    // Returns price of cars at this location.
    
    public int queryCarsPrice(int id, String location) {
        int price = queryPrice(id, Car.getKey(location));
        return price;
    }
    

    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    
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
    
    public boolean deleteRooms(int id, String location) {
        boolean isDeleted = deleteItem(id, Room.getKey(location));
        return isDeleted;
    }

    // Returns the number of rooms available at a location.
    
    public int queryRooms(int id, String location) {
        int rooms = queryNum(id, Room.getKey(location));
        return rooms;
    }
    
    // Returns room price at this location.
    
    public int queryRoomsPrice(int id, String location) {
        int price = queryPrice(id, Room.getKey(location));
        return price;
    }


    // Customer operations //

    
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
    
    public boolean newCustomerId(int id, int customerId) {
        Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") called.");
        
        // There are no sync issues here. If there was no customer but one
        // was created just before we write ours, we overwrite it and nothing
        // harmful happens.
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
            RMHashtable reservations = cust.getReservations();
            return reservations;
        }
    }

    // Return a bill.
    
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
    
    public boolean reserveFlight(int id, int customerId, int flightNumber) {
        boolean isReserved = reserveItem(id, customerId,
                Flight.getKey(flightNumber), String.valueOf(flightNumber));
        return isReserved;
    }

    // Add car reservation to this customer. 
    
    public boolean reserveCar(int id, int customerId, String location) {
        boolean isReserved = reserveItem(id, customerId, Car.getKey(location), location);
        return isReserved;
    }

    // Add room reservation to this customer. 
    
    public boolean reserveRoom(int id, int customerId, String location) {
        boolean isReserved = reserveItem(id, customerId, Room.getKey(location), location);
        return isReserved;
    }

	public boolean checkCustomerExistence(int id, int customerId) {
		Trace.info("RM::checkCustomerExistence(" + id + ", " + customerId + ") called.");
        if (readData(id, Customer.getKey(customerId)) == null) {
            return false;
        }
        return true;
	}
	
	public boolean shutdown() {
		Util.System.shutInstance(0);
		return true;
	}

    private void addTemporaryOperation(int id, String key, RMItem value, ClientOperation.Type operationType) {
        LinkedList<ClientOperation> operations = _temporaryOperations.get(id);

        if (operations == null) {
            operations = new LinkedList<ClientOperation>();
        }

        operations.addLast(new ClientOperation(key, value, operationType));
        _temporaryOperations.put(id, operations);
    }
}
