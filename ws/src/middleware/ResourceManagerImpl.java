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
	
	/* TODO implement a pool.
	 */
	private ResourceManager flightProxy;
	private ResourceManager carProxy;
	private ResourceManager roomProxy;
	private ResourceManager customerProxy;
	
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
	    flightProxy = getProxyFor("flight", env);
	    roomProxy = getProxyFor("room", env);
	    carProxy = getProxyFor("car", env);
	    customerProxy = getProxyFor("customer", env);
	}
	
	public boolean addCars(int id, String location, int numCars, int carPrice) {
		return carProxy.addCars(id, location, numCars, carPrice);
	}

	public boolean addFlight(int id, int flightNumber, int numSeats,
			int flightPrice) {
		return flightProxy.addFlight(id, flightNumber, numSeats, flightPrice);
	}

	public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		return roomProxy.addRooms(id, location, numRooms, roomPrice);
	}

	public boolean deleteCars(int id, String location) {
		return carProxy.deleteCars(id, location);
	}

	public boolean deleteCustomer(int id, int customerId) {
		if (!customerProxy.checkCustomerExistence(id, customerId))
		{
			return false;
		}
		
		carProxy.deleteCustomer(id, customerId);
		flightProxy.deleteCustomer(id, customerId);
		roomProxy.deleteCustomer(id, customerId);
		customerProxy.deleteCustomer(id, customerId);
		return true;
	}

	public boolean deleteFlight(int id, int flightNumber) {
		return flightProxy.deleteFlight(id, flightNumber);
	}

	public boolean deleteRooms(int id, String location) {
		return roomProxy.deleteRooms(id, location);
	}

	public int newCustomer(int id) {
		return customerProxy.newCustomer(id);
	}

	public boolean newCustomerId(int id, int customerId) {
		// Just call this at the customer RM. Normally the
		// client won't use it by themselves.
		return customerProxy.newCustomerId(id, customerId);
	}

	public int queryCars(int id, String location) {
		return carProxy.queryCars(id, location);
	}

	public int queryCarsPrice(int id, String location) {
		return carProxy.queryCarsPrice(id, location);
	}

	public String queryCustomerInfo(int id, int customerId) {
		// TODO Auto-generated method stub
		return null;
	}

	public int queryFlight(int id, int flightNumber) {
		return flightProxy.queryFlight(id, flightNumber);
	}

	public int queryFlightPrice(int id, int flightNumber) {
		return flightProxy.queryFlightPrice(id, flightNumber);
	}

	public int queryRooms(int id, String location) {
		return roomProxy.queryRooms(id, location);
	}

	public int queryRoomsPrice(int id, String location) {
		return roomProxy.queryRoomsPrice(id, location);
	}

	public boolean reserveCar(int id, int customerId, String location) {
		if (customerProxy.checkCustomerExistence(id, customerId))
		{
			carProxy.newCustomerId(id, customerId);
			return carProxy.reserveCar(id, customerId, location);
		}
		
		return false;
	}

	public boolean reserveFlight(int id, int customerId, int flightNumber) {
		if (customerProxy.checkCustomerExistence(id, customerId))
		{
			flightProxy.newCustomerId(id, customerId);
			return flightProxy.reserveFlight(id, customerId, flightNumber);
		}
		
		return false;
	}

	public boolean reserveItinerary(int id, int customerId,
			Vector flightNumbers, String location, boolean car, boolean room) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean reserveRoom(int id, int customerId, String location) {
		if (customerProxy.checkCustomerExistence(id, customerId))
		{
			roomProxy.newCustomerId(id, customerId);
			return roomProxy.reserveCar(id, customerId, location);
		}
		
		return false;
	}

	public boolean checkCustomerExistence(int id, int customerId) {
		// Again, this method probably won't be called by the client.
		return customerProxy.checkCustomerExistence(id, customerId);
	}
    
}
