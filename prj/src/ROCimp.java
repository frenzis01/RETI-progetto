import java.rmi.server.RemoteObject;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ROCimp extends RemoteObject implements ROCint {
    String username;
    HashSet<String> followers = new HashSet<String>();
    VolatileWrapper<Boolean> printEnable;

    public ROCimp(String username, VolatileWrapper<Boolean> printEnable) {
        super();
        this.username = new String(username);
        this.printEnable = printEnable;
    }

    public String name() {
        return username;
    }

    public void newFollowers(HashSet<String> users, boolean shouldPrint) {
        {
            // Generally, this will print only one user, since this method is invoked every
            // time an edit is performed on the client's followers

            // Print users who stopped following the user
            Set<String> unfollowers = this.followers.stream().filter((u) -> !users.contains(u))
                    .collect(Collectors.toSet());
            unfollowers.forEach(u -> {
                if (printEnable.v && shouldPrint)
                    System.out.println(u + " |-> no longer follows you");
                this.followers.remove(u);
            });
            // Print users who started following the user
            users.stream().filter((u) -> !this.followers.contains(u)).forEach(u -> {
                if (printEnable.v && shouldPrint)
                    System.out.println(u + " |-> has started following you");
                this.followers.add(u);
            });
        }
    }
}
