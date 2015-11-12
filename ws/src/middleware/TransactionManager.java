package middleware;

import middleware.LockManager.LockManager;

import java.util.HashMap;
import java.util.HashSet;

public class TransactionManager {
    public final static int CUSTOMER = 0;
    public final static int FLIGHT = 1;
    public final static int CAR = 2;
    public final static int ROOM = 3;

    private final Object _bidon = new Object();
    private HashMap<Integer, boolean[]> _currentTransactions = new HashMap<Integer, boolean[]>();
    private HashMap<Integer, TTL> _ttls = new HashMap<Integer, TTL>();
    private ResourceManagerImpl _rm;
    private int _transactionId = 0;
    private static boolean _isInstantiated = false;

    private TransactionManager(ResourceManagerImpl rm) {
        _rm = rm;
    }

    public static TransactionManager getInstance(ResourceManagerImpl rm) {
        TransactionManager tm = null;
        if (!_isInstantiated) {
            tm = new TransactionManager(rm);
            _isInstantiated = true;
        }
        return tm;
    }

    public int start() {
        synchronized(_bidon) {
            int transactionId = _transactionId++;
            _currentTransactions.put(transactionId, new boolean[4]);
            _ttls.put(transactionId, new TTL(transactionId, _rm, 20));
            return transactionId;
        }
    }

    public boolean commit(int id, LockManager lockManager) {
        return unlockId(id, lockManager);
    }

    public boolean abort(int id, LockManager lockManager) {
        return unlockId(id, lockManager);
    }

    public boolean hasValidId(int id) {
        boolean isValid = id < _transactionId && _currentTransactions.containsKey(id);
        if (isValid) {
            TTL ttl = _ttls.get(id);
            if (ttl != null) {
                ttl.restart();
            }
        }
        return isValid;
    }

    public boolean addTransactionRM(int id, int rm) {
        boolean isUpdated = false;
        boolean[] rmsUsed = _currentTransactions.get(id);

        if (rmsUsed != null) {
            rmsUsed[rm] = true;
            _currentTransactions.put(id, rmsUsed);
            isUpdated = true;
        }

        return isUpdated;
    }

    public boolean[] getRMsUsed(int id) {
        return _currentTransactions.get(id);
    }

    private boolean unlockId(int id, LockManager lockManager) {
        synchronized(_bidon) {
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
}
