import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;

@NoArgsConstructor
public class Post implements Comparable<Post>, Serializable {

    @Getter @Setter int idPost;
    @Getter @Setter String owner;
    @Getter @Setter String title;
    @Getter @Setter String content;
    @Getter @Setter Timestamp date;
    @Getter @Setter HashSet<String> upvote;
    @Getter @Setter HashSet<String> downvote;
    @Getter @Setter HashMap<String, HashSet<String>> comments;
    @Getter @Setter HashSet<String> rewiners;
    // useful to check if a post should appear in someone's feed

    // TODO reward
    @Getter @Setter public int rewardAlgorithmIterations = 0;
    @Getter @Setter public double reward = 0;

//    @JsonCreator
//    public Post() {
//        super();
//    }

    // default constructor
    public Post(String title, String content, String owner) {
        this.idPost = ServerInternal.idPostCounter++;
        this.owner = new String(owner);
        this.title = new String(title);
        this.content = new String(content);
        this.date = new Timestamp(System.currentTimeMillis());
        this.comments = new HashMap<>();
        this.rewiners = new HashSet<>();
        this.upvote = new HashSet<>();
        this.downvote = new HashSet<>();
        ServerInternal.posts.put(this.idPost, this);

        // This isn't automatically added to owner.posts
    }

    public int compareTo(Post p) {
        return p.idPost - this.idPost;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Post))
            return false;
        if (((Post) o).idPost == this.idPost)
            return true;
        return false;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.idPost);
    }


}
