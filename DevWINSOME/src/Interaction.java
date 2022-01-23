// @Author Simone Passera

import java.util.HashMap;
import java.util.HashSet;

// Oggetto che contiene le informazioni relative a un post
// necessarie per il calcolo delle ricompense.
// Le informazioni sono valide dall'ultimo calcolo delle ricompense
public class Interaction {
    // Numero d'interazioni (età del post)
    private int iteration;
    // Insieme degli utenti che hanno votato positivamente il post
    private final HashSet<String> upVote;
    // Numero di voti negativi
    private int downVote;
    // Mappa (username, numero di commenti)
    private final HashMap<String, Integer> numComments;
    // Flag per testare che è avvenuta almeno una interazione
    private boolean flagInteraction;
    // Autore del post
    private final String author;

    public Interaction(String author) {
        iteration = 1;
        upVote = new HashSet<>();
        downVote = 0;
        numComments = new HashMap<>();
        flagInteraction = false;
        this.author = author;
    }

    // Aggiunge un voto positivo
    public void addUpVote(String username) {
        upVote.add(username);
        flagInteraction = true;
    }

    // Aggiunge un voto negativo
    public void addDownVote() {
        downVote++;
        flagInteraction = true;
    }

    // Aggiunge un commento
    public void addComment(String username) {
        numComments.merge(username, 1, Integer::sum);
        flagInteraction = true;
    }

    // Restituisce l'età del post
    public int getIteration() {
        return iteration;
    }

    // Restituisce il numero di voti negativi
    public int getDownVote() {
        return downVote;
    }

    // Restituisce l'autore del post
    public String getAuthor() {
        return author;
    }

    // Restituisce l'insieme degli utenti che hanno votato positivamente il post
    public HashSet<String> getUpVote() {
        return upVote;
    }

    // Restituisce il numero dei commenti
    public HashMap<String, Integer> getNumComments() {
        return numComments;
    }

    // Restituisce il flag
    public boolean isFlagInteraction() {
        return flagInteraction;
    }

    // Incrementa l'età del post di una unità
    public void incrementIteration() {
        iteration++;
    }

    // Cancella tutte le interazioni, utilizzato alla fine del calcolo
    // delle ricompense per iniziare un nuovo ciclo d'interazioni
    public void reset() {
        upVote.clear();
        downVote = 0;
        numComments.clear();
        flagInteraction = false;
    }
}
