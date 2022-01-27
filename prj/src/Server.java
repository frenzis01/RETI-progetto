import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import exceptions.NotExistingPost;
import exceptions.NotExistingUser;

class Server {
    private volatile boolean quit = false;
    public int activeConnections;
    private ROSimp serverRMI = null;
    private ConcurrentHashMap<String, String> loggedUsers = new ConcurrentHashMap<>(); // (Remote) SocketAddress ->
                                                                                       // Username
    private volatile Selector sel = null;
    private ServerConfig config;
    private ThreadPoolExecutor workerPool;
    private static Object stdOutlock = new Object();

    private Pipe pipe = null;
    private ConcurrentLinkedQueue<RegisterParams> toRegister = new ConcurrentLinkedQueue<RegisterParams>();

    /**
     *
     * @param port
     */
    public Server(String configFilePath) {
        this.config = readConfigFile(configFilePath);
        ServerInternal.updateBackupDir(this.config.backupDir);
        ServerInternal.setAuthorPercentage(this.config.authorPercentage);
    }

    /**
     * avvia l'esecuzione del server
     */
    public void start() throws RemoteException {

        try {
            pipe = Pipe.open();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        // config already read by constructor
        // RESTORE BACKUP
        ServerInternal.restoreBackup();

        // BOOTING UP THREADS
        // Start the reward periodic calculation
        Thread rewardThread = new Thread(this.rewardDaemon());
        rewardThread.start();

        // Start loggerthread
        Thread loggerThread = new Thread(this.loggerDaemon(config.logTimeout, rewardThread));
        loggerThread.start();

        // Start btcRateThread
        Thread btcRateThread = new Thread(this.bitcoinRateGetter(config.btcTimeout));
        btcRateThread.start();

        // Start signal handler
        Runtime.getRuntime().addShutdownHook(new Thread(this.signalHandlerFun(Thread.currentThread())));

        // RMI setup
        this.serverRMI = new ROSimp();
        ROSint stub = (ROSint) UnicastRemoteObject.exportObject(serverRMI, 39000);
        LocateRegistry.createRegistry(config.registryPort);
        LocateRegistry.getRegistry(config.registryPort).rebind("winsomeServer", stub);
        // Now ready to handle RMI registration and followers's update notifications

        this.workerPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(this.config.nworker);

        Thread tcpThread = new Thread(this.selectorDaemon());
        tcpThread.start();

        try {
            tcpThread.join();
            workerPool.shutdown();
            workerPool.awaitTermination(5, TimeUnit.SECONDS);
            btcRateThread.interrupt();
            btcRateThread.join();
            rewardThread.interrupt();
            // rewardThread.join(); //this is done by loggerThread
            loggerThread.interrupt();
            loggerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Task assigned to worker threads.
     * 
     * @param req               request to be parsed and eventually evaluated
     * @param c_channel         assigned to the requesting client's channel. This
     *                          will be added to 'toRegister' when the worker has
     *                          done evaluating the request
     * @param c_channelToString result of c_channel.getRemoteAddress().toString(),
     *                          not executed here to avoid throwing
     *                          ClosedChannelException and running clientExitHandler
     *                          from multiple threads. This param is needed only for
     *                          printing to stdout purposes
     * @return
     */
    private Runnable requestHandler(String req, SocketChannel c_channel, String c_channelToString) {
        return () -> {

            String res = parseRequest(req, c_channelToString);
            p("------- Evaluated req by" + c_channelToString + " by Thread: "
                    + Thread.currentThread().getName() + "\n | => " + req +
                    "\n | Result => \n" + res + "\n-----------------------");

            ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
            length.putInt(res.length());
            length.flip();

            ByteBuffer message = ByteBuffer.wrap(res.getBytes());

            ByteBuffer toSend = ByteBuffer
                    .allocate(length.remaining() + message.remaining())
                    .put(length)
                    .put(message);
            toSend.flip();

            // ByteBuffer[] atc = { length, message };

            // c_channel.register() will be performed by selectorThread
            toRegister.add(new RegisterParams(c_channel, toSend));
            var token = ByteBuffer.allocateDirect(1);
            try {
                this.pipe.sink().write(token);
            } catch (IOException e) { // almost never thrown
                e.printStackTrace();
            }

        };
    }

    // THREADS implementation
    private Runnable rewardDaemon() {
        return () -> {
            try (DatagramSocket skt = new DatagramSocket(this.config.multicastPort + 1)) {
                while (!quit && !Thread.currentThread().isInterrupted()) {
                    byte[] msg = "Rewards calculated".getBytes();
                    try {
                        DatagramPacket dtg = new DatagramPacket(msg, msg.length,
                                InetAddress.getByName(this.config.multicastAddress),
                                this.config.multicastPort);
                        skt.send(dtg);
                    } catch (UnknownHostException | SocketException e1) {
                        p("|ERROR: multicast packet or socket");
                        e1.printStackTrace();
                    } catch (IOException e1) {
                        p("|ERROR: multicast sending");
                        e1.printStackTrace();
                    }

                    ServerInternal.rewardAlgorithm();
                    try {
                        Thread.sleep(this.config.rewardTimeout);
                    } catch (InterruptedException e) {
                        p("rewardThread woke");
                    }
                }
                // perform algorithm one last time
                ServerInternal.rewardAlgorithm();
                if (skt != null)
                    skt.close();
            } catch (SocketException e) {
                e.printStackTrace();
            }
            return;
        };
    }

    /**
     * Periodically creates the backup of the internal state of winsome
     * 
     * @param timeout
     * @param rewardThread
     * @return
     */
    private Runnable loggerDaemon(long timeout, Thread rewardThread) {
        return () -> {
            while (!this.quit && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    p("Logger woke up");
                }
                // if clause to avoid writing 2 times the same stuff
                if (!Thread.currentThread().isInterrupted()) {
                    // p("...Backing up...");
                    ServerInternal.write2json();
                }
            }
            // we want to be sure to perform backup on exit
            try {
                // wait for the rewards to be calculated one last time
                rewardThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // then perform backup
            ServerInternal.write2json();
            p("Logger performed last backup");
            return;
        };

    }

    /**
     * This thread will periodically update the conversion rate from wincoin to
     * bitcoin
     * 
     * @param timeout
     * @return
     */
    private Runnable bitcoinRateGetter(long timeout) {
        return () -> {
            try {
                while (!this.quit && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(timeout);
                    ServerInternal.setBtcRate();
                }
            } catch (InterruptedException e) {
                p("bitcoin rate daemon stopped");
            }
        };

    }

    /**
     * Used to clean exit after Ctrl+C
     * 
     * @param mainThread
     * @return
     */
    private Runnable signalHandlerFun(Thread mainThread) {
        return () -> {
            System.out.println("SIGINT | SIGTERM received => exiting...");
            this.quit = true;
            if (this.sel != null)
                this.sel.wakeup();
            try {
                mainThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("Handler interrupted");
            }
        };
    }

    /**
     * Handles TCP connections with clients using a NIO Selector. Uses a thread pool
     * to evaluate requests.
     * All register, read and write operations are performed by this thread; when a
     * worker has done evaluating a request,
     * adds the channel ready for OP_WRITE to a shared queue and notifies the
     * selector through a pipe.
     * 
     * @return
     */
    private Runnable selectorDaemon() {
        return () -> {

            // TCP setup
            this.activeConnections = 0;
            try {
                // Server setup
                ServerSocketChannel s_channel = ServerSocketChannel.open();
                s_channel.socket().bind(new InetSocketAddress(this.config.tcpPort));
                s_channel.configureBlocking(false); // non-blocking policy

                sel = Selector.open();
                s_channel.register(sel, SelectionKey.OP_ACCEPT);

                // add pipe workerPool->selectorDaemon to the selector
                pipe.source().configureBlocking(false);
                pipe.source().register(sel, SelectionKey.OP_READ);

                // Server is running
                System.out.printf("\t...waiting %d\n", this.config.tcpPort);
                while (!this.quit) {
                    if (sel.select() == 0)
                        continue;
                    Set<SelectionKey> selectedKeys = sel.selectedKeys(); // ready channels

                    // iterate on every ready channel
                    Iterator<SelectionKey> iter = selectedKeys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove(); // important step

                        try {
                            if (key.channel() == pipe.source()) { // a channel needs to be registered
                                var token = ByteBuffer.allocate(1);
                                pipe.source().read(token); // consume token from pipe
                                RegisterParams rp = toRegister.remove(); // get channel (and attachment) to be
                                                                         // registered
                                rp.c.register(sel, SelectionKey.OP_WRITE, rp.atc);
                            } else if (key.isAcceptable()) {
                                // we have to create a SocketChannel for the new client
                                ServerSocketChannel server = (ServerSocketChannel) key.channel();
                                SocketChannel c_channel = server.accept(); // non-blocking client channel
                                c_channel.configureBlocking(false);
                                p(
                                        "New connection from: " + c_channel.getRemoteAddress() +
                                                " | active clients: " + ++this.activeConnections);

                                // Server is going to read from this channel
                                c_channel.register(sel, SelectionKey.OP_READ, null);

                            } else if (key.isReadable()) { // READABLE
                                this.getClientRequest(sel, key); // get request
                            }

                            else if (key.isWritable()) { // WRITABLE
                                this.sendResult(sel, key);
                            }
                        } catch (IOException | BufferUnderflowException e) { // if the client prematurely disconnects
                            // e.printStackTrace();
                            p(
                                    "CLIENT DISCONNECTED: " + ((SocketChannel) key.channel()).getRemoteAddress());
                            clientExitHandler(key);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        };
    }

    // Stuff used by selectorDaemon

    /**
     * Content type of the shared queue between workers and selectorDaemon, which
     * uses this class to know when it has to call register(OP_WRITE) on a given
     * channel
     */
    private class RegisterParams {
        SocketChannel c;
        ByteBuffer atc;

        public RegisterParams(SocketChannel c, ByteBuffer atc) {
            this.c = c;
            this.atc = atc;
        }
    }

    /**
     * Read request from client and register OP_WRITE on the Selector
     *
     * @param sel selettore utilizzato dal server
     * @param key chiave di selezione
     * @throws IOException se si verifica un errore di I/O
     */
    private void getClientRequest(Selector sel, SelectionKey key) throws IOException {
        // Create new SocketChannel with the Client
        SocketChannel c_channel = (SocketChannel) key.channel();

        ByteBuffer msgBuf;
        if (key.attachment() == null){  // this is the first read
            ByteBuffer lenBuf = ByteBuffer.allocate(Integer.BYTES);
            var nread = c_channel.read(lenBuf); 
            // System.out.println("LENGTH READ " + lenBuf.position() + " " + lenBuf.limit() + " " + lenBuf.remaining() + " " + nread);
            assert (nread == Integer.BYTES);   // assume to read the entire message length
            lenBuf.flip();
            int msgLen = lenBuf.getInt();
            // System.out.println("LENGTH READ " + lenBuf.position() + " " + lenBuf.limit() + " " + lenBuf.remaining() + " " + nread+ " " + msgLen );
            
            
            msgBuf = ByteBuffer.allocate(msgLen);
            key.attach(msgBuf);
        }

        msgBuf = (ByteBuffer) key.attachment();
        c_channel.read(msgBuf);
        // System.out.println("READING " + c_channel.read(msgBuf));

        if (!msgBuf.hasRemaining()) {
            // System.out.println("END OF READ");

            key.interestOps(0);
            msgBuf.flip();
            String req = new String(msgBuf.array()).trim();
            msgBuf.clear();

            key.attach(null);
    
            p("-------" + c_channel.getRemoteAddress().toString() + "\n | Received => " + req);
            if (Pattern.matches("^logout\\s*$", req)) { // client logged out
                // p("client " + c_channel.getRemoteAddress());
                loggedUsers.remove(c_channel.getRemoteAddress().toString());
                c_channel.register(sel, SelectionKey.OP_READ, null);
            } else {
                this.workerPool.execute(this.requestHandler(req, c_channel, c_channel.getRemoteAddress().toString()));
            }
        }

    }

    /**
     * Writes the content of key.attachment to the associated channel
     *
     * @param key
     * @throws IOException
     */
    private void sendResult(Selector sel, SelectionKey key) throws IOException {
        SocketChannel c_channel = (SocketChannel) key.channel();

        /**
         * key.attachment() contains the length of the message to be written and the
         * message itself. We'll prepend the length.
         */
        // ByteBuffer[] answer = (ByteBuffer[]) key.attachment();
        ByteBuffer toSend = (ByteBuffer) key.attachment();

        c_channel.write(toSend);
        // System.out.println("WRITING " + c_channel.write(toSend) + " " + toSend.position() + " " + toSend.limit() + " " + toSend.remaining());

        // toSend.clear();
        // answer[0].flip();
        if (!toSend.hasRemaining()) {
            // System.out.println("END OF WRITE");
            toSend.clear();
            c_channel.register(sel, SelectionKey.OP_READ, null);
            // sel.wakeup();
        }

    }

    /**
     * When a client calls exit or prematurely leaves
     */
    private void clientExitHandler(SelectionKey key) throws IOException {
        // if the client has disconnected c.getRemoteAddress returns null
        SocketChannel c = (SocketChannel) key.channel();
        if (c.isOpen()) {
            SocketAddress cAddr = c.getRemoteAddress();
            if (cAddr != null) // this seems always to be true
                loggedUsers.remove(cAddr.toString());
            key.channel().close();
        }
        key.cancel();
        this.activeConnections--;
    }

    /**
     * Tries to match s with one of winsome's commands
     * 
     * @param s                request
     * @param requestorAddress
     * @return the response to be sent to the client
     */
    private String parseRequest(String s, String requestorAddress) {
        // login
        String toRet = "";
        if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", s)) {
            String param[] = s.split("\\s+");
            if (ServerInternal.login(param[1], param[2]) == 0) {
                var wrapper = new Object() {
                    // this value is never used, the client doesn't even send the request if there's
                    // another user logged in. However...
                    String s = "A different user is logged from your address";
                };
                loggedUsers.computeIfAbsent(requestorAddress, (k) -> {
                    if (this.config.exclusiveLogin && loggedUsers.containsValue(param[1])) {
                        wrapper.s = "The same user is already logged in from another address";
                        return null;
                    } else {
                        wrapper.s = "-Successfully logged in: " + config.multicastAddress + " " + config.multicastPort;
                        return param[1];
                    }
                });
                toRet = wrapper.s;

            } else
                toRet = "Login error. Either username or password are wrong";
        } else {
            String u = loggedUsers.get(requestorAddress);
            try {
                if (u == null) {
                    toRet = "Sign-in before sending requests";
                }
                // list users
                else if (Pattern.matches("^list\\s+users\\s*$", s)) {
                    toRet = ServerInternal.userWrapSet2String(ServerInternal.listUsers(u));
                }
                // list followers... never used
                else if (Pattern.matches("^list\\s+followers\\s*$", s)) {
                    toRet = ServerInternal.userWrapSet2String(ServerInternal.listFollowers(u));
                }
                // list following
                else if (Pattern.matches("^list\\s+following\\s*$", s)) {
                    toRet = ServerInternal.userWrapSet2String(ServerInternal.listFollowing(u));
                }
                // follow user
                else if (Pattern.matches("^follow\\s+\\S+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    int res = ServerInternal.followUser(param[1], u);
                    if (res == 0) {
                        toRet = "Now following \"" + param[1] + "\"";
                        serverRMI.update(param[1], true);
                    } else if (res == 1)
                        toRet = "Already following \"" + param[1] + "\"";
                    else
                        toRet = "Can't follow yourself";
                }
                // unfollow user
                else if (Pattern.matches("^unfollow\\s+\\S+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    if (ServerInternal.unfollowUser(param[1], u) == 0) {
                        toRet = "Now unfollowing \"" + param[1] + "\"";
                        serverRMI.update(param[1], true);
                    } else
                        toRet = "Already not following \"" + param[1] + "\"";
                }
                // view blog
                else if (Pattern.matches("^blog\\s*$", s)) {
                    toRet = ServerInternal.postWrapSet2String(ServerInternal.viewBlog(u));
                }
                // create post
                else if (Pattern.matches("^post\\s+\".+\"\\s+\".+\"\\s*$", s)) {
                    String param[] = s.split("\"");
                    ServerInternal.PostWrap p = ServerInternal.createPost(param[1], param[3], u);
                    toRet = "New post created: " + p.idPost /* + " " + param[1] + " " + param[3] */;
                }
                // show feed
                else if (Pattern.matches("^show\\s+feed\\s*$", s)) {
                    toRet = ServerInternal.postWrapSet2String(ServerInternal.showFeed(u));
                }
                // show post
                else if (Pattern.matches("^show\\s+post\\s+\\d+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    toRet = ServerInternal.postWrap2String(ServerInternal.showPost(Integer.parseInt(param[2]), u));
                }
                // delete post
                else if (Pattern.matches("^delete\\s+post\\s+[+-]?\\d+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    int res = ServerInternal.deletePost(Integer.parseInt(param[2]), u);
                    if (res == -1)
                        toRet = "Cannot delete a post which isn't owned by you";
                    else
                        toRet = "Post " + res + " deleted";
                }
                // rewin post
                else if (Pattern.matches("^rewin\\s+post\\s+\\d+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    if (ServerInternal.rewinPost(Integer.parseInt(param[2]), u) == 1)
                        toRet = "Cannot rewin a post made by yourself";
                    else
                        toRet = "Post " + param[2] + " rewined";
                }
                // rate post
                else if (Pattern.matches("^rate\\s+post\\s+\\d+\\s[+-]?\\d+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    int vote = Integer.parseInt(param[2]);
                    int res = ServerInternal.ratePost(vote, Integer.parseInt(param[3]), u);
                    if (res == 1)
                        toRet = "Cannot rate a post which isn't in your feed";
                    else if (res == 2)
                        toRet = "Already voted";
                    else
                        toRet = "Post " + param[2] + (vote >= 0 ? " upvoted" : " downvoted");
                }
                // add comment
                else if (Pattern.matches("^comment\\s+\\d+\\s+\".+\"\\s*$", s)) {
                    String id = s.split("\\s+")[1];
                    String comment = s.substring(s.indexOf("\"") + 1, s.lastIndexOf("\""));
                    if (ServerInternal.addComment(Integer.parseInt(id), comment, u) == 1)
                        toRet = "Cannot rate a post which isn't in your feed";
                    else
                        toRet = "Comment added to post " + id;
                }
                // get wallet
                else if (Pattern.matches("^wallet\\s*$", s)) {
                    toRet = ServerInternal.getWallet(u);
                }
                // get wallet bitcoin
                else if (Pattern.matches("^wallet\\s+btc\\s*$", s)) {
                    toRet = "" + ServerInternal.getWalletInBitcoin(u);
                } else if (Pattern.matches("^logout\\s*$", s) || Pattern.matches("^exit\\s*$", s)) {
                    toRet = "No response will be sent";
                } else {
                    return toRet = "Unknown command: " + s;
                }
            } catch (NotExistingUser e) {
                toRet = "User not found";
            } catch (NotExistingPost e) {
                toRet = "Post not found";
            } catch (RemoteException e) {
                e.printStackTrace();
                System.out.println("|ERROR updating followers list");
            }
        }

        return toRet;
    }

    /**
     * Utility to read configuration file.
     * Prints the fields read from the .json file, if found
     * 
     * @param configFilePath
     * @return ServerConfig instance which might contain some default fields
     */
    public ServerConfig readConfigFile(String configFilePath) {
        ServerConfig servConfig = new ServerConfig();
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File(configFilePath);
        if (configFile.exists()) {
            try {
                BufferedReader usersReader = new BufferedReader(new FileReader(configFile));
                servConfig = mapper.readValue(usersReader, new TypeReference<ServerConfig>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Configuration file " + configFilePath + " read.\nConfiguring the server as follows:");
            for (Field f : servConfig.getClass().getDeclaredFields()) {

                try {
                    System.out.println(" " + f.getName() + " : " + f.get(servConfig));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            System.out.println();
        } else
            System.out.println("Config file not found. Using default values.");
        return servConfig;
    }

    private static void p(String s) {
        synchronized (stdOutlock) {
            System.out.println(s);
        }
    }
}