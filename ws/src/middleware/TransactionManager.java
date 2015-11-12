package middleware;

import middleware.LockManager.LockManager;

import java.util.HashMap;
import java.util.HashSet;

public class TransactionManager {
    private final Object _bidon = new Object();
    private HashSet<Integer> _processedIds = new HashSet<>();
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
        boolean isValid = id < _transactionId && !_processedIds.contains(id);
        if (isValid) {
            TTL ttl = _ttls.get(id);
            if (ttl != null) {
                ttl.restart();
            }
        }
        return isValid;
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
                _processedIds.add(id);
            }

            return isUnlocked;
        }
    }
}
