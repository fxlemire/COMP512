Authors: Frederic Lafrance, Francois-Xavier Lemire, Muhammad Yousuf Ahmad

COMP 512: Distributed Systems
School of Computer Science
McGill University

A Distributed System in Java using Web Services.
Adapted from CSE 593, University of Washington.

==========

Changes to the project structure:

 + autoruns                       contains files used for automated tests

 + src                            source code
   + client                         client code
     - AutoClient.java                Scripted client
     - Transaction.java               Transaction object used by AutoClient

 - web_template.xml               template to generate /etc/<target>/web.xml
 - middleware_environment.xml     environment for /etc/middleware/web.xml
 - server_environment.xml     environment for /etc/server/web.xml
 
==========

New Ant targets:

 - autoclient                     builds and runs the automated client

New Ant properties:

 - ${autocli.txnrate}             number of transactions per second sent by the
                                    autoclient
 - ${autocli.txnfile}             file containing the transactions that the 
                                    autoclient may execute.
 - ${autocli.ntxn}                number of transactions that the autoclient
                                    will execute.
 - ${autocli.mode}                transaction selection strategy for the
                                    autoclient (seq for sequential, rand for
                                    random)

==========

Transaction IDs:

	Transaction IDs are specified using the <id> parameter of the command.

==========

Autoclient:

	The main code for the automated client is in client/AutoClient.java.
	
	Usage:
		AutoClient <service-name> <service-host> <service-port> 
			<transactions-file> <transaction-rate> <num-transactions> [mode]
			
		service-name, service-host, service-port: same as Client
		transactions-file: location of the file containing the transactions that
			this client will be able to execute (see below for file format)
		transaction-rate: Maximum number of transactions executed per second.
		num-transactions: Number of transactions to execute. If negative, all
			transactions in the file will be executed sequentially (the value
			of mode will be ignored).
		mode: if specified, controls how the transactions to execute are chosen.
			If 'seq', transactions are executed sequentially in the file (and
			loop around if there are not enough). If 'rand', transactions are
			picked at random from the file. If num-transactions is negative,
			this parameter is ignored and all the transactions in the file are
			executed sequentially. Default mode is 'rand'.
		
==========

Transaction files for the autoclient:

	A transaction file may contain a number of transactions. Transactions are
	always specified by a start command and a corresponding commit command.
	
	Lines starting with # are ignored as comments.
	
	Commands are specified the same way as they are on the normal client. For
	instance:
	
		newflight,1,767,32,500
	
	As expected, asks to create a new flight with ID 767, 32 seats and a price
	of 500$. The transaction ID is 1.
	
	Within a transaction, it is possible to save command results and reuse them
	later. Results are stored in registers, and registers are referred to by
	the notation "%<number>" (e.g. %2 for register 2). To save the result of a
	command, specify a register followed by a comma BEFORE the command. The
	following command queries the flight 767 (in transaction 2), and stores the
	value in register 3.
	
		%3,queryflight,2,767
	
	To use a register in a command, simply specify it in place of a parameter.
	The following commands create a customer and reserve a flight for it.
	
		%1,newcustomer,1
		reserveflight,1,%1,767
	
	Naturally, the most common use of this would be to save the transaction ID:
	
		%0,start
		%1,newcustomer,%0
		#...
		commit,%0
		
	The syntax of the abort command is as follows:
	
		[register,]abort,<tid>,<a>,<op>,<b>
	
	Where op is a comparison operand (currently == or !=) and a/b can either
	be registers or any string, decimal or boolean value (note that op, a and b
	are all required). If the comparison is true, the transaction is aborted. 
	For instance, the following snippet checks if any seats are available on a 
	particular flight and aborts if not.
	
		%1,queryflight,%0,767
		abort,%0,%1,==,0
		
	Note that this syntax is purely client side. The condition is evaluated on
	the client, and if it is true, then the abort command is sent to the
	middleware. The condition must be specified. If, for test reasons, an
	unconditional abort is required, you can do something like:
	
		abort,%0,true,==,true
		
	Note that you will still need to specify a commit for the transaction, even
	if it's never going to be executed. This is because the parsing code is very
	simple.
	
==========