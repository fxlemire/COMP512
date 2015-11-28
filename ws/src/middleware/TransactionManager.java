package middleware;

import Util.TTL;
import middleware.LockManager.LockManager;

import java.util.Arrays;
import java.util.HashMap;

public class TransactionManager {
    public final static int CUSTOMER = 0;
    public final static int FLIGHT = 1;
    public final static int CAR = 2;
    public final static int ROOM = 3;

    private HashMap<Integer, boolean[]> _currentTransactions = new HashMap<Integer, boolean[]>();
    private HashMap<Integer, TTL> _ttls = new HashMap<Integer, TTL>();
    private HashMap<Integer, Boolean> _transactionResults = new HashMap<Integer, Boolean>();
    private int _transactionId = 0;
    private static boolean _isInstantiated = false;
    private static TransactionManager tm = null;
    private static final int TIME_TO_LIVE = 180;

    private TransactionManager() { }

    public static TransactionManager getInstance() {
        if (!_isInstantiated) {
            tm = new TransactionManager();
            _isInstantiated = true;
        }
        return tm;
    }

    public synchronized int start(ResourceManagerImpl rm) {
        int transactionId = _transactionId++;
        _currentTransactions.put(transactionId, new boolean[4]);
        _ttls.put(transactionId, new TTL(transactionId, rm, TIME_TO_LIVE));
        return transactionId;
    }

    public synchronized boolean commit(int id, LockManager lockManager) {
    	_transactionResults.put(id, true);
        return unlockId(id, lockManager);
    }

    public synchronized boolean abort(int id, LockManager lockManager) {
    	_transactionResults.put(id, false);
    	return unlockId(id, lockManager);
    }
    
    public synchronized boolean getTransactionResult(int id) {
    	if (_transactionResults.containsKey(id)) {
    		return _transactionResults.get(id);
    	}
    	
    	// If we don't know about a given transaction, don't take any chances and say it
    	// was aborted.
    	return false;
    }

    public synchronized boolean hasValidId(int id) {
        boolean isValid = id < _transactionId && _currentTransactions.containsKey(id);
        if (isValid) {
            TTL ttl = _ttls.get(id);
            if (ttl != null) {
                ttl.restart();
            }
        }
        return isValid;
    }

    public synchronized boolean addTransactionRM(int id, int rm) {
        boolean isUpdated = false;
        boolean[] rmsUsed = _currentTransactions.get(id);

        if (rmsUsed != null) {
            rmsUsed[rm] = true;
            _currentTransactions.put(id, rmsUsed);
            isUpdated = true;
        }

        return isUpdated;
    }
    
    /* This will be used when a RM crashes in the middle of a transaction,
     * and thus doesn't "participate" in it anymore. */
    public synchronized boolean removeTransactionRM(int id, int rm) {
    	boolean[] rmsUsed = _currentTransactions.get(id);

        if (rmsUsed != null) {
            rmsUsed[rm] = false;
            _currentTransactions.put(id, rmsUsed);
        }
        
        return true;
    }

    public synchronized boolean[] getRMsUsed(int id) {
    	// Return a copy to prevent modifications from outside the synchronized block
        return Arrays.copyOf(_currentTransactions.get(id), 4);
    }

    private boolean unlockId(int id, LockManager lockManager) {
        boolean isUnlocked = lockManager.UnlockAll(id);

        if (isUnlocked) {
            TTL ttl = _ttls.get(id);
            if (ttl != null) {
                ttl.kill();
                _ttls.remove(id);
            }
            _currentTransactions.remove(id);
        }

        return isUnlocked;
    }
}
