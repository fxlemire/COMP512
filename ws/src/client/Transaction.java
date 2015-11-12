package client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Vector;

/**
 * A transaction that the automated client can execute.
 */
public class Transaction {
	
	private interface Operation {
		/**
		 * Execute the given operation of this transaction
		 * @return Whether to continue this transaction or not.
		 */
		public boolean Execute(HashMap<Integer, String> variables, ResourceManager proxy);
	}
	
	private HashMap<Integer, String> variables = new HashMap<Integer, String>();
	private LinkedList<Operation> operations = new LinkedList<Operation>();
	
	private static final String VAR_MARKER = "%";
	
	public final int size;
	private int numCompletedOps = 0;
	private boolean aborted = false;
	
	public Transaction(LinkedList<String> lines)
	{
		HashSet<Integer> currentVariables = new HashSet<Integer>();
		for(int i = 0 ; i < lines.size() ; i++)
			operations.add(createOperation(lines.get(i), currentVariables));
		
		size = operations.size();
	}
	
	public int getNumCompletedOps()
	{
		return numCompletedOps;
	}
	
	public boolean wasAborted()
	{
		return aborted;
	}
	
	public static final String getOpName(String cmd)
	{		
		if(!cmd.startsWith(VAR_MARKER))
		{
			int end = cmd.indexOf(",");
			if (end < 0)
				return cmd;
			return cmd.substring(0, end);
		}
		else
		{
			int start = cmd.indexOf(",") + 1;
			if (start < 0)
				throw new RuntimeException("This is just a variable name");
			
			int end = cmd.indexOf(",", start);
			
			if (end < 0)
				return cmd.substring(start);
			return cmd.substring(start, end);
		}
	}
	
	private Operation createOperation(String line, HashSet<Integer> currentVariables)
	{
		final String[] parts = line.split(",");
		//Start at 1 to skip a potential assignment
		//Then check whether all the variables we are using
		//are properly formed and currently available.
		for (int i = 1 ; i < parts.length ; i++)
		{
			if(parts[i].startsWith(VAR_MARKER))
			{
				if(!currentVariables.contains(getVariable(parts[i])))
					throw new RuntimeException("Parse Error: " + parts[i] + " is undeclared.");
			}
		}
		
		//If we're doing an assignment, add the variable we're assigning
		//to the set of current variables
		if(parts[0].startsWith(VAR_MARKER))
		{
			if(parts.length == 1)
				throw new RuntimeException("ParseError: " + parts[0] + " is not a command.");
			
			currentVariables.add(getVariable(parts[0]));
		}
		
		Operation op = new Operation() {
			
			public boolean Execute(HashMap<Integer, String> variables, ResourceManager proxy) {
				System.out.print("Executing: ");
				long start = System.nanoTime();
				//Determine whether we're assigning a variable or not
				int i = 1;
				String assignVar = null;
				String cmd = parts[0];
				if (parts[0].startsWith(VAR_MARKER))
				{
					i = 2;
					assignVar = parts[0];
					cmd = parts[1];
					
					System.out.print(assignVar + " = " + parts[1] + ",");
				}
				else
				{
					System.out.print(parts[0] + ",");
				}
				
				//Get the arguments
				ArrayList<String> arguments = new ArrayList<String>(parts.length);
				for (; i < parts.length ; i++) {
					String value = Transaction.getActual(parts[i], variables);
					arguments.add(value);
					System.out.print(value + ",");
				}
				
				
				
				//Perform the dispatch on the proxy
				String result = Transaction.dispatch(cmd, arguments, proxy);
				System.out.print(",, Result: " + result);
				//Assign the variable if there's one.
				if(assignVar != null)
					variables.put(getVariable(assignVar), result);
				
				long end = System.nanoTime();
				System.out.println(" (" + (int) ((end - start) / 1e6) + "ms)");
				
				//If the command was abort and the result of it was true,
				//we want to abort and thus return false.
				//In all other cases we want to return true.				
				return !(cmd.equals("abort") && Boolean.parseBoolean(result));
			}
		};
		
		return op;
	}
	
	private static Integer getVariable(String s)
	{
		String var = s.substring(1);
		try
		{
			return Integer.parseInt(var);
		}
		catch (NumberFormatException e)
		{
			throw new RuntimeException("Parse error: \"" + var + "\" is not a proper variable");
		}
	}
	
	private static String getActual(String unknown, HashMap<Integer, String> variables)
	{
		if (unknown.startsWith(VAR_MARKER))
		{
			return variables.get(getVariable(unknown));
		}
		return unknown;
	}
	
	private static String[] splitNotInQuotes(String s, char c)
	{
		LinkedList<String> r = new LinkedList<String>();
		boolean in = false;
		StringBuilder cur = new StringBuilder();
		for(int i = 0 ; i < s.length() ; i++)
		{
			char curc = s.charAt(i); 
			if(curc == c && !in)
			{
				r.add(cur.toString());
				cur = new StringBuilder();
			}
			else if (curc == '\"')
				in = !in;
			else
				cur.append(curc);
		}
		r.add(cur.toString());
		
		return r.toArray(new String[r.size()]);
	}
	
	/**
	 * Execute all operations in this transaction.
	 * Returns the execution time in ms.
	 */
	public int ExecuteAll(ResourceManager proxy)
	{		
		aborted = false;
		numCompletedOps = 0;
		
		variables.clear(); //Make sure we have no variable assignments
		
		long start = System.nanoTime();
		for(Operation op: operations)
		{
			numCompletedOps++;
			// Execute operations as long as one of them doesn't tell us to abort.
			if(!op.Execute(variables, proxy)) {
				aborted = true;
				break;
			}
		}
		
		long end = System.nanoTime();
		return (int) ((end - start) / 1e6); //Go from nanoseconds to miliseconds
	}	
	
	private static String dispatch(String command, ArrayList<String> arguments, ResourceManager proxy)
	{
		String result;
		switch(WSClient.findChoice(command)) {
            
        case 2:  //new flight
            if (arguments.size() != 4) {
                throw new RuntimeException("Wrong argument size");
            }
            
            result = String.valueOf(proxy.addFlight(Integer.parseInt(arguments.get(0)),
            		Integer.parseInt(arguments.get(1)),
            		Integer.parseInt(arguments.get(2)),
            		Integer.parseInt(arguments.get(3))));
            break;
            
        case 3:  //new car
        	if (arguments.size() != 4) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.addCars(Integer.parseInt(arguments.get(0)),
            		arguments.get(1),
            		Integer.parseInt(arguments.get(2)),
            		Integer.parseInt(arguments.get(3))));
            break;
            
        case 4:  //new room
        	if (arguments.size() != 4) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.addRooms(Integer.parseInt(arguments.get(0)),
            		arguments.get(1),
            		Integer.parseInt(arguments.get(2)),
            		Integer.parseInt(arguments.get(3))));
            break;
            
        case 5:  //new Customer
        	if (arguments.size() != 1) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.newCustomer(Integer.parseInt(arguments.get(0))));
            break;
            
        case 6: //delete Flight
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.deleteFlight(Integer.parseInt(arguments.get(0)),
            		Integer.parseInt(arguments.get(1))));
        	
            break;
            
        case 7: //delete car
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.deleteCars(Integer.parseInt(arguments.get(0)),
            		arguments.get(1)));
            break;
            
        case 8: //delete room
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.deleteRooms(Integer.parseInt(arguments.get(0)),
            		arguments.get(1)));
            break;
            
        case 9: //delete Customer
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.deleteCustomer(Integer.parseInt(arguments.get(0)),
            		Integer.parseInt(arguments.get(1))));
            break;
            
        case 10: //querying a flight
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.queryFlight(Integer.parseInt(arguments.get(0)),
            		Integer.parseInt(arguments.get(1))));
            break;
            
        case 11: //querying a car Location
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.queryCars(Integer.parseInt(arguments.get(0)),
            		arguments.get(1)));
            break;
            
        case 12: //querying a room location
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.queryRooms(Integer.parseInt(arguments.get(0)),
            		arguments.get(1)));
            break;
            
        case 13: //querying Customer Information
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.queryCustomerInfo(Integer.parseInt(arguments.get(0)),
            		Integer.parseInt(arguments.get(1))));
            break;             
            
        case 14: //querying a flight Price
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.queryFlightPrice(Integer.parseInt(arguments.get(0)),
            		Integer.parseInt(arguments.get(1))));
            break;
            
        case 15: //querying a car Price
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.queryCarsPrice(Integer.parseInt(arguments.get(0)),
            		arguments.get(1)));
            break;

        case 16: //querying a room price
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.queryRoomsPrice(Integer.parseInt(arguments.get(0)),
            		arguments.get(1)));
            break;
            
        case 17:  //reserve a flight
        	if (arguments.size() != 3) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.reserveFlight(Integer.parseInt(arguments.get(0)),
        			Integer.parseInt(arguments.get(1)),
            		Integer.parseInt(arguments.get(2))));
            break;
            
        case 18:  //reserve a car
        	if (arguments.size() != 3) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.reserveCar(Integer.parseInt(arguments.get(0)),
        			Integer.parseInt(arguments.get(1)),
            		arguments.get(2)));
            break;
            
        case 19:  //reserve a room
        	if (arguments.size() != 3) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.reserveRoom(Integer.parseInt(arguments.get(0)),
        			Integer.parseInt(arguments.get(1)),
            		arguments.get(2)));
            break;
            
        case 20:  //reserve an Itinerary
            if (arguments.size()<6) {
            	throw new RuntimeException("Wrong argument size");
            }
            
            // Note: do not put the type information for this!
            // If you do, it won't compile.
            Vector flightNumbers = new Vector();
            for (int i = 2; i < arguments.size() - 3; i++)
            	flightNumbers.addElement(arguments.get(i));
            
            result = String.valueOf(proxy.reserveItinerary(Integer.parseInt(arguments.get(0)), 
            		Integer.parseInt(arguments.get(1)), 
            		flightNumbers, 
            		arguments.get(arguments.size() - 3), 
            		Boolean.parseBoolean(arguments.get(arguments.size() - 2)),
            		Boolean.parseBoolean(arguments.get(arguments.size() - 1))));
            break;            
        case 22:  //new Customer given id
        	if (arguments.size() != 2) {
                throw new RuntimeException("Wrong argument size");
            }
            
        	result = String.valueOf(proxy.newCustomerId(Integer.parseInt(arguments.get(0)),
        			Integer.parseInt(arguments.get(1))));
            break;
        case 23: //Start
        	result = String.valueOf(proxy.start());
        	break;
        case 24: //Commit
        	result = String.valueOf(proxy.commit(Integer.parseInt(arguments.get(0))));
        	break;
        case 25: //Abort
        	if (arguments.size() != 4) {
                throw new RuntimeException("Wrong argument size");
            }
        	
        	if (evaluateAbortCondition(arguments.get(1), arguments.get(2), arguments.get(3)))
        	{
        		proxy.commit(Integer.parseInt(arguments.get(0)));
        		
        		//No need to wait for some response. Even if the abort doesn't make it,
        		//no commits have been sent so eventually the data will be removed.
        		result = "true";
        	}
        	else
        	{
        		result =  "false";
        	}
        	break;
        default:
            System.out.println("Warning: unsupported command " + command);
            result = "ERROR";
            break;
        }
		
		return result;
	}
	
	private static boolean evaluateAbortCondition(String a, String op, String b)
	{
		//TODO In theory here we should be a bit more careful and check for the
		//"types" of the operands (e.g. String "true" vs. boolean True.)
		if(op.equals("=="))
			return a.equals(b);
		else if(op.equals("!="))
			return !a.equals(b);
		
		throw new RuntimeException("Unsupported operand: \"" + op + "\"");
	}
}
