package server;

import java.util.Iterator;
import java.util.LinkedList;

public class ClientOperation {
    public enum Type { WRITE, DELETE }

    private String _key;
    private RMItem _item;
    private Type _operationType;

    public ClientOperation(String key, RMItem item, Type operationType) {
        _key = key;
        _item = item;
        _operationType = operationType;
    }

    public String getKey() {
        return _key;
    }

    public RMItem getItem() {
        if (_operationType == Type.DELETE)
        	return null;
    	return _item;
    }

    public Type getOperationType() {
        return _operationType;
    }

    public static boolean hasOp(LinkedList<ClientOperation> operations, String key) {
    	Iterator<ClientOperation> rev =  operations.descendingIterator();
    	while(rev.hasNext()) {
    		ClientOperation op = rev.next();
    		if (op.getKey().equals(key))
    			return true;
    	}
    	
    	return false;
    }
    
    public static RMItem getLatest(LinkedList<ClientOperation> operations, String key) {
    	Iterator<ClientOperation> rev =  operations.descendingIterator();
    	while(rev.hasNext()) {
    		ClientOperation op = rev.next();
    		//We also want to make a copy here
    		if (op.getKey().equals(key)) {
    			RMItem item = op.getItem();
    			if (item != null)
    				item = (RMItem) item.clone();
    			return item;
    		}
    	}
    	
    	return null;
    }
}
