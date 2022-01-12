import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;

@NoArgsConstructor
public @Data class Post implements Comparable<Post>, Serializable {

    int idPost;
    String owner;
    String title;
    String content;
    Timestamp date;
    HashSet<String> upvote;
    HashSet<String> downvote;
    HashMap<String, HashSet<String>> comments;
    HashSet<String> rewiners;
    // useful to check if a post should appear in someone's feed

    public int rewardIterationsOnCreation = 0;
    public double reward = 0;

    // default constructor
    public Post(String title, String content, String owner) {
        this.idPost = ServerInternal.getIdPostCounter();
        this.rewardIterationsOnCreation = ServerInternal.getRewardIterations();
        this.owner = new String(owner);
        this.title = new String(title);
        this.content = new String(content);
        this.date = new Timestamp(System.currentTimeMillis());
        this.comments = new HashMap<>();
        this.rewiners = new HashSet<>();
        this.upvote = new HashSet<>();
        this.downvote = new HashSet<>();
        // ServerInternal.posts.put(this.idPost, this);
        // This is done by createPost in serverInternal
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
