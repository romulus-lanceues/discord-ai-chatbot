package dev.romulus_lanceues;

import dev.romulus_lanceues.config.Config;
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

        JDA jda = JDABuilder.createLight(config.botToken(), GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setChunkingFilter(ChunkingFilter.NONE)
                .disableCache(EnumSet.allOf(CacheFlag.class))
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
        }, "cloud-shutdown-hook"));

        try {
            jda.awaitReady();
            LOG.info("Connected as {} (ID: {}) across {} guilds",
                    jda.getSelfUser().getAsTag(),
                    jda.getSelfUser().getId(),
                    jda.getGuildCache().size());
        } catch (InterruptedException e) {
            LOG.error("Interrupted while waiting for JDA to be ready", e);
            Thread.currentThread().interrupt();
        }
    }
}
