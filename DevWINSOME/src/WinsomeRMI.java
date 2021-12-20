// @Author Simone Passera

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class WinsomeRMI extends UnicastRemoteObject implements WinsomeRMIServices {
    public WinsomeRMI() throws RemoteException {
        super();
    }

    public int register(String username, String password, List<String> tags) throws RemoteException {
        if (username == null || password == null || tags == null) throw new NullPointerException();

        // Username vuoto
        if (username.isEmpty()) return 1;
        // Password vuota
        if (password.isEmpty()) return 3;
        // Lista di tag vuota
        if (tags.isEmpty()) return 4;
        // lista di tag troppo grande [max 5]
        if (tags.size() > 5) return 5;

        for (String tag : tags) {
            // lista di tag contiene valori vuoti
            if (tag.isEmpty()) return 6;
        }

        // Inserisco username e password se l'utente non esiste
        if (ServerMain.users.putIfAbsent(username, password) == null) {
            ArrayList<String> tagsList = new ArrayList<>();
            // Aggiungo i tag in minuscolo
            for (String tag : tags) {
                tagsList.add(tag.toLowerCase());
            }

            ServerMain.tags.put(username, tagsList);

            // Ok
            return 0;
        }

        // L'utente esiste già
        return 2;
    }
}
