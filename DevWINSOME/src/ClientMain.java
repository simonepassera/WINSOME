import java.io.*;
import java.util.Locale;

public class ClientMain {

    private static String SERVER_ADDRESS;
    private static String PORT_TCP;

    public static void main(String[] args) {

        // Controllo se esiste il file di configurazione
        if (args.length == 0) {
            System.err.println("Usage: java ClientMain <config file>");
            System.exit(1);
        }

        readConf(args[0]); // Leggo il file di configurazione

        System.out.println("*** WINSOME ***");
    }

    private static void readConf(String path) {
        try (FileReader fileReader = new FileReader(path);
             BufferedReader configFile = new BufferedReader(fileReader)) {

            String line;
            String[] values;

            while ((line = configFile.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;

                values = line.split("=");

                if (values.length != 2) {
                    System.err.println("File di configurazione: errore di sintassi");
                    System.exit(1);
                }

                switch (values[0].trim()) {
                    case "SERVER_ADDRESS":
                        SERVER_ADDRESS = values[1].trim();
                        System.out.println("value -> " + SERVER_ADDRESS);
                        break;
                    case "PORT_TCP":
                        break;
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
