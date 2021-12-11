import java.rmi.server.RMISocketFactory;
import java.rmi.server.RemoteObject;

public class ROCimp extends RemoteObject implements ROCint {
    public ROCimp () { super();}

    public void newFollowers (String[] users) {
        System.out.println("Dumb newfollowers");
    }
}
