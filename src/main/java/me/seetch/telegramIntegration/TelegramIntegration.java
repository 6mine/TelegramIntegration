package me.seetch.telegramIntegration;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TelegramIntegration extends JavaPlugin {

    private TelegramBot bot;
    private final Map<String, Map<String, Map<Integer, Queue<String>>>> messageQueues = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        bot = new TelegramBot.Builder(getConfig().getString("token")).build();
        bot.setUpdatesListener(updates -> UpdatesListener.CONFIRMED_UPDATES_ALL);
    }

    @Override
    public void onDisable() {
        if (bot != null) {
            bot.removeGetUpdatesListener();
        }
        sendRemainingMessages();
    }

    public void addMessage(String logType, String chatId, Integer messageThreadId, String message) {
        String timestamp = LocalDateTime.now(ZoneId.of("Europe/Moscow"))
                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"));

        String formattedMessage = "[" + timestamp + "] " + message.replaceAll("([&§])[0-9a-fk-or]", "");

        Queue<String> queue = messageQueues
                .computeIfAbsent(logType, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chatId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(messageThreadId != null ? messageThreadId : 0, k -> new ConcurrentLinkedQueue<>());

        queue.add(formattedMessage);

        if (queue.size() >= 10) {
            sendMessages(logType, chatId, messageThreadId != null ? messageThreadId : 0, queue);
        }
    }

    private void sendMessages(String logType, String chatId, int messageThreadId, Queue<String> queue) {
        StringBuilder messageBuilder = new StringBuilder();
        int count = 0;

        while (!queue.isEmpty() && count < 10) {
            messageBuilder.append(queue.poll()).append("\n");
            count++;
        }

        if (!messageBuilder.isEmpty()) {
            sendMessage(logType, chatId, messageThreadId, messageBuilder.toString());
        }
    }

    private void sendMessage(String logType, String chatId, int messageThreadId, String message) {
        SendMessage request = new SendMessage(chatId, "#" + logType.toUpperCase() + "\n\n" + message);
        if (messageThreadId != 0) {
            request.messageThreadId(messageThreadId);
        }
        bot.execute(request);
    }

    private void sendRemainingMessages() {
        for (Map.Entry<String, Map<String, Map<Integer, Queue<String>>>> logTypeEntry : messageQueues.entrySet()) {
            String logType = logTypeEntry.getKey();
            for (Map.Entry<String, Map<Integer, Queue<String>>> chatIdEntry : logTypeEntry.getValue().entrySet()) {
                String chatId = chatIdEntry.getKey();
                for (Map.Entry<Integer, Queue<String>> messageThreadIdEntry : chatIdEntry.getValue().entrySet()) {
                    int messageThreadId = messageThreadIdEntry.getKey();
                    Queue<String> queue = messageThreadIdEntry.getValue();
                    sendMessages(logType, chatId, messageThreadId, queue);
                }
            }
        }
    }
}
