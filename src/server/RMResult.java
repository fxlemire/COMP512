package server;

import java.io.Serializable;

public class RMResult implements Serializable {
	public final RMType type;
	public final Serializable m_data;
	
	public RMResult(Serializable data) throws AssertionError
	{
		m_data = data;
		if (m_data instanceof Exception)
		{
			type = RMType.ERROR;
		}
		else if (m_data instanceof Boolean)
		{
			type = RMType.BOOL;
		}
		else if (m_data instanceof Integer)
		{
			type = RMType.INT;
		}
		else if (m_data instanceof String)
		{
			type = RMType.STRING;
		}
		else if (m_data instanceof RMHashtable)
		{
			type = RMType.HTABLE;
		}
		else
		{
			throw new AssertionError("Unsupported result type");
		}
	}
	
	public boolean IsError()
	{
		return type == RMType.ERROR;
	}
	
	public Exception AsError()
	{
		if (type != RMType.ERROR)
			throw new RuntimeException("This result is not an error.");
		
		return (Exception) m_data;
	}
	
	public boolean AsBool()
	{
		if (type != RMType.BOOL)
			throw new RuntimeException("This result is not a boolean.");
		
		return (Boolean) m_data;
	}
	
	public Integer AsInt()
	{
		if (type != RMType.INT)
			throw new RuntimeException("This result is not an integer.");
		
		return (Integer) m_data;
	}
	
	public String AsString()
	{
		if (type != RMType.STRING)
			throw new RuntimeException("This result is not a string.");
		
		return (String) m_data;
	}
	
	public RMHashtable AsHashtable()
	{
		if (type != RMType.HTABLE)
			throw new RuntimeException("This result is not a hashtable.");
		
		return (RMHashtable) m_data;
	}
	
	public String toString()
	{
		return m_data.toString();
	}
}
