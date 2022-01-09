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
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.regex.Pattern;

import exceptions.ExistingUser;

public class Client {
    private final String EXIT_CMD = "exit";
    private final int port;
    private volatile boolean exit;
    private volatile boolean logged = false;
    private ROSint server;
    private ROCint stub = null;

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
        try {
            // RMI
            this.server = (ROSint) LocateRegistry.getRegistry(1900).lookup("rmi://127.0.0.1:1900");
        } catch (NotBoundException | RemoteException e) {
            System.out.println("Could not locate registry");}

        Thread sniffer = null;

        try (SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", port));) {
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Client: connesso");
            System.out.println("Digita 'exit' per uscire, i messaggi scritti saranno inviati al server:");

            while (!this.exit) {

                // get new request from commandline
                String msg = consoleReader.readLine().trim();

                if (Pattern.matches("^register\\s+\\S+\\s+\\S+\\s+.*\\s*$", msg)) {
                    String[] param = msg.split("\\s+", 4);
                    try {
                        System.out.println(this.server.register(param[1], param[2], param.length == 4 ? param[3] : ""));
                    } catch (ExistingUser e) {
                        System.out.println("Cannot register: username already taken");
                    }
                    continue;
                }
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
                if (Pattern.matches("^logout\\s*$", msg)) {
                    if (logged){
                        sniffer.interrupt();
                        server.unregisterForCallback(stub);
                        System.out.println("Logged out");
                    }
                    continue;
                }
                

                String response = Util.readMsgFromSocket(client);
                System.out.println(response);

                if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", msg) &&
                        Pattern.matches("^-Successfully\\slogged\\sin:\\s+\\S+\\s+\\d+\\s*$", response)) {
                    logged = true;
                    String[] param = response.split("\\s+");
                    sniffer = new Thread(snifferThread(param[3], param[4]));
                    sniffer.start();

                    stub = (ROCint) UnicastRemoteObject.exportObject(new ROCimp(new String("username")), 0);
                    this.server.registerForCallback(stub);
                }

            }
            System.out.println("Client: logout");
        }
         catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Runnable snifferThread(String addr, String portStr) {
        return () -> {
            int port = Integer.parseInt(portStr);
            try {
                MulticastSocket ms = new MulticastSocket(port);
                InetSocketAddress group = new InetSocketAddress(InetAddress.getByName(addr), port);
                NetworkInterface netInt = NetworkInterface.getByName("wlan1");
                ms.joinGroup(group, netInt);

                byte[] buffer = new byte[8192];
                while (!this.exit && !Thread.currentThread().isInterrupted()) {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    ms.receive(dp); // blocking
                    // the only way to wake the Thread from ms.receive would be to call ms.close()
                    // on logout
                    // but that wouldn't allow us to call leaveGroup()
                    // in this way the thread will terminate on the next received datagram
                    if (Thread.currentThread().isInterrupted())
                        break;
                    System.out.println(new String(dp.getData()));
                }
                ms.leaveGroup(group, netInt);
                ms.close();
                return;
            } catch (SocketException e) {
                System.out.println("|ERROR: snifferThread MulticastSocket closed");
            } catch (IOException e) {
                System.out.println("|ERROR: snifferThread");
                e.printStackTrace();
            }
        };
    }

}
