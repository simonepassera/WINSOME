import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

public class NotifyFollowers extends UnicastRemoteObject implements NotifyFollowersInterface {
    private ArrayList<String> listFollowers;

    public NotifyFollowers(ArrayList<String> listFollowers) throws RemoteException {
        super();
        this.listFollowers = listFollowers;
    }

    // Funzione utilizzata dal server al momento della registrazione
    // della callback per inizializzare la lista dei followers
    // @Return CodeReturn
    public String init(String listUsers) throws RemoteException {
        // listUsers null
        if (listUsers == null) return new Gson().toJson(new CodeReturn(400, "errore, listUsers uguale a null"));
        // listUsers vuoto
        if (listUsers.isEmpty()) return new Gson().toJson(new CodeReturn(401, "errore, listUsers vuoto"));

        Gson gson = new Gson();
        Type arrayListType = new TypeToken<ArrayList<String>>(){}.getType();

        synchronized (listFollowers) {
            listFollowers.addAll(gson.fromJson(listUsers, arrayListType));
        }

        return gson.toJson(new CodeReturn(200, "ok"));
    }

    // Aggiunge un utente alla lista dei followers
    public String addFollower(String username) throws RemoteException {
        // Username null
        if (username == null) return new Gson().toJson(new CodeReturn(400, "errore, username uguale a null"));
        // Username vuoto
        if (username.isEmpty()) return new Gson().toJson(new CodeReturn(401, "errore, username vuoto"));

        synchronized (listFollowers) {
            if (!listFollowers.contains(username)) listFollowers.add(username);
        }

        return new Gson().toJson(new CodeReturn(200, "username aggiunto"));
    }

    // Rimuove un utente dalla lista dei followers
    public String removeFollower(String username) throws RemoteException {
        // Username null
        if (username == null) return new Gson().toJson(new CodeReturn(400, "errore, username uguale a null"));
        // Username vuoto
        if (username.isEmpty()) return new Gson().toJson(new CodeReturn(401, "errore, username vuoto"));

        synchronized (listFollowers) {
            listFollowers.remove(listFollowers);
        }

        return new Gson().toJson(new CodeReturn(200, "username aggiunto"));
    }
}
