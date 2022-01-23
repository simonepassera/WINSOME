// @Author Simone Passera

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;

// Oggetto remoto inviato dal client al server (RMI callback),
// utilizzato dal server per aggiornare la lista dei follower del client
public class NotifyFollowers extends UnicastRemoteObject implements NotifyFollowersInterface {
    // Lista dei followers del client
    private final Vector<String> listFollowers;
    // Tipo della lista dei followers
    private final Type listFollowersType;
    // Tipo dell'oggetto restituito
    private final Type CodeReturnType;
    // Oggetto gson
    private final Gson gson;

    public NotifyFollowers(Vector<String> listFollowers) throws RemoteException {
        super();
        this.listFollowers = listFollowers;
        listFollowersType = new TypeToken<Vector<String>>(){}.getType();
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
        // Inizializzo la lista dei followers
        listFollowers.addAll(gson.fromJson(listUsers, listFollowersType));
        // Ok
        return gson.toJson(new CodeReturn(200, "ok"), CodeReturnType);
    }

    // Aggiunge un utente alla lista dei followers
    public String addFollower(String username) throws RemoteException {
        // Username null
        if (username == null) return gson.toJson(new CodeReturn(400, "errore, username uguale a null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);
        // Aggiungo il follower
        synchronized (listFollowers) {
            if (!listFollowers.contains(username)) listFollowers.add(username);
        }
        // Ok
        return gson.toJson(new CodeReturn(200, "username aggiunto"), CodeReturnType);
    }

    // Rimuove un utente dalla lista dei followers
    public String removeFollower(String username) throws RemoteException {
        // Username null
        if (username == null) return gson.toJson(new CodeReturn(400, "errore, username uguale a null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);
        // Rimuovo il follower
        listFollowers.remove(username);
        // Ok
        return gson.toJson(new CodeReturn(200, "username aggiunto"), CodeReturnType);
    }
}
