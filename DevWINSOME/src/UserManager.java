// @Author Simone Passera

import java.io.*;
import java.net.Socket;

public class UserManager implements Runnable {
    private Socket user;

    public UserManager(Socket user) {
        this.user = user;
    }

    @Override
    public void run() {
        while (true) {
            try (PrintWriter response = new PrintWriter(user.getOutputStream());
                 BufferedReader request = new BufferedReader(new InputStreamReader(user.getInputStream()))) {

                switch (request.readLine()) {
                    case 100:
                        login();
                        break;
                }
            } catch (IOException e) {

            }
        }
    }

    private void login(String username, String password, Writer client) {

        System.out.println("\033[1m<\033[22m " + username + " logged in");

        System.out.println("\033[1m<\033[22m errore, username vuoto");

        System.out.println("\033[1m<\033[22m errore, utente " + username + " non esistente");

        System.out.println("\033[1m<\033[22m errore, password vuota");

        System.out.println("\033[1m<\033[22m errore, password non corretta");
    }
}
