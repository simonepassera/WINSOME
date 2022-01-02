// @Author Simone Passera

import java.io.*;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.*;

public class ServerMain {
    // Numero di porta del server WINSOME
    private static int PORT_TCP;
    // Indirizzo e porta di multicast a cui inviare la notifica di avvenuto calcolo delle ricompense
    private static InetAddress MULTICAST_ADDRESS;
    private static int MULTICAST_PORT;
    // Porta del registry
    private static int REGISTRY_PORT;
    // Mappa (username, password)
    private static ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private static ConcurrentHashMap<String, ArrayList<String>> tags;
    // Mappa (username, stub_callback)
    private static ConcurrentHashMap<String, NotifyFollowersInterface> stubs;
    // Mappa (username, followers)
    private static ConcurrentHashMap<String, Vector<String>> followers;
    // Lista degli utenti connessi
    private static Vector<String> connectedUsers;

    public static void main(String[] args) {
        // Controllo se esiste il file di configurazione
        if (args.length == 0) {
            System.err.println("Usage: java ServerMain <config file>");
            System.exit(1);
        }

        // Leggo il file di configurazione
        readConf(args[0]);

        Registry registry = null;

        // Creo il registry
        try {
            registry = LocateRegistry.createRegistry(REGISTRY_PORT);
        } catch (RemoteException e) {
            System.err.println("Creazione del registry: " + e.getMessage());
            System.exit(1);
        }

        // inizializzo le strutture dati
        users = new ConcurrentHashMap<>();
        tags = new ConcurrentHashMap<>();
        stubs = new ConcurrentHashMap<>();
        followers = new ConcurrentHashMap<>();
        connectedUsers = new Vector<>();

        WinsomeRMIServices rmiServices = null;

        // Creo ed esporto l'oggetto remoto
        try {
            rmiServices = new WinsomeRMI(users, tags, stubs, followers);
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

        // Creo il listen socket sulla porta specificata nel file di configurazione
        try (ServerSocket listenSocket = new ServerSocket(PORT_TCP)) {
            ExecutorService pool = new ThreadPoolExecutor(10, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>());

            System.out.println("Server avviato ...");

            while (true) {
                pool.execute(new UserManager(listenSocket.accept(), users, tags, stubs, followers, connectedUsers));
            }
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
            int[] stringSet = new int[4];

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
}
