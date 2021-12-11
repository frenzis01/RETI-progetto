import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashSet;
import java.util.Iterator;

import exceptions.ExistingUser;

public class ROSimp extends RemoteServer implements ROSint {
    private HashSet<ROCint> loggedUsers;
    public ROSimp () {
        super();
        loggedUsers = new HashSet<>();
    }

    public int register(String username, String password, String tags) throws RemoteException, ExistingUser {
        if (username == null || password == null || tags == null)
            throw new NullPointerException();
        if (ServerMain.usernameUnavailable(username))
            return 1;
        if (tags.split("\\s+").length > 5)
            return 2;
        ServerMain.addUser(username, password, tags);
        // debug
        System.out.println("DUMB New User registered: " + username + " " + password + " " + tags);
        
        return 0;
    }

    public synchronized void registerForCallback(ROCint ClientInterface) throws RemoteException {
        if (!loggedUsers.contains(ClientInterface)) {
            loggedUsers.add(ClientInterface);
            System.out.println("New client registered.");
        }
    }

    /* annulla registrazione per il callback */
    public synchronized void unregisterForCallback(ROCint Client) throws RemoteException {
        if (this.loggedUsers.remove(Client))
            System.out.println("Client unregistered");
        else
            System.out.println("Unable to unregister client");
    }

    /*
     * notifica di una variazione di valore dell'azione
     * /* quando viene richiamato, fa il callback a tutti i client
     * registrati
     */
    public synchronized void update(String followed) throws RemoteException {
        System.out.println("Starting callbacks.");
        String[] mock = {"test", "test2"};
        Iterator<ROCint> i = this.loggedUsers.iterator();
        while (i.hasNext()) {
            ROCint client = (ROCint) i.next();
            client.newFollowers(mock);
        }
        System.out.println("Callbacks complete.");
    }
}
