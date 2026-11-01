package dev.romulus_lanceues.discord.commands;

import dev.romulus_lanceues.discord.SlashCommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class ConversationCommand implements SlashCommandHandler {

    private static final String NAME = "conversation";
    private static final String DESCRIPTION = "Have a conversation with Cloud.";
    private static final String OPTION_PROMPT = "prompt";

    // Discord message limit is 2000 characters. I truncate the preview to keep the total message well within bounds.
    static final int MAX_PROMPT_PREVIEW_LENGTH = 1800;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SlashCommandData commandData() {
        return Commands.slash(NAME, DESCRIPTION)
                .addOption(OptionType.STRING, OPTION_PROMPT, "The message or question to send to Cloud.", true);
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

        String prompt = promptOption.getAsString().strip();
        String displayPrompt = prompt.length() > MAX_PROMPT_PREVIEW_LENGTH
                ? prompt.substring(0, MAX_PROMPT_PREVIEW_LENGTH) + "..."
                : prompt;
        event.reply("**Conversation prompt received**: \"" + displayPrompt + "\"\n*(Placeholder: Gemini AI conversation will be connected here.)*")
                .queue();
    }
}
