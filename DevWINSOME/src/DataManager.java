// @Author Simone Passera

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Thread che salva lo stato del sistema a intervalli regolari
public class DataManager implements Runnable {
    // Stabilisce ogni quanti secondi salvare i dati
    private final long save_timer;
    // Percorso del file di salvataggio
    private final String data_path;
    // Mappa (username, password)
    private final ConcurrentHashMap<String, String> users;
    // Mappa (username, tags)
    private final ConcurrentHashMap<String, ArrayList<String>> tags;
    // Mappa (username, followers)
    private final ConcurrentHashMap<String, ArrayList<String>> followers;
    // Mappa (username, following)
    private final ConcurrentHashMap<String, ArrayList<String>> followings;
    // Mappa (username, blog)
    private final ConcurrentHashMap<String, Vector<Post>> blogs;
    // Mappa (idPost, post)
    private final ConcurrentHashMap<Integer, Post> posts;
    // Mappa (username, wallet)
    private final ConcurrentHashMap<String, Wallet> wallets;
    // Lista delle interazioni
    private final ListInteractions listInteractions;
    // Generatore id per un post
    private final AtomicInteger idGenerator;
    // Gson
    private final Gson gson;
    // Tipi
    private final Type usersType;
    private final Type tagsType;
    private final Type arrayListType;
    private final Type blogType;
    private final Type postType;
    private final Type walletsType;
    private final Type listInteractionsType;

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
            // Controllo se il thread è stato interrotto
            if (!Thread.currentThread().isInterrupted()) {
                try {
                    // Sospendo il thread per "timer" secondi
                    Thread.sleep(save_timer * 1000);
                } catch (InterruptedException e) {
                    // Thread interrotto
                    exit = true;
                }
            } else {
                // Thread interrotto
                exit = true;
            }

            // Apro il file di salvataggio
            try (PrintWriter dataFile = new PrintWriter(new BufferedWriter(new FileWriter(data_path)))) {
                // Salvo le strutture dati
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
