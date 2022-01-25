// @Author Simone Passera

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

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
    private static Vector<String> listFollowers = null;
    // Oggetto remoto registrato dal server tramite callback
    private static NotifyFollowersInterface callback = null;
    // Tipo dell'oggetto restituito dai metodi remoti
    private static Type CodeReturnType;
    // Tipo post
    private static Type postType;
    // Tipo wallet
    private static Type walletType;
    // Oggetto gson
    private static Gson gson;
    // Oggetto gson che considera le annotazioni
    private static Gson gsonExpose;
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
            // Ricevo dal server ip e porta di multicast su cui ricevere aggiornamenti relativo calcolo delle ricompense
            InetAddress multicastAddress = null;

            try {
                multicastAddress = InetAddress.getByName(inResponse.readLine());
                if (!multicastAddress.isMulticastAddress()) throw new UnknownHostException();
            } catch (UnknownHostException e) {
                System.err.println("Indirizzo di multicast invalido");
                System.exit(1);
            }

            int multicastPort = 0;

            try {
                multicastPort = Integer.parseInt(inResponse.readLine());
            } catch (NumberFormatException e) {
                System.err.println("Porta di multicast invalida");
                System.exit(1);
            }
            // Flag calcolo ricompense avvenuto
            AtomicBoolean reward = new AtomicBoolean(false);
            // Avvio il thread che riceve la notifica relativa calcolo delle ricompense tramite il gruppo multicast
            WalletUpdate walletUpdate = new WalletUpdate(multicastAddress, multicastPort, reward);
            Thread threadWalletUpdate = new Thread(walletUpdate);
            threadWalletUpdate.start();
            // Scanner per il parsing da linea di comando
            Scanner input = new Scanner(System.in);
            String line;
            ArrayList<String> command = new ArrayList<>();
            // Pulisco il terminale
            System.out.print("\033[H\033[2J");
            System.out.flush();
            System.out.println("\033[1m*** WINSOME ***\033[22m");
            // Inizializzo variabili di supporto
            CodeReturnType = new TypeToken<CodeReturn>(){}.getType();
            postType = new TypeToken<Post>(){}.getType();
            walletType = new TypeToken<Wallet>(){}.getType();
            gson = new Gson();
            gsonExpose = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
            Pattern p = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");
            Matcher m;
            int id;
            // Leggo i comandi da stdin
            while (true) {
                // Controllo se è stato eseguito il calcolo delle ricompense
                if (reward.compareAndSet(true, false)) System.out.println("\033[1m< NOTIFICA : eseguito il calcolo delle ricompense (comando 'wallet' per info)\033[22m");
                System.out.flush();
                System.out.print("> ");

                while((line = input.nextLine()).length() == 0) {
                    if (reward.compareAndSet(true, false)) System.out.println("\033[1m< NOTIFICA : eseguito il calcolo delle ricompense (comando 'wallet' per info)\033[22m");
                    System.out.flush();
                    System.out.print("> ");
                }
                // Utilizzo le espressioni regolari per considerare un unico comando se compreso tra apici
                m = p.matcher(line);
                command.clear();
                while (m.find()) command.add(m.group(1).replace("\"", ""));
                // Eseguo la funzione che implementa il comando richiesto
                switch (command.get(0)) {
                    case "help": help(); break;
                    case "verbose":
                        if (verbose = !verbose) System.out.println("\033[1m<\033[22m abilitato");
                        else System.out.println("\033[1m<\033[22m disabilitato");
                        break;
                    case "register":
                        if (command.size() < 4 || command.size() > 8) { System.out.println("\033[1m<\033[22m \033[1mregister\033[22m <username [\033[1mmax 15\033[22m]> <password> <tags [\033[1mmax 5\033[22m]>"); break; }
                        // Lista dei tag
                        ArrayList<String> tags = new ArrayList<>();
                        for (int i = 3; i < command.size(); i++) tags.add(command.get(i));

                        register(command.get(1), command.get(2), tags);
                        break;
                    case "login":
                        if (command.size() < 3) { System.out.println("\033[1m<\033[22m \033[1mlogin\033[22m <username> <password>"); break; }
                        if (login(command.get(1), command.get(2)) == 0) {
                            listFollowers = new Vector<>();
                            // Creo l'oggetto callback usato dal server
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
                            // Se logout ha avuto successo, rimuovo l'utente dal servizio di callback
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
                        if (command.size() >= 2) cmd = command.get(1);

                        switch (cmd) {
                            case "users":
                                listUsers();
                                break;
                            case "followers":
                                listFollowers();
                                break;
                            case "following":
                                listFollowing();
                                break;
                            default:
                                System.out.println("\033[1m<\033[22m \033[1mlist users\033[22m");
                                System.out.println("\033[1m<\033[22m \033[1mlist followers\033[22m");
                                System.out.println("\033[1m<\033[22m \033[1mlist following\033[22m");
                        }

                        break;
                    case "follow":
                        if (command.size() < 2) { System.out.println("\033[1m<\033[22m \033[1mfollow\033[22m <username>"); break; }
                        followUser(command.get(1));
                        break;
                    case "unfollow":
                        if (command.size() < 2) { System.out.println("\033[1m<\033[22m \033[1munfollow\033[22m <username>"); break; }
                        unfollowUser(command.get(1));
                        break;
                    case "blog":
                        viewBlog();
                        break;
                    case "show":
                        if (command.size() >= 2) {
                            if (command.get(1).equals("feed")) { showFeed(); break; }
                            if (command.get(1).equals("post")) {
                                if (command.size() >= 3) {
                                    try {
                                        id = Integer.parseInt(command.get(2));
                                    } catch (NumberFormatException e) {
                                        System.out.println("\033[1m<\033[22m \033[1mshow post\033[22m <id>");
                                        break;
                                    }

                                    showPost(id);
                                    break;
                                } else {
                                    System.out.println("\033[1m<\033[22m \033[1mshow post\033[22m <id>");
                                    break;
                                }
                            }
                        }

                        System.out.println("\033[1m<\033[22m \033[1mshow feed\033[22m");
                        System.out.println("\033[1m<\033[22m \033[1mshow post\033[22m <id>");
                        break;
                    case "post":
                        if (command.size() < 3) { System.out.println("\033[1m<\033[22m \033[1mpost\033[22m <title [\033[1mmax 20\033[22m]> <content [\033[1mmax 500\033[22m]>"); break; }
                        createPost(command.get(1), command.get(2));
                        break;
                    case "delete":
                        if (command.size() < 2) { System.out.println("\033[1m<\033[22m \033[1mdelete\033[22m <id>"); break; }

                        try {
                            id = Integer.parseInt(command.get(1));
                        } catch (NumberFormatException e) {
                            System.out.println("\033[1m<\033[22m \033[1mdelete\033[22m <id>");
                            break;
                        }

                        deletePost(id);
                        break;
                    case "rewin":
                        if (command.size() < 2) { System.out.println("\033[1m<\033[22m \033[1mrewin\033[22m <id>"); break; }

                        try {
                            id = Integer.parseInt(command.get(1));
                        } catch (NumberFormatException e) {
                            System.out.println("\033[1m<\033[22m \033[1mrewin\033[22m <id>");
                            break;
                        }

                        rewinPost(id);
                        break;
                    case "rate":
                        if (command.size() < 3) { System.out.println("\033[1m<\033[22m \033[1mrate\033[22m <id> <vote [\033[1m-1, +1\033[22m]>"); break; }

                        try {
                            id = Integer.parseInt(command.get(1));
                        } catch (NumberFormatException e) {
                            System.out.println("\033[1m<\033[22m \033[1mrate\033[22m <id> <vote [\033[1m-1, +1\033[22m]>");
                            break;
                        }

                        if (command.get(2).equals("+1")) {
                            ratePost(id, 1);
                            break;
                        }

                        if (command.get(2).equals("-1")) {
                            ratePost(id, -1);
                            break;
                        }

                        System.out.println("\033[1m<\033[22m \033[1mrate\033[22m <id> <vote [\033[1m-1, +1\033[22m]>");
                        break;
                    case "comment":
                        if (command.size() < 3) { System.out.println("\033[1m<\033[22m \033[1mcomment\033[22m <id> <comment [\033[1mmax 500\033[22m]>"); break; }

                        try {
                            id = Integer.parseInt(command.get(1));
                        } catch (NumberFormatException e) {
                            System.out.println("\033[1m<\033[22m \033[1mcomment\033[22m <id> <comment [\033[1mmax 500\033[22m]>");
                            break;
                        }

                        addComment(id, command.get(2));
                        break;
                    case "wallet":
                        if (command.size() == 1) { wallet(); break; }
                        if (command.get(1).equals("btc")) walletBtc();
                        else System.out.println("\033[1m<\033[22m \033[1mwallet btc\033[22m");
                        break;
                    case "exit":
                        if (exit(walletUpdate, threadWalletUpdate) == 0) System.exit(0);
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

    // Legge il file di configurazione
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
            boolean error = false;

            for (int i = 0; i < 5; i++) {
                if (stringSet[i] == 0) {
                    switch (i) {
                        case 0: System.err.println("File di configurazione: SERVER_ADDRESS -> valore mancante"); break;
                        case 1: System.err.println("File di configurazione: PORT_TCP -> valore mancante"); break;
                        case 2: System.err.println("File di configurazione: TIMEOUT -> valore mancante"); break;
                        case 3: System.err.println("File di configurazione: REGISTRY_ADDRESS -> valore mancante"); break;
                        case 4: System.err.println("File di configurazione: REGISTRY_PORT -> valore mancante"); break;
                    }

                    error = true;
                }
            }

            if (error) System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("File di configurazione: " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException | IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static void help() {
        System.out.println("\033[1m<\033[22m Comandi:");
        System.out.println("\033[1m<\033[22m \033[1mregister\033[22m <username> <password> <tags>\033[50Ginserisce un nuovo utente, <tags> è una lista di tag separati da uno spazio, al massimo 5 tag.");
        System.out.println("\033[1m<\033[22m \033[1mlogin\033[22m <username> <password>\033[50Glogin di un utente già registrato per accedere al servizio.");
        System.out.println("\033[1m<\033[22m \033[1mlogout\033[22m\033[50Geffettua il logout dal servizio.");
        System.out.println("\033[1m<\033[22m \033[1mlist users\033[22m\033[50Gvisualizza la lista degli utenti registrati al servizio.");
        System.out.println("\033[1m<\033[22m \033[1mlist followers\033[22m\033[50Gvisualizza la lista dei propri follower.");
        System.out.println("\033[1m<\033[22m \033[1mlist following\033[22m\033[50Gvisualizza la lista degli utenti che segui.");
        System.out.println("\033[1m<\033[22m \033[1mfollow\033[22m <username>\033[50Gpermette di seguire un utente.");
        System.out.println("\033[1m<\033[22m \033[1munfollow\033[22m <username>\033[50Gpermette di non seguire più un utente.");
        System.out.println("\033[1m<\033[22m \033[1mpost\033[22m <title> <content>\033[50Gpermette di pubblicare un nuovo post.");
        System.out.println("\033[1m<\033[22m \033[1mdelete\033[22m <id>\033[50Goperazione per cancellare un post di cui si è autori.");
        System.out.println("\033[1m<\033[22m \033[1mblog\033[22m\033[50Gvisualizza la lista dei post nel proprio blog.");
        System.out.println("\033[1m<\033[22m \033[1mshow feed\033[22m\033[50Gmostra il proprio feed.");
        System.out.println("\033[1m<\033[22m \033[1mshow post\033[22m <id>\033[50Gmostra il contenuto del post, i voti positivi e negativi ed i relativi commenti.");
        System.out.println("\033[1m<\033[22m \033[1mrewin\033[22m <id>\033[50Gpermette di pubblicare nel proprio blog un post presente nel proprio feed.");
        System.out.println("\033[1m<\033[22m \033[1mrate\033[22m <id> <vote>\033[50Gpermette di assegnare un voto positivo o negativo ad un post. (voto positivo +1, negativo -1)");
        System.out.println("\033[1m<\033[22m \033[1mcomment\033[22m <id> <comment>\033[50Gpermette di aggiungere un commento ad un post.");
        System.out.println("\033[1m<\033[22m \033[1mwallet\033[22m\033[50Gmostra il valore del proprio portafoglio, e le relative transazioni.");
        System.out.println("\033[1m<\033[22m \033[1mwallet btc\033[22m\033[50Gvisualizza il valore del proprio portafoglio in bitcoin.");
        System.out.println("\033[1m<\033[22m \033[1mhelp\033[22m\033[50Gmostra questa lista.");
        System.out.println("\033[1m<\033[22m \033[1mverbose\033[22m\033[50Gabilita la stampa dei codici di risposta dal server.");
        System.out.println("\033[1m<\033[22m \033[1mexit\033[22m\033[50Gtermina il processo.");
    }

    private static int exit(WalletUpdate walletUpdate, Thread threadWalletUpdate) {
        outRequest.println("exit");
        outRequest.flush();

        try {
            if (printResponse() == 200) {
                // Termino il thread che riceve la notifica di avvenuto calcolo delle ricompense
                walletUpdate.exit();
                try {
                    threadWalletUpdate.join();
                } catch (InterruptedException ignore) {}
                // Successo
                return 0;
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
        // Errore
        return 1;
    }

    private static void register(String username, String password, ArrayList<String> tags) {
        // Ottengo la password criptata con SHA-256
        String hexPass;

        try {
            hexPass = Hash.encrypt(password);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Eseguo il metodo register invocato sull'oggetto remoto del server
        CodeReturn code;

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
        // Ottengo la password criptata con SHA-256
        String hexPass;

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
            // Utente connesso
            return 0;
        } else return 1; // Errore
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
            // Eseguito il logout
            return 0;
        } else return 1; // Errore
    }

    private static void listUsers() {
        outRequest.println("listUsers");
        outRequest.flush();

        int code;
        String message;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Mi aspetto di ricevere cod. 201 (richiesta accettata ma non ancora terminata)
        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
            // Server in chiusura
            if (code == 502) System.exit(0);
        } else {
            HashMap<String, ArrayList<String>> listUsersTags;
            Type hashMapType = new TypeToken<HashMap<String, ArrayList<String>>>(){}.getType();
            // Ottengo la mappa (utente, lista di tag)
            try {
                listUsersTags = gson.fromJson(inResponse.readLine(), hashMapType);
            } catch (IOException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }
            // Controllo se è vuota
            if (listUsersTags.isEmpty()) {
                System.out.println("\033[1m<\033[22m non ci sono utenti con almeno un tag in comune!");
            } else {
                System.out.println("\033[1m<\033[22m         Utente\033[24G|       Tag");
                System.out.println("\033[1m<\033[22m ------------------------------------------------------");

                ArrayList<String> listTags;
                // Stampo utente | tag1, tag2, tag3, tag4, tag5
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
        // Controllo se c'è un utente connesso e quindi registrato al servizio di callback
        if (usernameConnected == null) {
            System.out.println("\033[1m<\033[22m errore, nessun utente connesso");
        } else {
            if (listFollowers.isEmpty()) {
                System.out.println("\033[1m<\033[22m non hai nessun follower");
            } else {
                System.out.println("\033[1m<\033[22m         Followers        ");
                System.out.println("\033[1m<\033[22m -------------------------");
                // Stampo tutti i nomi dei followers
                synchronized (listFollowers) {
                    for (String name : listFollowers) {
                        System.out.println("\033[1m<\033[22m    " + name);
                    }
                }
            }
        }
    }

    private static void listFollowing() {
        outRequest.println("listFollowing");
        outRequest.flush();

        int code;
        String message;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Mi aspetto di ricevere cod. 201 (richiesta accettata ma non ancora terminata)
        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
            // Server in chiusura
            if (code == 502) System.exit(0);
        } else {
            ArrayList<String> listUsers;
            Type arrayListType = new TypeToken<ArrayList<String>>(){}.getType();
            // Ottengo la lista dei nomi
            try {
                listUsers = gson.fromJson(inResponse.readLine(), arrayListType);
            } catch (IOException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }

            if (listUsers.isEmpty()) {
                System.out.println("\033[1m<\033[22m non segui nessun utente");
            } else {
                System.out.println("\033[1m<\033[22m         Utente");
                System.out.println("\033[1m<\033[22m ----------------------");
                // Stampo tutti gli utenti che la persona connessa segue
                for (String name : listUsers) {
                    System.out.println("\033[1m<\033[22m    " + name);
                }
            }
        }
    }

    private static void followUser(String username) {
        outRequest.println("follow");
        outRequest.println(username);
        outRequest.flush();

        try {
            printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void unfollowUser(String username) {
        outRequest.println("unfollow");
        outRequest.println(username);
        outRequest.flush();

        try {
            printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void viewBlog() {
        outRequest.println("blog");
        outRequest.flush();

        int code;
        String message;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Mi aspetto di ricevere cod. 201 (richiesta accettata ma non ancora terminata)
        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
            // Server in chiusura
            if (code == 502) System.exit(0);
        } else {
            int size;
            // Ottengo il numero dei post all'interno del blog
            try {
                size = Integer.parseInt(inResponse.readLine());
            } catch (IOException | NumberFormatException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }
            // Controllo se non ci sono post
            if (size == 0) {
                System.out.println("\033[1m<\033[22m blog vuoto");
                return;
            }
            // Stampo tutti i post nel blog
            Post post;

            System.out.println("\033[1m<\033[22m         Id\033[21G|        Autore\033[43G|        Titolo");
            System.out.println("\033[1m<\033[22m -----------------------------------------------------------------");

            try {
                for(int i = 0; i < size; i++) {
                    post = gsonExpose.fromJson(inResponse.readLine(), postType);
                    System.out.println("\033[1m<\033[22m         " + post.getId() + "\033[21G|   " + post.getAuthor() + "\033[43G|   " + post.getTitle());
                }
            } catch (IOException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            }
        }
    }

    private static void showFeed() {
        outRequest.println("showFeed");
        outRequest.flush();

        int code;
        String message;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Mi aspetto di ricevere cod. 201 (richiesta accettata ma non ancora terminata)
        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
            // Server in chiusura
            if (code == 502) System.exit(0);
        } else {
            int numFollowing;
            // Ottengo il numero delle persone che la persona connessa segue
            try {
                numFollowing = Integer.parseInt(inResponse.readLine());
            } catch (IOException | NumberFormatException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }
            // Controllo se non segue nessuno
            if (numFollowing == 0) {
                System.out.println("\033[1m<\033[22m feed vuoto");
                return;
            }
            Post post;

            int numPosts;
            boolean firstPost = true;
            // Stampo tutti i post per ogni utente che segue
            for(int i = 0; i < numFollowing; i++) {
                try {
                    // Ottengo il numero dei post all'interno del blog di un utente che segue
                    numPosts = Integer.parseInt(inResponse.readLine());

                    if (numPosts != 0 && firstPost) {
                        System.out.println("\033[1m<\033[22m         Id\033[21G|        Autore\033[43G|        Titolo");
                        System.out.println("\033[1m<\033[22m -----------------------------------------------------------------");
                        firstPost = false;
                    }
                    // Stampo i post nel blog
                    for (int k = 0; k < numPosts; k++) {
                        post = gsonExpose.fromJson(inResponse.readLine(), postType);
                        System.out.println("\033[1m<\033[22m         " + post.getId() + "\033[21G|   " + post.getAuthor() + "\033[43G|   " + post.getTitle());
                    }
                } catch (IOException | NumberFormatException e) {
                    System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                    return;
                }
            }
            // Se l'utente segue almeno una persona, ma non ci sono
            // post nei blog degli utenti seguiti il feed è vuoto
            if (firstPost) System.out.println("\033[1m<\033[22m feed vuoto");
        }
    }

    private static void createPost(String title, String content) {
        outRequest.println("createPost");
        outRequest.println(title);
        outRequest.println(content);
        outRequest.flush();

        try {
            printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void deletePost(int id) {
        outRequest.println("deletePost");
        outRequest.println(id);
        outRequest.flush();

        try {
            printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void showPost(int id) {
        outRequest.println("showPost");
        outRequest.println(id);
        outRequest.flush();

        int code;
        String message;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Mi aspetto di ricevere cod. 201 (richiesta accettata ma non ancora terminata)
        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
            // Server in chiusura
            if (code == 502) System.exit(0);
        } else {
            Post post;
            // Ottengo il post completo
            try {
                post = gson.fromJson(inResponse.readLine(), postType);
            } catch (IOException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }
            // Stampo il post
            System.out.println("\033[1m<\033[22m Autore: " + post.getAuthor());
            System.out.println("\033[1m<\033[22m Titolo: " + post.getTitle());
            System.out.println("\033[1m<\033[22m Contenuto: " + post.getText());
            System.out.println("\033[1m<\033[22m Voti: " + post.getUpvote() + " positivi, " + post.getDownVote() + " negativi");

            ArrayList<String> comments = post.getComments();

            System.out.print("\033[1m<\033[22m Commenti:");

            if (comments.size() == 0)  System.out.println(" 0");
            else {
                System.out.println();
                int index;
                for (String comment : comments) {
                    index = comment.indexOf(" ");
                    System.out.println("\033[1m<\033[22m   " + comment.substring(0, index) + ":" + comment.substring(index));
                }
            }
        }
    }

    private static void rewinPost(int id) {
        outRequest.println("rewinPost");
        outRequest.println(id);
        outRequest.flush();

        try {
            printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void ratePost(int id, int vote) {
        outRequest.println("ratePost");
        outRequest.println(id);
        outRequest.println(vote);
        outRequest.flush();

        try {
            printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void addComment(int id, String comment) {
        outRequest.println("addComment");
        outRequest.println(id);
        outRequest.println(comment);
        outRequest.flush();

        try {
            printResponse();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
        }
    }

    private static void wallet() {
        outRequest.println("getWallet");
        outRequest.flush();

        int code;
        String message;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Mi aspetto di ricevere cod. 201 (richiesta accettata ma non ancora terminata)
        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
            // Server in chiusura
            if (code == 502) System.exit(0);
        } else {
            Wallet wallet;
            // Recupero il portafoglio
            try {
                wallet = gson.fromJson(inResponse.readLine(), walletType);
            } catch (IOException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }
            // Stampo valore e transazioni
            System.out.println("\033[1m<\033[22m Valore: " + wallet.getWincoin() + " Wincoin");
            System.out.print("\033[1m<\033[22m Transazioni:");

            ArrayList<String> transactions = wallet.getTransactions();

            if (transactions.size() == 0)  System.out.println(" 0");
            else {
                System.out.println();
                for (String activity : transactions) {
                    System.out.println("\033[1m<\033[22m   " + activity);
                }
            }
        }
    }

    private static void walletBtc() {
        outRequest.println("getWalletBtc");
        outRequest.flush();

        int code;
        String message;

        try {
            code = Integer.parseInt(inResponse.readLine());
            message = inResponse.readLine();
        } catch (IOException | NumberFormatException e) {
            System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
            return;
        }
        // Mi aspetto di ricevere cod. 201 (richiesta accettata ma non ancora terminata)
        if (code != 201){
            if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
            else System.out.println("\033[1m<\033[22m " + message);
            // Server in chiusura
            if (code == 502) System.exit(0);
        } else {
            double bitcoin;
            // Recupero il valore convertito in bitcoin
            try {
                bitcoin = Double.parseDouble(inResponse.readLine());
            } catch (IOException | NumberFormatException e) {
                System.err.println("\033[1m<\033[22m errore: " + e.getMessage());
                return;
            }
            // Stampo il valore
            System.out.println("\033[1m<\033[22m Valore: " + bitcoin + " BTC");
        }
    }

    // Funzione per leggere dal server codice e messaggio di risposta
    // @Return codice di risposta
    private static int printResponse() throws NumberFormatException, IOException {
        int code;
        String message;

        code = Integer.parseInt(inResponse.readLine());
        message = inResponse.readLine();

        if (verbose) System.out.println("\033[1m<\033[22m [\033[1m" + code + "\033[22m] " + message);
        else System.out.println("\033[1m<\033[22m " + message);

        System.out.flush();

        // Server in chiusura
        if (code == 502) System.exit(0);

        return code;
    }
}
