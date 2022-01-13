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

    public double getWincoin() {
        return wincoin;
    }

    public ArrayList<String> getTransactions() {
        return transactions;
    }
}
