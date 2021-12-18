package exceptions;

public class NotExistingUser extends Exception {
    public NotExistingUser () {
        super("No user registered with the given username\n");
    }
}
