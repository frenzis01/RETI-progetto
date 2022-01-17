public class ClientMain {

    public static void main (String args[]) {
        try {

            // TCP communication
            Client client = new Client("../clientConfig.json");
            client.start(String.join(" ",args));
            System.exit(0);
            return;
        
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
