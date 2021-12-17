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

public class ServerMain {

    private static int idPostCounter = 0; // i will write this in a json file
    private static HashSet<String> activeUsernames = new HashSet<>();
    private static HashMap<String, User> users = new HashMap<>();
    private static HashMap<String, HashSet<User>> tagsUsers = new HashMap<>();
    private static HashMap<Integer, Post> posts = new HashMap<>();

    // redundant who-is-following-who to avoid recalculating on every update
    // we need it to notify the logged users
    private static HashMap<String, HashSet<String>> followers = new HashMap<>();

    public ServerMain() {
        super();
    }

    public static void main(String args[]) {
        try {

            // RMI setup
            ROSimp serverRMI = new ROSimp();
            ROSint stub = (ROSint) UnicastRemoteObject.exportObject(serverRMI, 39000);
            LocateRegistry.createRegistry(1900);
            LocateRegistry.getRegistry(1900).rebind("rmi://127.0.0.1:1900", stub);
            // Now ready to handle RMI registration and followers's update notifications

            // fakefollower
            // Thread.sleep(3000);
            // System.out.println("3sec elapsed " + users.containsKey("username"));
            // users.get("username").followers.add(new String("fakeusr"));
            // serverRMI.update("username");

            // TCP server setup
            Server server = new Server (12345);
            server.start();


            // while(true) {
            // Thread.sleep(800);
            // server.update("");
            // }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createStubUser () throws ExistingUser {
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

    public UserWrap[] listUsers() {

        return null; // TODO
    };

    public UserWrap[] listFollowers() {
        return null; // TODO
    };

    public UserWrap[] listFollowing() {
        return null; // TODO
    };

    public int followUser(String username) {

        return 0;
    };

    public Post[] viewBlog() {
        return null; // TODO
    }; // displays the logged user's blog, no username param needed

    public Post[] createPost(String titolo, String contenuto) {
        return null; // TODO
    };

    public Post[] showFeed() {
        return null; // TODO
    };

    public int deletePost(int idPost) {
        return 0; // TODO
    };

    public int rewinPost(int idPost) {
        return 0; // TODO
    };

    public int ratePost(int idPost, int vote) {
        return 0; // TODO
    };

    public int addComment(int idPost, String comment) {
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

    private static class User {
        final String username;
        final String password;

        // ArrayList<Post> myownposts; // probab not needed
        ArrayList<Integer> blog; // better to save post ID's or Post itself?
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

            ServerMain.followers.put(this.username, new HashSet<String>());
        }

        // The end user doesn't need to distinguish autor from curator rewards probab

    };

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
        }

        // public int getId() {return idPost;};

        // TODO reward
    };
}