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
	
	public String toString()
	{
		return m_data.toString();
	}
}
