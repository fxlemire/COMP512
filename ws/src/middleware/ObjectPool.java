package middleware;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;


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
	
	public ObjectPool (Iterable<T> objects)
	{
		m_pool = new LinkedList<T>();
		for(T obj: objects)
		{
			m_pool.offer(obj);
		}
		m_sem = new Semaphore(m_pool.size());
	}
	
	public T checkOut()
	{
		try 
		{
			m_sem.acquire();
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
