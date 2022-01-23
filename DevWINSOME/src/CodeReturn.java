// @Author Simone Passera

// Oggetto contenente codice e messaggio di risposta
// restituito dall'oggetto remoto WinsomeRMIServices
public class CodeReturn {
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
