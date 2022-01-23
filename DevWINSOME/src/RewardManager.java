// @Author Simone Passera

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Thread che calcola le ricompense
public class RewardManager implements Runnable {
    // Intervallo di tempo regolare che determina quando eseguire il calcolo delle ricompense
    private final long timer;
    // Ip gruppo multicast
    private final InetAddress multicastAddress;
    // Porta gruppo multicast
    private final int multicastPort;
    // Lista delle interazioni dall'ultimo calcolo delle ricompense
    private final ListInteractions listInteractions;
    // Mappa (username, wallet)
    private final ConcurrentHashMap<String, Wallet> wallets;
    // Percentuale della ricompensa totale per un post destinata all'autore
    private final int reward_author;

    public RewardManager(InetAddress multicastAddress, int multicastPort, int timer, ListInteractions listInteractions, ConcurrentHashMap<String, Wallet> wallets, int reward_author) {
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.timer = timer;
        this.listInteractions = listInteractions;
        this.wallets = wallets;
        this.reward_author = reward_author;
    }

    @Override
    public void run() {
        try (DatagramSocket notifySocket = new DatagramSocket()) {
            // Inizializzo il pacchetto di notifica
            String message = "WINSOME UPDATE WALLETS";
            byte[] buf = message.getBytes(StandardCharsets.US_ASCII);
            DatagramPacket rewardMessage = new DatagramPacket(buf, buf.length, multicastAddress, multicastPort);

            boolean exit = false;

            while (!exit) {
                // Controllo se il thread è stato interrotto
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Sospendo il thread per "timer" secondi
                        Thread.sleep(timer * 1000);
                    } catch (InterruptedException e) {
                        // Thread interrotto
                        exit = true;
                    }
                } else {
                    // Thread interrotto
                    exit = true;
                }

                synchronized (listInteractions) {
                    // Mappa (id, interazione)
                    HashMap<Integer, Interaction> interactions = listInteractions.getInteractions();
                    // Calcolo la ricompensa per ogni post
                    for (Map.Entry<Integer, Interaction> postInteractions : interactions.entrySet()) {
                        Interaction interaction = postInteractions.getValue();
                        // Controllo se il post ha avuto almeno un interazione
                        if (interaction.isFlagInteraction()) {
                            HashSet<String> upVote = interaction.getUpVote();
                            int downVote = interaction.getDownVote();
                            HashMap<String, Integer> numComments = interaction.getNumComments();

                            double sumNewComments = 0;
                            // Calcolo la somma relativa ai commenti
                            for (Integer num : numComments.values()) {
                                sumNewComments += 2 / (1 + Math.exp(-(num - 1)));
                            }
                            // Calcolo la ricompensa per il post
                            double reward = (Math.log(Math.max(upVote.size() - downVote, 0) + 1) + Math.log(sumNewComments + 1)) / interaction.getIteration();
                            // Arrotondo ad una cifra decimale
                            reward = Math.round(reward * 10) / 10.0;
                            // Ricompensa destinata all'autore
                            double rewardAuthor = (reward / 100) * reward_author;

                            // Utilizzo il set upVote per avere un insieme contenente
                            // una persona che ha effettuato un upVote e/o un commento
                            upVote.addAll(numComments.keySet());
                            int numCurators = upVote.size();
                            // Ricompensa destinata per ogni curatore
                            double rewardCurator = (reward - rewardAuthor) / numCurators;

                            // Timestamp attuale
                            Timestamp now = new Timestamp(System.currentTimeMillis());

                            Wallet wallet;

                            // Accredito la ricompensa all'autore
                            if (rewardAuthor != 0) {
                                wallet = wallets.get(interaction.getAuthor());
                                wallet.addReward(rewardAuthor, now);
                            }

                            // Accredito la ricompensa ai curatori
                            if (rewardCurator != 0) {
                                for (String username : upVote) {
                                    wallet = wallets.get(username);
                                    wallet.addReward(rewardCurator, now);
                                }
                            }
                            // Svuoto la lista delle interazioni per questo post
                            interaction.reset();
                        }
                        // Incremento il numero d'iterazioni per questo post
                        interaction.incrementIteration();
                    }
                }
                // Invio la notifica di avvenuto calcolo delle ricompense
                notifySocket.send(rewardMessage);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
