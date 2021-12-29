import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyFollowersInterface extends Remote {
    // Funzione utilizzata dal server al momento della registrazione
    // della callback per inizializzare la lista dei followers
    // @Return CodeReturn
    String init(String listUsers) throws RemoteException;
    // Aggiunge un utente alla lista dei followers
    // @Return CodeReturn
    String addFollower(String username) throws RemoteException;
    // Rimuove un utente dalla lista dei followers
    // @Return CodeReturn
    String removeFollower(String username) throws RemoteException;
}
