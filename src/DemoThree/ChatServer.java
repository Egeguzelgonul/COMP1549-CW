package DemoThree;

// The following code was based on the sample provided in the third lab

import java.io.IOException;
import java.io.*;
import java.net.*;
import java.net.Socket;
import java.net.InetAddress;
import java.util.*;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.*;

/**
 * A multithreaded chat room server. When a client connects the server requests a screen
 * name by sending the client the text "SUBMITNAME", and keeps requesting a name until
 * a unique one is received. After a client submits a unique name, the server acknowledges
 * with "NAMEACCEPTED". Then all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name. The broadcast messages are prefixed
 * with "MESSAGE".
 *
 * This is just a teaching example so it can be enhanced in many ways, e.g., better
 * logging. Another is to accept a lot of fun commands, like Slack.
 */
public class ChatServer {

    // All client names, so we can check for duplicates upon registration.
    private static Set<String> names = new HashSet<>();
    
    // All coordinator names
    private static Set<String> coordinators = new HashSet<>();
    
    // All Client IP Addresses, this is not a list considering that many clients can share the same IP
    private static List<String> ips = new ArrayList<String>();
    
    // All yes voters, used to store the names of the users who have cast a vote
    private static Set<String> yesvoters = new HashSet<>();
    
    // All yes voters, used to store the names of the users who have cast a vote
    private static Set<String> novoters = new HashSet<>();
    
    // List of dethroned users, these users are not allowed to be nominated as coordinators unless the server lifetime is up
    private static Set<String> dethroned = new HashSet<>();

    // The set of all the print writers for all the clients, used for broadcast.
    private static Set<PrintWriter> writers = new HashSet<>();
    
    // The server socket and the port
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running...");
        ExecutorService pool = Executors.newFixedThreadPool(500);
        try (ServerSocket listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        }
    }
    
    // A simple function to assign a new coordinator
    // If every user has been dethroned, the list is cleared, as there are no coordinator candidates
    public static void findcoordinator() {
    	if (dethroned.equals(names)) {
    		dethroned.clear();
    		for (PrintWriter writer : writers) {
                writer.println("MESSAGE " + "System: " + " all users are now requalified to be coordinators");
            }
    	}
    	// Server needs to have at least one member to be qualified as a coordinator
    	if (!names.isEmpty() && coordinators.isEmpty()) {
    		for (String i : names) {
    			// The user must not be dethroned
    		    if (!dethroned.contains(i)) {
    		    	coordinators.add(i);
    		    	for (PrintWriter writer : writers) {
    		    		// Announcing the change
                        writer.println("MESSAGE " + i + " is now the coordinator");
                    }
                	System.out.println(i + " is now the coordinator");
    		    	break;
    		    }
    		}
        }
    }
    /**
     * The client handler task.
     */
    private static class Handler implements Runnable {
    	// Name refers to the name of the client
        // The client's ip is requested directly from the client itself just like the name
    	// Printwriter allows the user to interract with the chat, like hitting the enter key to send a message
    	// Printwriter sends messages from the server to the client
    	// Scanner receives messages from the client
    	// Target is used to store the name of the target of the vote, it is static since any reset could disturb the voting process
    	// "told" is used to store the recepient of the private message
        private String name;
        private String ip;
        private Socket socket;
        private Scanner in;
        private PrintWriter out;
        private static String target;
        private static String told;
        private int currentindex = 0;
        private int desiredindex = 0;
        private int senderindex = 0;
        // currentindex, desiredindex and senderindex are to iterate through the list of printwriters for the /whisper command

        /**
         * Constructs a handler thread, squirreling away the socket. All the interesting
         * work is done in the run method. Remember the constructor is called from the
         * server's main method, so this has to be as short as possible.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }
        
        // A simple function to conclude the voting process
        public static void concludevoting( ) {
        	// if the yesvoters constitute more than half of the server population, the voting is decisive and therefore it concludes
        	if (yesvoters.size() > names.size()/2) {
            	coordinators.remove(target);
            	dethroned.add(target);
            	for (PrintWriter writer : writers) {
            		writer.println("MESSAGE " + "System: " + target + " has been dethroned");
                }
            	//The hashsets for voters are cleared, it is important as this is used to determine whether there is a voting process
            	yesvoters.clear();
            	novoters.clear();
            }
            
        	// if the novoters constitute more than half of the server population, the voting is indecisive and therefore it concludes
            if (novoters.size() > names.size()/2) {
            	for (PrintWriter writer : writers) {
            		writer.println("MESSAGE " + "System: " + target + " reigns for another day");
                }
            	yesvoters.clear();
            	novoters.clear();
            }
            // A new coordinator will be found, if applicable or needed
            findcoordinator();
        }

        /**
         * Services this thread's client by repeatedly requesting a screen name until a
         * unique one has been submitted, then acknowledges the name and registers the
         * output stream for the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // Request the client's IP, in a very similar fashion to the name
                out.println("GETIP");
                ip = in.nextLine();

                // Keep requesting a name until we get a unique one.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.nextLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (names) {
                        if (!name.isEmpty() && !names.contains(name)) {
                            names.add(name);
                            ips.add(ip);
                            break;
                        }
                    }
                }

                // Now that a successful name has been chosen, add the socket's print writer
                // to the set of all writers so this client can receive broadcast messages.
                // But BEFORE THAT, let everyone else know that the new person has joined!
                
                // The writer should be added first, so that the "has joined" message would be visible to themselves
                writers.add(out);
                
                out.println("NAMEACCEPTED " + name);
                for (PrintWriter writer : writers) {
                	// The welcome message
                    writer.println("MESSAGE " + "Welcome " + name + "!");
                    writer.println("MESSAGE " + "We hope you brought pizza");
                    writer.println("MESSAGE " + "Type /help to list down all the commands");
                }
                
                // The writer which happens to join first gets notified, this is applicable multiple times during the server lifetime
                if (names.size() == 1) {
                	for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + names.stream().findFirst().get() + " is the first one to join");
                    }
                	System.out.println(names.stream().findFirst().get() + " is the first one to join");
                }
                
                // If the naming process is successful, coordinator check occurs
                // Check if there's a coordinator, if not, assign
                findcoordinator();

                // Accept messages from this client and broadcast them.
                while (true) {
                	// The server will attempt to nominate a coordinator at all times
                	findcoordinator();
                	
                	// The user input
                    String input = in.nextLine();
                    
                    // Executable commands, mainly for debugging purposes
                    // The quit command, shuts down the client
                    if (input.toLowerCase().startsWith("/quit")) {
                        return;
                    }
                    // Normal messages
                    // All messages are broadcasted, UNLESS they start with /whisper which means they are meant to be private
                    for (PrintWriter writer : writers) {
                    	if (!input.toLowerCase().startsWith("/whisper")) {
                        writer.println("MESSAGE " + name + ": " + input);
                    	}
                    }
                    // The help command, lists down all the commands
                    if (input.toLowerCase().startsWith("/help")) {
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: ");
                            writer.println("MESSAGE " + "* /whisper - sends a private message to a user");
                            writer.println("MESSAGE " + "* /quit - shuts the client session");
                            writer.println("MESSAGE " + "* /userlist - list of current users");
                            writer.println("MESSAGE " + "* /crdntrlist - list of current coordinators");
                            writer.println("MESSAGE " + "* /dthlist - list of dethroned users");
                            writer.println("MESSAGE " + "* /serverip - displays the server's ip");
                            writer.println("MESSAGE " + "* /clientip - displays all the clients' ip -  requires coordinator status");
                            writer.println("MESSAGE " + "* /bestow - grants coordinator status to a user -  requires coordinator status");
                            writer.println("MESSAGE " + "* /dethrone - Starts a voting process to dethrone a coordinator");
                        }
                    }
                    // Send a private message to a user
                    if (input.toLowerCase().startsWith("/whisper")) {
                    	input = input.replace("/whisper ","");
                    	String[] toldsplit = input.split("\\s+");
                    	told = toldsplit[0];
                    	told.toString();
                    	// the command part is erased, then, the input is split by the spaces
                    	// the first part, which has to be the private message's recipient, has its name stored in "told"
                    	
                    	// the recipients name is erased from the remaining input, leaving just the message behind
                    	input = input.replace(told + " " ,"");
                    	
                    	currentindex = 0;
                    	// iterating through the list of all active users
                    	for (String element :names) {
                    		// the sender's index is saved if the name matches the client's
                            if (element.equals(name)) {
                            	senderindex = currentindex;
                            }
                            // the index which the recipient's name is held is saved
                            if (element.equals(told)) {
                    			desiredindex = currentindex;
                    		}
                            currentindex++;
                        }
                    	currentindex = 0;
                    	// iterating through the list of PrintWriters
                    	// the PrintWriters do not have any such property such as name
                    	// However, their order is in sync with the order of names list, therefore knowing the index makes this possible
                    	for (PrintWriter writer : writers) {
                    		// The part sent specifically to the recipient only
                            if (desiredindex == currentindex && names.contains(told)) {
                            	writer.println("MESSAGE " + "Whisper from " + name + ": " + input);
                            }
                            // The part sent specifically to the sender only, as the sender should be able to see their own messages too
                            if (senderindex == currentindex && names.contains(told)) {
                            	writer.println("MESSAGE " + "Whispered to " + told + ": " + input);
                            }
                            // If there is no such recipient, the error message is sent to the sender instead
                            if (senderindex == currentindex && !names.contains(told)) {
                            	writer.println("MESSAGE " + "System: Invalid input, name not found");
                            }
                            currentindex++;
                        }
                    	//desiredindex = 0;
                    	//senderindex = 0;
                    }
                    // List the current users
                    if (input.toLowerCase().startsWith("/userlist")) {
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + names);
                        }
                    }
                    // List coordinators
                    if (input.toLowerCase().startsWith("/crdntrlist")) {
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + coordinators);
                        }
                    }
                    // List dethroned users
                    if (input.toLowerCase().startsWith("/dthlist")) {
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + dethroned);
                        }
                    }
                    // Print the Server's IP address
                    if (input.toLowerCase().startsWith("/serverip")) {
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + InetAddress.getLocalHost().getHostAddress());
                        }
                    }
                    // Print the Clients' IP address
                    // Requires the coordinator status
                    if (input.toLowerCase().startsWith("/clientip") && coordinators.contains(name)) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + ips);
                        }
                    } else if (input.toLowerCase().startsWith("/clientip") && !coordinators.contains(name)) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Only coordinators are allowed to do that");
                        }
                    }
                    // Grant the coordinator status to a certain user
                    // Only coordinators are allowed to do this
                    if (input.toLowerCase().startsWith("/bestow") && coordinators.contains(name)) {
                    	if (names.contains(input.replace("/bestow ",""))) {
                    		coordinators.add(input.replace("/bestow ",""));
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + input.replace("/bestow ","") + " is now the coordinator");
                            }
                    	}
                    	else {
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "Invalid input, name not found");
                            }
                    	}
                    } else if (input.toLowerCase().startsWith("/bestow") && !coordinators.contains(name)) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Only coordinators are allowed to do that");
                        }
                    }
                    // Vote to revoke the coordinator status of an user
                    // This starts the voting process against the targeted user
                    // This is to allow to dethrone an idling coordinator
                    // The voting process will only start if there is no other voting process taking place
                    // In other words, the voters list must be empty
                    if (input.toLowerCase().startsWith("/dethrone") && yesvoters.isEmpty()) {
                    	target = input.replace("/dethrone ","");
                    	if (coordinators.contains(target)) {
                    		// The first voter can ONLY be added through this command, which counts the voting process as started
                        	yesvoters.add(name);
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "The voting process to dethrone " + target + " has started");
                            }
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "Type /y or /n to cast your vote");
                            }
                    		// The user to start the voting process votes automatically yes
                    		for (PrintWriter writer : writers) {
                                writer.println("MESSAGE " + "System: " + name + " has voted yes " + "[" + yesvoters.size() + "/" + names.size() + "]");
                            }
                    	} else if (!coordinators.contains(target)) {
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "Invalid input, the name not found in coordinator list");
                            }
                    	}
                    } else if (input.toLowerCase().startsWith("/dethrone") && !yesvoters.isEmpty()) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, voting already in process");
                        }
                    }
                    
                    // The command to vote for yes
                    if (input.toLowerCase().startsWith("/y") && !yesvoters.isEmpty() && !yesvoters.contains(name)) {
                    	yesvoters.add(name);
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + name + " has voted yes " + "[" + yesvoters.size() + "/" + names.size() + "]");
                        }
                    // If there is no ongoing voting process, the request is invalid
                    } else if (input.toLowerCase().startsWith("/y") && yesvoters.isEmpty()) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, there is no voting process");
                        }
                    // No user may vote twice
                    } else if (input.toLowerCase().startsWith("/y") && yesvoters.contains(name)) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, you have already voted");
                        }
                    }
                    
                    // The command to vote for no, it functions almost identically to the yes command
                    // However, the voting process is determined only by the yesvoters list
                    if (input.toLowerCase().startsWith("/n") && !yesvoters.isEmpty() && !novoters.contains(name)) {
                    	novoters.add(name);
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + name + " has voted no " + "[" + novoters.size() + "/" + names.size() + "]");
                        }
                    } else if (input.toLowerCase().startsWith("/n") && yesvoters.isEmpty()) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, there is no voting process");
                        }
                    } else if (input.toLowerCase().startsWith("/n") && novoters.contains(name)) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, you have already voted");
                        }
                    }
                    // The vote should be concluded, whenever applicable;
                    concludevoting( );
                }
            } catch (Exception e) {
                //System.out.println(e);
            } finally {
                if (out != null) {
                    writers.remove(out);
                }
                if (name != null) {
                    System.out.println(name + " is leaving");
                    for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + name + " has left");
                    }
                    // Whenever a user leaves, their name is removed from all the lists
                    // With the exception of dethroned list, as users should not be able to regain the qualification to be a coordinator simply by rejoining
                    names.remove(name);
                    coordinators.remove(name);
                    yesvoters.remove(name);
                    novoters.remove(name);
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}
