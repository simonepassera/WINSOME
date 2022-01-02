// @Author Simone Passera

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;

public class NotifyFollowers extends UnicastRemoteObject implements NotifyFollowersInterface {
    // Lista dei followers dell'utente connesso
    private Vector<String> listFollowers;
    // Tipo dell'oggetto restituito
    Type CodeReturnType;
    // Oggetto gson
    Gson gson;

    public NotifyFollowers(Vector<String> listFollowers) throws RemoteException {
        super();
        this.listFollowers = listFollowers;
        CodeReturnType = new TypeToken<CodeReturn>(){}.getType();
        gson = new Gson();
    }

    // Funzione utilizzata dal server al momento della registrazione
    // della callback per inizializzare la lista dei followers
    // @Return CodeReturn
    public String init(String listUsers) throws RemoteException {
        // listUsers null
        if (listUsers == null) return gson.toJson(new CodeReturn(400, "errore, listUsers uguale a null"), CodeReturnType);
        // listUsers vuoto
        if (listUsers.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, listUsers vuoto"), CodeReturnType);

        Type vectorType = new TypeToken<Vector<String>>(){}.getType();
        listFollowers.addAll(gson.fromJson(listUsers, vectorType));

        return gson.toJson(new CodeReturn(200, "ok"), CodeReturnType);
    }

    // Aggiunge un utente alla lista dei followers
    public String addFollower(String username) throws RemoteException {
        // Username null
        if (username == null) return gson.toJson(new CodeReturn(400, "errore, username uguale a null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);

        synchronized (listFollowers) {
            if (!listFollowers.contains(username)) listFollowers.add(username);
        }

        return gson.toJson(new CodeReturn(200, "username aggiunto"), CodeReturnType);
    }

    // Rimuove un utente dalla lista dei followers
    public String removeFollower(String username) throws RemoteException {
        // Username null
        if (username == null) return gson.toJson(new CodeReturn(400, "errore, username uguale a null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);

        listFollowers.remove(username);

        return gson.toJson(new CodeReturn(200, "username aggiunto"), CodeReturnType);
    }
}
