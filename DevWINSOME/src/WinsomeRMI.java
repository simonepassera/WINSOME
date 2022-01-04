// @Author Simone Passera

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WinsomeRMI extends UnicastRemoteObject implements WinsomeRMIServices {
    // Mappa (username, password)
    private final ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private final ConcurrentHashMap<String, ArrayList<String>> tags;
    // Mappa (username, stub_callback)
    private final ConcurrentHashMap<String, NotifyFollowersInterface> stubs;
    // Mappa (username, followers)
    private final ConcurrentHashMap<String, Vector<String>> followers;
    // Mappa (username, following)
    private final ConcurrentHashMap<String, Vector<String>> followings;
    // Mappa (username, blog)
    private final ConcurrentHashMap<String, Vector<Post>> blogs;
    // Tipo dell'oggetto restituito
    Type CodeReturnType;
    // Oggetto gson
    Gson gson;

    public WinsomeRMI(ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags, ConcurrentHashMap<String, NotifyFollowersInterface> stubs, ConcurrentHashMap<String, Vector<String>> followers, ConcurrentHashMap<String, Vector<String>> followings, ConcurrentHashMap<String, Vector<Post>> blogs) throws RemoteException {
        super();

        this.users = users;
        this.tags = tags;
        this.stubs = stubs;
        this.followers = followers;
        this.followings = followings;
        this.blogs = blogs;
        CodeReturnType = new TypeToken<CodeReturn>(){}.getType();
        gson = new Gson();
    }

    public String register(String username, String password, ArrayList<String> listTags) throws RemoteException {
        // Tipo dell'oggetto restituito
        Type CodeReturnType = new TypeToken<CodeReturn>(){}.getType();
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

        boolean insertUsers = false;

        synchronized (users) {
            synchronized (tags) {
                synchronized (followings) {
                    synchronized (followers) {
                        synchronized (blogs) {
                            // Inserisco username e password se l'utente non esiste

                            if (users.putIfAbsent(username, password) == null) {
                                // Inserisco i tag
                                tags.put(username, tagsList);
                                // Inizializzo la lista dei following
                                followings.put(username, new Vector<>());
                                // Inizializzo la lista dei followers
                                followers.put(username, new Vector<>());
                                // Inizializzo il blog
                                blogs.put(username, new Vector<>());

                                insertUsers = true;
                            }
                        }
                    }
                }
            }
        }

        if (insertUsers)
            // Ok
            return gson.toJson(new CodeReturn(200, "ok"), CodeReturnType);
        else
            // L'utente esiste già
            return gson.toJson(new CodeReturn(403, "errore, utente " + username + " già esistente"), CodeReturnType);
    }

    public String registerListFollowers(String username, NotifyFollowersInterface callback) throws RemoteException {
        // Argomenti null
        if (username == null || callback == null) return gson.toJson(new CodeReturn(400, "errore, uno o più argomenti uguali a null"), CodeReturnType);
        // Username vuoto
        if (username.isEmpty()) return gson.toJson(new CodeReturn(401, "errore, username vuoto"), CodeReturnType);

        // Recupero la lista dei followers di username
        Vector<String> listFollowers = followers.get(username);
        if (listFollowers == null)  return gson.toJson(new CodeReturn(500, "errore, lista dei followers non esiste"), CodeReturnType);

        // Tipo della lista dei followers
        Type vectorType = new TypeToken<Vector<String>>(){}.getType();

        String listFollowersJson;

        synchronized (listFollowers) {
            listFollowersJson = gson.toJson(listFollowers, vectorType);
        }

        // Inizializzo la lista del client
        CodeReturn code = gson.fromJson(callback.init(listFollowersJson), CodeReturnType);

        if (code.getCode() != 200) return gson.toJson(code, CodeReturnType);

        // Inserisco la callback nella lista degli stub
        if (stubs.putIfAbsent(username, callback) == null)
            // Ok
            return gson.toJson(new CodeReturn(200, "ok"), CodeReturnType);

        // L'utente è già registrato la servizio di callback
        return gson.toJson(new CodeReturn(501, "utente già registrato alla callback"), CodeReturnType);
    }

    // Annulla la registrazione alla callback
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
