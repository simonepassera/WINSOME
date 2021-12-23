// @Author Simone Passera

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface WinsomeRMIServices extends Remote {
    // Inserisce un nuovo utente, tags è una lista di massimo 5 tag
    CodeReturn register(String username, String password, List<String> tags) throws RemoteException;

    // Oggetto restituito dal server: coppia (codice, messaggio di spiegazione relativo al codice)
    class CodeReturn implements Serializable {
        private final Integer code;
        private final String message;

        public CodeReturn(Integer code, String message) {
            this.code = code;
            this.message = message;
        }

        public Integer getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
