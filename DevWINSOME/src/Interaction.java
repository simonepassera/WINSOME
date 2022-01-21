import java.util.HashMap;
import java.util.HashSet;

public class Interaction {
    private int iteration;
    private HashSet<String> upVote;
    private int downVote;
    private HashMap<String, Integer> numComments;
    private boolean flagInteraction;
    private String author;

    public Interaction(String author) {
        iteration = 1;
        upVote = new HashSet<>();
        downVote = 0;
        numComments = new HashMap<>();
        flagInteraction = false;
        this.author = author;
    }

    public void addUpVote(String username) {
        upVote.add(username);
        flagInteraction = true;
    }

    public void addDownVote() {
        downVote++;
    }

    public void addComment(String username) {
        numComments.merge(username, 1, Integer::sum);
        flagInteraction = true;
    }

    public int getIteration() {
        return iteration;
    }

    public int getDownVote() {
        return downVote;
    }

    public String getAuthor() {
        return author;
    }

    public HashSet<String> getUpVote() {
        return upVote;
    }

    public HashMap<String, Integer> getNumComments() {
        return numComments;
    }

    public boolean isFlagInteraction() {
        return flagInteraction;
    }

    public void incrementIteration() {
        iteration++;
    }

    public void reset() {
        upVote.clear();
        downVote = 0;
        numComments.clear();
        flagInteraction = false;
    }
}
