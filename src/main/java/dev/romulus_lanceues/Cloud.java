package dev.romulus_lanceues;

import com.google.genai.Client;
import dev.romulus_lanceues.ai.ChatService;
import dev.romulus_lanceues.ai.GeminiChatService;
import dev.romulus_lanceues.config.Config;
import dev.romulus_lanceues.discord.SlashCommandHandler;
import dev.romulus_lanceues.discord.SlashCommandListener;
import dev.romulus_lanceues.discord.commands.AskCommand;
import dev.romulus_lanceues.discord.commands.CloudCommand;
import dev.romulus_lanceues.discord.commands.ConversationCommand;
import dev.romulus_lanceues.util.RateLimiter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

public class Cloud {

    private static final Logger LOG = LoggerFactory.getLogger(Cloud.class);

    public static void main(String[] args) {
        Config config;
        try {
            config = Config.fromEnv();
        } catch (IllegalStateException e) {
            LOG.error("Failed to start Cloud bot: {}", e.getMessage());
            return;
        }

        Client client = Client.builder().apiKey(config.geminiConfig().apiKey()).build();
        ChatService chatService = new GeminiChatService(client, config.geminiConfig());
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5));

        List<SlashCommandHandler> commandHandlers = List.of(
                new CloudCommand(),
                new AskCommand(chatService, rateLimiter),
                new ConversationCommand()
        );

        SlashCommandListener slashCommandListener = new SlashCommandListener(commandHandlers);

        JDA jda = JDABuilder.createLight(config.botToken(), GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .disableCache(EnumSet.allOf(CacheFlag.class))
                .addEventListeners(slashCommandListener)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down Cloud bot...");
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                    LOG.warn("JDA did not shutdown within 10 seconds, forcing shutdown...");
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while waiting for JDA shutdown, forcing shutdown...");
                jda.shutdownNow();
                Thread.currentThread().interrupt();
            }
            try {
                client.close();
            } catch (Exception e) {
                LOG.warn("Error closing Gemini client", e);
            }
        }, "cloud-shutdown-hook"));

        try {
            jda.awaitReady();
            LOG.info("Connected as {} (ID: {}) across {} guilds",
                    jda.getSelfUser().getAsTag(),
                    jda.getSelfUser().getId(),
                    jda.getGuildCache().size());

            jda.updateCommands()
                    .addCommands(slashCommandListener.getCommandData())
                    .queue(
                            commands -> LOG.info("Registered {} slash commands with Discord", commands.size()),
                            error -> LOG.error("Failed to register slash commands", error)
                    );
        } catch (InterruptedException e) {
            LOG.error("Interrupted while waiting for JDA to be ready", e);
            Thread.currentThread().interrupt();
        }
    }
}
