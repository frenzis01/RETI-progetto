import exceptions.ExistingUser;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@NoArgsConstructor
class User implements Comparable<User>, Serializable {
    @Getter @Setter String username;
    @Getter @Setter String password;

    @Getter @Setter HashSet<Integer> blog; 
    // same thing for users
    @Getter @Setter HashSet<String> followers;
    @Getter @Setter HashSet<String> following;

    @Getter @Setter double wallet = 0;
    @Getter @Setter List<Transaction> walletHistory = new ArrayList<Transaction>();

    @Getter @Setter String[] tags; // tags can't be modified


    public User(String username, String password, String tags) throws ExistingUser {
        if (ServerInternal.usernameUnavailable(username))
            throw new ExistingUser(); // this should already have been checked

        this.username = new String(username);
        this.password = new String(password);
        tags.toLowerCase();
        this.tags = tags.split("\\s+");
        // I don't care to check that there aren't more than 5 tags here
        // It's not this constructor's responsibility to check it
        for (String tag : this.tags) 
            ServerInternal.add2tag(username, tag);
        

        this.followers = new HashSet<String>();
        this.following = new HashSet<String>();
        this.blog = new HashSet<Integer>();

        // ServerInternal.followers.put(new String(this.username), new HashSet<String>());
        // this is done by ServerInternal
    }

    public int compareTo(User u) {
        return this.username.compareTo(u.username);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof User))
            return false;
        if (((User) o).username.equals(this.username))
            return true;
        return false;
    }

    @Override
    public int hashCode() {
        return this.username.hashCode();
    }

    // private static final SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");

    public String walletHistoryToString() {
        return walletHistory.stream()
        .map( t -> t.ts + " : " + t.value.toString())
        .collect(Collectors.joining("\n"));
    }

}
