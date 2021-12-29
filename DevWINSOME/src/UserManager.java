// @Author Simone Passera

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager implements Runnable {
    // Socket per la connessione persistente con il client
    private final Socket user;
    // Stringa che contiene lo username se l'utente ha effettuato il login, oppure null in caso contrario
    private String usernameLogin = null;
    // Mappa (username, password)
    private final ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private final ConcurrentHashMap<String, ArrayList<String>> tags;
    // Variabile di terminazione
    private boolean exit = true;

    public UserManager(Socket user, ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags) {
        this.user = user;
        this.users = users;
        this.tags = tags;
    }

    @Override
    public void run() {
        try (PrintWriter response = new PrintWriter(user.getOutputStream());
             BufferedReader request = new BufferedReader(new InputStreamReader(user.getInputStream()))) {
            String command;

            while (exit) {
                command = request.readLine();
                if (command == null) return;

                switch (command) {
                    case "exit":
                        exit(response);
                        break;
                    case "login":
                        String username =  request.readLine();
                        String password = request.readLine();
                        login(username, password, response);
                        break;
                    case "logout":
                        logout(response);
                        break;
                    case "listUsers":
                        listUsers(response);
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

        try { user.close(); } catch (IOException e) {}
        exit = false;
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

            usernameLogin = username;

            response.println(200);
            response.println(username + " logged in");
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

        Gson gson = new Gson();

        response.println(gson.toJson(usersTags));
        response.flush();
    }
}
