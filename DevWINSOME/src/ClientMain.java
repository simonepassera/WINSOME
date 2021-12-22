// @Author Simone Passera

import java.io.*;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.security.*;
import java.util.*;

public class ClientMain {
    // Indirizzo del server WINSOME
    private static String SERVER_ADDRESS;
    // Numero di porta del server WINSOME
    private static int PORT_TCP;
    // Tempo massimo per aprire la connessione con il server WINSOME (ms)
    private static int TIMEOUT = 15000;
    // Indirizzo del registry
    private static String REGISTRY_ADDRESS;
    // Porta del registry
    private static int REGISTRY_PORT;
    // Oggetto remoto del server WINSOME
    private static WinsomeRMIServices winsomeRMI = null;
    // Settata dal comando [verbose]
    private static boolean verbose = false;
    // Stringa che contiene lo username se l'utente ha effettuato il login, oppure null in caso contrario
    private static String usernameLogin = null;

    public static void main(String[] args) {
        // Controllo se esiste il file di configurazione
        if (args.length == 0) {
            System.err.println("Usage: java ClientMain <config file>");
            System.exit(1);
        }

        // Leggo il file di configurazione
        readConf(args[0]);

        // Provo a risolvere l'indirizzo del server WINSOME
        InetAddress address = null;

        try {
            address = InetAddress.getByName(SERVER_ADDRESS);
        } catch (UnknownHostException e) {
            System.err.println("Non riesco a risolvere l'indirizzo del server (" + SERVER_ADDRESS + ")");
            System.exit(1);
        }

        // Ottengo un riferimento al registry
        Registry registry = null;

        try {
            registry = LocateRegistry.getRegistry(REGISTRY_ADDRESS, REGISTRY_PORT);
        } catch (RemoteException e) {
            System.err.println("Non riesco ad ottenere il riferimento al registry (" + REGISTRY_ADDRESS + ":" + REGISTRY_PORT + ")");
            System.exit(1);
        }

        // Creo una instanza dell'oggetto remoto
        try {
            winsomeRMI = (WinsomeRMIServices) registry.lookup("WINSOME");
        } catch (RemoteException | NotBoundException e) {
            System.err.println("Non riesco ad ottenere il riferimento all'oggetto remoto: " + e.getMessage());
            System.exit(1);
        }

        // Creo il socket tcp per la connessione
        try (Socket winsomeServer = new Socket()) {
            winsomeServer.bind(new InetSocketAddress(InetAddress.getLocalHost(), 0));
            InetSocketAddress socketAddress = new InetSocketAddress(address, PORT_TCP);
            System.out.println("Connessione con il server WINSOME ...");
            // Apro la connessione con il server WINSOME
            winsomeServer.connect(socketAddress, TIMEOUT);

            Scanner input = new Scanner(System.in);
            String line;
            String[] command;

            // Pulisco il terminale
            System.out.print("\033[H\033[2J");
            System.out.flush();

            System.out.print("\033[1m*** WINSOME ***\033[22m\n> ");

            // Leggo i comandi
            while (!(line = input.nextLine()).equals("exit")) {
                command = line.split(" ");

                switch (command[0]) {
                    case "help": help(); break;
                    case "verbose": verbose = !verbose; break;
                    case "register":
                        if (command.length < 4 || command.length > 8) { System.out.println("\033[1m<\033[22m \033[1mregister\033[22m <username> <password> <tags [\033[1mmax 5\033[22m]>"); break; }

                        ArrayList<String> tags = new ArrayList<>();
                        for (int i = 3; i < command.length; i++) tags.add(command[i]);

                        System.out.println(command[1]);

                        register(command[1], command[2], tags);
                        break;
                    case "login":
                        if (command.length != 3) { System.out.println("\033[1m<\033[22m \033[1mlogin\033[22m <username> <password>"); break; }
                        if (usernameLogin != null) {
                            if (usernameLogin.equals(command[1])) System.out.println("\033[1m<\033[22m C'è un utente già collegato, deve essere prima scollegato)");
                            else System.out.println("\033[1m<\033[22m " + usernameLogin + "già collegato");
                            break;
                        }

                        login(command[1], command[2], winsomeServer);
                        break;
                    default: System.out.println("\033[1m<\033[22m Comando non trovato (Prova 'help' per maggiori informazioni)");
                }

                System.out.flush();
                System.out.print("> ");
            }

            System.out.println("\033[1m<\033[22m Arrivederci :)");
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout per la connessione con il server WINSOME");
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static void readConf(String path) {
        try (FileReader fileReader = new FileReader(path);
             BufferedReader configFile = new BufferedReader(fileReader)) {

            String line;
            String[] values;
            // Array per ricordare le stringhe di configurazione incontrate
            int[] stringSet = new int[5];

            // Leggo una linea
            while ((line = configFile.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;

                values = line.split("=");

                // Controllo se ho trovato esattamente due stringhe separate da '='
                if (values.length != 2) {
                    System.err.println("File di configurazione: errore di sintassi");
                    System.exit(1);
                }

                switch (values[0].trim()) {
                    case "SERVER_ADDRESS":
                        SERVER_ADDRESS = values[1].trim();

                        if (SERVER_ADDRESS.length() == 0) {
                            System.err.println("File di configurazione: SERVER_ADDRESS -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[0] = 1;
                        break;
                    case "PORT_TCP":
                        try {
                            PORT_TCP = Integer.parseInt(values[1].trim());
                            if (PORT_TCP < 0 || PORT_TCP > 65535) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: PORT_TCP -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[1] = 1;
                        break;
                    case "TIMEOUT":
                        try {
                            TIMEOUT = Integer.parseInt(values[1].trim());
                            if (TIMEOUT < 0) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: TIMEOUT -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[2] = 1;
                        break;
                    case "REGISTRY_ADDRESS":
                        REGISTRY_ADDRESS = values[1].trim();

                        if (REGISTRY_ADDRESS.length() == 0) {
                            System.err.println("File di configurazione: REGISTRY_ADDRESS -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[3] = 1;
                        break;
                    case "REGISTRY_PORT":
                        try {
                            REGISTRY_PORT = Integer.parseInt(values[1].trim());
                            if (REGISTRY_PORT < 0 || REGISTRY_PORT > 65535) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: REGISTRY_PORT -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[4] = 1;
                        break;
                }
            }

            // Controllo di aver trovato tutte le stringhe di configurazione
            for (int val : stringSet) {
                if (val == 0) {
                    System.err.println("File di configurazione: file incompleto!");
                    System.exit(1);
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File di configurazione: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static void help() {
        System.out.println("\033[1m<\033[22m Comandi:");
        System.out.println("\033[1m<\033[22m \033[1mregister\033[22m <username> <password> <tags>\033[50Ginserisce un nuovo utente, <tags> è una lista di tag separati da uno spazio, al massimo 5 tag.");
        System.out.println("\033[1m<\033[22m \033[1mlogin\033[22m <username> <password>\033[50Glogin di un utente già registrato per accedere al servizio.");
        System.out.println("\033[1m<\033[22m \033[1mlogout\033[22m \033[50Geffettua il logout dal servizio.");
        System.out.println("\033[1m<\033[22m \033[1mhelp\033[22m \033[50Gmostra la lista dei comandi.");
        System.out.println("\033[1m<\033[22m \033[1mverbose\033[22m \033[50Gabilita la stampa dei codici di risposta dal server.");
        System.out.println("\033[1m<\033[22m \033[1mexit\033[22m \033[50Gtermina il processo.");
    }

    private static String encrypt(String password) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

        messageDigest.update(password.getBytes());

        byte[] digest = messageDigest.digest();

        StringBuilder hexString = new StringBuilder();

        for (byte b : digest) hexString.append(Integer.toHexString(Byte.toUnsignedInt(b)));

        return hexString.toString();
    }

    private static void register(String username, String password, ArrayList<String> tags) {
        String hexPass = null;

        try {
            hexPass = encrypt(password);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }

        int code = 0;

        try {
            code = winsomeRMI.register(username, hexPass, tags);
        } catch (RemoteException | NullPointerException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }

        switch (code) {
            case 0: System.out.println("\033[1m<\033[22m ok"); break;
            case 1: System.out.println("\033[1m<\033[22m errore, username vuoto"); break;
            case 2: System.out.println("\033[1m<\033[22m errore, utente " + username + " già esistente"); break;
            case 3: System.out.println("\033[1m<\033[22m errore, password vuota"); break;
            case 4: System.out.println("\033[1m<\033[22m errore, lista di tag vuota"); break;
            case 5: System.out.println("\033[1m<\033[22m errore, lista di tag troppo grande [max 5]"); break;
            case 6: System.out.println("\033[1m<\033[22m errore, lista di tag contiene valori vuoti"); break;
        }
    }

    private static void login(String username, String password, Socket server) {
        String hexPass = null;

        try {
            hexPass = encrypt(password);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }

        try (PrintWriter request = new PrintWriter(server.getOutputStream());
             BufferedReader response = new BufferedReader(new InputStreamReader(server.getInputStream()))) {

            request.println(100);
            request.println(username);
            request.println(password);
            request.flush();

            int code = Integer.parseInt(response.readLine());

            String message = response.readLine();

            if (code == 0) usernameLogin = username;

            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }
}
