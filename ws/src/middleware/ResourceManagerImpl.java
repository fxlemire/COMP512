// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middleware;

import Util.Trace;
import middleware.LockManager.LockManager;
import server.*;

import java.net.URL;
import java.util.*;

import javax.jws.HandlerChain;
import javax.jws.WebService;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

@WebService(endpointInterface = "server.ws.ResourceManager")
@HandlerChain(file="mw_handler.xml")
public class ResourceManagerImpl extends server.ws.ResourceManagerAbstract {
	
	private static final int PROXY_POOL_SIZE = 4;
	
	private ObjectPool<ResourceManager> flightProxies;
	private ObjectPool<ResourceManager> carProxies;
	private ObjectPool<ResourceManager> roomProxies;
	private ObjectPool<ResourceManager> customerProxies;

	private LockManager _lockManager = new LockManager();
	private TransactionManager _transactionManager = TransactionManager.getInstance();
	
	private ResourceManager getProxyFor(String rm, Context env) 
	{
		try
		{
			String serviceHost = (String) env.lookup("rmIp_" + rm);
		    Integer servicePort = (Integer) env.lookup("rmPort_" + rm);
		    URL wsdlLocation = new URL("http", serviceHost, servicePort, 
		    		"/rm_" + rm + "/service?wsdl");
		    return new ResourceManagerImplService(wsdlLocation).getResourceManagerImplPort();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private ObjectPool<ResourceManager> buildPoolFor(String rm, Context env)
	{
		LinkedList<ResourceManager> proxies = new LinkedList<ResourceManager>();
		for (int i = 0 ; i < ResourceManagerImpl.PROXY_POOL_SIZE ; i++)
		{
			proxies.add(this.getProxyFor(rm, env));
		}
		
		return new ObjectPool<ResourceManager>(proxies);
	}
	
	public ResourceManagerImpl()
	{
		Context env = null;
		try
		{
			env = (Context) new InitialContext().lookup("java:comp/env");
		} 
		catch (NamingException e)
		{
			throw new RuntimeException(e);
		}
	    flightProxies = buildPoolFor("flight", env);
	    roomProxies = buildPoolFor("room", env);
	    carProxies = buildPoolFor("car", env);
	    customerProxies = buildPoolFor("customer", env);
	}

	public boolean abort(int id) {
		boolean result = true;
		boolean[] rmsUsed = _transactionManager.getRMsUsed(id);

		if (rmsUsed == null) {
			result = false;
		} else {

			ResourceManager proxy;
			if (rmsUsed[TransactionManager.CUSTOMER]) {
				proxy = customerProxies.checkOut();
				proxy.abort(id);
				customerProxies.checkIn(proxy);
			}
	
			if (rmsUsed[TransactionManager.FLIGHT]) {
				proxy = flightProxies.checkOut();
				proxy.abort(id);
				flightProxies.checkIn(proxy);
			}
	
			if (rmsUsed[TransactionManager.CAR]) {
				proxy = carProxies.checkOut();
				proxy.abort(id);
				carProxies.checkIn(proxy);
			}
	
			if (rmsUsed[TransactionManager.ROOM]) {
				proxy = roomProxies.checkOut();
				proxy.abort(id);
				roomProxies.checkIn(proxy);
			}
			
			result = _transactionManager.abort(id, _lockManager);
		}
		
		
		return result;
	}
	
	public boolean addCars(int id, String location, int numCars, int carPrice) {
		
		boolean result;
		try {
			_lockManager.Lock(id, Car.getKey(location), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			result = false;
		}
		_transactionManager.addTransactionRM(id, TransactionManager.CAR);

		ResourceManager proxy = carProxies.checkOut();
		result = proxy.addCars(id, location, numCars, carPrice);
		carProxies.checkIn(proxy);
		
		return result;
	}

	public boolean addFlight(int id, int flightNumber, int numSeats,
			int flightPrice) {
		boolean result;
		
		try {
			_lockManager.Lock(id, Flight.getKey(flightNumber), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		_transactionManager.addTransactionRM(id, TransactionManager.FLIGHT);

		ResourceManager proxy = flightProxies.checkOut();
		result = proxy.addFlight(id, flightNumber, numSeats, flightPrice);
		flightProxies.checkIn(proxy);
	
		return result;
	}

	public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		boolean result; 
		try {
			_lockManager.Lock(id, Room.getKey(location), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		_transactionManager.addTransactionRM(id, TransactionManager.ROOM);

		ResourceManager proxy = roomProxies.checkOut();
		result = proxy.addRooms(id, location, numRooms, roomPrice);
		roomProxies.checkIn(proxy);
	
		return result;
	}

	@Override
	public boolean crash(String rm) {
		boolean result;

		String rmLC = rm.toLowerCase();

		switch (rmLC) {
		case "customer": {
			ResourceManager proxy = customerProxies.checkOut();
			result = proxy.selfDestruct();
			customerProxies.checkIn(proxy);
			break;
		}
		case "flight": {
			ResourceManager proxy = flightProxies.checkOut();
			result = proxy.selfDestruct();
			flightProxies.checkIn(proxy);
			break;
		}
		case "car": {
			ResourceManager proxy = carProxies.checkOut();
			result = proxy.selfDestruct();
			carProxies.checkIn(proxy);
			break;
		}
		case "room": {
			ResourceManager proxy = roomProxies.checkOut();
			result = proxy.selfDestruct();
			roomProxies.checkIn(proxy);
			break;
		}
		case "mw": {
			result = selfDestruct();
			break;
		}
		default:
			System.out.println("Error: No such RM: " + rm);
			result = false;
		}

		return result;
	}

	public boolean commit(int id) {
		Trace.info("Started 2PC for " + id);
		
		boolean result;
		boolean[] rmsUsed = _transactionManager.getRMsUsed(id);

		boolean decision = vote(id, rmsUsed);
		Trace.info("Decision for " + id + ": " + decision);
		
		if (!decision) {
			result = abort(id);
		} else {
			commitForReal(id, rmsUsed);
			result = _transactionManager.commit(id, _lockManager);
		}
		
		Trace.info("Finished 2PC for " + id);
		return result;
	}
	
	private void commitForReal(int id, boolean[] rmsUsed) {
		
		ResourceManager proxy;
		if (rmsUsed[TransactionManager.CUSTOMER]) {
			proxy = customerProxies.checkOut();
			proxy.commit(id);
			customerProxies.checkIn(proxy);
		}

		if (rmsUsed[TransactionManager.FLIGHT]) {
			proxy = flightProxies.checkOut();
			proxy.commit(id);
			flightProxies.checkIn(proxy);
		}

		if (rmsUsed[TransactionManager.CAR]) {
			proxy = carProxies.checkOut();
			proxy.commit(id);
			carProxies.checkIn(proxy);
		}

		if (rmsUsed[TransactionManager.ROOM]) {
			proxy = roomProxies.checkOut();
			proxy.commit(id);
			roomProxies.checkIn(proxy);
		}
	}
	
	private boolean vote(int id, boolean[] rmsUsed) {
		boolean result = true;
		
		ResourceManager proxy;
		if (rmsUsed[TransactionManager.CUSTOMER]) {
			proxy = customerProxies.checkOut();
			result = result && proxy.prepare(id);
			customerProxies.checkIn(proxy);
		}

		if (result && rmsUsed[TransactionManager.FLIGHT]) {
			proxy = flightProxies.checkOut();
			result = result && proxy.prepare(id);
			flightProxies.checkIn(proxy);
		}

		if (result && rmsUsed[TransactionManager.CAR]) {
			proxy = carProxies.checkOut();
			result = result && proxy.prepare(id);
			carProxies.checkIn(proxy);
		}

		if (result && rmsUsed[TransactionManager.ROOM]) {
			proxy = roomProxies.checkOut();
			result = result && proxy.prepare(id);
			roomProxies.checkIn(proxy);
		}
		
		return result;
	}

	public boolean deleteCars(int id, String location) {
		boolean result;
		try {
			_lockManager.Lock(id, Car.getKey(location), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		_transactionManager.addTransactionRM(id, TransactionManager.CAR);

		ResourceManager proxy = carProxies.checkOut();
		result = proxy.deleteCars(id, location);
		carProxies.checkIn(proxy);

		return result;
	}

	public boolean deleteCustomer(int id, int customerId) {
		boolean result;
		try {
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}

		ResourceManager proxy = customerProxies.checkOut();
		if (!proxy.checkCustomerExistence(id, customerId))
		{
			customerProxies.checkIn(proxy);
			result =  false;
		} else {
			_transactionManager.addTransactionRM(id, TransactionManager.CUSTOMER);
			_transactionManager.addTransactionRM(id, TransactionManager.FLIGHT);
			_transactionManager.addTransactionRM(id, TransactionManager.CAR);
			_transactionManager.addTransactionRM(id, TransactionManager.ROOM);
	
			proxy.deleteCustomer(id, customerId);
			customerProxies.checkIn(proxy);
			
			proxy = carProxies.checkOut();
			proxy.deleteCustomer(id, customerId);
			carProxies.checkIn(proxy);
			
			proxy = flightProxies.checkOut();
			proxy.deleteCustomer(id, customerId);
			flightProxies.checkIn(proxy);
			
			proxy = roomProxies.checkOut();
			proxy.deleteCustomer(id, customerId);
			roomProxies.checkIn(proxy);
			
			result = true;
		}
		
		
		return result;
	}

	public boolean deleteFlight(int id, int flightNumber) {
		boolean result;
		try {
			_lockManager.Lock(id, Flight.getKey(flightNumber), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		_transactionManager.addTransactionRM(id, TransactionManager.FLIGHT);

		ResourceManager proxy = flightProxies.checkOut();
		result = proxy.deleteFlight(id, flightNumber);
		flightProxies.checkIn(proxy);
		
		return result;
	}

	public boolean deleteRooms(int id, String location) {
		boolean result;
		
		try {
			_lockManager.Lock(id, Room.getKey(location), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		_transactionManager.addTransactionRM(id, TransactionManager.ROOM);

		ResourceManager proxy = roomProxies.checkOut();
		result = proxy.deleteRooms(id, location);
		roomProxies.checkIn(proxy);
		
		return result;
	}

	public int newCustomer(int id) {
		int result;
		_transactionManager.addTransactionRM(id, TransactionManager.CUSTOMER);

		ResourceManager proxy = customerProxies.checkOut();
		result = proxy.newCustomer(id);
		customerProxies.checkIn(proxy);
		
		
		return result;
	}

	public boolean newCustomerId(int id, int customerId) {
		boolean result;
		try {
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		_transactionManager.addTransactionRM(id, TransactionManager.CUSTOMER);

		// Just call this at the customer RM. Normally the
		// client won't use it by themselves.
		ResourceManager proxy = customerProxies.checkOut();
		result = proxy.newCustomerId(id, customerId);
		customerProxies.checkIn(proxy);
		
		return result;
	}

	public int queryCars(int id, String location) {
		int result;
		try {
			_lockManager.Lock(id, Car.getKey(location), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return -1;
		}
		ResourceManager proxy = carProxies.checkOut();
		result = proxy.queryCars(id, location);
		carProxies.checkIn(proxy);
		
		return result;
	}

	public int queryCarsPrice(int id, String location) {
		int result;
		try {
			_lockManager.Lock(id, Car.getKey(location), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return -1;
		}
		ResourceManager proxy = carProxies.checkOut();
		result = proxy.queryCarsPrice(id, location);
		carProxies.checkIn(proxy);
		
		return result;
	}

	public String queryCustomerInfo(int id, int customerId) {
		String result;
		try {
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return null;
		}
		ResourceManager customerProxy = customerProxies.checkOut();
		boolean exists = customerProxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(customerProxy);
		
		if (!exists)
		{
			result = "The customer ID does not exist.";
		}
		else {
			ArrayList<String> finalBill = new ArrayList<String>();
	
			ResourceManager flightProxy = flightProxies.checkOut();
			finalBill = updateBill(finalBill, flightProxy, id, customerId);
			flightProxies.checkIn(flightProxy);
	
			ResourceManager carProxy = carProxies.checkOut();
			finalBill = updateBill(finalBill, carProxy, id, customerId);
			carProxies.checkIn(carProxy);
	
			ResourceManager roomProxy = roomProxies.checkOut();
			finalBill = updateBill(finalBill, roomProxy, id, customerId);
			roomProxies.checkIn(roomProxy);
	
			result = "Customer has not made any reservation yet.";
	
			if (finalBill.size() != 0) {
				finalBill = addBillTotal(finalBill);
	
				finalBill.add(finalBill.size(), "}");
				
				StringBuilder sb = new StringBuilder();
				for(String s: finalBill)
				{
					sb.append(s);
					sb.append("\n");
				}
				
				result = sb.toString();
			}
		}
		
		
		return result;
	}

	public int queryFlight(int id, int flightNumber) {
		int result;
		try {
			_lockManager.Lock(id, Flight.getKey(flightNumber), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return -1;
		}
		ResourceManager proxy = flightProxies.checkOut();
		result = proxy.queryFlight(id, flightNumber);
		flightProxies.checkIn(proxy);
		
		return result;
	}

	public int queryFlightPrice(int id, int flightNumber) {
		int result;
		try {
			_lockManager.Lock(id, Flight.getKey(flightNumber), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return -1;
		}
		ResourceManager proxy = flightProxies.checkOut();
		result = proxy.queryFlightPrice(id, flightNumber);
		flightProxies.checkIn(proxy);
		
		return result;
	}

	public int queryRooms(int id, String location) {
		int result;
		try {
			_lockManager.Lock(id, Room.getKey(location), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return -1;
		}
		ResourceManager proxy = roomProxies.checkOut();
		result = proxy.queryRooms(id, location);
		roomProxies.checkIn(proxy);
		
		return result;
	}

	public int queryRoomsPrice(int id, String location) {
		int result;
		try {
			_lockManager.Lock(id, Room.getKey(location), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return -1;
		}
		ResourceManager proxy = roomProxies.checkOut();
		result = proxy.queryRoomsPrice(id, location);
		roomProxies.checkIn(proxy);
		

		return result;
	}

	public boolean reserveCar(int id, int customerId, String location) {
		boolean result;
		try {
			_lockManager.Lock(id, Car.getKey(location), LockManager.WRITE);
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		ResourceManager proxy = customerProxies.checkOut();
		boolean exists = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		
		if (exists)
		{
			_transactionManager.addTransactionRM(id, TransactionManager.CAR);
			proxy = carProxies.checkOut();
			proxy.newCustomerId(id, customerId);
			result = proxy.reserveCar(id, customerId, location);
			carProxies.checkIn(proxy);
		}
		else
			result = false;
		

		return result;
	}

	public boolean reserveFlight(int id, int customerId, int flightNumber) {
		
		boolean result;
		try {
			_lockManager.Lock(id, Flight.getKey(flightNumber), LockManager.WRITE);
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		ResourceManager proxy = customerProxies.checkOut();
		boolean exists = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		
		if (exists)
		{
			_transactionManager.addTransactionRM(id, TransactionManager.FLIGHT);
			proxy = flightProxies.checkOut();
			proxy.newCustomerId(id, customerId);
			result = proxy.reserveFlight(id, customerId, flightNumber);
			flightProxies.checkIn(proxy);
			
		} else result = false;
		
		return result;
	}

	@Override
	public boolean reserveItinerary(int id, int customerId,
			Vector flightNumbers, String location, boolean car, boolean room) {
		boolean result; 
		try {
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		// If customer doesn't exist, bail now.
		ResourceManager customerProxy = customerProxies.checkOut();
		boolean exists = customerProxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(customerProxy);
		
		if (!exists)
		{
			result = false;
		}
		else
		{
			try {
				_lockManager.Lock(id, Car.getKey(location), LockManager.WRITE);
				_lockManager.Lock(id, Room.getKey(location), LockManager.WRITE);
				for (int i = 0; i < flightNumbers.size(); ++i) {
					_lockManager.Lock(id, Flight.getKey(Integer.parseInt((String) flightNumbers.elementAt(i))), LockManager.WRITE);
				}
			} catch (Exception e) {
				abort(id);
				return false;
			}

			// Have a simple management of proxies here.
			_transactionManager.addTransactionRM(id, TransactionManager.FLIGHT);
			_transactionManager.addTransactionRM(id, TransactionManager.CAR);
			_transactionManager.addTransactionRM(id, TransactionManager.ROOM);
			ResourceManager flightProxy = flightProxies.checkOut();
			ResourceManager carProxy = carProxies.checkOut();
			ResourceManager roomProxy = roomProxies.checkOut();
			try
			{			
				// Precheck availability of flights and car/room.
				// We're assuming that nothing will change in-between.
				// This is okay of the purposes of the first assignment.
				
				for (int i = 0 ; i < flightNumbers.size() ; i++) {
					if (flightProxy.queryFlight(id, Integer.parseInt((String) flightNumbers.elementAt(i))) == 0)
					{
						throw new RuntimeException();
					}
				}
				
				if ((car && carProxy.queryCars(id, location) == 0) ||
						(room && roomProxy.queryRooms(id, location) == 0))
				{
					throw new RuntimeException();
				}
				
				// Create (if necessary) the customers
				flightProxy.newCustomerId(id, customerId);
				carProxy.newCustomerId(id, customerId);
				roomProxy.newCustomerId(id, customerId);
				
				// Proceed with the booking. If any of the reservations fail, just bail out.
				for (int i = 0 ; i < flightNumbers.size() ; i++) {
					if (!flightProxy.reserveFlight(id, customerId, 
							Integer.parseInt((String) flightNumbers.elementAt(i))))
					{
						throw new RuntimeException();
					}
				}
				
				if (car && !carProxy.reserveCar(id, customerId, location))
				{
					throw new RuntimeException();
				}
				
				if (room && !roomProxy.reserveRoom(id, customerId, location))
				{
					throw new RuntimeException();
				}
				
				result = true;
			}
			catch (RuntimeException e)
			{
				result = false;
			}
			finally
			{
				flightProxies.checkIn(flightProxy);
				carProxies.checkIn(carProxy);
				roomProxies.checkIn(roomProxy);
			}
		}

		return result;
	}

	public boolean reserveRoom(int id, int customerId, String location) {
		boolean result;
		try {
			_lockManager.Lock(id, Room.getKey(location), LockManager.WRITE);
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.WRITE);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		ResourceManager proxy = customerProxies.checkOut();
		boolean exists = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		
		if (exists)
		{
			_transactionManager.addTransactionRM(id, TransactionManager.ROOM);
			proxy = roomProxies.checkOut();
			proxy.newCustomerId(id, customerId);
			result = proxy.reserveRoom(id, customerId, location);
			roomProxies.checkIn(proxy);

		} else result = false;

		return result;
	}

	@Override
	public int start() {
		int result = _transactionManager.start(this);
		return result;
	}

	public boolean checkCustomerExistence(int id, int customerId) {
		// Again, this method probably won't be called by the client.
		try {
			_lockManager.Lock(id, Customer.getKey(customerId), LockManager.READ);
		} catch (Exception e) {
			abort(id);
			return false;
		}
		ResourceManager proxy = customerProxies.checkOut();
		boolean result = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		return result;
	}
	
	public boolean shutdown() {
		ResourceManager proxy = customerProxies.checkOut();
		proxy.shutdown();
		customerProxies.checkIn(proxy);
		
		proxy = carProxies.checkOut();
		proxy.shutdown();
		carProxies.checkIn(proxy);
		
		proxy = roomProxies.checkOut();
		proxy.shutdown();
		roomProxies.checkIn(proxy);
		
		proxy = flightProxies.checkOut();
		proxy.shutdown();
		flightProxies.checkIn(proxy);
		
		Timer end = new Timer();
		end.schedule(new TimerTask() {

			@Override
			public void run() {
				System.exit(0);
			} 
		}, 1000);
		
		return true;
	}

	private ArrayList<String> updateBill(ArrayList<String> finalBill, ResourceManager proxy, int id, int customerId) {
		String bill = proxy.queryCustomerInfo(id, customerId);

		if (!(bill.equals(""))) {
			finalBill = addToBill(finalBill, bill);
		}
		
		return finalBill;
	}

	private ArrayList<String> addToBill(ArrayList<String> finalBill, String bill) {
		ArrayList<String> tempBill = new ArrayList<>(Arrays.asList(bill.split("\n")));

		if (finalBill.size() == 0) {
			finalBill.add(0, tempBill.get(0));
		}

		tempBill.remove(0);
		tempBill.remove(tempBill.size() - 1);
		finalBill.addAll(tempBill);

		return finalBill;
	}

	private ArrayList<String> addBillTotal(ArrayList<String> bill) {
		int total = 0;

		for (int i = 1; i < bill.size(); ++i) {
			total += Integer.parseInt(bill.get(i).split("\\$")[1]);
		}

		bill.add(bill.size(), "Total: $" + total);

		return bill;
	}
}
