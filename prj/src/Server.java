import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import exceptions.NotExistingUser;



class Server {
    private final String EXIT_CMD = "logout";
    private final int port;
    private final String ADD_ANSWER = "echoed by server";
    public int activeConnections;
    private HashMap<String,String> loggedUsers = new HashMap<>(); // (Remote) SocketAddress -> Username

    /**
     *
     * @param port
     */
    public Server(int port) {
        this.port = port;
    }

    /**
     * avvia l'esecuzione del server
     */
    public void start() {
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
                            this.sendResult(sel, key); // TODO write request result
                        }
                    } catch (IOException e) { // if the client prematurely disconnects
                        e.printStackTrace();
                        System.out.println(" |\tClient disconnected: " + ((SocketChannel)key.channel()).getRemoteAddress());
                        // key.channel().close();
                        // key.cancel();
                        logoutHandler(key); // won't delete loggedUsers entry ? //TODO
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
        if (Pattern.matches("^logout\\s+\\S+\\s*$", msg)) { // client logged out
            System.out.println("Server: logout from client " + c_channel.getRemoteAddress());
            // c_channel.close();
            // key.cancel();
            ServerInternal.logout(loggedUsers.get(c_channel.getRemoteAddress().toString()));
            logoutHandler(key);
        } else {
            String result = msg + " " + this.ADD_ANSWER + (res != null ? ("\n" + res) : ""); // TODO get actual request result
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

    private void logoutHandler (SelectionKey key) throws IOException {
        // if the client has disconnected c.getRemoteAddress returns null
        SocketChannel c = (SocketChannel) key.channel();
        SocketAddress cAddr = c.getRemoteAddress();
        if (cAddr != null)
            loggedUsers.remove(cAddr.toString());
        key.channel().close();
        key.cancel();
    }

    
    private String parseRequest(String s, String k) {
        // TODO logout gets recognized before calling parseRequest using EXIT_CMD (?)
        // No...?
        // login
        String toRet = "";
        if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", s)) {
            String param [] = s.split("\\s+");
            if (ServerInternal.login(param[1], param[2]) == 0){
                /**
                 * This 'put' will overwrite the entry of a user who prematurely disconnected
                 * We might not be able to retrieve the address of a SelectionKey associated
                 * with an already disconnected client (//TODO make sure of this)
                 */
                loggedUsers.put(k, param[1]);
                // System.out.println("user logged in: " + param[1] + " " + param[2]);
                toRet = "--SUCCESSFULLY LOGGED IN";
            }
            else toRet = "Some error";
        }
        // // logout
        // else if (Pattern.matches("^logout\\s+\\S+\\s*$", s)) {
        //     logoutHandler(key);
        // }
        // list users
        else if (Pattern.matches("^list\\s+users\\s*$", s)) {
            String u = loggedUsers.get(k); 
            if (u != null) {
                try {
                    HashSet<ServerInternal.UserWrap> uWithCommonTags = ServerInternal.listUsers(u);
                    toRet = userWrap2String(uWithCommonTags);
                } catch (NotExistingUser e) {
                    toRet = "Some error"; // TODO
                }
                
            } 
        }
        // list followers
        else if (Pattern.matches("^list\\s+followers\\s*$", s)) {

        }
        // list following
        else if (Pattern.matches("^list\\s+following\\s*$", s)) {

        }
        // follow user
        else if (Pattern.matches("^follow\\s+\\S+\\s*$", s)) {

        }
        // unfollow user
        else if (Pattern.matches("^unfollow\\s+\\S+\\s*$", s)) {

        }
        // view blog
        else if (Pattern.matches("^blog\\s*$", s)) {

        }
        // create post
        else if (Pattern.matches("^post\\s+\\S+\\s+\\S+\\s*$", s)) {

        }
        // show feed
        else if (Pattern.matches("^show\\s+feed\\s*$", s)) {

        }
        // show post
        else if (Pattern.matches("^show\\s+post\\s+\\d+\\s*$", s)) {

        }
        // delete post
        else if (Pattern.matches("^delete\\s+post\\s+\\d+\\s*$", s)) {

        }
        // rewin post
        else if (Pattern.matches("^rewin\\s+post\\s+\\d+\\s*$", s)) {

        }
        // rate post
        else if (Pattern.matches("^rate\\s+post\\s+\\d+\\s+-?\\d+\\s*$", s)) {

        }
        // add comment
        else if (Pattern.matches("^add\\s+comment\\s+\\d+\\s+\\S+\\s*$", s)) {

        }
        // get wallet
        else if (Pattern.matches("^wallet\\s*$", s)) {

        }
        // get wallet bitcoin
        else if (Pattern.matches("^wallet\\s+btc\\s*$", s)) {

        } else {
            return toRet; // no matches, show help ? //TODO
        }

        return toRet;
    }
    
    private static String userWrap2String (HashSet<ServerInternal.UserWrap> users) {
        String toRet = "Utente \t|\t Tag\n";
        for (ServerInternal.UserWrap u : users) {
            toRet = toRet + u.username + " \t|\t " + Arrays.toString(u.tags) + "\n";
        }
        return toRet;
    }
}