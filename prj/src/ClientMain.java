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

            

            // TCP communication
            Client client = new Client(12345);
            client.start();

            return;
        
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
