package exceptions;

public class ExistingUser extends Exception {
    public ExistingUser () {
        super("Username already taken\n");
    }
}
