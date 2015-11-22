package server;

import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

/**
 * Handler whose job is to output performance information on each method call.
 */
public class PerfHandler implements SOAPHandler<SOAPMessageContext> {

	public static final String TIME_KEY = "perf_time";
	public static final String NAME_KEY = "method_name";
	public static final String TXN_ID_KEY = "txn_id";
	public static final Object DUMMY_TXN_ID = new Object();
	public static final Object NO_TXN_ID = new Object();
	
	public boolean handleMessage(SOAPMessageContext mc) {
		Boolean outbound = (Boolean) mc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
		
		SOAPMessage msg = mc.getMessage();
		try {
			SOAPElement req = (SOAPElement) msg.getSOAPBody().getFirstChild();
	        if (!outbound) {
	        	// Add some information to the message context for later retrieval
	            mc.put(TIME_KEY, System.nanoTime()); // Current time
	            
	            if (!mc.containsKey(NAME_KEY))
	            	mc.put(NAME_KEY, getMethodName(req));
	            
	            if (!mc.containsKey(TXN_ID_KEY))
	            	mc.put(TXN_ID_KEY, getTxnId((String) mc.get(NAME_KEY), req));
	        } else {
	        	long elapsedUs = (System.nanoTime() - (Long) mc.get(TIME_KEY)) / 1000;
	        	
	        	String method = mc.get(NAME_KEY).toString();
	        	String txnId;
	        	Object idObj = mc.get(TXN_ID_KEY);
	        	if (idObj == DUMMY_TXN_ID) {
	        		//Dummy: the transaction ID is the returned value
	        		txnId = ((SOAPElement) req.getFirstChild()).getValue();
	        	} else if (idObj == NO_TXN_ID) {
	        		txnId = "<no id>";
	        	} else {
	        		txnId = (String) idObj;
	        	}
	        	System.out.println("[PERF] " + method + " " + txnId + ": " + elapsedUs + "us");
	        }
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
        
        return true;
	}

	
	public static String getMethodName(SOAPElement req) {
		String nodeName = req.getNodeName();
		
        // Name of method appears as nc2:xxxx in the XML 
        return nodeName.substring(nodeName.indexOf(':') + 1);
	}

	public static Object getTxnId(String methodName, SOAPElement req) {
		if (methodName.equals("start")) {
	    	// The real txn id will appear at the output side
	    	return DUMMY_TXN_ID; 
	    } else if (methodName.equals("shutdown") || methodName.equals("crash") || methodName.equals("selfDestruct")) {
	    	// No txn id for shutdown
	    	return NO_TXN_ID;
	    } else {
	    	// By convention, txn id is always the first parameter.
	    	SOAPElement id = (SOAPElement) req.getFirstChild();
	    	return id.getValue();
	    }
	}
	
	
	/* We don't really care about those methods over here */

	public void close(MessageContext mc) {

	}

	public Set<QName> getHeaders() {
		return null;
	}

	public boolean handleFault(SOAPMessageContext mc) {
		return true;
	}

}
