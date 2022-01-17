import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.ConnectIOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import exceptions.ExistingUser;

public class Client {
    private volatile boolean exit = false;
    private volatile boolean logged = false;
    // setted to false as default value to avoid annoying notification messages
    // while typing commands
    private volatile boolean udpPrintEnable = false;
    private static boolean printEnable = false;
    private ROSint server;
    private ROCint stub = null;

    private ClientConfig config;
    private MulticastSocket ms = null; // last active multicast socket
    // private ConcurrentHashMap<MulticastSocket,Integer> map = new ConcurrentHashMap<MulticastSocket,Integer>();
    private Set<MulticastSocket> socketsToBeClosed = ConcurrentHashMap.newKeySet();
    /**
     * Default constructor
     * 
     * @param port listening port
     */
    public Client(String configFilePath) {
        this.config = readConfigFile(configFilePath);
    }

    private ClientConfig readConfigFile(String configFilePath) {
        ClientConfig clientConfig = new ClientConfig();
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File(configFilePath);
        if (configFile.exists()) {
            try {
                BufferedReader clientReader = new BufferedReader(new FileReader(configFile));
                clientConfig = mapper.readValue(clientReader, new TypeReference<ClientConfig>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

            print("Configuration file read.\nConfiguring the client as follows:");
            for (Field f : clientConfig.getClass().getDeclaredFields()) {

                try {
                    print(" " + f.getName() + " : " + f.get(clientConfig));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            print("");
        } else
            print("Config file not found. Using default values.");
        printEnable = !clientConfig.cli;
        return clientConfig;
    }

    public void start(String cliCommands) {
        // RMI setup
        try {
            this.server = (ROSint) LocateRegistry.getRegistry(this.config.registryAddress, this.config.registryPort)
                    .lookup(this.config.serverNameLookup);
        } catch (NotBoundException | RemoteException e) {
            print("Could not locate registry");
            return;
        }

        // this thread will later get udp notifications
        Thread sniffer = null;

        // TCP communication
        try (SocketChannel client = SocketChannel
                .open(new InetSocketAddress(this.config.serverAddress, this.config.serverPort));) {

            String[] commands = cliCommands.split("\\+\\s*");
            int iCliCommands = -1;

            // we'll use this to read from cmdline
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

            print(
                    "Connected to the Server\n" +
                            "Type 'exit' to close application.\n" +
                            "Type 'udp print' to enable/disable notifications printing");


            // Now the client is connected
            while (!this.exit && iCliCommands < commands.length - 1) {
                
                iCliCommands = config.cli ? iCliCommands+1 : iCliCommands;

                // get new request from commandline
                String msg = config.cli ? commands[iCliCommands] : consoleReader.readLine().trim();

                // "Rewards calculated" print enable/disable
                if (Pattern.matches("^udp\\s+print\\s*$", msg)) {
                    udpPrintEnable = !udpPrintEnable;
                    print(
                            "Reward calculation notification is now " + (udpPrintEnable ? "enabled" : "disabled"));
                    continue;
                }

                // register
                if (Pattern.matches("^register\\s+\\S+\\s+\\S+\\s+.*\\s*$", msg)) {
                    String[] param = msg.split("\\s+", 4);
                    try {
                        print("" + this.server.register(param[1], param[2], param.length == 4 ? param[3] : ""));
                    } catch (ExistingUser e) {
                        print("Cannot register: username already taken");
                    }
                    continue;
                }

                // login
                if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", msg) && logged) {
                    print("User already logged in. Execute 'logout' first");
                    continue;
                }

                // We exit after sending the exit message
                // It's important to notify the server about the logout
                if (Pattern.matches("^exit\\s*$", msg)) {
                    this.exit = true;
                    client.close();
                    continue;
                }

                // Generic request
                // we'll write first the length of the request
                ByteBuffer request = ByteBuffer.allocate(Integer.BYTES + msg.getBytes().length);
                request.putInt(msg.length()); // add request length before the request itself

                ByteBuffer readBuffer = ByteBuffer.wrap(msg.getBytes());
                request.put(readBuffer);
                request.flip();
                client.write(request);
                request.clear();
                readBuffer.clear();


                // logout
                if (Pattern.matches("^logout\\s*$", msg)) {
                    if (logged) {
                        sniffer.interrupt();
                        // sniffer = null;
                        server.unregisterForCallback(stub);
                        print("Logged out");
                        logged = false;
                    } else
                        print("No user logged");
                    continue;
                }

                String response = "";

                // Server's Response
                try {
                    response = Util.readMsgFromSocket(client);
                    print(response);
                    
                } catch (Exception e) {
                    print("Server closed the connection");
                    this.exit = true;
                    client.close();
                    continue;
                }

                // login
                if (Pattern.matches("^login\\s+\\S+\\s+\\S+\\s*$", msg) &&
                        Pattern.matches("^-Successfully\\slogged\\sin:\\s+\\S+\\s+\\d+\\s*$", response)) {
                    logged = true;
                    String[] param = response.split("\\s+");
                    String username = new String(msg.split("\\s+")[1]);
                    sniffer = new Thread(snifferThread(param[3], param[4]));
                    sniffer.start();

                    // we must register for callback to get the logged user's followers
                    stub = (ROCint) UnicastRemoteObject.exportObject(new ROCimp(username), 0);
                    this.server.registerForCallback(stub);
                }


            }

            print("Exiting");
            if (stub != null)
                server.unregisterForCallback(stub);
            if (ms != null)
                this.ms.close(); // this will wake the sniffer thread
            print("CLOSING SOCKETS");
            socketsToBeClosed.forEach((s) -> { print(s.toString()); s.close(); });
            if (sniffer != null) {
                try {
                    // sniffer.interrupt(); // not necessary
                    sniffer.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            // threadSet.forEach((t) -> {print(t.getId() + " | " + t.getName()
            // + " | " + t.toString()) ;});
            // Some RMI threads seem to be keeping the JVM on, we have to shutdown manually
            return;
        }
        catch (ConnectException | java.rmi.ConnectException | ConnectIOException | UnmarshalException e){
            print("Could not connect to server");
        } 
        catch (IOException e) {
            print("|ERROR Some error in the connection with the server");
            e.printStackTrace();
        }
        return;
    }

    private Runnable snifferThread(String addr, String portStr) {
        return () -> {
            int port = Integer.parseInt(portStr);
            try {
                
                MulticastSocket myMCskt = this.ms = new MulticastSocket(port); // this.ms might get overwritten in the meantime
                this.socketsToBeClosed.add(myMCskt);
                // we must keep a reference
                InetSocketAddress group = new InetSocketAddress(InetAddress.getByName(addr), port);
                NetworkInterface netInt = NetworkInterface.getByName("wlan1");
                myMCskt.joinGroup(group, netInt);

                byte[] buffer = new byte[8192];
                while (!this.exit && !Thread.currentThread().isInterrupted()) {
                    DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                    myMCskt.receive(dp); // blocking
                    
                    // the only way to wake the Thread from myMCskt.receive would be to call
                    // myMCskt.close()
                    // on logout
                    // but that wouldn't allow us to call leaveGroup()
                    // in this way the thread will terminate on the next received datagram

                    // in case we the user types in 'exit' the MulticastSocket gets closed and the
                    // thread wakes immediately

                    if (Thread.currentThread().isInterrupted())
                        break;
                    if (udpPrintEnable)
                        print(new String(dp.getData()));
                }
                myMCskt.leaveGroup(group, netInt);
                myMCskt.close();
                myMCskt = null;
                return;
            } catch (SocketException e) {
                if (!this.exit)
                    print("|ERROR: snifferThread MulticastSocket closed");
                // e.printStackTrace();
                return;
            } catch (IOException e) {
                print("|ERROR: snifferThread");
                e.printStackTrace();
            }
            print("Never printed");
            return;
        };
    }

    private static void print(String s){
        if (printEnable)
            print(s);
    }

}
