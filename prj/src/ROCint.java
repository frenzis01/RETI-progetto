import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ROCint extends Remote {
    public void newFollowers (String[] users) throws RemoteException;
}
