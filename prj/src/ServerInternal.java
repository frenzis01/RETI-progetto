import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

import exceptions.ExistingUser;
import exceptions.NotExistingPost;
import exceptions.NotExistingUser;

public class ServerInternal {

    private static volatile int idPostCounter = 0; // will write this in a json file
    private static volatile int rewardPerformedIterations = 0; // will write this in a json file

    private static ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, HashSet<String>> tagsUsers = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, Post> posts = new ConcurrentHashMap<>();

    // these are consumed by the reward algorithm
    private static ConcurrentHashMap<Integer, HashSet<String>> newUpvotes = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, HashSet<String>> newDownvotes = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, ArrayList<String>> newComments = new ConcurrentHashMap<>();
    // we don't care to save here the comments's content, we only care about the
    // author

    private static double authorPercentage = 0.7;

    // redundant who-is-following-who to avoid recalculating on every update
    // we need it to notify the logged users
    private static ConcurrentHashMap<String, HashSet<String>> followers = new ConcurrentHashMap<>();

    // init to default values
    private static File usersBackup = new File("../bkp/users.json");
    private static File postsBackup = new File("../bkp/posts.json");
    private static File followersBackup = new File("../bkp/followers.json");
    private static File tagsUsersBackup = new File("../bkp/tagsUsers.json");
    private static File countersBackup = new File("../bkp/counters.json");

    public ServerInternal() {
        super();
    }

    // Methods used in RMI interface implementation

    public static Boolean usernameUnavailable(String username) {
        return users.containsKey(username);
    }

    /**
     * adds user to winsome
     * 
     * @param username
     * @param password
     * @param tags
     * @throws ExistingUser
     */
    public static void addUser(String username, String password, String tags) throws ExistingUser {
        users.put(username, new User(username, password, tags));
        ServerInternal.followers.put(username, new HashSet<String>());
    }

    /**
     * 
     * @param username
     * @return username's followers (might be empty)
     */
    public static HashSet<UserWrap> getFollowers(String username) {
        if (!users.containsKey(username))
            return new HashSet<UserWrap>();
        return users.get(username).followers.stream()
        .filter( u -> users.containsKey(u))
        .map(u -> new ServerInternal().new UserWrap(users.get(u)))
        .collect(Collectors.toCollection(HashSet::new));
    }

    // Methods needed by User constructor

    /**
     * Add user to a tag set
     * 
     * @param username
     * @param tag
     */
    public static void add2tag(String username, String tag) {
        ServerInternal.tagsUsers.putIfAbsent(tag, new HashSet<String>());
        ServerInternal.tagsUsers.get(tag).add(username);
    }

    public static int getIdPostCounter() {
        return idPostCounter++;
    }

    /**
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
    }

    ;

    /**
     * Does this have any sense at all ?
     * //TODO
     *
     * @param username
     */
    public static void logout(String username) {
        if (username == null)
            throw new NullPointerException();

    }

    ;

    /**
     * @param username
     * @return the set of users who have at least on tag in common with the
     *         requestor
     * @throws NotExistingUser
     */
    public static HashSet<UserWrap> listUsers(String username) throws NotExistingUser {
        User user = checkUsername(username);
        HashSet<UserWrap> toRet = new HashSet<>();
        for (String tag : user.tags) {
            tagsUsers.get(tag)
                    .forEach((u) -> {
                        System.out.println(u);
                        if (!u.equals(username)) {
                            toRet.add(new ServerInternal().new UserWrap(users.get(u)));
                        }
                    });
        }
        return toRet;
    }

    ;

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
    }

    ;

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
    }

    ;

    /**
     * Removes a user from the requestor set of followed users
     *
     * @param toFollow
     * @param username
     * @return 0 successfully followed, 1 was following already
     * @throws NotExistingUser
     */
    public static int followUser(String toFollow, String username) throws NotExistingUser {
        User user = checkUsername(username);
        User followed = checkUsername(toFollow);
        user.following.add(toFollow);
        followed.followers.add(username);
        if (ServerInternal.followers.get(toFollow).add(username) == true)
            return 0;
        return 1;
    }

    ;

    /**
     * Removes a user from the requestor set of followed users
     *
     * @param toUnfollow
     * @param username
     * @return 0 successfully unfollowed, 1 wasn't following already
     * @throws NotExistingUser
     */
    public static int unfollowUser(String toUnfollow, String username) throws NotExistingUser {
        User user = checkUsername(username);
        User followed = checkUsername(toUnfollow);
        user.following.remove(toUnfollow);
        followed.followers.remove(username);
        if (ServerInternal.followers.get(toUnfollow).remove(username) == true)
            return 0;
        return 1;
    }

    ;

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
    }

    ; // displays the logged user's blog, no username param needed

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
        Post newPost = new Post(titolo, contenuto, username);
        posts.put(newPost.idPost, newPost);
        user.blog.add(newPost.idPost);
        return new ServerInternal().new PostWrap(newPost);
    }

    ;

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
    }

    ;

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
            // We have to remove the post from owner's and rewiners's blog
            user.blog.remove(idPost);
            p.rewiners.forEach((String name) -> {
                users.get(name).blog.remove(idPost);
            });
            return 0;
        }
        return 1; // the client isn't the owner
    }

    ;

    /**
     * rewin a Post made by another user. A client cannot rewin its own posts
     *
     * @param idPost
     * @param username
     * @return 0 success, 1 user is the post owner, 2 the post isn't in the user's
     *         feed
     * @throws NotExistingUser
     * @throws NotExistingPost
     */
    public static int rewinPost(int idPost, String username) throws NotExistingUser, NotExistingPost {
        User user = checkUsername(username);
        Post p = checkPost(idPost);
        if (username.equals(p.owner))
            return 1;
        if (!checkFeed(user, p))
            return 2;
        user.blog.add(p.idPost);
        p.rewiners.add(username);
        return 0;
    }

    ;

    /**
     * @param idPost
     * @param username
     * @return
     * @throws NotExistingUser
     * @throws NotExistingPost
     */
    public static PostWrap showPost(int idPost, String username) throws NotExistingUser, NotExistingPost {
        checkUsername(username);
        Post p = checkPost(idPost);
        return new ServerInternal().new PostWrap(p);
    }

    /**
     * @param idPost
     * @param vote
     * @param username
     * @return 0 success, 1 given post isn't in the user's feed, 2 user has already
     *         voted
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
        if (vote >= 0) {
            p.upvote.add(username);
            ServerInternal.newUpvotes.putIfAbsent(idPost, new HashSet<String>());
            ServerInternal.newUpvotes.get(idPost).add(username);
        } else {
            p.downvote.add(username);
            ServerInternal.newDownvotes.putIfAbsent(idPost, new HashSet<String>());
            ServerInternal.newDownvotes.get(idPost).add(username);
        }
        return 0;
    }

    ;

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
        p.comments.putIfAbsent(username, new HashSet<>());
        p.comments.get(username).add(comment);

        // add comment to newComments
        ServerInternal.newComments.putIfAbsent(idPost, new ArrayList<String>());
        ServerInternal.newComments.get(idPost).add(username);
        return 0;
    }

    public static double getWallet(String username) {
        User u = users.get(username);
        return u != null ? u.wallet : 0.0;
    };

    public static double getWalletInBitcoin(String username) {
        double toRet = getWallet(username);
        if (toRet != 0.0) {

            try {
                URL url = new URL("https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));

                    String inputLine = "";
                    StringBuffer res = new StringBuffer();

                    while ((inputLine = reader.readLine()) != null) {
                        res.append(inputLine);
                    }
                    reader.close();

                    toRet *= Double.parseDouble(res.toString());
                }
            } catch (Exception e) {
                toRet = -1.0;
                System.out.println("|ERROR connecting to random.org");
                e.printStackTrace();
            }
        }
        return toRet;
    };

    // private utilities

    /**
     * Check's whether the given username is associated with a winsome user
     * 
     * @param username
     * @return
     * @throws NotExistingUser
     */
    private static User checkUsername(String username) throws NotExistingUser {
        if (username == null)
            throw new NullPointerException();
        if (!users.containsKey(username))
            throw new NotExistingUser();
        return users.get(username);
    }

    /**
     * check's if post exists in winsome
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

        @Override
        public String toString() {
            return this.username + " \t|\t " + Arrays.toString(this.tags);
        }

    }

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
            this.comments = new HashMap<>(p.comments);
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

    public static String userWrapSet2String(HashSet<ServerInternal.UserWrap> users) {
        String toRet = "User \t|\t Tag\n";
        for (ServerInternal.UserWrap u : users) {
            toRet = toRet + u.username + " \t|\t " + Arrays.toString(u.tags) + "\n";
        }
        return toRet;
    }

    public static String postWrapSet2String(HashSet<ServerInternal.PostWrap> posts) {
        String toRet = "Id \t|\t Author \t|\t Title\n";
        for (ServerInternal.PostWrap p : posts) {
            // toRet = toRet + p.idPost + "\t|\t" + p.owner + "\t|\t" + p.title + "\n";
            toRet += p.toString();
        }
        return toRet;
    }

    public static String postWrap2String(ServerInternal.PostWrap p) {
        var wrapper = new Object() {
            String toRet = "Title: " + p.title + "\nContent: " + p.content + "\nVotes: " + p.upvote +
                    "+ | " + p.downvote + "-\nComments:\n";
        };
        p.comments.forEach((u, comms) -> {
            wrapper.toRet += "\t" + u + ":\n";
            comms.forEach((c) -> {
                wrapper.toRet += "\t " + c + "\n";
            });
        });
        return wrapper.toRet;
    }

    /**
     * Algorithm to calculate winsome's rewards based on post interactions.
     * Iterates on every modified post since the last time the algorithm ran,
     * avoiding unmodified posts;
     * to achieve this, it "consumes" three maps ( modifiedPostID ->
     * {usersWhoInteracted}).
     * 
     * To avoid iterating also on unmodified posts to increment their "age", we save
     * the number of times the reward algorithm has ran in the post
     * at its creation, so that post.age := {current reward algorithm iterations} -
     * {reward algorithm iterations at post's creation}
     */
    public static void rewardAlgorithm() {
        rewardPerformedIterations++;
        // get all the modified posts since the last time the algorithm got executed
        // we will empty the three Collections once we're done evaluating
        HashSet<Integer> modifiedPosts = new HashSet<>();
        modifiedPosts.addAll(newUpvotes.keySet());
        modifiedPosts.addAll(newDownvotes.keySet());
        modifiedPosts.addAll(newComments.keySet());

        modifiedPosts.forEach((id) -> {
            // check if the post still exists
            if (posts.containsKey(id)) {
                // these will come in handy later
                boolean anyUpvotes = newUpvotes.containsKey(id);
                boolean anyDownvotes = newDownvotes.containsKey(id);
                boolean anyComments = newComments.containsKey(id);

                // get the number of upvotes and downvotes
                int upvotes = anyUpvotes ? newUpvotes.get(id).size() : 0;
                int downvotes = anyDownvotes ? newDownvotes.get(id).size() : 0;

                // count duplicates and get the number of comments for each "commenting" user
                HashMap<String, Integer> nCommentsForEachUser = (HashMap<String, Integer>) newComments.get(id).stream()
                        .collect(Collectors.toMap(Function.identity(), v -> 1, Integer::sum));
                // now apply the formula to each user and calculate the sum
                var wrapper = new Object() {
                    Double sum = 0.0;
                };
                nCommentsForEachUser.forEach((user, cp) -> {
                    wrapper.sum += 2 / (1 + Math.pow(Math.E, -(cp - 1)));
                });

                Post post = posts.get(id); // we've already checked that posts.containsKey(id) == true
                // post.rewardAlgorithmIterations++; // it is initialized to 0, so we increment
                // before calculating
                double reward = (Math.log(Math.max(upvotes - downvotes, 0) + 1) + Math.log(wrapper.sum + 1))
                        / (rewardPerformedIterations - post.rewardIterationsOnCreation);
                post.reward += reward; // TODO is this somehow useful ...? probab not

                // AUTHOR REWARD
                if (users.containsKey(post.owner))
                    users.get(post.owner).wallet += reward * authorPercentage;

                // CURATOR REWARD
                HashSet<String> empty = new HashSet<String>(); // .flatMap handles null stream, but .of doesn't
                // get all the users who interacted with the post
                HashSet<String> curators = (HashSet<String>) Stream
                        .of(anyUpvotes ? newUpvotes.get(id) : empty, anyDownvotes ? newDownvotes.get(id) : empty,
                                anyComments ? new HashSet<String>(newComments.get(id)) : empty)
                        .flatMap(u -> u.stream())
                        .collect(Collectors.toSet());

                curators.forEach((username) -> {
                    if (users.containsKey(username)) // we ain't sure whether the user still exists or not
                        users.get(username).wallet += reward / curators.size() * (1 - authorPercentage);
                    // we must use curators.size to avoid counting duplicates
                });

            }
            modifiedPosts.remove(id); // delete the entry once evaluated
            newUpvotes.remove(id);
            newDownvotes.remove(id);
            newComments.remove(id);
        });
    }

    public static int getRewardIterations() {
        return ServerInternal.rewardPerformedIterations;
    }

    // JSON BACKUP

    /**
     * Serializes the content of users,posts and other useful fields and stores it
     * in the .json backup files
     */
    public static void write2json() {
        createBackupFiles();

        JsonFactory jsonFactory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(jsonFactory);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(usersBackup, users);
            mapper.writeValue(postsBackup, posts);
            mapper.writeValue(followersBackup, followers);
            mapper.writeValue(tagsUsersBackup, tagsUsers);
            int[] counters = new int[]{ idPostCounter, rewardPerformedIterations };
            mapper.writeValue(countersBackup, counters);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * Checks for each .json backup file if already exists, if not it creates it
     */
    private static void createBackupFiles() {
        File[] bkpFiles = { usersBackup, postsBackup, tagsUsersBackup, followersBackup, countersBackup };
        Arrays.asList(bkpFiles).forEach((bkp) -> {
            try {
                if (!bkp.exists())
                    bkp.createNewFile();
            } catch (IOException e) {
                System.out.println("|ERROR: creating backup files");
            }
        });
    }

    /**
     * Updates the location of the backup files
     * 
     * @param backupDir
     */
    public static void updateBackupDir(String backupDir) {
        usersBackup = new File(backupDir + "/users.json");
        postsBackup = new File(backupDir + "/posts.json");
        followersBackup = new File(backupDir + "/followers.json");
        tagsUsersBackup = new File(backupDir + "/tagsUsers.json");
        countersBackup = new File(backupDir + "/counters.json");
    }

    /**
     * Restores winsome's status deserializing .json backup files if the do exist
     */
    public static void restoreBackup() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            if (usersBackup.exists()) {
                BufferedReader usersReader = new BufferedReader(new FileReader(usersBackup));
                users = mapper.readValue(usersReader, new TypeReference<ConcurrentHashMap<String, User>>() {
                });
                System.out.println("backup utenti effettuato");
            }
            if (postsBackup.exists()) {
                BufferedReader postsReader = new BufferedReader(new FileReader(postsBackup));
                posts = mapper.readValue(postsReader, new TypeReference<ConcurrentHashMap<Integer, Post>>() {
                });
                System.out.println("backup post effettuato");
            }
            if (followersBackup.exists()) {
                BufferedReader followersReader = new BufferedReader(new FileReader(followersBackup));
                followers = mapper.readValue(followersReader,
                        new TypeReference<ConcurrentHashMap<String, HashSet<String>>>() {
                        });
                System.out.println("backup followers effettuato");
            }
            if (tagsUsersBackup.exists())

            {
                BufferedReader tagsUsersReader = new BufferedReader(new FileReader(tagsUsersBackup));
                tagsUsers = mapper.readValue(tagsUsersReader,
                        new TypeReference<ConcurrentHashMap<String, HashSet<String>>>() {
                        });
                System.out.println("backup tagsUsers effettuato");
            }
            if (countersBackup.exists()) {
                int[] counters = new int[2];
                BufferedReader countersReader = new BufferedReader(new FileReader(countersBackup));
                counters = mapper.readValue(countersReader, new TypeReference<int[]>() {
                });
                ServerInternal.idPostCounter = counters[0];
                ServerInternal.rewardPerformedIterations = counters[1];
                printCounters();

                System.out.println("backup counters effettuato");
            }

            System.out.println();

        } catch (IOException e) {
            System.out.println("|ERROR: restoreBackup");
            e.printStackTrace();
        }
    }

    private static void printCounters() {
        System.out.println("Counters -> " + idPostCounter + " | " + rewardPerformedIterations);
    }
}