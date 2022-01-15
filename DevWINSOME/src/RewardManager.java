import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class RewardManager implements Runnable {
    private final long timer;
    private final InetAddress multicastAddress;
    private final int multicastPort;

    public RewardManager(InetAddress multicastAddress, int multicastPort, int timer) {
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.timer = timer;
    }

    @Override
    public void run() {
        try (DatagramSocket notifySocket = new DatagramSocket()) {
            String message = "WINSOME UPDATE WALLETS";
            byte[] buf = message.getBytes(StandardCharsets.US_ASCII);
            DatagramPacket rewardMessage = new DatagramPacket(buf, buf.length, multicastAddress, multicastPort);

            boolean exit = false;

            while (!exit && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(timer * 1000);
                }catch (InterruptedException e) {
                    exit = true;
                }

                if (!exit) {
                    notifySocket.send(rewardMessage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
