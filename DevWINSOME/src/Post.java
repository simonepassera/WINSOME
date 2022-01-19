import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class Post {
    @Expose private final Integer id;
    @Expose private final String author;
    @Expose private final String title;
    // Variabili non serializzate/deserializzate durante l'invio del post parziale
    @Expose(serialize = false, deserialize = false)
    private final String text;
    @Expose(serialize = false, deserialize = false)
    private int upvote;
    @Expose(serialize = false, deserialize = false)
    private int downvote;
    @Expose(serialize = false, deserialize = false)
    private ArrayList<String> comments;
    // Utenti che hanno espresso una preferenza positiva o negativa
    // Non viene mai inviata
    transient private final HashSet<String> usersVote;
    // Utenti cha hanno eseguito il rewin del post
    // Non viene mai inviata
    transient private final HashSet<String> usersRewin;

    public Post(String author, String title, String text, AtomicInteger idGenerator) {
        id = idGenerator.incrementAndGet();
        this.author = author;
        this.title = title;
        this.text = text;
        usersVote = new HashSet<>();
        usersRewin = new HashSet<>();
        comments = new ArrayList<>();
        upvote = 0;
        downvote = 0;
    }

    public Integer getId() { return id; }

    public String getAuthor() { return author; }

    public String getTitle() { return title; }

    public String getText() { return text; }

    public ArrayList<String> getComments() { return comments; }

    public Integer getUpvote() { return upvote; }

    public Integer getDownVote() { return downvote; }

    public HashSet<String> getUsersRewin() { return usersRewin; }

    // @Return  0 -> ok
    //          1 -> post già votato
    public synchronized int addUpVote(String username) {
        if (!usersVote.add(username)) {
            return 1;
        } else {
            upvote++;
            return 0;
        }
    }

    // @Return  0 -> ok
    //          1 -> post già votato
    public synchronized int addDownVote(String username) {
        if (!usersVote.add(username)) {
            return 1;
        } else {
            downvote++;
            return 0;
        }
    }

    public synchronized void addComment(String username, String comment) {
        StringBuilder userComment = new StringBuilder();
        userComment.append(username).append(" ").append(comment);

        comments.add(userComment.toString());
    }

    public synchronized void addUserRewin(String username) {
        usersRewin.add(username);
    }
}
