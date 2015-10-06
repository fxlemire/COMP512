// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middleware;

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
	
	public boolean addCars(int id, String location, int numCars, int carPrice) {
		ResourceManager proxy = carProxies.checkOut();
		boolean result = proxy.addCars(id, location, numCars, carPrice);
		carProxies.checkIn(proxy);
		
		return result;
	}

	public boolean addFlight(int id, int flightNumber, int numSeats,
			int flightPrice) {
		ResourceManager proxy = flightProxies.checkOut();
		boolean result = proxy.addFlight(id, flightNumber, numSeats, flightPrice);
		flightProxies.checkIn(proxy);
		
		return result;
	}

	public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		ResourceManager proxy = roomProxies.checkOut();
		boolean result = proxy.addRooms(id, location, numRooms, roomPrice);
		roomProxies.checkIn(proxy);
		
		return result;
	}

	public boolean deleteCars(int id, String location) {
		ResourceManager proxy = carProxies.checkOut();
		boolean result = proxy.deleteCars(id, location);
		carProxies.checkIn(proxy);
		
		return result;
	}

	public boolean deleteCustomer(int id, int customerId) {
		ResourceManager proxy = customerProxies.checkOut();
		if (!proxy.checkCustomerExistence(id, customerId))
		{
			return false;
		}
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
		
		return true;
	}

	public boolean deleteFlight(int id, int flightNumber) {
		ResourceManager proxy = flightProxies.checkOut();
		boolean result = proxy.deleteFlight(id, flightNumber);
		flightProxies.checkIn(proxy);
		
		return result;
	}

	public boolean deleteRooms(int id, String location) {
		ResourceManager proxy = roomProxies.checkOut();
		boolean result = proxy.deleteRooms(id, location);
		roomProxies.checkIn(proxy);
		
		return result;
	}

	public int newCustomer(int id) {
		ResourceManager proxy = customerProxies.checkOut();
		int result = proxy.newCustomer(id);
		customerProxies.checkIn(proxy);
		
		return result;
	}

	public boolean newCustomerId(int id, int customerId) {
		// Just call this at the customer RM. Normally the
		// client won't use it by themselves.
		ResourceManager proxy = customerProxies.checkOut();
		boolean result = proxy.newCustomerId(id, customerId);
		customerProxies.checkIn(proxy);
		
		return result;
	}

	public int queryCars(int id, String location) {
		ResourceManager proxy = carProxies.checkOut();
		int result = proxy.queryCars(id, location);
		carProxies.checkIn(proxy);
		
		return result;
	}

	public int queryCarsPrice(int id, String location) {
		ResourceManager proxy = carProxies.checkOut();
		int result = proxy.queryCarsPrice(id, location);
		carProxies.checkIn(proxy);
		
		return result;
	}

	public String queryCustomerInfo(int id, int customerId) {
		// TODO Auto-generated method stub
		return null;
	}

	public int queryFlight(int id, int flightNumber) {
		ResourceManager proxy = flightProxies.checkOut();
		int result = proxy.queryFlight(id, flightNumber);
		flightProxies.checkIn(proxy);
		
		return result;
	}

	public int queryFlightPrice(int id, int flightNumber) {
		ResourceManager proxy = flightProxies.checkOut();
		int result = proxy.queryFlightPrice(id, flightNumber);
		flightProxies.checkIn(proxy);
		
		return result;
	}

	public int queryRooms(int id, String location) {
		ResourceManager proxy = roomProxies.checkOut();
		int result = proxy.queryRooms(id, location);
		roomProxies.checkIn(proxy);
		
		return result;
	}

	public int queryRoomsPrice(int id, String location) {
		ResourceManager proxy = roomProxies.checkOut();
		int result = proxy.queryRoomsPrice(id, location);
		roomProxies.checkIn(proxy);
		
		return result;
	}

	public boolean reserveCar(int id, int customerId, String location) {
		ResourceManager proxy = customerProxies.checkOut();
		boolean exists = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		
		if (exists)
		{
			proxy = carProxies.checkOut();
			proxy.newCustomerId(id, customerId);
			boolean result = proxy.reserveCar(id, customerId, location);
			carProxies.checkIn(proxy);
			
			return result;
		}
		
		return false;
	}

	public boolean reserveFlight(int id, int customerId, int flightNumber) {
		ResourceManager proxy = customerProxies.checkOut();
		boolean exists = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		
		if (exists)
		{
			proxy = flightProxies.checkOut();
			proxy.newCustomerId(id, customerId);
			boolean result = proxy.reserveFlight(id, customerId, flightNumber);
			flightProxies.checkIn(proxy);
			
			return result;
		}
		
		return false;
	}

	public boolean reserveItinerary(int id, int customerId,
			Vector flightNumbers, String location, boolean car, boolean room) {

		// If customer doesn't exist, bail now.
		ResourceManager customerProxy = customerProxies.checkOut();
		boolean exists = customerProxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(customerProxy);
		
		if (!exists)
		{
			return false;
		}
		
		
		// Have a simple management of proxies here.
		// TODO This could deadlock, figure out if we can fix it.
		ResourceManager flightProxy = flightProxies.checkOut();
		ResourceManager carProxy = carProxies.checkOut();
		ResourceManager roomProxy = roomProxies.checkOut();
		try
		{			
			// Precheck availability of flights and car/room.
			// We're assuming that nothing will change in-between.
			// This is okay of the purposes of the first assignment.
			
			for (int i = 0 ; i < flightNumbers.size() ; i++) {
				if (flightProxy.queryFlight(id, (Integer) flightNumbers.elementAt(i)) == 0)
				{
					throw new RuntimeException();
				}
			}
			
			if ((car && carProxy.queryCars(id, location) == 0) ||
					(room && roomProxy.queryRooms(id, location) == 0))
			{
				throw new RuntimeException();
			}
			
			// Proceed with the booking. If any of the reservations fail, just bail out.
			for (int i = 0 ; i < flightNumbers.size() ; i++) {
				if (!flightProxy.reserveFlight(id, customerId, (Integer) flightNumbers.elementAt(i)))
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
			
			return true;
		}
		catch (RuntimeException e)
		{
			return false;
		}
		finally
		{
			flightProxies.checkIn(flightProxy);
			carProxies.checkIn(carProxy);
			roomProxies.checkIn(roomProxy);
		}
	}

	public boolean reserveRoom(int id, int customerId, String location) {
		ResourceManager proxy = customerProxies.checkOut();
		boolean exists = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		
		if (exists)
		{
			proxy = roomProxies.checkOut();
			proxy.newCustomerId(id, customerId);
			boolean result = proxy.reserveRoom(id, customerId, location);
			roomProxies.checkIn(proxy);
			
			return result;
		}
		
		return false;
	}

	public boolean checkCustomerExistence(int id, int customerId) {
		// Again, this method probably won't be called by the client.
		ResourceManager proxy = customerProxies.checkOut();
		boolean result = proxy.checkCustomerExistence(id, customerId);
		customerProxies.checkIn(proxy);
		return result;
	}
}
