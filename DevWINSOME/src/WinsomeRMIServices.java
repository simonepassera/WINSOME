import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface WinsomeRMIServices extends Remote {
    int register(String username, String password, List<String> tags) throws RemoteException;
}
