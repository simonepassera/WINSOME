// @Author Simone Passera

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface WinsomeRMIServices extends Remote {
    // Inserisce un nuovo utente, tags è una lista di massimo 5 tag
    // @Return CodeReturn
    String register(String username, String password, ArrayList<String> tags) throws RemoteException;
    // Registra l'utente alla callback per ricevere aggiornamenti riguardo i propri followers
    // @Return CodeReturn
    String registerListFollowers(String username, NotifyFollowersInterface callback) throws RemoteException;
    // Annulla la registrazione alla callback
    // @Return CodeReturn
    String unregisterListFollowers(String username) throws RemoteException;
}
