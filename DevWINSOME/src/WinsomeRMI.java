// @Author Simone Passera

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class WinsomeRMI extends UnicastRemoteObject implements WinsomeRMIServices {
    public WinsomeRMI() throws RemoteException {
        super();
    }

    public CodeReturn register(String username, String password, List<String> tags) throws RemoteException {
        // Argomenti null
        if (username == null || password == null || tags == null) return new CodeReturn(400, "errore, uno o più argomenti uguali a null");
        // Username vuoto
        if (username.isEmpty()) return new CodeReturn(401, "errore, username vuoto");
        // Password vuota
        if (password.isEmpty()) return new CodeReturn(401, "errore, password vuota");
        // Lista di tag vuota
        if (tags.isEmpty()) return new CodeReturn(401, "errore, lista di tag vuota");
        // lista di tag troppo grande [max 5]
        if (tags.size() > 5) return new CodeReturn(402, "errore, lista di tag troppo grande [max 5]");

        for (String tag : tags) {
            // lista di tag contiene valori vuoti
            if (tag.isEmpty()) return new CodeReturn(401, "errore, lista di tag contiene valori vuoti");
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
            return new CodeReturn(200, "ok");
        }

        // L'utente esiste già
        return new CodeReturn(403, "errore, utente " + username + " già esistente");
    }
}
