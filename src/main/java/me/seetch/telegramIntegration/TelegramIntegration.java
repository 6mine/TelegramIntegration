package me.seetch.telegramIntegration;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

public final class TelegramIntegration extends JavaPlugin {

    private TelegramBot bot;
    private final Map<String, Map<Integer, Queue<String>>> messageQueues = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        bot = new TelegramBot.Builder(getConfig().getString("token")).build();
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                handleUpdate(update);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        scheduler = Executors.newScheduledThreadPool(1);
        startScheduler();
    }

    @Override
    public void onDisable() {
        if (bot != null) {
            bot.removeGetUpdatesListener();
        }

        stopScheduler();
    }

    public void addMessage(String chatId, Integer messageThreadId, String message) {
        String timestamp = LocalDateTime.now(ZoneId.of("Europe/Moscow"))
                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"));

        String formattedMessage = "[" + timestamp + "] " + message;

        if (messageThreadId == null) {
            messageQueues.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(0, k -> new ConcurrentLinkedQueue<>())
                    .add(formattedMessage);
        } else {
            messageQueues.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(messageThreadId, k -> new ConcurrentLinkedQueue<>())
                    .add(formattedMessage);
        }
    }


    private void startScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, Map<Integer, Queue<String>>> chatEntry : messageQueues.entrySet()) {
                String chatId = chatEntry.getKey();
                for (Map.Entry<Integer, Queue<String>> threadEntry : chatEntry.getValue().entrySet()) {
                    int messageThreadId = threadEntry.getKey();
                    Queue<String> queue = threadEntry.getValue();
                    StringBuilder messageBuilder = new StringBuilder();
                    while (!queue.isEmpty()) {
                        messageBuilder.append(queue.poll()).append("\n");
                    }
                    if (!messageBuilder.isEmpty()) {
                        sendMessage(chatId, messageThreadId, messageBuilder.toString());
                    }
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private void stopScheduler() {
        scheduler.shutdown();
    }

    private void sendMessage(String chatId, int messageThreadId, String message) {
        SendMessage request = new SendMessage(chatId, message);
        if (messageThreadId != 0) {
            request.messageThreadId(messageThreadId);
        }
        bot.execute(request);
    }

    private void handleUpdate(Update update) {
        if (update.message() != null) {
            Message message = update.message();
            if (message.text() != null && message.text().startsWith("/integration")) {
                String chatId = message.chat().id().toString();
                Integer messageThreadId = message.messageThreadId();
                String response = "💬 Chat ID: " + chatId;
                if (messageThreadId != null) {
                    response += "\n📦 Topic ID: " + messageThreadId;
                }
                sendMessage(chatId, messageThreadId != null ? messageThreadId : 0, response);
            }
        }
    }
}
