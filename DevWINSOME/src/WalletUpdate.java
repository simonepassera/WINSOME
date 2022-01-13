import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class WalletUpdate implements Runnable {
    private InetAddress ip;
    private int port;
    private AtomicBoolean notify;
    private MulticastSocket ms;

    public WalletUpdate(InetAddress ip, int port, AtomicBoolean notify) {
        this.ip = ip;
        this.port = port;
        this.notify = notify;
    }

    @Override
    public void run() {
        try {
            ms = new MulticastSocket(port);
            ms.joinGroup(ip);

            byte[] buf = new byte[64];
            DatagramPacket rewardMessage = new DatagramPacket(buf, buf.length);

            while (true) {
                ms.receive(rewardMessage);
                String message = new String(rewardMessage.getData(), 0, rewardMessage.getLength(), StandardCharsets.US_ASCII);
                if (message.equals("WINSOME UPDATE WALLETS")) notify.set(true);
            }
        } catch (IOException InvokeExit) {}
    }

    public void exit() {
        try {
            ms.leaveGroup(ip);
            ms.close();
        } catch (IOException ignore) {}
    }
}
