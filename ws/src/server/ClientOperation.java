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
        return _item;
    }

    public Type getOperationType() {
        return _operationType;
    }

    public static RMItem getLatest(LinkedList<ClientOperation> operations) {
        ClientOperation operation = operations.getLast();
        return operation.getOperationType() == Type.WRITE ? operation.getItem() : null;
    }
}
