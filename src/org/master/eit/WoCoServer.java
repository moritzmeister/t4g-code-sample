package org.master.eit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WoCoServer {

    public static final char SEPARATOR = '$';
    private static boolean DEBUG = false;

    private ConcurrentHashMap<Integer, StringBuilder> buffer;
    private ConcurrentHashMap<Integer, ConcurrentHashMap<String, Integer>> results;

    private ConcurrentHashMap<Integer, ArrayList<Float>> serverRecStats;
    private ConcurrentHashMap<Integer, ArrayList<Float>> serverCleanStats;
    private ConcurrentHashMap<Integer, ArrayList<Float>> serverWcStats;
    private ConcurrentHashMap<Integer, ArrayList<Float>> serverSerStats;

    private ConcurrentHashMap<Integer, Long> recStatsStart;

    private int threadCount;
    private HashMap<Integer, Integer> clientThreadMap;
    private int nextThreadToAllocate;
    private ArrayList<Executor> threadPool;

    private boolean printClean = false;

    /**
     * Performs the word count on a document. It first converts the document to
     * lower case characters and then extracts words by considering "a-z" english characters
     * only (e.g., "alpha-beta" become "alphabeta"). The code breaks the text up into
     * words based on spaces.
     *
     * @param line The document encoded as a string.
     * @param wc   A HashMap to store the results in.
     */
    public static void doWordCount(String line, ConcurrentHashMap<String, Integer> wc, boolean cMode) {

        String lineWC = line;

        if (!cMode) {
            // use skeleton code if cleaning is not active
            StringBuilder asciiLine = new StringBuilder();
            String ucLine = lineWC.toLowerCase();

            char lastAdded = ' ';
            for (int i = 0; i < lineWC.length(); i++) {
                char cc = ucLine.charAt(i);
                if ((cc >= 'a' && cc <= 'z') || (cc == ' ' && lastAdded != ' ')) {
                    asciiLine.append(cc);
                    lastAdded = cc;
                }
            }

            lineWC = asciiLine.toString();
        }

        if (DEBUG) {
            System.out.println("----new line to count words---");
            System.out.println(lineWC);
            System.out.println("------------------------------");
        }

        String[] words = lineWC.split(" ");
        for (String s : words) {

            if (wc.containsKey(s)) {
                wc.put(s, wc.get(s) + 1);
            } else {
                wc.put(s, 1);
            }
        }
    }

    /**
     * Cleans a document string from html tags and non alphabetical characters.
     *
     * @param line
     * @return A cleaned document
     */
    public String cleanDocument(String line) {

        if (DEBUG) {
            System.out.println("----new line to clean HTML---");
            System.out.println(line);
            System.out.println("-----------------------------");
        }

        StringBuilder asciiLine = new StringBuilder();
        Stack<Character> htmlStack = new Stack<>();

        String ucLine = line.toLowerCase();

        boolean docBeginning = true;
        String beginning = "";

        char lastAdded = ' ';
        for (int i = 0; i < ucLine.length(); i++) {

            char cc = ucLine.charAt(i);

            if (cc == '<') {
                if (docBeginning) {
                    asciiLine.append(beginning);
                    docBeginning = false;
                }
                htmlStack.push(cc);
            } else if (cc == '>') {
                docBeginning = false;
                if (!htmlStack.empty() && htmlStack.peek() == '<') {
                    htmlStack.pop();
                }
            } else if ((cc >= 'a' && cc <= 'z') || (cc == ' ' && lastAdded != ' ')) {

                if (docBeginning) {
                    beginning += cc;
                    lastAdded = cc;
                } else if (htmlStack.empty()) {
                    asciiLine.append(cc);
                    lastAdded = cc;
                }
            }
        }

        if (DEBUG) {
            System.out.println("Stack:" + htmlStack.toString());
        }

        if (printClean) {
            System.out.println(asciiLine.toString().trim());
            printClean = false;
        }

        return asciiLine.toString().trim();
    }

    /**
     * Constructor of the server.
     */
    public WoCoServer(int thCount) {
        buffer = new ConcurrentHashMap<Integer, StringBuilder>();
        results = new ConcurrentHashMap<Integer, ConcurrentHashMap<String, Integer>>();

        serverRecStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();
        serverCleanStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();
        serverWcStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();
        serverSerStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();

        recStatsStart = new ConcurrentHashMap<>();

        // initialize data structures needed for multithreading
        threadCount = thCount;
        if (threadCount > 1) {
            clientThreadMap = new HashMap<>();
            nextThreadToAllocate = 0;
            threadPool = new ArrayList<>();
            for (int i = 0; i < threadCount; i += 1) {
                threadPool.add(Executors.newFixedThreadPool(1));
            }
        }
    }

    /**
     * Resets the data structures for collecting performance statistics in order to start new experiment.
     *
     * @return
     */
    public String resetServer() {
        buffer = new ConcurrentHashMap<Integer, StringBuilder>();
        results = new ConcurrentHashMap<Integer, ConcurrentHashMap<String, Integer>>();

        serverRecStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();
        serverCleanStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();
        serverWcStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();
        serverSerStats = new ConcurrentHashMap<Integer, ArrayList<Float>>();

        recStatsStart = new ConcurrentHashMap<>();
        if (threadCount > 1) {
            clientThreadMap = new HashMap<>();
            nextThreadToAllocate = 0;
        }

        return "Server reset";
    }

    /**
     * This function handles data received from a specific client (TCP connection).
     * Internally it will check if the buffer associated with the client has a full
     * document in it (based on the SEPARATOR). If yes, it will process the document and
     * return true, otherwise it will add the data to the buffer and return false
     *
     * @param clientId
     * @param dataChunk
     * @return A document has been processed or not.
     */
    public boolean receiveData(int clientId, SocketChannel client, String dataChunk, boolean cMode) {

        if (!serverRecStats.containsKey(clientId)) {
            serverRecStats.put(clientId, new ArrayList<>());
        }

        StringBuilder sb;

        if (!results.containsKey(clientId)) {
            results.put(clientId, new ConcurrentHashMap<String, Integer>());
        }

        if (!buffer.containsKey(clientId)) {

            sb = new StringBuilder();
            buffer.put(clientId, sb);
        } else {
            sb = buffer.get(clientId);
        }

        sb.append(dataChunk);

        if (dataChunk.indexOf(WoCoServer.SEPARATOR) > -1) {
            //we have at least one line
            long recEnd = System.nanoTime();

            serverRecStats.get(clientId).add((float) ((recEnd - recStatsStart.get(clientId)) / 1000000.0));

            String bufData = sb.toString();

            int indexNL = bufData.indexOf(WoCoServer.SEPARATOR);

            final String line = bufData.substring(0, indexNL);

            String rest = (bufData.length() > indexNL + 1) ? bufData.substring(indexNL + 1) : null;

            if (indexNL == 0) {
                System.out.println("SEP@" + indexNL + " bufdata:\n" + bufData);
            }

            if (rest != null) {
                System.out.println("more than one line: \n" + rest);
                try {
                    System.in.read();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("this actually never happens");
                buffer.put(clientId, new StringBuilder(rest));
            } else {
                recStatsStart.remove(clientId);
                buffer.put(clientId, new StringBuilder());
            }


            // word count in line
            ConcurrentHashMap<String, Integer> wc = results.get(clientId);

            if (!serverCleanStats.containsKey(clientId)) {
                serverCleanStats.put(clientId, new ArrayList<>());
            }
            if (!serverWcStats.containsKey(clientId)) {
                serverWcStats.put(clientId, new ArrayList<>());
            }

            // check if multithreaded mode
            if (threadCount > 1) {
                if (!clientThreadMap.containsKey(clientId)) {
                    clientThreadMap.put(clientId, nextThreadToAllocate);
                    if (nextThreadToAllocate + 1 >= threadCount) {
                        nextThreadToAllocate = 0;
                    } else if (nextThreadToAllocate + 1 < threadCount) {
                        nextThreadToAllocate += 1;
                    }
                }

                threadPool.get(clientThreadMap.get(clientId)).execute(new Runnable() {
                    @Override
                    public void run() {
                        String threadLine = line;

                        long startTime = System.nanoTime();
                        if (cMode) {
                            threadLine = cleanDocument(threadLine);
                        }
                        long endTime = System.nanoTime();
                        serverCleanStats.get(clientId).add((float) ((endTime - startTime) / 1000000.0));

                        startTime = System.nanoTime();

                        doWordCount(threadLine, wc, cMode);
                        endTime = System.nanoTime();

                        serverWcStats.get(clientId).add((float) ((endTime - startTime) / 1000000.0));

                        ByteBuffer ba = ByteBuffer.wrap(serializeResultForClient(clientId).getBytes());
                        try {
                            client.write(ba);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                String localLine = line;

                long startTime = System.nanoTime();
                if (cMode) {
                    localLine = cleanDocument(localLine);
                }
                long endTime = System.nanoTime();
                serverCleanStats.get(clientId).add((float) ((endTime - startTime) / 1000000.0));

                startTime = System.nanoTime();

                doWordCount(localLine, wc, cMode);
                endTime = System.nanoTime();

                serverWcStats.get(clientId).add((float) ((endTime - startTime) / 1000000.0));
            }

            return true;

        } else {
            return false;
        }

    }

    /**
     * Returns a serialized version of the word count associated with the last
     * processed document for a given client. If not called before processing a new
     * document, the result is overwritten by the new one.
     *
     * @param clientId
     * @return
     */
    public String serializeResultForClient(int clientId) {
        if (results.containsKey(clientId)) {

            if (!serverSerStats.containsKey(clientId)) {
                serverSerStats.put(clientId, new ArrayList<>());
            }

            long startTime = System.nanoTime();

            StringBuilder sb = new StringBuilder();
            ConcurrentHashMap<String, Integer> hm = results.get(clientId);
            for (String key : hm.keySet()) {
                sb.append(key + ",");
                sb.append(hm.get(key) + ",");
            }
            results.remove(clientId);
            sb.append("\n");
            String serString = sb.substring(0);

            long endTime = System.nanoTime();

            serverSerStats.get(clientId).add((float) ((endTime - startTime) / 1000000.0));

            if (DEBUG) {
                System.out.println(serverSerStats.size());
            }

            return serString;
        } else {
            return "";
        }
    }

    /**
     * Merge all the statistics from all clients per cost of operation collected.
     * Calculate average and standard deviation and print the result as ";"-separated line.
     *
     * @param withPercentiles
     */
    public void printAllStats(boolean withPercentiles) {

        // serverRecStats
        ArrayList<Float> recAll = Utils.mergeClients(serverRecStats);
        float recAvg = Utils.calculateAverage(recAll);
        float recStd = Utils.calculateStd(recAll, recAvg);

        // serverCleanStats
        ArrayList<Float> cleanAll = Utils.mergeClients(serverCleanStats);
        float cleanAvg = Utils.calculateAverage(cleanAll);
        float cleanStd = Utils.calculateStd(cleanAll, cleanAvg);

        // serverWcStats
        ArrayList<Float> wcAll = Utils.mergeClients(serverWcStats);
        float wcAvg = Utils.calculateAverage(wcAll);
        float wcStd = Utils.calculateStd(wcAll, wcAvg);

        // serverSerStats
        ArrayList<Float> serAll = Utils.mergeClients(serverSerStats);
        float serAvg = Utils.calculateAverage(serAll);
        float serStd = Utils.calculateStd(serAll, serAvg);

        // final print
        System.out.println(recAvg + ";" + recStd + ";" + cleanAvg + ";" + cleanStd + ";" + wcAvg + ";" + wcStd + ";" + serAvg + ";" + serStd);

        if (withPercentiles) {
            Utils.printPercentiles(recAll, "Receive");
            Utils.printPercentiles(cleanAll, "Cleaning");
            Utils.printPercentiles(wcAll, "Word count");
            Utils.printPercentiles(serAll, "Serialization");
        }

    }


    public static void main(String[] args) throws IOException {

        if (args.length != 4) {
            System.out.println("Usage: <listenaddress> <listenport> <cleaning> <threadcount>");
            System.exit(0);
        }

        String lAddr = args[0];
        int lPort = Integer.parseInt(args[1]);
        boolean cMode = Boolean.parseBoolean(args[2]);
        int threadCount = Integer.parseInt(args[3]);

        // instantiate the server
        WoCoServer server = new WoCoServer(threadCount);

        // instantiate connection counter
        int activeConnect = 0;

        // detects the connections
        Selector selector = Selector.open();

        // instantiate the server socket
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        InetSocketAddress myAddr = new InetSocketAddress(lAddr, lPort);

        // ip:port to listen on
        serverSocket.bind(myAddr);

        serverSocket.configureBlocking(false);


        int ops = serverSocket.validOps();

        SelectionKey selectKey = serverSocket.register(selector, ops, null);

        ByteBuffer bb = ByteBuffer.allocate(1024 * 1024);
        ByteBuffer ba;

        System.out.println("Server started.");

        while (true) {
            // returns the channels that are ready for the events that we are interested in -> ops
            // blocks until at least one channel is ready for the events you registered for.
            // returns int telling us how many channels are ready to, that is since the last time it was called
            selector.select();

            // access the ready channels
            // this is the SelectionKey object that we got returned when we registered the channel with the selector
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            // iterate through the keys
            // This loop iterates the keys in the selected key set.
            // For each key it tests the key to determine what the channel referenced by the key is ready for.
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {
                    // blocks until a connection arrives
                    SocketChannel client = serverSocket.accept();

                    client.configureBlocking(false);

                    client.register(selector, SelectionKey.OP_READ);
                    System.out.println("Connection Accepted: " + client.getLocalAddress() + "\n");
                    activeConnect++;

                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    int clientId = client.hashCode();

                    bb.rewind();

                    int readCnt = client.read(bb);

                    if (readCnt > 0) {

                        if (!server.recStatsStart.containsKey(clientId)) {
                            server.recStatsStart.put(clientId, System.nanoTime());
                        }

                        String result = new String(bb.array(), 0, readCnt);

                        // receiveData calls the word count
                        boolean hasResult = server.receiveData(clientId, client, result, cMode);

                        if (hasResult && threadCount <= 1) {
                            ba = ByteBuffer.wrap(server.serializeResultForClient(clientId).getBytes());
                            client.write(ba);
                        }

                    } else {
                        key.cancel();
                        activeConnect--;

                        // check if all connected clients are disconnected again
                        if (activeConnect == 0) {
                            server.printAllStats(false);
                            System.out.println(server.resetServer());
                        }

                    }

                }
                iterator.remove();
            }
        }
    }

}

