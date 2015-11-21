package middleware;

import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import middleware.TransactionManager;

import server.PerfHandler;

public class MwHandler implements SOAPHandler<SOAPMessageContext>/*extends PerfHandler*/ {

	public boolean handleMessage(SOAPMessageContext mc) {
		// Don't continue if PerfHandler doesn't want to
		//if (!super.handleMessage(mc))
		//	return false;
		
		Boolean outbound = (Boolean) mc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
		try {
			SOAPMessage m = mc.getMessage();
			//m.writeTo(System.out);
			SOAPBody msg = m.getSOAPBody();
			//System.out.println();
			
			if (!outbound) {
				if (!checkTxnValid(mc, msg)) {
					cancel(msg);
					//m.writeTo(System.out);
					//System.out.println();
					return false;
				}
			}
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}		

		return true;
	}
	
	private boolean checkTxnValid(SOAPMessageContext mc, SOAPBody msg) {
		Object txnId = mc.get(PerfHandler.TXN_ID_KEY);
		if (txnId == PerfHandler.NO_TXN_ID || txnId == PerfHandler.DUMMY_TXN_ID)
			return true;
		
		if (txnId == null)
			System.out.println("LOL PHOQUE");
		
		int id = Integer.parseInt((String) txnId);
		return TransactionManager.getInstance().hasValidId(id);
	}
	
	private void cancel(SOAPBody msg) throws SOAPException {
		String name = msg.getFirstChild().getLocalName();
		msg.removeChild(msg.getFirstChild());
		
		// You have to do it this way, in one go, to prevent "nw2 was already bound" types of error
		msg.addChildElement(name + "Response", "ns2", "http://ws.server/");
		
		// Add a return element and give it the generic value we send when there's a failure.
		SOAPElement resp = (SOAPElement) msg.getChildElements().next();
		resp.addChildElement("return");
		SOAPElement ret = ((SOAPElement) resp.getChildElements().next());
		ret.setTextContent(getFailureContentFor(name));
	}
	
	private String getFailureContentFor(String name) {
		if (name.equals("queryCustomerInfo"))
			return "null";
		if (name.startsWith("query") || name.equals("newCustomer"))
			return "-1";
		
		return "false";
	}

	
	// === DON'T CARE ===
	public Set<QName> getHeaders() {
		return null;
	}

	public void close(MessageContext arg0) {
		
	}

	public boolean handleFault(SOAPMessageContext arg0) {
		return true;
	}

}
