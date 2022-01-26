public class ClientMain {

    public static void main (String args[]) {
        try {

            Client client = new Client(args.length > 0 && args[0].matches(".*json") ? args[0] : "../config/clientConfig.json");
            client.start(String.join(" ",args));
            
            // Some RMI threads seem to be keeping the JVM on, we have to shutdown manually
            System.exit(0);
            return;
        
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
