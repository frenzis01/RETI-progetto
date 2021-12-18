import java.nio.channels.*;
import java.net.InetSocketAddress;
import java.nio.*;
import java.rmi.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import exceptions.ExistingUser;
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
        users.put(username, new User(username, password, tags));
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
    public int login(String username, String password) {
        if (username == null || password == null)
            throw new NullPointerException();
        if (users.containsKey(username) && users.get(username).password.equals(password))
            return 0;
        return 1;
    };

    public void logout(String username) {
        if (username == null)
            throw new NullPointerException();
    };

    public HashSet<UserWrap> listUsers(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<UserWrap> toRet = new HashSet<>();
        for (String tag : user.tags) {
            tagsUsers.get(tag).forEach((u) -> {
                toRet.add(new UserWrap(u));
            });
        }
        return toRet; // TODO
    };

    public HashSet<UserWrap> listFollowers(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<UserWrap> toRet = new HashSet<>();
        for (String follower : user.followers)
            toRet.add(new UserWrap(users.get(follower)));
        return null; // TODO
    };

    public HashSet<UserWrap> listFollowing(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<UserWrap> toRet = new HashSet<>();
        for (String followed : user.following)
            toRet.add(new UserWrap(users.get(followed)));
        return toRet;
    };

    public int followUser(String toFollow, String username) throws NotExistingUser {
        User user = checkUsername(username);
        User followed = checkUsername(toFollow);
        user.following.add(toFollow);
        followed.followers.add(username);
        ServerInternal.followers.get(toFollow).add(username);
        return 0;
    };

    public int unfollowUser(String toUnfollow, String username) throws NotExistingUser {
        User user = checkUsername(username);
        User followed = checkUsername(toUnfollow);
        user.following.remove(toUnfollow);
        followed.followers.remove(username);
        ServerInternal.followers.get(toUnfollow).remove(username);
        // TODO should I report an error in case of "already not following"
        return 0;
    };

    public HashSet<PostWrap> viewBlog(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<PostWrap> toRet = new HashSet<>();
        for (Integer idPost : user.blog) {
            toRet.add(new PostWrap(ServerInternal.posts.get(idPost)));
        }
        return toRet;
    }; // displays the logged user's blog, no username param needed

    public HashSet<PostWrap> createPost(String titolo, String contenuto, String username) throws NotExistingUser {
        User user = checkUsername(username);

        return null; // TODO
    };

    public HashSet<PostWrap> showFeed(String username) throws NotExistingUser {
        User user = checkUsername(username);

        return null; // TODO
    };

    public int deletePost(int idPost, String username) throws NotExistingUser {
        User user = checkUsername(username);

        return 0; // TODO
    };

    public int rewinPost(int idPost, String username) throws NotExistingUser {
        User user = checkUsername(username);

        return 0; // TODO
    };

    public int ratePost(int idPost, int vote, String username) throws NotExistingUser {
        User user = checkUsername(username);

        return 0; // TODO
    };

    public int addComment(int idPost, String comment, String username) throws NotExistingUser {
        User user = checkUsername(username);

        return 0; // TODO
    };
    // TODO public Transaction[] getWallet (){};
    // TODO public Transaction[] getWalletInBitcoin (){};

    public class UserWrap {
        final String username;
        final String[] tags, following, followers;

        private UserWrap(User u) {
            this.username = u.username;
            this.tags = u.tags.clone();
            this.followers = (String[]) u.followers.toArray().clone();
            this.following = (String[]) u.following.toArray().clone();
        }
    }

    private static User checkUsername(String username) throws NotExistingUser {
        if (username == null)
            throw new NullPointerException();
        if (!users.containsKey(username))
            throw new NotExistingUser();
        return users.get(username);
    }

    private static class User {
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

    };

    public class PostWrap {
        final String owner;
        final int idPost, upvote, downvote;
        final String content;
        final HashMap<String, String> comments;

        public PostWrap(Post p) {
            this.owner = new String(p.owner);
            this.idPost = p.idPost;
            this.upvote = p.upvote.size();
            this.downvote = p.downvote.size();
            this.content = new String(p.content);
            this.comments = new HashMap<>(p.comments);
        }
    }

    private class Post {

        final int idPost;
        final String owner;
        final String content;
        HashSet<String> upvote;
        HashSet<String> downvote;
        HashMap<String, String> comments;

        // default constructor
        public Post(String owner, String content) {
            this.idPost = idPostCounter++;
            this.owner = new String(owner);
            this.content = new String(content);
            posts.put(this.idPost, this);

            // This isn't automatically added to owner.posts
        }

        // public int getId() {return idPost;};

        // TODO reward
    };
}