// @Author Simone Passera

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

// Thread che riceve la notifica relativa calcolo delle ricompense tramite il gruppo di multicast
public class WalletUpdate implements Runnable {
    // Ip gruppo multicast
    private final InetAddress ip;
    // Porta gruppo multicast
    private final int port;
    // Flag calcolo ricompense avvenuto
    private final AtomicBoolean notify;
    // Socket su cui ricevere le notifiche
    private MulticastSocket ms;

    public WalletUpdate(InetAddress ip, int port, AtomicBoolean notify) {
        this.ip = ip;
        this.port = port;
        this.notify = notify;
    }

    @Override
    public void run() {
        try {
            // Inizializzo il socket e mi unisco al gruppo di multicast
            ms = new MulticastSocket(port);
            ms.joinGroup(ip);
            // Inizializzo il pacchetto di risposta
            byte[] buf = new byte[64];
            DatagramPacket rewardMessage = new DatagramPacket(buf, buf.length);
            // Imposto il flag a true dopo aver ricevuto una risposta di avvenuto calcolo delle ricompense
            while (true) {
                ms.receive(rewardMessage);
                String message = new String(rewardMessage.getData(), 0, rewardMessage.getLength(), StandardCharsets.US_ASCII);
                if (message.equals("WINSOME UPDATE WALLETS")) notify.set(true);
            }
        } catch (IOException InvokeExit) {}
    }

    // Termina il thread
    public void exit() {
        try {
            ms.leaveGroup(ip);
            ms.close();
        } catch (IOException ignore) {}
    }
}
