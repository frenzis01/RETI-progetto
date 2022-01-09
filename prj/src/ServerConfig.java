import lombok.Getter;
import lombok.Setter;

public class ServerConfig {
    @Getter @Setter int tcpPort = 12345;
    @Getter @Setter int multicastPort = 6789;
    @Getter @Setter String multicastAddress = "239.255.1.3";
    @Getter @Setter int registryPort = 1900;
    @Getter @Setter double authorPercentage = 0.6;
    @Getter @Setter String backupDir = "../bkp/";
    @Getter @Setter long rewardTimeout = 10000;
    @Getter @Setter long logTimeout = 10000;
    
    public ServerConfig() {
        super();
    };
}
