import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashSet;

public interface ROCint extends Remote {
    /**
     * Used to notify a logged user about an update to its followers set
     * @param users updated set of followers
     * @throws RemoteException
     */
    public void newFollowers (HashSet<String> users, boolean shouldPrint) throws RemoteException;
    /**
     * Retrieve a logged user's name. Useful when the server has to send a notification
     * to a particular user regarding its followers
     * @return the logged user's username
     */
    public String name() throws RemoteException;
}
