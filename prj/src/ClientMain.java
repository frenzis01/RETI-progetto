import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.*;
import java.util.ArrayList;

public class ClientMain {

    public static void main (String args[]) {
        try {
            // RMI


            // RMI Callback
            // Registry registry = LocateRegistry.getRegistry(12345);  
            // ServerInterface server = (ServerInterface) registry.lookup("Server");
            
            // NotifyFollowersInt callbackObj =  new NotifyFollowers();
            // NotifyFollowers stub = (NotifyFollowers) UnicastRemoteObject.exportObject(callbackObj, 0);

            ROSint server = (ROSint) LocateRegistry.getRegistry(1900).lookup("rmi://127.0.0.1:1900");

            System.out.println(server.register("username", "test", "tag1 tag2"));

            ROCint stub = (ROCint) UnicastRemoteObject.exportObject(new ROCimp("username"), 0);
            server.registerForCallback(stub);

            Thread.sleep(5000); // do this on logout
            server.unregisterForCallback(stub);
            return;
        
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
