import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class WinsomeRMI extends UnicastRemoteObject implements WinsomeRMIServices {
    public WinsomeRMI() throws RemoteException {
        super();
    }

    public int register(String username, String password, List<String> tags) throws RemoteException {
        return 0;
    }
}
