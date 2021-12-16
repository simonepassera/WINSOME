import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientMain {
    // Indirizzo del server WINSOME
    private static String SERVER_ADDRESS;
    // Numero di porta del server WINSOME
    private static int PORT_TCP;
    // Tempo massimo per aprire la connessione con il server WINSOME (ms)
    private static int TIMEOUT = 15000;

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

        // Creo il socket tcp per la connessione
        try (Socket winsomeServer = new Socket()) {
            winsomeServer.bind(new InetSocketAddress(InetAddress.getLocalHost(), 0));
            InetSocketAddress socketAddress = new InetSocketAddress(address, PORT_TCP);
            System.out.println("Connessione con il server WINSOME ...");
            // Apro la connessione con il server WINSOME
            //winsomeServer.connect(socketAddress, TIMEOUT);  *********************************

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
        System.out.println("\033[1m<\033[22m \033[1mregister\033[22m <username> <password> <tags>\033[50Gspiegazione");
        System.out.println("\033[1m<\033[22m \033[1mlogin\033[22m <username> <password>\033[50Gspiegazione");
        System.out.println("\033[1m<\033[22m \033[1mlogout\033[22m \033[50Geffettua il logout dal servizio.");
        System.out.println("\033[1m<\033[22m \033[1mhelp\033[22m \033[50Gmostra la lista dei comandi.");
        System.out.println("\033[1m<\033[22m \033[1mexit\033[22m \033[50Gtermina il processo.");
    }
}
