// @Author Simone Passera

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class Post {
    // Id
    @Expose private final Integer id;
    // Autore
    @Expose private final String author;
    // Titolo
    @Expose private final String title;
    // Variabili non serializzate durante l'invio del post parziale
    @Expose(serialize = false, deserialize = false)
    // Contenuto
    private final String text;
    @Expose(serialize = false, deserialize = false)
    // Numero di voti positivi
    private int upvote;
    @Expose(serialize = false, deserialize = false)
    // Numero dei voti negativi
    private int downvote;
    @Expose(serialize = false, deserialize = false)
    // Lista dei commenti, stringa -> "<username> <commento>"
    private final ArrayList<String> comments;
    // Utenti che hanno espresso una preferenza positiva o negativa, non viene mai inviata al client
    private transient final HashSet<String> usersVote;
    // Utenti cha hanno eseguito il rewin del post, non viene mai inviata al client
    private transient final HashSet<String> usersRewin;

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

    // Metodi get usati dal client
    public Integer getId() { return id; }
    public String getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public Integer getUpvote() { return upvote; }
    public Integer getDownVote() { return downvote; }
    public ArrayList<String> getComments() { return comments; }

    // Aggiunge un voto positivo
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

    // Aggiunge un voto negativo
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

    // Aggiunge un commento
    public synchronized void addComment(String username, String comment) {
        comments.add(username + " " + comment);
    }

    // Aggiunge un utente alla lista delle persone che hanno eseguito il rewin del post
    public synchronized void addUserRewin(String username) {
        usersRewin.add(username);
    }

    // Restituisce l'insime degli utenti he hanno eseguito il rewin del post
    public HashSet<String> getUsersRewin() { return usersRewin; }
}
