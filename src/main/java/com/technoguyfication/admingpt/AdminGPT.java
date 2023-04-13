package com.technoguyfication.admingpt;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.MultiLineChart;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

    // metrics
    int totalMessages = 0;
    int totalCommands = 0;
    int totalResponses = 0;

    @Override
    public void onEnable() {
        // bStats
        int pluginId = 18196;
        Metrics metrics = new Metrics(this, pluginId);

        FileConfiguration config = this.getConfig();
        InputStream langStream = this.getResource("lang.yml");

        // Load lang.yml
        YamlConfiguration langConfig = new YamlConfiguration();
        try {
            langConfig.load(new InputStreamReader(langStream));

            // Load system prompt from lang.yml
            systemPrompt = langConfig.getString("openai-system-prompt");
        } catch (Exception e) {
            getLogger().severe("Failed to load lang.yml file.");
            e.printStackTrace();

            // Disable plugin
            this.setEnabled(false);
            return;
        }

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

        languageModel = config.getString("openai-language-model");

        // Add bStats charts
        metrics.addCustomChart(new SimplePie("language-model", () -> languageModel));
        metrics.addCustomChart(new MultiLineChart("messages", new Callable<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> call() {
                Map<String, Integer> valueMap = new HashMap<>();
                valueMap.put("total", totalMessages);
                valueMap.put("commands", totalCommands);
                valueMap.put("responses", totalResponses);

                // reset counters
                totalMessages = totalCommands = totalResponses = 0;

                return valueMap;
            }
        }));

        // Create OpenAI service
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

            List<String> commands = new LinkedList<String>();
            List<String> responses = new LinkedList<String>();

            // Run regex on each line of the result
            for (String line : responseMessage.getContent().split("\\r?\\n")) {
                Matcher matcher = responsePattern.matcher(line);
                if (matcher.find()) {
                    switch (matcher.group(1)) {
                        case "c":
                            String command = matcher.group(2);
                            getLogger().info(String.format("Command: %s", command));
                            commands.add(command);
                            break;
                        case "t":
                            String thought = matcher.group(2);
                            getLogger().info(String.format("Thought: %s", thought));
                            break;
                        case "p":
                            String response = matcher.group(2);
                            getLogger().info(String.format("Response: %s", response));
                            responses.add(response);
                            break;
                        default:
                            getLogger().warning(String.format("Invalid response pattern: %s", line));
                            break;
                    }
                }
            }

            // Run the rest of the code on the main thread
            Bukkit.getScheduler().runTask(this, () -> {

                // add the result to the list of messages
                addChatMessage(responseMessage);

                // Run the commands
                for (String command : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }

                // Broadcast response lines
                for (String response : responses) {
                    Bukkit.broadcastMessage(ChatColor.AQUA + String.format("<AdminGPT> %s", response));
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
