package com.technoguyfication.admingpt;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

public class AdminGPT extends JavaPlugin implements Listener {

    final int MAX_MESSAGES = 10;
    Pattern responsePattern = Pattern.compile("<([ctp])>\\/?(.*)<\\/[ctp]>");
    
    OpenAiService service;
    LinkedList<ChatMessage> messageHistory = new LinkedList<ChatMessage>();

    String systemPrompt;
    String languageModel;

    @Override
    public void onEnable() {
        FileConfiguration config = this.getConfig();

        // Load config
        String apiKey = config.getString("openai-api-key");
        if (apiKey == null || apiKey.isBlank()) {
            getLogger().severe("No OpenAI API key found in config.yml. Please add one and restart the server.");
            
            // Save default config
            this.saveDefaultConfig();

            // Disable plugin
            this.setEnabled(false);
            return;
        }

        systemPrompt = config.getString("openai-system-prompt");
        languageModel = config.getString("openai-language-model");

        service = new OpenAiService(apiKey, Duration.ofSeconds(15));    // set response timeout

        // Register event listeners
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin disabled
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) throws EventException {

        // Add new message to list
        addChatMessage(new ChatMessage(ChatMessageRole.USER.value(), String.format("%s: %s", event.getPlayer().getName(), event.getMessage())));

        // Make a new list with the system prompt and all messages
        List<ChatMessage> messages = new LinkedList<ChatMessage>();
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
        messages.addAll(messageHistory);

        // Create a chat completion request
        ChatCompletionRequest request = ChatCompletionRequest
            .builder()
            .model(languageModel)
            .messages(messages)
            .build();
        
        getLogger().fine("Sending chat completion request to OpenAI...");
        
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            ChatCompletionResult result = service.createChatCompletion(request);
            ChatMessage responseMessage = result.getChoices().get(0).getMessage();

            getLogger().fine("Received chat completion result from OpenAI.");

            String command = null;
            String thoughts = null;
            String response = null;

            // Run regex on each line of the result
            for (String line : responseMessage.getContent().split("\\r?\\n")) {
                Matcher matcher = responsePattern.matcher(line);
                if (matcher.find()) {
                    switch (matcher.group(1)) {
                        case "c":
                            command = matcher.group(2);
                            getLogger().info(String.format("Command: %s", command));
                            break;
                        case "t":
                            thoughts = matcher.group(2);
                            getLogger().info(String.format("Thoughts: %s", thoughts));
                            break;
                        case "p":
                            response = matcher.group(2);
                            getLogger().info(String.format("Response: %s", response));
                            break;
                        default:
                            getLogger().warning(String.format("Invalid response pattern: %s", line));
                            break;
                    }
                }
            }

            final String finalCommand = command;
            final String finalResponse = response;

            // Run the rest of the code on the main thread
            Bukkit.getScheduler().runTask(this, () -> {

                // add the result to the list of messages
                addChatMessage(responseMessage);

                // Run the command
                if (finalCommand != null && !finalCommand.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                }

                // Broadcast the response
                if (finalResponse != null && !finalResponse.isBlank()) {
                    Bukkit.broadcastMessage(ChatColor.AQUA + String.format("<AdminGPT> %s", finalResponse));   
                }
            });
        });
    }

    private void addChatMessage(ChatMessage message) {
        // Remove oldest message if list is full
        if (messageHistory.size() >= MAX_MESSAGES) {
            messageHistory.removeFirst();
        }

        // Add new message to list
        messageHistory.add(message);
    }

}
