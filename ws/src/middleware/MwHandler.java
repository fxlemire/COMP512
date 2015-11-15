package middleware;

import javax.xml.ws.handler.soap.SOAPMessageContext;

import server.PerfHandler;

public class MwHandler extends PerfHandler {

	public boolean handleMessage(SOAPMessageContext mc) {
		// Don't continue if PerfHandler doesn't want to
		if (!super.handleMessage(mc))
			return false;
		
		/* Any custom code for the MW here... */
		
		return true;
	}

}
