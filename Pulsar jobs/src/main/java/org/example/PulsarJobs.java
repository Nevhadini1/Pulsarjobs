package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 *
 */
public class PulsarJobs extends TelegramLongPollingBot {
    private final long ADMIN_CHAT_ID = 539667232;

    private final long DEVELOPER_ID = 769219987;
    private HashMap<Long, HashSet<String>> selectedCities = new HashMap<>();
    private Map<Long, Long> lastStartCommandTime = new HashMap<>();
    private int inFavorites = 0;
    private HashMap<Long, HashSet<String>> favoriteVacancies = FavoritesManager.loadFavorites();
    private String previousCity;   // Для збереження попереднього вибору міста
    private String previousGender;
    private Integer lastMessageId;
    private String language;

    public PulsarJobs() {
        scheduleFileBackup();
    }

    private void scheduleFileBackup() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Плануємо надсилання файлу кожні 2 дні
        scheduler.scheduleAtFixedRate(() -> {
            sendFavoritesFileToAdmin(DEVELOPER_ID);
        }, 0, 1, TimeUnit.DAYS);
    }
    @Override
    public String getBotUsername() {
        // Повертаємо ім'я бота, яке зареєстроване в BotFather
        return "PulsarJobsBot";
    }

    @Override
    public String getBotToken() {
        // Повертаємо токен бота, отриманий від BotFather
        return "7784646720:AAFPm-EZYAgm9tMsvyksgnUyWhWz90GKDVs";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            if (callbackData.equals("ua")) {
                language = "ua";
                sendInstructionWithOkButton(chatId, messageId, "Ласкаво просимо! Ви можете використовувати цього бота для пошуку роботи в Польщі. Для початку скористайтеся меню, щоб знайти роботу або переглянути вподобані вакансії.");
            } else if (callbackData.equals("ru")) {
                language = "ru";
                sendInstructionWithOkButton(chatId, messageId, "Добро пожаловать! Вы можете использовать этого бота для поиска работы в Польше. Для начала воспользуйтесь меню, чтобы найти работу или просмотреть избранные вакансии.");
            } else if (callbackData.equals("ok")) {
                sendMainMenu(chatId, messageId, language);
            } else if (callbackData.equals("main_menu")) {
                sendMainMenu(chatId, messageId, language);  // Повертаємося до головного меню
            } else if (callbackData.equals("show_favorites")) {
                inFavorites = 1;
                showFavorites(chatId,messageId, language);  // Відображаємо обрані вакансії
            } else if (callbackData.startsWith("add_to_favorites_")) {
                String jobId = callbackData.split("_")[3];
                addToFavorites(chatId,messageId, jobId, language);  // Додаємо вакансію до обраних
            } else if (callbackData.equals("back_to_menu")) {
                sendMainMenu(chatId, messageId, language);  // Повертаємося до головного меню
            } else if (callbackData.equals("find_job")) {
                inFavorites = 0;
                sendGenderSelection(chatId, messageId, language);
            } else if (callbackData.startsWith("male_") || callbackData.startsWith("female_")) {
                String[] data = callbackData.split("_");
                String gender = data[0];
                previousGender = gender;
                sendCitySelection(chatId, messageId, gender, language);  // Показуємо вибір міст для пошуку
            } else if (callbackData.startsWith("city_")) {
                String[] data = callbackData.split("_");
                String city = data[1];
                String gender = data[2];
                previousGender = gender;
                previousCity = city;
                // Оновлюємо список міст
                sendCitySelection(chatId, messageId, gender, language);
            } else if (callbackData.equals("start_search")) {
                // Запускаємо пошук за вибраними містами
                HashSet<String> cities = selectedCities.getOrDefault(chatId, new HashSet<>());

                if (cities.isEmpty()) {
                    sendErrorMessage(chatId, messageId, "Оберіть хоча б одне місто для пошуку");
                } else {
                    performSearch(chatId, messageId, cities, previousGender, language);  // Пошук за вибраними містами
                }
            } else if (callbackData.startsWith("job_")) {
                String[] data = callbackData.split("_");
                String jobId = data[1];
                showFullJobDescription(chatId, messageId, jobId, language);  // Відправляємо повний опис вакансії
            }
            // Обробка кнопки "Зв'язатися із роботодавцем"
            else if (callbackData.startsWith("contact_employer_")) {
                String jobId = callbackData.split("_")[2];
                contactEmployer(chatId, jobId, language);  // Виклик методу для зв'язку з роботодавцем
            }
            // Обробка кнопки "Назад" до списку вакансій
            else if (callbackData.equals("back_to_job_list")) {
                HashSet<String> cities = selectedCities.getOrDefault(chatId, new HashSet<>());

                if (cities.isEmpty()) {
                    sendErrorMessage(chatId, messageId, "Оберіть хоча б одне місто для пошуку");
                } else {
                    performSearch(chatId, messageId, cities, previousGender, language);  // Пошук за вибраними містами
                }// Повернення до списку вакансій
            } else if (callbackData.equals("back_to_city_selection")) {
                // Повертаємо користувача до вибору міста
                sendCitySelection(chatId, messageId, previousGender, language);
            } else if (callbackData.equals("back_to_gender_selection")) {
                // Повертаємо користувача до вибору гендера
                sendGenderSelection(chatId, messageId, language);
            } else if (callbackData.equals("back_to_main_menu")) {
                sendMainMenu(chatId, messageId, language);  // Повертаємося до головного меню
            } else if (callbackData.startsWith("remove_from_favorites_")) {
                String jobId = callbackData.split("_")[3];
                removeFromFavorites(chatId, messageId,jobId, language);  // Видаляємо вакансію з обраних
            } else if (callbackData.startsWith("back_to_favorites")) {
                showFavorites(chatId,messageId,language);  // Повертаємо користувача до списку обраних вакансій
            } else if (callbackData.equals("accommodation_jobs")) {
                inFavorites = 2;
                sendGenderSelectionForAccommodation(chatId, messageId, language);  // Показуємо вибір гендеру для вакансій із житлом
            } else if (callbackData.startsWith("accommodation_gender_")) {
                String gender = callbackData.split("_")[2];
                previousGender = gender;
                showAccommodationJobsByGender(chatId, messageId, gender, language);  // Показуємо вакансії з проживанням для вибраного гендеру
            }else if (callbackData.startsWith("add_city_")) {
                String city = callbackData.split("_")[2];
                selectedCities.putIfAbsent(chatId, new HashSet<>());
                selectedCities.get(chatId).add(city);

                // Після додавання оновлюємо відображення кнопок
                sendCitySelection(chatId, messageId, previousGender, language);
            } else if (callbackData.startsWith("remove_city_")) {
                String city = callbackData.split("_")[2];
                selectedCities.get(chatId).remove(city);

                // Після видалення оновлюємо відображення кнопок
                sendCitySelection(chatId, messageId, previousGender, language);
            }

        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            // Обробляємо тільки перший запит на /start
            if (messageText.equals("/start")) {
                String firstName = update.getMessage().getFrom().getFirstName();
                String lastName = update.getMessage().getFrom().getLastName();
                String username = update.getMessage().getFrom().getUserName();

                // Виклик методу для надсилання нікнейму адміністратору
                notifyAdminAboutNewUser(username, firstName, lastName);

                long currentTime = System.currentTimeMillis();

                // Якщо користувач вже викликав команду раніше, перевіряємо інтервал
                if (lastStartCommandTime.containsKey(chatId)) {
                    long lastTime = lastStartCommandTime.get(chatId);

                    // Якщо з часу останньої команди пройшло менше ніж 5 секунд (5000 мс)
                    if (currentTime - lastTime < 5000) {
                        // Видаляємо повідомлення користувача
                        deleteUserMessage(chatId, message.getMessageId());
                        return;
                    }
                }

                // Оновлюємо час виклику команди /start
                lastStartCommandTime.put(chatId, currentTime);

                // Обробляємо команду /start і відправляємо відповідь
                sendLanguageSelectionMessage(chatId);

                // Видаляємо повідомлення користувача після його обробки
            }
        }
    }

    private void showFavorites(long chatId, int messageId, String language) {
        // Отримуємо список обраних вакансій для користувача
        HashSet<String> favorites = favoriteVacancies.get(chatId);

        // Якщо список обраних порожній, відправляємо відповідне повідомлення
        if (favorites == null || favorites.isEmpty()) {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId);
            message.setMessageId(messageId);  // Редагуємо існуюче повідомлення
            message.setText(language.equals("ua") ? "--- Ваш список обраних порожній ---" : "---- Ваш список избранного пуст ----");

            // Додаємо кнопку "Головне меню"
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
            mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
            mainMenuButton.setCallbackData("main_menu");

            rowsInline.add(Collections.singletonList(mainMenuButton));
            markupInline.setKeyboard(rowsInline);

            message.setReplyMarkup(markupInline);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else {
            StringBuilder responseText = new StringBuilder(language.equals("ua") ? "---------- Обрані вакансії ----------" : "-------- Избранные вакансии ---------");

            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            // Додаємо кожну вакансію до повідомлення та створюємо кнопки для перегляду детальної інформації
            for (String jobId : favorites) {
                Vacancies vacancy = findVacancyById(jobId);  // Метод для пошуку вакансії за ID

                if (vacancy != null) {

                    InlineKeyboardButton jobButton = new InlineKeyboardButton();
                    jobButton.setText(vacancy.getShortDescription());
                    jobButton.setCallbackData("job_" + jobId + "_" + language);

                    rowsInline.add(Collections.singletonList(jobButton));
                }
            }

            // Кнопка для повернення назад
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад ⬅\uFE0F");
            backButton.setCallbackData("main_menu"); // Можна змінити callback на "back_to_menu", якщо є інша логіка
            rowsInline.add(Collections.singletonList(backButton));

            // Кнопка для повернення до головного меню
            InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
            mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
            mainMenuButton.setCallbackData("main_menu");
            rowsInline.add(Collections.singletonList(mainMenuButton));

            markupInline.setKeyboard(rowsInline);

            EditMessageText message = new EditMessageText();
            message.setChatId(chatId);
            message.setMessageId(messageId);  // Редагуємо існуюче повідомлення
            message.setText(responseText.toString());
            message.setReplyMarkup(markupInline);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }




    private void contactEmployer(long chatId, String jobId, String language) {
    }

    private void addToFavorites(long chatId, int messageId, String jobId, String language) {
        // Додаємо вакансію до обраних
        favoriteVacancies.putIfAbsent(chatId, new HashSet<>());
        favoriteVacancies.get(chatId).add(jobId);

        // Зберігаємо зміни у файл
        FavoritesManager.saveFavorites(favoriteVacancies);

        // Оновлюємо клавіатуру
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Додаємо кнопку "Видалити з обраних"
        InlineKeyboardButton removeFromFavoritesButton = new InlineKeyboardButton();
        removeFromFavoritesButton.setText(language.equals("ua") ? "Видалити з обраних ❌" : "Удалить из избранного ❌");
        removeFromFavoritesButton.setCallbackData("remove_from_favorites_" + jobId);

        rowsInline.add(Collections.singletonList(removeFromFavoritesButton));

        // Інші кнопки ("Назад", "Головне меню", тощо)
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F");
        backButton.setCallbackData(inFavorites == 1? "back_to_favorites" : "back_to_job_list");

        rowsInline.add(Collections.singletonList(backButton));

        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(mainMenuButton));

        // Кнопка "Звʼязатися"
        InlineKeyboardButton contactButton = new InlineKeyboardButton();
        contactButton.setText(language.equals("ua") ? "Звʼязатися \uD83D\uDCDE" : "Связаться \uD83D\uDCDE");
        contactButton.setCallbackData("contact_employer_" + jobId);
        contactButton.setUrl("tg://resolve?domain=anastasia_pulsar_work");

        rowsInline.add(Collections.singletonList(contactButton));

        markupInline.setKeyboard(rowsInline);

        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(markupInline);

        try {
            execute(editMarkup);  // Оновлюємо клавіатуру
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }




    // Метод для відправки повідомлення з кнопками для вибору мови
    private void sendLanguageSelectionMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("----------- Мова | Язык -------------");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        InlineKeyboardButton uaButton = new InlineKeyboardButton();
        uaButton.setText(" \uD83C\uDDFA\uD83C\uDDE6 Українська");
        uaButton.setCallbackData("ua");

        InlineKeyboardButton ruButton = new InlineKeyboardButton();
        ruButton.setText("Русский \uD83C\uDDF7\uD83C\uDDFA");
        ruButton.setCallbackData("ru");

        rowInline.add(uaButton);
        rowInline.add(ruButton);

        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            lastMessageId = execute(message).getMessageId();  // Зберігаємо ID повідомлення
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для відправки інструкції з кнопкою "ОК"
    private void sendInstructionWithOkButton(long chatId, int messageId, String instruction) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);  // Редагуємо останнє повідомлення
        message.setText(instruction);

        // Створюємо кнопку "ОК"
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        InlineKeyboardButton okButton = new InlineKeyboardButton();
        okButton.setText("ОК");
        okButton.setCallbackData("ok");

        rowInline.add(okButton);
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);  // Редагуємо повідомлення, додаючи кнопку "ОК"
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMainMenu(long chatId, int messageId, String language) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        if(language.equals("ua")){
            message.setText("------------ Головне меню -----------");// Редагуємо останнє повідомлення
        }else{
            message.setText("------------ Главное меню -----------");// Редагуємо останнє повідомлення
        }

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Кнопки головного меню
        InlineKeyboardButton findJobButton = new InlineKeyboardButton();
        findJobButton.setText(language.equals("ua") ? "Знайти роботу \uD83D\uDD0D" : "Найти работу \uD83D\uDD0D");
        findJobButton.setCallbackData("find_job");

        InlineKeyboardButton accommodationButton = new InlineKeyboardButton();
        accommodationButton.setText(language.equals("ua") ? "Робота із житлом \uD83C\uDFE0\n" : "Работа с жильем \uD83C\uDFE0\n");
        accommodationButton.setCallbackData("accommodation_jobs");

        // Кнопка "Мої вподобання"
        InlineKeyboardButton favoritesButton = new InlineKeyboardButton();
        favoritesButton.setText(language.equals("ua") ? "Мої вподобання ❤\uFE0F" : "Мои избранные ❤\uFE0F");
        favoritesButton.setCallbackData("show_favorites");

        InlineKeyboardButton contactButton = new InlineKeyboardButton();
        contactButton.setText(language.equals("ua") ? "Зв'язатися \uD83D\uDCDE" : "Связаться \uD83D\uDCDE");
        contactButton.setCallbackData("contact");
        contactButton.setUrl("tg://resolve?domain=anastasia_pulsar_work");

        // Додаємо кнопки до рядків
        rowsInline.add(Collections.singletonList(findJobButton));
        rowsInline.add(Collections.singletonList(accommodationButton));
        rowsInline.add(Collections.singletonList(favoritesButton));
        rowsInline.add(Collections.singletonList(contactButton));

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void sendGenderSelection(long chatId, int messageId, String language) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        message.setText(language.equals("ua") ? "------------ Оберіть стать ----------" : "------------ Выберите пол -----------");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Кнопки гендеру
        InlineKeyboardButton maleButton = new InlineKeyboardButton();
        maleButton.setText(language.equals("ua") ? "Чоловік \uD83D\uDC68" : "Мужчина \uD83D\uDC68");
        maleButton.setCallbackData("male_" + language);

        InlineKeyboardButton femaleButton = new InlineKeyboardButton();
        femaleButton.setText(language.equals("ua") ? "Жінка \uD83D\uDC69" : "Женщина \uD83D\uDC69");
        femaleButton.setCallbackData("female_" + language);

        // Кнопка "Назад" для повернення до головного меню
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F");
        backButton.setCallbackData("back_to_main_menu");


        // Додаємо кнопки в рядки
        rowsInline.add(Arrays.asList(maleButton, femaleButton));
        rowsInline.add(Collections.singletonList(backButton));
        
        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(mainMenuButton));// Додаємо кнопку "Назад"

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }




    private void sendJobList(long chatId, int messageId, String city, String gender, String language) {
        previousCity = city;
        previousGender = gender;
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        message.setText(language.equals("ua") ? "---- Список доступних вакансій ----" : "---- Список доступных вакансий ----");

        List<Vacancies> jobs = VacancyManager.searchVacancies(gender, city,language);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (Vacancies job : jobs) {
            InlineKeyboardButton jobButton = new InlineKeyboardButton();
            jobButton.setText(job.getShortDescription());
            jobButton.setCallbackData("job_" + job.getId() + "_" + language);

            rowsInline.add(Collections.singletonList(jobButton));
        }

        // Кнопка "Назад"
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F");
        backButton.setCallbackData("back_to_city_selection");  // Повертаємося до вибору міста

        // Кнопка "Головне меню"
        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(backButton));  // Додаємо кнопку "Назад"
        rowsInline.add(Collections.singletonList(mainMenuButton));  // Додаємо кнопку "Головне меню"

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void sendCitySelection(long chatId, int messageId, String gender, String language) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        message.setText(language.equals("ua") ? "- Оберіть місто для пошуку роботи -" : " Выберите город для поиска работы ");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Кнопки міст з урахуванням їхнього вибору
        boolean isWarsawSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("warsaw");
        boolean isWroclawSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("wroclaw");
        boolean isBrwinowSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("brwinow");
        boolean isLodzSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("lodz");
        boolean isStrykowSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("strykow");
        boolean isSwedowSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("swedow");
        boolean isBlonieSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("blonie");
        boolean isKopytowSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("kopytow");
        boolean isWiskitkiSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("wiskitki");
        boolean isСzestochowaSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("czestochowa");
        boolean isRawaSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("rawa");
        boolean isRadomskoSelected = selectedCities.containsKey(chatId) && selectedCities.get(chatId).contains("radomsko");


        // Кнопка для Варшави
        InlineKeyboardButton city1Button = new InlineKeyboardButton();
        city1Button.setText(isWarsawSelected ? "Варшава ✅" : "Варшава");
        city1Button.setCallbackData(isWarsawSelected ? "remove_city_warsaw_" + gender : "add_city_warsaw_" + gender);

        // Кнопка для Кракова
        InlineKeyboardButton city2Button = new InlineKeyboardButton();
        city2Button.setText(isWroclawSelected ? ("Вроцлав ✅") : ("Вроцлав"));
        city2Button.setCallbackData(isWroclawSelected ? "remove_city_wroclaw_" + gender : "add_city_wroclaw_" + gender);

        InlineKeyboardButton city3Button = new InlineKeyboardButton();
        city3Button.setText(isBrwinowSelected ? (language.equals("ua") ? "Брвінув ✅" : "Брвинув ✅") : (language.equals("ua") ? "Брвінув" : "Брвинув"));
        city3Button.setCallbackData(isBrwinowSelected ? "remove_city_brwinow_" + gender : "add_city_brwinow_" + gender);


        InlineKeyboardButton city4Button = new InlineKeyboardButton();
        city4Button.setText(isLodzSelected ? ("Лодзь ✅") : ("Лодзь"));
        city4Button.setCallbackData(isLodzSelected ? "remove_city_lodz_" + gender : "add_city_lodz_" + gender);

        InlineKeyboardButton city5Button = new InlineKeyboardButton();
        city5Button.setText(isStrykowSelected? (language.equals("ua") ? "Стрикув ✅" : "Стрыкув ✅") : (language.equals("ua") ? "Стрикув" : "Стрыкув"));
        city5Button.setCallbackData(isStrykowSelected ? "remove_city_strykow_" + gender : "add_city_strykow_" + gender);

        InlineKeyboardButton city6Button = new InlineKeyboardButton();
        city6Button.setText(isSwedowSelected? ("Свендув ✅") : ("Свендув"));
        city6Button.setCallbackData(isSwedowSelected ? "remove_city_swedow_" + gender : "add_city_swedow_" + gender);

        InlineKeyboardButton city7Button = new InlineKeyboardButton();
        city7Button.setText(isBlonieSelected? ("Блоне ✅") : ("Блоне"));
        city7Button.setCallbackData(isBlonieSelected ? "remove_city_blonie_" + gender : "add_city_blonie_" + gender);

        InlineKeyboardButton city8Button = new InlineKeyboardButton();
        city8Button.setText(isKopytowSelected? (language.equals("ua") ? "Копитув ✅" : "Копытув ✅") : (language.equals("ua") ? "Копитув" : "Копытув"));
        city8Button.setCallbackData(isKopytowSelected ? "remove_city_kopytow_" + gender : "add_city_kopytow_" + gender);

        InlineKeyboardButton city9Button = new InlineKeyboardButton();
        city9Button.setText(isWiskitkiSelected? (language.equals("ua") ? "Віскітки ✅" : "Вискитки ✅") : (language.equals("ua") ? "Віскітки" : "Вискитки"));
        city9Button.setCallbackData(isWiskitkiSelected ? "remove_city_wiskitki_" + gender : "add_city_wiskitki_" + gender);

        InlineKeyboardButton city10Button = new InlineKeyboardButton();
        city10Button.setText(isСzestochowaSelected? ("Ченстохова ✅") : "Ченстохова");
        city10Button.setCallbackData(isСzestochowaSelected ? "remove_city_czestochowa_" + gender : "add_city_czestochowa_" + gender);

        InlineKeyboardButton city11Button = new InlineKeyboardButton();
        city11Button.setText(isRawaSelected? (language.equals("ua") ? "Рава-Мазовецька ✅" : "Рава-Мазовецкая ✅") : (language.equals("ua") ? "Рава-Мазовецька" : "Рава-Мазовецкая"));
        city11Button.setCallbackData(isRawaSelected ? "remove_city_rawa_" + gender : "add_city_rawa_" + gender);

        InlineKeyboardButton city12Button = new InlineKeyboardButton();
        city12Button.setText(isRadomskoSelected? (language.equals("ua") ? "Радомсько ✅" : "Радомско ✅") : (language.equals("ua") ? "Радомсько" : "Радомско"));
        city12Button.setCallbackData(isRadomskoSelected ? "remove_city_radomsko_" + gender : "add_city_radomsko_" + gender);

        // Додаємо кнопки міст до рядка
        rowsInline.add(Arrays.asList(city1Button, city2Button));
        rowsInline.add(Arrays.asList(city3Button, city4Button));
        rowsInline.add(Arrays.asList(city5Button, city6Button));
        rowsInline.add(Arrays.asList(city7Button, city8Button));
        rowsInline.add(Arrays.asList(city9Button, city10Button));
        rowsInline.add(Arrays.asList(city11Button, city12Button));

        // Кнопка "Пошук"
        InlineKeyboardButton searchButton = new InlineKeyboardButton();
        searchButton.setText(language.equals("ua") ? "Пошук 🔍" : "Поиск 🔍");
        searchButton.setCallbackData("start_search");

        rowsInline.add(Collections.singletonList(searchButton));

        // Кнопка "Назад" для повернення до вибору гендера
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F");
        backButton.setCallbackData("back_to_gender_selection");

        rowsInline.add(Collections.singletonList(backButton));

        // Кнопка "Головне меню"
        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(mainMenuButton)); // Додаємо кнопку "Головне меню"

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }








    // Метод для відправки повного опису вакансії
    // Метод для редагування повідомлення з повним описом вакансії
    private void showFullJobDescription(long chatId, int messageId, String jobId, String language) {
        // Шукаємо вакансію за її унікальним ID
        Vacancies vacancy = findVacancyById(jobId);

        if (vacancy != null) {
            // Редагуємо існуюче повідомлення з повним описом вакансії
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId);
            message.setMessageId(messageId);  // Використовуємо ID існуючого повідомлення
            message.setText(language.equals("ua") ? "Опис вакансії:\n" + vacancy.getFullDescription()
                    : "Описание вакансии:\n" + vacancy.getFullDescription());

            // Додаємо кнопки "Назад", "Головне меню", і "Видалити з обраних" або "Додати до обраних"
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            // Якщо вакансія є в обраних, змінюємо кнопку на "Видалити з обраних"
            if (favoriteVacancies.containsKey(chatId) && favoriteVacancies.get(chatId).contains(jobId)) {
                // Кнопка "Видалити з обраних"
                InlineKeyboardButton removeFromFavoritesButton = new InlineKeyboardButton();
                removeFromFavoritesButton.setText(language.equals("ua") ? "Видалити з обраних ❌" : "Удалить из избранного ❌");
                removeFromFavoritesButton.setCallbackData("remove_from_favorites_" + jobId);

                rowsInline.add(Collections.singletonList(removeFromFavoritesButton));
            } else {
                // Якщо вакансії немає в обраних, додаємо кнопку "Додати до обраних"
                InlineKeyboardButton addToFavoritesButton = new InlineKeyboardButton();
                addToFavoritesButton.setText(language.equals("ua") ? "Додати до обраних ❤\uFE0F" : "Добавить в избранное ❤\uFE0F");
                addToFavoritesButton.setCallbackData("add_to_favorites_" + jobId);

                rowsInline.add(Collections.singletonList(addToFavoritesButton));
            }

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад ⬅\uFE0F");
            if(inFavorites == 0){
                backButton.setCallbackData("back_to_job_list");
            }else if(inFavorites == 1){
                backButton.setCallbackData("back_to_favorites");
            }else{
                backButton.setCallbackData("accommodation_gender_" + previousGender);
            }

            rowsInline.add(Collections.singletonList(backButton));

            // Кнопка "Головне меню"
            InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
            mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
            mainMenuButton.setCallbackData("main_menu");

            rowsInline.add(Collections.singletonList(mainMenuButton));

            // Кнопка "Звʼязатися"
            InlineKeyboardButton contactButton = new InlineKeyboardButton();
            contactButton.setText(language.equals("ua") ? "Звʼязатися \uD83D\uDCDE" : "Связаться \uD83D\uDCDE");
            contactButton.setCallbackData("contact_employer_" + jobId);
            contactButton.setUrl("tg://resolve?domain=anastasia_pulsar_work");

            rowsInline.add(Collections.singletonList(contactButton));


            markupInline.setKeyboard(rowsInline);
            message.setReplyMarkup(markupInline);

            try {
                execute(message);  // Редагуємо повідомлення
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else {
            // Якщо вакансія не знайдена
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId);
            message.setMessageId(messageId);
            message.setText(language.equals("ua") ? "-------- Вакансія не знайдена -------" : "-------- Вакансия не найдена --------");

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }


    // Метод для пошуку вакансії за унікальним ID
    private Vacancies findVacancyById(String jobId) {
        // Шукаємо вакансію за її ID в списку вакансій
        if(language.equals("ua"))
        for (Vacancies vacancy : VacancyManager.vacanciesListUa) {
            if (vacancy.getId().equals(jobId)) {
                return vacancy;  // Якщо знайдена вакансія, повертаємо її
            }
        }else {
            for (Vacancies vacancy : VacancyManager.vacanciesListRu) {
                if (vacancy.getId().equals(jobId)) {
                    return vacancy;  // Якщо знайдена вакансія, повертаємо її
                }
            }
        }
        return null;  // Якщо вакансія не знайдена, повертаємо null
    }

    private void removeFromFavorites(long chatId, int messageId, String jobId, String language) {
        // Видаляємо вакансію з обраних
        if (favoriteVacancies.containsKey(chatId)) {
            favoriteVacancies.get(chatId).remove(jobId);

            // Якщо список обраних порожній, видаляємо користувача із HashMap
            if (favoriteVacancies.get(chatId).isEmpty()) {
                favoriteVacancies.remove(chatId);
            }

            // Зберігаємо зміни у файл
            FavoritesManager.saveFavorites(favoriteVacancies);
        }

        // Оновлюємо клавіатуру
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Додаємо кнопку "Додати до обраних"
        InlineKeyboardButton addToFavoritesButton = new InlineKeyboardButton();
        addToFavoritesButton.setText(language.equals("ua") ? "Додати до обраних ❤\uFE0F" : "Добавить в избранное ❤\uFE0F");
        addToFavoritesButton.setCallbackData("add_to_favorites_" + jobId);

        rowsInline.add(Collections.singletonList(addToFavoritesButton));

        // Інші кнопки ("Назад", "Головне меню", тощо)
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F");
        backButton.setCallbackData(inFavorites == 1 ? "back_to_favorites" : "back_to_job_list");

        rowsInline.add(Collections.singletonList(backButton));

        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(mainMenuButton));

        // Кнопка "Звʼязатися"
        InlineKeyboardButton contactButton = new InlineKeyboardButton();
        contactButton.setText(language.equals("ua") ? "Звʼязатися \uD83D\uDCDE" : "Связаться \uD83D\uDCDE");
        contactButton.setCallbackData("contact_employer_" + jobId);
        contactButton.setUrl("tg://resolve?domain=anastasia_pulsar_work");

        rowsInline.add(Collections.singletonList(contactButton));

        markupInline.setKeyboard(rowsInline);

        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(markupInline);

        try {
            execute(editMarkup);  // Оновлюємо клавіатуру
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void deleteUserMessage(long chatId, int messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);

        try {
            execute(deleteMessage);  // Видаляємо повідомлення
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showAccommodationJobs(long chatId, int messageId, String language) {
        List<Vacancies> jobsWithAccommodation = new ArrayList<>();

        // Фільтруємо вакансії, у яких з житлом
        if(language.equals("ua")) {
            for (Vacancies job : VacancyManager.vacanciesListUa) {
                if (job.isWithAccommodation()) {
                    jobsWithAccommodation.add(job);
                }
            }
        }else{
            for (Vacancies job : VacancyManager.vacanciesListRu) {
                if (job.isWithAccommodation()) {
                    jobsWithAccommodation.add(job);
                }
            }
        }

        // Формуємо повідомлення для відображення
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        message.setText(language.equals("ua") ? "--------- Вакансії з житлом ---------" : "--------- Вакансии с жильем ---------");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (Vacancies job : jobsWithAccommodation) {
            InlineKeyboardButton jobButton = new InlineKeyboardButton();
            jobButton.setText(job.getShortDescription());
            jobButton.setCallbackData("job_" + job.getId() + "_" + language);
            rowsInline.add(Collections.singletonList(jobButton));
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F");
        backButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(backButton));

        // Кнопка для повернення до головного меню
        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(mainMenuButton));
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendGenderSelectionForAccommodation(long chatId, int messageId, String language) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        message.setText(language.equals("ua") ? "------------ Оберіть стать ----------" : "------------ Выберите пол -----------");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Кнопки гендеру
        InlineKeyboardButton maleButton = new InlineKeyboardButton();
        maleButton.setText(language.equals("ua") ? "Чоловік \uD83D\uDC68" : "Мужчина \uD83D\uDC68");
        maleButton.setCallbackData("accommodation_gender_male");

        InlineKeyboardButton femaleButton = new InlineKeyboardButton();
        femaleButton.setText(language.equals("ua") ? "Жінка \uD83D\uDC69" : "Женщина \uD83D\uDC69");
        femaleButton.setCallbackData("accommodation_gender_female");

        rowsInline.add(Arrays.asList(maleButton, femaleButton));

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F");
        backButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(backButton));
        // Додаємо кнопку для повернення до головного меню
        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(mainMenuButton));

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showAccommodationJobsByGender(long chatId, int messageId, String gender, String language) {
        List<Vacancies> jobsWithAccommodation = new ArrayList<>();

        // Фільтруємо вакансії, у яких з житлом та підходять за гендером
        if (language.equals("ua")) {
            for (Vacancies job : VacancyManager.vacanciesListUa) {
                if (job.isWithAccommodation() && job.isSuitableForGender(gender) ) {
                    jobsWithAccommodation.add(job);
                }
            }
        } else {
            for (Vacancies job : VacancyManager.vacanciesListRu) {
                if (job.isWithAccommodation() && job.isSuitableForGender(gender) ) {
                    jobsWithAccommodation.add(job);
                }
            }
        }

            // Формуємо повідомлення для відображення
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId);
            message.setMessageId(messageId);
            message.setText(language.equals("ua") ? "--------- Вакансії з житлом ---------" : "--------- Вакансии с жильем ---------");

            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            for (Vacancies job : jobsWithAccommodation) {
                InlineKeyboardButton jobButton = new InlineKeyboardButton();
                jobButton.setText(job.getShortDescription());
                jobButton.setCallbackData("job_" + job.getId() + "_" + language);
                rowsInline.add(Collections.singletonList(jobButton));
            }

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад ⬅\uFE0F\n");
            backButton.setCallbackData("accommodation_jobs");

            rowsInline.add(Collections.singletonList(backButton));

            // Додаємо кнопку для повернення до головного меню
            InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
            mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
            mainMenuButton.setCallbackData("main_menu");

            rowsInline.add(Collections.singletonList(mainMenuButton));
            markupInline.setKeyboard(rowsInline);
            message.setReplyMarkup(markupInline);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }


    private void performSearch(long chatId, int messageId, HashSet<String> cities, String gender, String language) {
        // Отримуємо список всіх вакансій
        List<Vacancies> allVacancies = new ArrayList<>();
        if(language.equals("ua")){
            allVacancies = VacancyManager.vacanciesListUa;
        }else {
            allVacancies = VacancyManager.vacanciesListRu;
        };  // Використовуємо ваш менеджер вакансій

        // Фільтруємо вакансії на основі вибраного гендера та міст
        List<Vacancies> filteredVacancies = new ArrayList<>();
        for (Vacancies vacancy : allVacancies) {
            if (vacancy.isSuitableForGender(gender) && cities.contains(vacancy.getCity().toLowerCase())) {
                filteredVacancies.add(vacancy);  // Додаємо вакансію до результатів пошуку
            }
        }

        // Формуємо результати пошуку
        StringBuilder searchResult = new StringBuilder();
        if (filteredVacancies.isEmpty()) {
            searchResult.append(language.equals("ua") ? "-------- Вакансії не знайдено -------" : "-------- Вакансии не найдены --------");
        } else {
            searchResult.append(language.equals("ua") ? "--------- Доступні вакансії ---------" : "-------- Доступные вакансии --------");
        }

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);
        message.setText(searchResult.toString());

        // Створюємо клавіатуру для вакансій
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        // Створюємо кнопку для кожної знайденої вакансії
        for (Vacancies vacancy : filteredVacancies) {
            InlineKeyboardButton jobButton = new InlineKeyboardButton();
            jobButton.setText(vacancy.getShortDescription());  // Короткий опис вакансії як текст кнопки
            jobButton.setCallbackData("job_" + vacancy.getId());  // Використовуємо ID вакансії для callbackData

            rowsInline.add(Collections.singletonList(jobButton));  // Додаємо кнопку як окремий рядок
        }

        // Кнопка "Назад" для повернення до попереднього меню
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад ⬅\uFE0F\n");
        backButton.setCallbackData("back_to_city_selection");

        // Кнопка "Головне меню"
        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText(language.equals("ua") ? "Головне меню \uD83D\uDCCB" : "Главное меню \uD83D\uDCCB");
        mainMenuButton.setCallbackData("main_menu");

        // Додаємо кнопки до клавіатури
        rowsInline.add(Collections.singletonList(backButton));  // Додаємо кнопку "Назад"
        rowsInline.add(Collections.singletonList(mainMenuButton));  // Додаємо кнопку "Головне меню"

        // Додаємо клавіатуру до повідомлення
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);  // Надсилаємо результати пошуку
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void sendErrorMessage(long chatId, int messageId, String errorMessage) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(messageId);  // Використовуємо ID існуючого повідомлення
        message.setText(errorMessage);  // Встановлюємо текст помилки

        // Можна додати кнопку повернення до головного меню або назад
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Головне меню");
        backButton.setCallbackData("main_menu");

        rowsInline.add(Collections.singletonList(backButton));
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);  // Надсилаємо повідомлення з помилкою
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void notifyAdminAboutNewUser(String username, String firstName, String lastName) {
        StringBuilder messageText = new StringBuilder("Новий користувач запустив бота:\n");
        if (username != null) {
            messageText.append("Нікнейм: @").append(username).append("\n");
        }
        if (firstName != null) {
            messageText.append("Ім'я: ").append(firstName).append(" ");
        }
        if (lastName != null) {
            messageText.append(lastName);
        }

        SendMessage message = new SendMessage();
        message.setChatId(ADMIN_CHAT_ID);  // ID адміністратора
        message.setText(messageText.toString());

        try {
            execute(message);  // Надсилаємо повідомлення адміністратору
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendFavoritesFileToAdmin(long chatId) {
        try {
            File file = new File("src/main/resources/favorites.dat");
            if (file.exists()) {
                SendDocument sendDocument = new SendDocument();
                sendDocument.setChatId(chatId);
                sendDocument.setDocument(new InputFile(file));
                execute(sendDocument);
            } else {
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText("Файл не знайдено.");
                execute(message);
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
