import java.io.IOException;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Pattern;

import exceptions.NotExistingPost;
import exceptions.NotExistingUser;

class Server {
    private final int port;
    private final int multicastPort;
    private final String multicastString = "239.255.1.3";
    private final long timeout = 1000;
    private volatile boolean quit = false;
    public int activeConnections;
    private ROSimp serverRMI = null;
    private HashMap<String, String> loggedUsers = new HashMap<>(); // (Remote) SocketAddress -> Username

    /**
     *
     * @param port
     */
    public Server(int port, int multicastPort) {
        this.port = port;
        this.multicastPort = multicastPort;
    }

    /**
     * avvia l'esecuzione del server
     */
    public void start() throws RemoteException {
        // RMI setup
        this.serverRMI = new ROSimp();
        ROSint stub = (ROSint) UnicastRemoteObject.exportObject(serverRMI, 39000);
        LocateRegistry.createRegistry(1900);
        LocateRegistry.getRegistry(1900).rebind("rmi://127.0.0.1:1900", stub);
        // Now ready to handle RMI registration and followers's update notifications

        // start the reward periodic calculation
        Thread rewardThread = new Thread(this.rewardThread());
        rewardThread.start();

        // TCP setup
        this.activeConnections = 0;
        try {
            // Server setup
            ServerSocketChannel s_channel = ServerSocketChannel.open();
            s_channel.socket().bind(new InetSocketAddress(this.port));
            s_channel.configureBlocking(false); // non-blocking policy
            Selector sel = Selector.open();
            s_channel.register(sel, SelectionKey.OP_ACCEPT);

            // Server is running
            System.out.printf("Server: waiting %d\n", this.port);
            while (true) {
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
                                    "Server: new connection from: " + c_channel.getRemoteAddress() +
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
        System.out.print("Server: ricevuto " + msg);
        String res = parseRequest(msg, c_channel.getRemoteAddress().toString());
        System.out.println(" | " + res);
        if (Pattern.matches("^logout\\s*$", msg)) { // client logged out
            System.out.println("Server: logout from client " + c_channel.getRemoteAddress());
            // c_channel.close();
            // key.cancel();
            ServerInternal.logout(loggedUsers.get(c_channel.getRemoteAddress().toString()));
            loggedUsers.remove(c_channel.getRemoteAddress().toString());
        } else {
            String result = (res != null ? ("\n" + res) : "");
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
        System.out.println("Server: sent " + answer[0].getInt() + " bytes containing: "
                + new String(toSend.array()).trim() + " || inviato al client " + c_channel.getRemoteAddress());
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
    }

    private String parseRequest(String s, String k) {
        // login
        String toRet = "";
        if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", s)) {
            String param[] = s.split("\\s+");
            if (ServerInternal.login(param[1], param[2]) == 0) {
                /**
                 * This 'put' will overwrite the entry of a user who prematurely disconnected
                 * We might not be able to retrieve the address of a SelectionKey associated
                 * with an already disconnected client (//TODO make sure of this)
                 */
                loggedUsers.put(k, param[1]);
                // System.out.println("user logged in: " + param[1] + " " + param[2]);
                toRet = "-Successfully logged in: " + multicastString  + " " +  multicastPort;
            } else
                toRet = "Some error";
        }
        // list users
        else {
            String u = loggedUsers.get(k);
            try {
                if (u == null) {
                    toRet = "Sign-in before sending requests";
                } else if (Pattern.matches("^list\\s+users\\s*$", s)) {
                    toRet = userWrapSet2String(ServerInternal.listUsers(u));
                }
                // list followers
                else if (Pattern.matches("^list\\s+followers\\s*$", s)) {
                    // TODO the result sent through TCP isn't necessary, the client will get it by
                    // itself
                    // toRet = userWrapSet2String(ServerInternal.listFollowers(u));
                    toRet = "";
                    try {
                        serverRMI.update(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        System.out.println("ERROR while updating followers list");
                    }
                }
                // list following
                else if (Pattern.matches("^list\\s+following\\s*$", s)) {
                    toRet = userWrapSet2String(ServerInternal.listFollowing(u));
                }
                // follow user
                else if (Pattern.matches("^follow\\s+\\S+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    ServerInternal.followUser(param[1], u);
                    toRet = "Now following \"" + param[1] + "\"";
                }
                // unfollow user
                else if (Pattern.matches("^unfollow\\s+\\S+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    ServerInternal.unfollowUser(param[1], u);
                    toRet = "Now unfollowing \"" + param[1] + "\"";
                }
                // view blog
                else if (Pattern.matches("^blog\\s*$", s)) {
                    toRet = postWrapSet2String(ServerInternal.viewBlog(u));
                }
                // create post
                else if (Pattern.matches("^post\\s+\".+\"\\s+\".+\"\\s*$", s)) {
                    String param[] = s.split("\"");
                    ServerInternal.PostWrap p = ServerInternal.createPost(param[1], param[3], u);
                    toRet = "New post created: " + p.idPost /* + " " + param[1] + " " + param[3] */;
                }
                // show feed
                else if (Pattern.matches("^show\\s+feed\\s*$", s)) {
                    toRet = postWrapSet2String(ServerInternal.showFeed(u));
                }
                // show post
                else if (Pattern.matches("^show\\s+post\\s+\\d+\\s*$", s)) {
                    String param[] = s.split("\\s+");
                    toRet = postWrap2String(ServerInternal.showPost(Integer.parseInt(param[2]), u));
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
                    // TODO
                    toRet = "Wallet not yet implemented";
                }
                // get wallet bitcoin
                else if (Pattern.matches("^wallet\\s+btc\\s*$", s)) {
                    // TODO
                    toRet = "Wallet not yet implemented";
                } else {
                    return toRet = "Unknown command: " + s; // no matches, show help ? //TODO
                }
            } catch (NotExistingUser e) {
                toRet = "User not found"; // TODO
            } catch (NotExistingPost e) {
                toRet = "Post not found";
            }
        }

        return toRet;
    }

    private static String userWrapSet2String(HashSet<ServerInternal.UserWrap> users) {
        String toRet = "User \t|\t Tag\n";
        for (ServerInternal.UserWrap u : users) {
            toRet = toRet + u.username + " \t|\t " + Arrays.toString(u.tags) + "\n";
        }
        return toRet;
    }

    private static String postWrapSet2String(HashSet<ServerInternal.PostWrap> posts) {
        String toRet = "Id \t|\t Author \t|\t Title\n";
        for (ServerInternal.PostWrap p : posts) {
            toRet = toRet + p.idPost + "\t|\t" + p.owner + "\t|\t" + p.title + "\n";
        }
        return toRet;
    }

    private static String postWrap2String(ServerInternal.PostWrap p) {
        var wrapper = new Object() {
            String toRet = "Title: " + p.title + "\nContent: " + p.content + "\nVotes: " + p.upvote +
                    "+ | " + p.downvote + "-\nComments:\n";
        };
        p.comments.forEach((u, comms) -> {
            wrapper.toRet += "\t" + u + ":\n";
            comms.forEach((c) -> {
                wrapper.toRet += "\t " + c + "\n";
            });
        });
        return wrapper.toRet;
    }

    private Runnable rewardThread() {
        return () -> {
            try (DatagramSocket skt = new DatagramSocket(this.multicastPort + 1)) {
                while (!quit) {
                    byte[] msg = "Rewards calculated".getBytes();
                    try {
                        DatagramPacket dtg = new DatagramPacket(msg, msg.length, InetAddress.getByName(multicastString),
                                this.multicastPort);
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
                        Thread.sleep(this.timeout);
                    } catch (InterruptedException e) {
                        System.out.println("|ERROR: rewardThread");
                        e.printStackTrace();
                    }
                }
                if (skt != null)
                    skt.close();
            } catch (SocketException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        };
    }

    public String getMulticastAddressPort() {
        return new String(multicastString + multicastPort);
    }
}