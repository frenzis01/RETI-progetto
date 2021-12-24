package exceptions;

public class NotExistingPost extends Exception {
    public NotExistingPost () {
        super("No user registered with the given username\n");
    }
}
