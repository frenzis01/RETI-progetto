import lombok.Data;
import lombok.Getter;
import lombok.Setter;

public @Data class ServerConfig {
    int tcpPort = 12345;
    int multicastPort = 6789;
    String multicastAddress = "239.255.1.3";
    int registryPort = 1900;
    double authorPercentage = 0.6;
    String backupDir = "../bkp/";
    long rewardTimeout = 10000;
    long logTimeout = 10000;
    long btcTimeout = 5000;
    
    public ServerConfig() {
        super();
    };
}
