import java.rmi.Remote;
import java.rmi.RemoteException;

import exceptions.ExistingUser;

public interface ROSint extends Remote {
    int register(String username, String password, String tags) throws RemoteException, ExistingUser;

    public void registerForCallback(ROCint cli) throws RemoteException;

    public void unregisterForCallback(ROCint cli) throws RemoteException;
}
