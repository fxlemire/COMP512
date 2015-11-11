package middleware;

import middleware.LockManager.LockManager;

import java.util.HashSet;

public class TransactionManager {
    private static HashSet<Integer> _processedIds = new HashSet<>();
    private static int _transactionId = 0;
    
    synchronized public int start() {
        return _transactionId++;
    }

    public boolean commit(int id, LockManager lockManager) {
        return unlockId(id, lockManager);
    }

    public boolean abort(int id, LockManager lockManager) {
        return unlockId(id, lockManager);
    }

    public boolean hasValidId(int id) {
        return id < _transactionId && !_processedIds.contains(id);
    }

    private synchronized boolean unlockId(int id, LockManager lockManager) {
        boolean isUnlocked = lockManager.UnlockAll(id);

        if (isUnlocked) {
            _processedIds.add(id);
        }

        return isUnlocked;
    }
}
