import java.util.HashMap;

public class ListInteractions {
    private HashMap<Integer, Interaction> interactions;

    public ListInteractions() {
        interactions = new HashMap<>();
    }

    public synchronized void addUpVote(Integer id, String username) {
        Interaction interaction = interactions.get(id);
        if (interaction != null) interaction.addUpVote(username);
    }

    public synchronized void addDownVote(Integer id) {
        Interaction interaction = interactions.get(id);
        if (interaction != null) interaction.addDownVote();
    }

    public synchronized void addComment(Integer id, String username) {
        Interaction interaction = interactions.get(id);
        if (interaction != null) interaction.addComment(username);
    }

    public synchronized void addPost(Integer id, String author) {
        interactions.put(id, new Interaction(author));
    }

    public synchronized void removePost(Integer id) {
        interactions.remove(id);
    }

    public synchronized HashMap<Integer, Interaction> getInteractions() {
        return interactions;
    }
}
