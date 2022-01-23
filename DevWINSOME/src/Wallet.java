// @Author Simone Passera

import java.sql.Timestamp;
import java.util.ArrayList;

// Portafoglio in wincoin contenente il valore e le varie transazioni
public class Wallet {
    // Valore del portafoglio in wincoin
    private double wincoin;
    // Lista delle transazioni, stringa -> "<incremento> <timestamp>"
    private final ArrayList<String> transactions;

    public Wallet() {
        wincoin = 0;
        transactions = new ArrayList<>();
    }

    // Restituisce il valore del portafoglio
    public synchronized double getWincoin() {
        return wincoin;
    }

    // Restituisce la lista delle transazioni
    public synchronized ArrayList<String> getTransactions() {
        return transactions;
    }

    // Accredita wincoin sul portafoglio
    public synchronized void addReward(double reward, Timestamp timestamp) {
        wincoin += reward;
        transactions.add("+" + reward + " " + timestamp);
    }
}
