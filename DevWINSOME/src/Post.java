import com.google.gson.annotations.Expose;

import java.util.concurrent.atomic.AtomicInteger;

public class Post {
    @Expose private final int id;
    @Expose private final String author;
    @Expose private final String title;
    // Variabili non serializzate/deserializzate durante l'invio del post con viewBlog()
    @Expose(serialize = false, deserialize = false)
    private final String text;

    public Post(String author, String title, String text, AtomicInteger idGenerator) {
        id = idGenerator.incrementAndGet();
        this.author = author;
        this.title = title;
        this.text = text;
    }

    public int getId() {
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
}
