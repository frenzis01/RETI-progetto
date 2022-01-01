import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Client {
    private final int BUFFER_DIMENSION = 1024;
    private final String EXIT_CMD = "exit";
    private final int port;
    private boolean exit;

    /**
     * Default constructor
     * 
     * @param port listening port
     */
    public Client(int port) {
        this.port = port;
        this.exit = false;
    }

    public void start() {

        try (SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", port));) {
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Client: connesso");
            System.out.println("Digita 'exit' per uscire, i messaggi scritti saranno inviati al server:");

            while (!this.exit) {

                // get new request from commandline
                String msg = consoleReader.readLine().trim();

                // we'll write first the length of the request
                ByteBuffer request = ByteBuffer.allocate(Integer.BYTES + msg.getBytes().length);
                request.putInt(msg.length()); // add request length before the request itself

                ByteBuffer readBuffer = ByteBuffer.wrap(msg.getBytes());
                // no explicit encoding using UTF_8
                request.put(readBuffer);
                request.flip();
                client.write(request);
                request.clear();
                readBuffer.clear();

                // We are going to exit after we read the server's response
                // It's important to notify the server about the logout
                if (msg.equals(this.EXIT_CMD)) {
                    this.exit = true;
                    continue;
                }
                
                System.out.println(Util.readMsgFromSocket(client));

            }
            System.out.println("Client: logout");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
