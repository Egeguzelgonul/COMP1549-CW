package DemoThree;

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
    
    // All Client IP Addresses
    private static List<String> ips = new ArrayList<String>();
    
    // All yes voters, used to store the names of the users who have cast a vote
    private static Set<String> yesvoters = new HashSet<>();
    
 // All yes voters, used to store the names of the users who have cast a vote
    private static Set<String> novoters = new HashSet<>();

     // The set of all the print writers for all the clients, used for broadcast.
    private static Set<PrintWriter> writers = new HashSet<>();

    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running...");
        ExecutorService pool = Executors.newFixedThreadPool(500);
        try (ServerSocket listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        }
    }

    /**
     * The client handler task.
     */
    private static class Handler implements Runnable {
        private String name;
        private String ip;
        private Socket socket;
        private Scanner in;
        private PrintWriter out;
        private static String target;

        /**
         * Constructs a handler thread, squirreling away the socket. All the interesting
         * work is done in the run method. Remember the constructor is called from the
         * server's main method, so this has to be as short as possible.
         */
        public Handler(Socket socket) {
            this.socket = socket;
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
                
                // Request the client's IP
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
                    writer.println("MESSAGE " + name + " has joined");
                }
                
                // The writer which happens to join first gets notified, this is applicable multiple times during the server lifetime
                if (names.size() == 1) {
                	for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + names.stream().findFirst().get() + " is the first one to join");
                    }
                	System.out.println(names.stream().findFirst().get() + " is the first one to join");
                }
                
                // If the naming process is successful, coordinator check occurs
                // Check if there's a coordinator, if not, assign the first name in the list
                if (coordinators.isEmpty() && !names.isEmpty()) {
                	coordinators.add(names.stream().findFirst().get());
                	for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + names.stream().findFirst().get() + " is now the coordinator");
                    }
                	System.out.println(names.stream().findFirst().get() + " is now the coordinator");
                }

                // Accept messages from this client and broadcast them.
                while (true) {
                    String input = in.nextLine();
                    // Executable commands, mainly for debugging purposes
                    
                    // The quit command, shuts down the client
                    if (input.toLowerCase().startsWith("/quit")) {
                        return;
                    }
                    // Normal messages
                    for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + name + ": " + input);
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
                    // Print the Server's IP address
                    if (input.toLowerCase().startsWith("/serverip")) {
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + InetAddress.getLocalHost().getHostAddress());
                        }
                    }
                    // Print the Clients' IP address
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
                    if (input.toLowerCase().startsWith("/bestow") && coordinators.contains(name)) {
                    	if (names.contains(input.replace("/bestow ",""))) {
                    		coordinators.add(input.replace("/bestow ",""));
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + input.replace("/bestow ","") + " is now the coordinator");
                            }
                    	}
                    	else {
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "Invalid input");
                            }
                    	}
                    } else if (input.toLowerCase().startsWith("/bestow") && !coordinators.contains(name)) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Only coordinators are allowed to do that");
                        }
                    }
                    // Vote to revoke the coordinator status of an user
                    if (input.toLowerCase().startsWith("/dethrone") && yesvoters.isEmpty()) {
                    	target = input.replace("/dethrone ","");
                    	if (name == target) {
                    		coordinators.remove(target);
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + name + " has been dethroned");
                            }
                    	} else if (coordinators.contains(target) && name != target) {
                        	yesvoters.add(name);
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "The voting process to dethrone " + target + " has started");
                            }
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "Type /y or /n to cast your vote");
                            }
                    		for (PrintWriter writer : writers) {
                                writer.println("MESSAGE " + "System: " + name + " has voted yes " + "[" + yesvoters.size() + "/" + names.size() + "]");
                            }
                    	} else if (!coordinators.contains(target)) {
                    		for (PrintWriter writer : writers) {
                        		writer.println("MESSAGE " + "System: " + "Invalid input");
                            }
                    	}
                    } else if (input.toLowerCase().startsWith("/dethrone") && !yesvoters.isEmpty()) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, voting already in process");
                        }
                    }
                    
                    if (input.toLowerCase().startsWith("/y") && !yesvoters.isEmpty() && !yesvoters.contains(name)) {
                    	yesvoters.add(name);
                    	for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + "System: " + name + " has voted yes " + "[" + yesvoters.size() + "/" + names.size() + "]");
                        }
                    } else if (input.toLowerCase().startsWith("/y") && yesvoters.isEmpty()) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, there is no voting process");
                        }
                    } else if (input.toLowerCase().startsWith("/y") && yesvoters.contains(name)) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + "Invalid request, you have already voted");
                        }
                    }
                    
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
                    
                    if (yesvoters.size() > names.size()/2) {
                    	coordinators.remove(target);
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + target + " has been dethroned");
                        }
                    	yesvoters.clear();
                    	novoters.clear();
                    }
                    
                    if (novoters.size() > names.size()/2) {
                    	for (PrintWriter writer : writers) {
                    		writer.println("MESSAGE " + "System: " + target + " reigns for another day");
                        }
                    	yesvoters.clear();
                    	novoters.clear();
                    }
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
                    names.remove(name);
                    coordinators.remove(name);
                    yesvoters.remove(name);
                    novoters.remove(name);
                }
                if (coordinators.isEmpty() && !names.isEmpty()) {
                	coordinators.add(names.stream().findFirst().get());
                	for (PrintWriter writer : writers) {
                        writer.println("MESSAGE " + names.stream().findFirst().get() + " is now the coordinator");
                    }
                	System.out.println(names.stream().findFirst().get() + " is now the coordinator");
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}
