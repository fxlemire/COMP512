// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middleware;

import Util.Trace;
import middleware.LockManager.LockManager;
import server.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
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

@WebService(endpointInterface = "server.ws.ResourceManager")
@HandlerChain(file="mw_handler.xml")
public class ResourceManagerImpl extends server.ws.ResourceManagerAbstract {
	
	private static final Object UNKNOWN_TXN_RESULT = new Object();
	
	private static final int PROXY_POOL_SIZE = 4;
	
	private ObjectPool<ResourceManager> flightProxies;
	private ObjectPool<ResourceManager> carProxies;
	private ObjectPool<ResourceManager> roomProxies;
	private ObjectPool<ResourceManager> customerProxies;

	private LockManager _lockManager = new LockManager();
	private TransactionManager _transactionManager = TransactionManager.getInstance();

	private boolean _isSetDie_used = false;
	private boolean _isSetDie_beforevote = false;
	private boolean _isSetDie_aftervote_some = false;
	private boolean _isSetDie_beforedecide = false;
	private boolean _isSetDie_afterdecide_none = false;
	private boolean _isSetDie_afterdecide_some = false;
	private boolean _isSetDie_afterdecide_all = false;
	private String _isSetDie_rm;
	private boolean _isSetDie_rm_aftervote = false;
	
	@PostConstruct
	public void init()
	{
		TransactionManager.getInstance().setMiddleware(this);

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
	    
	    checkRecover();
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
	
	private void checkRecover()
	{
		BufferedReader logFile;
		try
		{
			// TODO unhardcode
			logFile = new BufferedReader(new FileReader("logs/2PC_mw.log"));
		}
		catch (FileNotFoundException e)
		{
			Trace.info("No 2PC log found...");
			// File doesn't exist, we don't have to go further.
			return;
		}
		
		// Records incomplete transactions, with their results (unknown, true [commit], false [abort])
		HashMap<Integer, Object> incomplete = new HashMap<Integer, Object>();
		
		// Records transactions that haven't made it to the 2PC
		HashSet<Integer> noPC = new HashSet<>(); 
		
		try {
			String line;
			while ((line = logFile.readLine()) != null) {
				line = line.replace("[2PC][mw] ", "");
				String[] entry = line.split(" ");
				int id = Integer.parseInt(entry[1]);
				String op = entry[0];

				switch (op) {
				case "participate":
					noPC.add(id);
					break;
				case "start":
					incomplete.put(id, UNKNOWN_TXN_RESULT);
					noPC.remove(id); // We got to 2PC so don't track those guys anymore
					break;
				case "result":
					boolean result = Boolean.parseBoolean(entry[2]);
					incomplete.put(id, result);
					break;
				case "end":
					incomplete.remove(id);
					break;
				default:
					Trace.warn("Unknown log entry " + line);
					break;
				}
			}

			// Get rid of the old log file, we don't need it anymore
			logFile.close();
			Files.delete(Paths.get("logs/2PC_mw.log"));
		} catch (Exception e) {
			// do nothing
		}
		
		// Note: here we potentially tell some RMs about things they have no idea of
		// (i.e. tell them to abort a transaction they don't participate in). That's okay,
		// they know how to deal with that.
		final boolean[] allRMs = new boolean[] {true, true, true, true};
		
		//Go through those that didn't get to 2PC and abort them.
		for(Integer txn: noPC) {
			Trace.info("Found transaction " + txn + " without a commit/abort. Aborting...");
			abortForSpecific(txn, allRMs);
		}
		
		//Go through those that got as far as starting the 2PC.
		for(Map.Entry<Integer, Object> e: incomplete.entrySet()) {
			int id = e.getKey();
			if (e.getValue() == UNKNOWN_TXN_RESULT) {
				// If we don't know what happened, then we don't really
				// have a choice but to abort.
				Trace.info("Found transaction " + id + " without a vote result. Aborting...");
				abortForSpecific(id, allRMs);
			} else {
				// Otherwise, re-send the decision.
				boolean result = (Boolean) e.getValue();
				Trace.info("Found incomplete transaction " + id + ". Resending result of " + result);
				if (result)
					commitForSpecific(id, allRMs);
				else
					abortForSpecific(id, allRMs);
			}
		}
		
		// At this point, none of the RMs should have any pending transactions, so it's okay to
		// start fresh
	}

	public boolean abort(int id) {
		boolean result = true;
		boolean[] rmsUsed = _transactionManager.getRMsUsed(id);

		if (rmsUsed == null) {
			result = false;
		} else {
			abortForSpecific(id, rmsUsed);
			
			result = _transactionManager.abort(id, _lockManager);
		}

		Trace.persist("logs/2PC_mw.log", "[2PC][mw] end " + id, true);

		return result;
	}
	
	private void abortForSpecific(int id, boolean[] rmsUsed) {
		processRmsUsed(rmsUsed, new ProxyRunnable() {
			@Override
			public void run(ResourceManager proxy) {
				proxy.abort(id);
				if (_isSetDie_afterdecide_some) {
					selfDestruct();
				}
			}
		});
	}
	
	@Override
	public boolean crash(String rm) {
		final boolean[] result = {true};

		if (rm.equals("mw")) {
			result[0] = selfDestruct();
		} else {
			executeOnRm(rm, new ProxyRunnable() {
				@Override
				public void run(ResourceManager proxy) {
					result[0] = result[0] && proxy.selfDestruct();
				}
			});
		}

		return result[0];
	}

	public boolean commit(int id) {
		Trace.persist("logs/2PC_mw.log", "[2PC][mw] start " + id, true);
		
		boolean result;
		boolean[] rmsUsed = _transactionManager.getRMsUsed(id);

		boolean decision = vote(id, rmsUsed);
		Trace.persist("logs/2PC_mw.log", "[2PC][mw] result " + id + " " + decision, true);

		if (_isSetDie_afterdecide_none) {
			selfDestruct();
		}
		
		if (!decision) {
			result = abort(id);
		} else {
			commitForSpecific(id, rmsUsed);
			result = _transactionManager.commit(id, _lockManager);
		}

		Trace.persist("logs/2PC_mw.log", "[2PC][mw] end " + id, true);

		if (_isSetDie_afterdecide_all) {
			selfDestruct();
		}

		return result;
	}
	
	private void commitForSpecific(int id, boolean[] rmsUsed) {
		processRmsUsed(rmsUsed, new ProxyRunnable() {
			@Override
			public void run(ResourceManager proxy) {
				proxy.commit(id);
				if (_isSetDie_afterdecide_some) {
					selfDestruct();
				}
			}
		});
	}
	
	private boolean vote(int id, boolean[] rmsUsed) {
		if (_isSetDie_beforevote) {
			selfDestruct();
		}

		final boolean[] result = {true};

		ProxyRunnable runnable = new ProxyRunnable() {
			@Override
			public void run(ResourceManager proxy) {
				result[0] = result[0] && proxy.prepare(id);
				if (_isSetDie_aftervote_some) {
					selfDestruct();
				}
			}
		};

		processRmsUsed(rmsUsed, runnable);

		if (_isSetDie_rm_aftervote) {
			executeOnRm(_isSetDie_rm, new ProxyRunnable() {
				@Override
				public void run(ResourceManager proxy) {
					proxy.selfDestruct();
				}
			});
		}

		if (_isSetDie_beforedecide) {
			selfDestruct();
		}
		
		return result[0];
	}
	
	public boolean queryTxnResult(int id, int whence) {
		Trace.info("Answering query for " +
				"transaction " + id + " result...");
		return _transactionManager.getTransactionResult(id);
	}
	
	public void signalCrash(int id, int whence) {
		Trace.info("Crash occured for transaction " + id + "...");
		// We'll receive this if a RM crashed while the transaction is on-going.
		// (i.e. no commit/abort from client was called). Data was lost, abort.
		
		// We don't want to send the abort to the RM that just crashed, because it's
		// not ready to receive messages yet. Anyway, we don't even need to send it the abort.
		_transactionManager.removeTransactionRM(id, whence);
		abort(id);
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

	public void sendHeartBeat(int id, boolean[] rmsUsed) {
		processRmsUsed(rmsUsed, new ProxyRunnable() {
			@Override
			public void run(ResourceManager proxy) {
				proxy.isStillActive(id);
			}
		});
	}

	public boolean setDie(String server, String when) {
		boolean isSetToDie = true;

		if (!_isSetDie_used) {
			_isSetDie_used = true;

			if (server.equals("mw")) {
				switch (when) {
					case "beforevote":
						_isSetDie_beforevote = true;
						break;
					case "aftervote_some":
						_isSetDie_aftervote_some = true;
						break;
					case "beforedecide":
						_isSetDie_beforedecide = true;
						break;
					case "afterdecide_none":
						_isSetDie_afterdecide_none = true;
						break;
					case "afterdecide_some":
						_isSetDie_afterdecide_some = true;
						break;
					case "afterdecide_all":
						_isSetDie_afterdecide_all = true;
						break;
					default:
						Trace.info("Invalid moment for a setDie");
						isSetToDie = false;
				}
			} else {
				if (when.equals("aftervote")) {
					_isSetDie_rm = server;
					_isSetDie_rm_aftervote = true;
				} else {
					final boolean[] result = {true};
					executeOnRm(server, new ProxyRunnable() {
						@Override
						public void run(ResourceManager proxy) {
							result[0] = result[0] && proxy.setDie(server, when);
						}
					});
					isSetToDie = result[0];
				}
			}
		} else {
			Trace.info("Cannot use setDie more than once");
			isSetToDie = false;
		}

		return isSetToDie;
	}

	public boolean resetDie() {
		boolean[] result = {true};

		if (_isSetDie_used) {
			_isSetDie_used = false;
			_isSetDie_beforevote = false;
			_isSetDie_aftervote_some = false;
			_isSetDie_beforedecide = false;
			_isSetDie_afterdecide_none = false;
			_isSetDie_afterdecide_some = false;
			_isSetDie_afterdecide_all = false;
			_isSetDie_rm = null;
			_isSetDie_rm_aftervote = false;

			boolean[] allRms = {true, true, true, true};

			processRmsUsed(allRms, new ProxyRunnable() {
				@Override
				public void run(ResourceManager proxy) {
					result[0] = result[0] && proxy.resetDie();
				}
			});
		}

		return result[0];
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

	private void processRmsUsed(boolean[] rmsUsed, ProxyRunnable runnable) {
		ResourceManager proxy;
		if (rmsUsed[TransactionManager.CUSTOMER]) {
			proxy = customerProxies.checkOut();
			runnable.run(proxy);
			customerProxies.checkIn(proxy);
		}

		if (rmsUsed[TransactionManager.FLIGHT]) {
			proxy = flightProxies.checkOut();
			runnable.run(proxy);
			flightProxies.checkIn(proxy);
		}

		if (rmsUsed[TransactionManager.CAR]) {
			proxy = carProxies.checkOut();
			runnable.run(proxy);
			carProxies.checkIn(proxy);
		}

		if (rmsUsed[TransactionManager.ROOM]) {
			proxy = roomProxies.checkOut();
			runnable.run(proxy);
			roomProxies.checkIn(proxy);
		}
	}

	private boolean executeOnRm(String rm, ProxyRunnable runnable) {
		rm = rm.toLowerCase();

		switch (rm) {
		case "customer": {
			ResourceManager proxy = customerProxies.checkOut();
			runnable.run(proxy);
			customerProxies.checkIn(proxy);
			break;
		}
		case "flight": {
			ResourceManager proxy = flightProxies.checkOut();
			runnable.run(proxy);
			flightProxies.checkIn(proxy);
			break;
		}
		case "car": {
			ResourceManager proxy = carProxies.checkOut();
			runnable.run(proxy);
			carProxies.checkIn(proxy);
			break;
		}
		case "room": {
			ResourceManager proxy = roomProxies.checkOut();
			runnable.run(proxy);
			roomProxies.checkIn(proxy);
			break;
		}
		default:
			System.out.println("Error: No such RM: " + rm);
			return false;
		}

		return true;
	}

	private abstract class ProxyRunnable {
		public abstract void run(ResourceManager proxy);
	}
}
