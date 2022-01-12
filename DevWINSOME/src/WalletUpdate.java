import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class WalletUpdate implements Runnable {
    InetAddress ip;
    int port;
    AtomicBoolean notify;

    public WalletUpdate(InetAddress ip, int port, AtomicBoolean notify) {
        this.ip = ip;
        this.port = port;
        this.notify = notify;
    }

    @Override
    public void run() {
        try (MulticastSocket ms = new MulticastSocket(port)){
            ms.joinGroup(ip);

            byte[] buf = new byte[128];
            DatagramPacket rewardMessage = new DatagramPacket(buf, buf.length);

            while (true) {
                ms.receive(rewardMessage);
                String message = new String(rewardMessage.getData(), StandardCharsets.US_ASCII);
                if (message.equals("WINSOME UPDATE WALLETS")) notify.set(true);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
