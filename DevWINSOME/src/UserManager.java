// @Author Simone Passera

import java.io.*;
import java.net.Socket;

public class UserManager implements Runnable {
    // Socket per la connessione persistente con il client
    private final Socket user;
    // Stringa che contiene lo username se l'utente ha effettuato il login, oppure null in caso contrario
    private String usernameLogin = null;

    public UserManager(Socket user) {
        this.user = user;
    }

    @Override
    public void run() {
        try (PrintWriter response = new PrintWriter(user.getOutputStream());
             BufferedReader request = new BufferedReader(new InputStreamReader(user.getInputStream()))) {
            while (true) {
                switch (request.readLine()) {
                    case "login":
                        String username =  request.readLine();
                        String password = request.readLine();
                        login(username, password, response);
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Errore (" + Thread.currentThread().getName() + "): " + e.getMessage());
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

        if (ServerMain.users.containsKey(username)) {
            if (!ServerMain.users.get(username).equals(password)) {
                response.println(406);
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
}
