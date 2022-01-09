import com.google.gson.annotations.Expose;

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
    // Utenti che hanno espresso una preferenza positiva o negativa
    // Non viene mai serializzata con gson
    transient private final HashSet<String> usersVote;

    public Post(String author, String title, String text, AtomicInteger idGenerator) {
        id = idGenerator.incrementAndGet();
        this.author = author;
        this.title = title;
        this.text = text;
        usersVote = new HashSet<>();
        upvote = 0;
        downvote = 0;
    }

    public Integer getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public synchronized Integer getUpvote() { return upvote; }

    public synchronized Integer getDownVote() { return downvote; }


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
}
