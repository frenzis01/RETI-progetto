import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

import exceptions.ExistingUser;
import exceptions.NotExistingPost;
import exceptions.NotExistingUser;

public class ServerMain {

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


            createStubs();

            // TCP server setup
            Server server = new Server(12345);
            server.start();

            // while(true) {
            // Thread.sleep(800);
            // server.update("");
            // }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createStubs() throws NotExistingPost,NotExistingUser,ExistingUser {
        // Add some users
        ServerInternal.addUser("u1", "", "tag1 tag2");
        ServerInternal.addUser("u2", "", "tag1 tag2");
        ServerInternal.addUser("u3", "", "tag3");
        ServerInternal.addUser("u4", "", "tag4");
        ServerInternal.addUser("u5", "", "tag5 tag6");
        
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
        ServerInternal.addComment(p.idPost, "consectetur adipisce", "u2");
        ServerInternal.rewinPost(p.idPost, "u3");

    }

}