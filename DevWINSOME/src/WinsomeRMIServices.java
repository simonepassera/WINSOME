// @Author Simone Passera

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface WinsomeRMIServices extends Remote {
    // Inserisce un nuovo utente, tags è una lista di massimo 5 tag
    int register(String username, String password, List<String> tags) throws RemoteException;
}
