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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import exceptions.NotExistingPost;
import exceptions.NotExistingUser;

class Server {
    private volatile boolean quit = false;
    public int activeConnections;
    private ROSimp serverRMI = null;
    private HashMap<String, String> loggedUsers = new HashMap<>(); // (Remote) SocketAddress -> Username
    private volatile Selector sel = null;
    private ServerConfig config;

    /**
     *
     * @param port
     */
    public Server(String configFilePath) {
        this.config = readConfigFile(configFilePath);
        ServerInternal.updateBackupDir(this.config.backupDir);
    }

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

            System.out.println("Configuration file read.\nConfiguring the server as follows:");
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

    /**
     * avvia l'esecuzione del server
     */
    public void start() throws RemoteException {
        // config already read by constructor

        // RESTORE BACKUP
        ServerInternal.restoreBackup();

        // BOOTING UP THREADS
        // Start loggerthread
        Thread loggerThread = new Thread(this.loggerDaemon(config.logTimeout));
        loggerThread.start();

        // Start signal handler
        Runtime.getRuntime().addShutdownHook(new Thread(this.signalHandlerFun(Thread.currentThread())));

        // Start the reward periodic calculation
        Thread rewardThread = new Thread(this.rewardDaemon());
        rewardThread.start();

        // RMI setup
        this.serverRMI = new ROSimp();
        ROSint stub = (ROSint) UnicastRemoteObject.exportObject(serverRMI, 39000);
        LocateRegistry.createRegistry(config.registryPort);
        LocateRegistry.getRegistry(config.registryPort).rebind("winsomeServer", stub);
        // Now ready to handle RMI registration and followers's update notifications

        Thread tcpThread = new Thread(this.selectorDaemon());
        tcpThread.start();

        try {
            tcpThread.join();
            rewardThread.interrupt();
            rewardThread.join();
            loggerThread.interrupt();
            loggerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
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

        String msg = Util.readMsgFromSocket(c_channel);
        System.out.println("-------" + c_channel.getRemoteAddress().toString()+ "\n | Received => " + msg);
        String res = parseRequest(msg, c_channel.getRemoteAddress().toString());
        System.out.println(" | Result => \n" + res + "\n-----------------------" );
        if (Pattern.matches("^logout\\s*$", msg)) { // client logged out
            System.out.println("client " + c_channel.getRemoteAddress());
            ServerInternal.logout(loggedUsers.get(c_channel.getRemoteAddress().toString()));
            loggedUsers.remove(c_channel.getRemoteAddress().toString());
        } else {
            String result = (res != null && res != "" ? ("\n" + res) : "");
            ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
            length.putInt(result.length());
            length.flip();

            ByteBuffer message = ByteBuffer.wrap(result.getBytes());

            ByteBuffer[] atc = { length, message };
            c_channel.register(sel, SelectionKey.OP_WRITE, atc);
        }

    }

    /**
     * scrive il buffer sul canale del client
     *
     * @param key chiave di selezione
     * @throws IOException se si verifica un errore di I/O
     */
    private void sendResult(Selector sel, SelectionKey key) throws IOException {
        SocketChannel c_channel = (SocketChannel) key.channel();
        ByteBuffer[] answer = (ByteBuffer[]) key.attachment();
        ByteBuffer toSend = ByteBuffer.allocate(answer[0].remaining() + answer[1].remaining()).put(answer[0])
                .put(answer[1]);
        toSend.flip();
        c_channel.write(toSend);
        toSend.clear();

        answer[0].flip();
        if (toSend.hasRemaining()) {
            toSend.clear();
            c_channel.register(sel, SelectionKey.OP_READ);

        }

    }

    private void clientExitHandler(SelectionKey key) throws IOException {
        // if the client has disconnected c.getRemoteAddress returns null
        SocketChannel c = (SocketChannel) key.channel();
        SocketAddress cAddr = c.getRemoteAddress();
        if (cAddr != null) // this seems always to be true
            loggedUsers.remove(cAddr.toString());
        key.channel().close();
        key.cancel();
        this.activeConnections--;
    }

    private String parseRequest(String s, String k) {
        // login
        String toRet = "";
        if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", s)) {
            String param[] = s.split("\\s+");
            if (ServerInternal.login(param[1], param[2]) == 0) {
                /**
                 * This 'put' will overwrite the entry of a user who prematurely disconnected or logged out
                 */
                loggedUsers.put(k, param[1]);
                toRet = "-Successfully logged in: " + config.multicastAddress + " " + config.multicastPort;

            } else
                toRet = "Some error";
        } else {
            String u = loggedUsers.get(k);
            try {
                if (u == null) {
                    toRet = "Sign-in before sending requests";
                }
                // list users
                else if (Pattern.matches("^list\\s+users\\s*$", s)) {
                    toRet = ServerInternal.userWrapSet2String(ServerInternal.listUsers(u));
                }
                // list followers
                else if (Pattern.matches("^list\\s+followers\\s*$", s)) {
                    // the result sent through TCP isn't necessary, the client will get it by
                    // itself through RMI
                    // toRet = ServerInternal.userWrapSet2String(ServerInternal.listFollowers(u));
                    toRet = "Followers listed above";
                    try {
                        serverRMI.update(u);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        System.out.println("|ERROR updating followers list");
                    }
                }
                // list following
                else if (Pattern.matches("^list\\s+following\\s*$", s)) {
                    toRet = ServerInternal.userWrapSet2String(ServerInternal.listFollowing(u));
                }
                // follow user
                else if (Pattern.matches("^follow\\s+\\S+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    if (ServerInternal.followUser(param[1], u) == 0)
                        toRet = "Now following \"" + param[1] + "\"";
                    else
                        toRet = "Already following \"" + param[1] + "\"";
                }
                // unfollow user
                else if (Pattern.matches("^unfollow\\s+\\S+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    if (ServerInternal.unfollowUser(param[1], u) == 0)
                        toRet = "Now unfollowing \"" + param[1] + "\"";
                    else
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
                else if (Pattern.matches("^delete\\s+post\\s+\\d+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    if (ServerInternal.deletePost(Integer.parseInt(param[2]), u) == 1)
                        toRet = "Cannot delete a post which isn't owned by you";
                    else
                        toRet = "Post " + param[2] + "deleted";
                }
                // rewin post
                else if (Pattern.matches("^rewin\\s+post\\s+\\d+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    if (ServerInternal.rewinPost(Integer.parseInt(param[2]), u) == 1)
                        toRet = "Cannot rewin a post made by yourself";
                    else
                        toRet = "Post " + param[2] + "rewined";
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
                else if (Pattern.matches("^comment\\s+\\d+\\s+\\S+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    if (ServerInternal.addComment(Integer.parseInt(param[1]), param[2], u) == 1)
                        toRet = "Cannot rate a post which isn't in your feed";
                    else
                        toRet = "Comment added to post " + param[1];
                }
                // get wallet
                else if (Pattern.matches("^wallet\\s*$", s)) {
                    toRet = "" + ServerInternal.getWallet(u);
                }
                // get wallet bitcoin
                else if (Pattern.matches("^wallet\\s+btc\\s*$", s)) {
                    toRet = "" + ServerInternal.getWalletInBitcoin(u);
                } else if (Pattern.matches("^logout\\s*$", s) || Pattern.matches("^exit\\s*$", s)) {
                    toRet = "No response will be sent";
                } else {
                    return toRet = "Unknown command: " + s; // no matches, show help ? //TODO
                }
            } catch (NotExistingUser e) {
                toRet = "User not found";
            } catch (NotExistingPost e) {
                toRet = "Post not found";
            }
        }

        return toRet;
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
                        System.out.println("|ERROR: multicast packet or socket");
                        e1.printStackTrace();
                    } catch (IOException e1) {
                        System.out.println("|ERROR: multicast sending");
                        e1.printStackTrace();
                    }

                    ServerInternal.rewardAlgorithm();
                    try {
                        Thread.sleep(this.config.rewardTimeout);
                    } catch (InterruptedException e) {
                        System.out.println("rewardThread woke");
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

    private Runnable loggerDaemon(long timeout) {
        return () -> {
            while (!this.quit && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    System.out.println("Logger woke up");
                }
                // if clause to avoid writing 2 times the same stuff
                if (!Thread.currentThread().isInterrupted()) {
                    // System.out.println("...Backing up...");
                    ServerInternal.write2json();
                }
            }
            // we want to be sure to perform backup on exit
            ServerInternal.write2json();
            System.out.println("Logger performed last backup");
            return;
        };

    }

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
                            if (key.isAcceptable()) {
                                // we have to create a SocketChannel for the new client
                                ServerSocketChannel server = (ServerSocketChannel) key.channel();
                                SocketChannel c_channel = server.accept(); // non-blocking client channel
                                c_channel.configureBlocking(false);
                                System.out.println(
                                        "New connection from: " + c_channel.getRemoteAddress() +
                                                " | active clients: " + ++this.activeConnections);

                                // Server is going to read from this channel
                                c_channel.register(sel, SelectionKey.OP_READ);

                            } else if (key.isReadable()) { // READABLE
                                this.getClientRequest(sel, key); // parse request
                            }

                            else if (key.isWritable()) { // WRITABLE
                                this.sendResult(sel, key);
                            }
                        } catch (IOException | BufferUnderflowException e) { // if the client prematurely disconnects
                            System.out.println(
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
}