// @Author Simone Passera

import java.util.HashMap;

// Oggetto contenente le interazioni (commenti e voti) per tutti i post
// dall'ultimo calcolo delle ricompense
public class ListInteractions {
    // Mappa (id, interazione)
    private final HashMap<Integer, Interaction> interactions;

    public ListInteractions() {
        interactions = new HashMap<>();
    }

    // Aggiunge un voto positivo alle interazioni del post id
    public synchronized void addUpVote(Integer id, String username) {
        Interaction interaction = interactions.get(id);
        if (interaction != null) interaction.addUpVote(username);
    }

    // Aggiunge un voto negativo alle interazioni del post id
    public synchronized void addDownVote(Integer id) {
        Interaction interaction = interactions.get(id);
        if (interaction != null) interaction.addDownVote();
    }

    // Aggiunge un commento alle interazioni del post id
    public synchronized void addComment(Integer id, String username) {
        Interaction interaction = interactions.get(id);
        if (interaction != null) interaction.addComment(username);
    }

    // Aggiunge un nuovo post alla lista delle interazioni
    public synchronized void addPost(Integer id, String author) {
        interactions.put(id, new Interaction(author));
    }

    // Rimuove il post id dlla lista delle interazioni
    public synchronized void removePost(Integer id) {
        interactions.remove(id);
    }

    // Restituisce la lista delle interazioni
    public synchronized HashMap<Integer, Interaction> getInteractions() {
        return interactions;
    }
}
