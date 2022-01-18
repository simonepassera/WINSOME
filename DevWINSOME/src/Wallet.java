import java.sql.Timestamp;
import java.util.ArrayList;

public class Wallet {
    // Valore del portafoglio in wincoin
    private double wincoin;
    // Lista delle transazioni [<incremento> <timestamp>]
    private final ArrayList<String> transactions;

    public Wallet() {
        wincoin = 0;
        transactions = new ArrayList<>();
    }

    public synchronized double getWincoin() {
        return wincoin;
    }

    public synchronized ArrayList<String> getTransactions() {
        return transactions;
    }

    public synchronized void addReward(double reward, Timestamp timestamp) {
        wincoin += reward;
        transactions.add("+" + reward + " " + timestamp);
    }
}
