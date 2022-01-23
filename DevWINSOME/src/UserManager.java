// @Author Simone Passera

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Thread che gestisce le richieste di un client per l'intera durata della connessione
public class UserManager implements Runnable {
    // Socket per la connessione persistente con il client
    private final Socket user;
    // Ip e porta gruppo di multicast
    private final String multicastAddress;
    private final int multicastPort;
    // Stringa che contiene il nome dell'utente se ha effettuato il login, oppure null in caso contrario
    private String usernameLogin = null;
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
    // Mappa (idPost, post)
    private final ConcurrentHashMap<Integer, Post> posts;
    // Mappa (username, wallet)
    private final ConcurrentHashMap<String, Wallet> wallets;
    // Lista delle interazioni dall'ultimo calcolo delle ricompense
    private final ListInteractions listInteractions;
    // Lista degli utenti connessi
    private final Vector<String> connectedUsers;
    // Generatore id per un post
    private final AtomicInteger idGenerator;
    // Variabile di terminazione
    private boolean exit = false;
    // Oggetto gson
    private final Gson gson;
    // Oggetto gson che considera le annotazioni
    private final Gson gsonExpose;
    // Tipo dell'oggetto restituito
    private final Type CodeReturnType;
    // Tipo post
    private final Type postType;
    // Tipo wallet
    private final Type walletType;
    // Tipo mappa (utente, tags)
    private final Type mapUserTagsType;

    public UserManager(Socket user, ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags, ConcurrentHashMap<String, NotifyFollowersInterface> stubs, ConcurrentHashMap<String, ArrayList<String>> followers, ConcurrentHashMap<String, ArrayList<String>> followings, ConcurrentHashMap<String, Vector<Post>> blogs, ConcurrentHashMap<Integer, Post> posts, ConcurrentHashMap<String, Wallet> wallets, ListInteractions listInteractions, Vector<String> connectedUsers, AtomicInteger idGenerator, String multicastAddress, int multicastPort) {
        this.user = user;
        this.users = users;
        this.tags = tags;
        this.stubs = stubs;
        this.followers = followers;
        this.followings = followings;
        this.blogs = blogs;
        this.posts = posts;
        this.wallets = wallets;
        this.listInteractions = listInteractions;
        this.connectedUsers = connectedUsers;
        this.idGenerator = idGenerator;
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        gson = new Gson();
        gsonExpose = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        CodeReturnType = new TypeToken<CodeReturn>() {}.getType();
        postType = new TypeToken<Post>() {}.getType();
        walletType = new TypeToken<Wallet>() {}.getType();
        mapUserTagsType = new TypeToken<HashMap<String, ArrayList<String>>>() {}.getType();
    }

    @Override
    public void run() {
        // inizializzo gli stream di comunicazioni
        try (PrintWriter response = new PrintWriter(user.getOutputStream());
             BufferedReader request = new BufferedReader(new InputStreamReader(user.getInputStream()))) {
            // Comunico al client ip e porta relative al gruppo di multicast
            response.println(multicastAddress);
            response.println(multicastPort);
            response.flush();

            String command, username, password, title, content;
            int id = 0, vote;
            // Rispondo alle richieste del client fino alla terminazione esplicita del client
            while (!exit) {
                // Attendo una richiesta del client
                command = request.readLine();
                // Il client si è disconnesso improvvisamente
                if (command == null) {
                    exitNow();
                    return;
                }
                // Il server è in chiusura
                if (Thread.currentThread().isInterrupted()) {
                    response.println(502);
                    response.println("server in chiusura");
                    response.flush();
                    return;
                }
                // Eseguo la funzione che implementa il comando richiesto
                switch (command) {
                    case "exit":
                        exit(response);
                        break;
                    case "login":
                        username =  request.readLine();
                        password = request.readLine();
                        login(username, password, response);
                        break;
                    case "logout":
                        logout(response);
                        break;
                    case "listUsers":
                        listUsers(response);
                        break;
                    case "listFollowing":
                        listFollowing(response);
                        break;
                    case "follow":
                        username = request.readLine();
                        followUser(username, response);
                        break;
                    case "unfollow":
                        username = request.readLine();
                        unfollowUser(username, response);
                        break;
                    case "blog":
                        viewBlog(response);
                        break;
                    case "showFeed":
                        showFeed(response);
                        break;
                    case "createPost":
                        title =  request.readLine();
                        content = request.readLine();
                        createPost(title, content, response);
                        break;
                    case "deletePost":
                        try {
                            id = Integer.parseInt(request.readLine());
                        } catch (NumberFormatException fe) {
                            response.println(415);
                            response.println("id post errore di conversione");
                            response.flush();
                            break;
                        }

                        deletePost(id, response);
                        break;
                    case "showPost":
                        try {
                            id = Integer.parseInt(request.readLine());
                        } catch (NumberFormatException fe) {
                            response.println(415);
                            response.println("id post errore di conversione");
                            response.flush();
                            break;
                        }

                        showPost(id, response);
                        break;
                    case "rewinPost":
                        try {
                            id = Integer.parseInt(request.readLine());
                        } catch (NumberFormatException fe) {
                            response.println(415);
                            response.println("id post errore di conversione");
                            response.flush();
                            break;
                        }

                        rewinPost(id, response);
                        break;
                    case "ratePost":
                        try {
                            id = Integer.parseInt(request.readLine());
                        } catch (NumberFormatException fe) {
                            response.println(415);
                            response.println("id post errore di conversione");
                            response.flush();
                            break;
                        }

                        try {
                            vote = Integer.parseInt(request.readLine());
                        } catch (NumberFormatException fe) {
                            response.println(415);
                            response.println("voto errore di conversione");
                            response.flush();
                            break;
                        }

                        ratePost(id, vote, response);
                        break;
                    case "addComment":
                        try {
                            id = Integer.parseInt(request.readLine());
                        } catch (NumberFormatException fe) {
                            response.println(415);
                            response.println("id post errore di conversione");
                            response.flush();
                            break;
                        }

                        content = request.readLine();
                        addComment(id, content, response);
                        break;
                    case "getWallet":
                        getWallet(response);
                        break;
                    case "getWalletBtc":
                        getWalletBtc(response);
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Errore (" + Thread.currentThread().getName() + "): " + e.getMessage());
        }
    }

    private void exit(PrintWriter response) {
        // C'è un utente collegato
        if (usernameLogin != null) {
            response.println(405);
            response.println("c'è un utente collegato, deve essere prima scollegato");
            response.flush();
            return;
        }

        response.println(200);
        response.println("Arrivederci :)");
        response.flush();

        try { user.close(); } catch (IOException ignore) {}
        exit = true;
    }

    private void exitNow() {
        // L'utente è ancora connesso
        if (usernameLogin != null) {
            // Rimuovo l'utente dai connessi
            connectedUsers.remove(usernameLogin);
            // Rimuovo la callback
            stubs.remove(usernameLogin);
        }
    }

    private void login(String username, String password, PrintWriter response) {
        // Argomenti null
        if (username == null || password == null) {
            response.println(400);
            response.println("errore, uno o più argomenti uguali a null");
            response.flush();
            return;
        }
        // Username vuoto
        if (username.isEmpty()) {
            response.println(401);
            response.println("errore, username vuoto");
            response.flush();
            return;
        }
        // Password vuota
        if (password.isEmpty()) {
            response.println(401);
            response.println("errore, password vuota");
            response.flush();
            return;
        }
        // C'è un utente già collegato
        if (usernameLogin != null) {
            if (usernameLogin.equals(username)) {
                response.println(405);
                response.println(username + " già collegato");
            } else {
                response.println(405);
                response.println("c'è un utente già collegato, deve essere prima scollegato");
            }

            response.flush();
            return;
        }
        // Controllo se l'utente è registrato
        if (users.containsKey(username)) {
            // Controllo se la password è corretta
            if (!users.get(username).equals(password)) {
                response.println(407);
                response.println("errore, password non corretta");
                response.flush();
                return;
            }

            boolean absent;

            synchronized (connectedUsers) {
                absent = !connectedUsers.contains(username);
                if (absent) connectedUsers.add(username);
            }

            // Controllo se l'utente è già connesso su un altro terminale
            if (absent) {
                usernameLogin = username;

                response.println(200);
                response.println(username + " logged in");
            } else {
                response.println(405);
                response.println("utente già connesso su un altro terminale");
            }
        } else {
            response.println(404);
            response.println("errore, utente " + username + " non esiste");
        }

        response.flush();
    }

    private void logout(PrintWriter response) {
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Rimuovo l'utente dalla lista degli utenti connessi
        connectedUsers.remove(usernameLogin);

        response.println(200);
        response.println("utente " + usernameLogin + " disconnesso");
        response.flush();

        usernameLogin = null;
    }

    private void listUsers(PrintWriter response) {
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }

        response.println(201);
        response.println("invio lista utenti tags");
        response.flush();

        // Creo la lista da inviare
        HashMap<String, ArrayList<String>> usersTags = new HashMap<>();
        // Recupero la lista dei tag dell'utente connesso
        ArrayList<String> userLoginTags = tags.get(usernameLogin);
        // Inserisco nella lista da inviare gli utenti che hanno almeno
        // un tag in comune con l'utente connesso
        for (String name : users.keySet()) {
            // Controllo di non inserire l'utente connesso
            if (!name.equals(usernameLogin)) {
                ArrayList<String> nameTags = tags.get(name);
                boolean match = false;

                for (String tag : userLoginTags) {
                    if (nameTags.contains(tag)) {
                        match = true;
                        break;
                    }
                }
                // Se c'è stato un match inserisco l'utente nella lista
                if (match) usersTags.put(name, nameTags);
            }
        }
        // Invio la lista
        response.println(gson.toJson(usersTags, mapUserTagsType));
        response.flush();
    }

    private void listFollowing(PrintWriter response) {
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }

        response.println(201);
        response.println("invio lista utenti");
        response.flush();
        // Recupero la lista dei following
        ArrayList<String> listUsers = followings.get(usernameLogin);
        // Invio la lista
        response.println(gson.toJson(listUsers));
        response.flush();
    }

    private void followUser(String username, PrintWriter response) {
        // Argomenti null
        if (username == null) {
            response.println(400);
            response.println("errore, username uguale a null");
            response.flush();
            return;
        }
        // Username vuoto
        if (username.isEmpty()) {
            response.println(401);
            response.println("errore, username vuoto");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Controllo se username è registrato
        if (!users.containsKey(username)) {
            response.println(404);
            response.println("errore, utente " + username + " non esiste");
            response.flush();
            return;
        }
        // Controllo che non sia l'utente connesso
        if (usernameLogin.equals(username)) {
            response.println(419);
            response.println("errore, non è possibile seguire se stessi");
            response.flush();
            return;
        }
        // Controllo se posso seguire l'utente (almeno un tag in comune)
        ArrayList<String> userLoginTags = tags.get(usernameLogin);
        ArrayList<String> usernameTags = tags.get(username);

        boolean match = false;

        for (String tag : userLoginTags) {
            if (usernameTags.contains(tag)) {
                match = true;
                break;
            }
        }

        if (!match) {
            response.println(409);
            response.println("erroe, l'utente " + username + " non può essere seguito (nessun tag in comune)");
            response.flush();
            return;
        }

        ArrayList<String> listFollowing = followings.get(usernameLogin);
        // Controllo se l'utente segue già username
        if (listFollowing.contains(username)) {
            response.println(410);
            response.println("errore, segui già " + username);
            response.flush();
            return;
        }
        // Recupero la lista dei followers di username
        ArrayList<String> listFollowers = followers.get(username);

        synchronized (listFollowing) {
            synchronized (listFollowers) {
                // Aggiungo username alla lista following dell'utente connesso
                listFollowing.add(username);
                // Aggiungo all'utente username il follower utente connesso
                listFollowers.add(usernameLogin);
            }
        }

        // Notifico a username un nuovo follower con la callback se connesso
        NotifyFollowersInterface notifyUsername = stubs.get(username);
        CodeReturn code = null;

        try {
            if (notifyUsername != null) code = gson.fromJson(notifyUsername.addFollower(usernameLogin), CodeReturnType);
        } catch (RemoteException ignore) {
            // Richiesta eseguita con successo, ma la notifica è fallita
            response.println(200);
            response.println("Ora segui " + username + ", la notifica non è andata a buon fine");
            response.flush();
            return;
        }
        // Invio Codice Ok
        response.println(200);
        // Invio messaggio
        if (code != null && code.getCode() != 200)
            // Richiesta eseguita con successo, ma la notifica è fallita
            response.println("Ora segui " + username + ", la notifica non è andata a buon fine");
        else
            // Ok
            response.println("Ora segui " + username);

        response.flush();
    }

    private void unfollowUser(String username, PrintWriter response) {
        // Argomenti null
        if (username == null) {
            response.println(400);
            response.println("errore, username uguale a null");
            response.flush();
            return;
        }
        // Username vuoto
        if (username.isEmpty()) {
            response.println(401);
            response.println("errore, username vuoto");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Controllo se username è registrato
        if (!users.containsKey(username)) {
            response.println(404);
            response.println("errore, utente " + username + " non esiste");
            response.flush();
            return;
        }

        ArrayList<String> listFollowing = followings.get(usernameLogin);
        // Controllo se l'utente segue username
        if (!listFollowing.contains(username)) {
            response.println(411);
            response.println("errore, non segui " + username);
            response.flush();
            return;
        }
        // Recupero la lista dei followers di username
        ArrayList<String> listFollowers = followers.get(username);

        synchronized (listFollowing) {
            synchronized (listFollowers) {
                // Rimuovo username dalla lista following dell'utente connesso
                listFollowing.remove(username);
                // Rimuovo all'utente username il follower utente connesso
                listFollowers.remove(usernameLogin);
            }
        }

        // Notifico a username di rimuovere il follower con la callback se connesso
        NotifyFollowersInterface notifyUsername = stubs.get(username);
        CodeReturn code = null;

        try {
            if (notifyUsername != null) code = gson.fromJson(notifyUsername.removeFollower(usernameLogin), CodeReturnType);
        } catch (RemoteException ignore) {
            // Richiesta eseguita con successo, ma la notifica è fallita
            response.println(200);
            response.println("Ora non segui più " + username + ", la notifica non è andata a buon fine");
            response.flush();
            return;
        }
        // Invio Codice Ok
        response.println(200);
        // Invio messaggio
        if (code != null && code.getCode() != 200)
            // Richiesta eseguita con successo, ma la notifica è fallita
            response.println("Ora non segui più " + username + ", la notifica non è andata a buon fine");
        else
            // Ok
            response.println("Ora non segui più " + username);

        response.flush();
    }

    private void viewBlog(PrintWriter response) {
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }

        response.println(201);
        response.println("invio i post");
        response.flush();

        // Recupero la lista dei post nel blog
        Vector<Post> usernameBlog = blogs.get(usernameLogin);

        synchronized (usernameBlog) {
            response.println(usernameBlog.size());
            response.flush();
            // Invio i post
            for (Post p : usernameBlog) {
                synchronized (p) {
                    response.println(gsonExpose.toJson(p, postType));
                }
                response.flush();
            }
        }
    }

    private void showFeed(PrintWriter response) {
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }

        response.println(201);
        response.println("invio i post");
        response.flush();

        // Recupero la lista dei following
        ArrayList<String> listFollowing = followings.get(usernameLogin);
        Vector<Post> postsFollowed;

        // Invio il numero di utenti seguiti
        response.println(listFollowing.size());
        response.flush();

        for (String nameFollowed : listFollowing) {
            // Recupero il blog di ogni utente seguito
            postsFollowed = blogs.get(nameFollowed);

            synchronized (postsFollowed) {
                // Invio il numero dei post all'interno del blog
                response.println(postsFollowed.size());
                response.flush();
                // Invio i post nel blog
                for (Post p : postsFollowed) {
                    synchronized (p) {
                        response.println(gsonExpose.toJson(p, postType));
                    }
                    response.flush();
                }
            }
        }
    }

    private void createPost(String title, String content, PrintWriter response) {
        // Argomenti null
        if (title == null || content == null) {
            response.println(400);
            response.println("errore, uno o più argomenti uguali a null");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Controllo la lunghezza del titolo
        if (title.length() > 20) {
            response.println(413);
            response.println("errore, titolo del post troppo lungo [max 20]");
            response.flush();
            return;
        }
        // Controllo la lunghezza del contenuto
        if (content.length() > 500) {
            response.println(414);
            response.println("errore, contenuto del post troppo lungo [max 500]");
            response.flush();
            return;
        }
        // Creo il post
        Post newPost = new Post(usernameLogin, title, content, idGenerator);
        // Aggiungo il post alla lista delle interazioni
        listInteractions.addPost(newPost.getId(), usernameLogin);
        // Inserisco il post nel blog dell'utente connesso, e nella lista di tutti i post
        Vector<Post> blog = blogs.get(usernameLogin);

        synchronized (posts) {
            synchronized (blog) {
                posts.put(newPost.getId(), newPost);
                blog.add(newPost);
            }
        }

        // Ok
        response.println(200);
        response.println("post pubblicato (id = " + newPost.getId() + ")");
        response.flush();
    }

    private void deletePost(Integer id, PrintWriter response) {
        // Argomento null
        if (id == null) {
            response.println(400);
            response.println("errore, uno o più argomenti uguali a null");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Recupero il post
        Post p = posts.get(id);
        // Controllo se esiste
        if (p == null) {
            response.println(416);
            response.println("errore, post " + id + " non esiste");
            response.flush();
            return;
        }
        // Controllo che l'utente sia l'autore del post
        if (!p.getAuthor().equals(usernameLogin)) {
            response.println(416);
            response.println("errore, non sei autore del post " + id);
            response.flush();
            return;
        }
        // Elimino il post
        synchronized (posts) {
            posts.remove(p.getId());
            // Rimuovo il post dal blog
            Vector<Post> blog = blogs.get(usernameLogin);
            blog.remove(p);
            // Elimino il post nei blog degli utenti cha hanno eseguito il rewin
            synchronized (p) {
                for (String user : p.getUsersRewin()) {
                    blog = blogs.get(user);
                    blog.remove(p);
                }
            }
        }
        // Elimino il post dalla lista delle interazioni
        listInteractions.removePost(id);
        // Ok
        response.println(200);
        response.println("post (id = " + id + ") cancellato");
        response.flush();
    }

    private void showPost(Integer id, PrintWriter response) {
        // Argomento null
        if (id == null) {
            response.println(400);
            response.println("errore, uno o più argomenti uguali a null");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Recupero il post
        Post p = posts.get(id);
        // Controllo se esiste
        if (p == null) {
            response.println(416);
            response.println("errore, post " + id + " non esiste");
            response.flush();
            return;
        }
        // Controllo se l'autore del post è l'utente connesso
        if (!p.getAuthor().equals(usernameLogin)) {
            ArrayList<String> listFollowing = followings.get(usernameLogin);
            // Controllo se l'utente collegato segue l'autore del post
            if (!listFollowing.contains(p.getAuthor())) {
                // Cerco il post all'interno del feed
                boolean find = false;

                Iterator<String> listFollowingIterator = listFollowing.iterator();
                Vector<Post> postsFollowed;
                Iterator<Post> postsFollowedIterator;

                while (listFollowingIterator.hasNext() && !find) {
                    // Recupero il blog di ogni utente seguito
                    postsFollowed = blogs.get(listFollowingIterator.next());

                    synchronized (postsFollowed) {
                        postsFollowedIterator = postsFollowed.iterator();
                        // Cerco il post nel blog
                        while (postsFollowedIterator.hasNext() && !find) {
                            if (postsFollowedIterator.next().getId().equals(p.getId())) {
                                find = true;
                            }
                        }
                    }
                }
                // Se non trovato cerco il post nel blog (rewin post)
                if (!find) {
                    HashSet<String> usersRewin = p.getUsersRewin();

                    synchronized (p) {
                        if (usersRewin.contains(usernameLogin)) {
                            find = true;
                        }
                    }
                }
                // Post non trovato
                if (!find) {
                    response.println(417);
                    response.println("post (id = " + id + ") non appartiene al tuo feed o blog");
                    response.flush();
                    return;
                }
            }
        }

        response.println(201);
        response.println("invio il post");
        response.flush();

        // Invio il post
        synchronized (p) {
            response.println(gson.toJson(p, postType));
        }

        response.flush();
    }

    private void rewinPost(Integer id, PrintWriter response) {
        // Argomento null
        if (id == null) {
            response.println(400);
            response.println("errore, uno o più argomenti uguali a null");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }

        synchronized (posts) {
            // Recupero il post
            Post p = posts.get(id);
            // Controllo se esiste
            if (p == null) {
                response.println(416);
                response.println("errore, post " + id + " non esiste");
                response.flush();
                return;
            }

            // Controllo se è già stato eseguito il rewin
            HashSet<String> usersRewin = p.getUsersRewin();

            synchronized (p) {
                if (usersRewin.contains(usernameLogin)) {
                    response.println(418);
                    response.println("post (id = " + id + ") già presente nel blog");
                    response.flush();
                    return;
                }
            }

            // Controllo se l'utente collegato segue l'autore del post
            ArrayList<String> listFollowing = followings.get(usernameLogin);

            if (!listFollowing.contains(p.getAuthor())) {
                // Cerco il post all'interno del feed
                boolean find = false;

                Iterator<String> listFollowingIterator = listFollowing.iterator();
                Vector<Post> postsFollowed;
                Iterator<Post> postsFollowedIterator;

                while (listFollowingIterator.hasNext() && !find) {
                    // Recupero il blog di ogni followed
                    postsFollowed = blogs.get(listFollowingIterator.next());

                    synchronized (postsFollowed) {
                        postsFollowedIterator = postsFollowed.iterator();
                        // Cerco il post nel blog
                        while (postsFollowedIterator.hasNext() && !find) {
                            if (postsFollowedIterator.next().getId().equals(p.getId())) {
                                find = true;
                            }
                        }
                    }
                }

                // Post non trovato
                if (!find) {
                    response.println(417);
                    response.println("post (id = " + id + ") non appartiene al tuo feed");
                    response.flush();
                    return;
                }
            }
            // Inserisco il post nel blog
            Vector<Post> blog = blogs.get(usernameLogin);
            blog.add(p);
            // Inserisco nel post che usernameLogin ha eseguito il rewin
            p.addUserRewin(usernameLogin);
        }
        // Ok
        response.println(200);
        response.println("eseguito il rewin del post (id = " + id + ")");
        response.flush();
    }

    private void ratePost(Integer id, Integer vote, PrintWriter response) {
        // Argomenti null
        if (id == null || vote == null) {
            response.println(400);
            response.println("errore, uno o più argomenti uguali a null");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Controllo il voto se è valido
        if (!vote.equals(1) && !vote.equals(-1)) {
            response.println(420);
            response.println("errore, valore del voto [-1, +1]");
            response.flush();
            return;
        }
        // Recupero il post
        Post p = posts.get(id);
        // Controllo se esiste
        if (p == null) {
            response.println(416);
            response.println("errore, post " + id + " non esiste");
            response.flush();
            return;
        }
        // Controllo se l'utente collegato segue l'autore del post
        ArrayList<String> listFollowing = followings.get(usernameLogin);

        if (!listFollowing.contains(p.getAuthor())) {
            // Cerco il post all'interno del feed
            boolean find = false;

            Vector<Post> postsFollowed;
            Iterator<String> listFollowingIterator = listFollowing.iterator();
            Iterator<Post> postsFollowedIterator;

            while (listFollowingIterator.hasNext() && !find) {
                // Recupero il blog di ogni followed
                postsFollowed = blogs.get(listFollowingIterator.next());

                synchronized (postsFollowed) {
                    postsFollowedIterator = postsFollowed.iterator();
                    // Cerco il post nel blog
                    while (postsFollowedIterator.hasNext() && !find) {
                        if (postsFollowedIterator.next().getId().equals(p.getId())) {
                            find = true;
                        }
                    }
                }
            }
            // Post non trovato
            if (!find) {
                response.println(417);
                response.println("post (id = " + id + ") non appartiene al tuo feed");
                response.flush();
                return;
            }
        }
        // Aggiungo il voto
        int r;

        if (vote == 1) {
            r = p.addUpVote(usernameLogin);
        } else {
            r = p.addDownVote(usernameLogin);
        }
        // Post già votato
        if (r == 1) {
            response.println(417);
            response.println("post (id = " + id + ") già votato");
            response.flush();
            return;
        }
        // Aggiungo il voto nella lista delle interazioni
        if (vote == 1) listInteractions.addUpVote(id, usernameLogin);
        else listInteractions.addDownVote(id);
        // Ok
        response.println(200);
        response.println("post (id = " + id + ") votato");
        response.flush();
    }

    private void addComment(Integer id, String comment, PrintWriter response) {
        // Argomenti null
        if (id == null || comment == null) {
            response.println(400);
            response.println("errore, uno o più argomenti uguali a null");
            response.flush();
            return;
        }
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Controllo la lunghezza del commento
        if (comment.length() > 500) {
            response.println(422);
            response.println("errore, commento troppo lungo [max 500]");
            response.flush();
            return;
        }
        // Recupero il post
        Post p = posts.get(id);
        // Controllo se esiste
        if (p == null) {
            response.println(416);
            response.println("errore, post " + id + " non esiste");
            response.flush();
            return;
        }
        // Controllo se l'utente collegato non segue l'autore del post
        ArrayList<String> listFollowing = followings.get(usernameLogin);
        if (!listFollowing.contains(p.getAuthor())) {
            // Cerco il post all'interno del feed
            boolean find = false;

            Iterator<String> listFollowingIterator = listFollowing.iterator();
            Vector<Post> postsFollowed;
            Iterator<Post> postsFollowedIterator;

            while (listFollowingIterator.hasNext() && !find) {
                // Recupero il blog di ogni utente seguito
                postsFollowed = blogs.get(listFollowingIterator.next());

                synchronized (postsFollowed) {
                    postsFollowedIterator = postsFollowed.iterator();
                    // Cerco il post nel blog
                    while (postsFollowedIterator.hasNext() && !find) {
                        if (postsFollowedIterator.next().getId().equals(p.getId())) {
                            find = true;
                        }
                    }
                }
            }
            // Post non trovato
            if (!find) {
                response.println(417);
                response.println("post (id = " + id + ") non appartiene al tuo feed");
                response.flush();
                return;
            }
        }
        // Aggiungo il commento
        p.addComment(usernameLogin, comment);
        // Aggiungo il commento nella lista delle interazioni
        listInteractions.addComment(id, usernameLogin);
        // Ok
        response.println(200);
        response.println("commento aggiunto");
        response.flush();
    }

    private void getWallet(PrintWriter response) {
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }
        // Recupero il portafoglio
        Wallet wallet = wallets.get(usernameLogin);

        response.println(201);
        response.println("invio il wallet");
        response.flush();

        // Invio il portafoglio
        synchronized (wallet) {
            response.println(gson.toJson(wallet, walletType));
        }

        response.flush();
    }

    private void getWalletBtc(PrintWriter response) {
        // Controllo che ci sia un utente connesso
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }

        // Recupero il portafoglio
        Wallet wallet = wallets.get(usernameLogin);
        double wincoin = wallet.getWincoin();

        InputStream inStreamURL = null;

        try {
            // Apro uno stream di input sul sito web random.org
            URL randomSiteURL = new URL("https://www.random.org/decimal-fractions/?num=1&dec=6&col=1&format=plain&rnd=new");
            inStreamURL = randomSiteURL.openStream();
            // Leggo il tasso di cambio (numero decimale)
            byte[] decimalByte = inStreamURL.readAllBytes();
            String decimalString = new String(decimalByte);
            double exchangeRate = Double.parseDouble(decimalString);

            response.println(201);
            response.println("invio il valore convertito");
            response.flush();
            // Converto wincoin in bitcoin
            double btc = wincoin * exchangeRate;
            // Invio il valore in bitcoin
            response.println(btc);
            response.flush();
        } catch (NumberFormatException | IOException e) {
            // Conversione non riuscita
            response.println(501);
            response.println("errore, conversione non riuscita");
            response.flush();
        }
        // Chiudo lo stream
        try {
            inStreamURL.close();
        } catch (NullPointerException | IOException ignore) {}
    }
}
