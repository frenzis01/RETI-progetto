import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

class Server {
    /**
     * dimensione del buffer utilizzato per la lettura
     */
    private final int BUFFER_DIM = 2048;
    /**
     * comando utilizzato dal client per comunicare la fine della comunicazione
     */
    private final String EXIT_CMD = "logout";
    /**
     * porta su cui aprire il listening socket
     */
    private final int port;
    /**
     * messaggio di risposta
     */
    private final String ADD_ANSWER = "echoed by server";
    /**
     * numero di client con i quali è aperta una connessione
     */
    public int activeConnections;

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

                            this.registerRead(sel, c_channel); // Server is going to read from this channel

                        } else if (key.isReadable()) { // READABLE
                            this.getClientRequest(sel, key); // parse request
                        }

                        else if (key.isWritable()) { // WRITABLE
                            this.sendResult(sel, key); // TODO write request result
                        }
                    } catch (IOException e) { // if the client prematurely disconnects
                        e.printStackTrace();
                        key.channel().close();
                        key.cancel();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Register OP_READ on the Selector
     *
     * @param sel       selector used in Server.start
     * @param c_channel Client's channel
     * @throws IOException
     */
    private void registerRead(Selector sel, SocketChannel c_channel) throws IOException {

        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer message = ByteBuffer.allocate(BUFFER_DIM);
        ByteBuffer[] bfs = { length, message };
        // add [length, message] as attachment
        c_channel.register(sel, SelectionKey.OP_READ, bfs);
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
        ByteBuffer[] bfs = (ByteBuffer[]) key.attachment();
        c_channel.read(bfs); // actual read

        if (!bfs[0].hasRemaining()) {
            bfs[0].flip();
            int l = bfs[0].getInt();

            if (bfs[1].position() == l) {
                bfs[1].flip();
                String msg = new String(bfs[1].array()).trim();
                System.out.printf("Server: ricevuto %s | %d\n", msg, parseRequest(msg));
                if (msg.equals(this.EXIT_CMD)) { // client logged out
                    // TODO handle logout
                    System.out.println("Server: logout from client " + c_channel.getRemoteAddress());
                    key.cancel();
                    c_channel.close();
                } else {
                    String result = msg + " " + this.ADD_ANSWER; // TODO get actual request result
                    ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
                    length.putInt(result.length());
                    length.flip();

                    ByteBuffer message = ByteBuffer.wrap(result.getBytes());
                    
                    ByteBuffer[] atc = {length , message};
                    c_channel.register(sel, SelectionKey.OP_WRITE, atc);
                    // c_channel.register(sel, SelectionKey.OP_WRITE, result);
                }
            }
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
        ByteBuffer toSend = ByteBuffer.allocate(answer[0].remaining() + answer[1].remaining()).put(answer[0]).put(answer[1]);
        toSend.flip();
        c_channel.write(toSend);
        toSend.clear();

        answer[0].flip();
        System.out.println("Server: sent "+ answer[0].getInt() + " bytes containing: " + new String(toSend.array()).trim() + " inviato al client " + c_channel.getRemoteAddress());
        if (toSend.hasRemaining()) {
            toSend.clear();
            this.registerRead(sel, c_channel);
        }

    }

    private int parseRequest(String s) {
        // TODO logout gets recognized before calling parseRequest using EXIT_CMD (?)
        // No...?
        // login
        if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", s)) {
            
        }
        // logout
        else if (Pattern.matches("^logout\\s+\\S+\\s*$", s)) {

        }
        // list users
        else if (Pattern.matches("^list\\s+users\\s*$", s)) {

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
        
        }
        else {
            return 0; // no matches, show help ? //TODO
        }


        return 0;
    }
}
