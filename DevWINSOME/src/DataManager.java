import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.sql.rowset.serial.SerialStruct;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DataManager implements Runnable {
    // Stabilisce ogni quanti secondi salvare i dati
    private long save_timer;
    // Percorso del file di salvataggio
    private String data_path;
    // Mappa (username, password)
    private ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private ConcurrentHashMap<String, ArrayList<String>> tags;
    // Mappa (username, followers)
    private ConcurrentHashMap<String, ArrayList<String>> followers;
    // Mappa (username, following)
    private ConcurrentHashMap<String, ArrayList<String>> followings;
    // Mappa (username, blog)
    private ConcurrentHashMap<String, Vector<Post>> blogs;
    // Mappa (idPost, post)
    private ConcurrentHashMap<Integer, Post> posts;
    // Mappa (username, wallet)
    private ConcurrentHashMap<String, Wallet> wallets;
    // Lista delle interazioni usata per la sincronizzazione
    private final ListInteractions listInteractions;
    // Generatore id per un post
    private AtomicInteger idGenerator;
    // Gson
    private Gson gson;
    // Tipi
    Type usersType;
    Type tagsType;
    Type arrayListType;
    Type blogType;
    Type postType;
    Type walletsType;
    Type listInteractionsType;

    public DataManager(String data_path, int save_timer, ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags, ConcurrentHashMap<String, ArrayList<String>> followers, ConcurrentHashMap<String, ArrayList<String>> followings, ConcurrentHashMap<String, Vector<Post>> blogs, ConcurrentHashMap<Integer, Post> posts, ConcurrentHashMap<String, Wallet> wallets, ListInteractions listInteractions, AtomicInteger idGenerator) {
        this.data_path = data_path;
        this.save_timer = save_timer;
        this.users = users;
        this.tags = tags;
        this.followers = followers;
        this.followings = followings;
        this.blogs = blogs;
        this.posts = posts;
        this.wallets = wallets;
        this.listInteractions = listInteractions;
        this.idGenerator = idGenerator;
        gson = new GsonBuilder().excludeFieldsWithModifiers().create();
        usersType = new TypeToken<ConcurrentHashMap<String, String>>() {}.getType();
        tagsType = new TypeToken<ConcurrentHashMap<String, ArrayList<String>>>() {}.getType();
        arrayListType = new TypeToken<ArrayList<String>>() {}.getType();
        blogType = new TypeToken<Vector<Post>>() {}.getType();
        postType = new TypeToken<Post>() {}.getType();
        walletsType = new TypeToken<ConcurrentHashMap<String, Wallet>>() {}.getType();
        listInteractionsType = new TypeToken<ListInteractions>() {}.getType();
    }

    @Override
    public void run() {
        boolean exit = false;

        while (!exit) {
            if (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(save_timer * 1000);
                } catch (InterruptedException e) {
                    exit = true;
                }
            } else {
                exit = true;
            }

            try (PrintWriter dataFile = new PrintWriter(new BufferedWriter(new FileWriter(data_path)))) {
                synchronized (users) {
                    dataFile.println(gson.toJson(users, usersType));
                    dataFile.println(gson.toJson(tags, tagsType));

                    boolean first = true;

                    dataFile.print("{");
                    ArrayList<String> listFollowing;
                    for (Map.Entry<String, ArrayList<String>> couple : followings.entrySet()) {
                        if (first) first = false;
                        else dataFile.print(",");

                        listFollowing = couple.getValue();

                        synchronized (listFollowing) {
                            dataFile.print("\"" + couple.getKey() + "\":" + gson.toJson(listFollowing, arrayListType));
                        }
                    }
                    dataFile.println("}");

                    first = true;

                    dataFile.print("{");
                    ArrayList<String> listFollowers;
                    for (Map.Entry<String, ArrayList<String>> couple : followers.entrySet()) {
                        if (first) first = false;
                        else dataFile.print(",");

                        listFollowers = couple.getValue();

                        synchronized (listFollowers) {
                            dataFile.print("\"" + couple.getKey() + "\":" + gson.toJson(listFollowers, arrayListType));
                        }
                    }
                    dataFile.println("}");

                    first = true;

                    dataFile.print("{");
                    Vector<Post> blog;
                    for (Map.Entry<String, Vector<Post>> couple : blogs.entrySet()) {
                        if (first) first = false;
                        else dataFile.print(",");

                        blog = couple.getValue();

                        synchronized (blog) {
                            dataFile.print("\"" + couple.getKey() + "\":" + gson.toJson(blog, blogType));
                        }
                    }
                    dataFile.println("}");

                    first = true;

                    synchronized (posts) {
                        dataFile.print("{");
                        Post p;
                        for (Map.Entry<Integer, Post> couple : posts.entrySet()) {
                            if (first) first = false;
                            else dataFile.print(",");

                            p = couple.getValue();

                            synchronized (p) {
                                dataFile.print("\"" + couple.getKey() + "\":" + gson.toJson(p, postType));
                            }
                        }
                        dataFile.println("}");
                    }

                    synchronized (listInteractions) {
                        dataFile.println(gson.toJson(wallets, walletsType));
                        dataFile.println(gson.toJson(listInteractions, listInteractionsType));
                    }

                    dataFile.println(idGenerator.get());
                }
            } catch (IOException e) {
                System.err.println("File di salvataggio: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}
