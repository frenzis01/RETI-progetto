import java.rmi.server.RemoteObject;
import java.util.HashSet;

public class ROCimp extends RemoteObject implements ROCint {
    String username;
    HashSet <String> followers;
    public ROCimp (String username) { super(); this.username = new String(username); }

    public String name () { return new String(username);}

    public void newFollowers (HashSet<String> users) {
        System.out.println(this.username + " followers list updated");
        this.followers = new HashSet<String>(users); // TODO any cleaner way to this?
        // this re-assignment should be equal to:
        // this.followers.retainAll(users);
        // this.followers.addAll(users)
    }
}
