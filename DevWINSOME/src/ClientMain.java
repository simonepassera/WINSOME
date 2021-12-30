// @Author Simone Passera

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
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
    // Stream di output verso il server
    private static PrintWriter outRequest = null;
    // Stream su cui ricevere dati dal server
    private static BufferedReader inResponse = null;
    // Settata dal comando [verbose]
    private static boolean verbose = false;
    // Lista dei followers aggiornata dal server con RMI callback
    private static ArrayList<String> listFollowers = null;
    // Oggetto remoto registrato dal server tramite callback
    private static NotifyFollowersInterface callback = null;
    // Tipo dell'oggetto restituito dai metodi remoti
    private static Type CodeReturnType;
    // Oggetto gson
    private static Gson gson;
    // Username connesso
    private static String usernameConnected = null;

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

        // Creo una istanza dell'oggetto remoto
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
            // Apro la connessione con il server WINSOME con timeout
            winsomeServer.connect(socketAddress, TIMEOUT);
            // Inizializzo gli stream di comunicazione
            outRequest = new PrintWriter(winsomeServer.getOutputStream());
            inResponse = new BufferedReader(new InputStreamReader(winsomeServer.getInputStream()));
            // Scanner per il parsing da linea di comando
            Scanner input = new Scanner(System.in);
            String line;
            String[] command;
            // Pulisco il terminale
            System.out.print("\033[H\033[2J");
            System.out.flush();
            System.out.println("\033[1m*** WINSOME ***\033[22m");
            // Leggo i comandi
            CodeReturnType = new TypeToken<CodeReturn>(){}.getType();
            gson = new Gson();

            while (true) {
                System.out.flush();
                System.out.print("> ");

                line = input.nextLine();
                command = line.split(" ");

                switch (command[0]) {
                    case "help": help(); break;
                    case "verbose":
                        if (verbose = !verbose) System.out.println("\033[1m<\033[22m abilitato");
                        else System.out.println("\033[1m<\033[22m disabilitato");
                        break;
                    case "register":
                        if (command.length < 4 || command.length > 8) { System.out.println("\033[1m<\033[22m \033[1mregister\033[22m <username [\033[1mmax 15\033[22m]> <password> <tags [\033[1mmax 5\033[22m]>"); break; }

                        ArrayList<String> tags = new ArrayList<>();
                        for (int i = 3; i < command.length; i++) tags.add(command[i]);

                        register(command[1], command[2], tags);
                        break;
                    case "login":
                        if (command.length < 3) { System.out.println("\033[1m<\033[22m \033[1mlogin\033[22m <username> <password>"); break; }
                        if (login(command[1], command[2]) == 0) {
                            listFollowers = new ArrayList<>();
                            // Creo l'oggetto usato dal server
                            try {
                                callback = new NotifyFollowers(listFollowers);
                            } catch (RemoteException e) {
                                System.err.println("Non riesco a creare l'oggetto remoto per il servizio di callback: " + e.getMessage());
                                System.exit(1);
                            }
                            // Registro il client al servizio di callback
                            CodeReturn code = null;

                            try {
                                code = gson.fromJson(winsomeRMI.registerListFollowers(usernameConnected, callback), CodeReturnType);
                            } catch (RemoteException e) {
                                System.err.println("Non riesco a registrarmi al servizio di callback: " + e.getMessage());
                                System.exit(1);
                            }

                            if (code.getCode() != 200) {
                                System.err.println("Errore di registrazione al servizio di callback [" + code.getCode() + "]: " + code.getMessage());
                                System.exit(1);
                            }
                        }

                        break;
                    case "logout":
                        if (logout() == 0) {
                            CodeReturn code = null;

                            try {
                                code = gson.fromJson(winsomeRMI.unregisterListFollowers(usernameConnected), CodeReturnType);
                            } catch (RemoteException e) {
                                System.err.println("Errore: " + e.getMessage());
                                System.exit(1);
                            }

                            if (code.getCode() != 200) {
                                if (verbose) System.err.println("Errore durante la disconnessione dalla callback [" + code.getCode() + "]: " + code.getMessage());
                                System.exit(1);
                            }

                            usernameConnected = null;
                            callback = null;
                            listFollowers = null;
                        }
                        break;
                    case "list":
                        String cmd = "error";
                        if (command.length >= 2) cmd = command[1];

                        switch (cmd) {
                            case "users":
                                listUsers();
                                break;
                            case "followers":
                                listFollowers();
                                break;
                            case "following":
                                break;
                            default:
                                System.out.println("\033[1m<\033[22m \033[1mlist users\033[22m");
                                System.out.println("\033[1m<\033[22m \033[1mlist followers\033[22m");
                                System.out.println("\033[1m<\033[22m \033[1mlist following\033[22m");
                        }

                        break;
                    case "exit":
                        exit();
                        break;
                    default: System.out.println("\033[1m<\033[22m comando non trovato (Prova 'help' per maggiori informazioni)");
                }
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout per la connessione con il server WINSOME");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
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
        System.out.println("\033[1m<\033[22m \033[1mlist users\033[22m \033[50Gvisualizza la lista degli utenti registrati al servizio.");
        System.out.println("\033[1m<\033[22m \033[1mlist followers\033[22m \033[50Gvisualizza la lista dei propri follower.");
        System.out.println("\033[1m<\033[22m \033[1mhelp\033[22m \033[50Gmostra la lista dei comandi.");
        System.out.println("\033[1m<\033[22m \033[1mverbose\033[22m \033[50Gabilita la stampa dei codici di risposta dal server.");
        System.out.println("\033[1m<\033[22m \033[1mexit\033[22m \033[50Gtermina il processo.");
    }

    private static void exit() {
        outRequest.println("exit");
        outRequest.flush();

        try {
            if (printResponse() == 200) {
                System.exit(0);
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void register(String username, String password, ArrayList<String> tags) {
        String hexPass = null;

        try {
            hexPass = Hash.encrypt(password);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }

        CodeReturn code = null;

        try {
            code = gson.fromJson(winsomeRMI.register(username, hexPass, tags), CodeReturnType);
        } catch (RemoteException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }

        if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code.getCode() + "\033[22m] " + code.getMessage());
        else System.out.println("\033[1m<\033[22m " + code.getMessage());
    }

    private static int login(String username, String password) {
        String hexPass = null;

        try {
            hexPass = Hash.encrypt(password);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return 1;
        }

        outRequest.println("login");
        outRequest.println(username);
        outRequest.println(hexPass);
        outRequest.flush();

        int code = 0;

        try {
            code = printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }

        if (code == 200) {
            usernameConnected = username;
            return 0;
        } else return 1;
    }

    private static int logout() {
        outRequest.println("logout");
        outRequest.flush();

        int code = 0;

        try {
            code = printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }

        if (code == 200) {
            return 0;
        } else return 1;
    }

    private static void listUsers() {
        outRequest.println("listUsers");
        outRequest.flush();

        int code = 0;
        String message = null;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }

        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
        } else {
            HashMap<String, ArrayList<String>> listUsersTags = null;
            Type hashMapType = new TypeToken<HashMap<String, ArrayList<String>>>(){}.getType();

            try {
                listUsersTags = gson.fromJson(inResponse.readLine(), hashMapType);
            } catch (IOException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }

            if (listUsersTags.isEmpty()) {
                System.out.println("\033[1m<\033[22m non ci sono utenti con almeno un tag in comune!");
            } else {
                System.out.println("\033[1m<\033[22m         Utente\033[24G|       Tag");
                System.out.println("\033[1m<\033[22m ------------------------------------------------------");

                ArrayList<String> listTags;

                for (String name : listUsersTags.keySet()) {
                    System.out.print("\033[1m<\033[22m    " + name);
                    System.out.print("\033[24G|   ");

                    listTags = listUsersTags.get(name);

                    for (int i = 0; i < listTags.size(); i++) {
                        if (i == (listTags.size() - 1)) System.out.println(listTags.get(i));
                        else System.out.print(listTags.get(i) + ", ");
                    }
                }
            }
        }
    }

    private static void listFollowers() {
        if (listFollowers == null) {
            System.out.println("\033[1m<\033[22m errore, nessun utente connesso");
        } else {
            if (listFollowers.isEmpty()) {
                System.out.println("\033[1m<\033[22m non ci sono utenti con almeno un tag in comune!");
            } else {
                System.out.println("\033[1m<\033[22m         Followers        ");
                System.out.println("\033[1m<\033[22m -------------------------");

                synchronized (listFollowers) {
                    for (String name : listFollowers) {
                        System.out.println("\033[1m<\033[22m    " + name);
                    }
                }
            }
        }
    }

    private static int printResponse() throws NumberFormatException, IOException {
        int code;
        String message;

        code = Integer.parseInt(inResponse.readLine());
        message = inResponse.readLine();

        if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
        else System.out.println("\033[1m<\033[22m " + message);

        return code;
    }
}
