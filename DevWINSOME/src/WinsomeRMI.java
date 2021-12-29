// @Author Simone Passera

import com.google.gson.Gson;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomeRMI extends UnicastRemoteObject implements WinsomeRMIServices {
    // Mappa (username, password)
    private final ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private final ConcurrentHashMap<String, ArrayList<String>> tags;

    public WinsomeRMI(ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags) throws RemoteException {
        super();

        this.users = users;
        this.tags = tags;
    }

    public String register(String username, String password, List<String> listTags) throws RemoteException {
        // Argomenti null
        if (username == null || password == null || tags == null) return new Gson().toJson(new CodeReturn(400, "errore, uno o più argomenti uguali a null"));
        // Username vuoto
        if (username.isEmpty()) return new Gson().toJson(new CodeReturn(401, "errore, username vuoto"));
        // Username troppo lungo [max 15]
        if (username.length() > 15) return new Gson().toJson(new CodeReturn(401, "errore, username troppo lungo [max 15]"));
        // Password vuota
        if (password.isEmpty()) return new Gson().toJson(new CodeReturn(401, "errore, password vuota"));
        // Lista di tag vuota
        if (listTags.isEmpty()) return new Gson().toJson(new CodeReturn(401, "errore, lista di tag vuota"));
        // lista di tag troppo grande [max 5]
        if (listTags.size() > 5) return new Gson().toJson(new CodeReturn(402, "errore, lista di tag troppo grande [max 5]"));

        for (String tag : listTags) {
            // lista di tag contiene valori vuoti
            if (tag.isEmpty()) return new Gson().toJson(new CodeReturn(401, "errore, lista di tag contiene valori vuoti"));
        }

        // Inserisco username e password se l'utente non esiste
        if (users.putIfAbsent(username, password) == null) {
            ArrayList<String> tagsList = new ArrayList<>();
            // Aggiungo i tag in minuscolo
            for (String tag : listTags) {
                tagsList.add(tag.toLowerCase());
            }

            tags.put(username, tagsList);

            // Ok
            return new Gson().toJson(new CodeReturn(200, "ok"));
        }

        // L'utente esiste già
        return new Gson().toJson(new CodeReturn(403, "errore, utente " + username + " già esistente"));
    }

    public String registerListFollowers(NotifyFollowersInterface callback) throws RemoteException {
    }
}
