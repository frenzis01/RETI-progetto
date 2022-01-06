import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.*;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class ClientMain {

    public static void main (String args[]) {
        try {

            // RMI
            ROSint server = (ROSint) LocateRegistry.getRegistry(1900).lookup("rmi://127.0.0.1:1900");

            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String registrationMsg = consoleReader.readLine().trim();
            while (Pattern.matches("^register\\s+\\S+\\s+\\S+\\s+.*\\s*$", registrationMsg) == false){
                System.out.println("Input error. Usage:\t register <username> <password> <space_separated_tags>");
                registrationMsg = consoleReader.readLine().trim();
            }
            String[] param = registrationMsg.split("\\s+", 4);
            System.out.println(server.register(param[1], param[2], param.length == 4 ? param[3] : ""));


            ROCint stub = (ROCint) UnicastRemoteObject.exportObject(new ROCimp(new String("username")), 0);
            server.registerForCallback(stub);

            // TCP communication
            Client client = new Client(12345);
            client.start();
            
            // Thread.sleep(5000); // do this on logout
            server.unregisterForCallback(stub);
            return;
        
        }
        catch (RemoteException e){
            System.out.println("Could not locate registry");
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
