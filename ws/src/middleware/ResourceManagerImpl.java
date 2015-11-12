// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middleware;

import middleware.LockManager.LockManager;
import server.Trace;

import java.net.URL;
import java.util.*;

import javax.jws.WebService;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImpl implements server.ws.ResourceManager {
	
	private static final int PROXY_POOL_SIZE = 4;
	
	private ObjectPool<ResourceManager> flightProxies;
	private ObjectPool<ResourceManager> carProxies;
	private ObjectPool<ResourceManager> roomProxies;
	private ObjectPool<ResourceManager> customerProxies;

	private LockManager _lockManager = new LockManager();
	private TransactionManager _transactionManager = TransactionManager.getInstance(this);
	
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
		long start = System.nanoTime();
		boolean result = true;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

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
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] abort " + id + ": " + (int) ((end - start) / 1e6) + "ms");	
		return result;
	}
	
	public boolean addCars(int id, String location, int numCars, int carPrice) {
		long start = System.nanoTime();
		
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

			_transactionManager.addTransactionRM(id, TransactionManager.CAR);
	
			ResourceManager proxy = carProxies.checkOut();
			result = proxy.addCars(id, location, numCars, carPrice);
			carProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] addcars " + id + ": " + (int) ((end - start) / 1e6) + "ms");	
		return result;
	}

	public boolean addFlight(int id, int flightNumber, int numSeats,
			int flightPrice) {
		long start = System.nanoTime();
		boolean result;
		
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {
			_transactionManager.addTransactionRM(id, TransactionManager.FLIGHT);
	
			ResourceManager proxy = flightProxies.checkOut();
			result = proxy.addFlight(id, flightNumber, numSeats, flightPrice);
			flightProxies.checkIn(proxy);
		}
		long end = System.nanoTime();
		System.out.println("[PERF] addflight " + id + ": " + (int) ((end - start) / 1e6) + "ms");	
		return result;
	}

	public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		long start = System.nanoTime();
		boolean result; 
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

			_transactionManager.addTransactionRM(id, TransactionManager.ROOM);
	
			ResourceManager proxy = roomProxies.checkOut();
			result = proxy.addRooms(id, location, numRooms, roomPrice);
			roomProxies.checkIn(proxy);
		}
		long end = System.nanoTime();
		System.out.println("[PERF] addrooms " + id + ": " + (int) ((end - start) / 1e6) + "ms");	
		return result;
	}

	public boolean commit(int id) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

			boolean[] rmsUsed = _transactionManager.getRMsUsed(id);
	
			if (rmsUsed == null) {
				result = false;
			} else {
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
		
				result = _transactionManager.commit(id, _lockManager);
			}
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] commit " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean deleteCars(int id, String location) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result =  false;
		} else {
			_transactionManager.addTransactionRM(id, TransactionManager.CAR);
	
			ResourceManager proxy = carProxies.checkOut();
			result = proxy.deleteCars(id, location);
			carProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] deletecars " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean deleteCustomer(int id, int customerId) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

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
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] deletecustomer " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean deleteFlight(int id, int flightNumber) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

			_transactionManager.addTransactionRM(id, TransactionManager.FLIGHT);
	
			ResourceManager proxy = flightProxies.checkOut();
			result = proxy.deleteFlight(id, flightNumber);
			flightProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] deleteflight " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean deleteRooms(int id, String location) {
		long start = System.nanoTime();
		boolean result;
		
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

			_transactionManager.addTransactionRM(id, TransactionManager.ROOM);
	
			ResourceManager proxy = roomProxies.checkOut();
			result = proxy.deleteRooms(id, location);
			roomProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] deleterooms " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int newCustomer(int id) {
		long start = System.nanoTime();
		int result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result =  -1;
		} else {
			_transactionManager.addTransactionRM(id, TransactionManager.CUSTOMER);
	
			ResourceManager proxy = customerProxies.checkOut();
			result = proxy.newCustomer(id);
			customerProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] newcustomer " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean newCustomerId(int id, int customerId) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result =  false;
		} else {
			_transactionManager.addTransactionRM(id, TransactionManager.CUSTOMER);
	
			// Just call this at the customer RM. Normally the
			// client won't use it by themselves.
			ResourceManager proxy = customerProxies.checkOut();
			result = proxy.newCustomerId(id, customerId);
			customerProxies.checkIn(proxy);
		}
		long end = System.nanoTime();
		System.out.println("[PERF] newcustomerid " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int queryCars(int id, String location) {
		long start = System.nanoTime();
		int result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = -1;
		} else {

			ResourceManager proxy = carProxies.checkOut();
			result = proxy.queryCars(id, location);
			carProxies.checkIn(proxy);
		}
		long end = System.nanoTime();
		System.out.println("[PERF] querycars " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int queryCarsPrice(int id, String location) {
		long start = System.nanoTime();
		int result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = -1;
		} else {

			ResourceManager proxy = carProxies.checkOut();
			result = proxy.queryCarsPrice(id, location);
			carProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] querycars " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public String queryCustomerInfo(int id, int customerId) {
		long start = System.nanoTime();
		String result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = null;
		} else {

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
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] querycustomerinfo " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int queryFlight(int id, int flightNumber) {
		long start = System.nanoTime();
		int result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = -1;
		} else {

			ResourceManager proxy = flightProxies.checkOut();
			result = proxy.queryFlight(id, flightNumber);
			flightProxies.checkIn(proxy);
		}
		long end = System.nanoTime();
		System.out.println("[PERF] queryflight " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int queryFlightPrice(int id, int flightNumber) {
		long start = System.nanoTime();
		int result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = -1;
		} else {
			ResourceManager proxy = flightProxies.checkOut();
			result = proxy.queryFlightPrice(id, flightNumber);
			flightProxies.checkIn(proxy);
		}
		long end = System.nanoTime();
		System.out.println("[PERF] queryflightprice " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int queryRooms(int id, String location) {
		long start = System.nanoTime();
		int result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = -1;
		} else {

			ResourceManager proxy = roomProxies.checkOut();
			result = proxy.queryRooms(id, location);
			roomProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] queryrooms " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int queryRoomsPrice(int id, String location) {
		long start = System.nanoTime();
		int result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result= -1;
		} else {

			ResourceManager proxy = roomProxies.checkOut();
			result = proxy.queryRoomsPrice(id, location);
			roomProxies.checkIn(proxy);
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] queryRoomsPrice " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		
		return result;
	}

	public boolean reserveCar(int id, int customerId, String location) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

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
		}
		long end = System.nanoTime();
		System.out.println("[PERF] reservecar " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		
		return result;
	}

	public boolean reserveFlight(int id, int customerId, int flightNumber) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

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
		}
		long end = System.nanoTime();
		System.out.println("[PERF] reserveflight " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean reserveItinerary(int id, int customerId,
			Vector flightNumbers, String location, boolean car, boolean room) {
		long start = System.nanoTime();
		boolean result; 
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {

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
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] reserveitinerary " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean reserveRoom(int id, int customerId, String location) {
		long start = System.nanoTime();
		boolean result;
		if (!_transactionManager.hasValidId(id)) {
			Trace.info("ID is not valid.");
			result = false;
		} else {
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
		}
		
		long end = System.nanoTime();
		System.out.println("[PERF] reserveroom " + id + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public int start() {
		long start = System.nanoTime();
		int result = _transactionManager.start();
		long end = System.nanoTime();
		System.out.println("[PERF] start " + result + ": " + (int) ((end - start) / 1e6) + "ms");
		return result;
	}

	public boolean checkCustomerExistence(int id, int customerId) {
		// Again, this method probably won't be called by the client.
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
