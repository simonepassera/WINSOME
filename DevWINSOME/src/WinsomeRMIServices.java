// @Author Simone Passera

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface WinsomeRMIServices extends Remote {
    // Inserisce un nuovo utente, tags è una lista di massimo 5 tag
    // @Return CodeReturn
    String register(String username, String password, List<String> tags) throws RemoteException;
    // Registra l'utente alla callback per ricevere aggiornamenti riguardo i propri followers
    // @Return CodeReturn
    String registerListFollowers(NotifyFollowersInterface callback) throws RemoteException;
}
