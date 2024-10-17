package org.example;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;

public class FavoritesManager {
    private static final String FILE_PATH = "src/main/resources/favorites.dat";  // Шлях до файлу

    // Завантаження улюблених вакансій із файлу
    public static HashMap<Long, HashSet<String>> loadFavorites() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_PATH))) {
            return (HashMap<Long, HashSet<String>>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            // Якщо файл не знайдено або сталася помилка, повертаємо порожній HashMap
            return new HashMap<>();
        }
    }

    // Збереження улюблених вакансій у файл
    public static void saveFavorites(HashMap<Long, HashSet<String>> favoriteVacancies) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_PATH))) {
            oos.writeObject(favoriteVacancies);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

