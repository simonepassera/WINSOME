import java.util.HashMap;

public class ListInteractions {
    private HashMap<Integer, Interaction> interactions;

    public ListInteractions() {
        interactions = new HashMap<>();
    }

    public synchronized void addUpVote(Integer id, String username) {
        Interaction interaction = interactions.get(id);
        interaction.addUpVote(username);
    }

    public synchronized void addDownVote(Integer id) {
        Interaction interaction = interactions.get(id);
        interaction.addDownVote();
    }

    public synchronized void addComment(Integer id, String username) {
        Interaction interaction = interactions.get(id);
        interaction.addComment(username);
    }

    public synchronized void addPost(Integer id) {
        interactions.put(id, new Interaction());
    }

    public synchronized void removePost(Integer id) {
        interactions.remove(id);
    }

    public synchronized HashMap<Integer, Interaction> getInteractions() {
        return interactions;
    }
}