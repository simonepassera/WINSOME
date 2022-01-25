// @Author Simone Passera

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerMain {
    // Numero di porta del server WINSOME
    private static int PORT_TCP;
    // Indirizzo e porta di multicast a cui inviare la notifica di avvenuto calcolo delle ricompense
    private static InetAddress MULTICAST_ADDRESS;
    private static int MULTICAST_PORT;
    // Porta del registry
    private static int REGISTRY_PORT;
    // Tempo massimo per attendere la terminazione di tutti i client (secondi)
    private static int TERMINATION_TIMEOUT;
    // Percentuale ricompensa autore
    private static int REWARD_AUTHOR;
    // Stabilisce ogni quanti secondi eseguire il calcolo delle ricompense
    private static int REWARD_TIMER;
    // Stabilisce ogni quanti secondi salvare i dati
    private static int SAVE_TIMER;
    // Percorso del file di salvataggio
    private static String DATA_PATH;
    // Mappa (username, password)
    private static ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private static ConcurrentHashMap<String, ArrayList<String>> tags;
    // Mappa (username, stub_callback)
    private static ConcurrentHashMap<String, NotifyFollowersInterface> stubs;
    // Mappa (username, followers)
    private static ConcurrentHashMap<String, ArrayList<String>> followers;
    // Mappa (username, following)
    private static ConcurrentHashMap<String, ArrayList<String>> followings;
    // Mappa (username, blog)
    private static ConcurrentHashMap<String, Vector<Post>> blogs;
    // Mappa (idPost, post)
    private static ConcurrentHashMap<Integer, Post> posts;
    // Mappa (username, wallet)
    private static ConcurrentHashMap<String, Wallet> wallets;
    // Lista delle interazioni dall'ultimo calcolo delle ricompense
    private static ListInteractions listInteractions;
    // Lista degli utenti connessi
    private static Vector<String> connectedUsers;
    // Generatore id per un post
    private static AtomicInteger idGenerator;

    public static void main(String[] args) {
        // Controllo se esiste il file di configurazione
        if (args.length == 0) {
            System.err.println("Usage: java ServerMain <config file>");
            System.exit(1);
        }

        // Leggo il file di configurazione
        readConf(args[0]);
        // Leggo il file di salvataggio se esiste e inizializzo le strutture dati
        readData(DATA_PATH);
        // Creo il registry
        Registry registry = null;

        try {
            registry = LocateRegistry.createRegistry(REGISTRY_PORT);
        } catch (RemoteException e) {
            System.err.println("Creazione del registry: " + e.getMessage());
            System.exit(1);
        }
        // Creo ed esporto l'oggetto remoto
        WinsomeRMIServices rmiServices = null;

        try {
            rmiServices = new WinsomeRMI(users, tags, stubs, followers, followings, blogs, wallets);
        } catch (RemoteException e) {
            System.err.println("Creazione dell'oggeto remoto: " + e.getMessage());
            System.exit(1);
        }
        // Registro l'oggetto remoto nel registry
        try {
            registry.rebind("WINSOME", rmiServices);
        } catch (RemoteException e) {
            System.err.println("Registrazione dell' oggetto remoto nel registry: " + e.getMessage());
            System.exit(1);
        }
        // Avvio il thread per il calcolo delle ricompense
        RewardManager rewardManager = new RewardManager(MULTICAST_ADDRESS, MULTICAST_PORT, REWARD_TIMER, listInteractions, wallets, REWARD_AUTHOR);
        Thread threadRewardManager = new Thread(rewardManager);
        threadRewardManager.start();
        // Avvio il thread per salvare i dati
        DataManager dataManager = new DataManager(DATA_PATH, SAVE_TIMER, users, tags, followers, followings, blogs, posts, wallets, listInteractions, idGenerator);
        Thread threadDataManager = new Thread(dataManager);
        threadDataManager.start();
        // Creo il listen socket sulla porta specificata nel file di configurazione
        try (ServerSocket listenSocket = new ServerSocket(PORT_TCP)) {
            // Creo il thread pool (corePoolSize = 10, maximumPoolSize = inf, KeepAliveTime = 60s)
            ExecutorService pool = new ThreadPoolExecutor(10, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>());
            System.out.println("Server avviato ...");
            // Creo un thread per catturate 'exit' da tastiera e chiudere il socket
            Thread exitThread = new Thread() {
                @Override
                public void run() {
                    Scanner input = new Scanner(System.in);

                    System.out.println("Comando \033[1mexit\033[22m per terminare");
                    System.out.print("> ");

                    while (!input.nextLine().equals("exit")) {
                        System.out.print("> ");
                    }

                    System.out.println("\033[1m<\033[22m termino ed esco");

                    try {
                        listenSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            };
            // Avvio il thread exit
            exitThread.start();

            Socket client;

            while (true) {
                try {
                    //  Attendo un client
                    client = listenSocket.accept();
                } catch (IOException exit) {
                    // Esco dal loop (socket chiuso)
                    break;
                }
                // Avvio un thread nel pool per gestire la connessione con il client
                pool.execute(new UserManager(client, users, tags, stubs, followers, followings, blogs, posts, wallets, listInteractions, connectedUsers, idGenerator, MULTICAST_ADDRESS.getHostAddress(), MULTICAST_PORT));
            }
            // Termino il thread pool
            pool.shutdown();
            boolean poolTermination = false;
            // Aspetto per 'TERMINATION_TIMEOUT' secondi la terminazione di tutti i client
            try {
                poolTermination = pool.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            }
            // Forzo l'uscita se ci sono ancora client
            if (!poolTermination) pool.shutdownNow();
            // Termino il servizio RMI
            UnicastRemoteObject.unexportObject(rmiServices, true);
            // Aspetto per l'ultima volta 'TERMINATION_TIMEOUT' secondi la terminazione degli ultimi client
            try {
                pool.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            }
            // Termino il thread che calcola le ricompense, dopo aver eseguito un ultima iterazione
            threadRewardManager.interrupt();

            try {
                threadRewardManager.join();
            } catch (InterruptedException ignored) {
            }
            // Salvo i dati del sistema sul file di salvataggio e termino il thread che ha questo compito
            threadDataManager.interrupt();

            try {
                threadDataManager.join();
            } catch (InterruptedException ignored) {
            }
            // Esco
            System.exit(0);
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
            int[] stringSet = new int[9];

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
                    case "PORT_TCP":
                        try {
                            PORT_TCP = Integer.parseInt(values[1].trim());
                            if (PORT_TCP < 0 || PORT_TCP > 65535) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: PORT_TCP -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[0] = 1;
                        break;
                    case "MULTICAST_PORT":
                        try {
                            MULTICAST_PORT = Integer.parseInt(values[1].trim());
                            if (MULTICAST_PORT < 0 || MULTICAST_PORT > 65535) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: MULTICAST_PORT -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[1] = 1;
                        break;
                    case "MULTICAST_ADDRESS":
                        try {
                            MULTICAST_ADDRESS = InetAddress.getByName(values[1].trim());
                            if (!MULTICAST_ADDRESS.isMulticastAddress()) throw new UnknownHostException();
                        } catch (UnknownHostException e) {
                            System.err.println("File di configurazione: MULTICAST_ADDRESS -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[2] = 1;
                        break;
                    case "REGISTRY_PORT":
                        try {
                            REGISTRY_PORT = Integer.parseInt(values[1].trim());
                            if (REGISTRY_PORT < 0 || REGISTRY_PORT > 65535) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: REGISTRY_PORT -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[3] = 1;
                        break;
                    case "REWARD_AUTHOR":
                        try {
                            REWARD_AUTHOR = Integer.parseInt(values[1].trim());
                            if (REWARD_AUTHOR < 0 || REWARD_AUTHOR > 100) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: REWARD_AUTHOR -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[4] = 1;
                        break;
                    case "REWARD_TIMER":
                        try {
                            REWARD_TIMER = Integer.parseInt(values[1].trim());
                            if (REWARD_TIMER < 0) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: TIMEOUT -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[5] = 1;
                        break;
                    case "TERMINATION_TIMEOUT":
                        try {
                            TERMINATION_TIMEOUT = Integer.parseInt(values[1].trim());
                            if (TERMINATION_TIMEOUT < 0) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: TERMINATION_TIMEOUT -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[6] = 1;
                        break;
                    case "SAVE_TIMER":
                        try {
                            SAVE_TIMER = Integer.parseInt(values[1].trim());
                            if (SAVE_TIMER < 0) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            System.err.println("File di configurazione: SAVE_TIMER -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[7] = 1;
                        break;
                    case "DATA_PATH":
                        DATA_PATH = values[1].trim();

                        if (DATA_PATH.length() == 0) {
                            System.err.println("File di configurazione: DATA_PATH -> valore invalido");
                            System.exit(1);
                        }

                        stringSet[8] = 1;
                        break;
                }
            }

            // Controllo di aver trovato tutte le stringhe di configurazione
            boolean error = false;

            for (int i = 0; i < 9; i++) {
                if (stringSet[i] == 0) {
                    switch (i) {
                        case 0: System.err.println("File di configurazione: PORT_TCP -> valore mancante"); break;
                        case 1: System.err.println("File di configurazione: MULTICAST_PORT -> valore mancante"); break;
                        case 2: System.err.println("File di configurazione: MULTICAST_ADDRESS -> valore mancante"); break;
                        case 3: System.err.println("File di configurazione: REGISTRY_PORT -> valore mancante"); break;
                        case 4: System.err.println("File di configurazione: REWARD_AUTHOR -> valore mancante"); break;
                        case 5: System.err.println("File di configurazione: TIMEOUT -> valore mancante"); break;
                        case 6: System.err.println("File di configurazione: TERMINATION_TIMEOUT -> valore mancante"); break;
                        case 7: System.err.println("File di configurazione: SAVE_TIMER -> valore mancante"); break;
                        case 8: System.err.println("File di configurazione: DATA_PATH -> valore mancante"); break;
                    }

                    error = true;
                }
            }

            if (error) System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("File di configurazione: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    // Legge il file di salvataggio se esiste e inizializza le strutture dati
    private static void readData(String path) {
        try (FileReader fileReader = new FileReader(path);
             BufferedReader dataFile = new BufferedReader(fileReader)) {
            // Inizializzo le strutture dati
            Gson gson = new GsonBuilder().excludeFieldsWithModifiers().create();
            String json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            Type usersType = new TypeToken<ConcurrentHashMap<String, String>>() {}.getType();
            users = gson.fromJson(json, usersType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            Type mapStringArrayListStringType = new TypeToken<ConcurrentHashMap<String, ArrayList<String>>>() {}.getType();
            tags = gson.fromJson(json, mapStringArrayListStringType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            followings = gson.fromJson(json, mapStringArrayListStringType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            followers = gson.fromJson(json, mapStringArrayListStringType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            Type blogsType = new TypeToken<ConcurrentHashMap<String, Vector<Post>>>() {}.getType();
            blogs = gson.fromJson(json, blogsType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            Type postsType = new TypeToken<ConcurrentHashMap<Integer, Post>>() {}.getType();
            posts = gson.fromJson(json, postsType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            Type walletsType = new TypeToken<ConcurrentHashMap<String, Wallet>>() {}.getType();
            wallets = gson.fromJson(json, walletsType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            Type listInteractionsType = new TypeToken<ListInteractions>() {}.getType();
            listInteractions = gson.fromJson(json, listInteractionsType);
            json = dataFile.readLine();
            if (json.equals("")) { throw new JsonSyntaxException("Oggetto json stringa vuota"); }
            idGenerator = gson.fromJson(json, AtomicInteger.class);
        } catch (FileNotFoundException e) {
            // Il file non esiste e inizializzo le strutture con i valori di default
            users = new ConcurrentHashMap<>();
            tags = new ConcurrentHashMap<>();
            followings = new ConcurrentHashMap<>();
            followers = new ConcurrentHashMap<>();
            blogs = new ConcurrentHashMap<>();
            posts = new ConcurrentHashMap<>();
            wallets = new ConcurrentHashMap<>();
            listInteractions = new ListInteractions();
            idGenerator = new AtomicInteger();
        } catch (JsonSyntaxException e) {
            System.err.println("File di salvataggio: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        // Strutture dati utili a runtime
        stubs = new ConcurrentHashMap<>();
        connectedUsers = new Vector<>();
    }
}
