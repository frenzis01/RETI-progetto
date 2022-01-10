import java.util.Set;

public class ClientMain {

    public static void main (String args[]) {
        try {

            // TCP communication
            Client client = new Client("../clientConfig.json");
            client.start();

            return;
        
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
