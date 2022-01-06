import java.rmi.Remote;
import java.rmi.RemoteException;

import exceptions.ExistingUser;

public interface ROSint extends Remote {
    /**
     * Winsome's registration
     * @param username
     * @param password
     * @param tags     max 5 tags
     * @return 0 success, 1 already existing user, 2 too many tags
     * @throws ExistingUser
     * @throws NullPointerException
     */
    int register(String username, String password, String tags) throws RemoteException, ExistingUser;

    public void registerForCallback(ROCint cli) throws RemoteException;

    public void unregisterForCallback(ROCint cli) throws RemoteException;

}
