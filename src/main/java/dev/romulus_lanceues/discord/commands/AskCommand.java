package dev.romulus_lanceues.discord.commands;

import dev.romulus_lanceues.ai.ChatService;
import dev.romulus_lanceues.discord.SlashCommandHandler;
import dev.romulus_lanceues.util.MessageSplitter;
import dev.romulus_lanceues.util.RateLimiter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AskCommand implements SlashCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AskCommand.class);

    private static final String NAME = "ask";
    private static final String DESCRIPTION = "Ask Cloud a question or prompt.";
    private static final String OPTION_PROMPT = "prompt";

    private final ChatService chatService;
    private final RateLimiter rateLimiter;

    public AskCommand(ChatService chatService, RateLimiter rateLimiter) {
        this.chatService = Objects.requireNonNull(chatService, "chatService must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SlashCommandData commandData() {
        return Commands.slash(NAME, DESCRIPTION)
                .addOption(OptionType.STRING, OPTION_PROMPT, "The question or prompt to send to Cloud.", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        OptionMapping promptOption = event.getOption(OPTION_PROMPT);
        if (promptOption == null || promptOption.getAsString().strip().isEmpty()) {
            event.reply("Please provide a non-empty prompt.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long userId = event.getUser().getIdLong();
        Optional<Duration> cooldown = rateLimiter.checkCooldown(userId);
        if (cooldown.isPresent()) {
            long remainingSeconds = Math.max(1, (long) Math.ceil(cooldown.get().toMillis() / 1000.0));
            event.reply(String.format("⏳ Please wait %d seconds before asking another question.", remainingSeconds))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply().queue();

        String prompt = promptOption.getAsString().strip();
        chatService.ask(prompt).whenComplete((result, throwable) -> {
            if (throwable != null) {
                LOG.error("Failed to process /ask request for user {}", userId, throwable);
                event.getHook()
                        .editOriginal("I encountered an unexpected issue while generating a response. Please try again later.")
                        .setAllowedMentions(Collections.emptyList())
                        .queue();
                return;
            }

            List<String> chunks = MessageSplitter.split(result, 2000);
            if (chunks.isEmpty()) {
                event.getHook()
                        .editOriginal("I encountered an unexpected issue while generating a response. Please try again later.")
                        .setAllowedMentions(Collections.emptyList())
                        .queue();
                return;
            }

            event.getHook()
                    .editOriginal(chunks.get(0))
                    .setAllowedMentions(Collections.emptyList())
                    .queue();

            for (int i = 1; i < chunks.size(); i++) {
                event.getHook()
                        .sendMessage(chunks.get(i))
                        .setAllowedMentions(Collections.emptyList())
                        .queue();
            }
        });
    }
}
