import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
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
    private ConcurrentHashMap<String, Vector<String>> followers;
    // Mappa (username, following)
    private ConcurrentHashMap<String, Vector<String>> followings;
    // Mappa (username, blog)
    private ConcurrentHashMap<String, Vector<Post>> blogs;
    // Mappa (idPost, post)
    private ConcurrentHashMap<Integer, Post> posts;
    // Mappa (username, wallet)
    private ConcurrentHashMap<String, Wallet> wallets;
    // Generatore id per un post
    private AtomicInteger idGenerator;
    // Gson
    private Gson gson;
    // Tipi
    Type usersType;
    Type tagsType;
    Type mapStringVectorStringType;
    Type blogsType;
    Type postsType;
    Type walletsType;

    public DataManager(String data_path, int save_timer, ConcurrentHashMap<String, String> users, ConcurrentHashMap<String, ArrayList<String>> tags, ConcurrentHashMap<String, Vector<String>> followers, ConcurrentHashMap<String, Vector<String>> followings, ConcurrentHashMap<String, Vector<Post>> blogs, ConcurrentHashMap<Integer, Post> posts, ConcurrentHashMap<String, Wallet> wallets, AtomicInteger idGenerator) {
        this.data_path = data_path;
        this.save_timer = save_timer;
        this.users = users;
        this.tags = tags;
        this.followers = followers;
        this.followings = followings;
        this.blogs = blogs;
        this.posts = posts;
        this.wallets = wallets;
        this.idGenerator = idGenerator;
        gson = new GsonBuilder().excludeFieldsWithModifiers().create();
        usersType = new TypeToken<ConcurrentHashMap<String, String>>() {}.getType();
        tagsType = new TypeToken<ConcurrentHashMap<String, ArrayList<String>>>() {}.getType();
        mapStringVectorStringType = new TypeToken<ConcurrentHashMap<String, Vector<String>>>() {}.getType();
        blogsType = new TypeToken<ConcurrentHashMap<String, Vector<Post>>>() {}.getType();
        postsType = new TypeToken<ConcurrentHashMap<Integer, Post>>() {}.getType();
        walletsType = new TypeToken<ConcurrentHashMap<String, Wallet>>() {}.getType();
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
                dataFile.println(gson.toJson(users, usersType));
                dataFile.println(gson.toJson(tags, tagsType));
                dataFile.println(gson.toJson(followers, mapStringVectorStringType));
                dataFile.println(gson.toJson(followings, mapStringVectorStringType));
                dataFile.println(gson.toJson(blogs, blogsType));
                dataFile.println(gson.toJson(posts, postsType));
                dataFile.println(gson.toJson(wallets, walletsType));
                dataFile.println(gson.toJson(idGenerator, AtomicInteger.class));
            } catch (IOException e) {
                System.err.println("File di salvataggio: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}
