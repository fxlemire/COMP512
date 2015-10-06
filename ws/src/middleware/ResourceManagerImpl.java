// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middleware;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import javax.jws.WebService;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;


@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImpl implements server.ws.ResourceManager {
	
	/* Documentation indicates that, while the proxy objects are not
	 * considered thread-safe, there should be no ill-effects by using
	 * them the way we do - that is, without using per-request settings,
	 * sessions or security tokens. Hence, we're not bothering with
	 * creating a proxy pool or something else.
	 * 
	 * http://cxf.apache.org/faq.html#FAQ-AreJAX-WSclientproxiesthreadsafe?
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
		// TODO Auto-generated method stub
		return false;
	}

	public boolean addFlight(int id, int flightNumber, int numSeats,
			int flightPrice) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean deleteCars(int id, String location) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean deleteCustomer(int id, int customerId) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean deleteFlight(int id, int flightNumber) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean deleteRooms(int id, String location) {
		// TODO Auto-generated method stub
		return false;
	}

	public int newCustomer(int id) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean newCustomerId(int id, int customerId) {
		// TODO Auto-generated method stub
		return false;
	}

	public int queryCars(int id, String location) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int queryCarsPrice(int id, String location) {
		// TODO Auto-generated method stub
		return 0;
	}

	public String queryCustomerInfo(int id, int customerId) {
		// TODO Auto-generated method stub
		return null;
	}

	public int queryFlight(int id, int flightNumber) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int queryFlightPrice(int id, int flightNumber) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int queryRooms(int id, String location) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int queryRoomsPrice(int id, String location) {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean reserveCar(int id, int customerId, String location) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean reserveFlight(int id, int customerId, int flightNumber) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean reserveItinerary(int id, int customerId,
			Vector flightNumbers, String location, boolean car, boolean room) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean reserveRoom(int id, int customerId, String location) {
		// TODO Auto-generated method stub
		return false;
	}
    
}
