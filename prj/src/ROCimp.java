import java.rmi.server.RemoteObject;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ROCimp extends RemoteObject implements ROCint {
    String username;
    HashSet<String> followers = new HashSet<String>();
    boolean printEnable = true;

    public ROCimp(String username, boolean printEnable) {
        super();
        this.username = new String(username);
        this.printEnable = printEnable;
    }

    public String name() {
        return username;
    }

    public void newFollowers(HashSet<String> users, boolean shouldPrint) {
        // System.out.println(this.username + " followers list updated");
        {
            Set<String> unfollowers = this.followers.stream().filter((u) -> !users.contains(u))
                    .collect(Collectors.toSet());
            unfollowers.forEach(u -> {
                if (printEnable && shouldPrint)
                    System.out.println(u + " |-> no longer follows you");
                this.followers.remove(u);
            });
            users.stream().filter((u) -> !this.followers.contains(u)).forEach(u -> {
                if (printEnable && shouldPrint)
                    System.out.println(u + " |-> has started following you");
                this.followers.add(u);
            });
        }
        // this.followers = new HashSet<String>(users);
        // this re-assignment should be equal to:
        // this.followers.retainAll(users);
        // this.followers.addAll(users)
    }
}
