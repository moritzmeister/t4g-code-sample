package org.master.eit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;


public class WoCoClient {

	private int dSize;
	private Socket sHandle;
	private BufferedReader sInput;
	private BufferedWriter sOutput;
	private ArrayList<Float> respTime;
	private int cntSincePrint;
	private long timeLastPrint;
	private long timeCreate;
	private static boolean DEBUG = true;
	
	/**
	 * Function to generate a document based on the hardcoded example file. 
	 * @param length Length of the document in bytes.
	 * @param seed This random seed is used to start reading from different offsets
	 * in the file every time a new document is generated. Could be useful for debugging
	 * to return to a problematic seed.
	 * @return Returns the document which is encoded as a String 
	 * @throws IOException
	 */
	private static String generateDocument(int length, int seed) throws IOException {
		
        String fileName = "input.html";
        String line;
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(fileName));

        while((line = br.readLine()) != null) {
            sb.append(line.trim()+" ");
        }   

        br.close();
                
        String ref = sb.toString();
		
		sb = new StringBuilder(length);
		int i;
		
		for (i=0; i<length; i++) {
			sb.append(ref.charAt((i+seed)%ref.length()));								
		}
		
		//we need to remove all occurences of this special character! 
		return sb.substring(0).replace(WoCoServer.SEPARATOR, '.');
		
	}
	
	/**
	 * Instantiates the client.
	 * @param serverAddress IP address or hostname of the WoCoServer.
	 * @param serverPort Port number of the server.
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public WoCoClient(String serverAddress, int serverPort, int docSize) throws UnknownHostException, IOException {
        this.sHandle = new Socket(serverAddress, serverPort);
        this.sInput = new BufferedReader(new InputStreamReader(sHandle.getInputStream()));
        this.sOutput = new BufferedWriter(new OutputStreamWriter(sHandle.getOutputStream()));
        this.initStats();
        this.dSize = docSize;
	}
	
	/**
	 * Initializes the data structure that holds statistical information on requests. 
	 */
	private void initStats(){
		respTime = new ArrayList<Float>();
        cntSincePrint = 0;
        timeLastPrint = System.nanoTime();
        timeCreate = timeLastPrint;
	}
	
	/**
	 * Sends a document to the server and waits for a response. The response is an
	 * ASCII serialized version of the <word, count> map.
	 * @param doc 
	 * @return
	 * @throws IOException
	 */
	private String sendToServer(String doc) throws IOException {
		long startTime = System.nanoTime();    
		sOutput.write(doc);
		sOutput.write(WoCoServer.SEPARATOR);
		sOutput.flush();
	
		
		String response;
		response = sInput.readLine();

		long endTime = System.nanoTime();
		respTime.add((float) ((endTime-startTime)/1000000.0));
		cntSincePrint ++;
		return response;
	}
	
	/**
	 * Sends a document to the server and returns the map of <word,count> pairs.
	 * If DEBUG is set to false, it returns null!
	 * @param doc
	 * @return Empty hashmap if DEBUG is false, a proper one otherwise.
	 * @throws IOException
	 */
	public HashMap<String,Integer> getWordCount(String doc) throws IOException {
		
		String response = sendToServer(doc);

		// Parsing this text into a data structure takes time, we only do it 
		// if we are in debug mode. Otherwise we'll assume that everything went
		// alright and we'd have a correct answer.
		if (DEBUG==true) {			
			HashMap<String, Integer> wordMap = new HashMap<String,Integer>();			
			String[] rParts = response.split(",");
			for (int i=0; i<rParts.length; i+=2) {
				wordMap.put(rParts[i], new Integer(rParts[i+1]));
			}
			return wordMap;
		} else {
			return new HashMap<String,Integer>();
		}				
	}
	
	
	/**
	 * Prints out statistical information since the last printStats invocation. 
	 * If called multiple times in a quick succession, if will only print out values
	 * if at least a second has passed since the last call.
	 * @param withPercentiles if true, not only averages of response time are printed but also the percentiles.
	 * @param finalPrint if true, it only prints the overall stats
	 * @param clients Number of clients in the experiment
	 *
	 */
	public void printStats(boolean withPercentiles, boolean finalPrint, int clients) {
		long currTime = System.nanoTime();
		
		float elapsedSeconds = (float) ((currTime-timeLastPrint)/1000000000.0);
		
		if (elapsedSeconds<1 && withPercentiles==false && finalPrint==false) {
			return;
		}
		
		float tput = cntSincePrint/elapsedSeconds;
		timeLastPrint = currTime;
		cntSincePrint = 0;

		if (finalPrint) {
			float totalTime = (float) ((currTime-timeCreate)/1000000000.0);
			float totalRespAverage = Utils.calculateAverage(respTime);
			float totalRespStd = Utils.calculateStd(respTime, totalRespAverage);
			System.out.println(dSize + ";" + clients + ";" + totalTime + ";" + totalRespAverage + ";" + totalRespStd + ";" + tput);
		} else {
			// System.out.println(tput);
			System.out.println("Interval time [s], Throughput [ops/s]: "+elapsedSeconds + ", "+ tput);
		}
		
		if (withPercentiles) {
			//sorting for pctiles
			Collections.sort(respTime);
			
			System.out.println("-----");
			
			elapsedSeconds = (float) ((currTime-timeCreate)/1000000000.0);
			tput = respTime.size()/elapsedSeconds;
			System.out.println("Total time [s], Throughput [ops/s]: "+elapsedSeconds + ", "+ tput);
			
			System.out.print("Response time percentiles [ms]: ");
			System.out.print("\n");
			for (int p=1; p<=100; p++) {
				System.out.print(p+","+respTime.get(respTime.size()*p/100-1));
				if (p!=100) {
					System.out.print("\n");
				}
			}
			System.out.println();
		}
		
	}
	
	/**
	 * Closes the connection to the server gracefully.
	 */
	public void shutDown() {
		try {
			this.sHandle.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		
		//reading in parameters
		if (args.length<4) {
			System.out.println("Usage: <servername> <serverport> <documentsize(KiB)> <opcount(x1000)> [<seed>] [<number of clients>]");
			System.exit(0);
		}
		
		String sName = args[0];
		int sPort = Integer.parseInt(args[1]);
		float dSize = Float.parseFloat(args[2])*1024;
		int ops = Integer.parseInt(args[3])*1000;
		int seed;
		if (args.length < 5) {
			seed = (int) (Math.random()*10000);
		} else if (Integer.parseInt(args[4]) == -1) {
			seed = (int) (Math.random()*10000);
		} else {
			seed = Integer.parseInt(args[4]);
		}

		int nrClients = (args.length==6) ? Integer.parseInt(args[5]) : -1;
		
		//We generate one document for the entire runtime of this client
		//Otherwise the client would spend too much time generating new inputs.
    	String docu = WoCoClient.generateDocument((int) (dSize), seed);

		WoCoClient client = new WoCoClient(sName, sPort, Integer.parseInt(args[2]));
    	
    	//send requests to the server in a loop.    	
		for (int rep=0; rep<ops; rep++) {
			HashMap<String, Integer> result = client.getWordCount(docu);

			// only print stats when in debug mode, otherwise only in the very end one time print
			if (DEBUG==true) {
				if (rep%25 == 0) {
					//reduce the overhead of printing statistics by calling this less often
					client.printStats(false, false, nrClients);
				}
			}

		}

		//final printout without percentiles
		client.printStats(true, false, nrClients);
		Thread.sleep(2000);
		client.shutDown();

        System.exit(0);
	}

}
