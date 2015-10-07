package middleware;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * A very simple threadsafe Object pool, with basically no enforcement mechanisms.
 * Things will work fine if you use it accordingly to its protocol
 * (Check the objects back in, don't hold references to the objects after
 * using them). These basic functionalities should be good enough for the
 * assignment.
 */
public class ObjectPool<T> {
	
	private Queue<T> m_pool;
	private Semaphore m_sem;
	private Object m_lock = new Object();
	private static final int ACQUIRE_TIMEOUT_S = 45;
	
	public ObjectPool (Iterable<T> objects)
	{
		m_pool = new LinkedList<T>();
		for(T obj: objects)
		{
			m_pool.offer(obj);
		}
		
		// This semaphore should be fair, since we want to time out on it.
		m_sem = new Semaphore(m_pool.size(), true);
	}
	
	public T checkOut()
	{
		try 
		{
			// Only try to acquire an object for 45 seconds.
			// This is a hackish way to prevent deadlocks.
			if (!m_sem.tryAcquire(ObjectPool.ACQUIRE_TIMEOUT_S, TimeUnit.SECONDS))
			{
				throw new RuntimeException("Could not acquire object within " +
					ObjectPool.ACQUIRE_TIMEOUT_S + " seconds, system might be deadlocked.");
			}
		}
		catch (InterruptedException e)
		{
			// If our waiting gets interrupted, something strange happened.
			// Just throw an error.
			throw new AssertionError(e);
		}
		
		T obj;
		synchronized (m_lock)
		{
			obj = m_pool.poll();
		}
		
		return obj;
	}
	
	public void checkIn(T obj)
	{
		synchronized (m_lock)
		{
			m_pool.offer(obj);
		}
		
		m_sem.release();
	}
}
