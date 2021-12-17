// @Author Simone Passera

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ServerMain {
    // Numero di porta del server WINSOME
    private static int PORT_TCP;
    // Indirizzo e porta di multicast a cui inviare la notifica di avvenuto calcolo delle ricompense
    private static InetAddress MULTICAST_ADDRESS;
    private static int MULTICAST_PORT;

    public static void main(String[] args) {
        // Controllo se esiste il file di configurazione
        if (args.length == 0) {
            System.err.println("Usage: java ServerMain <config file>");
            System.exit(1);
        }

        // Leggo il file di configurazione
        readConf(args[0]);

        // Creo il listen socket sulla porta specificata nel file di configurazione
        try (ServerSocket listenSocket = new ServerSocket(PORT_TCP, 1)) {
            ExecutorService pool = Executors.newCachedThreadPool();

            System.out.println("Server avviato ...");

            while (true) {
                Socket user = listenSocket.accept();
                System.out.println(user);
            }
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
            int[] stringSet = new int[3];

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
