package client;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

public class AutoClient extends WSClient {

	private final int rate;
	private final boolean rand;
	private int transactionsLeft;
	private ArrayList<Transaction> transactions = new ArrayList<Transaction>();
	private Random randomGen = new Random();
	
	public AutoClient(String serviceName, String serviceHost, int servicePort,
			ArrayList<String> transactionFile, int p_rate, int numTransactions, boolean p_rand)
			throws IOException {
		super(serviceName, serviceHost, servicePort);
		
		//p_rate is number of transactions per second.
		//We transform that in time allowed per transactions in milliseconds.
		rate = (int) ((1.f / (float) p_rate) * 1000.f);
		rand = p_rand;
		transactionsLeft = numTransactions;
		String line;
		LinkedList<LinkedList<String>> txnsLines = new LinkedList<LinkedList<String>>(); 
		
		int i = 0;
		while(i < transactionFile.size())
		{
			line = transactionFile.get(i);
			if(Transaction.getOpName(line).equals("begin"))
			{
				//Start by adding the begin line
				LinkedList<String> txnLines = new LinkedList<String>();
				txnLines.add(line);
				
				i++;
				boolean finished = false;
				while (i < transactionFile.size())
				{
					//Consume stuff while we don't hit a commit or unconditional abort
					line = transactionFile.get(i);
					txnLines.add(line);
					i++;
					
					//If we have a commit or unconditional abort, finish
					//consuming this transaction.
					if (Transaction.getOpName(line).equals("commit") ||
							(Transaction.getOpName(line).equals("abort") &&
							line.indexOf("abort") + "abort".length() == line.length())
						)
					{
						txnsLines.add(txnLines);
						finished = true;
						break;
					}
					else if(Transaction.getOpName(line).equals("begin"))
						throw new RuntimeException("Error: begin command inside a transaction "
								+ "(line " + (i + 1) + ")");
				}
				
				if (!finished)
					throw new RuntimeException("Error: unfinished transaction at end of file.");
			}
			else
			{
				throw new RuntimeException("Error: command " + line + 
						" (line " + (i + 1) + ") is not inside a transaction");
			}
		}
		
		for(LinkedList<String> txnLines: txnsLines)
			transactions.add(new Transaction(txnLines));
		
		if (transactions.size() == 0)
			throw new RuntimeException("Error: file contained no transactions.");
	}

	public void run()
	{
		int i = -1;
		while(transactionsLeft > 0)
		{
			transactionsLeft--;
			
			//Select next transaction
			if (rand)
				i = randomGen.nextInt(transactions.size());
			else
				i = (i + 1) % transactions.size();
			
			//Execute it, record time taken
			Transaction txn = transactions.get(i);
			int time = txn.ExecuteAll(proxy);
			System.out.println(time + "ms (" + txn.size + " ops: " + (time / (float) txn.size) + "ms/op)");
			
			//Sleep in order to have the proper rate of transactions.
			int stime = rate - time;
			if (stime > 5) //Don't try to sleep for less than 5ms, it's not worth it.
			{
				try
				{
					Thread.sleep(stime);
				}
				catch (Exception e)
				{
					throw new RuntimeException("Spurious wake-up", e);
				}
			}
		}
	}
	
	public static void main(String[] args) {
		if (args.length != 6 && args.length != 7)
			usageAndQuit();
		
		int servicePort = 0, transactionRate = 0, numTransactions = 0;
		String serviceName = args[0];
		String serviceHost = args[1];

		try
		{			
			servicePort = Integer.parseInt(args[2]);
			transactionRate = Integer.parseInt(args[4]);
			numTransactions = Integer.parseInt(args[5]);
		} 
		catch (NumberFormatException e)
		{
			usageAndQuit();
		}
		
		boolean randMode = true;
		
		if (args.length == 7)
		{
			if(!(args[6].equals("rand") || args[6].equals("seq")))
				usageAndQuit();
			
			randMode = args[6].equals("rand");
		}
		
		ArrayList<String> lines = new ArrayList<String>();
		try
		{
			//Obtain lines from transaction file
			BufferedReader f = new BufferedReader(new InputStreamReader(new FileInputStream(args[3])));
			String line;
			while((line = f.readLine()) != null)
			{
				line = line.trim();
				if(!line.startsWith("#") && line.length() > 0)
					lines.add(line);
			}
			
			AutoClient cli = new AutoClient(serviceName, serviceHost, servicePort, lines, 
					transactionRate, numTransactions, randMode);
			
			cli.run();
			
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.exit(-1);
		}
	}
	

	
	public static void usageAndQuit()
	{
		System.out.println("Usage: AutoClient <service-name> " 
                + "<service-host> <service-port> <transactions-file> "
                + "<transaction-rate> <num-transactions> [mode]\n\n"
                + "\ttransactions-file: file containing the transactions to be executed\n"
                + "\ttransaction-rate: target number of transactions per second\n"
                + "\tnum-transactions: number of transactions to execute in total\n"
                + "\tmode: if rand, randomly executes transactions in the file. "
                + "If seq, execute them sequentially (looping if num-transactions is "
                + "larger than the number of transactions in the file). Default rand.");
        System.exit(-1);
	}
}
