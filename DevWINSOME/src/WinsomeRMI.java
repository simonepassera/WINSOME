// @Author Simone Passera

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Oggetto remoto esportato dal server Winsome
public class WinsomeRMI extends UnicastRemoteObject implements WinsomeRMIServices {
    // Mappa (username, password)
    private final ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private final ConcurrentHashMap<String, ArrayList<String>> tags;
    // Mappa (username, stub_callback)
    private final ConcurrentHashMap<String, NotifyFollowersInterface> stubs;
    // Mappa (username, followers)
    private final ConcurrentHashMap<String, ArrayList<String>> followers;
    // Mappa (username, following)
    private final ConcurrentHashMap<String, ArrayList<String>> followings;
    // Mappa (username, blog)
    private final ConcurrentHashMap<String, Vector<Post>> blogs;
    // Mappa (username, wallet)
    private final ConcurrentHashMap<String, Wallet> wallets;
    // Tipo della lista dei followers
    private final Type listFollowersType;
    // Tipo dell'oggetto restituito
    private final Type CodeReturnType;
    // Oggetto gson
    private final Gson gson;

    public WinsomeRMI(ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags, ConcurrentHashMap<String, NotifyFollowersInterface> stubs, ConcurrentHashMap<String, ArrayList<String>> followers, ConcurrentHashMap<String, ArrayList<String>> followings, ConcurrentHashMap<String, Vector<Post>> blogs, ConcurrentHashMap<String, Wallet> wallets) throws RemoteException {
        super();

        this.users = users;
        this.tags = tags;
        this.stubs = stubs;
        this.followers = followers;
        this.followings = followings;
        this.blogs = blogs;
        this.wallets = wallets;
        listFollowersType = new TypeToken<Vector<String>>(){}.getType();
        CodeReturnType = new TypeToken<CodeReturn>(){}.getType();
        gson = new Gson();
    }

    // Inserisce un nuovo utente, listTags è una lista di massimo 5 tag
    // @Return CodeReturn
    public String register(String username, String password, ArrayList<String> listTags) throws RemoteException {
        // Argomenti null
        if (username == null || password == null || listTags == null) return gson.toJson(new CodeReturn(400, "errore, uno o più argomenti uguali a null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);
        // Username troppo lungo [max 15]
        if (username.length() > 15) return gson.toJson(new CodeReturn(401, "errore, username troppo lungo [max 15]"), CodeReturnType);
        // Password vuota
        if (password.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, password vuota"), CodeReturnType);
        // Lista di tag vuota
        if (listTags.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, lista di tag vuota"), CodeReturnType);
        // lista di tag troppo grande [max 5]
        if (listTags.size() > 5) return gson.toJson(new CodeReturn(402, "errore, lista di tag troppo grande [max 5]"), CodeReturnType);

        // Lista di tag che inserisco nel server
        ArrayList<String> tagsList = new ArrayList<>();

        for (String tag : listTags) {
            // Lista di tag contiene valori vuoti
            if (tag.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, lista di tag contiene valori vuoti"), CodeReturnType);
            // Controllo se ci sono duplicati
            if (tagsList.contains(tag.toLowerCase())) return gson.toJson(new CodeReturn(412, "errore, lista dei tag contiene duplicati"), CodeReturnType);

            tagsList.add(tag.toLowerCase());
        }

        boolean insertUser = false;

        synchronized (users) {
            // Inserisco username e password se l'utente non esiste
            if (users.putIfAbsent(username, password) == null) {
                // Inserisco i tag
                tags.put(username, tagsList);
                // Inizializzo la lista dei following
                followings.put(username, new ArrayList<>());
                // Inizializzo la lista dei followers
                followers.put(username, new ArrayList<>());
                // Inizializzo il blog
                blogs.put(username, new Vector<>());
                // Inizializzo il wallet
                wallets.put(username, new Wallet());

                insertUser = true;
            }
        }

        if (insertUser)
            // Ok
            return gson.toJson(new CodeReturn(200, "ok"), CodeReturnType);
        else
            // L'utente esiste già
            return gson.toJson(new CodeReturn(403, "errore, utente " + username + " già esistente"), CodeReturnType);
    }

    // Registra l'utente alla callback per ricevere aggiornamenti riguardo i propri followers
    // @Return CodeReturn
    public String registerListFollowers(String username, NotifyFollowersInterface callback) throws RemoteException {
        // Argomenti null
        if (username == null || callback == null) return gson.toJson(new CodeReturn(400, "errore, uno o più argomenti uguali a null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);

        // Recupero la lista dei followers di username
        ArrayList<String> listFollowers = followers.get(username);

        String listFollowersJson;

        synchronized (listFollowers) {
            listFollowersJson = gson.toJson(listFollowers, listFollowersType);
        }

        // Inizializzo la lista del client con la callback
        CodeReturn code = gson.fromJson(callback.init(listFollowersJson), CodeReturnType);
        // Controllo se l'operazione ha avuto successo
        if (code.getCode() != 200) return gson.toJson(code, CodeReturnType);

        // Inserisco la callback nella lista degli stub
        if (stubs.putIfAbsent(username, callback) == null)
            // Ok
            return gson.toJson(new CodeReturn(200, "ok"), CodeReturnType);
        else
            // L'utente è già registrato la servizio di callback
            return gson.toJson(new CodeReturn(500, "utente già registrato alla callback"), CodeReturnType);
    }

    // Annulla la registrazione alla callback
    // @Return CodeReturn
    public String unregisterListFollowers(String username) throws RemoteException {
        // Username null
        if (username == null) return gson.toJson(new CodeReturn(400, "errore, username null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);
        // Rimuovo la callback nella lista degli stub
        stubs.remove(username);
        // Ok
        return gson.toJson(new CodeReturn(200, "ok"), CodeReturnType);
    }
}
