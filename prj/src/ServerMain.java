import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

public class ServerMain {

    public ServerMain() {
        super();
    }

    public static void main(String args[]) {
        try {

            // RMI setup
            ROSimp serverRMI = new ROSimp();
            ROSint stub = (ROSint) UnicastRemoteObject.exportObject(serverRMI, 39000);
            LocateRegistry.createRegistry(1900);
            LocateRegistry.getRegistry(1900).rebind("rmi://127.0.0.1:1900", stub);
            // Now ready to handle RMI registration and followers's update notifications
            
            // testing stuff
            ServerInternal.addUser("stub1", "", "tag1 tag2");
            ServerInternal.addUser("stub2", "", "tag1 tag2");

            // TCP server setup
            Server server = new Server(12345);
            server.start();


            // while(true) {
            // Thread.sleep(800);
            // server.update("");
            // }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}