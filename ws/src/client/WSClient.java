package client;

import java.net.URL;
import java.net.MalformedURLException;


public class WSClient {

    ResourceManagerImplService service;
    
    ResourceManager proxy;
    
    public WSClient(String serviceName, String serviceHost, int servicePort) 
    throws MalformedURLException {
    
        URL wsdlLocation = new URL("http", serviceHost, servicePort, 
                "/" + serviceName + "/service?wsdl");
                
        service = new ResourceManagerImplService(wsdlLocation);
        
        proxy = service.getResourceManagerImplPort();
    }
    
    public static int findChoice(String argument) {
        if (argument.compareToIgnoreCase("help") == 0)
            return 1;
        else if (argument.compareToIgnoreCase("newflight") == 0)
            return 2;
        else if (argument.compareToIgnoreCase("newcar") == 0)
            return 3;
        else if (argument.compareToIgnoreCase("newroom") == 0)
            return 4;
        else if (argument.compareToIgnoreCase("newcustomer") == 0)
            return 5;
        else if (argument.compareToIgnoreCase("deleteflight") == 0)
            return 6;
        else if (argument.compareToIgnoreCase("deletecar") == 0)
            return 7;
        else if (argument.compareToIgnoreCase("deleteroom") == 0)
            return 8;
        else if (argument.compareToIgnoreCase("deletecustomer") == 0)
            return 9;
        else if (argument.compareToIgnoreCase("queryflight") == 0)
            return 10;
        else if (argument.compareToIgnoreCase("querycar") == 0)
            return 11;
        else if (argument.compareToIgnoreCase("queryroom") == 0)
            return 12;
        else if (argument.compareToIgnoreCase("querycustomer") == 0)
            return 13;
        else if (argument.compareToIgnoreCase("queryflightprice") == 0)
            return 14;
        else if (argument.compareToIgnoreCase("querycarprice") == 0)
            return 15;
        else if (argument.compareToIgnoreCase("queryroomprice") == 0)
            return 16;
        else if (argument.compareToIgnoreCase("reserveflight") == 0)
            return 17;
        else if (argument.compareToIgnoreCase("reservecar") == 0)
            return 18;
        else if (argument.compareToIgnoreCase("reserveroom") == 0)
            return 19;
        else if (argument.compareToIgnoreCase("itinerary") == 0)
            return 20;
        else if (argument.compareToIgnoreCase("quit") == 0)
            return 21;
        else if (argument.compareToIgnoreCase("newcustomerid") == 0)
            return 22;
        else if (argument.compareToIgnoreCase("start") == 0)
            return 23;
        else if (argument.compareToIgnoreCase("commit") == 0)
            return 24;
        else if (argument.compareToIgnoreCase("abort") == 0)
            return 25;
        else if (argument.compareToIgnoreCase("shutdown") == 0)
            return 26;
        else if (argument.compareToIgnoreCase("crash") == 0)
            return 27;
        else
            return 666;
    }

}
