import java.net.Socket;

public class UserManager implements Runnable {
    private Socket user;

    public UserManager(Socket user) {
        this.user = user;
    }

    @Override
    public void run() {

    }
}
