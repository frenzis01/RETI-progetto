import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import exceptions.ExistingUser;

public class ROSimp extends RemoteServer implements ROSint {
    private ConcurrentHashMap<String,ROCint> loggedUsers;
    public ROSimp () {
        super();
        loggedUsers = new ConcurrentHashMap<>();
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
        System.out.println("New User registered: " + username + " " + password + " " + tags);
        return 0;
    }

    public synchronized void registerForCallback(ROCint ClientInterface) throws RemoteException {
        if (!loggedUsers.containsValue(ClientInterface)) {
            loggedUsers.put(ClientInterface.name(), ClientInterface);
            System.out.println("New client registered.");
            this.update(ClientInterface.name(), true);
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
    public synchronized void update(String followed, boolean notFirstUpdate) throws RemoteException {
        // System.out.println("Callback to -> " + followed);
        if (this.loggedUsers.containsKey(followed)) {
            // the client still might exit during the execution of this block
            this.loggedUsers.get(followed).
                newFollowers(ServerInternal.getFollowers(followed)
                .stream()
                .map( u -> u.toString())
                .collect(Collectors.toCollection(HashSet::new)), notFirstUpdate);
            // ServerInternal.getFollowers(followed).forEach((u) -> System.out.println(" " + u));
        }
        // System.out.println("Callback to -> " + followed + " completed");
    }
}
