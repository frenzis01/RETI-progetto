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
