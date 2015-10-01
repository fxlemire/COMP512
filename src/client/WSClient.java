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

}
