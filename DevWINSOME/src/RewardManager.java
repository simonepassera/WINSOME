import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RewardManager implements Runnable {
    private final long timer;
    private final InetAddress multicastAddress;
    private final int multicastPort;
    private final ListInteractions listInteractions;
    private final ConcurrentHashMap<Integer, Post> posts;
    private final ConcurrentHashMap<String, Wallet> wallets;
    private final int reward_author;

    public RewardManager(InetAddress multicastAddress, int multicastPort, int timer, ListInteractions listInteractions, ConcurrentHashMap<Integer, Post> posts, ConcurrentHashMap<String, Wallet> wallets, int reward_author) {
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.timer = timer;
        this.listInteractions = listInteractions;
        this.posts = posts;
        this.wallets = wallets;
        this.reward_author = reward_author;
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
                    synchronized (listInteractions) {
                        HashMap<Integer, Interaction> interactions = listInteractions.getInteractions();

                        for (Map.Entry<Integer, Interaction> postInteractions : interactions.entrySet()) {
                            Interaction interaction = postInteractions.getValue();

                            if (interaction.isFlagInteraction()) {
                                HashSet<String> upVote = interaction.getUpVote();
                                int downVote = interaction.getDownVote();
                                HashMap<String, Integer> numComments = interaction.getNumComments();

                                double sumNewComments = 0;

                                for (Integer num : numComments.values()) {
                                    sumNewComments += 2/(1 + Math.exp(-(num - 1)));
                                }

                                double reward = (Math.log(Math.max(upVote.size() - downVote, 0) + 1) + Math.log(sumNewComments + 1))/interaction.getIteration();
                                reward = Math.round(reward * 10) / 10.0;

                                String author = posts.get(postInteractions.getKey()).getAuthor();

                                double rewardAuthor = (reward / 100) * reward_author;

                                // Utilizzo il set upVote per avere un insieme contenente
                                // una persona che ha effettuato un upVote e/o un commento
                                upVote.addAll(numComments.keySet());
                                int numCurators = upVote.size();
                                double rewardCurator = (reward - rewardAuthor)/numCurators;

                                // Timestamp
                                Timestamp now = new Timestamp(System.currentTimeMillis());

                                Wallet wallet;

                                // Ricompensa autore
                                if (rewardAuthor != 0) {
                                     wallet = wallets.get(author);
                                    wallet.addReward(rewardAuthor, now);
                                }

                                // Ricompensa curatori
                                if (rewardCurator != 0) {
                                    for (String username : upVote) {
                                        wallet = wallets.get(username);
                                        wallet.addReward(rewardCurator, now);
                                    }
                                }

                                interaction.reset();
                            }

                            interaction.incrementIteration();
                        }
                    }

                    notifySocket.send(rewardMessage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
