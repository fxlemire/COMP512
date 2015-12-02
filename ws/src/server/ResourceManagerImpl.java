// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package server;

import Util.Trace;
import Util.TTL;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.jws.HandlerChain;
import javax.jws.WebService;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import server.ws.ResourceManager;

@WebService(endpointInterface = "server.ws.ResourceManager")
@HandlerChain(file="rm_handler.xml")
public class ResourceManagerImpl extends server.ws.ResourceManagerAbstract {
    private Hashtable<Integer, TTL> _ttls = new Hashtable<>();

    private static final int TIME_TO_LIVE = 300;
    private static final int TXN_TO_ABORT = 0;
    private static final int TXN_TO_CONFIRM = 1;

    private boolean _isSetDie_beforevote = false;
    private boolean _isSetDie_afterdecide = false;

    protected final Object bidon = new Object();
    protected Hashtable<Integer, LinkedList<ClientOperation>> _temporaryOperations = new Hashtable<Integer, LinkedList<ClientOperation>>();
    protected RMHashtable m_itemHT = new RMHashtable();
    protected String thisRmName;
    protected int thisRmIndex;
    
    // For the recovery protocol we may need to ask the 
    // MW for stuff directly, so we need this.
    protected String mwHostname;
    protected int mwPort;
    protected String mwServiceName;
    
    @PostConstruct
    public void init()
    {
    	Context env = null;
		try
		{
			env = (Context) new InitialContext().lookup("java:comp/env");
			thisRmName = (String) env.lookup("name");
			mwHostname = (String) env.lookup("mw_host");
			mwPort = (Integer) env.lookup("mw_port");
			mwServiceName = (String) env.lookup("mw_name");
			
			switch(thisRmName) {
			case "rm_customer":
				thisRmIndex = 0;
				break;
			case "rm_flight":
				thisRmIndex = 1;
				break;
			case "rm_car":
				thisRmIndex = 2;
				break;
			case "rm_room":
				thisRmIndex = 3;
				break;
			}
		} 
		catch (NamingException e)
		{
			throw new RuntimeException(e);
		}
		
		reload();
		checkRecover();
    }
    
    // Load whatever data we had on disk last time
    private void reload() {
    	try {
    		ObjectInputStream ois = new ObjectInputStream(
    									new FileInputStream("data/" + thisRmName + ".master"));
    		m_itemHT = (RMHashtable) ois.readObject();
    		ois.close();
    	} catch (FileNotFoundException e) {
    		// Nothing to do here, assume we're starting fresh
    	} catch (IOException e) {
    		Trace.error("Error when reading master file");
    	} catch (ClassNotFoundException e) {
    		Trace.error("NO");
    	}
    }
    
    private void checkRecover() {
    	HashMap<Integer, Integer> toFix = getIncompleteTxns();
    	for(Map.Entry<Integer, Integer> e: toFix.entrySet()) {
    		int id = e.getKey();
    		int status = e.getValue();
    		if (status == TXN_TO_ABORT) {
    			Trace.info("Recovery: Informing MW that txn " + id + " should be aborted...");
    			// Tell the middleware this should be aborted
    			callToMiddleware(id, status);
    			
    			// Just to make sure, delete the .next file (maybe we crashed in
    			// the middle of writing it)
    			deleteNextVersion(id);
    		} else if (status == TXN_TO_CONFIRM) {
    			Trace.info("Recovery: Asking MW for status of " + id + "...");
    			
    			boolean result = callToMiddleware(id, status);
    			if (result) {
    				Trace.info("Status was commit.");
    				commitIncompleteTxn(id);
    			} else {
    				// Abort. Since we're recovering, we have nothing in memory
    				// for this transaction. Further, if the decision was abort,
    				// there's no way we wrote anything to the master. Therefore,
    				// we just have to delete the next version.
    				Trace.info("Status was abort.");
    				deleteNextVersion(id);
    			}
    		}
    		
    		Trace.info("Recovery completed for txn" + id);
    		
    	}
    }
    
    private boolean callToMiddleware(int id, int status) {
    	// We can't use a proxy here because that would introduce a circular dependency
    	// between the MW and the RM. Therefore, we settle on the horribleness below.
    	String soapTemplate =
    		"<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\" " + 
    				"xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
				"<SOAP-ENV:Header/>" +
				"<S:Body>" +
					"<ns2:%METHOD% xmlns:ns2=\"http://ws.server/\">" +
						"<arg0>%ID%</arg0><arg1>%WHENCE%</arg1>" +
					"</ns2:%METHOD%>" +
				"</S:Body>" +
			"</S:Envelope>";
    	
    	boolean result = true;
    	
    	String soapContent = soapTemplate.replace("%ID%", Integer.toString(id))
    		.replace("%METHOD%", status == TXN_TO_ABORT ? "signalCrash" : "queryTxnResult")
    		.replace("%WHENCE%", Integer.toString(thisRmIndex));
    	byte[] b = soapContent.getBytes();
    	
    	try {
    		// Most of this code is taken from https://goo.gl/pxrCB7
    		
    		URL mw = new URL("http", mwHostname, mwPort, "/" + mwServiceName + "/service");
    		HttpURLConnection huc = (HttpURLConnection) mw.openConnection();
    		
    		// Sending the request...
    		String SOAPAction = "http://" + mwHostname + ":" + mwPort + "/" + mwServiceName + "/service";
			huc.setRequestProperty("Content-Length", String.valueOf(b.length));
			huc.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
			huc.setRequestProperty("SOAPAction", SOAPAction);
			huc.setRequestMethod("POST");
			huc.setDoOutput(true);
			huc.setDoInput(true);
			OutputStream out = huc.getOutputStream();
			//Write the content of the request to the outputstream of the HTTP Connection.
			out.write(b);
			out.close();
			
			// Getting the response...
			InputStreamReader isr = new InputStreamReader(huc.getInputStream());
			BufferedReader in = new BufferedReader(isr);

			String responseString = "", outputString = "";
			while ((responseString = in.readLine()) != null) {
				outputString = outputString + responseString;
			}
			
			if (status == TXN_TO_CONFIRM) {
				int e = outputString.indexOf("</return>");
				int s = outputString.lastIndexOf('>', e);
				
				String retval = outputString.substring(s + 1, e);
				result = Boolean.parseBoolean(retval);
			}
			
			huc.disconnect();
			
    	} catch (Exception e) {
    		Trace.error("I give up.");
    	}
    	
    	return result;
    }
    
    private void commitIncompleteTxn(int id) {
    	// Load the data from .next
    	try {
    		ObjectInputStream ois = new ObjectInputStream(
    									new FileInputStream("data/" + thisRmName + "." + id + ".next"));
    		
    		RMHashtable toWrite = (RMHashtable) ois.readObject();
    		HashSet<Integer> toDelete = (HashSet<Integer>) ois.readObject();

    		ois.close();
    		
    		for(Integer delId: toDelete) {
    			m_itemHT.remove(delId);
    		}
    		
    		for (Enumeration e = toWrite.keys(); e.hasMoreElements();) {
    			Object key = e.nextElement();
    			m_itemHT.put(key, toWrite.get(key));
    		}
    		
    		List<Object> objects = new ArrayList<>();
            objects.add(m_itemHT);

            Trace.persist("data/" + thisRmName + ".master", objects, false);

            deleteNextVersion(id);
    		
    	} catch (FileNotFoundException e) {
    		Trace.error("Data could not be read from .next file.");
    	} catch (IOException e) {
    		Trace.error("Error when reading master file");
    	} catch (ClassNotFoundException e) {
    		Trace.error("Just no.");
    	}
    }
    
    private HashMap<Integer, Integer> getIncompleteTxns() {
    	// Records incomplete transactions, with their results (unknown, true [commit], false [abort])
		HashMap<Integer, Integer> incomplete = new HashMap<Integer, Integer>();
    	BufferedReader logFile;
		try
		{
			// TODO unhardcode
			logFile = new BufferedReader(new FileReader("logs/2PC_" + thisRmName + ".log"));
		}
		catch (FileNotFoundException e)
		{
			Trace.info("No 2PC log found...");
			// File doesn't exist, we don't have to go further.
			return incomplete;
		}
		
		HashSet<Integer> trackAbort = new HashSet<Integer>();
    	String lead = "[2PC][" + thisRmName + "] ";
    	try {
			String line;
			while ((line = logFile.readLine()) != null) {
				line = line.replace(lead, "");
				String[] entry = line.split(" ");
				int id = Integer.parseInt(entry[1]);
				String op = entry[0];

				switch (op) {
					case "operation":
						// If we have just operations and no vote, 
						// then we need to tell the middleware to abort
						incomplete.put(id, TXN_TO_ABORT);
						break;
					case "vote":
						// If we have a vote, then we should check if it was yes or no.
						// If it was no then we're fine - no matter what happened on our
						// side, the txn aborted. If it was yes, then we potentially need
						// to confirm (if we never recieved a result from the mw)
						boolean result = Boolean.parseBoolean(entry[2]);
						if (result)
							incomplete.put(id, TXN_TO_CONFIRM);
						else
							// Potentially we'll need to delete a .next for this, but
							// wait to see if maybe the txn ended normally first.
							trackAbort.add(id);
						break;
					case "end":
						incomplete.remove(id);
						trackAbort.remove(id);
						break;
					default:
						Trace.info("Unknown log entry " + line);
						break;
				}
			}

			// Get rid of the old log file, we don't need it anymore
			logFile.close();
			Files.delete(Paths.get("logs/2PC_" + thisRmName + ".log"));
			
			// This set contains any txn id that crashed before receiving a confirmation,
			// but after successfully sending an abort. Therefore we know that the MW
			// knows that the txn needed to abort.
			for(Integer id: trackAbort) {
				deleteNextVersion(id);
			}
			
		} catch (Exception e) {
			// do nothing
		}
		
		return incomplete;
    }
    
    private void deleteNextVersion(int id) {
    	try {
			Files.deleteIfExists(Paths.get("data/" + thisRmName + "." + id + ".next"));
		} catch (IOException e) {
			//Technically this isn't really problematic.
			e.printStackTrace();
		}
    }
    
    // Basic operations on RMItem //

    /**
     * Read a data item. If the requested item has been locally updated, read this version.
     */
    private RMItem readData(int id, String key) {
        synchronized(bidon) {
            restartTTL(id);

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
        if (_isSetDie_afterdecide) {
            System.exit(-1);
        }

        synchronized(bidon) {
            killTTL(id);
            _temporaryOperations.remove(id);
        }
        
        deleteNextVersion(id);

        Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " end " + id, true);
		
		return true;
    }

    @Override
    public boolean prepare(int id) {
        if (_isSetDie_beforevote) {
            selfDestruct();
        }

    	RMHashtable next_write = new RMHashtable();
    	HashSet<String> next_remove = new HashSet<String>();
    	
    	boolean result = true;
    	
        LinkedList<ClientOperation> operations = _temporaryOperations.get(id);

        // If there are no operations when we are asked to prepare, something went wrong.
        // Indeed, we shouldn't be asked to prepare if only read operations were performed,
        // so we should tell the MW that our data was somehow lost.
        if (operations == null) {
            cancelTTL(id);
            return false;
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

        if (result) cancelTTL(id);
    	return result;
    }

    // Commit a transaction.
    public boolean commit(int id) {
        if (_isSetDie_afterdecide) {
            System.exit(-1);
        }

        synchronized(bidon) {
            killTTL(id);
            LinkedList<ClientOperation> operations = _temporaryOperations.get(id);

            if (operations == null) {
                Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " end " + id, true);
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

            Trace.persist("data/" + thisRmName + ".master", objects, false);

            deleteNextVersion(id);
        }

        Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " end " + id, true);
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
                synchronized(bidon) {
                    addTemporaryOperation(id, item.getKey(), item, ClientOperation.Type.WRITE);
                }
                
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

    @Override
    public boolean isStillActive(int id) {
        return restartTTL(id);
    }

    public boolean setDie(String which, String when) {
        boolean isSetToDie = true;

        switch (when) {
        case "beforevote":
            _isSetDie_beforevote = true;
            break;
        case "afterdecide":
            _isSetDie_afterdecide = true;
            break;
        default:
            Trace.info("Invalid moment for a setDie");
            isSetToDie = false;
        }

        return isSetToDie;
    }

	public boolean resetDie() {
		_isSetDie_beforevote = false;
		_isSetDie_afterdecide = false;
		return true;
	}

    private void addTemporaryOperation(int id, String key, RMItem value, ClientOperation.Type operationType) {
        TTL ttl = _ttls.get(id);
        if (ttl == null) {
            ttl = new TTL(id, this, TIME_TO_LIVE);
            _ttls.put(id, ttl);
        } else {
            ttl.restart();
        }

        LinkedList<ClientOperation> operations = _temporaryOperations.get(id);

        if (operations == null) {
            operations = new LinkedList<ClientOperation>();
        }

        operations.addLast(new ClientOperation(key, value, operationType));
        _temporaryOperations.put(id, operations);
        
        // This log is so that we know if we lost data during a crash.
        Trace.persist("logs/2PC_" + thisRmName + ".log", "[2PC][" + thisRmName + "]" + " operation " + id, true);
    }

    private boolean killTTL(int id) {
        boolean isCancelled = cancelTTL(id);
        if (isCancelled) {
            _ttls.remove(id);
        }
        return isCancelled;
    }

    private boolean cancelTTL(int id) {
        boolean isCancelled = false;
        TTL ttl = _ttls.get(id);
        if (ttl != null) {
            ttl.kill();
            isCancelled = true;
        }
        return isCancelled;
    }

    private boolean restartTTL(int id) {
        boolean isRestarted = false;
        TTL ttl = _ttls.get(id);
        if (ttl != null) {
            ttl.restart();
            isRestarted = true;
        }
        return isRestarted;
    }
}
