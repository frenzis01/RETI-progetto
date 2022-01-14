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
            Server server = new Server("../serverConfig.json");
            server.start();

        } catch (RemoteException e) {
            System.out.println("|ERROR starting server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
        ServerInternal.createPost("p1", "Lorem ipsum", "u1");
        ServerInternal.PostWrap p = ServerInternal.createPost("p2", "dolor sit amet", "u1");

        // Posts interaction
        ServerInternal.addComment(p.idPost, "consectetur adipisci", "u2");
        ServerInternal.addComment(p.idPost, "consectetur adipiscy", "u2");
        ServerInternal.addComment(p.idPost, "consectetur adipisce", "u4");
        ServerInternal.rewinPost(p.idPost, "u3");

        ServerInternal.rewardAlgorithm();
        // ServerInternal.rewardAlgorithm(); // this won't do anything, the modified post list has been confused
        // ServerInternal.printWallets();

        ServerInternal.write2json();

    }

}