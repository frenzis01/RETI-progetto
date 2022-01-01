import java.nio.channels.*;
import java.net.InetSocketAddress;
import java.nio.*;
import java.rmi.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import exceptions.ExistingUser;
import exceptions.NotExistingPost;
import exceptions.NotExistingUser;

public class ServerInternal {

    private static int idPostCounter = 0; // i will write this in a json file
    private static HashSet<String> activeUsernames = new HashSet<>();
    private static HashMap<String, User> users = new HashMap<>();
    private static HashMap<String, HashSet<User>> tagsUsers = new HashMap<>();
    private static HashMap<Integer, Post> posts = new HashMap<>();

    // redundant who-is-following-who to avoid recalculating on every update
    // we need it to notify the logged users
    private static HashMap<String, HashSet<String>> followers = new HashMap<>();

    public ServerInternal() {
        super();
    }

    private void createStubUser() throws ExistingUser {
        addUser("fakeusr", "stub", "tag1");
    }

    public static Boolean usernameUnavailable(String username) {
        return activeUsernames.contains(username);
    };

    public static void addUser(String username, String password, String tags) throws ExistingUser {
        users.put(username, new ServerInternal().new User(username, password, tags));
        activeUsernames.add(username);
    }

    public static HashSet<String> getFollowers(String username) {
        return new HashSet<String>(users.get(username).followers);
    }

    /**
     * 
     * @param username
     * @param password
     * @return 0 success, 1 login failed
     * @throws NullPointerException
     */
    public static int login(String username, String password) {
        if (username == null || password == null)
            throw new NullPointerException();
        if (users.containsKey(username) && users.get(username).password.equals(password))
            return 0;
        return 1;
    };

    /**
     * 
     * Does this have any sense at all ?
     * //TODO
     * 
     * @param username
     */
    public static void logout(String username) {
        if (username == null)
            throw new NullPointerException();

    };

    /**
     * 
     * @param username
     * @return the set of users who have at least on tag in common with the
     *         requestor
     * @throws NotExistingUser
     */
    public static HashSet<UserWrap> listUsers(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<UserWrap> toRet = new HashSet<>();
        for (String tag : user.tags) {
            tagsUsers.get(tag).forEach((u) -> {
                if (!u.username.equals(username)) {
                    toRet.add(new ServerInternal().new UserWrap(u)); // TODO okay to instantiate ServerInternal like
                                                                     // this?
                }
            });
        }
        return toRet;
    };

    /**
     * @param username
     * @return the set of users who are following the requestor
     * @throws NotExistingUser
     */
    public static HashSet<UserWrap> listFollowers(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<UserWrap> toRet = new HashSet<>();
        for (String follower : user.followers)
            toRet.add(new ServerInternal().new UserWrap(users.get(follower)));
        return toRet;
    };

    /**
     * @param username
     * @return the set of users followed by the requestor
     * @throws NotExistingUser
     */
    public static HashSet<UserWrap> listFollowing(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<UserWrap> toRet = new HashSet<>();
        for (String followed : user.following)
            toRet.add(new ServerInternal().new UserWrap(users.get(followed)));
        return toRet;
    };

    /**
     * Removes a user from the requestor set of followed users
     * 
     * @param toFollow
     * @param username
     * @return
     * @throws NotExistingUser
     */
    public static int followUser(String toFollow, String username) throws NotExistingUser {
        User user = checkUsername(username);
        User followed = checkUsername(toFollow);
        user.following.add(toFollow);
        followed.followers.add(username);
        ServerInternal.followers.get(toFollow).add(username);
        return 0;
    };

    /**
     * Removes a user from the requestor set of followed users
     * 
     * @param toUnfollow
     * @param username
     * @return
     * @throws NotExistingUser
     */
    public static int unfollowUser(String toUnfollow, String username) throws NotExistingUser {
        User user = checkUsername(username);
        User followed = checkUsername(toUnfollow);
        user.following.remove(toUnfollow);
        followed.followers.remove(username);
        ServerInternal.followers.get(toUnfollow).remove(username);
        // TODO should I report an error in case of "already not following"? Don't think
        // so
        return 0;
    };

    /**
     * @param username
     * @return set of posts made or rewined by the user
     * @throws NotExistingUser
     */
    public static HashSet<PostWrap> viewBlog(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<PostWrap> toRet = new HashSet<>();
        for (Integer idPost : user.blog) {
            toRet.add(new ServerInternal().new PostWrap(ServerInternal.posts.get(idPost)));
        }
        return toRet;
    }; // displays the logged user's blog, no username param needed

    /**
     * add a Post to the client's blog
     * 
     * @param titolo
     * @param contenuto
     * @param username
     * @return
     * @throws NotExistingUser
     */
    public static PostWrap createPost(String titolo, String contenuto, String username) throws NotExistingUser {
        User user = checkUsername(username);
        if (titolo == null || contenuto == null)
            throw new NullPointerException();
        Post newPost = new ServerInternal().new Post(titolo, contenuto, username);
        user.blog.add(newPost.idPost);
        return new ServerInternal().new PostWrap(newPost);
    };

    /**
     * @param username
     * @return the set of posts made or rewined by the users followed by the
     *         requestor
     * @throws NotExistingUser
     */
    public static HashSet<PostWrap> showFeed(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<PostWrap> toRet = new HashSet<>();
        // iterates over the set of users the client follows
        // for each of them retrieves all of their posts (aka blog)
        for (String followed : user.following) {
            User followedUser = checkUsername(followed); // this should never throw an exception
            followedUser.blog.forEach((Integer p) -> {
                toRet.add(new ServerInternal().new PostWrap(posts.get(p)));
            });
        }
        return toRet;
    };

    /**
     * Remove a post from winsome
     * 
     * @param idPost
     * @param username
     * @return 0 success, 1 user isn't the post owner
     * @throws NotExistingUser
     * @throws NotExistingPost
     */
    public static int deletePost(int idPost, String username) throws NotExistingUser, NotExistingPost {
        User user = checkUsername(username);
        Post p = checkPost(idPost);
        if (username.equals(p.owner)) {
            posts.remove(idPost);
            // We have to remove the post from every blog
            /**
             * //TODO this has linear cost. Would it be better to skip this session
             * and every time that we reference a post from a user's blog check if the post
             * actually exists? it might be better in terms of performance, but requires a
             * better
             * error management system
             */
            users.forEach((String name, User u) -> {
                u.blog.remove(idPost);
            });
            return 0;
        }
        return 1; // the client isn't the owner
    };

    /**
     * rewin a Post made by another user. A client cannot rewin its own posts
     * 
     * @param idPost
     * @param username
     * @return 0 success, 1 user is the post owner
     * @throws NotExistingUser
     * @throws NotExistingPost
     */
    public static int rewinPost(int idPost, String username) throws NotExistingUser, NotExistingPost {
        User user = checkUsername(username);
        Post p = checkPost(idPost);
        if (username.equals(p.owner))
            return 1;
        user.blog.add(p.idPost);
        p.rewiners.add(username);
        return 0;
    };

    /**
     * 
     * @param idPost
     * @param username
     * @return
     * @throws NotExistingUser
     * @throws NotExistingPost
     */
    public static PostWrap showPost(int idPost, String username) throws NotExistingUser, NotExistingPost {
        User user = checkUsername(username);
        Post p = checkPost(idPost);
        return new ServerInternal().new PostWrap(p);
    }

    /**
     * 
     * @param idPost
     * @param vote
     * @param username
     * @return 0 success, 1 given post isn't in the user's feed, 2 user has already voted
     * @throws NotExistingUser
     * @throws NotExistingPost
     */
    public static int ratePost(int idPost, int vote, String username) throws NotExistingUser, NotExistingPost {
        User user = checkUsername(username);
        Post p = checkPost(idPost);
        // check if the post is in the user's feed
        if (!checkFeed(user, p))
            return 1;
        // check if user has already voted
        if (p.upvote.contains(username) || p.downvote.contains(username))
            return 2;
        if (vote >= 0)
            p.upvote.add(username);
        else
            p.downvote.add(username);
        return 0;
    };

    /**
     * add comment to a Post, a user can add more than one comment to a single post.
     * A user can comment a Post
     * only if it is in its feed
     * 
     * @param idPost
     * @param comment
     * @param username
     * @return 1 posts isnt in user's feed
     * @throws NotExistingUser
     * @throws NotExistingPost
     */
    public static int addComment(int idPost, String comment, String username) throws NotExistingUser, NotExistingPost {
        User user = checkUsername(username);
        Post p = checkPost(idPost);
        if (!checkFeed(user, p))
            return 1;
        if (!p.comments.containsKey(username))
            p.comments.put(username, new HashSet<>());
        p.comments.get(username).add(comment);
        return 0;
    };
    // TODO public static Transaction[] getWallet (){};
    // TODO public static Transaction[] getWalletInBitcoin (){};

    private static User checkUsername(String username) throws NotExistingUser {
        if (username == null)
            throw new NullPointerException();
        if (!users.containsKey(username))
            throw new NotExistingUser();
        return users.get(username);
    }

    /**
     * check's if post exists
     */
    private static Post checkPost(int idPost) throws NotExistingPost {
        if (!posts.containsKey(idPost))
            throw new NotExistingPost();
        return posts.get(idPost);
    }

    /**
     * Check if a Post 'p' is in 'user' 's feed
     * 
     * @param user
     * @param p
     * @return
     */
    private static boolean checkFeed(User user, Post p) {
        if (!user.following.contains(p.owner) && Collections.disjoint(user.following, p.rewiners) == true)
            return false;
        return true;
    }

    public class UserWrap implements Comparable<UserWrap> {
        final String username;
        final String[] tags /* , following, followers */;
        final HashSet<String> followers, following;

        private UserWrap(User u) {
            this.username = u.username;
            this.tags = u.tags.clone();
            // System.out.print("------|||| ");
            // for (String tag : this.tags) {
            // System.out.print(tag);
            // }
            // System.out.println("");
            // this.followers = (String[]) u.followers.toArray()/*.clone()*/;
            // this.following = (String[]) u.following.toArray()/*.clone()*/;
            this.followers = new HashSet<String>(u.followers);
            this.following = new HashSet<String>(u.following);
        }

        public int compareTo(UserWrap u) {
            return this.username.compareTo(u.username);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof UserWrap))
                return false;
            if (((UserWrap) o).username.equals(this.username))
                return true;
            return false;
        }

        @Override
        public int hashCode() {
            return this.username.hashCode();
        }

    }

    private class User {
        final String username;
        final String password;

        // ArrayList<Post> myownposts; // probab not needed
        HashSet<Integer> blog; // better to save post ID's or Post itself?
        // probab it is better to keep trace of the ID's, considering that a single post
        // may appear in more blogs. Secondarily,
        // I can put all posts (with their content)
        // in a single json file, without worrying to who belongs what

        // same thing for users
        HashSet<String> followers;
        HashSet<String> following;

        int wallet = 0;

        String[] tags; // tags can't be modified

        public User(String username, String password, String tags) throws ExistingUser {
            if (activeUsernames.contains(username))
                throw new ExistingUser(); // this should already have been checked

            this.username = new String(username);
            this.password = new String(password);
            tags.toLowerCase();
            this.tags = tags.split("\\s+");
            // I don't care to check that there aren't more than 5 tags here
            // It's not this constructor's responsibility to check it
            for (String tag : this.tags) {
                tagsUsers.putIfAbsent(tag, new HashSet<User>());
                tagsUsers.get(tag).add(this);
                // TODO is it okay to add to a set a not-yet-existing user? mmmmmmmh
            }

            this.followers = new HashSet<String>();
            this.following = new HashSet<String>();
            this.blog = new HashSet<Integer>();

            ServerInternal.followers.put(this.username, new HashSet<String>());
        }

        // The end user doesn't need to distinguish autor from curator rewards probab

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

    };

    public class PostWrap implements Comparable<PostWrap> {
        final String owner;
        final int idPost, upvote, downvote;
        final String title, content;
        HashMap<String, HashSet<String>> comments;
        final Timestamp date;

        public PostWrap(Post p) {
            this.owner = new String(p.owner);
            this.idPost = p.idPost;
            this.upvote = p.upvote.size();
            this.downvote = p.downvote.size();
            this.content = new String(p.content);
            // this.comments = new HashMap<>(p.comments); // TODO why doesnt this work :(
            this.comments = new HashMap<String, HashSet<String>>();
            p.comments.forEach((k, v) -> {
                System.out.println("key :" + k);
                this.comments.put(k, new HashSet<String>());
                // this.comments.get(k).addAll(v);
                v.forEach( (c) -> { 
                    System.out.println(c);
                    this.comments.get(k).add( c); });
            });
            this.date = (Timestamp) p.date.clone();
            this.title = new String(p.title);
        }

        public int compareTo(PostWrap p) {
            return p.idPost - this.idPost;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PostWrap))
                return false;
            if (((PostWrap) o).idPost == this.idPost)
                return true;
            return false;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(this.idPost);
        }

    }

    private class Post {

        final int idPost;
        final String owner;
        final String title;
        final String content;
        final Timestamp date;
        HashSet<String> upvote;
        HashSet<String> downvote;
        HashMap<String, HashSet<String>> comments;
        HashSet<String> rewiners;
        // useful to check if a post should appear in someone's feed

        // default constructor
        public Post(String title, String content, String owner) {
            this.idPost = idPostCounter++;
            this.owner = new String(owner);
            this.title = new String(title);
            this.content = new String(content);
            this.date = new Timestamp(System.currentTimeMillis());
            this.comments = new HashMap<>();
            this.rewiners = new HashSet<>();
            this.upvote = new HashSet<>();
            this.downvote = new HashSet<>();
            posts.put(this.idPost, this);

            // This isn't automatically added to owner.posts
        }

        // public int getId() {return idPost;};

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

        // TODO reward
    };
}