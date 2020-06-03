import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;

/**
 * Router Class
 * 
 * Router implements Dijkstra's algorithm for computing the minumum distance to all nodes in the network
 * @author      Rumen Kasabov
 * @version     1.0
 *
 */
public class Router {
	
	private String peerIP;
	private int routerID;
	private int portNumber;
	private String configFile;
	private int neighbourUpdate;
	private int routeUpdate;
	
	private ArrayList<String> neighbours = new ArrayList();
	private ArrayList<int[]> linkStateVectors = new ArrayList();
	private LinkState linkState = null;
	private int routerCount = 0;
	private Timer timer;
	private DatagramSocket socket = null;
	private int costs[];
	
	private int[] distancevector;
	private int[] prev;
	/**
     	* Constructor to initialize the program 
     	* 
     	* @param peerip		IP address of other routers (we assume that all routers are running in the same machine)
     	* @param routerid	Router ID
     	* @param port		Router UDP port number
     	* @param configfile	Configuration file name
     	* @param neighborupdate	link state update interval - used to update router's link state vector to neighboring nodes
        * @param routeupdate 	Route update interval - used to update route information using Dijkstra's algorithm
 
     */
	public Router(String peerip, int routerid, int port, String configfile, int neighborupdate, int routeupdate) {
	
		peerIP = peerip;
		routerID = routerid;
		portNumber = port;
		configFile = configfile;
		neighbourUpdate = neighborupdate;
		routeUpdate = routeupdate;
			
	}
	
    	/**
     	*  Compute route information based on Dijkstra's algorithm and print the same
     	* 
     	*/
	public void compute() {

		FileInputStream fileInputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader bufferedReader = null;
		
		String currentLine = "";
		byte[] data = new byte[linkState.MAX_SIZE];
		
	try {
		
		File file = new File(configFile);
		
		if (file.exists()) {
			
			//Input streams to read config file
			fileInputStream = new FileInputStream(file);	
			inputStreamReader = new InputStreamReader(fileInputStream, Charset.forName("UTF-8"));
			bufferedReader = new BufferedReader(inputStreamReader);
			
			//Read each line of config file and put it in an array
			while ((currentLine = bufferedReader.readLine()) != null) {
				
				neighbours.add(currentLine);
				
			}
			
		
			//Get number of total nodes
			routerCount = Integer.parseInt(neighbours.get(0));
			
			
			timer = new Timer();
			InetAddress serverHostName = InetAddress.getByName(peerIP);

			socket = new DatagramSocket(portNumber,  serverHostName); 
			DatagramPacket receivePacket = new DatagramPacket(data, data.length);
			
			computeCosts();
		
			//Send link state message to other routers
			processUpdateNeighbor();
			processUpdateRoute();
			
			while (true) {
				

				//Listen in for incoming packets containing link state message from other routers
				socket.receive(receivePacket);
								
				//Update structure and send link state to other neighbours (essentially spreading it)
				processUpDateDS(receivePacket);
				
				
			}
					

			
		}
		
		else {
			System.out.println("The file that you have provided does not exist");
		}
		
		
		
	} 
	
	catch (Exception e) {
		e.printStackTrace();
	}

	}
	
	/*
	 * Creates a link state vector of all the costs for the current node
	 */
	public void computeCosts() {
		
		costs = new int[routerCount];
		
		//Initialize state vector
		for (int i = 0; i < routerCount; i++) {
			costs[i] = 999;
		}
		
		int counter = 1;
		int index = 0;
		while (counter < neighbours.size()) {
			
			String[] currentNeighbour =  neighbours.get(counter).split(" ");
			
			int currentID = Integer.parseInt(currentNeighbour[1]);
			
			if (currentID == index) {
				costs[index] = Integer.parseInt(currentNeighbour[2]);
				counter++;
			}
			
			index++;
			

		}
		
		costs[routerID] = 0;
		
			
	}
	
	// Update data structure(s).
	// Forward link state message received to neighboring nodes
	// based on broadcast algorithm used.
		
	public synchronized void processUpDateDS(DatagramPacket receivepacket) throws IOException, InterruptedException
	{

		
		LinkState receivedState = new LinkState(receivepacket);
		
		int originID = receivedState.sourceId;
		
		int[] receivedCosts = receivedState.getCost();
		boolean exists = false;
	
		
		
		//Broadcasted link state vectors are added up in an array until the node
		//has all the vectors from other notes 
		if (linkStateVectors.size() < routerCount) {
			
			int counter = 0;
			
			//Make sure link state vector is not already in the array before adding it
			while (counter < linkStateVectors.size()) {
				if (Arrays.equals(linkStateVectors.get(counter), receivedCosts)) {
					exists = true;
					
				}
				counter++;
			}
			
			if (!exists) {
				linkStateVectors.add(receivedCosts);
			}
		}
		
		//Forward the received link state vector to the current nodes' neighbours
		InetAddress serverHostName = InetAddress.getByName(peerIP);
		for (int index = 1; index < neighbours.size(); index++) {

			String[] currentNeighbour =  neighbours.get(index).split(" ");
			
			//Get port number and id for neighbour we are sending to
			int currentPortNumber = Integer.parseInt(currentNeighbour[3]);
			int currentDestinationID = Integer.parseInt(currentNeighbour[1]);
			
			//System.out.println("CURRENTLY SENDING TO: " + currentDestinationID + "/" + currentPortNumber + " link state vector " + costs[0] +" " + costs[1] +" "+ costs[2]+" " + costs[3] +" "+ costs[4] );

			//Create link state message with current known link state vector (costs)
			linkState = new LinkState(routerID, currentDestinationID, receivedCosts);
			
			//socket = new DatagramSocket(currentPortNumber, serverHostName);
			DatagramPacket packet = new DatagramPacket(linkState.getBytes(), linkState.getBytes().length, serverHostName, currentPortNumber);
			socket.send(packet);	
		}
	}
	public synchronized void processUpdateNeighbor() throws IOException{
	// Send node’s link state vector to neighboring nodes as link
	// state message.
	// Schedule task if Method-1 followed to implement recurring
	// timer task.
		
		
		InetAddress serverHostName = InetAddress.getByName(peerIP);

	
		for (int index = 1; index < neighbours.size(); index++) {

			String[] currentNeighbour =  neighbours.get(index).split(" ");
			
			//Get port number and id for neighbour we are sending to
			int currentPortNumber = Integer.parseInt(currentNeighbour[3]);
			int currentDestinationID = Integer.parseInt(currentNeighbour[1]);
			

			//Create link state message with current known link state vector (costs)
			linkState = new LinkState(routerID, currentDestinationID, costs);
			
			//socket = new DatagramSocket(currentPortNumber, serverHostName);
			DatagramPacket packet = new DatagramPacket(linkState.getBytes(), linkState.getBytes().length, serverHostName, currentPortNumber);
			socket.send(packet);

			
			
		}

		//Schedule to send node’s link state vector to neighboring nodes every 1000 ms
		timer.schedule(new BroadcastHandler(this), neighbourUpdate);
	
	
		
	}
	
	// If link state vectors of all nodes received,
	// Yes => Compute route info based on Dijkstra’s algorithm
	// and print as per the output format.
	 // No => ignore the event.
	// Schedule task if Method-1 followed to implement recurring
	// timer task
	public synchronized void processUpdateRoute(){
		
		
		int [][] orderedCosts = new int [routerCount][routerCount];
	    if (linkStateVectors.size() == routerCount) {

	        for (int i = 0; i < routerCount; i++){
	        	
	            for(int j = 0; j < routerCount; j++) { 
	            	
	                if (linkStateVectors.get(i)[j] == 0) {
	                	
	                    for(int k = 0; k < routerCount; k++) {
	                        orderedCosts[j][k]=linkStateVectors.get(i)[k];
	                    }
	                }
	            }
	        }
	        dijkstraAlgorithm(orderedCosts);
	    }
	    

	    //Schedule this for every 10 seconds
		timer.schedule(new DijkstraHandler(this), routeUpdate);
	    
	}
	    
		/*Gets the minimum distance from origin node for each node and puts it into distance array for printing
		 * @param orderedCosts (the link vector costs of each node to sort)
		 * */
	    public void dijkstraAlgorithm(int [][] orderedCosts) {
	    	
	         boolean [] visited = new boolean [routerCount];
	         int [] previous = new int [routerCount];                 
	         int [] distance = new int [routerCount];    
	
	         for (int i = 0; i < routerCount; i++) {   
	        	 
	        distance[i] = 999;
	        visited[i] = false;
	         
	         }
	         
	         distance[routerID] = 0;

	         //Get min the minimum vertex to source node
	         for (int index = 0; index < routerCount -1; index++) {

	           int minVertex = distanceCalculator(distance, visited);
	           visited[minVertex] = true;

	           for (int vertexIndex = 0; vertexIndex < routerCount; vertexIndex++) {
	        	   
	        	  //Make sure its not been previously visited or 0 or infinite or less than what is in the distance array
	             if (!visited[vertexIndex] && 
	            		 (orderedCosts[minVertex][vertexIndex] != 0) && 
	            		 distance[minVertex] != 999 && 
	            		 ((distance[minVertex] + orderedCosts[minVertex][vertexIndex]) < distance[vertexIndex])) 
	             {
	            	 
	            	 //Add new minimum distance to origin node in distance array
	                distance[vertexIndex] = distance[minVertex] + orderedCosts[minVertex][vertexIndex];
	             }
	           }
	         }
	         
	 	  	/**** You may use the follwing piece of code to print routing table info *******/
	 		
	     	System.out.println("Routing Info");
	     	System.out.println("RouterID \t Distance \t Prev RouterID");
	     	for(int i = 0; i < routerCount; i++)
	       	{
	       		System.out.println(i + "\t\t   " + distance[i] +  "\t\t\t" +  previous[i]);
	       	}
	 	
	  	/*******************/
	    }



	    //Helper method to calculate distance
	    public int distanceCalculator(int distance[], boolean visited[]) {
	    	
		   int minimumIndex = 0;
	       int minimum = 999;

	       for (int i = 0; i < routerCount; i++)
	         if (visited[i] == false && distance[i] <= minimum)
	         {
	             minimum = distance[i];
	             minimumIndex = i;
	         }
	       return minimumIndex;
	    }
	

	/* A simple test driver 
     	
	*/
	public static void main(String[] args) {
		
		String peerip = "127.0.0.1"; // all router programs running in the same machine for simplicity
		String configfile = "";
		int routerid = 999;
                int neighborupdate = 1000; // milli-seconds, update neighbor with link state vector every second
		int forwardtable = 10000; // milli-seconds, print route information every 10 seconds
		int port = -1; // router port number
	
		// check for command line arguments
		if (args.length == 3) {
			// either provide 3 parameters
			routerid = Integer.parseInt(args[0]);
			port = Integer.parseInt(args[1]);	
			configfile = args[2];
		}
		else {
			System.out.println("wrong number of arguments, try again.");
			System.out.println("usage: java Router routerid routerport configfile");
			System.exit(0);
		}

		
		Router router = new Router(peerip, routerid, port, configfile, neighborupdate, forwardtable);
		
		System.out.printf("Router initialized..running");
		System.out.println();
		router.compute();
	}

}
