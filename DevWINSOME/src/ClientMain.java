import java.io.*;
import java.net.*;

public class ClientMain {
    // Indirizzo del server WINSOME
    private static String SERVER_ADDRESS;
    // Numero di porta del server WINSOME
    private static int PORT_TCP;
    // Tempo massimo per aprire la connessione con il server WINSOME
    private static int timeout = 15000;

    public static void main(String[] args) {
        // Controllo se esiste il file di configurazione
        if (args.length == 0) {
            System.err.println("Usage: java ClientMain <config file>");
            System.exit(1);
        }

        // Leggo il file di configurazione
        readConf(args[0]);

        InetAddress address = null;

        // Provo a risolvere l'indirizzo del server WINSOME
        try {
            address = InetAddress.getByName(SERVER_ADDRESS);
        } catch (UnknownHostException e) {
            System.err.println("Non riesco a risolvere l'indirizzo del server (" + SERVER_ADDRESS + ")");
            System.exit(1);
        }

        // Creo il socket tcp per la connessione
        try (Socket winsomeServer = new Socket()) {
            winsomeServer.bind(new InetSocketAddress(InetAddress.getLocalHost(), 0));
            InetSocketAddress socketAddress = new InetSocketAddress(address, PORT_TCP);
            System.out.println("Connessione con il server WINSOME ...");
            // Apro la connessione con il server WINSOME
            winsomeServer.connect(socketAddress, timeout);
            System.out.println("*** WINSOME ***");
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
            int[] stringSet = new int[2];

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
