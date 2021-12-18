import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Util {
    public static boolean debugFlag = true; // useful during development

    /**
     * Reads from socket the length (Int) of a message and then the message itself 
     * @param s socket channel from which we are reading
     * @return the read message
     * @throws IOException
     */
    public static String readMsgFromSocket(SocketChannel s) throws IOException {

        ByteBuffer msg[] = new ByteBuffer[2];
        msg[0] = ByteBuffer.allocate(Integer.BYTES);
        printd("Read : " + s.read(msg[0]));

        msg[0].flip();
        int msgLen = msg[0].getInt();
        printd("msg : " + msgLen);
        msg[1] = ByteBuffer.allocate(msgLen);
        printd("Read : " + s.read(msg[1]));

        msg[1].flip();
        String reply = new String(msg[1].array()).trim();
        printd("Server sent: " + reply);
        msg[1].clear();
        
        return reply;
    }

    /**
     * Prints the given string only if the debugFlag is enabled
     * @param s
     */
    public static void printd (String s) {
        if (debugFlag)
            System.out.println(s);
    }
}
