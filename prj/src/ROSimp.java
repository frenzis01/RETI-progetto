import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import exceptions.ExistingUser;

public class ROSimp extends RemoteServer implements ROSint {
    private ConcurrentHashMap<ROCint, String> connectedClients;

    public ROSimp() {
        super();
        connectedClients = new ConcurrentHashMap<ROCint,String>();
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
        String clientName = ClientInterface.name();
        this.connectedClients.computeIfAbsent(ClientInterface, (v) -> {
            System.out.println("New client registered.");
            return clientName;
        });
        this.update(clientName, false);
    }

    public synchronized void unregisterForCallback(ROCint Client) throws RemoteException {
        if (this.connectedClients.remove(Client) != null)
            System.out.println("Client unregistered");
        else
            System.out.println("Unable to unregister client");
    }

    /*
     * 'followed' 's followers set has changed, one follower more or one less
     */
    public synchronized void update(String followed, boolean notFirstUpdate) throws RemoteException {
        // if a user can be logged from only one process, this set will contain only one element
        Set<ROCint> toNotify = this.connectedClients.entrySet()
        .stream()
        .filter( e -> {return e.getValue().equals(followed);} )
        .map(e -> e.getKey())
        .collect(Collectors.toSet());
        boolean shouldThrow = false; // used later on
        for (ROCint client : toNotify) {
            try {
            client.newFollowers(ServerInternal.getFollowers(followed)
            .stream()
            .map(u -> u.toString())
            .collect(Collectors.toCollection(HashSet::new)), notFirstUpdate);
            }
            catch (RemoteException e) {
                shouldThrow = true; // we'll throw this later, keep notifying other eventually available clients
            }
        }
        if (shouldThrow)
            throw new RemoteException();
    }
}
