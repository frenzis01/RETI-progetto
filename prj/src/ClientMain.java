import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.*;
import java.util.ArrayList;

public class ClientMain {

    public static void main (String args[]) {
        try {

            // RMI
            ROSint server = (ROSint) LocateRegistry.getRegistry(1900).lookup("rmi://127.0.0.1:1900");

            System.out.println(server.register("username", "test", "tag1 tag2"));
            System.out.println(server.register("username", "test", "tag1 tag2"));

            ROCint stub = (ROCint) UnicastRemoteObject.exportObject(new ROCimp(new String("username")), 0);
            server.registerForCallback(stub);

            // TCP communication
            Client client = new Client(12345);
            client.start();
            
            // Thread.sleep(5000); // do this on logout
            server.unregisterForCallback(stub);
            return;
        
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
