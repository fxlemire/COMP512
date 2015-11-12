import paramiko
import ast
import os
import time

VERBOSE = True
DEBUG = True

# Note: throughout this entire script, we assume that things will go as planned,
# which might obviously not be the case...


#  ====== Names for the configuration options ======

# Hosts for the RMs and middleware
CFG_RM_FLIGHT_HOST = "ant.rm.flight.host"
CFG_RM_CAR_HOST = "ant.rm.car.host"
CFG_RM_ROOM_HOST = "ant.rm.room.host"
CFG_RM_CUSTOMER_HOST = "ant.rm.customer.host"
CFG_MW_HOST = "ant.mw.host"

# Client definitions and list of potential hosts
CFG_CLIENT_DEFS = "client_defs"
CFG_CLIENT_HOSTS = "client_hosts"

# Logging
CFG_LOG_DIR = "ant.logdir"
CFG_LOG_DEST = "logs_destination"

# Machine account
CFG_USER = "user"
CFG_PASS = "pass"

# Where is the code
CFG_CODE = "code"

# ===

basePropertyFile = ""

def ifNotPresent(key, prev, dict):
	if prev != "":
		return prev
	if key not in dict:
		return key
	
	return ""

def consumeOutput(stdout, stderr):
	outputs = stdout.read()
	errors = stderr.read()
	if VERBOSE:
		print "[stdout]",
		print outputs
		
		print "[stderr]", 
		print errors
		print
		
	return (outputs, errors)

'''
Execute a command with potentially some basic input and discard any output.
Returns true if the command executed successfully, false otherwise.
'''
def simpleExec(ssh, cmd, input=None, ignoreOutput=False):
	if VERBOSE:
		print cmd,
		if input is not None:
			print "\t<" + str(input) + ">"
		else:
			print
		
	stdin, stdout, stderr = ssh.exec_command(cmd)
	if input != None:
		for inp in input:
			stdin.write(inp)
			stdin.flush()
	
	if not ignoreOutput:
		_, errors = consumeOutput(stdout, stderr)
		return errors is None
	else:
		return true

'''
Start the clients specified in the config file, distributed across the
machines that are also in the config file.
'''
def startClients(conf):
	if VERBOSE:
		print "Starting clients..."

	i = 0
	
	# Create one client per client specification
	# IDs start at 5 because we have the MW and 4 RMs
	for id, client in enumerate(conf[CFG_CLIENT_DEFS], 5):
		ssh = paramiko.SSHClient()
		ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
		ssh.connect(conf[CFG_CLIENT_HOSTS][i], username=conf[CFG_USER], 
					password=conf[CFG_PASS])
		
		if VERBOSE:
			print "Client " + str(id) + " at " + conf[CFG_CLIENT_HOSTS][i]
		startClient(id, ssh, conf, client)
		ssh.close()
		
		i += 1
		if i == len(conf[CFG_CLIENT_HOSTS]):
			i = 0

'''
Start one client in the background, and return its PID
'''
def startClient(id, ssh, conf, clientConf):
	# Generate the swarm properties file for this client
	props = ""
	for prop, val in clientConf.iteritems():
		props += "autocli." + prop + "=" + val + "\n"
	
	uploadFile(ssh, conf[CFG_CODE] + "/swarm_" + str(id) + ".properties",
				basePropertyFile + props)
				
	# Execute the autoclient there.
	cmd = "ant -buildfile " + conf[CFG_CODE] + "/build.xml -Dswarmid=" + str(id) + " autoclient"
	startLongProcess(ssh, cmd)
	
	# Caller closes ssh
	
'''
Start a process without waiting for it to finish, and return its pid
'''
def startLongProcess(ssh, cmd):
	if VERBOSE:
		print "Starting long process \"" + cmd + "\"..."
	i,o,r = ssh.exec_command("nohup " + cmd + "&")


'''
Create a file with the given contents and upload it to the specified location.
'''
def uploadFile(ssh, location, contents):
	
	sftp = ssh.open_sftp()
	f = sftp.file(location, mode='w')
	f.write(contents)
	f.close()
	sftp.close()

'''
Start a webservice
'''
def startWS(host, which, id):
	if VERBOSE:
		print "Starting WS " + which
	
	ssh = paramiko.SSHClient()
	ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
	ssh.connect(host, username=conf[CFG_USER], password=conf[CFG_PASS])

	# Upload the basic properties
	uploadFile(ssh, conf[CFG_CODE] + "/swarm_" + str(id) + ".properties",
				basePropertyFile)
	
	# Execute the ant task for this RM
	cmd = "ant -buildfile " + conf[CFG_CODE] + "/build.xml -Dswarmid=" + str(id) + " " + which
	startLongProcess(ssh, cmd)
	ssh.close()
	
	
# ======= Main =======

with open("swarm.config") as config:
	conf = ast.literal_eval(config.read())

# Check existence of options
msg = ""
msg = ifNotPresent(CFG_RM_FLIGHT_HOST, msg, conf)
msg = ifNotPresent(CFG_RM_CAR_HOST, msg, conf)
msg = ifNotPresent(CFG_RM_ROOM_HOST, msg, conf)
msg = ifNotPresent(CFG_RM_CUSTOMER_HOST, msg, conf)
msg = ifNotPresent(CFG_MW_HOST, msg, conf)

msg = ifNotPresent(CFG_CLIENT_DEFS, msg, conf)
msg = ifNotPresent(CFG_CLIENT_HOSTS, msg, conf)

msg = ifNotPresent(CFG_LOG_DIR, msg, conf)
msg = ifNotPresent(CFG_LOG_DEST, msg, conf)

msg = ifNotPresent(CFG_USER, msg, conf)
msg = ifNotPresent(CFG_PASS, msg, conf)

msg = ifNotPresent(CFG_CODE, msg, conf)

if msg != "":
	print "Please add option " + msg + " to swarm.config"
	exit()

for prop, val in conf.iteritems():
	if prop.startswith("ant."):
		basePropertyFile += prop.replace("ant.", "") + "=" + val + "\n"
			
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(conf[CFG_MW_HOST], username=conf[CFG_USER], password=conf[CFG_PASS])

# Create log directories (local and remote)
simpleExec(ssh, "mkdir -p " + conf[CFG_LOG_DIR])
ssh.close()
os.makedirs(conf[CFG_LOG_DEST])

# Start the RMs and middleware
if VERBOSE:
	print "Starting the resource managers..."
	
startWS(conf[CFG_RM_FLIGHT_HOST], "rm_flight", 1)
startWS(conf[CFG_RM_ROOM_HOST], "rm_room", 2)
startWS(conf[CFG_RM_CAR_HOST], "rm_car", 3)
startWS(conf[CFG_RM_CUSTOMER_HOST], "rm_customer", 4)

if VERBOSE:
	print "Starting the middleware..."
startWS(conf[CFG_MW_HOST], "middleware", 0)

if VERBOSE:
	print "Sleeping to let the middleware start before launching clients..."
time.sleep(15)

startClients(conf)

# Wait until the clients should be stopped
max = 0
for client in conf[CFG_CLIENT_DEFS]:
	amount = int(client["ntxn"]) / int(client["txnrate"])
	if amount > max:
		max = amount

# Perhaps everyone is just reading from their txn files. In this case let's just
# be conservative. Half a minute should be enough.
if max == 0:
	max = 30

if VERBOSE:
	print "Sleeping for " + str(max) + " seconds... good night!"

time.sleep(max)

if VERBOSE:
	print "Collecting logs..."

# Get and clear the logs
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(conf[CFG_MW_HOST], username=conf[CFG_USER], password=conf[CFG_PASS])

sftp = ssh.open_sftp()
for logfile in sftp.listdir(conf[CFG_LOG_DIR]):
	remote = conf[CFG_LOG_DIR] + "/" + logfile
	sftp.get(remote, conf[CFG_LOG_DEST] + "/" + logfile)
	
	# TODO remove this only after the things have been quit
	#sftp.remove(remote)
	if VERBOSE:
		print "Collected " + remote

# TODO Same comment as above
#sftp.rmdir(conf[CFG_LOG_DIR])

# TODO Remove swarm.properties files, actually quit