import lombok.Getter;
import lombok.Setter;

public class ClientConfig {
    @Getter @Setter String registryAddress = "localhost";
    @Getter @Setter String serverAddress = "localhost";
    @Getter @Setter String serverNameLookup = "winsomeServer";
    @Getter @Setter int registryPort = 1900;
    @Getter @Setter int serverPort = 12345;

    public ClientConfig() {super();}
}
