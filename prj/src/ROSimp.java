import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import exceptions.ExistingUser;

public class ROSimp extends RemoteServer implements ROSint {
    private HashMap<String,ROCint> loggedUsers;
    public ROSimp () {
        super();
        loggedUsers = new HashMap<>();
    }

    public int register(String username, String password, String tags) throws RemoteException, ExistingUser {
        if (username == null || password == null || tags == null)
            throw new NullPointerException();
        if (ServerInternal.usernameUnavailable(username) == true)
            return 1;
        if (tags.split("\\s+").length > 5)
            return 2;
        
        ServerInternal.addUser(new String(username), new String(password), new String(tags));
        // debug
        System.out.println("DUMB New User registered: " + username + " " + password + " " + tags);
        return 0;
    }

    public synchronized void registerForCallback(ROCint ClientInterface) throws RemoteException {
        if (!loggedUsers.containsValue(ClientInterface)) {
            loggedUsers.put(ClientInterface.name(), ClientInterface);
            System.out.println("New client registered.");
        }
    }

    /* annulla registrazione per il callback */
    public synchronized void unregisterForCallback(ROCint Client) throws RemoteException {
        if (this.loggedUsers.remove(Client.name()) != null)
            System.out.println("Client unregistered");
        else
            System.out.println("Unable to unregister client");
    }

    /*
     * notifica di una modifica (follower in più o in meno) ai follower di 'followed'
     */
    public synchronized void update(String followed) throws RemoteException {
        System.out.println("Starting callbacks.");
        if (this.loggedUsers.containsKey(followed)) {
            this.loggedUsers.get(followed).newFollowers(ServerInternal.getFollowers(followed));
        }
        System.out.println("Callbacks complete.");
    }
}
