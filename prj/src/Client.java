import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.regex.Pattern;

public class Client {
    private final int BUFFER_DIMENSION = 1024;
    private final String EXIT_CMD = "exit";
    private final int port;
    private volatile boolean exit;
    private MulticastSocket ms = null;
    private String mltAddr, mltPort;

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
        Thread sniffer = null;

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

                String response = Util.readMsgFromSocket(client);
                System.out.println(response);

                if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", msg) &&
                        Pattern.matches("^-Successfully\\slogged\\sin:\\s+\\S+\\s+\\d+\\s*$", response)) {
                    String param[] = response.split("\\s+");
                    sniffer = new Thread(snifferThread(param[3], param[4]));
                    sniffer.start();
                } else if (Pattern.matches("^logout\\s*$", msg)) {
                    // ms.leaveGroup(mltAddr, Integer.parseInt(mltPort));
                    ms.close();
                }

            }
            System.out.println("Client: logout");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Runnable snifferThread(String addr, String portStr) {
        return () -> {
            int port = Integer.parseInt(portStr);
            try {
                this.ms = new MulticastSocket(port);
                InetSocketAddress group = new InetSocketAddress(InetAddress.getByName(addr), port);
                NetworkInterface netInt = NetworkInterface.getByName("wlan1");
                this.ms.joinGroup(group, netInt);

                byte[] buffer = new byte[8192];
                while (!this.exit) {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    ms.receive(dp); // blocking
                    System.out.println(new String(dp.getData()));
                }
                ms.leaveGroup(group, netInt); // doing this after ms.close in client.start() will explode?
                // this.ms will be closed by this.start()
                // if the thread is stuck on ms.receive(dp), closing the socket will make it return immediately
                return;
            }
            catch (SocketException e) {
                System.out.println("|ERROR: snifferThread MulticastSocket closed");
            }
            catch (IOException e) {
                System.out.println("|ERROR: snifferThread");
                e.printStackTrace();
            }
        };
    }

}
