import java.rmi.RemoteException;

import exceptions.ExistingUser;
import exceptions.NotExistingPost;
import exceptions.NotExistingUser;

public class ServerMain {

    public ServerMain() {
        super();
    }

    public static void main(String args[]) {
        try {

            // createStubs();
            // TCP server setup
            Server server = new Server(args.length > 0 ? args[0] : "../config/serverConfig.json");
            server.start();

        } catch (RemoteException e) {
            System.out.println("|ERROR starting server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method used for testing purposes
    private static void createStubs() throws NotExistingPost, NotExistingUser, ExistingUser {
        // Add some users
        ServerInternal.addUser("u1", "1", "tag1 tag2");
        ServerInternal.addUser("u2", "2", "tag1 tag2");
        ServerInternal.addUser("u3", "3", "tag3");
        ServerInternal.addUser("u4", "4", "tag4");
        ServerInternal.addUser("u5", "5", "tag5 tag6");

        // Add some relationships between users
        ServerInternal.followUser("u2", "u1");
        ServerInternal.followUser("u1", "u2");
        ServerInternal.followUser("u3", "u1");
        ServerInternal.followUser("u1", "u4");
        ServerInternal.followUser("u1", "u5");

        // Add posts
        ServerInternal.PostWrap p0 =ServerInternal.createPost("p1", "Lorem ipsum", "u1");
        ServerInternal.PostWrap p1 = ServerInternal.createPost("p2", "dolor sit amet", "u1");

        // Posts interaction
        ServerInternal.addComment(p1.idPost, "consectetur adipisci elit", "u2");
        ServerInternal.addComment(p1.idPost, "sed eiusmod tempor", "u2");
        ServerInternal.addComment(p0.idPost, "incidunt ut labore", "u4");
        ServerInternal.rewinPost(p1.idPost, "u3");

        ServerInternal.rewardAlgorithm();

        ServerInternal.write2json();

    }

}