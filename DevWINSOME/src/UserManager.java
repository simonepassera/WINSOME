// @Author Simone Passera

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserManager implements Runnable {
    // Socket per la connessione persistente con il client
    private final Socket user;
    // Stringa che contiene lo username se l'utente ha effettuato il login, oppure null in caso contrario
    private String usernameLogin = null;
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
    // Mappa (idPost, post)
    private final ConcurrentHashMap<Integer, Post> posts;
    // Lista degli utenti connessi
    private final Vector<String> connectedUsers;
    // Generatore id per un post
    private final AtomicInteger idGenerator;
    // Variabile di terminazione
    private boolean exit = true;
    // Oggetto gson
    private final Gson gson;
    // Oggetto gson che considera le annotazioni
    private final Gson gsonExpose;
    // Tipo dell'oggetto restituito
    Type CodeReturnType;
    // Tipo post
    Type postType;


    public UserManager(Socket user, ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags, ConcurrentHashMap<String, NotifyFollowersInterface> stubs, ConcurrentHashMap<String, Vector<String>> followers, ConcurrentHashMap<String, Vector<String>> followings, ConcurrentHashMap<String, Vector<Post>> blogs, ConcurrentHashMap<Integer, Post> posts, Vector<String> connectedUsers, AtomicInteger idGenerator) {
        this.user = user;
        this.users = users;
        this.tags = tags;
        this.stubs = stubs;
        this.followers = followers;
        this.followings = followings;
        this.blogs = blogs;
        this.posts = posts;
        this.connectedUsers = connectedUsers;
        this.idGenerator = idGenerator;
        gson = new Gson();
        gsonExpose = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        CodeReturnType = new TypeToken<CodeReturn>(){}.getType();
        postType = new TypeToken<Post>() {}.getType();
    }

    @Override
    public void run() {
        try (PrintWriter response = new PrintWriter(user.getOutputStream());
             BufferedReader request = new BufferedReader(new InputStreamReader(user.getInputStream()))) {
            String command, username, password, title, content;
            int id = 0;

            while (exit) {
                command = request.readLine();
                if (command == null) {
                    exitNow();
                    return;
                }

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
                    case "showPost":
                        try {
                            id = Integer.parseInt(request.readLine());
                        } catch (NumberFormatException fe) {
                            response.println(415);
                            response.println("id post errore di conversione");
                            response.flush();
                        }

                        showPost(id, response);
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
        exit = false;
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
                response.println("c'è un utente già collegato, deve essere prima scollegato");
            } else {
                response.println(405);
                response.println(username + " già collegato");
            }

            response.flush();
            return;
        }
        // Controllo se l'utente è registrato
        if (users.containsKey(username)) {
            if (!users.get(username).equals(password)) {
                response.println(407);
                response.println("errore, password non corretta");
                response.flush();
                return;
            }

            // L'utente è già connesso su un altro terminale
            boolean absent;

            synchronized (connectedUsers) {
                absent = !connectedUsers.contains(username);
                if (absent) connectedUsers.add(username);
            }

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
        if (usernameLogin == null) {
            response.println(406);
            response.println("errore, nessun utente connesso");
            response.flush();
            return;
        }

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

        HashMap<String, ArrayList<String>> usersTags = new HashMap<>();

        ArrayList<String> userLoginTags = tags.get(usernameLogin);

        for (String name : tags.keySet()) {
            if (!name.equals(usernameLogin)) {
                ArrayList<String> nameTags = tags.get(name);
                boolean match = false;

                for (String tag : userLoginTags) {
                    if (nameTags.contains(tag)) {
                        match = true;
                        break;
                    }
                }

                if (match) usersTags.put(name, nameTags);
            }
        }

        Type hashMapType = new TypeToken<HashMap<String, ArrayList<String>>>(){}.getType();
        response.println(gson.toJson(usersTags, hashMapType));
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

        Vector<String> listUsers = followings.get(usernameLogin);

        synchronized (listUsers) {
            response.println(gson.toJson(listUsers));
        }

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
            response.println("l'utente " + username + " non può essere seguito (nessun tag in comune)");
            response.flush();
            return;
        }

        // Controllo se l'utente segue già username
        Vector<String> listFollowing = followings.get(usernameLogin);

        if (listFollowing.contains(username)) {
            response.println(410);
            response.println("errore, segui già " + username);
            response.flush();
            return;
        }

        Vector<String> listFollowers = followers.get(username);

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
            response.println(200);
            response.println("Ora segui " + username + ", la notifica non è andata a buon fine");
            response.flush();
            return;
        }

        response.println(200);

        if (code != null && code.getCode() != 200) response.println("Ora segui " + username + ", la notifica non è andata a buon fine");
        else response.println("Ora segui " + username);

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

        // Controllo se l'utente non segue username
        Vector<String> listFollowing = followings.get(usernameLogin);

        if (!listFollowing.contains(username)) {
            response.println(411);
            response.println("errore, non segui " + username);
            response.flush();
            return;
        }

        Vector<String> listFollowers = followers.get(username);

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
            response.println(200);
            response.println("Ora non segui più " + username + ", la notifica non è andata a buon fine");
            response.flush();
            return;
        }

        response.println(200);

        if (code != null && code.getCode() != 200) response.println("Ora non segui più " + username + ", la notifica non è andata a buon fine");
        else response.println("Ora non segui più " + username);

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

        // Recupero la lista dei post
        Vector<Post> post = blogs.get(usernameLogin);

        synchronized (post) {
            response.println(post.size());
            response.flush();

            for (Post p : post) {
                response.println(gsonExpose.toJson(p, postType));
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
        Vector<String> listFollowing = followings.get(usernameLogin);
        Vector<Post> postsFollowed;

        synchronized (listFollowing) {
            // Invio il numero di following
            response.println(listFollowing.size());
            response.flush();

            for (String nameFollowed : listFollowing) {
                // Recupero il blog di ogni followed
                postsFollowed = blogs.get(nameFollowed);

                synchronized (postsFollowed) {
                    // Invio il numero di post all'interno del blog
                    response.println(postsFollowed.size());
                    response.flush();

                    for (Post p : postsFollowed) {
                        // Invio i post nel blog
                        response.println(gsonExpose.toJson(p, postType));
                        response.flush();
                    }
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
        // Inserisco il post nel blog dell'utente connesso, e nella lista di tutti i post
        Vector<Post> blog = blogs.get(usernameLogin);

        synchronized (blog) {
            synchronized (posts) {
                blog.add(newPost);
                posts.putIfAbsent(newPost.getId(), newPost);
            }
        }
        // ok
        response.println(200);
        response.println("post pubblicato (id = " + newPost.getId() + ")");
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

        response.println(201);
        response.println("invio il post");
        response.flush();

        // Invio il post
        response.println(gson.toJson(p, postType));
        response.flush();
    }
}
