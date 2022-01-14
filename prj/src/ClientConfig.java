import lombok.Data;

public @Data class ClientConfig {
    String registryAddress = "localhost";
    String serverAddress = "localhost";
    String serverNameLookup = "winsomeServer";
    int registryPort = 1900;
    int serverPort = 12345;
    boolean cli = false;


    public ClientConfig() {super();}
}
